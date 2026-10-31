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

"""PocketFlow nodes for Arnold Smart Diff generation."""

import json
import re
import subprocess
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urlparse

import gitlab
from utils import scm
import requests
from pocketflow import Node

from flows.code_reviews.review.markdown_formatter import format_review_effort
from utils.call_llm import call_llm_agentic
from utils.arnold_auth import ArnoldAuth
from utils.harness.common import usage_summary
from utils.logger import get_logger

logger = get_logger("smart_diff_nodes_logger")

HUNK_RE = re.compile(r"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@")


def send_callback(shared, status, summary=None, groups=None, error_message=None,
                  comment_assessment_status=None, assessed_comment_count=0):
    arnold_url = shared.get("arnold_url")
    hostname = urlparse(arnold_url).hostname if arnold_url else None
    if shared.get("dry_run"):
        if status == "completed":
            print(json.dumps({"summary": summary, "groups": groups or []}, indent=2))
        return
    payload = {
        "executionId": shared["execution_id"],
        "headSha": shared["expected_head_sha"],
        "status": status,
        "summary": summary,
        "groups": groups or [],
        "errorMessage": error_message,
        "commentAssessmentStatus": comment_assessment_status,
        "assessedCommentCount": assessed_comment_count,
        "usage": usage_summary(),
    }
    response = requests.put(
        f"{arnold_url.rstrip('/')}/merge-requests/v1/{shared['project_id']}/{shared['merge_request_iid']}/smart-diff",
        params={"provider": "GITHUB" if scm.is_github(shared.get("gitlab_url")) else "GITLAB"},
        json={key: value for key, value in payload.items() if value is not None},
        headers={} if hostname in {"localhost", "127.0.0.1", "::1"} else {
            "Authorization": ArnoldAuth.get_auth_token()
        },
        timeout=30,
    )
    response.raise_for_status()


def ensure_live_merge_request(shared):
    client = scm.connect(shared["gitlab_url"], private_token=shared["gitlab_token"])
    project = client.projects.get(shared["project_id"])
    merge_request = project.mergerequests.get(shared["merge_request_iid"])
    if project.id != shared["resolved_project_id"] or merge_request.iid != shared["resolved_merge_request_iid"]:
        raise ValueError("MR identifiers do not match the supplied URL")
    if merge_request.sha.lower() != shared["expected_head_sha"].lower():
        raise ValueError("Merge request head changed before Smart Diff generation")


def git(repo, *arguments):
    result = subprocess.run(["git", "-C", repo, *arguments], capture_output=True, text=True, timeout=300)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or "git command failed")
    return result.stdout


def enumerate_units(repo, target_branch):
    merge_base = git(repo, "merge-base", f"origin/{target_branch}", "HEAD").strip()
    patch = git(repo, "diff", "--find-renames", "--unified=3", merge_base, "HEAD")
    units = []
    file_path = None
    current = None
    for line in patch.splitlines(keepends=True):
        if line.startswith("diff --git "):
            if current:
                units.append(current)
            current = None
            file_path = line.split(" b/", 1)[-1].strip()
        elif line.startswith("+++ b/"):
            file_path = line[6:].strip()
        elif match := HUNK_RE.match(line):
            if current:
                units.append(current)
            old_start, old_count = int(match.group(1)), int(match.group(2) or 1)
            new_start, new_count = int(match.group(3)), int(match.group(4) or 1)
            current = {
                "id": f"h{len(units) + 1:05d}",
                "filePath": file_path,
                "oldStartLine": old_start if old_count else None,
                "oldEndLine": old_start + old_count - 1 if old_count else None,
                "newStartLine": new_start if new_count else None,
                "newEndLine": new_start + new_count - 1 if new_count else None,
                "patch": line,
            }
        elif current:
            current["patch"] += line
    if current:
        units.append(current)

    changed_paths = [line.split("\t")[-1] for line in git(
        repo, "diff", "--name-only", merge_base, "HEAD"
    ).splitlines() if line]
    paths_with_hunks = {unit["filePath"] for unit in units}
    for file_path in changed_paths:
        if file_path not in paths_with_hunks:
            units.append({
                "id": f"h{len(units) + 1:05d}", "filePath": file_path,
                "oldStartLine": None, "oldEndLine": None,
                "newStartLine": None, "newEndLine": None,
                "patch": "Binary or metadata-only change",
            })
    return merge_base, units, [{"path": path, "status": "M"} for path in changed_paths]


