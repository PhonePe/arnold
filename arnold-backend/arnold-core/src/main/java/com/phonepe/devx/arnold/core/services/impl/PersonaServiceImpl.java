package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.persona.PersonaRecord;
import com.phonepe.devx.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.devx.arnold.core.services.PersonaService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikePersonaStore;
import com.phonepe.devx.arnold.models.persona.CreatePersonaRequest;
import com.phonepe.devx.arnold.models.persona.PersonaPolicy;
import com.phonepe.devx.arnold.models.persona.PersonaPrerequisite;
import com.phonepe.devx.arnold.models.persona.PersonaResponse;
import com.phonepe.devx.arnold.models.persona.PersonaRule;
import com.phonepe.devx.arnold.models.persona.PersonaStatus;
import com.phonepe.devx.arnold.models.persona.UpdatePersonaRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

@Singleton
public class PersonaServiceImpl implements PersonaService {
    private static final String SYSTEM_OWNER = "arnold";
    private static final long BUILTIN_VERSION = 1;
    private static final int POLICY_VERSION = 1;
    private static final int MAX_POLICY_YAML_LENGTH = 8_000;
    private static final int MAX_ENTRIES = 20;
    private static final int MAX_CONTRIBUTORS = 20;
    private static final Pattern RULE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    private static final List<PersonaResponse> BUILT_INS = List.of(
            builtIn("netra", "Netra", "Security and compliance reviewer",
                    "Prioritize authentication, authorization, sensitive data exposure, injection, and policy compliance. Report only actionable security risks supported by the changed code.",
                    List.of("security", "compliance")),
            builtIn("hayai", "Hayai", "Performance and hot-path reviewer",
                    "Prioritize algorithmic complexity, unnecessary I/O, N+1 access, allocation pressure, blocking work, and regressions on hot paths. Ignore cosmetic micro-optimizations.",
                    List.of("performance")),
            builtIn("kozo", "Kōzō", "Architecture and contract reviewer",
                    "Prioritize module boundaries, API and schema compatibility, ownership, coupling, and maintainable structure. Flag concrete contract drift and architectural regressions.",
                    List.of("architecture", "contracts")),
            builtIn("shinrai", "Shinrai", "Reliability and resilience reviewer",
                    "Prioritize retries, idempotency, timeouts, concurrency, failure recovery, observability, and data-loss risks. State the exact failure condition for every finding.",
                    List.of("reliability"))
    );

    private final AerospikePersonaStore store;

    @Inject
    public PersonaServiceImpl(final AerospikePersonaStore store) {
        this.store = store;
    }

    @Override
    public List<PersonaResponse> list(final String userId) {
        // ponytail: persona volume is small; add indexed catalog projections when full scans become measurable.
        return java.util.stream.Stream.concat(BUILT_INS.stream(), store.list().stream()
                .filter(persona -> persona.status() == PersonaStatus.PUBLISHED
                        || persona.contributorIds().contains(userId))
                        .map(PersonaServiceImpl::response))
                .sorted(Comparator.comparing(PersonaResponse::systemOwned).reversed()
                        .thenComparing(Comparator.comparingLong(PersonaResponse::updatedAt).reversed()))
                .toList();
    }

    @Override
    public PersonaResponse get(final String userId, final String personaId) {
        return builtIn(personaId).orElseGet(() -> response(visible(userId, personaId)));
    }

    @Override
    public PersonaResponse create(final String userId, final CreatePersonaRequest request) {
        final var now = System.currentTimeMillis();
        final var policy = normalize(request.getPolicy());
        final var persona = new PersonaRecord(UUID.randomUUID().toString(), request.getName().trim(),
                request.getSummary().trim(), yaml(policy), policy, tags(request.getTags()), userId,
                contributors(userId, request.getContributorIds()), PersonaStatus.DRAFT, 1, now, now);
        store.create(persona);
        return response(persona);
    }

    @Override
    public PersonaResponse update(final String userId, final String personaId, final UpdatePersonaRequest request) {
        final var policy = normalize(request.getPolicy());
        return mutate(userId, personaId, request.getVersion(), current -> new PersonaRecord(current.id(),
                request.getName().trim(), request.getSummary().trim(), yaml(policy), policy, tags(request.getTags()),
                current.ownerId(), request.getContributorIds() == null ? current.contributorIds()
                        : contributors(request.getContributorIds()), current.status(), current.version() + 1,
                current.createdAt(), System.currentTimeMillis()));
    }

