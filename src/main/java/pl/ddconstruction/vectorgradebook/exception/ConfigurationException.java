package pl.ddconstruction.vectorgradebook.exception;

public class ConfigurationException extends VectorGradebookException {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
