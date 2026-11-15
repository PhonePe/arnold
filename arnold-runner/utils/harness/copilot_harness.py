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

"""Copilot SDK harness — extracted from the original ``utils/call_llm.py``.

This module encapsulates all GitHub Copilot SDK specifics (CopilotClient,
session creation, event streaming, permission handling) behind the
:class:`AgenticHarness` interface.
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timedelta, timezone
import logging
import os
import threading
from typing import Any
import uuid

from utils.harness.base import AgenticHarness
from utils.harness.common import crg_available, record_usage
from utils.logger import get_logger

# Detect local vs CI environment
_is_local = not os.getenv("CI")
logger = get_logger(
    "copilot_harness",
    level=logging.DEBUG if _is_local else logging.INFO,
)

# Default model
DEFAULT_COPILOT_MODEL = os.getenv("CLIO_LLM_MODEL", "gpt-6.1-sol")


# ---------------------------------------------------------------------------
# Singleton event loop (reused across calls)
# ---------------------------------------------------------------------------

_loop: asyncio.AbstractEventLoop | None = None
_loop_thread: threading.Thread | None = None
_loop_lock = threading.Lock()
# Concurrent callers share one loop and client; serialize client creation/start.
_client_lock = asyncio.Lock()


def _get_event_loop() -> asyncio.AbstractEventLoop:
    global _loop, _loop_thread
    with _loop_lock:
        if _loop is None or _loop.is_closed():
            _loop = asyncio.new_event_loop()
            _loop_thread = threading.Thread(target=_loop.run_forever, daemon=True)
            _loop_thread.start()
        return _loop


def _run_async(coro):
    loop = _get_event_loop()
    future = asyncio.run_coroutine_threadsafe(coro, loop)
    return future.result()


# ---------------------------------------------------------------------------
# Copilot SDK client singleton
# ---------------------------------------------------------------------------

_copilot_sdk_client = None
_copilot_sdk_started = False
_copilot_sdk_arnold_auth = False
_sdk_event_parser_patched = False


def _normalize_sdk_event_dict(event_dict):
    """Fill fields omitted by ephemeral pending-message notifications."""
    if event_dict.get("type") == "pending_messages.modified":
        event_dict = dict(event_dict)
        event_dict.setdefault("id", str(uuid.uuid4()))
        event_dict.setdefault("timestamp", datetime.now(timezone.utc).isoformat())
    return event_dict


def _patch_sdk_event_parser():
    """Work around SDK parsing failures for id-less ephemeral events."""
    global _sdk_event_parser_patched
    if _sdk_event_parser_patched:
        return

    import copilot.client as client_module

    original = client_module.session_event_from_dict

    def parse_event(event_dict):
        return original(_normalize_sdk_event_dict(event_dict))

    client_module.session_event_from_dict = parse_event
    _sdk_event_parser_patched = True


def _approve_all_permissions(request, invocation):
    logger.debug("Permission request for tool=%s", getattr(request, "tool_name", "?"))
    from copilot.generated.rpc import PermissionDecisionApproveOnce
    return PermissionDecisionApproveOnce()


# ---------------------------------------------------------------------------
# Core async session runner
# ---------------------------------------------------------------------------


def _duration_ms(duration) -> float:
    """SDK 1.x reports durations as ``timedelta``; older versions sent milliseconds."""
    if isinstance(duration, timedelta):
        return duration.total_seconds() * 1000
    return float(duration or 0)


_CORE_BUILTIN_TOOLS = ["bash", "read_bash", "write_bash", "stop_bash", "list_bash", "view", "grep", "glob",
                       "create", "edit"]
_SUBAGENT_BUILTIN_TOOLS = ["task", "read_agent", "write_agent", "list_agents", "skill"]


def _session_tool_allowlist(with_subagents: bool):
    """Tools the model sees: core built-ins plus every Clio custom tool (output and graph tools).

    Every tool definition is resent on each turn, so this drops runtime extras (MCP servers such as the
    default GitHub one, sql, web_fetch) and the sub-agent tools when no custom agents are registered.
    """
    from copilot import ToolSet
    builtins = _CORE_BUILTIN_TOOLS + (_SUBAGENT_BUILTIN_TOOLS if with_subagents else [])
    return ToolSet().add_builtin(builtins).add_custom("*")


async def _call_copilot_sdk_agentic(
    prompt: str,
    system_message: str,
    model: str,
    tools: list,
    working_directory: str | None = None,
    custom_agents: list[dict] | None = None,
    enable_graph_tools: bool = True,
) -> str:
    from utils.harness.graph_mcp_client import make_graph_tools

    global _copilot_sdk_client, _copilot_sdk_started, _copilot_sdk_arnold_auth
    from copilot import CopilotClient
    _patch_sdk_event_parser()

    async with _client_lock:
        arnold_auth = bool(os.environ.get("ARNOLD_COPILOT_KEY"))
        if _copilot_sdk_client is not None and arnold_auth != _copilot_sdk_arnold_auth:
            raise RuntimeError("Copilot authentication mode cannot change within a worker")
        if _copilot_sdk_client is None and arnold_auth:
            child_env = {key: value for key, value in os.environ.items() if key not in {
                "ARNOLD_COPILOT_KEY", "GITHUB_TOKEN", "GH_TOKEN", "COPILOT_GITHUB_TOKEN", "COPILOT_SDK_AUTH_TOKEN",
            }}
            child_env["COPILOT_DISABLE_KEYTAR"] = "1"
            _copilot_sdk_client = CopilotClient(use_logged_in_user=False, env=child_env, log_level="info")
            _copilot_sdk_arnold_auth = True
            _copilot_sdk_started = False

        if _copilot_sdk_client is None:
            github_token = os.environ.get("GITHUB_TOKEN")
            if not github_token:
                raise EnvironmentError(
                    "The GITHUB_TOKEN environment variable must be set. "
                    "Provide a GitHub PAT with the 'copilot' scope."
                )
            # github-copilot-sdk >=0.3.0 no longer exports SubprocessConfig.
            # Try new-style init first, then fall back to legacy-style.
            try:
                _copilot_sdk_client = CopilotClient(
                    github_token=github_token,
                    log_level="debug" if _is_local else "info",
                )
            except TypeError:
                try:
                    from copilot import SubprocessConfig
                    _copilot_sdk_client = CopilotClient(
                        SubprocessConfig(github_token=github_token)
                    )
                except ImportError:
                    _copilot_sdk_client = CopilotClient()
            _copilot_sdk_started = False

        if not _copilot_sdk_started:
            await _copilot_sdk_client.start()
            _copilot_sdk_started = True
            if _is_local:
                logger.info("Copilot SDK client started")

    if working_directory:
        logger.info(f"Agentic session working_directory: {working_directory}")

    # Resolve native Copilot tools from metadata wrappers if needed
    native_tools = []
    for t in tools:
        meta = getattr(t, "_clio_metadata", None) or getattr(t, "metadata", None)
        if meta and getattr(meta, "copilot_tool", None) is not None:
            native_tools.append(meta.copilot_tool)
        elif meta and getattr(meta, "handler", None) is not None:
            # Tool was defined under a non-Copilot harness (e.g. opencode).
            # Re-create the native Copilot tool from metadata so fallback works.
            from copilot import define_tool as _copilot_define_tool

            copilot_tool = _copilot_define_tool(
                description=meta.description,
                is_terminal=getattr(meta, "ends_session", False),
            )(
                meta.handler
            )
            native_tools.append(copilot_tool)
        else:
            native_tools.append(t)

    # --- Wire up code-review-graph tools for Copilot SDK ---
    graph_client = None
    graph_tool_names: list[str] = []
    if working_directory and enable_graph_tools and crg_available():
        graph_tools, graph_client = make_graph_tools(working_directory)
        if graph_tools:
            native_tools.extend(graph_tools)
            graph_tool_names = [t.name for t in graph_tools]
            logger.info(
                "Registered %d graph tool(s) for Copilot session",
                len(graph_tools),
            )

    session_kwargs = dict(
        model=model,
        system_message={"mode": "replace", "content": system_message},
        tools=native_tools,
        on_permission_request=_approve_all_permissions,
        working_directory=working_directory,
        include_sub_agent_streaming_events=True,
    )
    if arnold_auth:
        from utils.harness.copilot_credentials import execution_token
        # TODO (ponytail): No mid-session refresh. Restore the provider with SDK >=1.0.15/runtime >=1.0.89,
        # accepting https://github.com and verifying real initial/refresh calls after CI permits the upgrade.
        credential = await asyncio.to_thread(execution_token)
        session_kwargs["github_token"] = credential["accessToken"]
    if custom_agents:
        # Agents with an explicit tool list get the graph tools too; their names are only known here.
        custom_agents = [
            {**agent, "tools": [*agent["tools"], *graph_tool_names]}
            if agent.get("tools") is not None else agent
            for agent in custom_agents
        ]
        session_kwargs["custom_agents"] = custom_agents
        skills_dir = (
            os.path.join(working_directory, ".github", "skills")
            if working_directory else None
        )
        if (
            skills_dir
            and os.path.isdir(skills_dir)
            and any(agent.get("skills") for agent in custom_agents)
        ):
            session_kwargs["enable_skills"] = True
            session_kwargs["skill_directories"] = [skills_dir]
            logger.info("Copilot skill directory: %s", skills_dir)
        logger.info(
            f"Custom agents registered: "
            f"{[a.get('name', '?') for a in custom_agents]}"
        )
    session_kwargs["available_tools"] = _session_tool_allowlist(bool(custom_agents))

    session = await _copilot_sdk_client.create_session(**session_kwargs)

    # ---- Stream SDK events to logger ----
    from copilot.generated.session_events import SessionEventType

    usage_stats: dict[str, Any] = {
        "turns": 0,
        "input_tokens": 0.0,
        "output_tokens": 0.0,
        "duration_ms": 0.0,
        "models": {},
        "tool_calls": 0,
        "tool_names": {},
        "subagents_completed": 0,
        "subagents_failed": 0,
    }
    session_errors: list[str] = []

    def _on_session_event(event):
        etype = event.type
        data = event.data
        scope = getattr(event, "agent_id", None) or "main"
        try:
            if etype == SessionEventType.ASSISTANT_TURN_START:
                logger.info("[SDK][agent=%s] ── Turn started ──", scope)
            elif etype == SessionEventType.SESSION_USAGE_INFO and not usage_stats.get("baseline_logged"):
                usage_stats["baseline_logged"] = True
                logger.info(
                    "[SDK] Context baseline: system=%s tool_definitions=%s conversation=%s total=%s",
                    data.system_tokens,
                    data.tool_definitions_tokens,
                    data.conversation_tokens,
                    data.current_tokens,
                )
            elif etype == SessionEventType.ASSISTANT_INTENT:
                logger.info("[SDK][agent=%s] Intent: %s", scope, data.intent)
            elif etype == SessionEventType.SUBAGENT_SELECTED:
                logger.info(
                    "[SDK] Subagent selected: name=%s tools=%s",
                    data.agent_name,
                    data.tools,
                )
            elif etype == SessionEventType.SUBAGENT_STARTED:
                logger.info(
                    "[SDK] Subagent started: name=%s model=%s call=%s",
                    data.agent_name,
                    data.model,
                    data.tool_call_id,
                )
            elif etype == SessionEventType.SUBAGENT_COMPLETED:
                usage_stats["subagents_completed"] += 1
                logger.info(
                    "[SDK] Subagent completed: name=%s tokens=%s tools=%s duration=%s",
                    data.agent_name,
                    data.total_tokens,
                    data.total_tool_calls,
                    data.duration,
                )
            elif etype == SessionEventType.SUBAGENT_FAILED:
                usage_stats["subagents_failed"] += 1
                logger.error(
                    "[SDK] Subagent failed: name=%s error=%s",
                    data.agent_name,
                    data.error,
                )
            elif etype == SessionEventType.SKILL_INVOKED:
                logger.info(
                    "[SDK][agent=%s] Skill invoked: name=%s path=%s trigger=%s",
                    scope,
                    data.name,
                    data.path,
                    data.trigger,
                )
            elif etype == SessionEventType.TOOL_EXECUTION_START:
                args_preview = str(data.arguments or "")[:2000]
                usage_stats["tool_calls"] += 1
                usage_stats["tool_names"][data.tool_name] = (
                    usage_stats["tool_names"].get(data.tool_name, 0) + 1
                )
                logger.info(
                    "[SDK][agent=%s] Tool call: %s(%s)",
                    scope,
                    data.tool_name,
                    args_preview,
                )
            elif etype == SessionEventType.TOOL_EXECUTION_COMPLETE:
                result_preview = ""
                if data.result:
                    result_preview = str(getattr(data.result, "content", ""))[:2000]
                logger.info(
                    "[SDK][agent=%s] Tool done: %s → %s",
                    scope,
                    data.tool_name,
                    result_preview,
                )
            elif etype == SessionEventType.ASSISTANT_MESSAGE:
                content_preview = (data.content or "")[:2000]
                logger.info("[SDK][agent=%s] Assistant: %s", scope, content_preview)
            elif etype == SessionEventType.ASSISTANT_TURN_END:
                logger.info("[SDK][agent=%s] ── Turn ended ──", scope)
            elif etype == SessionEventType.ASSISTANT_USAGE:
                model_stats = usage_stats["models"].setdefault(
                    data.model,
                    {
                        "turns": 0,
                        "input_tokens": 0.0,
                        "output_tokens": 0.0,
                        "cache_read_tokens": 0.0,
                        "duration_ms": 0.0,
                    },
                )
                usage_stats["turns"] += 1
                usage_stats["input_tokens"] += float(data.input_tokens or 0)
                usage_stats["output_tokens"] += float(data.output_tokens or 0)
                usage_stats["duration_ms"] += _duration_ms(data.duration)
                model_stats["turns"] += 1
                model_stats["input_tokens"] += float(data.input_tokens or 0)
                model_stats["output_tokens"] += float(data.output_tokens or 0)
                model_stats["cache_read_tokens"] += float(data.cache_read_tokens or 0)
                model_stats["duration_ms"] += _duration_ms(data.duration)
                logger.info(
                    f"[SDK] Usage: model={data.model}, "
                    f"input={data.input_tokens}, cached={data.cache_read_tokens}, "
                    f"output={data.output_tokens}, "
                    f"duration={data.duration}ms"
                )
            elif etype == SessionEventType.SESSION_ERROR:
                error_msg = f"{data.message} ({data.error_type})"
                logger.error(f"[SDK] Error: {error_msg}")
                session_errors.append(error_msg)
            elif etype == SessionEventType.SESSION_WARNING:
                logger.warning(f"[SDK] Warning: {data.message}")
        except Exception:
            pass

    # Keep bounded tool and assistant event logging in CI as well as locally.
    # This is essential for diagnosing sessions that return without invoking a
    # required terminal tool.
    session.on(_on_session_event)

    try:
        response = await session.send_and_wait(prompt, timeout=1200)
    finally:
        for model_name, model_stats in usage_stats["models"].items():
            record_usage(model_name, model_stats["input_tokens"], model_stats["output_tokens"],
                         model_stats["cache_read_tokens"])
    content = response.data.content if response and response.data else None

    # Check history for errors
    try:
        for event in await session.get_messages():
            if event.type == SessionEventType.SESSION_ERROR:
                error_msg = f"{event.data.message} ({event.data.error_type})"
                if error_msg not in session_errors:
                    logger.error(f"[SDK] Error (from history): {error_msg}")
                    session_errors.append(error_msg)
    except Exception:
        pass

    # CI metrics from SESSION_SHUTDOWN
    if not _is_local:
        try:
            for event in await session.get_messages():
                if event.type == SessionEventType.SESSION_SHUTDOWN:
                    d = event.data
                    model_metrics = d.model_metrics or {}
                    total_input = sum(
                        m.usage.input_tokens for m in model_metrics.values()
                    )
                    total_output = sum(
                        m.usage.output_tokens for m in model_metrics.values()
                    )
                    logger.info("[SDK] === Session Summary ===")
                    logger.info(
                        "[SDK] Total usage: input_tokens=%d, output_tokens=%d, "
                        "total_tokens=%d, llm_time=%.2fs",
                        int(total_input),
                        int(total_output),
                        int(total_input + total_output),
                        (d.total_api_duration_ms or 0) / 1000,
                    )
                    logger.info(
                        "[SDK] Estimated premium requests: %d session(s)",
                        int(d.total_premium_requests or 0),
                    )
                    for model_name, m in sorted(model_metrics.items()):
                        logger.info(
                            "[SDK] Model breakdown: model=%s, requests=%d, "
                            "input_tokens=%d, output_tokens=%d",
                            model_name,
                            int(m.requests.count),
                            int(m.usage.input_tokens),
                            int(m.usage.output_tokens),
                        )
                    break
        except Exception:
            pass
    else:
        total_tokens = int(
            usage_stats["input_tokens"] + usage_stats["output_tokens"]
        )
        logger.info("[SDK] === Session Summary ===")
        logger.info(
            "[SDK] Total usage: turns=%d, input_tokens=%d, output_tokens=%d, "
            "total_tokens=%d, llm_time=%.2fs",
            usage_stats["turns"],
            int(usage_stats["input_tokens"]),
            int(usage_stats["output_tokens"]),
            total_tokens,
            usage_stats["duration_ms"] / 1000,
        )
        logger.info("[SDK] Estimated premium requests: 1 session(s)")
        for model_name, model_stats in sorted(usage_stats["models"].items()):
            logger.info(
                "[SDK] Model breakdown: model=%s, turns=%d, input_tokens=%d, "
                "output_tokens=%d, llm_time=%.2fs",
                model_name,
                model_stats["turns"],
                int(model_stats["input_tokens"]),
                int(model_stats["output_tokens"]),
                model_stats["duration_ms"] / 1000,
            )
        logger.info(
            "[SDK] Tool activity: total_calls=%d, unique_tools=%d",
            usage_stats["tool_calls"],
            len(usage_stats["tool_names"]),
        )
        logger.info(
            "[SDK] Subagent activity: completed=%d, failed=%d",
            usage_stats["subagents_completed"],
            usage_stats["subagents_failed"],
        )
        for tool_name, tool_count in sorted(
            usage_stats["tool_names"].items(),
            key=lambda item: (-item[1], item[0]),
        ):
            logger.info(
                "[SDK] Tool breakdown: tool=%s, calls=%d", tool_name, tool_count
            )

    try:
        await session.disconnect()
    except AttributeError:
        # Fallback for older SDK versions (< 1.0.0)
        try:
            await session.destroy()
        except AttributeError:
            pass

    # Always terminate the code-review-graph subprocess when done
    if graph_client is not None:
        try:
            graph_client.stop()
        except Exception as exc:
            logger.warning("Error stopping graph client: %s", exc)

    if session_errors:
        raise RuntimeError(
            f"Copilot SDK session encountered {len(session_errors)} error(s): "
            + "; ".join(session_errors)
        )

    if content is None:
        raise RuntimeError(
            "Copilot SDK agentic session returned no content (possible timeout)"
        )
    return content


# ---------------------------------------------------------------------------
# Harness class
# ---------------------------------------------------------------------------


class CopilotHarness(AgenticHarness):
    """GitHub Copilot SDK harness."""

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
        return _run_async(
            _call_copilot_sdk_agentic(
                prompt,
                system_message,
                model,
                tools,
                working_directory=working_directory,
                custom_agents=custom_agents,
                enable_graph_tools=enable_graph_tools,
            )
        )
