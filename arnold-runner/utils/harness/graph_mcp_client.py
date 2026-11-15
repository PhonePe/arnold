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

"""MCP client for code-review-graph — spawns ``code-review-graph serve`` and
exposes its tools so they can be wrapped with Copilot SDK-style ``define_tool``
decorators.

The client speaks NDJSON (newline-delimited JSON) over stdio, the same
protocol used by OpenCode's MCP transport.
"""

from __future__ import annotations

import json
import logging
import os
import select
import shutil
import subprocess
import sys
import time
import uuid
from typing import Any

from utils.logger import get_logger

logger = get_logger("graph_mcp_client")

# Tools we want to expose from code-review-graph
DEFAULT_TOOLS = (
    # Context & Impact
    "query_graph_tool",
    "semantic_search_nodes_tool",
    "detect_changes_tool",
    "get_review_context_tool",
    "get_impact_radius_tool",
    "get_affected_flows_tool",
    # Structural Robustness (new)
    "get_knowledge_gaps_tool",
    "get_surprising_connections_tool",
    "get_hub_nodes_tool",
    "get_bridge_nodes_tool",
    "find_large_functions_tool",
    "get_suggested_questions_tool",
    "get_architecture_overview_tool",
    "traverse_graph_tool",
)


class GraphMCPClient:
    """Manages the ``code-review-graph serve`` subprocess and exposes tools."""

    def __init__(self, repo_dir: str, tools: tuple[str, ...] | None = None):
        self.repo_dir = repo_dir
        self.tools = tools or DEFAULT_TOOLS
        self._proc: subprocess.Popen | None = None
        self._tool_specs: list[dict] = []

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    def start(self) -> bool:
        """Spawn the subprocess, initialize MCP, and cache tool list.

        Returns True on success, False if code-review-graph is missing or
        the server failed to start.
        """
        if shutil.which("code-review-graph") is None:
            logger.warning("code-review-graph CLI not found on PATH")
            return False

        if not os.path.isdir(os.path.join(self.repo_dir, ".code-review-graph")):
            logger.info("No .code-review-graph directory found in %s", self.repo_dir)
            return False

        cmd = [
            "code-review-graph",
            "serve",
            "--repo", self.repo_dir,
            "--tools", ",".join(self.tools),
        ]

        try:
            self._proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                stdin=subprocess.PIPE,
                text=True,
                bufsize=1,  # line buffered
            )
        except OSError as exc:
            logger.error("Failed to start code-review-graph serve: %s", exc)
            return False

        # --- MCP handshake ---
        init_req = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": {"name": "clio", "version": "1.0.0"},
            },
        }
        if not self._send(init_req):
            self.stop()
            return False

        init_resp = self._read(timeout=10)
        if init_resp is None or "result" not in init_resp:
            logger.error("code-review-graph did not respond to initialize")
            self.stop()
            return False

        # notifications/initialized (no response needed)
        self._send({"jsonrpc": "2.0", "method": "notifications/initialized"})

        # --- Fetch tool list ---
        list_req = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "tools/list",
        }
        if not self._send(list_req):
            self.stop()
            return False

        list_resp = self._read(timeout=10)
        if list_resp is None:
            logger.error("code-review-graph did not respond to tools/list")
            self.stop()
            return False

        result = list_resp.get("result", {})
        self._tool_specs = result.get("tools", [])
        logger.info(
            "code-review-graph MCP ready with %d tool(s)",
            len(self._tool_specs),
        )
        return True

    def stop(self) -> None:
        """Terminate the subprocess if it is alive."""
        if self._proc is None:
            return
        try:
            self._proc.stdin.close()
        except OSError:
            pass
        try:
            self._proc.terminate()
            self._proc.wait(timeout=5)
        except Exception:
            try:
                self._proc.kill()
            except Exception:
                pass
        finally:
            self._proc = None
            self._tool_specs = []

    # ------------------------------------------------------------------
    # Public helpers
    # ------------------------------------------------------------------

    @property
    def is_running(self) -> bool:
        return self._proc is not None and self._proc.poll() is None

    def tool_specs(self) -> list[dict]:
        """Return tool schemas (name, description, inputSchema …)."""
        return list(self._tool_specs)

    def call_tool(self, name: str, arguments: dict) -> str:
        """Call a tool by name and return its text result.

        Raises RuntimeError on timeout or error response.
        """
        if not self.is_running:
            raise RuntimeError("GraphMCPClient is not running")

        req = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "tools/call",
            "params": {"name": name, "arguments": arguments},
        }
        if not self._send(req):
            raise RuntimeError("Failed to send tool call to code-review-graph")

        resp = self._read(timeout=60)
        if resp is None:
            raise RuntimeError("Timeout waiting for code-review-graph tool response")

        if "error" in resp:
            err = resp["error"]
            raise RuntimeError(f"code-review-graph error: {err.get('message', err)}")

        result = resp.get("result", {})
        if result.get("isError"):
            contents = result.get("content", [])
            text = (
                contents[0].get("text", "")
                if contents and isinstance(contents, list)
                else ""
            )
            raise RuntimeError(f"code-review-graph tool error: {text}")

        contents = result.get("content", [])
        if contents and isinstance(contents, list):
            return contents[0].get("text", "")
        return json.dumps(result)

    # ------------------------------------------------------------------
    # NDJSON I/O helpers
    # ------------------------------------------------------------------

    def _send(self, msg: dict) -> bool:
        """Write a single NDJSON line to the subprocess stdin."""
        if self._proc is None or self._proc.stdin is None:
            return False
        try:
            line = json.dumps(msg) + "\n"
            self._proc.stdin.write(line)
            self._proc.stdin.flush()
            return True
        except BrokenPipeError:
            logger.error("Pipe to code-review-graph broke")
            return False
        except OSError as exc:
            logger.error("Write error to code-review-graph: %s", exc)
            return False

    def _read(self, timeout: float = 10.0) -> dict | None:
        """Read a single NDJSON line from stdout with timeout."""
        if self._proc is None or self._proc.stdout is None:
            return None

        deadline = time.time() + timeout
        while time.time() < deadline:
            # Poll for available data
            remaining = deadline - time.time()
            if remaining <= 0:
                break
            ready, _, _ = select.select([self._proc.stdout], [], [], min(remaining, 1.0))
            if not ready:
                # Check if the process died
                if self._proc.poll() is not None:
                    stderr = self._proc.stderr.read() if self._proc.stderr else ""
                    logger.error(
                        "code-review-graph exited (code=%s): %s",
                        self._proc.returncode,
                        stderr[:500],
                    )
                    return None
                continue

            line = self._proc.stdout.readline()
            if not line:
                # EOF
                break
            line = line.strip()
            if line:
                try:
                    return json.loads(line)
                except json.JSONDecodeError as exc:
                    logger.warning("Invalid NDJSON from code-review-graph: %s", exc)
                    continue
            # Empty line — keep polling

        return None  # timeout


