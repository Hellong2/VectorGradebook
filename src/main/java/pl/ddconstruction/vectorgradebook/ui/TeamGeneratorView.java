package pl.ddconstruction.vectorgradebook.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.router.Route;
import lombok.AllArgsConstructor;
import lombok.Data;
import pl.ddconstruction.vectorgradebook.model.Student;
import pl.ddconstruction.vectorgradebook.service.StudentService;
import pl.ddconstruction.vectorgradebook.service.CourseService;
import pl.ddconstruction.vectorgradebook.service.TeamFormationService;

import java.util.List;
import java.util.stream.Collectors;

@Route(value = "teams", layout = MainLayout.class)
public class TeamGeneratorView extends VerticalLayout {

    private final StudentService studentService;
    private final CourseService courseService;
    private final TeamFormationService teamFormationService;

    private final Grid<Team> teamGrid = new Grid<>(Team.class);
    private final Span statsSpan = new Span();
    private final RadioButtonGroup<String> sizePreference = new RadioButtonGroup<>();

    public TeamGeneratorView(StudentService studentService,
            CourseService courseService,
            TeamFormationService teamFormationService) {
        this.studentService = studentService;
        this.courseService = courseService;
        this.teamFormationService = teamFormationService;

        setSizeFull();

        if (!courseService.isConfigured()) {
            add("Przedmiot nie jest skonfigurowany.");
            return;
        }

        sizePreference.setLabel("Preferencja rozmiaru zespołu");
        sizePreference.setItems("Pary (2)", "Trójki (3)");
        sizePreference.setValue("Pary (2)");

        Button generateButton = new Button("Generuj Zespoły", e -> generateTeams());
        generateButton.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);

        Button statsButton = new Button("Pokaż Najsłabszy Obszar", e -> showStats());

        configureGrid();

        add(new H2("Formowanie Zespołów"),
                new HorizontalLayout(sizePreference, generateButton, statsButton),
                statsSpan,
                teamGrid);
    }

    private void showStats() {
        String stats = studentService.getProblematicAreaStats();
        statsSpan.setText("Najsłabszy Obszar Klasy: " + stats);
        statsSpan.getElement().getStyle().set("font-weight", "bold");
    }

    private void configureGrid() {
        teamGrid.removeAllColumns();
        teamGrid.addColumn(t -> t.getMembers().stream()
                .map(Student::getName)
                .collect(Collectors.joining(", ")))
                .setHeader("Członkowie Zespołu")
                .setAutoWidth(true);

        teamGrid.addColumn(t -> String.format("%.2f", t.getScore()))
                .setHeader("Przewidywana Efektywność")
                .setSortable(true);
    }

    private void generateTeams() {
        boolean preferTrios = "Trójki (3)".equals(sizePreference.getValue());
        List<Student> allStudents = studentService.findAll();

        List<Team> teams = teamFormationService.generateTeams(allStudents, preferTrios);

        teamGrid.setItems(teams);
        if (teams.isEmpty()) {
            Notification.show("Brak studentów do utworzenia zespołów.");
        }
    }

    @Data
    @AllArgsConstructor
    public static class Team {
        private List<Student> members;
        private double score;
    }
}
