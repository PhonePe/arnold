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

from pocketflow import Flow
from flows.code_reviews.common.nodes import (
    PostAcknowledgment,
    CloneRepository,
    CheckoutBranch,
    FetchRFCContent,
    FetchJiraTickets,
    GetChangedFiles,
    GetImpactContext,
    Cleanup,
)
from flows.code_reviews.improve.nodes import (
    FetchSkills,
    ExecuteCodeReview,
    FetchExistingComments,
    FilterDuplicateComments,
    WriteReviewOutput,
    PostMergeRequestComments,
)


def create_code_review_flow():
    """Creates and returns the GitLab code review flow (inline suggestions)."""
    post_ack = PostAcknowledgment()
    clone_repo = CloneRepository()
    checkout_branch = CheckoutBranch()
    fetch_rfc_content = FetchRFCContent()
    get_changed_files = GetChangedFiles()
    get_impact = GetImpactContext()
    fetch_jira = FetchJiraTickets()
    fetch_skills = FetchSkills()
    fetch_existing_comments = FetchExistingComments()
    execute_review = ExecuteCodeReview(max_retries=3, wait=10)
    filter_duplicates = FilterDuplicateComments()
    write_output = WriteReviewOutput()
    post_comments = PostMergeRequestComments()
    cleanup = Cleanup()

    post_ack >> clone_repo >> checkout_branch >> fetch_rfc_content >> get_changed_files >> fetch_jira >> get_impact >> fetch_skills >> fetch_existing_comments >> execute_review >> filter_duplicates >> write_output >> post_comments >> cleanup

    return Flow(start=post_ack)


def create_local_review_flow():
    """Creates a flow for reviewing a local repository (already cloned)."""
    post_ack = PostAcknowledgment()
    checkout_branch = CheckoutBranch()
    fetch_rfc_content = FetchRFCContent()
    get_changed_files = GetChangedFiles()
    get_impact = GetImpactContext()
    fetch_jira = FetchJiraTickets()
    fetch_skills = FetchSkills()
    fetch_existing_comments = FetchExistingComments()
    execute_review = ExecuteCodeReview(max_retries=3, wait=10)
    filter_duplicates = FilterDuplicateComments()
    write_output = WriteReviewOutput()
    post_comments = PostMergeRequestComments()
    cleanup = Cleanup()

    post_ack >> checkout_branch >> fetch_rfc_content >> get_changed_files >> fetch_jira >> get_impact >> fetch_skills >> fetch_existing_comments >> execute_review >> filter_duplicates >> write_output >> post_comments >> cleanup

    return Flow(start=post_ack)
