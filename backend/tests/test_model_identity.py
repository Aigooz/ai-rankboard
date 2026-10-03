import unittest

from app.model_identity import canonical_slug


class ModelIdentityTests(unittest.TestCase):
    def test_claude_word_order_variants_join(self) -> None:
        self.assertEqual(
            canonical_slug("Claude 4.5 Sonnet (non-reasoning)"),
            canonical_slug("Claude Sonnet 4.5 (non-reasoning)"),
        )

    def test_reasoning_tiers_are_preserved(self) -> None:
        self.assertNotEqual(
            canonical_slug("GPT-5.2 Codex (xhigh)"),
            canonical_slug("GPT-5.2 Codex (non-reasoning)"),
        )
        self.assertEqual(canonical_slug("GPT-5.2 Codex (xhigh)"), "gpt-5-2-codex-xhigh")

    def test_fallback_text_is_removed(self) -> None:
        self.assertEqual(
            canonical_slug("Claude Opus 5.5 (high with fallback)"),
            canonical_slug("Claude Opus 5.5 (high)"),
        )

    def test_livebench_and_modelsage_variants_join(self) -> None:
        self.assertEqual(
            canonical_slug("Claude 4.5 Opus (high)"),
            canonical_slug("Claude Opus 4.5 (high with fallback)"),
        )


if __name__ == "__main__":
    unittest.main()
