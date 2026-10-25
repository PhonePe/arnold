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

"""Nodes and helpers for reviewing pinned merge-request groups."""

import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import tempfile
from typing import Any, Literal
from urllib.parse import urlparse
import uuid

import gitlab
from utils import scm
from pocketflow import Node
from pydantic import BaseModel, Field, ValidationError
import requests

from flows.code_reviews.common.cli import add_common_args, build_shared_dict, resolve_args
from flows.code_reviews.common.nodes import CloneRepository, FetchJiraTickets, FetchRFCContent, GetImpactContext
from flows.code_reviews.improve.nodes import (
    ExecuteCodeReview,
    FetchSkills,
    FilterDuplicateComments,
    PostMergeRequestComments,
    discussion_comments,
)
from flows.code_reviews.group_review.prompts import (
    build_combined_correction_prompt,
    build_combined_review_prompt,
    build_group_member_context,
    GROUP_REVIEW_SYSTEM_MESSAGE,
)
from flows.code_reviews.smart_diff.nodes import git
from utils.call_llm import call_llm_agentic
from utils.arnold_auth import ArnoldAuth
from utils.tool_adapter import define_tool
from utils.logger import get_logger


logger = get_logger("group_review_nodes_logger")


class StaleRun(Exception):
    pass


class GroupEvidence(BaseModel):
    mergeRequestId: str = Field(min_length=1)
    checkout: str = Field(min_length=1)
    file: str | None = None
    line: int | None = Field(default=None, ge=1)
    symbol: str | None = None
    excerpt: str = Field(min_length=1, max_length=4000)
    role: Literal["producer", "consumer", "schema", "config", "rollout", "other"]


class GroupFinding(BaseModel):
    findingId: str = Field(min_length=1, max_length=200)
    findingType: Literal["member", "cross_member"]
    mergeRequestId: str = Field(min_length=1)
    relatedMergeRequestIds: list[str] = Field(default_factory=list)
    relevant_file: str | None = None
    line: int | None = Field(default=None, ge=1)
    suggestion_content: str = Field(min_length=1, max_length=12000)
    label: Literal[
        "security",
        "critical bug",
        "possible bug",
        "possible issue",
        "performance",
        "best practice",
        "maintainability",
        "enhancement",
        "typo",
        "rfc_violation",
    ]
    priority: Literal["high", "medium", "low"]
    confidence: float = Field(ge=0.7, le=1.0)
    language: str = ""
    existing_code: str = ""
    improved_code: str = ""
    lines_to_replace: int = Field(default=0, ge=0)
    one_sentence_summary: str = Field(default="", max_length=200)
    failureScenario: str = Field(min_length=1, max_length=4000)
    deploymentAssumption: str | None = Field(default=None, max_length=2000)
    sourceFindingIds: list[str] = Field(default_factory=list)
    evidence: list[GroupEvidence] = Field(min_length=1)


class FindingReconciliation(BaseModel):
    memberFindingId: str = Field(min_length=1)
    action: Literal["preserved", "merged", "rejected_duplicate", "rejected_invalid"]
    consolidatedFindingId: str | None = None
    reason: str = Field(min_length=1, max_length=2000)


class GroupReviewResult(BaseModel):
    reviewedMergeRequestIds: list[str] = Field(min_length=1)
    findings: list[GroupFinding] = Field(default_factory=list, max_length=500)
    reconciliation: list[FindingReconciliation] = Field(default_factory=list)


class GroupReviewSubmission(BaseModel):
    """Permissive wire model so validation failures reach our diagnostics."""

    reviewedMergeRequestIds: Any = None
    findings: Any = None
    reconciliation: Any = None


_GROUP_FINDING_ALIASES = {
    "primaryMergeRequestId": "mergeRequestId",
    "related_merge_request_ids": "relatedMergeRequestIds",
    "relevantFile": "relevant_file",
    "line_number": "line",
    "suggestionContent": "suggestion_content",
    "description": "suggestion_content",
    "body": "suggestion_content",
    "failure_scenario": "failureScenario",
    "source_finding_ids": "sourceFindingIds",
    "finding_type": "findingType",
}

_GROUP_LABEL_ALIASES = {
    "critical": "critical bug",
    "bug": "possible bug",
    "issue": "possible issue",
    "security issue": "security",
    "security vulnerability": "security",
    "rfc violation": "rfc_violation",
}


