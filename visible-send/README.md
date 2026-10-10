# Visible-send observation model

A small Java state machine for examining whether a sequence of supplied conversation snapshots is consistent with a new outgoing text message. It is extracted for discussion and improvement, with its existing behavior preserved.

It takes **synthetic frames supplied by the caller**. This module contains no Android code, screen reader, accessibility service, database, network client, or connection to a messaging application.

## The useful part

The model requires an observed draft, an empty composer, and exactly one additional matching outgoing bubble. A click alone and an empty composer alone are insufficient. Identical consecutive sends receive different occurrence tokens, and display events cannot indefinitely renew an unedited draft's lifetime.

| Input or rule | Current behavior |
|---|---|
| Conversation scope | Application label, conversation title, window number |
| Body matching | SHA-256 of text after trimming surrounding whitespace; inner whitespace, literal markup and emoji joiners remain distinct |
| Draft lifetime | 120 seconds from the latest actual composer edit |
| Confirmation window | 8 seconds after arming; a nearby click extends it to 15 seconds without restarting its clock |
| Matching bubble count | Must rise by exactly one; falling or rising by more than one invalidates the pending attempt |
| Output | An inferred occurrence, with an observation time and a random occurrence token |

`ConfirmedSend` is the retained class name. **It means only that this model's conditions matched. It is not a server receipt, authenticated conversation identity, or delivery confirmation.**

## Run

Requires a JDK 11 or newer and Bash. No downloaded dependencies or build system are required.

```bash
bash run-tests.sh
```

From the repository root, run `bash visible-send/run-tests.sh`. `JAVA_HOME`, or explicit `JAVAC` and `JAVA` paths, can select the JDK. If the `javac` launcher is absent but Java includes `jdk.compiler`, the script uses that module directly. Compilation output is created in a temporary directory and removed afterward.

The script runs 41 adapted regression cases with a small ordinary-Java assertion runner, then the synthetic example. The test runner is not JUnit. These tests cover the model; they do not establish real-device behavior.

## Integration contract

- Confine all calls on a tracker to one thread.
- Supply complete, bounded `ConversationFrame` values with a nondecreasing elapsed-time clock. The frame's `packageName` is a caller-supplied scope label; nothing is installed or queried.
- Call `clear()` whenever the observation context becomes unreliable, including a known viewport change. The model cannot discover scrolling itself.
- Schedule `expire(now)` from `nextExpiryMillis()` if plaintext draft references must be dropped while no new events arrive. The model has no background timer.
- Dropping a Java reference is not secure erasure, and the model cannot control snapshots retained by its caller.
- A single pending draft is tracked. Starting another message before the previous matching bubble appears can lose the previous observation.

## The open problem

An old bubble entering the viewport can create the same count increase as a new send. A deleted draft followed by an old matching bubble may produce **exactly the same input frames** as a real send. The model accepts both. A context reset helps only when the caller knows that the viewport changed.

`SyntheticExample` makes this limitation executable: two identical frame sequences are accepted even though their real-world interpretations differ. It also shows that an explicit reset rejects a stale observation. No claim is made that a particular application or device produced those traces.

See [REVIEW.md](REVIEW.md) for the focused review question and contribution boundaries.
