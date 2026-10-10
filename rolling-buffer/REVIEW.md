# Review request: can a committed protected clip lose a segment?

This component isolates an encrypted rolling buffer so its transaction order can
be challenged without an Android device. Please read the limits in the README
before treating a storage guarantee as established.

## Focused question

Can you construct an interleaving or process-interruption checkpoint that breaks
either invariant?

1. A successfully committed protected clip loses a referenced segment through
   retention or concurrent export/delete activity.
2. A failed catalog commit makes the previously committed archive unreadable.

The current write order is: write and sync an immutable authenticated segment;
atomically replace the encrypted catalog; publish the state snapshot; delete
only unreferenced files. Saves protect intervals, overlapping clips share
segments, and read leases block clip deletion. Recovery reconciles authenticated
orphan segments and preserves unreadable orphans rather than silently resetting
the archive. The scope is process interruption, not guaranteed durability across
sudden power loss. One repository instance owns a directory.

## Most useful contribution

Please submit a small deterministic failing test, the exact interruption point
or interleaving, the expected invariant, and the smallest proposed fix. If a
guarantee requires a narrower contract instead of a code change, explain why.

Existing tests cover commit rejection, protected orphan recovery, corrupt or
unmovable orphans, missing referenced files, blocked writers, concurrent
save/prune commands and deletion while a lease is held. The fault hook currently
intercepts the catalog commit before replacement; it is not exhaustive crash
injection at every filesystem operation.

Secondary feedback is welcome on how to measure or reduce full-catalog rewrite
cost and lock contention without weakening the storage order. Please separate
a demonstrated defect from an unmeasured performance concern.

Independent review already found one concrete defect: mutating lease A's public
clip ID to B and closing A could release B's deletion protection. This extraction
now captures a private immutable lease identity and uses it for release. The new
regression checks that B remains protected, A is released normally, and repeated
close does not affect B. This is a deliberate correctness fix, not a claim that
the whole component is now verified against every caller behavior.

Run `./run-tests.sh --demo` for 59 synthetic scenarios and a short
recovery example. Keep proposed fixtures synthetic and free of recordings,
credentials and personal metadata. Hardware capture and a broader application
rewrite are outside this review's scope.

