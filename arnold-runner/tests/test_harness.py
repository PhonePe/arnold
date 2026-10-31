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

"""Tests for the harness abstraction layer and tool adapter.

These tests validate the plumbing without requiring actual LLM credentials.
Run with: python3 -m pytest tests/test_harness.py -v
"""

import json
import os
import sys
import tempfile
import threading
import time
from unittest.mock import MagicMock, patch

import pytest
from pydantic import BaseModel, Field


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _set_harness(name: str):
    """Patch the env and reload the adapter module to pick up the change."""
    os.environ["CLIO_HARNESS"] = name
    # Force the adapter to re-evaluate the harness
    import utils.tool_adapter as ta
    ta._HARNESS = name


# ---------------------------------------------------------------------------
# Tool Adapter tests
# ---------------------------------------------------------------------------


class TestToolAdapter:
    """Tests for utils.tool_adapter.define_tool."""

    def test_non_copilot_harness_returns_wrapper(self):
        """When harness is 'opencode', define_tool returns a _ToolWrapper."""
        _set_harness("opencode")

        from utils.tool_adapter import define_tool, _ToolWrapper

        class MyParams(BaseModel):
            name: str = Field(description="A name")

        @define_tool(description="Test tool")
        def my_tool(params: MyParams) -> str:
            return f"hello {params.name}"

        assert isinstance(my_tool, _ToolWrapper)
        assert my_tool.metadata.name == "my_tool"
        assert my_tool.metadata.description == "Test tool"
        assert my_tool.metadata.params_model is MyParams
        assert my_tool.metadata.copilot_tool is None

        # Callable
        result = my_tool(MyParams(name="world"))
        assert result == "hello world"

    def test_metadata_has_json_schema(self):
        """ToolMetadata.params_model can produce JSON schema for MCP."""
        _set_harness("opencode")
        from utils.tool_adapter import define_tool

        class SomeParams(BaseModel):
            count: int = Field(description="Number of items")
            label: str = Field(default="x", description="Label")

        @define_tool(description="Another tool")
        def another(params: SomeParams) -> str:
            return "ok"

        schema = another.metadata.params_model.model_json_schema()
        assert "count" in schema["properties"]
        assert "label" in schema["properties"]

    def test_tool_without_pydantic_param(self):
        """Tools that take no Pydantic param still work."""
        _set_harness("opencode")
        from utils.tool_adapter import define_tool

        @define_tool(description="No-param tool")
        def no_param_tool() -> str:
            return "done"

        assert no_param_tool.metadata.params_model is None
        assert no_param_tool.metadata.name == "no_param_tool"

    def test_copilot_metadata_remains_json_serializable(self):
        _set_harness("copilot")
        from utils.tool_adapter import define_tool

        class MyParams(BaseModel):
            value: str

        @define_tool(description="Native tool")
        def native_tool(params: MyParams) -> str:
            return params.value

        assert native_tool.metadata is None
        assert native_tool._clio_metadata.name == "native_tool"
        json.dumps(native_tool.metadata)

    def test_final_output_tools_end_the_copilot_turn(self):
        _set_harness("copilot")
        from utils.tool_adapter import define_tool

        class MyParams(BaseModel):
            value: str

        @define_tool(description="Final", ends_session=True)
        def final_tool(params: MyParams) -> str:
            return params.value

        @define_tool(description="Lookup")
        def lookup_tool(params: MyParams) -> str:
            return params.value

        assert final_tool.is_terminal is True
        assert lookup_tool.is_terminal is False


# ---------------------------------------------------------------------------
# Harness factory tests
# ---------------------------------------------------------------------------


class TestHarnessFactory:
    def test_get_copilot_harness(self):
        os.environ["CLIO_HARNESS"] = "copilot"
        from utils.harness import get_harness
        from utils.harness.copilot_harness import CopilotHarness

        h = get_harness()
        assert isinstance(h, CopilotHarness)

    def test_get_opencode_harness(self):
        os.environ["CLIO_HARNESS"] = "opencode"
        from utils.harness import get_harness
        from utils.harness.opencode_harness import OpenCodeHarness

        h = get_harness()
        assert isinstance(h, OpenCodeHarness)

    def test_unknown_harness_raises(self):
        os.environ["CLIO_HARNESS"] = "nonexistent"
        from utils.harness import get_harness

        with pytest.raises(ValueError, match="Unknown harness"):
            get_harness()

    def teardown_method(self):
        os.environ.pop("CLIO_HARNESS", None)


