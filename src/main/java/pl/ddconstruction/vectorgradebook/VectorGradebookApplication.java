package pl.ddconstruction.vectorgradebook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@com.vaadin.flow.theme.Theme("vector-gradebook")
public class VectorGradebookApplication implements com.vaadin.flow.component.page.AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(VectorGradebookApplication.class, args);
    }

}
