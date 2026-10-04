import csv
import json
import re
import unittest
from pathlib import Path

from app.scraper import parse_aa_board, parse_arena_board, parse_livebench_board, parse_release_date, parse_swe_bench_board

FIXTURES = Path(__file__).parent / "fixtures"


def load_livebench_fixtures():
    with (FIXTURES / "livebench_table.csv").open(encoding="utf-8", newline="") as f:
        table = list(csv.DictReader(f))
    with (FIXTURES / "livebench_cost.csv").open(encoding="utf-8", newline="") as f:
        costs = list(csv.DictReader(f))
    categories = json.loads((FIXTURES / "livebench_categories.json").read_text(encoding="utf-8"))
    return table, categories, costs


class ScraperRegressionTests(unittest.TestCase):
    def test_release_date_from_json_ld(self) -> None:
        html = """
        <html><body>
        <script type="application/ld+json">
        {"@type":"Product","name":"GPT-X","releaseDate":"2025-08-07"}
        </script>
        </body></html>
        """
        self.assertEqual(parse_release_date(html), "2025-08-07")
        self.assertIsNone(parse_release_date('<script type="application/ld+json">{}</script>'))

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

    def test_livebench_overall(self) -> None:
        table, categories, costs = load_livebench_fixtures()
        rows = parse_livebench_board(table, categories, costs, "*")

        self.assertGreater(len(rows), 50)
        self.assertTrue(all(row["score"] is not None for row in rows))
        self.assertEqual([row["rank"] for row in rows], list(range(1, len(rows) + 1)))
        self.assertGreaterEqual(rows[0]["score"], rows[-1]["score"])
        self.assertIn(rows[0]["vendor"], {"Anthropic", "OpenAI", "Google DeepMind", "DeepSeek"})
        priced = [row for row in rows if row["price_in"] is not None and row["price_out"] is not None]
        self.assertGreater(len(priced), 20)
        self.assertTrue(all(row["currency"] == "USD" for row in rows))

    def test_livebench_coding_uses_one_category(self) -> None:
        table, categories, costs = load_livebench_fixtures()
        overall = parse_livebench_board(table, categories, costs, "*")
        coding = parse_livebench_board(table, categories, costs, "Coding")

        self.assertEqual(len(overall), len(coding))
        self.assertNotEqual(coding[0]["score"], overall[0]["score"])

    def test_livebench_cost_file_is_optional(self) -> None:
        table, categories, _ = load_livebench_fixtures()
        rows = parse_livebench_board(table, categories, None, "*")

        self.assertGreater(len(rows), 50)
        self.assertIsNone(rows[0]["price_in"])
        self.assertIsNone(rows[0]["price_out"])

    def test_swe_bench_verified(self) -> None:
        data = json.loads((FIXTURES / "swebench_leaderboards.json").read_text(encoding="utf-8"))
        rows = parse_swe_bench_board(data, "Verified")

        self.assertGreater(len(rows), 100)
        self.assertEqual(rows[0]["rank"], 1)
        self.assertGreater(len([row for row in rows if row["vendor"]]), 100)
        self.assertTrue(all(row["score"] is not None for row in rows))
        self.assertTrue(all(row["license"] in {"open", "proprietary"} for row in rows))
        self.assertEqual([row["rank"] for row in rows], list(range(1, len(rows) + 1)))


if __name__ == "__main__":
    unittest.main()
