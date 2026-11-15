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

from types import SimpleNamespace
import json

import pytest

import flows.code_reviews.common.cli as cli
from flows.code_reviews.common.cli import build_shared_dict


def test_build_shared_dict_appends_review_context(monkeypatch):
    monkeypatch.setenv("REVIEW_CONTEXT", "Focus on retry behavior.")
    args = SimpleNamespace(
        jira_url=None,
        jira_token=None,
        jira_cloud_id=None,
        jira_browse_url=None,
        jira_tickets=None,
        rfc_url=None,
        rfc_api_url=None,
        local_dir="/tmp/repo",
        dry_run=True,
        no_cleanup=False,
        enable_code_graph=True,
    )

    shared = build_shared_dict(
        args, "repo", "feature", "main", "42", 7, "gitlab", "token", "project",
        {"mr_description": "Original description"},
    )

    assert shared["mr_description"] == (
        "Original description\n\nAdditional review context:\nFocus on retry behavior."
    )


def test_build_shared_dict_loads_review_personas(monkeypatch):
    monkeypatch.setenv("REVIEW_PERSONAS_JSON", json.dumps([{
        "id": "netra", "name": "Netra", "instructions": "Check security.", "version": 1,
    }]))
    args = SimpleNamespace(
        jira_url=None, jira_token=None, jira_cloud_id=None, jira_browse_url=None, jira_tickets=None,
        rfc_url=None, rfc_api_url=None, local_dir="/tmp/repo", dry_run=True, no_cleanup=False,
        enable_code_graph=True,
    )

    shared = build_shared_dict(
        args, "repo", "feature", "main", "42", 7, "gitlab", "token", "project", {},
    )

    assert shared["review_personas"][0]["id"] == "netra"


class _Response:
    def __init__(self, body):
        self.body = body

    def raise_for_status(self):
        pass

    def json(self):
        return self.body


def _persona_env(monkeypatch):
    monkeypatch.setenv("REVIEW_PERSONA_REFS", "netra@3,sai@1")
    monkeypatch.setenv("ARNOLD_RUN_ID", "run-1")
    monkeypatch.setenv("REVIEW_PERSONAS_JSON", "")
    monkeypatch.setattr(cli, "get_env_optional", lambda key: "https://arnold/" if key == "ARNOLD_URL" else None)
    monkeypatch.setattr("utils.arnold_auth.ArnoldAuth.get_auth_token", staticmethod(lambda: "O-token"))


def test_review_persona_refs_fetch_personas_from_arnold(monkeypatch):
    _persona_env(monkeypatch)
    calls = []
    personas = [
        {"id": "netra", "name": "Netra", "instructions": "Check auth.", "version": 3},
        {"id": "sai", "name": "Sai", "instructions": "Check perf.", "version": 1},
    ]

    def fake_get(url, **kwargs):
        calls.append((url, kwargs["params"], kwargs["headers"]))
        return _Response({"success": True, "response": personas})

    monkeypatch.setattr("requests.get", fake_get)

    assert cli._review_personas_from_env() == personas
    assert calls == [(
        "https://arnold/analysis-callbacks/v1/runs/run-1/personas",
        {"refs": "netra@3,sai@1"},
        {"Authorization": "O-token"},
    )]


def test_review_persona_refs_reject_mismatched_snapshot(monkeypatch):
    _persona_env(monkeypatch)
    monkeypatch.setattr("requests.get", lambda url, **kwargs: _Response({"success": True, "response": [
        {"id": "netra", "name": "Netra", "instructions": "Check auth.", "version": 2},
    ]}))

    with pytest.raises(RuntimeError, match="do not match"):
        cli._review_personas_from_env()


def test_review_persona_refs_fail_when_arnold_is_unreachable(monkeypatch):
    _persona_env(monkeypatch)

    def fail(url, **kwargs):
        raise ConnectionError("down")

    monkeypatch.setattr("requests.get", fail)

    with pytest.raises(RuntimeError, match="Could not fetch review personas"):
        cli._review_personas_from_env()
