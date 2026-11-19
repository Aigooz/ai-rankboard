"""Official Aider/EvalPlus public data adapters; absent facts remain null.

Sources are public benchmark releases; source links retain attribution.
Aider: https://github.com/Aider-AI/aider/blob/main/LICENSE.txt
EvalPlus: https://github.com/evalplus/evalplus.github.io/blob/master/LICENSE
"""
import asyncio
import json
import math
import re
from datetime import date, datetime, timedelta, timezone
from urllib.parse import urlsplit

import yaml
from bs4 import BeautifulSoup

AIDER_DATA_URL = "https://raw.githubusercontent.com/Aider-AI/aider/main/aider/website/_data/polyglot_leaderboard.yml"
EVALPLUS_DATA_URL = "https://raw.githubusercontent.com/evalplus/evalplus.github.io/master/results.json"
BFCL_CSV_URL = "https://gorilla.cs.berkeley.edu/data_overall.csv"
BFCL_PAGE_URL = "https://gorilla.cs.berkeley.edu/leaderboard.html"
TERMINAL_BENCH_URL = (
    "https://hub.harborframework.com/datasets/terminal-bench/terminal-bench/latest"
    "?tab=leaderboard&leaderboard=4-1-0"
)
TERMINAL_BENCH_SUBMISSIONS_API = (
    "https://api.github.com/repos/harbor-framework/terminal-bench/"
    "contents/leaderboard/submissions"
)
OPENROUTER_RANKINGS_URL = "https://openrouter.ai/rankings"


def _percentage(value):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    value = float(value)
    return value if math.isfinite(value) and 0 <= value <= 100 else None


def _row(name, score, url):
    return {"name": name, "slug": re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-"),
            "vendor": None, "score": score, "rank": 0, "score_ci": None,
            "votes": None, "license": None, "context_window": None,
            "price_in": None, "price_out": None, "currency": "USD", "source_url": url}


def _public_http_url(value):
    if not isinstance(value, str) or any(char.isspace() for char in value):
        return None
    try:
        url = urlsplit(value.strip())
    except ValueError:
        return None
    if (url.scheme not in {"http", "https"} or not url.hostname
            or url.username is not None or url.password is not None):
        return None
    return url.geturl()


def _rank(rows):
    # Sequential positions preserve every tied row in the legacy store, which
    # deduplicates equal ranks. Equal percentages do not imply meaningful order.
    rows.sort(key=lambda row: (-row["score"], row["name"].casefold()))
    for rank, row in enumerate(rows, 1):
        row["rank"] = rank
    if not rows:
        raise ValueError("source contains no valid scored entries")
    return rows


def _competition_rank(rows):
    """Sort descending and assign equal scores the same official rank."""
    rows.sort(key=lambda row: (-row["score"], row["name"].casefold()))
    previous_score = None
    previous_rank = 0
    for position, row in enumerate(rows, 1):
        row["rank"] = previous_rank if row["score"] == previous_score else position
        previous_score = row["score"]
        previous_rank = row["rank"]
    if not rows:
        raise ValueError("source contains no valid scored entries")
    return rows


def parse_aider_polyglot(text):
    data = yaml.safe_load(text)
    if not isinstance(data, list):
        raise ValueError("Aider: expected YAML result list")
    rows = []
    seen = set()
    for item in data:
        if not isinstance(item, dict):
            continue
        name = item.get("model")
        score = _percentage(item.get("pass_rate_2"))
        edit_format = item.get("edit_format")
        # Keep named configurations, not a best-of-model aggregation; otherwise
        # editing methods would be silently collapsed by canonical model identity.
        if not isinstance(name, str) or not name.strip() or score is None:
            continue
        if not isinstance(edit_format, str) or not edit_format.strip():
            continue
        name = f"{name.strip()} [Aider {edit_format.strip()}]"
        if name in seen:
            raise ValueError("Aider: duplicate model/edit configuration")
        seen.add(name)
        rows.append(_row(name, score, "https://aider.chat/docs/leaderboards/"))
    return _rank(rows)


def parse_evalplus(data, category="humaneval+"):
    if category not in {"humaneval+", "mbpp+"}:
        raise ValueError("EvalPlus: unsupported category")
    if not isinstance(data, dict):
        raise ValueError("EvalPlus: expected model-result mapping")
    rows = []
    for name, item in data.items():
        if not isinstance(name, str) or not name.strip() or not isinstance(item, dict):
            continue
        scores = item.get("pass@1")
        if not isinstance(scores, dict):
            continue
        score = _percentage(scores.get(category))
        if score is None:
            continue
        link = item.get("link")
        url = link if isinstance(link, str) and link.startswith("https://") else "https://evalplus.github.io/leaderboard.html"
        rows.append(_row(name.strip(), score, url))
    return _rank(rows)


