package pl.ddconstruction.vectorgradebook.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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

        if (courseService.isConfigured() && courseService.getCurrentConfig() != null) {
            String courseName = courseService.getCurrentConfig().getCourseName();
            if (courseName != null && !courseName.isEmpty()) {
                Span courseBadge = new Span(courseName);
                courseBadge.getElement().getThemeList().add("badge contrast");
                courseBadge.addClassNames(LumoUtility.Margin.Start.AUTO, LumoUtility.Margin.End.MEDIUM);
                header.add(courseBadge);
            }
        }

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

        if (courseService.isConfigured()) {
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

        if (courseService.isConfigured()) {
            Button resetBtn = new Button("Resetuj Dane", new Icon(VaadinIcon.TRASH), e -> showResetConfirmation());
            resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            resetBtn.setWidthFull();
            layout.add(resetBtn);
        }

        return layout;
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
