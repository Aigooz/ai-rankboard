"""Fetch and parse modelsage.cn leaderboard pages.

Two table layouts exist:
- AA boards (overall/coding/...): Rank | Model | Provider | Score | Input | Output | value
- Arena boards: Rank | range | Model | vendor | License | Elo +/- CI | votes | $/M in | $/M out | context
Columns are mapped by header text, so CSS-hidden cells do not matter.
"""

import asyncio
import re

import csv
import httpx
import io
import json

from bs4 import BeautifulSoup

from .config import (
    AA_ARTICLES_URL,
    BASE_URL,
    LIVEBENCH_RELEASE,
    LIVEBENCH_URL,
    MAX_RETRIES,
    REQUEST_TIMEOUT,
    SWE_BENCH_DATA_URL,
    SWE_BENCH_URL,
    USER_AGENT,
)

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


async def fetch_json(client: httpx.AsyncClient, url: str):
    last_exc = None
    for attempt in range(MAX_RETRIES + 1):
        try:
            resp = await client.get(url, headers={"User-Agent": USER_AGENT}, timeout=REQUEST_TIMEOUT)
            resp.raise_for_status()
            return resp.json()
        except Exception as exc:  # noqa: BLE001
            last_exc = exc
            if attempt < MAX_RETRIES:
                await asyncio.sleep(2 * (attempt + 1))
    raise RuntimeError(f"fetch failed after {MAX_RETRIES + 1} attempts: {url}") from last_exc


async def fetch_csv(client: httpx.AsyncClient, url: str) -> list[dict]:
    text = await fetch_html(client, url)
    return list(csv.DictReader(io.StringIO(text)))


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


def parse_release_date(html: str) -> str | None:
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup.find_all("script", type="application/ld+json"):
        try:
            payloads = json.loads(tag.string or "")
        except (json.JSONDecodeError, TypeError):
            continue
        if not isinstance(payloads, list):
            payloads = [payloads]
        for payload in payloads:
            if isinstance(payload, dict) and payload.get("releaseDate"):
                value = str(payload["releaseDate"])
                if re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
                    return value
    return None


async def fetch_model_release_dates(urls: list[str]) -> dict[str, str]:
    """Fetch release dates from ModelSage model detail pages."""
    result: dict[str, str] = {}
    semaphore = asyncio.Semaphore(8)

    async with httpx.AsyncClient(follow_redirects=True) as client:
        async def fetch_one(url: str) -> None:
            async with semaphore:
                try:
                    html = await fetch_html(client, url)
                    release_date = parse_release_date(html)
                    if release_date:
                        result[url] = release_date
                except Exception as exc:  # noqa: BLE001
                    # A missing date must not break the whole leaderboard refresh.
                    print(f"release date fetch failed: {url}: {exc}")

        await asyncio.gather(*(fetch_one(url) for url in urls))
    return result


_VENDOR_RULES = [
    ("claude", "Anthropic"),
    ("deepseek", "DeepSeek"),
    ("gemini", "Google DeepMind"),
    ("glm", "Zhipu AI"),
    ("gpt", "OpenAI"),
    ("grok", "xAI"),
    ("kimi", "Moonshot AI"),
    ("minimax", "MiniMax"),
    ("nemotron", "NVIDIA"),
    ("qwen", "Alibaba Qwen"),
]


def _livebench_vendor(model_id: str) -> str | None:
    lower = model_id.lower()
    for prefix, vendor in _VENDOR_RULES:
        if lower.startswith(prefix):
            return vendor
    return None


def _livebench_name(model_id: str) -> str:
    """Turn LiveBench's model ids into compact human-facing labels."""
    value = model_id.strip()
    lower = value.lower()

    match = re.match(r"claude-(opus|sonnet|haiku|fable)-(\d+)(?:-(\d+))?-(.*)", lower)
    if match:
        family, major, minor, rest = match.groups()
        version = f"{major}.{minor}" if minor else major
        return "Claude %s %s (%s)" % (version, family.capitalize(), _pretty_suffix(rest))

    match = re.match(r"deepseek-v(\d+(?:\.\d+)?)(?:-(.*))?", lower)
    if match:
        version, rest = match.groups()
        return "DeepSeek V%s%s" % (version, " (%s)" % _pretty_suffix(rest) if rest else "")

    match = re.match(r"gemini-(\d+(?:\.\d+)?)(?:-(.*))?", lower)
    if match:
        version, rest = match.groups()
        return "Gemini %s%s" % (version, " (%s)" % _pretty_suffix(rest) if rest else "")

    match = re.match(r"gpt-(\d+(?:\.\d+)?)(?:-(.*))?", lower)
    if match:
        version, rest = match.groups()
        return "GPT %s%s" % (version, " (%s)" % _pretty_suffix(rest) if rest else "")

    match = re.match(r"qwen(\d+(?:\.\d+)?)(?:-(.*))?", lower)
    if match:
        version, rest = match.groups()
        return "Qwen%s%s" % (version, " (%s)" % _pretty_suffix(rest) if rest else "")

    return " ".join(token.upper() if token in {"glm", "gpt"} else token.capitalize() for token in lower.split("-"))


