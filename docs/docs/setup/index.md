# Setup

Arnold reads its settings from three places. For Docker Compose, almost everything goes into one `.env` file.

| Component | Configuration | In Docker Compose |
|---|---|---|
| Backend | `arnold-backend/arnold-server/config/local.yml`; every secret is a `${ENV_VAR}` | `.env` (via `env_file`) |
| UI | `arnold-ui/config/*.yaml` (node-config), plus a few env vars | `deploy/ui/development.yaml` mounted as `production.yaml` |
| Runner | Environment variables only | `.env` (via `env_file`) |

`.env` is created from `deploy/.env.example` and is git-ignored. Never commit real values.

## Setup checklist

| Step | Required | Page |
|---|---|---|
| Run the stack | yes | [Quick start](../getting-started/quick-start.md) |
| Connect at least one forge | yes | [GitHub App](github-app.md), [GitLab](gitlab.md) |
| Configure a review model | yes, for AI features | [LLM and harnesses](llm.md) |
| Choose where reviews run | default `LOCAL` works | [Runners](runners.md) |
| Real sign-in | before anyone else uses it | [Sign-in](sign-in.md) |
| Elasticsearch for insights | optional | [Storage and analytics](storage.md) |
| Webhooks for instant updates | optional | [Webhooks and sync](webhooks-and-sync.md) |
| Hardening, TLS, scaling | for shared deployments | [Production deployment](production.md) |

## Choosing options

**GitHub or GitLab?** Both can be enabled together. GitHub uses a GitHub App, so Arnold can mint short-lived,
repository-scoped tokens for runners and act as the user through user-to-server tokens. GitLab uses an OAuth
application per user plus a token for the runner.

**OpenCode or Copilot?** Use OpenCode with your own OpenAI-compatible endpoint. Copilot is opt-in and has licensing
caveats; see [LLM and harnesses](llm.md#github-copilot-opt-in).

**Which runner mode?** `LOCAL` (workers next to the backend) is the simplest and the default. Use `GITLAB` or `GITHUB`
to run reviews on CI capacity you already have; see [Runners](runners.md).

## Applying changes

After editing configuration, recreate what reads it:

| You changed | Run |
|---|---|
| `.env` (backend or runner settings) | `docker compose up -d --force-recreate server runner` |
| `deploy/ui/*.yaml` | `docker compose up -d --force-recreate ui` |
| `local.yml` | Rebuild from source: see [Building](../development/building.md#run-the-stack-from-source) |

The backend logs every configuration validation error at boot (`docker compose logs server`).
