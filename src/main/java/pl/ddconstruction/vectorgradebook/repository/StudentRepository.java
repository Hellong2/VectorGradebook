package pl.ddconstruction.vectorgradebook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.ddconstruction.vectorgradebook.model.entity.Student;

import java.util.List;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {
    List<Student> findByCourseId(UUID courseId);
}
