package com.phonepe.devx.arnold.core.config;

import com.livetheoogway.crudstore.aerospike.NamespaceSet;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public record AerospikeStorageConfiguration(
        @NotNull
        @NotEmpty NamespaceSet userSet,
        @NotNull
        @NotEmpty NamespaceSet reviewSet
) {
}
