package pl.ddconstruction.vectorgradebook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ddconstruction.vectorgradebook.dto.ClassDTO;
import pl.ddconstruction.vectorgradebook.model.entity.ClassEntity;
import pl.ddconstruction.vectorgradebook.model.ClassType;
import pl.ddconstruction.vectorgradebook.model.entity.Course;
import pl.ddconstruction.vectorgradebook.model.entity.Grade;
import pl.ddconstruction.vectorgradebook.model.entity.Student;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final pl.ddconstruction.vectorgradebook.repository.StudentRepository studentRepository;
    private final pl.ddconstruction.vectorgradebook.repository.GradeRepository gradeRepository;
    private final pl.ddconstruction.vectorgradebook.repository.ClassRepository classRepository;
    private final pl.ddconstruction.vectorgradebook.repository.CourseRepository courseRepository;
    private final VectorProcessingService vectorService;
    private final CourseService courseService;

    @Transactional(readOnly = true)
    public List<Student> findAllByCourseId(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }

        List<Student> students = studentRepository.findByCourseId(courseId);
        students.forEach(this::populateTransientGrades);
        return students;
    }

    @Transactional
    public void save(Student student, UUID courseId) {
        // 1. Fetch existing student if ID is present
        Student managedStudent;
        if (student.getId() != null) {
            managedStudent = studentRepository.findById(student.getId()).orElse(student);
        } else {
            managedStudent = student;
        }

        // 2. Update basic fields
        managedStudent.setName(student.getName());

        // 3. Update Grades
        if (student.getClassGrades() != null) {
            java.util.Map<UUID, Double> desiredGrades = student.getClassGrades();
            desiredGrades.forEach((classId, value) -> {
                ClassEntity cls = classRepository.findById(classId).orElse(null);
                if (cls != null) {
                    managedStudent.getGrades().stream()
                            .filter(g -> g.getClazz().getId().equals(classId))
                            .findFirst()
                            .ifPresentOrElse(
                                    existingGrade -> existingGrade.setValue(value),
                                    () -> {
                                        var newGrade = Grade.builder()
                                                .student(managedStudent)
                                                .clazz(cls)
                                                .value(value)
                                                .build();
                                        managedStudent.getGrades().add(newGrade);
                                    });
                }
            });
            managedStudent.getGrades().removeIf(g -> !desiredGrades.containsKey(g.getClazz().getId()));
        }

        // 4. Save Student
        Student saved = studentRepository.save(managedStudent);

        // 5. Link to Course
        if (courseId != null) {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new RuntimeException("Course not found: " + courseId));
            saved.setCourse(course);
        }

        // 5. Update Vector
        try {
            if (courseId != null) {
                pl.ddconstruction.vectorgradebook.model.CourseConfig config = courseService.getCourseConfig(courseId);
                vectorService.updateStudentVector(saved, courseId, config);
            }
        } catch (Exception e) {
            // Log warning
            System.err.println("Failed to update vector: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(UUID id) {
        Student student = findById(id);
        UUID courseId = student.getCourse() != null ? student.getCourse().getId() : null;

        studentRepository.delete(student);

        if (courseId != null) {
            vectorService.deleteStudent(id, List.of(courseId));
        }
    }

    @Transactional(readOnly = true)
    public Student findById(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new pl.ddconstruction.vectorgradebook.exception.StudentNotFoundException(id));
        populateTransientGrades(student);
        return student;
    }

    private void populateTransientGrades(Student student) {
        if (student.getGrades() != null) {
            student.getClassGrades().clear();
            student.getGrades().forEach(g -> student.getClassGrades().put(g.getClazz().getId(), g.getValue()));
        }
    }

    public String getProblematicAreaStats(UUID courseId) {
        pl.ddconstruction.vectorgradebook.model.CourseConfig config = courseService.getCourseConfig(courseId);
        return vectorService.findProblematicAreas(courseId, config);
    }

    public double calculateSubjectGrade(Student student, pl.ddconstruction.vectorgradebook.model.CourseConfig config) {
        if (config == null)
            return 0.0;

        List<ClassDTO> lectures = config.getClasses().stream()
                .filter(c -> c.type() == ClassType.LECTURE)
                .toList();

        List<ClassDTO> labs = config.getClasses().stream()
                .filter(c -> c.type() == ClassType.LAB)
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

    private double calculateAverage(Student student, List<ClassDTO> classes) {
        if (classes.isEmpty())
            return 0.0;

        double sum = classes.stream()
                .mapToDouble(c -> student.getClassGrades().getOrDefault(c.id(), 0.0))
                .sum();
        return sum / classes.size();
    }
}
