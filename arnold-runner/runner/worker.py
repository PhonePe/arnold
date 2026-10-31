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

"""Run Arnold jobs claimed from the job queue.

* ``worker``: long-running process that claims and runs jobs (``runner.mode: LOCAL``).
* ``job``: runs a single job, either from environment variables (GitLab CI) or by claiming a specific job number
  (GitHub Actions).
"""
from __future__ import annotations

import argparse
import logging
import os
import shutil
import signal
import socket
import sys
import tempfile
import threading
import time
import uuid

from runner import jobs
from runner.arnold_client import ArnoldRunnerClient

logger = logging.getLogger("arnold_runner")


def _worker_id(prefix: str | None = None) -> str:
    base = prefix or socket.gethostname()
    clean = "".join(ch if ch.isalnum() or ch in "._-" else "-" for ch in base)[:80]
    return f"{clean}-{uuid.uuid4().hex[:8]}"


class _Heartbeat(threading.Thread):
    def __init__(self, client: ArnoldRunnerClient, number: int, worker_id: str, interval: float):
        super().__init__(daemon=True)
        self.client, self.number, self.worker_id, self.interval = client, number, worker_id, interval
        self.stopped = threading.Event()

    def run(self):
        while not self.stopped.wait(self.interval):
            try:
                self.client.heartbeat(self.number, self.worker_id)
            except Exception as error:  # Lease loss surfaces on completion; keep the job running.
                logger.warning("Heartbeat failed for job %s: %s", self.number, error)


def execute_leased(client: ArnoldRunnerClient, job: dict, worker_id: str) -> bool:
    """Run a leased job with heartbeats and report its outcome to Arnold."""
    number = job["number"]
    lease_ms = max(job.get("leaseExpiresAt", 0) - int(time.time() * 1000), 60_000)
    heartbeat = _Heartbeat(client, number, worker_id, interval=max(lease_ms / 3000, 10))
    workdir = tempfile.mkdtemp(prefix=f"arnold-job-{number}-")
    heartbeat.start()
    try:
        logger.info("Running job %s (%s, attempt %s)", number, job.get("workflow"), job.get("attempt"))
        try:
            code = jobs.run(job.get("variables") or {}, workdir=workdir)
            detail = None if code == 0 else f"Workflow exited with status {code}"
        except jobs.JobError as error:
            code, detail = 2, str(error)
        succeeded = code == 0
        client.complete(number, worker_id, succeeded, detail)
        logger.info("Job %s %s", number, "succeeded" if succeeded else f"failed: {detail}")
        return succeeded
    finally:
        heartbeat.stopped.set()
        shutil.rmtree(workdir, ignore_errors=True)


def worker(concurrency: int, name: str | None, once: bool = False) -> None:
    client = ArnoldRunnerClient()
    stop = threading.Event()
    busy: set[int] = set()
    busy_lock = threading.Lock()

    def terminate(signum, _frame):
        # Container runtimes send SIGTERM on stop; as PID 1 the default action would be ignored.
        logger.info("Received signal %s; finishing running jobs before exit", signum)
        stop.set()

    signal.signal(signal.SIGTERM, terminate)

    def loop(slot: int):
        worker_id = _worker_id(f"{name or socket.gethostname()}-{slot}")
        while not stop.is_set():
            try:
                job = client.claim(worker_id)
            except Exception as error:
                logger.warning("Claim failed: %s", error)
                if once:
                    return
                stop.wait(5)
                continue
            if job:
                with busy_lock:
                    busy.add(slot)
                try:
                    execute_leased(client, job, worker_id)
                except Exception:
                    logger.exception("Job %s could not be completed", job.get("number"))
                finally:
                    with busy_lock:
                        busy.discard(slot)
            if once:
                return

    threads = [threading.Thread(target=loop, args=(slot,), daemon=True) for slot in range(concurrency)]
    for thread in threads:
        thread.start()
    try:
        # Idle slots may be blocked in a long-poll claim; they are daemon threads and safe to abandon on stop.
        while any(thread.is_alive() for thread in threads):
            if stop.is_set():
                with busy_lock:
                    if not busy:
                        return
            time.sleep(0.5)
    except KeyboardInterrupt:
        logger.info("Stopping after current jobs; press Ctrl+C again to abort")
        stop.set()
        for thread in threads:
            thread.join()


def single_job(claim: int | None) -> int:
    if claim is None:
        variables = {name: value for name, value in os.environ.items() if name in _job_variable_names()}
        try:
            return jobs.run(variables)
        except jobs.JobError as error:
            logger.error("%s", error)
            return 2
    client = ArnoldRunnerClient()
    worker_id = _worker_id(os.environ.get("GITHUB_RUN_ID") and f"gha-{os.environ['GITHUB_RUN_ID']}")
    job = client.claim(worker_id, job=claim)
    if not job:
        logger.error("Job %s is not available to claim", claim)
        return 3
    return 0 if execute_leased(client, job, worker_id) else 1


def _job_variable_names() -> set[str]:
    """Variables Arnold sets on CI-triggered jobs; other environment variables belong to the runner."""
    names = os.environ.get("ARNOLD_JOB_VARIABLES")
    if names:
        return {name.strip() for name in names.split(",") if name.strip()}
    return {name for name in os.environ if name.startswith(("ARNOLD_", "CLIO_", "MR_", "REVIEW_", "JIRA_TICKETS",
                                                            "RFC_URL", "PROJECT_ID", "EXPECTED_HEAD_SHA",
                                                            "EXECUTION_ID", "WORKFLOW", "PROVIDER", "REPO_URL",
                                                            "SOURCE_BRANCH", "TARGET_BRANCH", "MIN_CONFIDENCE",
                                                            "DRY_RUN"))
            and not jobs._PROTECTED.match(name)}


def main(argv: list[str] | None = None, command: str = "worker") -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    parser = argparse.ArgumentParser(prog=f"main.py {command}")
    if command == "worker":
        parser.add_argument("--concurrency", type=int, default=int(os.environ.get("ARNOLD_RUNNER_CONCURRENCY", "1")))
        parser.add_argument("--name", default=os.environ.get("ARNOLD_RUNNER_NAME"))
        parser.add_argument("--once", action="store_true", help="Exit after handling one claim per slot")
        args = parser.parse_args(argv)
        worker(max(1, args.concurrency), args.name, args.once)
        return
    parser.add_argument("--claim", type=int, help="Claim and run this queued job number (GitHub Actions mode)")
    args = parser.parse_args(argv)
    sys.exit(single_job(args.claim))
