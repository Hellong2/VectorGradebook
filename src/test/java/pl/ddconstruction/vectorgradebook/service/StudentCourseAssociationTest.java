package pl.ddconstruction.vectorgradebook.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.entity.Student;
import pl.ddconstruction.vectorgradebook.repository.CourseRepository;
import pl.ddconstruction.vectorgradebook.repository.StudentRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
public class StudentCourseAssociationTest {

    @Autowired
    private CourseService courseService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    public void testStudentAddedToActiveCourse() {
        // 1. Create and Activate Course
        CourseConfig config = new CourseConfig();
        String courseName = "Test Course " + UUID.randomUUID();
        config.setCourseName(courseName);
        config.setAvailableSkills(Set.of("Java"));
        config.setClasses(List.of());

        // Save returns the ID
        UUID courseId = courseService.saveConfig(config);

        // 2. Create Student
        Student student = new Student();
        student.setName("Test Student");

        // 3. Save Student with Context
        studentService.save(student, courseId);

        // 4. Verify Student is in Course (via Service findAll which uses filter)
        List<Student> studentsInCourseService = studentService.findAllByCourseId(courseId);
        boolean foundInService = studentsInCourseService.stream().anyMatch(s -> s.getName().equals("Test Student"));
        assertTrue(foundInService,
                "Student should be visible in StudentService.findAllByCourseId() for the active course");

        // 5. Verify Database Relationship explicitly
        var refreshedCourse = courseRepository.findById(courseId).orElseThrow();
        boolean foundInRepo = refreshedCourse.getStudents().stream().anyMatch(s -> s.getName().equals("Test Student"));
        assertTrue(foundInRepo, "Student should be present in Course.getStudents() collection");
    }
}
