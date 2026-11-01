# Runners

Review workflows run in the **arnold-runner** image (`ghcr.io/phonepe/arnold-runner`). The backend setting
`runner.mode` (`ARNOLD_RUNNER_MODE`) decides how jobs reach a runner. Every mode uses the same image and the same job
contract: a flat set of variables such as `WORKFLOW`, `ARNOLD_RUN_ID`, `MR_LINK` and `EXECUTION_ID`.

| Mode | How a job reaches a runner | Runner credentials live in | Good for |
|---|---|---|---|
| `LOCAL` (default) | Jobs are queued in Arnold; `worker` processes long-poll and claim them | Worker environment | Docker Compose, Kubernetes, VMs |
| `GITLAB` | Arnold triggers a pipeline in a runner project with the job as pipeline variables | Runner project CI/CD variables | Reusing GitLab CI capacity |
| `GITHUB` | Arnold queues the job and dispatches a workflow with only the job number; the workflow claims it | Runner repository secrets | Reusing GitHub Actions capacity |

In every mode the runner posts review comments to the forge directly and reports results to Arnold. It needs outbound
access to the backend, the forge and the LLM endpoint, and no inbound access.

## Runner API key

Runners authenticate to the backend with a shared API key sent as a bearer token. The backend entry needs these roles:

```yaml title="local.yml (default)"
serviceCoreConfiguration:
  auth:
    apiKeys:
      - name: runner
        key: ${ARNOLD_RUNNER_API_KEY}
        roles: [runner_execute, analysis_callback_write, smart_diff_callback_write, review_feedback_write]
```

Set the same `ARNOLD_RUNNER_API_KEY` for the backend and every runner. Generate a strong value
(`openssl rand -hex 32`); the example `local-runner-key-change-me` is for local use only.

## Forge credentials for jobs

| Forge | Credential |
|---|---|
| GitHub | With the GitHub App configured, each job carries a short-lived installation token for the PR's repository (`SCM_GITHUB_TOKEN`). Nothing to configure on runners |
| GitLab | The runner's own `GITLAB_ACCESS_TOKEN` (`api` scope) |

## LOCAL

Workers poll `POST /runner/v1/jobs/claim`, send heartbeats while working, and post completion. A job whose worker stops
heartbeating for `runner.local.leaseSeconds` (300) is requeued, up to `runner.local.maxAttempts` (2), then marked failed.

```bash
docker run --rm \
  -e ARNOLD_URL=https://arnold-api.example.com \
  -e ARNOLD_RUNNER_API_KEY=... \
  -e GITLAB_ACCESS_TOKEN=glpat-... \
  -e CLIO_HARNESS=opencode \
  -e CLIO_LLM_BASE_URL=https://api.openai.com/v1 \
  -e CLIO_LLM_API_KEY=sk-... \
  ghcr.io/phonepe/arnold-runner:latest worker --concurrency 2
```

| Option | Env | Default | Purpose |
|---|---|---|---|
| `--concurrency N` | `ARNOLD_RUNNER_CONCURRENCY` | 1 | Jobs processed in parallel by this worker |
| `--name` | `ARNOLD_RUNNER_NAME` | hostname | Worker name prefix used in job leases |
| `--once` | | | Handle one claim per slot, then exit |
| | `ARNOLD_RUNNER_JOB_TIMEOUT` | 9900 s | Hard limit per job, in seconds |

Scale by running more workers. Each job clones repositories into a temporary directory and runs an agent, so allow
roughly 1 CPU and 2 GB memory per concurrent job, plus disk for checkouts.

Without Docker: `cd arnold-runner && pip install -r requirements.txt && npm i -g opencode-ai && python main.py worker`.

## GitLab CI

1. Create a project for the runner, e.g. `acme/arnold-runner`, and add this `.gitlab-ci.yml`:

    ```yaml title=".gitlab-ci.yml"
    --8<-- "arnold-runner/ci/gitlab/arnold-runner.gitlab-ci.yml"
    ```

2. Add masked, protected CI/CD variables: `ARNOLD_URL`, `ARNOLD_RUNNER_API_KEY`, `GITLAB_ACCESS_TOKEN`,
   `CLIO_HARNESS`, `CLIO_LLM_BASE_URL`, `CLIO_LLM_API_KEY`.
3. Create a pipeline trigger token: **Settings → CI/CD → Pipeline trigger tokens**.
4. Configure the backend:

    ```bash title=".env"
    ARNOLD_RUNNER_MODE=GITLAB
    RUNNER_GITLAB_PROJECT_ID=123
    RUNNER_GITLAB_PROJECT_PATH=acme/arnold-runner
    RUNNER_GITLAB_REF=main
    RUNNER_GITLAB_TRIGGER_TOKEN=glptt-...
    ```

    The triggering GitLab is `GITLAB_URL`. In Docker Compose, also remove `ARNOLD_RUNNER_MODE: LOCAL` from the
    `server` service, or it overrides `.env`, and stop the local `runner` service.

!!! warning
    Keep CI debug tracing off. Job variables can include short-lived execution keys for the Copilot harness.

## GitHub Actions

1. Create a **private** repository for the runner, e.g. `acme/arnold-runner`, and add this workflow as
   `.github/workflows/arnold-runner.yml`:

    ```yaml title=".github/workflows/arnold-runner.yml"
    --8<-- "arnold-runner/ci/github/arnold-runner.yml"
    ```

2. Add repository secrets: `ARNOLD_URL`, `ARNOLD_RUNNER_API_KEY`, `CLIO_LLM_BASE_URL`, `CLIO_LLM_API_KEY`, and
   `SCM_GITLAB_TOKEN` if you also review GitLab MRs.
3. Create a token that can dispatch workflows in that repository: a fine-grained PAT with **Actions: Read and write**
   on it only.
4. Configure the backend:

    ```bash title=".env"
    ARNOLD_RUNNER_MODE=GITHUB
    RUNNER_GITHUB_REPOSITORY=acme/arnold-runner
    RUNNER_GITHUB_WORKFLOW=arnold-runner.yml
    RUNNER_GITHUB_REF=main
    RUNNER_GITHUB_TOKEN=github_pat_...
    # RUNNER_GITHUB_API_URL=https://github.example.com/api/v3   # GitHub Enterprise Server
    ```

The workflow input carries only the job number. The workflow claims the job from Arnold, so review details and
tokens never appear in workflow inputs or logs.

## Choosing a mode

- Start with `LOCAL`. It has the lowest latency (no pipeline start-up) and the fewest moving parts.
- Use `GITLAB` or `GITHUB` when reviews must run inside an existing CI trust boundary or you want CI to absorb the
  compute. Expect 20–60 seconds of extra start-up per run.
- `GITLAB` and `GITHUB` modes need the backend reachable from CI runners (`ARNOLD_URL`).
