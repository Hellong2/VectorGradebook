package pl.ddconstruction.vectorgradebook.service;

import org.springframework.stereotype.Component;
import pl.ddconstruction.vectorgradebook.dto.ClassDTO;
import pl.ddconstruction.vectorgradebook.dto.SkillDTO;
import pl.ddconstruction.vectorgradebook.model.entity.ClassEntity;
import pl.ddconstruction.vectorgradebook.model.entity.Skill;

import java.util.stream.Collectors;

@Component
public class EntityMapper {

    public SkillDTO toDTO(Skill skill) {
        if (skill == null)
            return null;
        return new SkillDTO(skill.getId(), skill.getName());
    }

    public ClassDTO toDTO(ClassEntity clazz) {
        if (clazz == null)
            return null;
        return new ClassDTO(
                clazz.getId(),
                clazz.getTopic(),
                clazz.getDate(),
                clazz.getType(),
                clazz.getSkills().stream().map(this::toDTO).collect(Collectors.toSet()));
    }
}
