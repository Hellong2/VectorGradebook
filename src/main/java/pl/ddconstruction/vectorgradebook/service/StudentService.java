package pl.ddconstruction.vectorgradebook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.ddconstruction.vectorgradebook.model.Student;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final VectorProcessingService vectorService;
    private final CourseService courseService;

    public List<Student> findAll() {
        return vectorService.getAllStudents();
    }

    public void save(Student student) {
        if (student.getId() == null) {
            student.setId(UUID.randomUUID());
        }
        vectorService.updateStudentVector(student);
    }

    public void delete(UUID id) {
        vectorService.deleteStudent(id);
    }

    public Student findById(UUID id) {
        return vectorService.getStudentById(id);
    }

    public String getProblematicAreaStats() {
        return vectorService.findProblematicAreas();
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
