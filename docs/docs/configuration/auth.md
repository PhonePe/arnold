# Roles and API keys

The backend authenticates every request with `Authorization: Bearer <credential>`. The credential is one of:

- a session JWT from the UI (issuer `arnold-ui`);
- a JWT from another trusted issuer (`auth.jwt.issuers`, by shared secret or JWKS);
- a static API key (`auth.apiKeys`).

With `auth.dev.enabled`, requests without a credential act as a development superadmin.

## Roles

| Role | Granted to | Allows |
|---|---|---|
| `superadmin` | `auth.admins`, role mappings, dev user | **Admin** page: sync controls, review metrics, token usage, permissions |
| `runner_execute` | runner key | Claim jobs, heartbeat, complete (`/runner/v1/jobs`) |
| `analysis_callback_write` | runner key | Report analysis progress/results, fetch persona snapshots, exchange Copilot execution keys |
| `smart_diff_callback_write` | runner key | Store story mode (smart diff) results |
| `review_feedback_write` | runner key | Ingest review feedback |
| `review_feedback_read` | | Query review feedback (`POST /review-feedback`) |
| `backfill_review_feedback` | | Housekeeping backfill of review feedback |

Signed-in users need no role for everyday features; what they can see and do on a forge is decided by their own
forge permissions.

## Admins and role mappings

```yaml title="local.yml"
serviceCoreConfiguration:
  auth:
    admins: [alice@example.com, bob@example.com]   # emails or user ids → superadmin (env: ARNOLD_ADMINS)
    roleMappings:                                  # group/org claim value → roles
      arnold-admins: [superadmin]
      quality-team: [review_feedback_read]
    jwt:
      userIdClaim: sub
      emailClaim: email
      nameClaim: name
      rolesClaim: groups
      clockSkewSeconds: 60
```

Groups come from the sign-in provider: the OIDC `groupsClaim`, or GitHub organization names when
`allowedOrganizations` is used.

## API keys

```yaml title="local.yml"
serviceCoreConfiguration:
  auth:
    apiKeys:
      - name: runner
        key: ${ARNOLD_RUNNER_API_KEY}
        roles: [runner_execute, analysis_callback_write, smart_diff_callback_write, review_feedback_write]
      - name: reporting
        key: ${ARNOLD_REPORTING_API_KEY}
        roles: [review_feedback_read]
```

Keys are configured only in the file (there is no UI). Use long random values and a separate key per consumer.
An entry with an empty `key` is ignored.
