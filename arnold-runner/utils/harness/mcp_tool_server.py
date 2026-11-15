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

"""MCP tool bridge — exposes Clio tools as an MCP stdio server.

This module starts a lightweight MCP server (over stdio) that registers
every Clio tool so OpenCode can call them.  The server is spawned as a
subprocess; its stdin/stdout are wired to OpenCode's MCP transport.

Usage::

    bridge = MCPToolBridge(tools)
    # Returns the command + args to pass to OpenCode's mcp config
    cmd = bridge.server_command()
    # Clean up when done
    bridge.cleanup()
"""

from __future__ import annotations

import json
import logging
import os
import sys
import tempfile
from pathlib import Path
from typing import Any

from utils.logger import get_logger

_is_local = not os.getenv("CI")
logger = get_logger(
    "mcp_tool_bridge",
    level=logging.DEBUG if _is_local else logging.INFO,
)


# Tools whose invocation constitutes a successful session even when stdout is empty,
# in addition to any tool defined with ends_session=True.
# Stored without the MCP namespace prefix (e.g. "clio-tools_") since callers
# may or may not include it — matched against the stripped `lookup_name`.
_OUTPUT_TOOL_NAMES = frozenset({
    "write_pr_description",
    "write_review_summary",
    "write_review_findings",
    "write_filter_results",
    "write_impact_analysis",
    "write_chapter",
    "write_fix_results",
})


def _write_response_atomic(resp_path: str, payload: dict) -> None:
    """Write a JSON response file atomically via a temp file + rename.

    This prevents the MCP server from reading a partially-written file
    (which would throw JSONDecodeError and crash the server process).
    """
    tmp_path = resp_path + ".tmp"
    with open(tmp_path, "w") as f:
        json.dump(payload, f)
    os.replace(tmp_path, resp_path)


def _pydantic_model_to_json_schema(model_cls) -> dict:
    """Convert a Pydantic model to JSON Schema suitable for MCP tool input."""
    if model_cls is None:
        return {"type": "object", "properties": {}}
    schema = model_cls.model_json_schema()
    # Strip Pydantic-internal keys that MCP doesn't need
    schema.pop("title", None)
    return schema


