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

package com.phonepe.arnold.core.elastic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.phonepe.columbus.Columbus;
import com.phonepe.columbus.dao.id.ValueId;
import com.phonepe.columbus.dao.index.IndexReference;
import com.phonepe.columbus.search.misc.result.Hit;
import com.phonepe.columbus.search.misc.result.Result;
import com.phonepe.columbus.search.query.Query;
import com.phonepe.columbus.search.sort.Sort;
import com.phonepe.columbus.storage.StorageClient;
import com.phonepe.columbus.storage.commands.search.SearchCommand;
import com.phonepe.columbus.storage.models.result.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ESStorageManagerTest {

    @Mock
    private Columbus columbus;

    @Mock
    private StorageClient storageClient;

    private ESStorageManager esStorageManager;

    @BeforeEach
    void setUp() {
        when(columbus.storage("arnold-es-cluster")).thenReturn(storageClient);
        esStorageManager = new ESStorageManager(columbus);
    }

    @Test
    void save_shouldExecuteUpsertCommand_onHappyPath() {
        final var document = new Object();

        esStorageManager.save("TEST_INDEX", "10:20:55", document);

        verify(storageClient).execute(any());
    }

    @Test
    void save_shouldPropagateException_whenStorageClientThrows() {
        doThrow(new RuntimeException("ES unavailable"))
                .when(storageClient).execute(any());

        assertThrows(RuntimeException.class,
                () -> esStorageManager.save("TEST_INDEX", "10:20:55", new Object()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_shouldResolveConfiguredIndex_whenGivenIndexReference() {
        esStorageManager.search("TEST_INDEX", null, new TypeReference<String>() {}, List.of(), List.of(), 0, 0);

        final ArgumentCaptor<SearchCommand<String>> commandCaptor = ArgumentCaptor.forClass(SearchCommand.class);
        verify(storageClient).execute(commandCaptor.capture());
        final var index = (IndexReference) commandCaptor.getValue().getIndexBoosts().keySet().iterator().next();
        assertEquals("TEST_INDEX", index.getKey());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchEntities_shouldReturnEntities_whenSearchReturnsHits() {
        final var id = ValueId.builder().value("1").build();
        final Hit<String> hit1 = Hit.<String>builder().id(id).entity("doc-1").build();
        final Hit<String> hit2 = Hit.<String>builder().id(ValueId.builder().value("2").build()).entity("doc-2").build();
        final Result<String> result = Result.<String>builder().hits(List.of(hit1, hit2)).build();
        final SearchResult<String> searchResult = SearchResult.<String>builder().result(result).build();

        when(storageClient.execute(any())).thenReturn(searchResult);

        final List<String> entities = esStorageManager.searchEntities(
                "TEST_INDEX",
                (Query) null,
                new TypeReference<String>() {},
                (List<Sort>) null,
                10,
                0);

        assertEquals(List.of("doc-1", "doc-2"), entities);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchEntities_shouldReturnEmpty_whenSearchReturnsNull() {
        when(storageClient.execute(any())).thenReturn(null);

        final List<String> entities = esStorageManager.searchEntities(
                "TEST_INDEX",
                (Query) null,
                new TypeReference<String>() {},
                (List<Sort>) null,
                10,
                0);

        assertTrue(entities.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchEntities_shouldReturnEmpty_whenResultIsNull() {
        final SearchResult<String> searchResult = SearchResult.<String>builder().result(null).build();

        when(storageClient.execute(any())).thenReturn(searchResult);

        final List<String> entities = esStorageManager.searchEntities(
                "TEST_INDEX",
                (Query) null,
                new TypeReference<String>() {},
                (List<Sort>) null,
                10,
                0);

        assertTrue(entities.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchEntities_shouldReturnEmpty_whenHitsNull() {
        final Result<String> result = Result.<String>builder().hits(null).build();
        final SearchResult<String> searchResult = SearchResult.<String>builder().result(result).build();

        when(storageClient.execute(any())).thenReturn(searchResult);

        final List<String> entities = esStorageManager.searchEntities(
                "TEST_INDEX",
                (Query) null,
                new TypeReference<String>() {},
                (List<Sort>) null,
                10,
                0);

        assertTrue(entities.isEmpty());
    }

}
