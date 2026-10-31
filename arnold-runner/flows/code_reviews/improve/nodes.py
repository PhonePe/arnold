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

import os
import re
import yaml
import logging
import gitlab
from utils import scm
from pocketflow import Node
from utils.call_llm import call_llm_agentic, resolve_effective_model
from utils.config import get_harness_label, get_harness_name
from utils.crawl_skills import fetch_all_skills
from utils.logger import get_logger

REVIEW_MODEL = "gpt-5.6-sol"
from flows.code_reviews.improve.prompts import (
    build_system_message,
    build_review_prompt,
    build_specialist_review_prompt,
    build_skill_review_prompt,
    FILTER_DUPLICATES_SYSTEM_MESSAGE,
    build_filter_duplicates_prompt,
)
from utils.review_comment_metadata import (
    ARNOLD_COMMENT_MARKER,
    build_persona_marker,
    build_persona_tag,
    build_review_comment_marker,
    normalize_source_harness,
    normalize_source_model,
)

# Set up logger
logger = get_logger("code_review_improve_nodes_logger")

# Sub-agents otherwise inherit every session tool (task, edit, main-only output tools…),
# whose schemas are resent on every turn. Graph tools are appended by the harness.
_SPECIALIST_TOOLS = ["view", "bash", "grep", "glob", "read_review_context", "submit_specialist_findings"]
# Above this size each specialist reads only the diffs relevant to its focus instead.
_INLINE_DIFFS_MAX_CHARS = 40_000


def _read_staged_diffs(local_repo_path, changed_files, max_chars=_INLINE_DIFFS_MAX_CHARS):
    """Return {path: diff} from `.diffs/`, or {} when the total exceeds max_chars."""
    diffs, total = {}, 0
    for f in changed_files:
        diff_file = os.path.join(local_repo_path, ".diffs", f["path"] + ".diff")
        if not os.path.isfile(diff_file):
            continue
        with open(diff_file, encoding="utf-8", errors="replace") as fh:
            diffs[f["path"]] = fh.read()
        total += len(diffs[f["path"]])
        if total > max_chars:
            return {}
    return diffs


class FetchSkills(Node):
    """Discover and collect agent skills from the central skills repo.

    Applicable skill folders are copied into ``.github/skills/`` of the cloned
    repo, prefixed with the group/subgroup hierarchy, then filtered by
    code-review relevance.
    """

    def prep(self, shared):
        return {
            "local_repo_path": shared.get("local_repo_path", "."),
            "repo_url": shared.get("repo_url", ""),
            "gitlab_token": shared.get("gitlab_token"),
        }

    def exec(self, prep_res):
        local_repo_path = prep_res["local_repo_path"]
        repo_url = prep_res["repo_url"]
        gitlab_token = prep_res["gitlab_token"]

        skills = fetch_all_skills(
            local_repo_path=local_repo_path,
            repo_url=repo_url,
            gitlab_token=gitlab_token,
        )

        logger.info(f"Total skills collected: {len(skills)}")

        # Derive unique folder names from the *filtered* skills list (not
        # the raw disk walk) so non-code-review skills like lens-devtools
        # are excluded from custom agent registration.
        _seen: set = set()
        skill_folders: list[str] = []
        for skill in skills:
            _parts = skill["path"].replace("\\", "/").split("/")
            if len(_parts) >= 3 and _parts[0] == ".github" and _parts[1] == "skills":
                _folder = _parts[2]
                if _folder not in _seen:
                    _seen.add(_folder)
                    skill_folders.append(_folder)

        if skill_folders:
            logger.info(f"Skills available at .github/skills/: {len(skills)} file(s) in {len(skill_folders)} folder(s)")
        else:
            logger.info("No skills found — review will proceed without skill context")

        return {
            "skill_folders": skill_folders,
        }

    def post(self, shared, prep_res, exec_res):
        shared["skill_folders"] = exec_res["skill_folders"]


def attribute_persona(finding, persona):
    """Tag a specialist finding with the review persona that produced it; skill reviewers stay general."""
    if hasattr(finding, "model_dump"):
        finding = finding.model_dump()
    if not isinstance(finding, dict):
        return finding
    finding = {key: value for key, value in finding.items() if not key.startswith("persona_")}
    if persona:
        finding["persona_id"] = persona["id"]
        finding["persona_version"] = persona.get("version")
        finding["persona_name"] = persona.get("name")
    return finding


def inherit_duplicate_personas(review_comments, duplicate_of, persona_order):
    """When a persona finding is dropped as a duplicate, its retained twin takes the persona attribution.

    Persona attribution wins over general findings; among personas the earliest in ``persona_order`` wins.
    """
    rank = {persona_id: index for index, persona_id in enumerate(persona_order)}

    def index_of(comment_id):
        try:
            prefix, value = str(comment_id).split("-", 1)
            index = int(value)
        except (TypeError, ValueError):
            return None
        return index if prefix == "new" and 0 <= index < len(review_comments) else None

    for dropped_id, kept_id in (duplicate_of or {}).items():
        dropped, kept = index_of(dropped_id), index_of(kept_id)
        if dropped is None or kept is None:
            continue
        source, target = review_comments[dropped], review_comments[kept]
        persona_id = source.get("persona_id")
        if not persona_id:
            continue
        current = target.get("persona_id")
        if current and rank.get(current, len(rank)) <= rank.get(persona_id, len(rank)):
            continue
        target["persona_id"] = persona_id
        target["persona_version"] = source.get("persona_version")
        target["persona_name"] = source.get("persona_name")


