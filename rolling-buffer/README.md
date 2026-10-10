# Encrypted rolling buffer

A dependency-free Java 17 core for retaining a recent time window of encoded audio,
protecting selected intervals, and recovering an interrupted archive. This is a
small reviewable extraction, not a microphone recorder or a production-ready SDK.

The useful question is whether retention, save, export and recovery preserve the
same storage invariants when they overlap. See [REVIEW.md](REVIEW.md) for a focused
request for counterexamples and failing tests.

## Run

Install a JDK 17 or newer, then run from this directory:

```sh
./run-tests.sh
./run-tests.sh --demo
```

Set `JAVA` to the Java executable path to select a runtime explicitly. Otherwise,
the script uses `JAVA_HOME` when it points to Java, then `javac` and `java`
from `PATH`. An explicit `JAVA` uses its own compiler module. If the `javac`
executable is absent but the installed Java contains
the `jdk.compiler` module, it invokes that compiler directly. It compiles with
`--release 17` and needs no Gradle, Maven, Android SDK or network downloads.
Use a POSIX shell; on Windows, Git Bash or WSL is suitable.
JUnit-style output is written to `build/test-results/core-tests.xml`.

The suite contains 59 scenarios: the original 58 plus a regression for mutable
lease metadata. The demo injects one failure
after a segment write but before its catalog commit, reopens the repository, and
checks recovery of the protected orphan. It does not kill a process or simulate
a power cut. All packet payloads in the tests and demo are synthetic; they are
not decodable audio. No microphone, personal recording or production key is used.

## Design

1. Encoded packets are grouped into immutable segment files without restarting
   an external encoder. The core does not implement that encoder.
2. Each segment is independently authenticated with AES-GCM. Its object name is
   included in authenticated data.
3. A serialized repository writes the segment, syncs and atomically replaces an
   encrypted catalog, publishes the new state snapshot, then deletes unreferenced
   segments. A failed catalog commit must leave previously committed files intact.
4. Save protects a half-open interval. Repeated save while a clip is active returns
   that clip; overlapping clips can share segments. Finish waits until durable
   encoded output covers the requested end. A completed clip can still contain
   reported gaps: completion does not mean uninterrupted audio.
5. Read leases prevent deletion of a clip during playback or export. Recovery
   reconciles protected orphan segments, quarantines unreadable orphans, and marks
   interrupted recordings without restarting capture or guessing a crash time.
6. Export produces separate AAC parts for timestamp discontinuities and a JSON
   timeline. Export hashes describe the exported bytes; they are not signatures
   or proof of origin. Playback offers a random-access ADTS view without writing
   a plaintext temporary audio file.

## Files and boundaries

| Class | Responsibility |
| --- | --- |
| `Repository` | Retention, save/finish, leases, catalog transactions and recovery |
| `Model` | Packets, segment encoding, clip intervals and timeline events |
| `CryptoBox` | Authenticated object encryption using a caller-supplied key |
| `SegmentWriter`, `Aac` | Segment grouping and AAC-LC framing helpers |
| `Archive` | Export and virtual random-access playback |
| `CaptureClock` | Fixed sample-time anchor and explicit discontinuity rejection |
| `PcmQueue` | Bounded transient PCM queue |
| `CaptureJournal` | Encrypted interruption marker and last incident |

This module excludes Android services, UI, permissions, hardware capture,
Keystore integration, signing material and application data. Java packages and
test labels were renamed during extraction. Independent review then demonstrated
that changing a lease's public clip ID could release another clip's protection.
The extracted implementation fixes that defect by storing the original lease
identity privately, with a focused regression. See [EXTRACTION.md](EXTRACTION.md)
for the exact change boundary. The legacy `RollingAudio/v1/` authenticated-data
prefix and binary schema markers are preserved as format constants. Some
existing user-facing strings and the export notice remain Hebrew.

## Honest limits

- The intended recovery contract concerns **process interruption**. Temporary
  files are synced before atomic replacement, but parent directories are not
  fsynced. **Abrupt device power-loss durability is not guaranteed.** Filesystems
  without atomic replacement fail closed.
- Pending encoder or segment data can be lost. Recovery cannot reconstruct audio
  that never reached storage. Missing referenced files or an unreadable catalog
  fail closed; unreadable orphan objects are preserved where possible.
- Only one `Repository` instance should own a directory. Synchronization and
  leases are process-local; there is no cross-process file locking.
- The caller owns key creation, durable key availability, access controls and
  lifecycle. Losing the key means losing access to encrypted content. The demo
  deliberately uses an ephemeral key and deletes its temporary archive.
- A rolling time window does not cap protected archives. Protected clips are not
  deleted to free space. Low storage or the catalog size limit can reject writes.
- The entire catalog is rewritten on a commit. Segment reads under a lease hold
  the repository monitor during I/O and decryption. Scalability and tail latency
  need measurement rather than assumptions.
- Clip boundaries use AAC frame granularity, not sample-exact editing. Export is
  intentionally plaintext when explicitly requested. Silence and missing time
  are different concepts.
- Java tests do not verify a real codec, microphone availability, physical device
  lock-screen behavior, sustained capture, thermal behavior or battery usage.
  This extraction is not an independent cryptographic audit or a claim of data-loss
  immunity.

