"""Regression tests for authoritative public leaderboard adapters."""
import csv
from datetime import date, timedelta
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
    parse_openrouter_rankings,
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


def openrouter_fixture():
    names = [
        "Alpha", "Beta", "Gamma", "Delta", "Epsilon",
        "Zeta", "Eta", "Theta", "Iota", "Kappa",
    ]
    values = [62.1, 46.6, 16.0, 13.0, 10.9, 8.62, 7.63, 6.39, 4.05, 3.67]
    changes = ["+17%", "new", "-2%", "-36%", "-9%",
               "+20%", "+15%", "-7%", "+25%", "-4%"]
    items = [
        {
            "@type": "ListItem",
            "position": index,
            "name": name,
            "item": f"https://openrouter.ai/example/{name.lower()}",
        }
        for index, name in enumerate(names, 1)
    ]
    documents = [
        {
            "@context": "https://schema.org",
            "@type": "CollectionPage",
            "url": "https://openrouter.ai/rankings",
            "dateModified": "2026-11-18T00:00:00.000Z",
        },
        {
            "@context": "https://schema.org",
            "@type": "ItemList",
            "name": "Top LLMs by weekly token usage on OpenRouter",
            "numberOfItems": 10,
            "itemListOrder": "https://schema.org/ItemListOrderDescending",
            "itemListElement": items,
        },
    ]
    rows = "".join(
        (
            f"<tr><td>{index}</td><th>{name}</th><td>author-{index}</td>"
            f"<td>{value}T tokens</td><td>{change}</td></tr>"
        )
        for index, (name, value, change) in enumerate(
            zip(names, values, changes), 1
        )
    )
    html = (
        "<html><head>"
        + "".join(
            f'<script type="application/ld+json">{json.dumps(item)}</script>'
            for item in documents
        )
        + "</head><body><table><caption>Model rankings by tokens processed</caption>"
        "<thead><tr><th>Rank</th><th>Model</th><th>Author</th>"
        "<th>tokens processed</th><th>Change</th></tr></thead>"
        f"<tbody>{rows}</tbody></table></body></html>"
    )
    return html, names, values