def test_usage_duration_accepts_sdk_timedelta():
    from datetime import timedelta
    from utils.harness.copilot_harness import _duration_ms

    assert _duration_ms(timedelta(seconds=1.5)) == 1500
    assert _duration_ms(250) == 250
    assert _duration_ms(None) == 0


def test_pending_message_event_gets_required_sdk_fields():
    from utils.harness.copilot_harness import _normalize_sdk_event_dict

    normalized = _normalize_sdk_event_dict({
        "type": "pending_messages.modified",
        "data": {},
        "ephemeral": True,
    })

    assert normalized["id"]
    assert normalized["timestamp"]
    assert normalized["type"] == "pending_messages.modified"


# ---------------------------------------------------------------------------
# MCP Tool Bridge tests
# ---------------------------------------------------------------------------


class TestMCPToolBridge:
    """Tests for the MCP tool bridge IPC mechanism."""

    def test_generate_server_script(self):
        """Server script is created and tool specs are embedded."""
        _set_harness("opencode")
        from utils.tool_adapter import define_tool
        from utils.harness.mcp_tool_server import MCPToolBridge

        class P(BaseModel):
            x: int = Field(description="An integer")

        @define_tool(description="Add one")
        def add_one(params: P) -> str:
            return str(params.x + 1)

        bridge = MCPToolBridge([add_one])
        try:
            script_path = bridge.generate_server_script()
            assert os.path.isfile(script_path)

            # Verify tool specs JSON
            specs_path = os.path.join(bridge._tmp_dir, "tool_specs.json")
            with open(specs_path) as f:
                specs = json.load(f)
            assert len(specs) == 1
            assert specs[0]["name"] == "add_one"
            assert "x" in specs[0]["input_schema"]["properties"]
        finally:
            bridge.cleanup()

    def test_process_tool_calls(self):
        """Tool calls via IPC are processed correctly."""
        _set_harness("opencode")
        from utils.tool_adapter import define_tool
        from utils.harness.mcp_tool_server import MCPToolBridge

        captured = {}

        class Params(BaseModel):
            value: str = Field(description="A value")

        @define_tool(description="Capture value")
        def capture(params: Params) -> str:
            captured["v"] = params.value
            return f"captured: {params.value}"

        bridge = MCPToolBridge([capture])
        bridge.generate_server_script()

        try:
            ipc_dir = bridge.ipc_dir
            os.makedirs(ipc_dir, exist_ok=True)

            # Simulate an MCP tool call request
            call_id = "test-call-001"
            req_path = os.path.join(ipc_dir, f"req_{call_id}.json")
            resp_path = os.path.join(ipc_dir, f"resp_{call_id}.json")

            with open(req_path, "w") as f:
                json.dump({
                    "tool_name": "capture",
                    "arguments": {"value": "hello"},
                    "call_id": call_id,
                }, f)

            processed = bridge.process_tool_calls()
            assert processed == 1
            assert captured["v"] == "hello"

            # Check response file was written
            assert os.path.isfile(resp_path)
            with open(resp_path) as f:
                resp = json.load(f)
            assert resp["result"] == "captured: hello"
            assert resp["is_error"] is False
        finally:
            bridge.cleanup()

    def test_process_unknown_tool(self):
        """Unknown tool names return an error response."""
        _set_harness("opencode")
        from utils.tool_adapter import define_tool
        from utils.harness.mcp_tool_server import MCPToolBridge

        class P(BaseModel):
            x: int = Field(description="x")

        @define_tool(description="Noop")
        def noop(params: P) -> str:
            return "ok"

        bridge = MCPToolBridge([noop])
        bridge.generate_server_script()

        try:
            ipc_dir = bridge.ipc_dir
            os.makedirs(ipc_dir, exist_ok=True)

            call_id = "test-unknown"
            req_path = os.path.join(ipc_dir, f"req_{call_id}.json")
            with open(req_path, "w") as f:
                json.dump({
                    "tool_name": "nonexistent_tool",
                    "arguments": {},
                    "call_id": call_id,
                }, f)

            processed = bridge.process_tool_calls()
            assert processed == 1

            resp_path = os.path.join(ipc_dir, f"resp_{call_id}.json")
            with open(resp_path) as f:
                resp = json.load(f)
            assert resp["is_error"] is True
            assert "Unknown tool" in resp["result"]
        finally:
            bridge.cleanup()

    def test_calls_processed_tracking(self):
        """MCPToolBridge tracks cumulative processed calls."""
        _set_harness("opencode")
        from utils.tool_adapter import define_tool
        from utils.harness.mcp_tool_server import MCPToolBridge

        class P(BaseModel):
            x: int = Field(description="x")

        @define_tool(description="Double")
        def double(params: P) -> str:
            return str(params.x * 2)

        bridge = MCPToolBridge([double])
        bridge.generate_server_script()

        try:
            ipc_dir = bridge.ipc_dir
            os.makedirs(ipc_dir, exist_ok=True)

            # First call
            for i, call_id in enumerate(["call-1", "call-2"]):
                req_path = os.path.join(ipc_dir, f"req_{call_id}.json")
                with open(req_path, "w") as f:
                    json.dump({
                        "tool_name": "double",
                        "arguments": {"x": i + 1},
                        "call_id": call_id,
                    }, f)

            # process_tool_calls increments _calls_processed and returns
            # just the number processed *in this invocation*
            processed = bridge.process_tool_calls()
            assert processed == 2
            assert bridge.calls_processed == 2

            # Another call
            req_path = os.path.join(ipc_dir, "req_call-3.json")
            with open(req_path, "w") as f:
                json.dump({
                    "tool_name": "double",
                    "arguments": {"x": 5},
                    "call_id": "call-3",
                }, f)

            processed = bridge.process_tool_calls()
            assert processed == 1
            # Cumulative count
            assert bridge.calls_processed == 3
            # "double" is not an output tool
            assert bridge.output_tools_called == 0
        finally:
            bridge.cleanup()

    def test_output_tools_called_tracking(self):
        """MCPToolBridge only counts output-producing tools."""
        _set_harness("opencode")
        from utils.tool_adapter import define_tool
        from utils.harness.mcp_tool_server import MCPToolBridge

        class P(BaseModel):
            x: int = Field(description="x")

        @define_tool(description="Double")
        def double(params: P) -> str:
            return str(params.x * 2)

        @define_tool(description="Write PR desc")
        def write_pr_description(params: P) -> str:
            return str(params.x * 2)

        @define_tool(description="Filter results")
        def write_filter_results(params: P) -> str:
            return str(params.x * 2)

        bridge = MCPToolBridge([double, write_pr_description, write_filter_results])
        bridge.generate_server_script()

        try:
            ipc_dir = bridge.ipc_dir
            os.makedirs(ipc_dir, exist_ok=True)

            # Call non-output tool
            req_path = os.path.join(ipc_dir, "req_call-1.json")
            with open(req_path, "w") as f:
                json.dump({
                    "tool_name": "double",
                    "arguments": {"x": 5},
                    "call_id": "call-1",
                }, f)

            bridge.process_tool_calls()
            assert bridge.calls_processed == 1
            assert bridge.output_tools_called == 0

            # Call output tool: write_pr_description (MCP prefixed name)
            req_path = os.path.join(ipc_dir, "req_call-2.json")
            with open(req_path, "w") as f:
                json.dump({
                    "tool_name": "clio-tools_write_pr_description",
                    "arguments": {"x": 10},
                    "call_id": "call-2",
                }, f)

            bridge.process_tool_calls()
            assert bridge.calls_processed == 2
            assert bridge.output_tools_called == 1

            # Call output tool: write_filter_results (MCP prefixed name)
            req_path = os.path.join(ipc_dir, "req_call-3.json")
            with open(req_path, "w") as f:
                json.dump({
                    "tool_name": "clio-tools_write_filter_results",
                    "arguments": {"x": 3},
                    "call_id": "call-3",
                }, f)

            bridge.process_tool_calls()
            assert bridge.calls_processed == 3
            assert bridge.output_tools_called == 2
        finally:
            bridge.cleanup()


