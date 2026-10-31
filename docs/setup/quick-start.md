# Quick start

Runs Arnold backend, UI, a local review runner and Aerospike on your machine.

Requirements: JDK 17, Maven 3.9, Docker or Podman with Compose (Podman: 8 GB+ free disk in the VM).

```bash
mvn -f arnold-backend/pom.xml -DskipTests package
cp deploy/.env.example .env
docker compose up --build
```

Open <http://localhost:3000>. The backend needs ~20 s to start; the UI shows *Arnold is starting* and continues on
its own. A setup guide then walks you through connecting GitHub or GitLab based on what `.env` configures.

The quick start uses **development authentication**: no sign-in, every request is a local admin. Never expose it.

Stop with `docker compose down`. Data (users, connections, runs) lives in the `aerospike-data` volume and survives
restarts; `docker compose down -v` wipes it.

## Pull requests and merge requests in Arnold

Arnold imports every open pull request from repositories where its GitHub App is installed, and every open merge request
visible to `GITLAB_SERVICE_TOKEN` (optionally limited by `ARNOLD_GITLAB_SYNC_GROUPS`). The first import starts ~30 s
after boot and repeats every `ARNOLD_REPOSITORY_SYNC_INTERVAL_SECONDS` (default 300); see them under
**Merge Requests → All open**. Locally, GitHub/GitLab cannot reach your webhooks, so this periodic import keeps things
current; forward webhooks (e.g. smee.io) for instant updates. Any URL can also be reviewed directly from **Run Analysis**.

## What to configure next

1. A forge: [GitHub App](github-app.md) and/or [GitLab](gitlab.md).
2. An LLM for reviews: [LLM harnesses](llm-harnesses.md).
3. Optionally, real login: [Sign-in providers](sign-in.md).

After editing `.env`, recreate the affected services:

```bash
docker compose up -d --force-recreate server runner   # backend / runner settings
docker compose up -d --build ui                        # after UI code or config changes
mvn -f arnold-backend/pom.xml -DskipTests package && docker compose up -d --build server   # backend code changes
```

Logs: `docker logs -f arnold_server_1`, `docker logs -f arnold_runner_1`, `docker logs -f arnold_ui_1`.

Analytics (Organisation Insights) needs Elasticsearch: `docker compose --profile analytics up -d`.
