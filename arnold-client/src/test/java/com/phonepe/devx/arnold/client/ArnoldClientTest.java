package com.phonepe.devx.arnold.client;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.platform.http.v2.common.ServiceEndpointProvider;
import com.phonepe.platform.http.v2.discovery.ServiceEndpointProviderFactory;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class ArnoldClientTest {

    @Mock
    Environment environment;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void whenBuildIsCalledWithNullConfig_thenExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                     () -> ArnoldClientBuilder.build(null, null, null));
    }

    @Test
    void whenBuildIsCalledWithCorrectConfig_thenClientIsBuilt() {
        when(environment.metrics()).thenReturn(new MetricRegistry());
        when(environment.getObjectMapper()).thenReturn(new ObjectMapper());
        Supplier<ServiceEndpointProvider> endpointProviderSupplier = () ->
                new ServiceEndpointProviderFactory().provider("test");
        ArnoldClient test = ArnoldClientBuilder.build(
                TestUtils.sampleHttpConfig("test"),
                endpointProviderSupplier,
                environment);
        assertNotNull(test);
    }
}