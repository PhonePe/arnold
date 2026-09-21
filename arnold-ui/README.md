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

The browser calls same-origin `/api` routes. Express proxies them to `ARNOLD_API_URL`, or the `api` settings in the YAML configuration. The backend URL is a runtime setting; changing it does not require rebuilding browser assets. GitLab operations use each user’s OAuth credentials through the backend; the UI does not need a shared GitLab API token.

## Docker deployment

Build from the repository root:

```bash
docker build -t arnold-ui:local arnold-ui
```

The build uses PhonePe's Node 22 image and Artifactory npm registries. It runs `npm ci`, lint, deployment checks, and `next build`, then copies the build and production dependencies into the runtime image. Internal registry access is required. CI supplies the build proxy; local builders can pass `--build-arg https_proxy=...`, `--build-arg http_proxy=...`, and `--build-arg no_proxy=...` as needed. Space Grotesk and IBM Plex Mono are bundled in `src/app/fonts` with their licenses; the build does not need access to Google Fonts. Build proxy settings are not copied into the runtime image.

The image runs the custom `server.js` with `NODE_ENV=production`, preserving Olympus login, the Arnold API proxy, and Next.js serving. It listens on the port in runtime configuration (use `3000`). Route ingress traffic to that port and configure an HTTP health probe for `/healthcheck`; the probe checks the UI process, not backend availability.

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
The jobs run on `backend-docker-large`, following Spotlight's separate Maven and Docker jobs:

| Job | Runner image | Action |
|-----|--------------|--------|
| `backend_docker_stage` | Java 17 / Maven 3.9.9 | Test, build and push the backend snapshot through the Maven Docker plugin |
| `ui_docker_stage` | `registry.example.com/image` | After `backend_docker_stage`, build and push the UI snapshot using `ci-docker` |
| `release_server` | Java 17 / Maven 3.9.9 | Release the backend through JGitFlow and the Maven Docker plugin |
| `release_ui` | `registry.example.com/image` | After `release_server`, build and push the UI with the backend release version |

The Maven jobs use `registry.example.com/image`.
No job installs Java or downloads Maven. Python, already available in both runner images, reads the POM and checks release metadata.

Snapshot builds run automatically for merge requests changing the backend, UI, or CI configuration. In web pipelines,
start `backend_docker_stage` manually; the UI job follows automatically. Both use the same pipeline commit and stable snapshot
version, such as `1.10-SNAPSHOT`.

Production starts with the manual `release_server` job, available only on `develop`. It rejects stale pipelines if `develop`
has moved and rejects existing release tags. JGitFlow releases the backend (for example `1.10`), advances the development
POMs (for example `1.11-SNAPSHOT`), and the job pushes `master`, `develop`, and the shared release tag atomically.
The backend job records its image version and exact source commit in `target/image-version.env`, passed to the UI job
as a dotenv artifact. `release_ui` verifies and checks out that release commit; it does not build the latest `master` or
read the newly advanced development version. There is no separate UI release tag.

| Environment | Backend | UI |
|-------------|---------|----|
| Stage | `registry.example.com/image` | `stg-registry.example.com/image` |
| Production | `registry.example.com/image` | `registry.example.com/image` |

The backend retains the image name configured in its Maven Docker plugin. For UI images, `ci-docker` adds the project
namespace and routes stage and production to their registries; production pushes internally to `prd-registry.example.com/image`.
UI images also receive a `build-<CI_JOB_ID>` tag and OCI version/source-commit labels. The UI job records its versioned and
unique image references in `target/ui-image.env`. Deployment repositories must match the paths above.

The backend is already published when the UI job starts. If the UI job fails, retry that job to build the same pinned
backend release; do not start another backend release. Treat the pair as ready for rollout only after both jobs succeed.
Keep production release tags immutable in the registry. Updating a snapshot tag alone does not restart a running service.

The runner needs registry access and network access to Artifactory and the build proxy. `UI_BUILD_PROXY` and
`UI_BUILD_NO_PROXY` can be overridden in CI settings. Run `python3 arnold-ui/scripts/ci-images.test.py` from the repository
root to check the version handoff, pinned release checkout, branch restrictions, and failure handling without publishing anything.

These jobs publish images; they do not roll out services. Select matching image versions in the deployment platform,
supply the UI's Rosey settings or mounted configuration, and configure ingress and health probes before deploying.

## MR group analysis

Create a group in the merge queue, choose **Analyse group**, and select the usual review context/personas. History
shows the saved member revisions, review progress, combined findings, publication links, and publication-only retry.
Story generation is not part of this workflow.

Manual verification: select a backend/frontend group, attach context, and start analysis. Confirm both members appear in
History and each finding links to its destination MR. Check partial publication exposes retry, stale results expose a
fresh analysis action, and switching back to Single MR preserves the existing workflow. Use a disposable GitLab group
for live verification: successful runs automatically post comments.
