from pathlib import Path
import os
import secrets

BASE_URL = "https://modelsage.cn"
LIVEBENCH_URL = "https://livebench.ai"
LIVEBENCH_RELEASE = "2026-06-25"
SWE_BENCH_DATA_URL = (
    "https://raw.githubusercontent.com/SWE-bench/SWE-bench.github.io/"
    "master/data/leaderboards.json"
)
SWE_BENCH_URL = "https://www.swebench.com"
AA_ARTICLES_URL = "https://artificialanalysis.ai/articles"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)
REQUEST_TIMEOUT = 30.0
MAX_RETRIES = 2
FETCH_INTERVAL_HOURS = 6

# Local runs get an ephemeral token so a refresh endpoint is never public by
# accident. Set ALLOW_UNAUTHENTICATED_REFRESH only for a trusted development
# environment.
ADMIN_TOKEN = os.getenv("ADMIN_TOKEN", "").strip() or secrets.token_urlsafe(24)
ALLOW_UNAUTHENTICATED_REFRESH = (
    os.getenv("ALLOW_UNAUTHENTICATED_REFRESH", "").strip().lower() in {"1", "true", "yes"}
)
ALLOWED_ORIGINS = [
    origin.strip()
    for origin in os.getenv("ALLOWED_ORIGINS", "").split(",")
    if origin.strip()
]

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
DB_PATH = DATA_DIR / "leaderboards.db"
