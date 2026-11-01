# Admin and insights

## Admin

**Governance → Admin** is visible to users with the `superadmin` role (see
[Roles](../configuration/auth.md#admins-and-role-mappings)).

### Sync controls

- **Merge request sync mode**: *Event driven* (recommended), *Always scheduled* or *Callback only*. See
  [Webhooks and sync](../setup/webhooks-and-sync.md#sync-mode-admin).
- Whether webhooks have been observed, with **Reset callback observation**.
- The current sync leader (backend instance) and its lease.
- Repository sync status, with a manual trigger.

### Review metrics

Needs [Elasticsearch](../setup/storage.md#elasticsearch-optional). Measures whether AI findings are useful:

- **acceptance rate**: share of findings applied or partially applied;
- **resolution rate**: share of AI threads resolved;
- **outcome mix**: applied / partially applied / ignored / not applicable;
- **suggestion usage**: how often suggested changes are applied;
- AI vs human comments; persona vs general findings; weekly trends.

Filter by project, persona, model, category, priority, comment type and outcome. Each chart lists the Elasticsearch
fields it uses, so you can rebuild it in Kibana.

### Token usage

Tokens consumed by runs: totals, by workflow, by model, and a list of recent jobs with harness, models and input,
cached and output tokens. Use it to track cost per workflow and compare models.

## Organisation Insights

**Organisation Insights** (`/stats`) is reserved for organisation-wide review analytics (throughput, time to merge,
review load). The page currently shows a *coming soon* placeholder; the backend endpoint `GET /stats/v1/organization`
exists for integrations.

Personal stats appear on [Home](merge-queue.md#home) (`GET /stats/v1/mine`).

## Review feedback API

The acceptance data behind review metrics is available to integrations with an API key holding
`review_feedback_read`:

```bash
curl -X POST https://arnold-api.example.com/review-feedback \
  -H "Authorization: Bearer $ARNOLD_REPORTING_API_KEY" -H 'Content-Type: application/json' \
  -d '{"statuses": ["ignored"], "scope": "PROJECT", "targetPath": "acme/payments", "daysBack": 30}'
```

| Field | Default | Meaning |
|---|---|---|
| `statuses` | all | `applied`, `partially_applied`, `ignored`, `not_applicable` |
| `scope`, `targetPath` | `GROUP` | `GROUP` (group path) or `PROJECT` (full `namespace/project`) |
| `daysBack` | `30` | 1–90 |
| `minScore` | `0.5` | Minimum assessment confidence, 0–1 |
| `minPriority` | | Minimum finding priority |
| `limit`, `offset` | `500`, `0` | Paging (limit ≤ 1000) |
