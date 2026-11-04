## Introduction

This project is a multi-module Java application written in Java 17, that uses JUnit 5 for testing.

# For module: arnold-models, arnold-core, arnold-server, arnold-client

## IMPORTANT

1. Make sure you use the best java practices. Use java 17 constructs while generating tests.
2. Ensure that you keep the code precise and non-verbose.
3. Do not write code comments explaining your approach. Only write them from the perspective of a reader who is reading
   the code for the first time, or trying to understand logic. Write comments only when absolutely necessary for
   explaining complicated logic.
4. Reusability of code is important. If you find yourself writing the same code in multiple places, consider creating a
   utility class or method to encapsulate that logic.
5. Use meaningful variable names that convey the purpose of the variable.
6. When designing APIs, look at existing resources under the com.phonepe.devx.arnold.core package in arnold-core
   module for reference. Create model classes that are required for the APIs under com.phonepe.devx.arnold.models
   package in the arnold-models module.

## Domain model

The models live in `arnold-models` (`com.phonepe.devx.arnold.models`).

- **`ReviewFeedbackRequest`** — the payload ingested from the AI review workflow pipeline. Bundles the merge request
  `metadata` (`ReviewMetadata`) with the list of `comments` (`ReviewComment`) produced for it.
- **`ReviewMetadata`** — identifies the merge request: `project_id`, `mr_id`, `mr_iid` (project-scoped internal id) and
  `mr_author_id`, plus optional `analysis_run_id` linking the feedback to the Arnold run that produced it.
- **`ReviewComment`** — a single AI-generated comment: `note_id`, `discussion_id`, `comment_type`, `has_suggestion`,
  `status` (`AcceptanceStatus`), `score` (`0.0`–`1.0`), `reasoning` (optional), `priority`, `comment_category` and
  optional `persona_id`/`persona_version` for per-persona acceptance analytics.
- **`ReviewFeedbackDocument`** — the Elasticsearch document persisted per comment. Each `ReviewComment` is enriched with
  `project` (`ProjectInfo`), `mrAuthor`/`reviewer` (`User`) and `position` (resolved via CIBot), plus `ingestedAt` and
  `origin` (`arnold` | `workflow`, see below). Its `documentId()` is the deterministic ES upsert id, formatted as `projectId:mrId:noteId`.

Arnold talks to the **CIBot** service to fetch GitLab project, user and note details used to enrich review feedback
before it is indexed.

## Ingestion API

Review feedback is ingested via a single authenticated endpoint, secured by Olympus:

```
POST /review-feedback/v1/ingest
```

The request body is a `ReviewFeedbackRequest`. Each comment is persisted as its own `ReviewFeedbackDocument` in
Elasticsearch, and the response returns the number of comments ingested.

### Comment origin (Arnold vs previous workflow)

Every note Arnold writes to GitLab (discussions, replies, drafts, draft publish) gets a trailing hidden
`<!-- arnold -->` marker (`ArnoldCommentMarker`, applied in `GitLabClientImpl`). GitLab and the UI renderer hide HTML
comments, and `GitLabClientImpl` strips the marker when reading notes back. At ingest, `origin` is `arnold` when the
feedback carries an `analysis_run_id` or the CIBot-fetched note body has the marker; otherwise `workflow`.
Clio posts Arnold-triggered findings itself: Improve appends `<!-- arnold -->` when `ARNOLD_RUN_ID` is set, and
group review's existing `<!-- arnold-group:{runId}:{findingId} -->` marker is also recognised. Deploy the Clio change
for Improve attribution; earlier Improve comments count as workflow.

Documents ingested before this field existed have no `origin`, so queries treat
`origin.keyword:arnold OR _exists_:analysisRunId` as Arnold and everything else as the previous workflow. Old
comments posted manually through Arnold therefore count as workflow until re-ingested. The review-feedback metrics
limit the previous workflow to AI feedback (`reviewer.username.keyword:cibot`) so the comparison is AI vs AI; human
reviewer feedback is excluded from it.

### Review-feedback metrics

```
GET /admin/v1/metrics/review-feedback?from=<epochMs>&to=<epochMs>&project=<name>&model=<model>&personaId=<id>
```

Optional exact-match filters on review feedback: `project`, `model`, `personaId`, `status` (acceptance status),
`priority`, `commentType`, `category`, `hasSuggestion` (`true`/`false`).

