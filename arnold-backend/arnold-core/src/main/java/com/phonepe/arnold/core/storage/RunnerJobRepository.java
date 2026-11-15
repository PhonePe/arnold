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

import com.livetheoogway.crudstore.core.ConcurrentStore;
import com.livetheoogway.crudstore.core.Store;
import com.phonepe.arnold.core.model.runner.RunnerJob;

public interface RunnerJobRepository extends Store<RunnerJob>, ConcurrentStore<RunnerJob, Long> {
}