class ExecuteCodeReview(Node):
    """Execute the review prompt in agent mode with changed files and other contexts as input."""
    
    def prep(self, shared):
        changed_files = shared.get("changed_files", [])
        project_name = shared.get("project_name", "Unknown Project")
        source_branch = shared.get("source_branch", "")
        target_branch = shared.get("target_branch", "main")
        mr_title = shared.get("mr_title", "")
        mr_description = shared.get("mr_description", "")
        local_repo_path = shared.get("local_repo_path", ".")
        min_confidence = shared.get("min_confidence", 0.6)
        skill_folders = shared.get("skill_folders", [])
        impact_context = shared.get("impact_context", "")
        graph_tools_available = shared.get("code_graph_built", False)
        rfc_content = shared.get("rfc_content", "")
        jira_file_path = shared.get("jira_file_path", "")
        review_personas = shared.get("review_personas", [])
        group_context = shared.get("group_context", "")
        
        return {
            "changed_files": changed_files,
            "project_name": project_name,
            "source_branch": source_branch,
            "target_branch": target_branch,
            "mr_title": mr_title,
            "mr_description": mr_description,
            "local_repo_path": local_repo_path,
            "min_confidence": min_confidence,
            "skill_folders": skill_folders,
            "impact_context": impact_context,
            "graph_tools_available": graph_tools_available,
            "rfc_content": rfc_content,
            "jira_file_path": jira_file_path,
            "review_personas": review_personas,
            "group_context": group_context,
            "strict_review": shared.get("strict_review", False),
        }
    
    def exec(self, prep_res):
        import json
        from pydantic import BaseModel, Field
        from utils.tool_adapter import define_tool
        
        changed_files = prep_res["changed_files"]
        source_branch = prep_res["source_branch"]
        target_branch = prep_res["target_branch"]
        mr_title = prep_res["mr_title"]
        mr_description = prep_res["mr_description"]
        local_repo_path = prep_res["local_repo_path"]
        skill_folders = prep_res.get("skill_folders", [])
        impact_context = prep_res.get("impact_context", "")
        graph_tools_available = prep_res.get("graph_tools_available", False)
        rfc_content = prep_res.get("rfc_content", "")
        jira_file_path = prep_res.get("jira_file_path", "")
        review_personas = prep_res.get("review_personas", [])
        group_context = prep_res.get("group_context", "")

        if not changed_files:
            logger.info("No changed files to review")
            return {"review_comments": [], "review_findings": {}}
        
        # --- Session-scoped state shared by the parent and all subagents ---
        captured_findings: dict = {}
        review_context: dict | None = None
        specialist_results: dict[str, dict] = {}
        final_written = False
        staged_diffs = _read_staged_diffs(local_repo_path, changed_files) if (review_personas or skill_folders) else {}
        if staged_diffs:
            logger.info(
                "Inlining %d staged diff(s) (%d chars) into the shared review context",
                len(staged_diffs), sum(map(len, staged_diffs.values())),
            )

        persona_agents = []
        persona_by_agent = {}
        for persona in review_personas:
            suffix = re.sub(r"[^a-z0-9]+", "-", persona["id"].lower()).strip("-")
            agent_name = f"persona-{suffix or 'reviewer'}"
            persona_by_agent[agent_name] = persona
            persona_agents.append({
                "name": agent_name,
                "display_name": f"Persona reviewer: {persona['name']}",
                "description": f"Specialist reviewer applying the {persona['name']} review focus.",
                "prompt": build_specialist_review_prompt(
                    reviewer_id=agent_name,
                    focus_name=persona["name"],
                    instructions=persona["instructions"],
                    mr_title=mr_title,
                    mr_description=mr_description,
                    source_branch=source_branch,
                    target_branch=target_branch,
                    changed_files=changed_files,
                    local_repo_path=local_repo_path,
                ),
                "tools": list(_SPECIALIST_TOOLS),
                "infer": False,
            })

        custom_agents = list(persona_agents)
        for folder in skill_folders:
            custom_agents.append({
                "name": folder,
                "display_name": f"Skill reviewer: {folder}",
                "description": (
                    f"Specialist code reviewer for the '{folder}' skill. "
                    "Applies its domain rules to the shared review context."
                ),
                "prompt": build_skill_review_prompt(
                    skill_folder=folder,
                    mr_title=mr_title,
                    mr_description=mr_description,
                    source_branch=source_branch,
                    target_branch=target_branch,
                    changed_files=changed_files,
                    local_repo_path=local_repo_path,
                ),
                "skills": [folder],
                "tools": list(_SPECIALIST_TOOLS),
                "infer": False,
            })

        expected_reviewers = [agent["name"] for agent in custom_agents]
        if len(expected_reviewers) != len(set(expected_reviewers)):
            raise ValueError(f"Duplicate specialist reviewer names: {expected_reviewers}")

        # Typed so a wrongly shaped finding is rejected with field-level errors the model can fix,
        # instead of being silently dropped by the loader.
        class ReviewFinding(BaseModel):
            model_config = {"extra": "allow"}
            relevant_file: str = Field(description="Full path from the git diff, e.g. src/main.py")
            language: str = Field(default="", description="Programming language, e.g. python")
            line_number: int = Field(description="NEW-file line number where existing_code starts (single integer)")
            lines_to_replace: int = Field(default=1, description="Number of lines in existing_code (single integer)")
            existing_code: str = Field(default="", description="Exact code from the diff that needs improvement")
            suggestion_content: str = Field(description="What is wrong, why it matters, and when it manifests")
            improved_code: str = Field(default="", description="Runnable replacement, or empty if no safe inline fix")
            one_sentence_summary: str = Field(description="Brief title, max 80 chars")
            label: str = Field(description=(
                "One of: security, critical bug, possible bug, possible issue, performance, "
                "best practice, maintainability, enhancement, typo, rfc_violation"
            ))
            priority: str = Field(description="One of: high, medium, low")
            confidence: float = Field(description="0.0-1.0 confidence this is a real issue requiring action")

        class PublishReviewContextParams(BaseModel):
            context: dict = Field(description=(
                "Immutable shared review baseline containing MR intent, changed-file "
                "diff evidence, relevant source/caller context, graph findings, Jira/RFC "
                "requirements, and repository conventions."
            ))

        @define_tool(description=(
            "Publish the shared review context after completing common exploration. "
            "Call exactly once before delegating to specialist reviewers."
        ))
        def publish_review_context(params: PublishReviewContextParams) -> str:
            nonlocal review_context
            if review_context is not None:
                raise ValueError("Review context is already published and immutable")
            review_context = params.context
            size = len(json.dumps(review_context, default=str))
            logger.info(
                "[tool] publish_review_context — %d bytes, keys=%s",
                size,
                sorted(review_context),
            )
            return json.dumps({"context_id": "review-context", "bytes": size})

        class ReadReviewContextParams(BaseModel):
            context_id: str = Field(default="review-context")

        @define_tool(description=(
            "Read the immutable context published by the main reviewer. Specialist "
            "reviewers must call this before their targeted exploration."
        ))
        def read_review_context(params: ReadReviewContextParams) -> str:
            if params.context_id != "review-context" or review_context is None:
                raise ValueError(f"Unknown or unpublished context: {params.context_id}")
            logger.info("[tool] read_review_context — context read by a reviewer")
            if staged_diffs:
                return json.dumps({**review_context, "staged_diffs": staged_diffs}, default=str)
            return json.dumps(review_context, default=str)

        class SubmitSpecialistFindingsParams(BaseModel):
            reviewer_id: str = Field(description="Exact registered persona or skill agent name")
            code_suggestions: list[ReviewFinding] = Field(
                default_factory=list,
                description="Structured findings; each one is posted as an inline MR comment",
            )
            overall_assessment: str = Field(default="Specialist review completed")
            additional_evidence: list = Field(
                default_factory=list,
                description="Extra files, lines, searches, or graph queries used to verify findings",
            )

        @define_tool(description=(
            "Submit one specialist review. Every persona and skill subagent must call "
            "this exactly once, including when it found no issues."
        ))
        def submit_specialist_findings(params: SubmitSpecialistFindingsParams) -> str:
            if params.reviewer_id not in expected_reviewers:
                raise ValueError(f"Unknown specialist reviewer: {params.reviewer_id}")
            if params.reviewer_id in specialist_results:
                raise ValueError(f"Reviewer already submitted: {params.reviewer_id}")
            specialist_results[params.reviewer_id] = {
                "code_suggestions": [finding.model_dump() for finding in params.code_suggestions],
                "overall_assessment": params.overall_assessment,
                "additional_evidence": params.additional_evidence,
            }
            logger.info(
                "[tool] submit_specialist_findings — reviewer=%s findings=%d evidence=%d",
                params.reviewer_id,
                len(params.code_suggestions),
                len(params.additional_evidence),
            )
            return json.dumps({
                "accepted": True,
                "reviewer_id": params.reviewer_id,
                "findings": len(params.code_suggestions),
            })

        @define_tool(description=(
            "Return completion status and submitted results for all required specialist "
            "reviewers. The main reviewer uses this before final submission."
        ))
        def get_specialist_findings() -> str:
            missing = [name for name in expected_reviewers if name not in specialist_results]
            logger.info(
                "[tool] get_specialist_findings — completed=%d/%d missing=%s",
                len(specialist_results),
                len(expected_reviewers),
                missing,
            )
            return json.dumps({
                "expected": expected_reviewers,
                "completed": list(specialist_results),
                "missing": missing,
                "results": specialist_results,
            }, default=str)

        # ---- Custom tool: write_review_findings ----
        class WriteReviewFindingsParams(BaseModel):
            code_suggestions: list[ReviewFinding] = Field(
                description="Your own structured findings; specialist findings are appended automatically"
            )
            overall_assessment: str = Field(
                default="Code review completed",
                description="Overall assessment summary of the review"
            )
            recommended_action: str = Field(
                default="NEEDS_DISCUSSION",
                description="Recommended action: APPROVED, NEEDS_DISCUSSION, or REQUEST_CHANGES"
            )

        @define_tool(description=(
            "Write the final code review findings. Call this ONCE after you "
            "have reviewed all changed files. Provide all code suggestions "
            "as structured data. Each suggestion must include: relevant_file, "
            "language, line_number, lines_to_replace, existing_code, "
            "suggestion_content, improved_code, one_sentence_summary, label, "
            "priority, and confidence."
        ), ends_session=True)
        def write_review_findings(params: WriteReviewFindingsParams) -> str:
            nonlocal final_written
            if final_written:
                raise ValueError("Final review findings have already been written")
            if expected_reviewers:
                if review_context is None:
                    raise ValueError("Publish the shared review context before final submission")
                missing = [name for name in expected_reviewers if name not in specialist_results]
                if missing:
                    raise ValueError(f"Missing required specialist reviews: {missing}")
            specialist_findings = [
                attribute_persona(finding, persona_by_agent.get(reviewer_id))
                for reviewer_id, result in specialist_results.items()
                for finding in result["code_suggestions"]
            ]
            combined_findings = [f.model_dump() for f in params.code_suggestions] + specialist_findings
            logger.info(
                "[tool] write_review_findings — base=%d specialist=%d total=%d",
                len(params.code_suggestions),
                len(specialist_findings),
                len(combined_findings),
            )
            captured_findings["code_suggestions"] = combined_findings
            captured_findings["summary"] = {
                "overall_assessment": params.overall_assessment,
                "recommended_action": params.recommended_action,
            }
            captured_findings["specialist_reviews"] = specialist_results
            final_written = True
            return (
                f"OK — {len(combined_findings)} total findings received. "
                f"Recommended action: {params.recommended_action}"
            )

        tools = [
            publish_review_context,
            read_review_context,
            submit_specialist_findings,
            get_specialist_findings,
            write_review_findings,
        ]

        # Prepare comprehensive review prompt (base — no skills)
        review_prompt = build_review_prompt(
            mr_title=mr_title,
            mr_description=mr_description,
            source_branch=source_branch,
            target_branch=target_branch,
            changed_files=changed_files,
            local_repo_path=local_repo_path,
            impact_context=impact_context,
            graph_tools_available=graph_tools_available,
            rfc_content=rfc_content,
            jira_file_path=jira_file_path,
            group_context=group_context,
        )

        if custom_agents:
            logger.info(
                f"Registered {len(custom_agents)} required specialist agents: "
                f"{[a['name'] for a in custom_agents]}"
            )

        _harness_name = get_harness_name()
        logger.info(f"Invoking {_harness_name} harness for code review...")

        try:
            # The agentic call handles the full tool loop automatically.
            # working_directory tells the SDK to set the agent's CWD to the
            # cloned repo so its built-in view/grep/list tools work on it.
            _final_response = call_llm_agentic(
                review_prompt,
                tools=tools,
                system_message=build_system_message(
                    expected_reviewers,
                    graph_tools_available=graph_tools_available,
                    group_review=bool(group_context),
                ),
                model=REVIEW_MODEL,
                working_directory=local_repo_path,
                custom_agents=custom_agents or None,
                enable_graph_tools=graph_tools_available,
            )

            logger.info(f"{_harness_name} code review completed")

            if (expected_reviewers or prep_res.get("strict_review")) and not final_written:
                raise RuntimeError(
                    "Review session ended without a validated final submission from "
                    "all required specialist reviewers"
                )

            # ---- Process findings captured by the tool ----
            review_data = captured_findings

            # Fallback: if the tool wasn't called (e.g. OpenCode outputs text
            # instead of invoking MCP tools), parse findings from the response.
            if not review_data.get("code_suggestions") and _final_response:
                parsed = self._parse_findings_from_response(_final_response)
                if parsed:
                    logger.info(
                        f"Parsed {len(parsed.get('code_suggestions', []))} "
                        f"findings from LLM text response (tool was not called)"
                    )
                    review_data = parsed

            validated_comments = []
            summary = "Code review completed"
            recommendation = "NEEDS_DISCUSSION"

            findings_list = review_data.get("code_suggestions", [])
            if findings_list:
                summary = f"Found {len(findings_list)} code suggestions"
                recommendation = "NEEDS_DISCUSSION" if findings_list else "APPROVED"

                for finding in findings_list:
                    if isinstance(finding, BaseModel):
                        finding = finding.model_dump() if hasattr(finding, 'model_dump') else dict(finding)
                    if not isinstance(finding, dict):
                        continue

                    # LLMs sometimes return ranges like "99-104" instead of ints
                    def _parse_range(value):
                        try:
                            parts = [int(p) for p in str(value).strip().split("-", 1)]
                        except (ValueError, TypeError):
                            return None, None
                        return parts[0], parts[-1]

                    raw_line = finding.get("line_number") or finding.get("line", 1)
                    line, line_end = _parse_range(raw_line)
                    if not line or line < 1:
                        line, line_end = 1, None

                    # Get lines_to_replace
                    start, end = _parse_range(finding.get("lines_to_replace"))
                    if start is None or start < 1:
                        lines_to_replace = None
                    elif end != start:
                        lines_to_replace = end - start + 1
                    else:
                        lines_to_replace = start
                    if not lines_to_replace and line_end and line_end >= line:
                        lines_to_replace = line_end - line + 1
                    if not lines_to_replace or lines_to_replace < 1:
                        existing_code = str(finding.get("existing_code", "")).strip()
                        if existing_code:
                            lines_to_replace = len(existing_code.split('\n'))
                        else:
                            lines_to_replace = 1

                    # Parse confidence score
                    confidence = finding.get("confidence", 1.0)
                    try:
                        confidence = float(str(confidence).strip())
                        confidence = max(0.0, min(1.0, confidence))
                    except (ValueError, TypeError):
                        confidence = 1.0

                    validated_comment = {
                        "relevant_file": str(finding.get("relevant_file", "")).strip(),
                        "language": str(finding.get("language", "")).strip(),
                        "existing_code": str(finding.get("existing_code", "")).strip(),
                        "suggestion_content": str(finding.get("suggestion_content", "")).strip(),
                        "improved_code": str(finding.get("improved_code", "")).strip(),
                        "one_sentence_summary": str(finding.get("one_sentence_summary", "")).strip(),
                        "label": str(finding.get("label", "general")).strip(),
                        "priority": str(finding.get("priority", "medium")).strip(),
                        "confidence": confidence,
                        "line": line,
                        "lines_to_replace": int(lines_to_replace),
                        "source_model": resolve_effective_model(REVIEW_MODEL),
                    }
                    if finding.get("persona_id"):
                        validated_comment["persona_id"] = finding["persona_id"]
                        validated_comment["persona_version"] = finding.get("persona_version")
                        validated_comment["persona_name"] = finding.get("persona_name")

                    if validated_comment["relevant_file"] and validated_comment["suggestion_content"]:
                        validated_comments.append(validated_comment)
                    else:
                        logger.warning(
                            "Dropping finding without relevant_file/suggestion_content (keys=%s)",
                            sorted(finding),
                        )

                logger.info(f"Loaded {len(validated_comments)} findings from {_harness_name} review")
            else:
                logger.warning("No code_suggestions captured by write_review_findings tool")

            # Apply confidence filtering
            min_confidence = prep_res.get("min_confidence", 0.6)
            original_count = len(validated_comments)
            validated_comments = [
                c for c in validated_comments 
                if c.get("confidence", 1.0) >= min_confidence
            ]

            filtered_count = original_count - len(validated_comments)
            if filtered_count > 0:
                logger.info(f"Filtered out {filtered_count} low-confidence suggestions (< {min_confidence})")

            logger.info(f"Generated {len(validated_comments)} review findings from {_harness_name}")

            # Calculate severity counts
            severity_counts = {
                "critical": 0,
                "high": 0,
                "medium": 0,
                "low": 0
            }

            category_counts = {
                "security": 0,
                "bugs": 0,
                "performance": 0,
                "best_practices": 0
            }

            files_with_issues = set()

            for c in validated_comments:
                file_path = c.get("relevant_file") or c.get("file", "")
                if file_path:
                    files_with_issues.add(file_path)

                label = c.get("label", "").lower()
                priority = c.get("priority", "medium").lower()

                # Count by priority
                if priority in severity_counts:
                    severity_counts[priority] += 1
                else:
                    severity_counts["medium"] += 1

                # Count by category
                if "security" in label:
                    category_counts["security"] += 1
                elif "bug" in label or "issue" in label:
                    category_counts["bugs"] += 1
                elif "performance" in label:
                    category_counts["performance"] += 1
                elif "best practice" in label or "maintainability" in label:
                    category_counts["best_practices"] += 1
                else:
                    category_counts["best_practices"] += 1

            # Get overall assessment from captured summary
            yaml_summary = review_data.get("summary", {})
            overall_assessment = yaml_summary.get("overall_assessment", summary) if isinstance(yaml_summary, dict) else summary
            recommended_action = yaml_summary.get("recommended_action", recommendation) if isinstance(yaml_summary, dict) else recommendation

            return {
                "review_comments": validated_comments,
                "review_findings": {
                    "summary": overall_assessment,
                    "recommendation": recommended_action,
                    "findings": validated_comments,
                    "total": len(validated_comments),
                    "by_severity": severity_counts,
                    "by_category": category_counts,
                    "files_reviewed": len(changed_files),
                    "files_with_issues": len(files_with_issues),
                    # Non-finding reviewer output (deferred cross-member concerns, evidence)
                    # consumed by the group combined reviewer.
                    "review_context": review_context or {},
                    "specialist_reviews": {
                        reviewer: {
                            "overall_assessment": result.get("overall_assessment", ""),
                            "additional_evidence": result.get("additional_evidence", []),
                        }
                        for reviewer, result in (review_data.get("specialist_reviews") or {}).items()
                    },
                },
                "raw_output": _final_response
            }

        except Exception as e:
            logger.error(f"{_harness_name} review failed: {e}")
            if expected_reviewers or prep_res.get("strict_review"):
                raise
            return {"review_comments": [], "review_findings": {}, "raw_output": ""}

    @staticmethod
    def _parse_findings_from_response(response: str) -> dict | None:
        """Extract structured findings from plain-text LLM response.

        When the LLM outputs a JSON block with ``findings`` and
        ``overall_assessment`` instead of calling the ``write_review_findings``
        MCP tool, this method recovers those findings so they aren't lost.
        """
        import json as _json
        import re

        # Try to find a JSON code block first (```json ... ```)
        json_blocks = re.findall(r"```json\s*\n(.*?)\n```", response, re.DOTALL)
        for block in json_blocks:
            try:
                data = _json.loads(block)
                if isinstance(data, dict) and "findings" in data:
                    return {
                        "code_suggestions": data["findings"],
                        "summary": {
                            "overall_assessment": data.get("overall_assessment", ""),
                            "recommended_action": data.get("recommended_action", "NEEDS_DISCUSSION"),
                        },
                    }
            except _json.JSONDecodeError:
                continue

        # Fallback: try to find raw JSON object in the response
        decoder = _json.JSONDecoder()
        for match in re.finditer(r"\{", response):
            start = match.start()
            try:
                data, _ = decoder.raw_decode(response, start)
                if isinstance(data, dict) and "findings" in data:
                    return {
                        "code_suggestions": data["findings"],
                        "summary": {
                            "overall_assessment": data.get("overall_assessment", ""),
                            "recommended_action": data.get("recommended_action", "NEEDS_DISCUSSION"),
                        },
                    }
            except _json.JSONDecodeError:
                continue

        return None

    def post(self, shared, prep_res, exec_res):
        shared["review_comments"] = exec_res["review_comments"]
        shared["review_findings"] = exec_res["review_findings"]
        shared["raw_output"] = exec_res.get("raw_output", "")


