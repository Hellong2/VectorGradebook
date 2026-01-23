package pl.ddconstruction.vectorgradebook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.ddconstruction.vectorgradebook.model.entity.Grade;

import java.util.UUID;

public interface GradeRepository extends JpaRepository<Grade, UUID> {
}
