# Environment reference

All settings live in `.env` at the repository root (copied from `deploy/.env.example`, never committed). Unset values
fall back to defaults in `arnold-backend/arnold-server/config/local.yml`.

## Forges

| Variable | Read by | Purpose |
|----------|---------|---------|
| `GITHUB_APP_ENABLED` | backend | `true` to enable GitHub |
| `GITHUB_APP_ID` | backend | **Numeric** App ID (not the app name) |
| `GITHUB_APP_SLUG` | backend | App URL name, for the "install on repositories" link |
| `GITHUB_APP_CLIENT_ID` | backend | App Client ID (`Iv1.…` / `Iv23…`) |
| `GITHUB_APP_CLIENT_SECRET` | backend | App client secret |
| `GITHUB_APP_PRIVATE_KEY` | backend | App private key, one line with `\n` between lines |
| `GITHUB_APP_WEBHOOK_SECRET` | backend | Webhook secret (only when webhooks are relayed) |
| `GITLAB_URL` | backend | GitLab base URL |
| `GITLAB_OAUTH_CLIENT_ID` / `GITLAB_OAUTH_CLIENT_SECRET` | backend | GitLab OAuth application |
| `GITLAB_SERVICE_TOKEN` | backend | Optional `read_api` token for review-feedback enrichment |
| `GITLAB_ACCESS_TOKEN` | runner | Token the runner uses to clone and comment on GitLab |

## LLM

| Variable | Read by | Purpose |
|----------|---------|---------|
| `OPENCODE_ENABLED` | backend | Offer the OpenCode harness in Run Analysis |
| `OPENCODE_PROVIDER` | backend | Prefix for model ids, e.g. `openai` → `openai/gpt-4.1` |
| `OPENCODE_MODELS_URL` / `OPENCODE_API_KEY` | backend | Only to list models (`GET {url}/models`) for the picker. Same provider URL/key as below |
| `CLIO_HARNESS` | runner | `opencode` (recommended) or `copilot` |
| `CLIO_LLM_BASE_URL` / `CLIO_LLM_API_KEY` | runner | OpenAI-compatible endpoint used for the actual reviews |
| `CLIO_OPENCODE_MODEL` | runner | Default model when the run does not pick one |
| `COPILOT_ENABLED` / `COPILOT_CLIENT_ID` / `COPILOT_ENCRYPTION_KEY` | backend | Opt-in Copilot harness, see [LLM harnesses](llm-harnesses.md) |

`OPENCODE_API_KEY` is **not** an OpenCode account key; OpenCode has no accounts. It is your LLM provider key.

## Auth and runner

| Variable | Read by | Purpose |
|----------|---------|---------|
| `ARNOLD_UI_JWT_SECRET` | backend | Must equal the UI `auth.sessionSecret` when sign-in is enabled |
| `ARNOLD_RUNNER_API_KEY` | backend, runner | Shared key runners use to claim jobs and call back |
| `ARNOLD_RUNNER_MODE` | backend | `LOCAL` (default), `GITLAB` or `GITHUB`; see [runner modes](../../arnold-runner/ci/README.md) |
