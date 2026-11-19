"""Optional schema-v3 trust metadata; fetch observations are not upstream dates."""
from datetime import datetime, timezone

STALE_AFTER_HOURS = 48


def metric_info(board):
    kind = board["score_type"]
    descriptions = {
        "arena_elo": ("Arena 偏好评分", "来自盲测偏好比较；评分受样本、类别与评测时间影响。", None),
        "livebench": ("LiveBench 1.0 历史题集得分", "来自1.0固定历史题集与类别，不是当前2.0榜单；抓取成功不代表题集或成绩刚更新，不保证收录最新模型，不同版本不可直接比较。", None),
        "livebench_v2": ("LiveBench 2.0 得分", "官方当前 2.0 题集。分类分为有效任务均值；综合分按该 release 的 _overall_weights 加权，未指定权重为1，空白/n/a不补零。模型配置保留官方ID，不推测未提供的推理档位；与1.0旧题集不可直接比较。", "%"),
        "swe_bench": ("SWE-bench 解决率", "来源榜单的任务解决率；代理框架和评测子集也会影响结果。", "%"),
        "aider_polyglot": ("Aider 双轮通过率", "Polyglot 代码编辑任务的 pass_rate_2；保留模型/推理与编辑配置，非单轮 pass@1。并列分数以名称排序不代表能力差距；公开结果可能长期未更新，不保证包含最新模型。", "%"),
        "evalplus": ("EvalPlus pass@1", "指定 HumanEval+ 或 MBPP+ 测试集的 pass@1 百分比；基础与指令模型提示方式不同。并列分数以名称排序；公开结果可能长期未更新，不保证包含最新模型。", "%"),
        "bfcl": ("BFCL 综合准确率", "Berkeley Function Calling Leaderboard V4 的综合调用准确率；保留 FC/Prompt 等调用配置，不同调用模式的成绩不合并。", "%"),
        "terminal_bench": ("Terminal-Bench 解决率", "Terminal-Bench 4.1 在指定 Agent、模型与推理档位下的解决率；成绩属于该完整配置，不是独立模型能力。", "%"),
        "openrouter_usage": ("OpenRouter 周 token 使用量", "OpenRouter 官方排行榜中最接近“最近完整一周”的 Top 10 模型 token 处理量，单位为万亿 tokens/周。它反映该聚合平台的使用趋势，不代表模型能力、质量、市场份额或全球调用量。", "万亿 tokens/周"),
        "aa_index": ("来源榜单指标", "保留来源榜单分数；速度、性价比与能力榜的指标定义不同，请查阅来源。", None),
    }
    name, description, unit = descriptions.get(kind, ("来源分数", "具体定义请查阅来源，不跨榜直接比较。", None))
    return {"name": name, "description": description, "unit": unit,
            "higherIsBetter": True if kind in {"arena_elo", "livebench", "livebench_v2", "swe_bench", "aider_polyglot", "evalplus", "bfcl", "terminal_bench", "openrouter_usage"} else None,
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
