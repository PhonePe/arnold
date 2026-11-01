# Analysis history

**Governance → Analysis History** lists every analysis run.

## Finding runs

- Search by run id, project, title, group or user.
- Filter by status and by who triggered the run.

| Status | Meaning |
|---|---|
| Running | Dispatched or in progress |
| Completed | All reviews finished and findings were published |
| Partial | Some personas or members failed; the rest were published |
| Failed | The run failed; the error is shown on the run |
| Stale | The change got new commits after the run was pinned |

## Run details

Selecting a run shows its type (single MR or MR group), personas and versions, harness and model, a link to the
runner pipeline (in `GITLAB`/`GITHUB` mode), the review state and, for failures, the error message.

| Action | Does |
|---|---|
| Open review / View live review | Opens the [review workspace](review-workspace.md) on this run |
| Run analysis again | Opens Run Analysis prefilled with this run's settings |
| Retry analysis | Groups: resumes with the original context and saved member reviews |
| Retry publication | Groups: re-posts saved findings without another AI review |

## What a run records

- the context (RFC, Jira, notes) and persona snapshots;
- the head commit it reviewed;
- each posted finding: comment id, file, line, rendered body, publication status, comment URL, persona;
- token usage per model (shown in **Admin → Token usage**).

When a run fails, check the error here first, then the runner logs. See [Debugging](../operations/debugging.md).
