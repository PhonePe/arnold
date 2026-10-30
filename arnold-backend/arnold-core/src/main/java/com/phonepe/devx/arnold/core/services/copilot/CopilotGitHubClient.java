package com.phonepe.devx.arnold.core.services.copilot;

import com.phonepe.devx.arnold.core.clients.EnvironmentProxySelector;
import java.net.ProxySelector;
import okhttp3.HttpUrl;
import lombok.extern.slf4j.Slf4j;
import java.util.TreeMap;
import java.util.List;
import com.phonepe.devx.arnold.models.analysis.HarnessOption.Model;
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
@Slf4j
public class CopilotGitHubClient {
    private static final Set<String> DEVICE_ERRORS = Set.of("authorization_pending", "slow_down",
            "expired_token", "access_denied", "incorrect_device_code", "bad_refresh_token", "invalid_grant");
    private static final String CLIENT_ID = "client_id";
    private static final String AUTHORIZATION = "Authorization";
    private static final String GITHUB_HOST = "github.com";
    private static final String GITHUB_URL = "https://" + GITHUB_HOST;
    private static final String GITHUB_API_URL = "https://api." + GITHUB_HOST;
    private static final String COPILOT_API_HOST = "api.githubcopilot.com";
    private static final String COPILOT_API_DOMAIN_SUFFIX = ".githubcopilot.com";
    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final String COPILOT_MODELS_API_VERSION = "2025-04-01";
    private static final String EDITOR_VERSION = "vscode/1.107.0";
    private static final String EDITOR_PLUGIN_VERSION = "copilot-chat/0.35.0";
    private static final String COPILOT_USER_AGENT = "GitHubCopilotChat/0.35.0";
    private static final String COPILOT_INTEGRATION_ID = "vscode-chat";
    private static final String USER_AGENT = "Arnold-Copilot";
    private final ObjectMapper mapper;
    private final OkHttpClient http;

