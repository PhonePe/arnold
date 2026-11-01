# Production deployment

The quick start is for a single machine with development sign-in. This page covers what changes for a shared
deployment.

## Images

Tagged releases publish images to GitHub Container Registry:

| Image | Port(s) | Config |
|---|---|---|
| `ghcr.io/phonepe/arnold-server:<version>` | 8090 API, 8091 admin | `/app/config/arnold.yml` (defaults to `local.yml`), plus env vars |
| `ghcr.io/phonepe/arnold-ui:<version>` | 3000 | `/app/config/production.yaml` (required), plus env vars |
| `ghcr.io/phonepe/arnold-runner:<version>` | none | Env vars only |

Pin a version tag rather than `latest`.

## Topology

```mermaid
flowchart LR
    U[Users] -- HTTPS --> LB[Load balancer / ingress]
    LB --> UI[arnold-ui × N]
    UI --> S[arnold-server × N]
    F[GitLab / GitHub] -- webhooks --> LB2[Ingress: /gitlab-callbacks, /github-callbacks]
    LB2 --> S
    CI[Runners: workers or CI] -- "/runner/v1, callbacks" --> S
    S --> A[(Aerospike cluster)]
    S -.-> E[(Elasticsearch)]
```

- **UI** is stateless; run two or more replicas behind the load balancer.
- **Backend** is stateless; run two or more replicas. Scheduled sync and runner job leases are coordinated through
  Aerospike, so any number of replicas is safe.
- **The backend needs its own route** for forge webhooks (`/github-callbacks/`, `/gitlab-callbacks/`) and, in
  `GITLAB`/`GITHUB` runner modes or with remote workers, for runners. Runners call `/runner/v1`,
  `/analysis-callbacks/v1`, `/group-analysis-callbacks/v1`, `/review-feedback`, the smart-diff callback under
  `/merge-requests/v1`, and `/copilot-integrations/v1/execution-token`, all authenticated with the runner API key.
  Prefer a private network path for runners, and expose only the webhook paths publicly. Never expose the admin
  port (8091).
- **Runners** need outbound access only.

## Checklist

### Authentication

- [ ] UI `auth.mode: oauth` with at least one provider ([Sign-in](sign-in.md)).
- [ ] `ARNOLD_DEV_AUTH=false` on the backend; `ARNOLD_ALLOW_DEV_AUTH` unset on the UI.
- [ ] `ARNOLD_UI_JWT_SECRET` is random (≥ 32 bytes) and identical on UI and backend.
- [ ] `auth.publicUrl` / `ARNOLD_UI_URL` is the public `https://` URL (used for OAuth callbacks and secure cookies).
- [ ] Admins granted via `auth.admins` or `auth.roleMappings`.
- [ ] Restrict sign-in: `allowedEmailDomains`, `allowedOrganizations`, or an IdP that only admits your users.

### Secrets

- [ ] `ARNOLD_RUNNER_API_KEY` is random and not the example value.
- [ ] `GITLAB_WEBHOOK_SECRET` and `GITHUB_APP_WEBHOOK_SECRET` are random.
- [ ] `COPILOT_ENCRYPTION_KEY` (if Copilot is enabled) is backed up; losing it disconnects every user.
- [ ] Secrets come from your secret manager as environment variables, never from committed files.
- [ ] `ELASTIC_PASSWORD` is changed from the default `changeme`.

### Forges

- [ ] GitHub App and GitLab OAuth callback URLs use the public UI URL.
- [ ] Webhooks configured with secrets ([Webhooks and sync](webhooks-and-sync.md)).
- [ ] `GITLAB_ACCESS_TOKEN` belongs to a bot account with the least access needed.

### Data

- [ ] Dedicated Aerospike namespace with persistence, authentication and TLS; `nsup-period` > 0
  ([Storage](storage.md)).
- [ ] Backups of the Aerospike namespace.
- [ ] Elasticsearch (optional) with authentication.

### Runners

- [ ] Runner mode chosen ([Runners](runners.md)); `CLIO_HARNESS=opencode` set on workers.
- [ ] LLM keys scoped to Arnold, with spend limits at the provider.
- [ ] CI debug tracing off in runner projects.

### Operations

- [ ] Liveness/readiness on the backend admin port: `GET :8091/healthcheck`; UI: `GET :3000/healthcheck`.
- [ ] Logs collected from all three components (stdout).
- [ ] Metrics scraped from `GET :8091/metrics` (Dropwizard JSON).

## Backend configuration file

The image copies `local.yml` to `/app/config/arnold.yml`. Every value that differs between environments is an
`${ENV_VAR:-default}` reference, so most deployments only need environment variables. To change structural settings
(Aerospike namespaces, extra issuers, role mappings, static model lists), mount your own file:

```bash
docker run -v $PWD/arnold.yml:/app/config/arnold.yml:ro --env-file arnold.env \
  -p 8090:8090 -p 8091:8091 ghcr.io/phonepe/arnold-server:<version>
```

JVM options go in `JAVA_OPTS` (default `-XX:+UseG1GC -Xms512m -Xmx1g`).

## UI configuration file

The UI container validates `/app/config/production.yaml` before starting, and refuses to start when:

- `application.devMode` is not `false`;
- `auth.mode` is `dev` without `ARNOLD_ALLOW_DEV_AUTH=true`;
- `auth.sessionSecret` is shorter than 32 bytes in `oauth` mode.

Point it at the backend with `ARNOLD_API_URL=http://arnold-server:8090` or the `api.*` keys. See
[UI configuration](../configuration/ui.md).

## Kubernetes sketch

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: arnold-server }
spec:
  replicas: 2
  selector: { matchLabels: { app: arnold-server } }
  template:
    metadata: { labels: { app: arnold-server } }
    spec:
      containers:
        - name: server
          image: ghcr.io/phonepe/arnold-server:<version>
          envFrom: [{ secretRef: { name: arnold-server-env } }]
          ports: [{ containerPort: 8090 }, { containerPort: 8091 }]
          readinessProbe: { httpGet: { path: /healthcheck, port: 8091 } }
          livenessProbe: { httpGet: { path: /healthcheck, port: 8091 }, initialDelaySeconds: 60 }
          resources: { requests: { cpu: "500m", memory: 1Gi }, limits: { memory: 1536Mi } }
```

The UI and runner deployments follow the same pattern: the UI mounts `production.yaml` from a Secret, and runners run
`worker --concurrency N` with their environment from a Secret.
