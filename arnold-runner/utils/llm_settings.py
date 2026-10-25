"""OpenAI-compatible LLM endpoint settings shared by the simple LLM client and the OpenCode harness.

Any OpenAI-compatible server works: OpenAI, Azure OpenAI, vLLM, Ollama, LiteLLM or an internal gateway.
"""
import os

DEFAULT_BASE_URL = "https://api.openai.com/v1"
DEFAULT_MODEL = "gpt-4.1"


def base_url() -> str:
    return os.environ.get("CLIO_LLM_BASE_URL") or os.environ.get("OPENAI_BASE_URL") or DEFAULT_BASE_URL


def api_key() -> str:
    return os.environ.get("CLIO_LLM_API_KEY") or os.environ.get("OPENAI_API_KEY") or ""


def model() -> str:
    return os.environ.get("CLIO_LLM_MODEL") or DEFAULT_MODEL
