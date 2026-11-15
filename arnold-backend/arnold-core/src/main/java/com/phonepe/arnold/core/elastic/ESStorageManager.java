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

package com.phonepe.arnold.core.elastic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.columbus.Columbus;
import com.phonepe.columbus.dao.id.ValueId;
import com.phonepe.columbus.dao.index.Indices;
import com.phonepe.columbus.search.aggregator.Aggregator;
import com.phonepe.columbus.search.misc.result.Hit;
import com.phonepe.columbus.search.misc.result.Result;
import com.phonepe.columbus.search.page.PageOffset;
import com.phonepe.columbus.search.query.Query;
import com.phonepe.columbus.search.sort.Sort;
import com.phonepe.columbus.storage.StorageClient;
import com.phonepe.columbus.storage.commands.entity.UpsertCommand;
import com.phonepe.columbus.storage.commands.search.SearchCommand;
import com.phonepe.columbus.storage.models.result.SearchResult;
import com.phonepe.columbus.type.JavaType;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Singleton
public class ESStorageManager {

    private static final String ES_CLUSTER_NAME = "arnold-es-cluster";
    private final StorageClient storageClient;

    @Inject
    public ESStorageManager(final Columbus columbus) {
        final var safeColumbus = Objects.requireNonNull(columbus, "Columbus is not initialized");
        this.storageClient = Objects.requireNonNull(
                safeColumbus.storage(ES_CLUSTER_NAME),
                "StorageClient is not configured for cluster: " + ES_CLUSTER_NAME);
    }

    public <T> void save(String indexReference, String id, T document) {
        final var command = UpsertCommand.withValue(document)
                .index(Indices.reference(indexReference))
                .id(ValueId.builder().value(id).build())
                .build();
        storageClient.execute(command);
        log.info("Saved document with id: {} in index with reference: {}", id, indexReference);
    }

    public <T> SearchResult<T> search(String indexReference,
                                      Query query,
                                      TypeReference<T> typeReference,
                                      List<Sort> sorts,
                                      int pageSize,
                                      int offset) {
        return search(indexReference, query, typeReference, sorts, List.of(), pageSize, offset);
    }

    public <T> SearchResult<T> search(String indexReference,
                                      Query query,
                                      TypeReference<T> typeReference,
                                      List<Sort> sorts,
                                      List<Aggregator> aggregators,
                                      int pageSize,
                                      int offset) {
        final var command = SearchCommand.forType(JavaType.of(typeReference))
                .index(Indices.reference(indexReference))
                .query(query)
                .sorts(sorts)
                .aggregators(aggregators)
                .page(PageOffset.builder().size(pageSize).offset(offset).build())
                .build();
        return storageClient.execute(command);
    }

    public <T> List<T> searchEntities(String indexReference,
                                      Query query,
                                      TypeReference<T> typeReference,
                                      List<Sort> sorts,
                                      int pageSize,
                                      int offset) {
        return Optional.ofNullable(search(indexReference, query, typeReference, sorts, pageSize, offset))
                .map(SearchResult::getResult)
                .map(Result::getHits)
                .stream()
                .flatMap(Collection::stream)
                .map(Hit::getEntity)
                .toList();
    }
}
