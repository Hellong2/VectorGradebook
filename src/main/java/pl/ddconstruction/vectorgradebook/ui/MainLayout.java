package pl.ddconstruction.vectorgradebook.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import pl.ddconstruction.vectorgradebook.service.CourseService;

public class MainLayout extends AppLayout {

    private final CourseService courseService;

    public MainLayout(CourseService courseService) {
        this.courseService = courseService;
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        H1 logo = new H1("Dziennik Ocen Wektorowych");
        logo.addClassNames("text-l", "m-m");

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), logo);

        if (courseService.isConfigured() && courseService.getCurrentConfig() != null) {
            String courseName = courseService.getCurrentConfig().getCourseName();
            if (courseName != null && !courseName.isEmpty()) {
                Span courseNameSpan = new Span(courseName);
                courseNameSpan.getStyle().set("margin-left", "auto");
                courseNameSpan.getStyle().set("margin-right", "1em");
                courseNameSpan.getStyle().set("font-weight", "bold");
                header.add(courseNameSpan);
            }
        }

        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidth("100%");
        header.addClassNames("py-0", "px-m");

        addToNavbar(header);
    }

    private void createDrawer() {
        VerticalLayout list = new VerticalLayout();
        list.setHeightFull(); // Allow list to take full height for positioning

        if (courseService.isConfigured()) {
            list.add(new RouterLink("Dziennik Ocen", GradeBookView.class));
            list.add(new RouterLink("Generator Zespołów", TeamGeneratorView.class));
        }

        // Spacer to push the reset button to the bottom
        Div spacer = new Div();
        list.add(spacer);
        list.setFlexGrow(1, spacer);

        // Reset configuration button with confirmation
        Button resetBtn = new Button("Konfiguracja (Reset)", new Icon(VaadinIcon.COG));
        resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        resetBtn.setWidthFull();
        resetBtn.addClickListener(e -> showResetConfirmation());

        list.add(resetBtn);

        addToDrawer(new Scroller(list));
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
