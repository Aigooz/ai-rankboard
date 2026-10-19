import asyncio
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from app import db
from app.benchmark_references import HLE_REFERENCE, HLE_CANARY
from app.boards import BOARD_BY_SLUG
from app.public_sources import parse_hle_official, parse_hle_diamond
from app.publish import restore_baseline
from app.scraper import fetch_board
from app.snapshot import build_snapshot
from app.store import save_board_data

HISTORICAL = '''<p>Judge Model: o3-mini | Dataset Updated: April 3rd, 2025</p>
<table><thead><tr><th>Model</th><th>Accuracy (%)</th><th>Calibration Error (%)</th></tr></thead><tbody>
<tr><td>Model A</td><td>38.3</td><td>57.2</td></tr>
<tr><td>Model B</td><td>0</td><td>50.0</td></tr>
<tr><td>Text model*</td><td>90.0</td><td>10</td></tr></tbody></table>'''
DIAMOND = '''<figure aria-label="HLE-Diamond: accuracy by model"><ul>
<li><span><img src="/provider-logos/openai_logo.svg"><span>GPT-6</span><span>Astra</span></span><div aria-label="Accuracy: 59.9%"></div></li>
<li><span><span>Model Zero</span></span><div aria-label="Accuracy: 0%"></div></li>
</ul></figure><figure aria-label="HLE-Diamond with tools"><ul><li><span>Wrong tools result</span><div aria-label="Accuracy: 99%"></div></li></ul></figure>'''


class HleTests(unittest.TestCase):
    def test_original_historical_accuracy_and_subset_exclusion(self):
        rows = parse_hle_official(HISTORICAL)
        self.assertEqual([(row['name'], row['score']) for row in rows], [('Model A', 38.3), ('Model B', 0)])
        self.assertIsNone(rows[0]['price_in'])
        self.assertIsNone(rows[0]['score_ci'])  # calibration error is NOT score CI
        for invalid in [HISTORICAL.replace('April 3rd, 2025', 'April 4th, 2026'), HISTORICAL.replace('38.3', '101'), '<table></table>']:
            with self.assertRaises(ValueError):
                parse_hle_official(invalid)

    def test_diamond_only_no_tools_main_figure(self):
        rows = parse_hle_diamond(DIAMOND)
        self.assertEqual(len(rows), 2)
        self.assertEqual(rows[0]['name'], 'GPT-6 Astra')
        self.assertEqual(rows[0]['score'], 59.9)
        self.assertEqual(rows[0]['vendor'], 'OpenAI')
        self.assertEqual(rows[1]['score'], 0)
        self.assertNotIn('Wrong tools result', [row['name'] for row in rows])
        for invalid in [DIAMOND.replace('59.9%', '101%'), DIAMOND.replace('HLE-Diamond: accuracy by model', 'Other chart')]:
            with self.assertRaises(ValueError):
                parse_hle_diamond(invalid)

    def test_reference_only_single_public_question_no_answers(self):
        reference = HLE_REFERENCE
        self.assertEqual(len(reference['examples']), 1)
        self.assertEqual(reference['examples'][0]['category'], 'Ecology')
        self.assertIsNone(reference['examples'][0]['answer'])
        self.assertIn('Hummingbirds within Apodiformes', reference['examples'][0]['question'])
        self.assertIn('Edward V', reference['examples'][0]['author'])
        self.assertEqual(reference['canary'], HLE_CANARY)
        self.assertIn('NEVER APPEAR IN TRAINING CORPORA', reference['trainingWarning'])
        self.assertIn('勿公开分享', reference['licenseNotice'])
        self.assertLess(len(json.dumps(reference)), 6000)

    def test_fetch_routes(self):
        with patch('app.scraper.fetch_html', new=AsyncMock(return_value=HISTORICAL)) as fetch:
            self.assertEqual(len(asyncio.run(fetch_board(BOARD_BY_SLUG['hle-official-2025-04']))), 2)
            self.assertEqual(fetch.call_args.args[1], 'https://lastexam.ai/')
        with patch('app.scraper.fetch_html', new=AsyncMock(return_value=DIAMOND)):
            self.assertEqual(len(asyncio.run(fetch_board(BOARD_BY_SLUG['hle-diamond-no-tools-high']))), 2)

    def test_store_snapshot_and_restore_distinguish_versions(self):
        with tempfile.TemporaryDirectory() as tmp:
            original = db.DB_PATH, db.DATA_DIR
            try:
                db.DATA_DIR = Path(tmp)
                db.DB_PATH = Path(tmp) / 'hle.db'
                db.init_db()
                for slug, rows in [('hle-official-2025-04', parse_hle_official(HISTORICAL)), ('hle-diamond-no-tools-high', parse_hle_diamond(DIAMOND))]:
                    save_board_data(BOARD_BY_SLUG[slug], rows, '2026-10-19T07:00:00+00:00')
                snapshot = build_snapshot()
                self.assertEqual(snapshot['schemaVersion'], 3)
                self.assertEqual(snapshot['benchmarkReferences']['hle']['canary'], HLE_CANARY)
                self.assertEqual({board['source_id'] for board in snapshot['boards']}, {'hle'})
                self.assertEqual({board['evaluation']['version'] for board in snapshot['boards']}, {'HLE dataset 2025-04-03', 'HLE-Diamond 2026-09-22'})
                for board in snapshot['boards']:
                    self.assertEqual(board['metric']['unit'], '%')
                    self.assertTrue(board['metric']['higherIsBetter'])
                    self.assertIsNone(board['evaluation']['upstreamUpdatedAt'])
                path = Path(tmp) / 'snapshot.json'
                path.write_text(json.dumps(snapshot), encoding='utf-8')
                db.DB_PATH = Path(tmp) / 'fresh.db'
                restore_baseline(path)
                self.assertEqual(build_snapshot()['entriesByBoard'], snapshot['entriesByBoard'])
            finally:
                db.DB_PATH, db.DATA_DIR = original


if __name__ == '__main__':
    unittest.main()
