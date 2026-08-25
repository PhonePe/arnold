package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.model.vcs.ProviderIdentityLink;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.ProviderIdentityService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeProviderIdentityLinkStore;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class ProviderIdentityServiceImpl implements ProviderIdentityService {
    private final AerospikeProviderIdentityLinkStore store;

    @Inject
    public ProviderIdentityServiceImpl(final AerospikeProviderIdentityLinkStore store) {
        this.store = store;
    }

    @Override
    public void link(final VersionControlProviderType provider,
                     final String providerUserId,
                     final String userId) {
        final var id = ProviderIdentityLink.key(provider, providerUserId);
        final var link = new ProviderIdentityLink(id, provider, providerUserId, userId,
                System.currentTimeMillis());
        if (store.get(id).isPresent()) {
            store.update(link);
            return;
        }
        try {
            store.create(link);
        } catch (RuntimeException race) {
            store.update(link);
        }
    }

    @Override
    public Optional<String> findUserId(final VersionControlProviderType provider,
                                       final String providerUserId) {
        return store.get(ProviderIdentityLink.key(provider, providerUserId))
                .map(ProviderIdentityLink::userId);
    }
}
