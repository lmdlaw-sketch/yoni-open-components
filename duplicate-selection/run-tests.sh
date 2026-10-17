#!/usr/bin/env bash
set -euo pipefail
component_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
compiler="${KOTLINC:-kotlinc}"
java_runtime="${JAVA:-java}"
test_dir="$(mktemp -d "${TMPDIR:-/tmp}/duplicate-selection.XXXXXX")"
trap 'rm -rf -- "$test_dir"' EXIT
"$compiler" "$component_dir"/src/*.kt "$component_dir"/test/*.kt \
  "$component_dir"/examples/*.kt -jvm-target 17 -include-runtime \
  -d "$test_dir/tests.jar"
"$java_runtime" -jar "$test_dir/tests.jar"
