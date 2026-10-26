# GitHub App

Arnold reviews GitHub pull requests through a GitHub App: user-to-server tokens act as the signed-in user, and
short-lived installation tokens (scoped to one repository) let runners post review comments as the app.

## Create the app

GitHub → **Settings → Developer settings → GitHub Apps → New GitHub App**:

| Field | Value |
|-------|-------|
| Homepage URL | `http://localhost:3000` |
| Callback URL | `http://localhost:3000/github/oauth/callback` (must match exactly) |
| Expire user authorization tokens | checked |
| Request user authorization during installation | optional |
| Setup URL | blank |
| Webhook | uncheck **Active** locally (see below) |
| Repository permissions | Pull requests: Read & write · Contents: Read · Checks: Read · Commit statuses: Read · Metadata: Read |
| Where can this app be installed | Only on this account |

Then note the **App ID** (number) and **Client ID**, generate a **client secret** and a **private key**, and
**Install App** on the repositories to review. You can only install it where you have admin rights: for public
projects, fork them.

## Configure

```bash
GITHUB_APP_ENABLED=true
GITHUB_APP_ID=1234567
GITHUB_APP_SLUG=my-arnold
GITHUB_APP_CLIENT_ID=Iv23...
GITHUB_APP_CLIENT_SECRET=...
GITHUB_APP_PRIVATE_KEY=-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----
```

Make the key a single line: `awk 'NF {printf "%s\\n", $0}' my-arnold.private-key.pem`.

Restart: `docker compose up -d --force-recreate server`. Then **Settings → GitHub → Connect GitHub**.

## Use

1. **Run Analysis** → paste `https://github.com/<owner>/<repo>/pull/<n>` → resolve.
2. Open the review (`/review/GITHUB/<repoId>/<n>`): diff, comments, line comments, pending review, approve/request
   changes, resolve threads.
3. Start Improve; the runner posts inline comments as the app.

Current limitation: the Home page and Merge Requests list are populated by GitLab sync only; GitHub PRs are reached via
Run Analysis.

## Webhooks (optional)

GitHub cannot reach `localhost`. Use a relay such as [smee.io](https://smee.io): set the app webhook URL to the smee
channel, `GITHUB_APP_WEBHOOK_SECRET` to the app's webhook secret, subscribe to *Pull request* and *Pull request review*
events, and run `npx smee -u <channel> -t http://localhost:8090/github-callbacks/v1/events`.
