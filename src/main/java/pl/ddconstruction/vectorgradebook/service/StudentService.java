package pl.ddconstruction.vectorgradebook.service;

import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.ddconstruction.vectorgradebook.model.Student;

import java.util.List;
import java.util.UUID;

import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final VectorProcessingService vectorService;

    public List<Student> findAll() {
        try {
            return vectorService.getAllStudents();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch students", e);
        }
    }

    public void save(Student student) {
        if (student.getId() == null) {
            student.setId(UUID.randomUUID());
        }
        try {
            vectorService.upsertStudent(student);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Failed to upsert vector", e);
        }
    }

    public void delete(UUID id) {
        try {
            vectorService.deleteStudent(id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete student", e);
        }
    }

    public Student findById(UUID id) {
        try {
            return vectorService.getStudentById(id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch student", e);
        }
    }

    public Student findPartner(Student student) {
        try {
            Points.ScoredPoint point = vectorService.findComplementaryPartner(student);
            if (point == null)
                return null;

            // Extract UUID from point ID
            UUID partnerId = UUID.fromString(point.getId().getUuid());
            return findById(partnerId);
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
