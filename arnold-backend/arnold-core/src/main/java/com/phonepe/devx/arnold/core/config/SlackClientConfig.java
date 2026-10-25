package com.phonepe.devx.arnold.core.config;

import com.phonepe.devx.arnold.core.clients.HttpClientSettings;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SlackClientConfig extends HttpClientSettings {
    private boolean enabled;
    @ToString.Exclude
    private String botToken;
    private String uiBaseUrl;

    @Override
    public String getUrl() {
        return super.getUrl() == null || super.getUrl().isBlank() ? "https://slack.com" : super.getUrl();
    }
}
