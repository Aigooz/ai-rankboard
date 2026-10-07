import json
import re
import tempfile
import unittest
from pathlib import Path

import app.db as db
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
        self.assertEqual(snapshot["entriesByBoard"]["modelsage-arena"][0]["release_date"], "2025-08-07")
        self.assertTrue(re.match(r"^\d{4}-\d{2}-\d{2}T", snapshot["generatedAt"]))
        self.assertTrue(snapshot["boards"])
        self.assertTrue(snapshot["entriesByBoard"])
        self.assertTrue(snapshot["models"])
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
        self.assertIn("modelsage", {item["id"] for item in snapshot["sources"]})
        self.assertTrue(snapshot["generatedAt"])
        self.assertTrue(snapshot["boards"])
        self.assertTrue(snapshot["models"])
        self.assertTrue(snapshot["entriesByBoard"])


if __name__ == "__main__":
    unittest.main()