Requires `superadmin`. All params are optional (`to` defaults to now, `from` to 30 days earlier; max 366 days). One
size-0 aggregation over the `CORE_REVIEWS` index returns, per origin (`arnold`, `workflow`): totals, acceptance rate
(`(applied + partially_applied) / (total - not_applicable)`), score stats, top-10 breakdowns (acceptance, priority,
comment type, suggestion, category, model, persona, project; the UI uses them as filter options) and UTC Monday-aligned weekly volume/acceptance
(`ReviewFeedbackMetricsResponse`). `personaSplit` divides the Arnold origin into `persona` (feedback with a `personaId`)
and `general` buckets with the same shape. Its `authorship` block runs a second aggregation over `CORE_COMMENTS`
(`arnold.comments`): published, non-deleted MR comments authored by `cibot` count as AI, every other author as human,
with totals, thread resolution rate, distinct MRs and weekly volume. Only the time range applies to
it. Both aggregations select by when the note was posted (`noteCreatedAt` for feedback, `createdAt` for comments) and
keep only merge requests that are merged, at or after the range start (comments posted after an earlier merge are not
review activity). The Admin page's *Review metrics* tab renders it, together with a catalog of each
metric's ES field and a Kibana recipe for plotting it.

### Token-usage metrics

```
GET /admin/v1/metrics/token-usage?from=<epochMs>&to=<epochMs>
```

Requires `superadmin`; defaults to the last 30 days, max 90 (ledger retention). Every Clio flow Arnold triggers
(`improve`, `group_review`, `smart_diff`) reports the job's cumulative LLM token usage on its callbacks (`usage`:
input/output tokens split by model, plus `jobUrl` from `CI_JOB_URL`). An optional `cacheReadTokens` (top level and
per model) is the prompt-cache-hit subset of `inputTokens`, not an addition to it; it is `null` for reports from Clio
versions that predate it, and totals treat that as 0. Arnold stores it on the run (analysis persona
result, group run per execution, smart diff record) and returns it as `usage` on the analysis, group analysis and
smart diff responses, which the UI shows on each run view. Smart diff usage is also projected to the
`inputTokens`/`outputTokens`/`totalTokens`/`cacheReadTokens` fields of its ES document, like `analysis_run`. Deploy
Arnold before a Clio that sends `cacheReadTokens`: the usage records reject unknown fields.

Each report also publishes `FlowJobUsageReported`, which `TokenUsageMetricsService` upserts into the per-job ledger
(`aerospikeStorageConfiguration.flowJobUsageSet`, id `<flow>:<execution>`, 90-day TTL); the latest report replaces
earlier ones. Each ledger write is projected to `arnold.analysis.runs` as one `type=flow_job_usage` document per job
(totals, model names, `jobUrl`) and one `type=flow_job_model_usage` document per model in the job (ids
`usage:<flow>:<execution>[:<model>]`); aggregate per-model tokens over the latter, since the dynamic mapping
flattens object arrays. These outlive the 90-day ledger for long-term analytics. The endpoint scans the ledger and returns totals, per-flow and per-model totals and the 100 newest jobs
(`TokenUsageMetricsResponse`). The Admin page's *Token usage* tab renders it.

## Retention and analytics projections

Aerospike is the source of truth; Elasticsearch holds query/analytics projections written asynchronously through
`ProjectionQueue` (Aerospike projection events drained by `ProjectionServiceImpl`). MR/comment sync enqueues strictly;
analytics-only writers (`enqueueBestEffort`) never fail the business write.

| Aerospike record | TTL (sliding, from last write) | ES index |
|---|---|---|
| Webhook deliveries | 7 days | — |
| Analysis runs | 90 days | `arnold.analysis.runs` (`type=analysis_run`, plus one `type=analysis_finding` doc per finding) |
| Smart diffs | 90 days | `arnold.analysis.runs` (`type=smart_diff`) |
| Review progress | 90 days | `arnold.review.engagement` |
| Flow-job token usage | 90 days | `arnold.analysis.runs` (`type=flow_job_usage`, plus one `type=flow_job_model_usage` doc per model) |
| Change requests, comments, review state, group runs | none | `arnold.merge.requests`, `arnold.comments` |

Expired records stay queryable in ES, so analytics outlive the Aerospike TTL. Production namespaces must not run
with `nsup-period 0`, otherwise TTLs are never enforced. Index mappings are dynamic; apply an ILM/rollover policy
to the analytics indices (e.g. 2-year retention) in production.

## Design choices

Key decisions made recently in the backend (`arnold-core`):

