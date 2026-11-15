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

package com.phonepe.arnold.core.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenericExceptionMapperTest {

    private GenericExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GenericExceptionMapper();
    }

    @Test
    void testToResponseWithWebApplicationException() {
        WebApplicationException exception = mock(WebApplicationException.class);
        Response response = Response.status(404).build();
        when(exception.getResponse()).thenReturn(response);

        Response result = mapper.toResponse(exception);

        assertEquals(404, result.getStatus());
        assertEquals("ArnoldResponse(success=false, response=null, errorCode=404, errorMessage=null, details=null)", result.getEntity().toString());
    }

    @Test
    void testToResponseWithException() {
        Exception exception = new RuntimeException("Something went wrong");

        Response result = mapper.toResponse(exception);

        assertEquals(500, result.getStatus());
        assertEquals("ArnoldResponse(success=false, response=null, errorCode=1000, errorMessage=Internal Server Error, details=null)", result.getEntity().toString());
    }
}