# ---------------------------------------------------------------------------
# OpenCode config generation test
# ---------------------------------------------------------------------------


class TestOpenCodeConfig:
    """Tests for OpenCode config generation."""

    def test_build_config(self):
        """Config is generated with correct structure."""
        os.environ["CLIO_OPENCODE_API_KEY"] = "test-key-123"
        try:
            from utils.harness.opencode_harness import _build_opencode_config

            config = _build_opencode_config(
                model="openai/gpt-4.1",
                mcp_server_cmd=["python3", "/tmp/mcp_server.py"],
                system_message="You are a test assistant.",
                custom_agents=[
                    {"name": "reviewer", "prompt": "Review code", "description": "Code reviewer"}
                ],
            )

            assert config["$schema"] == "https://opencode.ai/config.json"
            assert "openai-compatible" in config["provider"]
            assert config["provider"]["openai-compatible"]["options"]["apiKey"] == "test-key-123"
            assert config["agent"]["plan"]["model"] == "openai-compatible/gpt-4.1"
            assert config["compaction"]["strategy"] == "summarize"
            assert "clio-tools" in config["mcp"]
            assert config["mcp"]["clio-tools"]["command"] == ["python3", "/tmp/mcp_server.py"]
            assert config["mcp"]["clio-tools"]["type"] == "local"
            assert config["mcp"]["clio-tools"]["enabled"] is True
            assert "reviewer" in config["agent"]
            assert config["agent"]["reviewer"]["mode"] == "subagent"
            assert config["agent"]["reviewer"]["permission"]["edit"] == "deny"
            assert config["agent"]["reviewer"]["permission"]["task"] == "deny"
            assert (
                config["agent"]["reviewer"]["permission"]
                ["clio-tools_write_review_findings"]
                == "deny"
            )
            assert config["permission"] == {
                "doom_loop": "deny",
                "*": "allow",
            }
        finally:
            os.environ.pop("CLIO_OPENCODE_API_KEY", None)

    def test_build_config_missing_key_raises(self):
        """Missing API key raises EnvironmentError."""
        os.environ.pop("CLIO_OPENCODE_API_KEY", None)
        os.environ.pop("CLIO_LLM_API_KEY", None)
        os.environ.pop("OPENAI_API_KEY", None)
        from utils.harness.opencode_harness import _build_opencode_config

        with pytest.raises(EnvironmentError, match="No API key available"):
            _build_opencode_config(
                model="test",
                mcp_server_cmd=["echo"],
                system_message="test",
            )

    def test_build_config_with_crg(self, tmp_path):
        """code-review-graph MCP server is added when graph dir exists."""
        os.environ["CLIO_OPENCODE_API_KEY"] = "test-key-123"
        try:
            from unittest.mock import patch
            from utils.harness.opencode_harness import _build_opencode_config

            # Create a fake .code-review-graph directory
            graph_dir = tmp_path / ".code-review-graph"
            graph_dir.mkdir()

            with patch("utils.harness.common.crg_available", return_value=True):
                config = _build_opencode_config(
                    model="test-model",
                    mcp_server_cmd=["python3", "/tmp/mcp_server.py"],
                    system_message="You are a reviewer.",
                    crg_repo_dir=str(tmp_path),
                )

            assert "code-review-graph" in config["mcp"]
            assert config["mcp"]["code-review-graph"]["enabled"] is True
            assert config["mcp"]["code-review-graph"]["type"] == "local"
            # Verify it points to the repo
            assert "--repo" in config["mcp"]["code-review-graph"]["command"]
            assert str(tmp_path) in config["mcp"]["code-review-graph"]["command"]
        finally:
            os.environ.pop("CLIO_OPENCODE_API_KEY", None)

    def test_build_config_without_crg(self):
        """code-review-graph MCP server is NOT added when graph dir is absent."""
        os.environ["CLIO_OPENCODE_API_KEY"] = "test-key-123"
        try:
            from utils.harness.opencode_harness import _build_opencode_config

            config = _build_opencode_config(
                model="test-model",
                mcp_server_cmd=["python3", "/tmp/mcp_server.py"],
                system_message="You are a reviewer.",
                crg_repo_dir="/nonexistent/path",
            )

            assert "code-review-graph" not in config["mcp"]
        finally:
            os.environ.pop("CLIO_OPENCODE_API_KEY", None)

    def test_build_config_crg_none(self):
        """code-review-graph MCP server is NOT added when crg_repo_dir is None."""
        os.environ["CLIO_OPENCODE_API_KEY"] = "test-key-123"
        try:
            from utils.harness.opencode_harness import _build_opencode_config

            config = _build_opencode_config(
                model="test-model",
                mcp_server_cmd=["python3", "/tmp/mcp_server.py"],
                system_message="You are a reviewer.",
                crg_repo_dir=None,
            )

            assert "code-review-graph" not in config["mcp"]
        finally:
            os.environ.pop("CLIO_OPENCODE_API_KEY", None)


