package pl.ddconstruction.vectorgradebook.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.theme.lumo.LumoUtility;
import pl.ddconstruction.vectorgradebook.model.Class;
import pl.ddconstruction.vectorgradebook.model.ClassType;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.service.CourseService;
import pl.ddconstruction.vectorgradebook.service.VectorProcessingService;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

@Route("setup")
@PageTitle("Konfiguracja Przedmiotu")
public class SetupWizardView extends VerticalLayout {

    private final CourseService courseService;
    private final VectorProcessingService vectorProcessingService;
    private final VerticalLayout contentLayout = new VerticalLayout();
    private final Div stepIndicator = new Div();
    private CourseConfig config = new CourseConfig();

    public SetupWizardView(CourseService courseService, VectorProcessingService vectorProcessingService) {
        this.courseService = courseService;
        this.vectorProcessingService = vectorProcessingService;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        addClassName("setup-wizard-view");

        Div card = new Div();
        card.addClassName("card");
        card.addClassName("wizard-card");
        card.setWidth("100%");
        card.setMaxWidth("800px");

        H2 title = new H2("Konfiguracja Przedmiotu");
        title.addClassNames(LumoUtility.TextAlignment.CENTER, LumoUtility.Margin.Bottom.LARGE);

        stepIndicator.addClassName("step-indicator");
        updateStepIndicator(1);

        contentLayout.setPadding(false);

        card.add(title, stepIndicator, contentLayout);
        add(card);

        showStep1();
    }

    private void updateStepIndicator(int step) {
        stepIndicator.removeAll();
        for (int i = 1; i <= 3; i++) {
            Div dot = new Div();
            dot.addClassName("step-dot");
            if (i == step) {
                dot.addClassName("active");
            }
            stepIndicator.add(dot);
        }
    }

