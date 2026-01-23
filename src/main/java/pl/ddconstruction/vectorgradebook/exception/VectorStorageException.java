package pl.ddconstruction.vectorgradebook.exception;

public class VectorStorageException extends VectorGradebookException {
    public VectorStorageException(String message) {
        super(message);
    }

    public VectorStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
