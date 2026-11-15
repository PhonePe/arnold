/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.services;

import com.phonepe.arnold.models.persona.CreatePersonaRequest;
import com.phonepe.arnold.models.persona.PersonaResponse;
import com.phonepe.arnold.models.persona.UpdatePersonaRequest;
import com.phonepe.arnold.core.model.persona.ReviewPersonaSnapshot;

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
