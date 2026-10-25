import json
from types import SimpleNamespace
from unittest.mock import Mock, patch

import pytest
import tempfile

from flows.code_reviews.group_review import main as group_main
from flows.code_reviews.group_review import nodes as group
from flows.code_reviews.group_review.prompts import (
    GROUP_REVIEW_SYSTEM_MESSAGE,
    build_combined_correction_prompt,
    build_combined_review_prompt,
    build_group_member_context,
)


def members():
    return [dict(mergeRequestId=f"gitlab:{i}:1", projectId=i, iid=1, repositoryPath=f"team/repo{i}",
                 webUrl=f"https://gitlab.example/team/repo{i}/-/merge_requests/1", title="Change", description="",
                 sourceBranch="feature", targetBranch="main", baseSha="a" * 40, startSha="b" * 40,
                 headSha=str(i) * 40) for i in (1, 2)]


def changes():
    return {member["mergeRequestId"]: [dict(new_path="api.py", old_path="api.py", status="M",
                                         diff="@@ -1,2 +1,2 @@\n-old\n+new\n context\n")]
            for member in members()}


def raw(mid="gitlab:1:1", **kwargs):
    return dict(mergeRequestId=mid, relevant_file="api.py", line=1, suggestion_content="Incompatible contract",
                label="possible bug", **kwargs)


def test_same_paths_are_qualified_by_mr_and_findings_are_deduplicated():
    findings = group.normalize_findings([raw(), raw(), raw("gitlab:2:1")], members(), changes(), "run")
    assert len(findings) == 2
    assert findings[0]["id"] != findings[1]["id"]
    assert findings[0]["headSha"] == "1" * 40
    assert findings[0]["newLine"] == 1
    assert "arnold-group:run:" in findings[0]["body"]


def test_cross_mr_finding_links_evidence_and_invalid_anchor_becomes_general_discussion():
    comment = raw(relatedMergeRequestIds=["gitlab:2:1"])
    comment.update(line=999, improved_code="danger", existing_code="old", lines_to_replace=1)
    finding = group.normalize_findings([comment], members(), changes(), "run")[0]
    assert finding["newLine"] is None
    assert members()[1]["webUrl"] in finding["body"]
    assert "```suggestion" not in finding["body"]


@pytest.mark.parametrize("field,value", [("relevant_file", "../api.py"), ("relevant_file", "unknown.py"),
                                         ("relatedMergeRequestIds", ["gitlab:99:1"]), ("confidence", float("nan"))])
def test_invalid_evidence_is_rejected(field, value):
    comment = raw()
    comment[field] = value
    with pytest.raises(ValueError):
        group.normalize_findings([comment], members(), changes(), "run")


def test_freshness_checks_all_diff_revisions_and_not_only_head():
    member = members()[0]
    mr = SimpleNamespace(state="opened", target_branch="main", diff_refs=dict(
        base_sha="changed", start_sha=member["startSha"], head_sha=member["headSha"]))
    with patch.object(group, "member_mr", return_value=mr), pytest.raises(group.StaleRun):
        group.ensure_fresh([member], "token")


def callback():
    cb = Mock(run_id="run")
    cb.manifest.return_value = dict(run=dict(runId="run", members=members(), memberStatuses={}, findings=[]),
                                    context={}, reviewPersonas=[], publicationOnly=False)
    return cb


def test_failed_member_withholds_publication_and_cleans_workspace():
    cb = callback()
    roots = []

    def prepare(member, context, personas, root, index):
        roots.append(root)
        if index == 1:
            raise RuntimeError("checkout failed")
        return {}, []

    with patch.object(group, "ensure_fresh"), patch.object(group, "prepare", side_effect=prepare), \
            patch.object(group, "publish") as publish, pytest.raises(RuntimeError):
        group_main.execute(cb, "token")
    publish.assert_not_called()
    assert not roots[0].exists()
    assert cb.update.call_args.args[0] == "failed"
    assert cb.update.call_args.args[1]["gitlab:2:1"] == "failed"


def test_repeated_worker_claim_failure_never_starts_work():
    cb = callback()
    cb.update.side_effect = RuntimeError("another worker claimed this run")
    with patch.object(group, "prepare") as prepare, pytest.raises(RuntimeError):
        group_main.execute(cb, "token")
    prepare.assert_not_called()