- **Generic, table-driven state machine.** A single reusable engine (`statemachine.StateMachine`) is fully
  domain-agnostic — states, events, per-state data and event payloads are all type parameters supplied by the
  caller. Concrete flows declare their own transition table via a fluent `StateMachineBuilder`, keeping the
  engine free of any review-specific logic.
- **Review flow as a concrete instantiation.** The MR review lifecycle lives in the `review` package and is
  built by `ReviewStateMachineFactory` over the generic engine. A different forge/strategy would just supply its
  own factory + table.
- **Specific vs global transitions.** Specific edges are keyed on `(from, event)`; global edges fire on an event
  from any non-terminal state. Globals model authoritative out-of-band facts (e.g. GitLab merge/close/reopen)
  that can legitimately arrive in almost any state. A specific edge always wins over a global.
- **Strict vs lenient firing.** `fire()` is strict (throws on unhandled events → map to 4xx) for user/system
  commands; `tryFire()` is lenient (never throws, returns a `TransitionResult`) for source-of-truth syncs and
  async callbacks that may be stale.
- **Optimistic concurrency via CAS.** State is never mutated in place. The engine reads state + an opaque
  version, computes the next state, and commits only if the version is unchanged, retrying on contention. This
  serializes concurrent events per entity without a global lock; actions must be **idempotent** since they may
  run more than once.
- **Pluggable, version-aware `StateStore` seam.** The engine depends only on the `StateStore` interface. The
  version token is an opaque `Object` so backends carry their natural token (Aerospike generation, ETag, …)
  without leaking it into the engine. An in-memory store backs tests and early phases.
- **Aerospike-backed durability via crud-store.** `ConcurrentStoreStateStore` adapts any crud-store
  `ConcurrentStore` (`getWithVersion` + `checkAndUpdate`) into the `StateStore`, giving true optimistic locking
  backed by Aerospike. Caller-supplied mappers keep the adapter domain-agnostic.
- **Guice-driven wiring.** `DBModule` provides the store, actions (`GuiceActionResolver`) and the assembled
  review state machine as singletons; Aerospike namespace/set config is externalized into
  `AerospikeStorageConfiguration`.

### Authentication

- `OlympusIMBundle` is the API authentication boundary. Use `@PermitAll` when any authenticated Olympus caller is
  allowed, and `@RolesAllowed("<permission>")` when the caller also needs a permission. Swagger security annotations
  document this contract; they do not enforce it.
- Admin sync APIs (`/admin/v1/user-sync` and its reset endpoint) require the Olympus `superadmin` permission.
  Assign this permission to the intended administrators in Olympus for Arnold before rollout. The UI checks
  `/app/v1/permissions`, which uses the same permission annotation, before showing or mounting Admin.
- For user-scoped APIs, inject `@Auth ServiceUserPrincipal` and obtain the caller through `AuthenticatedUser.id(...)`
  or `details(...)`; do not take the owning user id from the request.
- Internal HTTP and event clients attach the Olympus system token supplied by `OlympusIMClient#getSystemAuthHeader`.
- GitLab API access uses OAuth or PAT credentials bound to the authenticated Arnold user; OAuth state is one-time and
  expiring. GitLab webhooks are authenticated separately by constant-time validation of `X-Gitlab-Token` against the
  configured secret.
- Subscribe the GitLab webhook to **Merge request events**, **Comments**, and **Pipeline events** at
  `/gitlab-callbacks/v1/events`. Pipeline events for tracked MRs refresh their status through GitLab using a
  linked user's credential; pipeline success alone does not establish merge readiness.
- Completed webhook deliveries are deduplicated. Failed deliveries can be retried immediately; interrupted
  `processing` deliveries can be reclaimed after five minutes. Concurrent attempts receive a conflict response.

Clio's Improve and Smart Diff workflows both use `serviceCoreConfiguration.gitlabConfiguration.clio` for
`projectId`, `projectPath`, `ref`, and `triggerToken` (`${CLIO_TRIGGER_TOKEN}`). `projectPath` is the canonical GitLab
namespace path used for browser links, for example `devx/clio`. The GITLAB HTTP client configuration only supplies
connection settings; it does not need `authToken`. The separate `pipelineConfig` block is no longer used.

### Single-MR analysis findings

The Improve completion callback at `POST /analysis-callbacks/v1/persona-completed` includes the exact findings handled
by that run: GitLab note ID, pinned head revision, file/line, rendered body, publication status, and comment URL.
Arnold stores these findings with the persona result and returns them from `/analyses/v1`, allowing Analysis History to
show the same run-scoped publication details as group analysis. Callbacks that omit `findings` remain valid for rolling
upgrades; deploy Arnold before the corresponding Clio change.

