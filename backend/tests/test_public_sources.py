"""Regression tests with minimal synthetic fixtures matching official source shapes."""
import asyncio
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from app import db
from app.boards import BOARD_BY_SLUG
from app.model_identity import canonical_model_slug
from app.public_sources import parse_aider_polyglot, parse_evalplus
from app.publish import restore_baseline
from app.scraper import fetch_board
from app.snapshot import build_snapshot
from app.store import save_board_data

AIDER = """
- model: Example reasoner (high)
  edit_format: diff
  pass_rate_2: 80.0
  total_cost: 100.0
  date: 2025-01-01
- model: Example reasoner (high)
  edit_format: whole
  pass_rate_2: 70.0
- model: Example zero
  edit_format: diff
  pass_rate_2: 0
- model: Missing score
  edit_format: whole
"""
EVALPLUS = {
    "Model A": {"link": "https://example.com/model", "pass@1": {"humaneval+": 90.0, "mbpp+": None}, "prompted": True},
    "Model B": {"pass@1": {"humaneval+": 90, "mbpp+": 70}},
    "Zero": {"pass@1": {"humaneval+": 0, "mbpp+": 0}},
    "Missing": {"pass@1": {"humaneval+": None}},
    "Invalid": {"pass@1": {"humaneval+": float("nan"), "mbpp+": 101}},
    "Bool": {"pass@1": {"humaneval+": True}},
}


class PublicSourceParserTests(unittest.TestCase):
    def test_aider_metric_configuration_and_honest_nulls(self):
        rows = parse_aider_polyglot(AIDER)
        self.assertEqual(len(rows), 3)
        self.assertEqual(rows[0]["score"], 80)
        self.assertEqual(rows[-1]["score"], 0)
        self.assertNotEqual(canonical_model_slug(rows[0]["name"], rows[0]["slug"]), canonical_model_slug(rows[1]["name"], rows[1]["slug"]))
        self.assertIn("high", rows[0]["name"])
        self.assertIsNone(rows[0]["price_in"])
        self.assertIsNone(rows[0]["price_out"])
        self.assertNotIn("release_date", rows[0])  # result date is not model release
        self.assertIsNone(rows[0]["license"])

    def test_aider_rejects_bad_shape_empty_duplicate_and_unsafe_yaml(self):
        for content in ["{}", "[]", AIDER + AIDER, "!!python/object/apply:os.system ['echo unsafe']"]:
            with self.assertRaises(Exception):
                parse_aider_polyglot(content)

    def test_evalplus_selects_exact_metric_preserves_ties_and_zero(self):
        human = parse_evalplus(EVALPLUS)
        mbpp = parse_evalplus(EVALPLUS, "mbpp+")
        self.assertEqual([row["score"] for row in human], [90, 90, 0])
        self.assertEqual([row["rank"] for row in human], [1, 2, 3])
        self.assertEqual([row["score"] for row in mbpp], [70, 0])
        self.assertIsNone(human[0]["license"])
        self.assertEqual(human[0]["source_url"], "https://example.com/model")
        for bad in [[], {}, {"model": {"pass@1": {"humaneval+": "95"}}}]:
            with self.assertRaises(ValueError):
                parse_evalplus(bad)
        with self.assertRaises(ValueError):
            parse_evalplus(EVALPLUS, "humaneval")

    def test_fetch_routes_to_official_data(self):
        with patch("app.scraper.fetch_html", new=AsyncMock(return_value=AIDER)) as html:
            rows = asyncio.run(fetch_board(BOARD_BY_SLUG["aider-polyglot"]))
        self.assertEqual(len(rows), 3)
        self.assertIn("Aider-AI/aider", html.call_args.args[1])
        with patch("app.scraper.fetch_json", new=AsyncMock(return_value=EVALPLUS)) as data:
            rows = asyncio.run(fetch_board(BOARD_BY_SLUG["evalplus-mbpp-plus"]))
        self.assertEqual(len(rows), 2)
        self.assertIn("evalplus.github.io", data.call_args.args[1])

    def test_new_sources_store_snapshot_and_baseline_restore(self):
        with tempfile.TemporaryDirectory() as tmp:
            old_path, old_dir = db.DB_PATH, db.DATA_DIR
            try:
                db.DATA_DIR = Path(tmp)
                db.DB_PATH = Path(tmp) / "sources.db"
                db.init_db()
                for slug, rows in [("aider-polyglot", parse_aider_polyglot(AIDER)),
                                   ("evalplus-humaneval-plus", parse_evalplus(EVALPLUS)),
                                   ("evalplus-mbpp-plus", parse_evalplus(EVALPLUS, "mbpp+"))]:
                    self.assertEqual(save_board_data(BOARD_BY_SLUG[slug], rows, "2026-10-19T04:00:00+00:00"), len(rows))
                snapshot = build_snapshot()
                self.assertEqual({board["source_id"] for board in snapshot["boards"]}, {"aider", "evalplus"})
                for board in snapshot["boards"]:
                    self.assertEqual(board["metric"]["unit"], "%")
                    self.assertTrue(board["metric"]["higherIsBetter"])
                    expected_version = BOARD_BY_SLUG[board["slug"]].get("release")
                    self.assertEqual(board["evaluation"]["version"], expected_version)
                    self.assertIsNone(board["evaluation"]["upstreamUpdatedAt"])
                    self.assertTrue(board["source"]["licenseUrl"])
                    self.assertEqual(len(snapshot["entriesByBoard"][board["slug"]]), board["model_count"])
                path = Path(tmp) / "baseline.json"
                path.write_text(json.dumps(snapshot), encoding="utf-8")
                db.DB_PATH = Path(tmp) / "restored.db"
                restore_baseline(path)
                restored = build_snapshot()
                self.assertEqual(restored["entriesByBoard"], snapshot["entriesByBoard"])
                self.assertEqual(restored["historyByBoard"], snapshot["historyByBoard"])
            finally:
                db.DB_PATH, db.DATA_DIR = old_path, old_dir


if __name__ == "__main__":
    unittest.main()
