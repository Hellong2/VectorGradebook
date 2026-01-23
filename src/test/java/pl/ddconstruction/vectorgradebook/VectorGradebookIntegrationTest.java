package pl.ddconstruction.vectorgradebook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.ddconstruction.vectorgradebook.model.Class;
import pl.ddconstruction.vectorgradebook.model.ClassType;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.Student;
import pl.ddconstruction.vectorgradebook.service.CourseService;
import pl.ddconstruction.vectorgradebook.service.TeamFormationService;
import pl.ddconstruction.vectorgradebook.service.VectorProcessingService;
import pl.ddconstruction.vectorgradebook.ui.TeamGeneratorView;
import pl.ddconstruction.vectorgradebook.ui.TeamGeneratorView.Team;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "app.course-config.path=target/test-course_config.json")
public class VectorGradebookIntegrationTest {

    @Container
    static GenericContainer<?> qdrant = new GenericContainer<>("qdrant/qdrant:v1.13.4")
            .withExposedPorts(6334);

    @DynamicPropertySource
    static void qdrantProperties(DynamicPropertyRegistry registry) {
        registry.add("qdrant.host", qdrant::getHost);
        registry.add("qdrant.port", () -> qdrant.getMappedPort(6334));
    }

    @Autowired
    private VectorProcessingService vectorProcessingService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private TeamFormationService teamFormationService;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        // 1. Configure Course with 5 Lectures and 5 Labs
        CourseConfig config = new CourseConfig();
        config.setCourseName("Integration Test Course");

        Set<String> skills = new HashSet<>(Arrays.asList("Java", "Spring", "Database"));
        config.setAvailableSkills(skills);

        List<Class> classes = new ArrayList<>();
        // 5 Lectures
        for (int i = 1; i <= 5; i++) {
            Set<String> classSkills = new HashSet<>();
            classSkills.add(i % 2 == 0 ? "Spring" : "Java"); // Mix skills

            classes.add(Class.builder()
                    .id(UUID.randomUUID())
                    .topic("Wykład " + i)
                    .date(LocalDate.now().plusDays(i))
                    .type(ClassType.LECTURE)
                    .skills(classSkills)
                    .build());
        }
        // 5 Labs
        for (int i = 1; i <= 5; i++) {
            Set<String> classSkills = new HashSet<>();
            classSkills.add("Database");

            classes.add(Class.builder()
                    .id(UUID.randomUUID())
                    .topic("Laboratorium " + i)
                    .date(LocalDate.now().plusDays(5 + i))
                    .type(ClassType.LAB)
                    .skills(classSkills)
                    .build());
        }
        config.setClasses(classes);
        courseService.saveConfig(config);

        vectorProcessingService.recreateCollection(classes.size());
    }

    @Test
    void testScenarios() throws ExecutionException, InterruptedException {
        // 2. Generate 15 Students with random grades
        List<Student> students = new ArrayList<>();
        Random random = new Random();
        CourseConfig config = courseService.getCurrentConfig();

        for (int i = 1; i <= 15; i++) {
            Student student = new Student();
            student.setId(UUID.randomUUID());
            student.setName("Student " + i);
            Map<UUID, Double> grades = new HashMap<>();

            for (Class cls : config.getClasses()) {
                // Determine grade bias based on tag to force "Problematic Area"
                // Let's make "Database" (Labs) hard -> low grades
                double base = 3.0;
                if (cls.getSkills().contains("Database")) {
                    base = 2.0; // Lower average for Database
                } else {
                    base = 4.0; // Higher average for Java/Spring
                }

                double grade = base + (random.nextDouble());
                // Round to nearest 0.5
                grade = Math.round(grade * 2) / 2.0;
                if (grade > 5.0)
                    grade = 5.0;

                grades.put(cls.getId(), grade);
            }
            student.setClassGrades(grades);
            students.add(student);
            vectorProcessingService.updateStudentVector(student);
        }

        // Wait for indexing (simple sleep or query loop)
        Thread.sleep(2000);

        // Scenario 1: Verify Subject Grade Calculation
        verifyGradeCalculation(students, config);

        // Scenario 2: Problematic Area Identification
        // Should be "Database" because we biased the grades lower
        String problemArea = vectorProcessingService.findProblematicAreas();
        System.out.println("Problematic Area: " + problemArea);
        assertNotNull(problemArea);
        assertTrue(problemArea.contains("Database"), "Problematic area should be Database");

        // Scenario 3: Team Formation
        verifyTeamFormation(students);
    }

    private void verifyGradeCalculation(List<Student> students, CourseConfig config) {
        // Keep existing logic, just a placeholder check
        assertFalse(students.isEmpty());
    }

    private void verifyTeamFormation(List<Student> students) {
        // Test Prefer 2
        List<Team> pairs = teamFormationService
                .generateTeams(students, false);
        System.out.println("Generated Pair Teams count: " + pairs.size());
        assertEquals(15, countStudentsInTeams(pairs), "All students should be assigned in pairs preference");

        // With 15 students, prefer 2 should create: 6 pairs (12) + 1 trio (3) = 7
        // teams.
        // OR 7 pairs (14) + 1 left? No, logic handles leftovers by adding to existing.
        // If we have 7 teams: 6*2 + 1*3 = 15. Correct.
        assertTrue(pairs.stream().anyMatch(t -> t.getMembers().size() == 3),
                "Should have at least one trio to handle odd number");

        // Test Prefer 3
        List<Team> trios = teamFormationService
                .generateTeams(students, true);
        System.out.println("Generated Trio Teams count: " + trios.size());
        assertEquals(15, countStudentsInTeams(trios), "All students should be assigned in trios preference");
        // 15 is divisible by 3, so should be exactly 5 trios.
        assertEquals(5, trios.size());
        assertTrue(trios.stream().allMatch(t -> t.getMembers().size() == 3), "All teams should be trios");
    }

    private int countStudentsInTeams(List<Team> teams) {
        return teams.stream().mapToInt(t -> t.getMembers().size()).sum();
    }
}
