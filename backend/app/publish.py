"""Refresh all sources and export the portable snapshot for the app.

Used by the scheduled GitHub Actions workflow; exits without writing when
every source fails so the previously published snapshot is kept.
"""

import asyncio
import sys
from pathlib import Path

from .service import refresh_all
from .snapshot import write_snapshot


def main() -> None:
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("leaderboards.json")
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
