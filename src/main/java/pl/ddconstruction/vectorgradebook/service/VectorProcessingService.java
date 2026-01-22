package pl.ddconstruction.vectorgradebook.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.WithVectorsSelector;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.ddconstruction.vectorgradebook.model.Class;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.Student;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.qdrant.client.PointIdFactory.id;

@Service
@RequiredArgsConstructor
public class VectorProcessingService {

    private final QdrantClient qdrantClient;
    private final CourseService courseService;
    private static final String COLLECTION_NAME = "students";
    private static final int MAX_POINTS_TO_ANALYZE = 100;

    public void updateStudentVector(Student student) throws ExecutionException, InterruptedException {
        CourseConfig config = courseService.getCurrentConfig();
        if (config == null || config.getClasses().isEmpty()) {
            throw new IllegalStateException("Course not configured");
        }

        // Sort classes to ensure consistent vector dimension order
        List<Class> sortedClasses = getSortedClasses(config);

        // Convert grades to float vector based on sorted classes
        List<Float> vector = sortedClasses.stream()
                .map(cls -> student.getClassGrades().getOrDefault(cls.getId(), 0.0).floatValue())
                .toList();

        System.out.println("DEBUG: Config classes size: " + config.getClasses().size());
        System.out.println("DEBUG: Sorted classes size: " + sortedClasses.size());
        System.out.println("DEBUG: Generated vector size: " + vector.size());
        System.out.println("DEBUG: Generated vector: " + vector);

        // Convert grades map (UUID -> Double) to Qdrant payload (String -> Double)
        Map<String, Value> gradesPayload = new HashMap<>();
        student.getClassGrades().forEach((k, v) -> gradesPayload.put(k.toString(), ValueFactory.value(v)));

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

    public void recreateCollection(int dimension) {
        try {
            qdrantClient.deleteCollectionAsync(COLLECTION_NAME).get();
            System.out.println("Collection 'students' deleted.");
        } catch (Exception e) {
            System.out.println("Collection deletion skipped (likely didn't exist): " + e.getMessage());
        }

        try {
            qdrantClient.createCollectionAsync(
                    COLLECTION_NAME,
                    VectorParams.newBuilder()
                            .setSize(dimension)
                            .setDistance(Distance.Cosine)
                            .build())
                    .get();
            System.out.println("Collection 'students' created with dimension: " + dimension);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create collection", e);
        }
    }

    public void deleteStudent(UUID id) throws ExecutionException, InterruptedException {
        qdrantClient.deleteAsync(COLLECTION_NAME, List.of(id(id)))
                .get();
    }

    public Student getStudentById(UUID id) throws ExecutionException, InterruptedException {
        return getAllStudents().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private Student mapPointToStudent(RetrievedPoint point) {
        UUID id = UUID.fromString(point.getId().getUuid());
        String name = point.getPayloadMap().containsKey("name") ? point.getPayloadMap().get("name").getStringValue()
                : "Unknown";

        Map<UUID, Double> grades = new HashMap<>();

        if (point.getPayloadMap().containsKey("grades")) {
            Map<String, Value> gradesStruct = point.getPayloadMap()
                    .get("grades").getStructValue().getFieldsMap();
            gradesStruct.forEach((k, v) -> grades.put(UUID.fromString(k), v.getDoubleValue()));
        }

        return Student.builder()
                .id(id)
                .name(name)
                .classGrades(grades)
                .build();
    }

    public ScoredPoint findComplementaryPartner(Student student) throws ExecutionException, InterruptedException {
        CourseConfig config = courseService.getCurrentConfig();
        if (config == null)
            return null;

        List<Class> sortedClasses = getSortedClasses(config);

        // Target vector calculation (Complementary skills)
        List<Float> targetVector = sortedClasses.stream()
                .map(cls -> 5.0f - student.getClassGrades().getOrDefault(cls.getId(), 0.0).floatValue())
                .collect(Collectors.toList());

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

        String studentIdStr = student.getId().toString();

        return results.stream()
                .filter(sp -> !sp.getId().getUuid().equals(studentIdStr))
                .findFirst()
                .orElse(null);
    }

    public String findProblematicAreas() throws ExecutionException, InterruptedException {
        List<RetrievedPoint> points = fetchPointsFromQdrant();

        if (points.isEmpty()) {
            return "No data";
        }

        CourseConfig config = courseService.getCurrentConfig();
        if (config == null)
            return "No config";

        Map<String, List<Double>> scoresByTag = new HashMap<>();

        // Process each student
        for (RetrievedPoint point : points) {
            Student student = mapPointToStudent(point);
            Map<UUID, Double> grades = student.getClassGrades();

            // Map grades to tags
            config.getClasses().forEach(cls -> {
                Double val = grades.get(cls.getId());
                if (val != null) {
                    for (String tag : cls.getTags()) {
                        scoresByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(val);
                    }
                }
            });
        }

        if (scoresByTag.isEmpty()) {
            return "No tags data";
        }

        // Calculate averages
        String worstTag = null;
        double minAvg = Double.MAX_VALUE;

        for (Map.Entry<String, List<Double>> entry : scoresByTag.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(d -> d).average().orElse(0.0);
            if (avg < minAvg) {
                minAvg = avg;
                worstTag = entry.getKey();
            }
        }

        if (worstTag == null)
            return "Unknown";

        return String.format("%s (Avg: %.2f)", worstTag, minAvg);
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

    private List<Class> getSortedClasses(CourseConfig config) {
        // Sort by Date, then ID to ensure deterministic order
        return config.getClasses().stream()
                .sorted(Comparator.comparing(Class::getDate)
                        .thenComparing(Class::getId))
                .toList();
    }
}
