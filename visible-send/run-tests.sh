#!/usr/bin/env bash
set -euo pipefail

component_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
javac_bin="${JAVAC:-javac}"
java_bin="${JAVA:-java}"
if [[ -n "${JAVA_HOME:-}" ]]; then
  javac_bin="${JAVAC:-$JAVA_HOME/bin/javac}"
  java_bin="${JAVA:-$JAVA_HOME/bin/java}"
fi
if ! command -v "$java_bin" >/dev/null 2>&1; then
  echo "Java runtime missing. Set JAVA_HOME or JAVAC/JAVA." >&2
  exit 2
fi
compiler=("$javac_bin")
if ! command -v "$javac_bin" >/dev/null 2>&1; then
  if [[ -z "${JAVAC:-}" ]] && "$java_bin" --describe-module jdk.compiler >/dev/null 2>&1; then
    compiler=("$java_bin" -m jdk.compiler/com.sun.tools.javac.Main)
  else
    echo "A JDK 11 or newer is required (javac/compiler module missing). Set JAVA_HOME or JAVAC/JAVA." >&2
    exit 2
  fi
fi
build_dir="$(mktemp -d "${TMPDIR:-/tmp}/visible-send.XXXXXX")"
trap 'rm -rf -- "$build_dir"' EXIT

"${compiler[@]}" --release 11 -encoding UTF-8 -d "$build_dir" \
  "$component_dir"/src/org/yonicomponents/visiblesend/*.java \
  "$component_dir"/test/org/yonicomponents/visiblesend/*.java \
  "$component_dir"/examples/org/yonicomponents/visiblesend/*.java
"$java_bin" -cp "$build_dir" org.yonicomponents.visiblesend.VisibleSendTrackerTest
"$java_bin" -cp "$build_dir" org.yonicomponents.visiblesend.SyntheticExample
