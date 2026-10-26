# PhonePe Arnold

Arnold is an AI-assisted code review platform for GitLab and GitHub. It gives reviewers a merge-request workspace —
diffs, discussions, draft reviews, a personal merge queue and review stats — and runs AI review workflows that post
inline suggestions back to the merge request or pull request.

## How it fits together

```
           ┌──────────────┐    REST     ┌────────────────┐   Aerospike (state)
 browser ─▶│  arnold-ui   │───────────▶ │ arnold-backend │── Elasticsearch (analytics, optional)
           │  (Next.js)   │  /api proxy │  (Dropwizard)  │
           └──────────────┘             └───────┬────────┘
                                                │ jobs (local queue, GitLab CI or GitHub Actions)
                                                ▼
                                        ┌────────────────┐   review comments    ┌──────────────────┐
                                        │ arnold-runner  │─────────────────────▶│  GitLab / GitHub  │
                                        │  (PocketFlow)  │◀── callbacks ───┐    └──────────────────┘
                                        └────────────────┘                 │
                                                └──────────────────────────┘
```

- **arnold-ui** signs users in (GitHub, Google or any OpenID Connect provider) and proxies API calls to the backend
  with a session token.
- **arnold-backend** keeps merge requests, reviews, personas and analysis runs, talks to GitLab (OAuth application) and
  GitHub (GitHub App) on behalf of each user, and dispatches review jobs.
- **arnold-runner** executes review workflows with an LLM harness (OpenCode against any OpenAI-compatible endpoint, or
  the opt-in GitHub Copilot harness), posts comments to the forge, and reports results back to Arnold.

## Quick start

Requirements: JDK 17, Maven 3.9, Docker (or Podman) with Compose.

```bash
mvn -f arnold-backend/pom.xml -DskipTests package
cp deploy/.env.example .env      # uncomment GitLab/GitHub and LLM settings as needed
docker compose up --build
```

Open <http://localhost:3000>. The quick start uses development authentication (no sign-in) and runs a local review
worker. See [deployment](#deployment) before exposing Arnold to anyone else.

## Modules

| Module                         | Description                                                                          |
|--------------------------------|--------------------------------------------------------------------------------------|
| `arnold-backend/arnold-server` | Dropwizard application wiring around the core bundle.                                |
| `arnold-backend/arnold-core`   | Resources, services, version control providers, auth, runner dispatch and storage.   |
| `arnold-backend/arnold-models` | Shared request, response and document models.                                        |
| `arnold-ui`                    | Next.js frontend and the Express server that handles sign-in and the API proxy.      |
| `arnold-runner`                | Python review workflows (improve, review, describe, smart diff, group review, impact). |
| `deploy`                       | Docker Compose quick-start configuration.                                            |

## Deployment

| Concern | Where to configure |
|---------|--------------------|
| Sign-in | `arnold-ui/config/production.example.yaml` (`auth.providers`) and the backend `auth.jwt.issuers` entry that trusts the UI |
| GitLab | Backend `gitlabConfiguration` (OAuth application with the `api` scope) |
| GitHub | Backend `githubConfiguration` (GitHub App: user-to-server tokens, installation tokens, webhook secret) |
| Review runners | Backend `runner.mode` — `LOCAL`, `GITLAB` or `GITHUB`; see [arnold-runner/ci](arnold-runner/ci/README.md) |
| Storage | Aerospike (`aerospike`, `aerospikeStorageConfiguration`); Elasticsearch for analytics (`elasticSearchConfiguration`) |

The annotated backend configuration is [arnold-server/config/local.yml](arnold-backend/arnold-server/config/local.yml);
every secret in it is read from an environment variable.

## Documentation

- Setup and options: [docs/](docs/README.md)
- Backend: [arnold-backend/BACKEND.md](arnold-backend/BACKEND.md)
- UI: [arnold-ui/README.md](arnold-ui/README.md)
- Runner: [arnold-runner/README.md](arnold-runner/README.md) and [runner modes](arnold-runner/ci/README.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md) · Security: [SECURITY.md](SECURITY.md)

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
