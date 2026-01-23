package pl.ddconstruction.vectorgradebook.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import pl.ddconstruction.vectorgradebook.model.ClassType;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClassDTO(
        UUID id,
        String topic,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        ClassType type,
        Set<SkillDTO> skills) {
}