def _pretty_suffix(value: str) -> str:
    if not value:
        return ""
    tokens = []
    for token in value.split("-"):
        if token in {"thinking", "auto", "preview", "effort"} or token.isdigit():
            continue
        elif len(token) <= 3 and token in {"b", "k", "max", "pro"}:
            tokens.append(token.upper())
        else:
            tokens.append(token.capitalize())
    return ", ".join(tokens)


def parse_livebench_board(
    table_rows: list[dict],
    categories: dict[str, list[str]],
    cost_rows: list[dict],
    category: str,
) -> list:
    wanted = list(categories.keys()) if category == "*" else [category]
    missing = [name for name in wanted if name not in categories]
    if missing:
        raise ValueError(f"LiveBench categories missing: {', '.join(missing)}")

    costs = {
        row.get("model", ""): (
            _first_float(row.get("input_price_per_million", "") or ""),
            _first_float(row.get("output_price_per_million", "") or ""),
        )
        for row in cost_rows
    }
    ranked = []
    for row in table_rows:
        model_id = row.get("model", "").strip()
        if not model_id:
            continue
        all_values = []
        for category_name in wanted:
            values = []
            for task in categories[category_name]:
                score = _first_float(row.get(task, "") or "")
                if score is not None:
                    values.append(score)
            if values:
                all_values.append(sum(values) / len(values))
        if not all_values:
            continue
        price_in, price_out = costs.get(model_id, (None, None))
        ranked.append({
            "slug": model_id,
            "name": _livebench_name(model_id),
            "vendor": _livebench_vendor(model_id),
            "rank": 0,
            "score": round(sum(all_values) / len(all_values), 3),
            "score_ci": None,
            "votes": None,
            "license": None,
            "context_window": None,
            "price_in": price_in,
            "price_out": price_out,
            "currency": "USD",
            "source_url": f"{LIVEBENCH_URL}/leaderboard",
        })
    ranked.sort(key=lambda row: row["score"] or 0, reverse=True)
    for index, row in enumerate(ranked, 1):
        row["rank"] = index
    return ranked


def parse_swe_bench_board(data: dict, board_name: str) -> list:
    if not isinstance(data, dict) or not isinstance(data.get("leaderboards"), list):
        raise ValueError("SWE-bench: invalid leaderboard JSON")
    board = next((item for item in data["leaderboards"] if item.get("name") == board_name), None)
    if board is None:
        raise ValueError(f"SWE-bench board not found: {board_name}")
    rows = [item for item in board.get("results", []) if item.get("resolved") is not None]
    rows.sort(key=lambda item: (float(item.get("resolved") or 0), -float(item.get("cost") or 0)), reverse=True)
    parsed = []
    for rank, item in enumerate(rows, 1):
        name = (item.get("model_display") or item.get("name") or "").strip()
        if not name:
            continue
        os_model = item.get("os_model")
        parsed.append({
            "slug": re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-"),
            "name": name,
            "vendor": item.get("model_org") or None,
            "rank": rank,
            "score": _first_float(str(item.get("resolved") or "")),
            "score_ci": None,
            "votes": None,
            "license": "open" if os_model is True else ("proprietary" if os_model is False else None),
            "context_window": None,
            "price_in": None,
            "price_out": None,
            "currency": "USD",
            "source_url": SWE_BENCH_URL,
        })
    return parsed


async def fetch_livebench_board(board: dict) -> list:
    release = board.get("release", LIVEBENCH_RELEASE)
    async with httpx.AsyncClient(follow_redirects=True) as client:
        table = await fetch_csv(client, f"{LIVEBENCH_URL}/table_{release}.csv")
        categories = await fetch_json(client, f"{LIVEBENCH_URL}/categories_{release}.json")
        costs = await fetch_csv(client, f"{LIVEBENCH_URL}/cost_{release}.csv")
    return parse_livebench_board(table, categories, costs, board.get("category", "*"))


async def fetch_swe_bench_board(board: dict) -> list:
    async with httpx.AsyncClient(follow_redirects=True) as client:
        data = await fetch_json(client, SWE_BENCH_DATA_URL)
    return parse_swe_bench_board(data, board.get("swe_board", "Verified"))


