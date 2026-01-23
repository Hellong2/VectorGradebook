package pl.ddconstruction.vectorgradebook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ddconstruction.vectorgradebook.dto.ClassDTO;
import pl.ddconstruction.vectorgradebook.dto.SkillDTO;
import pl.ddconstruction.vectorgradebook.exception.ConfigurationException;
import pl.ddconstruction.vectorgradebook.model.entity.ClassEntity;
import pl.ddconstruction.vectorgradebook.model.entity.Course;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.entity.Skill;
import pl.ddconstruction.vectorgradebook.repository.ClassRepository;
import pl.ddconstruction.vectorgradebook.repository.CourseRepository;
import pl.ddconstruction.vectorgradebook.repository.SkillRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final SkillRepository skillRepository;
    private final ClassRepository classRepository;
    private final EntityMapper entityMapper;
    private final VectorProcessingService vectorService;

    @Transactional
    public UUID saveConfig(CourseConfig config) {
        Course course = new Course();
        course.setName(config.getCourseName());

        Set<Skill> courseSkills = loadOrCreateSkills(config.getAvailableSkills());
        course.setSkills(courseSkills);

        Set<ClassEntity> newClasses = new HashSet<>();
        if (config.getClasses() != null) {
            for (ClassDTO clsDTO : config.getClasses()) {
                ClassEntity cls = new ClassEntity();
                cls.setTopic(clsDTO.topic());
                cls.setDate(clsDTO.date());
                cls.setType(clsDTO.type());

                Set<Skill> classSkills = loadOrCreateSkills(
                        clsDTO.skills().stream().map(SkillDTO::name).collect(Collectors.toSet()));
                cls.setSkills(classSkills);

                cls.setCourse(course);
                newClasses.add(cls);
            }
            course.getClassEntities().addAll(newClasses);
        }

        Course savedCourse = courseRepository.save(course);

        if (config.getClasses() != null && !config.getClasses().isEmpty()) {
            vectorService.recreateCollection(savedCourse.getId(), config.getClasses().size());
        }

        return savedCourse.getId();
    }

    private Set<Skill> loadOrCreateSkills(Set<String> skillNames) {
        if (skillNames == null)
            return new HashSet<>();

        return skillNames.stream()
                .map(name -> skillRepository.findByName(name)
                        .orElseGet(() -> skillRepository.save(Skill.builder().name(name).build())))
                .collect(Collectors.toSet());
    }

    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    @Transactional
    public CourseConfig getCourseConfig(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ConfigurationException("Course not found"));

        CourseConfig config = new CourseConfig();
        config.setCourseName(course.getName());
        config.setAvailableSkills(course.getSkills().stream().map(Skill::getName).collect(Collectors.toSet()));

        List<ClassDTO> classDTOs = course.getClassEntities().stream()
                .map(entityMapper::toDTO)
                .collect(Collectors.toList());
        config.setClasses(classDTOs);

        return config;
    }

    @Transactional
    public CourseConfig getExportConfig(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ConfigurationException("Course not found"));

        CourseConfig exportConfig = new CourseConfig();
        exportConfig.setCourseName(course.getName());
        exportConfig.setAvailableSkills(course.getSkills().stream().map(Skill::getName).collect(Collectors.toSet()));

        List<ClassDTO> cleanClasses = course.getClassEntities().stream().map(cls -> new ClassDTO(
                null,
                cls.getTopic(),
                cls.getDate(),
                cls.getType(),
                cls.getSkills().stream()
                        .map(s -> new SkillDTO(null, s.getName()))
                        .collect(Collectors.toSet())))
                .collect(Collectors.toList());

        exportConfig.setClasses(cleanClasses);
        return exportConfig;
    }
}
