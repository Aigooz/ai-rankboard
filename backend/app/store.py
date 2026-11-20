"""Persistence: upsert parsed rows into SQLite."""

import json
import re

from .db import get_conn, now_iso
from .model_identity import canonical_model_slug
from .scraper import _PARAMS_RE, BoardFetchResult
from .config import LIVEBENCH_URL, SWE_BENCH_URL

HISTORY_LIMIT = 12
HISTORY_ENTRY_LIMIT = 100
CODE_LIKE_NAME = re.compile(r"^[a-z0-9._-]+$", re.I)
LIVEBENCH_VERSION_SUFFIX = re.compile(r"\s*\[LiveBench 2\.0\]\s*$")


def safe_fetch_error(error: str) -> str:
    """Do not publish URLs, local paths, or arbitrary upstream exception bodies."""
    # Exceptions may contain credentials or response bodies; publish only a generic
    # diagnostic, preserving detailed exceptions exclusively in server-side logs.
    return "抓取失败；保留上次成功数据。请检查来源可用性或服务端日志。"


def extract_params_b(name: str):
    m = _PARAMS_RE.search(name)
    return float(m.group(1)) if m else None


def clean_model_name(name: str) -> str:
    """Remove the retired duplicated version tag from persisted model names."""
    return LIVEBENCH_VERSION_SUFFIX.sub("", name or "").strip()


def choose_display_name(existing: str, incoming: str) -> str:
    """Keep a more informative name when a later source exposes only a code id."""
    existing = clean_model_name(existing)
    incoming = clean_model_name(incoming)
    if not existing:
        return incoming
    if not incoming:
        return existing
    if CODE_LIKE_NAME.fullmatch(incoming) and not CODE_LIKE_NAME.fullmatch(existing):
        return existing
    if len(existing) > len(incoming) and not CODE_LIKE_NAME.fullmatch(existing):
        return existing
    return incoming


def upsert_board_meta(conn, board: dict) -> None:
    if board.get("url"):
        url = board["url"]
    elif board.get("kind") == "livebench":
        url = f"{LIVEBENCH_URL}/"
    elif board.get("kind") == "swe_bench":
        url = SWE_BENCH_URL
    elif board.get("kind") == "bfcl":
        url = "https://gorilla.cs.berkeley.edu/leaderboard.html"
    elif board.get("kind") == "terminal_bench":
        url = (
            "https://hub.harborframework.com/datasets/terminal-bench/terminal-bench/latest"
            "?tab=leaderboard&leaderboard=4-1-0"
        )
    else:
        url = "https://modelsage.cn" + board["path"]
    conn.execute(
        """INSERT INTO boards(slug, name, dimension, score_type, url)
           VALUES (?, ?, ?, ?, ?)
           ON CONFLICT(slug) DO UPDATE SET name=excluded.name, dimension=excluded.dimension,
               score_type=excluded.score_type, url=excluded.url""",
        (board["slug"], board["name"], board["dimension"], board["score_type"], url),
    )