class WriteReviewOutput(Node):
    """Write the review output to YAML and JSON files with metadata including SHAs."""
    
    def prep(self, shared):
        review_comments = shared.get("review_comments", [])
        review_findings = shared.get("review_findings", {})
        local_repo_path = shared.get("local_repo_path")
        output_file = shared.get("output_file", "code_review_output.yaml")
        project_name = shared.get("project_name", "Unknown Project")
        source_branch = shared.get("source_branch")
        target_branch = shared.get("target_branch")
        base_sha = shared.get("base_sha")
        head_sha = shared.get("head_sha")
        start_sha = shared.get("start_sha")
        
        return {
            "review_comments": review_comments,
            "review_findings": review_findings,
            "local_repo_path": local_repo_path,
            "output_file": output_file,
            "project_name": project_name,
            "source_branch": source_branch,
            "target_branch": target_branch,
            "base_sha": base_sha,
            "head_sha": head_sha,
            "start_sha": start_sha
        }
    
    def exec(self, prep_res):
        from datetime import datetime
        
        review_comments = prep_res["review_comments"]
        review_findings = prep_res["review_findings"]
        local_repo_path = prep_res["local_repo_path"]
        output_file = prep_res["output_file"]
        project_name = prep_res["project_name"]
        source_branch = prep_res["source_branch"]
        target_branch = prep_res["target_branch"]
        base_sha = prep_res["base_sha"]
        head_sha = prep_res["head_sha"]
        start_sha = prep_res["start_sha"]
        
        # Build the main YAML output structure
        output_data = {
            "metadata": {
                "project": project_name,
                "source_branch": source_branch,
                "target_branch": target_branch,
                "review_date": datetime.now().isoformat(),
                "total_comments": len(review_comments),
                "diff_refs": {
                    "base_sha": base_sha,
                    "head_sha": head_sha,
                    "start_sha": start_sha
                },
                "review_findings": review_findings
            },
            "comments": review_comments
        }
        
        # Write main YAML output
        output_path = os.path.join(local_repo_path, output_file) if local_repo_path else output_file
        
        with open(output_path, 'w', encoding='utf-8') as f:
            yaml.dump(output_data, f, default_flow_style=False, allow_unicode=True, sort_keys=False)
        
        logger.info(f"Review output written to: {output_path}")
        
        # Also write review_findings.yaml for consistency
        if review_findings and local_repo_path:
            findings_path = os.path.join(local_repo_path, "review_findings.yaml")
            findings_yaml = {
                "code_suggestions": review_findings.get("findings", []),
                "summary": {
                    "total_issues": review_findings.get("total", len(review_comments)),
                    "by_severity": review_findings.get("by_severity", {}),
                    "by_category": review_findings.get("by_category", {}),
                    "files_reviewed": review_findings.get("files_reviewed", 0),
                    "files_with_issues": review_findings.get("files_with_issues", 0),
                    "overall_assessment": review_findings.get("summary", "Review completed"),
                    "recommended_action": review_findings.get("recommendation", "NEEDS_DISCUSSION")
                }
            }
            
            with open(findings_path, 'w', encoding='utf-8') as f:
                yaml.dump(findings_yaml, f, default_flow_style=False, allow_unicode=True, sort_keys=False)
            
            logger.info(f"Review findings written to: {findings_path}")
        
        return {
            "output_path": output_path,
            "output_data": output_data,
            "findings_data": review_findings
        }
    
    def post(self, shared, prep_res, exec_res):
        shared["review_output_path"] = exec_res["output_path"]
        shared["review_output_data"] = exec_res["output_data"]
        shared["review_findings"] = exec_res["findings_data"]


