package com.phonepe.devx.arnold.core.config;

import javax.validation.constraints.NotBlank;

public record GitLabSyncConfiguration(@NotBlank String webhookSecret) {
}
