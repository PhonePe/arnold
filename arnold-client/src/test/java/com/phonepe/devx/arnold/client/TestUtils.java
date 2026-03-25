package com.phonepe.devx.arnold.client;

import com.phonepe.platform.http.v2.common.HttpConfiguration;
import com.phonepe.platform.http.v2.common.hub.HttpRangerHubConfiguration;
import lombok.experimental.UtilityClass;

import java.util.Set;

@UtilityClass
public class TestUtils {

    public HttpRangerHubConfiguration validHubConfig(String clientId) {
        return HttpRangerHubConfiguration
                .builder()
                .services(Set.of(sampleHttpConfig(clientId)))
                .build();
    }

    public HttpConfiguration sampleHttpConfig(String clientId) {
        return HttpConfiguration.builder()
                .host("localhost")
                .clientId("test")
                .port(8080)
                .connections(10)
                .opTimeoutMs(1000)
                .connectTimeoutMs(1000)
                .clientId(clientId)
                .build();
    }
}
