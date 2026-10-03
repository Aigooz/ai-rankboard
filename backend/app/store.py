"""Persistence: upsert parsed rows into SQLite."""

from .db import get_conn
from .model_identity import canonical_model_slug
from .scraper import _PARAMS_RE


def extract_params_b(name: str):
    m = _PARAMS_RE.search(name)
    return float(m.group(1)) if m else None


def upsert_board_meta(conn, board: dict) -> None:
    conn.execute(
        """INSERT INTO boards(slug, name, dimension, score_type, url)
           VALUES (?, ?, ?, ?, ?)
           ON CONFLICT(slug) DO UPDATE SET name=excluded.name, dimension=excluded.dimension,
               score_type=excluded.score_type, url=excluded.url""",
        (board["slug"], board["name"], board["dimension"], board["score_type"],
         "https://modelsage.cn" + board["path"]),
    )


def save_board_data(board: dict, rows: list, fetched_at: str) -> int:
    with get_conn() as conn:
        for row in rows:
            model_slug = canonical_model_slug(row["name"], row["slug"])
            conn.execute(
                """INSERT INTO models(slug, display_name, vendor, params_b, license, context_window, source_url, first_seen_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(slug) DO UPDATE SET
                       display_name=excluded.display_name,
                       vendor=COALESCE(excluded.vendor, models.vendor),
                       params_b=COALESCE(models.params_b, excluded.params_b),
                       license=COALESCE(excluded.license, models.license),
                       context_window=COALESCE(excluded.context_window, models.context_window),
                       source_url=COALESCE(excluded.source_url, models.source_url),
                       updated_at=excluded.updated_at""",
                (model_slug, row["name"], row["vendor"], extract_params_b(row["name"]),
                 row["license"], row["context_window"], row["source_url"], fetched_at, fetched_at),
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
        conn.execute(
            "UPDATE boards SET last_success_at=?, last_status='ok', last_error=NULL WHERE slug=?",
            (fetched_at, board["slug"]),
        )
    return len(rows)


def mark_board_error(board: dict, error: str) -> None:
    with get_conn() as conn:
        conn.execute(
            "UPDATE boards SET last_status='error', last_error=? WHERE slug=?",
            (error[:500], board["slug"]),
        )
