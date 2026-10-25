"""Client for Arnold's runner job lease API (``/runner/v1/jobs``)."""
from __future__ import annotations

import os

import requests

from utils.arnold_auth import ArnoldAuth


class ArnoldRunnerClient:
    def __init__(self, base_url: str | None = None, session: requests.Session | None = None):
        url = base_url or os.environ.get("ARNOLD_URL", "")
        if not url:
            raise RuntimeError("ARNOLD_URL is required")
        token = ArnoldAuth.get_auth_token()
        if not token:
            raise RuntimeError("ARNOLD_RUNNER_API_KEY is required")
        self.url = url.rstrip("/") + "/runner/v1/jobs"
        self.session = session or requests.Session()
        self.session.headers.update({"Authorization": token, "Content-Type": "application/json"})

    def _post(self, path: str, body: dict, timeout: float) -> dict | None:
        response = self.session.post(self.url + path, json=body, timeout=timeout, allow_redirects=False)
        response.raise_for_status()
        payload = response.json()
        if not payload.get("success"):
            raise RuntimeError(f"Arnold rejected {path}: {payload.get('errorMessage')}")
        return payload.get("response")

    def claim(self, worker_id: str, job: int | None = None, wait_seconds: int = 20) -> dict | None:
        body = {"workerId": worker_id, "waitSeconds": 0 if job else wait_seconds}
        if job:
            body["job"] = job
        return self._post("/claim", body, timeout=wait_seconds + 15)

    def heartbeat(self, number: int, worker_id: str) -> dict | None:
        return self._post(f"/{number}/heartbeat", {"workerId": worker_id}, timeout=30)

    def complete(self, number: int, worker_id: str, succeeded: bool, detail: str | None = None) -> dict | None:
        body = {"workerId": worker_id, "succeeded": succeeded}
        if detail:
            body["detail"] = detail[:2000]
        return self._post(f"/{number}/complete", body, timeout=30)
