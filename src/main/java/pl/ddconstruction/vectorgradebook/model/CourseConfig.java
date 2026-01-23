package pl.ddconstruction.vectorgradebook.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseConfig {
    private String courseName;
    @Builder.Default
    private List<Class> classes = new ArrayList<>();
    @Builder.Default
    private Set<String> availableSkills = new HashSet<>();
}
