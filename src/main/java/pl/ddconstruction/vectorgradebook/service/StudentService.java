package pl.ddconstruction.vectorgradebook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ddconstruction.vectorgradebook.dto.ClassDTO;
import pl.ddconstruction.vectorgradebook.exception.StudentNotFoundException;
import pl.ddconstruction.vectorgradebook.model.ClassType;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.entity.ClassEntity;
import pl.ddconstruction.vectorgradebook.model.entity.Course;
import pl.ddconstruction.vectorgradebook.model.entity.Grade;
import pl.ddconstruction.vectorgradebook.model.entity.Student;
import pl.ddconstruction.vectorgradebook.repository.ClassRepository;
import pl.ddconstruction.vectorgradebook.repository.CourseRepository;
import pl.ddconstruction.vectorgradebook.repository.StudentRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final VectorProcessingService vectorService;
    private final CourseService courseService;

    @Transactional
    public List<Student> findAllByCourseId(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }

        List<Student> students = studentRepository.findByCourseId(courseId);
        CourseConfig config = courseService.getCourseConfig(courseId);
        Set<UUID> classIds = config.getClasses().stream()
                .map(ClassDTO::id)
                .collect(Collectors.toSet());
        students.forEach(student -> {
            populateTransientGrades(student);
            mergeMissingGradesFromVectorStore(student, courseId, classIds);
        });
        return students;
    }

    @Transactional
    public void save(Student student, UUID courseId) {
        Student managedStudent;
        if (student.getId() != null) {
            managedStudent = studentRepository.findById(student.getId()).orElse(student);
        } else {
            managedStudent = student;
        }

        managedStudent.setName(student.getName());
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

        Student saved = studentRepository.save(managedStudent);
        if (courseId != null) {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new RuntimeException("Course not found: " + courseId));
            saved.setCourse(course);
        }

        try {
            if (courseId != null) {
                CourseConfig config = courseService.getCourseConfig(courseId);
                vectorService.updateStudentVector(saved, courseId, config);
            }
        } catch (Exception e) {
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
                .orElseThrow(() -> new StudentNotFoundException(id));
        populateTransientGrades(student);
        return student;
    }

    private void populateTransientGrades(Student student) {
        if (student.getGrades() != null) {
            student.getClassGrades().clear();
            student.getGrades().forEach(g -> student.getClassGrades().put(g.getClazz().getId(), g.getValue()));
        }
    }

    private void mergeMissingGradesFromVectorStore(Student student, UUID courseId, Set<UUID> classIds) {
        if (classIds.isEmpty()) {
            return;
        }

        Map<UUID, Double> currentGrades = student.getClassGrades();
        if (currentGrades == null) {
            return;
        }

        Set<UUID> missing = new java.util.HashSet<>(classIds);
        missing.removeAll(currentGrades.keySet());
        if (missing.isEmpty()) {
            return;
        }

        try {
            Student vectorStudent = vectorService.getStudentById(student.getId(), courseId);
            Map<UUID, Double> vectorGrades = vectorStudent.getClassGrades();
            if (vectorGrades == null || vectorGrades.isEmpty()) {
                return;
            }
            missing.forEach(id -> {
                Double value = vectorGrades.get(id);
                if (value != null) {
                    currentGrades.put(id, value);
                    classRepository.findById(id).ifPresent(cls -> {
                        Grade grade = Grade.builder()
                                .student(student)
                                .clazz(cls)
                                .value(value)
                                .build();
                        student.getGrades().add(grade);
                    });
                }
            });
        } catch (Exception e) {
            // Ignore vector store issues and rely on SQL data.
        }
    }

    public String getProblematicAreaStats(UUID courseId) {
        CourseConfig config = courseService.getCourseConfig(courseId);
        return vectorService.findProblematicAreas(courseId, config);
    }

    public double calculateSubjectGrade(Student student, CourseConfig config) {
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

        Map<UUID, Double> grades = student.getClassGrades();
        if (grades == null || grades.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        int count = 0;
        for (ClassDTO cls : classes) {
            Double value = grades.get(cls.id());
            if (value != null) {
                sum += value;
                count++;
            }
        }

        return count == 0 ? 0.0 : sum / count;
    }
}
