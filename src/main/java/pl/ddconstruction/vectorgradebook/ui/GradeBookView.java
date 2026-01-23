package pl.ddconstruction.vectorgradebook.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.Student;
import pl.ddconstruction.vectorgradebook.service.CourseService;
import pl.ddconstruction.vectorgradebook.service.StudentService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Dziennik Ocen")
public class GradeBookView extends VerticalLayout {

    private final StudentService studentService;
    private final Grid<Student> grid = new Grid<>(Student.class);
    private GridListDataView<Student> dataView;

    // Config needed for dynamic columns
    private CourseConfig config;

    public GradeBookView(StudentService studentService, CourseService courseService) {
        this.studentService = studentService;

        this.config = courseService.getCurrentConfig();

        setSizeFull();
        addClassNames("gradebook-view", LumoUtility.Padding.MEDIUM);

        if (!courseService.isConfigured()) {
            Div emptyState = new Div();
            emptyState.setText("Przedmiot nie jest skonfigurowany. Przejdź do konfiguracji.");
            emptyState.addClassNames(LumoUtility.TextAlignment.CENTER, LumoUtility.TextColor.SECONDARY);
            add(emptyState);
            setAlignItems(Alignment.CENTER);
            setJustifyContentMode(JustifyContentMode.CENTER);
            return;
        }

        configureGrid();

        Div statsCard = createStatsCard();

        Div card = new Div();
        card.addClassName("card");
        card.addClassName("gradebook-card");
        card.setSizeFull();

        HorizontalLayout toolbar = createToolbar();

        card.add(toolbar, statsCard, grid);
        add(card);

        updateList();
    }

    private final Div statsCard = new Div();
    private final Span statsSpan = new Span();

    private Div createStatsCard() {
        statsCard.setVisible(false);
        statsCard.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.Background.CONTRAST_5,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Margin.Bottom.MEDIUM);
        statsCard.add(statsSpan);
        return statsCard;
    }

    private HorizontalLayout createToolbar() {
        TextField searchField = new TextField();
        searchField.setPlaceholder("Szukaj studenta...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> {
            if (dataView != null) {
                dataView.setFilter(student -> {
                    String searchTerm = searchField.getValue().trim();
                    if (searchTerm.isEmpty())
                        return true;
                    return student.getName().toLowerCase().contains(searchTerm.toLowerCase());
                });
            }
        });
        searchField.setWidth("300px");

        Button statsButton = new Button("Analiza Obszarów", new Icon(VaadinIcon.CHART), e -> showStats());
        statsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button addStudentButton = new Button("Dodaj Studenta", new Icon(VaadinIcon.PLUS), e -> openStudentDialog(null));
        addStudentButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout actions = new HorizontalLayout(statsButton, addStudentButton);

        HorizontalLayout toolbar = new HorizontalLayout(searchField, actions);
        toolbar.setWidthFull();
        toolbar.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        return toolbar;
    }

    private void showStats() {
        try {
            // Toggle visibility if already showing
            if (statsCard.isVisible()) {
                statsCard.setVisible(false);
                return;
            }

            String stats = studentService.getProblematicAreaStats();
            statsSpan.removeAll();

            Icon icon = VaadinIcon.WARNING.create();
            icon.addClassNames(LumoUtility.TextColor.ERROR, LumoUtility.Margin.Right.SMALL);

            Span label = new Span("Najsłabszy Obszar Klasy:");
            label.addClassNames(LumoUtility.FontWeight.BOLD);

            Span value = new Span(stats);
            value.addClassNames(LumoUtility.Margin.Left.SMALL);

            statsSpan.add(icon, label, value);
            statsCard.setVisible(true);
        } catch (Exception e) {
            Notification.show(e.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.removeAllColumns();

        grid.addColumn(Student::getName).setHeader("Student").setFrozen(true).setAutoWidth(true).setFlexGrow(0);

        // Subject Grade Column
        grid.addColumn(student -> {
            return String.format("%.2f", studentService.calculateSubjectGrade(student));
        }).setHeader("Ocena Końcowa").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        // Dynamic Columns for each Class
        config.getClasses().forEach(cls -> {
            grid.addColumn(student -> {
                Double grade = student.getClassGrades().get(cls.getId());
                return grade != null ? String.valueOf(grade) : "-";
            }).setHeader(cls.getTopic() + " (" + cls.getType().getLabel() + ")").setAutoWidth(true);
        });

        // Actions Column
        grid.addComponentColumn(student -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openStudentDialog(student));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.setTooltipText("Edytuj");

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.setTooltipText("Usuń");
            deleteBtn.addClickListener(e -> {
                studentService.delete(student.getId());
                updateList();
                Notification.show("Usunięto studenta").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Akcje").setAutoWidth(true).setFlexGrow(0).setFrozenToEnd(true);
    }

    private void openStudentDialog(Student studentToEdit) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(studentToEdit == null ? "Nowy Student" : "Edytuj Studenta");

        TextField nameField = new TextField("Imię i Nazwisko");
        nameField.setWidthFull();
        if (studentToEdit != null)
            nameField.setValue(studentToEdit.getName());

        // Dynamic Grade Fields
        Map<UUID, NumberField> gradeFields = new HashMap<>();
        VerticalLayout gradesLayout = new VerticalLayout();
        gradesLayout.setPadding(false);
        gradesLayout.setSpacing(true);

        config.getClasses().forEach(cls -> {
            NumberField field = new NumberField(cls.getTopic() + " (" + cls.getType().getLabel() + ")");
            field.setWidthFull();
            field.setMin(2.0);
            field.setMax(5.0);
            field.setStep(0.5);
            field.setStepButtonsVisible(true);

            if (studentToEdit != null) {
                Double g = studentToEdit.getClassGrades().get(cls.getId());
                if (g != null)
                    field.setValue(g);
            }

            gradeFields.put(cls.getId(), field);
            gradesLayout.add(field);
        });

        Scroller scroller = new Scroller(gradesLayout);
        scroller.setMaxHeight("400px");
        scroller.setWidthFull();

        Button saveButton = new Button("Zapisz", e -> {
            if (nameField.isEmpty()) {
                Notification.show("Imię jest wymagane");
                return;
            }

            Map<UUID, Double> grades = new HashMap<>();
            gradeFields.forEach((id, field) -> {
                if (field.getValue() != null) {
                    grades.put(id, field.getValue());
                }
            });

            Student student;
            if (studentToEdit != null) {
                student = studentToEdit;
                student.setName(nameField.getValue());
                student.setClassGrades(grades);
            } else {
                student = Student.builder()
                        .name(nameField.getValue())
                        .classGrades(grades)
                        .build();
            }

            studentService.save(student);
            updateList();
            dialog.close();
            Notification.show("Zapisano!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button("Anuluj", e -> dialog.close());

        VerticalLayout layout = new VerticalLayout(nameField, new H4("Oceny"), scroller);
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setWidth("400px");

        dialog.add(layout);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void updateList() {
        dataView = grid.setItems(studentService.findAll());
    }
}
