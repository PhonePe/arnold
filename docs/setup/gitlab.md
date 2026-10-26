# GitLab

1. In GitLab, **User/Group settings → Applications**: create an application with scope `api` and redirect URI
   `http://localhost:3000/gitlab/oauth/callback`.
2. `.env`:

   ```bash
   GITLAB_URL=https://gitlab.com
   GITLAB_OAUTH_CLIENT_ID=...
   GITLAB_OAUTH_CLIENT_SECRET=...
   GITLAB_ACCESS_TOKEN=glpat-...   # runner: clone + comment (api scope)
   GITLAB_SERVICE_TOKEN=glpat-...  # optional, read_api, review-feedback enrichment
   ```

3. `docker compose up -d --force-recreate server runner`, then **Settings → GitLab → Connect GitLab**.

If GitHub is not configured, Arnold asks for a GitLab connection on every page until it is connected.

Webhooks (merge request, comment and pipeline events) go to `/gitlab-callbacks/v1/events` with the secret in
`serviceCoreConfiguration.gitlabSyncConfiguration.webhookSecret`.
