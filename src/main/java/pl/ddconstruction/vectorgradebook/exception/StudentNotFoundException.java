package pl.ddconstruction.vectorgradebook.exception;

import java.util.UUID;

public class StudentNotFoundException extends VectorGradebookException {
    public StudentNotFoundException(UUID id) {
        super("Student with ID " + id + " not found");
    }

    public StudentNotFoundException(String message) {
        super(message);
    }
}
