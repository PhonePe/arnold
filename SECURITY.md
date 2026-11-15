# Security Policy

## Reporting a vulnerability

Please do not open public GitHub issues for security problems. Report them through
[GitHub private vulnerability reporting](https://github.com/PhonePe/arnold/security/advisories/new) with steps to
reproduce, affected versions and any proof of concept.

We aim to acknowledge reports within 3 business days and to share a remediation plan within 14 days.

## Supported versions

Security fixes are applied to the latest released version.

## Deployment hardening

- Never enable development authentication (`auth.dev.enabled`, UI `auth.mode: dev`) outside local machines.
- Supply every secret through environment variables or a secret manager; the shipped configuration contains none.
- Use a random `sessionSecret` of at least 32 bytes shared only between the UI and the backend `arnold-ui` issuer.
- Give runner API keys only the roles they need, and keep runner repositories/projects private.
- Review agents read untrusted merge request content. They get read-only file tools, no shell or network
  tools, and an environment without SCM, Jira, Sonar or Arnold credentials. Sonarfix changes files only through
  path-checked tools and commits only those files. Its coverage pass compiles and runs the tests it writes, which
  executes build code from the repository under review; run sonarfix only on repositories you trust, on isolated
  runners.
- The opt-in Copilot harness relies on undocumented GitHub endpoints; review the caveats in
  `CopilotConfiguration` before enabling it.
