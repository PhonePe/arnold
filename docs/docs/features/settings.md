# Settings

**Settings** (account menu → Settings) holds your account, connections and preferences.

## Account

Your identity from the sign-in provider (name, email). In development mode, the local developer user.

## Connected accounts

| Card | Connects | How |
|---|---|---|
| **GitLab** | Your GitLab account, for reading and acting on merge requests | OAuth: **Connect** → authorize on GitLab → back to Arnold |
| **GitHub** | Your GitHub account through the Arnold GitHub App | **Connect** → authorize the app; then install it on repositories if needed |
| **GitHub Copilot** | Your Copilot subscription, for the Copilot harness | Device flow: copy the code, enter it on GitHub, then **Test connection** |

Each card shows its status (*connected*, *disconnected*, *not configured by your administrator*) and offers
**Reconnect** and **Disconnect**. Disconnecting removes the stored token from Arnold; revoke the authorization on the
forge too if you want to remove it there.

A card says *not configured* when the administrator has not set up that integration. See
[GitHub App](../setup/github-app.md), [GitLab](../setup/gitlab.md) and [LLM](../setup/llm.md#github-copilot-opt-in).

## Appearance

Choose **paper** (light) or **charcoal** (dark). The choice is stored in your browser; by default Arnold follows your
system setting. The sun/moon toggle in the top bar does the same.

## Session

Sign out. Sessions last `auth.sessionTtlSeconds` (12 hours by default), after which you sign in again.
