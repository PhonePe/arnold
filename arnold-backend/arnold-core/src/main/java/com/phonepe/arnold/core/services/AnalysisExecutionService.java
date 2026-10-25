package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.services.copilot.CopilotConnectionService;
import com.phonepe.arnold.models.analysis.ExecutionOptions;
import com.phonepe.arnold.models.analysis.HarnessOption;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Slf4j
@AllArgsConstructor(onConstructor_ = @Inject)
public class AnalysisExecutionService {
    private final ArnoldCoreConfiguration config;
    private final CopilotConnectionService copilot;
    private final ModelCatalog modelCatalog;

    public List<HarnessOption> options(final String user) {
        final var options = new ArrayList<HarnessOption>();
        options.add(option(user, "copilot"));
        options.add(opencodeOption());
        return options;
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
        if ("copilot".equals(harness)) return copilotOption(user);
        if ("opencode".equals(harness)) return opencodeOption();
        throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
    }

    private HarnessOption copilotOption(final String user) {
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

    private HarnessOption opencodeOption() {
        if (!config.getOpenCodeConfiguration().isEnabled()) {
            return new HarnessOption("opencode", "OpenCode", false, "OpenCode execution is not enabled by your administrator.", List.of());
        }
        try {
            final var prefix = config.getOpenCodeConfiguration().getProvider() + "/";
            final var defaultModel = config.getOpenCodeConfiguration().getDefaultModel();
            final var models = modelCatalog.models().stream()
                    .map(model -> new HarnessOption.Model(prefix + model.id(), model.name(),
                            defaultModel != null && defaultModel.equals(prefix + model.id())))
                    .toList();
            return new HarnessOption("opencode", "OpenCode", !models.isEmpty(),
                    models.isEmpty() ? "No models were found in the configured model catalog." : "Models available from the configured model catalog.", models);
        } catch (ArnoldError failure) {
            log.warn("OpenCode models unavailable: errorCode={}", failure.getErrorCode());
            return new HarnessOption("opencode", "OpenCode", false, "Could not load OpenCode models. Retry later.", List.of());
        }
    }

    private HarnessOption unavailable(final String message) {
        return new HarnessOption("copilot", "GitHub Copilot", false, message, List.of());
    }
}
