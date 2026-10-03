import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone

from .config import DATA_DIR, DB_PATH

SCHEMA = """
CREATE TABLE IF NOT EXISTS boards (
    slug TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    dimension TEXT NOT NULL,
    score_type TEXT NOT NULL,
    url TEXT NOT NULL,
    last_success_at TEXT,
    last_status TEXT,
    last_error TEXT
);
CREATE TABLE IF NOT EXISTS models (
    slug TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    vendor TEXT,
    params_b REAL,
    license TEXT,
    context_window TEXT,
    source_url TEXT,
    first_seen_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS scores (
    board_slug TEXT NOT NULL,
    model_slug TEXT NOT NULL,
    rank INTEGER NOT NULL,
    score REAL,
    score_ci REAL,
    votes INTEGER,
    price_in REAL,
    price_out REAL,
    currency TEXT NOT NULL DEFAULT 'CNY',
    fetched_at TEXT NOT NULL,
    PRIMARY KEY (board_slug, model_slug),
    FOREIGN KEY (board_slug) REFERENCES boards(slug),
    FOREIGN KEY (model_slug) REFERENCES models(slug)
);
CREATE INDEX IF NOT EXISTS idx_scores_board_rank ON scores(board_slug, rank);
CREATE INDEX IF NOT EXISTS idx_scores_model ON scores(model_slug);
"""


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=30)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


@contextmanager
def get_conn():
    conn = connect()
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with get_conn() as conn:
        conn.executescript(SCHEMA)

