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

import json

import pytest

from flows.code_reviews.improve.nodes import ExecuteCodeReview, inherit_duplicate_personas
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

    assert "Never alternate one diff read and one graph query per turn" in prompt
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


@pytest.mark.parametrize("diff_size, inlined", [(100, True), (improve_nodes._INLINE_DIFFS_MAX_CHARS + 1, False)])
def test_specialists_get_scoped_tools_and_small_diffs_inline(monkeypatch, tmp_path, diff_size, inlined):
    (tmp_path / ".diffs" / "src").mkdir(parents=True)
    (tmp_path / ".diffs" / "src" / "example.py.diff").write_text("+" * diff_size)
    seen = {}

    def fake_agentic(prompt, tools, **kwargs):
        seen["agents"] = kwargs["custom_agents"]
        _invoke_tool(tools, "publish_review_context", context={"evidence": []})
        seen["context"] = json.loads(_invoke_tool(tools, "read_review_context", context_id="review-context"))
        raise RuntimeError("stop")

    monkeypatch.setattr(improve_nodes, "call_llm_agentic", fake_agentic)
    with pytest.raises(RuntimeError, match="stop"):
        ExecuteCodeReview().exec({
            "changed_files": [{"path": "src/example.py", "status": "M"}],
            "source_branch": "feature", "target_branch": "main",
            "mr_title": "t", "mr_description": "", "local_repo_path": str(tmp_path),
            "skill_folders": ["python-review"],
            "review_personas": [{"id": "security", "name": "Security", "instructions": "x"}],
            "graph_tools_available": False,
        })

    for agent in seen["agents"]:
        assert "submit_specialist_findings" in agent["tools"]
        assert not {"task", "edit", "write_review_findings"} & set(agent["tools"])
    assert ("staged_diffs" in seen["context"]) == inlined
    if inlined:
        assert seen["context"]["staged_diffs"] == {"src/example.py": "+" * diff_size}


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


def test_persona_findings_are_attributed_and_skill_findings_stay_general(monkeypatch):
    def finding(path, persona_id=None):
        value = {
            "relevant_file": path, "language": "python", "line_number": 1, "lines_to_replace": 1,
            "existing_code": "old()", "suggestion_content": f"Issue in {path}", "improved_code": "new()",
            "one_sentence_summary": "Fix", "label": "possible bug", "priority": "medium", "confidence": 0.9,
        }
        if persona_id:
            value["persona_id"] = persona_id
        return value

    def fake_agentic(prompt, tools, **kwargs):
        _invoke_tool(tools, "publish_review_context", context={"evidence": []})
        _invoke_tool(tools, "submit_specialist_findings", reviewer_id="persona-netra",
                     code_suggestions=[finding("persona.py")])
        _invoke_tool(tools, "submit_specialist_findings", reviewer_id="python-review",
                     code_suggestions=[finding("skill.py", persona_id="spoofed")])
        _invoke_tool(tools, "write_review_findings", code_suggestions=[finding("base.py")])

    monkeypatch.setattr(improve_nodes, "call_llm_agentic", fake_agentic)
    result = ExecuteCodeReview().exec({
        "changed_files": [{"path": "base.py", "status": "M"}],
        "source_branch": "feature", "target_branch": "main", "mr_title": "t", "mr_description": "",
        "local_repo_path": "/repo", "skill_folders": ["python-review"],
        "review_personas": [{"id": "netra", "name": "Netra", "instructions": "Check auth.", "version": 4}],
        "graph_tools_available": False, "min_confidence": 0.7,
    })

    attribution = {c["relevant_file"]: (c.get("persona_id"), c.get("persona_version"), c.get("persona_name"))
                   for c in result["review_comments"]}
    assert attribution == {
        "base.py": (None, None, None),
        "persona.py": ("netra", 4, "Netra"),
        "skill.py": (None, None, None),
    }


def test_retained_duplicate_inherits_persona_attribution():
    comments = [
        {"relevant_file": "a.py"},
        {"relevant_file": "a.py", "persona_id": "sai", "persona_version": 1},
        {"relevant_file": "a.py", "persona_id": "netra", "persona_version": 2, "persona_name": "Netra"},
        {"relevant_file": "b.py", "persona_id": "netra", "persona_version": 2},
    ]

    inherit_duplicate_personas(
        comments,
        {"new-1": "new-0", "new-2": "new-0", "new-3": "prev-0"},
        ["netra", "sai"],
    )

    assert (comments[0]["persona_id"], comments[0]["persona_version"], comments[0]["persona_name"]) == ("netra", 2, "Netra")
    assert "persona_id" in comments[3]


def test_review_summary_prompt_requires_batched_lookups():
    from flows.code_reviews.review.prompts import build_review_summary_prompt
    prompt = build_review_summary_prompt(
        "t", "", "feature", "main", [{"path": "src/example.py", "status": "M"}], "/repo")
    assert "issue them in ONE response" in prompt
    assert "Never spend a turn on a single grep" in prompt


def test_comment_analysis_reads_only_commented_diffs_up_front():
    from flows.code_reviews.review.prompts import build_previous_comment_analysis_prompt
    prompt = build_previous_comment_analysis_prompt(
        [{"file_path": "src/a.py", "line_number": 3}, {"file_path": "src/a.py"}, {"body": "general"}],
        "/repo", "/repo/.diffs",
        changed_files=[{"path": "src/a.py"}, {"path": "src/helper.py"}],
    )
    first_read = next(line for line in prompt.splitlines() if line.startswith("cat /repo/.comments") and ".diff" in line)
    assert first_read == "cat /repo/.comments/prev_comments.yaml /repo/.diffs/src/a.py.diff"
    assert "- `/repo/.diffs/src/helper.py.diff`" in prompt


def test_specialist_findings_in_another_shape_are_rejected_not_silently_dropped(monkeypatch):
    from pydantic import ValidationError

    def fake_agentic(prompt, tools, **kwargs):
        _invoke_tool(tools, "publish_review_context", context={})
        with pytest.raises(ValidationError, match="relevant_file"):
            _invoke_tool(tools, "submit_specialist_findings", reviewer_id="persona-security", code_suggestions=[{
                "file": "src/example.py", "line": 1, "title": "Bug", "description": "Broken", "severity": "high",
            }])
        raise RuntimeError("stop")

    monkeypatch.setattr(improve_nodes, "call_llm_agentic", fake_agentic)
    with pytest.raises(RuntimeError, match="stop"):
        ExecuteCodeReview().exec({
            "changed_files": [{"path": "src/example.py", "status": "M"}],
            "source_branch": "f", "target_branch": "m", "mr_title": "t", "mr_description": "",
            "local_repo_path": "/repo", "skill_folders": [],
            "review_personas": [{"id": "security", "name": "Security", "instructions": "x"}],
            "graph_tools_available": False,
        })
