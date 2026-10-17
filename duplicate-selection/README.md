# Duplicate selection

A small Kotlin/JVM planner for duplicate-file selections. One path has one
selection state, even when several result groups contain it. The planner checks
both the visible groups and content sets before returning selected paths.

The useful problem is overlap: two independent exact-content groups can share a
name-based presentation group. Choosing every suggested copy independently can
empty that presentation group. This component removes selections as needed to
retain a represented path in every nonempty group and every represented content
set of at least two paths.

## Run

Use a JDK 17 or newer, Kotlin/JVM 2.1.20 or compatible, and Bash:

```sh
bash run-tests.sh
```

Set `KOTLINC` or `JAVA` to select executable paths. The runner compiles into a
temporary directory, runs 17 source-derived tests and an in-memory example,
then removes its build output. It downloads no dependencies and uses a small
ordinary-Kotlin assertion runner.

## Operations

| Operation | Result |
| --- | --- |
| `suggestions(groups)` | Suggested paths after applying the same selection constraints |
| `sanitize(groups, requested)` | Immutable selected paths and requested paths that were removed |
| `canSelect(groups, selected, path)` | Whether adding one path preserves the requested selection without silently removing another |

Only a valid `EXACT` group makes a path eligible for selection. It needs at least
two distinct paths, equal sizes and equal nonempty supplied hashes. `NAME`
groups can constrain selections but cannot introduce a removal candidate.

The fallback keeper order is: a path inside a `DCIM` directory, an unmarked
filename, an older modification time, a shorter path, then lexical path order.
A group's valid `keeperPath` takes precedence. `NameRules` classifies explicit
copy markers and at most one trailing single-digit marker; a normalized name
does not establish equal content.

## Input contract

- Supply one coherent snapshot: the same path must describe the same file and
  metadata wherever it appears. Keep group IDs stable and unique for predictable
  ordering.
- Supply consistent, canonical path strings in the format used by the host JVM.
  Paths are string identities here; aliases, links and filesystem case behavior
  are the caller's responsibility.
- Provide sizes and full-content hashes from a trusted scanning process. The
  planner compares the supplied values; it does not read files or compute hashes.
- Do not mutate the input collections while a call is running.
- Revalidate file identity, content and surviving copies immediately before a
  separate filesystem action. A previously returned selection is only a plan.

## Scope and limits

All tests and the example use invented path strings and hash labels. The code
performs no filesystem reads, writes or deletions. Java's path helpers are used
only to interpret path strings.

The retained `DuplicateGroup.reclaimable` property is a per-group display
estimate derived from the group's keeper. It is not an aggregate of a sanitized
selection, and summing it across overlapping groups can double count paths.
No measured disk-space claim follows from this planner.

The policy can retain more copies than a globally optimal deletion plan,
especially because name-based presentation groups also retain a path. Large-scan
performance, concurrent filesystem changes, hard links, actual file recovery
and Android behavior are outside the standalone verification.

## Extraction and validation

The two algorithm files retain their source behavior. The model file keeps the
declarations needed here; application scanning, actions and trash models are
omitted. Existing test bodies were adapted from JUnit annotations/assertions to
the included assertion runner. See [VALIDATION.md](VALIDATION.md).

See [REVIEW.md](REVIEW.md) for the focused question and the
[root README](../README.md#license-status) for the repository's license status.
