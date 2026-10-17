# Validation record

## 2026-10-17 hosted CI

GitHub reports the following completed workflow run as successful:

| Field | Recorded value |
| --- | --- |
| Workflow | `Component tests` |
| Run | [39049615823](https://github.com/lmdlaw-sketch/yoni-open-components/actions/runs/39049615823) |
| Commit | `edbec2363011becfa3cfb4966909df9cef595a5e` |
| Branch and event | `main`, `push` |
| Status and conclusion | `completed`, `success` |

This entry records the workflow's reported outcome. The per-component test counts
below remain the separately recorded local verification results.

## 2026-10-17 maintenance verification

The existing root runner was rerun locally against exact Git source
`617ba95f2f7095c0b7dfd793997ec2593a313f67`; all 41 fetched files matched their
Git blob hashes before execution. No component implementation, test or runner
was changed.

| Component | Executed tests | Outcome |
| --- | --- | --- |
| Voice history | 16 | Passed |
| Encrypted rolling buffer | 59 | Passed |
| Visible-send observation | 41 | Passed |
| Transcript coverage | 16 | Passed |
| **Total** | **132** | **0 failures** |

The root runner's rolling-buffer, visible-send and transcript examples completed.
The separate voice-history example was also compiled and run using its documented
command. Toolchains: OpenJDK 17.0.20 (including its compiler module),
Kotlin/JVM 2.1.20, Python 3.12.14.

The official Kotlin compiler ZIP was verified against its published SHA-256:
`a118197b0de55ffab2bc8d5cd03a5e39033cfb53383d6931bc761dec0784891a`.
The maintenance workflow uses the same pinned compiler and the unchanged root
runner. These are local results; hosted CI has its own result for each commit.

## 2026-10-10 extraction verification

The standalone packages were compiled and exercised on 2026-10-10. This is a
local verification record, not a CI badge or an independent audit.

| Component | Checks | Outcome |
| --- | --- | --- |
| Voice history | 16 deterministic tests and the overflow/cut example | Passed |
| Encrypted rolling buffer | 58 original synthetic scenarios, one lease-identity regression, recovery example | Passed |
| Visible-send observation | 41 adapted regression cases and an ambiguity/reset example | Passed |
| Transcript coverage | 16 synthetic tests and four examples | Passed |

Toolchains: OpenJDK and Java compiler 17.0.20; Kotlin/JVM 2.1.20; Python 3.12.14.
Python tests use the standard library. The root runner executes every component's suite; see the
component READMEs for separate commands and example details.

The lease-identity regression was added after a synthetic counterexample showed
that changing one lease's public clip ID could release another clip's protection.
The corrected implementation records the original acquired ID privately. This
fix is limited to the extracted public component.

Core-source comparison was also performed against the pinned extraction inputs.
The declared differences are package/public-visibility changes, test harness and
synthetic-label adaptations, the lease fix, and the Python footer option. Source
provenance is retained by the maintainer.

These checks do not validate microphone capture, application integration,
recognition accuracy, a provider's event guarantees, server delivery, actual
document completeness, physical power-loss durability or cryptographic design.