class TestOpenCodeProcess:
    def test_large_prompt_is_piped_through_stdin(self, tmp_path):
        from unittest.mock import MagicMock

        from utils.harness.opencode_harness import OpenCodeHarness

        fake_opencode = tmp_path / "opencode"
        fake_opencode.write_text("#!/bin/sh\ncat", encoding="utf-8")
        fake_opencode.chmod(0o755)
        prompt = "x" * 200_000

        response = OpenCodeHarness()._run_opencode_process(
            str(fake_opencode), prompt, str(tmp_path), {}, MagicMock(output_tools_called=0)
        )

        assert response == prompt

    def test_json_events_rebuild_output_and_record_usage(self, monkeypatch, tmp_path):
        from unittest.mock import MagicMock

        from utils.harness import common
        from utils.harness.opencode_harness import OpenCodeHarness

        monkeypatch.setattr(common, "_usage_by_model", {})
        monkeypatch.setenv("CI_JOB_URL", "https://gitlab.example/acme/arnold-runner/-/jobs/7")
        events = [
            {"type": "step_start", "part": {}},
            {"type": "text", "part": {"text": " done \n"}},
            {"type": "step_finish", "part": {"tokens": {"input": 10, "output": 3, "reasoning": 2,
                                                        "cache": {"read": 5, "write": 1}}}},
            {"type": "step_finish", "part": {"tokens": {"input": 4, "output": 1}}},
        ]
        fake_opencode = tmp_path / "opencode"
        fake_opencode.write_text("#!/bin/sh\ncat >/dev/null\ncat <<'EOF'\nstatus line\n"
                                 + "\n".join(json.dumps(event) for event in events) + "\nEOF\n", encoding="utf-8")
        fake_opencode.chmod(0o755)

        response = OpenCodeHarness()._run_opencode_process(
            str(fake_opencode), "prompt", str(tmp_path), {}, MagicMock(output_tools_called=0), model="m"
        )

        assert response == "status line\ndone"
        assert common.usage_summary() == {
            "inputTokens": 20, "outputTokens": 6, "cacheReadTokens": 5,
            "models": [{"model": "m", "inputTokens": 20, "outputTokens": 6, "cacheReadTokens": 5}],
            "jobUrl": "https://gitlab.example/acme/arnold-runner/-/jobs/7",
        }

    def test_json_error_event_fails_session(self, tmp_path):
        from unittest.mock import MagicMock

        from utils.harness.opencode_harness import OpenCodeHarness

        fake_opencode = tmp_path / "opencode"
        fake_opencode.write_text("#!/bin/sh\ncat >/dev/null\necho '"
                                 + json.dumps({"type": "error", "error": {"name": "E", "data": {"message": "quota hit"}}})
                                 + "'\nexit 1\n", encoding="utf-8")
        fake_opencode.chmod(0o755)

        with pytest.raises(RuntimeError, match="quota hit"):
            OpenCodeHarness()._run_opencode_process(
                str(fake_opencode), "prompt", str(tmp_path), {}, MagicMock(output_tools_called=0)
            )

    def test_early_exit_does_not_leak_broken_pipe(self, monkeypatch, tmp_path):
        from unittest.mock import MagicMock

        from utils.harness.opencode_harness import OpenCodeHarness

        fake_opencode = tmp_path / "opencode"
        fake_opencode.write_text("#!/bin/sh\nexit 1", encoding="utf-8")
        fake_opencode.chmod(0o755)
        thread_errors = []
        monkeypatch.setattr(
            threading, "excepthook", lambda args: thread_errors.append(args.exc_value)
        )

        with pytest.raises(RuntimeError, match="exit code 1"):
            OpenCodeHarness()._run_opencode_process(
                str(fake_opencode), "x" * 1_000_000, str(tmp_path), {},
                MagicMock(output_tools_called=0),
            )

        assert thread_errors == []


