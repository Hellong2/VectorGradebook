package pl.ddconstruction.vectorgradebook;

import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Push(PushMode.AUTOMATIC)
@Theme("vector-gradebook")
public class VectorGradebookApplication implements com.vaadin.flow.component.page.AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(VectorGradebookApplication.class, args);
    }

}
