import unittest
from unittest.mock import AsyncMock, patch
from app.scraper import fetch_missing_model_dates, parse_release_date


class ReleaseMetadataTests(unittest.IsolatedAsyncioTestCase):
    def test_only_explicit_valid_release_date(self):
        def html(value):
            return '<script type="application/ld+json">' + value + '</script>'
        self.assertEqual(parse_release_date(html('{"@graph":[{"@type":"Product","releaseDate":"2026-09-22"}]}')), '2026-09-22')
        for value in ['{"releaseDate":"2026-02-31"}', '{"created":123,"datePublished":"2026-09-22"}', '{"releaseDate":"2026-09"}', '[{"releaseDate":"2026-09-22"},{"releaseDate":"2026-09-23"}]']:
            self.assertIsNone(parse_release_date(html(value)))

    async def test_vendor_url_does_not_disable_metadata_discovery(self):
        board = {'slug':'overall','path':'/leaderboards/overall','score_type':'aa_index'}
        parsed = [{'name':'Claude Opus 5.5 (max with fallback)', 'slug':'anthropic-claude-opus-5-5', 'source_url':'https://modelsage.cn/model/anthropic-claude-opus-5-5'}]
        with patch('app.boards.BOARDS', [board]), patch('app.scraper.fetch_html', AsyncMock(return_value='table')), patch('app.scraper.parse_board', return_value=parsed), patch('app.scraper.fetch_model_release_dates', AsyncMock(return_value={parsed[0]['source_url']:'2026-09-22'})) as fetch:
            result = await fetch_missing_model_dates([{'slug':'claude-opus-5-5-max','source_url':'https://www.anthropic.com/claude-opus-5-5'}])
        self.assertEqual(result['claude-opus-5-5-max'], {'release_date':'2026-09-22','source_url':parsed[0]['source_url']})
        self.assertEqual(fetch.call_args.args[0], [parsed[0]['source_url']])

    def test_date_evidence_is_persisted_and_exported_without_replacing_model_source(self):
        import tempfile
        from pathlib import Path
        import app.db as db
        from app.store import save_model_dates_by_slug
        from app.snapshot import build_snapshot
        old_path, old_dir = db.DB_PATH, db.DATA_DIR
        try:
            with tempfile.TemporaryDirectory() as directory:
                db.DATA_DIR = Path(directory)
                db.DB_PATH = db.DATA_DIR / 'dates.db'
                db.init_db()
                with db.get_conn() as conn:
                    conn.execute("INSERT INTO models(slug,display_name,source_url,first_seen_at,updated_at) VALUES ('model','Model','https://vendor.example/model','now','now')")
                    conn.execute("INSERT INTO boards(slug,name,dimension,score_type,url) VALUES ('overall','Overall','overall','aa_index','https://modelsage.cn')")
                    conn.execute("INSERT INTO scores(board_slug,model_slug,rank,fetched_at) VALUES ('overall','model',1,'now')")
                evidence = {'model': {'release_date':'2026-09-22', 'source_url':'https://modelsage.cn/model/exact'}}
                self.assertEqual(save_model_dates_by_slug(evidence), 1)
                self.assertEqual(save_model_dates_by_slug(evidence), 0)
                model = build_snapshot()['models']['model']
                self.assertEqual(model['source_url'], 'https://vendor.example/model')
                self.assertEqual(model['release_date'], '2026-09-22')
                self.assertEqual(model['release_date_source_url'], evidence['model']['source_url'])
        finally:
            db.DB_PATH, db.DATA_DIR = old_path, old_dir

    async def test_unmatched_models_do_not_inherit_similar_names(self):
        with patch('app.boards.BOARDS', []), patch('app.scraper.fetch_model_release_dates', AsyncMock(return_value={})):
            self.assertEqual(await fetch_missing_model_dates([{'slug':'unknown-2026-10-01','source_url':'https://openrouter.ai/models'}]), {})
