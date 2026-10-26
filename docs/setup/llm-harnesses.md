# LLM harnesses

The runner reviews code with an agent *harness*. Choose per run in **Run Analysis**.

## OpenCode (recommended, bring your own key)

OpenCode runs inside the runner image and talks to any OpenAI-compatible endpoint. There is no account to connect.

```bash
OPENCODE_ENABLED=true
OPENCODE_PROVIDER=openai
OPENCODE_MODELS_URL=https://api.openai.com/v1   # backend: model picker only
OPENCODE_API_KEY=sk-proj-...
CLIO_HARNESS=opencode
CLIO_LLM_BASE_URL=https://api.openai.com/v1     # runner: actual reviews
CLIO_LLM_API_KEY=sk-proj-...
```

Both pairs normally hold the same URL and key.

| Provider | Base URL | Key |
|----------|----------|-----|
| OpenAI | `https://api.openai.com/v1` | `sk-proj-…` |
| OpenRouter | `https://openrouter.ai/api/v1` | `sk-or-…` |
| Groq | `https://api.groq.com/openai/v1` | Groq key |
| Ollama (local, free) | `http://host.docker.internal:11434/v1` | any non-empty value |
| Anthropic, Gemini, Bedrock, Azure | through a [LiteLLM](https://docs.litellm.ai) proxy or the provider's OpenAI-compatible endpoint | provider key |

The model must support tool calling. If the provider has no `/models` endpoint, leave `OPENCODE_MODELS_URL` empty and
list models under `serviceCoreConfiguration.openCodeConfiguration.models` in `local.yml`.

## GitHub Copilot (opt-in, see caveat)

```bash
COPILOT_ENABLED=true
COPILOT_CLIENT_ID=Ov23...                       # your own GitHub OAuth App with "Enable Device Flow"
COPILOT_ENCRYPTION_KEY=<openssl rand -base64 32>   # keep stable; changing it disconnects everyone
```

Create the OAuth App under **Settings → Developer settings → OAuth Apps** (separate from the GitHub App), then connect
in **Settings → GitHub Copilot** with an account that has a Copilot subscription.

**Caveat.** Account connection and model listing use undocumented GitHub endpoints (`copilot_internal`), and Copilot
is licensed to individual subscribers; server-side use on behalf of users is a grey area under GitHub's terms. It is
disabled by default and should be enabled only after your own legal review. This is not legal advice.
