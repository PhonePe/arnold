package com.phonepe.arnold.core.cluster;

import javax.inject.Singleton;
import java.lang.management.ManagementFactory;
import java.util.UUID;

@Singleton
public class ClusterNodeIdentity {
    private final String id = ManagementFactory.getRuntimeMXBean().getName() + ":" + UUID.randomUUID();

    public String id() {
        return id;
    }
}
