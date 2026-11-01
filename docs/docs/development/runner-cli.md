# Runner workflows (CLI)

`arnold-runner/main.py` runs workflows directly, which is useful for debugging and experimentation. In the compose
stack: `docker compose exec runner python /app/main.py <command> …`.

## Commands

| Command | What it does |
|---|---|
| `worker` | Claim and run jobs from Arnold (`LOCAL` mode). `--concurrency N`, `--name`, `--once` |
| `job` | Run one job from environment variables (GitLab CI), or `--claim NUMBER` (GitHub Actions) |
| `improve` | Inline review findings (the main Arnold workflow) |
| `review` | High-level review summary |
| `describe` | Structured PR/MR description |
| `smart_diff` | Story mode walkthrough |
| `group_review` | Review a group of changes (`--run-id`, `--execution-id`) |
| `impact` | Post-merge impact analysis |
| `sonarfix` | Fix SonarQube issues on a branch |

## Common options

| Option | Meaning |
|---|---|
| `--mr-link URL` | PR/MR to review (or `--repo`, `--local-dir`) |
| `-s`, `--source-branch` / `-t`, `--target-branch` | Branches when not using `--mr-link` (target defaults to `main`) |
| `--token` | GitLab token (default `GITLAB_ACCESS_TOKEN`) |
| `--gitlab-url`, `--project-id`, `--mr-iid` | GitLab coordinates |
| `--jira-url`, `--jira-token`, `--jira-cloud-id`, `--jira-browse-url`, `--jira-tickets` | Jira context |
| `--rfc-url`, `--rfc-api-url` | Design doc context |
| `--dry-run` | Do not post anything |
| `--no-cleanup` | Keep the checkout for inspection |

`improve` also takes `-o FILE` (default `code_review_output.yaml`), `--min-confidence` (default `0.6`) and
`--no-cache`.

## Examples

```bash
# Review a GitHub PR without posting
SCM_GITHUB_TOKEN=ghp_... CLIO_HARNESS=opencode CLIO_LLM_API_KEY=sk-... \
  python main.py improve --mr-link https://github.com/acme/api/pull/42 --dry-run

# Review a GitLab MR and post findings
GITLAB_ACCESS_TOKEN=glpat-... python main.py improve \
  --mr-link https://gitlab.example.com/acme/api/-/merge_requests/7 --jira-tickets PROJ-123

# Review local changes against main
python main.py improve --local-dir . -s my-feature -t main --dry-run
```

## Job contract

Arnold-dispatched jobs map `WORKFLOW` to a command: `improve`, `review`, `describe`, `smart_diff`, `group_review`;
`fix_sonar` → `sonarfix`; `review_analysis` → `impact`. See [Runner configuration](../configuration/runner.md#job-variables).
