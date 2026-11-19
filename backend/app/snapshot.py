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
from .db import get_conn, init_db
from .store import HISTORY_LIMIT, HISTORY_ENTRY_LIMIT
from .trust import health_info, metric_info
from .benchmark_references import HLE_REFERENCE

SCHEMA_VERSION = 3
# 受众以国内为主，时间戳直接使用东八区，便于 App 端直接展示日期。
CST = timezone(timedelta(hours=8))
SOURCE = {
    "id": "modelsage",
    "name": "ModelSage",
    "url": "https://modelsage.cn",
}
SOURCES = [
    {"id": "hle", "name": "CAIS / Scale AI", "url": "https://lastexam.ai/"},
    SOURCE,
    {"id": "livebench", "name": "LiveBench", "url": "https://livebench.ai/"},
    {"id": "swebench", "name": "SWE-bench", "url": "https://www.swebench.com"},
    {"id": "aider", "name": "Aider", "url": "https://aider.chat/docs/leaderboards/",
     "license": "Apache-2.0", "licenseUrl": "https://github.com/Aider-AI/aider/blob/main/LICENSE.txt"},
    {"id": "evalplus", "name": "EvalPlus", "url": "https://evalplus.github.io/leaderboard.html",
     "license": "Apache-2.0", "licenseUrl": "https://github.com/evalplus/evalplus.github.io/blob/master/LICENSE"},
    {"id": "bfcl", "name": "UC Berkeley BFCL", "url": "https://gorilla.cs.berkeley.edu/leaderboard.html",
     "license": "Apache-2.0", "licenseUrl": "https://github.com/ShishirPatil/gorilla/blob/main/LICENSE"},
    {"id": "terminal_bench", "name": "Terminal-Bench",
     "url": "https://hub.harborframework.com/datasets/terminal-bench/terminal-bench/latest"
            "?tab=leaderboard&leaderboard=4-1-0",
     "license": "Apache-2.0", "licenseUrl": "https://github.com/laude-institute/terminal-bench/blob/main/LICENSE"},
    {"id": "openrouter", "name": "OpenRouter", "url": "https://openrouter.ai/rankings"},
]


