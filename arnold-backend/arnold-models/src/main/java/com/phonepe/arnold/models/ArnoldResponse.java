package com.phonepe.arnold.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class ArnoldResponse<T> {
    @Builder.Default
    private boolean success = true;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T response;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer errorCode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String errorMessage;

    public static <T> ArnoldResponse<T> ok(final T response) {
        return ArnoldResponse.<T>builder()
                .response(response)
                .build();
    }
}