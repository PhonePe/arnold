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

from unittest.mock import MagicMock

from flows.code_reviews.common.nodes import Cleanup


def test_cleanup_keeps_completed_ack_when_improve_has_no_suggestions(monkeypatch):
    note = MagicMock()
    mr = MagicMock()
    mr.notes.get.return_value = note
    project = MagicMock()
    project.mergerequests.get.return_value = mr
    gitlab = MagicMock()
    gitlab.projects.get.return_value = project
    monkeypatch.setattr(
        "flows.code_reviews.common.nodes.gitlab.Gitlab", MagicMock(return_value=gitlab)
    )

    cleanup = Cleanup()
    prep_res = cleanup.prep({
        "flow_type": "improve",
        "review_output_data": {"comments": []},
        "ack_note_id": 42,
        "gitlab_token": "token",
        "project_id": 1,
        "merge_request_iid": 2,
    })
    result = cleanup.exec(prep_res)

    assert result["ack_deleted"] is False
    assert "No actionable suggestions were found" in note.body
    note.save.assert_called_once_with()
    note.delete.assert_not_called()
