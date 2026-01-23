package pl.ddconstruction.vectorgradebook.dto;

import java.util.Map;
import java.util.UUID;

public record StudentDTO(
        UUID id,
        String name,
        Map<UUID, Double> classGrades // classId -> gradeValue (flattened for UI)
) {
}