def normalize_group_submission(payload):
    """Normalize common model aliases before applying the strict contract."""
    normalized = deepcopy(payload)
    changes = []
    raw_findings = normalized.get("findings")
    if not isinstance(raw_findings, list):
        return normalized, changes

    for index, finding in enumerate(raw_findings):
        if not isinstance(finding, dict):
            continue
        for alias, canonical in _GROUP_FINDING_ALIASES.items():
            if canonical not in finding and alias in finding:
                finding[canonical] = finding.pop(alias)
                changes.append(f"findings.{index}.{alias}->{canonical}")

        related = finding.get("relatedMergeRequestIds")
        if "findingType" not in finding and isinstance(related, list):
            finding["findingType"] = "cross_member" if related else "member"
            changes.append(f"findings.{index}.findingType inferred")

        label = finding.get("label")
        if isinstance(label, str) and label.lower() in _GROUP_LABEL_ALIASES:
            finding["label"] = _GROUP_LABEL_ALIASES[label.lower()]
            changes.append(f"findings.{index}.label normalized")

        evidence = finding.get("evidence")
        if isinstance(evidence, dict):
            evidence_identity_keys = {
                "mergeRequestId",
                "merge_request_id",
                "checkout",
                "checkoutDirectory",
            }
            if evidence_identity_keys.intersection(evidence):
                finding["evidence"] = [evidence]
            elif evidence and all(isinstance(item, dict) for item in evidence.values()):
                finding["evidence"] = list(evidence.values())
            if isinstance(finding.get("evidence"), list):
                changes.append(f"findings.{index}.evidence converted to list")

        for evidence_index, item in enumerate(finding.get("evidence") or []):
            if not isinstance(item, dict):
                continue
            evidence_aliases = {
                "merge_request_id": "mergeRequestId",
                "checkoutDirectory": "checkout",
                "filePath": "file",
                "lineNumber": "line",
            }
            for alias, canonical in evidence_aliases.items():
                if canonical not in item and alias in item:
                    item[canonical] = item.pop(alias)
                    changes.append(
                        f"findings.{index}.evidence.{evidence_index}.{alias}->{canonical}"
                    )

    return normalized, changes


def _finding_example(member, finding_id, finding_type="member", related=None,
                     source_ids=None, anchored=True):
    related = related or []
    return {
        "findingId": finding_id,
        "findingType": finding_type,
        "mergeRequestId": member["mergeRequestId"],
        "relatedMergeRequestIds": related,
        "relevant_file": "<path from the primary member diff>" if anchored else None,
        "line": 1 if anchored else None,
        "suggestion_content": "<what is wrong, why it matters, and the required fix>",
        "label": "possible bug",
        "priority": "high",
        "confidence": 0.9,
        "language": "",
        "existing_code": "",
        "improved_code": "",
        "lines_to_replace": 0,
        "one_sentence_summary": "<brief finding title>",
        "failureScenario": "<specific input or rollout order that causes failure>",
        "deploymentAssumption": None,
        "sourceFindingIds": source_ids or [],
        "evidence": [{
            "mergeRequestId": member["mergeRequestId"],
            "checkout": member["checkout"],
            "file": "<exact evidence path or null>",
            "line": None,
            "symbol": None,
            "excerpt": "<exact excerpt or precise absence/search explanation>",
            "role": "producer",
        }],
    }


def _submission_examples(members, finding_ids):
    first = members[0]
    source_ids = [finding_ids[0]] if finding_ids else ["<exact memberFindingId>"]
    member_example = _finding_example(
        first, "member-finding-id", source_ids=source_ids
    )
    related = [members[1]["mergeRequestId"]] if len(members) > 1 else []
    cross_example = _finding_example(
        first,
        "cross-member-finding-id",
        finding_type="cross_member",
        related=related or ["<different exact mergeRequestId>"],
        source_ids=[],
    )
    if len(members) > 1:
        cross_example["evidence"].append({
            "mergeRequestId": members[1]["mergeRequestId"],
            "checkout": members[1]["checkout"],
            "file": "<exact evidence path or null>",
            "line": None,
            "symbol": None,
            "excerpt": "<exact consumer-side excerpt or absence explanation>",
            "role": "consumer",
        })
    general_example = _finding_example(
        first,
        "general-discussion-finding-id",
        source_ids=source_ids,
        anchored=False,
    )
    return {
        "preservedMemberFinding": member_example,
        "newCrossMemberFinding": cross_example,
        "generalDiscussionFinding": general_example,
    }


