# Arnold

AI-assisted code review platform for GitLab and GitHub.

## Layout

| Path                           | Contents                                                                                |
|--------------------------------|-----------------------------------------------------------------------------------------|
| `arnold-backend/arnold-server` | Dropwizard application; only wires `ArnoldBundle`.                                      |
| `arnold-backend/arnold-core`   | Resources, services, forge clients, auth, runner dispatch, Aerospike and ES storage.    |
| `arnold-backend/arnold-models` | Request, response and document models.                                                  |
| `arnold-ui`                    | Next.js frontend plus the Express server for sign-in and the `/api` proxy.              |
| `arnold-runner`                | Python (PocketFlow) review workflows, run locally, on GitLab CI or on GitHub Actions.   |
| `deploy`                       | Docker Compose quick start.                                                             |
| `docs`                         | Documentation site (Zensical).                                                          |

Flow: the UI proxies to the backend; the backend dispatches review jobs to the runner; the runner posts comments to the
forge and reports back through callbacks.

## Module guides

Read the guide for the module you are changing before editing it:

- Backend: [arnold-backend/BACKEND.md](arnold-backend/BACKEND.md)
- UI: [arnold-ui/AGENTS.md](arnold-ui/AGENTS.md)
- Runner: [arnold-runner/README.md](arnold-runner/README.md)
- Product behavior and configuration: [docs/docs](docs/docs/index.md)

## Rules

### Commits

- Commit after each logical change. Never push.
- Format: `type(ui|backend|<group>): <message>`, imperative mood, present tense. Types: `feat`, `fix`, `perf`,
  `refactor`, `test`, `docs`, `ci`, `chore`, `res` (resources).
  - `feat(backend|workflow): prefill task owner from project context`
  - `fix(ui|login): handle special characters in usernames`
- No `Co-authored-by:` trailers or AI/tool attribution. Enforced by `scripts/check_commit_message.sh`.

### Comments

Comment only logic a first-time reader would not follow. Do not narrate your approach or restate obvious code.
