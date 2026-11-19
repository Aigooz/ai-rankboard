import tempfile
import unittest
from pathlib import Path

from app import db
from app.main import search_models


class ApiModelSearchTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.original = db.DB_PATH, db.DATA_DIR
        db.DATA_DIR = Path(self.tmp.name)
        db.DB_PATH = db.DATA_DIR / "api.db"
        db.init_db()
        now = db.now_iso()
        with db.get_conn() as conn:
            conn.executemany(
                """INSERT INTO boards
                   (slug, name, dimension, score_type, url, last_success_at)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                [
                    ("arena", "Arena", "overall", "arena_elo", "https://example.com", now),
                    ("percent", "Percent", "coding", "evalplus", "https://example.com", now),
                ],
            )
            conn.executemany(
                """INSERT INTO models
                   (slug, display_name, vendor, first_seen_at, updated_at)
                   VALUES (?, ?, ?, ?, ?)""",
                [
                    ("alpha", "Alpha", "Vendor", now, now),
                    ("zeta", "Zeta", "Vendor", now, now),
                ],
            )
            conn.executemany(
                """INSERT INTO scores
                   (board_slug, model_slug, rank, score, currency, fetched_at)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                [
                    ("arena", "alpha", 2, 90.0, "CNY", now),
                    ("percent", "alpha", 1, 1500.0, "USD", now),
                    ("arena", "zeta", 1, 80.0, "CNY", now),
                ],
            )

    def tearDown(self):
        db.DB_PATH, db.DATA_DIR = self.original
        self.tmp.cleanup()

    def test_model_search_keeps_scores_on_their_own_board(self):
        result = search_models()

        self.assertEqual(result["rankingPolicy"], "no_cross_board_score_aggregation")
        self.assertEqual([model["slug"] for model in result["models"]], ["alpha", "zeta"])
        self.assertEqual(result["total"], 2)
        alpha = result["models"][0]
        self.assertNotIn("best_score", alpha)
        self.assertNotIn("best_dimension", alpha)
        self.assertEqual(len(alpha["scores"]), 2)
        self.assertEqual(
            {
                (score["board_slug"], score["score"])
                for score in alpha["scores"]
            },
            {("arena", 90.0), ("percent", 1500.0)},
        )

    def test_model_search_pages_without_using_mixed_scores(self):
        first = search_models(limit=1)
        second = search_models(limit=1, offset=1)

        self.assertEqual(first["models"][0]["slug"], "alpha")
        self.assertEqual(second["models"][0]["slug"], "zeta")
        self.assertEqual(first["total"], 2)


if __name__ == "__main__":
    unittest.main()
