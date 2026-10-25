package com.phonepe.devx.arnold.server;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.hystrix.configurator.config.HystrixConfig;
import com.phonepe.platform.dw.bundle.core.PlatformBundleConfig;
import io.dropwizard.Configuration;
import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
@JsonDeserialize(as = ArnoldConfiguration.class)
public abstract class BaseConfiguration extends Configuration {

    @Valid
    @NotNull
    private HystrixConfig hystrixConfig = new HystrixConfig();

    @Valid
    @NotNull
    private PlatformBundleConfig platformBundle = new PlatformBundleConfig();

}
