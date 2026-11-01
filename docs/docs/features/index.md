# Features

<div class="grid cards" markdown>

-   **[Home and merge queue](merge-queue.md)**

    Your review work at a glance, a prioritised inbox, and every open PR/MR across synced repositories.

-   **[Review workspace](review-workspace.md)**

    Diffs, threads, drafts, suggestions, approvals, merge checks and AI findings in one place.

-   **[Story mode](story-mode.md)**

    An AI review brief, guided review steps and reviewer focus areas for a change.

-   **[Run analysis](run-analysis.md)**

    Start an AI review of a change or a group, with context, personas and a chosen model.

-   **[Review personas](personas.md)**

    Versioned review policies that run as specialist reviewers.

-   **[Review groups](review-groups.md)**

    Review related changes across repositories together.

-   **[Analysis history](analysis-history.md)**

    Every run, its status, findings and retries.

-   **[Admin and insights](admin.md)**

    Sync controls, review metrics, token usage and organisation insights.

-   **[Settings](settings.md)**

    Connected accounts, Copilot, theme.

</div>

## Navigation

| Sidebar | Page |
|---|---|
| Home | `/` |
| Merge Requests | `/queue` (badge shows your queue size) |
| Personas | `/personas` |
| Organisation Insights | `/stats` |
| Governance → Analysis History | `/history` |
| Governance → Admin | `/admin` (superadmins only) |
| **Run Analysis** button | `/analyze` |
| Account menu → Settings, Sign out | `/settings`, `/auth/logout` |

The top bar holds notifications (with **Mark all read** and **Sync now**) and the light/dark theme toggle. The footer
shows sync status per forge.

## Setup guide

On first use, a **Set up Arnold** dialog lists what is left to do for the current user:

1. **Connect source control**: required while a configured forge is not connected. Shows *Reconnect* when a token
   stops working. If no forge is configured, it is shown as an administrator task.
2. **Install Arnold on repositories** (GitHub): links to the app's install page; done once repository sync finds a
   repository.
3. **Connect GitHub Copilot**: only when Copilot is enabled; required only if no other harness is available.
4. **Review engine**: an administrator task when no harness is available.

**Finish later** hides it until the next visit. It never covers **Settings** or **Admin**.