@pytest.mark.parametrize("failure_stage", ["member", "combined", "filter"])
def test_failed_review_never_saves_drafts_for_publication_retry(failure_stage):
    cb = callback()
    prepared = {m["mergeRequestId"]: dict(local_repo_path="/repo", changed_files=[{"path": "api.py"}])
                for m in members()}

    def review(shared):
        if failure_stage == "member" and shared is prepared["gitlab:2:1"]:
            raise RuntimeError("review failed")
        shared.update(review_findings={"summary": "Reviewed"}, review_comments=[raw()])

    with patch.object(group, "git"), patch.object(group, "ensure_fresh"), \
            patch.object(group, "discussion_comments", return_value=[]), \
            patch.object(group, "prepare", side_effect=lambda m, *_: (prepared[m["mergeRequestId"]], changes()[m["mergeRequestId"]])), \
            patch.object(group.ExecuteCodeReview, "run", side_effect=review), \
            patch.object(group, "combined_review", return_value={"findings": [raw()]},
                         side_effect=RuntimeError("review failed") if failure_stage == "combined" else None), \
            patch.object(group.FilterDuplicateComments, "run",
                         side_effect=RuntimeError("review failed") if failure_stage == "filter" else None), \
            patch.object(group, "publish") as publish, pytest.raises(RuntimeError, match="review failed"):
        group_main.execute(cb, "token")

    publish.assert_not_called()
    assert cb.update.call_args.args[0] == "failed"
    # Checkpoints are separate from findings that are ready for publication.
    assert all(call.args[2] == [] for call in cb.update.call_args_list)


def test_publication_routes_primary_mr_and_reconciles_existing_marker():
    findings = group.normalize_findings([raw(), raw("gitlab:2:1")], members(), changes(), "run")
    mrs = {member["mergeRequestId"]: Mock() for member in members()}
    mrs["gitlab:1:1"].discussions.list.return_value = [SimpleNamespace(id="discussion", attributes={"notes": [{"id": 7, "body": findings[0]["body"]}]})]
    mrs["gitlab:2:1"].discussions.list.return_value = []
    mrs["gitlab:2:1"].discussions.create.return_value.attributes = {"notes": [{"id": 8}]}
    cb = callback()
    with patch.object(group, "ensure_fresh", return_value=mrs):
        group.publish(findings, members(), "token", cb, {})
    mrs["gitlab:1:1"].discussions.create.assert_not_called()
    payload = mrs["gitlab:2:1"].discussions.create.call_args.args[0]
    assert payload["position"]["head_sha"] == "2" * 40
    assert [finding["publicationStatus"] for finding in findings] == ["posted", "posted"]
    assert findings[0]["commentUrl"].endswith("#note_7")


def test_uncertain_publication_is_not_blindly_repeated():
    findings = group.normalize_findings([raw()], members(), changes(), "run")
    findings[0]["publicationStatus"] = "unknown"
    mr = Mock()
    mr.discussions.list.return_value = []
    with patch.object(group, "ensure_fresh", return_value={"gitlab:1:1": mr}):
        group.publish(findings, members(), "token", callback(), {})
    mr.discussions.create.assert_not_called()
    assert findings[0]["publicationStatus"] == "unknown"


def test_revision_drift_stops_all_publication():
    findings = group.normalize_findings([raw()], members(), changes(), "run")
    with patch.object(group, "ensure_fresh", side_effect=group.StaleRun("changed")), pytest.raises(group.StaleRun):
        group.publish(findings, members(), "token", callback(), {})
    assert findings[0]["publicationStatus"] == "pending"


def test_publication_retry_uses_saved_findings_without_llm():
    cb = callback()
    cb.manifest.return_value["publicationOnly"] = True
    cb.manifest.return_value["run"].update(memberStatuses={m["mergeRequestId"]: "reviewed" for m in members()})
    with patch.object(group, "ensure_fresh"), patch.object(group, "prepare") as prepare, \
            patch.object(group, "combined_review") as review, patch.object(group, "publish") as publish:
        group_main.execute(cb, "token")
    prepare.assert_not_called()
    review.assert_not_called()
    publish.assert_called_once()
    assert cb.update.call_args.args[0] == "completed"


