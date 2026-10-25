package com.phonepe.devx.arnold.server;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import in.vectorpro.dropwizard.swagger.SwaggerBundleConfiguration;
import io.dropwizard.Configuration;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonDeserialize(as = ArnoldConfiguration.class)
public abstract class BaseConfiguration extends Configuration {

    @Valid
    @NotNull
    private SwaggerBundleConfiguration swagger = defaultSwagger();

    private static SwaggerBundleConfiguration defaultSwagger() {
        final var swagger = new SwaggerBundleConfiguration();
        swagger.setTitle("Arnold");
        swagger.setResourcePackage("com.phonepe.devx.arnold.core.resources");
        return swagger;
    }
}
