# Concepts

Change request
:   A GitLab merge request or a GitHub pull request. Arnold treats both the same way and identifies them by
    provider, repository id and number (`/review/GITHUB/<repoId>/<number>`). The UI says "merge request" (MR) for both.

Forge
:   The source control host: GitLab (gitlab.com or self-managed) or GitHub (github.com or Enterprise Server).

Connection
:   A user's link between Arnold and a forge (GitLab OAuth or the GitHub App's user authorization). Arnold acts on
    the forge as that user. Forge tokens are stored in Aerospike (Copilot tokens are additionally encrypted with
    `COPILOT_ENCRYPTION_KEY`), so treat Aerospike as a secret store.

Repository sync
:   A periodic job that imports every open change request Arnold can see: repositories where the GitHub App is
    installed, and GitLab projects visible to the service token. See [Webhooks and sync](../setup/webhooks-and-sync.md).

Merge queue
:   Your personal list of change requests: ones you author, review or watch. You can reorder it by priority.

Analysis run
:   One execution of an AI workflow (usually *Improve*) on a change request or group. A run records its context,
    personas, harness, model, status, findings and the comments it posted.

Workflow
:   What a runner does for a job: `improve` (inline findings), `smart_diff` (story mode), `group_review`
    (cross-repository review), plus CLI-only `review`, `describe`, `impact` and `sonarfix`.

Harness
:   The agent program that drives the model inside the runner: **OpenCode** (any OpenAI-compatible endpoint) or the
    opt-in **GitHub Copilot** harness. See [LLM and harnesses](../setup/llm.md).

Runner mode
:   Where jobs execute: `LOCAL` (workers poll Arnold), `GITLAB` (CI pipelines) or `GITHUB` (Actions). See
    [Runners](../setup/runners.md).

Review persona
:   A versioned, publishable review policy (guidance, rules, prerequisites, exclusions) that runs as a specialist
    sub-agent during Improve. Its findings are tagged with the persona. See [Review personas](../features/personas.md).

Review group
:   A named set of related change requests, possibly across repositories, that can be analysed together. See
    [Review groups](../features/review-groups.md).

Story mode (smart diff)
:   An AI-generated review brief and ordered review steps for a change, with reviewer focus areas pinned to lines.

Acceptance status
:   Arnold's assessment of whether the author acted on an AI finding: *applied*, *partially applied*, *ignored* or
    *not applicable*. It feeds review metrics.
