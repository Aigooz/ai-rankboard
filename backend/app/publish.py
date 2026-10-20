"""Refresh all sources and export the portable snapshot for the app.

Used by the scheduled GitHub Actions workflow; exits without writing when
every source fails so the previously published snapshot is kept.
"""

import asyncio
import json
import sys
from pathlib import Path
from datetime import date
from urllib.parse import urlsplit

from .service import refresh_all
from .snapshot import write_snapshot
from .boards import BOARD_BY_SLUG
from .db import get_conn, init_db
from .store import save_board_data, HISTORY_LIMIT, HISTORY_ENTRY_LIMIT, safe_fetch_error


def restore_baseline(output: Path) -> None:
    """Seed only absent boards from the previous v3 output on fresh CI runners.

    A previous current score is a cache, not an invented historical observation.
    Existing local database boards are never overwritten by an older snapshot.
    """
    init_db()
    if not output.is_file():
        return
    previous = json.loads(output.read_text(encoding="utf-8"))
    if previous.get("schemaVersion") != 3:
        raise ValueError("unsupported baseline snapshot schema")
    for board in previous.get("boards", []):
        slug = board.get("slug")
        if slug not in BOARD_BY_SLUG:
            continue
        with get_conn() as conn:
            if conn.execute("SELECT 1 FROM scores WHERE board_slug=? LIMIT 1", (slug,)).fetchone():
                continue
        entries = previous.get("entriesByBoard", {}).get(slug, [])
        success = board.get("health", {}).get("lastSuccessAt") or board.get("last_success_at")
        if not entries or not success:
            continue
        rows = [
            {"slug": entry["slug"], "name": entry["display_name"],
             **{key: entry.get(key) for key in ("vendor", "license", "context_window", "release_date",
                                                "score", "score_ci", "votes", "price_in", "price_out")},
             "source_url": previous.get("models", {}).get(entry["slug"], {}).get("source_url"),
             "rank": entry["rank"], "currency": entry.get("currency", "CNY")}
            for entry in entries
        ]
        version = board.get("evaluation", {}).get("version")
        # Legacy release is explicitly a configured evaluation version, not time.
        if "evaluation" not in board:
            version = board.get("release")
        save_board_data({**BOARD_BY_SLUG[slug], "release": version}, rows, success)
        with get_conn() as conn:
            conn.execute("DELETE FROM board_history WHERE board_slug=?", (slug,))
            observations = previous.get("historyByBoard", {}).get(slug, [])[-HISTORY_LIMIT:]
            for observation in observations:
                conn.execute(
                    """INSERT OR REPLACE INTO board_history
                       (board_slug,fetched_at,model_count,evaluation_version,entries_json)
                       VALUES (?,?,?,?,?)""",
                    (slug, observation["fetchedAt"], observation["modelCount"],
                     observation.get("evaluationVersion"),
                     json.dumps(observation["entries"][:HISTORY_ENTRY_LIMIT], allow_nan=False)),
                )
            health = board.get("health", {})
            status = health.get("latestAttemptStatus", board.get("last_status"))
            conn.execute(
                """UPDATE boards SET last_attempt_at=?,last_status=?,last_error=? WHERE slug=?""",
                (health.get("latestAttemptAt", board.get("last_attempt_at")), status,
                 safe_fetch_error("") if status == "error" else None, slug),
            )

    with get_conn() as conn:
        for model_slug, model in previous.get("models", {}).items():
            release_date = model.get("release_date")
            source_url = model.get("release_date_source_url")
            if not isinstance(release_date, str) or not isinstance(source_url, str):
                continue
            try:
                # Require the canonical day, not timestamps, approximate dates,
                # or Python's accepted compact ISO form.
                if date.fromisoformat(release_date).isoformat() != release_date:
                    continue
                url = urlsplit(source_url)
                if (url.scheme not in ("http", "https") or not url.hostname
                        or url.username is not None or url.password is not None
                        or any(char.isspace() for char in source_url)):
                    continue
            except ValueError:
                continue
            # Evidence belongs to this exact date. Fill an absent baseline date,
            # but never replace a different local date or existing evidence.
            conn.execute(
                "UPDATE models SET release_date=?, release_date_source_url=? "
                "WHERE slug=? AND (release_date IS NULL OR release_date='' OR release_date=?) "
                "AND (release_date_source_url IS NULL OR release_date_source_url='')",
                (release_date, source_url, model_slug, release_date),
            )


def main() -> None:
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("leaderboards.json")
    restore_baseline(output)
    results = asyncio.run(refresh_all())
    ok = [
        slug
        for slug, result in results.items()
        if isinstance(result, dict) and result.get("status") == "ok"
    ]
    if not ok:
        print("all sources failed; keeping previous snapshot")
        return
    digest = write_snapshot(output)
    failed = [
        slug
        for slug, result in results.items()
        if isinstance(result, dict) and result.get("status") == "error"
    ]
    print(f"snapshot written: {output}")
    print(f"ok boards: {len(ok)}; failed: {failed or 'none'}")
    print(f"sha256: {digest}")


if __name__ == "__main__":
    main()