def build_snapshot() -> dict:
    # Direct CLI exports and tests can use a database created by an older
    # schema. Run the idempotent migrations before querying new columns.
    init_db()
    board_source = {
        "aa_index": "modelsage",
        "arena_elo": "modelsage",
        "livebench": "livebench",
        "livebench_v2": "livebench",
        "swe_bench": "swebench",
        "aider_polyglot": "aider",
        "evalplus": "evalplus",
        "bfcl": "bfcl",
        "terminal_bench": "terminal_bench",
        "openrouter_usage": "openrouter",
    }
    current_slugs = list(BOARD_BY_SLUG)
    placeholders = ",".join("?" for _ in current_slugs)
    with get_conn() as conn:
        rows = conn.execute(
            f"""
            SELECT slug, name, dimension, score_type, url, last_success_at,
                   last_attempt_at, last_status, last_error, evaluation_version,
                   upstream_updated_at, score_metadata,
                   (SELECT COUNT(*) FROM scores s WHERE s.board_slug = b.slug) AS model_count
            FROM boards b
            WHERE b.slug IN ({placeholders})
            ORDER BY b.slug
            """,
            current_slugs,
        ).fetchall()
        boards = [
            {
                **dict(row),
                "source_id": board_source.get(row["score_type"], "modelsage"),
                **{
                    key: BOARD_BY_SLUG[row["slug"]][key]
                    for key in ("kind", "category", "release", "historical")
                    if row["slug"] in BOARD_BY_SLUG and key in BOARD_BY_SLUG[row["slug"]]
                },
            }
            for row in rows
        ]
        history = {board["slug"]: [] for board in boards}
        now = datetime.now(timezone.utc)
        for board in boards:
            observations = conn.execute(
                """SELECT * FROM board_history WHERE board_slug=?
                   ORDER BY fetched_at DESC LIMIT ?""", (board["slug"], HISTORY_LIMIT)
            ).fetchall()
            history[board["slug"]] = [
                {"fetchedAt": row["fetched_at"], "modelCount": row["model_count"],
                 "evaluationVersion": row["evaluation_version"],
                 "upstreamUpdatedAt": row["upstream_updated_at"],
                 "truncated": row["model_count"] > HISTORY_ENTRY_LIMIT,
                 "entries": json.loads(row["entries_json"])[:HISTORY_ENTRY_LIMIT]}
                for row in reversed(observations)
            ]
            board["health"] = health_info(board, history[board["slug"]], now)
            board["metric"] = metric_info(board)
            source = next(
                (item for item in SOURCES if item["id"] == board["source_id"]),
                {"id": board["source_id"], "name": board["source_id"]},
            )
            board["source"] = {**source, "url": board["url"]}
            version = board.pop("evaluation_version")
            upstream_updated_at = board.pop("upstream_updated_at")
            if "release" in board or board.get("kind") == "livebench_v2":
                board["release"] = version
            board["evaluation"] = {"version": version,
                                   "versionSource": ("upstream" if board.get("kind") == "livebench_v2" else "registry") if version else None,
                                   "upstreamUpdatedAt": upstream_updated_at}
            # Old flat fields remain available; never expose raw legacy errors.
            board["last_error"] = board["health"]["error"]

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
                "release_date_source_url": row["release_date_source_url"],
                "updated_at": row["updated_at"],
            }
            for row in conn.execute(
                f"""
                SELECT slug, display_name, vendor, params_b, license, context_window,
                       source_url, release_date, release_date_source_url, updated_at
                FROM models
                WHERE slug IN (
                    SELECT s.model_slug FROM scores s JOIN boards b ON b.slug=s.board_slug
                    WHERE b.slug IN ({placeholders})
                )
                ORDER BY slug
                """,
                current_slugs,
            )
        }

        entries = defaultdict(list)
        metadata_by_board = {
            board["slug"]: json.loads(board.pop("score_metadata") or "{}")
            for board in boards
        }
        query = """
            SELECT s.*, m.display_name, m.slug, m.vendor, m.params_b,
                   m.license, m.context_window, m.release_date
            FROM scores s
            JOIN models m ON m.slug = s.model_slug
            ORDER BY s.board_slug, s.rank
        """
        for row in conn.execute(query):
            item = dict(row)
            if item["board_slug"] not in history:
                continue
            entry = {
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
            weekly_change = metadata_by_board.get(
                item["board_slug"], {}
            ).get("weekly_changes", {}).get(item["slug"])
            if weekly_change is not None:
                entry["weekly_change"] = weekly_change
            entries[item["board_slug"]].append(entry)
        for board in boards:
            entries.setdefault(board["slug"], [])
        for slug in current_slugs:
            entries.setdefault(slug, [])

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
                        "content": row["content"],
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
        "historyByBoard": history,
        "models": models,
        "benchmarkReferences": {"hle": HLE_REFERENCE},
        "benchmarkMeta": {
            "livebench": {
                "release": next((
                    board["release"]
                    for board in boards
                    if board.get("kind") == "livebench_v2"
                ), None),
                "categories": [
                    {
                        "slug": board["slug"],
                        "name": board["name"],
                        "category": board.get("category"),
                    }
                    for board in boards
                    if board.get("kind") == "livebench_v2"
                ],
            },
        },
        "dataStatus": build_data_status(boards),
    }
    if usage:
        snapshot["usageRanking"] = usage
    if news:
        snapshot["news"] = news
    snapshot["boards"] = [
        board for board in boards if board["slug"] in BOARD_BY_SLUG
    ]
    return snapshot


def build_data_status(boards: list[dict]) -> dict:
    sources: dict[str, dict] = {}
    for board in boards:
        source_id = board.get("source_id") or "modelsage"
        source = sources.setdefault(source_id, {
            "sourceId": source_id,
            "boards": 0,
            "failedBoards": 0,
            "staleBoards": 0,
            "boardsWithoutData": 0,
            "lastSuccessAt": None,
            "oldestSuccessAt": None,
        })
        source["boards"] += 1
        health = board.get("health") or {}
        if health.get("latestAttemptStatus") == "error":
            source["failedBoards"] += 1
        if health.get("stale") is True:
            source["staleBoards"] += 1
        if not health.get("lastSuccessAt") or board.get("model_count", 0) <= 0:
            source["boardsWithoutData"] += 1
        success = health.get("lastSuccessAt")
        if success and (
            source["lastSuccessAt"] is None or success > source["lastSuccessAt"]
        ):
            source["lastSuccessAt"] = success
        if success and (
            source["oldestSuccessAt"] is None or success < source["oldestSuccessAt"]
        ):
            source["oldestSuccessAt"] = success
    return {
        "status": "degraded" if any(
            source["failedBoards"] or source["staleBoards"] or source["boardsWithoutData"]
            for source in sources.values()
        ) else "ok",
        "staleAfterHours": 48,
        "sources": [sources[key] for key in sorted(sources)],
    }


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
