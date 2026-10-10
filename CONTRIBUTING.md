# Contributing a useful review

Choose one component and read its `README.md` and `REVIEW.md` first. Each can be
built and tested independently.

An actionable issue contains:

1. Component and repository commit.
2. Runtime/compiler versions and exact reproduction command.
3. A minimal synthetic input sequence or test.
4. Expected and observed results, distinguishing a documented tradeoff from a bug.
5. The smallest proposed contract or implementation change, if known.

Please avoid bundling unrelated changes. A failing regression test is especially
useful. Do not attach real conversations, recordings, documents, credentials or
private source material; recreate the behavior with invented inputs.

For speech history, distinguish observed background from authorized actions.
For the rolling buffer, distinguish process interruption from physical power
loss. For visible-send, distinguish an inferred occurrence from a server receipt.
For transcript coverage, distinguish agreement from independent completeness.

Label AI assistance when the destination community requests it, and review every
technical claim before posting. This repository does not override a community's
rules about generated content.
