import json
import re
import tempfile
import unittest
from datetime import date
from pathlib import Path

import app.db as db
from app.boards import BOARD_BY_SLUG
from app.snapshot import build_snapshot

ASSET = Path(__file__).resolve().parents[2] / "android" / "app" / "src" / "main" / "assets" / "leaderboards.json"


class SnapshotContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        # CI 没有本地开发库：用临时数据库自建 schema + 种子数据，保证测试可移植
        cls._tmp = tempfile.TemporaryDirectory()
        cls._orig_db_path = db.DB_PATH
        cls._orig_data_dir = db.DATA_DIR
        tmp = Path(cls._tmp.name)
        db.DATA_DIR = tmp
        db.DB_PATH = tmp / "test.db"
        db.init_db()
        now = db.now_iso()
        with db.get_conn() as conn:
            conn.executemany(
                "INSERT INTO boards (slug, name, dimension, score_type, url, last_success_at) VALUES (?,?,?,?,?,?)",
                [
                    ("modelsage-arena", "ModelSage Arena", "overall", "elo", "https://modelsage.cn", now),
                ],
            )
            conn.executemany(
                "INSERT INTO models (slug, display_name, vendor, params_b, license, context_window, source_url, release_date, first_seen_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                [
                    ("gpt-x", "GPT-X", "openai", 1000.0, "proprietary", "128k", "https://example.com/gpt-x", "2025-08-07", now, now),
                    ("open-y", "Open-Y", "meta", 70.0, "open", "128k", "https://example.com/open-y", "2025-01-01", now, now),
                ],
            )
            conn.executemany(
                "INSERT INTO scores (board_slug, model_slug, rank, score, votes, fetched_at) VALUES (?,?,?,?,?,?)",
                [
                    ("modelsage-arena", "gpt-x", 1, 1280.5, 1000, now),
                    ("modelsage-arena", "open-y", 2, 1210.0, 900, now),
                ],
            )

    @classmethod
    def tearDownClass(cls) -> None:
        db.DB_PATH = cls._orig_db_path
        db.DATA_DIR = cls._orig_data_dir
        cls._tmp.cleanup()

    def test_snapshot_schema_from_db(self) -> None:
        snapshot = build_snapshot()

        self.assertEqual(snapshot["schemaVersion"], 3)
        self.assertTrue(re.match(r"^\d{4}-\d{2}-\d{2}T", snapshot["generatedAt"]))
        self.assertEqual(
            {board["slug"] for board in snapshot["boards"]}, set(BOARD_BY_SLUG)
        )
        self.assertIn("agent", snapshot["entriesByBoard"])
        self.assertEqual(snapshot["entriesByBoard"]["agent"], [])
        self.assertEqual(snapshot["models"], {})
        self.assertIn("benchmarkMeta", snapshot)
        self.assertIn("livebench", snapshot["benchmarkMeta"])
        for board in snapshot["boards"]:
            self.assertIn(board["slug"], snapshot["entriesByBoard"])
            self.assertEqual(len(snapshot["entriesByBoard"][board["slug"]]), board["model_count"])

    def test_bundled_snapshot_matches_schema(self) -> None:
        snapshot = json.loads(ASSET.read_text(encoding="utf-8"))

        self.assertEqual(snapshot["schemaVersion"], 3)
        self.assertTrue(snapshot["source"]["id"])
        self.assertTrue(snapshot["sources"])
        self.assertIn("openrouter", {item["id"] for item in snapshot["sources"]})
        self.assertNotIn("helm", {item["id"] for item in snapshot["sources"]})
        self.assertTrue(snapshot["generatedAt"])
        self.assertTrue(snapshot["boards"])
        self.assertTrue(snapshot["models"])
        self.assertTrue(snapshot["entriesByBoard"])
        usage = [
            board for board in snapshot["boards"]
            if board["dimension"] == "usage"
        ]
        self.assertEqual(
            [board["slug"] for board in usage],
            ["openrouter-weekly-usage"],
        )
        self.assertEqual(len(snapshot["entriesByBoard"][usage[0]["slug"]]), 10)
        upstream = usage[0]["evaluation"]["upstreamUpdatedAt"]
        self.assertRegex(upstream, r"^\d{4}-\d{2}-\d{2}$")
        self.assertLessEqual(
            abs(
                (
                    date.fromisoformat(upstream)
                    - date.fromisoformat(snapshot["generatedAt"][:10])
                ).days
            ),
            7,
        )

    def test_build_snapshot_migrates_an_old_database_before_querying(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            original = db.DB_PATH, db.DATA_DIR
            try:
                db.DATA_DIR = Path(tmp)
                db.DB_PATH = db.DATA_DIR / "legacy.db"
                with db.get_conn() as conn:
                    conn.executescript(
                        """
                        CREATE TABLE boards (
                            slug TEXT PRIMARY KEY, name TEXT NOT NULL,
                            dimension TEXT NOT NULL, score_type TEXT NOT NULL,
                            url TEXT NOT NULL, last_success_at TEXT,
                            last_status TEXT, last_error TEXT
                        );
                        CREATE TABLE models (
                            slug TEXT PRIMARY KEY, display_name TEXT NOT NULL,
                            vendor TEXT, params_b REAL, license TEXT,
                            context_window TEXT, source_url TEXT,
                            first_seen_at TEXT NOT NULL, updated_at TEXT NOT NULL
                        );
                        CREATE TABLE scores (
                            board_slug TEXT NOT NULL, model_slug TEXT NOT NULL,
                            rank INTEGER NOT NULL, score REAL, score_ci REAL,
                            votes INTEGER, price_in REAL, price_out REAL,
                            currency TEXT NOT NULL DEFAULT 'CNY',
                            fetched_at TEXT NOT NULL,
                            PRIMARY KEY (board_slug, model_slug)
                        );
                        """
                    )

                snapshot = build_snapshot()

                self.assertEqual(
                    {board["slug"] for board in snapshot["boards"]},
                    set(BOARD_BY_SLUG),
                )
                with db.get_conn() as conn:
                    board_columns = {
                        row[1] for row in conn.execute("PRAGMA table_info(boards)")
                    }
                self.assertIn("score_metadata", board_columns)
                self.assertIn("upstream_updated_at", board_columns)
            finally:
                db.DB_PATH, db.DATA_DIR = original


if __name__ == "__main__":
    unittest.main()
