# Troubleshooting

## Startup

| Symptom | Cause | Fix |
|---|---|---|
| UI: *Arnold is starting* / *Could not connect to backend API* | Backend still starting (~20 s) | Wait; the UI retries |
| Server fails at boot with Aerospike *not yet fully initialized* | Server connected before the cluster formed | The compose healthcheck prevents this; `server` also restarts on failure |
| Aerospike error 22 *Operation not allowed* | Namespace has `nsup-period 0` | Use `deploy/aerospike/aerospike.conf` or set `nsup-period` |
| UI exits: *auth.mode=dev is not allowed in production* | Dev auth without the opt-in | Set `ARNOLD_ALLOW_DEV_AUTH=true` (local only), or configure [sign-in](../setup/sign-in.md) |
| UI exits: *auth.sessionSecret must be at least 32 bytes* | Short secret in `oauth` mode | `openssl rand -hex 32` |
| UI exits: *Runtime configuration must set application.devMode to false* | `production.yaml` | Set `application.devMode: false` |
| `Incorrect type of value at: …githubConfiguration.appId` | `GITHUB_APP_ID` is the app name | Use the numeric App ID |

## Connecting forges

| Symptom | Cause | Fix |
|---|---|---|
| GitHub/GitLab shows a redirect URI error | Callback mismatch | GitHub: `<ui>/github/oauth/callback`; GitLab: `<ui>/gitlab/oauth/callback`, exactly |
| *Connect GitLab to continue* blocks every page | GitLab configured, GitHub not, user not connected | Connect GitLab, or configure GitHub too |
| `GitHub integration is not configured` | `GITHUB_APP_ENABLED`, client id or secret missing | Set them and recreate `server` |
| Copilot card says *Not configured* | `COPILOT_ENABLED`, `COPILOT_CLIENT_ID` or `COPILOT_ENCRYPTION_KEY` missing | See [LLM](../setup/llm.md#github-copilot-opt-in) |
| **All open** is empty | No repository sync source | Install the GitHub App on repositories, or set `GITLAB_SERVICE_TOKEN`; wait for the first sync (~30 s) |

## Reviews

| Symptom | Cause | Fix |
|---|---|---|
| Run Analysis has no OpenCode models | `OPENCODE_MODELS_URL`/`OPENCODE_API_KEY` unset, or no `/models` endpoint | Set them, or [list models](../setup/llm.md#models-without-a-models-endpoint) in `local.yml` |
| *No review engine is available yet* | No harness enabled | Enable OpenCode (`OPENCODE_ENABLED=true`) |
| Runner: *SCM token required for MR link parsing* on a GitHub PR, and the server logs *Could not mint a GitHub installation token … HTTP 401 Bad credentials* | Host clock drift (e.g. Podman VM after sleep) makes the App JWT look expired; or wrong App ID/key | Compare `date -u` with `docker exec arnold_server_1 date -u`. Podman: `podman machine ssh "sudo date -u -s '$(date -u '+%F %T')'"` or restart the machine |
| *HTTP 400* from the model provider | Gateway does not accept the parameters for that model | Use a supported model; check the gateway's OpenAI compatibility |
| More run failures | | See [Debugging](debugging.md#common-run-failures) |

## Containers

| Symptom | Fix |
|---|---|
| Podman build: *no space left on device* | `podman image prune -f`, or grow the VM disk |
| podman-compose passes `${VAR:-x}` literally | Put values in `.env` (the default setup already does) |
| UI container slow to stop | Rebuild `ui`; current images handle SIGTERM |
| Stale UI after pulling changes | `docker compose up -d --build ui` |
