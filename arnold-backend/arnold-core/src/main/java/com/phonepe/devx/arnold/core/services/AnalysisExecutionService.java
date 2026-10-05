package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.services.copilot.CopilotConnectionService;
import com.phonepe.devx.arnold.models.analysis.ExecutionOptions;
import com.phonepe.devx.arnold.models.analysis.HarnessOption;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
@Slf4j
@AllArgsConstructor(onConstructor_ = @Inject)
public class AnalysisExecutionService {
    private final ArnoldCoreConfiguration config;
    private final CopilotConnectionService copilot;

    public List<HarnessOption> options(final String user) {
        return List.of(option(user, "copilot"));
    }

    public void validate(final String user, final ExecutionOptions execution) {
        if (execution == null) return;
        validated(user, execution);
    }

    public HarnessOption validated(final String user, final ExecutionOptions execution) {
        final var option = option(user, execution.harness());
        if (!option.available() || (execution.model() != null && option.models().stream()
                .noneMatch(model -> model.id().equals(execution.model())))) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        return option;
    }

    public static String defaultModel(final HarnessOption option) {
        return option.models().stream().filter(HarnessOption.Model::isDefault).map(HarnessOption.Model::id)
                .findFirst().orElseGet(() -> option.models().stream().map(HarnessOption.Model::id).findFirst().orElse(null));
    }

    private HarnessOption option(final String user, final String harness) {
        if (!"copilot".equals(harness)) throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        if (!config.getCopilotConfiguration().isEnabled() || !config.getCopilotConfiguration().isJobTokenExchangeEnabled()) {
            return unavailable("Copilot execution is not enabled by your administrator.");
        }
        if (!copilot.status(user).connected()) return unavailable("Connect and verify your Copilot account in Settings.");
        try {
            final var models = copilot.models(user);
            return new HarnessOption("copilot", "GitHub Copilot", !models.isEmpty(),
                    models.isEmpty() ? "No available tool-capable Copilot models were returned." : "Models available to your connected Copilot account.", models);
        } catch (ArnoldError failure) {
            log.warn("Copilot models unavailable: errorCode={}", failure.getErrorCode());
            return unavailable("Could not load Copilot models. Retry or check your connection in Settings.");
        }
    }

    private HarnessOption unavailable(final String message) {
        return new HarnessOption("copilot", "GitHub Copilot", false, message, List.of());
    }
}
