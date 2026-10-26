#!/usr/bin/env python3
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

"""Review pinned MR groups and publish only after every review succeeds."""

import argparse
import os
import shutil

from dotenv import load_dotenv

from flows.code_reviews.group_review.flow import create_group_review_flow
from flows.code_reviews.group_review.nodes import Callback, StaleRun
from utils.env import get_env_optional


def execute(callback, token):
    """Run the group review flow and report terminal failures."""
    shared = {
        "callback": callback,
        "token": token,
        "states": {},
        "findings": [],
        "workspace": None,
    }
    try:
        create_group_review_flow().run(shared)
    except StaleRun as error:
        callback.update(
            "stale",
            shared["states"],
            shared["findings"],
            str(error),
        )
    except Exception:
        for mid, state in shared["states"].items():
            if state in {"preparing", "reviewing"}:
                shared["states"][mid] = "failed"
        callback.update(
            "partial" if shared["findings"] else "failed",
            shared["states"],
            shared["findings"],
            "Group analysis could not finish. See the runner logs.",
            member_reviews=shared.get("member_reviews"),
        )
        raise
    finally:
        if shared.get("workspace"):
            shutil.rmtree(shared["workspace"])


def main():
    load_dotenv()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--execution-id", required=True)
    args = parser.parse_args()
    url = get_env_optional("ARNOLD_URL")
    token = os.environ.get("GITLAB_ACCESS_TOKEN") or os.environ.get("SCM_GITHUB_TOKEN")
    if not url or not token:
        raise ValueError("ARNOLD_URL and an SCM token (GITLAB_ACCESS_TOKEN or SCM_GITHUB_TOKEN) are required")
    execute(Callback(url, args.run_id, args.execution_id), token)


if __name__ == "__main__":
    main()