@pytest.mark.parametrize("saved_comments", [[], [raw()]])
def test_retry_reuses_completed_members_and_original_context(saved_comments):
    cb = callback()
    first, second = members()
    context = {"rfcUrl": "https://docs.example/original", "jiraTickets": ["ACME-0"],
               "reviewContext": "Original feature context"}
    personas = [{"id": "security", "name": "Security", "instructions": "Original instructions", "version": 2}]
    cb.manifest.return_value.update(context=context, reviewPersonas=personas, memberReviews={
        first["mergeRequestId"]: {"headSha": first["headSha"], "findings": saved_comments},
    })
    cb.manifest.return_value["run"]["memberStatuses"] = {first["mergeRequestId"]: "reviewed", second["mergeRequestId"]: "failed"}
    prepared = {m["mergeRequestId"]: dict(local_repo_path="/repo", changed_files=[{"path": "api.py"}]) for m in members()}

    def prepare(member, received_context, received_personas, *_):
        assert received_context == context
        assert received_personas == personas
        return prepared[member["mergeRequestId"]], changes()[member["mergeRequestId"]]

    def review(shared):
        assert shared is prepared[second["mergeRequestId"]]
        shared.update(review_findings={"summary": "Reviewed"}, review_comments=[raw(second["mergeRequestId"])])

    def combined(root, reviewed_members, findings):
        assert len(findings) == len(saved_comments) + 1
        assert findings[-1]["memberFindingId"] == f"{second['mergeRequestId']}:member-0"
        if saved_comments:
            assert findings[0]["suggestion_content"] == saved_comments[0]["suggestion_content"]
        return {"findings": []}

    with patch.object(group, "ensure_fresh"), patch.object(group, "git"), \
            patch.object(group, "prepare", side_effect=prepare), \
            patch.object(group.ExecuteCodeReview, "run", side_effect=review) as reviewer, \
            patch.object(group, "combined_review", side_effect=combined) as combined_reviewer, \
            patch.object(group, "discussion_comments", return_value=[]), \
            patch.object(group, "publish"):
        group_main.execute(cb, "token")
    reviewer.assert_called_once()
    combined_reviewer.assert_called_once()
    checkpoint = next(call.kwargs["member_reviews"] for call in cb.update.call_args_list if "member_reviews" in call.kwargs)
    assert checkpoint[first["mergeRequestId"]]["findings"] == saved_comments
    assert second["mergeRequestId"] in checkpoint
    assert any(call.kwargs.get("analysis_complete") is True for call in cb.update.call_args_list)
    assert cb.update.call_args.args[0] == "completed"


def test_retry_rejects_checkpoint_from_another_revision():
    cb = callback()
    cb.manifest.return_value["memberReviews"] = {"gitlab:1:1": {"headSha": "old", "findings": []}}
    with patch.object(group, "prepare") as prepare, pytest.raises(ValueError, match="pinned revision"):
        group_main.execute(cb, "token")
    prepare.assert_not_called()


def test_analysis_finishes_all_members_and_combined_pass_before_publication():
    cb = callback()
    events = []
    prepared = {m["mergeRequestId"]: dict(local_repo_path="/repo", changed_files=[{"path": "api.py"}], review_comments=[], review_findings={}) for m in members()}
    mrs = {m["mergeRequestId"]: Mock() for m in members()}
    for mr in mrs.values():
        mr.discussions.list.return_value = []

    def review(shared):
        events.append("member")
        shared["review_findings"] = {"summary": "Reviewed"}

    def combined(*_):
        events.append("combined")
        return {"summary": "Contract mismatch", "findings": [raw()]}

    with patch.object(group, "git"), patch.object(group, "ensure_fresh", return_value=mrs), \
            patch.object(group, "prepare", side_effect=lambda m, *_: (prepared[m["mergeRequestId"]], changes()[m["mergeRequestId"]])), \
            patch.object(group.ExecuteCodeReview, "run", side_effect=review), \
            patch.object(group.FilterDuplicateComments, "run"), \
            patch.object(group, "combined_review", side_effect=combined), \
            patch.object(group, "publish", side_effect=lambda *_: events.append("publish")):
        group_main.execute(cb, "token")
    assert events == ["member", "member", "combined", "publish"]


def test_callback_retries_with_the_same_sequence_after_timeout():
    cb = group.Callback("https://arnold.example", "run", "execution")
    with patch.object(cb, "request", side_effect=[group.requests.Timeout(), {}]) as request:
        cb.update("running", {}, [], member_reviews={"gitlab:1:1": {"headSha": "head", "findings": []}},
                  analysis_complete=True)
    assert request.call_args_list[0].kwargs["json"] == request.call_args_list[1].kwargs["json"]
    assert "summary" not in request.call_args_list[0].kwargs["json"]
    assert request.call_args.kwargs["json"]["memberReviews"]["gitlab:1:1"]["findings"] == []
    assert request.call_args.kwargs["json"]["analysisComplete"] is True


