package pl.ddconstruction.vectorgradebook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.ddconstruction.vectorgradebook.model.entity.Course;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    Optional<Course> findByName(String name);
}
