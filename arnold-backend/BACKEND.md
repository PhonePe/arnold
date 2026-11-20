# Arnold Backend

Java 17, Dropwizard, Guice, JUnit 5. Aerospike is the source of truth; Elasticsearch holds analytics projections.

| Module          | Contents                                                                    |
|-----------------|-----------------------------------------------------------------------------|
| `arnold-server` | Dropwizard application; wires `ArnoldBundle`. Config in `config/`.          |
| `arnold-core`   | Resources, services, forge clients, auth, runner dispatch, storage.         |
| `arnold-models` | Request, response and document models (`com.phonepe.arnold.models`).        |

## Build, run, debug

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn test                                   # from arnold-backend/
cd arnold-server && sh run.sh d            # run locally, debugger on port 5005
```

Attach IntelliJ with a *Remote JVM Debug* configuration on port 5005.

## Conventions

### Code

- Use Java 17 constructs (`final var`, records, switch expressions). Keep code concise and reuse existing utilities.
- Comment only non-obvious logic, written for a first-time reader.
- New APIs follow existing resources in `com.phonepe.arnold.core`; their models go in `arnold-models`.

### Auth

- `AuthFeature` tries credential verifiers in order: API keys (`auth.apiKeys`), JWTs from `auth.jwt.issuers` (UI shared
  secret or OIDC/JWKS), Guice-contributed verifiers, then the dev user (`auth.dev.enabled`).
- Annotate resources with `@PermitAll` or `@RolesAllowed("<role>")`. `superadmin` implies every role. Roles come from
  `auth.roleMappings`, `auth.admins` and API key config. Swagger annotations document; they do not enforce.
- For user-scoped APIs inject `@Auth ArnoldPrincipal` and use `AuthenticatedUser.id(...)`. Never take the owner id
  from the request.
- Runner API keys need `runner_execute`, `analysis_callback_write`, `smart_diff_callback_write` and
  `review_feedback_write`.

### Errors

- Return `ArnoldResponse.ok(...)`. Raise expected failures with `ArnoldError.raise(ArnoldErrorCode...)` (or
  `propagate(...)` to keep a cause) and let `AppExceptionMapper` map them; do not catch in resources.
- Add an `ArnoldErrorCode` (unique integer, client-facing message, HTTP status) only when no existing code fits. Never
  expose internal messages or stack traces.
- Fixed mappings: validation `3000`, JSON processing `500/1002`, unhandled `500/1000`, auth `401` with a
  `WWW-Authenticate: Bearer` challenge.

### Tests

- Name tests `test<Method>_should<Outcome>_when<Context>`, e.g. `testGetListing_shouldReturnListing_whenUnitValid`.
- Group related cases with `@Nested`. Use `final var`.
- Extend the existing base tests for resources; reuse data generators under `/data` (`DataGenerator`, `DataSets`, …).
- Do not invent models that are not in the codebase; ask.

## Architecture

### State machine

- `statemachine.StateMachine` is a generic, table-driven engine; flows declare transitions with `StateMachineBuilder`.
  The MR review flow is built by `ReviewStateMachineFactory` in the `review` package.
- Specific edges are keyed on `(from, event)`; global edges fire from any non-terminal state (e.g. forge
  merge/close/reopen). Specific wins over global.
- `fire()` is strict (throws, maps to 4xx) for commands; `tryFire()` is lenient for syncs and callbacks that may be
  stale.
- Commits use CAS on an opaque version (`StateStore`, Aerospike-backed by `ConcurrentStoreStateStore`) and retry on
  contention. Actions must be idempotent.

### Storage and retention

Elasticsearch projections are written asynchronously through `ProjectionQueue`/`ProjectionServiceImpl`. Sync writes
enqueue strictly; analytics-only writes use `enqueueBestEffort` and never fail the business write.

| Aerospike record                                    | TTL (sliding) | ES index                                                    |
|-----------------------------------------------------|---------------|-------------------------------------------------------------|
| Webhook deliveries                                  | 7 days        | none                                                        |
| Analysis runs                                       | 90 days       | `arnold.analysis.runs` (`analysis_run`, `analysis_finding`) |
| Smart diffs                                         | 90 days       | `arnold.analysis.runs` (`smart_diff`)                       |
| Review progress                                     | 90 days       | `arnold.review.engagement`                                  |
| Flow-job token usage                                | 90 days       | `arnold.analysis.runs` (`flow_job_usage`, `flow_job_model_usage`) |
| Change requests, comments, review state, group runs | none          | `arnold.merge.requests`, `arnold.comments`                  |

Elasticsearch is optional (`ELASTIC_ENABLED`, default `false`). When disabled, `ESStorageManager` skips writes and
searches raise `ANALYTICS_DISABLED` (503), and no health check is registered.

Production namespaces must not use `nsup-period 0` (TTLs would never apply). Apply an ILM policy to analytics indices.

### Forges and webhooks

- GitLab uses per-user OAuth or PAT credentials; webhooks at `/gitlab-callbacks/v1/events` (merge request, comment and
  pipeline events) are verified against `X-Gitlab-Token`.
- GitHub uses a GitHub App: user-to-server tokens for user actions, installation tokens for runner jobs. Webhooks at
  `/github-callbacks/v1/events` are verified with `X-Hub-Signature-256` and deduplicated on `X-GitHub-Delivery`.
- Completed deliveries are deduplicated; failed ones can be retried; stuck `processing` deliveries are reclaimable
  after five minutes.
- Forge tokens are stored AES-256-GCM encrypted. Set `ARNOLD_CREDENTIAL_ENCRYPTION_KEY`
  (`openssl rand -base64 32`), identical on every replica. Changing it forces users to reconnect.

### Repository sync

`RepositorySyncServiceImpl` imports every open change request the deployment can see, on the `repository-sync` leader
every `repositorySync.intervalSeconds` (default 300).

- GitHub: all installations of the App (archived repositories skipped).
- GitLab: projects in `repositorySync.gitlabGroups`, else projects the service token is a member of.
- Imports go through `VersionControlSyncService` (same path as webhooks). Stored open requests missing from the list
  are re-fetched (up to `maxReconcilePerRun`, default 200) to record merges and closes.
- Rate-limited runs import what they fetched and report `FAILED`.

Endpoints: `GET /repository-syncs/v1/status`, `GET|POST /admin/v1/repository-sync` (superadmin). Env:
`ARNOLD_REPOSITORY_SYNC_ENABLED`, `ARNOLD_REPOSITORY_SYNC_INTERVAL_SECONDS`, `ARNOLD_GITLAB_SYNC_GROUPS`.

### Runner dispatch

Improve, Smart Diff and group review go through `RunnerDispatcher`. `runner.mode`: `LOCAL` (Aerospike queue claimed via
`/runner/v1/jobs`), `GITLAB` (pipeline trigger) or `GITHUB` (`workflow_dispatch`). The legacy
`gitlabConfiguration.clio` block is read as `GITLAB`.

Deploy the backend before a runner that sends new callback fields: usage records reject unknown fields.

### Analysis

- **Single MR.** The Improve callback (`POST /analysis-callbacks/v1/persona-completed`) carries the run's findings with
  note id, revision, position and publication status. `findings` is optional for rolling upgrades.
- **Personas.** Improve runs a general pass plus one per selected persona. `analysisConfiguration.personaDelivery`:
  `INLINE` (default, sends `REVIEW_PERSONAS_JSON`) or `FETCH` (sends refs; runner calls
  `GET /analysis-callbacks/v1/runs/{runId}/personas`). Findings and notes carry `personaId`/`personaVersion`; notes
  also carry `<!-- arnold-persona:{runId}:{personaId}@{version} -->`. The run's snapshot version is authoritative.
- **Groups.** `POST /merge-request-groups/v1/mine/{groupId}/analyses` pins member revisions and dispatches
  `WORKFLOW=group_review`. Callbacks under `/group-analysis-callbacks/v1/{runId}` checkpoint per-member results so
  retries (`POST /group-analyses/v1/{runId}/retry`) skip finished work. Publication states are `pending`, `posted`,
  `failed`, `unknown`; unconfirmed writes are reconciled, never blindly reposted. Executions expire after three hours.
  Requires `groupAnalysisRunSet`.

### Review feedback and metrics

- `POST /review-feedback/v1/ingest` (`review_feedback_write`) stores each `ReviewComment` as a
  `ReviewFeedbackDocument` (id `projectId:mrId:noteId`), enriched via `ScmMetadataService`.
- Notes written by Arnold end with a hidden `<!-- arnold -->` marker. `origin` is `arnold` when the feedback has an
  `analysis_run_id` or the note has the marker, otherwise `workflow`. Legacy documents without `origin` are classified
  by `origin.keyword:arnold OR _exists_:analysisRunId`.
- `GET /admin/v1/metrics/review-feedback` (superadmin, max 366 days): acceptance, score, breakdowns and weekly trends
  per origin, a persona/general split, and AI vs human authorship over `arnold.comments`. Only merged MRs count.
- `GET /admin/v1/metrics/token-usage` (superadmin, max 90 days): per-flow and per-model token totals from the job
  ledger (`flowJobUsageSet`). `cacheReadTokens` is a subset of `inputTokens`.
- `GET /personas/v1/{personaId}/metrics` and `/personas/v1/metrics/summary`: per-persona runs, findings and acceptance.

### GitHub Copilot

Off by default. Users connect Copilot through device authorization (`/copilot-integrations/v1`); the analysis UI then
lists models from their live catalog (`GET /analysis-execution/v1`).

- Enable `copilotConfiguration.enabled` with `COPILOT_ENCRYPTION_KEY` (base64, 32 bytes, identical on every replica) and
  `clientId` for your own OAuth app with device flow enabled.
- Allow outbound `github.com`, `api.github.com` and `*.githubcopilot.com`. The client honors `https_proxy`,
  `http_proxy`, `all_proxy` and `no_proxy`; proxy credentials are not supported.
- Copilot access checks and the model catalog use internal GitHub APIs. Smoke-test with a real account after each
  deployment.
- `copilotConfiguration.jobTokenExchangeEnabled` (deploy the runner first) lets runner jobs fetch the user's token via
  `POST /copilot-integrations/v1/execution-token` using a per-execution `ARNOLD_COPILOT_KEY` (hashed at rest, three-hour
  expiry). Treat that key as a secret: restrict pipeline variables, disable CI debug tracing, and redact the
  `X-Arnold-Copilot-Key` header and response body in proxies and APM.
