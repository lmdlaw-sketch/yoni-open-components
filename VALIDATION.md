# Validation record

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