def parse_model_json(text):
    match = re.search(r"\{[\s\S]*\}", text)
    if not match:
        raise ValueError("model returned no JSON object")
    return json.loads(match.group(0))


def validate_result(result, expected_ids):
    if not isinstance(result.get("summary"), str) or not result["summary"].strip() \
            or len(result["summary"]) > 10_000:
        raise ValueError("model returned no summary")
    groups = result.get("groups")
    if not isinstance(groups, list) or not groups or len(groups) > 100:
        raise ValueError("model returned no chapters")
    selected = []
    for group in groups:
        if not all(isinstance(group.get(key), str) and group[key].strip() for key in ("title", "rationale")) \
                or len(group["title"]) > 500 or len(group["rationale"]) > 5_000:
            raise ValueError("invalid chapter")
        if not isinstance(group.get("steps"), list) or not group["steps"] or len(group["steps"]) > 500:
            raise ValueError("chapter has no steps")
        for step in group["steps"]:
            if not all(isinstance(step.get(key), str) and step[key].strip() for key in ("title", "explanation")) \
                    or len(step["title"]) > 120 or len(step["explanation"]) > 300:
                raise ValueError("invalid review step")
            if not isinstance(step.get("hunkIds"), list) or not step["hunkIds"]:
                raise ValueError("review step has no hunks")
            selected.extend(step["hunkIds"])
    counts = Counter(selected)
    missing = sorted(set(expected_ids) - counts.keys())
    unexpected = sorted(counts.keys() - set(expected_ids))
    duplicates = sorted(hunk_id for hunk_id, count in counts.items() if count > 1)
    if missing or unexpected or duplicates:
        raise ValueError(
            f"every changed unit must be assigned exactly once; missing={missing}, "
            f"unexpected={unexpected}, duplicates={duplicates}"
        )
    return result


def prompt_for(units, context):
    hunks = "\n\n".join(
        f"### {unit['id']} {unit['filePath']} ({unit['oldStartLine']}, {unit['newStartLine']})\n"
        f"{unit['patch']}"
        for unit in units
    )
    return f"""Organize this merge-request diff into a context-first walkthrough for a reviewer with no project context.
Return JSON only: {{"summary":"compact Markdown review brief","groups":[{{"title":"chapter", "rationale":"why this chapter is next", "steps":[{{"title":"step", "explanation":"one short sentence covering why this matters and what to inspect", "hunkIds":["h00001"]}}]}}]}}.
The summary must include the supplied review signals: a one-sentence overview, tests and security. Do not include a review-effort score or rationale; these are appended separately from structured review data. Include Jira or RFC compliance only when that context is supplied. Use a ## Review Brief title and separate ### Overview, ### Tests, and ### Security sections. Use separate headings for any supplied compliance sections. Keep the entire brief concise.
Do not include review focus areas in the summary, chapters, rationales, or steps; they are rendered separately on diff lines.
Keep each step title under 120 characters and its explanation to one sentence under 300 characters. The guide already shows the title, so do not repeat it in the explanation.
Use every supplied hunk ID exactly once. A hunk belongs only to its most specific review step. Put cross-cutting or overview guidance in the summary or chapter rationale without hunk IDs; never create a broad step that reuses hunks from detailed steps. Before responding, flatten all hunkIds and verify they exactly equal the required list with no duplicates. Preserve a comprehension-first order, not file-system order. Do not invent IDs.
Required hunk IDs: {json.dumps([unit["id"] for unit in units])}

Repository, review, and optional issue context:
{context[:20_000]}

Changed units:
{hunks}
"""