    @Override
    public PersonaResponse publish(final String userId, final String personaId, final long version) {
        return mutate(userId, personaId, version, current -> withStatus(current, PersonaStatus.PUBLISHED));
    }

    @Override
    public PersonaResponse unpublish(final String userId, final String personaId, final long version) {
        return mutate(userId, personaId, version, current -> withStatus(current, PersonaStatus.DRAFT));
    }

    @Override
    public void delete(final String userId, final String personaId, final long version) {
        final var current = managed(userId, personaId);
        if (current.value().version() != version) conflict();
        if (current.value().status() != PersonaStatus.DRAFT) {
            throw ArnoldError.raise(ArnoldErrorCode.OPERATION_NOT_ALLOWED).build();
        }
        store.delete(personaId);
    }

    @Override
    public List<ReviewPersonaSnapshot> resolveForAnalysis(final String userId, final List<String> personaIds) {
        if (personaIds == null || personaIds.isEmpty()) return List.of();
        final var distinctIds = new java.util.LinkedHashSet<>(personaIds);
        if (distinctIds.size() != personaIds.size() || distinctIds.size() > 3) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        return distinctIds.stream().map(id -> get(userId, id))
                .map(persona -> new ReviewPersonaSnapshot(persona.id(), persona.name(), persona.instructions(),
                        persona.version()))
                .toList();
    }

    private PersonaResponse mutate(final String userId, final String personaId, final long version,
                                   final Function<PersonaRecord, PersonaRecord> mutation) {
        final var current = managed(userId, personaId);
        if (current.value().version() != version) conflict();
        final var next = mutation.apply(current.value());
        try {
            store.checkAndUpdate(next, current.version());
            return response(next);
        } catch (ConcurrentUpdateException error) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
    }

    private com.livetheoogway.crudstore.core.Versioned<PersonaRecord, Long> managed(
            final String userId, final String personaId) {
        if (builtIn(personaId).isPresent()) {
            throw ArnoldError.raise(ArnoldErrorCode.FORBIDDEN_ERROR).build();
        }
        final var current = store.getWithVersion(personaId)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
        if (!current.value().contributorIds().contains(userId)) {
            throw ArnoldError.raise(ArnoldErrorCode.FORBIDDEN_ERROR).build();
        }
        return current;
    }

    private PersonaRecord visible(final String userId, final String personaId) {
        final var persona = store.get(personaId)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
        if (persona.status() != PersonaStatus.PUBLISHED && !persona.contributorIds().contains(userId)) {
            throw ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build();
        }
        return persona;
    }

    private static PersonaRecord withStatus(final PersonaRecord current, final PersonaStatus status) {
        return new PersonaRecord(current.id(), current.name(), current.summary(), current.instructions(),
                current.policy(), current.tags(), current.ownerId(), current.contributorIds(), status,
                current.version() + 1, current.createdAt(), System.currentTimeMillis());
    }

    private static List<String> tags(final List<String> values) {
        if (values == null) return List.of();
        return values.stream().map(String::trim).map(String::toLowerCase).filter(value -> !value.isBlank())
                .distinct().toList();
    }

    private static java.util.Optional<PersonaResponse> builtIn(final String id) {
        return BUILT_INS.stream().filter(persona -> persona.id().equals(id)).findFirst();
    }

    private static PersonaResponse builtIn(final String id, final String name, final String summary,
                                           final String guidance, final List<String> tags) {
        return builtIn(id, name, summary,
                new PersonaPolicy(POLICY_VERSION, guidance, List.of(), List.of(), List.of()), tags);
    }

    private static PersonaResponse builtIn(final String id, final String name, final String summary,
                                           final PersonaPolicy value, final List<String> tags) {
        final var policy = normalize(value);
        return new PersonaResponse(id, name, summary, yaml(policy), policy, tags, SYSTEM_OWNER, List.of(),
                PersonaStatus.PUBLISHED, true, BUILTIN_VERSION, 0, 0);
    }

