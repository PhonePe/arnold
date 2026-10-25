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
    GetChangedFiles,
    GetImpactContext,
    Cleanup,
)
from flows.code_reviews.describe.nodes import (
    ExecuteDescribe,
    FormatDescribeMarkdown,
    PostDescription,
)


def create_describe_flow():
    """Creates and returns the PR description flow (remote repo)."""
    post_ack = PostAcknowledgment()
    clone_repo = CloneRepository()
    checkout_branch = CheckoutBranch()
    get_changed_files = GetChangedFiles()
    get_impact = GetImpactContext()
    execute_describe = ExecuteDescribe(max_retries=3, wait=10)
    format_markdown = FormatDescribeMarkdown()
    post_description = PostDescription()
    cleanup = Cleanup()

    post_ack >> clone_repo >> checkout_branch >> get_changed_files >> get_impact >> execute_describe >> format_markdown >> post_description >> cleanup

    return Flow(start=post_ack)


def create_local_describe_flow():
    """Creates a describe flow for a local repository (already cloned)."""
    post_ack = PostAcknowledgment()
    checkout_branch = CheckoutBranch()
    get_changed_files = GetChangedFiles()
    get_impact = GetImpactContext()
    execute_describe = ExecuteDescribe(max_retries=3, wait=10)
    format_markdown = FormatDescribeMarkdown()
    post_description = PostDescription()
    cleanup = Cleanup()

    post_ack >> checkout_branch >> get_changed_files >> get_impact >> execute_describe >> format_markdown >> post_description >> cleanup

    return Flow(start=post_ack)
