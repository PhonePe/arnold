#!/usr/bin/env python3
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

"""Improve Flow - Automated inline code suggestions for GitLab merge requests.

Usage:
  python -m flows.code_reviews.improve.main --mr-link <MR_URL>
  python -m flows.code_reviews.improve.main --repo <REPO_URL> --source-branch <BRANCH>
"""

from dotenv import load_dotenv
import argparse
import logging
import os
import time

import requests

from flows.code_reviews.common.cli import add_common_args, resolve_args, build_shared_dict, post_failure_notification
from flows.code_reviews.improve.flow import create_code_review_flow, create_local_review_flow
from utils.env import get_env_optional
from utils.harness.common import usage_summary
from utils.logger import get_logger
from utils.arnold_auth import ArnoldAuth

load_dotenv()

logger = get_logger("improve_main_logger")


def report_analysis(shared, outcome, detail, findings=None):
    if not shared.get("arnold_run_id") or not shared.get("arnold_merge_request_id") or not shared.get("arnold_url"):
        return
    payload = {
        "mergeRequestId": shared["arnold_merge_request_id"],
        "runId": shared["arnold_run_id"],
        "personaId": "improve",
        "outcome": outcome,
        "detail": detail[:2000] if detail else None,
        "findings": findings or [],
        "usage": usage_summary(),
    }
    for attempt in range(3):
        try:
            response = requests.post(
                f"{shared['arnold_url'].rstrip('/')}/analysis-callbacks/v1/persona-completed",
                json={key: value for key, value in payload.items() if value is not None},
                headers={"Authorization": ArnoldAuth.get_auth_token()},
                timeout=30,
            )
            response.raise_for_status()
            return
        except Exception as error:
            if attempt == 2:
                logger.warning(f"Failed to report Improve status to Arnold: {error}")
            else:
                time.sleep(attempt + 1)


def main():
    parser = argparse.ArgumentParser(
        description="Improve Flow — Inline code suggestions for GitLab MRs",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Examples:\n"
               "  python -m flows.code_reviews.improve.main --mr-link <MR_URL>\n"
               "  python -m flows.code_reviews.improve.main --repo <REPO_URL> -s feature-branch",
    )

    add_common_args(parser)

    # Improve-specific options
    parser.add_argument(
        "-o", "--output", default="code_review_output.yaml",
        help="Output YAML file name (default: code_review_output.yaml)",
    )
    parser.add_argument(
        "--min-confidence", type=float, default=0.6,
        help="Minimum confidence score (0.0-1.0) for review comments (default: 0.6)",
    )
    parser.add_argument(
        "--no-cache", action="store_true",
        help="Disable LLM response caching",
    )

    args = parser.parse_args()
    callback = {
        "arnold_url": get_env_optional("ARNOLD_URL"),
        "arnold_run_id": os.environ.get("ARNOLD_RUN_ID"),
        "arnold_merge_request_id": os.environ.get("ARNOLD_MERGE_REQUEST_ID"),
    }
    try:
        (repo_url, source_branch, target_branch, project_id, mr_iid,
         gitlab_url, gitlab_token, project_name, mr_details) = resolve_args(args)
        shared = build_shared_dict(
            args, repo_url, source_branch, target_branch, project_id,
            mr_iid, gitlab_url, gitlab_token, project_name, mr_details,
        )
    except Exception as error:
        report_analysis(callback, "FAILED", str(error))
        raise

    # Improve-specific shared keys
    shared.update({
        "flow_type": "improve",
        "min_confidence": args.min_confidence,
        "output_file": args.output,
        "review_comments": [],
        "review_output_path": None,
        "review_output_data": None,
        "posted_comments": [],
        **callback,
    })

    logger.info("=" * 60)
    logger.info(f"Improve Flow: {project_name}")
    logger.info(f"Branch: {source_branch} → {target_branch}")
    if mr_iid:
        logger.info(f"MR: {mr_iid}")
    logger.info("=" * 60)

    try:
        if args.local_dir:
            logger.info("Using local repository (skipping clone)")
            flow = create_local_review_flow()
        else:
            logger.info("Using remote repository (will clone)")
            flow = create_code_review_flow()
        flow.run(shared)

        logger.info("=" * 60)
        logger.info("Improve Flow Completed!")
        review_data = shared.get("review_output_data", {})
        metadata = review_data.get("metadata", {})
        total = metadata.get("total_comments", 0)
        logger.info(f"Total Issues: {total}")
        if shared.get("review_output_path"):
            logger.info(f"Output: {shared['review_output_path']}")
        posted = shared.get("posted_comments", [])
        if posted:
            logger.info(f"Posted {len(posted)} comments")
        report_analysis(
            shared,
            "SUCCEEDED",
            f"{len(posted)} inline suggestions posted",
            shared.get("analysis_findings", []),
        )
        logger.info("=" * 60)
    except Exception as e:
        logger.error(f"Improve flow failed: {e}")
        post_failure_notification(shared, e)
        report_analysis(shared, "FAILED", str(e), shared.get("analysis_findings", []))
        raise


if __name__ == "__main__":
    main()
