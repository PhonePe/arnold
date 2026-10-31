package com.phonepe.devx.arnold.core.services.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotGitHubClientCoverageTest {

    private static final String TOKEN_OK = "{\"token\":\"t\"}";

    private static CopilotGitHubClient client(final List<Request> seen, final Function<Request, Response.Builder> fn) {
        final var http = new OkHttpClient.Builder().addInterceptor(chain -> {
            seen.add(chain.request());
            return fn.apply(chain.request()).request(chain.request()).protocol(Protocol.HTTP_1_1).build();
        }).build();
        return new CopilotGitHubClient(new ObjectMapper(), http);
    }

    private static Response.Builder reply(final int code, final String body) {
        return new Response.Builder().code(code).message("m")
                .body(ResponseBody.create(MediaType.get("application/json"), body));
    }

    private static CopilotClientFailure failure(final CopilotGitHubClient c, final Runnable r) {
        return assertThrows(CopilotClientFailure.class, r::run);
    }

    @Test
    void defaultConstructorBuildsClient() {
        assertNotNull(new CopilotGitHubClient(new ObjectMapper()));
    }

    @Test
    void deviceFlowCallsPostFormBodiesToGitHub() {
        final var seen = new ArrayList<Request>();
        final var c = client(seen, r -> reply(200, "{\"ok\":true}"));
        assertTrue(c.initiate("cid").path("ok").asBoolean());
        assertTrue(c.poll("cid", "dev").path("ok").asBoolean());
        assertTrue(c.refresh("cid", "ref").path("ok").asBoolean());
        assertTrue(c.identity("tok").path("ok").asBoolean());
        assertEquals("https://github.com/login/device/code", seen.get(0).url().toString());
        assertEquals("https://github.com/login/oauth/access_token", seen.get(1).url().toString());
        assertEquals("POST", seen.get(2).method());
        assertEquals("Bearer tok", seen.get(3).header("Authorization"));
    }

    @Test
    void githubDevice400ErrorsAreMappedToKnownCodes() {
        final var pending = client(new ArrayList<>(), r -> reply(400, "{\"error\":\"authorization_pending\"}"));
        assertEquals("authorization_pending", failure(pending, () -> pending.poll("c", "d")).code());
        final var unknown = client(new ArrayList<>(), r -> reply(200, "{\"error\":\"weird\"}"));
        assertEquals("upstream_error", failure(unknown, () -> unknown.poll("c", "d")).code());
    }

    @Test
    void non2xxFromOtherHostsAndBadBodiesAreUpstreamErrors() {
        final var serverError = client(new ArrayList<>(), r -> reply(500, "{}"));
        assertEquals(500, failure(serverError, () -> serverError.identity("t")).status());
        final var array = client(new ArrayList<>(), r -> reply(200, "[]"));
        assertEquals(502, failure(array, () -> array.identity("t")).status());
        final var empty = client(new ArrayList<>(), r -> reply(200, ""));
        assertEquals(502, failure(empty, () -> empty.identity("t")).status());
        final var noBody = client(new ArrayList<>(), r -> new Response.Builder().code(200).message("m"));
        assertEquals(502, failure(noBody, () -> noBody.identity("t")).status());
        final var badRequestNoError = client(new ArrayList<>(), r -> reply(400, "{}"));
        assertEquals(400, failure(badRequestNoError, () -> badRequestNoError.poll("c", "d")).status());
    }

    @Test
    void copilotTokenValidation() {
        final var blank = client(new ArrayList<>(), r -> reply(200, "{\"token\":\" \"}"));
        assertEquals(502, failure(blank, () -> blank.verifyCopilot("t")).status());
        final var disabled = client(new ArrayList<>(), r -> reply(200, "{\"token\":\"t\",\"chat_enabled\":false}"));
        assertEquals(403, failure(disabled, () -> disabled.verifyCopilot("t")).status());
        final var ok = client(new ArrayList<>(), r -> reply(200, "{\"token\":\"t\",\"chat_enabled\":true}"));
        ok.verifyCopilot("t");
    }

    @Test
    void modelsRejectUnsafeEndpointsAndMalformedData() {
        for (final String endpoint : List.of("http://api.githubcopilot.com", "https://evil.example.com",
                "https://api.githubcopilot.com:8443", "https://u:p@api.githubcopilot.com",
                "https://api.githubcopilot.com/?q=1", "https://api.githubcopilot.com/#f", "not a url")) {
            final var c = client(new ArrayList<>(), r -> reply(200,
                    "{\"token\":\"t\",\"endpoints\":{\"api\":\"" + endpoint + "\"}}"));
            assertEquals(502, failure(c, () -> c.models("o")).status(), endpoint);
        }
        final var noData = client(new ArrayList<>(), r ->
                r.url().host().equals("api.github.com") ? reply(200, TOKEN_OK) : reply(200, "{\"data\":{}}"));
        assertEquals(502, failure(noData, () -> noData.models("o")).status());
    }

    @Test
    void modelsDefaultEndpointAndSortingAndNameFallback() {
        final var seen = new ArrayList<Request>();
        final var c = client(seen, r -> r.url().host().equals("api.github.com") ? reply(200, TOKEN_OK)
                : reply(200, "{\"data\":[{\"id\":\"b\"},{\"id\":\"a\",\"name\":\"A\",\"is_chat_default\":true}]}"));
        final var models = c.models("o");
        assertEquals("https://api.githubcopilot.com/models", seen.get(1).url().toString());
        assertEquals(List.of("a", "b"), models.stream().map(m -> m.id()).toList());
        assertEquals("b", models.get(1).name());
        assertTrue(models.get(0).isDefault() || models.get(0).name().equals("A"));
    }
}
