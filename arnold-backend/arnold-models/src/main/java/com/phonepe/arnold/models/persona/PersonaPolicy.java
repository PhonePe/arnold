package com.phonepe.arnold.models.persona;

import java.util.List;

public record PersonaPolicy(
        int version,
        String guidance,
        List<PersonaRule> rules,
        List<PersonaPrerequisite> prerequisites,
        List<String> ignore
) {
    public PersonaPolicy {
        rules = rules == null ? List.of() : List.copyOf(rules);
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        ignore = ignore == null ? List.of() : List.copyOf(ignore);
    }
}
