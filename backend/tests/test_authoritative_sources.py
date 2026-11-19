"""Regression tests for authoritative public leaderboard adapters."""
import csv
import io
import json
import re
import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from app.boards import BOARD_BY_SLUG
from app import db
from app.model_identity import canonical_model_slug
from app.public_sources import (
    parse_bfcl,
    parse_helm_classic,
    parse_terminal_bench_submissions,
)
from app.scraper import fetch_board
from app.snapshot import build_snapshot
from app.store import save_board_data

FIXTURES = Path(__file__).parent / "fixtures"


def load_bfcl_rows():
    with (FIXTURES / "bfcl_overall.csv").open(encoding="utf-8-sig", newline="") as fixture:
        return list(csv.DictReader(fixture))


class BfclParserTests(unittest.TestCase):
    def test_official_fixture_preserves_call_configuration_and_metadata(self):
        rows = parse_bfcl(load_bfcl_rows())

        self.assertEqual(len(rows), 109)
        self.assertEqual(rows[0]["rank"], 1)
        self.assertEqual(rows[0]["score"], 77.47)
        self.assertIn("Claude-Opus-4-5-20251101 (FC)", rows[0]["name"])
        self.assertTrue(rows[0]["name"].endswith("[BFCL]"))
        self.assertEqual(rows[0]["vendor"], "Anthropic")
        self.assertEqual(rows[0]["license"], "Proprietary")
        self.assertEqual(rows[0]["source_url"], "https://www.anthropic.com/news/claude-4")
        self.assertEqual([row["rank"] for row in rows], list(range(1, 110)))
        self.assertTrue(any("(Prompt)" in row["name"] for row in rows))
        self.assertTrue(any("(FC thinking)" in row["name"] for row in rows))

        prompt_row = next(row for row in rows if "Gemini-3-Pro-Preview (Prompt)" in row["name"])
        fc_row = next(row for row in rows if "Gemini-3-Pro-Preview (FC)" in row["name"])
        self.assertEqual(prompt_row["source_url"], "https://deepmind.google/technologies/gemini/pro/")
        self.assertNotEqual(
            canonical_model_slug(prompt_row["name"], prompt_row["slug"]),
            canonical_model_slug(fc_row["name"], fc_row["slug"]),
        )

    def test_rejects_bad_rank_percentage_link_and_duplicate_configuration(self):
        valid = {
            "Rank": "1",
            "Overall Acc": "80.00%",
            "Model": "Example (FC)",
            "Model Link": "https://example.com/model",
            "Organization": "Example",
            "License": "MIT",
        }
        cases = [
            [],
            [{**valid, "Rank": "first"}],
            [{**valid, "Overall Acc": "80"}],
            [{**valid, "Overall Acc": "101%"}],
            [{**valid, "Model Link": "javascript:alert(1)"}],
            [{**valid, "Model Link": "https://user:password@example.com/model"}],
            [valid, dict(valid)],
        ]
        for rows in cases:
            with self.subTest(rows=rows):
                with self.assertRaises(ValueError):
                    parse_bfcl(rows)

    def test_csv_reader_handles_comma_inside_field(self):
        text = (
            "Rank,Overall Acc,Model,Model Link,Organization,License\n"
            '1,84.25%,"Model, Tools (Prompt)",https://example.com/model,"Org, Inc.",Apache 2.0\n'
        )
        rows = parse_bfcl(list(csv.DictReader(io.StringIO(text))))

        self.assertEqual(rows[0]["score"], 84.25)
        self.assertIn("Model, Tools", rows[0]["name"])
        self.assertEqual(rows[0]["vendor"], "Org, Inc.")

    def test_bfcl_fetch_uses_official_csv_adapter(self):
        rows = load_bfcl_rows()
        with patch("app.scraper.fetch_csv", new=AsyncMock(return_value=rows)) as fetch:
            parsed = __import__("asyncio").run(fetch_board(BOARD_BY_SLUG["bfcl-v4"]))

        self.assertEqual(len(parsed), 109)
        self.assertIn("gorilla.cs.berkeley.edu/data_overall.csv", fetch.call_args.args[1])

    def test_bfcl_page_version_and_date_are_explicit(self):
        html = (FIXTURES / "bfcl_leaderboard.html").read_text(encoding="utf-8")
        version = re.search(r"bfcl-eval==([0-9.]+)", html)
        updated = re.search(r"Last Updated:\s*(\d{4}-\d{2}-\d{2})", html, re.I)

        self.assertEqual(version.group(1), "2025.12.17")
        self.assertEqual(updated.group(1), "2026-04-12")


