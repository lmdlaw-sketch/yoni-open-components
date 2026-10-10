#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

printf '\nVoice history\n'
bash "$repo_dir/voice-history/run-tests.sh"
printf '\nEncrypted rolling buffer\n'
bash "$repo_dir/rolling-buffer/run-tests.sh" --demo
printf '\nVisible-send observation\n'
bash "$repo_dir/visible-send/run-tests.sh"
printf '\nTranscript coverage\n'
(
  cd "$repo_dir/transcript-coverage"
  PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -v
  PYTHONDONTWRITEBYTECODE=1 python3 demo.py
)
printf '\nAll component checks completed.\n'
