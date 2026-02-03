package pl.ddconstruction.vectorgradebook.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import pl.ddconstruction.vectorgradebook.dto.ClassDTO;
import pl.ddconstruction.vectorgradebook.dto.SkillDTO;
import pl.ddconstruction.vectorgradebook.model.ClassType;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.entity.Student;
import pl.ddconstruction.vectorgradebook.repository.StudentRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({ StudentService.class, CourseService.class, EntityMapper.class, StudentServicePersistenceTest.TestConfig.class })
class StudentServicePersistenceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public VectorProcessingService vectorProcessingService() {
            return mock(VectorProcessingService.class);
        }
    }

    @Autowired
    private StudentService studentService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private VectorProcessingService vectorProcessingService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findAllByCourseId_PersistsMissingGradesFromVector() {
        CourseConfig config = new CourseConfig();
        config.setCourseName("Persistence Test Course");
        config.setAvailableSkills(Set.of("Java"));
        config.setClasses(List.of(
                new ClassDTO(null, "Lecture 1", LocalDate.now(), ClassType.LECTURE,
                        Set.of(new SkillDTO(null, "Java"))),
                new ClassDTO(null, "Lecture 2", LocalDate.now().plusDays(1), ClassType.LECTURE,
                        Set.of(new SkillDTO(null, "Java")))));

        UUID courseId = courseService.saveConfig(config);
        CourseConfig persistedConfig = courseService.getCourseConfig(courseId);
        UUID classId1 = persistedConfig.getClasses().stream()
                .filter(cls -> "Lecture 1".equals(cls.topic()))
                .map(ClassDTO::id)
                .findFirst()
                .orElseThrow();
        UUID classId2 = persistedConfig.getClasses().stream()
                .filter(cls -> "Lecture 2".equals(cls.topic()))
                .map(ClassDTO::id)
                .findFirst()
                .orElseThrow();

        Student student = Student.builder()
                .name("Test Student")
                .classGrades(Map.of(classId1, 4.0))
                .build();
        studentService.save(student, courseId);

        Map<UUID, Double> vectorGrades = new HashMap<>();
        vectorGrades.put(classId1, 4.0);
        vectorGrades.put(classId2, 3.5);
        Student vectorStudent = Student.builder()
                .id(student.getId())
                .name(student.getName())
                .classGrades(vectorGrades)
                .build();

        when(vectorProcessingService.getStudentById(student.getId(), courseId)).thenReturn(vectorStudent);

        studentService.findAllByCourseId(courseId);

        entityManager.flush();
        entityManager.clear();

        Student refreshed = studentRepository.findById(student.getId()).orElseThrow();
        assertThat(refreshed.getGrades()).hasSize(2);
        assertThat(refreshed.getGrades()).anySatisfy(grade -> {
            assertThat(grade.getClazz().getId()).isEqualTo(classId2);
            assertThat(grade.getValue()).isEqualTo(3.5);
        });
    }
}
