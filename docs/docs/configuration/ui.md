# UI configuration

`arnold-ui` reads YAML through [node-config](https://github.com/node-config/node-config) from `NODE_CONFIG_DIR`
(`/app/config` in the image). The container loads `production.yaml` and validates it before starting.

| File | Use |
|---|---|
| `arnold-ui/config/development.yaml` | `pnpm dev` |
| `arnold-ui/config/production.example.yaml` | Template for deployments |
| `deploy/ui/development.yaml` | Docker Compose quick start (mounted as `production.yaml`) |
| `arnold-ui/config/custom-environment-variables.yaml` | Environment overrides (below) |

## Keys

| Key | Default | Meaning |
|---|---|---|
| `application.devMode` | | Must be `false` in the container |
| `server.host`, `server.port` | `0.0.0.0`, `3000` | Listen address |
| `api.host` | | Backend base URL, e.g. `http://server:8090`. `ARNOLD_API_URL` overrides it |
| `api.domain`, `api.port` | | Backend host and port |
| `auth.*` | | Sign-in; see [Sign-in](../setup/sign-in.md#2-ui-configuration) |

## Environment overrides

| Variable | Overrides |
|---|---|
| `ARNOLD_API_URL` | `api.host` |
| `ARNOLD_AUTH_MODE` | `auth.mode` |
| `ARNOLD_UI_URL` | `auth.publicUrl` |
| `ARNOLD_UI_JWT_SECRET` | `auth.sessionSecret` |
| `ARNOLD_ALLOW_DEV_AUTH` | Permits `auth.mode: dev` in the container |

## Endpoints served by the UI server

| Path | Purpose |
|---|---|
| `/healthcheck` | Liveness |
| `/auth/login`, `/auth/logout`, `/auth/callback/<id>` | Sign-in |
| `/api/*` | Proxied to the backend with the session as bearer token; returns 401 without a session |
| everything else | The Next.js app (redirects to `/auth/login` without a session) |

## Example

```yaml title="config/production.yaml"
--8<-- "arnold-ui/config/production.example.yaml"
```