class TerminalBenchParserTests(unittest.TestCase):
    @staticmethod
    def submissions():
        def submission(filename, model, agent, effort, score, ci, vendor, url):
            return {
                "name": filename,
                "payload": {
                    "leaderboard": "4-1-0",
                    "metadata": {
                        "agent_display": {"label": agent},
                        "model_display": {"label": model, "url": url},
                        "model_org": {"label": vendor},
                        "reasoning_effort": effort,
                        "date": "2026-11-17",
                    },
                    "metrics": {
                        "accuracy": score,
                        "accuracy_ci95_half_width": ci,
                    },
                },
            }

        return [
            submission(
                "2026-11-14-4-1-0-fusion-none-copilot-cli.json",
                "Fusion",
                "GitHub Copilot",
                "max",
                71.21,
                2.88,
                "GitHub",
                "https://docs.github.com/en/copilot/concepts/models/copilot-fusion",
            ),
            submission(
                "2026-11-17-4-1-0-gpt-6-2-astra-max-codex.json",
                "GPT-6.2 Astra",
                "Codex",
                "max",
                62.12,
                2.3,
                "OpenAI",
                "https://developers.openai.com/api/docs/models/gpt-6.2-astra",
            ),
            submission(
                "2026-11-17-4-1-0-gpt-6-2-sol-max-codex.json",
                "GPT-6.2 Sol",
                "Codex",
                "max",
                61.52,
                2.75,
                "OpenAI",
                "https://developers.openai.com/api/docs/models/gpt-6.2-sol",
            ),
        ]

    def test_official_submissions_preserve_agent_model_effort_configuration(self):
        rows = parse_terminal_bench_submissions(self.submissions())

        self.assertEqual(len(rows), 3)
        self.assertEqual(rows[0]["rank"], 1)
        self.assertEqual(rows[0]["score"], 71.21)
        self.assertEqual(rows[0]["score_ci"], 2.88)
        self.assertIn("Fusion", rows[0]["name"])
        self.assertIn("GitHub Copilot", rows[0]["name"])
        self.assertIn("推理 max", rows[0]["name"])
        self.assertEqual(rows[0]["vendor"], "GitHub")
        self.assertEqual([row["rank"] for row in rows], [1, 2, 3])
        self.assertEqual(len({row["slug"] for row in rows}), len(rows))

    def test_terminal_bench_rejects_layout_or_identity_changes(self):
        valid = self.submissions()[0]
        invalid_payload = json.loads(json.dumps(valid))
        invalid_payload["payload"]["metadata"].pop("reasoning_effort")
        invalid_version = json.loads(json.dumps(valid))
        invalid_version["payload"]["leaderboard"] = "4-0-0"
        invalid_score = json.loads(json.dumps(valid))
        invalid_score["payload"]["metrics"]["accuracy"] = 101
        invalid_url = json.loads(json.dumps(valid))
        invalid_url["payload"]["metadata"]["model_display"]["url"] = "javascript:alert(1)"
        duplicate = json.loads(json.dumps(valid))
        duplicate["name"] = "2026-11-18-4-1-0-fusion-none-copilot-cli.json"
        duplicate["payload"]["metrics"]["accuracy"] = 72.0
        obsolete = json.loads(json.dumps(valid))
        obsolete["name"] = "2026-11-12-4-1-0-fusion-none-copilot-cli-regrade.json"
        for value in [[], [{}], [invalid_payload], [invalid_version],
                      [invalid_score], [invalid_url]]:
            with self.subTest():
                with self.assertRaises(ValueError):
                    parse_terminal_bench_submissions(value)

        latest = parse_terminal_bench_submissions([obsolete, duplicate])
        self.assertEqual(len(latest), 1)
        self.assertEqual(latest[0]["score"], 72)

    def test_terminal_bench_fetch_uses_official_json_submissions(self):
        entries = [
            {
                "name": "2026-11-14-4-1-0-fusion-none-copilot-cli.json",
                "download_url": (
                    "https://raw.githubusercontent.com/harbor-framework/terminal-bench/"
                    "main/leaderboard/submissions/"
                    "2026-11-14-4-1-0-fusion-none-copilot-cli.json"
                ),
            },
            {
                "name": "README.md",
                "download_url": "https://raw.githubusercontent.com/example/not-a-submission",
            },
        ]
        payload = self.submissions()[0]["payload"]
        with patch(
            "app.scraper.fetch_json",
            new=AsyncMock(side_effect=[entries, payload]),
        ) as fetch:
            parsed = __import__("asyncio").run(
                fetch_board(BOARD_BY_SLUG["terminal-bench-4-1"])
            )

        self.assertEqual(len(parsed), 1)
        called = [call.args[1] for call in fetch.call_args_list]
        self.assertIn("api.github.com/repos/harbor-framework/terminal-bench", called[0])
        self.assertIn("raw.githubusercontent.com/harbor-framework/terminal-bench", called[1])

    def test_new_authoritative_sources_store_and_export_with_license_and_units(self):
        bfcl_rows = parse_bfcl(load_bfcl_rows())
        terminal_rows = parse_terminal_bench_submissions(self.submissions())
        with tempfile.TemporaryDirectory() as tmp:
            original = db.DB_PATH, db.DATA_DIR
            try:
                db.DATA_DIR = Path(tmp)
                db.DB_PATH = db.DATA_DIR / "authoritative.db"
                db.init_db()
                for slug, rows in [("bfcl-v4", bfcl_rows), ("terminal-bench-4-1", terminal_rows)]:
                    self.assertEqual(
                        save_board_data(BOARD_BY_SLUG[slug], rows, "2026-11-19T00:00:00+00:00"),
                        len(rows),
                    )
                snapshot = build_snapshot()
                self.assertTrue(
                    {"bfcl-v4", "terminal-bench-4-1"}.issubset(
                        {board["slug"] for board in snapshot["boards"]}
                    )
                )
                self.assertEqual(
                    {
                        board["source_id"]
                        for board in snapshot["boards"]
                        if board["slug"] in {"bfcl-v4", "terminal-bench-4-1"}
                    },
                    {"bfcl", "terminal_bench"},
                )
                for board in snapshot["boards"]:
                    if board["slug"] in {"bfcl-v4", "terminal-bench-4-1"}:
                        self.assertEqual(board["metric"]["unit"], "%")
                        self.assertTrue(board["metric"]["higherIsBetter"])
                        self.assertTrue(board["source"]["licenseUrl"])
                    self.assertEqual(
                        len(snapshot["entriesByBoard"][board["slug"]]),
                        board["model_count"],
                    )
            finally:
                db.DB_PATH, db.DATA_DIR = original


