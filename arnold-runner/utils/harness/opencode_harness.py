# Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

"""OpenCode CLI harness for Clio.

Runs agentic sessions via the ``opencode`` CLI, using a generated
``opencode.json`` config that points to an OpenAI-compatible provider and exposes
Clio tools through an MCP stdio server bridge.
"""

from __future__ import annotations

from contextlib import suppress
import json
import logging
import os
import re
from utils import llm_settings
import select
import shutil
import subprocess
import tempfile
import threading
import time
from typing import Any
from urllib.parse import urlparse

from utils.harness.base import AgenticHarness
from utils.harness.common import crg_available, record_usage
from utils.harness.mcp_tool_server import MCPToolBridge
from utils.logger import get_logger

logger = get_logger("opencode_harness", level=logging.DEBUG if not os.getenv("CI") else logging.INFO)

_is_local = not os.getenv("CI")

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------

DEFAULT_OPENCODE_MODEL = os.getenv("CLIO_OPENCODE_MODEL", "openai/gpt-4.1")
PROVIDER_ID = "openai-compatible"

DEFAULT_BASE_URL = os.getenv("CLIO_OPENCODE_BASE_URL") or llm_settings.base_url()

SESSION_TIMEOUT = int(os.getenv("CLIO_OPENCODE_TIMEOUT", "2700"))
MAX_RETRIES = int(os.getenv("CLIO_OPENCODE_MAX_RETRIES", "1"))
RETRY_DELAY = int(os.getenv("CLIO_OPENCODE_RETRY_DELAY", "5"))


# ---------------------------------------------------------------------------
# Config generation
# ---------------------------------------------------------------------------


def _get_api_key() -> str:
    """Resolve the API key for the OpenCode provider (``CLIO_OPENCODE_API_KEY``, then the shared LLM key)."""
    key = os.environ.get("CLIO_OPENCODE_API_KEY") or llm_settings.api_key()
    if not key:
        raise EnvironmentError(
            "No API key available for the OpenCode provider. Set CLIO_OPENCODE_API_KEY or CLIO_LLM_API_KEY."
        )
    return key


def _provider_model_id(model: str) -> str:
    """Normalize a model selection to the ``<PROVIDER_ID>/<model>`` form.

    Accepts either ``provider/model`` or a bare model id; the provider segment is
    always rewritten to the one configured in opencode.json.
    """
    global_id = model.split("/", 1)[1] if "/" in model else model
    return f"{PROVIDER_ID}/{global_id}"


def _provider_package(base_url: str | None) -> str:
    """AI SDK package for the configured endpoint.

    OpenAI's own API rejects ``max_tokens`` for reasoning models, which the generic compatible package always sends,
    so api.openai.com uses the native OpenAI package; every other gateway uses the compatible one.
    """
    host = (urlparse(base_url).hostname or "").lower() if base_url else ""
    return "@ai-sdk/openai" if host == "api.openai.com" else "@ai-sdk/openai-compatible"


def _provider_error(stderr_text: str | None) -> str | None:
    """The provider's own error message from OpenCode's log, e.g. a 400 for an unsupported parameter."""
    for line in reversed((stderr_text or "").splitlines()):
        if "AI_APICallError" not in line and "APICallError" not in line:
            continue
        match = re.search(r'"statusCode":(\d+)', line)
        message = re.search(r'"data":\{"error":\{"message":"((?:[^"\\]|\\.)*)"', line) \
            or re.search(r'"message":"((?:[^"\\]|\\.)*)"', line)
        if message:
            status = f"HTTP {match.group(1)}: " if match else ""
            return status + message.group(1).replace('\\"', '"')
    return None


