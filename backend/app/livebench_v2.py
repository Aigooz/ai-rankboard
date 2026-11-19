"""LiveBench 2.0 public releases; discovery fails closed on a changed layout."""
import json
import math
import re
from datetime import date
from urllib.parse import urljoin, urlsplit

from bs4 import BeautifulSoup

BASE_URL = "https://livebench.ai"


def latest_release(html: str, bundle: str, today: date | None = None) -> str:
    candidates = []
    for value in re.findall(r'\[(?:"20\d{6}"\s*,\s*)*"20\d{6}"\]', bundle):
        releases = json.loads(value)
        try:
            dates = [date.fromisoformat(f"{item[:4]}-{item[4:6]}-{item[6:]}") for item in releases]
        except ValueError:
            continue
        if dates == sorted(set(dates)) and all(day <= (today or date.today()) for day in dates):
            candidates.append(releases)
    if "LiveBench 2.0" not in html or len(candidates) != 1:
        raise ValueError("LiveBench 2.0 release discovery changed or is ambiguous")
    return candidates[0][-1]


def upstream_updated_at(bundle: str) -> str | None:
    dates = re.findall(r'"(\d{1,2} (?:January|February|March|April|May|June|July|August|September|October|November|December) 20\d{2})"', bundle)
    if len(set(dates)) != 1:
        return None
    from datetime import datetime
    try:
        value = datetime.strptime(dates[0], "%d %B %Y").date()
        return value.isoformat() if value <= date.today() else None
    except ValueError:
        return None


def bundle_url(html: str) -> str:
    soup = BeautifulSoup(html, "html.parser")
    urls = [urljoin(BASE_URL + "/", tag["src"]) for tag in soup.select("script[src]")]
    urls = [url for url in urls if urlsplit(url).netloc == "livebench.ai"
            and re.fullmatch(r"/static/js/main\.[a-z0-9]+\.js", urlsplit(url).path)]
    if len(urls) != 1:
        raise ValueError("LiveBench 2.0 main bundle not found")
    return urls[0]


def parse_board(table: list[dict], metadata: dict, costs: list[dict], category: str) -> list[dict]:
    from .scraper import _livebench_vendor

    categories = {key: value for key, value in metadata.items() if key != "_overall_weights"}
    weights = metadata.get("_overall_weights", {})
    if (not categories or not isinstance(weights, dict)
            or any(not isinstance(tasks, list) or not tasks or any(not isinstance(task, str) for task in tasks)
                   for tasks in categories.values())
            or any(key not in categories or isinstance(value, bool) or not isinstance(value, (int, float))
                   or not math.isfinite(value) or value <= 0 for key, value in weights.items())):
        raise ValueError("LiveBench 2.0 category/weight schema changed")
    wanted = list(categories) if category == "*" else [category]
    if any(key not in categories for key in wanted):
        raise ValueError("LiveBench 2.0 category missing")
    prices = {row.get("model"): row for row in costs}
    seen = set()
    result = []
    for row in table:
        model = row.get("model", "").strip()
        if not model or model in seen:
            raise ValueError("LiveBench 2.0 empty or duplicate model")
        seen.add(model)
        values = []
        for key in wanted:
            scores = []
            for task in categories[key]:
                if task not in row:
                    raise ValueError("LiveBench 2.0 task column missing")
                raw = str(row[task] if row[task] is not None else "").strip()
                if raw.lower() in {"", "-", "n/a", "na"}:
                    continue
                value = float(raw)
                if not math.isfinite(value) or not 0 <= value <= 100:
                    raise ValueError("LiveBench 2.0 score out of range")
                scores.append(value)
            if scores:
                values.append((sum(scores) / len(scores), weights.get(key, 1) if category == "*" else 1))
        if not values:
            continue
        score = round(sum(value * weight for value, weight in values) / sum(weight for _, weight in values), 2)
        price = prices.get(model, {})

        def valid_price(key):
            try:
                value = float(price[key])
                return value if math.isfinite(value) and value >= 0 else None
            except (KeyError, ValueError, TypeError):
                return None

        # Keep the source id visible and isolated: effort metadata is not in the CSV.
        name = re.sub(r"^gpt-(\d+(?:\.\d+)?)-", r"GPT-\1 ", model)
        name = re.sub(r"\b(astra|sol|terra|luna)\b", lambda m: m[0].capitalize(), name)
        result.append({
            "slug": "livebench-v2-" + model, "name": name + " [LiveBench 2.0]",
            "vendor": _livebench_vendor(model), "rank": 0, "score": score,
            "score_ci": None, "votes": None, "license": None, "context_window": None,
            "price_in": valid_price("input_price_per_million"),
            "price_out": valid_price("output_price_per_million"), "currency": "USD",
            "source_url": BASE_URL + "/",
        })
    result.sort(key=lambda row: (-row["score"], row["name"]))
    for index, row in enumerate(result, 1):
        row["rank"] = index
    if not result:
        raise ValueError("LiveBench 2.0 parsed no scores")
    return result
