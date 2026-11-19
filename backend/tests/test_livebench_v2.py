import unittest
import asyncio
from datetime import date
from unittest.mock import AsyncMock, patch
from app.boards import BOARD_BY_SLUG
from app.scraper import fetch_board
from app.livebench_v2 import bundle_url, latest_release, upstream_updated_at, parse_board


class LiveBench2Tests(unittest.TestCase):
    def test_legacy_modelsage_fetch_still_dispatches_to_its_own_parser(self):
        with patch("app.scraper.fetch_html", new=AsyncMock(return_value="html")), patch("app.scraper.parse_board", return_value=[{"name": "A"}]) as parse:
            result = asyncio.run(fetch_board(BOARD_BY_SLUG["overall"]))
        self.assertEqual(len(result), 1)
        parse.assert_called_once()

    def test_shared_run_uses_one_release_and_propagates_its_version(self):
        cache = {"release": "20261106", "table": [{"model": "gpt-6.2-astra", "a": "80"}],
                 "categories": {"A": ["a"]}, "costs": [], "updated_at": "2026-11-11"}
        result = asyncio.run(fetch_board({"kind": "livebench_v2", "category": "*"}, livebench_cache=cache))
        self.assertEqual(result.evaluation_version, "LiveBench 2.0 / 2026-11-06")
        self.assertEqual(result.upstream_updated_at, "2026-11-11")
        self.assertEqual(result.rows[0]["score"], 80)

    def test_page_updated_date_is_not_inferred_from_release(self):
        self.assertEqual(upstream_updated_at('const date="11 November 2026"'), "2026-11-11")
        self.assertIsNone(upstream_updated_at('["20261106"]'))
        self.assertIsNone(upstream_updated_at('"11 November 2026";"12 November 2026"'))

    def test_discovery_uses_official_release_list_and_rejects_unknown_layout(self):
        html = '<title>LiveBench 2.0</title><script src="./static/js/main.abc123.js"></script>'
        self.assertEqual(bundle_url(html), "https://livebench.ai/static/js/main.abc123.js")
        self.assertEqual(latest_release(html, 'const a=["20261014","20261106"]', date(2026, 11, 20)), "20261106")
        for bundle in ['const a=[]', '["20261121"]', '["20261106"];["20261014"]']:
            with self.assertRaises(ValueError):
                latest_release(html, bundle, date(2026, 11, 20))
        with self.assertRaises(ValueError):
            bundle_url('<script src="https://evil.test/static/js/main.abc.js"></script>')

    def test_weights_missing_zero_and_exact_model_identity(self):
        meta = {"Agent": ["a"], "Reasoning": ["r"], "_overall_weights": {"Agent": 2}}
        table = [{"model": "gpt-6.2-astra", "a": "60", "r": "90"},
                 {"model": "gpt-6.2-sol", "a": "0", "r": "n/a"},
                 {"model": "empty", "a": "", "r": "-"}]
        rows = parse_board(table, meta, [], "*")
        self.assertEqual([row["score"] for row in rows], [70, 0])
        self.assertEqual(rows[0]["name"], "GPT-6.2 Astra [LiveBench 2.0]")
        self.assertIsNone(rows[0]["price_in"])
        self.assertEqual(parse_board(table, meta, [], "Reasoning")[0]["score"], 90)
        self.assertEqual(len(parse_board(table, meta, [], "Reasoning")), 1)

    def test_invalid_metadata_or_scores_fail_instead_of_publishing_partial_data(self):
        for table, meta in [
            ([{"model": "a", "x": "101"}], {"X": ["x"]}),
            ([{"model": "a", "x": "inf"}], {"X": ["x"]}),
            ([{"model": "a", "y": "2"}], {"X": ["x"]}),
            ([{"model": "a", "x": "20"}], {"X": ["x"], "_overall_weights": {"X": -1}}),
            ([{"model": "a", "x": "20"}] * 2, {"X": ["x"]}),
        ]:
            with self.subTest(table=table, meta=meta), self.assertRaises(ValueError):
                parse_board(table, meta, [], "*")
