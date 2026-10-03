from pathlib import Path

BASE_URL = "https://modelsage.cn"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)
REQUEST_TIMEOUT = 30.0
MAX_RETRIES = 2
FETCH_INTERVAL_HOURS = 6

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
DB_PATH = DATA_DIR / "leaderboards.db"

