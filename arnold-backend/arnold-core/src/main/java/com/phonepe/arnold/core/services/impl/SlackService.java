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

package com.phonepe.arnold.core.services.impl;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.phonepe.arnold.core.clients.HttpClients;
import com.phonepe.arnold.core.config.SlackClientConfig;
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
        final OkHttpClient client = HttpClients.create(configuration);

        final SlackConfig slackConfig = new SlackConfig();
        slackConfig.setMethodsEndpointUrlPrefix(HttpClients.trimTrailingSlash(configuration.getUrl()) + "/api/");

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
}
