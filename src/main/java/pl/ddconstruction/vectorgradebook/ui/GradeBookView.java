package pl.ddconstruction.VectorGradebook.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import pl.ddconstruction.VectorGradebook.model.Student;
import pl.ddconstruction.VectorGradebook.service.StudentService;

import java.util.HashMap;
import java.util.Map;

@Route(value = "", layout = MainLayout.class)
public class GradeBookView extends VerticalLayout {

    private final StudentService studentService;
    private final Grid<Student> grid = new Grid<>(Student.class);

    private final TextField nameField = new TextField("Name");
    private final NumberField algoField = new NumberField("Algorithms");
    private final NumberField dbField = new NumberField("Databases");
    private final NumberField javaField = new NumberField("Java");
    private final NumberField testingField = new NumberField("Testing");

    public GradeBookView(StudentService studentService) {
        this.studentService = studentService;

        setSizeFull();
        configureGrid();
        configureForm();

        add(new HorizontalLayout(grid, createFormLayout()));
        updateList();
    }

    private void configureGrid() {
        grid.setColumns("name");
        grid.addColumn(student -> student.getGrades().get("Algorithms")).setHeader("Algorithms");
        grid.addColumn(student -> student.getGrades().get("Databases")).setHeader("Databases");
        grid.addColumn(student -> student.getGrades().get("Java")).setHeader("Java");
        grid.addColumn(student -> student.getGrades().get("Testing")).setHeader("Testing");
        grid.getColumns().forEach(col -> col.setAutoWidth(true));
    }

    private VerticalLayout createFormLayout() {
        Button saveButton = new Button("Save", e -> saveStudent());

        // Configure number fields
        configNumberField(algoField);
        configNumberField(dbField);
        configNumberField(javaField);
        configNumberField(testingField);

        VerticalLayout form = new VerticalLayout(
                nameField,
                algoField, dbField, javaField, testingField,
                saveButton);
        form.setWidth("300px");
        return form;
    }

    private void configNumberField(NumberField field) {
        field.setMin(2.0);
        field.setMax(5.0);
        field.setStep(0.5);
    }

    private void configureForm() {
        // Additional form config if needed
    }

    private void saveStudent() {
        if (nameField.isEmpty()) {
            Notification.show("Name is required");
            return;
        }

        Map<String, Double> grades = new HashMap<>();
        grades.put("Algorithms", algoField.getValue() != null ? algoField.getValue() : 2.0);
        grades.put("Databases", dbField.getValue() != null ? dbField.getValue() : 2.0);
        grades.put("Java", javaField.getValue() != null ? javaField.getValue() : 2.0);
        grades.put("Testing", testingField.getValue() != null ? testingField.getValue() : 2.0);

        Student student = Student.builder()
                .name(nameField.getValue())
                .grades(grades)
                .build();

        studentService.save(student);
        updateList();
        clearForm();
        Notification.show("Student saved and vector updated!");
    }

    private void updateList() {
        grid.setItems(studentService.findAll());
    }

    private void clearForm() {
        nameField.clear();
        algoField.clear();
        dbField.clear();
        javaField.clear();
        testingField.clear();
    }
}