class OpenRouterRankingsTests(unittest.TestCase):
    def setUp(self):
        self.html, self.names, self.values = openrouter_fixture()

    def test_weekly_usage_table_and_json_ld_must_agree(self):
        rows, updated = parse_openrouter_rankings(
            self.html, today=date(2026, 11, 19)
        )

        self.assertEqual(updated, "2026-11-18")
        self.assertEqual([row["name"] for row in rows], self.names)
        self.assertEqual([row["score"] for row in rows], self.values)
        self.assertEqual([row["rank"] for row in rows], list(range(1, 11)))
        self.assertEqual(rows[0]["vendor"], "author-1")
        self.assertEqual(rows[0]["weekly_change"], "+17%")
        self.assertEqual(rows[0]["source_url"], "https://openrouter.ai/example/alpha")
        self.assertIsNone(rows[0]["context_window"])

    def test_rejects_structural_disagreement_and_duplicates(self):
        mutations = [
            self.html.replace(
                '<th>Alpha</th>', '<th>Changed</th>', 1
            ),
            self.html.replace(
                'https://openrouter.ai/example/alpha',
                'https://openrouter.ai/example/beta',
            ),
            self.html.replace(
                '"name": "Gamma"', '"name": "Beta"'
            ),
            self.html.replace(
                "<td>16.0T tokens</td>", "<td>0T tokens</td>"
            ),
            self.html.replace(
                "https://schema.org/ItemListOrderDescending",
                "https://schema.org/ItemListOrderAscending",
            ),
            self.html.replace(
                "Model rankings by tokens processed",
                "Models ranked by tokens processed on OpenRouter over the most recent complete day",
            ),
        ]
        for html in mutations:
            with self.subTest():
                with self.assertRaises(ValueError):
                    parse_openrouter_rankings(html, today=date(2026, 11, 19))

    def test_rejects_stale_future_and_invalid_dates(self):
        for modified in [
            "2026-11-11T00:00:00.000Z",
            "2026-02-30T00:00:00.000Z",
            "2026-10-01T00:00:00.000Z",
        ]:
            html = self.html.replace("2026-11-18T00:00:00.000Z", modified)
            with self.subTest(modified=modified):
                with self.assertRaises(ValueError):
                    parse_openrouter_rankings(html, today=date(2026, 11, 19))
        future = self.html.replace(
            "2026-11-18T00:00:00.000Z", "2026-11-20T00:00:00.000Z"
        )
        with self.assertRaises(ValueError):
            parse_openrouter_rankings(future, today=date(2026, 11, 19))

        within_week = self.html.replace(
            "2026-11-18T00:00:00.000Z", "2026-11-12T00:00:00.000Z"
        )
        rows, updated = parse_openrouter_rankings(
            within_week, today=date(2026, 11, 19), max_age_days=7
        )
        self.assertEqual(len(rows), 10)
        self.assertEqual(updated, "2026-11-12")

    def test_fetch_uses_official_rankings_page_and_keeps_upstream_date(self):
        with patch(
            "app.scraper.fetch_html", new=AsyncMock(return_value=self.html)
        ) as fetch:
            result = __import__("asyncio").run(
                fetch_board(BOARD_BY_SLUG["openrouter-weekly-usage"])
            )

        self.assertEqual(len(result.rows), 10)
        self.assertEqual(result.upstream_updated_at, "2026-11-18")
        self.assertEqual(fetch.call_args.args[1], "https://openrouter.ai/rankings")

    def test_store_snapshot_history_and_baseline_keep_real_upstream_date(self):
        rows, updated = parse_openrouter_rankings(
            self.html, today=date(2026, 11, 19)
        )
        with tempfile.TemporaryDirectory() as tmp:
            original = db.DB_PATH, db.DATA_DIR
            try:
                db.DATA_DIR = Path(tmp)
                db.DB_PATH = db.DATA_DIR / "openrouter.db"
                db.init_db()
                count = save_board_data(
                    BOARD_BY_SLUG["openrouter-weekly-usage"],
                    rows,
                    "2026-11-19T00:00:00+00:00",
                    upstream_updated_at=updated,
                    row_metadata={"weekly_changes": {
                        row["slug"]: row["weekly_change"] for row in rows
                    }},
                )
                self.assertEqual(count, 10)
                snapshot = build_snapshot()
                board = next(
                    item for item in snapshot["boards"]
                    if item["slug"] == "openrouter-weekly-usage"
                )
                self.assertEqual(board["slug"], "openrouter-weekly-usage")
                self.assertEqual(board["evaluation"]["upstreamUpdatedAt"], updated)
                self.assertEqual(board["metric"]["unit"], "万亿 tokens/周")
                self.assertEqual(
                    snapshot["historyByBoard"][board["slug"]][-1]["upstreamUpdatedAt"],
                    updated,
                )
                saved_rows = snapshot["entriesByBoard"][board["slug"]]
                self.assertEqual(saved_rows[0]["weekly_change"], "+17%")

                path = db.DATA_DIR / "baseline.json"
                path.write_text(json.dumps(snapshot), encoding="utf-8")
                db.DB_PATH = db.DATA_DIR / "restored.db"
                from app.publish import restore_baseline
                restore_baseline(path)
                restored = build_snapshot()
                restored_board = next(
                    item for item in restored["boards"]
                    if item["slug"] == "openrouter-weekly-usage"
                )
                self.assertEqual(
                    restored_board["evaluation"]["upstreamUpdatedAt"], updated
                )
                self.assertEqual(
                    len(restored["entriesByBoard"][board["slug"]]), len(saved_rows)
                )
                self.assertIn(
                    "openrouter", {source["id"] for source in restored["sources"]}
                )
            finally:
                db.DB_PATH, db.DATA_DIR = original

    def test_migration_removes_retired_helm_rows_and_orphan_models(self):
        with tempfile.TemporaryDirectory() as tmp:
            original = db.DB_PATH, db.DATA_DIR
            try:
                db.DATA_DIR = Path(tmp)
                db.DB_PATH = db.DATA_DIR / "old.db"
                db.init_db()
                with db.get_conn() as conn:
                    conn.execute(
                        "INSERT INTO boards(slug,name,dimension,score_type,url) "
                        "VALUES('helm-classic-accuracy','HELM Classic','overall',"
                        "'helm_classic','https://crfm.stanford.edu/helm/classic/latest/')"
                    )
                    conn.execute(
                        "INSERT INTO models(slug,display_name,first_seen_at,updated_at) "
                        "VALUES('old-helm-model','Old HELM','2026-01-01','2026-01-01')"
                    )
                    conn.execute(
                        "INSERT INTO scores(board_slug,model_slug,rank,score,fetched_at) "
                        "VALUES('helm-classic-accuracy','old-helm-model',1,50,'2026-01-01')"
                    )
                db.init_db()
                with db.get_conn() as conn:
                    self.assertIsNone(
                        conn.execute(
                            "SELECT 1 FROM boards WHERE slug='helm-classic-accuracy'"
                        ).fetchone()
                    )
                    self.assertIsNone(
                        conn.execute(
                            "SELECT 1 FROM models WHERE slug='old-helm-model'"
                        ).fetchone()
                    )
                boot = build_snapshot()
                self.assertNotIn("helm", {source["id"] for source in boot["sources"]})
                self.assertNotIn(
                    "helm-classic-accuracy",
                    {board["slug"] for board in boot["boards"]},
                )
                self.assertNotIn("helm-classic-accuracy", boot["entriesByBoard"])
            finally:
                db.DB_PATH, db.DATA_DIR = original


if __name__ == "__main__":
    unittest.main()
