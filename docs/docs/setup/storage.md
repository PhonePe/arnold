# Storage and analytics

## Aerospike (required)

Aerospike holds all operational state. Arnold uses one namespace and a set per entity (users, change requests,
comments, queues, personas, analysis runs, runner jobs, notifications, credentials, …).

```bash title=".env"
AEROSPIKE_HOSTS=aerospike-1,aerospike-2    # comma-separated seeds; port 3000
```

```yaml title="local.yml"
aerospike:
  hosts: ${AEROSPIKE_HOSTS:-localhost}
  port: 3000
  retries: 5
  sleepBetweenRetries: 500
  timeout: 500
  maxConnectionsPerNode: 10
```

The sets live under `serviceCoreConfiguration.aerospikeStorageConfiguration`, each with a `namespace` and a `set`. The
default namespace is `test`; for production, create a dedicated namespace (e.g. `arnold`) and change every entry.

Namespace requirements:

- `nsup-period` must be non-zero. Arnold writes records with TTLs (OAuth state, leases, deliveries), and Aerospike
  rejects them with error 22 *Operation not allowed* when `nsup-period` is 0.
- Use persistent storage (`storage-engine device` or `pmem`/`memory` with a `file`). The quick start's
  `deploy/aerospike/aerospike.conf` is file-backed in the `aerospike-data` volume.
- Restrict network access and enable authentication: the database contains users' forge OAuth tokens.

Arnold creates the one secondary index it needs (on the user sync state set) at startup, so its database user needs
`sys-admin` or `data-admin` privileges for that, plus `read-write` on the namespace. Nothing else needs provisioning.
Back up the namespace with `asbackup`.

## Elasticsearch (optional)

Elasticsearch 7.x (or 8.x in 7-compatibility mode) stores analytics documents. Without it, Arnold works, but these
show empty states:

- Home **Your productivity** chart and merged/median stats;
- **Organisation Insights**;
- persona metrics on **Personas**;
- **Admin → Review metrics**.

It is off by default: no client is created, `/healthcheck` does not include it, analytics writes are skipped, and
analytics endpoints return `503` with error code `4002`.

Run it with the quick start (set `ELASTIC_ENABLED=true` in `.env` first):

```bash
docker compose --profile analytics up -d
```

Point Arnold at your cluster:

```bash title=".env"
ELASTIC_ENABLED=true
ELASTIC_HOST=es.example.com
ELASTIC_PORT=9200
ELASTIC_SCHEME=https
ELASTIC_USERNAME=arnold
ELASTIC_PASSWORD=...
```

Indices, created on first write:

| Key | Index | Contents |
|---|---|---|
| `CORE_REVIEWS` | `arnold.code.reviews` | AI review findings and their acceptance status |
| `CORE_MERGE_REQUESTS` | `arnold.merge.requests` | Merge request projections for stats |
| `CORE_COMMENTS` | `arnold.comments` | Comment projections |
| `CORE_ANALYSIS_RUNS` | `arnold.analysis.runs` | Analysis runs and token usage |
| `CORE_REVIEW_ENGAGEMENT` | `arnold.review.engagement` | Reviewer engagement events |

Rename indices under `elasticSearchConfiguration.columbus.storages[0].indices.registry` in `local.yml`.

The user needs `create_index`, `write`, `read` and `view_index_metadata` on `arnold.*`.
