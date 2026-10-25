package com.phonepe.arnold.core.model.persona;

public record ReviewPersonaSnapshot(
        String id,
        String name,
        String instructions,
        long version
) {
}