class Callback:
    def __init__(self, url, run_id, execution_id):
        self.url = f"{url.rstrip('/')}/group-analysis-callbacks/v1/{run_id}"
        self.execution_id = execution_id
        self.run_id = run_id
        self.worker_id = str(uuid.uuid4())
        self.sequence = 0

    def request(self, method, suffix="", **kwargs):
        response = requests.request(
            method,
            self.url + suffix,
            headers={"Authorization": ArnoldAuth.get_auth_token()},
            timeout=60,
            **kwargs,
        )
        response.raise_for_status()
        body = response.json()
        if not body.get("success"):
            raise RuntimeError("Arnold rejected group analysis request")
        return body["response"]

    def manifest(self):
        return self.request("GET", params={"executionId": self.execution_id})

    def fresh(self):
        if not self.request("GET", "/fresh", params={"executionId": self.execution_id}):
            raise StaleRun("Group membership or revisions changed; start a new analysis.")

    def update(self, status, states, findings, detail=None, *, member_reviews=None, analysis_complete=None):
        self.sequence += 1
        payload = dict(
            executionId=self.execution_id,
            workerId=self.worker_id,
            sequence=self.sequence,
            status=status,
            memberStatuses=states,
            findings=findings,
            detail=detail,
        )
        if member_reviews is not None:
            payload["memberReviews"] = member_reviews
        if analysis_complete is not None:
            payload["analysisComplete"] = analysis_complete
        for attempt in range(3):
            try:
                return self.request("PUT", json=payload)
            except (requests.Timeout, requests.ConnectionError):
                if attempt == 2:
                    raise


def member_mr(member, token):
    parsed = urlparse(member["webUrl"])
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError("Invalid member GitLab URL")
    client = scm.connect(f"{parsed.scheme}://{parsed.netloc}", private_token=token, timeout=60)
    return client.projects.get(member["projectId"]).mergerequests.get(member["iid"])


def ensure_fresh(members, token, member_mr_fn=None):
    member_mr_fn = member_mr_fn or member_mr
    result = {}
    for member in members:
        mr = member_mr_fn(member, token)
        refs = mr.diff_refs or {}
        if mr.state != "opened" or mr.target_branch != member["targetBranch"] or any(
                refs.get(key) != member[field] for key, field in
                (("base_sha", "baseSha"), ("start_sha", "startSha"), ("head_sha", "headSha"))):
            raise StaleRun("A member revision changed; automatic publication stopped.")
        result[member["mergeRequestId"]] = mr
    return result


def safe_path(path):
    if not isinstance(path, str) or not path or PurePosixPath(path).is_absolute() \
            or ".." in PurePosixPath(path).parts or "\\" in path:
        raise ValueError("Invalid repository-relative file path")
    return path


def collect_changes(repo, member, git_fn=None):
    git_fn = git_fn or git
    entries = iter(git_fn(
        repo,
        "diff",
        "--name-status",
        "-z",
        "--find-renames",
        member["baseSha"],
        member["headSha"],
    ).split("\0"))
    changes = []
    for status in entries:
        if not status:
            continue
        old_path = safe_path(next(entries))
        new_path = safe_path(next(entries)) if status.startswith(("R", "C")) else old_path
        patch = git_fn(
            repo,
            "diff",
            "--find-renames",
            "--unified=3",
            member["baseSha"],
            member["headSha"],
            "--",
            old_path,
            new_path,
        )
        changes.append(dict(old_path=old_path, new_path=new_path, diff=patch, status=status[0]))
        output = Path(repo, ".diffs", new_path + ".diff")
        if not output.resolve().is_relative_to(Path(repo).resolve()):
            raise ValueError("Diff output escapes the checkout")
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(patch)
    return changes


