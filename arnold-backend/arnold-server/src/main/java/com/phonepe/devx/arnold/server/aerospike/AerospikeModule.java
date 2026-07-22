package com.phonepe.devx.arnold.server.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.livetheoogway.crudstore.aerospike.AerospikeClientHelpers;
import com.phonepe.devx.arnold.server.ArnoldConfiguration;

import javax.inject.Singleton;

public class AerospikeModule extends AbstractModule {

    @Provides
    @Singleton
    public IAerospikeClient aerospikeClient(final ArnoldConfiguration appConfig) {
        return AerospikeClientHelpers.aerospikeClient((appConfig.getAerospike()));
    }
}
