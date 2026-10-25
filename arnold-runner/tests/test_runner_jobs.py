import subprocess

import pytest

from runner import jobs, worker


def test_command_for_maps_every_arnold_workflow():
    assert jobs.command_for({"WORKFLOW": "improve", "MR_LINK": "https://scm/a/-/merge_requests/1",
                             "MIN_CONFIDENCE": "0.7"}) == [
        "improve", "--mr-link", "https://scm/a/-/merge_requests/1", "--min-confidence", "0.7"]
    assert jobs.command_for({"WORKFLOW": "review_analysis", "REPO_URL": "https://scm/a", "SOURCE_BRANCH": "f",
                             "DRY_RUN": "true"}) == [
        "impact", "--repo", "https://scm/a", "--source-branch", "f", "--target-branch", "main", "--dry-run"]
    assert jobs.command_for({"WORKFLOW": "smart_diff", "MR_URL": "u", "PROJECT_ID": "1", "MR_IID": "2",
                             "EXPECTED_HEAD_SHA": "abc", "EXECUTION_ID": "e"})[0] == "smart_diff"
    assert jobs.command_for({"WORKFLOW": "group_review", "ARNOLD_RUN_ID": "r", "EXECUTION_ID": "e"}) == [
        "group_review", "--run-id", "r", "--execution-id", "e"]


@pytest.mark.parametrize("variables", [
    {"WORKFLOW": "tutorial"},
    {"WORKFLOW": "improve"},
    {"WORKFLOW": "smart_diff", "MR_URL": "u"},
    {},
])
def test_command_for_rejects_unknown_or_incomplete_jobs(variables):
    with pytest.raises(jobs.JobError):
        jobs.command_for(variables)


@pytest.mark.parametrize("name", ["ARNOLD_RUNNER_API_KEY", "GITLAB_ACCESS_TOKEN", "PATH", "PYTHONPATH",
                                  "LD_PRELOAD", "GIT_SSH_COMMAND", "HTTPS_PROXY", "CLIO_LLM_BASE_URL", "lower",
                                  "A-B", "1ABC"])
def test_sanitize_rejects_protected_and_invalid_names(name):
    with pytest.raises(jobs.JobError):
        jobs.sanitize({name: "x"})


def test_run_passes_job_as_argv_and_environment_without_a_shell(monkeypatch, tmp_path):
    captured = {}

    def fake_run(argv, env, cwd, timeout, check):
        captured.update(argv=argv, env=env)
        return subprocess.CompletedProcess(argv, 0)

    monkeypatch.setattr(jobs.subprocess, "run", fake_run)
    link = "https://scm/a/-/merge_requests/1; rm -rf /"
    assert jobs.run({"WORKFLOW": "improve", "MR_LINK": link, "ARNOLD_RUN_ID": "r1"}, workdir=str(tmp_path)) == 0
    assert captured["argv"][-2:] == ["--mr-link", link]
    assert captured["env"]["ARNOLD_RUN_ID"] == "r1"
    assert captured["env"]["TMPDIR"] == str(tmp_path)


class FakeClient:
    def __init__(self, job=None):
        self.job = job
        self.completed = []
        self.claims = []

    def claim(self, worker_id, job=None, wait_seconds=20):
        self.claims.append((worker_id, job))
        return self.job

    def heartbeat(self, number, worker_id):
        return None

    def complete(self, number, worker_id, succeeded, detail=None):
        self.completed.append((number, succeeded, detail))


def test_execute_leased_reports_success_and_failure(monkeypatch):
    client = FakeClient()
    job = {"number": 7, "workflow": "improve", "variables": {"WORKFLOW": "improve"}, "leaseExpiresAt": 0}
    monkeypatch.setattr(jobs, "run", lambda variables, workdir=None: 0)
    assert worker.execute_leased(client, job, "w")
    monkeypatch.setattr(jobs, "run", lambda variables, workdir=None: 5)
    assert not worker.execute_leased(client, job, "w")

    def invalid(variables, workdir=None):
        raise jobs.JobError("Unsupported workflow")

    monkeypatch.setattr(jobs, "run", invalid)
    assert not worker.execute_leased(client, job, "w")
    assert client.completed == [(7, True, None), (7, False, "Workflow exited with status 5"),
                                (7, False, "Unsupported workflow")]


def test_single_job_claims_specific_job(monkeypatch):
    client = FakeClient({"number": 9, "variables": {"WORKFLOW": "improve"}, "leaseExpiresAt": 0})
    monkeypatch.setattr(worker, "ArnoldRunnerClient", lambda: client)
    monkeypatch.setattr(jobs, "run", lambda variables, workdir=None: 0)
    assert worker.single_job(9) == 0
    assert client.claims[0][1] == 9
    assert client.completed == [(9, True, None)]


def test_single_job_from_environment_ignores_runner_secrets(monkeypatch):
    seen = {}
    monkeypatch.setenv("WORKFLOW", "improve")
    monkeypatch.setenv("MR_LINK", "https://scm/a/-/merge_requests/1")
    monkeypatch.setenv("ARNOLD_RUNNER_API_KEY", "secret")
    monkeypatch.setenv("CLIO_LLM_API_KEY", "secret")
    monkeypatch.delenv("ARNOLD_JOB_VARIABLES", raising=False)
    monkeypatch.setattr(jobs, "run", lambda variables, workdir=None: seen.update(variables) or 0)
    assert worker.single_job(None) == 0
    assert seen["MR_LINK"].endswith("/1")
    assert "ARNOLD_RUNNER_API_KEY" not in seen and "CLIO_LLM_API_KEY" not in seen
