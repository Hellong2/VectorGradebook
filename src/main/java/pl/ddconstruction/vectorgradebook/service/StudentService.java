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
    private final CourseService courseService;

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
            vectorService.updateStudentVector(student);
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

    public double calculateSubjectGrade(Student student) {
        var config = courseService.getCurrentConfig();
        if (config == null)
            return 0.0;

        List<pl.ddconstruction.vectorgradebook.model.Class> lectures = config.getClasses().stream()
                .filter(c -> c.getType() == pl.ddconstruction.vectorgradebook.model.ClassType.LECTURE)
                .toList();

        List<pl.ddconstruction.vectorgradebook.model.Class> labs = config.getClasses().stream()
                .filter(c -> c.getType() == pl.ddconstruction.vectorgradebook.model.ClassType.LAB)
                .toList();

        double lectureAvg = calculateAverage(student, lectures);
        double labAvg = calculateAverage(student, labs);

        if (lectures.isEmpty() && labs.isEmpty())
            return 0.0;
        if (lectures.isEmpty())
            return labAvg;
        if (labs.isEmpty())
            return lectureAvg;

        return (lectureAvg + labAvg) / 2.0;
    }

    private double calculateAverage(Student student, List<pl.ddconstruction.vectorgradebook.model.Class> classes) {
        if (classes.isEmpty())
            return 0.0;

        double sum = classes.stream()
                .mapToDouble(c -> student.getClassGrades().getOrDefault(c.getId(), 0.0))
                .sum();
        return sum / classes.size();
    }
}
