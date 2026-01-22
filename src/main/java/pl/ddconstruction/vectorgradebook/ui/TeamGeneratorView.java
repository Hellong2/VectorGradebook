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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.stream.Collectors;

@Route(value = "teams", layout = MainLayout.class)
public class TeamGeneratorView extends VerticalLayout {

    private final StudentService studentService;
    private final Grid<Team> teamGrid = new Grid<>(Team.class);
    private final Span statsSpan = new Span();

    public TeamGeneratorView(StudentService studentService) {
        this.studentService = studentService;

        setSizeFull();

        Button generateButton = new Button("Generuj Zespoły", e -> generateTeams());
        generateButton.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);

        Button statsButton = new Button("Pokaż Najsłabszy Obszar", e -> showStats());

        configureGrid();

        add(new H2("Formowanie Zespołów"), new HorizontalLayout(generateButton, statsButton), statsSpan, teamGrid);
    }

    private void showStats() {
        // Translate stats if possible, or just show the topic
        String stats = studentService.getProblematicAreaStats();
        // Simple translation mappings for topics could be added, but preserving English
        // topic names is likely fine for "Algorithms" etc.
        // Maybe just the label "Problematic Area".
        statsSpan.setText("Najsłabszy Obszar Klasy: " + stats);
        statsSpan.getElement().getStyle().set("font-weight", "bold");
    }

    private void configureGrid() {
        teamGrid.removeAllColumns();
        teamGrid.addColumn(t -> t.member1.getName()).setHeader("Student 1");
        teamGrid.addColumn(t -> formatGrades(t.member1.getGrades())).setHeader("Oceny S1").setAutoWidth(true);
        teamGrid.addColumn(t -> t.member2.getName()).setHeader("Partner");
        teamGrid.addColumn(t -> t.member2 != null ? formatGrades(t.member2.getGrades()) : "-")
                .setHeader("Oceny Partnera").setAutoWidth(true);
    }

    private String formatGrades(java.util.Map<String, Double> grades) {
        if (grades == null)
            return "";
        // Format as: Algo: 5.0, DB: 3.0...
        return grades.entrySet().stream()
                .map(e -> String.format("%s: %.1f", mapTopicToShort(e.getKey()), e.getValue()))
                .collect(Collectors.joining(", "));
    }

    private String mapTopicToShort(String topic) {
        // Optional: Shorten or translate
        switch (topic) {
            case "Algorithms":
                return "Alg";
            case "Databases":
                return "BD";
            case "Java":
                return "Java";
            case "Testing":
                return "Test";
            default:
                return topic;
        }
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
                Student placeholder = Student.builder().name("Brak Partnera").grades(null).build();
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
