# Contributing to PhonePe Arnold

Thanks for your interest in improving Arnold. This guide covers how to propose changes.

## Ground rules

- Be respectful; this project follows the [Code of Conduct](CODE_OF_CONDUCT.md).
- Report security issues privately as described in [SECURITY.md](SECURITY.md), not in public issues.
- Open an issue to discuss significant changes before investing in a large pull request.

## Development setup

| Module | Requirements | Build and test |
|--------|--------------|----------------|
| `arnold-backend` | JDK 17, Maven 3.9 | `mvn -f arnold-backend/pom.xml verify` |
| `arnold-ui` | Node.js 22 | `cd arnold-ui && npm ci && npm run lint && npm run test:deployment && npm run build` |
| `arnold-runner` | Python 3.12 | `cd arnold-runner && pip install -r requirements.txt pytest && pytest` |

`docker compose up --build` (see the [README](README.md#quick-start)) runs the whole stack locally.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Keep changes focused; one logical change per commit.
3. Use commit messages of the form `type(area|component): summary`, where `type` is one of `feat`, `fix`, `perf`,
   `refactor`, `test`, `docs`, `ci`, `chore` or `build` — for example `fix(ui|login): handle expired sessions`.
4. Add or update tests for behaviour changes, and keep all module builds green.
5. Sign off every commit (`git commit -s`) to certify the [Developer Certificate of Origin](https://developercertificate.org/).
6. Do not commit secrets, credentials or internal hostnames; `gitleaks` runs in pre-commit and CI.

## Code style

- Java: follow the existing conventions described in [arnold-backend/BACKEND.md](arnold-backend/BACKEND.md).
- UI: see [arnold-ui/AGENTS.md](arnold-ui/AGENTS.md); `npm run lint` must pass.
- Comments explain non-obvious logic for future readers; avoid narrating the change.
- New source files carry the Apache 2.0 license header used across the repository.

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
