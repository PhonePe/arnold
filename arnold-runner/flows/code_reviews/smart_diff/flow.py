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

"""PocketFlow graph for Arnold Smart Diff generation."""

from pocketflow import Flow

from flows.code_reviews.common.nodes import (
    CheckoutBranch,
    Cleanup,
    CloneRepository,
    FetchJiraTickets,
    FetchRFCContent,
    GetImpactContext,
)
from flows.code_reviews.smart_diff.nodes import (
    CompleteSmartDiff,
    GenerateSmartDiffArtifacts,
    GetSmartDiffChanges,
    StartSmartDiff,
)


def create_smart_diff_flow():
    start = StartSmartDiff()
    clone = CloneRepository()
    checkout = CheckoutBranch()
    get_changes = GetSmartDiffChanges()
    get_impact = GetImpactContext()
    fetch_jira = FetchJiraTickets()
    fetch_rfc = FetchRFCContent()
    generate = GenerateSmartDiffArtifacts()
    complete = CompleteSmartDiff()
    cleanup = Cleanup()

    (start >> clone >> checkout >> get_changes >> get_impact >> fetch_jira >> fetch_rfc
     >> generate >> complete >> cleanup)

    return Flow(start=start)
