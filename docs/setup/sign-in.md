# Sign-in providers

The quick start runs without sign-in (development mode). To require login:

## 1. Create OAuth clients

| Provider | Where | Redirect / callback URL |
|----------|-------|-------------------------|
| GitHub | Settings → Developer settings → **OAuth Apps** (separate from the GitHub App) | `http://localhost:3000/auth/callback/github` |
| Google | Cloud Console → APIs & Services → Credentials → OAuth client ID (Web) | `http://localhost:3000/auth/callback/google` |
| Any OIDC (Keycloak, Okta, Auth0, GitLab) | your IdP | `http://localhost:3000/auth/callback/<id>` |

## 2. UI configuration (`deploy/ui/development.yaml`)

```yaml
application: { devMode: false }
server: { host: 0.0.0.0, port: 3000 }
api: { host: http://server:8090, domain: server, port: 8090 }
auth:
  mode: oauth
  publicUrl: http://localhost:3000
  sessionSecret: <openssl rand -hex 32>
  allowedEmailDomains: []            # e.g. [example.com]
  providers:
    - { id: github, type: github, label: GitHub, clientId: ..., clientSecret: ..., allowedOrganizations: [] }
    - { id: google, type: oidc, label: Google, issuer: https://accounts.google.com, clientId: ..., clientSecret: ... }
```

## 3. Backend

- `.env`: `ARNOLD_UI_JWT_SECRET=<same value as sessionSecret>`.
- `docker-compose.yml`: set `ARNOLD_DEV_AUTH: "false"` (server) and remove `ARNOLD_ALLOW_DEV_AUTH` (ui).
- Admins: add emails to `serviceCoreConfiguration.auth.admins` in `local.yml`, or map IdP groups with
  `auth.roleMappings`.

Restart: `docker compose up -d --force-recreate server ui`. `/auth/login` shows a provider chooser.
