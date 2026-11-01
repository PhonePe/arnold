# Runner configuration

The runner is configured only through environment variables. In Docker Compose it reads `.env`.

## Connection to Arnold

| Variable | Required | Purpose |
|---|---|---|
| `ARNOLD_URL` | yes | Backend base URL. Must be HTTPS for the Copilot harness |
| `ARNOLD_RUNNER_API_KEY` | yes | Matches the backend `runner` API key |

## Forges

| Variable | Purpose |
|---|---|
| `GITLAB_ACCESS_TOKEN` | Clone and comment on GitLab MRs |
| `SCM_GITHUB_TOKEN` | Injected per job by the backend for GitHub PRs. Set manually only for debugging |

## Harness and model

| Variable | Default | Purpose |
|---|---|---|
| `CLIO_HARNESS` | `copilot` | `opencode` or `copilot`. Set `opencode` unless you use Copilot |
| `CLIO_LLM_BASE_URL` | `https://api.openai.com/v1` | OpenAI-compatible endpoint |
| `CLIO_LLM_API_KEY` | | Its key (`OPENAI_API_KEY` also accepted) |
| `CLIO_OPENCODE_MODEL` | `openai/gpt-4.1` | Default OpenCode model |
| `CLIO_OPENCODE_API_KEY` / `CLIO_OPENCODE_BASE_URL` | LLM values | OpenCode-only overrides |
| `CLIO_OPENCODE_TIMEOUT` | `2700` | Session timeout (s) |
| `CLIO_LLM_MODEL` | | Copilot model |
| `CLIO_DISABLE_FALLBACK` | set by Arnold | Fail instead of switching harness |
| `GITHUB_TOKEN` | | Copilot harness without an Arnold execution key (CLI use) |

## Worker

| Variable | Default | Purpose |
|---|---|---|
| `ARNOLD_RUNNER_CONCURRENCY` | `1` | Parallel jobs |
| `ARNOLD_RUNNER_NAME` | hostname | Worker name prefix |
| `ARNOLD_RUNNER_JOB_TIMEOUT` | `9900` | Per-job timeout (s) |

## Job variables

Arnold sends each job as a flat variable map (pipeline variables in `GITLAB` mode, the claim response otherwise).
You do not set these yourself; they are listed to help when reading logs.

| Variable | Meaning |
|---|---|
| `WORKFLOW` | `improve`, `smart_diff`, `group_review`, `review`, `describe`, `fix_sonar`, `review_analysis` |
| `ARNOLD_RUN_ID` | Analysis run id |
| `ARNOLD_MERGE_REQUEST_ID` | Change request id in Arnold |
| `MR_LINK` | PR/MR URL |
| `EXECUTION_ID` | Execution id for smart diff and group review |
| `REVIEW_PERSONA_REFS` | Personas to apply, e.g. `netra@3,hayai@1`; the runner fetches the snapshot |
| `CLIO_HARNESS`, `CLIO_OPENCODE_MODEL` / `CLIO_LLM_MODEL` | Harness and model chosen for the run |
| `ARNOLD_COPILOT_KEY` | Short-lived Copilot execution key (Copilot runs only) |
| `SCM_GITHUB_TOKEN` | Installation token (GitHub PRs) |

Jobs cannot override protected variables such as `ARNOLD_URL` or `ARNOLD_RUNNER_API_KEY`.

## Optional integrations

| Variable | Purpose |
|---|---|
| `CLIO_AI_BOT_USER_ID` | Forge user id of the bot posting inline suggestions |
| `RFC_CENTRAL_API_URL`, `RFC_API_TOKEN` | Fetch design-doc content for the RFC URL attached to a run |
| `IMPACT_SERVICE_URL`, `IMPACT_SERVICE_TOKEN` | Post-merge impact data sink |
