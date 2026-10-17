# Yoni's component review lab

[![Component tests (main)](https://github.com/lmdlaw-sketch/yoni-open-components/actions/workflows/component-tests.yml/badge.svg?branch=main&event=push)](https://github.com/lmdlaw-sketch/yoni-open-components/actions/workflows/component-tests.yml)

Four small, independently runnable components, each centered on one concrete
correctness question. Start with the component closest to your experience; no
knowledge of the surrounding applications is needed.

| Component | Language | What it does | Review question |
| --- | --- | --- | --- |
| [Voice history](voice-history/) | Kotlin/JVM | Bounds partial speech observations without retaining a misleading sentence tail | [What boundary information would permit less destructive eviction?](voice-history/REVIEW.md) |
| [Encrypted rolling buffer](rolling-buffer/) | Java 17 | Retains recent encoded packets and protects selected intervals | [Can save, retention, leases and recovery lose a protected interval?](rolling-buffer/REVIEW.md) |
| [Visible-send observation](visible-send/) | Java 11+ | Matches a draft and outgoing-bubble count changes | [What extra evidence distinguishes a new send from a viewport change?](visible-send/REVIEW.md) |
| [Transcript coverage](transcript-coverage/) | Python 3.9+ | Finds token differences between two transcriptions | [How can OCR noise be tolerated without hiding consequential omissions?](transcript-coverage/REVIEW.md) |

Each directory contains code, synthetic examples, regression tests, an input
contract and a focused review request. These are review baselines, not complete
applications or production SDKs. Their limits are part of the questions.

## Run everything

Install Java 17 or newer, Kotlin/JVM 2.1.20 or compatible, Python 3.9 or newer,
and Bash. No Android SDK, Gradle, Maven, JUnit, API keys or service accounts are
needed. The test scripts do not download dependencies.

```sh
bash run-tests.sh
```

Each component can also be tested on its own using its README. `KOTLINC` and
`JAVA` can select executable paths; the Java runners also support `JAVA_HOME`.
Generated files are temporary or ignored under `build/`.
See [VALIDATION.md](VALIDATION.md) for the recorded test results and their limits.

## Continuous integration

[Component tests](.github/workflows/component-tests.yml) runs the existing root
runner and the documented voice-history example for pull requests and pushes to
`main`. It uses Java 17, Python 3.12 and Kotlin/JVM 2.1.20. The Kotlin compiler
download is pinned to the checksum published with the official release, and
GitHub Actions dependencies are pinned to full commit hashes.

The workflow provisions its toolchains; the component test scripts themselves
remain offline and do not download dependencies. It needs no repository secrets,
Android SDK, service account or application integration. The job uses read-only
repository permissions and does not upload build artifacts or publish releases.

The badge above tracks push runs on `main`. Open the
[workflow runs](https://github.com/lmdlaw-sketch/yoni-open-components/actions/workflows/component-tests.yml)
to check the result for the exact commit.
[Recorded hosted CI results](VALIDATION.md#2026-10-17-hosted-ci) identify the verified
run and commit separately from the local test results in the same document.

## Review and contribute

Please open one issue per question or submit a small patch with a failing
synthetic test. Include the repository commit, component, exact command, observed
result and expected behavior. See [CONTRIBUTING.md](CONTRIBUTING.md).

The most useful feedback is an executable counterexample, a clearer input
contract or evidence that a stated invariant is too strong. A passing unit test
does not establish real-device behavior, provider behavior, OCR accuracy or
power-loss durability.

## Extraction notes

The voice-history and visible-send core algorithms retain their original behavior.
The rolling buffer fixes a lease-accounting bug found during extraction review:
closing a lease uses its original identity, independently of mutable display
metadata. Packages and synthetic labels were renamed, and standalone test
harnesses were added. The Python checker has one deliberate behavior change: an application-specific
footer exemption is now an explicit exact-line option, disabled by default.
Each component documents its boundaries in more detail.

AI assistance was used to extract these components, adapt tests and prepare
documentation. Test output and review questions are provided so the claims can
be checked directly.

## License status

This publication is for inspection and technical review. No general reuse
license has been selected yet; public visibility alone is not a reuse license.
