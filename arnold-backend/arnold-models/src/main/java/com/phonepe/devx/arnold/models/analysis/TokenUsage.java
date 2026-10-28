package com.phonepe.devx.arnold.models.analysis;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * LLM tokens a Clio flow job consumed, as reported by the executing harness, split by model. {@code jobUrl} links
 * the CI job that reported it.
 */
public record TokenUsage(@PositiveOrZero long inputTokens,
                         @PositiveOrZero long outputTokens,
                         @Valid @Size(max = 20) List<@NotNull Model> models,
                         @Size(max = 2048) @Pattern(regexp = "https://\\S+") String jobUrl) {
    public TokenUsage {
        models = models == null ? List.of() : List.copyOf(models);
    }

    public TokenUsage(final long inputTokens, final long outputTokens, final List<Model> models) {
        this(inputTokens, outputTokens, models, null);
    }

    /** Sums usages per model, keeping the job link only for a single usage; {@code null} when none were reported. */
    public static TokenUsage merge(final Collection<TokenUsage> usages) {
        final var reported = usages.stream().filter(Objects::nonNull).toList();
        if (reported.isEmpty()) return null;
        final var models = new TreeMap<String, long[]>();
        reported.forEach(usage -> usage.models().forEach(model -> {
            final var totals = models.computeIfAbsent(model.model(), ignored -> new long[2]);
            totals[0] += model.inputTokens();
            totals[1] += model.outputTokens();
        }));
        return new TokenUsage(reported.stream().mapToLong(TokenUsage::inputTokens).sum(),
                reported.stream().mapToLong(TokenUsage::outputTokens).sum(),
                models.entrySet().stream().map(entry -> new Model(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1])).toList(), reported.size() == 1 ? reported.get(0).jobUrl() : null);
    }

    public record Model(@NotBlank @Size(max = 200) String model,
                        @PositiveOrZero long inputTokens,
                        @PositiveOrZero long outputTokens) { }
}