def test_combined_review_requires_all_members_and_submits_contract_issue(tmp_path):
    reviewed_members = members()
    for index, member in enumerate(reviewed_members):
        member["checkout"] = f"member-{index}"
        diff = tmp_path / member["checkout"] / ".diffs" / "api.py.diff"
        diff.parent.mkdir(parents=True)
        diff.write_text("@@ -1 +1 @@\n-old\n+new")

    member_findings = [
        dict(raw(), memberFindingId="gitlab:1:1:member-0"),
    ]
    finding = dict(
        findingId="cross-1",
        findingType="cross_member",
        mergeRequestId=reviewed_members[0]["mergeRequestId"],
        relatedMergeRequestIds=[reviewed_members[1]["mergeRequestId"]],
        relevant_file="api.py",
        line=1,
        suggestion_content="The client still expects the removed response field.",
        label="possible bug",
        priority="high",
        confidence=0.95,
        failureScenario="The consumer reads a field removed by the producer.",
        sourceFindingIds=["gitlab:1:1:member-0"],
        evidence=[
            dict(
                mergeRequestId=member["mergeRequestId"],
                checkout=member["checkout"],
                file="api.py",
                line=1,
                excerpt="new",
                role="producer" if index == 0 else "consumer",
            )
            for index, member in enumerate(reviewed_members)
        ],
    )

    def agent(prompt, tools, **kwargs):
        metadata = getattr(tools[0], "_clio_metadata", None) or tools[0].metadata
        with pytest.raises(ValueError, match="every member"):
            metadata.handler(metadata.params_model(
                reviewedMergeRequestIds=["gitlab:1:1"],
            ))
        with pytest.raises(ValueError, match="Reconcile every member finding"):
            metadata.handler(metadata.params_model(
                reviewedMergeRequestIds=[m["mergeRequestId"] for m in reviewed_members],
                findings=[finding],
                reconciliation=[],
            ))
        metadata.handler(metadata.params_model(
            reviewedMergeRequestIds=[m["mergeRequestId"] for m in reviewed_members],
            findings=[finding],
            reconciliation=[dict(
                memberFindingId="gitlab:1:1:member-0",
                action="merged",
                consolidatedFindingId="cross-1",
                reason="The member finding is part of the cross-member contract mismatch.",
            )],
        ))
        assert "producer/consumer contract matrix" in prompt
        assert "member_findings.json" in prompt
        assert kwargs["working_directory"] == str(tmp_path)
        assert "untrusted evidence" in kwargs["system_message"]

    with patch.object(group, "call_llm_agentic", side_effect=agent):
        result = group.combined_review(tmp_path, reviewed_members, member_findings)
    assert result["findings"][0]["mergeRequestId"] == "gitlab:1:1"
    staged = (tmp_path / "member_findings.json").read_text()
    assert "gitlab:1:1:member-0" in staged
    template = (tmp_path / "group_submission_template.json").read_text()
    examples = (tmp_path / "group_submission_examples.json").read_text()
    schema = (tmp_path / "group_submission_schema.json").read_text()
    assert "reviewedMergeRequestIds" in template
    assert '"failureScenario"' in template
    assert '"preservedMemberFinding"' in examples
    assert '"newCrossMemberFinding"' in examples
    assert '"generalDiscussionFinding"' in examples
    assert '"GroupEvidence"' in schema
    assert '"summary":' not in template
    assert '"overallRisk"' not in schema
    assert '"memberSummaries"' not in schema
    assert "summary" not in result


def test_combined_review_retries_with_template_after_missing_tool_call(tmp_path):
    reviewed_members = members()
    for index, member in enumerate(reviewed_members):
        member["checkout"] = f"member-{index}"

    calls = []

    def agent(prompt, tools, **kwargs):
        calls.append(prompt)
        if len(calls) == 1:
            return "I reviewed the group but forgot to submit the tool."
        metadata = getattr(tools[0], "_clio_metadata", None) or tools[0].metadata
        metadata.handler(metadata.params_model(
            reviewedMergeRequestIds=[member["mergeRequestId"] for member in reviewed_members],
            findings=[],
            reconciliation=[],
        ))
        return "Submitted."

    result = group.combined_review(tmp_path, reviewed_members, [], call_llm_fn=agent)

    assert result == {"findings": [], "reconciliation": []}
    assert len(calls) == 2
    assert "Correct the Combined Review Submission" in calls[1]
    assert "forgot to submit" in calls[1]


