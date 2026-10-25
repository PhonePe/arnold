package com.phonepe.arnold.core.storage;

public record VersionedValue<T>(T value, Object version) {
}
