from unittest.mock import Mock, patch
from threading import Barrier

import pytest

from flows.code_reviews.smart_diff.flow import create_smart_diff_flow
from flows.code_reviews.smart_diff.main import parser
from flows.code_reviews.smart_diff.nodes import (
    GenerateSmartDiffArtifacts, OrganizeSmartDiff, build_focus_group, enumerate_units, organize,
    send_callback, send_comment_assessments,
)


def unit(hunk_id, path="src/A.java", old=1, old_end=3, new=2, new_end=5):
    return {
        "id": hunk_id, "filePath": path, "oldStartLine": old, "oldEndLine": old_end,
        "newStartLine": new, "newEndLine": new_end, "patch": "@@ diff",
    }


def test_enumerate_units_maps_stable_hunk_and_whole_file_selectors():
    outputs = iter([
        "base\n",
        "diff --git a/src/A.java b/src/A.java\n--- a/src/A.java\n+++ b/src/A.java\n"
        "@@ -10,2 +12,3 @@\n-old\n+new\n"
        "diff --git a/logo.png b/logo.png\nBinary files differ\n",
        "src/A.java\nlogo.png\n",
    ])
    with patch("flows.code_reviews.smart_diff.nodes.git", side_effect=lambda *_: next(outputs)):
        merge_base, units, changed = enumerate_units("repo", "main")

    assert merge_base == "base"
    assert [(entry["id"], entry["filePath"], entry["oldStartLine"], entry["oldEndLine"],
             entry["newStartLine"], entry["newEndLine"])
            for entry in units] == [
        ("h00001", "src/A.java", 10, 11, 12, 14),
        ("h00002", "logo.png", None, None, None, None),
    ]
    assert changed == [{"path": "src/A.java", "status": "M"}, {"path": "logo.png", "status": "M"}]


def test_organize_keeps_story_chapters_and_maps_complete_hunk_ranges():
    result = {
        "summary": "Follow the contract into the implementation.",
        "groups": [{
            "title": "Contract first", "rationale": "Understand the boundary before its consumers.",
            "steps": [{"title": "Review API", "explanation": "Check the contract first.",
                       "hunkIds": ["h00001", "h00002"]}],
        }],
    }
    llm = Mock(return_value=__import__("json").dumps(result))
    units = [unit("h00001"), unit("h00002", path="src/B.java")]
    units[0]["patch"] = "first complete patch"
    units[1]["patch"] = "second complete patch"

    summary, groups = organize(units, "Review signals", llm=llm)

    assert llm.call_count == 1
    assert "first complete patch" in llm.call_args.args[0]
    assert "second complete patch" in llm.call_args.args[0]
    assert summary == "Follow the contract into the implementation."
    assert groups[0]["title"] == "Contract first"
    assert groups[0]["steps"][0]["changes"] == [{
        "filePath": "src/A.java", "oldStartLine": 1, "oldEndLine": 3,
        "newStartLine": 2, "newEndLine": 5,
    }, {
        "filePath": "src/B.java", "oldStartLine": 1, "oldEndLine": 3,
        "newStartLine": 2, "newEndLine": 5,
    }]


def test_build_focus_group_maps_review_issues_to_diff_ranges_and_skips_invalid_ones():
    review = {
        "estimated_effort": 2, "relevant_tests": "Yes", "security_concerns": "No",
        "key_issues": [
            {"relevant_file": "src/A.java", "issue_header": "Null handling",
             "issue_content": "Check the nullable response.", "start_line": 3, "end_line": 4},
            {"relevant_file": "src/B.java", "issue_header": "Not changed",
             "issue_content": "Ignore this hallucinated range.", "start_line": 8, "end_line": 9},
        ],
    }

    group = build_focus_group(review, [unit("h00001")])

    assert group["id"] == "review-focus"
    assert group["steps"] == [{
        "id": "focus-1", "title": "Null handling", "explanation": "Check the nullable response.",
        "changes": [{"filePath": "src/A.java", "oldStartLine": None, "oldEndLine": None,
                     "newStartLine": 3, "newEndLine": 4}],
    }]


def test_build_focus_group_preserves_full_issue_details():
    explanation = "Full reviewer context. " * 20
    review = {"key_issues": [{
        "relevant_file": "src/A.java", "issue_header": "Retry behavior",
        "issue_content": explanation, "start_line": 3, "end_line": 4,
    }]}

    group = build_focus_group(review, [unit("h00001")])

    assert group["steps"][0]["explanation"] == explanation.strip()


def test_organizer_keeps_focus_areas_out_of_story_context():
    shared = {
        "review_data": {
            "relevant_tests": "Yes",
            "key_issues": [{"relevant_file": "src/A.java", "issue_header": "Inline only",
                            "issue_content": "INLINE_ONLY", "start_line": 3, "end_line": 4}],
        },
        "impact_context": "", "jira_file_path": "", "rfc_content": "", "units": [unit("h00001")],
    }
    with patch("flows.code_reviews.smart_diff.nodes.organize", return_value=("Story", [])) as organizer:
        result = OrganizeSmartDiff().exec(shared)

    context = organizer.call_args.args[1]
    assert "INLINE_ONLY" not in context
    assert '"relevant_tests": "Yes"' in context
    assert result["smart_diff_groups"][0]["id"] == "review-focus"


