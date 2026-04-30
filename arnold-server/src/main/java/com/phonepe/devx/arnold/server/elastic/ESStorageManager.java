package com.phonepe.devx.arnold.server.elastic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.commons.columbus.Columbus;
import com.phonepe.commons.columbus.dao.id.ValueId;
import com.phonepe.commons.columbus.dao.index.IndexName;
import com.phonepe.commons.columbus.search.page.PageOffset;
import com.phonepe.commons.columbus.search.query.Query;
import com.phonepe.commons.columbus.search.sort.Sort;
import com.phonepe.commons.columbus.storage.StorageClient;
import com.phonepe.commons.columbus.storage.commands.entity.UpsertCommand;
import com.phonepe.commons.columbus.storage.commands.search.SearchCommand;
import com.phonepe.commons.columbus.storage.models.result.SearchResult;
import com.phonepe.commons.columbus.type.JavaType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Singleton
public class ESStorageManager {

    private final StorageClient storageClient;

    private static final String ES_CLUSTER_NAME = "arnold-es-cluster";

    @Inject
    public ESStorageManager(final Columbus columbus) {
        final var safeColumbus = Objects.requireNonNull(columbus, "Columbus is not initialized");
        this.storageClient = Objects.requireNonNull(
                safeColumbus.storage(ES_CLUSTER_NAME),
                "StorageClient is not configured for cluster: " + ES_CLUSTER_NAME);
    }

    public <T> void save(String indexName, String id, T document) {
        final var command = UpsertCommand.withValue(document)
                .index(IndexName.builder().name(indexName).build())
                .id(ValueId.builder().value(id).build())
                .build();
        storageClient.execute(command);
        log.info("Saved document with id: {} in index: {}", id, indexName);
    }

    public <T> SearchResult<T> search(String indexName,
                                      Query query,
                                      TypeReference<T> typeReference,
                                      List<Sort> sorts,
                                      int pageSize,
                                      int offset) {
        final var command = SearchCommand.forType(JavaType.of(typeReference))
                .indexBoosts(Map.of(IndexName.builder().name(indexName).build(), 1.0f))
                .query(query)
                .sorts(sorts)
                .page(PageOffset.builder().size(pageSize).offset(offset).build())
                .build();
        return storageClient.execute(command);
    }
}