class PostMergeRequestComments(Node):
    """Post inline comments to merge request using python-gitlab library."""
    
    def _parse_diff_lines(self, diff_content):
        """Parse git diff and return dict mapping line numbers to line_code.
        
        Only lines that appear in the diff can have inline comments:
        - Lines with '+' prefix (newly added)
        - Lines with '-' prefix (removed - but can't comment on these in GitLab)
        - Lines with ' ' prefix (context lines around changes)
        """
        line_map = {}
        if not diff_content:
            return line_map
        
        current_new_line = None
        current_old_line = None
        
        for line in diff_content.split('\n'):
            if line.startswith('@@'):
                match = re.search(r'@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@', line)
                if match:
                    current_old_line = int(match.group(1))
                    current_new_line = int(match.group(2))
            elif current_new_line is not None:
                if line.startswith('+'):
                    # New line added - can comment
                    line_code = f"{current_new_line}_0_0"
                    line_map[current_new_line] = {'line_code': line_code, 'type': 'new', 'old_line': None}
                    current_new_line += 1
                elif line.startswith('-'):
                    # Line removed - cannot comment on deleted lines
                    current_old_line += 1
                elif line.startswith(' '):
                    # Unchanged line (context) - can comment
                    line_code = f"{current_new_line}_{current_old_line}_{current_new_line}"
                    line_map[current_new_line] = {'line_code': line_code, 'type': 'context', 'old_line': current_old_line}
                    current_new_line += 1
                    current_old_line += 1
        return line_map
    
    def prep(self, shared):
        review_output_data = shared.get("review_output_data", {})
        gitlab_token = shared.get("gitlab_token")
        gitlab_url = shared.get("gitlab_url", "https://gitlab.example.com")
        project_id = shared.get("project_id")
        merge_request_iid = shared.get("merge_request_iid")
        dry_run = shared.get("dry_run", False)
        base_sha = shared.get("base_sha")
        head_sha = shared.get("head_sha")
        start_sha = shared.get("start_sha")
        
        return {
            "review_output_data": review_output_data,
            "gitlab_token": gitlab_token,
            "gitlab_url": gitlab_url,
            "project_id": project_id,
            "merge_request_iid": merge_request_iid,
            "dry_run": dry_run,
            "base_sha": base_sha,
            "head_sha": head_sha,
            "start_sha": start_sha,
        }
    
    def exec(self, prep_res):
        review_output_data = prep_res["review_output_data"]
        gitlab_token = prep_res["gitlab_token"]
        gitlab_url = prep_res["gitlab_url"]
        project_id = prep_res["project_id"]
        merge_request_iid = prep_res["merge_request_iid"]
        dry_run = prep_res["dry_run"]
        base_sha = prep_res["base_sha"]
        head_sha = prep_res["head_sha"]
        start_sha = prep_res["start_sha"]
        
        comments = review_output_data.get("comments", [])
        metadata = review_output_data.get("metadata", {})
        
        if not comments:
            logger.info("No comments to post")
            return {"posted_comments": [], "summary_posted": False, "failed_comments": []}
        
        if dry_run:
            logger.info("Dry run mode - not posting comments to GitLab")
            formatted_comments = self._format_comments(comments)
            for fc in formatted_comments:
                logger.info(f"Would post: {fc['file']}:{fc['line']} - {fc['body'][:100]}...")
            return {"posted_comments": formatted_comments, "summary_posted": False, "failed_comments": []}
        
        if not gitlab_token or not project_id or not merge_request_iid:
            logger.warning("Missing GitLab credentials or MR info - skipping comment posting")
            return {"posted_comments": [], "summary_posted": False, "failed_comments": []}
        
        # Use SHAs from metadata if not provided
        if not base_sha:
            base_sha = metadata.get("diff_refs", {}).get("base_sha")
        if not head_sha:
            head_sha = metadata.get("diff_refs", {}).get("head_sha")
        if not start_sha:
            start_sha = metadata.get("diff_refs", {}).get("start_sha")
        
        logger.info(f"Posting {len(comments)} inline comments to MR {merge_request_iid}")
        
        posted_comments = []
        failed_comments = []
        
        # Initialize GitLab connection once
        try:
            gl = scm.connect(gitlab_url, private_token=gitlab_token)
            project = gl.projects.get(project_id)
            mr = project.mergerequests.get(merge_request_iid)
        except Exception as e:
            logger.error(f"Failed to connect to GitLab: {e}")
            return {"posted_comments": [], "summary_posted": False, "failed_comments": []}
        
        # Fetch MR changes to get proper line_code for each diff position
        try:
            mr_changes = mr.changes(access_raw_diffs=True)
            line_code_map = self._build_line_code_map(mr_changes)
            logger.info(f"Built line_code map for {len(line_code_map)} files")
            
            # Log valid line ranges for debugging
            for file_path, line_map in line_code_map.items():
                if line_map:
                    valid_lines = sorted(line_map.keys())
                    logger.debug(f"Valid lines for {file_path}: {valid_lines[:10]}{'...' if len(valid_lines) > 10 else ''}")
        except Exception as e:
            logger.error(f"Failed to fetch MR changes: {e}")
            line_code_map = {}
        
        # Collect files where comments target lines missing from the line_code_map.
        # A file may be in the map but with a truncated diff (only some lines).
        formatted_comments = self._format_comments(comments)
        missing_files = set()
        for fc in formatted_comments:
            fp = fc.get('file')
            ln = fc.get('line')
            if fp and ln and not line_code_map.get(fp, {}).get(ln):
                missing_files.add(fp)
        
        # Fallback: use the MR versions API for files with truncated diffs
        if missing_files:
            logger.info(
                f"{len(missing_files)} file(s) with missing lines in line_code map, "
                f"trying MR versions API: {missing_files}"
            )
            self._fill_from_mr_versions(mr, line_code_map, missing_files)
        
        findings = []
        for index, fc in enumerate(formatted_comments):
            if not fc['file'] or not fc['line']:
                logger.warning(f"Skipping comment with invalid file/line: {fc['body'][:50]}")
                failed_comments.append({
                    "file": fc['file'],
                    "line": fc['line'],
                    "reason": "invalid_location"
                })
                continue
            
            # Get line_code for proper positioning
            file_path = fc['file']
            line_number = fc['line']
            line_info = line_code_map.get(file_path, {}).get(line_number)
            
            if not line_info:
                logger.warning(f"Line {line_number} not in diff for {file_path} (skipping inline comment)")
                failed_comments.append({
                    "file": file_path,
                    "line": line_number,
                    "reason": "line_not_in_diff"
                })
                continue
            
            publication = self._post_inline_discussion_via_gitlab_lib(
                mr=mr,
                base_sha=base_sha,
                start_sha=start_sha,
                head_sha=head_sha,
                file_path=fc['file'],
                line_number=fc['line'],
                line_info=line_info,
                comment_body=fc['body']
            )
            
            finding = {
                "id": f"improve-{index + 1}",
                "headSha": head_sha,
                "filePath": fc["file"],
                "oldPath": fc["file"],
                "newLine": fc["line"],
                "body": fc["body"],
                "publicationStatus": "posted" if publication else "failed",
            }
            if fc.get("persona_id"):
                finding["personaId"] = fc["persona_id"]
                finding["personaVersion"] = fc["persona_version"]
            if publication:
                finding["id"] = publication["id"]
                if publication.get("commentUrl"):
                    finding["commentUrl"] = publication["commentUrl"]
                posted_comments.append({
                    "file": fc['file'],
                    "line": fc['line'],
                    "status": "posted_inline",
                    **publication,
                })
            else:
                failed_comments.append({
                    "file": fc['file'],
                    "line": fc['line'],
                    "reason": "post_failed"
                })
            findings.append(finding)
        
        return {
            "posted_comments": posted_comments,
            "failed_comments": failed_comments,
            "summary_posted": False,
            "findings": findings,
        }
    
    def _post_inline_discussion_via_gitlab_lib(self, mr, base_sha, start_sha, head_sha, 
                                                file_path, line_number, line_info, comment_body):
        """Post inline discussion using python-gitlab library with proper line_code."""
        try:
            # Create position for inline comment using line_code from GitLab API
            position = {
                'base_sha': base_sha,
                'start_sha': start_sha,
                'head_sha': head_sha,
                'position_type': 'text',
                'old_path': file_path,
                'new_path': file_path,
                'new_line': line_number
            }

            # Add line_code (required by GitLab API)
            if line_info.get('line_code'):
                position['line_code'] = line_info['line_code']

            # Add old_line if available (for changed lines)
            if line_info.get('old_line'):
                position['old_line'] = line_info['old_line']
            
            # Create discussion with inline comment
            discussion = mr.discussions.create({
                'body': comment_body,
                'position': position
            })
            
            notes = discussion.attributes.get("notes", [])
            note_id = notes[0].get("id") if notes else None
            finding_id = str(note_id or getattr(discussion, "id", f"{file_path}:{line_number}"))
            merge_request_url = getattr(mr, "web_url", None)
            logger.info(f"✓ Posted inline comment on {file_path}:{line_number}")
            return {
                "id": finding_id,
                "commentUrl": f"{merge_request_url}#note_{note_id}" if merge_request_url and note_id else None,
            }
            
        except gitlab.exceptions.GitlabCreateError as e:
            error_msg = str(e)
            logger.error(f"✗ Failed to post comment on {file_path}:{line_number} - {error_msg}")
            return False
        except Exception as e:
            logger.error(f"✗ Exception posting inline comment on {file_path}:{line_number}: {e}")
            return False
    
    def _build_line_code_map(self, mr_changes):
        """Build a map of file paths to line numbers to line_code and position info."""
        line_code_map = {}
        
        for change in mr_changes.get('changes', []):
            file_path = change.get('new_path') or change.get('old_path')
            if not file_path:
                continue
            
            diff_content = change.get('diff', '')
            line_map = self._parse_diff_lines(diff_content)
            
            if line_map:
                line_code_map[file_path] = line_map
        
        return line_code_map
    
    def _fill_from_mr_versions(self, mr, line_code_map, missing_files):
        """Fill line_code_map gaps using the MR versions (diff versions) API.
        
        The /versions/:id endpoint returns per-file diffs that are not subject
        to the same truncation as mr.changes().  We fetch the latest version
        and parse diffs only for the files we are missing.
        """
        try:
            versions = mr.diffs.list()
            if not versions:
                logger.warning("No MR diff versions found")
                return
            
            # Latest version is first in the list
            latest = mr.diffs.get(versions[0].id)
            diffs = latest.diffs if hasattr(latest, 'diffs') else []
            
            filled = 0
            for diff_entry in diffs:
                file_path = diff_entry.get('new_path') or diff_entry.get('old_path')
                if file_path not in missing_files:
                    continue
                
                diff_content = diff_entry.get('diff', '')
                if not diff_content:
                    continue
                
                line_map = self._parse_diff_lines(diff_content)
                if line_map:
                    line_code_map[file_path] = line_map
                    filled += 1
                    logger.info(
                        f"Filled line_code map for {file_path} via versions API "
                        f"({len(line_map)} lines)"
                    )
            
            if filled:
                logger.info(f"Versions API filled {filled}/{len(missing_files)} missing files")
            else:
                logger.warning("Versions API did not resolve any missing files")
        except Exception as e:
            logger.warning(f"MR versions fallback failed: {e}")
    
    def _format_comments(self, comments):
        """Format comments for posting to GitLab with suggestion blocks."""
        formatted = []
        
        for comment in comments:
            label = comment.get("label", "general").lower()
            
            # Build comment body
            suggestion_content = comment.get("suggestion_content", "")
            existing_code = comment.get("existing_code", "").strip()
            improved_code = comment.get("improved_code", "").strip()
            lines_to_replace = comment.get("lines_to_replace", 0)
            
            body = f"**{label.title()}:** {suggestion_content}\n\n"
            persona_tag = build_persona_tag(comment.get("persona_name") or comment.get("persona_id"))
            if persona_tag:
                body = f"{persona_tag}\n\n{body}"
            
            # Add suggestion block if we have improved code
            if existing_code and improved_code and lines_to_replace > 0:
                lines_below = lines_to_replace - 1
                
                body += f"```suggestion:-0+{lines_below}\n"
                body += improved_code
                if not improved_code.endswith('\n'):
                    body += '\n'
                body += "```\n"
                body += f"\n*Priority: {comment.get('priority', 'medium')}*"
            elif improved_code:
                # Just show improved code without suggestion block
                language = comment.get("language", "")
                body += f"```{language}\n{improved_code}\n```\n"
                body += f"\n*Priority: {comment.get('priority', 'medium')}*"

            source_harness = normalize_source_harness(
                comment.get("source_harness") or get_harness_label()
            ) or ""
            source_model = normalize_source_model(
                comment.get("source_model", "")
            ) or ""
            marker = build_review_comment_marker(
                source_harness,
                source_model,
            )
            if marker:
                # GitLab stores HTML comments in the note body/API response,
                # but does not render them in the discussion UI.
                body = f"{body.rstrip()}\n\n{marker}"
            run_id = os.environ.get("ARNOLD_RUN_ID")
            persona_marker = build_persona_marker(
                run_id, comment.get("persona_id"), comment.get("persona_version"),
            )
            if run_id:
                # Lets Arnold attribute review feedback on this note to Arnold-triggered runs.
                body = f"{body.rstrip()}\n\n{ARNOLD_COMMENT_MARKER}"
            if persona_marker:
                body = f"{body.rstrip()}\n\n{persona_marker}"
            
            formatted.append({
                "file": comment.get("relevant_file", comment.get("file", "")),
                "line": comment.get("line", 1) or 1,
                "body": body,
                "label": label,
                "source_harness": source_harness,
                "source_model": source_model,
                "persona_id": comment.get("persona_id") if persona_marker else None,
                "persona_version": comment.get("persona_version") if persona_marker else None,
            })
        
        return formatted
    
    def post(self, shared, prep_res, exec_res):
        shared["posted_comments"] = exec_res["posted_comments"]
        shared["failed_comments"] = exec_res.get("failed_comments", [])
        shared["summary_posted"] = exec_res["summary_posted"]
        shared["analysis_findings"] = exec_res.get("findings", [])


