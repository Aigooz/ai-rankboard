"""Export a portable leaderboard snapshot for the Android app.

The snapshot is intentionally static: it can be bundled as the initial state and
later hosted on any static file service for background updates.
"""

import argparse
import hashlib
import json
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from .db import get_conn

SCHEMA_VERSION = 2
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
            {**dict(row), "source_id": board_source.get(row["score_type"], "modelsage")}
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
                "updated_at": row["updated_at"],
            }
            for row in conn.execute(
                """
                SELECT slug, display_name, vendor, params_b, license, context_window,
                       source_url, updated_at
                FROM models
                ORDER BY slug
                """
            )
        }

        entries = defaultdict(list)
        query = """
            SELECT s.*, m.display_name, m.slug, m.vendor, m.params_b,
                   m.license, m.context_window
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

    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "source": SOURCE,
        "sources": SOURCES,
        "boards": boards,
        "entriesByBoard": dict(entries),
        "models": models,
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