def organize(units, context, llm=call_llm_agentic):
    expected = [unit["id"] for unit in units]
    prompt = prompt_for(units, context)
    for attempt in range(2):
        try:
            result = validate_result(parse_model_json(llm(
                prompt, tools=[], enable_graph_tools=False
            )), expected)
            break
        except (ValueError, json.JSONDecodeError) as error:
            if attempt:
                raise
            prompt += (
                f"\n\nYour previous response was invalid: {error}.\n"
                "If IDs were duplicated, remove them from broad or overview steps and keep them only in the "
                "most specific step; remove any step or chapter left empty. "
                f"Return corrected JSON using this exact hunk ID set once each: {json.dumps(expected)}."
            )

    unit_by_id = {unit["id"]: unit for unit in units}
    groups = []
    for group_index, group in enumerate(result["groups"], 1):
        steps = []
        for step_index, step in enumerate(group["steps"], 1):
            steps.append({
                "id": f"c1-g{group_index}-s{step_index}",
                "title": step["title"],
                "explanation": step["explanation"],
                "changes": [{key: unit_by_id[hunk_id][key] for key in (
                    "filePath", "oldStartLine", "oldEndLine", "newStartLine", "newEndLine"
                )} for hunk_id in step["hunkIds"]],
            })
        groups.append({
            "id": f"c1-g{group_index}", "title": group["title"],
            "rationale": group["rationale"], "steps": steps,
        })
    return result["summary"].strip(), groups


def build_focus_group(review_data, units):
    steps = []
    for issue in review_data.get("key_issues", []):
        path = str(issue.get("relevant_file", "")).strip()
        start, end = issue.get("start_line"), issue.get("end_line")
        if not isinstance(start, int) or not isinstance(end, int) or start < 1 or end < start:
            continue
        if not any(unit["filePath"] == path and unit["newStartLine"] is not None
                   and start >= unit["newStartLine"] and end <= unit["newEndLine"] for unit in units):
            continue
        title = str(issue.get("issue_header") or "").strip() or "Review focus area"
        explanation = str(issue.get("issue_content") or "").strip() or "Inspect this changed range."
        steps.append({
            "id": f"focus-{len(steps) + 1}",
            "title": title[:120], "explanation": explanation,
            "changes": [{
                "filePath": path, "oldStartLine": None, "oldEndLine": None,
                "newStartLine": start, "newEndLine": end,
            }],
        })

    return {
        "id": "review-focus", "title": "Review focus areas",
        "rationale": "Inspect the highest-risk changed lines first.", "steps": steps,
    } if steps else None


class StartSmartDiff(Node):
    def prep(self, shared):
        return shared

    def exec(self, shared):
        ensure_live_merge_request(shared)
        send_callback(shared, "running", comment_assessment_status="running")


class GetSmartDiffChanges(Node):
    def prep(self, shared):
        return shared["local_repo_path"], shared["target_branch"], shared["expected_head_sha"]

    def exec(self, prep_res):
        repo, target_branch, expected_head_sha = prep_res
        head = git(repo, "rev-parse", "HEAD").strip()
        if head.lower() != expected_head_sha.lower():
            raise ValueError("cloned merge request head does not match expected SHA")
        merge_base, units, changed_files = enumerate_units(repo, target_branch)
        if not units:
            raise ValueError("merge request has no changed units")
        diffs_dir = Path(repo, ".diffs")
        for unit in units:
            diff_file = diffs_dir / f"{unit['filePath']}.diff"
            diff_file.parent.mkdir(parents=True, exist_ok=True)
            with diff_file.open("a", encoding="utf-8") as output:
                output.write(unit["patch"])
        return {"merge_base": merge_base, "head_sha": head, "units": units, "changed_files": changed_files,
                "diffs_dir": str(diffs_dir)}

    def post(self, shared, prep_res, exec_res):
        shared.update(exec_res)


class OrganizeSmartDiff(Node):
    def prep(self, shared):
        return shared

    def exec(self, shared):
        review_data = shared.get("review_data")
        if not review_data:
            raise RuntimeError("review summary generation returned no data")
        story_review_data = {key: value for key, value in review_data.items() if key not in {"key_issues", "estimated_effort", "estimated_effort_rationale"}}
        context = "\n\n".join(filter(None, [
            shared.get("impact_context", ""),
            Path(shared["jira_file_path"]).read_text() if shared.get("jira_file_path") else "",
            shared.get("rfc_content", ""),
            f"Structured review signals:\n{json.dumps(story_review_data)}",
        ]))
        units = shared["units"]
        story_units = [unit for unit in units if unit["oldStartLine"] is not None
                       or unit["newStartLine"] is not None]
        if not story_units:
            raise ValueError("merge request has no line-backed changes to organize")
        summary, groups = organize(story_units, context)
        summary = "\n\n".join(filter(None, [summary, format_review_effort(review_data)]))
        focus_group = build_focus_group(review_data, units)
        if focus_group:
            groups.append(focus_group)
        return {"smart_diff_summary": summary, "smart_diff_groups": groups}

    def post(self, shared, prep_res, exec_res):
        shared.update(exec_res)


