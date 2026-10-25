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

"""
Arnold runner — unified CLI entry point.

Usage:
    python main.py worker   [--concurrency N]     claim and run jobs from Arnold (local mode)
    python main.py job      [--claim NUMBER]      run one job (GitLab CI / GitHub Actions modes)
    python main.py improve  [improve-specific args]
"""

import sys


def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        print(
            "Usage: python main.py <command> [options]\n"
            "\n"
            "Commands:\n"
            "  worker                Claim and run jobs from Arnold (runner.mode LOCAL)\n"
            "  job                   Run one job from env vars, or --claim a queued job (GitLab CI / GitHub Actions)\n"
            "  improve               Run inline code suggestions on a merge request\n"
            "  review                Run high-level review summary on a merge request\n"
            "  describe              Generate a structured PR description for a merge request\n"
            "  sonarfix              Auto-fix SonarQube issues on a merge request\n"
            "  impact                Post-merge impact analysis of code review comments\n"
            "  smart_diff           Generate an Arnold review walkthrough\n"
            "  group_review         Analyze an Arnold MR group\n"
            "\n"
            "Run 'python main.py <command> --help' for command-specific options."
        )
        sys.exit(0)

    command = sys.argv[1]
    # Remove the command name so the sub-module's argparse sees clean argv
    sys.argv = [sys.argv[0]] + sys.argv[2:]

    if command in ("worker", "job"):
        from runner.worker import main as runner_main
        runner_main(sys.argv[1:], command)
        return
    if command == "improve":
        from flows.code_reviews.improve.main import main as improve_main
        improve_main()
    elif command == "review":
        from flows.code_reviews.review.main import main as review_main
        review_main()
    elif command == "describe":
        from flows.code_reviews.describe.main import main as describe_main
        describe_main()
    elif command == "sonarfix":
        from flows.sonarfix.main import main as sonarfix_main
        sonarfix_main()
    elif command == "impact":
        from flows.code_reviews.impact.main import main as impact_main
        impact_main()
    elif command == "group_review":
        from flows.code_reviews.group_review.main import main as group_review_main
        group_review_main()
    elif command == "smart_diff":
        from flows.code_reviews.smart_diff.main import main as smart_diff_main
        smart_diff_main()
    else:
        print(f"Unknown command: {command}")
        print("Available commands: improve, review, describe, sonarfix, impact, smart_diff")
        sys.exit(1)


if __name__ == "__main__":
    main()
