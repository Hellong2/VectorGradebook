package pl.ddconstruction.vectorgradebook.exception;

public class VectorGradebookException extends RuntimeException {
    public VectorGradebookException(String message) {
        super(message);
    }

    public VectorGradebookException(String message, Throwable cause) {
        super(message, cause);
    }
}
