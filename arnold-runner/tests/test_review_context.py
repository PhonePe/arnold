from types import SimpleNamespace
import json

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