    @Inject
    public CopilotGitHubClient(final ObjectMapper mapper) {
        this(mapper, new OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS)
                .proxySelector(EnvironmentProxySelector.fromEnvironment(ProxySelector.getDefault()))
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build());
    }

    CopilotGitHubClient(final ObjectMapper mapper, final OkHttpClient http) {
        this.mapper = mapper;
        this.http = http;
    }

    public JsonNode initiate(final String clientId) {
        return post(GITHUB_URL + "/login/device/code", Map.of(CLIENT_ID, clientId, "scope", "read:user"));
    }

    public JsonNode poll(final String clientId, final String deviceCode) {
        return post(GITHUB_URL + "/login/oauth/access_token", Map.of(CLIENT_ID, clientId,
                "device_code", deviceCode, "grant_type", "urn:ietf:params:oauth:grant-type:device_code"));
    }

    public JsonNode refresh(final String clientId, final String refreshToken) {
        return post(GITHUB_URL + "/login/oauth/access_token", Map.of(CLIENT_ID, clientId,
                "refresh_token", refreshToken, "grant_type", "refresh_token"));
    }

    public JsonNode identity(final String accessToken) {
        return send(base(GITHUB_API_URL + "/user").header(AUTHORIZATION, "Bearer " + accessToken).build());
    }

    private JsonNode copilotToken(final String accessToken) {
        final var result = send(base(GITHUB_API_URL + "/copilot_internal/v2/token")
                .header(AUTHORIZATION, "token " + accessToken)
                .header("Editor-Version", EDITOR_VERSION)
                .header("Editor-Plugin-Version", EDITOR_PLUGIN_VERSION).build());
        if (result.path("token").asText("").isBlank()) {
            log.warn("Copilot token exchange returned no token");
            throw new CopilotClientFailure(502, "upstream_error");
        }
        if (result.has("chat_enabled") && !result.path("chat_enabled").asBoolean()) {
            log.warn("Copilot token exchange reported chat disabled");
            throw new CopilotClientFailure(403, "upstream_error");
        }
        return result;
    }

    public void verifyCopilot(final String accessToken) {
        copilotToken(accessToken);
    }

    public List<Model> models(final String accessToken) {
        final var token = copilotToken(accessToken);
        final var endpoint = HttpUrl.parse(token.path("endpoints").path("api").asText("https://" + COPILOT_API_HOST));
        if (endpoint == null || !"https".equals(endpoint.scheme()) || endpoint.port() != 443
                || !(endpoint.host().equals(COPILOT_API_HOST) || endpoint.host().endsWith(COPILOT_API_DOMAIN_SUFFIX))
                || !endpoint.username().isEmpty() || !endpoint.password().isEmpty()
                || endpoint.query() != null || endpoint.fragment() != null) {
            log.warn("Copilot model discovery rejected the API endpoint supplied by GitHub");
            throw new CopilotClientFailure(502, "upstream_error");
        }
        final var data = send(base(endpoint.newBuilder().addPathSegment("models").build().toString())
                .header(AUTHORIZATION, "Bearer " + token.path("token").asText())
                .header("Copilot-Integration-Id", COPILOT_INTEGRATION_ID)
                .header("X-GitHub-Api-Version", COPILOT_MODELS_API_VERSION)
                .header("Editor-Version", EDITOR_VERSION)
                .header("Editor-Plugin-Version", EDITOR_PLUGIN_VERSION)
                .header("User-Agent", COPILOT_USER_AGENT)
                .header("OpenAI-Intent", "conversation-panel").build()).path("data");
        if (!data.isArray()) {
            log.warn("Copilot model discovery response has no data array");
            throw new CopilotClientFailure(502, "upstream_error");
        }
        final var models = new TreeMap<String, Model>();
        for (final var model : data) {
            final var id = model.path("id").asText("");
            if (!id.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}")
                    || !"chat".equals(model.path("capabilities").path("type").asText("chat"))
                    || !model.path("capabilities").path("supports").path("tool_calls").asBoolean(true)
                    || !model.path("model_picker_enabled").asBoolean(true)
                    || "disabled".equals(model.path("policy").path("state").asText())) continue;
            models.put(id, new Model(id, model.path("name").asText(id),
                    model.path("is_chat_default").asBoolean(false)));
        }
        return List.copyOf(models.values());
    }

    private JsonNode post(final String url, final Map<String, String> fields) {
        final var body = new FormBody.Builder();
        fields.forEach(body::add);
        return send(base(url).post(body.build()).build());
    }

    private Request.Builder base(final String url) {
        return new Request.Builder().url(url).header("Accept", "application/json")
                .header("User-Agent", USER_AGENT).header("X-GitHub-Api-Version", GITHUB_API_VERSION);
    }

    private JsonNode send(final Request request) {
        int status = 0;
        try (final var response = http.newCall(request).execute()) {
            status = response.code();
            if (!response.isSuccessful() && (response.code() != 400 || !GITHUB_HOST.equals(request.url().host()))) {
                throw new CopilotClientFailure(response.code(), "upstream_error");
            }
            if (response.body() == null) throw new CopilotClientFailure(502, "upstream_error");
            final var result = mapper.readTree(response.body().byteStream());
            if (result == null || !result.isObject()) throw new CopilotClientFailure(502, "upstream_error");
            if (result.has("error")) {
                final var error = result.path("error").asText();
                throw new CopilotClientFailure(400, DEVICE_ERRORS.contains(error) ? error : "upstream_error");
            }
            if (!response.isSuccessful()) throw new CopilotClientFailure(response.code(), "upstream_error");
            return result;
        } catch (CopilotClientFailure failure) {
            if (!Set.of("authorization_pending", "slow_down").contains(failure.code())) {
                log.warn("Copilot upstream request failed: host={}, httpStatus={}, code={}",
                        request.url().host(), status, failure.code());
            }
            throw failure;
        } catch (IOException e) {
            log.warn("Copilot upstream request failed: host={}, httpStatus={}, exceptionType={}",
                    request.url().host(), status, e.getClass().getSimpleName());
            throw new CopilotClientFailure(502, "upstream_error");
        }
    }
}
