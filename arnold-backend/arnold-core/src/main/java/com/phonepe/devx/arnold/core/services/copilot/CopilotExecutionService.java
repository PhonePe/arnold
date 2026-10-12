package com.phonepe.devx.arnold.core.services.copilot;
import com.phonepe.devx.arnold.core.services.AnalysisExecutionService;
import com.phonepe.devx.arnold.models.analysis.ExecutionOptions;
import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.copilot.CopilotExecutionGrant;
import com.phonepe.devx.arnold.core.model.review.SmartDiffRecord;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeGroupAnalysisRunStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeSmartDiffStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCopilotExecutionGrantStore;
import com.phonepe.devx.arnold.models.integration.CopilotExecutionTokenResponse;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Singleton
public class CopilotExecutionService {
    public static final String KEY_VARIABLE = "ARNOLD_COPILOT_KEY";
    private static final Set<String> ACTIVE = Set.of("queued", "running");
    private static final long TTL = 3 * 60 * 60 * 1000L;
    private final AnalysisExecutionService catalog;
    private final ArnoldCoreConfiguration config;
    private final CopilotConnectionService connections;
    private final AerospikeCopilotExecutionGrantStore grants;
    private final AerospikeAnalysisRunStore analyses;
    private final AerospikeGroupAnalysisRunStore groups;
    private final AerospikeSmartDiffStore stories;

    @Inject
    public CopilotExecutionService(final ArnoldCoreConfiguration config, final CopilotConnectionService connections,
                                  final AerospikeCopilotExecutionGrantStore grants, final AerospikeAnalysisRunStore analyses,
                                  final AerospikeGroupAnalysisRunStore groups, final AerospikeSmartDiffStore stories,
                                  final AnalysisExecutionService catalog) {
        this.catalog = catalog;
        this.config = config;
        this.connections = connections;
        this.grants = grants;
        this.analyses = analyses;
        this.groups = groups;
        this.stories = stories;
    }

    public Map<String, String> pipelineVariables(final Map<String, String> original) {
        final var variables = new HashMap<>(original);
        final var owner = variables.remove("ARNOLD_COPILOT_OWNER");
        final var enabled = config.getCopilotConfiguration().isJobTokenExchangeEnabled();
        final var workflow = variables.get("WORKFLOW");
        final String user;
        final String run;
        final String execution;
        ExecutionOptions selection = null;
        switch (workflow == null ? "" : workflow) {
            case "improve" -> {
                final var analysis = analyses.get(variables.get("ARNOLD_RUN_ID")).orElseThrow(CopilotExecutionService::denied);
                selection = analysis.execution();
                user = analysis.invokedBy();
                run = analysis.id();
                execution = run;
            }
            case "group_review" -> {
                final var group = groups.get(variables.get("ARNOLD_RUN_ID")).orElseThrow(CopilotExecutionService::denied);
                if (group.publicationOnly()) return variables;
                selection = group.context() == null ? null : group.context().execution();
                user = group.invokedBy();
                run = group.id();
                execution = variables.get("EXECUTION_ID");
            }
            case "smart_diff" -> {
                if (!enabled) return variables;
                require(owner != null && !owner.isBlank());
                user = owner;
                run = SmartDiffRecord.key(Long.parseLong(variables.get("PROJECT_ID")),
                        Long.parseLong(variables.get("MR_IID")), variables.get("EXPECTED_HEAD_SHA"));
                execution = variables.get("EXECUTION_ID");
                final var story = stories.get(run).orElseThrow(CopilotExecutionService::denied);
                if (story.execution() != null) {
                    require(owner.equals(story.requestedBy()));
                    selection = story.execution();
                }
            }
            default -> { return variables; }
        }
        if (selection != null) {
            final var option = catalog.validated(user, selection);
            require(selection.harness() != null && Set.of("copilot", "opencode").contains(selection.harness()));
            require(selection.isModelCompatible());
            final var isCopilot = "copilot".equals(selection.harness());
            var model = selection.model();
            if (model == null && isCopilot) model = AnalysisExecutionService.defaultModel(option);
            require(model == null || model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}"));
            variables.put("CLIO_HARNESS", selection.harness());
            variables.put("CLIO_DISABLE_FALLBACK", "true");
            if (model != null) variables.put(isCopilot ? "CLIO_LLM_MODEL" : "CLIO_OPENCODE_MODEL", model);
            if (!isCopilot) return variables;
            require(enabled);
        }
        if (!enabled) return variables;
        require(config.getCopilotConfiguration().isEnabled());
        require(user != null && !user.isBlank() && execution != null && !execution.isBlank());
        final var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        final var key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        final var grant = new CopilotExecutionGrant(hash(key), user, connections.executionConnectionId(user),
                workflow, run, execution, System.currentTimeMillis() + TTL);
        require(active(grant));
        grants.create(grant);
        variables.put(KEY_VARIABLE, key);
        variables.put("CLIO_HARNESS", "copilot");
        return variables;
    }

    public CopilotExecutionTokenResponse exchange(final String key) {
        require(config.getCopilotConfiguration().isEnabled() && config.getCopilotConfiguration().isJobTokenExchangeEnabled());
        require(key != null && key.matches("[A-Za-z0-9_-]{43}"));
        final var grant = grants.get(hash(key)).orElseThrow(CopilotExecutionService::denied);
        require(active(grant));
        final var token = connections.executionToken(grant.userId(), grant.connectionId());
        // Refresh may call GitHub; recheck execution state before releasing its credential.
        require(active(grant));
        return token;
    }

    public void revoke(final Map<String, String> variables) {
        final var key = variables.get(KEY_VARIABLE);
        if (key != null) grants.delete(hash(key));
    }

    private boolean active(final CopilotExecutionGrant grant) {
        final var now = System.currentTimeMillis();
        if (grant.expiresAt() <= now) return false;
        return switch (grant.workflow()) {
            case "improve" -> analyses.get(grant.runId()).filter(run -> grant.userId().equals(run.invokedBy())
                    && ACTIVE.contains(run.status()) && now - run.createdAt() < TTL).isPresent();
            case "group_review" -> groups.get(grant.runId()).filter(run -> grant.userId().equals(run.invokedBy())
                    && grant.executionId().equals(run.result().executionId()) && ACTIVE.contains(run.result().runStatus())
                    && now - run.executionStartedAt() < TTL).isPresent();
            case "smart_diff" -> stories.get(grant.runId()).filter(run -> grant.executionId().equals(run.executionId())
                    && ACTIVE.contains(run.status()) && now - run.updatedAt() < 135 * 60 * 1000L).isPresent();
            default -> false;
        };
    }

    static String hash(final String key) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private static void require(final boolean condition) {
        if (!condition) throw denied();
    }

    private static ArnoldError denied() {
        return ArnoldError.raise(ArnoldErrorCode.FORBIDDEN_ERROR).build();
    }
}
