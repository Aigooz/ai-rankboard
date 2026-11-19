"""Official Aider/EvalPlus public data adapters; absent facts remain null.

Sources are public benchmark releases; source links retain attribution.
Aider: https://github.com/Aider-AI/aider/blob/main/LICENSE.txt
EvalPlus: https://github.com/evalplus/evalplus.github.io/blob/master/LICENSE
HELM: https://github.com/stanford-crfm/helm/blob/main/LICENSE
"""
import asyncio
import json
import math
import re
from urllib.parse import urlsplit

import yaml

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
HELM_PAGE_URL = "https://crfm.stanford.edu/helm/classic/latest/"
HELM_RELEASE = "v0.4.0"
HELM_BASE_URL = (
    "https://storage.googleapis.com/crfm-helm-public/classic/"
    f"benchmark_output/releases/{HELM_RELEASE}"
)
HELM_SUMMARY_URL = f"{HELM_BASE_URL}/summary.json"
HELM_SCHEMA_URL = f"{HELM_BASE_URL}/schema.json"
HELM_CORE_URL = f"{HELM_BASE_URL}/groups/core_scenarios.json"


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


def parse_helm_classic(summary, schema, tables):
    """Parse HELM Classic's official Accuracy table as one neutral ranking.

    "Mean win rate" is the mean fraction of other models a model outperforms,
    not task accuracy. The release stores it as a 0-1 value; the app stores the
    same quantity as a percentage for display. Unknown tables/columns are
    rejected so a silent layout change cannot create a misleading score.
    """
    if not isinstance(summary, dict):
        raise ValueError("HELM: expected release summary")
    release = summary.get("release")
    release_date = summary.get("date")
    if release != HELM_RELEASE or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(release_date or "")):
        raise ValueError("HELM: unsupported release")

    if not isinstance(schema, dict) or not isinstance(schema.get("models"), list):
        raise ValueError("HELM: expected model schema")
    metadata = {}
    for model in schema["models"]:
        if not isinstance(model, dict):
            continue
        name = model.get("display_name")
        if not isinstance(name, str) or not name.strip():
            continue
        # A few HELM display names intentionally cover multiple deployments
        # (for example text-bison and text-bison-32k). Only the displayed name
        # occurs in the Accuracy table, so never merge ambiguous metadata.
        if model.get("name") in metadata:
            raise ValueError("HELM: duplicate model name")
        params = model.get("num_parameters")
        if isinstance(params, bool) or (
            params is not None and (not isinstance(params, (int, float)) or not math.isfinite(params))
        ):
            raise ValueError("HELM: invalid model parameter count")
        metadata[model.get("name")] = {
            "display_name": name,
            "vendor": model.get("creator_organization"),
            "params_b": params / 1_000_000_000 if isinstance(params, (int, float)) else None,
            "release_date": model.get("release_date"),
        }
    by_display_name = {}
    for model in metadata.values():
        by_display_name.setdefault(model["display_name"], []).append(model)

    if not isinstance(tables, list):
        raise ValueError("HELM: expected table list")
    accuracy = next(
        (table for table in tables
         if isinstance(table, dict) and table.get("title") == "Accuracy"),
        None,
    )
    if accuracy is None:
        raise ValueError("HELM: Accuracy table not found")
    header = accuracy.get("header")
    if not isinstance(header, list) or len(header) < 2:
        raise ValueError("HELM: malformed Accuracy header")
    labels = [cell.get("value") if isinstance(cell, dict) else None for cell in header]
    if labels[:2] != ["Model/adapter", "Mean win rate"]:
        raise ValueError("HELM: unexpected Accuracy columns")

    rows = accuracy.get("rows")
    if not isinstance(rows, list):
        raise ValueError("HELM: malformed Accuracy rows")
    parsed = []
    seen_models = set()
    seen_slugs = set()
    for row in rows:
        if not isinstance(row, list) or len(row) != len(header):
            raise ValueError("HELM: malformed Accuracy row")
        name_cell, score_cell = row[0], row[1]
        if not isinstance(name_cell, dict) or not isinstance(score_cell, dict):
            raise ValueError("HELM: malformed Accuracy cells")
        name = name_cell.get("value")
        raw_score = score_cell.get("value")
        if not isinstance(name, str) or not name.strip() or name in seen_models:
            raise ValueError("HELM: duplicate or empty model")
        # A missing value means the model is not ranked on this table. It must
        # not be converted to zero or carried from a differently named row.
        if isinstance(raw_score, bool) or not isinstance(raw_score, (int, float)):
            raise ValueError("HELM: incomplete Accuracy row")
        value = float(raw_score)
        if not math.isfinite(value) or not 0 <= value <= 1:
            raise ValueError("HELM: invalid Mean win rate")
        candidates = by_display_name.get(name, [])
        if len(candidates) > 1:
            # The official table gives no adapter/deployment key. Duplicating
            # one displayed score would invent model identities, so omit this
            # ambiguous display label while retaining all unambiguous rows.
            continue
        if not candidates:
            raise ValueError("HELM: Accuracy model absent from schema")
        model = candidates[0]
        slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
        if not slug or slug in seen_slugs:
            raise ValueError("HELM: unresolved model slug")
        release_date_value = model["release_date"]
        if release_date_value is not None:
            if not isinstance(release_date_value, str) or not re.fullmatch(
                r"\d{4}-\d{2}-\d{2}", release_date_value
            ):
                raise ValueError("HELM: invalid model release date")
            try:
                from datetime import date
                date.fromisoformat(release_date_value)
            except ValueError as exc:
                raise ValueError("HELM: invalid model release date") from exc
        vendor = model["vendor"]
        seen_models.add(name)
        seen_slugs.add(slug)
        parsed.append({
            "name": name,
            "slug": slug,
            "vendor": vendor.strip() if isinstance(vendor, str) and vendor.strip() else None,
            "params_b": model["params_b"],
            "score": value * 100,
            "rank": 0,
            "score_ci": None,
            "votes": None,
            "license": None,
            "context_window": None,
            "price_in": None,
            "price_out": None,
            "currency": "USD",
            "source_url": HELM_PAGE_URL,
            "release_date": release_date_value,
        })
    return _competition_rank(parsed)
