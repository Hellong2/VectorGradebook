package pl.ddconstruction.vectorgradebook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.ddconstruction.vectorgradebook.model.entity.ClassEntity;

import java.util.List;
import java.util.UUID;

public interface ClassRepository extends JpaRepository<ClassEntity, UUID> {
    List<ClassEntity> findByCourseId(UUID courseId);
}
