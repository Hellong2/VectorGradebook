package pl.ddconstruction.vectorgradebook.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.theme.lumo.LumoUtility;
import pl.ddconstruction.vectorgradebook.model.entity.Course;
import pl.ddconstruction.vectorgradebook.service.CourseService;

public class MainLayout extends AppLayout {

    private final CourseService courseService;
    private H2 viewTitle;

    public MainLayout(CourseService courseService) {
        this.courseService = courseService;

        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }

    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Menu toggle");

        viewTitle = new H2();
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        HorizontalLayout header = new HorizontalLayout(toggle, viewTitle);

        // Course Selector
        ComboBox<Course> courseSelector = new ComboBox<>();
        courseSelector.setItems(courseService.getAllCourses());
        courseSelector.setItemLabelGenerator(Course::getName);
        courseSelector.setPlaceholder("Wybierz kurs...");
        courseSelector.addClassNames(LumoUtility.Margin.Start.AUTO);

        if (!courseService.getAllCourses().isEmpty()) {
            Course current = null;
            Object activeIdObj = com.vaadin.flow.server.VaadinSession.getCurrent().getAttribute("activeCourseId");
            if (activeIdObj != null) {
                java.util.UUID activeId = (java.util.UUID) activeIdObj;
                current = courseService.getAllCourses().stream()
                        .filter(c -> c.getId().equals(activeId))
                        .findFirst()
                        .orElse(null);
            }

            // Auto-select first if nothing selected but courses exist
            if (current == null && !courseService.getAllCourses().isEmpty()) {
                current = courseService.getAllCourses().get(0);
                com.vaadin.flow.server.VaadinSession.getCurrent().setAttribute("activeCourseId", current.getId());
            }

            if (current != null) {
                courseSelector.setValue(current);
            }
        }

        courseSelector.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                // courseService.setActiveCourse(event.getValue().getId()); // Removed stateful
                // call
                com.vaadin.flow.server.VaadinSession.getCurrent().setAttribute("activeCourseId",
                        event.getValue().getId());
                getUI().ifPresent(ui -> ui.getPage().reload());
            }
        });

        Button addCourseBtn = new Button(new Icon(VaadinIcon.PLUS));
        addCourseBtn.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
        addCourseBtn.setTooltipText("Dodaj nowy kurs");
        addCourseBtn.addClickListener(e -> {
            // courseService.clearCurrentConfig(); // Removed stateful call
            com.vaadin.flow.server.VaadinSession.getCurrent().setAttribute("activeCourseId", null);
            getUI().ifPresent(ui -> ui.navigate(SetupWizardView.class));
        });

        header.add(courseSelector, addCourseBtn);

        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.addClassNames(LumoUtility.Padding.Vertical.NONE, LumoUtility.Padding.Horizontal.MEDIUM);

        addToNavbar(true, header);
    }

    private void addDrawerContent() {
        H1 appName = new H1("V-Grade");
        appName.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        com.vaadin.flow.component.html.Header header = new com.vaadin.flow.component.html.Header(appName);
        header.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER,
                LumoUtility.JustifyContent.CENTER);

        Scroller scroller = new Scroller(createNavigation());
        scroller.addClassNames(LumoUtility.Padding.SMALL);

        addToDrawer(header, scroller, createFooter());
    }

    private SideNav createNavigation() {
        SideNav nav = new SideNav();

        // Check session for active course
        boolean hasActiveCourse = com.vaadin.flow.server.VaadinSession.getCurrent()
                .getAttribute("activeCourseId") != null;

        if (hasActiveCourse) {
            nav.addItem(new SideNavItem("Dziennik Ocen", GradeBookView.class, VaadinIcon.TABLE.create()));
            nav.addItem(new SideNavItem("Generator Zespołów", TeamGeneratorView.class, VaadinIcon.USERS.create()));
        } else {
            nav.addItem(new SideNavItem("Konfiguracja", SetupWizardView.class, VaadinIcon.COG.create()));
        }

        return nav;
    }

    private com.vaadin.flow.component.html.Footer createFooter() {
        com.vaadin.flow.component.html.Footer layout = new com.vaadin.flow.component.html.Footer();
        layout.addClassNames(LumoUtility.Padding.MEDIUM);

        boolean hasActiveCourse = com.vaadin.flow.server.VaadinSession.getCurrent()
                .getAttribute("activeCourseId") != null;

        if (hasActiveCourse) {
            // Export button
            Button exportBtn = new Button("Eksportuj Konfigurację", new Icon(VaadinIcon.DOWNLOAD),
                    e -> exportCourseConfig());
            exportBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            exportBtn.setWidthFull();

            Button resetBtn = new Button("Resetuj Dane", new Icon(VaadinIcon.TRASH), e -> showResetConfirmation());
            resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            resetBtn.setWidthFull();

            layout.add(exportBtn, resetBtn);
        }

        return layout;
    }

    private void exportCourseConfig() {
        try {
            // Get all courses and let user pick if multiple, or use current
            var courses = courseService.getAllCourses();
            if (courses.isEmpty()) {
                com.vaadin.flow.component.notification.Notification.show("Brak kursów do eksportu")
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
                return;
            }

            // For now, export the first course (or current config)
            var courseId = courses.get(0).getId();
            var exportConfig = courseService.getExportConfig(courseId);

            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(exportConfig);

            // Use JavaScript to trigger download
            getElement().executeJs(
                    "const blob = new Blob([$0], { type: 'application/json' });" +
                            "const url = URL.createObjectURL(blob);" +
                            "const a = document.createElement('a');" +
                            "a.href = url;" +
                            "a.download = 'course_config.json';" +
                            "a.click();" +
                            "URL.revokeObjectURL(url);",
                    json);

            com.vaadin.flow.component.notification.Notification.show("Konfiguracja wyeksportowana!")
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            com.vaadin.flow.component.notification.Notification.show("Błąd eksportu: " + ex.getMessage())
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
        }
    }

    @Override
    protected void afterNavigation() {
        super.afterNavigation();
        viewTitle.setText(getCurrentPageTitle());
    }

    private String getCurrentPageTitle() {
        PageTitle title = getContent().getClass().getAnnotation(PageTitle.class);
        return title == null ? "" : title.value();
    }

    private void showResetConfirmation() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Reset Konfiguracji");

        VerticalLayout layout = new VerticalLayout(
                new Span("Czy na pewno chcesz zresetować konfigurację?"),
                new Span("To spowoduje usunięcie wszystkich danych studentów!"));
        dialog.add(layout);

        Button confirm = new Button("Resetuj", e -> {
            getUI().ifPresent(ui -> ui.navigate(SetupWizardView.class));
            dialog.close();
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancel = new Button("Anuluj", e -> dialog.close());

        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }
}
