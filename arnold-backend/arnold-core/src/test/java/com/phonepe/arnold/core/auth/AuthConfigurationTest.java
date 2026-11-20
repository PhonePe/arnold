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

package com.phonepe.arnold.core.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthConfigurationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Set<String> admins(final String json) throws Exception {
        return MAPPER.readValue(json, AuthConfiguration.class).getAdmins();
    }

    @Nested
    class Admins {

        @Test
        void testAdmins_shouldSplitAndTrim_whenGivenCommaSeparatedString() throws Exception {
            final var admins = admins("{\"admins\": \" alice@example.com, bob@example.com ,,\"}");

            assertEquals(Set.of("alice@example.com", "bob@example.com"), admins);
        }

        @Test
        void testAdmins_shouldBeEmpty_whenEnvironmentVariableIsUnset() throws Exception {
            assertEquals(Set.of(), admins("{\"admins\": \"\"}"));
        }

        @Test
        void testAdmins_shouldAcceptList_whenConfiguredAsYamlList() throws Exception {
            final var admins = admins("{\"admins\": [\"alice@example.com\", \"bob@example.com,carol@example.com\"]}");

            assertEquals(Set.of("alice@example.com", "bob@example.com", "carol@example.com"), admins);
        }

        @Test
        void testAdmins_shouldBeEmpty_whenNotConfigured() throws Exception {
            assertEquals(Set.of(), admins("{}"));
        }
    }
}