# ---------------------------------------------------------------------------
# Convenience: build graph tool callables for Copilot
# ---------------------------------------------------------------------------


def _build_copilot_tool_handler(client: GraphMCPClient, tool_name: str, description: str):
    """Return a callable that forwards Copilot tool calls to GraphMCPClient."""
    def _handler(tool_args: dict) -> str:
        # Some Copilot SDK versions wrap args in a ``params`` key
        arguments = tool_args.get("params", tool_args)
        if isinstance(arguments, str):
            arguments = json.loads(arguments)
        # Additional ad-hoc normalisation
        if not isinstance(arguments, dict):
            arguments = {}
        return client.call_tool(tool_name, arguments)
    _handler.__name__ = tool_name
    _handler.__doc__ = description
    return _handler


def make_graph_tools(repo_dir: str, client: GraphMCPClient | None = None) -> tuple[list, GraphMCPClient | None]:
    """Start ``code-review-graph serve`` and return Copilot-compatible tools.

    The caller is responsible for calling ``client.stop()`` when the session ends.
    If *client* is provided, it is used directly (caller must already call
    ``client.start()``). Otherwise a new client is started and returned.

    Returns ``([], None)`` if the graph is not available or the server fails
    to start.
    """
    own_client = client is None
    if own_client:
        client = GraphMCPClient(repo_dir)
        if not client.start():
            return [], None
    elif not client.is_running:
        if not client.start():
            return [], None

    tools: list = []
    for spec in client.tool_specs():
        name = spec.get("name", "")
        description = spec.get("description", "")
        handler = _build_copilot_tool_handler(client, name, description)

        # Register with Copilot SDK's define_tool directly.
        try:
            from copilot import define_tool as _copilot_define_tool
            copilot_tool = _copilot_define_tool(description=description)(handler)
            # define_tool only derives schemas from Pydantic types; without the MCP
            # inputSchema the model sees a parameterless tool and every call fails.
            copilot_tool.parameters = spec.get("inputSchema") or {"type": "object", "properties": {}}
            tools.append(copilot_tool)
        except Exception as exc:
            logger.warning("Could not register graph tool %s with Copilot: %s", name, exc)

    logger.info("Registered %d graph tool(s) for Copilot session", len(tools))
    return tools, client if own_client else None
