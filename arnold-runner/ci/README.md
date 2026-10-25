# Runner modes

Arnold's backend setting `serviceCoreConfiguration.runner.mode` decides where review workflows run. Every mode runs
the same image (`ghcr.io/phonepe/arnold-runner`) and the same job contract: a flat map of variables such as
`WORKFLOW`, `ARNOLD_RUN_ID`, `MR_LINK` and `EXECUTION_ID`. The runner posts review comments to the SCM directly and
reports results to Arnold with `ARNOLD_RUNNER_API_KEY`.

| Mode | How a job reaches the runner | Where runner credentials live |
|------|------------------------------|-------------------------------|
| `LOCAL` | Jobs are queued in Arnold; `arnold-runner worker` processes long-poll `/runner/v1/jobs/claim` | Worker environment |
| `GITLAB` | Arnold triggers a pipeline in the runner project with the job as pipeline variables | Runner project CI/CD variables |
| `GITHUB` | Arnold queues the job and dispatches a workflow with only the job number; the workflow claims it | Runner repository secrets |

The backend API key used by runners needs the `runner_execute`, `analysis_callback_write`, `smart_diff_callback_write`
and `review_feedback_write` roles:

```yaml
serviceCoreConfiguration:
  auth:
    apiKeys:
      - name: runner
        key: ${ARNOLD_RUNNER_API_KEY}
        roles: [runner_execute, analysis_callback_write, smart_diff_callback_write, review_feedback_write]
```

## GitHub pull requests

When the backend's GitHub App is configured (`githubConfiguration.appId` and `privateKey`), Arnold adds a short-lived
installation token scoped to the pull request's repository to each job as `SCM_GITHUB_TOKEN`. The runner then clones and
posts review comments as the Arnold app, and needs no long-lived GitHub credential. Install the app with
`Pull requests: read & write`, `Contents: read`, `Checks: read` and `Metadata: read` permissions.

## Local worker

```bash
docker run --rm \
  -e ARNOLD_URL=https://arnold.example.com -e ARNOLD_RUNNER_API_KEY=... \
  -e GITLAB_ACCESS_TOKEN=... -e CLIO_LLM_BASE_URL=... -e CLIO_LLM_API_KEY=... \
  ghcr.io/phonepe/arnold-runner:latest worker --concurrency 2
```

Without Docker: `pip install -r requirements.txt && python main.py worker`. Workers need only outbound access to
Arnold, the SCM and the LLM endpoint. A job whose worker stops heartbeating is requeued once
(`runner.local.maxAttempts`), then marked failed.

## GitLab CI

Use [`gitlab/arnold-runner.gitlab-ci.yml`](gitlab/arnold-runner.gitlab-ci.yml) in a runner project, create a pipeline
trigger token, and configure the backend:

```yaml
runner:
  mode: GITLAB
  gitlab: { projectId: 123, projectPath: acme/arnold-runner, ref: main, triggerToken: ${RUNNER_GITLAB_TRIGGER_TOKEN} }
```

Keep CI debug tracing off; job variables include short-lived execution keys for the opt-in Copilot harness.

## GitHub Actions

Copy [`github/arnold-runner.yml`](github/arnold-runner.yml) to `.github/workflows/` in a private runner repository and
configure the backend with a token that has `actions: write` on it:

```yaml
runner:
  mode: GITHUB
  github: { repository: acme/arnold-runner, workflow: arnold-runner.yml, ref: main, token: ${RUNNER_GITHUB_TOKEN} }
```
