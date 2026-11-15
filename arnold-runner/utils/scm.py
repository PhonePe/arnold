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

"""SCM connection factory.

Workflows were written against python-gitlab. :func:`connect` returns a python-gitlab client for GitLab hosts and a
small GitHub adapter exposing the same subset of the python-gitlab object model for GitHub hosts
(``projects.get().mergerequests.get()`` with ``notes``, ``discussions``, ``changes()``, ``diffs`` and ``diff_refs``),
so the same workflow code posts review comments to either forge.
"""
from __future__ import annotations

import os
from types import SimpleNamespace
from urllib.parse import urlparse

import gitlab
import requests

GITHUB_API_VERSION = "2022-11-28"


def github_web_url() -> str:
    return os.environ.get("GITHUB_WEB_URL", "https://github.com").rstrip("/")


def github_api_url() -> str:
    explicit = os.environ.get("GITHUB_API_URL")
    if explicit:
        return explicit.rstrip("/")
    web = github_web_url()
    return "https://api.github.com" if web == "https://github.com" else f"{web}/api/v3"


def is_github(url: str | None) -> bool:
    if not url:
        return False
    return (urlparse(url).hostname or "").lower() == (urlparse(github_web_url()).hostname or "").lower()


def github_token(fallback: str | None = None) -> str | None:
    """Token for GitHub API and git access; Arnold may inject a short-lived installation token per job."""
    return os.environ.get("SCM_GITHUB_TOKEN") or fallback


def token_for(url: str | None, explicit: str | None = None) -> str | None:
    """Token for the forge hosting ``url``: the job's GitHub installation token, or the GitLab access token."""
    if is_github(url):
        return os.environ.get("SCM_GITHUB_TOKEN") or explicit
    return explicit or os.environ.get("GITLAB_ACCESS_TOKEN")


def connect(url: str, private_token: str | None = None, timeout: int | None = None, **kwargs):
    """Return a python-gitlab compatible client for the forge hosting ``url``."""
    if is_github(url):
        return GitHubGitlab(github_token(private_token), timeout=timeout or 60)
    return gitlab.Gitlab(url, private_token=private_token, timeout=timeout, **kwargs)


def authenticated_clone_url(repo_url: str, token: str | None) -> str:
    if not token:
        return repo_url
    parsed = urlparse(repo_url)
    user = "x-access-token" if is_github(repo_url) else "oauth2"
    secret = github_token(token) if is_github(repo_url) else token
    return parsed._replace(netloc=f"{user}:{secret}@{parsed.netloc}", params="", query="", fragment="").geturl()


class _GitHubApi:
    def __init__(self, token: str | None, timeout: int, session: requests.Session | None = None):
        self.base = github_api_url()
        self.timeout = timeout
        self.session = session or requests.Session()
        self.session.headers.update({"Accept": "application/vnd.github+json",
                                     "X-GitHub-Api-Version": GITHUB_API_VERSION})
        if token:
            self.session.headers["Authorization"] = f"Bearer {token}"

    def request(self, method: str, path: str, *, create: bool = False, **kwargs):
        response = self.session.request(method, self.base + path, timeout=self.timeout, allow_redirects=False,
                                        **kwargs)
        if response.status_code >= 400:
            error_type = gitlab.exceptions.GitlabCreateError if create else gitlab.exceptions.GitlabHttpError
            raise error_type(response.text[:500], response.status_code)
        return response.json() if response.content else None

    def paged(self, path: str) -> list:
        items, page = [], 1
        while True:
            separator = "&" if "?" in path else "?"
            batch = self.request("GET", f"{path}{separator}per_page=100&page={page}") or []
            items.extend(batch)
            if len(batch) < 100:
                return items
            page += 1


class GitHubGitlab:
    """Minimal python-gitlab look-alike backed by the GitHub REST API."""

    def __init__(self, token: str | None, timeout: int = 60, session: requests.Session | None = None):
        self.api = _GitHubApi(token, timeout, session)
        self.projects = SimpleNamespace(get=self._project)

    def auth(self):
        return None

    def _project(self, id_or_path):
        path = f"/repositories/{id_or_path}" if str(id_or_path).isdigit() else f"/repos/{id_or_path}"
        return _Repository(self.api, self.api.request("GET", path))


class _Repository:
    def __init__(self, api: _GitHubApi, data: dict):
        self.api = api
        self.attributes = data
        self.id = data["id"]
        self.path_with_namespace = data["full_name"]
        self.http_url_to_repo = data.get("clone_url") or f"{github_web_url()}/{data['full_name']}.git"
        self.web_url = data.get("html_url")
        self.mergerequests = SimpleNamespace(get=self._pull)
        self.wikis = SimpleNamespace(get=self._no_wiki)

    def _pull(self, number):
        return _PullRequest(self.api, self, self.api.request("GET", f"/repos/{self.path_with_namespace}/pulls/{number}"))

    @staticmethod
    def _no_wiki(_slug):
        raise gitlab.exceptions.GitlabGetError("GitHub wikis are not available through the API", 404)


