/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

package com.phonepe.arnold.core.storage;

import com.phonepe.arnold.core.model.cluster.ClusterControlRecord;

import java.util.Optional;

public interface ClusterControlStore {
    Optional<ClusterControlRecord> get(String id);

    Optional<VersionedValue<ClusterControlRecord>> getVersioned(String id);

    void create(ClusterControlRecord record);

    void update(ClusterControlRecord record, Object expectedVersion);
}
