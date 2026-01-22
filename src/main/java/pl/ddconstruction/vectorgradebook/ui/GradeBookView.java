package pl.ddconstruction.vectorgradebook.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.notification.NotificationVariant;
import pl.ddconstruction.vectorgradebook.model.Student;
import pl.ddconstruction.vectorgradebook.service.StudentService;

import java.util.HashMap;
import java.util.Map;

@Route(value = "", layout = MainLayout.class)
public class GradeBookView extends VerticalLayout {

    private final StudentService studentService;
    private final Grid<Student> grid = new Grid<>(Student.class);
    private Student currentStudent; // Selected student for editing
    private final Button saveButton = new Button("Zapisz", e -> saveStudent());
    private final Button cancelButton = new Button("Anuluj", e -> clearForm());

    private final TextField nameField = new TextField("Imię");
    private final NumberField algoField = new NumberField("Algorytmy");
    private final NumberField dbField = new NumberField("Bazy Danych");
    private final NumberField javaField = new NumberField("Java");
    private final NumberField testingField = new NumberField("Testowanie");

    public GradeBookView(StudentService studentService) {
        this.studentService = studentService;

        setSizeFull();
        configureGrid();

        HorizontalLayout content = new HorizontalLayout(grid, createFormLayout());
        content.setSizeFull();
        content.setFlexGrow(2, grid);
        content.setFlexGrow(1, content.getComponentAt(1));

        add(content);
        updateList();
    }

    private void configureGrid() {
        grid.removeAllColumns();
        grid.addColumn(Student::getName).setHeader("Imię");
        grid.addColumn(student -> student.getGrades().get("Algorithms")).setHeader("Algorytmy");
        grid.addColumn(student -> student.getGrades().get("Databases")).setHeader("Bazy Danych");
        grid.addColumn(student -> student.getGrades().get("Java")).setHeader("Java");
        grid.addColumn(student -> student.getGrades().get("Testing")).setHeader("Testowanie");
        grid.addComponentColumn(student -> {
            Button deleteBtn = new Button("Usuń");
            deleteBtn.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> {
                studentService.delete(student.getId());
                updateList();
                Notification.show("Usunięto studenta");
                if (currentStudent != null && currentStudent.getId().equals(student.getId())) {
                    clearForm();
                }
            });
            return deleteBtn;
        }).setHeader("Akcje");

        grid.getColumns().forEach(col -> col.setAutoWidth(true));

        grid.asSingleSelect().addValueChangeListener(event -> editStudent(event.getValue()));
    }

    private void editStudent(Student student) {
        if (student == null) {
            clearForm();
        } else {
            currentStudent = student;
            nameField.setValue(student.getName());
            algoField.setValue(student.getGrades().getOrDefault("Algorithms", 2.0));
            dbField.setValue(student.getGrades().getOrDefault("Databases", 2.0));
            javaField.setValue(student.getGrades().getOrDefault("Java", 2.0));
            testingField.setValue(student.getGrades().getOrDefault("Testing", 2.0));
            saveButton.setText("Zaktualizuj");
        }
    }

    private VerticalLayout createFormLayout() {
        saveButton.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);
        cancelButton.addClickListener(e -> clearForm());

        // Configure number fields
        configNumberField(algoField);
        configNumberField(dbField);
        configNumberField(javaField);
        configNumberField(testingField);

        FormLayout formLayout = new FormLayout();
        formLayout.add(nameField, algoField, dbField, javaField, testingField);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1));

        VerticalLayout formWrapper = new VerticalLayout(formLayout, new HorizontalLayout(saveButton, cancelButton));

        formWrapper.setSpacing(true);
        formWrapper.setPadding(true);
        formWrapper.setWidth("350px");
        return formWrapper;
    }

    private void configNumberField(NumberField field) {
        field.setMin(2.0);
        field.setMax(5.0);
        field.setStep(0.5);
        field.setStepButtonsVisible(true);
    }

    private void saveStudent() {
        if (nameField.isEmpty()) {
            Notification.show("Imię jest wymagane").addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        Map<String, Double> grades = new HashMap<>();
        grades.put("Algorithms", algoField.getValue() != null ? algoField.getValue() : 2.0);
        grades.put("Databases", dbField.getValue() != null ? dbField.getValue() : 2.0);
        grades.put("Java", javaField.getValue() != null ? javaField.getValue() : 2.0);
        grades.put("Testing", testingField.getValue() != null ? testingField.getValue() : 2.0);

        Student student;
        if (currentStudent != null) {
            // Update mode
            student = currentStudent;
            student.setName(nameField.getValue());
            student.setGrades(grades);
        } else {
            // Create mode
            student = Student.builder()
                    .name(nameField.getValue())
                    .grades(grades)
                    .build();
        }

        studentService.save(student);
        updateList();
        clearForm();
        Notification.show("Zapisano studenta i zaktualizowano wektor!")
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void updateList() {
        grid.setItems(studentService.findAll());
    }

    private void clearForm() {
        currentStudent = null;
        grid.asSingleSelect().clear();
        saveButton.setText("Zapisz");
        nameField.clear();
        algoField.clear();
        dbField.clear();
        javaField.clear();
        testingField.clear();
    }
}
