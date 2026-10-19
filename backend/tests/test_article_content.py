import asyncio
import json
import tempfile
import unittest
from contextlib import closing
from pathlib import Path
from unittest.mock import patch

from app import db
from app.scraper import parse_aa_article_content, fetch_aa_articles, ARTICLE_CONTENT_LIMIT, ARTICLE_CONTENT_FETCH_LIMIT
from app.snapshot import build_snapshot
from app.store import save_news_articles


class ArticleContentTests(unittest.TestCase):
    def test_prose_plain_text_order_no_page_chrome_or_duplicates(self):
        html = '''<nav>Menu</nav><div class="prose prose-sm"><h1>Title</h1>
        <a>See model page</a><p>First <strong>bold</strong> &amp; safe.</p>
        <h2>Section</h2><ul><li><p>Point <em>one</em></p></li><li>Point two</li></ul>
        <blockquote><p>A quote</p></blockquote><script>secret()</script>
        <p hidden>Hidden</p><iframe>Embedded</iframe><p>Last</p></div>
        <section><h3>Read the latest</h3><p>Related card</p></section><footer>Footer</footer>'''
        content = parse_aa_article_content(html)
        self.assertEqual(content, 'First bold & safe.\n\nSection\n\nPoint one\n\nPoint two\n\nA quote\n\nLast')
        self.assertNotIn('<', content)

    def test_custom_layout_and_fail_closed_unknown_layout(self):
        html = '''<main><section><h1>Title</h1><p>Subtitle</p></section>
        <div class="container pt-12"><div class="grid grid-cols-12"><aside>Contents</aside>
        <div class="col-span-full"><section id="intro"><h2>Intro</h2><p>Real body</p></section>
        </div></div></div></main><footer>Footer</footer>'''
        self.assertEqual(parse_aa_article_content(html), 'Intro\n\nReal body')
        self.assertIsNone(parse_aa_article_content('<main><h1>Title</h1><p>Unknown layout</p></main>'))
        self.assertIsNone(parse_aa_article_content('<div class="prose"><p>No title</p></div>'))
        self.assertIsNone(parse_aa_article_content('<div class="prose"><h1>Empty</h1></div>'))

    def test_bounded_content(self):
        content = parse_aa_article_content('<div class="prose"><h1>Title</h1><p>' + 'word ' * 3000 + '</p></div>')
        self.assertEqual(len(content), ARTICLE_CONTENT_LIMIT)
        self.assertTrue(content.endswith('…'))

    def test_fetch_top_limit_and_independent_failure(self):
        articles = [{"position": i, "title": f"Article {i}", "url": f"/articles/test-{i}", "published_at": None}
                    for i in range(1, 9)]
        calls = []
        async def fake_fetch(client, url):
            calls.append(url)
            if url.endswith('test-2'):
                raise RuntimeError('failed article')
            return '<div class="prose"><h1>Title</h1><p>Real paragraph</p></div>'
        with patch('app.scraper.parse_aa_articles', return_value=articles), patch('app.scraper.fetch_html', new=fake_fetch):
            result = asyncio.run(fetch_aa_articles())
        self.assertEqual(len(calls), ARTICLE_CONTENT_FETCH_LIMIT + 1)
        self.assertEqual(result[0]['content'], 'Real paragraph')
        self.assertIsNone(result[1]['content'])
        self.assertTrue(all(item['content'] is None for item in result[ARTICLE_CONTENT_FETCH_LIMIT:]))

    def test_migrate_store_snapshot_optional_content(self):
        import sqlite3
        with tempfile.TemporaryDirectory() as tmp:
            original = db.DB_PATH, db.DATA_DIR
            try:
                db.DATA_DIR = Path(tmp)
                db.DB_PATH = Path(tmp) / 'news.db'
                with closing(sqlite3.connect(db.DB_PATH)) as conn:
                    conn.execute('CREATE TABLE news_articles(position INTEGER PRIMARY KEY,title TEXT NOT NULL,url TEXT NOT NULL,published_at TEXT,fetched_at TEXT NOT NULL)')
                    conn.execute("INSERT INTO news_articles VALUES(1,'Old','/articles/old',NULL,'2026-01-01')")
                    conn.commit()
                db.init_db()
                self.assertIsNone(build_snapshot()['news']['articles'][0]['content'])
                save_news_articles([
                    {'position': 1, 'title': 'Article', 'url': '/articles/a', 'published_at': '2026-10-16', 'content': 'One\n\nTwo'},
                    {'position': 2, 'title': 'Missing', 'url': '/articles/b', 'published_at': None},
                ], '2026-10-19T05:00:00+00:00')
                snapshot = build_snapshot()
                self.assertEqual(snapshot['schemaVersion'], 3)
                self.assertEqual(snapshot['news']['articles'][0]['content'], 'One\n\nTwo')
                self.assertEqual(snapshot['news']['articles'][0]['published_at'], '2026-10-16')
                self.assertIsNone(snapshot['news']['articles'][1]['content'])
                self.assertLess(len(json.dumps(snapshot['news'])), 1000)
            finally:
                db.DB_PATH, db.DATA_DIR = original


if __name__ == '__main__':
    unittest.main()