def prepare(member, context, personas, root, index):
    parser = argparse.ArgumentParser()
    add_common_args(parser)
    args = parser.parse_args(["--mr-link", member["webUrl"], "--dry-run"])
    resolved = resolve_args(args)
    shared = build_shared_dict(args, *resolved)
    if int(shared["project_id"]) != member["projectId"] or shared["merge_request_iid"] != member["iid"]:
        raise ValueError("Member URL and identity differ")
    shared.update(
        review_personas=personas,
        rfc_url=context.get("rfcUrl") or "",
        jira_tickets=",".join(context.get("jiraTickets") or []),
        flow_type="improve",
        strict_review=True,
        min_confidence=0.7,
        mr_title=member["title"],
        mr_description=member.get("description") or "",
    )
    shared["mr_description"] += "\n\n" + (context.get("reviewContext") or "")
    shared["clone_parent_dir"] = str(root)
    CloneRepository().run(shared)
    clone = Path(shared["local_repo_path"])
    destination = root / f"member-{index}"
    try:
        shutil.move(str(clone), destination)
    finally:
        if clone.exists():
            shutil.rmtree(clone)
    repo = str(destination)
    shared["local_repo_path"] = repo
    for sha in {member["baseSha"], member["headSha"], member["startSha"]}:
        if not re.fullmatch(r"[a-fA-F0-9]{40,64}", sha):
            raise ValueError("Invalid pinned revision")
        git(repo, "fetch", "origin", sha)
    git(repo, "checkout", "--detach", member["headSha"])
    shared.update(
        base_sha=member["baseSha"],
        merge_base=member["baseSha"],
        head_sha=member["headSha"],
        start_sha=member["startSha"],
        source_branch=member["headSha"],
        target_branch=member["baseSha"],
    )
    changes = collect_changes(repo, member)
    shared["changed_files"] = [dict(path=item["new_path"], status=item["status"]) for item in changes]
    shared["diffs_dir"] = str(destination / ".diffs")
    original_files = shared["changed_files"][:]
    GetImpactContext().run(shared)
    shared["changed_files"] = original_files
    FetchJiraTickets().run(shared)
    FetchRFCContent().run(shared)
    FetchSkills().run(shared)
    return shared, changes


