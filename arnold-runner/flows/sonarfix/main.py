#!/usr/bin/env python3
"""SonarFix Flow -- Automated SonarQube issue fixing for GitLab merge requests.

Usage:
  python main.py sonarfix --mr-link <MR_URL> --sonar-url <URL> [options]
  python main.py sonarfix --sonar-issues-file issues.json --repo <REPO_URL> -s <BRANCH> [options]
  python main.py sonarfix --local-dir ./repo -s <BRANCH> --sonar-issues-file issues.json [options]

Agent Strategy (Hybrid Sequential):
  1. Issues agent  -- fixes bugs, vulnerabilities, code smells, hotspots
  2. Coverage agent -- writes unit tests to improve coverage
  3. Duplication agent -- refactors duplicated code blocks
  Only launches agents for FAILING conditions; skips the rest.
"""

from dotenv import load_dotenv
import argparse
import logging
import os

from flows.code_reviews.common.cli import (
    add_common_args, resolve_args, build_shared_dict, post_failure_notification,
)
from flows.sonarfix.flow import create_sonarfix_flow, create_local_sonarfix_flow
from utils.logger import get_logger

load_dotenv()

logger = get_logger("sonarfix_main_logger")


def main():
    parser = argparse.ArgumentParser(
        description="SonarFix Flow -- Auto-fix SonarQube issues on a merge request",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  # Fix issues using token auth (recommended)\n"
            "  python main.py sonarfix --mr-link <MR_URL> \\\n"
            "      --sonar-url https://sonarqube.example.com \\\n"
            "      --sonar-token <TOKEN>\n\n"
            "  # Fix issues using cookie auth\n"
            "  python main.py sonarfix --mr-link <MR_URL> \\\n"
            "      --sonar-url https://sonarqube.example.com \\\n"
            "      --sonar-cookies 'key=val'\n\n"
            "  # Fix issues with explicit component key\n"
            "  python main.py sonarfix --mr-link <MR_URL> \\\n"
            "      --sonar-url https://sonarqube.example.com \\\n"
            "      --sonar-component-key com.example:project \\\n"
            "      --sonar-pr 80 --sonar-token <TOKEN>\n\n"
            "  # Fix issues from a saved JSON file\n"
            "  python main.py sonarfix --mr-link <MR_URL> \\\n"
            "      --sonar-issues-file sonar_issues.json\n\n"
            "  # Fix on a local repo with tracking\n"
            "  python main.py sonarfix --local-dir ./myrepo -s feature-branch \\\n"
            "      --sonar-issues-file issues.json --tracking-file tracking.json"
        ),
    )

    add_common_args(parser)

    # SonarQube options
    sonar_group = parser.add_argument_group("SonarQube options")
    sonar_group.add_argument(
        "--sonar-url",
        help="SonarQube server URL (e.g., https://sonarqube.example.com)",
    )
    sonar_group.add_argument(
        "--sonar-component-key",
        help="SonarQube project component key (auto-detected from pom.xml/build.gradle if not provided)",
    )
    sonar_group.add_argument(
        "--sonar-pr",
        help="SonarQube pull request number (auto-detected from MR IID if not provided)",
    )
    sonar_group.add_argument(
        "--sonar-cookies",
        help="SonarQube cookies for authentication (semicolon-separated key=value pairs)",
    )
    sonar_group.add_argument(
        "--sonar-token",
        help="SonarQube API token for authentication (recommended over cookies for SonarQube 10.x+)",
    )
    sonar_group.add_argument(
        "--sonar-issues-file",
        help="Path to a pre-saved JSON file with SonarQube issues (skip API call)",
    )

    # Fix options
    fix_group = parser.add_argument_group("Fix options")
    fix_group.add_argument(
        "--tracking-file",
        help="JSON file to track issue fix status (for resumability across runs)",
    )
    fix_group.add_argument(
        "--fix-branch",
        help="Name for the fix branch (default: sonarfix/<source-branch>)",
    )
    fix_group.add_argument(
        "--push",
        action="store_true",
        help="Push the fix branch to the remote after committing",
    )
    fix_group.add_argument(
        "--create-mr",
        action="store_true",
        help="Create a GitLab merge request with the fixes targeting the source branch",
    )

    args = parser.parse_args()

    (repo_url, source_branch, target_branch, project_id, mr_iid,
     gitlab_url, gitlab_token, project_name, mr_details) = resolve_args(args)

    shared = build_shared_dict(
        args, repo_url, source_branch, target_branch, project_id,
        mr_iid, gitlab_url, gitlab_token, project_name, mr_details,
    )

    # SonarQube-specific shared keys
    shared.update({
        "flow_type": "sonarfix",
        "sonar_url": args.sonar_url or os.environ.get("SONAR_URL", ""),
        "sonar_component_key": args.sonar_component_key or os.environ.get("SONAR_COMPONENT_KEY", ""),
        "sonar_pr_number": args.sonar_pr or (str(mr_iid) if mr_iid else ""),
        "sonar_cookies": args.sonar_cookies or os.environ.get("SONAR_COOKIES", ""),
        "sonar_token": args.sonar_token or os.environ.get("SONAR_TOKEN", ""),
        "sonar_issues_file": args.sonar_issues_file,
        "tracking_file": args.tracking_file,
        "fix_branch_name": args.fix_branch,
        "push_branch": args.push,
        # Outputs
        "sonar_issues": [],
        "sonar_issues_total": 0,
        "tracked_issues": [],
        "fix_results": [],
        "total_fixed": 0,
        "total_skipped": 0,
        "fix_branch_created": False,
        "fix_branch_pushed": False,
        "quality_gate": None,
        "measures": {},
        "create_mr": args.create_mr,
        "mr_created": False,
        "mr_url": None,
    })

    logger.info("=" * 60)
    logger.info(f"SonarFix Flow: {project_name}")
    logger.info(f"Branch: {source_branch} -> {target_branch}")
    logger.info("Agent strategy: Hybrid sequential (issues -> coverage -> duplication)")
    if mr_iid:
        logger.info(f"MR: {mr_iid}")
    if args.sonar_issues_file:
        logger.info(f"Issues source: {args.sonar_issues_file}")
    else:
        logger.info(f"SonarQube: {args.sonar_url or 'N/A'}")
    logger.info("=" * 60)

    if args.local_dir:
        logger.info("Using local repository (skipping clone)")
        flow = create_local_sonarfix_flow()
    else:
        logger.info("Using remote repository (will clone)")
        flow = create_sonarfix_flow()

    try:
        flow.run(shared)

        logger.info("=" * 60)
        logger.info("SonarFix Flow Completed!")
        logger.info(f"Total issues: {shared.get('sonar_issues_total', 0)}")
        logger.info(f"Fixed: {shared.get('total_fixed', 0)}")
        logger.info(f"Skipped: {shared.get('total_skipped', 0)}")
        if shared.get("fix_branch_created"):
            logger.info(f"Fix branch: {shared.get('fix_branch_name', 'N/A')}")
            if shared.get("fix_branch_pushed"):
                logger.info("Branch pushed to remote")
            else:
                logger.info("Branch NOT pushed (use --push or --create-mr)")
        if shared.get("mr_created"):
            logger.info(f"Merge Request: {shared.get('mr_url', 'N/A')}")
        if shared.get("tracking_file"):
            logger.info(f"Tracking file: {shared['tracking_file']}")
        logger.info("=" * 60)
    except Exception as e:
        logger.error(f"SonarFix flow failed: {e}")
        post_failure_notification(shared, e)
        raise


if __name__ == "__main__":
    main()
