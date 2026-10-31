package com.phonepe.devx.arnold.core.clients;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.phonepe.platform.http.v2.executor.factory.HttpExecutorBuilderFactory;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public class ClientHttpExecutorFactory {

    @Inject
    @Named(BindingNames.CLIENT_FACTORY)
    private Map<String, HttpExecutorBuilderFactory> executorFactory;

    public HttpExecutorBuilderFactory getExecutor(final String serviceClientName) {
        return executorFactory.get(serviceClientName);
    }
}