def _build_opencode_config(
    model: str,
    mcp_server_cmd: list[str],
    system_message: str,
    custom_agents: list[dict] | None = None,
    crg_repo_dir: str | None = None,
    enable_graph: bool = True,
) -> dict:
    """Build a complete ``opencode.json`` configuration dict."""
    api_key = _get_api_key()

    base_url = DEFAULT_BASE_URL
    provider_model = _provider_model_id(model)

    config: dict[str, Any] = {
        "$schema": "https://opencode.ai/config.json",
        "compaction": {
            "auto": True,
            "prune": True,
            "threshold": 0.65,
            "strategy": "summarize",
        },
        "provider": {
            PROVIDER_ID: {
                "npm": _provider_package(base_url),
                "name": "OpenAI-compatible",
                "options": {
                    "baseURL": base_url,
                    "apiKey": api_key,
                },
                "models": {},
            },
        },
    }

    # Any selected model must be registered under provider.models for
    # OpenCode to accept it, even when it is not one of the known entries.
    model_global_id = provider_model.split("/", 1)[1]
    config["provider"][PROVIDER_ID]["models"].setdefault(
        model_global_id, {"name": model_global_id}
    )

    config["agent"] = {
        "plan": {
            "model": provider_model,
        },
        "build": {
            "model": provider_model,
        },
    }
    # Auto-approve all tool calls in CI/non-interactive mode,
    # but deny doom_loop so OpenCode breaks out of repeated identical
    # tool calls instead of looping indefinitely (the default "allow"
    # string would override the built-in doom_loop guard).
    config["permission"] = {
        "doom_loop": "deny",
        "*": "allow",
    }
    # Disable auto-update in CI
    config["autoupdate"] = False
    # Disable snapshots for perf
    config["snapshot"] = False
    # MCP server for Clio tools
    config["mcp"] = {
        "clio-tools": {
            "type": "local",
            "command": mcp_server_cmd,
            "enabled": True,
        },
    }
    # Inject system message as instructions
    config["instructions"] = [system_message]
    # Plugin list (empty)
    config["plugin"] = []

    # Add code-review-graph MCP server when a graph exists in the cloned repo
    if enable_graph and crg_repo_dir and os.path.isdir(os.path.join(crg_repo_dir, ".code-review-graph")):
        config["mcp"]["code-review-graph"] = {
            "type": "local",
            "command": [
                "code-review-graph",
                "serve",
                "--repo", crg_repo_dir,
                "--tools",
                (
                    "query_graph_tool,semantic_search_nodes_tool,"
                    "detect_changes_tool,get_review_context_tool,"
                    "get_impact_radius_tool,get_affected_flows_tool,"
                    "get_knowledge_gaps_tool,get_surprising_connections_tool,"
                    "get_hub_nodes_tool,get_bridge_nodes_tool,"
                    "find_large_functions_tool,get_suggested_questions_tool,"
                    "get_architecture_overview_tool,traverse_graph_tool"
                ),
            ],
            "enabled": True,
        }
        logger.info("code-review-graph MCP server will be exposed for %s", crg_repo_dir)

    # Map custom agents to OpenCode agent config
    if custom_agents:
        _RESERVED_AGENT_NAMES = {"plan", "build"}
        for agent in custom_agents:
            name = agent.get("name", "agent")
            if name in _RESERVED_AGENT_NAMES:
                raise ValueError(
                    f"Custom agent name '{name}' conflicts with a reserved "
                    f"built-in agent name. Reserved names: {sorted(_RESERVED_AGENT_NAMES)}"
                )
            config["agent"][name] = {
                "description": agent.get("description", agent.get("display_name", name)),
                "prompt": agent.get("prompt", ""),
                "mode": agent.get("mode", "subagent"),
                "permission": agent.get("permission", {
                    "*": "allow",
                    "edit": "deny",
                    "bash": "deny",
                    "task": "deny",
                    "clio-tools_publish_review_context": "deny",
                    "clio-tools_get_specialist_findings": "deny",
                    "clio-tools_write_review_findings": "deny",
                }),
            }
            if "model" in agent:
                config["agent"][name]["model"] = agent["model"]
            if "steps" in agent:
                config["agent"][name]["steps"] = agent["steps"]

    return config


# ---------------------------------------------------------------------------
# Harness class
# ---------------------------------------------------------------------------