async def fetch_board(board: dict) -> list:
    if board.get("kind") == "livebench":
        return await fetch_livebench_board(board)
    if board.get("kind") == "swe_bench":
        return await fetch_swe_bench_board(board)
    url = f"{BASE_URL}{board['path']}"
    async with httpx.AsyncClient(follow_redirects=True) as client:
        html = await fetch_html(client, url)
    return parse_board(board, html)


# 用量榜是 div 结构而非表格，行首为名次 span；数值单元格靠 title 文本锚定。
_USAGE_ROW_RE = re.compile(
    r"^\s*[^>]*?>(?P<position>\d+)</span>"
    r".*?(?:<a[^>]*href=\"(?P<url>/model/[^\"]+)\"[^>]*>(?P<aname>[^<]+)</a>"
    r"|<div[^>]*><span[^>]*>(?P<sname>[^<]+)</span></div>)"
    r".*?bar-fill"
    r".*?title=\"含输入[^\"]*\">(?P<tokens>[^<]+)</span>"
    r".*?title=\"占本周[^\"]*\">(?P<share>[\d.]+)%</span>"
    r".*?title=\"(?:调用量相对上周|上周未进前)[^\"]*\">(?P<wow>[^<]+)</span>",
    re.S,
)


def parse_usage_trend(html: str) -> dict:
    text = html.replace("<!-- -->", "")
    week = re.search(r"网关真实用量榜\s*·\s*(.*?)</div>", text)
    total = re.search(r"共消耗\s*([\d.]+\s*万亿)\s*token", text)
    platform_wow = re.search(r"平台总量环比\s*([+\-−][\d.]+)\s*%", text)

    entries = []
    chunks = re.split(r'<span class="w-5 shrink-0 text-right', text)[1:]
    for chunk in chunks:
        match = _USAGE_ROW_RE.search(chunk)
        if not match:
            continue
        name = (match.group("aname") or match.group("sname") or "").strip()
        if not name:
            continue
        entries.append({
            "position": int(match.group("position")),
            "name": name,
            "model_url": match.group("url"),
            "tokens": match.group("tokens").strip(),
            "share": float(match.group("share")),
            "wow": match.group("wow").strip(),
        })
    if not entries:
        raise ValueError("usage trends: no rows parsed")
    return {
        "week_label": week.group(1).strip() if week else "",
        "total_tokens": total.group(1) if total else "",
        "platform_wow": (platform_wow.group(1) + "%") if platform_wow else "",
        "entries": entries,
    }


async def fetch_usage_trend() -> dict:
    async with httpx.AsyncClient(follow_redirects=True) as client:
        html = await fetch_html(client, f"{BASE_URL}/trends")
    return parse_usage_trend(html)


# 文章卡片：链接 + 标题 + 日期，日期形如 "September 30, 2026"。
_ARTICLE_RE = re.compile(
    r'<a class="relative flex flex-col gap-4 group" href="(?P<url>/articles/[^"]+)">'
    r".*?<h3[^>]*>(?P<title>[^<]+)</h3>"
    r"(?:<p class=\"text-xs text-muted-foreground\">(?P<date>[^<]+)</p>)?",
    re.S,
)

_MONTHS = {
    "January": 1, "February": 2, "March": 3, "April": 4, "May": 5, "June": 6,
    "July": 7, "August": 8, "September": 9, "October": 10, "November": 11, "December": 12,
}


def _parse_article_date(raw: str | None) -> str | None:
    if not raw:
        return None
    match = re.search(r"([A-Z][a-z]+)\s+(\d{1,2}),\s*(\d{4})", raw)
    if not match:
        return None
    month = _MONTHS.get(match.group(1))
    if not month:
        return None
    return f"{match.group(3)}-{month:02d}-{int(match.group(2)):02d}"


def parse_aa_articles(html: str) -> list[dict]:
    import html as html_lib

    entries = []
    seen: set[str] = set()
    for match in _ARTICLE_RE.finditer(html):
        url = match.group("url").split("?")[0]
        if url in seen:
            continue
        title = html_lib.unescape(match.group("title")).strip()
        if not title:
            continue
        seen.add(url)
        entries.append({
            "position": len(entries) + 1,
            "title": title,
            "url": url,
            "published_at": _parse_article_date(match.group("date")),
        })
    if not entries:
        raise ValueError("AA articles: no rows parsed")
    return entries


async def fetch_aa_articles() -> list[dict]:
    async with httpx.AsyncClient(follow_redirects=True) as client:
        html = await fetch_html(client, AA_ARTICLES_URL)
    return parse_aa_articles(html)
