package com.phonepe.arnold.models.mergerequest;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;

@Value
@Builder
@Jacksonized
public class ResolveMergeRequestRequest {
    String provider;

    @NotBlank
    String url;
}