# ---------------------------------------------------------------------------
# Graph MCP client tests
# ---------------------------------------------------------------------------


class TestGraphMCPClient:
    """Tests for GraphMCPClient used by the Copilot harness."""

    def test_start_returns_false_when_crg_missing(self):
        """start() returns False when code-review-graph CLI is not installed."""
        import shutil
        from unittest.mock import patch
        from utils.harness.graph_mcp_client import GraphMCPClient

        with patch.object(shutil, "which", return_value=None):
            client = GraphMCPClient("/fake/repo")
            assert client.start() is False
            assert not client.is_running

    def test_start_returns_false_when_graph_dir_missing(self, tmp_path):
        """start() returns False when .code-review-graph directory does not exist."""
        import shutil
        from unittest.mock import patch
        from utils.harness.graph_mcp_client import GraphMCPClient

        with patch.object(shutil, "which", return_value="/usr/bin/code-review-graph"):
            client = GraphMCPClient(str(tmp_path))
            assert client.start() is False
            assert not client.is_running

    def test_make_graph_tools_returns_empty_when_no_graph(self):
        """make_graph_tools returns ([], None) when graph is unavailable."""
        import shutil
        from unittest.mock import patch
        from utils.harness.graph_mcp_client import make_graph_tools

        with patch.object(shutil, "which", return_value=None):
            tools, client = make_graph_tools("/fake/repo")
            assert tools == []
            assert client is None

    def test_make_graph_tools_with_provided_client(self):
        """make_graph_tools respects a pre-started client."""
        import shutil
        from unittest.mock import patch, MagicMock
        from utils.harness.graph_mcp_client import make_graph_tools, GraphMCPClient

        mock_client = MagicMock(spec=GraphMCPClient)
        mock_client.is_running = True
        mock_client.tool_specs.return_value = [
            {"name": "query_graph_tool", "description": "Query the graph"}
        ]

        # Don't try to import copilot SDK in test
        with patch.object(shutil, "which", return_value="/usr/bin/code-review-graph"):
            tools, returned_client = make_graph_tools("/fake/repo", client=mock_client)

        # Should not have created a new client
        assert returned_client is None
        # Should have returned the tools (but we can't register them without copilot SDK)
        # The mock client was used directly
        mock_client.tool_specs.assert_called_once()

    def test_make_graph_tools_forwards_mcp_input_schema(self):
        """Graph tools expose the MCP inputSchema so the model can pass arguments."""
        from unittest.mock import MagicMock
        from utils.harness.graph_mcp_client import make_graph_tools, GraphMCPClient

        schema = {
            "type": "object",
            "properties": {"pattern": {"type": "string"}, "target": {"type": "string"}},
            "required": ["pattern", "target"],
        }
        mock_client = MagicMock(spec=GraphMCPClient)
        mock_client.is_running = True
        mock_client.tool_specs.return_value = [
            {"name": "query_graph_tool", "description": "Query the graph", "inputSchema": schema}
        ]

        tools, _ = make_graph_tools("/fake/repo", client=mock_client)

        assert [t.name for t in tools] == ["query_graph_tool"]
        assert tools[0].parameters == schema


