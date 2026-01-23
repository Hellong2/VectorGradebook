package pl.ddconstruction.vectorgradebook.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillDTO(
        UUID id,
        String name) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SkillDTO fromString(String name) {
        return new SkillDTO(null, name);
    }
}
