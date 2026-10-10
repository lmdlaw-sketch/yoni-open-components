"""Synthetic text examples; no model, image, network, or external data required."""

import hashlib
import unittest

from transcript_coverage import CoverageError, transcript_difference


class TranscriptCoverageTests(unittest.TestCase):
    def test_missing_whole_instruction_even_when_reader_quote_matches(self):
        result = transcript_difference("Inspect on 2030-04-01. Disconnect power first.", "Inspect on 2030-04-01.")
        self.assertEqual(result["state"], "NEEDS_MANUAL_REVIEW")
        self.assertEqual(result["differences"][0]["source_text"], "Disconnect power first.")
        self.assertGreater(result["omission_candidates"], 0)

    def test_negation_condition_deadline_and_second_date_remain_visible(self):
        examples = [
            ("The valve must not open.", "The valve must open."),
            ("Run only with supervision.", "Run."),
            ("Reply within 7 days.", "Reply."),
            ("Inspect on 2030-04-01 and 2030-04-08.", "Inspect on 2030-04-01."),
        ]
        for source, reader in examples:
            with self.subTest(source=source):
                self.assertEqual(transcript_difference(source, reader)["state"], "NEEDS_MANUAL_REVIEW")

    def test_additions_replacements_digits_and_punctuation_are_differences(self):
        for reader in ("Inspect the early valve.", "Inspect the valve at 10:30.", "Inspect the valve?"):
            with self.subTest(reader=reader):
                self.assertEqual(transcript_difference("Inspect the valve.", reader)["state"], "NEEDS_MANUAL_REVIEW")
        result = transcript_difference("Inspect at 10:30.", "Inspect at 10:35.")
        self.assertEqual(result["differences"][0]["source_text"], "30")
        self.assertEqual(result["differences"][0]["reader_text"], "35")

    def test_line_wrapping_and_token_boundary_spacing_can_agree(self):
        result = transcript_difference("On 2030-04-01\nat 10:30.", "On 2030 - 04 - 01 at 10 : 30 .")
        self.assertEqual(result["state"], "TRANSCRIPT_MATCHES_LOCAL_OCR_NOT_VERIFIED")
        for key in ("coverage_verified", "external_actions_authorized", "source_item_closed", "acceptance_eligible"):
            self.assertIs(result[key], False)

    def test_explicit_terminal_exclusion_is_exact_and_recorded(self):
        result = transcript_difference("Inspect.\nEND OF SAMPLE\n\n", "Inspect.", ignored_terminal_line="END OF SAMPLE")
        self.assertEqual(result["differences"], [])
        self.assertEqual(result["excluded_lines"], [{"line": 2, "text": "END OF SAMPLE", "reason": "explicit_exact_terminal_line"}])
        for source in ("END OF SAMPLE\nDisconnect power.", "Inspect.\nEND OF SAMPLE and stop", "Inspect.\nend of sample", "Inspect.\n END OF SAMPLE"):
            with self.subTest(source=source):
                result = transcript_difference(source, "Inspect.", ignored_terminal_line="END OF SAMPLE")
                self.assertEqual(result["state"], "NEEDS_MANUAL_REVIEW")
                self.assertEqual(result["excluded_lines"], [])

    def test_exclusions_are_off_by_default(self):
        result = transcript_difference("Inspect.\nEND OF SAMPLE", "Inspect.")
        self.assertEqual(result["state"], "NEEDS_MANUAL_REVIEW")
        self.assertEqual(result["excluded_lines"], [])
        self.assertEqual(result["differences"][0]["source_text"], "END OF SAMPLE")

    def test_empty_or_over_budget_never_looks_like_agreement(self):
        for source, reader in (("", ""), ("  ", "Inspect"), ("Only source", ""), ("word " * 2001, "word"), ("a", "!" * 2001)):
            with self.subTest(lengths=(len(source), len(reader))):
                result = transcript_difference(source, reader)
                self.assertEqual(result["state"], "NEEDS_MANUAL_REVIEW")
                self.assertEqual(result["reason"], "EMPTY_OR_TOO_MANY_TOKENS")
                self.assertIsNone(result["omission_candidates"])
                self.assertIsNone(result["addition_candidates"])

    def test_excluding_the_only_line_does_not_pass(self):
        result = transcript_difference("END OF SAMPLE", "", ignored_terminal_line="END OF SAMPLE")
        self.assertEqual(result["state"], "NEEDS_MANUAL_REVIEW")
        self.assertEqual(result["reason"], "EMPTY_OR_TOO_MANY_TOKENS")

    def test_duplicate_clause_omission_and_reordering_are_detected(self):
        result = transcript_difference("Inspect first. Inspect first.", "Inspect first.")
        self.assertGreater(result["omission_candidates"], 0)
        result = transcript_difference("First inspect. Then run.", "Then run. First inspect.")
        self.assertTrue(result["differences"])

    def test_agreement_does_not_prove_either_reader_saw_all_source_text(self):
        result = transcript_difference("The valve opens.", "The valve opens.")
        self.assertFalse(result["coverage_verified"])
        self.assertFalse(result["acceptance_eligible"])
        self.assertEqual(result["api_calls"], 0)

    def test_one_way_quote_matching_misses_an_extra_instruction(self):
        source = "Inspect the valve. Disconnect power first."
        reader = "Inspect the valve."
        self.assertIn(reader, source)  # A one-way quote check succeeds.
        result = transcript_difference(source, reader)
        self.assertGreater(result["omission_candidates"], 0)
        self.assertEqual(result["state"], "NEEDS_MANUAL_REVIEW")

    def test_invalid_text_types_and_character_limits_raise(self):
        for source, reader in ((None, "x"), (b"x", "x"), ("x", 4), ("x" * 100001, "x"), ("x", "x" * 4001)):
            with self.subTest(types=(type(source), type(reader))):
                with self.assertRaisesRegex(CoverageError, "TRANSCRIPT_COVERAGE_TEXT_LIMIT"):
                    transcript_difference(source, reader)

    def test_invalid_exclusion_cannot_broaden_the_match(self):
        for exclusion in ("", " ", "footer\nextra", "footer\n", "footer\rextra", "a\u2028b", 3, b"footer", "x" * 4001):
            with self.subTest(exclusion=repr(exclusion)[:40]):
                with self.assertRaisesRegex(CoverageError, "TRANSCRIPT_COVERAGE_INVALID_EXCLUSION"):
                    transcript_difference("Inspect.", "Inspect.", ignored_terminal_line=exclusion)

    def test_digests_cover_original_text_even_when_a_line_is_excluded(self):
        source = "Inspect.\nEND OF SAMPLE"
        full = transcript_difference(source, "Inspect.")
        excluded = transcript_difference(source, "Inspect.", ignored_terminal_line="END OF SAMPLE")
        self.assertEqual(full["source_text_digest"], excluded["source_text_digest"])
        self.assertNotEqual(full["source_text_digest"], transcript_difference("Inspect.", "Inspect.")["source_text_digest"])
        self.assertEqual(transcript_difference("abc", "abc")["source_text_digest"], hashlib.sha256(b'"abc"').hexdigest())

    def test_unicode_letters_are_not_folded_or_repaired(self):
        result = transcript_difference("הדלת לא פתוחה.", "הדלת פתוחה.")
        self.assertEqual(result["differences"][0]["source_text"], "לא")
        for source, reader in (("OPEN", "open"), ("café", "cafe"), ("alpha", "alp ha")):
            with self.subTest(source=source):
                self.assertEqual(transcript_difference(source, reader)["state"], "NEEDS_MANUAL_REVIEW")

    def test_ranges_are_half_open_and_identify_the_reported_tokens(self):
        result = transcript_difference("red green blue", "red blue")
        self.assertEqual(result["differences"], [{
            "kind": "delete", "source_token_range": [1, 2], "reader_token_range": [1, 1],
            "source_text": "green", "reader_text": "",
        }])
        self.assertEqual(result["source_tokens"], 3)
        self.assertEqual(result["reader_tokens"], 2)


if __name__ == "__main__":
    unittest.main()
