package pl.ddconstruction.VectorGradebook.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import lombok.AllArgsConstructor;
import lombok.Data;
import pl.ddconstruction.VectorGradebook.model.Student;
import pl.ddconstruction.VectorGradebook.service.StudentService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Route(value = "teams", layout = MainLayout.class)
public class TeamGeneratorView extends VerticalLayout {

    private final StudentService studentService;
    private final Grid<Team> teamGrid = new Grid<>(Team.class);
    private final Span statsSpan = new Span();

    public TeamGeneratorView(StudentService studentService) {
        this.studentService = studentService;

        setSizeFull();

        Button generateButton = new Button("Generate Teams", e -> generateTeams());
        Button statsButton = new Button("Show Weakest Area", e -> showStats());

        configureGrid();

        add(new H2("Team Formation"), generateButton, statsButton, statsSpan, teamGrid);
    }

    private void showStats() {
        statsSpan.setText("Problematic Area: " + studentService.getProblematicAreaStats());
    }

    private void configureGrid() {
        teamGrid.removeAllColumns();
        teamGrid.addColumn(t -> t.member1.getName()).setHeader("Student 1");
        teamGrid.addColumn(t -> t.member1.getGrades()).setHeader("S1 Grades");
        teamGrid.addColumn(t -> t.member2.getName()).setHeader("Partner");
        teamGrid.addColumn(t -> t.member2.getGrades()).setHeader("Partner Grades");
    }

    private void generateTeams() {
        List<Student> allStudents = studentService.findAll();
        List<Team> teams = new ArrayList<>();
        Set<Student> matched = new HashSet<>();

        for (Student s : allStudents) {
            if (matched.contains(s))
                continue;

            Student partner = studentService.findPartner(s);

            // If partner is already matched, or is self (unlikely if strictly filtered), or
            // null
            if (partner != null && !matched.contains(partner)) {
                teams.add(new Team(s, partner));
                matched.add(s);
                matched.add(partner);
            } else {
                // Determine if we should leave them alone or handle differently.
                // For this requirement, simple pair matching.
                // If partner matches someone else, we might skip or show as unmatched.

                // Let's check: if A's best is B.
                // But B's best might be C.
                // This is a simple greedy approach.
            }
        }

        // Add unmatched
        for (Student s : allStudents) {
            if (!matched.contains(s)) {
                teams.add(new Team(s, Student.builder().name("No Partner Found").grades(null).build()));
                matched.add(s);
            }
        }

        teamGrid.setItems(teams);
    }

    @Data
    @AllArgsConstructor
    public static class Team {
        private Student member1;
        private Student member2;
    }
}
