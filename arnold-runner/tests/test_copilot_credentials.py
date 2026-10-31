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

import asyncio
import os
from unittest.mock import AsyncMock, patch

import pytest

from utils.harness.copilot_credentials import execution_token


def test_exchange_is_authenticated_and_does_not_redirect():
    with patch.dict(os.environ, {"ARNOLD_COPILOT_KEY": "key", "ARNOLD_URL": "https://arnold/api"}, clear=True), \
            patch("utils.harness.copilot_credentials.ArnoldAuth.get_auth_token", return_value="Bearer service"), \
            patch("utils.harness.copilot_credentials.requests.post") as post:
        post.return_value.status_code = 200
        post.return_value.json.return_value = {"success": True, "response": {"accessToken": "secret"}}
        assert execution_token() == {"kind": "token", "accessToken": "secret", "expiresIn": 7200}
        assert post.call_args.args[0] == "https://arnold/api/copilot-integrations/v1/execution-token"
        assert post.call_args.kwargs["headers"] == {"Authorization": "Bearer service", "X-Arnold-Copilot-Key": "key"}
        assert post.call_args.kwargs["allow_redirects"] is False
        for status in (302, 401, 403, 500):
            post.return_value.status_code = status
            with pytest.raises(RuntimeError, match="exchange failed"):
                execution_token()
        post.side_effect = RuntimeError("secret key")
        with pytest.raises(RuntimeError) as error:
            execution_token()
        assert "secret" not in str(error.value)


def test_insecure_endpoint_is_rejected_before_authentication():
    with patch.dict(os.environ, {"ARNOLD_COPILOT_KEY": "key", "ARNOLD_URL": "http://arnold"}, clear=True), \
            patch("utils.harness.copilot_credentials.requests.post") as post:
        with pytest.raises(RuntimeError, match="HTTPS"):
            execution_token()
        post.assert_not_called()



def test_exchange_expiry_is_bounded_and_expired_credentials_are_rejected():
    with patch.dict(os.environ, {"ARNOLD_COPILOT_KEY": "key", "ARNOLD_URL": "https://arnold"}, clear=True), \
            patch("utils.harness.copilot_credentials.ArnoldAuth.get_auth_token", return_value="auth"), \
            patch("utils.harness.copilot_credentials.time.time", return_value=1000), \
            patch("utils.harness.copilot_credentials.requests.post") as post:
        post.return_value.status_code = 200
        for expiry, remaining in ((None, 7200), (10_000_000, 7200), (1_500_000, 500)):
            post.return_value.json.return_value = {
                "success": True, "response": {"accessToken": "secret", "expiresAt": expiry}}
            assert execution_token()["expiresIn"] == remaining
        for expiry in (1_000_000, 999_000, "invalid", True):
            post.return_value.json.return_value["response"]["expiresAt"] = expiry
            with pytest.raises(RuntimeError, match="exchange failed"):
                execution_token()


@pytest.mark.parametrize("exchange_fails", [False, True])
def test_session_fetches_token_before_creation_without_inherited_credentials(exchange_fails):
    from utils.harness import copilot_harness as harness
    with patch.dict(os.environ, {"ARNOLD_COPILOT_KEY": "key", "GITHUB_TOKEN": "shared"}), \
            patch.object(harness, "_copilot_sdk_client", None), \
            patch.object(harness, "_copilot_sdk_started", False), \
            patch.object(harness, "_copilot_sdk_arnold_auth", False), \
            patch("utils.harness.copilot_credentials.execution_token", return_value={"accessToken": "user-token"}) as exchange, \
            patch("copilot.CopilotClient") as client:
        if exchange_fails:
            exchange.side_effect = RuntimeError("exchange failed")

        async def create_session(**kwargs):
            exchange.assert_called_once()
            assert kwargs["github_token"] == "user-token"
            assert "github_token_provider" not in kwargs
            raise RuntimeError("session creation reached")

        client.return_value.start = AsyncMock()
        client.return_value.create_session = AsyncMock(side_effect=create_session)
        expected_error = "exchange failed" if exchange_fails else "session creation reached"
        with pytest.raises(RuntimeError, match=expected_error):
            asyncio.run(harness._call_copilot_sdk_agentic("prompt", "system", "model", []))
        exchange.assert_called_once()
        if exchange_fails:
            client.return_value.create_session.assert_not_called()
        else:
            client.return_value.create_session.assert_awaited_once()
        kwargs = client.call_args.kwargs
        assert kwargs["use_logged_in_user"] is False
        assert "GITHUB_TOKEN" not in kwargs["env"]
        assert "ARNOLD_COPILOT_KEY" not in kwargs["env"]
        assert "github_token" not in kwargs


@pytest.mark.parametrize("custom_agents", [None, [{"name": "persona-kozo", "prompt": "p"}]])
def test_session_allowlists_tools_so_runtime_extras_are_not_resent_each_turn(custom_agents):
    from utils.harness import copilot_harness as harness
    with patch.dict(os.environ, {"GITHUB_TOKEN": "token"}), patch.dict(os.environ, clear=False) as env, \
            patch.object(harness, "_copilot_sdk_client", None), \
            patch.object(harness, "_copilot_sdk_started", False), \
            patch.object(harness, "_copilot_sdk_arnold_auth", False), \
            patch("copilot.CopilotClient") as client:
        env.pop("ARNOLD_COPILOT_KEY", None)
        client.return_value.start = AsyncMock()
        client.return_value.create_session = AsyncMock(side_effect=RuntimeError("session creation reached"))
        with pytest.raises(RuntimeError, match="session creation reached"):
            asyncio.run(harness._call_copilot_sdk_agentic("prompt", "system", "model", [], custom_agents=custom_agents))

    allowed = set(client.return_value.create_session.call_args.kwargs["available_tools"])
    assert {"builtin:bash", "builtin:view", "builtin:edit", "custom:*"} <= allowed
    assert not any(entry.startswith("mcp:") for entry in allowed)
    assert ("builtin:task" in allowed) == bool(custom_agents)


def test_scoped_custom_agents_also_get_graph_tools():
    from types import SimpleNamespace
    from utils.harness import copilot_harness as harness
    agents = [{"name": "scoped", "prompt": "p", "tools": ["view"]}, {"name": "open", "prompt": "p"}]
    with patch.dict(os.environ, {"GITHUB_TOKEN": "token"}), patch.dict(os.environ, clear=False) as env, \
            patch.object(harness, "_copilot_sdk_client", None), \
            patch.object(harness, "_copilot_sdk_started", False), \
            patch.object(harness, "_copilot_sdk_arnold_auth", False), \
            patch.object(harness, "crg_available", return_value=True), \
            patch("utils.harness.graph_mcp_client.make_graph_tools",
                  return_value=([SimpleNamespace(name="query_graph_tool")], None)), \
            patch("copilot.CopilotClient") as client:
        env.pop("ARNOLD_COPILOT_KEY", None)
        client.return_value.start = AsyncMock()
        client.return_value.create_session = AsyncMock(side_effect=RuntimeError("session creation reached"))
        with pytest.raises(RuntimeError, match="session creation reached"):
            asyncio.run(harness._call_copilot_sdk_agentic(
                "prompt", "system", "model", [], working_directory="/repo", custom_agents=agents))

    sent = client.return_value.create_session.call_args.kwargs["custom_agents"]
    assert sent[0]["tools"] == ["view", "query_graph_tool"]
    assert "tools" not in sent[1]
    assert agents[0]["tools"] == ["view"]
