## Introduction

This project is a multi-module Java application written in Java 17, that uses JUnit 5 for testing.

# For module: arnold-models, arnold-core, arnold-server, arnold-client

## IMPORTANT

1. Make sure you use the best java practices. Use java 17 constructs while generating tests.
2. Ensure that you keep the code precise and non-verbose.
3. Do not write code comments explaining your approach. Only write them from the perspective of a reader who is reading
   the code for the first time, or trying to understand logic. Write comments only when absolutely necessary for
   explaining complicated logic.
4. Reusability of code is important. If you find yourself writing the same code in multiple places, consider creating a
   utility class or method to encapsulate that logic.
5. Use meaningful variable names that convey the purpose of the variable.
6. When designing APIs, look at existing resources under the com.phonepe.devx.arnold.core package in arnold-core
   module for reference. Create model classes that are required for the APIs under com.phonepe.devx.arnold.models
   package in the arnold-models module.

## Domain model

The models live in `arnold-models` (`com.phonepe.devx.arnold.models`).

- **`ReviewFeedbackRequest`** — the payload ingested from the AI review workflow pipeline. Bundles the merge request
  `metadata` (`ReviewMetadata`) with the list of `comments` (`ReviewComment`) produced for it.
- **`ReviewMetadata`** — identifies the merge request: `project_id`, `mr_id`, `mr_iid` (project-scoped internal id) and
  `mr_author_id`.
- **`ReviewComment`** — a single AI-generated comment: `note_id`, `discussion_id`, `comment_type`, `has_suggestion`,
  `status` (`AcceptanceStatus`), `score` (`0.0`–`1.0`), `reasoning` (optional), `priority` and `comment_category`.
- **`ReviewFeedbackDocument`** — the Elasticsearch document persisted per comment. Each `ReviewComment` is enriched with
  `project` (`ProjectInfo`), `mrAuthor`/`reviewer` (`User`) and `position` (resolved via CIBot), plus `ingestedAt`. Its
  `documentId()` is the deterministic ES upsert id, formatted as `projectId:mrId:noteId`.

Arnold talks to the **CIBot** service to fetch GitLab project, user and note details used to enrich review feedback
before it is indexed.

## Ingestion API

Review feedback is ingested via a single authenticated endpoint, secured by Olympus:

```
POST /review-feedback/v1/ingest
```

The request body is a `ReviewFeedbackRequest`. Each comment is persisted as its own `ReviewFeedbackDocument` in
Elasticsearch, and the response returns the number of comments ingested.

## TESTS

1. Think deeply about how you plan on writing these tests and then write them thoroughly.
2. Use knowledge of existing tests and base tests when generating new tests. Ensure that you reuse existing flows and
   utilities
3. For Resource tests, use existing tests on resources as reference and extend Base tests as necessary
4. Add minimal code comments on the tests, on in places where there is a logic that needs to be explained. Don't write
   basic comments, like explaining variable declaration.
5. For Model classes whose implementations are not available in the codebase, make sure you ask for implementation of
   the model, if you are unaware, before trying to assume or hallucinate the model.
6. Naming convention should follow the pattern: `test<MethodName>_should<Scenario>_when<Context>`. For example,
   `testGetListingView_shouldReturnValidListing_whenValidUnitData`.
7. Add @Nested tests wherever applicable to group related tests together.
8. Always use `final var` for variable declarations where applicable.
9. If the modules contain Data generation utilities, they will be present under "/data" (like DataGenerator.java,
   DataSets.java, TestDataGenerator.java etc). Utilize them while writing tests whenever possible


<!-- headroom:rtk-instructions -->
# RTK (Rust Token Killer) - Token-Optimized Commands

When running shell commands, **always prefix with `rtk`**. This reduces context
usage by 60-90% with zero behavior change. If rtk has no filter for a command,
it passes through unchanged — so it is always safe to use.

## Key Commands
```bash
# Git
rtk git status          rtk git diff            rtk git log

# Files & Search
rtk ls <path>           rtk read <file>         rtk grep <pattern>
rtk find <pattern>      rtk diff <file>

# Test — shows failures only
rtk pytest tests/       rtk cargo test          rtk test <cmd>

# Build & Lint — shows errors only
rtk tsc                 rtk lint                rtk cargo build
rtk prettier --check    rtk mypy                rtk ruff check

# Analysis
rtk err <cmd>           rtk log <file>          rtk json <file>
rtk summary <cmd>       rtk deps                rtk env

# GitHub
rtk gh pr view <n>      rtk gh run list         rtk gh issue list

# Infrastructure
rtk docker ps           rtk kubectl get         rtk docker logs <c>

# Package managers (70-90% savings)
rtk pip list            rtk pnpm install        rtk npm run <script>
```

## Rules
- In command chains, prefix each segment: `rtk git add . && rtk git commit -m "msg"`
- For debugging, use raw command without rtk prefix
- `rtk proxy <cmd>` runs command without filtering but tracks usage
<!-- /headroom:rtk-instructions -->



# Local debugging

### For running the server on command line:

```bash
cd arnold-server && sh run.sh d
```

You can then connect remotely from intellij remotely, by setting up a remote configuration (Remote JVM debug), and
connecting to the default 5005 port

### For running the server on IntelliJ:

You can run the service from Intellij Idea as well by setting the following in edit configurations (a ready-made
configuration also lives in [.run/ArnoldApplication.run.xml](.run/ArnoldApplication.run.xml)).

**Prerequisite**

When running service locally, local.yml config would be fetched.  
Pre requisite make sure you have zookeeper running, as the local.yml points to localhost:2181  
Alternatively you can also point to stage zookeeper, just make sure you don't block others.

Select type Application and choose the `ArnoldApplication` Main class from the server-module

1) *VM args*  
   `-DlocalConfig=true`

2) *Program args*  
   `server config/local.yml`

3) *Working directory*  
   `$PROJECT_DIR$/arnold-server`

4) *Environment variables*  
   `TEAM_ID=shopping;DROVE_APP_NAME=arnold`

Voila! Visit `localhost:8090/swagger` to check the APIs (admin context runs on `localhost:8091`).