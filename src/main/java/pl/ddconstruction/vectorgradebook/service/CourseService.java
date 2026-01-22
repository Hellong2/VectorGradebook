package pl.ddconstruction.vectorgradebook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.ddconstruction.vectorgradebook.exception.ConfigurationException;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;

import java.io.File;
import java.io.IOException;

@Service
public class CourseService {

    private final ObjectMapper objectMapper;
    private final File configFile;

    @Getter
    private CourseConfig currentConfig;

    public CourseService(
            @Value("${app.course-config.path:course_config.json}") String configPath) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.configFile = new File(configPath);
        loadConfig();
    }

    public boolean isConfigured() {
        return currentConfig != null && currentConfig.getCourseName() != null && !currentConfig.getClasses().isEmpty();
    }

    public void saveConfig(CourseConfig config) {
        try {
            objectMapper.writeValue(configFile, config);
            this.currentConfig = config;
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Failed to save course configuration", e);
        }
    }

    private void loadConfig() {
        if (configFile.exists()) {
            try {
                this.currentConfig = objectMapper.readValue(configFile, CourseConfig.class);
            } catch (IOException e) {
                // Log error or just start empty
                this.currentConfig = new CourseConfig();
            }
        } else {
            this.currentConfig = new CourseConfig();
        }
    }

}
