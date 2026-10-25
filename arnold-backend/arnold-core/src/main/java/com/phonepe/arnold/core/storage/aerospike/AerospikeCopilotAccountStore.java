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

package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.model.copilot.CopilotAccount;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeCopilotAccountStore extends AerospikeStore<CopilotAccount> {
    @Inject
    public AerospikeCopilotAccountStore(final IAerospikeClient client, final ArnoldCoreConfiguration config,
                                        final ObjectMapper mapper) {
        super(client, new NamespaceSet(config.getAerospikeStorageConfiguration().oauthStateSet().namespace(),
                config.getCopilotConfiguration().getCredentialSet()), mapper, CopilotAccount.class,
                new DefaultErrorHandler<>());
    }
}
