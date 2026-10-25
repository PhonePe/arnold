"""Sonarfix flow definitions.

Provides two flow variants:
- create_sonarfix_flow()        — full flow (clone + checkout + fix + cleanup)
- create_local_sonarfix_flow()  — local repo flow (checkout + fix, no clone/cleanup)

Agent strategy: Hybrid sequential
  1. FixIssues  — only if issue-based conditions are failing
  2. FixCoverage — only if coverage conditions are failing
  3. FixDuplication — only if duplication conditions are failing
Each agent skips itself if its condition type is already passing.
"""

from pocketflow import Flow
from flows.code_reviews.common.nodes import (
    PostAcknowledgment,
    CloneRepository,
    CheckoutBranch,
    Cleanup,
)
from flows.sonarfix.nodes import (
    DetectSonarConfig,
    FetchQualityGateStatus,
    FetchSonarQubeIssues,
    FixIssues,
    FixCoverage,
    FixDuplication,
    CreateFixBranch,
    CreateMergeRequest,
)


def create_sonarfix_flow():
    """Creates the full sonarfix flow (remote repository).

    Order:
      ack -> clone -> checkout -> detect config -> fetch quality gate
        -> (gate_failed) fetch issues -> fix issues -> fix coverage
           -> fix duplication -> branch -> MR -> cleanup
        -> (gate_passed) cleanup  (quality gate already green, nothing to do)
    """
    post_ack = PostAcknowledgment()
    clone_repo = CloneRepository()
    checkout_branch = CheckoutBranch()
    detect_config = DetectSonarConfig()
    fetch_quality_gate = FetchQualityGateStatus()
    fetch_issues = FetchSonarQubeIssues()
    fix_issues = FixIssues(max_retries=3, wait=10)
    fix_coverage = FixCoverage(max_retries=3, wait=10)
    fix_duplication = FixDuplication(max_retries=3, wait=10)
    create_branch = CreateFixBranch()
    create_mr = CreateMergeRequest()
    cleanup = Cleanup()

    # Happy path: gate failed -> fetch issues and fix sequentially
    post_ack >> clone_repo >> checkout_branch >> detect_config >> fetch_quality_gate
    fetch_quality_gate - "gate_failed" >> fetch_issues >> fix_issues >> fix_coverage >> fix_duplication >> create_branch >> create_mr >> cleanup

    # Short-circuit: gate passed -> nothing to fix
    fetch_quality_gate - "gate_passed" >> cleanup

    return Flow(start=post_ack)


def create_local_sonarfix_flow():
    """Creates a sonarfix flow for a local repository (already cloned).

    Order:
      ack -> checkout -> detect config -> fetch quality gate
        -> (gate_failed) fetch issues -> fix issues -> fix coverage
           -> fix duplication -> branch -> MR -> cleanup
        -> (gate_passed) cleanup  (quality gate already green, nothing to do)
    """
    post_ack = PostAcknowledgment()
    checkout_branch = CheckoutBranch()
    detect_config = DetectSonarConfig()
    fetch_quality_gate = FetchQualityGateStatus()
    fetch_issues = FetchSonarQubeIssues()
    fix_issues = FixIssues(max_retries=3, wait=10)
    fix_coverage = FixCoverage(max_retries=3, wait=10)
    fix_duplication = FixDuplication(max_retries=3, wait=10)
    create_branch = CreateFixBranch()
    create_mr = CreateMergeRequest()
    cleanup = Cleanup()

    # Happy path: gate failed -> fetch issues and fix sequentially
    post_ack >> checkout_branch >> detect_config >> fetch_quality_gate
    fetch_quality_gate - "gate_failed" >> fetch_issues >> fix_issues >> fix_coverage >> fix_duplication >> create_branch >> create_mr >> cleanup

    # Short-circuit: gate passed -> cleanup (deletes ack comment)
    fetch_quality_gate - "gate_passed" >> cleanup

    return Flow(start=post_ack)
