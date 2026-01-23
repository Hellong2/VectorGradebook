package pl.ddconstruction.vectorgradebook.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import lombok.AllArgsConstructor;
import lombok.Data;
import pl.ddconstruction.vectorgradebook.model.Student;
import pl.ddconstruction.vectorgradebook.service.StudentService;
import pl.ddconstruction.vectorgradebook.service.CourseService;
import pl.ddconstruction.vectorgradebook.service.TeamFormationService;

import java.util.List;
import java.util.stream.Collectors;

@Route(value = "teams", layout = MainLayout.class)
@PageTitle("Generator Zespołów")
public class TeamGeneratorView extends VerticalLayout {

    private final StudentService studentService;
    private final TeamFormationService teamFormationService;

    private final Grid<Team> teamGrid = new Grid<>(Team.class);
    private final RadioButtonGroup<String> sizePreference = new RadioButtonGroup<>();

    public TeamGeneratorView(StudentService studentService,
            CourseService courseService,
            TeamFormationService teamFormationService) {
        this.studentService = studentService;
        this.teamFormationService = teamFormationService;

        setSizeFull();
        addClassNames("team-generator-view", LumoUtility.Padding.MEDIUM);

        if (!courseService.isConfigured()) {
            Div emptyState = new Div();
            emptyState.setText("Przedmiot nie jest skonfigurowany. Przejdź do konfiguracji.");
            emptyState.addClassNames(LumoUtility.TextAlignment.CENTER, LumoUtility.TextColor.SECONDARY);
            add(emptyState);
            setAlignItems(Alignment.CENTER);
            setJustifyContentMode(JustifyContentMode.CENTER);
            return;
        }

        Div mainCard = new Div();
        mainCard.addClassName("card");
        mainCard.setSizeFull();
        mainCard.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN);

        H2 title = new H2("Formowanie Zespołów");
        title.addClassNames(LumoUtility.Margin.Top.NONE);

        sizePreference.setLabel("Preferencja rozmiaru zespołu");
        sizePreference.setItems("Pary (2)", "Trójki (3)");
        sizePreference.setValue("Pary (2)");
        sizePreference.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);

        Button generateButton = new Button("Generuj Zespoły", new Icon(VaadinIcon.USERS), e -> generateTeams());
        generateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout controls = new HorizontalLayout(sizePreference, generateButton);
        controls.setAlignItems(Alignment.END); // Align button to bottom with radio group
        controls.setWidthFull();
        controls.addClassNames(LumoUtility.Margin.Bottom.MEDIUM, LumoUtility.Border.BOTTOM,
                LumoUtility.BorderColor.CONTRAST_10);
        controls.setPadding(true);

        configureGrid();
        teamGrid.addClassName("team-grid");

        mainCard.add(title, controls, teamGrid);
        add(mainCard);
    }

    private void configureGrid() {
        teamGrid.removeAllColumns();
        teamGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);

        teamGrid.addColumn(t -> t.getMembers().stream()
                .map(Student::getName)
                .collect(Collectors.joining(", ")))
                .setHeader("Członkowie Zespołu")
                .setAutoWidth(true);

        teamGrid.addColumn(t -> String.format("%.2f", t.getScore()))
                .setHeader("Efektywność")
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private void generateTeams() {
        boolean preferTrios = "Trójki (3)".equals(sizePreference.getValue());
        List<Student> allStudents = studentService.findAll();

        if (allStudents.isEmpty()) {
            Notification.show("Brak studentów do utworzenia zespołów.")
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        List<Team> teams = teamFormationService.generateTeams(allStudents, preferTrios);

        teamGrid.setItems(teams);
        if (teams.isEmpty()) {
            Notification.show("Nie udało się utworzyć zespołów.").addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        } else {
            Notification.show("Wygenerowano " + teams.size() + " zespołów.")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        }
    }

    @Data
    @AllArgsConstructor
    public static class Team {
        private List<Student> members;
        private double score;
    }
}
