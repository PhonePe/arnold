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

"""Retrieve an execution-scoped Copilot credential from Arnold."""
import os
import time
from urllib.parse import urlsplit

import requests

from utils.env import get_env_optional
from utils.arnold_auth import ArnoldAuth


def execution_token() -> dict:
    key = os.environ.get("ARNOLD_COPILOT_KEY")
    url = get_env_optional("ARNOLD_URL").rstrip("/")
    parsed = urlsplit(url)
    if not key or parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.query or parsed.fragment:
        raise RuntimeError("Copilot exchange requires an execution key and an HTTPS ARNOLD_URL")
    try:
        response = requests.post(
            url + "/copilot-integrations/v1/execution-token",
            headers={"Authorization": ArnoldAuth.get_auth_token(), "X-Arnold-Copilot-Key": key},
            timeout=(10, 60), allow_redirects=False,
        )
        if response.status_code != 200:
            raise ValueError("exchange rejected")
        body = response.json()
        token = body.get("response", {}).get("accessToken")
        if not body.get("success") or not isinstance(token, str) or not token:
            raise ValueError("invalid credential")
        expires_at = body["response"].get("expiresAt")
        # Bound SDK reuse when GitHub supplies no expiry; this does not revoke the OAuth token.
        expires_in = 2 * 60 * 60
        if expires_at is not None:
            if type(expires_at) is not int:
                raise ValueError("invalid expiry")
            expires_in = min(expires_in, int(expires_at / 1000 - time.time()))
        if expires_in <= 0:
            raise ValueError("expired credential")
        return {"kind": "token", "accessToken": token, "expiresIn": expires_in}
    except Exception:
        # Response bodies and transport exceptions may contain credentials.
        raise RuntimeError("Arnold Copilot credential exchange failed") from None
