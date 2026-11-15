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

"""Impact Flow - Post-merge analysis of code review comment adoption.

Analyzes a merged MR to determine which review comments (from /improve,
/review, and human reviewers) were applied, partially applied, or ignored.

Usage:
  python -m flows.code_reviews.impact.main --mr-link <MERGED_MR_URL>
  python -m flows.code_reviews.impact.main --repo <REPO_URL> --source-branch <BRANCH> --mr-iid <IID>
"""

from dotenv import load_dotenv
import argparse
import logging
import os

from utils.env import get_env_optional

from flows.code_reviews.common.cli import add_common_args, resolve_args, build_shared_dict, post_failure_notification
from flows.code_reviews.impact.flow import create_impact_flow, create_local_impact_flow
from utils.logger import get_logger

load_dotenv()

logger = get_logger("impact_main_logger")


def main():
    parser = argparse.ArgumentParser(
        description="Impact Flow — Post-merge code review impact analysis",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Examples:\n"
               "  python -m flows.code_reviews.impact.main --mr-link <MERGED_MR_URL>\n"
               "  python -m flows.code_reviews.impact.main --repo <REPO_URL> -s feature-branch --mr-iid 123",
    )

    add_common_args(parser)

    # Impact-specific options
    parser.add_argument(
        "-o", "--output", default="impact_analysis_output.yaml",
        help="Output YAML file name (default: impact_analysis_output.yaml)",
    )

    args = parser.parse_args()

    (repo_url, source_branch, target_branch, project_id, mr_iid,
     gitlab_url, gitlab_token, project_name, mr_details) = resolve_args(args)

    shared = build_shared_dict(
        args, repo_url, source_branch, target_branch, project_id,
        mr_iid, gitlab_url, gitlab_token, project_name, mr_details,
    )

    # Impact-specific shared keys
    shared.update({
        "flow_type": "impact",
        "output_file": args.output,
        "impact_results": [],
        "overall_adoption_rate": 0.0,
        "overall_summary": "",
        "impact_output_path": None,
        "impact_output_data": None,
        "impact_summary_posted": False,
        "impact_service_url": get_env_optional("IMPACT_SERVICE_URL"),
    })

    logger.info("=" * 60)
    logger.info(f"Impact Analysis Flow: {project_name}")
    logger.info(f"Branch: {source_branch} -> {target_branch}")
    if mr_iid:
        logger.info(f"MR: {mr_iid}")
    logger.info("=" * 60)

    if args.local_dir:
        logger.info("Using local repository (skipping clone)")
        flow = create_local_impact_flow()
    else:
        logger.info("Using remote repository (will clone)")
        flow = create_impact_flow()

    try:
        flow.run(shared)

        logger.info("=" * 60)
        logger.info("Impact Analysis Flow Completed!")
        impact_data = shared.get("impact_output_data", {})
        comments = impact_data.get("comments", []) if impact_data else []
        logger.info(f"Total comments analyzed: {len(comments)}")
        if shared.get("impact_output_path"):
            logger.info(f"Output: {shared['impact_output_path']}")
        if shared.get("impact_summary_posted"):
            logger.info("Impact data sent to service")
        logger.info("=" * 60)
    except Exception as e:
        logger.error(f"Impact analysis flow failed: {e}")
        post_failure_notification(shared, e)
        raise


if __name__ == "__main__":
    main()
