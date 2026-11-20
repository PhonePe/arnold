# Debugging

How to trace a review run end to end on the Docker Compose stack.

## Where to look

| Component | Logs | What you see |
|---|---|---|
| Backend | `docker compose logs -f server` | API errors, validation failures, runner dispatch, GitHub/GitLab calls |
| Runner | `docker compose logs -f runner` | Job claims, workflow progress, LLM and forge errors |
| UI | `docker compose logs -f ui` | Sign-in and proxy errors (`ECONNREFUSED` = backend not up yet) |

A failed run's error is also shown on the run in **Analysis History**.

## Quick checks

```bash
curl -s localhost:8091/healthcheck                              # backend health
curl -s localhost:3000/api/app/v1/setup                         # what the setup guide sees
curl -s localhost:3000/api/github-integrations/v1/status        # unconfigured / disconnected / connected
curl -s localhost:3000/api/analysis-execution/v1                # available harnesses and models
docker compose logs server 2>&1 | grep -iE "error|warn" | tail
docker compose logs server 2>&1 | grep "runner/v1" | tail -3   # the runner is polling for jobs
```

(`/api` calls work without a session only in development mode.)

## How a job gets its forge token

- **GitHub PRs**: the backend mints a short-lived installation token for the PR's repository and adds it to the job
  as `SCM_GITHUB_TOKEN`. If minting fails, the server logs a warning mentioning *installation token*.
- **GitLab MRs**: the runner uses `GITLAB_ACCESS_TOKEN` from its own environment.

## Common run failures

| Error (Analysis History / runner log) | Cause | Fix |
|---|---|---|
| `SCM token required for MR link parsing` on a GitHub PR | No GitHub token reached the job | Check the server log for *installation token* warnings: the app is not installed on the repository, or `GITHUB_APP_ID` / `GITHUB_APP_PRIVATE_KEY` is wrong |
| Same, on a GitLab MR | `GITLAB_ACCESS_TOKEN` unset | Set it, `docker compose up -d --force-recreate runner` |
| `No API key available for the OpenCode provider` | `CLIO_LLM_API_KEY` unset | See [LLM](../setup/llm.md) |
| *the model provider rejected the request (HTTP 400 …)* | Model/endpoint mismatch | Use a model the endpoint supports, with tool calling |
| Run stays *Running* and the runner log is quiet | Runner not polling, or API key mismatch | `ARNOLD_RUNNER_API_KEY` must be identical for server and runner |
| `Claim failed … Connection refused` (runner) | Backend still starting | Wait ~20 s |

## Bypass the GitHub App while debugging

Give the runner a personal access token (repository read, pull request write):

```bash title=".env"
SCM_GITHUB_TOKEN=ghp_...
```

```bash
docker compose up -d --force-recreate runner
```

Comments are then posted as you. Remove it once the app works.

## Run a workflow by hand

```bash
# Print the review without posting
docker compose exec runner python /app/main.py improve \
  --mr-link https://github.com/<owner>/<repo>/pull/<n> --dry-run
```

See [Runner workflows (CLI)](../development/runner-cli.md) for all commands.

## Debug the backend in an IDE

```bash
docker compose stop server
cd arnold-backend/arnold-server && sh run.sh d    # JDWP on port 5005
```

Attach a *Remote JVM Debug* configuration to `localhost:5005`, and point the UI at your local backend with
`ARNOLD_API_URL=http://host.docker.internal:8090`. Raise log levels in `local.yml`:

```yaml
logging:
  loggers:
    com.phonepe.arnold: DEBUG
```

## Run the UI from source

```bash
cd arnold-ui
npm ci
ARNOLD_API_URL=http://localhost:8090 npm run dev     # http://localhost:3000, uses config/development.yaml
```
