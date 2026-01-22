package pl.ddconstruction.vectorgradebook.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import pl.ddconstruction.vectorgradebook.model.Class;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.Student;
import pl.ddconstruction.vectorgradebook.service.CourseService;
import pl.ddconstruction.vectorgradebook.service.StudentService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Route(value = "", layout = MainLayout.class)
public class GradeBookView extends VerticalLayout {

    private final StudentService studentService;
    private final CourseService courseService;
    private final Grid<Student> grid = new Grid<>(Student.class);

    // Config needed for dynamic columns
    private CourseConfig config;

    public GradeBookView(StudentService studentService, CourseService courseService) {
        this.studentService = studentService;
        this.courseService = courseService;

        this.config = courseService.getCurrentConfig();

        setSizeFull();

        if (!courseService.isConfigured()) {
            add("Przedmiot nie jest skonfigurowany. Przejdź do konfiguracji.");
            return;
        }

        configureGrid();

        Button addStudentButton = new Button("Dodaj Studenta", e -> openStudentDialog(null));
        addStudentButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(new HorizontalLayout(addStudentButton), grid);
        updateList();
    }

    private void configureGrid() {
        grid.removeAllColumns();
        grid.addColumn(Student::getName).setHeader("Imię").setFrozen(true);

        // Subject Grade Column
        grid.addColumn(student -> String.format("%.2f", studentService.calculateSubjectGrade(student)))
                .setHeader("Ocena Końcowa").setSortable(true);

        // Dynamic Columns for each Class
        config.getClasses().forEach(cls -> {
            grid.addColumn(student -> {
                Double grade = student.getClassGrades().get(cls.getId());
                return grade != null ? grade : "-";
            }).setHeader(cls.getTopic() + " (" + cls.getType().getLabel() + ")");
        });

        // Actions Column
        grid.addComponentColumn(student -> {
            Button editBtn = new Button("Edytuj", e -> openStudentDialog(student));

            Button deleteBtn = new Button("Usuń");
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> {
                studentService.delete(student.getId());
                updateList();
                Notification.show("Usunięto studenta");
            });

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Akcje");

        grid.getColumns().forEach(col -> col.setAutoWidth(true));
    }

    private void openStudentDialog(Student studentToEdit) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(studentToEdit == null ? "Nowy Student" : "Edytuj Studenta");

        TextField nameField = new TextField("Imię i Nazwisko");
        if (studentToEdit != null)
            nameField.setValue(studentToEdit.getName());

        // Dynamic Grade Fields
        Map<UUID, NumberField> gradeFields = new HashMap<>();
        VerticalLayout gradesLayout = new VerticalLayout();

        config.getClasses().forEach(cls -> {
            NumberField field = new NumberField(cls.getTopic() + " - " + cls.getType().getLabel());
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

        VerticalLayout layout = new VerticalLayout(nameField, gradesLayout,
                new HorizontalLayout(saveButton, cancelButton));
        dialog.add(layout);
        dialog.open();
    }

    private void updateList() {
        grid.setItems(studentService.findAll());
    }
}
