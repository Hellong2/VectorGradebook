package pl.ddconstruction.vectorgradebook.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;
import lombok.AllArgsConstructor;
import lombok.Data;
import pl.ddconstruction.vectorgradebook.model.Student;
import pl.ddconstruction.vectorgradebook.service.StudentService;
import pl.ddconstruction.vectorgradebook.service.CourseService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Route(value = "teams", layout = MainLayout.class)
public class TeamGeneratorView extends VerticalLayout {

    private final StudentService studentService;
    private final CourseService courseService;
    private final Grid<Team> teamGrid = new Grid<>(Team.class);
    private final Span statsSpan = new Span();

    public TeamGeneratorView(StudentService studentService, CourseService courseService) {
        this.studentService = studentService;
        this.courseService = courseService;

        setSizeFull();

        if (!courseService.isConfigured()) {
            add("Przedmiot nie jest skonfigurowany.");
            return;
        }

        Button generateButton = new Button("Generuj Zespoły", e -> generateTeams());
        generateButton.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);

        Button statsButton = new Button("Pokaż Najsłabszy Obszar", e -> showStats());

        configureGrid();

        add(new H2("Formowanie Zespołów"), new HorizontalLayout(generateButton, statsButton), statsSpan, teamGrid);
    }

    private void showStats() {
        String stats = studentService.getProblematicAreaStats();
        statsSpan.setText("Najsłabszy Obszar Klasy: " + stats);
        statsSpan.getElement().getStyle().set("font-weight", "bold");
    }

    private void configureGrid() {
        teamGrid.removeAllColumns();
        teamGrid.addColumn(t -> t.member1.getName()).setHeader("Student 1");
        teamGrid.addColumn(t -> formatGrades(t.member1.getClassGrades())).setHeader("Oceny S1").setAutoWidth(true);
        teamGrid.addColumn(t -> t.member2.getName()).setHeader("Partner");
        teamGrid.addColumn(t -> t.member2 != null ? formatGrades(t.member2.getClassGrades()) : "-")
                .setHeader("Oceny Partnera").setAutoWidth(true);
    }

    private String formatGrades(java.util.Map<UUID, Double> grades) {
        if (grades == null)
            return "";

        var config = courseService.getCurrentConfig();
        if (config == null)
            return "";

        // Map UUID to Topic Name for display
        return grades.entrySet().stream()
                .map(e -> {
                    String topic = config.getClasses().stream()
                            .filter(c -> c.getId().equals(e.getKey()))
                            .findFirst()
                            .map(c -> c.getTopic())
                            .orElse("Unknown");
                    return String.format("%s: %.1f", topic, e.getValue());
                })
                .collect(Collectors.joining(", "));
    }

    private void generateTeams() {
        List<Student> allStudents = studentService.findAll();
        List<Team> teams = new ArrayList<>();
        Set<Student> matched = new HashSet<>();

        for (Student s : allStudents) {
            if (matched.contains(s))
                continue;

            Student partner = studentService.findPartner(s);

            if (partner != null && !matched.contains(partner)) {
                teams.add(new Team(s, partner));
                matched.add(s);
                matched.add(partner);
            }
        }

        // Add unmatched
        for (Student s : allStudents) {
            if (!matched.contains(s)) {
                Student placeholder = Student.builder().name("Brak Partnera").classGrades(null).build();
                teams.add(new Team(s, placeholder));
                matched.add(s);
            }
        }

        teamGrid.setItems(teams);
        if (teams.isEmpty()) {
            Notification.show("Brak studentów do utworzenia zespołów.");
        }
    }

    @Data
    @AllArgsConstructor
    public static class Team {
        private Student member1;
        private Student member2;
    }
}
