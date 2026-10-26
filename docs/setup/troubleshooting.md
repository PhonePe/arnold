# Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| UI: *Could not connect to backend API* right after start | Backend still starting (~20 s) | Wait, click **Retry** |
| `Incorrect type of value at: …githubConfiguration.appId` | `GITHUB_APP_ID` set to the app name | Use the numeric App ID |
| GitHub authorize page errors on redirect | Callback URL mismatch | App callback must be exactly `http://localhost:3000/github/oauth/callback` |
| *Connect GitLab to continue* blocks every page | GitHub not configured | Configure GitHub, or connect GitLab |
| `GitHub integration is not configured` | Missing `GITHUB_APP_ENABLED`, client id or secret | Set them and recreate `server` |
| Copilot card says *Not configured* | `COPILOT_ENABLED`, `COPILOT_CLIENT_ID` or `COPILOT_ENCRYPTION_KEY` missing | See [LLM harnesses](llm-harnesses.md) |
| Run Analysis has no OpenCode models | `OPENCODE_MODELS_URL`/`OPENCODE_API_KEY` unset or endpoint has no `/models` | Set them, or list models in `local.yml` |
| Aerospike error 22 *Operation not allowed* | Namespace has `nsup-period 0` | Use `deploy/aerospike/aerospike.conf` |
| Podman build: *no space left on device* | VM disk full | `podman image prune -f` |
| Podman compose passes `${VAR:-x}` literally | podman-compose limitation | Put values in `.env` (already the default) |
| UI container slow to stop | Old image without signal handling | Rebuild `ui` |
