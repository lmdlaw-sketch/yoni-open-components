# Review request

**What additional observable evidence, or what weaker output contract, is needed to distinguish a new outgoing message from an old matching bubble entering the viewport when stable message IDs are unavailable?**

The current model intentionally avoids treating a click or composer-clear event alone as a send. It also rejects a count increase greater than one, expiration, incomplete observations, and changed conversation scope. Its remaining limitation is precise: `afterCount == beforeCount + 1` cannot explain why the count changed.

## Useful contributions

1. A minimal synthetic trace that demonstrates a false acceptance or missed legitimate repeated send.
2. An explicit assumption under which an improved algorithm works, including how a caller could verify that assumption.
3. A bounded alternative to count-only evidence, with tests for repeated identical messages, viewport replacement, delayed events, expiry and back-to-back drafts.
4. A clearer uncertainty-aware result type if the available observations cannot support a binary answer.

## Constraints worth preserving

- Keep the core independent of any platform or messaging application.
- Preserve literal text distinctions and separate tokens for separate inferred occurrences.
- Keep retained drafts and confirmation windows bounded. Do not extend a deadline merely because another display event arrived.
- Use invented input frames only; no private conversations, screenshots, credentials or device archives.
- State which additional information a proposal needs. A different threshold alone cannot distinguish two identical input traces.

The 41 adapted regression cases preserve the extracted model's previous behavior. They are a baseline for review, not evidence of universal correctness. The example intentionally exposes the unresolved ambiguity instead of silently changing the algorithm.
