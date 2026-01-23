package pl.ddconstruction.vectorgradebook.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Course {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @EqualsAndHashCode.Include
        private UUID id;

        private String name;

        @Column(length = 1000)
        private String description;

        @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
        @Builder.Default
        private Set<Student> students = new HashSet<>();

        @ManyToMany
        @JoinTable(name = "course_skill", joinColumns = @JoinColumn(name = "course_id"), inverseJoinColumns = @JoinColumn(name = "skill_id"))
        @Builder.Default
        @ToString.Exclude
        private Set<Skill> skills = new HashSet<>();

        @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
        @Builder.Default
        @ToString.Exclude
        private Set<ClassEntity> classEntities = new HashSet<>();
}
