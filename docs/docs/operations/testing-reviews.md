# Testing reviews

| Level | Purpose | How |
|---|---|---|
| Unit and contract | Workflow logic with mocked forge and LLM | `cd arnold-runner && pytest`; backend tests with `mvn -f arnold-backend/pom.xml verify` |
| Dry run | See what a model would comment, without posting | `docker compose exec runner python /app/main.py improve --mr-link <url> --dry-run` |
| Sandbox end to end | The full flow through the UI | A throwaway repository you own, app installed, PRs seeded with known issues |
| Golden set | Review quality across models and personas | 10–20 fixed PRs with expected findings; compare precision and recall per run |
| Cheap plumbing | No API cost | Ollama with a small coding model, e.g. `qwen2.5-coder` |

## First end-to-end run (GitHub)

1. Fork a small public repository and install your GitHub App on the fork.
2. Open a PR that introduces obvious issues: an unchecked null, SQL built by string concatenation, a missing test.
3. **Run Analysis** → paste the PR URL → pick an OpenCode model → start.
4. Watch `docker logs -f arnold_runner_1`. Inline comments appear on the PR, authored by the app.
5. Push a fix for one finding. The AI assessment marks it *Applied*; the others stay *Ignored*.

## Comparing models and personas

Use **Run analysis again** with a different model or persona set on the same change, then compare in the review
workspace's **Persona** tab, **Admin → Review metrics** (acceptance by model and persona) and **Admin → Token usage**
(cost by model).
