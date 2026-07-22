# Arnold Monorepo

This is a monorepo for the Arnold project, an AI-driven code review platform for GitLab on-prem. It consists of
multiple modules including a backend server, data models, core logic, a client, and a frontend UI.

## Project Structure

This repository contains multiple modules, each serving a specific purpose in the overall Arnold system. Below is a
brief overview of each module:

- `arnold-server`: This java module contains the backend server code for Arnold. It just wires up the dropwizard
  application but does not include the core logic. It just includes the core-bundle (`ArnoldBundle`).
- `arnold-models`: This java module defines the data models used across the Arnold system. It includes the request,
  response and Elasticsearch document classes for review feedback.
- `arnold-client`: This java module contains the client code for interacting with the Arnold backend service.
- `arnold-core`: This java module is the heart of the backend service, managing review-feedback ingestion, persistence
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
