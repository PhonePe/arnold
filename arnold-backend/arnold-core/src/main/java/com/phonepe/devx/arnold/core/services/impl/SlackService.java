package com.phonepe.devx.arnold.core.services.impl;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.phonepe.devx.arnold.core.config.SlackClientConfig;
import com.slack.api.Slack;
import com.slack.api.SlackConfig;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.util.http.SlackHttpClient;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Singleton;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

@Singleton
@Slf4j
public class SlackService {

    private final MethodsClient slackClient;

    @Inject
    public SlackService(final @Config SlackClientConfig configuration,
                        final MetricRegistry metricRegistry) throws GeneralSecurityException, IOException {
        if (configuration == null || !configuration.isEnabled()) {
            this.slackClient = null;
            return;
        }
        final OkHttpClient client = com.phonepe.platform.http.v2.client.ClientFactory.newHttpClientBuilder()
                .withMetricRegistry(metricRegistry)
                .withConfiguration(configuration)
                .build();

        final SlackConfig slackConfig = new SlackConfig();
        slackConfig.setMethodsEndpointUrlPrefix(baseUrl(configuration) + "/api/");

        final SlackHttpClient httpClient = new SlackHttpClient(client);
        httpClient.setConfig(slackConfig);
        this.slackClient = Slack.getInstance(slackConfig, httpClient).methods(configuration.getBotToken());
    }

    @SneakyThrows
    @MonitoredFunction
    public UsersLookupByEmailResponse usersLookupByEmail(final String email) {
        final UsersLookupByEmailResponse response = slackClient.usersLookupByEmail(
                UsersLookupByEmailRequest.builder().email(email).build());
        response.setHttpResponseHeaders(Map.of());
        log.info("slack_usersLookupByEmail email={} status={} error={}", email, response.isOk(),
                response.getError());
        return response;
    }

    @SneakyThrows
    @MonitoredFunction
    public ChatPostMessageResponse chatPostMessage(final String channel, final String text) {
        final ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(channel)
                .text(text)
                .mrkdwn(true)
                .build();
        final ChatPostMessageResponse response = slackClient.chatPostMessage(request);
        response.setHttpResponseHeaders(Map.of());
        log.info("slack_chatPostMessage channel={} status={} error={}", channel, response.isOk(),
                response.getError());
        return response;
    }

    @SneakyThrows
    @MonitoredFunction
    public ChatPostMessageResponse chatPostBlockMessage(final String channel, final List<LayoutBlock> blocks) {
        final ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(channel)
                .blocks(blocks)
                .mrkdwn(true)
                .build();
        final ChatPostMessageResponse response = slackClient.chatPostMessage(request);
        response.setHttpResponseHeaders(Map.of());
        log.info("slack_chatPostBlockMessage channel={} status={} error={}", channel, response.isOk(),
                response.getError());
        return response;
    }

    private static String baseUrl(final SlackClientConfig configuration) {
        final var scheme = configuration.isSecure() ? "https" : "http";
        return String.format("%s://%s:%d", scheme, configuration.getHost(), configuration.getPort());
    }
}
