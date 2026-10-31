# Arnold Runner

The Arnold runner executes Arnold's AI review workflows, built on [PocketFlow](https://github.com/The-Pocket/PocketFlow).
It posts review comments to the SCM directly and reports results back to Arnold.

## Arnold callbacks

The runner authenticates to Arnold with `ARNOLD_RUNNER_API_KEY`, matching an entry in the backend's
`serviceCoreConfiguration.auth.apiKeys` with the `analysis_callback_write`, `smart_diff_callback_write` and
`review_feedback_write` roles. `ARNOLD_URL` points at the Arnold backend.

## Single-MR Improve analysis

Arnold triggers `WORKFLOW=improve` with `ARNOLD_RUN_ID` and `ARNOLD_MERGE_REQUEST_ID`. After publishing inline GitLab
discussions, the runner reports the exact note IDs, pinned head revision, file/line, rendered body, publication status, and
comment URLs in the terminal Arnold callback. Arnold persists these run-scoped findings for Analysis History. Deploy
the compatible Arnold callback contract before this runner version.

### Review persona attribution

Selected review personas arrive either inline as `REVIEW_PERSONAS_JSON` or as `REVIEW_PERSONA_REFS=netra@3,sai@1`.
With refs, the runner fetches the run's persona snapshot from
`GET {ARNOLD_URL}/analysis-callbacks/v1/runs/{ARNOLD_RUN_ID}/personas?refs=...` using its runner API key (`ARNOLD_RUNNER_API_KEY`)
(`analysis_callback_write`) and fails the run if the fetch fails or the snapshot does not match the refs.

Each persona runs as a specialist sub-agent. Its findings carry `personaId`/`personaVersion` in the completion
callback, the posted note starts with a visible `🎭 **{name}** review persona` line, and it gets a hidden `<!-- arnold-persona:{runId}:{personaId}@{version} -->` marker next to
`<!-- arnold -->`. Findings from the general pass and skill reviewers have no persona. When deduplication drops a
persona finding in favour of another new finding, the retained finding takes the persona attribution (personas win
over general; the earliest persona in the selection order wins among personas).

## MR group analysis

Arnold triggers `WORKFLOW=group_review` with `ARNOLD_RUN_ID` and `EXECUTION_ID`. Run locally with:

```bash
python main.py group_review --run-id <run-id> --execution-id <execution-id>
```

The workflow requires `ARNOLD_URL`, `ARNOLD_RUNNER_API_KEY`, and
`GITLAB_ACCESS_TOKEN` with access to every member repository. Arnold owns the immutable manifest; credentials are
never stored in it. Each MR gets a separate pinned checkout, including MRs from the same repository.

Member reviews collect findings with shared group context. A combined review checks cross-repository interactions,
consolidates evidence, and filters existing discussions before any findings are published. Failure of a required review
withholds all publication. Saved findings survive publication failures and can be retried from Arnold without another
AI review. Revision/membership changes stop remaining publication and require a fresh analysis.

Arnold checkpoints each completed member review, including reviews with no findings. Retry analysis reuses those
results and the run's original RFC URL, Jira tickets, review context, pinned MR metadata, and persona versions. The runner
recreates the pinned checkouts, reviews only unfinished members, then repeats combined review and duplicate filtering.
Only findings that passed those stages are eligible for publication-only retries. Deploy the updated Arnold callback
contract before the runner. Older runs without checkpoints must repeat member review; saved preliminary findings are not
treated as completed analysis.

Publication retries search discussion notes for stable finding markers. Unconfirmed writes stay `unknown` if no marker
is found, rather than risking duplicate comments. Already confirmed posts are preserved. The job cleans up its temporary
checkouts and is limited to 2h45m; Arnold expires interrupted executions after three hours.

This workflow does not simulate merges or generate stories. Existing single-MR commands remain available.

## Harness Configuration

The runner supports multiple agentic harnesses. Select the active harness via the
`CLIO_HARNESS` environment variable.

Arnold's single-MR and group analysis launch screens can select the harness and an optional model ID.
These executions set `CLIO_DISABLE_FALLBACK=true`; the selected harness fails without switching providers,
and `CLIO_LLM_MODEL` or `CLIO_OPENCODE_MODEL` takes precedence over workflow model defaults.
OpenCode choices use existing deployment credentials and do not receive a Copilot execution key.
Blank model choices use the deployment default. Group retries preserve their saved selection.

### Copilot SDK (default)

