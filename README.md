# Arnold

Arnold is an AI-driven code review platform over popular software Forges (Currently Gitlab on-prem).

An AI review workflow analyses merge requests and posts review comments back on GitLab via CIBot. Arnold ingests the
feedback produced by that pipeline, enriches it with project, author and reviewer metadata, and persists each comment to
Elasticsearch so it can be searched, aggregated and visualized (e.g. via Kibana and the Arnold UI).

The UI surfaces this data through a merge queue, an MR review workspace, persona and policy management, and quality
stats.

## How it fits together

```
AI review workflow ──▶ GitLab (via CIBot) ──▶ Arnold backend ──▶ Elasticsearch ──▶ Arnold UI / Kibana
```

- An AI workflow reviews a merge request and posts comments on GitLab through CIBot.
- The workflow sends that feedback to Arnold, which enriches it (project, author, reviewer) and indexes each comment.
- Developers and reviewers explore the results through the Arnold UI and Kibana.

## Modules

| Module          | Description                                                                                          |
|-----------------|------------------------------------------------------------------------------------------------------|
| `arnold-server` | Dropwizard application wiring. Hosts the core bundle (`ArnoldBundle`); no business logic of its own. |
| `arnold-core`   | The heart of the backend: feedback ingestion, Elasticsearch persistence (Columbus), CIBot integration. |
| `arnold-models` | Shared data models — review-feedback request/response and Elasticsearch document classes.            |
| `arnold-client` | Java client for talking to the Arnold backend service.                                               |
| `arnold-ui`     | Next.js frontend for the code review platform.                                                       |

## Documentation

- Backend setup, conventions and local debugging: [BACKEND.md](arnold-backend/BACKEND.md)
- Frontend (UI) guidelines: [arnold-ui/AGENTS.md](arnold-ui/AGENTS.md) and [arnold-ui/README.md](arnold-ui/README.md)
- Repository and module overview: [AGENTS.md](AGENTS.md)
