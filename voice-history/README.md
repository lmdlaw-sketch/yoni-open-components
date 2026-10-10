# Voice history

A small Kotlin/JVM buffer for bounded, partial speech observations. Its purpose is to retain background for a later connection without turning a clipped sentence into a misleading standalone suffix.

For example, retaining “create a reminder” after evicting an earlier “do not” changes the apparent meaning. An assistant interjection such as “okay?” does not necessarily establish a new user turn. This buffer groups observations conservatively and omits whole groups instead of keeping their tails.

This is observed background, **not action authority, a transcript of completed turns, or proof that audio was played**. It has no microphone, network connection, model dependency, storage adapter or action executor. It does not itself prevent an external application from executing a command.

## Run

Requires a JDK and a Kotlin/JVM compiler. No Gradle, Android SDK, JUnit or third-party libraries are required.

```sh
sh ./run-tests.sh
```

The script uses `kotlinc` and `java` from `PATH`. Set `KOTLINC` or `JAVA` to executable paths if needed. It builds in a temporary directory and removes it when finished. The test runner has 16 deterministic cases.

Verified on 2026-10-10 with Kotlin/JVM 2.1.20 and OpenJDK 17.0.20: all 16 tests passed, and the example compiled and ran successfully.

Run the small example from this directory:

```sh
kotlinc src/main/kotlin/org/yonicomponents/voicehistory/LiveHistory.kt \
  examples/Example.kt -include-runtime -d /tmp/voice-history-example.jar
java -jar /tmp/voice-history-example.jar
```

The example prints a synthetic overflow, the resulting loss of later context, and recovery after a cut. It does not request a reminder or connect to a provider.

## Input contract

The caller delivers deltas once, in delivery order. Each delta supplies a speaker (`input=true` for user), text, a start offset and an optional interruption generation. A generation identifies input associated with an acknowledged interruption; it is not an inferred conversational turn.

Use one consistent, nonnegative offset timeline. `cut(offset, acceptedInterruptionGeneration)` is a boundary established by the host. The buffer does not discover or verify that boundary. Do not infer a cut merely from silence or an assistant interjection.

| Method | Meaning |
|---|---|
| `add(input, text, start, interruptionGeneration)` | Append an observed delta. Adjacent deltas from the same speaker and generation form one unit. A blocked speaker is ignored until a cut or clear. |
| `cut(offset, acceptedInterruptionGeneration)` | Remove current-generation assistant output and ambiguous input groups; retain eligible earlier input and input owned by the accepted interruption after its boundary. Advance the buffer generation and clear speaker blocks. |
| `snapshot(previous)` | Return bounded `(speaker, text)` pairs, optionally combined with an earlier snapshot. The special speaker `omitted` denotes missing context. This call does not mutate stored history. |
| `clear()` | Forget all observations, generations, blocks and omission state. |

Deliver snapshots as quoted observations with explicit missing-context metadata. Do not turn their pairs into newly authorized requests or assert that their assistant text was heard. Keep action receipts and permissions separate.

## Limits and intentional loss

- At most 32 output units, including an omission marker.
- At most 5,000 UTF-8 bytes of text, including the marker.
- At most 1,000 Unicode code points per unit. An oversized unit is omitted whole.
- Eviction discards all observations by the affected speaker in the same buffer generation. That speaker is blocked for the remainder of the current generation.
- Imported snapshots contain no generation metadata. Their observations are grouped conservatively by speaker for eviction.

After an oversized user unit, even an unrelated later question is omitted from this buffer until a cut. This is a documented availability tradeoff. It does **not** mean the current question must disappear from the application's live caption or request path; those paths are outside this component.

The limits bound retained text, not allocations required to accept an arbitrarily large incoming string. Enforce input-size limits before calling this component in a host application.

## Scope and verification

The tests exercise cuts, interruption ownership, negation/suffix loss, assistant backchannels, unit and byte budgets, imported snapshots, Unicode limits, clearing state and the intentional loss of unrelated later input. They do not establish provider behavior, audio playback, speech recognition accuracy or crash recovery.

The caller owns duplicate suppression, transcript-revision handling, input validation and serialization of concurrent access. The class is process-local and is not thread-safe. It reduces a specific class of misleading suffixes; it cannot prove that the retained text is semantically complete.

Review the state-machine tradeoff in [REVIEW.md](REVIEW.md). See the root README for the repository's license status.
