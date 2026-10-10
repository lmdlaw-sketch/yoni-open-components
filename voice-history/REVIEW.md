# Review question

**What additional boundary or provenance contract would let this buffer evict less context while still preventing a retained imperative suffix from losing its earlier negation?**

The current input contract has speaker, text, start offset and an acknowledged interruption generation. It does not include an authoritative completed-turn boundary. That describes this component's inputs, not a claim about every speech API.

The implementation discards an affected speaker's entire generation and ignores that speaker until a host-established cut. An assistant interjection cannot unblock it. The 16-case test runner includes both the safety example and its cost: a later unrelated question is also absent from recovery background.

Proposals should state the extra evidence their boundary needs, show its behavior when that evidence is missing or arrives late, and include a deterministic test. Preserve the byte, code-point and unit limits, whole-unit omission, and separation between observed history and permission to execute actions.

Useful feedback includes a counterexample to the current grouping rules, a smaller state machine with the same stated behavior, or a concrete alternative that keeps more useful context under a clearly stronger input contract. Provider or playback claims require separate evidence; the included tests are local buffer tests.