def discussion_comments(mr):
    comments = []
    for discussion in mr.discussions.list(iterator=True):
        for note in discussion.attributes.get("notes", []):
            if note.get("system"):
                continue
            position = note.get("position") or {}
            line_number = position.get("new_line") or position.get("old_line")
            comments.append({
                "file": position.get("new_path") or position.get("old_path") or "",
                "line": int(line_number) if line_number else None,
                "body": note.get("body", ""),
                "discussion_id": discussion.id,
                "note_id": note.get("id"),
            })
    return comments


class FetchExistingComments(Node):
    """Fetch existing discussion comments from the merge request to avoid posting duplicates."""

    def prep(self, shared):
        gitlab_token = shared.get("gitlab_token")
        gitlab_url = shared.get("gitlab_url", "https://gitlab.example.com")
        project_id = shared.get("project_id")
        merge_request_iid = shared.get("merge_request_iid")
        dry_run = shared.get("dry_run", False)

        return {
            "gitlab_token": gitlab_token,
            "gitlab_url": gitlab_url,
            "project_id": project_id,
            "merge_request_iid": merge_request_iid,
            "dry_run": dry_run,
        }

    def exec(self, prep_res):
        gitlab_token = prep_res["gitlab_token"]
        gitlab_url = prep_res["gitlab_url"]
        project_id = prep_res["project_id"]
        merge_request_iid = prep_res["merge_request_iid"]
        dry_run = prep_res["dry_run"]

        existing_comments = []

        if dry_run:
            logger.info("Dry run mode — skipping fetch of existing MR comments")
            return {"existing_comments": existing_comments}

        if not gitlab_token or not project_id or not merge_request_iid:
            logger.warning(
                "Missing GitLab credentials or MR info — cannot fetch existing comments"
            )
            return {"existing_comments": existing_comments}

        try:
            gl = scm.connect(gitlab_url, private_token=gitlab_token)
            project = gl.projects.get(project_id)
            mr = project.mergerequests.get(merge_request_iid)

            existing_comments = discussion_comments(mr)

            inline_count = sum(1 for c in existing_comments if c["file"])
            general_count = len(existing_comments) - inline_count
            logger.info(
                f"Collected {len(existing_comments)} existing comments "
                f"({inline_count} inline, {general_count} general)"
            )

        except Exception as e:
            logger.error(f"Failed to fetch existing MR comments: {e}")

        return {"existing_comments": existing_comments}

    def post(self, shared, prep_res, exec_res):
        shared["existing_comments"] = exec_res["existing_comments"]


