"""Compare two transcriptions without claiming that either is complete or correct."""

from difflib import SequenceMatcher
import hashlib
import json
import re


VERSION = "transcript-coverage-1"
MAX_TOKENS = 2000
TOKEN = re.compile(r"\w+|[^\w\s]", re.UNICODE)


class CoverageError(ValueError):
    """The caller supplied text or an exclusion outside the supported contract."""


def _digest(value):
    canonical = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def transcript_difference(source_text, reader_text, *, ignored_terminal_line=None):
    """Return token differences between an OCR transcript and a reader transcript.

    `source_text` is a comparison input, not ground truth. An exact final nonblank
    line can be excluded only when the caller supplies `ignored_terminal_line`.
    Exclusions are off by default, case-sensitive, and recorded in the result.
    """
    if not (
        isinstance(source_text, str) and len(source_text) <= 100000
        and isinstance(reader_text, str) and len(reader_text) <= 4000
    ):
        raise CoverageError("TRANSCRIPT_COVERAGE_TEXT_LIMIT")
    if ignored_terminal_line is not None and not (
        isinstance(ignored_terminal_line, str)
        and 0 < len(ignored_terminal_line) <= 4000
        and ignored_terminal_line.strip()
        and ignored_terminal_line.splitlines() == [ignored_terminal_line]
    ):
        raise CoverageError("TRANSCRIPT_COVERAGE_INVALID_EXCLUSION")

    lines = source_text.splitlines(keepends=True)
    excluded = []
    last = next((i for i in range(len(lines) - 1, -1, -1) if lines[i].strip()), None)
    if (
        ignored_terminal_line is not None and last is not None
        and lines[last].rstrip("\r\n") == ignored_terminal_line
    ):
        excluded = [{
            "line": last + 1,
            "text": lines[last].rstrip("\r\n"),
            "reason": "explicit_exact_terminal_line",
        }]
        lines[last] = ""
    operative = "".join(lines)
    left = list(TOKEN.finditer(operative))
    right = list(TOKEN.finditer(reader_text))
    base = {
        "schema": 1,
        "version": VERSION,
        "api_calls": 0,
        "source_text_digest": _digest(source_text),
        "reader_text_digest": _digest(reader_text),
        "excluded_lines": excluded,
        "coverage_verified": False,
        "external_actions_authorized": False,
        "source_item_closed": False,
        "acceptance_eligible": False,
        "normalization": (
            "whitespace and token-boundary spacing only; "
            "all letters, digits and punctuation retained"
        ),
    }
    if not left or not right or max(len(left), len(right)) > MAX_TOKENS:
        return {
            **base,
            "state": "NEEDS_MANUAL_REVIEW",
            "reason": "EMPTY_OR_TOO_MANY_TOKENS",
            "differences": [],
            "omission_candidates": None,
            "addition_candidates": None,
        }

    def span(matches, start, end, text):
        if start == end:
            return ""
        return text[matches[start].start():matches[end - 1].end()]

    diffs = []
    for op, a, b, c, d in SequenceMatcher(
        None, [m.group() for m in left], [m.group() for m in right], autojunk=False
    ).get_opcodes():
        if op == "equal":
            continue
        diffs.append({
            "kind": op,
            "source_token_range": [a, b],
            "reader_token_range": [c, d],
            "source_text": span(left, a, b, operative),
            "reader_text": span(right, c, d, reader_text),
        })
    return {
        **base,
        "state": "NEEDS_MANUAL_REVIEW" if diffs else "TRANSCRIPT_MATCHES_LOCAL_OCR_NOT_VERIFIED",
        "reason": "POSSIBLE_OMISSION_OR_DIFFERENCE" if diffs else None,
        "differences": diffs,
        "source_tokens": len(left),
        "reader_tokens": len(right),
        "omission_candidates": sum(bool(d["source_text"]) for d in diffs),
        "addition_candidates": sum(bool(d["reader_text"]) for d in diffs),
    }
