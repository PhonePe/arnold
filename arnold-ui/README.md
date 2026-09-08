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

The build uses PhonePe's Node 22 image and Artifactory npm registries. It runs `npm ci`, lint, deployment checks, and `next build`, then copies the build and production dependencies into the runtime image. Internal registry access is required. CI supplies the build proxy; local builders can pass `--build-arg https_proxy=...`, `--build-arg http_proxy=...`, and `--build-arg no_proxy=...` as needed. Space Grotesk and IBM Plex Mono are bundled in `src/app/fonts` with their licenses; the build does not need access to Google Fonts. Build proxy settings are not copied into the runtime image.

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

UI and backend images share the version in `arnold-backend/pom.xml`. `package.json` does not control image tags.
Both image jobs use `registry.example.com/image` on the `backend-docker-large` runner for `ci-docker`.
Their setup installs Java 17 and Maven 3.9.9 so the shared backend/UI build and release run in the same job.
The runner needs access to Ubuntu package repositories and the Apache Maven archive through the build proxy.

- `docker_build`: builds and tests both modules from the pipeline commit, then publishes both snapshot images. It runs automatically for merge requests changing the backend, UI, or CI configuration, and manually in web pipelines. The shared `1.10-SNAPSHOT` tag stays the same across builds until the Maven version advances.
- `release`: a manual job available only in `develop` branch pipelines. It rejects stale pipelines if `develop` has moved. JGitFlow starts the release, both images are built and validated with the release version (for example `1.10`), and JGitFlow finishes the Maven release and advances the development POMs (for example to `1.11-SNAPSHOT`). The job verifies that the release tag has the same backend and UI source trees as the image build, publishes both images, then atomically pushes `master`, `develop`, and the shared Git release tag. There is no separate UI production release or `ui-v*` tag.

`ci-docker` chooses the registry and adds the GitLab project namespace. For `devx/arnold`, the image references are:

| Environment | Backend | UI |
|-------------|---------|----|
| Stage | `stg-registry.example.com/image` | `stg-registry.example.com/image` |
| Production | `registry.example.com/image` | `registry.example.com/image` |

Production references use the public registry address; `ci-docker` pushes internally to `prd-registry.example.com/image`.
These paths replace the earlier unnamespaced image paths. Update deployment image repositories accordingly.
Each image also receives a `build-<CI_JOB_ID>` tag and OCI version/source-commit labels. The job's `target/ci-images.env`
artifact records the shared version, source commit, and both versioned and unique build references.

Both images must build successfully before any image is pushed. Image jobs are serialized to prevent concurrent tag updates.
The two registry pushes are not atomic: if a push fails, the job fails and does not push the Git release refs. Do not deploy
an incomplete release; keep production release tags immutable in the registry. A failed publication may require registry
recovery before retrying. Deploy matching unique build tags or digests together for exact rollbacks. Updating a snapshot tag
alone does not restart a running service.

The runner must have registry push access and network access to Artifactory and the build proxy. `UI_BUILD_PROXY` and
`UI_BUILD_NO_PROXY` can be overridden in CI settings. Run `python3 arnold-ui/scripts/ci-images.test.py` from the repository
root to check shared versioning, branch restrictions, and build/publication failure handling without publishing anything.

These jobs publish images; they do not roll out services. In the deployment platform, select the matching image versions,
supply the UI's Rosey settings or mounted configuration, configure ingress and health probes, and deploy the services.
The Rosey path and deployment service must be provisioned for Arnold UI before the first rollout.
