# GitHub App

Arnold reviews GitHub pull requests through a **GitHub App** that you create and own. The app gives Arnold:

- **user-to-server tokens**, so actions in Arnold (comments, reviews, approvals, resolving threads) are performed as
  the signed-in user;
- **installation tokens**, short-lived and scoped to one repository, which runners use to clone and post review
  comments as the app;
- **webhooks** (optional) for instant pull request updates.

github.com and GitHub Enterprise Server are both supported.

!!! info "Two different GitHub registrations"
    The GitHub **App** on this page is for reviewing code. Signing in to Arnold with GitHub uses a separate GitHub
    **OAuth App** (see [Sign-in](sign-in.md)), and so does the optional Copilot harness (see [LLM](llm.md)).

## 1. Create the app

Go to **GitHub → Settings → Developer settings → GitHub Apps → New GitHub App** (or the same page under an
organization's settings to own it there).

| Field | Value |
|---|---|
| GitHub App name | Anything unique, e.g. `acme-arnold`. Its URL name is the *slug* |
| Homepage URL | Your Arnold URL, e.g. `http://localhost:3000` |
| Callback URL | `<ARNOLD_UI_URL>/github/oauth/callback`, e.g. `http://localhost:3000/github/oauth/callback`. Must match exactly |
| Expire user authorization tokens | Checked |
| Request user authorization (OAuth) during installation | Optional |
| Enable Device Flow | Unchecked |
| Setup URL | Blank |
| Webhook → Active | Unchecked for local use. For a reachable deployment, see [webhooks](#4-webhooks-optional) |
| Where can this GitHub App be installed? | *Only on this account* for a single org; *Any account* to let others install it |

**Repository permissions**

| Permission | Access | Used for |
|---|---|---|
| Pull requests | Read & write | Read PRs, post comments and reviews, resolve threads |
| Contents | Read | Clone the code, read files and diffs |
| Checks | Read | Merge checks in the review workspace |
| Commit statuses | Read | Merge checks in the review workspace |
| Metadata | Read | Mandatory |

No organization or account permissions are needed.

## 2. Collect credentials

On the app's settings page:

1. Note the **App ID**: a number such as `1234567`, not the name.
2. Note the **Client ID**: `Iv1.…` or `Iv23…`.
3. **Generate a new client secret** and copy it.
4. **Generate a private key**. A `.pem` file downloads.
5. **Install App** on the accounts and repositories Arnold should review. You need admin rights on them; to try
   Arnold on a public project you do not own, fork it and install the app on the fork.

## 3. Configure Arnold

```bash title=".env"
GITHUB_APP_ENABLED=true
GITHUB_APP_ID=1234567
GITHUB_APP_SLUG=acme-arnold
GITHUB_APP_CLIENT_ID=Iv23...
GITHUB_APP_CLIENT_SECRET=...
GITHUB_APP_PRIVATE_KEY=-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----
```

The private key must be on one line with literal `\n` between lines. Convert the downloaded file with:

```bash
awk 'NF {printf "%s\\n", $0}' acme-arnold.*.private-key.pem
```

PKCS#1 (`BEGIN RSA PRIVATE KEY`) and PKCS#8 (`BEGIN PRIVATE KEY`) keys both work.

For **GitHub Enterprise Server**, also set:

```bash
GITHUB_WEB_URL=https://github.example.com
GITHUB_API_URL=https://github.example.com/api/v3
```

Apply and connect:

```bash
docker compose up -d --force-recreate server
```

In Arnold, open **Settings → Connected accounts → GitHub → Connect**, authorize the app, and you are sent back to
Arnold. The setup guide then offers **Install Arnold on repositories** if the app is not installed anywhere yet.

## 4. Webhooks (optional)

Without webhooks, [repository sync](webhooks-and-sync.md) re-imports open pull requests every few minutes. With them,
updates are immediate.

1. In the app settings, check **Webhook → Active**.
2. Webhook URL: `https://<backend-host>/github-callbacks/v1/events`. Expose the backend for this path; the UI's
   `/api` proxy only forwards it in development mode, because it requires a signed-in session otherwise.
3. Set a random **Webhook secret** and put the same value in `GITHUB_APP_WEBHOOK_SECRET`.
4. Under **Permissions & events → Subscribe to events**, select **Pull request** and **Pull request review**.

Arnold verifies `X-Hub-Signature-256` and ignores duplicate deliveries (`X-GitHub-Delivery`).

For local testing, relay webhooks with [smee.io](https://smee.io):

```bash
npx smee-client -u https://smee.io/<channel> -t http://localhost:8090/github-callbacks/v1/events
```

## How runners authenticate to GitHub

When the app is configured, the backend mints an installation token for the pull request's repository and adds it to
each job as `SCM_GITHUB_TOKEN`. Runners then need no GitHub credential of their own, and comments are authored by the
app (`acme-arnold[bot]`).

## Limitations

- **Apply suggestion** is not supported on GitHub. Use GitHub's own *Commit suggestion*.
- A user sees a pull request in *their* queue only after connecting their GitHub account. All synced pull requests
  are always visible under **Merge Requests → All open**.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Incorrect type of value at: …githubConfiguration.appId` | `GITHUB_APP_ID` must be the numeric ID |
| GitHub shows a *redirect_uri* error when connecting | The app callback URL must equal `<ARNOLD_UI_URL>/github/oauth/callback` exactly |
| `GitHub integration is not configured` | `GITHUB_APP_ENABLED`, client id or secret missing; recreate `server` |
| Runner: *SCM token required for MR link parsing* | The app is not installed on that repository, or the App ID/private key is wrong. Check the server log for *installation token* warnings |
| *HTTP 401 Bad credentials* minting tokens | Wrong key, or the host clock is skewed (common with Podman after sleep); see [Troubleshooting](../operations/troubleshooting.md) |
