"""Tests for the usage trends parser (modelsage.cn/trends)."""

from pathlib import Path

from app.scraper import parse_usage_trend

FIXTURE = Path(__file__).parent / "fixtures" / "modelsage_trends.html"


def test_parses_usage_rows_week_and_totals():
    data = parse_usage_trend(FIXTURE.read_text(encoding="utf-8"))

    assert len(data["entries"]) == 15
    first = data["entries"][0]
    assert first["position"] == 1
    assert first["name"] == "DeepSeek V4.1 Flash (max)"
    assert first["model_url"] == "/model/deepseek-deepseek-v4-1-flash"
    assert first["tokens"] == "19.58 万亿"
    assert first["share"] == 13.4
    assert first["wow"] == "+24.1%"

    linked = {e["name"]: e for e in data["entries"]}
    assert "GLM-5.3-Flash" in linked
    assert linked["GLM-5.3-Flash"]["wow"] == "+16.0%"

    # 未收录模型没有详情页链接，也要能解析出名次和数值
    stealth = linked["space-bunny-alpha"]
    assert stealth["model_url"] is None
    assert stealth["tokens"] == "13.86 万亿"
    assert stealth["share"] == 9.5
    assert stealth["wow"] == "新进榜"

    assert "9月21日" in data["week_label"] and "9月27日" in data["week_label"]
    assert data["total_tokens"] == "145.85 万亿"
    assert data["platform_wow"] == "+13.2%"
