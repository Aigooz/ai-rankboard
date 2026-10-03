"""Registry of leaderboard pages aggregated from modelsage.cn."""

AA = "aa_index"
ARENA = "arena_elo"

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
]

BOARD_BY_SLUG = {b["slug"]: b for b in BOARDS}

