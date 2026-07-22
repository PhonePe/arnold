package com.phonepe.devx.arnold.core.clients;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare external clients.
 * The value should be the name of the service as defined in the rangerHubConfiguration.
 * The stitching during startup will happen automatically. Refer {@link ClientHttpExecutorFactory} and {@link com.phonepe.devx.arnold.server.guice.ClientModule}for more details.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ClientDeclaration {
    String value();
}
