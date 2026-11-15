#!/usr/bin/env python3
"""Add (default) or verify (--check) the Apache 2.0 header on tracked source files."""
import re
import subprocess
import sys

YEAR = "2026"
LINES = [
    f"Copyright (c) {YEAR} Original Author(s), PhonePe Limited",
    "",
    'Licensed under the Apache License, Version 2.0 (the "License");',
    "you may not use this file except in compliance with the License.",
    "You may obtain a copy of the License at",
    "",
    "http://www.apache.org/licenses/LICENSE-2.0",
    "",
    "Unless required by applicable law or agreed to in writing, software",
    'distributed under the License is distributed on an "AS IS" BASIS,',
    "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
    "See the License for the specific language governing permissions and",
    "limitations under the License.",
]
MARKER = "Licensed under the Apache License, Version 2.0"
INCLUDE = re.compile(r"^(arnold-backend/.*\.java|arnold-ui/(src/.*|[^/]+|scripts/[^/]+)\.(js|jsx|mjs|cjs)"
                     r"|arnold-runner/.*\.py|scripts/.*\.py)$")
EXCLUDE = re.compile(r"(^|/)(node_modules|target|\.next)/|eslint\.config\.mjs$|next\.config\.mjs$|postcss\.config\.mjs$")


def c_style():
    return "/*\n" + "\n".join((" * " + line).rstrip() for line in LINES) + "\n */\n"


def hash_style():
    return "\n".join(("# " + line).rstrip() for line in LINES) + "\n"


def add(path, text):
    if path.endswith(".py"):
        lines = text.split("\n")
        prefix = []
        while lines and (lines[0].startswith("#!") or re.match(r"#.*coding[:=]", lines[0])):
            prefix.append(lines.pop(0))
        return "\n".join(prefix + [hash_style().rstrip("\n"), ""] + lines) if prefix else hash_style() + "\n" + text
    directive = re.match(r"((?:'use (?:client|server)';|\"use (?:client|server)\";)\n)", text)
    if directive:
        return directive.group(1) + "\n" + c_style() + text[directive.end():].lstrip("\n").join(["\n", ""])
    return c_style() + "\n" + text


def main():
    check = "--check" in sys.argv
    files = subprocess.check_output(["git", "ls-files"], text=True).split()
    missing = []
    for path in files:
        if not INCLUDE.match(path) or EXCLUDE.search(path):
            continue
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        if MARKER in text[:1500]:
            continue
        missing.append(path)
        if not check:
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(add(path, text))
    if check and missing:
        print("Missing license header:\n  " + "\n  ".join(missing))
        sys.exit(1)
    if not check:
        print(f"Added headers to {len(missing)} files")


if __name__ == "__main__":
    main()
