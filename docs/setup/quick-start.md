# Quick start

Runs Arnold backend, UI, a local review runner and Aerospike on your machine.

Requirements: JDK 17, Maven 3.9, Docker or Podman with Compose (Podman: 8 GB+ free disk in the VM).

```bash
mvn -f arnold-backend/pom.xml -DskipTests package
cp deploy/.env.example .env
docker compose up --build
```

Open <http://localhost:3000>. The backend needs ~20 s to start; if the UI shows *Could not connect to backend API*,
wait and click **Retry**.

The quick start uses **development authentication**: no sign-in, every request is a local admin. Never expose it.

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
