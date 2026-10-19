"""Official Aider/EvalPlus public data adapters; absent facts remain null.

Sources are Apache-2.0 repositories; source links retain attribution.
Aider: https://github.com/Aider-AI/aider/blob/main/LICENSE.txt
EvalPlus: https://github.com/evalplus/evalplus.github.io/blob/master/LICENSE
"""
import math
import re

import yaml

AIDER_DATA_URL = "https://raw.githubusercontent.com/Aider-AI/aider/main/aider/website/_data/polyglot_leaderboard.yml"
EVALPLUS_DATA_URL = "https://raw.githubusercontent.com/evalplus/evalplus.github.io/master/results.json"


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


def _rank(rows):
    # Sequential positions preserve every tied row in the legacy store, which
    # deduplicates equal ranks. Equal percentages do not imply meaningful order.
    rows.sort(key=lambda row: (-row["score"], row["name"].casefold()))
    for rank, row in enumerate(rows, 1):
        row["rank"] = rank
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
