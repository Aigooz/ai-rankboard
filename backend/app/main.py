import asyncio
import logging
from contextlib import asynccontextmanager

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

from .boards import BOARDS, BOARD_BY_SLUG
from .config import ADMIN_TOKEN, FETCH_INTERVAL_HOURS
from .db import get_conn, init_db, now_iso
from .service import refresh_all

logging.basicConfig(level=logging.INFO)

scheduler = AsyncIOScheduler(timezone="Asia/Shanghai")


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    scheduler.add_job(refresh_all, "interval", hours=FETCH_INTERVAL_HOURS, id="refresh_all")
    scheduler.start()
    # 首轮抓取放到后台：12 个榜单串行抓取可能要几分钟，不能阻塞服务启动。
    first_refresh = asyncio.create_task(refresh_all())
    try:
        yield
    finally:
        first_refresh.cancel()
        scheduler.shutdown(wait=False)


app = FastAPI(title="AI Leaderboard Aggregator", version="0.1.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/v1/meta")
def meta():
    with get_conn() as conn:
        boards = conn.execute(
            "SELECT slug, name, dimension, score_type, url, last_success_at, last_status, last_error, "
            "(SELECT COUNT(*) FROM scores s WHERE s.board_slug=b.slug) AS model_count "
            "FROM boards b ORDER BY b.slug"
        ).fetchall()
    return {"updated_at": now_iso(), "boards": [dict(b) for b in boards]}


@app.get("/v1/leaderboards")
def list_leaderboards(dimension: str | None = None):
    with get_conn() as conn:
        rows = conn.execute(
            "SELECT slug, name, dimension, score_type, url, last_success_at, "
            "(SELECT COUNT(*) FROM scores s WHERE s.board_slug=b.slug) AS model_count "
            "FROM boards b ORDER BY b.slug"
        ).fetchall()
    boards = [dict(r) for r in rows]
    if dimension:
        boards = [b for b in boards if b["dimension"] == dimension]
    return {"boards": boards}


@app.get("/v1/leaderboards/{board_slug}")
def leaderboard_entries(
    board_slug: str,
    sort: str = Query("rank", pattern="^(rank|score|updated)$"),
    q: str | None = None,
    limit: int = Query(50, ge=1, le=500),
    offset: int = Query(0, ge=0),
):
    order = {
        "rank": "s.rank ASC",
        "score": "s.score DESC",
        "updated": "s.fetched_at DESC",
    }[sort]
    with get_conn() as conn:
        board = conn.execute("SELECT * FROM boards WHERE slug=?", (board_slug,)).fetchone()
        if not board:
            raise HTTPException(404, "board not found")
        sql = f"""
            SELECT m.slug, m.display_name, m.vendor, m.params_b, m.license, m.context_window, m.source_url,
                   m.release_date, s.rank, s.score, s.score_ci, s.votes, s.price_in, s.price_out, s.currency, s.fetched_at
            FROM scores s JOIN models m ON m.slug = s.model_slug
            WHERE s.board_slug = ?
        """
        params = [board_slug]
        if q:
            sql += " AND (m.display_name LIKE ? OR m.vendor LIKE ? OR m.slug LIKE ?)"
            like = f"%{q}%"
            params += [like, like, like]
        total = conn.execute(f"SELECT COUNT(*) FROM ({sql})", params).fetchone()[0]
        sql += f" ORDER BY {order} LIMIT ? OFFSET ?"
        rows = conn.execute(sql, params + [limit, offset]).fetchall()
    return {"board": dict(board), "entries": [dict(r) for r in rows], "total": total, "limit": limit, "offset": offset}


@app.get("/v1/models")
def search_models(q: str | None = None, limit: int = Query(50, le=200)):
    with get_conn() as conn:
        sql = """
            SELECT m.slug, m.display_name, m.vendor, m.params_b, m.license, m.context_window,
                   MAX(s.score) AS best_score,
                   (SELECT b.dimension FROM scores s2 JOIN boards b ON b.slug = s2.board_slug
                    WHERE s2.model_slug = m.slug AND s2.score IS NOT NULL
                    ORDER BY s2.score DESC LIMIT 1) AS best_dimension
            FROM models m LEFT JOIN scores s ON s.model_slug = m.slug
        """
        params = []
        if q:
            sql += " WHERE m.display_name LIKE ? OR m.vendor LIKE ? OR m.slug LIKE ?"
            like = f"%{q}%"
            params += [like, like, like]
        sql += " GROUP BY m.slug ORDER BY best_score DESC LIMIT ?"
        params.append(limit)
        rows = conn.execute(sql, params).fetchall()
    return {"models": [dict(r) for r in rows]}


@app.get("/v1/models/{model_slug}")
def model_detail(model_slug: str):
    with get_conn() as conn:
        model = conn.execute("SELECT * FROM models WHERE slug=?", (model_slug,)).fetchone()
        if not model:
            raise HTTPException(404, "model not found")
        scores = conn.execute(
            """SELECT s.board_slug, b.name AS board_name, b.dimension, b.score_type,
                      s.rank, s.score, s.score_ci, s.votes, s.price_in, s.price_out, s.currency, s.fetched_at
               FROM scores s JOIN boards b ON b.slug = s.board_slug
               WHERE s.model_slug = ? ORDER BY b.dimension, s.rank""",
            (model_slug,),
        ).fetchall()
    return {"model": dict(model), "scores": [dict(r) for r in scores]}


@app.post("/v1/admin/refresh")
async def admin_refresh(
    slugs: list[str] | None = None,
    x_admin_token: str | None = Header(default=None, alias="X-Admin-Token"),
):
    if ADMIN_TOKEN and x_admin_token != ADMIN_TOKEN:
        raise HTTPException(401, "invalid admin token")
    unknown = sorted(set(slugs or []) - BOARD_BY_SLUG.keys())
    if unknown:
        raise HTTPException(400, f"unknown boards: {', '.join(unknown)}")
    results = await refresh_all(slugs)
    return {"results": results}
