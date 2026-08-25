# Arnold Monorepo

This is a monorepo for the Arnold project, an AI-driven code review platform for GitLab on-prem. It consists of
multiple modules including a backend server, data models, core logic, a client, and a frontend UI.

## Project Structure

This repository contains multiple modules, each serving a specific purpose in the overall Arnold system. Below is a
brief overview of each module:

- `arnold-backend/arnold-server`: This java module contains the backend server code for Arnold. It just wires up the dropwizard
  application but does not include the core logic. It just includes the core-bundle (`ArnoldBundle`).
- `arnold-backend/arnold-models`: This java module defines the data models used across the Arnold system. It includes the request,
  response and Elasticsearch document classes for review feedback.
- `arnold-backend/arnold-client`: This java module contains the client code for interacting with the Arnold backend service.
- `arnold-backend/arnold-core`: This java module is the heart of the backend service, managing review-feedback ingestion, persistence
  to Elasticsearch (via Columbus), CIBot integration, resources, services, and core functionalities, etc.
- `arnold-ui`: This react module (Next.js) contains the frontend code for Arnold. It provides the user interface for the
  code review platform, including the merge queue, MR review workspace, personas, policies, and stats. It makes
  appropriate API calls to the backend service and renders the data accordingly.
- the rest of the folders should be ignored.

## Guidelines

- For understanding the core concepts of Arnold, refer to the [README.md](README.md)
- For instructions on how to contribute to the backend modules (arnold-server, arnold-core, arnold-models,
  arnold-client), refer to [BACKEND.md](arnold-backend/BACKEND.md)
- For instructions on how to contribute to the UI module, refer to [arnold-ui/AGENTS.md](arnold-ui/AGENTS.md)

## Base Directives

### Commit Styles
- Remember to make meaningful commits each time after a logical state of the code.
- Commit messages should be succinct and crisp. Use present tense and imperative mood. 
- Format - type(ui/backend|<code-group>): <message>. type can be one of feat, fix, perf, refactor, test, docs, ci,  
  chore, res (for resources). Group can be a logical code component. 
- Egs: 
  - feat(backend|workflow): prefill task and milestone owner fields based on project context
  - perf(backend|sync): optimize task and milestone owner prefill logic
  - fix(ui|login): fixes olympus login fails for users with special characters in their username
- NEVER push any changes

### Code Comments
- Never write code comments explaining your approach. Only write them from the perspective of a reader who is reading
  the code for the first time, or trying to understand logic. Write comments only when absolutely necessary for
  explaining complicated logic.
- Keep it precise and non-verbose. Avoid writing comments for trivial code or obvious logic.