import asyncio
import logging

from .boards import BOARDS, BOARD_BY_SLUG
from .db import get_conn, init_db, now_iso
from .scraper import fetch_board
from .store import mark_board_error, save_board_data, upsert_board_meta

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
    return results