# ---------------------------------------------------------------------------
# OpenCode → Copilot fallback tests
# ---------------------------------------------------------------------------


class TestOpenCodeProcess:
    def test_internal_stderr_logs_are_not_printed(self):
        from utils.harness.opencode_harness import OpenCodeHarness

        proc = MagicMock()
        proc.stdout.readline.side_effect = [""]
        proc.stdout.__iter__.return_value = iter(())
        proc.stderr.__iter__.return_value = iter((
            "timestamp=2026-08-31T10:05:20Z level=INFO message=stream\n",
            "→ Read src/example.py [limit=15]\n",
        ))
        proc.returncode = 0

        with (
            patch("utils.harness.opencode_harness.subprocess.Popen", return_value=proc),
            patch("utils.harness.opencode_harness.select.select", return_value=([proc.stdout], [], [])),
            patch("utils.harness.opencode_harness.logger.debug") as debug,
        ):
            OpenCodeHarness()._run_opencode_process(
                "/usr/bin/opencode", "review", "/tmp", {}, MagicMock(output_tools_called=1)
            )

        logs = "\n".join(str(call) for call in debug.call_args_list)
        assert "→ Read src/example.py" in logs
        assert "message=stream" not in logs

    def test_prompt_is_sent_via_stdin(self):
        from utils.harness.opencode_harness import OpenCodeHarness

        proc = MagicMock()
        proc.stdout.readline.side_effect = ["done\n", ""]
        proc.stdout.__iter__.return_value = iter(())
        proc.stderr.__iter__.return_value = iter(())
        proc.returncode = 0
        bridge = MagicMock(output_tools_called=0)

        with (
            patch("utils.harness.opencode_harness.subprocess.Popen", return_value=proc) as popen,
            patch("utils.harness.opencode_harness.select.select", return_value=([proc.stdout], [], [])),
        ):
            result = OpenCodeHarness()._run_opencode_process(
                "/usr/bin/opencode", "x" * 300_000, "/tmp", {}, bridge
            )

        assert result == "done"
        assert popen.call_args.args[0] == [
            "/usr/bin/opencode",
            "run",
            "--dir",
            "/tmp",
            "--format",
            "json",
            "--print-logs",
            "--log-level",
            "DEBUG",
        ]
        proc.stdin.write.assert_called_once_with("x" * 300_000)
        proc.stdin.close.assert_called_once_with()


