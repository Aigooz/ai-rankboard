"""Optional schema-v3 trust metadata; fetch observations are not upstream dates."""
from datetime import datetime, timezone

STALE_AFTER_HOURS = 48


def metric_info(board):
    kind = board["score_type"]
    descriptions = {
        "arena_elo": ("Arena 偏好评分", "来自盲测偏好比较；评分受样本、类别与评测时间影响。", None),
        "livebench": ("LiveBench 得分", "来自指定评测版本与类别；不同版本不可直接比较。", None),
        "swe_bench": ("SWE-bench 解决率", "来源榜单的任务解决率；代理框架和评测子集也会影响结果。", "%"),
        "aider_polyglot": ("Aider 双轮通过率", "Polyglot 代码编辑任务的 pass_rate_2；保留模型/推理与编辑配置，非单轮 pass@1。并列分数以名称排序不代表能力差距；公开结果可能长期未更新，不保证包含最新模型。", "%"),
        "evalplus": ("EvalPlus pass@1", "指定 HumanEval+ 或 MBPP+ 测试集的 pass@1 百分比；基础与指令模型提示方式不同。并列分数以名称排序；公开结果可能长期未更新，不保证包含最新模型。", "%"),
        "hle": ("HLE 官方公开历史准确率", "官网公开历史结果表，数据集版本2025-04-03、裁判o3-mini；并非实时提交仪表盘，2025-04是题集版本而非所有模型成绩发布日期。排除标*的纯文本子集条目；不与HLE-Diamond或带工具结果混比。", "%"),
        "hle_diamond": ("HLE-Diamond 准确率", "官方2026-09-22发布页的1000题精选子集结果，reasoning high、无工具；不是原版2500题HLE，也不是带工具或max配置，不能直接跨版本比较。", "%"),
        "aa_index": ("来源榜单指标", "保留来源榜单分数；速度、性价比与能力榜的指标定义不同，请查阅来源。", None),
    }
    name, description, unit = descriptions.get(kind, ("来源分数", "具体定义请查阅来源，不跨榜直接比较。", None))
    return {"name": name, "description": description, "unit": unit,
            "higherIsBetter": True if kind in {"arena_elo", "livebench", "swe_bench", "aider_polyglot", "evalplus", "hle", "hle_diamond"} else None,
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
