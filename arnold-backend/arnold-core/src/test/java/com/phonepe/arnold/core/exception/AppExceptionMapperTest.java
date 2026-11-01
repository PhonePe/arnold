/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.arnold.core.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.OAuthCredentialManager;
import com.phonepe.arnold.models.ArnoldResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.Response;

@DisplayName("AppExceptionMapper Tests")
@ExtendWith(MockitoExtension.class)
class AppExceptionMapperTest {

    private ArnoldError error = ArnoldError
            .raise(ArnoldErrorCode.INTERNAL_SERVER_ERROR)
            .message("An internal error occurred")
            .build();

    @Test
    @DisplayName("Test if toResponse returns the expected response")
    void testToResponseReturnsExpectedResponse() {
        // Create an instance of AppExceptionMapper
        AppExceptionMapper appExceptionMapper = new AppExceptionMapper();

        // Call the toResponse method and check the returned response
        Response response = appExceptionMapper.toResponse(error);

        Assertions.assertEquals(response.getStatus(), ArnoldErrorCode.INTERNAL_SERVER_ERROR.getHttpStatusCode().getStatusCode());

        ArnoldResponse ArnoldResponse = (ArnoldResponse) response.getEntity();
        Assertions.assertEquals(ArnoldErrorCode.INTERNAL_SERVER_ERROR.getCode(), ArnoldResponse.getErrorCode());
        Assertions.assertEquals("Error [1000]: Internal Server Error", ArnoldResponse.getErrorMessage());
        Assertions.assertFalse(ArnoldResponse.isSuccess());
    }

    @Test
    @DisplayName("Reconnect errors expose provider details in the response body")
    void testToResponse_shouldRenderReconnectDetails() throws Exception {
        final var reconnect = ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR)
                .message("GitHub refresh token was rejected; reconnect the account")
                .details(OAuthCredentialManager.reconnectDetails(VersionControlProviderType.GITHUB))
                .build();

        final var response = new AppExceptionMapper().toResponse(reconnect);
        final var mapper = new ObjectMapper();
        final var json = mapper.readTree(mapper.writeValueAsString(response.getEntity()));

        Assertions.assertEquals(401, response.getStatus());
        Assertions.assertFalse(json.path("success").asBoolean());
        Assertions.assertEquals(2000, json.path("errorCode").asInt());
        Assertions.assertEquals("SCM_RECONNECT_REQUIRED", json.path("details").path("reason").asText());
        Assertions.assertEquals("GITHUB", json.path("details").path("provider").asText());
        Assertions.assertTrue(json.path("details").path("reconnect").asBoolean());
        Assertions.assertTrue(mapper.readTree(mapper.writeValueAsString(
                new AppExceptionMapper().toResponse(error).getEntity())).path("details").isMissingNode());
    }
}
