package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.persona.CreatePersonaRequest;
import com.phonepe.devx.arnold.models.persona.PersonaResponse;
import com.phonepe.devx.arnold.models.persona.UpdatePersonaRequest;
import com.phonepe.devx.arnold.core.model.persona.ReviewPersonaSnapshot;

import java.util.List;

public interface PersonaService {
    List<PersonaResponse> list(String userId);

    PersonaResponse get(String userId, String personaId);

    PersonaResponse create(String userId, CreatePersonaRequest request);

    PersonaResponse update(String userId, String personaId, UpdatePersonaRequest request);

    PersonaResponse publish(String userId, String personaId, long version);

    PersonaResponse unpublish(String userId, String personaId, long version);

    void delete(String userId, String personaId, long version);

    List<ReviewPersonaSnapshot> resolveForAnalysis(String userId, List<String> personaIds);
}