class TestOpenCodeFallback:
    """Tests for the opencode → copilot fallback in call_llm_agentic."""

    def test_opencode_failure_falls_back_to_copilot(self, monkeypatch):
        """When CLIO_HARNESS=opencode and opencode fails, copilot is tried."""
        monkeypatch.setenv("CLIO_HARNESS", "opencode")

        from unittest.mock import MagicMock, patch
        from utils.harness.opencode_harness import OpenCodeHarness
        from utils.harness.copilot_harness import CopilotHarness

        # Make opencode harness raise
        mock_oc = MagicMock(spec=OpenCodeHarness)
        mock_oc.run_session.side_effect = RuntimeError("opencode crashed")

        # Make copilot harness succeed
        mock_copilot = MagicMock(spec=CopilotHarness)
        mock_copilot.run_session.return_value = "copilot response"

        with patch(
            "utils.harness.get_harness",
            side_effect=lambda: (
                mock_oc if os.environ.get("CLIO_HARNESS") == "opencode"
                else mock_copilot
            ),
        ):
            from utils.call_llm import call_llm_agentic

            result = call_llm_agentic(
                prompt="test",
                tools=[],
                system_message="sys",
                model="test-model",
                enable_graph_tools=False,
            )

        assert result == "copilot response"
        mock_oc.run_session.assert_called_once()
        mock_copilot.run_session.assert_called_once()
        assert mock_oc.run_session.call_args.kwargs["enable_graph_tools"] is False
        assert mock_copilot.run_session.call_args.kwargs["enable_graph_tools"] is False

    def test_opencode_failure_copilot_also_fails_raises(self, monkeypatch):
        """When both opencode and copilot fail, the copilot error is raised."""
        monkeypatch.setenv("CLIO_HARNESS", "opencode")

        from unittest.mock import MagicMock, patch
        from utils.harness.opencode_harness import OpenCodeHarness
        from utils.harness.copilot_harness import CopilotHarness

        mock_oc = MagicMock(spec=OpenCodeHarness)
        mock_oc.run_session.side_effect = RuntimeError("opencode crashed")

        mock_copilot = MagicMock(spec=CopilotHarness)
        mock_copilot.run_session.side_effect = RuntimeError("copilot also crashed")

        with patch(
            "utils.harness.get_harness",
            side_effect=lambda: (
                mock_oc if os.environ.get("CLIO_HARNESS") == "opencode"
                else mock_copilot
            ),
        ):
            from utils.call_llm import call_llm_agentic

            with pytest.raises(RuntimeError, match="copilot also crashed"):
                call_llm_agentic(
                    prompt="test",
                    tools=[],
                    system_message="sys",
                    model="test-model",
                )

    def test_copilot_harness_failure_no_fallback(self, monkeypatch):
        """When CLIO_HARNESS=copilot and it fails, no fallback is attempted."""
        monkeypatch.setenv("CLIO_HARNESS", "copilot")

        from unittest.mock import MagicMock, patch
        from utils.harness.copilot_harness import CopilotHarness

        mock_copilot = MagicMock(spec=CopilotHarness)
        mock_copilot.run_session.side_effect = RuntimeError("copilot crashed")

        with patch("utils.harness.get_harness", return_value=mock_copilot):
            from utils.call_llm import call_llm_agentic

            with pytest.raises(RuntimeError, match="copilot crashed"):
                call_llm_agentic(
                    prompt="test",
                    tools=[],
                    system_message="sys",
                    model="test-model",
                )

        # Only one call — no fallback attempted
        mock_copilot.run_session.assert_called_once()

    def test_opencode_success_no_fallback(self, monkeypatch):
        """When opencode succeeds, copilot fallback is not triggered."""
        monkeypatch.setenv("CLIO_HARNESS", "opencode")

        from unittest.mock import MagicMock, patch
        from utils.harness.opencode_harness import OpenCodeHarness

        mock_oc = MagicMock(spec=OpenCodeHarness)
        mock_oc.run_session.return_value = "opencode response"

        with patch("utils.harness.get_harness", return_value=mock_oc):
            from utils.call_llm import call_llm_agentic

            result = call_llm_agentic(
                prompt="test",
                tools=[],
                system_message="sys",
                model="test-model",
            )

        assert result == "opencode response"
        mock_oc.run_session.assert_called_once()

    def test_disable_fallback_skips_copilot(self, monkeypatch):
        """When disable_fallback=True, copilot fallback is skipped even on opencode failure."""
        monkeypatch.setenv("CLIO_HARNESS", "opencode")

        from unittest.mock import MagicMock, patch
        from utils.harness.opencode_harness import OpenCodeHarness

        mock_oc = MagicMock(spec=OpenCodeHarness)
        mock_oc.run_session.side_effect = RuntimeError("opencode crashed")

        with patch("utils.harness.get_harness", return_value=mock_oc):
            from utils.call_llm import call_llm_agentic

            with pytest.raises(RuntimeError, match="opencode crashed"):
                call_llm_agentic(
                    prompt="test",
                    tools=[],
                    system_message="sys",
                    model="test-model",
                    disable_fallback=True,
                )

        # Only one call — fallback not attempted
        mock_oc.run_session.assert_called_once()


