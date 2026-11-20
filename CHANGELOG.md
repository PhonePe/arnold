# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0]

First public release.

### Added

- Merge-request workspace for GitLab and GitHub: diffs, discussions, draft reviews, suggestions and thread resolution.
- Personal merge queue with review groups, priority ordering and repository sync of all open merge/pull requests.
- AI review workflows (improve, review, describe, smart diff, group review, impact) with personas, executed by
  `arnold-runner` locally, on GitLab CI or on GitHub Actions.
- LLM harnesses: OpenCode against any OpenAI-compatible endpoint, and an opt-in GitHub Copilot harness.
- GitHub App integration (installation tokens, user OAuth, signed webhooks) and GitLab OAuth integration with webhooks.
- Pluggable authentication: API keys, JWT (shared secret or JWKS), UI sign-in via GitHub or OpenID Connect.
- Review statistics and feedback metrics backed by optional Elasticsearch.
- Docker Compose quick start and container images `ghcr.io/phonepe/arnold-{server,ui,runner}`.
- `ARNOLD_ADMINS`: grant `superadmin` to a comma-separated list of emails or user ids.

### Changed since the v0.1.0 preview

- Elasticsearch is off unless `ELASTIC_ENABLED=true`. When off, the backend creates no client, `/healthcheck` no
  longer fails, analytics writes are skipped and analytics endpoints return `503` (error `4002`). Deployments that use
  Elasticsearch must set `ELASTIC_ENABLED=true`.

[Unreleased]: https://github.com/PhonePe/arnold/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/PhonePe/arnold/releases/tag/v1.0.0
