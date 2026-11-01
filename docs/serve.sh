#!/usr/bin/env bash
# Serves the Arnold documentation locally at http://127.0.0.1:8000.
set -euo pipefail

cd "$(dirname "$0")"
pip install -q -r requirements.txt
python -m zensical serve --config-file zensical.toml
