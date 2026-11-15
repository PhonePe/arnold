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

"""PocketFlow graph for grouped merge-request analysis."""

from pocketflow import Flow

from flows.code_reviews.group_review.nodes import (
    CleanupGroupWorkspace,
    CompleteGroupAnalysis,
    ExecuteCombinedReview,
    FilterGroupFindings,
    LoadGroupManifest,
    PrepareGroupMembers,
    PublishGroupFindings,
    ReviewGroupMembers,
    ValidateGroupSnapshot,
)


def create_group_review_flow():
    """Create the grouped merge-request review and publication flow."""
    load_manifest = LoadGroupManifest()
    validate_snapshot = ValidateGroupSnapshot()
    prepare_members = PrepareGroupMembers()
    review_members = ReviewGroupMembers()
    combined_review = ExecuteCombinedReview()
    filter_findings = FilterGroupFindings()
    cleanup = CleanupGroupWorkspace()
    publish_findings = PublishGroupFindings()
    complete = CompleteGroupAnalysis()

    (load_manifest >> validate_snapshot >> prepare_members >> review_members
     >> combined_review >> filter_findings >> cleanup >> publish_findings >> complete)

    return Flow(start=load_manifest)

