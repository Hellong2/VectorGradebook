package pl.ddconstruction.vectorgradebook.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClassType {
    LECTURE("Wykład"),
    LAB("Laboratoria");

    private final String label;
}
