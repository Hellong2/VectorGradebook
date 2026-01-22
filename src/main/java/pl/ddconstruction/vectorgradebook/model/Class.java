package pl.ddconstruction.vectorgradebook.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Class {
    private UUID id;
    private String topic;
    private LocalDate date;
    private ClassType type;
    @Builder.Default
    private java.util.Set<String> tags = new java.util.HashSet<>();
}
