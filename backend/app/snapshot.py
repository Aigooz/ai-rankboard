"""Export a portable leaderboard snapshot for the Android app.

The snapshot is intentionally static: it can be bundled as the initial state and
later hosted on any static file service for background updates.
"""

import argparse
import hashlib
import json
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

from .boards import BOARD_BY_SLUG, LIVEBENCH
from .db import get_conn

SCHEMA_VERSION = 3
# 受众以国内为主，时间戳直接使用东八区，便于 App 端直接展示日期。
CST = timezone(timedelta(hours=8))
SOURCE = {
    "id": "modelsage",
    "name": "ModelSage",
    "url": "https://modelsage.cn",
}
SOURCES = [
    SOURCE,
    {"id": "livebench", "name": "LiveBench", "url": "https://livebench.ai/leaderboard"},
    {"id": "swebench", "name": "SWE-bench", "url": "https://www.swebench.com"},
]


def build_snapshot() -> dict:
    board_source = {
        "aa_index": "modelsage",
        "arena_elo": "modelsage",
        "livebench": "livebench",
        "swe_bench": "swebench",
    }
    with get_conn() as conn:
        boards = [
            {
                **dict(row),
                "source_id": board_source.get(row["score_type"], "modelsage"),
                **{
                    key: BOARD_BY_SLUG[row["slug"]][key]
                    for key in ("kind", "category", "release")
                    if row["slug"] in BOARD_BY_SLUG and key in BOARD_BY_SLUG[row["slug"]]
                },
            }
            for row in conn.execute(
                """
                SELECT slug, name, dimension, score_type, url, last_success_at,
                       (SELECT COUNT(*) FROM scores s WHERE s.board_slug = b.slug) AS model_count
                FROM boards b
                ORDER BY b.slug
                """
            )
        ]

        models = {
            row["slug"]: {
                "slug": row["slug"],
                "display_name": row["display_name"],
                "vendor": row["vendor"],
                "params_b": row["params_b"],
                "license": row["license"],
                "context_window": row["context_window"],
                "source_url": row["source_url"],
                "release_date": row["release_date"],
                "updated_at": row["updated_at"],
            }
            for row in conn.execute(
                """
                SELECT slug, display_name, vendor, params_b, license, context_window,
                       source_url, release_date, updated_at
                FROM models
                ORDER BY slug
                """
            )
        }

        entries = defaultdict(list)
        query = """
            SELECT s.*, m.display_name, m.slug, m.vendor, m.params_b,
                   m.license, m.context_window, m.release_date
            FROM scores s
            JOIN models m ON m.slug = s.model_slug
            ORDER BY s.board_slug, s.rank
        """
        for row in conn.execute(query):
            item = dict(row)
            entries[item["board_slug"]].append(
                {
                    "slug": item["slug"],
                    "display_name": item["display_name"],
                    "vendor": item["vendor"],
                    "params_b": item["params_b"],
                    "license": item["license"],
                    "context_window": item["context_window"],
                    "release_date": item["release_date"],
                    "rank": item["rank"],
                    "score": item["score"],
                    "score_ci": item["score_ci"],
                    "votes": item["votes"],
                    "price_in": item["price_in"],
                    "price_out": item["price_out"],
                    "currency": item["currency"],
                    "fetched_at": item["fetched_at"],
                }
            )

        usage_rows = conn.execute("SELECT * FROM usage_ranking ORDER BY position").fetchall()
        usage = None
        if usage_rows:
            first = usage_rows[0]
            usage = {
                "week_label": first["week_label"],
                "total_tokens": first["total_tokens"],
                "platform_wow": first["platform_wow"],
                "generated_at": first["fetched_at"],
                "entries": [
                    {
                        "position": row["position"],
                        "name": row["name"],
                        "model_url": row["model_url"],
                        "tokens": row["tokens"],
                        "share": row["share"],
                        "wow": row["wow"],
                    }
                    for row in usage_rows
                ],
            }

        news_rows = conn.execute("SELECT * FROM news_articles ORDER BY position").fetchall()
        news = None
        if news_rows:
            news = {
                "generated_at": news_rows[0]["fetched_at"],
                "articles": [
                    {
                        "title": row["title"],
                        "url": row["url"],
                        "published_at": row["published_at"],
                    }
                    for row in news_rows
                ],
            }

    snapshot = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": datetime.now(CST).isoformat(timespec="seconds"),
        "source": SOURCE,
        "sources": SOURCES,
        "boards": boards,
        "entriesByBoard": dict(entries),
        "models": models,
        "benchmarkMeta": {
            "livebench": {
                "release": next(
                    (board.get("release") for board in boards if board.get("kind") == LIVEBENCH),
                    None,
                ),
                "categories": [
                    {
                        "slug": board["slug"],
                        "name": board["name"],
                        "category": board.get("category"),
                    }
                    for board in boards
                    if board.get("kind") == LIVEBENCH
                ],
            },
        },
    }
    if usage:
        snapshot["usageRanking"] = usage
    if news:
        snapshot["news"] = news
    return snapshot


def write_snapshot(path: Path) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = json.dumps(build_snapshot(), ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    path.write_bytes(data)
    digest = hashlib.sha256(data).hexdigest()
    path.with_suffix(path.suffix + ".sha256").write_text(digest + "  " + path.name + "\n", encoding="ascii")
    return digest


def main() -> None:
    parser = argparse.ArgumentParser(description="Export a static Android snapshot")
    parser.add_argument("output", type=Path, help="output JSON path")
    args = parser.parse_args()
    digest = write_snapshot(args.output)
    print(f"snapshot written: {args.output}")
    print(f"sha256: {digest}")


if __name__ == "__main__":
    main()