def _event_text(line: str, usage: list[int], errors: list[str]) -> str | None:
    """Return what ``opencode run`` would print for one ``--format json`` line.

    Token counts are added to ``usage`` as ``[input, output, cache_read]`` (cache
    reads are a subset of input) and session
    errors to ``errors``. Non-JSON lines (e.g. provider status logs) pass through.
    """
    try:
        event = json.loads(line)
    except ValueError:
        return line
    if not isinstance(event, dict):
        return line
    part = event.get("part") or {}
    if event.get("type") == "text":
        return (part.get("text") or "").strip()
    if event.get("type") == "step_finish":
        tokens = part.get("tokens") or {}
        cache = tokens.get("cache") or {}
        # OpenCode splits cached prompt and reasoning tokens out; fold them in to match Copilot's totals.
        usage[0] += int(tokens.get("input") or 0) + int(cache.get("read") or 0) + int(cache.get("write") or 0)
        usage[1] += int(tokens.get("output") or 0) + int(tokens.get("reasoning") or 0)
        usage[2] += int(cache.get("read") or 0)
    elif event.get("type") == "error":
        error = event.get("error") or {}
        data = error.get("data") if isinstance(error, dict) else None
        errors.append(str(data.get("message") if isinstance(data, dict) and data.get("message")
                          else error.get("name") if isinstance(error, dict) else error))
    return None