    private void showStep1() {
        contentLayout.removeAll();
        updateStepIndicator(1);

        H4 stepTitle = new H4("Krok 1: Rozpocznij");
        stepTitle.addClassName(LumoUtility.TextAlignment.CENTER);

        UploadHandler handler = UploadHandler.toTempFile((event, file) -> {
            try (InputStream inputStream = new FileInputStream(file)) {
                ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
                CourseConfig importedConfig = mapper.readValue(inputStream, CourseConfig.class);

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
        upload.setWidthFull();

        Button manualParamsButton = new Button("Konfiguracja Ręczna", new Icon(VaadinIcon.EDIT), e -> showStep2());
        manualParamsButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        manualParamsButton.setWidthFull();

        HorizontalLayout options = new HorizontalLayout();
        options.setWidthFull();
        options.setAlignItems(Alignment.BASELINE);
        options.setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout optionA = new VerticalLayout(new H4("Opcja A: Import JSON"), upload);
        optionA.setAlignItems(Alignment.CENTER);
        optionA.addClassName("card");
        optionA.getStyle().set("box-shadow", "none").set("border", "1px solid var(--lumo-contrast-10pct)");

        VerticalLayout optionB = new VerticalLayout(new H4("Opcja B: Nowy Przedmiot"), manualParamsButton);
        optionB.setAlignItems(Alignment.CENTER);
        optionB.addClassName("card");
        optionB.getStyle().set("box-shadow", "none").set("border", "1px solid var(--lumo-contrast-10pct)");

        options.add(optionA, optionB);

        contentLayout.add(stepTitle, options);
    }

    private void showStep2() {
        contentLayout.removeAll();
        updateStepIndicator(2);

        H4 stepTitle = new H4("Krok 2: Podstawowe Informacje");

        TextField courseNameField = new TextField("Nazwa przedmiotu");
        courseNameField.setWidthFull();
        courseNameField.setPlaceholder("np. Analiza Matematyczna");

        Button nextButton = new Button("Dalej", new Icon(VaadinIcon.ARROW_RIGHT), e -> {
            if (courseNameField.isEmpty()) {
                Notification.show("Podaj nazwę przedmiotu");
                return;
            }
            config.setCourseName(courseNameField.getValue());
            if (config.getClasses() == null)
                config.setClasses(new ArrayList<>());
            showManageTagsStep();
        });
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        nextButton.setIconAfterText(true);

        HorizontalLayout actions = new HorizontalLayout(nextButton);
        actions.setWidthFull();
        actions.setJustifyContentMode(JustifyContentMode.END);

        contentLayout.add(stepTitle, courseNameField, actions);
    }

    private void showManageTagsStep() {
        contentLayout.removeAll();
        updateStepIndicator(2); // Still technically part of step 2 or could be 2.5

        H4 stepTitle = new H4("Krok 2.5: Tagi (Obszary tematyczne)");

        Grid<String> tagsGrid = new Grid<>();
        tagsGrid.addColumn(t -> t).setHeader("Tag");
        tagsGrid.setItems(config.getAvailableTags());
        tagsGrid.addComponentColumn(tag -> {
            Button remove = new Button(new Icon(VaadinIcon.TRASH), e -> {
                config.getAvailableTags().remove(tag);
                tagsGrid.getDataProvider().refreshAll();
            });
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            return remove;
        }).setWidth("80px").setFlexGrow(0);
        tagsGrid.addThemeVariants(com.vaadin.flow.component.grid.GridVariant.LUMO_NO_BORDER,
                com.vaadin.flow.component.grid.GridVariant.LUMO_ROW_STRIPES);
        tagsGrid.setHeight("200px");

        TextField tagField = new TextField();
        tagField.setPlaceholder("Nowy Tag");
        Button addTagButton = new Button("Dodaj", e -> {
            if (!tagField.isEmpty()) {
                config.getAvailableTags().add(tagField.getValue());
                tagField.clear();
                tagsGrid.getDataProvider().refreshAll();
            }
        });

        HorizontalLayout addTagLayout = new HorizontalLayout(tagField, addTagButton);
        addTagLayout.setAlignItems(Alignment.BASELINE);

        Button nextButton = new Button("Dalej", new Icon(VaadinIcon.ARROW_RIGHT), e -> showStep3());
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        nextButton.setIconAfterText(true);

        HorizontalLayout actions = new HorizontalLayout(nextButton);
        actions.setWidthFull();
        actions.setJustifyContentMode(JustifyContentMode.END);

        contentLayout.add(stepTitle, addTagLayout, tagsGrid, actions);
    }

    private void showStep3() {
        contentLayout.removeAll();
        updateStepIndicator(3);

        H4 stepTitle = new H4("Krok 3: Harmonogram Zajęć");

        Grid<Class> classGrid = new Grid<>(Class.class);
        classGrid.setItems(config.getClasses());
        classGrid.removeAllColumns();
        classGrid.addColumn(Class::getTopic).setHeader("Temat").setAutoWidth(true);
        classGrid.addColumn(Class::getDate).setHeader("Data").setAutoWidth(true);
        classGrid.addColumn(aClass -> aClass.getType().getLabel()).setHeader("Typ").setAutoWidth(true);
        classGrid.addColumn(aClass -> String.join(", ", aClass.getTags())).setHeader("Tagi");
        classGrid.addComponentColumn(c -> {
            Button remove = new Button(new Icon(VaadinIcon.TRASH), e -> {
                config.getClasses().remove(c);
                classGrid.getDataProvider().refreshAll();
            });
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            return remove;
        }).setWidth("80px").setFlexGrow(0);

        classGrid.addThemeVariants(com.vaadin.flow.component.grid.GridVariant.LUMO_NO_BORDER,
                com.vaadin.flow.component.grid.GridVariant.LUMO_ROW_STRIPES);
        classGrid.setHeight("300px");

        Button addClassButton = new Button("Dodaj Zajęcia", new Icon(VaadinIcon.PLUS),
                e -> openAddClassDialog(classGrid));

        Button finishButton = new Button("Zakończ Konfigurację", e -> {
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

        HorizontalLayout toolbar = new HorizontalLayout(addClassButton);
        toolbar.setWidthFull();

        HorizontalLayout actions = new HorizontalLayout(finishButton);
        actions.setWidthFull();
        actions.setJustifyContentMode(JustifyContentMode.END);

        contentLayout.add(stepTitle, toolbar, classGrid, actions);
    }

    private void openAddClassDialog(Grid<Class> grid) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Dodaj Zajęcia");

        TextField topicField = new TextField("Temat");
        topicField.setWidthFull();
        DatePicker datePicker = new DatePicker("Data");
        datePicker.setWidthFull();
        ComboBox<ClassType> typeSelect = new ComboBox<>("Typ");
        typeSelect.setItems(ClassType.values());
        typeSelect.setItemLabelGenerator(ClassType::getLabel);
        typeSelect.setWidthFull();

        com.vaadin.flow.component.combobox.MultiSelectComboBox<String> tagsSelect = new com.vaadin.flow.component.combobox.MultiSelectComboBox<>(
                "Tagi");
        tagsSelect.setItems(config.getAvailableTags());
        tagsSelect.setWidthFull();

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
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Anuluj", e -> dialog.close());

        VerticalLayout layout = new VerticalLayout(topicField, datePicker, typeSelect, tagsSelect);
        dialog.add(layout);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void navigateToMain() {
        getUI().ifPresent(ui -> ui.navigate(""));
    }
}
