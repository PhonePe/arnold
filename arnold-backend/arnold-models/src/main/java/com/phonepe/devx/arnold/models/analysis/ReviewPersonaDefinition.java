package com.phonepe.devx.arnold.models.analysis;

/**
 * Persona definition frozen on an analysis run, served to the review pipeline so it can run one sub-agent per persona.
 */
public record ReviewPersonaDefinition(String id, String name, String instructions, long version) {
}