    private static PersonaResponse response(final PersonaRecord persona) {
        final var policy = persona.policy() == null
                ? normalize(new PersonaPolicy(POLICY_VERSION, persona.instructions(), List.of(), List.of(), List.of()))
                : normalize(persona.policy());
        return new PersonaResponse(persona.id(), persona.name(), persona.summary(), yaml(policy), policy,
                persona.tags(), persona.ownerId(), persona.contributorIds(), persona.status(), false,
                persona.version(), persona.createdAt(), persona.updatedAt());
    }

    private static List<String> contributors(final String creatorId, final List<String> values) {
        return contributors(java.util.stream.Stream.concat(java.util.stream.Stream.of(creatorId),
                values == null ? java.util.stream.Stream.empty() : values.stream()).toList());
    }

    private static List<String> contributors(final List<String> values) {
        final var contributors = values.stream().map(value -> required(value, 128)).distinct().toList();
        valid(!contributors.isEmpty() && contributors.size() <= MAX_CONTRIBUTORS);
        return contributors;
    }

    private static PersonaPolicy normalize(final PersonaPolicy policy) {
        valid(policy != null && policy.version() == POLICY_VERSION);
        final var guidance = optional(policy.guidance(), 2_000);
        valid(policy.rules().size() <= MAX_ENTRIES && policy.prerequisites().size() <= MAX_ENTRIES
                && policy.ignore().size() <= MAX_ENTRIES);
        final Set<String> ids = new LinkedHashSet<>();
        final var rules = policy.rules().stream().map(rule -> {
            valid(rule != null);
            final var id = id(rule.id(), ids);
            return new PersonaRule(id, required(rule.requirement(), 500));
        }).toList();
        final var prerequisites = policy.prerequisites().stream().map(prerequisite -> {
            valid(prerequisite != null);
            final var id = id(prerequisite.id(), ids);
            return new PersonaPrerequisite(id, required(prerequisite.appliesWhen(), 500),
                    required(prerequisite.requirement(), 500));
        }).toList();
        final var ignore = policy.ignore().stream().map(value -> required(value, 500)).distinct().toList();
        valid(!guidance.isBlank() || !rules.isEmpty() || !prerequisites.isEmpty() || !ignore.isEmpty());
        final var normalized = new PersonaPolicy(POLICY_VERSION, guidance, rules, prerequisites, ignore);
        valid(yaml(normalized).length() <= MAX_POLICY_YAML_LENGTH);
        return normalized;
    }

    private static String id(final String value, final Set<String> ids) {
        final var id = required(value, 128);
        valid(RULE_ID.matcher(id).matches() && ids.add(id));
        return id;
    }

    private static String required(final String value, final int maximum) {
        final var normalized = optional(value, maximum);
        valid(!normalized.isBlank());
        return normalized;
    }

    private static String optional(final String value, final int maximum) {
        final var normalized = value == null ? "" : value.trim();
        valid(normalized.length() <= maximum);
        return normalized;
    }

    private static String yaml(final PersonaPolicy policy) {
        final var yaml = new StringBuilder("version: 1\n");
        if (!policy.guidance().isBlank()) {
            yaml.append("\nguidance: |-\n");
            block(yaml, policy.guidance(), 2);
        }
        if (!policy.rules().isEmpty()) {
            yaml.append("\nrules:\n");
            policy.rules().forEach(rule -> {
                yaml.append("  - id: ").append(rule.id()).append("\n    requirement: |-\n");
                block(yaml, rule.requirement(), 6);
            });
        }
        if (!policy.prerequisites().isEmpty()) {
            yaml.append("\nprerequisites:\n");
            policy.prerequisites().forEach(prerequisite -> {
                yaml.append("  - id: ").append(prerequisite.id()).append("\n    applies_when: |-\n");
                block(yaml, prerequisite.appliesWhen(), 6);
                yaml.append("    requirement: |-\n");
                block(yaml, prerequisite.requirement(), 6);
            });
        }
        if (!policy.ignore().isEmpty()) {
            yaml.append("\nignore:\n");
            policy.ignore().forEach(value -> {
                yaml.append("  - |-\n");
                block(yaml, value, 4);
            });
        }
        return yaml.toString();
    }

    private static void block(final StringBuilder yaml, final String value, final int indentation) {
        final var prefix = " ".repeat(indentation);
        value.replace("\r\n", "\n").replace('\r', '\n').lines()
                .forEach(line -> yaml.append(prefix).append(line).append('\n'));
    }

    private static void valid(final boolean condition) {
        if (!condition) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
    }

    private static void conflict() {
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }
}
