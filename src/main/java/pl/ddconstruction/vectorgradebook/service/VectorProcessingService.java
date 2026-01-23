package pl.ddconstruction.vectorgradebook.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.WithVectorsSelector;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.ddconstruction.vectorgradebook.dto.ClassDTO;
import pl.ddconstruction.vectorgradebook.dto.SkillDTO;
import pl.ddconstruction.vectorgradebook.exception.ConfigurationException;
import pl.ddconstruction.vectorgradebook.exception.StudentNotFoundException;
import pl.ddconstruction.vectorgradebook.exception.VectorGradebookException;
import pl.ddconstruction.vectorgradebook.exception.VectorStorageException;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.entity.Student;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.PointIdFactory.id;

@Service
@RequiredArgsConstructor
public class VectorProcessingService {

    private final QdrantClient qdrantClient;
    private static final int MAX_POINTS_TO_ANALYZE = 100;

    private String getCollectionName(UUID courseId) {
        return "course_" + courseId.toString();
    }

    public void updateStudentVector(Student student, UUID courseId, CourseConfig config) {
        if (config == null || config.getClasses().isEmpty()) {
            throw new ConfigurationException("Course not configured");
        }

        List<ClassDTO> sortedClasses = getSortedClasses(config);

        List<Float> vector = sortedClasses.stream()
                .map(cls -> student.getClassGrades().getOrDefault(cls.id(), 0.0).floatValue())
                .toList();

        Map<String, Value> gradesPayload = new HashMap<>();
        student.getClassGrades().forEach((k, v) -> gradesPayload.put(k.toString(), ValueFactory.value(v)));

        PointStruct point = PointStruct.newBuilder()
                .setId(id(student.getId()))
                .setVectors(VectorsFactory.vectors(vector))
                .putPayload("name", ValueFactory.value(student.getName()))
                .putPayload("grades", ValueFactory.value(gradesPayload))
                .build();

        try {
            qdrantClient.upsertAsync(getCollectionName(courseId), List.of(point)).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new VectorStorageException(
                    "Failed to save student vector", e);
        }
    }

    public void recreateCollection(UUID courseId, int dimension) {
        String collectionName = getCollectionName(courseId);
        try {
            qdrantClient.deleteCollectionAsync(collectionName).get();
        } catch (Exception e) {
        }

        try {
            qdrantClient.createCollectionAsync(
                    collectionName,
                    VectorParams.newBuilder()
                            .setSize(dimension)
                            .setDistance(Distance.Cosine)
                            .build())
                    .get();
        } catch (Exception e) {
            throw new VectorStorageException("Failed to create collection",
                    e);
        }
    }

    public void deleteStudent(UUID id, List<UUID> courseIds) {
        for (UUID courseId : courseIds) {
            try {
                qdrantClient.deleteAsync(getCollectionName(courseId), List.of(id(id))).get();
            } catch (ExecutionException | InterruptedException e) {
            }
        }
    }

    public Student getStudentById(UUID id, UUID courseId) {
        try {
            List<RetrievedPoint> points = qdrantClient.retrieveAsync(
                    getCollectionName(courseId),
                    List.of(id(id)),
                    true,
                    true,
                    null).get();

            if (points.isEmpty()) {
                throw new StudentNotFoundException(id);
            }
            return mapPointToStudent(points.getFirst());
        } catch (ExecutionException | InterruptedException e) {
            throw new VectorStorageException("Failed to retrieve student",
                    e);
        }
    }

    public String findProblematicAreas(UUID courseId, CourseConfig config) {
        try {
            List<RetrievedPoint> points = fetchPointsFromQdrant(getCollectionName(courseId));

            if (points.isEmpty()) {
                throw new VectorGradebookException(
                        "No data available for analysis");
            }

            if (config == null)
                throw new ConfigurationException("Configuration missing");

            Map<String, List<Double>> scoresBySkill = new HashMap<>();

            // Process each student
            for (RetrievedPoint point : points) {
                Student student = mapPointToStudent(point);
                Map<UUID, Double> grades = student.getClassGrades();

                // Map grades to skills
                config.getClasses().forEach(cls -> {
                    Double val = grades.get(cls.id());
                    if (val != null) {
                        for (SkillDTO skill : cls.skills()) {
                            scoresBySkill.computeIfAbsent(skill.name(), k -> new ArrayList<>()).add(val);
                        }
                    }
                });
            }

            if (scoresBySkill.isEmpty()) {
                return "No skills data";
            }

            // Calculate averages
            String worstSkill = null;
            double minAvg = Double.MAX_VALUE;

            for (Map.Entry<String, List<Double>> entry : scoresBySkill.entrySet()) {
                double avg = entry.getValue().stream().mapToDouble(d -> d).average().orElse(0.0);
                if (avg < minAvg) {
                    minAvg = avg;
                    worstSkill = entry.getKey();
                }
            }

            if (worstSkill == null)
                return "Unknown";

            return String.format("%s (Avg: %.2f)", worstSkill, minAvg);
        } catch (ExecutionException | InterruptedException e) {
            throw new VectorStorageException("Failed to analyze areas", e);
        }
    }

    private List<RetrievedPoint> fetchPointsFromQdrant(String collectionName)
            throws ExecutionException, InterruptedException {
        return qdrantClient.scrollAsync(
                ScrollPoints.newBuilder()
                        .setCollectionName(collectionName)
                        .setLimit(MAX_POINTS_TO_ANALYZE)
                        .setWithVectors(WithVectorsSelector.newBuilder().setEnable(true).build())
                        .build())
                .get()
                .getResultList();
    }

    private List<ClassDTO> getSortedClasses(CourseConfig config) {
        return config.getClasses().stream()
                .sorted(Comparator.comparing(ClassDTO::date)
                        .thenComparing(ClassDTO::id))
                .toList();
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
}
