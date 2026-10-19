# 新增公开榜单数据来源声明

本项目是独立聚合客户端，与以下评测项目无隶属关系。成绩、名称和配置来自对应项目的公开仓库；未提供的模型许可、API 价格及上游发布时间不作推断。

## Aider Polyglot

- 项目与归属：Aider / Aider-AI；完整著作权及条款以原仓库许可证为准。
- 榜单：<https://aider.chat/docs/leaderboards/>
- 原始数据：<https://github.com/Aider-AI/aider/blob/main/aider/website/_data/polyglot_leaderboard.yml>
- 许可证：Apache License 2.0；原文：<https://github.com/Aider-AI/aider/blob/main/LICENSE.txt>
- 聚合处理：读取 `pass_rate_2` 百分比，保留模型推理配置和编辑方式；不将整次评测成本视作 API token 单价。相同分数按显示名称稳定排列，序号不表示有统计显著差距。

## EvalPlus

- 项目与归属：EvalPlus / evalplus；完整著作权及条款以原仓库许可证为准。
- 榜单：<https://evalplus.github.io/leaderboard.html>
- 原始数据：<https://github.com/evalplus/evalplus.github.io/blob/master/results.json>
- 许可证：Apache License 2.0；原文：<https://github.com/evalplus/evalplus.github.io/blob/master/LICENSE>
- 聚合处理：分别读取 HumanEval+、MBPP+ 的 `pass@1` 百分比，缺失成绩不补零。评测版本依据榜单公开说明，不把抓取时间作为上游更新时间。

以上仅说明新增来源；并非本项目自身的开源授权声明。原始许可证副本已随 APK 资产分发，并在网页保留 [Aider 许可证](../web/licenses/Aider-Apache-2.0.txt) 与 [EvalPlus 许可证](../web/licenses/EvalPlus-Apache-2.0.txt)。Apache License 2.0 正文见 <https://www.apache.org/licenses/LICENSE-2.0>。
