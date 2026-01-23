package pl.ddconstruction.vectorgradebook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.ddconstruction.vectorgradebook.model.entity.Skill;

import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {
    Optional<Skill> findByName(String name);
}
