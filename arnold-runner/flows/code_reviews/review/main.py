#!/usr/bin/env python3
"""Review Flow - Automated high-level review summary for GitLab merge requests.

Usage:
  python -m flows.code_reviews.review.main --mr-link <MR_URL>
  python -m flows.code_reviews.review.main --repo <REPO_URL> --source-branch <BRANCH>
"""

from dotenv import load_dotenv
import argparse
import logging

from flows.code_reviews.common.cli import add_common_args, resolve_args, build_shared_dict, post_failure_notification
from flows.code_reviews.review.flow import create_review_summary_flow, create_local_review_summary_flow
from utils.logger import get_logger

load_dotenv()

logger = get_logger("review_main_logger")


def main():
    parser = argparse.ArgumentParser(
        description="Review Flow — High-level review summary for GitLab MRs",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Examples:\n"
               "  python -m flows.code_reviews.review.main --mr-link <MR_URL>\n"
               "  python -m flows.code_reviews.review.main --repo <REPO_URL> -s feature-branch",
    )

    add_common_args(parser)

    # --persistent-comment is kept for backward CLI compatibility but is now
    # a no-op: the review summary always updates the existing note in-place.
    parser.add_argument(
        "--persistent-comment", action="store_true",
        help="(deprecated — update-or-create is now the default behavior)",
    )

    args = parser.parse_args()

    (repo_url, source_branch, target_branch, project_id, mr_iid,
     gitlab_url, gitlab_token, project_name, mr_details) = resolve_args(args)

    shared = build_shared_dict(
        args, repo_url, source_branch, target_branch, project_id,
        mr_iid, gitlab_url, gitlab_token, project_name, mr_details,
    )

    # Review-specific shared keys
    shared.update({
        "flow_type": "review",
        "summary_posted": False,
    })

    logger.info("=" * 60)
    logger.info(f"Review Flow: {project_name}")
    logger.info(f"Branch: {source_branch} → {target_branch}")
    if mr_iid:
        logger.info(f"MR: {mr_iid}")
    logger.info("=" * 60)

    if args.local_dir:
        logger.info("Using local repository (skipping clone)")
        flow = create_local_review_summary_flow()
    else:
        logger.info("Using remote repository (will clone)")
        flow = create_review_summary_flow()

    try:
        flow.run(shared)

        logger.info("=" * 60)
        logger.info("Review Flow Completed!")
        review_data = shared.get("review_data", {})
        if review_data:
            logger.info(f"Score: {review_data.get('score', 'N/A')}/100")
            logger.info(f"Effort: {review_data.get('estimated_effort', 'N/A')}/5")
            logger.info(f"Key Issues: {len(review_data.get('key_issues', []))}")
        if shared.get("summary_posted"):
            logger.info("Review summary posted to MR")
        logger.info("=" * 60)
    except Exception as e:
        logger.error(f"Review flow failed: {e}")
        post_failure_notification(shared, e)
        raise


if __name__ == "__main__":
    main()
