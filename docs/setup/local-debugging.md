# Local debugging

How to trace a review run end to end on the docker compose stack.

## Where to look

| Component | Logs | What you see |
|-----------|------|--------------|
| Backend | `docker logs -f arnold_server_1` | API errors, validation failures, runner dispatch, GitHub/GitLab calls |
| Runner | `docker logs -f arnold_runner_1` | Job claims, workflow progress, LLM and SCM errors |
| UI | `docker logs -f arnold_ui_1` | Proxy errors (`ECONNREFUSED` = backend not up yet) |

A failed run's error is also shown in **Analysis History** on the selected run.

Useful checks:

```bash
curl -s localhost:3000/api/github-integrations/v1/status     # disconnected / connected / unconfigured
curl -s localhost:3000/api/app/v1/bootstrap | head -c 400     # user, GitLab status
docker logs arnold_server_1 2>&1 | grep -iE "error|warn" | tail
docker logs arnold_server_1 2>&1 | grep "runner/v1" | tail -3  # runner is polling for jobs
```

## How a job gets its SCM token

- **GitHub PRs:** when the GitHub App is configured, the backend mints a short-lived installation token scoped to the
  PR's repository and adds it to the job as `SCM_GITHUB_TOKEN`. The runner uses it to clone and comment as the app.
- **GitLab MRs:** the runner uses `GITLAB_ACCESS_TOKEN` from its own environment.

## Common run failures

| Error in Analysis History / runner log | Cause | Fix |
|----------------------------------------|-------|-----|
| `SCM token required for MR link parsing` (older images: `GitLab token required…`) on a GitHub PR | No GitHub token reached the job | Rebuild runner and server (`mvn … package && docker compose up -d --build server runner`). Then check the server log for *installation token* warnings: the app is not installed on the repository, or `GITHUB_APP_ID` / `GITHUB_APP_PRIVATE_KEY` is wrong |
| Same, on a GitLab MR | `GITLAB_ACCESS_TOKEN` unset | Set it in `.env`, `docker compose up -d --force-recreate runner` |
| `requestSmartDiff.request.execution.model: must match …` (backend log) | Older backend rejected an empty model | Rebuild the backend; a blank model now means the deployment default |
| `No API key available for the OpenCode provider` | `CLIO_LLM_API_KEY` unset | See [LLM harnesses](llm-harnesses.md) |
| Run stays *queued* | Runner not polling, or API key mismatch | `docker logs arnold_runner_1`; `ARNOLD_RUNNER_API_KEY` must be identical for server and runner |
| `Claim failed … Connection refused` (runner) | Backend still starting | Wait ~20 s |

## Stop-gap: personal token for GitHub

To bypass the GitHub App while debugging, give the runner a personal access token (repo read, pull request write):

```bash
# .env
SCM_GITHUB_TOKEN=ghp_...
docker compose up -d --force-recreate runner
```

Comments are then posted as you instead of the app. Remove it once the app works.

## Running a workflow by hand

```bash
# Dry run: print the review without posting
docker compose exec runner python /app/main.py improve --mr-link https://github.com/<owner>/<repo>/pull/<n> --dry-run
```

Set `SCM_GITHUB_TOKEN` (GitHub) or `GITLAB_ACCESS_TOKEN` (GitLab) in the runner environment first.

## Debugging the backend in an IDE

```bash
cd arnold-backend/arnold-server && sh run.sh d   # JDWP on port 5005
```

Attach a *Remote JVM debug* configuration to `localhost:5005`. Stop the compose `server` service first
(`docker compose stop server`) and point the UI at it with `ARNOLD_API_URL=http://host.docker.internal:8090`.
