#!/usr/bin/env python3
"""Describe Flow - Automated PR description generation for GitLab merge requests.

Usage:
  python -m flows.code_reviews.describe.main --mr-link <MR_URL>
  python -m flows.code_reviews.describe.main --repo <REPO_URL> --source-branch <BRANCH>
"""

from dotenv import load_dotenv
import argparse
import logging

from flows.code_reviews.common.cli import add_common_args, resolve_args, build_shared_dict, post_failure_notification
from flows.code_reviews.describe.flow import create_describe_flow, create_local_describe_flow
from utils.logger import get_logger

load_dotenv()

logger = get_logger("describe_main_logger")


def main():
    parser = argparse.ArgumentParser(
        description="Describe Flow — Automated PR description for GitLab MRs",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Examples:\n"
               "  python -m flows.code_reviews.describe.main --mr-link <MR_URL>\n"
               "  python -m flows.code_reviews.describe.main --repo <REPO_URL> -s feature-branch",
    )

    add_common_args(parser)

    # --persistent-comment kept for backward CLI compatibility — now a no-op
    # since delete-and-recreate is the default behavior.
    parser.add_argument(
        "--persistent-comment", action="store_true",
        help="(deprecated — delete-and-recreate is now the default behavior)",
    )

    args = parser.parse_args()

    (repo_url, source_branch, target_branch, project_id, mr_iid,
     gitlab_url, gitlab_token, project_name, mr_details) = resolve_args(args)

    shared = build_shared_dict(
        args, repo_url, source_branch, target_branch, project_id,
        mr_iid, gitlab_url, gitlab_token, project_name, mr_details,
    )

    # Describe-specific shared keys
    shared.update({
        "flow_type": "describe",
        "description_posted": False,
    })

    logger.info("=" * 60)
    logger.info(f"Describe Flow: {project_name}")
    logger.info(f"Branch: {source_branch} → {target_branch}")
    if mr_iid:
        logger.info(f"MR: {mr_iid}")
    logger.info("=" * 60)

    if args.local_dir:
        logger.info("Using local repository (skipping clone)")
        flow = create_local_describe_flow()
    else:
        logger.info("Using remote repository (will clone)")
        flow = create_describe_flow()

    try:
        flow.run(shared)

        logger.info("=" * 60)
        logger.info("Describe Flow Completed!")
        describe_data = shared.get("describe_data", {})
        if describe_data:
            logger.info(f"Title: {describe_data.get('title', 'N/A')}")
            logger.info(f"Type: {describe_data.get('pr_type', [])}")
            logger.info(f"Files described: {len(describe_data.get('walkthrough', []))}")
        if shared.get("description_posted"):
            logger.info("PR description posted to MR")
        logger.info("=" * 60)
    except Exception as e:
        logger.error(f"Describe flow failed: {e}")
        post_failure_notification(shared, e)
        raise


if __name__ == "__main__":
    main()