def _percent_text(value):
    if isinstance(value, bool) or not isinstance(value, str):
        return None
    match = re.fullmatch(r"\s*(\d+(?:\.\d+)?)\s*%\s*", value)
    if not match:
        return None
    value = float(match.group(1))
    return value if math.isfinite(value) and 0 <= value <= 100 else None


def parse_bfcl(rows):
    """Parse the official Berkeley Function Calling Leaderboard CSV.

    The final parenthetical suffix (FC, Prompt, FC thinking, ...) is part of a
    configuration and must stay part of the model identity.
    """
    if not isinstance(rows, list):
        raise ValueError("BFCL: expected CSV rows")
    parsed = []
    seen = set()
    for row in rows:
        if not isinstance(row, dict):
            continue
        name = row.get("Model")
        raw_score = row.get("Overall Acc")
        rank_text = row.get("Rank")
        org = row.get("Organization")
        license_ = row.get("License")
        url = row.get("Model Link")
        if not isinstance(name, str) or not name.strip():
            continue
        score = _percent_text(raw_score)
        if score is None:
            continue
        if not isinstance(rank_text, str) or not re.fullmatch(r"\d+", rank_text.strip()):
            raise ValueError("BFCL: invalid rank row")
        url = _public_http_url(url)
        if url is None:
            raise ValueError("BFCL: missing or invalid model link")
        name = name.strip()
        if name in seen:
            raise ValueError("BFCL: duplicate model/call configuration")
        seen.add(name)
        parsed.append({
            "name": f"{name} [BFCL]",
            "slug": re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-"),
            "vendor": org.strip() if isinstance(org, str) and org.strip() else None,
            "score": score, "rank": int(rank_text), "score_ci": None,
            "votes": None,
            "license": license_.strip() if isinstance(license_, str) and license_.strip() else None,
            "context_window": None, "price_in": None, "price_out": None,
            "currency": "USD", "source_url": url,
        })
    parsed.sort(key=lambda row: row["rank"])
    if not parsed:
        raise ValueError("BFCL: source contains no valid scored entries")
    return parsed


def _terminal_bench_display(metadata):
    return metadata["label"].strip() if isinstance(metadata.get("label"), str) else ""


def _terminal_bench_url(metadata):
    return _public_http_url(metadata.get("url"))


def parse_terminal_bench_submissions(submissions):
    """Parse official Terminal-Bench 4.1 submission records.

    The official repository keeps one JSON document per submitted configuration.
    Regrades use a later filename for the same model/agent/effort tuple, so only
    the latest submission for each tuple is retained before ranking.
    """
    if not isinstance(submissions, list) or not submissions:
        raise ValueError("Terminal-Bench: submission list is empty")
    latest = {}
    for submission in submissions:
        if not isinstance(submission, dict):
            raise ValueError("Terminal-Bench: malformed submission entry")
        filename = submission.get("name")
        payload = submission.get("payload")
        if (
            not isinstance(filename, str)
            or not re.fullmatch(r"\d{4}-\d{2}-\d{2}-4-1-0-[^/]+\.json", filename)
            or not isinstance(payload, dict)
        ):
            raise ValueError("Terminal-Bench: malformed submission metadata")
        if payload.get("leaderboard") != "4-1-0":
            raise ValueError("Terminal-Bench: unexpected leaderboard version")
        metadata = payload.get("metadata")
        metrics = payload.get("metrics")
        if not isinstance(metadata, dict) or not isinstance(metrics, dict):
            raise ValueError("Terminal-Bench: missing metadata or metrics")
        agent_display = metadata.get("agent_display")
        model_display = metadata.get("model_display")
        agent = _terminal_bench_display(agent_display) if isinstance(agent_display, dict) else ""
        model = _terminal_bench_display(model_display) if isinstance(model_display, dict) else ""
        model_url = _terminal_bench_url(model_display) if isinstance(model_display, dict) else None
        if model_url is None:
            raise ValueError("Terminal-Bench: invalid model URL")
        effort = metadata.get("reasoning_effort")
        if not agent or not model or not isinstance(effort, str) or not effort.strip():
            raise ValueError("Terminal-Bench: incomplete Agent/model configuration")
        score = _percentage(metrics.get("accuracy"))
        ci = _percentage(metrics.get("accuracy_ci95_half_width"))
        if score is None or ci is None:
            raise ValueError("Terminal-Bench: invalid accuracy or confidence interval")
        config = (model.casefold(), agent.casefold(), effort.strip().casefold())
        candidate = {
            "filename": filename,
            "model": model,
            "agent": agent,
            "effort": effort.strip(),
            "score": score,
            "score_ci": ci,
            "model_display": model_display,
            "model_org": metadata.get("model_org"),
            "date": metadata.get("date"),
        }
        previous = latest.get(config)
        if previous is None or filename > previous["filename"]:
            latest[config] = candidate

    parsed = []
    seen_slugs = set()
    for item in latest.values():
        model_url = _terminal_bench_url(item["model_display"]) or TERMINAL_BENCH_URL
        vendor = (
            _terminal_bench_display(item["model_org"])
            if isinstance(item["model_org"], dict)
            else ""
        )
        slug = re.sub(
            r"[^a-z0-9]+",
            "-",
            f"{item['model']}-{item['agent']}-{item['effort']}".lower(),
        ).strip("-")
        if not slug or slug in seen_slugs:
            raise ValueError("Terminal-Bench: unresolved duplicate model slug")
        seen_slugs.add(slug)
        parsed.append({
            "name": f"{item['model']} · {item['agent']} · 推理 {item['effort']}",
            "slug": slug,
            "vendor": vendor or None,
            "score": item["score"],
            "rank": 0,
            "score_ci": item["score_ci"],
            "votes": None,
            "license": None,
            "context_window": None,
            "price_in": None,
            "price_out": None,
            "currency": "USD",
            "source_url": model_url,
        })
    return _competition_rank(parsed)


