# Environment variables

With Docker Compose, all of these go in `.env` at the repository root (copied from `deploy/.env.example`). Backend
defaults come from `arnold-backend/arnold-server/config/local.yml`.

**Read by**: **B** backend, **U** UI, **R** runner.

## Core

| Variable | Read by | Default | Purpose |
|---|---|---|---|
| `ARNOLD_UI_URL` | B, U | `http://localhost:3000` | Public UI URL. Builds OAuth callback URLs and links in notifications |
| `ARNOLD_API_URL` | U | from `api.*` config | Backend URL the UI proxies `/api` to |
| `ARNOLD_URL` | R | | Backend URL runners call |
| `ARNOLD_DEV_AUTH` | B | `true` | Development authentication. **Must be `false` in production** |
| `ARNOLD_ADMINS` | B | | Emails or user ids granted `superadmin`, comma-separated |
| `ARNOLD_ALLOW_DEV_AUTH` | U | | Must be `true` for the UI to start with `auth.mode: dev` |
| `ARNOLD_AUTH_MODE` | U | `oauth` | Overrides `auth.mode` (`oauth` / `dev`) |
| `ARNOLD_UI_JWT_SECRET` | B, U | dev value | Session JWT secret shared by UI and backend, ≥ 32 bytes |
| `ARNOLD_RUNNER_API_KEY` | B, R | | Shared key runners use for the backend |
| `ARNOLD_CREDENTIAL_ENCRYPTION_KEY` | B | | Base64 32-byte key for stored GitLab/GitHub tokens; required before connecting a provider |

## Storage

| Variable | Read by | Default | Purpose |
|---|---|---|---|
| `AEROSPIKE_HOSTS` | B | `localhost` | Aerospike seed hosts, comma-separated (port 3000) |
| `ELASTIC_ENABLED` | B | `false` | Enable the optional Elasticsearch analytics store |
| `ELASTIC_HOST` | B | `localhost` | Elasticsearch host |
| `ELASTIC_PORT` | B | `9200` | |
| `ELASTIC_SCHEME` | B | `http` | |
| `ELASTIC_USERNAME` | B | `elastic` | |
| `ELASTIC_PASSWORD` | B | `changeme` | |

## GitHub

| Variable | Read by | Default | Purpose |
|---|---|---|---|
| `GITHUB_APP_ENABLED` | B | `false` | Enable GitHub |
| `GITHUB_WEB_URL` | B | `https://github.com` | GitHub web URL (Enterprise Server: your host) |
| `GITHUB_API_URL` | B | `https://api.github.com` | API URL (Enterprise Server: `https://host/api/v3`) |
| `GITHUB_APP_ID` | B | `0` | **Numeric** App ID |
| `GITHUB_APP_SLUG` | B | | App URL name, for the install link |
| `GITHUB_APP_CLIENT_ID` | B | | `Iv1.…` / `Iv23…` |
| `GITHUB_APP_CLIENT_SECRET` | B | | |
| `GITHUB_APP_PRIVATE_KEY` | B | | PEM on one line with `\n` separators |
| `GITHUB_APP_WEBHOOK_SECRET` | B | | Webhook HMAC secret |
| `SCM_GITHUB_TOKEN` | R | set per job | Normally injected by the backend per job. Set by hand only for debugging |

## GitLab

| Variable | Read by | Default | Purpose |
|---|---|---|---|
| `GITLAB_URL` | B | `https://gitlab.com` | GitLab base URL |
| `GITLAB_OAUTH_CLIENT_ID` | B | | OAuth application id (scope `api`) |
| `GITLAB_OAUTH_CLIENT_SECRET` | B | | |
| `GITLAB_SERVICE_TOKEN` | B | | Optional `read_api` token: repository sync and feedback enrichment |
| `GITLAB_WEBHOOK_SECRET` | B | `local-webhook-secret` | Expected `X-Gitlab-Token` on webhooks |
| `GITLAB_ACCESS_TOKEN` | R | | Runner token to clone and comment (`api`) |

## Sync