### Review persona attribution

Improve runs a general pass plus one pass per selected review persona; Arnold records which pass produced each
finding/comment (no backfill: older findings and comments count as general).

- **Dispatch**: with `analysisConfiguration.personaDelivery: INLINE` (default) Arnold sends full definitions as
  `REVIEW_PERSONAS_JSON`. With `FETCH` it sends only `REVIEW_PERSONA_REFS=netra@3,sai@1` and Clio fetches
  `GET /analysis-callbacks/v1/runs/{runId}/personas?refs=netra@3,sai@1` (`analysis_callback_write`), which serves the
  run's frozen `reviewPersonas` snapshot (`[{id,name,instructions,version}]`). 404 for an unknown run; 409 when the run
  is not running or `refs` differs from the snapshot. The snapshot is served (not `PersonaRecord`) because persona
  records keep only their latest version. Switch to `FETCH` once Clio supports it.
- **Completion**: each `findings[]` item may carry `personaId`/`personaVersion` (null ⇒ general). Ids not in the run's
  snapshot are dropped to general with a warning; the snapshot version is authoritative.
- **Note marker**: persona notes also carry `<!-- arnold-persona:{runId}:{personaId}@{version} -->`. Arnold parses it
  before stripping markers (`GitLabClientImpl`, webhook `GitLabCallbackServiceImpl`) and stores it on comments
  (`CommentDocument.personaId/personaVersion/analysisRunId`). Feedback ingest uses it when `persona_id` is absent.
- **Comments API**: `CommentResponse` exposes `personaId`, `personaVersion`, `personaName` (from the run snapshot),
  `analysisRunId` and `reviewSource` (`persona` | `ai` | `human`); the review page's *Persona* view groups threads by it.
- **Metrics** (`PersonaMetricsService`, aggregations over runs/findings, `CORE_REVIEWS` and `CORE_COMMENTS`):
  - `GET /personas/v1/{personaId}/metrics?from&to`: runs, findings (posted/failed), acceptance, thread resolution,
    weekly trend and per-version breakdown.
  - `GET /personas/v1/metrics/summary?from&to`: headline numbers per persona plus a `general` entry.

### MR group analysis

`POST /merge-request-groups/v1/mine/{groupId}/analyses` accepts `rfcUrl`, `jiraTickets`, `reviewContext`, and
`reviewPersonaIds`. It resolves every member with the caller's GitLab credential and stores immutable base/start/head
revisions before triggering Clio with `WORKFLOW=group_review`, `ARNOLD_RUN_ID`, and `EXECUTION_ID`.

`GET /group-analyses/v1` lists the caller's runs (optional `groupId`); `GET /group-analyses/v1/{runId}` returns saved
findings and checks current revisions. `POST /group-analyses/v1/{runId}/retry` resumes the saved run with its original
RFC URL, Jira tickets, review context, member snapshot, and persona instructions/versions. The existing
`retry-publication` route remains an alias. Changed/deleted groups cannot retry; start a fresh analysis.

Callbacks checkpoint validated member results in `memberReviews`, including successful reviews with no findings.
Retries skip those member reviews and repeat combined review and filtering. `analysisComplete=true` records that all
review stages finished; subsequent retries publish the saved final findings without another model call. Checkpoints
are immutable within a run and must match its member revisions. Legacy runs without checkpoints repeat analysis unless
publication already started (a saved `posted` or `unknown` finding). Deploy Arnold before the corresponding Clio change.

Clio reads the manifest and sends ordered progress callbacks under `/group-analysis-callbacks/v1/{runId}`; its
`/fresh` endpoint checks live access, membership, and revisions before publication. These endpoints require the existing
`analysis_callback_write` Olympus permission. Each execution accepts only one worker; old execution IDs and terminal
state regressions are rejected. Executions expire after three hours, including pipelines that never report back.

Deploy the Clio `group_review` job before enabling the Arnold UI. Configure `groupAnalysisRunSet` (local/stage examples
use `arnold-group-analysis-run-v1`), and ensure Clio's existing GitLab credential can read and comment on every member.
Group history is independent of the single-MR review state machine. No story-generation behavior changes in this phase.

