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
import java.util.ArrayList;
import java.util.List;

@Singleton
@Slf4j
@AllArgsConstructor(onConstructor_ = @Inject)
public class AnalysisExecutionService {
    private static final String COPILOT = "copilot";
    private static final String OPENCODE = "opencode";
    private static final String OPENCODE_NAME = "OpenCode";
    private final ArnoldCoreConfiguration config;
    private final CopilotConnectionService copilotConnection;
    private final GodricService godric;

    public List<HarnessOption> options(final String user) {
        final var options = new ArrayList<HarnessOption>();
        options.add(option(user, COPILOT));
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
        if (COPILOT.equals(harness)) return copilotOption(user);
        if (OPENCODE.equals(harness)) return opencodeOption();
        throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
    }

    private HarnessOption copilotOption(final String user) {
        if (!config.getCopilotConfiguration().isEnabled() || !config.getCopilotConfiguration().isJobTokenExchangeEnabled()) {
            return unavailable("Copilot execution is not enabled by your administrator.");
        }
        if (!copilotConnection.status(user).connected()) return unavailable("Connect and verify your Copilot account in Settings.");
        try {
            final var models = copilotConnection.models(user);
            return new HarnessOption(COPILOT, "GitHub Copilot", !models.isEmpty(),
                    models.isEmpty() ? "No available tool-capable Copilot models were returned." : "Models available to your connected Copilot account.", models);
        } catch (ArnoldError failure) {
            log.warn("Copilot models unavailable: errorCode={}", failure.getErrorCode());
            return unavailable("Could not load Copilot models. Retry or check your connection in Settings.");
        }
    }

    private HarnessOption opencodeOption() {
        if (!config.getOpenCodeConfiguration().isEnabled()) {
            return new HarnessOption(OPENCODE, OPENCODE_NAME, false, "OpenCode execution is not enabled by your administrator.", List.of());
        }
        try {
            final var prefix = "godric/" + config.getOpenCodeConfiguration().getGodricNamespace() + ":";
            final var defaultModel = config.getOpenCodeConfiguration().getDefaultModel();
            final var models = godric.listLLMs().stream()
                    .filter(entity -> entity.active() && (entity.lifecycle() == null
                            || !"REMOVED".equals(entity.lifecycle().state())))
                    .filter(entity -> entity.id() != null && !entity.id().isBlank())
                    .map(entity -> new HarnessOption.Model(prefix + entity.id(),
                            entity.name() == null ? entity.id() : entity.name(),
                            defaultModel != null && defaultModel.equals(prefix + entity.id())))
                    .toList();
            return new HarnessOption(OPENCODE, OPENCODE_NAME, !models.isEmpty(),
                    models.isEmpty() ? "No onboarded models were found in the model gateway." : "Models onboarded in the internal model gateway.", models);
        } catch (ArnoldError failure) {
            log.warn("OpenCode models unavailable: errorCode={}", failure.getErrorCode());
            return new HarnessOption(OPENCODE, OPENCODE_NAME, false, "Could not load OpenCode models. Retry later.", List.of());
        }
    }

    private HarnessOption unavailable(final String message) {
        return new HarnessOption(COPILOT, "GitHub Copilot", false, message, List.of());
    }
}
