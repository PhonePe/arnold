# Building from source

| Module | Requirements | Build and test |
|---|---|---|
| `arnold-backend` | JDK 17, Maven 3.9 | `mvn -f arnold-backend/pom.xml verify` |
| `arnold-ui` | Node.js 22 | `cd arnold-ui && npm ci && npm run lint && npm run test:deployment && npm run build` |
| `arnold-runner` | Python 3.12, Node.js (for OpenCode) | `cd arnold-runner && pip install -r requirements.txt pytest && pytest` |
| `docs` | Python 3.12 | `./docs/serve.sh` (see [Writing docs](docs.md)) |

## Repository layout

```text
arnold/
├── arnold-backend/
│   ├── arnold-server/     # Dropwizard app, config/local.yml, Dockerfile, run.sh
│   ├── arnold-core/       # resources, services, providers, auth, runner dispatch, storage
│   └── arnold-models/     # request/response/document models
├── arnold-ui/             # Next.js app + Express server (server.js, auth.js), config/
├── arnold-runner/         # Python workflows (main.py, flows/, runner/), ci/ templates
├── deploy/                # compose support files: .env.example, aerospike/, ui/
├── docs/                  # this site (Zensical)
└── docker-compose.yml
```

## Run the stack from source

`docker-compose.dev.yml` builds the images from your checkout instead of pulling releases:

```bash
mvn -f arnold-backend/pom.xml -DskipTests package
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build            # everything
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build ui         # after UI changes
```

## Running modules locally

=== "Backend"

    ```bash
    docker compose up -d aerospike
    mvn -f arnold-backend/pom.xml -DskipTests package
    cd arnold-backend/arnold-server && sh run.sh        # sh run.sh d → debug on :5005
    ```

=== "UI"

    ```bash
    cd arnold-ui && npm ci
    ARNOLD_API_URL=http://localhost:8090 npm run dev
    ```

=== "Runner"

    ```bash
    cd arnold-runner
    python -m venv .venv && . .venv/bin/activate
    pip install -r requirements.txt && npm i -g opencode-ai
    ARNOLD_URL=http://localhost:8090 ARNOLD_RUNNER_API_KEY=local-runner-key-change-me \
      CLIO_HARNESS=opencode CLIO_LLM_API_KEY=sk-... python main.py worker
    ```

## Images

`docker compose build` builds all three images. Tagged releases (`v*`) build and push
`ghcr.io/phonepe/arnold-{server,ui,runner}` through `.github/workflows/release.yml`.

## Contributing

See [CONTRIBUTING.md](https://github.com/PhonePe/arnold/blob/main/CONTRIBUTING.md): sign off commits (DCO), use
`type(area|component): summary` commit messages, keep builds green, and never commit secrets (`gitleaks` runs in
pre-commit and CI). Backend conventions are in
[BACKEND.md](https://github.com/PhonePe/arnold/blob/main/arnold-backend/BACKEND.md).
