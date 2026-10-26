# Testing the review flow

| Level | Purpose | How |
|-------|---------|-----|
| Unit / contract | Workflow logic with mocked SCM and LLM | `cd arnold-runner && pytest`; backend WireMock tests via `mvn verify` |
| Dry run | See what a model would comment, without posting | `docker compose exec runner python /app/main.py improve --mr-link <PR/MR url> --dry-run` |
| Sandbox end to end | Full flow through the UI | Throwaway repo you own, app installed, PRs seeded with known issues |
| Golden set | Review quality regression across models/personas | 10–20 fixed PRs with expected findings; compare precision/recall per run |
| Cheap plumbing | No API cost | Ollama with a small coder model (e.g. `qwen2.5-coder`) |

## First end-to-end run (GitHub)

1. Fork a small public repository and install your GitHub App on the fork.
2. Open a PR introducing an obvious issue (unchecked null, SQL string concatenation, missing test).
3. **Run Analysis** → paste the PR URL → pick an OpenCode model → start Improve.
4. Watch `docker logs -f arnold_runner_1`; inline comments appear on the PR, authored by the app.
5. Accept/reject comments; Analysis History records the outcome for later quality analysis.
