package com.phonepe.devx.arnold.core.services.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Singleton
public class CopilotGitHubClient {
    private static final Set<String> DEVICE_ERRORS = Set.of("authorization_pending", "slow_down",
            "expired_token", "access_denied", "incorrect_device_code", "bad_refresh_token", "invalid_grant");
    private final ObjectMapper mapper;
    private final OkHttpClient http;

    @Inject
    public CopilotGitHubClient(final ObjectMapper mapper) {
        this(mapper, new OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build());
    }

    CopilotGitHubClient(final ObjectMapper mapper, final OkHttpClient http) {
        this.mapper = mapper;
        this.http = http;
    }

    public JsonNode initiate(final String clientId) {
        return post("https://github.com/login/device/code", Map.of("client_id", clientId, "scope", "read:user"));
    }

    public JsonNode poll(final String clientId, final String deviceCode) {
        return post("https://github.com/login/oauth/access_token", Map.of("client_id", clientId,
                "device_code", deviceCode, "grant_type", "urn:ietf:params:oauth:grant-type:device_code"));
    }

    public JsonNode refresh(final String clientId, final String refreshToken) {
        return post("https://github.com/login/oauth/access_token", Map.of("client_id", clientId,
                "refresh_token", refreshToken, "grant_type", "refresh_token"));
    }

    public JsonNode identity(final String accessToken) {
        return send(base("https://api.github.com/user").header("Authorization", "Bearer " + accessToken).build());
    }

    public void verifyCopilot(final String accessToken) {
        final var result = send(base("https://api.github.com/copilot_internal/v2/token")
                .header("Authorization", "token " + accessToken)
                .header("Editor-Version", "vscode/1.107.0")
                .header("Editor-Plugin-Version", "copilot-chat/0.35.0").build());
        if (result.path("token").asText("").isBlank()) throw new Failure(502, "upstream_error");
        if (result.has("chat_enabled") && !result.path("chat_enabled").asBoolean()) {
            throw new Failure(403, "upstream_error");
        }
    }

    private JsonNode post(final String url, final Map<String, String> fields) {
        final var body = new FormBody.Builder();
        fields.forEach(body::add);
        return send(base(url).post(body.build()).build());
    }

    private Request.Builder base(final String url) {
        return new Request.Builder().url(url).header("Accept", "application/json")
                .header("User-Agent", "Arnold-Copilot").header("X-GitHub-Api-Version", "2022-11-28");
    }

    private JsonNode send(final Request request) {
        try (final var response = http.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 400) throw new Failure(response.code(), "upstream_error");
            if (response.body() == null) throw new Failure(502, "upstream_error");
            final var result = mapper.readTree(response.body().byteStream());
            if (result == null || !result.isObject()) throw new Failure(502, "upstream_error");
            if (result.has("error")) {
                final var error = result.path("error").asText();
                throw new Failure(400, DEVICE_ERRORS.contains(error) ? error : "upstream_error");
            }
            if (!response.isSuccessful()) throw new Failure(response.code(), "upstream_error");
            return result;
        } catch (IOException e) {
            throw new Failure(502, "upstream_error");
        }
    }

    public static class Failure extends RuntimeException {
        private final int status;
        private final String code;

        public Failure(final int status, final String code) {
            super("GitHub authentication request failed");
            this.status = status;
            this.code = code;
        }

        public int status() { return status; }
        public String code() { return code; }
    }
}
