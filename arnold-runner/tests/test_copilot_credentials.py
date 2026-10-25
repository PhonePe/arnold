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
