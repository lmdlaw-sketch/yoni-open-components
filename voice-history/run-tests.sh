#!/bin/sh
set -eu

component_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
compiler=${KOTLINC:-kotlinc}
java_command=${JAVA:-java}
if ! command -v "$compiler" >/dev/null 2>&1; then
    printf '%s\n' 'Kotlin/JVM compiler missing. Add kotlinc to PATH or set KOTLINC to its executable.' >&2
    exit 127
fi
if ! command -v "$java_command" >/dev/null 2>&1; then
    printf '%s\n' 'Java missing. Add java to PATH or set JAVA to its executable.' >&2
    exit 127
fi
build_dir=$(mktemp -d "${TMPDIR:-/tmp}/voice-history-tests.XXXXXX")
trap 'rm -rf "$build_dir"' 0
trap 'exit 1' HUP INT TERM
"$compiler" \
    "$component_dir/src/main/kotlin/org/yonicomponents/voicehistory/LiveHistory.kt" \
    "$component_dir/src/test/kotlin/org/yonicomponents/voicehistory/LiveHistoryTest.kt" \
    -include-runtime -d "$build_dir/tests.jar"
"$java_command" -jar "$build_dir/tests.jar"
