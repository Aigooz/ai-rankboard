"""Optional schema-v3 trust metadata; fetch observations are not upstream dates."""
from datetime import datetime, timezone

STALE_AFTER_HOURS = 48


def metric_info(board):
    kind = board["score_type"]
    descriptions = {
        "arena_elo": ("Arena 偏好评分", "来自盲测偏好比较；评分受样本、类别与评测时间影响。", None),
        "livebench": ("LiveBench 得分", "来自指定评测版本与类别；不同版本不可直接比较。", None),
        "swe_bench": ("SWE-bench 解决率", "来源榜单的任务解决率；代理框架和评测子集也会影响结果。", "%"),
        "aa_index": ("来源榜单指标", "保留来源榜单分数；速度、性价比与能力榜的指标定义不同，请查阅来源。", None),
    }
    name, description, unit = descriptions.get(kind, ("来源分数", "具体定义请查阅来源，不跨榜直接比较。", None))
    return {"name": name, "description": description, "unit": unit,
            "higherIsBetter": True if kind in {"arena_elo", "livebench", "swe_bench"} else None,
            "comparableAcrossBoards": False}


def health_info(board, history, now):
    success = board.get("last_success_at")
    stale = None
    if success:
        try:
            timestamp = datetime.fromisoformat(success.replace("Z", "+00:00"))
            if timestamp.tzinfo is not None:
                stale = (now - timestamp).total_seconds() > STALE_AFTER_HOURS * 3600
        except (ValueError, TypeError):
            pass
    anomalies = []
    if len(history) >= 2:
        previous, latest = history[-2:]
        # Unknown/different versions are not a comparable baseline.
        if latest["evaluationVersion"] is not None and latest["evaluationVersion"] == previous["evaluationVersion"]:
            if previous["modelCount"] >= 10 and latest["modelCount"] < previous["modelCount"] / 2:
                anomalies.append({"code": "model_count_drop", "severity": "warning",
                                  "message": "较前次抓取模型数减少超过一半；可能是来源变动或解析变化，请核对来源。"})
            old = {entry["slug"]: entry["rank"] for entry in previous["entries"]}
            if any(abs(entry["rank"] - old[entry["slug"]]) >= 20 for entry in latest["entries"] if entry["slug"] in old):
                anomalies.append({"code": "rank_shift", "severity": "warning",
                                  "message": "部分模型名次较前次抓取变化至少20位；不代表数据错误，请核对来源。"})
    return {"latestAttemptAt": board.get("last_attempt_at"),
            "latestAttemptStatus": board.get("last_status"),
            "error": "抓取失败；保留上次成功数据。请检查来源可用性或服务端日志。" if board.get("last_error") else None,
            "lastSuccessAt": success, "stale": stale, "staleAfterHours": STALE_AFTER_HOURS,
            "anomalies": anomalies}
