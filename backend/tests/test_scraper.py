import re
import unittest
from pathlib import Path

from app.scraper import parse_aa_board, parse_arena_board

FIXTURES = Path(__file__).parent / "fixtures"


class ScraperRegressionTests(unittest.TestCase):
    def test_aa_board_fixture(self) -> None:
        html = (FIXTURES / "aa_overall.html").read_text(encoding="utf-8")
        rows = parse_aa_board(html)

        self.assertGreater(len(rows), 50)
        self.assertEqual(rows[0]["rank"], 1)
        self.assertTrue(all(row["name"] for row in rows))
        self.assertTrue(all(row["vendor"] for row in rows))
        self.assertTrue(all(row["score"] is not None for row in rows))
        self.assertTrue(all(row["source_url"].startswith("https://modelsage.cn/model/") for row in rows))
        self.assertEqual([row["rank"] for row in rows], list(range(1, len(rows) + 1)))

    def test_arena_board_fixture(self) -> None:
        html = (FIXTURES / "arena_text.html").read_text(encoding="utf-8")
        rows = parse_arena_board(html)

        self.assertGreater(len(rows), 50)
        self.assertEqual(rows[0]["rank"], 1)
        self.assertTrue(all(row["name"] for row in rows))
        self.assertTrue(all(row["score"] is not None for row in rows))
        self.assertTrue(all(row["currency"] == "USD" for row in rows))
        self.assertTrue(all(row["license"] for row in rows))
        self.assertTrue(
            all(
                row["price_in"] is None or row["price_in"] >= 0
                for row in rows
            )
        )
        self.assertEqual([row["rank"] for row in rows], list(range(1, len(rows) + 1)))

    def test_price_and_votes_are_numeric(self) -> None:
        html = (FIXTURES / "arena_text.html").read_text(encoding="utf-8")
        rows = parse_arena_board(html)
        priced = [row for row in rows if row["price_in"] is not None and row["price_out"] is not None]
        voted = [row for row in rows if row["votes"] is not None]

        self.assertGreater(len(priced), 10)
        self.assertGreater(len(voted), 10)
        self.assertTrue(all(row["price_out"] >= row["price_in"] for row in priced))
        self.assertTrue(all(re.fullmatch(r"\d+", str(row["votes"])) for row in voted))


if __name__ == "__main__":
    unittest.main()
