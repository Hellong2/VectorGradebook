package pl.ddconstruction.vectorgradebook.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseConfig {
    private String courseName;
    @Builder.Default
    private List<Class> classes = new ArrayList<>();
    @Builder.Default
    private java.util.Set<String> availableTags = new java.util.HashSet<>();
}
