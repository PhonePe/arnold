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

import json

import gitlab
import pytest

from utils import scm


class FakeResponse:
    def __init__(self, status, body):
        self.status_code = status
        self._body = body
        self.content = b"" if body is None else json.dumps(body).encode()
        self.text = self.content.decode()

    def json(self):
        return self._body


class FakeSession:
    def __init__(self, routes):
        self.routes = routes
        self.headers = {}
        self.calls = []

    def request(self, method, url, timeout=None, allow_redirects=None, json=None):
        path = url.replace("https://api.github.com", "")
        self.calls.append((method, path, json))
        for (route_method, route_path), (status, body) in self.routes.items():
            if route_method == method and path.split("&page=")[0].split("?per_page")[0] == route_path:
                return FakeResponse(status, body)
        return FakeResponse(404, {"message": "not found"})


PULL = {"id": 555, "number": 7, "title": "Add cache", "body": None, "state": "open", "merged_at": None,
        "html_url": "https://github.com/acme/api/pull/7", "user": {"id": 1, "login": "alice"},
        "head": {"ref": "feature", "sha": "head"}, "base": {"ref": "main", "sha": "base"}}


def client(routes):
    session = FakeSession(routes)
    return scm.GitHubGitlab("tok", session=session), session


@pytest.fixture(autouse=True)
def github_env(monkeypatch):
    monkeypatch.delenv("GITHUB_WEB_URL", raising=False)
    monkeypatch.delenv("GITHUB_API_URL", raising=False)
    monkeypatch.delenv("SCM_GITHUB_TOKEN", raising=False)


def test_connect_selects_adapter_by_host():
    assert isinstance(scm.connect("https://github.com", private_token="t"), scm.GitHubGitlab)
    assert isinstance(scm.connect("https://gitlab.example.com", private_token="t"), gitlab.Gitlab)


def test_clone_url_uses_installation_token_for_github(monkeypatch):
    monkeypatch.setenv("SCM_GITHUB_TOKEN", "ghs_job")
    assert scm.authenticated_clone_url("https://github.com/acme/api.git", "pat") == \
        "https://x-access-token:ghs_job@github.com/acme/api.git"
    assert scm.authenticated_clone_url("https://gitlab.example.com/acme/api", "glpat") == \
        "https://oauth2:glpat@gitlab.example.com/acme/api"


def test_pull_request_maps_to_merge_request_shape():
    gl, _ = client({
        ("GET", "/repos/acme/api"): (200, {"id": 42, "full_name": "acme/api"}),
        ("GET", "/repos/acme/api/pulls/7"): (200, PULL),
        ("GET", "/repos/acme/api/compare/base...head"): (200, {"merge_base_commit": {"sha": "mb"}}),
        ("GET", "/repos/acme/api/pulls/7/files"): (200, [
            {"filename": "a.py", "status": "modified", "patch": "@@ -1 +1 @@\n-x\n+y"},
            {"filename": "b.py", "previous_filename": "old.py", "status": "renamed", "patch": ""}]),
    })
    project = gl.projects.get("acme/api")
    mr = project.mergerequests.get(7)

    assert project.id == 42 and project.http_url_to_repo == "https://github.com/acme/api.git"
    assert (mr.iid, mr.state, mr.source_branch, mr.sha, mr.description) == (7, "opened", "feature", "head", "")
    assert mr.author == {"id": 1, "username": "alice"}
    assert mr.diff_refs == {"base_sha": "mb", "start_sha": "mb", "head_sha": "head"}
    changes = mr.changes(access_raw_diffs=True)["changes"]
    assert changes[1]["old_path"] == "old.py" and changes[1]["renamed_file"]


def test_inline_discussion_posts_review_comment_and_returns_note_id():
    gl, session = client({
        ("GET", "/repositories/42"): (200, {"id": 42, "full_name": "acme/api"}),
        ("GET", "/repos/acme/api/pulls/7"): (200, PULL),
        ("POST", "/repos/acme/api/pulls/7/comments"): (201, {"id": 99, "path": "a.py", "line": 3, "side": "RIGHT",
                                                            "body": "x", "user": {"id": 1, "login": "bot"}}),
    })
    mr = gl.projects.get(42).mergerequests.get(7)

    discussion = mr.discussions.create({"body": "x", "position": {
        "head_sha": "head", "old_path": "a.py", "new_path": "a.py", "new_line": 3}})

    assert discussion.attributes["notes"][0]["id"] == 99
    assert session.calls[-1][2] == {"body": "x", "commit_id": "head", "path": "a.py", "line": 3, "side": "RIGHT"}


