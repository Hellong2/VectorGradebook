package pl.ddconstruction.vectorgradebook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import pl.ddconstruction.vectorgradebook.model.ClassType;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "classes") // Class is a reserved keyword in SQL usually, better safe
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ClassEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @EqualsAndHashCode.Include
    private String topic;
    @EqualsAndHashCode.Include
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @EqualsAndHashCode.Include
    private ClassType type;

    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    @ToString.Exclude
    private Course course;

    @ManyToMany
    @JoinTable(name = "class_skill", joinColumns = @JoinColumn(name = "class_id"), inverseJoinColumns = @JoinColumn(name = "skill_id"))
    @Builder.Default
    @ToString.Exclude
    private Set<Skill> skills = new HashSet<>();

    // Helper to keep compatibility with existing string-based skills if needed,
    // or we just rely on the new Skill entity.
    // The previous code had Set<String> skills.
    // I should probably check where it is used.
    // It was used in VectorProcessingService to map grades to skills.
}