async def fetch_terminal_bench_submissions(client, fetch_json):
    """Fetch the official 4.1 JSON submissions advertised by GitHub Contents API."""
    entries = []
    for page in range(1, 21):
        page_entries = await fetch_json(
            client,
            f"{TERMINAL_BENCH_SUBMISSIONS_API}?ref=main&per_page=100&page={page}",
        )
        if not isinstance(page_entries, list) or not page_entries:
            break
        entries.extend(page_entries)
        if len(page_entries) < 100:
            break
    selected = [
        entry for entry in entries
        if isinstance(entry, dict)
        and isinstance(entry.get("name"), str)
        and re.fullmatch(r"\d{4}-\d{2}-\d{2}-4-1-0-[^/]+\.json", entry["name"])
    ]
    if not selected:
        raise ValueError("Terminal-Bench: no 4.1 submissions found")

    semaphore = asyncio.Semaphore(6)

    async def load(entry):
        async with semaphore:
            url = _public_http_url(entry.get("download_url"))
            if url is None or not url.startswith(
                "https://raw.githubusercontent.com/harbor-framework/terminal-bench/"
            ):
                raise ValueError("Terminal-Bench: invalid submission download URL")
            return {"name": entry["name"], "payload": await fetch_json(client, url)}

    submissions = await asyncio.gather(*(load(entry) for entry in selected))
    return parse_terminal_bench_submissions(submissions)


_OPENROUTER_CAPTION = "Model rankings by tokens processed"
_OPENROUTER_HEADER = ["Rank", "Model", "Author", "tokens processed", "Change"]
_OPENROUTER_TOKEN_RE = re.compile(r"^\s*(\d+(?:\.\d+)?)\s*[Tt]\s+tokens\s*$")


def _openrouter_json_ld(soup):
    documents = []
    for tag in soup.find_all("script", type="application/ld+json"):
        try:
            documents.append(json.loads(tag.string or ""))
        except (json.JSONDecodeError, TypeError) as exc:
            raise ValueError("OpenRouter: malformed JSON-LD") from exc
    return documents


def _openrouter_page_date(documents):
    collections = [
        item for item in documents
        if isinstance(item, dict)
        and item.get("@type") == "CollectionPage"
        and item.get("url") == OPENROUTER_RANKINGS_URL
    ]
    if len(collections) != 1:
        raise ValueError("OpenRouter: expected one rankings CollectionPage")
    modified = collections[0].get("dateModified")
    if not isinstance(modified, str) or not re.fullmatch(
        r"\d{4}-\d{2}-\d{2}(?:T00:00:00(?:\.000Z|\+00:00))?", modified
    ):
        raise ValueError("OpenRouter: invalid page modification date")
    local_day = modified[:10]
    try:
        if date.fromisoformat(local_day).isoformat() != local_day:
            raise ValueError
    except ValueError as exc:
        raise ValueError("OpenRouter: invalid page modification date") from exc
    return local_day