def test_group_submission_normalizes_logged_model_aliases():
    payload, changes = group.normalize_group_submission({
        "findings": [{
            "findingId": "finding-1",
            "primaryMergeRequestId": "gitlab:1:1",
            "relatedMergeRequestIds": [],
            "description": "The callback failure is discarded.",
            "failure_scenario": "A transient callback failure leaves the run pending.",
            "label": "critical",
            "evidence": {
                "merge_request_id": "gitlab:1:1",
                "checkoutDirectory": "member-0",
                "filePath": "api.py",
                "lineNumber": 1,
                "excerpt": "new",
                "role": "producer",
            },
        }],
    })

    finding = payload["findings"][0]
    assert finding["findingType"] == "member"
    assert finding["mergeRequestId"] == "gitlab:1:1"
    assert finding["suggestion_content"].startswith("The callback")
    assert finding["failureScenario"].startswith("A transient")
    assert finding["label"] == "critical bug"
    assert finding["evidence"] == [{
        "mergeRequestId": "gitlab:1:1",
        "checkout": "member-0",
        "file": "api.py",
        "line": 1,
        "excerpt": "new",
        "role": "producer",
    }]
    assert changes


def test_combined_correction_repairs_saved_cross_member_finding(tmp_path):
    reviewed_members = [dict(member, checkout=f"member-{index}")
                        for index, member in enumerate(members())]
    finding = dict(
        findingId="cross-1", findingType="cross_member",
        mergeRequestId=reviewed_members[0]["mergeRequestId"],
        relatedMergeRequestIds=[reviewed_members[1]["mergeRequestId"]],
        suggestion_content="The consumer still expects the removed field.",
        failureScenario="The consumer reads the producer's response.",
        label="possible bug", priority="high", confidence=0.95, evidence=[],
    )
    calls = []

    def agent(prompt, tools, **kwargs):
        calls.append(prompt)
        metadata = getattr(tools[0], "_clio_metadata", None) or tools[0].metadata
        if len(calls) == 1:
            with pytest.raises(ValueError, match="evidence"):
                metadata.handler(metadata.params_model(
                    reviewedMergeRequestIds=[m["mergeRequestId"] for m in reviewed_members],
                    findings=[finding], reconciliation=[],
                ))
            return "Submission failed."

        assert "group_submission_draft.json" in prompt
        assert "Targeted source/diff reads" in prompt
        payload = json.loads((tmp_path / "group_submission_draft.json").read_text())
        assert payload["findings"] == [finding]
        payload["findings"][0]["evidence"] = [
            dict(mergeRequestId=m["mergeRequestId"], checkout=m["checkout"],
                 excerpt="Verified contract excerpt", role="other")
            for m in reviewed_members
        ]
        metadata.handler(metadata.params_model(**payload))
        return "Submitted."

    result = group.combined_review(tmp_path, reviewed_members, [], call_llm_fn=agent)
    assert len(calls) == 2
    assert len(result["findings"]) == 1
    assert result["findings"][0]["suggestion_content"] == finding["suggestion_content"]


def test_correction_prompt_exposes_exact_nested_contract():
    prompt = build_combined_correction_prompt(
        [
            "Schema validation failed: findings.0.findingType: Field required",
            "Schema validation failed: findings.0.evidence: Input should be a valid list",
        ],
        "",
        2,
    )

    assert "correction attempt 2 of 2" in prompt
    assert "Read `group_submission_schema.json` first" in prompt
    assert "`mergeRequestId`" in prompt
    assert "`suggestion_content`" in prompt
    assert "`failureScenario`" in prompt
    assert "`evidence` must always be a JSON list" in prompt


def test_combined_review_allows_second_correction_attempt(tmp_path):
    reviewed_members = members()
    for index, member in enumerate(reviewed_members):
        member["checkout"] = f"member-{index}"

    calls = []

    def agent(prompt, tools, **kwargs):
        calls.append(prompt)
        if len(calls) < 3:
            return "No valid submission."
        metadata = getattr(tools[0], "_clio_metadata", None) or tools[0].metadata
        metadata.handler(metadata.params_model(
            reviewedMergeRequestIds=[member["mergeRequestId"] for member in reviewed_members],
            findings=[],
            reconciliation=[],
        ))

    result = group.combined_review(tmp_path, reviewed_members, [], call_llm_fn=agent)

    assert result == {"findings": [], "reconciliation": []}
    assert len(calls) == 3
    assert "correction attempt 2 of 2" in calls[2]


