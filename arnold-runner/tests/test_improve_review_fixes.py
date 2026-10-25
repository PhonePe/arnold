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

import pytest

from flows.code_reviews.improve.nodes import ExecuteCodeReview
import flows.code_reviews.improve.nodes as improve_nodes
from flows.code_reviews.common.nodes import FetchJiraTickets
from flows.code_reviews.improve.prompts import (
    _is_test_file,
    build_review_prompt,
    build_skill_review_prompt,
    build_system_message,
)


def test_improve_review_preserves_mr_metadata():
    prepared = ExecuteCodeReview().prep({
        "mr_title": "BM Auth intervention in PPR",
        "mr_description": "Review context",
    })

    assert prepared["mr_title"] == "BM Auth intervention in PPR"
    assert prepared["mr_description"] == "Review context"


def test_explicit_jira_context_is_merged_with_mr_metadata():
    tickets = FetchJiraTickets()._extract_ticket_ids(
        "ACME-0 in title", "", "feature/acme-123", "https://jira/browse/OPS-456"
    )

    assert tickets == ["ACME-0", "OPS-456"]


def test_payment_tests_are_classified_as_tests():
    path = "CommonCode/Payment/Payment/Sources/PaymentTests/FooTests.swift"

    assert _is_test_file(path)

    changed_files = [{"path": path, "status": "M"}]
    base_prompt = build_review_prompt(
        "title", "description", "source", "target", changed_files, "/repo"
    )
    skill_prompt = build_skill_review_prompt(
        "ios-review", "title", "description", "source", "target",
        changed_files, "/repo",
    )

    assert "1 total: 0 core + 1 test" in base_prompt
    assert "1 total: 0 core + 1 test" in skill_prompt


def test_words_ending_in_tests_are_not_classified_as_tests():
    assert not _is_test_file("src/contests/handler.py")
    assert not _is_test_file("src/contests.py")


def test_failed_graph_is_not_required_by_review_instructions():
    message = build_system_message(
        ["ios-review"], graph_tools_available=False
    )

    assert "Use graph query tools" not in message
    assert "semantic_search_nodes_tool" not in message


def test_graph_review_uses_known_diff_paths_and_graph_discovery():
    prompt = build_review_prompt(
        "title",
        "description",
        "source",
        "target",
        [{"path": "src/example.py", "status": "M"}],
        "/repo",
        graph_tools_available=True,
    )

    assert "call `query_graph_tool` immediately after reading its diff" in prompt
    assert "Do NOT use `find`, `cat`, `grep`, or `glob`" in prompt
    assert "find /repo/.diffs" not in prompt


def _invoke_tool(tools, name, **arguments):
    tool = next(
        tool for tool in tools
        if (getattr(tool, "_clio_metadata", None) or tool.metadata).name == name
    )
    metadata = getattr(tool, "_clio_metadata", None) or tool.metadata
    model = metadata.params_model
    return metadata.handler(model(**arguments)) if model else metadata.handler()


def test_group_review_context_has_dedicated_prompt_and_system_rules():
    prompt = build_review_prompt(
        "title",
        "description",
        "source",
        "target",
        [{"path": "src/example.py", "status": "M"}],
        "/repo",
        group_context="## Merge Request Group Context\nSibling contract",
    )
    message = build_system_message(
        graph_tools_available=True,
        group_review=True,
    )

    assert "## Merge Request Group Context" in prompt
    assert "graph results apply only to the current checkout" in message.lower()


def test_main_agent_shares_context_and_collects_every_specialist(monkeypatch):
    finding = {
        "relevant_file": "src/example.py",
        "language": "python",
        "line_number": 1,
        "lines_to_replace": 1,
        "existing_code": "old()",
        "suggestion_content": "The changed call uses the wrong function.",
        "improved_code": "new()",
        "one_sentence_summary": "Use the correct function",
        "label": "possible bug",
        "priority": "medium",
        "confidence": 0.9,
    }

    def fake_agentic(prompt, tools, **kwargs):
        assert [agent["name"] for agent in kwargs["custom_agents"]] == [
            "persona-security", "python-review",
        ]
        assert "Shared-Context Specialist Reviews" in kwargs["system_message"]

        _invoke_tool(tools, "publish_review_context", context={
            "changed_files": ["src/example.py"],
            "evidence": ["src/example.py:1"],
        })
        shared = _invoke_tool(
            tools, "read_review_context", context_id="review-context"
        )
        assert "src/example.py" in shared

        for reviewer in ("persona-security", "python-review"):
            _invoke_tool(
                tools,
                "submit_specialist_findings",
                reviewer_id=reviewer,
                code_suggestions=[finding] if reviewer == "python-review" else [],
                overall_assessment="done",
                additional_evidence=[{"source": "src/example.py:1"}],
            )

        status = _invoke_tool(tools, "get_specialist_findings")
        assert '"missing": []' in status
        _invoke_tool(
            tools,
            "write_review_findings",
            code_suggestions=[],
            overall_assessment="done",
            recommended_action="NEEDS_DISCUSSION",
        )
        return "done"

    monkeypatch.setattr(improve_nodes, "call_llm_agentic", fake_agentic)
    result = ExecuteCodeReview().exec({
        "changed_files": [{"path": "src/example.py", "status": "M"}],
        "source_branch": "feature",
        "target_branch": "main",
        "mr_title": "Change example",
        "mr_description": "",
        "local_repo_path": "/repo",
        "skill_folders": ["python-review"],
        "review_personas": [{
            "id": "security",
            "name": "Security",
            "instructions": "Check trust boundaries.",
        }],
        "graph_tools_available": False,
        "min_confidence": 0.7,
    })

    assert len(result["review_comments"]) == 1
    assert result["review_comments"][0]["relevant_file"] == "src/example.py"


def test_final_submission_rejects_missing_specialist(monkeypatch):
    def fake_agentic(prompt, tools, **kwargs):
        _invoke_tool(tools, "publish_review_context", context={"evidence": []})
        _invoke_tool(
            tools,
            "write_review_findings",
            code_suggestions=[],
            overall_assessment="done",
            recommended_action="APPROVED",
        )

    monkeypatch.setattr(improve_nodes, "call_llm_agentic", fake_agentic)
    with pytest.raises(ValueError, match="Missing required specialist"):
        ExecuteCodeReview().exec({
            "changed_files": [{"path": "src/example.py", "status": "M"}],
            "source_branch": "feature",
            "target_branch": "main",
            "mr_title": "Change example",
            "mr_description": "",
            "local_repo_path": "/repo",
            "skill_folders": ["python-review"],
            "review_personas": [],
            "graph_tools_available": False,
        })