class OpenCodeHarness(AgenticHarness):
    """OpenCode CLI harness — runs agentic sessions via ``opencode run``."""

    def run_session(
        self,
        prompt: str,
        tools: list,
        system_message: str,
        model: str,
        working_directory: str | None = None,
        custom_agents: list[dict] | None = None,
        enable_graph_tools: bool = True,
    ) -> str:
        # Verify opencode is installed
        opencode_bin = shutil.which("opencode")
        if opencode_bin is None:
            raise EnvironmentError(
                "The 'opencode' CLI is not installed or not on PATH. "
                "Install it with: npm install -g opencode-ai"
            )

        # Map model name for OpenCode.
        # The caller may pass a Copilot-native model (e.g. "gpt-6.1-sol")
        # that does not exist in the configured provider.  Use CLIO_OPENCODE_MODEL
        # or the built-in default instead; only honour the caller's value when
        # it already looks like a provider model ("provider/model").
        if os.getenv("CLIO_OPENCODE_MODEL"):
            oc_model = os.environ["CLIO_OPENCODE_MODEL"]
        elif "/" in model:
            oc_model = model
        else:
            oc_model = DEFAULT_OPENCODE_MODEL

        # Set up MCP tool bridge
        bridge = MCPToolBridge(tools)
        try:
            mcp_cmd = bridge.server_command()

            # Create temp directory for this session
            session_dir = tempfile.mkdtemp(prefix="clio_opencode_")
            try:
                # Write system message as AGENTS.md
                agents_md_path = os.path.join(
                    working_directory or session_dir, "AGENTS.md"
                )
                agents_md_existed = os.path.exists(agents_md_path)
                if not agents_md_existed:
                    with open(agents_md_path, "w") as f:
                        f.write(system_message)

                # Detect code-review-graph in the cloned repo and wire it up
                crg_repo_dir: str | None = None
                if (
                    working_directory
                    and enable_graph_tools
                    and crg_available()
                    and os.path.isdir(os.path.join(working_directory, ".code-review-graph"))
                ):
                    crg_repo_dir = working_directory
                    logger.info("code-review-graph detected in working directory — enabling MCP tools")

                # Generate opencode.json
                config = _build_opencode_config(
                    model=oc_model,
                    mcp_server_cmd=mcp_cmd,
                    system_message=system_message,
                    custom_agents=custom_agents,
                    crg_repo_dir=crg_repo_dir,
                    enable_graph=enable_graph_tools,
                )
                config_path = os.path.join(session_dir, "opencode.json")
                with open(config_path, "w") as f:
                    json.dump(config, f, indent=2)

                # Log config (redact API key)
                safe_config = json.loads(json.dumps(config))
                try:
                    safe_config["provider"][PROVIDER_ID]["options"]["apiKey"] = "***"
                except KeyError:
                    pass
                logger.info(f"OpenCode config written to {config_path}")
                logger.debug(f"OpenCode config: {json.dumps(safe_config, indent=2)}")
                logger.info(f"OpenCode model: {oc_model}")
                logger.debug(f"MCP bridge command: {mcp_cmd}")
                if custom_agents:
                    logger.info(
                        f"Custom agents: {[a.get('name', '?') for a in custom_agents]}"
                    )

                # Build environment
                env = os.environ.copy()
                env["OPENCODE_CONFIG"] = config_path
                # Ensure non-interactive mode
                env["CI"] = "true"

                cwd = working_directory or session_dir

                # Retry loop — retries on transient API errors (e.g. malformed
                # responses from the LLM provider) before giving up.
                last_error: Exception | None = None
                for attempt in range(1, MAX_RETRIES + 1):
                    try:
                        response = self._run_opencode_process(
                            opencode_bin=opencode_bin,
                            prompt=prompt,
                            cwd=cwd,
                            env=env,
                            bridge=bridge,
                            model=oc_model,
                        )
                        return response
                    except RuntimeError as e:
                        last_error = e
                        error_msg = str(e)
                        # Only retry on transient/API errors, not on legitimate
                        # session failures (e.g. tool errors, logic issues).
                        is_retryable = (
                            "no output" in error_msg
                            or "Type validation failed" in error_msg
                            or "exit code" in error_msg
                            or "event disconnected" in error_msg
                            or "stream closed" in error_msg
                            or "connection closed" in error_msg
                        )
                        if not is_retryable or attempt == MAX_RETRIES:
                            break
                        logger.warning(
                            f"OpenCode attempt {attempt}/{MAX_RETRIES} failed: "
                            f"{error_msg[:200]}. Retrying in {RETRY_DELAY}s..."
                        )
                        time.sleep(RETRY_DELAY)

                # All retries exhausted
                raise last_error  # type: ignore[misc]

            finally:
                # Clean up session directory and AGENTS.md
                if not agents_md_existed and os.path.exists(agents_md_path):
                    try:
                        os.unlink(agents_md_path)
                    except OSError:
                        pass
                shutil.rmtree(session_dir, ignore_errors=True)
        finally:
            bridge.cleanup()

    def _run_opencode_process(
        self,
        opencode_bin: str,
        prompt: str,
        cwd: str,
        env: dict,
        bridge: MCPToolBridge,
        model: str | None = None,
    ) -> str:
        """Execute a single OpenCode subprocess and return its output.

        Raises RuntimeError on failure (non-zero exit or empty output).
        """
        # Start the IPC polling thread for tool calls
        stop_event = threading.Event()
        poll_thread = threading.Thread(
            target=self._poll_tool_calls,
            args=(bridge, stop_event),
            daemon=True,
        )
        poll_thread.start()

        # Run opencode with real-time output streaming
        start_time = time.time()
        # JSON events carry per-step token usage; text events rebuild the plain output.
        cmd = [opencode_bin, "run", "--dir", cwd, "--format", "json"]
        if _is_local:
            cmd.extend(["--print-logs", "--log-level", "DEBUG"])
        logger.info("=== OPENCODE SESSION START ===")
        logger.info(f"Working dir: {cwd}")
        logger.info(f"Prompt length: {len(prompt)} chars")
        logger.debug(f"Command: {cmd}")

        proc = subprocess.Popen(
            cmd,
            cwd=cwd,
            env=env,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        def _stream_stdin():
            assert proc.stdin is not None
            with suppress(OSError, ValueError):
                proc.stdin.write(prompt)
            with suppress(OSError, ValueError):
                proc.stdin.close()

        stdin_thread = threading.Thread(target=_stream_stdin, daemon=True)
        stdin_thread.start()

        # Stream stderr in a background thread so we don't block on stdout
        stderr_lines: list[str] = []

        def _stream_stderr():
            assert proc.stderr is not None
            for line in proc.stderr:
                line = line.rstrip("\n")
                if line:
                    stderr_lines.append(line)
                    if not line.lstrip().startswith("timestamp="):
                        logger.debug(f"[OpenCode action] {line}")

        stderr_thread = threading.Thread(target=_stream_stderr, daemon=True)
        stderr_thread.start()

        # Stream stdout line-by-line with timeout to prevent hanging
        stdout_chunks: list[str] = []
        usage = [0, 0, 0]
        event_errors: list[str] = []
        assert proc.stdout is not None

        def _consume(line: str):
            logger.debug(f"[OpenCode stdout] {line}")
            text = _event_text(line, usage, event_errors)
            if text:
                stdout_chunks.append(text)

        deadline = start_time + SESSION_TIMEOUT
        while True:
            remaining = deadline - time.time()
            if remaining <= 0:
                logger.warning("Stdout reading timed out, killing process")
                proc.kill()
                break
            ready, _, _ = select.select([proc.stdout], [], [], min(remaining, 1.0))
            if ready:
                line = proc.stdout.readline()
                if not line:
                    break
                _consume(line.rstrip("\n"))
            elif proc.poll() is not None:
                # Process exited, drain remaining stdout
                for line in proc.stdout:
                    _consume(line.rstrip("\n"))
                break

        # Wait for process and stderr thread
        proc.wait(timeout=SESSION_TIMEOUT)
        stdin_thread.join(timeout=10)
        stderr_thread.join(timeout=10)
        record_usage(model, *usage)

        elapsed = time.time() - start_time

        # Stop IPC polling
        stop_event.set()
        poll_thread.join(timeout=5)

        stderr_text = "\n".join(event_errors + stderr_lines)

        if proc.returncode != 0:
            logger.error(
                f"OpenCode exited with code {proc.returncode} "
                f"after {elapsed:.2f}s"
            )
            raise RuntimeError(
                f"OpenCode session failed (exit code {proc.returncode}): "
                f"{stderr_text[:500]}"
            )

        response = "\n".join(stdout_chunks).strip()
        logger.info(f"OpenCode session completed in {elapsed:.2f}s")
        logger.info(f"Response length: {len(response)} chars")
        logger.info("=== OPENCODE SESSION END ===")

        # Detect OpenCode SSE disconnect / provider error messages that are
        # written to stdout instead of real agent output.  These look like:
        #   "[HH:MM:SS.mmm] INFO (#NNN): event disconnected"
        #   "[HH:MM:SS.mmm] ERROR (#NNN): ..."
        # When the ENTIRE response is one of these status lines (no tool calls
        # were made), treat it as a retryable failure so the retry loop can
        # re-establish the connection.
        _OC_STATUS_PATTERNS = (
            "event disconnected",
            "event error",
            "provider error",
            "connection closed",
            "stream closed",
        )
        if response and bridge.output_tools_called == 0:
            response_lower = response.lower()
            if any(p in response_lower for p in _OC_STATUS_PATTERNS):
                logger.warning(
                    f"OpenCode returned a provider status message instead of "
                    f"real output ('{response[:80]}'). Treating as retryable failure."
                )
                raise RuntimeError(
                    f"OpenCode session returned no output (event disconnected). "
                    f"Response was: {response[:200]}"
                )

        if not response:
            # If the agent successfully invoked an output-producing tool
            # (e.g. write_pr_description) but produced no stdout, that is a
            # legitimate success — the final deliverable was committed via MCP.
            if bridge.output_tools_called > 0:
                logger.info(
                    f"OpenCode returned no stdout but {bridge.output_tools_called} "
                    f"output tool call(s) succeeded — treating as success"
                )
                return ""
            # Otherwise, genuinely empty stdout is a failure
            provider_error = _provider_error(stderr_text)
            if provider_error:
                raise RuntimeError(f"OpenCode session failed: the model provider rejected the request ({provider_error})")
            context = stderr_text[-300:] if stderr_text else "(no stderr)"
            raise RuntimeError(
                f"OpenCode session returned no output (possible timeout). "
                f"Last stderr: {context}"
            )

        return response

    @staticmethod
    def _poll_tool_calls(bridge: MCPToolBridge, stop_event: threading.Event):
        """Background thread that processes IPC tool calls from the MCP server."""
        while not stop_event.is_set():
            try:
                bridge.process_tool_calls()
            except Exception as e:
                logger.error(f"Tool call processing error: {e}")
            stop_event.wait(0.1)
