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
        board = next(item for item in snapshot["boards"] if item["slug"] == self.board["slug"])
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
        board = next(
            item for item in build_snapshot()["boards"]
            if item["slug"] == self.board["slug"]
        )
        self.assertEqual(board["health"]["latestAttemptStatus"], "error")

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

    def test_tied_ranks_keep_each_evaluation_configuration(self):
        self.board = BOARD_BY_SLUG["bfcl-v4"]
        rows = self.rows(3)
        for index, row in enumerate(rows):
            row["rank"] = 2
            row["name"] = f"Model {index} [Config {index}]"
        count = save_board_data(self.board, rows, "2026-01-01T00:00:00+00:00")
        entries = build_snapshot()["entriesByBoard"][self.board["slug"]]

        self.assertEqual(count, 3)
        self.assertEqual([entry["rank"] for entry in entries], [2, 2, 2])

    def test_model_display_name_keeps_more_descriptive_existing_name(self):
        first_board = BOARD_BY_SLUG["aider-polyglot"]
        second_board = BOARD_BY_SLUG["evalplus-humaneval-plus"]
        save_board_data(
            first_board,
            [{
                **self.rows(1)[0],
                "name": "Shared Model",
            }],
            "2026-01-01T00:00:00+00:00",
        )
        save_board_data(
            second_board,
            [{
                **self.rows(1)[0],
                "name": "shared-model",
            }],
            "2026-01-02T00:00:00+00:00",
        )

        with db.get_conn() as conn:
            display_name = conn.execute(
                "SELECT display_name FROM models WHERE slug='shared-model'"
            ).fetchone()["display_name"]

        self.assertEqual(display_name, "Shared Model")

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
        baseline_board = next(
            item for item in snapshot["boards"] if item["slug"] == self.board["slug"]
        )
        baseline_board.pop("health")
        baseline_board.pop("last_attempt_at")
        path.write_text(json.dumps(snapshot), encoding="utf-8")
        db.DB_PATH = db.DATA_DIR / "legacy.db"
        restore_baseline(path)
        self.assertEqual(build_snapshot()["historyByBoard"][self.board["slug"]], [])
        restored_board = next(
            item for item in build_snapshot()["boards"]
            if item["slug"] == self.board["slug"]
        )
        self.assertIsNone(restored_board["health"]["latestAttemptAt"])

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
        from app.publish import publish
        from app.boards import BOARDS
        baseline_time = "2026-01-01T00:00:00+00:00"
        for board in BOARDS:
            save_board_data(board, self.rows(), baseline_time)
        path = db.DATA_DIR / "publish.json"
        original = json.dumps(build_snapshot())
        path.write_text(original, encoding="utf-8")
        db.DB_PATH = db.DATA_DIR / "publish-fresh.db"
        with patch(
            "app.publish.refresh_all",
            new=AsyncMock(return_value={self.board["slug"]: {"status": "error"}}),
        ):
            result = __import__("app.publish", fromlist=["publish"]).publish(path)
        self.assertFalse(result["published"])
        self.assertEqual(result["reason"], "all_sources_failed")
        self.assertEqual(path.read_text(encoding="utf-8"), original)

        async def partial():
            mark_board_error(self.board, "sensitive error")
            save_board_data(BOARD_BY_SLUG["livebench-reasoning"], self.rows(3), datetime.now(timezone.utc).isoformat())
            return {
                self.board["slug"]: {"status": "error"},
                "livebench-reasoning": {"status": "ok"},
                "news": {"status": "ok"},
            }

        with patch("app.publish.refresh_all", new=partial):
            result = publish(path)
        self.assertTrue(result["published"])
        updated = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(updated["dataStatus"]["status"], "degraded")
        self.assertEqual(len(updated["entriesByBoard"]["livebench-reasoning"]), 3)
        cached = next(board for board in updated["boards"] if board["slug"] == self.board["slug"])
        self.assertEqual(cached["health"]["lastSuccessAt"], baseline_time)
        self.assertEqual(cached["evaluation"]["version"], self.board["release"])
        self.assertEqual(len(updated["entriesByBoard"][self.board["slug"]]), 2)
        self.assertEqual(updated["entriesByBoard"][self.board["slug"]][0]["fetched_at"], baseline_time)
        self.assertEqual(result["health"]["failedBoards"], [self.board["slug"]])

    def test_restore_optional_baseline_preserves_original_timestamps_and_local_values(self):
        from app.store import save_usage_trend, save_news_articles
        usage = {"entries": [{"position": 1, "name": "Model", "tokens": "1T", "share": 10, "wow": None}],
                 "week_label": "old week"}
        save_usage_trend(usage, "2026-01-01T00:00:00Z")
        save_news_articles([{"position": 1, "title": "News", "url": "/articles/news"}], "2026-01-02T00:00:00Z")
        path = db.DATA_DIR / "optional.json"
        previous = build_snapshot()
        path.write_text(json.dumps(previous), encoding="utf-8")
        db.DB_PATH = db.DATA_DIR / "optional-fresh.db"
        restore_baseline(path)
        restored = build_snapshot()
        self.assertEqual(restored["usageRanking"], previous["usageRanking"])
        self.assertEqual(restored["news"], previous["news"])
        save_usage_trend({**usage, "week_label": "local week"}, "2026-01-03T00:00:00Z")
        restore_baseline(path)
        self.assertEqual(build_snapshot()["usageRanking"]["week_label"], "local week")
        self.assertEqual(build_snapshot()["news"]["generated_at"], "2026-01-02T00:00:00Z")

    def test_usage_and_news_success_do_not_count_as_leaderboard_success(self):
        from app.publish import publish

        path = db.DATA_DIR / "non-board-results.json"
        previous = json.dumps({"schemaVersion": 3, "boards": [], "entriesByBoard": {}})
        path.write_text(previous, encoding="utf-8")
        with patch(
            "app.publish.refresh_all",
            new=AsyncMock(return_value={
                "usage": {"status": "ok"},
                "news": {"status": "ok"},
            }),
        ):
            result = publish(path)

        self.assertFalse(result["published"])
        self.assertEqual(result["reason"], "all_sources_failed")
        self.assertEqual(path.read_text(encoding="utf-8"), previous)

    def test_release_metadata_uses_successful_version_not_registry(self):
        save_board_data({**self.board, "release": "older-evaluation"}, self.rows(), "2026-01-01T00:00:00+00:00")
        mark_board_error(self.board, "failure")
        snapshot = build_snapshot()
        board = next(item for item in snapshot["boards"] if item["slug"] == self.board["slug"])
        self.assertEqual(board["release"], "older-evaluation")
        self.assertEqual(board["evaluation"]["version"], "older-evaluation")
        self.assertEqual(board["evaluation"]["version"], "older-evaluation")

    def test_dynamic_release_is_persisted_and_kept_when_next_fetch_fails(self):
        import asyncio
        from app.scraper import BoardFetchResult
        board = BOARD_BY_SLUG["livebench-v2-overall"]
        result = BoardFetchResult(self.rows(), upstream_updated_at="2026-11-11",
                                  evaluation_version="LiveBench 2.0 / 2026-11-06")
        with patch("app.service.fetch_board", new=AsyncMock(return_value=result)):
            asyncio.run(refresh_board(board))
        mark_board_error(board, "failed")
        exported = next(item for item in build_snapshot()["boards"] if item["slug"] == board["slug"])
        self.assertEqual(exported["evaluation"]["version"], "LiveBench 2.0 / 2026-11-06")
        self.assertEqual(exported["evaluation"]["versionSource"], "upstream")
        self.assertEqual(exported["evaluation"]["upstreamUpdatedAt"], "2026-11-11")
        self.assertEqual(exported["health"]["latestAttemptStatus"], "error")

    def test_publish_freshness_budget_matches_client_stale_threshold(self):
        from app.publish import SNAPSHOT_MAX_AGE_HOURS, snapshot_health

        self.assertEqual(SNAPSHOT_MAX_AGE_HOURS, 48)
        save_board_data(self.board, self.rows(), "2026-01-01T00:00:00+00:00")
        health = snapshot_health(Path("unused"))
        self.assertIn("ageHours", health["sources"][0])
        self.assertEqual(health["maxAgeHours"], 48)

    def test_source_freshness_uses_oldest_current_board_success(self):
        from app.publish import snapshot_health

        newer_board = BOARD_BY_SLUG["livebench-reasoning"]
        save_board_data(self.board, self.rows(), "2026-01-01T00:00:00+00:00")
        save_board_data(newer_board, self.rows(), "2026-01-02T00:00:00+00:00")

        health = snapshot_health(Path("unused"))
        source = next(item for item in health["sources"] if item["sourceId"] == "livebench")

        self.assertEqual(source["lastSuccessAt"], "2026-01-02T00:00:00+00:00")
        self.assertEqual(source["oldestSuccessAt"], "2026-01-01T00:00:00+00:00")
        self.assertGreater(source["ageHours"], 48)

    def test_retired_board_rows_do_not_block_current_snapshot_health(self):
        from app.publish import snapshot_health

        save_board_data(self.board, self.rows(), "2026-01-01T00:00:00+00:00")
        with db.get_conn() as conn:
            conn.execute(
                """INSERT INTO boards
                   (slug,name,dimension,score_type,url,last_success_at)
                   VALUES (?,?,?,?,?,?)""",
                (
                    "hle-official-retired",
                    "Retired HLE",
                    "reasoning",
                    "hle",
                    "https://example.com/retired",
                    "2020-01-01T00:00:00+00:00",
                ),
            )
        health = snapshot_health(Path("unused"))

        self.assertNotIn("hle", {source["sourceId"] for source in health["sources"]})
        self.assertNotIn("hle-official-retired", health["failedBoards"])

    def test_empty_current_board_blocks_publication_but_cached_failures_are_diagnostic(self):
        from app.publish import snapshot_health

        now = datetime.now(timezone.utc).isoformat(timespec="seconds")
        save_board_data(self.board, self.rows(), now)
        with patch(
            "app.publish.build_snapshot",
            return_value={
                "boards": [
                    {
                        "slug": "current",
                        "source_id": "source",
                        "model_count": 1,
                        "health": {"latestAttemptStatus": "error"},
                    },
                    {
                        "slug": "empty",
                        "source_id": "source",
                        "model_count": 0,
                        "health": {"latestAttemptStatus": "ok"},
                    },
                ],
                "dataStatus": {
                    "sources": [{
                        "sourceId": "source",
                        "lastSuccessAt": now,
                        "oldestSuccessAt": now,
                    }]
                },
            },
        ):
            health = snapshot_health(Path("unused"))
        self.assertEqual(health["failedBoards"], ["current"])
        self.assertEqual(health["boardsWithoutData"], ["empty"])
        self.assertEqual(health["staleSources"], [])

        with (
            patch(
                "app.publish.build_snapshot",
                return_value={
                    "boards": [
                        {
                            "slug": "current",
                            "source_id": "source",
                            "model_count": 1,
                            "health": {"latestAttemptStatus": "error"},
                        },
                        {
                            "slug": "empty",
                            "source_id": "source",
                            "model_count": 0,
                            "health": {"latestAttemptStatus": "ok"},
                        },
                    ],
                    "dataStatus": {
                        "sources": [{
                            "sourceId": "source",
                            "lastSuccessAt": now,
                            "oldestSuccessAt": now,
                        }]
                    },
                },
            ),
            patch("app.publish.write_snapshot") as write_snapshot,
            patch(
                "app.publish.refresh_all",
                new=AsyncMock(return_value={
                    "livebench-overall": {"status": "ok", "models": 1},
                    "livebench-reasoning": {"status": "error"},
                }),
            ),
            patch("app.publish.restore_baseline"),
        ):
            result = __import__("app.publish", fromlist=["publish"]).publish(Path("unused"))
        self.assertFalse(result["published"])
        self.assertEqual(result["reason"], "incomplete_snapshot")
        self.assertEqual(result["health"]["failedBoards"], ["current"])
        self.assertEqual(result["integrityErrors"]["boardsWithoutData"], ["empty"])
        write_snapshot.assert_not_called()

    def test_migration_does_not_invent_attempts(self):
        import sqlite3
        db.DB_PATH = db.DATA_DIR / "old.db"
        from contextlib import closing
        with closing(sqlite3.connect(db.DB_PATH)) as conn:
            conn.execute("CREATE TABLE boards(slug TEXT PRIMARY KEY,name TEXT,dimension TEXT,score_type TEXT,url TEXT,last_success_at TEXT,last_status TEXT,last_error TEXT)")
            conn.execute("INSERT INTO boards VALUES('old','Old','overall','elo','https://example.com','2026-01-01', 'ok', NULL)")
            conn.commit()
        db.init_db()
        board = next(item for item in build_snapshot()["boards"] if item["slug"] == "overall")
        self.assertIsNone(board["health"]["latestAttemptAt"])
        self.assertIsNone(board["evaluation"]["version"])
        self.assertEqual(build_snapshot()["historyByBoard"]["overall"], [])


if __name__ == "__main__":
    unittest.main()