def _openrouter_items(documents):
    item_lists = [
        item for item in documents
        if isinstance(item, dict) and item.get("@type") == "ItemList"
    ]
    if len(item_lists) != 1:
        raise ValueError("OpenRouter: expected one rankings ItemList")
    item_list = item_lists[0]
    if item_list.get("name") != "Top LLMs by weekly token usage on OpenRouter":
        raise ValueError("OpenRouter: unexpected ranking definition")
    if item_list.get("numberOfItems") != 10:
        raise ValueError("OpenRouter: expected ten ranked items")
    if item_list.get("itemListOrder") != "https://schema.org/ItemListOrderDescending":
        raise ValueError("OpenRouter: expected descending ranking order")
    elements = item_list.get("itemListElement")
    if not isinstance(elements, list) or len(elements) != 10:
        raise ValueError("OpenRouter: malformed ranking items")
    parsed = []
    for expected_position, item in enumerate(elements, 1):
        if not isinstance(item, dict) or item.get("@type") != "ListItem":
            raise ValueError("OpenRouter: malformed ranking item")
        if item.get("position") != expected_position:
            raise ValueError("OpenRouter: non-contiguous ranking positions")
        name = item.get("name")
        url = _public_http_url(item.get("item"))
        if not isinstance(name, str) or not name.strip() or url is None:
            raise ValueError("OpenRouter: incomplete ranking item")
        if urlsplit(url).hostname != "openrouter.ai":
            raise ValueError("OpenRouter: unexpected ranking item host")
        parsed.append({"position": expected_position, "name": name.strip(), "url": url})
    if len({item["name"] for item in parsed}) != 10 or len({item["url"] for item in parsed}) != 10:
        raise ValueError("OpenRouter: duplicate ranking item")
    return parsed


def _openrouter_table(soup):
    candidates = [
        table for table in soup.find_all("table")
        if table.find("caption") is not None
        and table.find("caption").get_text(" ", strip=True) == _OPENROUTER_CAPTION
    ]
    if len(candidates) != 1:
        raise ValueError("OpenRouter: expected one weekly token table")
    table = candidates[0]
    header_rows = table.select("thead tr")
    if len(header_rows) != 1:
        raise ValueError("OpenRouter: malformed table header")
    header = [cell.get_text(" ", strip=True) for cell in header_rows[0].find_all(["th", "td"])]
    if header != _OPENROUTER_HEADER:
        raise ValueError("OpenRouter: unexpected weekly token columns")
    body_rows = table.select("tbody tr")
    if len(body_rows) != 10:
        raise ValueError("OpenRouter: expected ten weekly token rows")
    parsed = []
    for expected_position, row in enumerate(body_rows, 1):
        cells = row.find_all(["th", "td"])
        if len(cells) != 5:
            raise ValueError("OpenRouter: malformed weekly token row")
        values = [cell.get_text(" ", strip=True) for cell in cells]
        if values[0] != str(expected_position):
            raise ValueError("OpenRouter: unexpected table position")
        token_match = _OPENROUTER_TOKEN_RE.fullmatch(values[3])
        if token_match is None:
            raise ValueError("OpenRouter: invalid weekly token amount")
        tokens_t = float(token_match.group(1))
        if not math.isfinite(tokens_t) or tokens_t <= 0:
            raise ValueError("OpenRouter: weekly token amount must be positive")
        parsed.append({
            "position": expected_position,
            "name": values[1],
            "author": values[2],
            "tokens_t": tokens_t,
            "change": values[4],
        })
    previous = None
    for item in parsed:
        if previous is not None and item["tokens_t"] > previous:
            raise ValueError("OpenRouter: weekly token amounts are not descending")
        previous = item["tokens_t"]
    return parsed


def parse_openrouter_rankings(
    html: str,
    *,
    today: date | None = None,
    max_age_days: int = 7,
) -> tuple[list[dict], str]:
    """Parse OpenRouter's current weekly token-usage ranking.

    The visible table and JSON-LD ItemList must agree. The page modification
    date must be no more than one week old and is exposed separately as
    upstream metadata. It is not an evaluation date and is never inferred from
    fetch time.
    """
    soup = BeautifulSoup(html, "html.parser")
    documents = _openrouter_json_ld(soup)
    upstream_date = _openrouter_page_date(documents)
    items = _openrouter_items(documents)
    table_rows = _openrouter_table(soup)
    for item, row in zip(items, table_rows):
        if (
            item["position"] != row["position"]
            or item["name"] != row["name"]
        ):
            raise ValueError("OpenRouter: JSON-LD and table rows differ")

    observed = today or datetime.now(timezone.utc).date()
    page_day = date.fromisoformat(upstream_date)
    if page_day > observed:
        raise ValueError("OpenRouter: page date is in the future")
    age_days = (observed - page_day).days
    if age_days > max_age_days:
        raise ValueError("OpenRouter: weekly usage ranking is stale")

    rows = []
    for item, table_row in zip(items, table_rows):
        rows.append({
            "name": item["name"],
            "slug": item["url"].rstrip("/").rsplit("/", 1)[-1],
            "vendor": table_row["author"],
            "score": table_row["tokens_t"],
            "rank": item["position"],
            "score_ci": None,
            "votes": None,
            "license": None,
            "context_window": None,
            "price_in": None,
            "price_out": None,
            "currency": "USD",
            "source_url": item["url"],
            "weekly_change": table_row["change"],
        })
    return rows, upstream_date
