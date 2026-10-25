package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.phonepe.devx.arnold.core.config.OpenCodeConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.services.ModelCatalog;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleModelCatalogTest {

    private WireMockServer server;
    private OpenCodeConfiguration config;
    private OpenAiCompatibleModelCatalog catalog;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        config = new OpenCodeConfiguration();
        catalog = new OpenAiCompatibleModelCatalog(config, new ObjectMapper(), new OkHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void shouldPreferStaticModels() {
        final var entry = new OpenCodeConfiguration.ModelEntry();
        entry.setId("m1");
        config.setModels(List.of(entry));
        config.setModelsUrl(server.baseUrl());

        assertEquals(List.of(new ModelCatalog.CatalogModel("m1", "m1")), catalog.models());
        assertEquals(0, server.getAllServeEvents().size());
    }

    @Test
    void shouldDiscoverModelsFromOpenAiCompatibleEndpoint() {
        config.setModelsUrl(server.baseUrl() + "/v1/");
        config.setApiKey("key");
        server.stubFor(get(urlEqualTo("/v1/models")).withHeader("Authorization", equalTo("Bearer key"))
                .willReturn(aResponse().withBody("{\"data\":[{\"id\":\"gpt-4.1\"},{\"id\":\"\"}]}")));

        assertEquals(List.of(new ModelCatalog.CatalogModel("gpt-4.1", "gpt-4.1")), catalog.models());
    }

    @Test
    void shouldFail_whenDiscoveryFails() {
        config.setModelsUrl(server.baseUrl());
        server.stubFor(get(urlEqualTo("/models")).willReturn(aResponse().withStatus(500)));

        assertThrows(ArnoldError.class, catalog::models);
    }

    @Test
    void shouldReturnEmpty_whenNothingConfigured() {
        assertTrue(catalog.models().isEmpty());
    }
}
