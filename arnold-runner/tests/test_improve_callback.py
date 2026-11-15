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

from unittest.mock import Mock, patch

from flows.code_reviews.improve.main import report_analysis
from flows.code_reviews.improve.nodes import PostMergeRequestComments


def test_report_analysis_posts_terminal_status_with_findings():
    response = Mock()
    shared = {
        "arnold_url": "https://arnold.example",
        "arnold_run_id": "run-1",
        "arnold_merge_request_id": "gitlab:42:12",
    }

    with patch("flows.code_reviews.improve.main.ArnoldAuth.get_auth_token", return_value="Bearer token"), \
            patch("flows.code_reviews.improve.main.requests.post", return_value=response) as post:
        report_analysis(shared, "SUCCEEDED", "1 inline suggestion posted", [{
            "id": "101",
            "headSha": "abc123",
            "filePath": "src/main.py",
            "oldPath": "src/main.py",
            "newLine": 12,
            "body": "Fix this",
            "publicationStatus": "posted",
            "commentUrl": "https://gitlab.example/project/-/merge_requests/12#note_101",
        }])

    post.assert_called_once_with(
        "https://arnold.example/analysis-callbacks/v1/persona-completed",
        json={
            "mergeRequestId": "gitlab:42:12",
            "runId": "run-1",
            "personaId": "improve",
            "outcome": "SUCCEEDED",
            "detail": "1 inline suggestion posted",
            "findings": [{
                "id": "101",
                "headSha": "abc123",
                "filePath": "src/main.py",
                "oldPath": "src/main.py",
                "newLine": 12,
                "body": "Fix this",
                "publicationStatus": "posted",
                "commentUrl": "https://gitlab.example/project/-/merge_requests/12#note_101",
            }],
        },
        headers={"Authorization": "Bearer token"},
        timeout=30,
    )
    response.raise_for_status.assert_called_once_with()


def test_report_analysis_skips_non_arnold_runs():
    with patch("flows.code_reviews.improve.main.requests.post") as post:
        report_analysis({}, "FAILED", "failure")

    post.assert_not_called()


def test_post_inline_discussion_returns_run_scoped_publication_metadata():
    merge_request = Mock()
    merge_request.web_url = "https://gitlab.example/project/-/merge_requests/12"
    discussion = Mock()
    discussion.id = "discussion-1"
    discussion.attributes = {"notes": [{"id": 101}]}
    merge_request.discussions.create.return_value = discussion

    publication = PostMergeRequestComments()._post_inline_discussion_via_gitlab_lib(
        mr=merge_request,
        base_sha="base",
        start_sha="start",
        head_sha="head",
        file_path="src/main.py",
        line_number=12,
        line_info={"line_code": "12_0_0", "old_line": None},
        comment_body="Fix this",
    )

    assert publication == {
        "id": "101",
        "commentUrl": "https://gitlab.example/project/-/merge_requests/12#note_101",
    }
