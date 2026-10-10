"""Run with `python3 demo.py`; all text below is synthetic."""

import json

from transcript_coverage import transcript_difference


def main():
    examples = {
        "missing_negation": ("The valve must not open.", "The valve must open."),
        "omitted_instruction": (
            "Inspect at 10:30. Disconnect power first.",
            "Inspect at 10:30.",
        ),
        "spacing_only": ("Inspect at 10:30.\nLog the result.", "Inspect at 10 : 30 . Log the result."),
        "agreement_is_not_proof": ("The valve opens.", "The valve opens."),
    }
    for name, (source, reader) in examples.items():
        print(json.dumps({"example": name, "result": transcript_difference(source, reader)},
                         ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
