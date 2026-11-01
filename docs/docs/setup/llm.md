# LLM and harnesses

The runner reviews code with an agent **harness**: a program that lets the model read the repository, run tools
and produce findings. Users choose the harness and model per run in **Run Analysis**; deployments set the defaults.

| Harness | Model access | Status |
|---|---|---|
| **OpenCode** | Any OpenAI-compatible endpoint, with your own key | Recommended. Enabled by default |
| **GitHub Copilot** | Each user's Copilot subscription | Opt-in, disabled by default. Read the [caveat](#github-copilot-opt-in) |

## OpenCode

[OpenCode](https://opencode.ai) is bundled in the runner image and talks to any OpenAI-compatible API. There is no
OpenCode account; the key is your LLM provider's key.

Two sets of variables point at the same provider:

| Variables | Read by | Purpose |
|---|---|---|
| `OPENCODE_MODELS_URL`, `OPENCODE_API_KEY` | backend | Lists models (`GET {url}/models`) for the model picker in Run Analysis |
| `CLIO_LLM_BASE_URL`, `CLIO_LLM_API_KEY` | runner | Makes the actual review calls |

```bash title=".env"
# backend
OPENCODE_ENABLED=true
OPENCODE_PROVIDER=openai                       # prefix for model ids: openai/gpt-4.1
OPENCODE_MODELS_URL=https://api.openai.com/v1
OPENCODE_API_KEY=sk-...

# runner
CLIO_HARNESS=opencode
CLIO_LLM_BASE_URL=https://api.openai.com/v1
CLIO_LLM_API_KEY=sk-...
CLIO_OPENCODE_MODEL=openai/gpt-4.1             # default when a run does not pick one
```

!!! warning "Set `CLIO_HARNESS=opencode`"
    The runner's built-in default harness is `copilot`. Runs started from the UI always carry the chosen harness,
    but anything else (CLI runs, older jobs) falls back to `CLIO_HARNESS`.

### Providers

| Provider | Base URL | Key |
|---|---|---|
| OpenAI | `https://api.openai.com/v1` | `sk-…` |
| OpenRouter | `https://openrouter.ai/api/v1` | `sk-or-…` |
| Groq | `https://api.groq.com/openai/v1` | Groq key |
| Azure OpenAI, Anthropic, Gemini, Bedrock | Through a [LiteLLM](https://docs.litellm.ai) proxy or the provider's OpenAI-compatible endpoint | Provider key |
| Ollama (local, free) | `http://host.docker.internal:11434/v1` | Any non-empty value |
| vLLM, LM Studio, other self-hosted | Your server's `/v1` URL | As configured |

Requirements for the model:

- It must support **tool calling**. Reviews fail or produce nothing otherwise.
- A long context helps (64k+ tokens); large changes are reviewed with repository context.
- Reasoning models work if the endpoint accepts OpenAI's parameters for them. Some gateways reject `max_tokens` for
  reasoning models; use a model the gateway supports in that case.

### Models without a `/models` endpoint

Leave `OPENCODE_MODELS_URL` empty and list the models in `local.yml`:

```yaml
serviceCoreConfiguration:
  openCodeConfiguration:
    enabled: true
    provider: openai
    defaultModel: gpt-4.1                  # preselected in the picker
    models:
      - { id: gpt-4.1, name: GPT-4.1 }
      - { id: gpt-4.1-mini, name: GPT-4.1 mini }
```

Model ids are exposed to runners as `provider/id`, e.g. `openai/gpt-4.1`.

### Runner overrides

| Variable | Default | Purpose |
|---|---|---|
| `CLIO_OPENCODE_API_KEY` | `CLIO_LLM_API_KEY` | Different key for OpenCode only |
| `CLIO_OPENCODE_BASE_URL` | `CLIO_LLM_BASE_URL` | Different endpoint for OpenCode only |
| `CLIO_OPENCODE_TIMEOUT` | `2700` | Session timeout in seconds |

## GitHub Copilot (opt-in)

The Copilot harness lets each user run reviews on their own Copilot subscription. Users connect their GitHub account
with a device code in **Settings**. Arnold stores the token encrypted, and gives each job a short-lived *execution
key* that the runner exchanges for the user's token just before the session starts.

!!! danger "Caveat"
    Account connection and model listing use undocumented GitHub endpoints (`copilot_internal`), and Copilot is
    licensed to individual subscribers. Server-side use on behalf of users is a grey area under GitHub's terms.
    It is disabled by default. Enable it only after your own legal review. This is not legal advice.

### Enable

1. Create a GitHub **OAuth App** (Settings → Developer settings → OAuth Apps), separate from the GitHub App.
   Check **Enable Device Flow**. The callback URL is unused; any URL works.
2. Configure:

    ```bash title=".env"
    COPILOT_ENABLED=true
    COPILOT_CLIENT_ID=Ov23...
    COPILOT_ENCRYPTION_KEY=<openssl rand -base64 32>
    ```

    Keep `COPILOT_ENCRYPTION_KEY` stable and secret: changing it disconnects every user.

3. `docker compose up -d --force-recreate server runner`.
4. Each user: **Settings → GitHub Copilot → Connect**, enter the code on GitHub, then **Test connection**.

Security notes:

- Execution keys expire after three hours and stop working when the run ends.
- Keep CI debug tracing off in runner projects: job variables include execution keys.
- Only `github.com` accounts are supported.

## How a run picks harness and model

1. The user picks a harness and model in **Run Analysis** (or **Story** mode).
2. The backend checks the harness is available: OpenCode enabled, or Copilot enabled and connected for that user.
3. The job carries `CLIO_HARNESS` and the model, and sets `CLIO_DISABLE_FALLBACK=true`: a failing harness fails the
   run instead of silently switching providers.
4. A blank model means the deployment default (`CLIO_OPENCODE_MODEL` / `CLIO_LLM_MODEL`).

If no harness is available, the setup guide shows *No review engine is available yet* to users.
