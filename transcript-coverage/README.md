# Transcript coverage

A small, offline Python baseline for finding differences between local OCR text
and a second reader's verbatim transcription. It highlights missing instructions,
negations, conditions, dates, added words and punctuation changes. It does not run
OCR or call a model.

## Run

Python 3.9 or newer; standard library only. From this directory:

```sh
python3 -m unittest discover -s tests -v
python3 demo.py
```

## Use

```python
from transcript_coverage import transcript_difference

result = transcript_difference(
    "The valve must not open.",
    "The valve must open.",
)
print(result["differences"][0]["source_text"])  # not
```

The comparison tokenizes Unicode words and individual non-whitespace punctuation,
then uses `difflib.SequenceMatcher(..., autojunk=False)`. Spacing between tokens
can differ. Letters, digits and punctuation are not normalized, corrected or
fuzzily substituted. Source and reader token ranges are zero-based and half-open.
The labels `source_text` and `reader_text` identify inputs; neither is ground truth.

## Results and limits

| Result | Meaning |
| --- | --- |
| `NEEDS_MANUAL_REVIEW` with differences | The two token sequences disagree. Inspect the supplied text and original source. |
| `NEEDS_MANUAL_REVIEW` with `EMPTY_OR_TOO_MANY_TOKENS` | No comparison verdict; one input is empty after tokenization or exceeds the token limit. Empty `differences` is not agreement. |
| `TRANSCRIPT_MATCHES_LOCAL_OCR_NOT_VERIFIED` | The compared token sequences agree after an explicitly requested exclusion, if any. This does not prove either transcription is correct or complete. |

The function accepts at most 100,000 source characters, 4,000 reader characters
and 2,000 tokens in either input. Invalid types or character limits raise
`CoverageError`. Token-limit and empty-input outcomes retain `None` candidate
counts. Candidate counts refer to unmatched spans, not word counts or severity.
`SequenceMatcher` can have quadratic behavior; these limits bound input size,
not a measured execution-time guarantee.

The result always keeps `coverage_verified`, `external_actions_authorized`,
`source_item_closed` and `acceptance_eligible` false. These fields communicate that
text agreement provides no action authority. This module performs no network or
file operations. Digests identify the original input strings serialized as compact
JSON; they are not authentication or evidence that OCR ran.

If both OCR and a model omit the same clause, this checker cannot see it. Differences
may also be harmless OCR errors. The tests exercise synthetic strings, not document
accuracy, model behavior or an end-to-end OCR pipeline. No precision/recall claim
is made.

## Optional terminal-line exclusion

Nothing is ignored by default. For a known synthetic marker, a caller may opt in:

```python
result = transcript_difference(
    "Inspect.\nEND OF SAMPLE\n",
    "Inspect.",
    ignored_terminal_line="END OF SAMPLE",
)
```

Only the exact final nonblank source line is eligible. Matching is case-sensitive;
leading/trailing spaces are significant. Ordinary CR/LF line endings are ignored.
The excluded line, its original one-based line number and the reason are returned
in `excluded_lines`. Earlier matches, trailing clauses, case changes and additional
spaces are not excluded. The original source digest still includes the line.

This deliberately changes the extracted algorithm's previous behavior: an
application-specific terminal-footer rule was replaced with an explicit exact-line
argument that defaults to off. No implicit footer vocabulary remains.

The image cropper and source/receipt binding layer are outside this package.
Four original image/receipt integration checks are not claimed here. The public
suite contains 16 focused text tests, including the one-way quote-check counterexample
and tests of the new exclusion boundary.

## Review

See [REVIEW.md](REVIEW.md) for the question this baseline is intended to make concrete.
