"""Persistence: upsert parsed rows into SQLite."""

from .db import get_conn
from .model_identity import canonical_model_slug
from .scraper import _PARAMS_RE
from .config import LIVEBENCH_URL, SWE_BENCH_URL


def extract_params_b(name: str):
    m = _PARAMS_RE.search(name)
    return float(m.group(1)) if m else None


def upsert_board_meta(conn, board: dict) -> None:
    if board.get("kind") == "livebench":
        url = f"{LIVEBENCH_URL}/leaderboard"
    elif board.get("kind") == "swe_bench":
        url = SWE_BENCH_URL
    else:
        url = "https://modelsage.cn" + board["path"]
    conn.execute(
        """INSERT INTO boards(slug, name, dimension, score_type, url)
           VALUES (?, ?, ?, ?, ?)
           ON CONFLICT(slug) DO UPDATE SET name=excluded.name, dimension=excluded.dimension,
               score_type=excluded.score_type, url=excluded.url""",
        (board["slug"], board["name"], board["dimension"], board["score_type"], url),
    )


def save_board_data(board: dict, rows: list, fetched_at: str) -> int:
    if not rows:
        raise ValueError(f"board {board['slug']}: parsed 0 rows, refusing to wipe existing data")

    # 单次解析的名次唯一：同榜同名次且同分说明是同一行的两代残留（或站点改名），
    # 保留名字更长（信息更全）的一行。
    by_rank: dict = {}
    for row in rows:
        current = by_rank.get(row["rank"])
        if current is None or len(row["name"] or "") > len(current["name"] or ""):
            by_rank[row["rank"]] = row
    rows = sorted(by_rank.values(), key=lambda item: item["rank"])

    seen: set[str] = set()
    deduped = []
    for row in rows:
        model_slug = canonical_model_slug(row["name"], row["slug"])
        if model_slug in seen:
            continue
        seen.add(model_slug)
        deduped.append((model_slug, row))

    with get_conn() as conn:
        # 榜单内容以本次抓取为准：先清掉该榜旧成绩再写入，避免站点改了命名后
        # 新旧两代 slug 并存出现"模型分身"。抓取失败不会走到这里，旧数据得以保留。
        conn.execute("DELETE FROM scores WHERE board_slug=?", (board["slug"],))
        for model_slug, row in deduped:
            conn.execute(
                """INSERT INTO models(slug, display_name, vendor, params_b, license, context_window, source_url, release_date, first_seen_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(slug) DO UPDATE SET
                       display_name=CASE
                           WHEN excluded.display_name NOT GLOB '*[^a-z0-9._-]*'
                                AND models.display_name GLOB '*[^a-z0-9._-]*'
                           THEN models.display_name
                           ELSE excluded.display_name
                       END,
                       vendor=COALESCE(excluded.vendor, models.vendor),
                       params_b=COALESCE(models.params_b, excluded.params_b),
                       license=COALESCE(excluded.license, models.license),
                       context_window=COALESCE(excluded.context_window, models.context_window),
                       source_url=COALESCE(excluded.source_url, models.source_url),
                       release_date=COALESCE(models.release_date, excluded.release_date),
                       updated_at=excluded.updated_at""",
                (model_slug, row["name"], row["vendor"], extract_params_b(row["name"]),
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
            "UPDATE boards SET last_success_at=?, last_status='ok', last_error=NULL WHERE slug=?",
            (fetched_at, board["slug"]),
        )
    return len(deduped)


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


def mark_board_error(board: dict, error: str) -> None:
    with get_conn() as conn:
        conn.execute(
            "UPDATE boards SET last_status='error', last_error=? WHERE slug=?",
            (error[:500], board["slug"]),
        )
