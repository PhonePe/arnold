# GitLab

Arnold works with gitlab.com and self-managed GitLab. Each user connects their own GitLab account through an
**OAuth application**, so actions in Arnold are performed as that user. Runners use a separate access token to clone
code and post review comments.

## 1. Create an OAuth application

In GitLab, open **User settings → Applications** (or **Group → Settings → Applications**, or **Admin → Applications**
for an instance-wide app) and add a new application:

| Field | Value |
|---|---|
| Name | `Arnold` |
| Redirect URI | `<ARNOLD_UI_URL>/gitlab/oauth/callback`, e.g. `http://localhost:3000/gitlab/oauth/callback` |
| Confidential | Checked |
| Scopes | `api` |

Copy the **Application ID** and **Secret**.

## 2. Create tokens

| Token | Scope | Used by | Purpose |
|---|---|---|---|
| `GITLAB_ACCESS_TOKEN` | `api` | Runner | Clone repositories and post review comments. Use a bot/service account with *Developer* access to reviewed projects; comments are authored by it |
| `GITLAB_SERVICE_TOKEN` | `read_api` | Backend | Optional. Imports every open MR in projects it can see ([repository sync](webhooks-and-sync.md)) and enriches review feedback |

A project, group or personal access token works for both.

## 3. Configure Arnold

```bash title=".env"
GITLAB_URL=https://gitlab.com            # or https://gitlab.example.com
GITLAB_OAUTH_CLIENT_ID=...
GITLAB_OAUTH_CLIENT_SECRET=...
GITLAB_ACCESS_TOKEN=glpat-...
GITLAB_SERVICE_TOKEN=glpat-...           # optional
ARNOLD_GITLAB_SYNC_GROUPS=platform,42    # optional: limit sync to these groups (ids or paths, incl. subgroups)
```

```bash
docker compose up -d --force-recreate server runner
```

Then **Settings → Connected accounts → GitLab → Connect**.

!!! note
    When GitLab is configured and GitHub is not, Arnold asks every user to connect GitLab before using the app.
    If a user's token is revoked or expires and cannot be refreshed, Arnold asks them to reconnect.

## 4. Webhooks (optional)

Each connected user's merge requests are synced every 15 minutes, and open MRs are imported by repository sync.
Webhooks make updates immediate.

In each project or group, **Settings → Webhooks → Add new webhook**:

| Field | Value |
|---|---|
| URL | `https://<backend-host>/gitlab-callbacks/v1/events` |
| Secret token | Same as `GITLAB_WEBHOOK_SECRET` |
| Triggers | Merge request events, Comments, Pipeline events |

Arnold checks the `X-Gitlab-Token` header against `GITLAB_WEBHOOK_SECRET` (default `local-webhook-secret`; change it)
and deduplicates deliveries by `X-Gitlab-Event-UUID`. Admins can choose how webhooks and scheduled sync interact
under **Admin → Sync controls**; see [Webhooks and sync](webhooks-and-sync.md).

## Running reviews on GitLab CI

Instead of a local worker, reviews can run as pipelines in a GitLab project. See
[Runners → GitLab CI](runners.md#gitlab-ci).

## Troubleshooting

| Symptom | Fix |
|---|---|
| GitLab shows *The redirect URI included is not valid* | The application's redirect URI must equal `<ARNOLD_UI_URL>/gitlab/oauth/callback` |
| *Connect GitLab to continue* blocks every page | Connect GitLab, or configure GitHub too |
| Runner fails with *SCM token required* on a GitLab MR | `GITLAB_ACCESS_TOKEN` is not set for the runner |
| **All open** is empty | `GITLAB_SERVICE_TOKEN` unset, or not a member of any project; check `ARNOLD_GITLAB_SYNC_GROUPS` |
