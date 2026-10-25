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

from pocketflow import Flow
from flows.code_reviews.common.nodes import (
    PostAcknowledgment,
    CloneRepository,
    CheckoutBranch,
    FetchRFCContent,
    GetChangedFiles,
    GetImpactContext,
    FetchJiraTickets,
    FetchMRReviewStatus,
    Cleanup,
)
from flows.code_reviews.review.nodes import (
    ExecuteReviewSummary,
    AnalyzePreviousCommentStatus,
    FormatReviewMarkdown,
    PostReviewSummary,
)


def create_review_summary_flow():
    """Creates and returns the high-level review summary flow (remote repo)."""
    post_ack = PostAcknowledgment()
    clone_repo = CloneRepository()
    checkout_branch = CheckoutBranch()
    fetch_rfc_content = FetchRFCContent()
    get_changed_files = GetChangedFiles()
    get_impact = GetImpactContext()
    fetch_jira = FetchJiraTickets()
    fetch_mr_status = FetchMRReviewStatus()
    analyze_prev_comments = AnalyzePreviousCommentStatus(max_retries=2, wait=10)
    execute_review = ExecuteReviewSummary(max_retries=3, wait=10)
    format_markdown = FormatReviewMarkdown()
    post_summary = PostReviewSummary()
    cleanup = Cleanup()

    (post_ack >> clone_repo >> checkout_branch >> fetch_rfc_content
     >> get_changed_files >> get_impact >> fetch_jira
     >> fetch_mr_status >> analyze_prev_comments
     >> execute_review >> format_markdown >> post_summary >> cleanup)

    return Flow(start=post_ack)


def create_local_review_summary_flow():
    """Creates a review summary flow for a local repository (already cloned)."""
    post_ack = PostAcknowledgment()
    checkout_branch = CheckoutBranch()
    fetch_rfc_content = FetchRFCContent()
    get_changed_files = GetChangedFiles()
    get_impact = GetImpactContext()
    fetch_jira = FetchJiraTickets()
    fetch_mr_status = FetchMRReviewStatus()
    analyze_prev_comments = AnalyzePreviousCommentStatus(max_retries=2, wait=10)
    execute_review = ExecuteReviewSummary(max_retries=3, wait=10)
    format_markdown = FormatReviewMarkdown()
    post_summary = PostReviewSummary()
    cleanup = Cleanup()

    (post_ack >> checkout_branch >> fetch_rfc_content
     >> get_changed_files >> get_impact >> fetch_jira
     >> fetch_mr_status >> analyze_prev_comments
     >> execute_review >> format_markdown >> post_summary >> cleanup)

    return Flow(start=post_ack)
