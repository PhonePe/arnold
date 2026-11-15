# Copyright (c) 2026 Original Author(s), PhonePe Limited
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
LLM module for Clio.

Provides two calling modes:

1. ``call_llm()``      — simple, non-agentic chat completion against an OpenAI-compatible endpoint.
2. ``call_llm_agentic()`` — agentic sessions with tool calling, dispatched to
   the active harness (Copilot SDK or OpenCode) via ``CLIO_HARNESS`` env var.
"""

import os
import logging
from datetime import datetime
import time
from openai import OpenAI
import tiktoken
import httpx
from utils.prompt_safety import with_untrusted_guard
from utils.config import get_harness_label
from utils import llm_settings

# Set up log directory and file
log_directory = os.getenv("LOG_DIR", "logs")
os.makedirs(log_directory, exist_ok=True)
log_file = os.path.join(
    log_directory, f"llm_calls_{datetime.now().strftime('%Y%m%d')}.log"
)

# Set up logger
logger = logging.getLogger("llm_logger")
logger.setLevel(logging.INFO)
logger.propagate = False

if not logger.handlers:
    # File handler
    file_handler = logging.FileHandler(log_file, encoding='utf-8')
    file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(file_handler)

    # Console handler (for GitLab runner output)
    console_handler = logging.StreamHandler()
    console_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(console_handler)

def estimate_tokens(text):
    # Try tiktoken for accurate count, fall back to rough estimate (~4 chars per token)
    try:
        encoding = tiktoken.encoding_for_model("gpt-4")
        num_tokens = len(encoding.encode(text))
        return num_tokens
    except Exception:
        return len(text) // 4

def call_llm(prompt, use_cache: bool = True):
    prompt_length = len(prompt)
    prompt_words = len(prompt.split())
    estimated_input_tokens = estimate_tokens(prompt)
    
    logger.info(f"=== LLM CALL START === (prompt: {prompt_length} chars, ~{estimated_input_tokens} tokens)")
    if estimated_input_tokens > 1000000:
        raise ValueError("Token limit exceeded. The input tokens exceed the 1,000,000 token limit. LLM call aborted.")

    start_time = time.time()
    http_client = httpx.Client(verify=os.environ.get("SSL_CERT_FILE") or True)
    client = OpenAI(base_url=llm_settings.base_url(), api_key=llm_settings.api_key(), http_client=http_client)

    # Log prompt stats
    logger.debug(f"Use cache: {use_cache}")

    try:
        r = client.chat.completions.create(
            model=llm_settings.model(),
            messages=[{"role": "user", "content": prompt}],
            response_format={"type": "text"},
            store=False
        )
        response = r.choices[0].message.content
        api_call_time = time.time() - start_time

        response_length = len(response)
        estimated_output_tokens = estimate_tokens(response)
        estimated_total_tokens = estimated_input_tokens + estimated_output_tokens

        logger.info(f"=== LLM CALL END === (time: {api_call_time:.2f}s, output: ~{estimated_output_tokens} tokens, total: ~{estimated_total_tokens} tokens)")

        return response

    except Exception as e:
        elapsed_time = time.time() - start_time
        logger.error(f"LLM call failed after {elapsed_time:.2f} seconds: {e}")
        logger.error("=== LLM CALL FAILED ===")
        raise
if __name__ == "__main__":
    test_prompt = "Hello, how are you?"

    # First call - should hit the API
    logger.info("Making call...")
    response1 = call_llm(test_prompt, use_cache=False)
    logger.info(f"Response: {response1}")


# ---------------------------------------------------------------------------
# Agentic harness router
# ---------------------------------------------------------------------------

DEFAULT_SYSTEM_MESSAGE = (
    "You are a helpful AI assistant. Follow the user's instructions carefully."
)

DEFAULT_COPILOT_MODEL = os.getenv("CLIO_LLM_MODEL", "gpt-6.1-sol")


def resolve_effective_model(model: str | None = None) -> str:
    """Return the model that will actually run for the active harness."""
    if get_harness_label() == "opencode":
        # Copilot-native names (no "/") are ignored by OpenCode.
        if model is None or "/" not in model:
            from utils.harness.opencode_harness import DEFAULT_OPENCODE_MODEL
            return os.getenv("CLIO_OPENCODE_MODEL", DEFAULT_OPENCODE_MODEL)
        return model
    return model or DEFAULT_COPILOT_MODEL


def call_llm_agentic(
    prompt: str,
    tools: list,
    system_message: str | None = None,
    model: str | None = None,
    working_directory: str | None = None,
    custom_agents: list[dict] | None = None,
    enable_graph_tools: bool = True,
    _is_fallback: bool = False,
    disable_fallback: bool = False,
) -> str:
    """Run an agentic session with the given tools.

    The active harness is selected via the ``CLIO_HARNESS`` environment
    variable (``copilot`` or ``opencode``; default ``copilot``).

    Parameters
    ----------
    prompt : str
        The user-role message content.
    tools : list
        A list of tool objects created with ``utils.tool_adapter.define_tool``.
    system_message : str | None
        Optional system-role message.
    model : str | None
        The model to use.  When ``None``, defaults to ``CLIO_LLM_MODEL``
        (Copilot harness) or ``CLIO_OPENCODE_MODEL`` (OpenCode harness).
    working_directory : str | None
        If set, the harness's built-in filesystem tools operate relative
        to this directory.
    custom_agents : list[dict] | None
        Optional list of sub-agent config dicts (``name``, ``prompt``, …).
    enable_graph_tools : bool
        Whether graph MCP tools may be registered for this session.
    _is_fallback : bool
        Internal flag to prevent infinite recursion during fallback.
    disable_fallback : bool
        When True, skip the copilot fallback even if opencode fails.
        Use this when caller handles failure recovery itself.
    """
    from utils.harness import get_harness

    harness_name = get_harness_label()
    explicit_selection = os.getenv("CLIO_DISABLE_FALLBACK", "").lower() == "true"
    disable_fallback = disable_fallback or explicit_selection
    if explicit_selection:
        model = os.getenv("CLIO_OPENCODE_MODEL" if harness_name == "opencode" else "CLIO_LLM_MODEL") or model
    if os.environ.get("ARNOLD_COPILOT_KEY") and harness_name != "copilot":
        raise RuntimeError("Arnold Copilot execution keys require the Copilot harness")

    model = resolve_effective_model(model)

    sys_msg = with_untrusted_guard(system_message if system_message is not None else DEFAULT_SYSTEM_MESSAGE)
    if custom_agents:
        custom_agents = [{**a, "prompt": with_untrusted_guard(a.get("prompt", ""))} for a in custom_agents]

    harness = get_harness()

    start_time = time.time()
    logger.info(f"=== AGENTIC SESSION START === (harness: {harness_name}, model: {model}, tools: {len(tools)})")
    if custom_agents:
        logger.info(f"Custom agents: {[a.get('name', '?') for a in custom_agents]}")

    # Remember the model actually used for logging; fallback will use a
    # different model so we must not mutate this variable.
    _effective_model = model

    session_kwargs = dict(
        prompt=prompt,
        tools=tools,
        system_message=sys_msg,
        model=_effective_model,
        working_directory=working_directory,
        custom_agents=custom_agents,
        enable_graph_tools=enable_graph_tools,
    )

    original_cwd = None
    try:
        original_cwd = os.getcwd()
    except OSError:
        original_cwd = None

    try:
        if working_directory:
            os.chdir(working_directory)

        try:
            response = harness.run_session(**session_kwargs)

            api_call_time = time.time() - start_time

            logger.info(f"=== AGENTIC SESSION END === (time: {api_call_time:.2f}s)")

            return response

        except Exception as e:
            elapsed_time = time.time() - start_time
            logger.error(
                f"Agentic LLM call failed after {elapsed_time:.2f}s "
                f"(harness={harness_name}, model={model}): {e}"
            )

            # Fallback: if the opencode harness failed, retry with copilot
            if harness_name == "opencode" and not _is_fallback and not disable_fallback:
                logger.warning(
                    "=== OPENCODE HARNESS FAILED — FALLING BACK TO COPILOT HARNESS ==="
                )
                logger.warning(f"OpenCode failure reason: {e}")

                try:
                    original_harness = os.environ.get("CLIO_HARNESS")
                    os.environ["CLIO_HARNESS"] = "copilot"
                    try:
                        response = call_llm_agentic(
                            prompt=prompt,
                            tools=tools,
                            system_message=system_message,
                            model=model if "/" not in model else DEFAULT_COPILOT_MODEL,
                            working_directory=working_directory,
                            custom_agents=custom_agents,
                            enable_graph_tools=enable_graph_tools,
                            _is_fallback=True,
                            disable_fallback=True,
                        )
                    finally:
                        if original_harness is not None:
                            os.environ["CLIO_HARNESS"] = original_harness
                        else:
                            os.environ.pop("CLIO_HARNESS", None)

                    total_time = time.time() - start_time
                    logger.info(
                        f"Copilot fallback succeeded (total wall time: {total_time:.2f}s)"
                    )
                    logger.info("=== AGENTIC LLM CALL END (via copilot fallback) ===")
                    return response

                except Exception as fallback_err:
                    total_time = time.time() - start_time
                    logger.error(
                        f"Copilot fallback also failed after {total_time:.2f}s: "
                        f"{fallback_err}"
                    )
                    logger.error("=== AGENTIC LLM CALL FAILED (both harnesses) ===")
                    raise fallback_err from e

            logger.error("=== AGENTIC LLM CALL FAILED ===")
            raise
    finally:
        if original_cwd is not None:
            try:
                os.chdir(original_cwd)
            except OSError as exc:
                logger.warning(
                    f"Failed to restore original working directory "
                    f"{original_cwd!r}: {exc}"
                )
