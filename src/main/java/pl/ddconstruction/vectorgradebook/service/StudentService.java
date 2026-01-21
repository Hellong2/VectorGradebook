package pl.ddconstruction.VectorGradebook.service;

import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.ddconstruction.VectorGradebook.model.Student;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final VectorProcessingService vectorService;
    private final Map<UUID, Student> studentMap = new ConcurrentHashMap<>();

    public List<Student> findAll() {
        return new ArrayList<>(studentMap.values());
    }

    public void save(Student student) {
        if (student.getId() == null) {
            student.setId(UUID.randomUUID());
        }
        studentMap.put(student.getId(), student);
        try {
            vectorService.upsertStudent(student);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Failed to upsert vector", e);
        }
    }

    public Student findById(UUID id) {
        return studentMap.get(id);
    }

    public Student findPartner(Student student) {
        try {
            Points.ScoredPoint point = vectorService.findComplementaryPartner(student);
            if (point == null)
                return null;

            // Extract UUID from point ID
            UUID partnerId = UUID.fromString(point.getId().getUuid());
            return studentMap.get(partnerId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find partner", e);
        }
    }

    public String getProblematicAreaStats() {
        try {
            return vectorService.findProblematicAreas();
        } catch (Exception e) {
            return "Error calculating stats";
        }
    }
}
