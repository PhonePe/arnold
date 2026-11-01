# Backend configuration (local.yml)

The backend is a Dropwizard application configured by one YAML file:
`arnold-backend/arnold-server/config/local.yml`. The container image copies it to `/app/config/arnold.yml`.
Values written as `${VAR:-default}` are read from the environment, so most deployments change only environment
variables (see [Environment variables](environment.md)).

## Sections

| Section | Purpose |
|---|---|
| `server` | Dropwizard HTTP server: API port 8090, admin port 8091, thread pool, request log |
| `logging` | Log level and format. Set `com.phonepe.arnold: DEBUG` to trace requests to forges |
| `swagger` | API documentation for the resources package |
| `elasticSearchConfiguration.columbus` | Elasticsearch connection and index names ([Storage](../setup/storage.md#elasticsearch-optional)) |
| `serviceCoreConfiguration.auth` | Dev auth, trusted JWT issuers, API keys, admins, role mappings ([Roles and API keys](auth.md)) |
| `serviceCoreConfiguration.copilotConfiguration` | Opt-in Copilot harness ([LLM](../setup/llm.md#github-copilot-opt-in)) |
| `serviceCoreConfiguration.openCodeConfiguration` | OpenCode harness and model list ([LLM](../setup/llm.md#opencode)) |
| `serviceCoreConfiguration.asyncExecutor` | Background thread pool (`coreSize`, `maxSize`, `queueCapacity`, …) |
| `serviceCoreConfiguration.aerospikeStorageConfiguration` | Namespace and set for each entity ([Storage](../setup/storage.md#aerospike-required)) |
| `serviceCoreConfiguration.userSyncConfiguration` | Per-user forge sync ([Webhooks and sync](../setup/webhooks-and-sync.md#user-sync)) |
| `serviceCoreConfiguration.repositorySync` | Import of all open PRs/MRs ([Webhooks and sync](../setup/webhooks-and-sync.md#repository-sync)) |
| `serviceCoreConfiguration.gitlabSyncConfiguration` | GitLab webhook secret and history window |
| `serviceCoreConfiguration.gitlabConfiguration` | GitLab URL, OAuth application, service token ([GitLab](../setup/gitlab.md)) |
| `serviceCoreConfiguration.githubConfiguration` | GitHub App ([GitHub App](../setup/github-app.md)) |
| `serviceCoreConfiguration.runner` | Runner mode and per-mode settings ([Runners](../setup/runners.md)) |
| `serviceCoreConfiguration.slackClientConfig` | Slack notifications (disabled by default) |
| `aerospike` | Aerospike client: hosts, port, retries, timeouts |

## Selected settings

### `gitlabConfiguration`

| Key | Default | Meaning |
|---|---|---|
| `baseUrl` | `${GITLAB_URL}` | GitLab base URL |
| `clientId`, `clientSecret` | | OAuth application |
| `callbackUrl` | `${ARNOLD_UI_URL}/gitlab/oauth/callback` | Must match the application's redirect URI |
| `oauthStateTtlMs` | `600000` | Lifetime of a pending OAuth state |
| `tokenRefreshBufferMs` | `60000` | Refresh user tokens this long before expiry |
| `requestTimeoutMs` | `30000` | GitLab API timeout |
| `serviceToken` | `${GITLAB_SERVICE_TOKEN}` | Optional read token |

### `githubConfiguration`

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | |
| `webUrl`, `apiUrl` | github.com | Change both for GitHub Enterprise Server |
| `appId`, `appSlug`, `clientId`, `clientSecret`, `privateKey` | | GitHub App credentials |
| `callbackUrl` | `${ARNOLD_UI_URL}/github/oauth/callback` | Must match the App's callback URL |
| `webhookSecret` | | Webhook HMAC secret |

### `runner`

| Key | Default | Meaning |
|---|---|---|
| `mode` | `LOCAL` | `LOCAL`, `GITLAB` or `GITHUB` |
| `local.leaseSeconds` | `300` | A claimed job is requeued if its worker stops heartbeating this long |
| `local.maxAttempts` | `2` | Claims per job before it is marked failed |
| `gitlab.projectId`, `projectPath`, `ref`, `triggerToken` | | Runner project and trigger |
| `github.apiUrl`, `repository`, `workflow`, `ref`, `token` | | Runner repository and dispatch token |

### `copilotConfiguration`

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Show and allow the Copilot harness |
| `jobTokenExchangeEnabled` | `false` | Let runners exchange execution keys for users' tokens. Follows `COPILOT_ENABLED` |
| `clientId` | | GitHub OAuth App (device flow) |
| `credentialSet` | `arnold-copilot-v1` | Credential namespace; changing it disconnects everyone |
| `encryptionKey` | | Base64 32-byte AES key for stored tokens |

## Full default file

??? example "arnold-backend/arnold-server/config/local.yml"

    ```yaml
    --8<-- "arnold-backend/arnold-server/config/local.yml"
    ```
