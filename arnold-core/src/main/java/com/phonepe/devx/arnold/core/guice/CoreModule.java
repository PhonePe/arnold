package com.phonepe.devx.arnold.core.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.phonepe.olympus.im.client.OlympusIMBundle;
import com.phonepe.olympus.im.client.OlympusIMClient;
import com.phonepe.devx.arnold.core.guice.bindings.OlympusAuthSupplier;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.core.services.SampleService;
import com.phonepe.devx.arnold.core.services.impl.CibotServiceHttpImpl;
import com.phonepe.devx.arnold.core.services.impl.SampleServiceHttpImpl;

import javax.inject.Singleton;
import java.util.function.Supplier;
import io.dropwizard.Configuration;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class CoreModule<T extends Configuration> extends AbstractModule {
    private final OlympusIMBundle<T> olympusIMBundle;

    @Override
    protected void configure() {
        bind(SampleService.class).to(SampleServiceHttpImpl.class);
        bind(CibotService.class).to(CibotServiceHttpImpl.class);
    }

    @Provides
    @Singleton
    @OlympusAuthSupplier
    public Supplier<String> olympusAuthSupplier() {
        return () -> olympusIMBundle.getOlympusIMClient().getSystemAuthHeader();
    }

    @Provides
    @Singleton
    public OlympusIMClient olympusIMClient() {
        return olympusIMBundle.getOlympusIMClient();
    }
}