def helm_fixtures():
    summary = {"release": "v0.4.0", "date": "2023-11-17"}
    schema = {
        "models": [
            {
                "name": "example/alpha",
                "display_name": "Alpha (7B)",
                "creator_organization": "Example Lab",
                "release_date": "2023-01-02",
                "num_parameters": 7_000_000_000,
            },
            {
                "name": "example/beta",
                "display_name": "Beta",
                "creator_organization": "Other Lab",
                "release_date": "2023-02-03",
                "num_parameters": 13_000_000_000,
            },
            {
                "name": "example/ambiguous-a",
                "display_name": "Ambiguous",
                "creator_organization": "Example Lab",
            },
            {
                "name": "example/ambiguous-b",
                "display_name": "Ambiguous",
                "creator_organization": "Other Lab",
            },
        ]
    }
    accuracy = {
        "title": "Accuracy",
        "header": [
            {"value": "Model/adapter"},
            {"value": "Mean win rate"},
            {"value": "MMLU - EM"},
        ],
        "rows": [
            [{"value": "Alpha (7B)"}, {"value": 0.75}, {"value": 0.9}],
            [{"value": "Beta"}, {"value": 0.50}, {"value": 0.8}],
            [{"value": "Ambiguous"}, {"value": 0.99}, {"value": 0.99}],
        ],
    }
    other = {"title": "Calibration", "header": [{"value": "Model/adapter"}], "rows": []}
    return summary, schema, [accuracy, other]


