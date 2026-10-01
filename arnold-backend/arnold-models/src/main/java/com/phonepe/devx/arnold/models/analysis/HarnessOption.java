package com.phonepe.devx.arnold.models.analysis;

import java.util.List;

public record HarnessOption(String id, String name, boolean available, String message, List<Model> models) {
    public record Model(String id, String name) { }
}
