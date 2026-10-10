# Extraction and review changes

This module began as a narrow Java-core extraction. Package and import names were
changed to `org.yonicomponents.rollingbuffer`; the synthetic test directory prefix
and JUnit suite label were renamed. The original 58 test scenarios remain.
The demo, shell runner and English review documentation are new.

One deliberate correctness fix was made after independent review produced a
counterexample:

- The public `Lease.clip` metadata remains mutable. Previously, `close()` used
  its current `clip.id` to update the private lease registry. If a caller changed
  lease A's clip ID to B, closing A could remove B's deletion protection while
  B's lease was still open. A's original registration could also remain held.
- `Lease` now captures `originalClipId` in a private final field at construction.
  `close()` always releases that original identity, independent of later metadata
  mutation. The method remains synchronized and idempotent.
- The 59th test opens two leases, mutates A's public metadata, closes A, and checks
  that B cannot be deleted, A can be deleted, B remains readable, and closing A
  again does not affect B. B can be deleted after its own lease closes.

No other storage algorithm, serialization format or cryptographic format was
changed during extraction. This statement describes the patch boundary; it does
not certify the remaining implementation. Other public metadata remains mutable.
The originating application has not been modified by this extraction.

The shell runner supports an explicit `JAVA` executable path, then `JAVA_HOME`,
then `PATH`. It uses the selected runtime's `jdk.compiler` module for an explicit
`JAVA` or when no `javac` executable is present, without shell evaluation.