def combined_review(root, members, findings, call_llm_fn=None):
    call_llm_fn = call_llm_fn or call_llm_agentic
    captured = {}
    root = Path(root)
    (root / "member_findings.json").write_text(
        json.dumps(findings, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    expected_members = {member["mergeRequestId"]: member for member in members}
    expected_finding_ids = {finding["memberFindingId"] for finding in findings}
    finding_examples = _submission_examples(
        members, sorted(expected_finding_ids)
    )
    validation_errors = []
    submission_template = {
        "reviewedMergeRequestIds": list(expected_members),
        "findings": [finding_examples["preservedMemberFinding"]],
        "reconciliation": [{
            "memberFindingId": finding_id,
            "action": "<preserved|merged|rejected_duplicate|rejected_invalid>",
            "consolidatedFindingId": "<final findingId, or null only for rejected_invalid>",
            "reason": "<concrete disposition>",
        } for finding_id in sorted(expected_finding_ids)],
    }
    (root / "group_submission_template.json").write_text(
        json.dumps(submission_template, indent=2),
        encoding="utf-8",
    )
    (root / "group_submission_examples.json").write_text(
        json.dumps(finding_examples, indent=2),
        encoding="utf-8",
    )
    (root / "group_submission_schema.json").write_text(
        json.dumps(GroupReviewResult.model_json_schema(), indent=2),
        encoding="utf-8",
    )

    def reject_submission(message):
        validation_errors.append(message)
        logger.warning("Combined review submission rejected: %s", message)
        raise ValueError(message)

    @define_tool(description="Submit the combined review after inspecting every member and their interactions.")
    def submit_group_review(result: GroupReviewSubmission) -> str:
        payload = result.model_dump(exclude_none=True)
        (root / "group_submission_draft.json").write_text(
            json.dumps(payload, indent=2), encoding="utf-8",
        )
        payload, normalization_changes = normalize_group_submission(payload)
        if normalization_changes:
            logger.info(
                "Normalized combined review submission fields: %s",
                normalization_changes,
            )
        try:
            validated = GroupReviewResult.model_validate(payload)
        except ValidationError as error:
            details = "; ".join(
                f"{'.'.join(str(part) for part in item['loc'])}: {item['msg']}"
                for item in error.errors()[:20]
            )
            reject_submission(f"Schema validation failed: {details}")

        reviewed_ids = validated.reviewedMergeRequestIds
        if len(reviewed_ids) != len(set(reviewed_ids)) or set(reviewed_ids) != set(expected_members):
            reject_submission("Review every member before submitting")

        finding_ids = [finding.findingId for finding in validated.findings]
        if len(finding_ids) != len(set(finding_ids)):
            reject_submission("Every consolidated finding needs a unique findingId")
        final_ids = set(finding_ids)

        reconciled_ids = [item.memberFindingId for item in validated.reconciliation]
        if len(reconciled_ids) != len(set(reconciled_ids)) \
                or set(reconciled_ids) != expected_finding_ids:
            missing = sorted(expected_finding_ids - set(reconciled_ids))
            unknown = sorted(set(reconciled_ids) - expected_finding_ids)
            reject_submission(
                f"Reconcile every member finding exactly once; missing={missing}, unknown={unknown}"
            )

        for item in validated.reconciliation:
            references_final = item.action in {"preserved", "merged", "rejected_duplicate"}
            if references_final != (item.consolidatedFindingId is not None):
                reject_submission(
                    "Preserved, merged, or duplicate findings must reference a final finding"
                )
            if item.consolidatedFindingId and item.consolidatedFindingId not in final_ids:
                reject_submission("Reconciliation references an unknown final finding")

        source_occurrences = {
            source_id: [
                finding.findingId
                for finding in validated.findings
                if source_id in finding.sourceFindingIds
            ]
            for source_id in expected_finding_ids
        }
        for item in validated.reconciliation:
            if item.consolidatedFindingId:
                if source_occurrences[item.memberFindingId] != [item.consolidatedFindingId]:
                    reject_submission("Final finding must retain its reconciled sourceFindingId")
            elif source_occurrences[item.memberFindingId]:
                reject_submission("Rejected invalid findings cannot appear in final findings")

        for finding in validated.findings:
            if finding.mergeRequestId not in expected_members:
                reject_submission("Every finding needs a primary member MR")
            related = finding.relatedMergeRequestIds
            if len(related) != len(set(related)) or finding.mergeRequestId in related \
                    or not set(related).issubset(expected_members):
                reject_submission("Finding references an invalid related member")
            if finding.findingType == "cross_member" and not related:
                reject_submission("Cross-member findings require at least one related member")
            if finding.findingType == "member" and related:
                reject_submission("Member findings cannot claim related members")
            if finding.findingType == "member" and not finding.sourceFindingIds:
                reject_submission("Member findings must retain a source member finding")
            involved = {finding.mergeRequestId, *related}
            evidence_members = {evidence.mergeRequestId for evidence in finding.evidence}
            if not involved.issubset(evidence_members):
                reject_submission("Finding needs evidence from every involved member")
            for evidence in finding.evidence:
                member = expected_members.get(evidence.mergeRequestId)
                if not member or evidence.checkout != member.get("checkout"):
                    reject_submission("Evidence checkout does not match its member")
                if evidence.file is not None:
                    try:
                        safe_path(evidence.file)
                    except ValueError as error:
                        reject_submission(str(error))
            if not set(finding.sourceFindingIds).issubset(expected_finding_ids):
                reject_submission("Finding references an unknown member finding")
            if finding.relevant_file is not None:
                try:
                    relative = safe_path(finding.relevant_file)
                except ValueError as error:
                    reject_submission(str(error))
                diff_path = root / expected_members[finding.mergeRequestId]["checkout"] / ".diffs" / f"{relative}.diff"
                if not diff_path.is_file():
                    reject_submission("Primary finding file is outside its member diff")
                if finding.line is None:
                    reject_submission("Inline findings require a new-file line")
                line_map = PostMergeRequestComments()._build_line_code_map({
                    "changes": [{
                        "new_path": relative,
                        "diff": diff_path.read_text(encoding="utf-8"),
                    }],
                })
                anchor = line_map.get(relative, {}).get(finding.line)
                if not anchor or anchor["type"] != "new":
                    reject_submission("Inline findings must anchor to an added line")
            elif finding.line is not None:
                reject_submission("A line cannot be supplied without a relevant file")

        captured.update(
            findings=[item.model_dump() for item in validated.findings],
            reconciliation=[item.model_dump() for item in validated.reconciliation],
        )
        return "Combined review recorded"

    first_response = ""
    try:
        first_response = call_llm_fn(
            build_combined_review_prompt(),
            tools=[submit_group_review],
            working_directory=str(root),
            enable_graph_tools=False,
            system_message=GROUP_REVIEW_SYSTEM_MESSAGE,
        )
    except Exception as error:
        validation_errors.append(f"Initial combined-review session failed: {error}")
        logger.exception("Initial combined-review session failed")

    if not captured:
        logger.warning(
            "Combined review produced no valid submission; errors=%s response=%r",
            validation_errors,
            (first_response or "")[:2000],
        )
        previous_response = first_response
        for attempt in range(2):
            try:
                correction_response = call_llm_fn(
                    build_combined_correction_prompt(
                        validation_errors, previous_response, attempt + 1
                    ),
                    tools=[submit_group_review],
                    working_directory=str(root),
                    enable_graph_tools=False,
                    system_message=GROUP_REVIEW_SYSTEM_MESSAGE,
                )
            except Exception as error:
                validation_errors.append(f"Correction session failed: {error}")
                logger.exception("Combined-review correction session failed")
                correction_response = ""
            if captured:
                break
            logger.error(
                "Combined correction attempt %d produced no valid submission; "
                "errors=%s response=%r",
                attempt + 1,
                validation_errors,
                correction_response[:2000],
            )
            previous_response = correction_response
    if not captured:
        detail = "; ".join(validation_errors[-5:]) or "no submit_group_review tool call"
        raise RuntimeError(f"Combined review did not submit a validated result: {detail}")
    return captured


def normalize_findings(raw, members, changes, run_id):
    by_id = {member["mergeRequestId"]: member for member in members}
    formatter = PostMergeRequestComments()
    result = {}
    for comment in raw:
        member = by_id[comment["mergeRequestId"]]
        related = comment.get("relatedMergeRequestIds") or []
        if not isinstance(related, list) or not set(related).issubset(by_id):
            raise ValueError("Finding references an unknown MR")
        content = comment.get("suggestion_content")
        if not isinstance(content, str) or not content.strip():
            raise ValueError("Finding has no explanation")
        confidence = float(comment.get("confidence", 1))
        if not 0 <= confidence <= 1:
            raise ValueError("Invalid finding confidence")
        if confidence < 0.7:
            continue
        path = comment.get("relevant_file") or None
        changed = {item["new_path"]: item for item in changes[member["mergeRequestId"]]}
        if path is not None and safe_path(path) not in changed:
            raise ValueError("Finding references a file outside its member diff")
        line = comment.get("line") or comment.get("line_number")
        line_map = formatter._build_line_code_map({"changes": list(changed.values())})
        anchor = line_map.get(path, {}).get(line) if isinstance(line, int) and not isinstance(line, bool) else None
        prepared = dict(comment)
        if anchor is None:
            prepared.update(existing_code="", improved_code="", lines_to_replace=0)
        else:
            count = int(prepared.get("lines_to_replace") or 0)
            if count and not all(line_map.get(path, {}).get(value) for value in range(line, line + count)):
                prepared.update(existing_code="", improved_code="", lines_to_replace=0)
        prepared["line"] = line
        body = formatter._format_comments([prepared])[0]["body"]
        if related:
            body += "\n\nRelated changes: " + ", ".join(
                f"[!{by_id[mid]['iid']}]({by_id[mid]['webUrl']})" for mid in related
            )
        key = json.dumps([
            member["mergeRequestId"],
            member["headSha"],
            path,
            line if anchor else None,
            content.strip(),
        ])
        finding_id = hashlib.sha256(key.encode()).hexdigest()[:32]
        body += f"\n\n<!-- arnold-group:{run_id}:{finding_id} -->"
        if len(body) > 20000:
            raise ValueError("Finding exceeds publication size limit")
        result[finding_id] = dict(
            id=finding_id,
            mergeRequestId=member["mergeRequestId"],
            headSha=member["headSha"],
            filePath=path,
            oldPath=changed[path]["old_path"] if path else None,
            newLine=line if anchor else None,
            body=body,
            relatedMergeRequestIds=related,
            publicationStatus="pending",
            commentUrl=None,
        )
    if len(result) > 500:
        raise ValueError("Too many findings; split this group into smaller reviews")
    return list(result.values())


def publish(findings, members, token, callback, states, ensure_fresh_fn=None):
    ensure_fresh_fn = ensure_fresh_fn or ensure_fresh
    by_id = {member["mergeRequestId"]: member for member in members}
    callback.fresh()
    ensure_fresh_fn(members, token)
    for finding in findings:
        if finding["publicationStatus"] == "posted":
            continue
        callback.fresh()
        mr = ensure_fresh_fn(members, token)[finding["mergeRequestId"]]
        member = by_id[finding["mergeRequestId"]]
        marker = f"<!-- arnold-group:{callback.run_id}:{finding['id']} -->"
        existing = next((note for note in discussion_comments(mr) if marker in note["body"]), None)
        if existing:
            finding.update(
                publicationStatus="posted",
                commentUrl=f"{member['webUrl']}#note_{existing['note_id']}",
            )
        elif finding["publicationStatus"] == "unknown":
            continue
        else:
            finding["publicationStatus"] = "unknown"
            callback.update("running", states, findings)
            payload = {"body": finding["body"]}
            if finding["newLine"] is not None:
                payload["position"] = dict(
                    position_type="text",
                    base_sha=member["baseSha"],
                    start_sha=member["startSha"],
                    head_sha=member["headSha"],
                    old_path=finding["oldPath"],
                    new_path=finding["filePath"],
                    new_line=finding["newLine"],
                )
            try:
                discussion = mr.discussions.create(payload)
                note_id = discussion.attributes["notes"][0]["id"]
                finding.update(
                    publicationStatus="posted",
                    commentUrl=f"{member['webUrl']}#note_{note_id}",
                )
            except gitlab.exceptions.GitlabCreateError as error:
                if error.response_code and 400 <= error.response_code < 500:
                    finding["publicationStatus"] = "failed"
            except (requests.RequestException, gitlab.exceptions.GitlabError):
                pass
        callback.update("running", states, findings)
    callback.fresh()
    ensure_fresh_fn(members, token)


class LoadGroupManifest(Node):
    def prep(self, shared):
        return shared["callback"]

    def exec(self, callback):
        manifest = callback.manifest()
        run = manifest["run"]
        callback.run_id = run["runId"]
        return {
            "manifest": manifest,
            "members": run["members"],
            "states": dict(run["memberStatuses"]),
            "findings": list(run["findings"]),
            "publication_only": manifest["publicationOnly"],
            "member_reviews": manifest.get("memberReviews") or {},
        }

    def post(self, shared, prep_res, exec_res):
        shared.update(exec_res)
        for member in shared["members"]:
            review = shared["member_reviews"].get(member["mergeRequestId"])
            if review is not None and review["headSha"] != member["headSha"]:
                raise ValueError("Saved member review does not match its pinned revision")
        shared["callback"].update(
            "running",
            shared["states"],
            shared["findings"],
        )


class ValidateGroupSnapshot(Node):
    def prep(self, shared):
        return shared

    def exec(self, shared):
        shared["callback"].fresh()
        ensure_fresh(shared["members"], shared["token"])


class PrepareGroupMembers(Node):
    def prep(self, shared):
        return shared

    def exec(self, shared):
        if shared["publication_only"]:
            return {}
        root = Path(tempfile.mkdtemp(prefix="clio-group-"))
        shared["workspace"] = str(root)
        members = shared["members"]
        manifest = shared["manifest"]
        context = {
            "members": members,
            "context": manifest["context"],
            "reviewPersonas": manifest["reviewPersonas"],
        }
        (root / "group_context.json").write_text(
            json.dumps(context, indent=2, sort_keys=True),
            encoding="utf-8",
        )
        prepared = {}
        changes = {}
        for index, member in enumerate(members):
            mid = member["mergeRequestId"]
            shared["states"][mid] = "reviewed" if mid in shared["member_reviews"] else "preparing"
            shared["callback"].update("running", shared["states"], shared["findings"])
            prepared[mid], changes[mid] = prepare(
                member,
                manifest["context"],
                manifest["reviewPersonas"],
                root,
                index,
            )
            member["checkout"] = f"member-{index}"
        (root / "group_context.json").write_text(
            json.dumps(context, indent=2, sort_keys=True),
            encoding="utf-8",
        )
        return {"workspace": str(root), "prepared": prepared, "changes": changes}

    def post(self, shared, prep_res, exec_res):
        shared.update(exec_res)


class ReviewGroupMembers(Node):
    def prep(self, shared):
        return shared

    def exec(self, shared):
        if shared["publication_only"]:
            return {}
        raw = []
        for member in shared["members"]:
            mid = member["mergeRequestId"]
            saved = shared["member_reviews"].get(mid)
            if saved is not None:
                raw.extend(dict(item, mergeRequestId=mid, memberFindingId=f"{mid}:member-{index}")
                           for index, item in enumerate(saved["findings"]))
                continue
            member_shared = shared["prepared"][mid]
            shared["states"][mid] = "reviewing"
            shared["callback"].update("running", shared["states"], shared["findings"])
            member_shared["group_context"] = build_group_member_context(shared["members"], member)
            if member_shared["changed_files"]:
                ExecuteCodeReview(max_retries=2, wait=1).run(member_shared)
                if not member_shared.get("review_findings"):
                    raise RuntimeError("Member review returned no validated result")
            git(
                member_shared["local_repo_path"],
                "diff",
                "--exit-code",
                member["headSha"],
                "--",
            )
            comments = member_shared.get("review_comments", [])
            for index, item in enumerate(comments):
                raw.append(dict(
                    item,
                    mergeRequestId=mid,
                    memberFindingId=f"{mid}:member-{index}",
                ))
            shared["member_reviews"][mid] = {"headSha": member["headSha"], "findings": comments}
            shared["states"][mid] = "reviewed"
            shared["callback"].update("running", shared["states"], shared["findings"],
                                      member_reviews=shared["member_reviews"])
        return {"raw_findings": raw}

    def post(self, shared, prep_res, exec_res):
        shared.update(exec_res)


class ExecuteCombinedReview(Node):
    def prep(self, shared):
        return shared

    def exec(self, shared):
        if shared["publication_only"]:
            return {}
        combined = combined_review(
            Path(shared["workspace"]),
            shared["members"],
            shared["raw_findings"],
        )
        for member in shared["members"]:
            git(
                shared["prepared"][member["mergeRequestId"]]["local_repo_path"],
                "diff",
                "--exit-code",
                member["headSha"],
                "--",
            )
        return {"combined_review": combined}

    def post(self, shared, prep_res, exec_res):
        shared.update(exec_res)


class FilterGroupFindings(Node):
    def prep(self, shared):
        return shared

    def exec(self, shared):
        if shared["publication_only"]:
            return {}
        filtered = []
        live = ensure_fresh(shared["members"], shared["token"])
        for member in shared["members"]:
            mid = member["mergeRequestId"]
            member_shared = shared["prepared"][mid]
            member_shared["review_comments"] = [
                item for item in shared["combined_review"]["findings"]
                if item["mergeRequestId"] == mid
            ]
            member_shared["existing_comments"] = discussion_comments(live[mid])
            FilterDuplicateComments().run(member_shared)
            filtered.extend(member_shared["review_comments"])
        findings = normalize_findings(
            filtered,
            shared["members"],
            shared["changes"],
            shared["callback"].run_id,
        )
        return {"findings": findings}

    def post(self, shared, prep_res, exec_res):
        shared.update(exec_res)
        if exec_res:
            shared["callback"].update(
                "running",
                shared["states"],
                shared["findings"],
                analysis_complete=True,
            )


class CleanupGroupWorkspace(Node):
    def prep(self, shared):
        return shared.get("workspace")

    def exec(self, workspace):
        if workspace:
            shutil.rmtree(workspace)

    def post(self, shared, prep_res, exec_res):
        shared["workspace"] = None


class PublishGroupFindings(Node):
    def prep(self, shared):
        return shared

    def exec(self, shared):
        publish(
            shared["findings"],
            shared["members"],
            shared["token"],
            shared["callback"],
            shared["states"],
        )


class CompleteGroupAnalysis(Node):
    def prep(self, shared):
        return shared

    def exec(self, shared):
        status = (
            "completed"
            if all(finding["publicationStatus"] == "posted" for finding in shared["findings"])
            else "partial"
        )
        shared["callback"].update(
            status,
            shared["states"],
            shared["findings"],
            "Some comments could not be confirmed. Retry publication." if status == "partial" else None,
        )