def test_rejected_inline_comment_raises_gitlab_create_error():
    gl, _ = client({
        ("GET", "/repos/acme/api"): (200, {"id": 42, "full_name": "acme/api"}),
        ("GET", "/repos/acme/api/pulls/7"): (200, PULL),
        ("POST", "/repos/acme/api/pulls/7/comments"): (422, {"message": "line must be part of the diff"}),
    })
    mr = gl.projects.get("acme/api").mergerequests.get(7)

    with pytest.raises(gitlab.exceptions.GitlabCreateError) as error:
        mr.discussions.create({"body": "x", "position": {"new_path": "a.py", "old_path": "a.py", "new_line": 3}})
    assert error.value.response_code == 422


def test_discussions_group_replies_and_include_conversation_comments():
    gl, _ = client({
        ("GET", "/repos/acme/api"): (200, {"id": 42, "full_name": "acme/api"}),
        ("GET", "/repos/acme/api/pulls/7"): (200, PULL),
        ("GET", "/repos/acme/api/pulls/7/comments"): (200, [
            {"id": 1, "path": "a.py", "line": 3, "side": "RIGHT", "body": "root", "user": {"login": "bot"}},
            {"id": 2, "in_reply_to_id": 1, "path": "a.py", "line": 3, "body": "reply", "user": {"login": "dev"}}]),
        ("GET", "/repos/acme/api/issues/7/comments"): (200, [{"id": 5, "body": "LGTM", "user": {"login": "dev"}}]),
    })
    discussions = gl.projects.get("acme/api").mergerequests.get(7).discussions.list(all=True)

    assert [d.id for d in discussions] == ["1", "issue:5"]
    notes = discussions[0].attributes["notes"]
    assert [n["body"] for n in notes] == ["root", "reply"]
    assert notes[0]["position"]["new_line"] == 3
    assert discussions[1].attributes["notes"][0]["position"] is None


def test_notes_support_ack_update_and_delete():
    gl, session = client({
        ("GET", "/repos/acme/api"): (200, {"id": 42, "full_name": "acme/api"}),
        ("GET", "/repos/acme/api/pulls/7"): (200, PULL),
        ("POST", "/repos/acme/api/issues/7/comments"): (201, {"id": 8, "body": "working"}),
        ("PATCH", "/repos/acme/api/issues/comments/8"): (200, {"id": 8, "body": "done"}),
        ("DELETE", "/repos/acme/api/issues/comments/8"): (204, None),
    })
    mr = gl.projects.get("acme/api").mergerequests.get(7)
    note = mr.notes.create({"body": "working"})
    note.body = "done"
    note.save()
    note.delete()

    assert [call[0] for call in session.calls[-3:]] == ["POST", "PATCH", "DELETE"]


def test_parse_mr_link_accepts_github_pull_requests(monkeypatch):
    from flows.code_reviews.common import cli
    gl, _ = client({
        ("GET", "/repos/acme/api"): (200, {"id": 42, "full_name": "acme/api"}),
        ("GET", "/repos/acme/api/pulls/7"): (200, PULL),
    })
    monkeypatch.setattr(cli.scm, "connect", lambda *args, **kwargs: gl)

    details = cli.parse_mr_link("https://github.com/acme/api/pull/7", "tok")

    assert (details["project_id"], details["mr_iid"], details["source_branch"]) == (42, 7, "feature")
    with pytest.raises(ValueError):
        cli.parse_mr_link("https://example.org/acme/api/pull/7", "tok")


def test_token_for_selects_the_forge_token(monkeypatch):
    monkeypatch.setenv("SCM_GITHUB_TOKEN", "ghs_job")
    monkeypatch.setenv("GITLAB_ACCESS_TOKEN", "glpat")
    assert scm.token_for("https://github.com/acme/api/pull/1") == "ghs_job"
    assert scm.token_for("https://gitlab.example.com/a/b/-/merge_requests/1") == "glpat"
    assert scm.token_for("https://gitlab.example.com/a/b/-/merge_requests/1", "explicit") == "explicit"
