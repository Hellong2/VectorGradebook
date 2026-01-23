package pl.ddconstruction.vectorgradebook.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import pl.ddconstruction.vectorgradebook.dto.ClassDTO;
import pl.ddconstruction.vectorgradebook.dto.SkillDTO;
import pl.ddconstruction.vectorgradebook.model.ClassType;
import pl.ddconstruction.vectorgradebook.model.entity.Course;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.repository.CourseRepository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest
@Import({ CourseService.class, EntityMapper.class, CourseServiceIsolationTest.TestConfig.class })
public class CourseServiceIsolationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public VectorProcessingService vectorProcessingService() {
            return mock(VectorProcessingService.class);
        }
    }

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    void saveConfig_ShouldPersistCourseAndClasses_WhenValidConfigProvided() {
        // Arrange
        CourseConfig config = new CourseConfig();
        config.setCourseName("Isolated Test Course");

        Set<String> skills = new HashSet<>();
        skills.add("Java");
        config.setAvailableSkills(skills);

        ClassDTO classDTO = new ClassDTO(
                null,
                "Intro to Java",
                LocalDate.now(),
                ClassType.LECTURE,
                Collections.singleton(new SkillDTO(null, "Java")));
        config.setClasses(List.of(classDTO));

        // Act
        courseService.saveConfig(config);

        // Assert
        List<Course> courses = courseRepository.findAll();
        assertThat(courses).hasSize(1);

        Course savedCourse = courses.get(0);
        assertThat(savedCourse.getName()).isEqualTo("Isolated Test Course");
        assertThat(savedCourse.getClassEntities()).hasSize(1);

        // Verify bidirectional relationship and cascading
        assertThat(savedCourse.getClassEntities().iterator().next().getCourse()).isEqualTo(savedCourse);
    }
}