def send_comment_assessments(shared):
    comments = shared.get("previous_review_comments", [])
    results = shared.get("comment_analysis_results", [])
    if not shared.get("comment_fetch_succeeded") or not shared.get("comment_analysis_succeeded"):
        raise RuntimeError("comment assessment did not complete")
    if not comments:
        return 0

    result_by_id = {result.get("comment_id"): result for result in results}
    if len(result_by_id) != len(comments):
        raise RuntimeError("comment assessment did not return one result per comment")
    assessed = []
    for index, comment in enumerate(comments):
        result = result_by_id.get(f"comment-{index}")
        if not result:
            raise RuntimeError(f"missing assessment for comment-{index}")
        assessed.append({
            "note_id": comment["note_id"],
            "discussion_id": comment["discussion_id"],
            "comment_type": "inline",
            "has_suggestion": "```suggestion" in comment.get("body", ""),
            "status": result["status"],
            "score": result["score"],
            "reasoning": result.get("reasoning", ""),
            "priority": "medium",
            "comment_category": "general",
        })

    payload = {
        "metadata": {
            "project_id": shared["project_id"],
            "mr_id": shared["merge_request_id"],
            "mr_iid": shared["merge_request_iid"],
            "mr_author_id": shared["mr_author_id"],
            "assessed_sha": shared["expected_head_sha"],
        },
        "comments": assessed,
    }
    if shared.get("dry_run"):
        return len(assessed)

    arnold_url = shared["arnold_url"]
    hostname = urlparse(arnold_url).hostname
    response = requests.post(
        f"{arnold_url.rstrip('/')}/review-feedback/v1/ingest",
        json=payload,
        headers={} if hostname in {"localhost", "127.0.0.1", "::1"} else {
            "Authorization": ArnoldAuth.get_auth_token()
        },
        timeout=30,
    )
    response.raise_for_status()
    body = response.json()
    saved = body.get("response") if isinstance(body, dict) and "success" in body else body
    if saved != len(assessed):
        raise RuntimeError(f"Arnold persisted {saved} of {len(assessed)} comment assessments")
    return saved


class GenerateSmartDiffArtifacts(Node):
    def prep(self, shared):
        return shared.copy()

    def exec(self, shared):
        from flows.code_reviews.common.nodes import FetchMRReviewStatus
        from flows.code_reviews.review.nodes import AnalyzePreviousCommentStatus, ExecuteReviewSummary

        def generate_story():
            branch = shared.copy()
            ExecuteReviewSummary(max_retries=3, wait=10).run(branch)
            OrganizeSmartDiff().run(branch)
            return branch["smart_diff_summary"], branch["smart_diff_groups"]

        def assess_comments():
            branch = shared.copy()
            try:
                FetchMRReviewStatus().run(branch)
                AnalyzePreviousCommentStatus(max_retries=2, wait=10).run(branch)
                return "completed", send_comment_assessments(branch)
            except Exception as error:
                logger.exception("Comment assessment failed")
                return "failed", 0

        with ThreadPoolExecutor(max_workers=2) as executor:
            story = executor.submit(generate_story)
            assessment = executor.submit(assess_comments)
            summary, groups = story.result()
            assessment_status, assessed_count = assessment.result()
        return {
            "smart_diff_summary": summary,
            "smart_diff_groups": groups,
            "comment_assessment_status": assessment_status,
            "assessed_comment_count": assessed_count,
        }

    def post(self, shared, prep_res, exec_res):
        shared.update(exec_res)


class CompleteSmartDiff(Node):
    def prep(self, shared):
        return shared

    def exec(self, shared):
        ensure_live_merge_request(shared)
        send_callback(
            shared, "completed", shared["smart_diff_summary"], shared["smart_diff_groups"],
            comment_assessment_status=shared["comment_assessment_status"],
            assessed_comment_count=shared["assessed_comment_count"],
        )
