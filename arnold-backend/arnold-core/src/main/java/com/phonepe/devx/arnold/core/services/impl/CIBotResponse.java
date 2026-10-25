package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@Builder
@NoArgsConstructor
public class CIBotResponse<T> {
    @JsonProperty("success")
    @Builder.Default
    private boolean success = true;

    @JsonProperty("response")
    private T response;

    @JsonProperty("errorCode")
    private int errorCode;

    @JsonProperty("errorMessage")
    private String errorMessage;

    public static <T> CIBotResponse<T> ok(T response) {
        return CIBotResponse.<T>builder()
                .response(response)
                .build();
    }

    public static <T> CIBotResponse<T> error(int errorCode, String errorMessage) {
        return CIBotResponse.<T>builder()
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}