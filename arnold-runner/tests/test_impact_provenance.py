"""Tests for review-comment provenance consumed by impact analysis."""

from __future__ import annotations

from unittest.mock import MagicMock, patch

import yaml

from flows.code_reviews.impact.nodes import (
    AnalyzeCommentImpact,
    FetchAllReviewComments,
    WriteImpactOutput,
)
from flows.code_reviews.improve.nodes import PostMergeRequestComments
from utils.review_comment_metadata import (
    build_review_comment_marker,
    parse_review_comment_metadata,
)


def _comment_body(marker: str) -> str:
    return (
        "**Critical Bug:** Fix the boundary condition.\n\n"
        "```suggestion\nfixed();\n```\n\n"
        f"{marker}"
    )


class TestReviewCommentMetadata:
    def test_round_trips_harness_and_model(self):
        marker = build_review_comment_marker("codex", "gpt-5.6-sol")

        body, provenance = parse_review_comment_metadata(
            f"Visible review text\n\n{marker}"
        )

        assert body == "Visible review text"
        assert provenance == {
            "source_harness": "codex",
            "source_model": "gpt-5.6-sol",
        }

    def test_parses_legacy_harness_marker_with_unknown_model(self):
        body, provenance = parse_review_comment_metadata(
            "Visible\n\n<!-- clio:source-harness=opencode -->"
        )

        assert body == "Visible"
        assert provenance["source_harness"] == "opencode"
        assert provenance["source_model"] is None

    @patch.dict("os.environ", {"CLIO_HARNESS": "opencode"})
    def test_posted_comment_uses_active_harness_as_fallback(self):
        formatted = PostMergeRequestComments()._format_comments(
            [{"suggestion_content": "Fix it", "file": "a.py", "line": 1}]
        )

        assert formatted[0]["source_harness"] == "opencode"
        assert formatted[0]["source_model"] == ""
        assert formatted[0]["body"].endswith(
            '<!-- clio-meta:{"source_harness":"opencode",'
            '"source_model":null} -->'
        )

    def test_removes_malformed_marker_without_failing(self):
        body, provenance = parse_review_comment_metadata(
            "Visible\n\n<!-- clio-meta:not-json -->"
        )

        assert body == "Visible"
        assert provenance["source_harness"] is None
        assert provenance["source_model"] is None


class TestImpactProvenanceFlow:
    def test_fetches_clean_body_category_and_provenance(self):
        marker = build_review_comment_marker("codex", "gpt-5.6-sol")
        note = {
            "id": 101,
            "body": _comment_body(marker),
            "system": False,
            "author": {"username": "clio", "id": 7},
            "position": {
                "new_path": "src/example.py",
                "new_line": 12,
            },
        }
        discussion = MagicMock()
        discussion.id = "discussion-1"
        discussion.attributes = {"notes": [note]}
        mr = MagicMock()
        mr.author = {"username": "author"}
        mr.discussions.list.return_value = [discussion]
        project = MagicMock()
        project.mergerequests.get.return_value = mr
        gitlab_client = MagicMock()
        gitlab_client.projects.get.return_value = project

        with patch(
            "flows.code_reviews.impact.nodes.gitlab.Gitlab",
            return_value=gitlab_client,
        ):
            result = FetchAllReviewComments().exec(
                {
                    "gitlab_token": "token",
                    "gitlab_url": "https://gitlab.example",
                    "project_id": "group/project",
                    "merge_request_iid": 42,
                    "mr_author_username": "",
                }
            )

        comment = result["review_comments"][0]
        assert "clio-meta" not in comment["body"]
        assert comment["label"] == "critical bug"
        assert comment["has_suggestion"] is True
        assert comment["source_harness"] == "codex"
        assert comment["source_model"] == "gpt-5.6-sol"

    def test_staged_llm_comments_exclude_provenance(self, tmp_path):
        comments_file = AnalyzeCommentImpact._stage_comments(
            str(tmp_path),
            [
                {
                    "body": "Visible",
                    "label": "rfc_violation",
                    "source_harness": "codex",
                    "source_model": "gpt-5.6-sol",
                }
            ],
        )

        with open(comments_file, encoding="utf-8") as stream:
            staged = yaml.safe_load(stream)

        assert staged[0]["body"] == "Visible"
        assert staged[0]["label"] == "rfc_violation"
        assert "source_harness" not in staged[0]
        assert "source_model" not in staged[0]

    def test_output_propagates_provenance_and_visible_category(
        self,
        tmp_path,
    ):
        output_path = tmp_path / "impact.yaml"
        result = WriteImpactOutput().exec(
            {
                "impact_results": [
                    {
                        "comment_id": "comment-0",
                        "status": "applied",
                        "score": 0.9,
                        "reasoning": "Implemented",
                        "priority": "high",
                        "comment_category": "general",
                    }
                ],
                "review_comments": [
                    {
                        "note_id": 101,
                        "discussion_id": "discussion-1",
                        "file_path": "src/example.py",
                        "line_number": 12,
                        "author": "clio",
                        "author_id": 7,
                        "comment_type": "inline",
                        "has_suggestion": True,
                        "label": "rfc_violation",
                        "source_harness": "codex",
                        "source_model": "gpt-5.6-sol",
                    }
                ],
                "project_id": 10,
                "merge_request_id": 20,
                "merge_request_iid": 30,
                "mr_author_id": 40,
                "local_repo_path": None,
                "output_file": str(output_path),
            }
        )

        comment = result["output_data"]["comments"][0]
        assert comment["source_harness"] == "codex"
        assert comment["source_model"] == "gpt-5.6-sol"
        assert comment["comment_category"] == "rfc_violation"