Publication records distinguish `pending`, `posted`, `failed`, and `unknown`. Retries reconcile saved comment markers
against GitLab discussions. An unconfirmed write is never blindly reposted; if reconciliation still finds no comment,
inspect the linked pipeline/GitLab before starting a new analysis. Findings remain available if a group is deleted.

### Error Handling

- Return successful API data with `ArnoldResponse.ok(...)`. Application failures use the same envelope with
  `success=false`, `errorCode` and `errorMessage`.
- Introduce new error codes in `ArnoldErrorCode` with a unique integer and a client-facing message. Do this only when
  the current ones aren't able to accomodate. Avoid exposing internal exception messages or stack traces to clients.
- Raise expected domain/service failures with `ArnoldError.raise(ArnoldErrorCode...)`; use `propagate(...)` to retain an
  underlying cause. Let the exception mappers translate them instead of catching them in resources.
- `AppExceptionMapper` maps each `ArnoldErrorCode` to its declared HTTP status. Client-facing messages come from the
  error code and context; a custom builder `message(...)` is retained for logs, not returned in the response.
- `@Valid` violations keep Jersey's status and use error code `3000`. `WebApplicationException` keeps its HTTP status;
  JSON processing failures return `500/1002`, and unhandled failures return the sanitized `500/1000`. Olympus
  authentication failures retain Olympus's error format.


## TESTS

1. Think deeply about how you plan on writing these tests and then write them thoroughly.
2. Use knowledge of existing tests and base tests when generating new tests. Ensure that you reuse existing flows and
   utilities
3. For Resource tests, use existing tests on resources as reference and extend Base tests as necessary
4. Add minimal code comments on the tests, on in places where there is a logic that needs to be explained. Don't write
   basic comments, like explaining variable declaration.
5. For Model classes whose implementations are not available in the codebase, make sure you ask for implementation of
   the model, if you are unaware, before trying to assume or hallucinate the model.
6. Naming convention should follow the pattern: `test<MethodName>_should<Scenario>_when<Context>`. For example,
   `testGetListingView_shouldReturnValidListing_whenValidUnitData`.
7. Add @Nested tests wherever applicable to group related tests together.
8. Always use `final var` for variable declarations where applicable.
9. If the modules contain Data generation utilities, they will be present under "/data" (like DataGenerator.java,
   DataSets.java, TestDataGenerator.java etc). Utilize them while writing tests whenever possible

   
# Local debugging and testing

### For running the server on command line:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
cd arnold-backend/arnold-server && sh run.sh d
```

You can then connect remotely from intellij remotely, by setting up a remote configuration (Remote JVM debug), and
connecting to the default 5005 port

### for running tests:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn test
```

### Personal Copilot connection

Settings supports GitHub device authorization under `/copilot-integrations/v1`: `GET /status`,
`POST /device/initiate`, `POST /device/poll`, `DELETE /device/attempt`, `POST /verify`, and
`DELETE /connection`. Poll/cancel bodies contain only `attemptId`. These account-management routes use the authenticated
Olympus user; their responses never contain provider tokens or GitHub device codes.

Enable `serviceCoreConfiguration.copilotConfiguration.enabled` only after supplying
`COPILOT_ENCRYPTION_KEY` as a base64-encoded random 32-byte secret through deployment secret management.
Use the same stable key on every replica; replacing it makes existing connections unreadable and
requires users to reconnect. Do not commit the key. The feature defaults to disabled and needs no
key while disabled. `clientId` defaults to the public device client in the SAI reference;
`credentialSet` defaults to `arnold-copilot-v1`, in the existing OAuth-state Aerospike namespace.
Deploy the backend/configuration before enabling the UI connection flow.

GitHub sign-in is followed by identity and Copilot-access verification. The Copilot check uses
GitHub's internal token-exchange endpoint, whose compatibility is not guaranteed. A 403 there is
shown as Copilot unavailable; transient failures preserve credentials and allow Test connection.
Expiring OAuth credentials refresh during verification when a refresh token exists. No background
refresh is performed.

Access/refresh/device tokens are AES-256-GCM encrypted with owner-and-field-bound authentication.
Device polling uses persisted intervals and generation-checked operation claims, so replicas cannot
poll concurrently or restore credentials after cancellation/disconnect. An interrupted operation
can be retried after its 90-second claim expires. Empty records are retained after disconnect to
fence in-flight updates; this removes Arnold's credentials, not GitHub-wide authorization.