class FilterDuplicateComments(Node):
    """Filter out review suggestions that match comments already posted on the MR.

    Uses an LLM agent to semantically compare new review comments against
    existing MR comments and remove duplicates.
    """

    def prep(self, shared):
        review_comments = shared.get("review_comments", [])
        existing_comments = shared.get("existing_comments", [])
        review_findings = shared.get("review_findings", {})

        return {
            "review_comments": review_comments,
            "existing_comments": existing_comments,
            "review_findings": review_findings,
            "persona_order": [persona["id"] for persona in shared.get("review_personas", [])],
            "strict_review": shared.get("strict_review", False),
        }

    def exec(self, prep_res):
        from pydantic import BaseModel, Field
        from utils.tool_adapter import define_tool

        review_comments = prep_res["review_comments"]
        existing_comments = prep_res["existing_comments"]
        review_findings = prep_res["review_findings"]

        if not review_comments:
            logger.info("No review comments to filter")
            return {
                "review_comments": review_comments,
                "review_findings": review_findings,
                "duplicates_removed": 0,
            }

        # Skip the agentic dedup call if there's nothing to deduplicate:
        # - Only 1 review comment AND no existing comments
        inline_existing = (existing_comments or []) if prep_res.get("strict_review") else [c for c in (existing_comments or []) if c.get("file")]
        if len(review_comments) <= 1 and not inline_existing:
            logger.info("Single comment, no existing comments — nothing to deduplicate")
            return {
                "review_comments": review_comments,
                "review_findings": review_findings,
                "duplicates_removed": 0,
            }

        original_count = len(review_comments)

        # --- Captured state for tool closure ---
        captured_results: dict = {"keep_ids": None, "drop_ids": None, "duplicate_of": None}

        # ---- Custom tool: write_filter_results ----
        class FilterResultsParams(BaseModel):
            keep_comment_ids: list[str] = Field(
                description=(
                    "List of comment IDs to KEEP and post (e.g. [\"new-0\", \"new-2\", \"new-4\"]). "
                    "These are the unique, valuable comments that should be posted."
                )
            )
            drop_comment_ids: list[str] = Field(
                default_factory=list,
                description=(
                    "List of comment IDs to DROP (e.g. [\"new-1\", \"new-3\"]). "
                    "These are duplicates or redundant comments."
                ),
            )
            reasoning: str = Field(
                default="",
                description="Brief explanation of why dropped comments were removed.",
            )
            duplicate_of: dict[str, str] = Field(
                default_factory=dict,
                description=(
                    "Mapping from every dropped new comment ID to the retained new "
                    "comment ID, or to a prev-* ID when it duplicates an existing comment."
                ),
            )

        @define_tool(description=(
            "Write the final filtering decision. Call this ONCE after analysing "
            "all review comments. Provide the list of comment IDs to KEEP "
            "(post to MR) and the list to DROP (duplicates/redundant). "
            "Every comment ID must appear in exactly one list."
        ), ends_session=True)
        def write_filter_results(params: FilterResultsParams) -> str:
            if prep_res.get("strict_review"):
                ids = params.keep_comment_ids + params.drop_comment_ids
                if len(ids) != original_count or set(ids) != {f"new-{i}" for i in range(original_count)}:
                    raise ValueError("Deduplication must classify every finding exactly once")
                if set(params.duplicate_of) != set(params.drop_comment_ids):
                    raise ValueError("Every dropped finding needs a duplicate_of mapping")
                valid_targets = set(params.keep_comment_ids) | {
                    f"prev-{i}" for i in range(len(inline_existing))
                }
                if not set(params.duplicate_of.values()).issubset(valid_targets):
                    raise ValueError("duplicate_of references an unknown retained finding")
            logger.info(
                f"[tool] write_filter_results called — "
                f"keep={len(params.keep_comment_ids)}, "
                f"drop={len(params.drop_comment_ids)}"
            )
            captured_results["keep_ids"] = params.keep_comment_ids
            captured_results["drop_ids"] = params.drop_comment_ids
            captured_results["duplicate_of"] = params.duplicate_of
            captured_results["reasoning"] = params.reasoning
            return (
                f"OK — keeping {len(params.keep_comment_ids)} comments, "
                f"dropping {len(params.drop_comment_ids)} duplicates."
            )

        tools = [write_filter_results]

        # Build the dedup prompt — handles both intra-review and vs-existing
        prompt = build_filter_duplicates_prompt(review_comments, inline_existing)

        _harness_name = get_harness_name()
        logger.info(f"Invoking {_harness_name} harness for duplicate comment filtering...")

        try:
            _response = call_llm_agentic(
                prompt,
                tools=tools,
                system_message=FILTER_DUPLICATES_SYSTEM_MESSAGE,
                model="gpt-6.1-sol",
            )

            logger.info(f"{_harness_name} duplicate filtering completed")

            # ---- Process captured results ----
            keep_ids = captured_results.get("keep_ids")

            if keep_ids is None:
                if prep_res.get("strict_review"):
                    raise RuntimeError("Duplicate filtering did not return a validated result")
                # Tool was never called — keep everything
                logger.warning("write_filter_results was not called — keeping all comments")
                return {
                    "review_comments": review_comments,
                    "review_findings": review_findings,
                    "duplicates_removed": 0,
                }

            # Parse indices from "new-<idx>" IDs
            keep_indices: set[int] = set()
            for cid in keep_ids:
                try:
                    idx = int(cid.split("-", 1)[1])
                    if 0 <= idx < original_count:
                        keep_indices.add(idx)
                except (IndexError, ValueError):
                    logger.warning(f"Could not parse comment ID: {cid}")

            # Empty keep list: trust it only when every finding is explicitly dropped as a
            # duplicate of a valid target; otherwise keep all (guards against a forgotten list).
            valid_targets = {f"prev-{i}" for i in range(len(inline_existing))}
            duplicate_of = captured_results.get("duplicate_of") or {}
            all_explicit_duplicates = all(
                duplicate_of.get(f"new-{i}") in valid_targets for i in range(original_count)
            )
            if (
                not keep_indices and review_comments and not all_explicit_duplicates
                and not prep_res.get("strict_review")
            ):
                logger.warning("Agent returned empty keep list — keeping all comments as safety fallback")
                return {
                    "review_comments": review_comments,
                    "review_findings": review_findings,
                    "duplicates_removed": 0,
                }

            inherit_duplicate_personas(
                review_comments,
                captured_results.get("duplicate_of"),
                prep_res.get("persona_order", []),
            )

            # Filter to only kept comments
            filtered_comments = [
                c for idx, c in enumerate(review_comments)
                if idx in keep_indices
            ]

            # Log dropped comments
            drop_ids = captured_results.get("drop_ids", [])
            for cid in drop_ids:
                try:
                    idx = int(cid.split("-", 1)[1])
                    if 0 <= idx < original_count:
                        comment = review_comments[idx]
                        file_path = comment.get("relevant_file", "")
                        line = comment.get("line", "?")
                        summary = comment.get("one_sentence_summary", "")[:80]
                        logger.info(f"Dropped: {file_path}:{line} — {summary}")
                except (IndexError, ValueError):
                    pass

            duplicates_removed = original_count - len(filtered_comments)
            reasoning = captured_results.get("reasoning", "")
            logger.info(
                f"Deduplication: {original_count} suggestions → "
                f"{len(filtered_comments)} "
                f"({duplicates_removed} duplicates removed)"
            )
            if reasoning:
                logger.info(f"Agent reasoning: {reasoning}")

            # Update the review_findings to reflect the filtered list
            if review_findings and duplicates_removed > 0:
                review_findings["findings"] = filtered_comments
                review_findings["total"] = len(filtered_comments)
                review_findings["duplicates_removed"] = duplicates_removed

            return {
                "review_comments": filtered_comments,
                "review_findings": review_findings,
                "duplicates_removed": duplicates_removed,
            }

        except Exception as e:
            if prep_res.get("strict_review"):
                raise
            logger.error(f"{_harness_name} duplicate filtering failed: {e}")
            logger.warning("Falling back to keeping all comments (no dedup)")
            return {
                "review_comments": review_comments,
                "review_findings": review_findings,
                "duplicates_removed": 0,
            }

    def post(self, shared, prep_res, exec_res):
        shared["review_comments"] = exec_res["review_comments"]
        shared["review_findings"] = exec_res["review_findings"]
        shared["duplicates_removed"] = exec_res.get("duplicates_removed", 0)
