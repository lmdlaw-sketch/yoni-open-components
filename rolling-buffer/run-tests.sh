#!/usr/bin/env sh
set -eu

component_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$component_dir"

if [ -n "${JAVA:-}" ]; then
    runtime="$JAVA"
    compiler=""
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    runtime="$JAVA_HOME/bin/java"
    compiler=""
    if [ -x "$JAVA_HOME/bin/javac" ]; then compiler="$JAVA_HOME/bin/javac"; fi
else
    compiler=$(command -v javac || true)
    runtime=$(command -v java || true)
fi
if [ -z "$runtime" ]; then
    echo "Java 17 or newer with compiler support is required (or set JAVA_HOME)." >&2
    exit 2
fi
if [ -z "$compiler" ] && ! "$runtime" -m jdk.compiler/com.sun.tools.javac.Main --version >/dev/null 2>&1; then
    echo "The Java compiler is missing: install JDK 17 or newer with javac or the jdk.compiler module." >&2
    exit 2
fi
compile_sources() {
    if [ -n "$compiler" ]; then
        "$compiler" "$@"
    else
        "$runtime" -m jdk.compiler/com.sun.tools.javac.Main "$@"
    fi
}

case "${1:-}" in
    ""|--demo) ;;
    *) echo "Usage: ./run-tests.sh [--demo]" >&2; exit 2 ;;
esac

mkdir -p build
build_dir=$(mktemp -d "$component_dir/build/run.XXXXXX")
trap 'rm -rf "$build_dir"' 0
trap 'exit 130' INT
trap 'exit 143' TERM

find src/main/java src/test/java src/demo/java -name '*.java' -print > "$build_dir/sources.txt"
compile_sources --release 17 -encoding UTF-8 -d "$build_dir/classes" @"$build_dir/sources.txt"

test_status=0
(cd "$build_dir" && "$runtime" -cp classes org.yonicomponents.rollingbuffer.CoreTests) || test_status=$?
mkdir -p build/test-results
if [ -f "$build_dir/core-tests.xml" ]; then
    cp "$build_dir/core-tests.xml" build/test-results/core-tests.xml
fi
if [ "$test_status" -ne 0 ]; then
    exit "$test_status"
fi
if [ "${1:-}" = "--demo" ]; then
    "$runtime" -cp "$build_dir/classes" org.yonicomponents.rollingbuffer.Demo
fi

