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
  `mr_author_id`.
- **`ReviewComment`** — a single AI-generated comment: `note_id`, `discussion_id`, `comment_type`, `has_suggestion`,
  `status` (`AcceptanceStatus`), `score` (`0.0`–`1.0`), `reasoning` (optional), `priority` and `comment_category`.
- **`ReviewFeedbackDocument`** — the Elasticsearch document persisted per comment. Each `ReviewComment` is enriched with
  `project` (`ProjectInfo`), `mrAuthor`/`reviewer` (`User`) and `position` (resolved via CIBot), plus `ingestedAt`. Its
  `documentId()` is the deterministic ES upsert id, formatted as `projectId:mrId:noteId`.

Arnold talks to the **CIBot** service to fetch GitLab project, user and note details used to enrich review feedback
before it is indexed.

## Ingestion API

Review feedback is ingested via a single authenticated endpoint, secured by Olympus:

```
POST /review-feedback/v1/ingest
```

The request body is a `ReviewFeedbackRequest`. Each comment is persisted as its own `ReviewFeedbackDocument` in
Elasticsearch, and the response returns the number of comments ingested.

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
`DELETE /connection`. Poll/cancel bodies contain only `attemptId`. All routes use the authenticated
Olympus user; responses never contain provider tokens or GitHub device codes.

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
refresh or downstream credential delivery is introduced.

Access/refresh/device tokens are AES-256-GCM encrypted with owner-and-field-bound authentication.
Device polling uses persisted intervals and generation-checked operation claims, so replicas cannot
poll concurrently or restore credentials after cancellation/disconnect. An interrupted operation
can be retried after its 90-second claim expires. Empty records are retained after disconnect to
fence in-flight updates; this removes Arnold's credentials, not GitHub-wide authorization.

Validation before rollout: connect a real Copilot account from Settings, approve the displayed code,
verify its username and access status, test reconnection and disconnect, and check that neither
browser responses nor logs contain credentials. Check an account without Copilot access separately.
Workflow/harness selection and job-token transport remain deferred.