def save_board_data(
    board: dict,
    rows: list | BoardFetchResult,
    fetched_at: str,
    upstream_updated_at: str | None = None,
    row_metadata: dict | None = None,
) -> int:
    if isinstance(rows, BoardFetchResult):
        if row_metadata is None:
            row_metadata = rows.metadata
        if upstream_updated_at is None:
            upstream_updated_at = rows.upstream_updated_at
        rows = rows.rows
    if not rows:
        raise ValueError(f"board {board['slug']}: parsed 0 rows, refusing to wipe existing data")

    for row in rows:
        row["name"] = clean_model_name(row["name"])

    # 名次可以不唯一；同一模型+配置=同一行。这样既保留官方并列，
    # 又能折叠同一配置的重复记录（例如站点改名带来的两代残留）。
    seen: set[str] = set()
    by_model: dict[str, dict] = {}
    for row in rows:
        model_slug = canonical_model_slug(row["name"], row["slug"])
        if model_slug in seen:
            current = by_model[model_slug]
            if len(row["name"] or "") > len(current["name"] or ""):
                by_model[model_slug] = row
            continue
        seen.add(model_slug)
        by_model[model_slug] = row

    deduped = []
    for model_slug in sorted(
        by_model,
        key=lambda slug: (by_model[slug]["rank"], by_model[slug]["name"].casefold()),
    ):
        row = by_model[model_slug]
        deduped.append((model_slug, row))

    with get_conn() as conn:
        upsert_board_meta(conn, board)
        # Capture only observed successful fetches, never backfill invented history.
        weekly_changes = dict((row_metadata or {}).get("weekly_changes", {}))
        history_entries = []
        for slug, row in deduped[:HISTORY_ENTRY_LIMIT]:
            entry = {
                "slug": slug,
                "display_name": row["name"],
                "rank": row["rank"],
                "score": row["score"],
            }
            change = row.get("weekly_change") or weekly_changes.get(row["slug"])
            if change is not None:
                entry["weekly_change"] = change
                weekly_changes[slug] = change
            history_entries.append(entry)
        conn.execute(
            "UPDATE boards SET score_metadata=? WHERE slug=?",
            (
                json.dumps(
                    {"weekly_changes": weekly_changes},
                    ensure_ascii=False,
                    allow_nan=False,
                ),
                board["slug"],
            ),
        )
        conn.execute(
            """INSERT OR REPLACE INTO board_history
               (board_slug, fetched_at, model_count, evaluation_version,
                upstream_updated_at, entries_json)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (board["slug"], fetched_at, len(deduped), board.get("release"),
             upstream_updated_at,
             json.dumps(history_entries, ensure_ascii=False, allow_nan=False)),
        )
        conn.execute(
            """DELETE FROM board_history WHERE board_slug=? AND fetched_at NOT IN
               (SELECT fetched_at FROM board_history WHERE board_slug=?
                ORDER BY fetched_at DESC LIMIT ?)""",
            (board["slug"], board["slug"], HISTORY_LIMIT),
        )
        # 榜单内容以本次抓取为准：先清掉该榜旧成绩再写入，避免站点改了命名后
        # 新旧两代 slug 并存出现"模型分身"。抓取失败不会走到这里，旧数据得以保留。
        conn.execute("DELETE FROM scores WHERE board_slug=?", (board["slug"],))
        for model_slug, row in deduped:
            existing = conn.execute(
                "SELECT display_name FROM models WHERE slug=?", (model_slug,)
            ).fetchone()
            existing_name = existing["display_name"] if existing else ""
            display_name = choose_display_name(existing_name, row["name"])
            conn.execute(
                """INSERT INTO models(slug, display_name, vendor, params_b, license, context_window, source_url, release_date, first_seen_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(slug) DO UPDATE SET
                       display_name=excluded.display_name,
                       vendor=COALESCE(excluded.vendor, models.vendor),
                       params_b=COALESCE(models.params_b, excluded.params_b),
                       license=COALESCE(excluded.license, models.license),
                       context_window=COALESCE(excluded.context_window, models.context_window),
                       source_url=COALESCE(excluded.source_url, models.source_url),
                       release_date=COALESCE(models.release_date, excluded.release_date),
                       updated_at=excluded.updated_at""",
                (model_slug, display_name, row["vendor"], extract_params_b(row["name"]),
                 row["license"], row["context_window"], row["source_url"], row.get("release_date"),
                 fetched_at, fetched_at),
            )
            conn.execute(
                """INSERT INTO scores(board_slug, model_slug, rank, score, score_ci, votes, price_in, price_out, currency, fetched_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(board_slug, model_slug) DO UPDATE SET
                       rank=excluded.rank, score=excluded.score, score_ci=excluded.score_ci,
                       votes=excluded.votes, price_in=excluded.price_in, price_out=excluded.price_out,
                       currency=excluded.currency, fetched_at=excluded.fetched_at""",
                (board["slug"], model_slug, row["rank"], row["score"], row["score_ci"],
                 row["votes"], row["price_in"], row["price_out"], row["currency"], fetched_at),
            )
        # 清理已经不在任何榜单里的孤儿模型。
        conn.execute("DELETE FROM models WHERE slug NOT IN (SELECT DISTINCT model_slug FROM scores)")
        conn.execute(
            """UPDATE boards SET last_success_at=?, last_attempt_at=?, last_status='ok',
               last_error=NULL, evaluation_version=?, upstream_updated_at=? WHERE slug=?""",
            (fetched_at, fetched_at, board.get("release"), upstream_updated_at, board["slug"]),
        )
    return len(deduped)


def save_model_dates_by_slug(dates: dict[str, dict[str, str]]) -> int:
    changed = 0
    with get_conn() as conn:
        for slug, evidence in dates.items():
            cursor = conn.execute(
                "UPDATE models SET release_date=?, release_date_source_url=? "
                "WHERE slug=? AND (release_date IS NULL OR release_date='')",
                (evidence["release_date"], evidence["source_url"], slug),
            )
            changed += cursor.rowcount
    return changed


def save_model_release_dates(dates: dict[str, str]) -> int:
    if not dates:
        return 0
    with get_conn() as conn:
        for source_url, release_date in dates.items():
            conn.execute(
                "UPDATE models SET release_date=? WHERE source_url=? AND (release_date IS NULL OR release_date='')",
                (release_date, source_url),
            )
    return len(dates)


def save_usage_trend(data: dict, fetched_at: str) -> int:
    with get_conn() as conn:
        conn.execute("DELETE FROM usage_ranking")
        for entry in data["entries"]:
            conn.execute(
                """INSERT INTO usage_ranking(position, name, model_url, tokens, share, wow,
                                             week_label, total_tokens, platform_wow, fetched_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (entry["position"], entry["name"], entry.get("model_url"), entry["tokens"],
                 entry["share"], entry["wow"], data.get("week_label", ""),
                 data.get("total_tokens", ""), data.get("platform_wow", ""), fetched_at),
            )
    return len(data["entries"])


def save_news_articles(articles: list[dict], fetched_at: str) -> int:
    with get_conn() as conn:
        conn.execute("DELETE FROM news_articles")
        for article in articles:
            conn.execute(
                """INSERT INTO news_articles(position, title, url, published_at, content, fetched_at)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (article["position"], article["title"], article["url"],
                 article.get("published_at"), article.get("content"), fetched_at),
            )
    return len(articles)


def mark_board_error(board: dict, error: str, attempted_at: str | None = None) -> None:
    with get_conn() as conn:
        upsert_board_meta(conn, board)
        conn.execute(
            "UPDATE boards SET last_status='error', last_error=?, last_attempt_at=? WHERE slug=?",
            (safe_fetch_error(error), attempted_at or now_iso(), board["slug"]),
        )