class HelmClassicParserTests(unittest.TestCase):
    def test_official_accuracy_table_uses_percent_units_and_metadata(self):
        summary, schema, tables = helm_fixtures()
        rows = parse_helm_classic(summary, schema, tables)
        self.assertEqual([row["name"] for row in rows], ["Alpha (7B)", "Beta"])
        self.assertEqual([row["rank"] for row in rows], [1, 2])
        self.assertEqual(rows[0]["score"], 75)
        self.assertEqual(rows[0]["vendor"], "Example Lab")
        self.assertEqual(rows[0]["params_b"], 7)
        self.assertEqual(rows[0]["release_date"], "2023-01-02")
        self.assertTrue(all(row["score_ci"] is None for row in rows))
        self.assertTrue(all(row["source_url"].startswith("https://crfm.stanford.edu/") for row in rows))

    def test_ties_share_rank_and_away_rows_are_skipped(self):
        summary, schema, tables = helm_fixtures()
        tables[0]["rows"][1][1]["value"] = 0.75
        rows = parse_helm_classic(summary, schema, tables)
        self.assertEqual([row["rank"] for row in rows], [1, 1])

        tables[0]["rows"].append([{"value": "Alpha (7B)"}, {"value": 0.7}, {"value": 0.8}])
        with self.assertRaises(ValueError):
            parse_helm_classic(summary, schema, tables)

    def test_rejects_changed_release_headers_missing_values_and_bad_metadata(self):
        summary, schema, tables = helm_fixtures()
        mutations = []
        changed_summary = dict(summary)
        changed_summary["release"] = "v0.5.0"
        mutations.append((changed_summary, schema, tables))
        changed_header = json.loads(json.dumps(tables))
        changed_header[0]["header"][1]["value"] = "Accuracy"
        mutations.append((summary, schema, changed_header))
        missing_score = json.loads(json.dumps(tables))
        missing_score[0]["rows"][0][1] = {"description": "No matching runs"}
        mutations.append((summary, schema, missing_score))
        bad_params = json.loads(json.dumps(schema))
        bad_params["models"][0]["num_parameters"] = True
        mutations.append((summary, bad_params, tables))
        no_accuracy = [{"title": "Calibration", "header": [], "rows": []}]
        mutations.append((summary, schema, no_accuracy))
        for value in mutations:
            with self.subTest():
                with self.assertRaises(ValueError):
                    parse_helm_classic(*value)

    def test_helm_fetch_uses_official_release_documents(self):
        summary, schema, tables = helm_fixtures()
        with patch(
            "app.scraper.fetch_json",
            new=AsyncMock(side_effect=[summary, schema, tables]),
        ) as fetch:
            parsed = __import__("asyncio").run(fetch_board(BOARD_BY_SLUG["helm-classic-accuracy"]))

        self.assertEqual(len(parsed), 2)
        called = [call.args[1] for call in fetch.call_args_list]
        self.assertTrue(any(url.endswith("/summary.json") for url in called))
        self.assertTrue(any(url.endswith("/schema.json") for url in called))
        self.assertTrue(any(url.endswith("/groups/core_scenarios.json") for url in called))

    def test_helm_store_and_snapshot_expose_source_license_and_percent(self):
        summary, schema, tables = helm_fixtures()
        rows = parse_helm_classic(summary, schema, tables)
        with tempfile.TemporaryDirectory() as tmp:
            original = db.DB_PATH, db.DATA_DIR
            try:
                db.DATA_DIR = Path(tmp)
                db.DB_PATH = db.DATA_DIR / "helm.db"
                db.init_db()
                self.assertEqual(
                    save_board_data(
                        BOARD_BY_SLUG["helm-classic-accuracy"],
                        rows,
                        "2026-11-19T00:00:00+00:00",
                    ),
                    2,
                )
                snapshot = build_snapshot()
                board = next(
                    item for item in snapshot["boards"]
                    if item["slug"] == "helm-classic-accuracy"
                )
                self.assertEqual(board["source_id"], "helm")
                self.assertEqual(board["metric"]["name"], "HELM Mean win rate")
                self.assertEqual(board["metric"]["unit"], "%")
                self.assertTrue(board["source"]["licenseUrl"])
                self.assertEqual(len(snapshot["entriesByBoard"][board["slug"]]), 2)
            finally:
                db.DB_PATH, db.DATA_DIR = original


if __name__ == "__main__":
    unittest.main()
