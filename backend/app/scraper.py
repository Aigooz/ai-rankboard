"""Fetch and parse modelsage.cn leaderboard pages.

Two table layouts exist:
- AA boards (overall/coding/...): Rank | Model | Provider | Score | Input | Output | value
- Arena boards: Rank | range | Model | vendor | License | Elo +/- CI | votes | $/M in | $/M out | context
Columns are mapped by header text, so CSS-hidden cells do not matter.
"""

import asyncio
import re

import httpx
from bs4 import BeautifulSoup

from .config import BASE_URL, MAX_RETRIES, REQUEST_TIMEOUT, USER_AGENT

_NUM_RE = re.compile(r"-?\d+(?:\.\d+)?")
_PARAMS_RE = re.compile(r"(\d+(?:\.\d+)?)\s*[bB]\b")
_K_RE = re.compile(r"^(\d+(?:\.\d+)?)\s*[kK]$")


async def fetch_html(client: httpx.AsyncClient, url: str) -> str:
    last_exc = None
    for attempt in range(MAX_RETRIES + 1):
        try:
            resp = await client.get(url, headers={"User-Agent": USER_AGENT}, timeout=REQUEST_TIMEOUT)
            resp.raise_for_status()
            return resp.text
        except Exception as exc:  # noqa: BLE001
            last_exc = exc
            if attempt < MAX_RETRIES:
                await asyncio.sleep(2 * (attempt + 1))
    raise RuntimeError(f"fetch failed after {MAX_RETRIES + 1} attempts: {url}") from last_exc


def _cell_text(cell) -> str:
    return cell.get_text(" ", strip=True) if cell else ""


def _first_float(text: str):
    m = _NUM_RE.search(text.replace(",", ""))
    return float(m.group()) if m else None


def _parse_votes(text: str):
    m = _K_RE.match(text.strip())
    if m:
        return int(float(m.group(1)) * 1000)
    digits = re.sub(r"[^\d]", "", text)
    return int(digits) if digits else None


def slugify(name: str) -> str:
    s = name.strip().lower()
    s = re.sub(r"[^a-z0-9\u4e00-\u9fff]+", "-", s)
    return s.strip("-")


def _header_map(row) -> dict:
    return {th.get_text(strip=True): i for i, th in enumerate(row.find_all("th"))}


def parse_aa_board(html: str) -> list:
    soup = BeautifulSoup(html, "html.parser")
    table = soup.find("table")
    if not table:
        raise ValueError("AA board: no table found")
    header = _header_map(table.find("thead").find("tr"))
    rows = []
    for tr in table.find("tbody").find_all("tr"):
        cells = tr.find_all("td")
        if len(cells) < 4 or "Model" not in header:
            continue
        model_a = cells[header["Model"]].find("a", href=re.compile(r"^/model/"))
        if not model_a:
            continue
        # prefer the bold model-name span; ignore tier badges like "★ 前沿" inside the link
        name_span = model_a.find("span", class_=re.compile(r"font-semibold"))
        name = (name_span if name_span else model_a).get_text(strip=True)
        slug = model_a["href"].rsplit("/", 1)[-1]
        vendor = _cell_text(cells[header["Provider"]]) or None
        score = _first_float(_cell_text(cells[header["Score"]]))
        price_in = _first_float(_cell_text(cells[header["Input"]])) if "Input" in header else None
        price_out = _first_float(_cell_text(cells[header["Output"]])) if "Output" in header else None
        rank_val = _first_float(_cell_text(cells[header["Rank"]]))
        rows.append({
            "slug": slug,
            "name": name,
            "vendor": vendor,
            "rank": int(rank_val or 0),
            "score": score,
            "score_ci": None,
            "votes": None,
            "license": None,
            "context_window": None,
            "price_in": price_in,
            "price_out": price_out,
            "currency": "CNY",
            "source_url": f"{BASE_URL}/model/{slug}",
        })
    return rows


def parse_arena_board(html: str) -> list:
    soup = BeautifulSoup(html, "html.parser")
    table = soup.find("table")
    if not table:
        raise ValueError("Arena board: no table found")
    header = _header_map(table.find("thead").find("tr"))
    elo_key = "Elo 分" if "Elo 分" in header else ("Elo" if "Elo" in header else None)
    rows = []
    for tr in table.find("tbody").find_all("tr"):
        cells = tr.find_all("td")
        if len(cells) < 4:
            continue
        model_cell = cells[header["Model"]]
        model_a = model_cell.find("a") or model_cell
        name = model_a.get_text(strip=True)
        if not name:
            continue
        elo_text = _cell_text(cells[header[elo_key]]) if elo_key else ""
        score = _first_float(elo_text)
        ci_m = re.search(r"±\s*(\d+(?:\.\d+)?)", elo_text)
        votes = _parse_votes(_cell_text(cells[header["投票数"]])) if "投票数" in header else None
        price_in = _first_float(_cell_text(cells[header["输入 $/M"]])) if "输入 $/M" in header else None
        price_out = _first_float(_cell_text(cells[header["输出 $/M"]])) if "输出 $/M" in header else None
        context = _cell_text(cells[header["上下文"]]) if "上下文" in header else None
        license_ = _cell_text(cells[header["License"]]) if "License" in header else None
        rank_val = _first_float(_cell_text(cells[header["Rank"]]))
        href = model_a.get("href") if hasattr(model_a, "get") else None
        rows.append({
            "slug": slugify(name),
            "name": name,
            "vendor": _cell_text(cells[header["厂商"]]) or None,
            "rank": int(rank_val or 0),
            "score": score,
            "score_ci": float(ci_m.group(1)) if ci_m else None,
            "votes": votes,
            "license": license_ or None,
            "context_window": context or None,
            "price_in": price_in,
            "price_out": price_out,
            "currency": "USD",
            "source_url": href if href and href.startswith("http") else None,
        })
    return rows


def parse_board(board: dict, html: str) -> list:
    if board["score_type"] == "aa_index":
        return parse_aa_board(html)
    return parse_arena_board(html)


async def fetch_board(board: dict) -> list:
    url = f"{BASE_URL}{board['path']}"
    async with httpx.AsyncClient(follow_redirects=True) as client:
        html = await fetch_html(client, url)
    return parse_board(board, html)