| Variable | Read by | Default | Purpose |
|---|---|---|---|
| `ARNOLD_REPOSITORY_SYNC_ENABLED` | B | `true` | Periodically import every open PR/MR |
| `ARNOLD_REPOSITORY_SYNC_INTERVAL_SECONDS` | B | `300` | Interval |
| `ARNOLD_GITLAB_SYNC_GROUPS` | B | | Comma-separated GitLab group ids/paths; empty = all of the service token's projects |

## LLM

| Variable | Read by | Default | Purpose |
|---|---|---|---|
| `OPENCODE_ENABLED` | B | `true` | Offer OpenCode in Run Analysis |
| `OPENCODE_PROVIDER` | B | `openai` | Model id prefix (`openai/gpt-4.1`) |
| `OPENCODE_MODELS_URL` | B | | OpenAI-compatible base URL to list models |
| `OPENCODE_API_KEY` | B | | Key for the model listing |
| `CLIO_HARNESS` | R | `copilot` | Default harness: set to `opencode` |
| `CLIO_LLM_BASE_URL` | R | `https://api.openai.com/v1` | OpenAI-compatible endpoint for reviews |
| `CLIO_LLM_API_KEY` | R | | Its key (`OPENAI_API_KEY` is also accepted) |
| `CLIO_OPENCODE_MODEL` | R | `openai/gpt-4.1` | Default OpenCode model |
| `CLIO_OPENCODE_API_KEY` | R | `CLIO_LLM_API_KEY` | OpenCode-only key |
| `CLIO_OPENCODE_BASE_URL` | R | `CLIO_LLM_BASE_URL` | OpenCode-only endpoint |
| `CLIO_OPENCODE_TIMEOUT` | R | `2700` | OpenCode session timeout (s) |
| `CLIO_LLM_MODEL` | R | | Default Copilot model |
| `COPILOT_ENABLED` | B | `false` | Enable the opt-in Copilot harness |
| `COPILOT_CLIENT_ID` | B | | GitHub OAuth App with device flow |
| `COPILOT_ENCRYPTION_KEY` | B | | Base64 32-byte key for stored Copilot tokens |

## Runner dispatch

| Variable | Read by | Default | Purpose |
|---|---|---|---|
| `ARNOLD_RUNNER_MODE` | B | `LOCAL` | `LOCAL`, `GITLAB` or `GITHUB` |
| `RUNNER_GITLAB_PROJECT_ID` | B | `0` | GitLab runner project id |
| `RUNNER_GITLAB_PROJECT_PATH` | B | | e.g. `acme/arnold-runner` |
| `RUNNER_GITLAB_REF` | B | `main` | |
| `RUNNER_GITLAB_TRIGGER_TOKEN` | B | | Pipeline trigger token |
| `RUNNER_GITHUB_API_URL` | B | `https://api.github.com` | |
| `RUNNER_GITHUB_REPOSITORY` | B | | e.g. `acme/arnold-runner` |
| `RUNNER_GITHUB_WORKFLOW` | B | `arnold-runner.yml` | |
| `RUNNER_GITHUB_REF` | B | `main` | |
| `RUNNER_GITHUB_TOKEN` | B | | Token with `actions: write` on the runner repository |
| `ARNOLD_RUNNER_CONCURRENCY` | R | `1` | Worker concurrency |
| `ARNOLD_RUNNER_NAME` | R | hostname | Worker name prefix |
| `ARNOLD_RUNNER_JOB_TIMEOUT` | R | `9900` | Per-job timeout (s) |

## Optional integrations (runner)

| Variable | Purpose |
|---|---|
| `CLIO_AI_BOT_USER_ID` | Forge user id of the bot that posts inline suggestions |
| `RFC_CENTRAL_API_URL`, `RFC_API_TOKEN` | Design-doc (RFC) content service for review context |
| `IMPACT_SERVICE_URL`, `IMPACT_SERVICE_TOKEN` | Sink for post-merge impact data |

## Notifications (backend)

| Variable | Default | Purpose |
|---|---|---|
| `SLACK_API_URL` | `https://slack.com` | Slack API URL (Slack notifications are disabled by default: `slackClientConfig.enabled`) |
| `ARNOLD_SLACK_TOKEN` | | Slack bot token |
