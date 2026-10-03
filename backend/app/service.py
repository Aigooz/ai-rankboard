import asyncio
import logging

from .boards import BOARDS, BOARD_BY_SLUG
from .db import get_conn, init_db, now_iso
from .scraper import fetch_board, fetch_model_release_dates
from .store import mark_board_error, save_board_data, save_model_release_dates, upsert_board_meta

log = logging.getLogger("service")


async def refresh_board(board: dict) -> int:
    fetched_at = now_iso()
    try:
        rows = await fetch_board(board)
    except Exception as exc:  # noqa: BLE001
        log.warning("board %s refresh failed: %s", board["slug"], exc)
        mark_board_error(board, str(exc))
        raise
    return save_board_data(board, rows, fetched_at)


async def refresh_all(slugs=None) -> dict:
    init_db()
    targets = [BOARD_BY_SLUG[s] for s in slugs] if slugs else BOARDS
    with get_conn() as conn:
        for board in targets:
            upsert_board_meta(conn, board)
    results = {}
    for board in targets:
        try:
            count = await refresh_board(board)
            results[board["slug"]] = {"status": "ok", "models": count}
        except Exception as exc:  # noqa: BLE001
            results[board["slug"]] = {"status": "error", "error": str(exc)}
        await asyncio.sleep(1.5)
    results["release_dates"] = {"models": await refresh_missing_release_dates()}
    return results


async def refresh_missing_release_dates() -> int:
    with get_conn() as conn:
        rows = conn.execute(
            """
            SELECT DISTINCT source_url FROM models
            WHERE source_url LIKE 'https://modelsage.cn/model/%'
              AND (release_date IS NULL OR release_date = '')
            """
        ).fetchall()
    urls = [row["source_url"] for row in rows if row["source_url"]]
    dates = await fetch_model_release_dates(urls)
    return save_model_release_dates(dates)