def callback_shared(arnold_url="https://arnold.example", dry_run=False):
    return {
        "arnold_url": arnold_url, "project_id": 10, "merge_request_iid": 12,
        "execution_id": "123e4567-e89b-42d3-a456-426614174000",
        "expected_head_sha": "a" * 40, "dry_run": dry_run,
    }


def test_callback_sends_execution_lifecycle_without_reviewer_token():
    shared = callback_shared()
    response = Mock()
    with patch("flows.code_reviews.smart_diff.nodes.ArnoldAuth.get_auth_token", return_value="Bearer token"), \
            patch("flows.code_reviews.smart_diff.nodes.requests.put", return_value=response) as put:
        send_callback(shared, "running")

    response.raise_for_status.assert_called_once()
    assert put.call_args.kwargs["json"] == {
        "executionId": shared["execution_id"], "headSha": "a" * 40, "status": "running", "groups": [],
        "assessedCommentCount": 0,
    }
    assert put.call_args.kwargs["headers"] == {"Authorization": "Bearer token"}


def test_local_callback_skips_only_arnold_authentication():
    shared = callback_shared("http://localhost:8090")
    response = Mock()
    with patch("flows.code_reviews.smart_diff.nodes.ArnoldAuth.get_auth_token") as auth, \
            patch("flows.code_reviews.smart_diff.nodes.requests.put", return_value=response) as put:
        send_callback(shared, "running")

    auth.assert_not_called()
    assert put.call_args.kwargs["headers"] == {}


def test_dry_run_prints_result_without_calling_arnold(capsys):
    with patch("flows.code_reviews.smart_diff.nodes.requests.put") as put:
        send_callback(callback_shared(dry_run=True), "completed", summary="Overview", groups=[{"id": "g1"}])

    put.assert_not_called()
    assert __import__("json").loads(capsys.readouterr().out) == {
        "summary": "Overview", "groups": [{"id": "g1"}],
    }


def test_flow_connects_smart_diff_work_as_pocketflow_nodes():
    node = create_smart_diff_flow().start
    names = []
    while node:
        names.append(type(node).__name__)
        node = node.successors.get("default")

    assert names == [
        "StartSmartDiff", "CloneRepository", "CheckoutBranch", "GetSmartDiffChanges",
        "GetImpactContext", "FetchJiraTickets", "FetchRFCContent", "GenerateSmartDiffArtifacts",
        "CompleteSmartDiff", "Cleanup",
    ]


def test_generation_runs_story_and_assessment_in_parallel():
    barrier = Barrier(2)
    shared = callback_shared(dry_run=True)

    def story_run(_node, branch):
        barrier.wait(timeout=1)
        branch["review_data"] = {"estimated_effort": 1}

    def organize_run(_node, branch):
        branch.update(smart_diff_summary="Story", smart_diff_groups=[{"id": "g1"}])

    def comments_run(_node, branch):
        barrier.wait(timeout=1)
        branch.update(comment_fetch_succeeded=True, previous_review_comments=[])

    def assessment_run(_node, branch):
        branch.update(comment_analysis_succeeded=True, comment_analysis_results=[])

    with patch("flows.code_reviews.review.nodes.ExecuteReviewSummary.run", story_run), \
            patch("flows.code_reviews.smart_diff.nodes.OrganizeSmartDiff.run", organize_run), \
            patch("flows.code_reviews.common.nodes.FetchMRReviewStatus.run", comments_run), \
            patch("flows.code_reviews.review.nodes.AnalyzePreviousCommentStatus.run", assessment_run):
        result = GenerateSmartDiffArtifacts().exec(shared)

    assert result == {
        "smart_diff_summary": "Story", "smart_diff_groups": [{"id": "g1"}],
        "comment_assessment_status": "completed", "assessed_comment_count": 0,
    }


def test_comment_assessments_are_tagged_with_expected_sha():
    shared = callback_shared()
    shared.update({
        "merge_request_id": 99, "mr_author_id": 7,
        "comment_fetch_succeeded": True, "comment_analysis_succeeded": True,
        "previous_review_comments": [{
            "note_id": 21, "discussion_id": "discussion-1", "body": "Please fix this",
        }],
        "comment_analysis_results": [{
            "comment_id": "comment-0", "status": "applied", "score": 0.9,
            "reasoning": "Fixed",
        }],
    })
    response = Mock()
    response.json.return_value = {"success": True, "response": 1}
    with patch("flows.code_reviews.smart_diff.nodes.ArnoldAuth.get_auth_token", return_value="Bearer token"), \
            patch("flows.code_reviews.smart_diff.nodes.requests.post", return_value=response) as post:
        assert send_comment_assessments(shared) == 1

    assert post.call_args.kwargs["json"]["metadata"]["assessed_sha"] == "a" * 40
    assert post.call_args.kwargs["json"]["comments"][0]["status"] == "applied"


def test_parser_requires_pipeline_identity_inputs():
    with pytest.raises(SystemExit):
        parser().parse_args(["--mr-link", "https://gitlab.example/a/-/merge_requests/1"])


def test_parser_accepts_dry_run_without_arnold_url():
    args = parser().parse_args([
        "--mr-link", "https://gitlab.example/a/-/merge_requests/1",
        "--project-id", "10", "--mr-iid", "1", "--expected-head-sha", "a" * 40,
        "--execution-id", "123e4567-e89b-42d3-a456-426614174000", "--dry-run",
    ])

    assert args.dry_run
