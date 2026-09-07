# Arnold UI

Frontend for **Arnold AI** — an AI-driven code review platform for GitLab on-prem.

## Quick Start

```bash
cd arnold-ui # from the repository root
npm ci
npm run dev
```

Open [http://internal.example/](http://internal.example/). The development host and port are defined in `config/development.yaml`.

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Start Turbopack dev server |
| `npm run build` | Production build |
| `npm run start` | Serve production build |
| `npm run lint` | Run ESLint |
| `npm run test:deployment` | Check mounted and Rosey runtime configuration loading |

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

Olympus SSO via Express middleware in `server.js`. See [OlympusIntegration.md](OlympusIntegration.md).

## Configuration

The browser calls same-origin `/api` routes. Express proxies them to `ARNOLD_API_URL`, or the `api` settings in the YAML configuration. The backend URL is a runtime setting; changing it does not require rebuilding browser assets.

## Docker deployment

Build from the repository root:

```bash
docker build -t arnold-ui:local arnold-ui
```

The build uses PhonePe's Node 22 image and Artifactory npm registries. It runs `npm ci`, lint, deployment checks, and `next build`, then copies the build and production dependencies into the runtime image. Internal registry access is required. CI supplies the build proxy; local builders can pass `--build-arg https_proxy=...`, `--build-arg http_proxy=...`, and `--build-arg no_proxy=...` as needed. The Google fonts used by Next.js must also be reachable during the build. Build proxy settings are not copied into the runtime image.

The image runs the custom `server.js` with `NODE_ENV=production`, preserving Olympus login, GitLab and Arnold API proxies, and Next.js serving. It listens on the port in runtime configuration (use `3000`). Route ingress traffic to that port and configure an HTTP health probe for `/healthcheck`; the probe checks the UI process, not backend availability.

### Runtime configuration

Start with [`config/production.example.yaml`](config/production.example.yaml), replace the example hosts and credentials, and store the completed configuration in Rosey or mount it as `/app/config/production.yaml`. Configuration, local `.env` files, and credentials are excluded from the Docker build context.

For Rosey, configure these deployment settings:

| Setting | Purpose |
|---------|---------|
| `CONFIG_SERVICE_HOST` | Rosey config service base URL |
| `ROSEY_CONFIG_PATH` | Full path after `/configs/`, including the data center and `/raw` suffix; use Arnold UI's registered path |
| `/rosey/config.yml` mount | YAML containing the `devx` bearer key; readable by the container |
| `ROSEY_KEY_FILE` | Optional alternate path to the key file |
| `ARNOLD_API_URL` | Optional backend URL override, including `https://` or `http://` |
| `NODE_EXTRA_CA_CERTS` | Path to a mounted PEM CA bundle when internal services require additional trusted CAs |

The startup script fetches the configuration, validates YAML and `application.devMode: false`, and writes `/app/config/production.yaml` with owner-only permissions before starting the server. A failed fetch or invalid configuration stops startup. No credentials are passed as Docker build arguments. Keep `NODE_ENV=production`; leave `NODE_CONFIG_ENV` unset or set it to `production`. Stage versus production is selected by the Rosey path and configuration, not by changing Node's runtime mode.

To run with a prepared local configuration instead of Rosey:

```bash
docker run --rm -p 3000:3000 \
  --mount type=bind,src=/absolute/path/production.yaml,dst=/app/config/production.yaml,readonly \
  arnold-ui:local
```

Production verifies upstream TLS certificates. Configure Olympus redirect URLs and secure cookie settings for the deployed UI hostname, and provide the internal CA bundle when needed.

### GitLab CI and rollout

The root `.gitlab-ci.yml` adds two jobs on the `backend-docker-large` runner, following [Spotlight UI](https://gitlab.example.com/devx/spotlight-ui):

- `ui_docker_stage`: automatically builds and publishes for merge requests changing the UI or CI configuration, and for manually created web pipelines.
- `ui_docker_prod`: manually triggered in web pipelines.

Images are named `registry.example.com/image<CI_PIPELINE_IID>` and `registry.example.com/image<CI_PIPELINE_IID>`. Both jobs build the selected commit and run independently of the backend's manual jobs. The runner must have Docker registry push access and network access to Artifactory and the build proxy. `UI_BUILD_PROXY` and `UI_BUILD_NO_PROXY` can be overridden in CI settings.

These jobs publish images; they do not roll out a service. In the deployment platform, select the image tag, supply the Rosey settings or mounted configuration, configure ingress and health probes, and deploy the UI service separately from the Java backend. Roll back by selecting the previous image tag. The Rosey path and deployment service must be provisioned for Arnold UI before the first rollout.
