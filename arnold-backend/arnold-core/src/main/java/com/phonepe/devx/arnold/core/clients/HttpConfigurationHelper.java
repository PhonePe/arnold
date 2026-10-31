package com.phonepe.devx.arnold.core.clients;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.platform.http.v2.common.HttpConfiguration;
import com.phonepe.platform.http.v2.common.hub.RangerHubConfiguration;
import com.phonepe.platform.http.v2.discovery.utils.DiscoveryUtils;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.zeus.models.routing.RoutingRequest;
import com.phonepe.zeus.models.utils.DiscoveryProperties;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@Singleton
@Slf4j
public class HttpConfigurationHelper {
    private final RangerHubConfiguration hubConfiguration;

    @Inject
    public HttpConfigurationHelper(@Config final RangerHubConfiguration hubConfiguration) {
        this.hubConfiguration = hubConfiguration;
    }

    public HttpConfiguration fetchHttpConfiguration(final String clientId) {
        val httpConfiguration = DiscoveryUtils.getConfiguration(hubConfiguration,
                                                                clientId).orElse(null);
        if (Objects.isNull(httpConfiguration)) {
            log.error("Http Configuration cannot be null for clientId: {}", clientId);
            throw ArnoldError.raise(ArnoldErrorCode.INIT_ERROR)
                    .context(Map.of("id", clientId))
                    .build();
        }
        return httpConfiguration;
    }

    public RoutingRequest buildRoutingRequest(final String clientId) {
        return buildRoutingRequest(fetchHttpConfiguration(clientId));
    }

    public RoutingRequest buildRoutingRequest(final HttpConfiguration httpConfiguration) {
        return httpConfiguration.isUsingRegistry() ? RoutingRequest.builder()
                .clientId(DiscoveryProperties.getOlympusImComponentInstanceId())
                .componentId(httpConfiguration.getServiceComponentId())
                .headers(Collections.emptyMap())
                .build() : null;
    }

}