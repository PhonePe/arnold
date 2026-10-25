package com.phonepe.devx.arnold.core.services;

import java.util.List;

/**
 * Lists the LLM models available to the OpenCode harness.
 */
public interface ModelCatalog {

    List<CatalogModel> models();

    record CatalogModel(String id, String name) { }
}
