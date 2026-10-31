package com.phonepe.devx.arnold.models.mergequeue;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;

@Value
@Builder
@Jacksonized
public class CreateMergeRequestGroupRequest {
    @NotBlank
    String name;
    @NotEmpty
    List<String> mergeRequestIds;
    @PositiveOrZero
    long version;
}
