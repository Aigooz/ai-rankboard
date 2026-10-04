"""Tests for the Artificial Analysis articles parser."""

from pathlib import Path

from app.scraper import parse_aa_articles

FIXTURE = Path(__file__).parent / "fixtures" / "aa_articles.html"


def test_parses_article_titles_urls_and_dates():
    articles = parse_aa_articles(FIXTURE.read_text(encoding="utf-8"))

    assert len(articles) >= 10
    first = articles[0]
    assert first["position"] == 1
    assert first["title"] == "Korean AI Lab Upstage releases Solar Mini 4"
    assert first["url"] == "/articles/korean-ai-lab-upstage-releases-solar-mini-4"
    assert first["published_at"] == "2026-09-30"

    second = articles[1]
    assert second["title"] == "Gemini 4 Argon: Google is back as one of the top three labs in intelligence achieved"
    assert second["published_at"] == "2026-09-30"

    # 标题里的 HTML 实体应被还原
    assert all("&amp;" not in a["title"] for a in articles)
    # 顺序按页面出现次序（新文章在前）
    assert [a["position"] for a in articles] == sorted(a["position"] for a in articles)
