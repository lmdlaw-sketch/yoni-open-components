# Local validation record

## 2026-10-17 extraction check

The standalone component was compiled and run locally with Kotlin/JVM 2.1.20
and OpenJDK 17.0.20. The runner completed with exit code 0.

| Existing source suite | Executed cases | Result |
| --- | --- | --- |
| SelectionPolicyTest | 9 | Passed |
| NameRulesTest | 8 | Passed |
| Total | 17 | 0 failures |

The synthetic example also ran. In its overlapping-group example, two requested
paths were reduced to one selection and one correction. The name-only example
returned an empty suggested selection.

These are source-derived deterministic tests. JUnit imports and annotations
were replaced by the included assertion functions and explicit case runner.
The algorithm files were preserved apart from their package names.

The five fetched source files matched their recorded Git blob hashes. A direct
text comparison confirmed that both algorithm files differ only in the package
declaration, and both test suites differ only in the declared harness adaptation.

This record covers local compilation, the listed tests and the in-memory example.
It makes no claim about hosted CI, Android, file scanning, deletion, disk space
or device acceptance.