class _PullRequest:
    def __init__(self, api: _GitHubApi, repository: _Repository, data: dict):
        self.api = api
        self.repository = repository
        self.attributes = data
        self.id = data["id"]
        self.iid = data["number"]
        self.title = data.get("title")
        self.description = data.get("body") or ""
        self.web_url = data.get("html_url")
        self.source_branch = data["head"]["ref"]
        self.target_branch = data["base"]["ref"]
        self.sha = data["head"]["sha"]
        self.state = "merged" if data.get("merged_at") else ("opened" if data.get("state") == "open" else "closed")
        self.merged_at = data.get("merged_at")
        user = data.get("user") or {}
        self.author = {"id": user.get("id"), "username": user.get("login")}
        merged_by = data.get("merged_by") or None
        self.merged_by = {"id": merged_by.get("id"), "username": merged_by.get("login")} if merged_by else None
        self._path = f"/repos/{repository.path_with_namespace}"
        self._diff_refs = None
        self.notes = SimpleNamespace(create=self._create_note, list=self._list_notes, get=self._get_note)
        self.discussions = SimpleNamespace(create=self._create_discussion, list=self._list_discussions)
        self.diffs = SimpleNamespace(list=lambda **_: [SimpleNamespace(id=1)],
                                     get=lambda _id: SimpleNamespace(diffs=self._changes()))

    @property
    def diff_refs(self) -> dict:
        if self._diff_refs is None:
            base = self.attributes["base"]["sha"]
            compare = self.api.request("GET", f"{self._path}/compare/{base}...{self.sha}")
            merge_base = (compare.get("merge_base_commit") or {}).get("sha", base)
            self._diff_refs = {"base_sha": merge_base, "start_sha": merge_base, "head_sha": self.sha}
        return self._diff_refs

    def changes(self, **_):
        return {"changes": self._changes(), "diff_refs": self.diff_refs}

    def _changes(self) -> list[dict]:
        changes = []
        for item in self.api.paged(f"{self._path}/pulls/{self.iid}/files"):
            status = item.get("status")
            changes.append({
                "old_path": item.get("previous_filename") or item["filename"],
                "new_path": item["filename"],
                "new_file": status == "added",
                "deleted_file": status == "removed",
                "renamed_file": status == "renamed",
                "diff": item.get("patch", ""),
            })
        return changes

    # Notes map to pull request conversation (issue) comments.
    def _create_note(self, data: dict):
        created = self.api.request("POST", f"{self._path}/issues/{self.iid}/comments", create=True,
                                   json={"body": data["body"]})
        return _Note(self.api, self._path, created)

    def _list_notes(self, **_):
        return [_Note(self.api, self._path, item) for item in self.api.paged(f"{self._path}/issues/{self.iid}/comments")]

    def _get_note(self, note_id):
        return _Note(self.api, self._path, self.api.request("GET", f"{self._path}/issues/comments/{note_id}"))

    # Discussions map to review comment threads plus conversation comments.
    def _create_discussion(self, data: dict):
        position = data.get("position")
        if not position:
            note = self._create_note(data)
            return SimpleNamespace(id=f"issue:{note.id}", attributes={"notes": [note.attributes_as_note()]})
        right = position.get("new_line") is not None
        payload = {
            "body": data["body"],
            "commit_id": position.get("head_sha") or self.sha,
            "path": position["new_path"] if right else position.get("old_path") or position["new_path"],
            "line": position["new_line"] if right else position["old_line"],
            "side": "RIGHT" if right else "LEFT",
        }
        created = self.api.request("POST", f"{self._path}/pulls/{self.iid}/comments", create=True, json=payload)
        note = _review_note(created)
        return SimpleNamespace(id=str(created["id"]), attributes={"notes": [note]})

    def _list_discussions(self, **_):
        threads: dict[int, list] = {}
        order: list[int] = []
        for comment in self.api.paged(f"{self._path}/pulls/{self.iid}/comments"):
            root = comment.get("in_reply_to_id") or comment["id"]
            if root not in threads:
                threads[root] = []
                order.append(root)
            threads[root].append(_review_note(comment))
        discussions = [SimpleNamespace(id=str(root), attributes={"notes": threads[root]}) for root in order]
        for note in self._list_notes():
            discussions.append(SimpleNamespace(id=f"issue:{note.id}", attributes={"notes": [note.attributes_as_note()]}))
        return discussions


def _review_note(comment: dict) -> dict:
    line = comment.get("line") or comment.get("original_line")
    left = comment.get("side") == "LEFT"
    user = comment.get("user") or {}
    return {
        "id": comment["id"],
        "body": comment.get("body", ""),
        "system": False,
        "author": {"id": user.get("id"), "username": user.get("login")},
        "created_at": comment.get("created_at"),
        "position": {
            "new_path": comment.get("path"),
            "old_path": comment.get("path"),
            "new_line": None if left else line,
            "old_line": line if left else None,
        },
    }


class _Note:
    def __init__(self, api: _GitHubApi, path: str, data: dict):
        self.api = api
        self._path = path
        self.attributes = data
        self.id = data["id"]
        self.body = data.get("body", "")
        user = data.get("user") or {}
        self.author = {"id": user.get("id"), "username": user.get("login")}

    def attributes_as_note(self) -> dict:
        return {"id": self.id, "body": self.body, "system": False, "author": self.author,
                "created_at": self.attributes.get("created_at"), "position": None}

    def get(self, key, default=None):
        return self.attributes_as_note().get(key, default)

    def save(self):
        self.attributes = self.api.request("PATCH", f"{self._path}/issues/comments/{self.id}", json={"body": self.body})

    def delete(self):
        self.api.request("DELETE", f"{self._path}/issues/comments/{self.id}")
