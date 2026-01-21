package pl.ddconstruction.VectorGradebook.service;

import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.ddconstruction.VectorGradebook.model.Student;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VectorProcessingService {

    private final QdrantClient qdrantClient;
    private static final String COLLECTION_NAME = "students";
    // Fixed order for vector dimensions
    private static final List<String> TOPICS = List.of("Algorithms", "Databases", "Java", "Testing");

    public void upsertStudent(Student student) throws ExecutionException, InterruptedException {
        // Convert grades to float vector in fixed order
        List<Float> vector = TOPICS.stream()
                .map(topic -> student.getGrades().getOrDefault(topic, 0.0).floatValue())
                .collect(Collectors.toList());

        PointStruct point = PointStruct.newBuilder()
                .setId(PointIdFactory.id(student.getId()))
                .setVectors(VectorsFactory.vectors(vector))
                .putPayload("name", ValueFactory.value(student.getName()))
                .build();

        qdrantClient.upsertAsync(COLLECTION_NAME, List.of(point)).get();
    }

    public ScoredPoint findComplementaryPartner(Student student) throws ExecutionException, InterruptedException {
        // Target vector: 5.0 - grade. We want to find someone who has high scores where
        // this student has low scores.
        // e.g. Student: [3, 5, 4, 2] -> Weakest are Algo(3) and Testing(2).
        // Ideal partner has high Algo and Testing.
        // Inverse vector: [5-3, 5-5, 5-4, 5-2] = [2, 0, 1, 3] -> We search for vector
        // close to this?
        // Wait, if we use Cosine similarity, we want the partner's vector to align with
        // the "needs".
        // A simple "Complementary" approach in vector space (Pure Math):
        // Target = MaxScore - CurrentScore.
        // Then we search implementation for nearest neighbor to Target.

        List<Float> targetVector = TOPICS.stream()
                .map(topic -> 5.0f - student.getGrades().getOrDefault(topic, 0.0).floatValue())
                .collect(Collectors.toList());

        List<ScoredPoint> results = qdrantClient.searchAsync(
                Points.SearchPoints.newBuilder()
                        .setCollectionName(COLLECTION_NAME)
                        .addAllVector(targetVector)
                        .setLimit(2) // Get top 2, because the student themselves shouldn't be matched (though their
                                     // vector is diff)
                        // Actually, if we search for [2,0,1,3], the student [3,5,4,2] might not be
                        // close.
                        // But we should filter the student themself out just in case.
                        .setFilter(Points.Filter.newBuilder()
                                .addMustNot(Points.Condition.newBuilder()
                                        .setHasId(Points.HasIdCondition.newBuilder()
                                                .addHasId(PointIdFactory.id(student.getId())).build())
                                        .build())
                                .build())
                        .build())
                .get();

        if (results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    public String findProblematicAreas() throws ExecutionException, InterruptedException {
        // This requires retrieving all vectors or doing an aggregation if Qdrant
        // supports it (it doesn't directly support column avg).
        // We will scroll through points.
        // For simplicity, let's fetch first 100 points (assuming class size < 100).

        List<Points.RetrievedPoint> points = qdrantClient.scrollAsync(
                Points.ScrollPoints.newBuilder()
                        .setCollectionName(COLLECTION_NAME)
                        .setLimit(100)
                        .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(true).build())
                        .build())
                .get().getResultList();

        if (points.isEmpty())
            return "No data";

        double[] sums = new double[4];
        int count = points.size();

        for (Points.RetrievedPoint p : points) {
            List<Float> vec = p.getVectors().getVector().getDataList(); // Assuming named vectors not used, uses default
            for (int i = 0; i < 4; i++) {
                sums[i] += vec.get(i);
            }
        }

        int minIndex = -1;
        double minAvg = Double.MAX_VALUE;

        for (int i = 0; i < 4; i++) {
            double avg = sums[i] / count;
            if (avg < minAvg) {
                minAvg = avg;
                minIndex = i;
            }
        }

        return TOPICS.get(minIndex) + " (Avg: " + String.format("%.2f", minAvg) + ")";
    }
}
