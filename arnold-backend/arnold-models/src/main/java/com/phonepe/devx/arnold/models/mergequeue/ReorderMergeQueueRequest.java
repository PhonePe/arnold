package com.phonepe.devx.arnold.models.mergequeue;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;

@Value
@Builder
@Jacksonized
public class ReorderMergeQueueRequest {
    @NotEmpty
    List<String> orderedItemIds;
    @PositiveOrZero
    long version;
}
