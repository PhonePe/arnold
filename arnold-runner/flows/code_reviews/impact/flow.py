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
    Cleanup,
)
from flows.code_reviews.impact.nodes import (
    FetchMergeRequestDetails,
    CloneAndCheckoutCode,
    CheckoutCodeLocal,
    FetchAllReviewComments,
    AnalyzeCommentImpact,
    WriteImpactOutput,
    SendImpactToService,
)


def create_impact_flow():
    """Creates and returns the impact analysis flow (remote repo).

    Supports both merged and open MRs (auto-detected):
    - Merged MR: checks out merge commit for post-merge analysis
    - Open MR: checks out source branch for pre-merge analysis

    Pipeline order:
      1. FetchMergeRequestDetails — fetch MR state, branches, merge commit
      2. FetchAllReviewComments — collect root note from each discussion
         → (no_comments) skip clone/analysis, go directly to Cleanup
         → (has_comments) proceed with clone and analysis
      3. CloneAndCheckoutCode — clone repo and checkout appropriate ref
      4. AnalyzeCommentImpact — LLM scores each comment against code
      5. WriteImpactOutput — write results to local YAML file
      6. SendImpactToService — send impact data to configured external service
      7. Cleanup — remove temp files
    """
    fetch_mr_details = FetchMergeRequestDetails()
    fetch_comments = FetchAllReviewComments()
    clone_and_checkout = CloneAndCheckoutCode()
    analyze_impact = AnalyzeCommentImpact(max_retries=3, wait=10)
    write_output = WriteImpactOutput()
    send_to_service = SendImpactToService()
    cleanup = Cleanup()

    fetch_mr_details >> fetch_comments
    fetch_comments - "has_comments" >> clone_and_checkout >> analyze_impact >> write_output >> send_to_service >> cleanup
    fetch_comments - "no_comments" >> cleanup

    return Flow(start=fetch_mr_details)


def create_local_impact_flow():
    """Creates an impact analysis flow for a local repository (already cloned).

    Same as the remote flow but skips cloning — uses CheckoutCodeLocal
    to checkout the appropriate ref in the existing repo.
    """
    fetch_mr_details = FetchMergeRequestDetails()
    fetch_comments = FetchAllReviewComments()
    checkout_code = CheckoutCodeLocal()
    analyze_impact = AnalyzeCommentImpact(max_retries=3, wait=10)
    write_output = WriteImpactOutput()
    send_to_service = SendImpactToService()
    cleanup = Cleanup()

    fetch_mr_details >> fetch_comments
    fetch_comments - "has_comments" >> checkout_code >> analyze_impact >> write_output >> send_to_service >> cleanup
    fetch_comments - "no_comments" >> cleanup

    return Flow(start=fetch_mr_details)