Validation before rollout: connect a real Copilot account from Settings, approve the displayed code,
verify its username and access status, test reconnection and disconnect, and check that neither
browser responses nor logs contain credentials. Check an account without Copilot access separately.
Single-MR and group analysis support **Copilot only** in the current launch UI. Users select a model
from their connected account's live catalog; OpenCode selection is deferred. Smart Diff is unchanged.
`GET /analysis-execution/v1` requires the authenticated Olympus user and returns harness availability,
a safe status message, and model IDs/names. It never returns credentials and uses `Cache-Control: no-store`.
Both Copilot flags must be enabled and the user must have a verified connection.

Arnold exchanges OAuth credentials for a short-lived Copilot bearer and reads `/models` from the
HTTPS `*.githubcopilot.com` endpoint supplied by GitHub. Allow this outbound domain through the backend
proxy/firewall as well as `github.com` and `api.github.com`. Non-chat, explicitly disabled, hidden and
non-tool-capable models are excluded. Catalog requests are per-user, with no shared model cache. An
upstream failure or empty catalog blocks selection and offers a refresh action; no static fallback list is used.
The catalog uses GitHub's internal API, so a live-account smoke test is required when deploying.

The Copilot HTTP client honors `https_proxy`, `http_proxy`, `all_proxy` and `no_proxy`
(and their uppercase equivalents; nonblank lowercase values take precedence). Supply them to the backend
container at runtime, for example `https_proxy=http://proxy.example:3128`. `no_proxy` supports host/domain
entries, optional ports and `*`. Without a matching environment proxy, the JVM proxy selector is used.
Proxy URLs containing credentials are rejected; proxy authentication is not supported.

The UI submits `execution: {harness: "copilot", model: "<selected-id>"}`. The backend rechecks the catalog
before pipeline dispatch and rejects unavailable selections. GitHub remains the final authority on model
access. Selections are stored on each run and shown in history; group retries preserve the original request,
and single-MR retry links prefill its model. Explicit choices override Clio's workflow model defaults and
disable harness fallback. Older requests without `execution` retain deployment defaults.

Deploy the backend before this UI, using Clio's existing explicit-selection and token-provider support.
No new secrets, Aerospike sets or configuration flags are needed.

### Copilot credentials for Clio executions

Deploy the updated Clio worker first, then enable
`serviceCoreConfiguration.copilotConfiguration.jobTokenExchangeEnabled` (defaults to false).
Copilot connections must also be enabled. Existing connections need **Test connection** once to acquire
an account-connection identifier; reconnecting also works.

For Copilot executions, Arnold adds a random `ARNOLD_COPILOT_KEY` and `CLIO_HARNESS=copilot` to Improve,
group review and Smart Diff pipelines. It stores only the key's SHA-256 hash with the owner, connection and execution identifiers.
Keys expire after three hours, have an Aerospike TTL, and are rejected for terminal or replaced executions
and disconnected/replaced accounts. A key can retrieve credentials for multiple sessions within that execution.
Publication-only group retries do not need a key. Dispatch failures revoke newly issued keys.

During SDK session creation and requested credential refresh, Clio's `github_token_provider` calls
`POST /copilot-integrations/v1/execution-token` with
`X-Arnold-Copilot-Key` and its Olympus service authorization (`analysis_callback_write`). Configure Clio's
DC-specific `ARNOLD_URL` to use HTTPS and grant its service identity that permission. The response is
`{success: true, response: {accessToken, expiresAt}}` with `Cache-Control: no-store`.
Arnold refreshes expiring OAuth credentials when possible. Missing/invalid keys fail closed.
Clio caps the SDK credential reuse window at two hours or the remaining OAuth lifetime, whichever is
shorter. If GitHub reports no expiry, the two-hour window is a local revalidation policy, not OAuth
revocation. The SDK requests refresh before credential-consuming operations with one hour or less
remaining; there is no idle refresh or automatic recovery from unexpected token revocation.

The response contains the existing GitHub OAuth token, not a newly minted job-scoped GitHub token.
It reaches Clio memory and the SDK session, but is never passed as a pipeline variable or subprocess
credential environment variable. Expiring/revoking the execution key stops future retrieval; it cannot
revoke a token already delivered or terminate an existing SDK session.
Treat the execution key as a secret: restrict pipeline-variable access, disable CI debug tracing/environment
dumps, and redact the exchange header and response body in proxies/APM. The key plus service identity
is the authorization boundary; this implementation does not attest GitLab job identity. Do not expose
these credentials through job logs or artifacts. Smoke-test a real connected account after deployment;
unit tests use mocked GitHub, GitLab and SDK boundaries.