class MCPToolBridge:
    """Manages an MCP server script that exposes Clio tools.

    The bridge generates a Python script that runs an MCP stdio server.
    OpenCode connects to this server via its ``mcp`` config.
    """

    def __init__(self, tools: list):
        self._tools = tools
        self._tmp_dir = tempfile.mkdtemp(prefix="clio_mcp_")
        self._server_script: str | None = None
        self._tool_handlers_script: str | None = None
        self._calls_processed = 0
        # Count only successful calls to tools that produce final output
        self._output_tools_called = 0

    def _extract_metadata(self, tool) -> dict:
        """Pull ToolMetadata from a tool object."""
        meta = getattr(tool, "metadata", None)
        if meta is None:
            raise ValueError(
                f"Tool {tool!r} has no .metadata — was it created with "
                f"utils.tool_adapter.define_tool?"
            )
        return {
            "name": meta.name,
            "description": meta.description,
            "input_schema": _pydantic_model_to_json_schema(meta.params_model),
        }

    def generate_server_script(self) -> str:
        """Generate the MCP server Python script and return its path.

        The script embeds tool schemas and delegates calls back to the
        main Clio process via a simple JSON-over-TCP protocol on a
        randomly assigned port.  However, to keep the first version
        simple, we use a **file-based IPC** approach:

        1.  OpenCode calls a tool → MCP server writes the call to a
            request file and waits.
        2.  The parent process (CopilotHarness replacement) polls for
            request files, invokes the real Python handler, and writes
            the response file.
        3.  The MCP server reads the response and returns it to OpenCode.
        """
        tool_specs = [self._extract_metadata(t) for t in self._tools]

        ipc_dir = os.path.join(self._tmp_dir, "ipc")
        os.makedirs(ipc_dir, exist_ok=True)

        # Dump tool specs for the server script to load
        specs_path = os.path.join(self._tmp_dir, "tool_specs.json")
        with open(specs_path, "w") as f:
            json.dump(tool_specs, f)

        server_script = os.path.join(self._tmp_dir, "mcp_server.py")

        script_content = f'''\
#!/usr/bin/env python3
"""Auto-generated MCP stdio server for Clio tools.

Uses newline-delimited JSON (NDJSON) framing — each message is a single
line of JSON followed by a newline character.  This matches the protocol
used by OpenCode v1.14+.
"""
import json
import os
import sys
import time
import uuid

TOOL_SPECS_PATH = {specs_path!r}
IPC_DIR = {ipc_dir!r}
POLL_INTERVAL = 0.1  # seconds
TIMEOUT = 600  # seconds

with open(TOOL_SPECS_PATH) as f:
    TOOLS = json.load(f)

def read_message():
    """Read one NDJSON line from stdin and parse as JSON-RPC."""
    for line in sys.stdin:
        line = line.strip()
        if line:
            return json.loads(line)
    return None

def write_message(msg):
    """Write a JSON-RPC message as a single NDJSON line to stdout."""
    sys.stdout.write(json.dumps(msg) + "\\n")
    sys.stdout.flush()

def handle_initialize(msg):
    # Echo back the client's requested protocol version for compatibility
    client_version = msg.get("params", {{}}).get("protocolVersion", "2024-11-05")
    return {{
        "jsonrpc": "2.0",
        "id": msg["id"],
        "result": {{
            "protocolVersion": client_version,
            "capabilities": {{"tools": {{}}}},
            "serverInfo": {{"name": "clio-tools", "version": "1.0.0"}},
        }},
    }}

def handle_tools_list(msg):
    tool_list = []
    for spec in TOOLS:
        tool_list.append({{
            "name": spec["name"],
            "description": spec["description"],
            "inputSchema": spec["input_schema"],
        }})
    return {{
        "jsonrpc": "2.0",
        "id": msg["id"],
        "result": {{"tools": tool_list}},
    }}

def handle_tools_call(msg):
    params = msg.get("params", {{}})
    tool_name = params.get("name", "")
    arguments = params.get("arguments", {{}})

    # IPC: write request atomically, wait for response from parent process
    call_id = str(uuid.uuid4())
    request_path = os.path.join(IPC_DIR, f"req_{{call_id}}.json")
    request_tmp = request_path + ".tmp"
    response_path = os.path.join(IPC_DIR, f"resp_{{call_id}}.json")

    # Write request atomically so the parent never reads a partial file
    with open(request_tmp, "w") as f:
        json.dump({{"tool_name": tool_name, "arguments": arguments, "call_id": call_id}}, f)
    os.replace(request_tmp, request_path)

    # Poll for response
    elapsed = 0
    while elapsed < TIMEOUT:
        if os.path.exists(response_path):
            try:
                with open(response_path) as f:
                    result = json.load(f)
                os.unlink(response_path)
                return {{
                    "jsonrpc": "2.0",
                    "id": msg["id"],
                    "result": {{
                        "content": [{{"type": "text", "text": result.get("result", "")}}],
                        "isError": result.get("is_error", False),
                    }},
                }}
            except (json.JSONDecodeError, OSError):
                # File may still be mid-write; retry on next poll tick
                pass
        time.sleep(POLL_INTERVAL)
        elapsed += POLL_INTERVAL

    return {{
        "jsonrpc": "2.0",
        "id": msg["id"],
        "result": {{
            "content": [{{"type": "text", "text": "Timeout waiting for tool response"}}],
            "isError": True,
        }},
    }}

def main():
    while True:
        try:
            msg = read_message()
        except Exception:
            time.sleep(0.5)
            continue
        if msg is None:
            break

        try:
            method = msg.get("method", "")
            if method == "initialize":
                write_message(handle_initialize(msg))
            elif method == "notifications/initialized":
                pass  # notification, no response needed
            elif method == "tools/list":
                write_message(handle_tools_list(msg))
            elif method == "tools/call":
                write_message(handle_tools_call(msg))
            elif method == "ping":
                write_message({{"jsonrpc": "2.0", "id": msg["id"], "result": {{}}}})
            else:
                write_message({{
                    "jsonrpc": "2.0",
                    "id": msg.get("id"),
                    "error": {{"code": -32601, "message": f"Unknown method: {{method}}"}},
                }})
        except Exception as e:
            # Never let an unhandled exception crash the server — return an error response
            try:
                write_message({{
                    "jsonrpc": "2.0",
                    "id": msg.get("id") if isinstance(msg, dict) else None,
                    "error": {{"code": -32603, "message": f"Internal server error: {{e}}"}},
                }})
            except Exception:
                pass

if __name__ == "__main__":
    main()
'''
        with open(server_script, "w") as f:
            f.write(script_content)
        os.chmod(server_script, 0o755)

        self._server_script = server_script
        return server_script

    @property
    def calls_processed(self) -> int:
        """Return the total number of tool calls processed so far."""
        return self._calls_processed

    @property
    def output_tools_called(self) -> int:
        """Return the number of successful calls to output-producing tools."""
        return self._output_tools_called

    @property
    def ipc_dir(self) -> str:
        return os.path.join(self._tmp_dir, "ipc")

    def server_command(self) -> list[str]:
        """Return the command to start the MCP server."""
        if self._server_script is None:
            self.generate_server_script()
        return [sys.executable, self._server_script]

    def process_tool_calls(self, timeout: float = 0.1) -> int:
        """Poll the IPC directory for pending tool calls and handle them.

        Returns the number of calls processed.
        """
        processed = self._do_process_tool_calls(timeout=timeout)
        self._calls_processed += processed
        return processed

    def _do_process_tool_calls(self, timeout: float = 0.1) -> int:
        """Core logic for processing tool calls."""
        ipc_dir = self.ipc_dir
        processed = 0

        if not os.path.isdir(ipc_dir):
            return 0

        for fname in os.listdir(ipc_dir):
            if not fname.startswith("req_") or not fname.endswith(".json"):
                continue

            req_path = os.path.join(ipc_dir, fname)
            try:
                with open(req_path) as f:
                    request = json.load(f)
                # Remove request file immediately to prevent re-processing
                os.unlink(req_path)
            except (json.JSONDecodeError, OSError):
                continue

            call_id = request["call_id"]
            tool_name = request["tool_name"]
            arguments = request["arguments"]

            # Strip MCP namespace prefix for internal handler lookup
            if tool_name.startswith("clio-tools_"):
                lookup_name = tool_name[len("clio-tools_"):]
            else:
                lookup_name = tool_name

            if _is_local:
                logger.info(
                    "[OpenCode tool] start name=%s args=%s",
                    tool_name,
                    json.dumps(arguments, default=str)[:2000],
                )

            # Find the tool handler by original name (without prefix)
            handler = None
            params_model = None
            ends_session = False
            for t in self._tools:
                meta = getattr(t, "metadata", None)
                if meta and meta.name == lookup_name:
                    handler = meta.handler
                    params_model = meta.params_model
                    ends_session = meta.ends_session
                    break

            resp_path = os.path.join(ipc_dir, f"resp_{call_id}.json")

            if handler is None:
                _write_response_atomic(
                    resp_path,
                    {"result": f"Unknown tool: {tool_name}", "is_error": True},
                )
                processed += 1
                continue

            try:
                if params_model is not None:
                    params = params_model(**arguments)
                    result = handler(params)
                else:
                    result = handler(**arguments)
                _write_response_atomic(
                    resp_path, {"result": str(result), "is_error": False}
                )
                if _is_local:
                    logger.info(
                        "[OpenCode tool] complete name=%s result=%s",
                        tool_name,
                        str(result)[:2000],
                    )
                # Track successful calls to output-producing tools. Compare
                # against `lookup_name` (prefix stripped) so this works
                # regardless of whether the caller namespaces tool calls.
                if ends_session or lookup_name in _OUTPUT_TOOL_NAMES:
                    self._output_tools_called += 1
            except Exception as e:
                logger.error(f"Tool {tool_name} failed: {e}")
                _write_response_atomic(
                    resp_path, {"result": f"Tool error: {e}", "is_error": True}
                )

            processed += 1

        return processed

    def cleanup(self):
        """Remove temporary files."""
        import shutil

        if os.path.isdir(self._tmp_dir):
            shutil.rmtree(self._tmp_dir, ignore_errors=True)
