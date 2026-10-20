import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch, AsyncMock

from app import db
from app.boards import BOARD_BY_SLUG
from app.store import save_board_data, mark_board_error, HISTORY_LIMIT
from app.snapshot import build_snapshot
from app.publish import restore_baseline
from app.service import refresh_board
from app.trust import health_info


class TrustTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.original = db.DB_PATH, db.DATA_DIR
        db.DATA_DIR = Path(self.tmp.name)
        db.DB_PATH = db.DATA_DIR / "test.db"
        db.init_db()
        self.board = BOARD_BY_SLUG["livebench-overall"]

    def tearDown(self):
        db.DB_PATH, db.DATA_DIR = self.original
        self.tmp.cleanup()

    def rows(self, count=2):
        return [{"slug": f"model-{i}", "name": f"Model {i}", "vendor": None,
                 "license": None, "context_window": None, "source_url": None,
                 "rank": i + 1, "score": 90 - i, "score_ci": None, "votes": None,
                 "price_in": None, "price_out": None, "currency": "CNY"} for i in range(count)]

    def test_success_failure_distinguish_times_and_preserve_scores(self):
        save_board_data(self.board, self.rows(), "2026-01-01T00:00:00+00:00")
        mark_board_error(self.board, "secret https://user:password@example/x?key=TOKEN C:\\secret", "2026-01-02T00:00:00+00:00")
        snapshot = build_snapshot()
        board = snapshot["boards"][0]
        self.assertEqual(snapshot["schemaVersion"], 3)
        self.assertEqual(board["health"]["lastSuccessAt"], "2026-01-01T00:00:00+00:00")
        self.assertEqual(board["health"]["latestAttemptAt"], "2026-01-02T00:00:00+00:00")
        self.assertEqual(board["health"]["latestAttemptStatus"], "error")
        self.assertTrue(board["health"]["stale"])
        self.assertIsNone(board["evaluation"]["upstreamUpdatedAt"])
        self.assertNotIn("TOKEN", json.dumps(snapshot))
        self.assertEqual(len(snapshot["entriesByBoard"][self.board["slug"]]), 2)
        self.assertEqual(len(snapshot["historyByBoard"][self.board["slug"]]), 1)
        self.assertEqual(board["health"]["anomalies"], [])

    def test_history_bounded_and_persisted_with_version(self):
        for day in range(1, 16):
            save_board_data(self.board, self.rows(102), f"2026-01-{day:02}T00:00:00+00:00")
        history = build_snapshot()["historyByBoard"][self.board["slug"]]
        self.assertEqual(len(history), HISTORY_LIMIT)
        self.assertEqual(len(history[-1]["entries"]), 100)
        self.assertTrue(history[-1]["truncated"])
        self.assertEqual(history[-1]["evaluationVersion"], self.board["release"])

    def test_empty_or_failed_write_does_not_change_history(self):
        save_board_data(self.board, self.rows(), "2026-01-01T00:00:00+00:00")
        rows = self.rows()
        rows[0]["currency"] = None  # SQL NOT NULL fails after history insert
        with self.assertRaises(Exception):
            save_board_data(self.board, rows, "2026-01-02T00:00:00+00:00")
        self.assertEqual(len(build_snapshot()["historyByBoard"][self.board["slug"]]), 1)
        with patch("app.service.fetch_board", new=AsyncMock(return_value=[])):
            import asyncio
            with self.assertRaises(ValueError):
                asyncio.run(refresh_board(self.board))
        self.assertEqual(build_snapshot()["boards"][0]["health"]["latestAttemptStatus"], "error")

    def test_empty_health_and_unknown_version_are_honest(self):
        health = health_info({}, [], datetime.now(timezone.utc))
        self.assertIsNone(health["stale"])
        self.assertIsNone(health["latestAttemptAt"])
        baseline = {"evaluationVersion": None, "modelCount": 20, "entries": []}
        changed = {"evaluationVersion": None, "modelCount": 1, "entries": []}
        self.assertEqual(health_info({}, [baseline, changed], datetime.now(timezone.utc))["anomalies"], [])
        baseline["evaluationVersion"] = changed["evaluationVersion"] = "v1"
        self.assertEqual(health_info({}, [baseline, changed], datetime.now(timezone.utc))["anomalies"][0]["code"], "model_count_drop")
        changed["evaluationVersion"] = "v2"
        self.assertEqual(health_info({}, [baseline, changed], datetime.now(timezone.utc))["anomalies"], [])

    def test_restore_baseline_preserves_history_without_inventing_it(self):
        save_board_data(self.board, self.rows(), "2026-01-01T00:00:00+00:00")
        snapshot = build_snapshot()
        path = db.DATA_DIR / "baseline.json"
        path.write_text(json.dumps(snapshot), encoding="utf-8")
        db.DB_PATH = db.DATA_DIR / "fresh.db"
        restore_baseline(path)
        restored = build_snapshot()
        self.assertEqual(restored["historyByBoard"], snapshot["historyByBoard"])
        self.assertEqual(restored["entriesByBoard"], snapshot["entriesByBoard"])
        mark_board_error(self.board, "failed", "2026-01-02T00:00:00+00:00")
        self.assertEqual(len(build_snapshot()["entriesByBoard"][self.board["slug"]]), 2)
        # A legacy v3 snapshot has cache but no historical evidence.
        snapshot.pop("historyByBoard")
        snapshot["boards"][0].pop("health")
        snapshot["boards"][0].pop("last_attempt_at")
        path.write_text(json.dumps(snapshot), encoding="utf-8")
        db.DB_PATH = db.DATA_DIR / "legacy.db"
        restore_baseline(path)
        self.assertEqual(build_snapshot()["historyByBoard"][self.board["slug"]], [])
        self.assertIsNone(build_snapshot()["boards"][0]["health"]["latestAttemptAt"])

    def test_restore_baseline_restores_release_date_evidence_on_fresh_database(self):
        rows = self.rows()
        rows[0]["release_date"] = "2026-01-01"
        save_board_data(self.board, rows, "2026-01-02T00:00:00+00:00")
        snapshot = build_snapshot()
        evidence = "https://example.com/models/model-0#release"
        snapshot["models"]["model-0"]["release_date_source_url"] = evidence
        path = db.DATA_DIR / "date-baseline.json"
        path.write_text(json.dumps(snapshot), encoding="utf-8")
        db.DB_PATH = db.DATA_DIR / "date-fresh.db"
        restore_baseline(path)
        restored = build_snapshot()["models"]["model-0"]
        self.assertEqual(restored["release_date"], "2026-01-01")
        self.assertEqual(restored["release_date_source_url"], evidence)

    def test_restore_baseline_fills_missing_date_from_model_metadata(self):
        save_board_data(self.board, self.rows(1), "2026-01-02T00:00:00+00:00")
        snapshot = build_snapshot()
        snapshot["models"]["model-0"].update(
            release_date="2026-01-01",
            release_date_source_url="https://example.com/release",
        )
        path = db.DATA_DIR / "date-baseline.json"
        path.write_text(json.dumps(snapshot), encoding="utf-8")
        db.DB_PATH = db.DATA_DIR / "metadata-date-fresh.db"
        restore_baseline(path)
        restored = build_snapshot()["models"]["model-0"]
        self.assertEqual(restored["release_date"], "2026-01-01")
        self.assertEqual(restored["release_date_source_url"], "https://example.com/release")

    def test_restore_baseline_does_not_replace_local_date_or_evidence(self):
        rows = self.rows()
        for row in rows:
            row["release_date"] = "2026-01-01"
        save_board_data(self.board, rows, "2026-01-02T00:00:00+00:00")
        snapshot = build_snapshot()
        for model in snapshot["models"].values():
            model["release_date_source_url"] = "https://example.com/baseline"
        path = db.DATA_DIR / "date-baseline.json"
        path.write_text(json.dumps(snapshot), encoding="utf-8")
        with db.get_conn() as conn:
            conn.execute("UPDATE models SET release_date='2026-01-03' WHERE slug='model-0'")
            conn.execute("UPDATE models SET release_date_source_url='https://example.com/local' WHERE slug='model-1'")
        restore_baseline(path)
        restored = build_snapshot()["models"]
        self.assertEqual(restored["model-0"]["release_date"], "2026-01-03")
        self.assertIsNone(restored["model-0"]["release_date_source_url"])
        self.assertEqual(restored["model-1"]["release_date_source_url"], "https://example.com/local")

    def test_restore_baseline_ignores_invalid_release_date_evidence(self):
        cases = [
            ("2026-02-30", "https://example.com/release"),
            ("20260101", "https://example.com/release"),
            ("2026-01-01T00:00:00Z", "https://example.com/release"),
            ("2026-01-01", "javascript:alert(1)"),
            ("2026-01-01", "https:///release"),
            ("2026-01-01", "https://user:password@example.com/release"),
            ("2026-01-01", "https://example.com/bad path"),
            ("2026-01-01", 123),
        ]
        for index, (release_date, source_url) in enumerate(cases):
            with self.subTest(release_date=release_date, source_url=source_url):
                db.DB_PATH = db.DATA_DIR / f"invalid-{index}.db"
                db.init_db()
                rows = self.rows(1)
                rows[0]["release_date"] = release_date
                save_board_data(self.board, rows, "2026-01-02T00:00:00+00:00")
                snapshot = build_snapshot()
                snapshot["models"]["model-0"]["release_date_source_url"] = source_url
                path = db.DATA_DIR / "date-baseline.json"
                path.write_text(json.dumps(snapshot), encoding="utf-8")
                restore_baseline(path)
                self.assertIsNone(build_snapshot()["models"]["model-0"]["release_date_source_url"])

    def test_publish_all_failed_keeps_file_partial_success_keeps_cached_board(self):
        from app.publish import main
        save_board_data(self.board, self.rows(), "2026-01-01T00:00:00+00:00")
        path = db.DATA_DIR / "publish.json"
        original = json.dumps(build_snapshot())
        path.write_text(original, encoding="utf-8")
        db.DB_PATH = db.DATA_DIR / "publish-fresh.db"
        with patch("app.publish.sys.argv", ["publish", str(path)]), patch(
            "app.publish.refresh_all", new=AsyncMock(return_value={self.board["slug"]: {"status": "error"}})
        ):
            main()
        self.assertEqual(path.read_text(encoding="utf-8"), original)

        async def partial():
            mark_board_error(self.board, "sensitive error")
            return {self.board["slug"]: {"status": "error"}, "news": {"status": "ok"}}

        with patch("app.publish.sys.argv", ["publish", str(path)]), patch("app.publish.refresh_all", new=partial):
            main()
        published = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(len(published["entriesByBoard"][self.board["slug"]]), 2)
        self.assertEqual(published["boards"][0]["health"]["latestAttemptStatus"], "error")
        self.assertEqual(len(published["historyByBoard"][self.board["slug"]]), 1)

    def test_release_metadata_uses_successful_version_not_registry(self):
        save_board_data({**self.board, "release": "older-evaluation"}, self.rows(), "2026-01-01T00:00:00+00:00")
        mark_board_error(self.board, "failure")
        snapshot = build_snapshot()
        self.assertEqual(snapshot["boards"][0]["release"], "older-evaluation")
        self.assertEqual(snapshot["boards"][0]["evaluation"]["version"], "older-evaluation")
        self.assertEqual(snapshot["benchmarkMeta"]["livebench"]["release"], "older-evaluation")

    def test_migration_does_not_invent_attempts(self):
        import sqlite3
        db.DB_PATH = db.DATA_DIR / "old.db"
        from contextlib import closing
        with closing(sqlite3.connect(db.DB_PATH)) as conn:
            conn.execute("CREATE TABLE boards(slug TEXT PRIMARY KEY,name TEXT,dimension TEXT,score_type TEXT,url TEXT,last_success_at TEXT,last_status TEXT,last_error TEXT)")
            conn.execute("INSERT INTO boards VALUES('old','Old','overall','elo','https://example.com','2026-01-01', 'ok', NULL)")
            conn.commit()
        db.init_db()
        board = build_snapshot()["boards"][0]
        self.assertIsNone(board["health"]["latestAttemptAt"])
        self.assertIsNone(board["evaluation"]["version"])
        self.assertEqual(build_snapshot()["historyByBoard"]["old"], [])


if __name__ == "__main__":
    unittest.main()
