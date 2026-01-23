package pl.ddconstruction.vectorgradebook.dto;

import java.util.UUID;

public record GradeDTO(
        UUID id,
        UUID classId,
        UUID studentId,
        Double value) {
}
