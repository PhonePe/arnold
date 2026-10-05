package com.phonepe.devx.arnold.core.config;

import com.phonepe.platform.http.v2.common.HttpConfiguration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SlackClientConfig extends HttpConfiguration {
    private boolean enabled;
    @ToString.Exclude
    private String botToken;
}
