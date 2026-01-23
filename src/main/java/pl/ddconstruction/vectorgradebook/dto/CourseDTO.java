package pl.ddconstruction.vectorgradebook.dto;

import java.util.List;
import java.util.UUID;

public record CourseDTO(
        UUID id,
        String name,
        String description,
        List<ClassDTO> classes) {
}
