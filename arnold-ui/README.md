# Arnold UI

Frontend for **Arnold AI** — an AI-driven code review platform.

## Quick Start

```bash
cd arnold-ui # from the repository root
npm ci
npm run dev
```

Open [http://localhost:3000/](http://localhost:3000/). The development host and port are defined in `config/development.yaml`. Development runs with `auth.mode: dev` (no sign-in), which requires the backend to run with `auth.dev.enabled: true`.

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Start Turbopack dev server |
| `npm run build` | Production build |
| `npm run start` | Serve production build |
| `npm run lint` | Run ESLint |
| `npm run test:deployment` | Check runtime configuration loading and authentication |

## Tech Stack

- **Next.js 16** (App Router)
- **React 19** (JavaScript/JSX — no TypeScript)
- **Tailwind CSS v4**
- **Lucide React** for icons
- **Space Grotesk** / **IBM Plex Mono** / system body fonts

## Project Structure

```
src/
├── app/              # App Router pages and layouts
├── components/
│   ├── ui/           # Primitives (Button, Badge, Card, Input, Avatar, etc.)
│   ├── layout/       # Shells (Sidebar, TopNav, AppShell, StatusBar)
│   └── composite/    # Domain components (MRListItem, RuleCard, DiffViewer)
└── lib/              # Shared helpers and API utilities
```

## Design System

See [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md) for colors, typography, component patterns, and the tonal layering philosophy. The upstream creative spec lives in [`DESIGN.md`](DESIGN.md).

## Authentication

`auth.js` implements sign-in in the Express server. Supported provider types:

| Type | Use for |
|------|---------|
| `github` | GitHub.com or GitHub Enterprise OAuth apps; optional `allowedOrganizations` |
| `oidc` | Any OpenID Connect provider: Google, GitLab, Keycloak, Okta, Auth0, … |

After sign-in the server issues a session JWT (issuer `arnold-ui`, audience `arnold`) in an http-only cookie and
forwards it to the backend as a bearer token on `/api` calls. Configure the backend to trust that issuer with the same
secret. Optional `allowedEmailDomains` restricts who may sign in; backend roles come from the backend `auth` settings.

## Configuration

The browser calls same-origin `/api` routes. Express proxies them to `ARNOLD_API_URL`, or the `api` settings in the YAML configuration. The backend URL is a runtime setting; changing it does not require rebuilding browser assets. GitLab operations use each user’s OAuth credentials through the backend; the UI does not need a shared GitLab API token.

## Docker deployment

Build from the repository root:

```bash
docker build -t arnold-ui:local arnold-ui
```

The build uses the public `node:22` image. It runs `npm ci`, lint, deployment checks, and `next build`, then copies the build and production dependencies into the runtime image. Space Grotesk and IBM Plex Mono are bundled in `src/app/fonts` with their licenses; the build does not need access to Google Fonts.

The image runs the custom `server.js` with `NODE_ENV=production`, handling sign-in, the Arnold API proxy, and Next.js serving. It listens on the port in runtime configuration (use `3000`). Route ingress traffic to that port and configure an HTTP health probe for `/healthcheck`; the probe checks the UI process, not backend availability.

### Runtime configuration

Start with [`config/production.example.yaml`](config/production.example.yaml), replace the example hosts and credentials, and mount it as `/app/config/production.yaml`. Configuration, local `.env` files, and credentials are excluded from the Docker build context.

| Setting | Purpose |
|---------|---------|
| `ARNOLD_API_URL` | Optional backend URL override, including `https://` or `http://` |
| `ARNOLD_UI_JWT_SECRET` | Overrides `auth.sessionSecret` |
| `ARNOLD_UI_URL` | Overrides `auth.publicUrl` |
| `NODE_EXTRA_CA_CERTS` | Path to a mounted PEM CA bundle when upstream services use a private CA |

The startup script validates the YAML, `application.devMode: false` and that `auth.mode` is not `dev` before starting the server. Keep `NODE_ENV=production`; leave `NODE_CONFIG_ENV` unset or set it to `production`.

```bash
docker run --rm -p 3000:3000 \
  --mount type=bind,src=/absolute/path/production.yaml,dst=/app/config/production.yaml,readonly \
  arnold-ui:local
```

Production verifies upstream TLS certificates. Register `{auth.publicUrl}/auth/callback/{providerId}` as the redirect URL with each sign-in provider, and set the same `sessionSecret` on the backend's `arnold-ui` JWT issuer.
