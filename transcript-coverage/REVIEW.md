# Review question

**How can an OCR–model transcription checker reduce false alarms without masking
a missing "not", condition, or date?**

This baseline compares an OCR transcript against a second reader's verbatim
transcription. A one-way quotation check misses omitted clauses, so it performs
bidirectional token alignment. Every token difference is reported; agreement is
never treated as proof of completeness or permission to perform an action.

Useful feedback would include a small synthetic failing example or a reproducible
comparison against this baseline:

1. Which alignment approach handles repeated clauses and reordering more clearly
   while preserving all changed digits, negations and punctuation?
2. How should a dataset separate benign OCR noise from consequential omissions
   without treating the OCR output itself as ground truth?
3. How would you test correlated omissions, where both systems miss the same text,
   using independently annotated source images?
4. Can a proposed change reduce nuisance flags without silently discarding a real
   missing condition, and what precision/recall tradeoff does the evidence show?

The included examples are entirely synthetic. Please contribute synthetic
counterexamples or data you are entitled to share; no private documents are needed
to review the alignment logic. The current suite does not evaluate OCR accuracy.
