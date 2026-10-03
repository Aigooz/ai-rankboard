"""Registry of leaderboard pages aggregated from public sources."""

AA = "aa_index"
ARENA = "arena_elo"
LIVEBENCH = "livebench"
SWE_BENCH = "swe_bench"

BOARDS = [
    {"slug": "overall", "name": "综合能力", "dimension": "overall", "score_type": AA, "path": "/leaderboards/overall"},
    {"slug": "coding", "name": "代码能力", "dimension": "coding", "score_type": AA, "path": "/leaderboards/coding"},
    {"slug": "agent", "name": "Agent 智能体", "dimension": "agent", "score_type": AA, "path": "/leaderboards/agent"},
    {"slug": "speed", "name": "生成速度", "dimension": "speed", "score_type": AA, "path": "/leaderboards/speed"},
    {"slug": "value", "name": "性价比", "dimension": "value", "score_type": AA, "path": "/leaderboards/value"},
    {"slug": "arena-text", "name": "Arena 写作盲测", "dimension": "writing", "score_type": ARENA, "path": "/leaderboards/arena/text/overall"},
    {"slug": "arena-code", "name": "Arena 代码盲测", "dimension": "coding", "score_type": ARENA, "path": "/leaderboards/arena/code/overall"},
    {"slug": "arena-agent", "name": "Arena 智能体盲测", "dimension": "agent", "score_type": ARENA, "path": "/leaderboards/arena/agent"},
    {"slug": "arena-vision", "name": "Arena 视觉理解", "dimension": "multimodal", "score_type": ARENA, "path": "/leaderboards/arena/vision/overall"},
    {"slug": "arena-search", "name": "Arena 搜索", "dimension": "search", "score_type": ARENA, "path": "/leaderboards/arena/search/overall"},
    {"slug": "arena-image", "name": "Arena 图像生成", "dimension": "multimodal", "score_type": ARENA, "path": "/leaderboards/arena/text-to-image/overall"},
    {"slug": "arena-video", "name": "Arena 视频生成", "dimension": "multimodal", "score_type": ARENA, "path": "/leaderboards/arena/text-to-video/overall"},
    {"slug": "livebench-overall", "name": "LiveBench 综合", "dimension": "overall", "score_type": LIVEBENCH, "kind": LIVEBENCH, "category": "*", "release": "2026-06-25"},
    {"slug": "livebench-coding", "name": "LiveBench 代码", "dimension": "coding", "score_type": LIVEBENCH, "kind": LIVEBENCH, "category": "Coding", "release": "2026-06-25"},
    {"slug": "livebench-agentic-coding", "name": "LiveBench 智能体代码", "dimension": "coding", "score_type": LIVEBENCH, "kind": LIVEBENCH, "category": "Agentic Coding", "release": "2026-06-25"},
    {"slug": "livebench-writing", "name": "LiveBench 语言", "dimension": "writing", "score_type": LIVEBENCH, "kind": LIVEBENCH, "category": "Language", "release": "2026-06-25"},
    {"slug": "livebench-math", "name": "LiveBench 数学", "dimension": "math", "score_type": LIVEBENCH, "kind": LIVEBENCH, "category": "Mathematics", "release": "2026-06-25"},
    {"slug": "livebench-data-analysis", "name": "LiveBench 数据分析", "dimension": "analysis", "score_type": LIVEBENCH, "kind": LIVEBENCH, "category": "Data Analysis", "release": "2026-06-25"},
    {"slug": "swe-bench-verified", "name": "SWE-bench Verified", "dimension": "coding", "score_type": SWE_BENCH, "kind": SWE_BENCH, "swe_board": "Verified"},
    {"slug": "swe-bench-multilingual", "name": "SWE-bench 多语言", "dimension": "coding", "score_type": SWE_BENCH, "kind": SWE_BENCH, "swe_board": "Multilingual"},
    {"slug": "swe-bench-multimodal", "name": "SWE-bench Multimodal", "dimension": "multimodal", "score_type": SWE_BENCH, "kind": SWE_BENCH, "swe_board": "Multimodal"},
]

BOARD_BY_SLUG = {b["slug"]: b for b in BOARDS}
