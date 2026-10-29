package com.phonepe.devx.arnold.core.services.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import javax.net.ssl.SSLHandshakeException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CopilotGitHubClientTest {
    @Test
    void diagnosticsRetainStatusAndExceptionTypeWithoutCredentials() {
        final var logger = (Logger) LoggerFactory.getLogger(CopilotGitHubClient.class);
        final var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (boolean transportFailure : new boolean[]{false, true}) {
                final var http = new OkHttpClient.Builder().addInterceptor(chain -> {
                    if (transportFailure) throw new SSLHandshakeException("secret transport details");
                    return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                            .code(400).message("Bad Request")
                            .body(ResponseBody.create(MediaType.get("text/plain"), "secret response body")).build();
                }).build();
                final var failure = assertThrows(CopilotClientFailure.class,
                        () -> new CopilotGitHubClient(new ObjectMapper(), http).verifyCopilot("secret token"));
                assertEquals(transportFailure ? 502 : 400, failure.status());
            }
            assertEquals(2, appender.list.size());
            assertTrue(appender.list.get(0).getFormattedMessage().contains("httpStatus=400"));
            assertTrue(appender.list.get(1).getFormattedMessage().contains("exceptionType=SSLHandshakeException"));
            for (final var event : appender.list) {
                assertTrue(event.getFormattedMessage().contains("host=api.github.com"));
                assertFalse(event.getFormattedMessage().contains("secret"));
                assertNull(event.getThrowableProxy());
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void modelsUseTheExchangedBearerAndFilterUnsupportedEntries() {
        final var http = new OkHttpClient.Builder().addInterceptor(chain -> {
            final var request = chain.request();
            final String payload;
            if (request.url().host().equals("api.github.com")) {
                assertEquals("token oauth", request.header("Authorization"));
                assertEquals("2022-11-28", request.header("X-GitHub-Api-Version"));
                payload = "{\"token\":\"bearer-secret\",\"endpoints\":{\"api\":\"https://api.business.githubcopilot.com\"}}";
            } else {
                assertEquals("https://api.business.githubcopilot.com/models", request.url().toString());
                assertEquals("Bearer bearer-secret", request.header("Authorization"));
                assertEquals("2025-04-01", request.header("X-GitHub-Api-Version"));
                payload = """
                        {"data":[
                          {"id":"available","name":"Available","capabilities":{"type":"chat","supports":{"tool_calls":true}},"is_chat_default":true},
                          {"id":"disabled","policy":{"state":"disabled"}},
                          {"id":"embedding","capabilities":{"type":"embeddings"}},
                          {"id":"no-tools","capabilities":{"supports":{"tool_calls":false}}},
                          {"id":"hidden","model_picker_enabled":false},
                          {"id":"invalid id"}]}
                        """;
            }
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.get("application/json"), payload)).build();
        }).build();
        final var models = new CopilotGitHubClient(new ObjectMapper(), http).models("oauth");
        assertEquals(1, models.size());
        assertEquals("available", models.get(0).id());
        assertEquals("Available", models.get(0).name());
        assertTrue(models.get(0).isDefault());
    }

    @Test
    void modelsNeverSendBearerTokensToAnUntrustedEndpoint() {
        final var calls = new AtomicInteger();
        final var http = new OkHttpClient.Builder().addInterceptor(chain -> {
            calls.incrementAndGet();
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.get("application/json"),
                            "{\"token\":\"secret\",\"endpoints\":{\"api\":\"https://githubcopilot.com.attacker.example\"}}"))
                    .build();
        }).build();
        assertThrows(CopilotClientFailure.class, () -> new CopilotGitHubClient(new ObjectMapper(), http).models("oauth"));
        assertEquals(1, calls.get());
    }

    @Test
    void testPoll_shouldRecognizeSlowDown_whenGitHubReturnsHttp400() {
        final var http = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(400).message("Bad Request")
                .body(ResponseBody.create(MediaType.get("application/json"), "{\"error\":\"slow_down\"}")).build()).build();
        final var client = new CopilotGitHubClient(new ObjectMapper(), http);
        final var failure = assertThrows(CopilotClientFailure.class, () -> client.poll("client", "device"));
        assertEquals("slow_down", failure.code());
    }

    @Test
    void testVerifyCopilot_shouldRejectAccess_whenChatIsDisabled() {
        final var http = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(MediaType.get("application/json"),
                        "{\"token\":\"temporary-bearer\",\"chat_enabled\":false}")).build()).build();
        final var client = new CopilotGitHubClient(new ObjectMapper(), http);
        assertEquals(403, assertThrows(CopilotClientFailure.class, () -> client.verifyCopilot("oauth")).status());
    }

    @Test
    void testDeviceFlow_shouldUseExpectedEndpointsAndBody_whenAuthorizing() {
        final var step = new AtomicInteger();
        final var http = new OkHttpClient.Builder().addInterceptor(chain -> {
            final var request = chain.request();
            assertEquals("application/json", request.header("Accept"));
            final var body = new okio.Buffer();
            if (request.body() != null) request.body().writeTo(body);
            final var form = body.readUtf8();
            final var payload = switch (step.getAndIncrement()) {
                case 0 -> {
                    assertEquals("https://github.com/login/device/code", request.url().toString());
                    assertEquals("POST", request.method());
                    assertTrue(form.contains("scope=read%3Auser"));
                    yield "{\"device_code\":\"device\"}";
                }
                case 1 -> {
                    assertEquals("https://github.com/login/oauth/access_token", request.url().toString());
                    assertTrue(form.contains("device_code=device"));
                    assertTrue(form.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"));
                    yield "{\"access_token\":\"oauth\"}";
                }
                case 2 -> {
                    assertEquals("https://api.github.com/user", request.url().toString());
                    assertEquals("Bearer oauth", request.header("Authorization"));
                    yield "{\"id\":1,\"login\":\"user\"}";
                }
                default -> {
                    assertEquals("https://api.github.com/copilot_internal/v2/token", request.url().toString());
                    assertEquals("token oauth", request.header("Authorization"));
                    assertNotNull(request.header("Editor-Version"));
                    yield "{\"token\":\"temporary-bearer\"}";
                }
            };
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.get("application/json"), payload)).build();
        }).build();
        final var client = new CopilotGitHubClient(new ObjectMapper(), http);
        client.initiate("client");
        client.poll("client", "device");
        client.identity("oauth");
        client.verifyCopilot("oauth");
        assertEquals(4, step.get());
    }

    @Test
    void testPoll_shouldSanitizeErrors_whenUpstreamIncludesSensitiveDetails() {
        final var http = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(MediaType.get("application/json"),
                        "{\"error\":\"unexpected-secret\",\"error_description\":\"oauth-secret\"}")).build()).build();
        final var client = new CopilotGitHubClient(new ObjectMapper(), http);
        final var failure = assertThrows(CopilotClientFailure.class, () -> client.poll("client", "device"));
        assertEquals("upstream_error", failure.code());
        assertFalse(failure.getMessage().contains("secret"));
    }
}
