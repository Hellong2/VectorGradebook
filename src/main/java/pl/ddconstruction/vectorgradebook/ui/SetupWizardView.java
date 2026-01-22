package pl.ddconstruction.vectorgradebook.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import java.io.FileInputStream;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import pl.ddconstruction.vectorgradebook.model.Class;
import pl.ddconstruction.vectorgradebook.model.ClassType;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.service.CourseService;
import pl.ddconstruction.vectorgradebook.service.VectorProcessingService;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

@Route("setup")
public class SetupWizardView extends VerticalLayout {

    private final CourseService courseService;
    private final VectorProcessingService vectorProcessingService;
    private final VerticalLayout stepLayout = new VerticalLayout();
    private CourseConfig config = new CourseConfig();

    public SetupWizardView(CourseService courseService, VectorProcessingService vectorProcessingService) {
        this.courseService = courseService;
        this.vectorProcessingService = vectorProcessingService;
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H2 title = new H2("Konfiguracja Przedmiotu");
        add(title, stepLayout);

        showStep1();
    }

    private void showStep1() {
        stepLayout.removeAll();
        H4 stepTitle = new H4("Krok 1: Import konfiguracji lub Nowy Przedmiot");

        UploadHandler handler = UploadHandler.toTempFile((event, file) -> {
            try (InputStream inputStream = new FileInputStream(file)) {
                ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
                CourseConfig importedConfig = mapper.readValue(inputStream, CourseConfig.class);

                // Ensure all classes have an ID
                if (importedConfig.getClasses() != null) {
                    importedConfig.getClasses().forEach(c -> {
                        if (c.getId() == null) {
                            c.setId(UUID.randomUUID());
                        }
                    });
                }

                getUI().ifPresent(ui -> ui.access(() -> {
                    courseService.saveConfig(importedConfig);
                    vectorProcessingService.recreateCollection(importedConfig.getClasses().size());
                    Notification.show("Zaimportowano konfigurację!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    navigateToMain();
                }));
            } catch (Exception e) {
                getUI().ifPresent(ui -> ui.access(() -> Notification.show("Błąd importu: " + e.getMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR)));
            }
        });

        Upload upload = new Upload(handler);
        upload.setAcceptedFileTypes(".json");

        Button manualParamsButton = new Button("Konfiguracja Ręczna", e -> showStep2());
        manualParamsButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        stepLayout.add(stepTitle, new H4("Opcja A: Import JSON"), upload, new H4("Opcja B: Ręcznie"),
                manualParamsButton);
        stepLayout.setAlignItems(Alignment.CENTER);
    }

    private void showStep2() {
        stepLayout.removeAll();
        H4 stepTitle = new H4("Krok 2: Nazwa Przedmiotu");

        TextField courseNameField = new TextField("Nazwa przedmiotu");
        courseNameField.setWidth("300px");

        Button nextButton = new Button("Dalej", e -> {
            if (courseNameField.isEmpty()) {
                Notification.show("Podaj nazwę przedmiotu");
                return;
            }
            config.setCourseName(courseNameField.getValue());
            config.setClasses(new ArrayList<>());
            showManageTagsStep();
        });
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        stepLayout.add(stepTitle, courseNameField, nextButton);
    }

    private void showManageTagsStep() {
        stepLayout.removeAll();
        H4 stepTitle = new H4("Krok 2.5: Zdefiniuj Tagi (Obszary tematyczne)");

        Grid<String> tagsGrid = new Grid<>();
        tagsGrid.addColumn(t -> t).setHeader("Tag");
        tagsGrid.setItems(config.getAvailableTags());
        tagsGrid.addComponentColumn(tag -> {
            Button remove = new Button("Usuń", e -> {
                config.getAvailableTags().remove(tag);
                tagsGrid.getDataProvider().refreshAll();
            });
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return remove;
        });

        TextField tagField = new TextField("Nowy Tag");
        Button addTagButton = new Button("Dodaj", e -> {
            if (!tagField.isEmpty()) {
                config.getAvailableTags().add(tagField.getValue());
                tagField.clear();
                tagsGrid.getDataProvider().refreshAll();
            }
        });

        Button nextButton = new Button("Dalej", e -> showStep3());
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        stepLayout.add(stepTitle, new HorizontalLayout(tagField, addTagButton), tagsGrid, nextButton);
        stepLayout.setWidth("600px");
    }

    private void showStep3() {
        stepLayout.removeAll();
        H4 stepTitle = new H4("Krok 3: Definicja Zajęć (Wykłady i Laboratoria)");

        Grid<Class> classGrid = new Grid<>(Class.class);
        classGrid.setItems(config.getClasses());
        classGrid.removeAllColumns();
        classGrid.addColumn(Class::getTopic).setHeader("Temat");
        classGrid.addColumn(Class::getDate).setHeader("Data");
        classGrid.addColumn(aClass -> aClass.getType().getLabel()).setHeader("Typ");
        classGrid.addColumn(aClass -> String.join(", ", aClass.getTags())).setHeader("Tagi");
        classGrid.addComponentColumn(c -> {
            Button remove = new Button("Usuń", e -> {
                config.getClasses().remove(c);
                classGrid.getDataProvider().refreshAll();
            });
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return remove;
        });

        Button addClassButton = new Button("Dodaj Zajęcia", e -> openAddClassDialog(classGrid));
        Button finishButton = new Button("Zakończ i Zapisz", e -> {
            if (config.getClasses().isEmpty()) {
                Notification.show("Dodaj przynajmniej jedne zajęcia");
                return;
            }
            courseService.saveConfig(config);
            vectorProcessingService.recreateCollection(config.getClasses().size());
            Notification.show("Konfiguracja zapisana!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            navigateToMain();
        });
        finishButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        stepLayout.add(stepTitle, classGrid, new HorizontalLayout(addClassButton, finishButton));
        stepLayout.setWidth("600px");
    }

    private void openAddClassDialog(Grid<Class> grid) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Dodaj Zajęcia");

        TextField topicField = new TextField("Temat");
        DatePicker datePicker = new DatePicker("Data");
        ComboBox<ClassType> typeSelect = new ComboBox<>("Typ");
        typeSelect.setItems(ClassType.values());
        typeSelect.setItemLabelGenerator(ClassType::getLabel);

        com.vaadin.flow.component.combobox.MultiSelectComboBox<String> tagsSelect = new com.vaadin.flow.component.combobox.MultiSelectComboBox<>(
                "Tagi");
        tagsSelect.setItems(config.getAvailableTags());

        Button save = new Button("Dodaj", e -> {
            if (topicField.isEmpty() || datePicker.isEmpty() || typeSelect.isEmpty()) {
                Notification.show("Wypełnij wszystkie pola");
                return;
            }
            Class newClass = Class.builder()
                    .id(UUID.randomUUID())
                    .topic(topicField.getValue())
                    .date(datePicker.getValue())
                    .type(typeSelect.getValue())
                    .tags(tagsSelect.getValue())
                    .build();
            config.getClasses().add(newClass);
            grid.getDataProvider().refreshAll();
            dialog.close();
        });

        VerticalLayout layout = new VerticalLayout(topicField, datePicker, typeSelect, tagsSelect, save);
        dialog.add(layout);
        dialog.open();
    }

    private void navigateToMain() {
        getUI().ifPresent(ui -> ui.navigate(""));
    }
}
