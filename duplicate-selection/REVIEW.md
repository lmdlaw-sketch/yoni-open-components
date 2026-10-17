# Review question: selections across overlapping groups

Under the input contract in [README.md](README.md), can an arrangement of exact
content groups and name-based presentation groups cause the selected set to
contain every represented path in a group or a content set?

A useful counterexample should provide a small synthetic list of groups, the
requested path set, the returned selected/corrected sets, and an assertion that
demonstrates the violated invariant.

The test `crossGroupSafetyProtectsANameGroupWithoutAKeeperPath` and the included
example show why independently selecting the non-keeper in each exact group
does not suffice when presentation groups overlap.

Please keep two boundaries explicit:

1. The planner assumes consistent metadata for a path across its input groups.
   If relaxing that contract is the proposed change, show the conflicting input
   and the validation rule that would be needed.
2. Leaving one represented path unselected is a property of supplied metadata.
   Filesystem identity and content must be checked again by the action layer.

The current policy favors deterministic conservative selections. It does not
claim to find a selection that maximizes the bytes available to remove.
