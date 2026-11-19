"""Refresh all sources and export the portable snapshot for the app.

Used by the scheduled GitHub Actions workflow; exits without writing when
every source fails so the previously published snapshot is kept.
"""

import asyncio
import json
import sys
from pathlib import Path
from datetime import date, datetime, timezone
from urllib.parse import urlsplit

from .service import refresh_all
from .snapshot import build_snapshot, write_snapshot
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


SNAPSHOT_MAX_AGE_HOURS = 48


def snapshot_health(output: Path) -> dict:
    snapshot = build_snapshot()
    boards = snapshot["boards"]
    data_status = snapshot.get("dataStatus") or {}
    sources = [{**source, "ageHours": None} for source in data_status.get("sources", [])]

    now = datetime.now(timezone.utc)
    for source in sources:
        try:
            timestamp = datetime.fromisoformat(
                (source.get("oldestSuccessAt") or "").replace("Z", "+00:00")
            )
            if timestamp.tzinfo is None:
                raise ValueError
            source["ageHours"] = round((now - timestamp).total_seconds() / 3600, 1)
        except (ValueError, TypeError):
            source["ageHours"] = None

    failed = sorted(
        board["slug"]
        for board in boards
        if (board.get("health") or {}).get("latestAttemptStatus") == "error"
    )
    boards_without_data = sorted(
        board["slug"] for board in boards if board.get("model_count", 0) <= 0
    )
    sources_without_data = sorted(
        source["sourceId"] for source in sources
        if any(board["source_id"] == source["sourceId"] for board in boards)
        and source.get("lastSuccessAt") is None
    )
    stale_sources = sorted(
        source["sourceId"] for source in sources
        if source.get("ageHours") is None or source["ageHours"] > SNAPSHOT_MAX_AGE_HOURS
    )
    return {
        "status": data_status.get("status", "degraded" if failed else "ok"),
        "failedBoards": failed,
        "boardsWithoutData": boards_without_data,
        "sourcesWithoutData": sources_without_data,
        "staleSources": stale_sources,
        "maxAgeHours": SNAPSHOT_MAX_AGE_HOURS,
        "sources": sources,
    }


def publish(output: Path) -> dict:
    restore_baseline(output)
    results = asyncio.run(refresh_all())
    ok = [
        slug
        for slug, result in results.items()
        if slug in BOARD_BY_SLUG
        and isinstance(result, dict)
        and result.get("status") == "ok"
    ]
    if not ok:
        print("all sources failed; keeping previous snapshot")
        return {"published": False, "reason": "all_sources_failed", "results": results}
    health = snapshot_health(output)
    integrity_errors = {
        "failedBoards": health["failedBoards"],
        "boardsWithoutData": health["boardsWithoutData"],
        "sourcesWithoutData": health["sourcesWithoutData"],
        "staleSources": health["staleSources"],
    }
    if any(integrity_errors.values()):
        print(
            "snapshot contains incomplete or stale boards; keeping previous snapshot: "
            + json.dumps(integrity_errors, ensure_ascii=False, sort_keys=True)
        )
        return {
            "published": False,
            "reason": "incomplete_snapshot",
            "health": health,
            "integrityErrors": integrity_errors,
            "results": results,
        }
    digest = write_snapshot(output)
    failed = [
        slug
        for slug, result in results.items()
        if isinstance(result, dict) and result.get("status") == "error"
    ]
    print(f"snapshot written: {output}")
    print(f"ok boards: {len(ok)}; failed: {failed or 'none'}")
    print(f"sha256: {digest}")
    return {
        "published": True,
        "digest": digest,
        "okBoards": len(ok),
        "failedBoards": failed,
        "health": health,
        "results": results,
    }


def main() -> None:
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("leaderboards.json")
    result = publish(output)
    if not result["published"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
