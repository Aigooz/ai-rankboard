"""HLE remains reference material only, never an active score leaderboard."""
import json
import tempfile
import unittest
from pathlib import Path

from app import db
from app.benchmark_references import HLE_REFERENCE, HLE_CANARY
from app.boards import BOARDS, BOARD_BY_SLUG
from app.publish import restore_baseline
from app.snapshot import build_snapshot, SOURCES


class HleReferenceTests(unittest.TestCase):
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

    def test_registry_has_26_boards_and_six_reference_sources(self):
        self.assertEqual(len(BOARDS), 26)
        self.assertEqual(len(BOARD_BY_SLUG), 26)
        self.assertNotIn('hle-official-2025-04', BOARD_BY_SLUG)
        self.assertNotIn('hle-diamond-no-tools-high', BOARD_BY_SLUG)
        self.assertFalse(any(board['score_type'].startswith('hle') for board in BOARDS))
        self.assertEqual(len(SOURCES), 6)
        self.assertEqual(next(source['name'] for source in SOURCES if source['id'] == 'hle'), 'CAIS / Scale AI')

    def test_snapshot_omits_retired_scores_from_existing_database_keeps_reference(self):
        with tempfile.TemporaryDirectory() as tmp:
            original = db.DB_PATH, db.DATA_DIR
            try:
                db.DATA_DIR = Path(tmp)
                db.DB_PATH = Path(tmp) / 'old.db'
                db.init_db()
                with db.get_conn() as conn:
                    conn.execute("INSERT INTO boards(slug,name,dimension,score_type,url) VALUES('hle-official-2025-04','Old HLE','reasoning','hle','https://lastexam.ai/')")
                    conn.execute("INSERT INTO models(slug,display_name,first_seen_at,updated_at) VALUES('old-hle-only','Retired model','2026-01-01','2026-01-01')")
                    conn.execute("INSERT INTO scores(board_slug,model_slug,rank,score,fetched_at) VALUES('hle-official-2025-04','old-hle-only',1,20,'2026-01-01')")
                snapshot = build_snapshot()
                self.assertEqual(snapshot['boards'], [])
                self.assertEqual(snapshot['entriesByBoard'], {})
                self.assertEqual(snapshot['historyByBoard'], {})
                self.assertEqual(snapshot['models'], {})
                self.assertEqual(snapshot['benchmarkReferences']['hle']['canary'], HLE_CANARY)
                self.assertEqual(len(snapshot['sources']), 6)
                # Older published snapshots must not resurrect removed HLE boards.
                snapshot['boards'] = [{'slug': 'hle-diamond-no-tools-high', 'last_success_at': '2026-01-01'}]
                snapshot['entriesByBoard'] = {'hle-diamond-no-tools-high': [{'slug': 'old-model'}]}
                path = Path(tmp) / 'old.json'
                path.write_text(json.dumps(snapshot), encoding='utf-8')
                db.DB_PATH = Path(tmp) / 'fresh.db'
                restore_baseline(path)
                self.assertEqual(build_snapshot()['boards'], [])
                self.assertEqual(build_snapshot()['benchmarkReferences']['hle'], HLE_REFERENCE)
            finally:
                db.DB_PATH, db.DATA_DIR = original


if __name__ == '__main__':
    unittest.main()