# ---------------------------------------------------------------------------
# Cleanup
# ---------------------------------------------------------------------------


@pytest.fixture(autouse=True)
def _restore_harness_env():
    """Reset CLIO_HARNESS after each test."""
    original = os.environ.get("CLIO_HARNESS")
    yield
    if original is not None:
        os.environ["CLIO_HARNESS"] = original
    else:
        os.environ.pop("CLIO_HARNESS", None)

@pytest.mark.parametrize("harness,variable,model", [
    ("copilot", "CLIO_LLM_MODEL", "selected-model"),
    ("opencode", "CLIO_OPENCODE_MODEL", "provider/selected-model"),
])
def test_explicit_execution_selection_overrides_workflow_defaults_and_disables_fallback(monkeypatch, harness, variable, model):
    monkeypatch.setenv("CLIO_HARNESS", harness)
    monkeypatch.setenv("CLIO_DISABLE_FALLBACK", "true")
    monkeypatch.setenv(variable, model)
    monkeypatch.delenv("ARNOLD_COPILOT_KEY", raising=False)
    from utils.call_llm import call_llm_agentic
    selected = MagicMock()
    selected.run_session.return_value = "result"
    with patch("utils.harness.get_harness", return_value=selected) as get_harness:
        assert call_llm_agentic("prompt", [], model="workflow-default") == "result"
        assert selected.run_session.call_args.kwargs["model"] == model
        selected.run_session.side_effect = RuntimeError("selected harness failed")
        with pytest.raises(RuntimeError, match="selected harness failed"):
            call_llm_agentic("prompt", [])
        assert get_harness.call_count == 2
        assert os.environ["CLIO_HARNESS"] == harness


def test_usage_summary_links_github_actions_run(monkeypatch):
    from utils.harness import common

    monkeypatch.setattr(common, "_usage_by_model", {})
    monkeypatch.delenv("CI_JOB_URL", raising=False)
    monkeypatch.setenv("GITHUB_SERVER_URL", "https://github.com")
    monkeypatch.setenv("GITHUB_REPOSITORY", "acme/reviews")
    monkeypatch.setenv("GITHUB_RUN_ID", "42")
    common.record_usage("gpt", 10, 5)

    assert common.usage_summary()["jobUrl"] == "https://github.com/acme/reviews/actions/runs/42"