```bash
export CLIO_HARNESS=copilot          # or omit — this is the default
export GITHUB_TOKEN=ghp_...          # GitHub PAT with 'copilot' scope
export CLIO_LLM_MODEL=gpt-6.1-sol       # optional, model override
```

### Arnold execution keys

For Arnold-launched Copilot workflows, Arnold supplies `ARNOLD_COPILOT_KEY` and `CLIO_HARNESS=copilot`.
The runner retrieves the user's GitHub OAuth token before each SDK session is created from
`POST {ARNOLD_URL}/copilot-integrations/v1/execution-token`, using `X-Arnold-Copilot-Key` and
`ARNOLD_RUNNER_API_KEY`. `ARNOLD_URL` must use HTTPS. Redirects are rejected. Retrieval failure stops the session without shared-token fallback.
The runner passes the token through the session’s `github_token` option; inherited GitHub credentials and the execution key are removed
from the SDK subprocess environment, and logged-in-user authentication is disabled.

Deploy this worker before enabling Arnold's `copilotConfiguration.jobTokenExchangeEnabled` flag.
Keys expire after three hours and stop working when the execution ends, is replaced, or the account is
reconnected/disconnected. Publication-only retries do not retrieve credentials. Keep CI debug tracing off,
restrict access to pipeline variables, and never dump the environment or exchange response into logs/artifacts.
The runner still receives the user's OAuth token in memory; key expiry cannot revoke a token already retrieved.
Tokens are checked for expiry when retrieved. There is no automatic mid-session refresh or retry on
revocation; a session whose token expires or is revoked may fail. Each new session fetches credentials again.
Only the `github.com` host is supported by Arnold's current connection flow.

TODO: Restore callback-based refresh after upgrading to SDK 1.0.15/runtime 1.0.89 or a verified newer pair
once CI's 14-day dependency-age policy permits it. Accept the runtime's `https://github.com` host value
and verify initial acquisition and refresh with a real credential before enabling it.

### OpenCode

```bash
export CLIO_HARNESS=opencode
export CLIO_LLM_BASE_URL=https://api.openai.com/v1   # any OpenAI-compatible endpoint
export CLIO_LLM_API_KEY=...
export CLIO_OPENCODE_MODEL=openai/gpt-4.1          # optional
export CLIO_OPENCODE_TIMEOUT=2700    # optional, session timeout in seconds (default: 45 mins)
```

OpenCode uses `CLIO_OPENCODE_API_KEY` when set, otherwise `CLIO_LLM_API_KEY`.

**Prerequisites for OpenCode harness:**
- Node.js ≥ 18 and `opencode` CLI (`npm install -g opencode-ai`)
- The OpenCode CLI must be on `PATH`

### Environment Variables Reference

| Variable | Required | Default | Description |
|---|---|---|---|
| `CLIO_HARNESS` | No | `copilot` | Harness backend: `copilot` or `opencode` |
| `GITHUB_TOKEN` | Copilot without an Arnold key | — | GitHub PAT with `copilot` scope |
| `ARNOLD_COPILOT_KEY` | Arnold Copilot jobs | — | Execution credential retrieval key; supplied by Arnold |
| `CLIO_LLM_MODEL` | No | `gpt-6.1-sol` | Model for Copilot harness |
| `ARNOLD_URL` | Arnold workflows | — | Arnold backend base URL |
| `ARNOLD_RUNNER_API_KEY` | Arnold workflows | — | Runner API key for Arnold callbacks |
| `CLIO_LLM_BASE_URL` | No | `https://api.openai.com/v1` | OpenAI-compatible endpoint for simple LLM calls and OpenCode |
| `CLIO_LLM_API_KEY` | OpenCode / simple LLM calls | — | API key for that endpoint (`OPENAI_API_KEY` also accepted) |
| `CLIO_OPENCODE_API_KEY` | No | — | Overrides the key for OpenCode only |
| `CLIO_OPENCODE_MODEL` | No | `openai/gpt-4.1` | Model for OpenCode harness |
| `CLIO_OPENCODE_BASE_URL` | No | `CLIO_LLM_BASE_URL` | Provider base URL override for OpenCode |
| `CLIO_AI_BOT_USER_ID` | No | — | SCM user id of the bot account that posts inline suggestions |
| `RFC_CENTRAL_API_URL` / `RFC_API_TOKEN` | No | — | Optional design-doc (RFC) content service |
| `IMPACT_SERVICE_URL` / `IMPACT_SERVICE_TOKEN` | No | — | Optional sink for post-merge impact data |
| `CLIO_OPENCODE_TIMEOUT` | No | `2700` | Session timeout (seconds) |
