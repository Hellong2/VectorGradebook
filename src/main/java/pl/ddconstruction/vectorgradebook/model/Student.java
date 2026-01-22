package pl.ddconstruction.vectorgradebook.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Student {
    private UUID id;
    private String name;

    @Builder.Default
    private Map<UUID, Double> classGrades = new HashMap<>();
}