def test_collect_changes_uses_exact_revisions_and_handles_renames(tmp_path):
    member = members()[0]
    with patch.object(group, "git", side_effect=["R100\0old.py\0new.py\0", "patch"]) as git:
        changes = group.collect_changes(str(tmp_path), member)
    assert git.call_args_list[0].args[-2:] == (member["baseSha"], member["headSha"])
    assert changes[0]["old_path"] == "old.py"
    assert changes[0]["new_path"] == "new.py"
    assert (tmp_path / ".diffs/new.py.diff").read_text() == "patch"


def test_strict_dedup_can_drop_all_existing_findings():
    def agent(prompt, tools, **kwargs):
        metadata = getattr(tools[0], "_clio_metadata", None) or tools[0].metadata
        metadata.handler(metadata.params_model(
            keep_comment_ids=[],
            drop_comment_ids=["new-0"],
            duplicate_of={"new-0": "prev-0"},
        ))

    with patch("flows.code_reviews.improve.nodes.call_llm_agentic", side_effect=agent):
        result = group.FilterDuplicateComments().exec({
            "strict_review": True, "review_comments": [raw()], "review_findings": {},
            "existing_comments": [{"body": "Incompatible contract", "file": "", "line": None}],
        })
    assert result["review_comments"] == []


def test_group_prompts_require_rollout_evidence_and_member_ownership():
    member_context = build_group_member_context(
        [dict(member, checkout=f"member-{index}") for index, member in enumerate(members())],
        dict(members()[0], checkout="member-0"),
    )
    combined = build_combined_review_prompt()

    assert "Code-graph results apply only to the current checkout" in member_context
    assert "producer before consumer" in combined
    assert "Every memberFindingId must appear exactly once" in combined
    assert "group_submission_schema.json" in combined
    assert '"failureScenario"' in combined
    assert "Do not generate a narrative summary" in combined
    assert "untrusted evidence" in GROUP_REVIEW_SYSTEM_MESSAGE


def test_strict_dedup_fails_closed_without_result():
    with patch("flows.code_reviews.improve.nodes.call_llm_agentic", return_value=""), pytest.raises(RuntimeError):
        group.FilterDuplicateComments().exec({
            "strict_review": True, "review_comments": [raw(), raw()], "review_findings": {}, "existing_comments": [],
        })


def test_strict_review_does_not_convert_agent_failure_to_success():
    shared = dict(changed_files=[{"path": "api.py", "status": "M"}], source_branch="feature", target_branch="main",
                  mr_title="Change", mr_description="", local_repo_path="/repo", skill_folders=[],
                  review_personas=[], graph_tools_available=False, strict_review=True)
    with patch("flows.code_reviews.improve.nodes.call_llm_agentic", side_effect=RuntimeError("agent failed")), \
            pytest.raises(RuntimeError, match="agent failed"):
        group.ExecuteCodeReview().exec(shared)


def test_two_mrs_in_one_repository_have_isolated_pinned_checkouts(tmp_path):
    first, second = members()
    first.update(title="Saved title", description="Saved description")
    second.update(projectId=1, iid=2, repositoryPath=first["repositoryPath"])

    def clone(shared):
        shared["local_repo_path"] = tempfile.mkdtemp(dir=shared["clone_parent_dir"])

    with patch.dict("os.environ", {"REVIEW_CONTEXT": "Changed environment context"}), \
            patch.object(group, "resolve_args", side_effect=[
                ("https://gitlab.example/repo", "feature", "main", 1, member["iid"],
                 "https://gitlab.example", "token", "repo", {"mr_description": None})
                for member in (first, second)
            ]), patch.object(group.CloneRepository, "run", side_effect=clone), \
            patch.object(group, "git") as git, patch.object(group, "collect_changes", return_value=[]), \
            patch.object(group.GetImpactContext, "run"), patch.object(group.FetchJiraTickets, "run"), \
            patch.object(group.FetchRFCContent, "run"), patch.object(group.FetchSkills, "run"):
        left, _ = group.prepare(first, {"reviewContext": "Saved context"}, [], tmp_path, 0)
        right, _ = group.prepare(second, {}, [], tmp_path, 1)
    assert left["local_repo_path"] != right["local_repo_path"]
    assert left["head_sha"] == first["headSha"]
    assert right["head_sha"] == second["headSha"]
    assert left["mr_title"] == "Saved title"
    assert left["mr_description"] == "Saved description\n\nSaved context"
    assert right["mr_description"].strip() == ""
    checkouts = [call.args[-1] for call in git.call_args_list if "checkout" in call.args]
    assert checkouts == [first["headSha"], second["headSha"]]
