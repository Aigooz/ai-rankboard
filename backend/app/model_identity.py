"""Stable model identity used to join rows from different leaderboards."""

import re


def _clean_name(name: str) -> str:
    value = re.sub(r"\s*with fallback\s*", " ", name.strip(), flags=re.I)
    value = value.replace("（", "(").replace("）", ")")
    return re.sub(r"\s+", " ", value).strip()


def canonical_slug(name: str) -> str:
    """Map display variants such as “Claude 4.5 Sonnet (non-reasoning)” and
    “Claude Sonnet 4.5 (non-reasoning)” to one stable slug."""
    value = _clean_name(name)
    lower = value.lower()

    # Anthropic puts version/family in different orders across boards.
    family_first = re.search(r"claude\s+(opus|sonnet|haiku|fable)\s+(\d+(?:\.\d+)?)", lower)
    version_first = re.search(r"claude\s+(\d+(?:\.\d+)?)\s+(opus|sonnet|haiku|fable)", lower)
    match = family_first or version_first
    if match:
        if family_first:
            family, version = family_first.groups()
        else:
            version, family = version_first.groups()
        value = "claude %s %s %s" % (family, version, value[match.end():])

    match = re.search(r"gpt[- ]?(\d+(?:\.\d+)?o?)\s+(mini|nano|codex|luna|sol)(.*)", lower)
    if match:
        version, variant, rest = match.groups()
        value = "gpt %s %s%s" % (version, variant, rest)

    match = re.search(r"deepseek\s+v?(\d+(?:\.\d+)?)(?:\s+(pro|flash|terminus|exp))?(.*)", lower)
    if match:
        version, variant, rest = match.groups()
        value = "deepseek v%s%s%s" % (version, " " + variant if variant else "", rest)

    match = re.search(r"gemini\s+(\d+(?:\.\d+)?)(?:\s+(pro|flash|ultra|nano))?(.*)", lower)
    if match:
        version, variant, rest = match.groups()
        value = "gemini %s%s%s" % (version, " " + variant if variant else "", rest)

    value = value.lower()
    value = value.replace("(", " ").replace(")", " ")
    value = value.replace("+", "-plus").replace(".", "-")
    value = re.sub(r"[^a-z0-9\u4e00-\u9fff]+", "-", value)
    return value.strip("-")


def canonical_model_slug(display_name: str, source_slug: str) -> str:
    return canonical_slug(display_name) or source_slug
