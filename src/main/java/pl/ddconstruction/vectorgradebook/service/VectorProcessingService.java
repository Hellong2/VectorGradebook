package pl.ddconstruction.vectorgradebook.service;

import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;

import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.WithVectorsSelector;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.ddconstruction.vectorgradebook.model.Student;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.qdrant.client.PointIdFactory.id;

@Service
@RequiredArgsConstructor
public class VectorProcessingService {

    private final QdrantClient qdrantClient;
    private static final String COLLECTION_NAME = "students";
    // Fixed order for vector dimensions
    private static final List<String> TOPICS = List.of("Algorithms", "Databases", "Java", "Testing");
    private static final int VECTOR_DIMENSIONS = 4;
    private static final int MAX_POINTS_TO_ANALYZE = 100;

    public void upsertStudent(Student student) throws ExecutionException, InterruptedException {
        // Convert grades to float vector in fixed order
        List<Float> vector = TOPICS.stream()
                .map(topic -> student.getGrades().getOrDefault(topic, 0.0).floatValue())
                .toList();

        // Convert grades map to Qdrant payload map
        Map<String, Value> gradesPayload = new HashMap<>();
        student.getGrades().forEach((k, v) -> gradesPayload.put(k, ValueFactory.value(v)));

        PointStruct point = PointStruct.newBuilder()
                .setId(id(student.getId()))
                .setVectors(VectorsFactory.vectors(vector))
                .putPayload("name", ValueFactory.value(student.getName()))
                .putPayload("grades", ValueFactory.value(gradesPayload))
                .build();

        qdrantClient.upsertAsync(COLLECTION_NAME, List.of(point)).get();
    }

    public List<Student> getAllStudents() throws ExecutionException, InterruptedException {
        List<RetrievedPoint> points = fetchPointsFromQdrant();
        return points.stream()
                .map(this::mapPointToStudent)
                .toList();
    }

    public void deleteStudent(UUID id) throws ExecutionException, InterruptedException {
        qdrantClient.deleteAsync(COLLECTION_NAME, List.of(id(id)))
                .get();
    }

    public Student getStudentById(UUID id) throws ExecutionException, InterruptedException {
        // For small datasets, retrieving all and filtering is acceptable
        return getAllStudents().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private Student mapPointToStudent(RetrievedPoint point) {
        UUID id = UUID.fromString(point.getId().getUuid());
        String name = point.getPayloadMap().containsKey("name") ? point.getPayloadMap().get("name").getStringValue()
                : "Unknown";

        Map<String, Double> grades = new HashMap<>();

        if (point.getPayloadMap().containsKey("grades")) {
            Map<String, Value> gradesStruct = point.getPayloadMap()
                    .get("grades").getStructValue().getFieldsMap();
            gradesStruct.forEach((k, v) -> grades.put(k, v.getDoubleValue()));
        }

        return Student.builder()
                .id(id)
                .name(name)
                .grades(grades)
                .build();
    }

    public ScoredPoint findComplementaryPartner(Student student) throws ExecutionException, InterruptedException {
        // Target vector calculation
        List<Float> targetVector = TOPICS.stream()
                .map(topic -> 5.0f - student.getGrades().getOrDefault(topic, 0.0).floatValue())
                .collect(Collectors.toList());

        // Get top 5 results and filter manually in Java to avoid import issues with
        // Filter/Condition
        List<ScoredPoint> results = qdrantClient.searchAsync(
                SearchPoints.newBuilder()
                        .setCollectionName(COLLECTION_NAME)
                        .addAllVector(targetVector)
                        .setLimit(5)
                        .build())
                .get();

        if (results.isEmpty()) {
            return null;
        }

        // Filter out the student themselves
        String studentIdStr = student.getId().toString();

        return results.stream()
                .filter(sp -> !sp.getId().getUuid().equals(studentIdStr))
                .findFirst()
                .orElse(null);
    }

    /**
     * Identifies the topic area with the lowest average score across all student
     * data.
     *
     * @return Topic name with its average score, or "No data" if collection is
     *         empty
     */
    public String findProblematicAreas() throws ExecutionException, InterruptedException {
        List<RetrievedPoint> points = fetchPointsFromQdrant();

        if (points.isEmpty()) {
            return "No data";
        }

        double[] averages = calculateAverageScoresPerDimension(points);
        int lowestScoreIndex = findIndexOfLowestAverage(averages);

        return formatResult(lowestScoreIndex, averages[lowestScoreIndex]);
    }

    private List<RetrievedPoint> fetchPointsFromQdrant() throws ExecutionException, InterruptedException {
        return qdrantClient.scrollAsync(
                ScrollPoints.newBuilder()
                        .setCollectionName(COLLECTION_NAME)
                        .setLimit(MAX_POINTS_TO_ANALYZE)
                        .setWithVectors(WithVectorsSelector.newBuilder().setEnable(true).build())
                        .build())
                .get()
                .getResultList();
    }

    private double[] calculateAverageScoresPerDimension(List<RetrievedPoint> points) {
        double[] dimensionSums = sumVectorsByDimension(points);
        int pointCount = points.size();

        return Arrays.stream(dimensionSums)
                .map(sum -> sum / pointCount)
                .toArray();
    }

    private double[] sumVectorsByDimension(List<RetrievedPoint> points) {
        double[] sums = new double[VECTOR_DIMENSIONS];

        for (RetrievedPoint point : points) {
            List<Float> vector = point.getVectors().getVector().getDataList();

            for (int dimension = 0; dimension < VECTOR_DIMENSIONS; dimension++) {
                sums[dimension] += vector.get(dimension);
            }
        }

        return sums;
    }

    private int findIndexOfLowestAverage(double[] averages) {
        int lowestIndex = 0;
        double lowestValue = averages[0];

        for (int i = 1; i < averages.length; i++) {
            if (averages[i] < lowestValue) {
                lowestValue = averages[i];
                lowestIndex = i;
            }
        }

        return lowestIndex;
    }

    private String formatResult(int topicIndex, double averageScore) {
        String topicName = TOPICS.get(topicIndex);
        String formattedScore = String.format("%.2f", averageScore);

        return String.format("%s (Avg: %s)", topicName, formattedScore);
    }
}
