package pl.ddconstruction.VectorGradebook.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Student {
    private UUID id;
    private String name;
    // Keys: "Algorithms", "Databases", "Java", "Testing"
    private Map<String, Double> grades;
}
