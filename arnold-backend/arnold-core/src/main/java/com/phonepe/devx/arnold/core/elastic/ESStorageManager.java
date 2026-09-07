package com.phonepe.devx.arnold.core.elastic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.commons.columbus.Columbus;
import com.phonepe.commons.columbus.dao.id.ValueId;
import com.phonepe.commons.columbus.dao.index.Indices;
import com.phonepe.commons.columbus.search.aggregator.Aggregator;
import com.phonepe.commons.columbus.search.misc.result.Hit;
import com.phonepe.commons.columbus.search.misc.result.Result;
import com.phonepe.commons.columbus.search.page.PageOffset;
import com.phonepe.commons.columbus.search.query.Query;
import com.phonepe.commons.columbus.search.sort.Sort;
import com.phonepe.commons.columbus.storage.StorageClient;
import com.phonepe.commons.columbus.storage.commands.entity.UpsertCommand;
import com.phonepe.commons.columbus.storage.commands.search.SearchCommand;
import com.phonepe.commons.columbus.storage.models.result.SearchResult;
import com.phonepe.commons.columbus.type.JavaType;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    private <T> SearchResult<T> searchReference(String indexReference,
                                                Query query,
                                                TypeReference<T> typeReference,
                                                List<Sort> sorts,
                                                int pageSize,
                                                int offset) {
        final var command = SearchCommand.forType(JavaType.of(typeReference))
                .index(Indices.reference(indexReference))
                .query(query)
                .sorts(sorts)
                .aggregators(List.of())
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
        return Optional.ofNullable(searchReference(indexReference, query, typeReference, sorts, pageSize, offset))
                .map(SearchResult::getResult)
                .map(Result::getHits)
                .stream()
                .flatMap(Collection::stream)
                .map(Hit::getEntity)
                .toList();
    }
}
