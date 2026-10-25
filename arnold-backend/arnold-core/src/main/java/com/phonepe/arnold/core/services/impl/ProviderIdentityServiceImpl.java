/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.services.impl;

import com.phonepe.arnold.core.model.vcs.ProviderIdentityLink;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.ProviderIdentityService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeProviderIdentityLinkStore;

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
