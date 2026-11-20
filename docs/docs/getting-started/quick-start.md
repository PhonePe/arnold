# Quick start

This runs the Arnold backend, the UI, a local review runner and Aerospike on your machine.

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 | Builds the backend jar |
| Maven | 3.9+ | |
| Docker or Podman | with Compose | Podman needs 8 GB+ free disk in its VM |

## 1. Build and start

```bash
git clone https://github.com/PhonePe/arnold.git && cd arnold
mvn -f arnold-backend/pom.xml -DskipTests package
cp deploy/.env.example .env
docker compose up --build
```

Open <http://localhost:3000>.

The backend takes about 20 seconds to start. Until it is ready the UI shows *Arnold is starting* and retries by
itself. A **Set up Arnold** guide then walks you through whatever `.env` configures: connecting GitHub or GitLab,
installing the GitHub App on repositories, and connecting Copilot if it is enabled.

!!! danger "Development authentication"
    The quick start runs with **development authentication**: there is no sign-in and every request acts as a
    local superadmin. Never expose this setup to a network. See [Sign-in](../setup/sign-in.md).

## 2. Connect a forge and a model

Arnold is not useful until it can see your code and call a model. Edit `.env`:

=== "GitHub"

    Create a GitHub App (about 5 minutes, see [GitHub App](../setup/github-app.md)), then:

    ```bash
    GITHUB_APP_ENABLED=true
    GITHUB_APP_ID=1234567
    GITHUB_APP_SLUG=my-arnold
    GITHUB_APP_CLIENT_ID=Iv23...
    GITHUB_APP_CLIENT_SECRET=...
    GITHUB_APP_PRIVATE_KEY=-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----
    ```

=== "GitLab"

    Create a GitLab OAuth application with the `api` scope (see [GitLab](../setup/gitlab.md)), then:

    ```bash
    GITLAB_URL=https://gitlab.com
    GITLAB_OAUTH_CLIENT_ID=...
    GITLAB_OAUTH_CLIENT_SECRET=...
    GITLAB_ACCESS_TOKEN=glpat-...    # runner: clone and comment
    GITLAB_SERVICE_TOKEN=glpat-...   # optional: import every open MR
    ```

Add an OpenAI-compatible endpoint for reviews (see [LLM and harnesses](../setup/llm.md)):

```bash
CLIO_HARNESS=opencode
CLIO_LLM_BASE_URL=https://api.openai.com/v1
CLIO_LLM_API_KEY=sk-...
OPENCODE_MODELS_URL=https://api.openai.com/v1
OPENCODE_API_KEY=sk-...
```

Recreate the services that read `.env`:

```bash
docker compose up -d --force-recreate server runner
```

Then open **Settings → Connected accounts** and connect GitHub or GitLab.

## 3. Review something

1. Click **Run Analysis**, paste a pull request or merge request URL, and click **Fetch details**.
2. Optionally add an RFC link, Jira tickets or extra context.
3. Pick up to three review personas, a harness and a model.
4. Start. Follow progress in **Analysis History**. Findings appear as inline comments on the PR/MR and in the
   Arnold review workspace.

## Where your pull requests come from

Arnold imports every open pull request from repositories where its GitHub App is installed, and every open merge
request visible to `GITLAB_SERVICE_TOKEN` (optionally limited by `ARNOLD_GITLAB_SYNC_GROUPS`). The first import starts
about 30 seconds after boot and repeats every `ARNOLD_REPOSITORY_SYNC_INTERVAL_SECONDS` (default 300). They appear
under **Merge Requests → All open**.

Forges cannot reach webhooks on `localhost`, so locally this periodic import keeps Arnold current. See
[Webhooks and sync](../setup/webhooks-and-sync.md) to forward webhooks for instant updates.

## Day-to-day commands

```bash
docker compose up -d --force-recreate server runner   # after editing .env
docker compose up -d --build ui                        # after UI code or config changes
mvn -f arnold-backend/pom.xml -DskipTests package \
  && docker compose up -d --build server               # after backend code changes
docker compose --profile analytics up -d               # add Elasticsearch for insights and metrics
docker compose down                                    # stop (data is kept)
docker compose down -v                                 # stop and wipe all data
```

| Service | URL / port | Logs |
|---|---|---|
| UI | <http://localhost:3000> | `docker logs -f arnold_ui_1` |
| Backend API | <http://localhost:8090> | `docker logs -f arnold_server_1` |
| Backend admin (health, metrics) | <http://localhost:8091> | |
| Runner | none (polls the backend) | `docker logs -f arnold_runner_1` |

Data (users, connections, runs) lives in the `aerospike-data` volume and survives restarts.

## Next

- [Production deployment](../setup/production.md#run-the-published-images): run the released images with real sign-in.
- [Architecture](architecture.md): how the parts talk to each other.
- [Setup](../setup/index.md): everything you can configure.
- [Troubleshooting](../operations/troubleshooting.md): if something does not come up.
