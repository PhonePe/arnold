# Webhooks and sync

Arnold keeps its own copy of change requests so lists and the merge queue load without per-row forge API calls. Three
mechanisms keep that copy fresh.

| Mechanism | What it imports | When | Configure |
|---|---|---|---|
| **Repository sync** | Every open PR/MR in repositories Arnold can access | Every `ARNOLD_REPOSITORY_SYNC_INTERVAL_SECONDS` (300), first run ~30 s after boot | `ARNOLD_REPOSITORY_SYNC_*`, `ARNOLD_GITLAB_SYNC_GROUPS` |
| **User sync** | Each connected user's authored, reviewing and assigned MRs | Every 15 minutes per user, and on demand (**Sync now**) | `userSyncConfiguration` |
| **Webhooks** | Changes as they happen | On each event | Forge webhook settings |

Running several backend instances is safe: scheduled sync uses a leader lease in Aerospike, so only one instance
syncs at a time.

## Repository sync

Imports from:

- **GitHub**: every repository where the GitHub App is installed;
- **GitLab**: every project `GITLAB_SERVICE_TOKEN` is a member of, or only projects in `ARNOLD_GITLAB_SYNC_GROUPS`
  (comma-separated group ids or full paths; subgroups included).

```bash title=".env"
ARNOLD_REPOSITORY_SYNC_ENABLED=true
ARNOLD_REPOSITORY_SYNC_INTERVAL_SECONDS=300
ARNOLD_GITLAB_SYNC_GROUPS=platform,payments/backend
```

Results appear in **Merge Requests → All open**. Admins can see the last run and trigger one under **Admin → Sync
controls**.

## User sync

Per-user sync uses each user's own forge connection, so it sees exactly what they can see. Settings:

```yaml title="local.yml"
serviceCoreConfiguration:
  userSyncConfiguration:
    intervalMs: 900000          # per-user sync interval
    leaseMs: 300000             # lease held while syncing one user
    workerBatchSize: 25
    maxConcurrentUsers: 4
    leaderLeaseMs: 120000       # scheduler leader lease
  gitlabSyncConfiguration:
    webhookSecret: ${GITLAB_WEBHOOK_SECRET}
    historyWindowMs: 7776000000 # 90 days of merged/closed history
    watermarkOverlapMs: 300000
```

Users can trigger their own sync from the notification menu (**Sync GitLab now**). The footer status bar shows the
sync state for each forge.

## Webhooks

| Forge | Endpoint (on the backend) | Events | Secret |
|---|---|---|---|
| GitHub | `/github-callbacks/v1/events` | Pull request, Pull request review | `GITHUB_APP_WEBHOOK_SECRET` (HMAC `X-Hub-Signature-256`) |
| GitLab | `/gitlab-callbacks/v1/events` | Merge request, Comments (note), Pipeline | `GITLAB_WEBHOOK_SECRET` (`X-Gitlab-Token`) |

Both endpoints deduplicate deliveries. They must be reachable from the forge; they are not served through the UI's
`/api` proxy once sign-in is enabled. See [GitHub App](github-app.md#4-webhooks-optional) and
[GitLab](gitlab.md#4-webhooks-optional) for the forge-side steps.

For local testing, relay webhooks with [smee.io](https://smee.io):

```bash
npx smee-client -u https://smee.io/<channel> -t http://localhost:8090/github-callbacks/v1/events
```

## Sync mode (admin)

**Admin → Sync controls** sets how periodic user sync and webhooks interact. The setting is stored in Aerospike and
applies to the whole cluster.

| Mode | Behaviour |
|---|---|
| Event driven (`AUTO`, recommended) | Periodic sync runs, and Arnold records whether webhooks are arriving |
| Always scheduled (`POLLING`) | Periodic sync always runs |
| Callback only (`CALLBACK_ONLY`) | No periodic user sync; rely entirely on webhooks |

**Reset callback observation** clears the "webhooks seen" flag, for example after changing webhook configuration.
