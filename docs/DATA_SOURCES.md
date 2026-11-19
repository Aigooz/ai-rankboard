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

## BFCL V4

- 项目与归属：UC Berkeley Gorilla / BFCL；完整著作权及条款以原仓库许可证为准。
- 榜单：<https://gorilla.cs.berkeley.edu/leaderboard.html>
- 原始数据：<https://gorilla.cs.berkeley.edu/data_overall.csv>
- 许可证：Apache License 2.0；原文：<https://github.com/ShishirPatil/gorilla/blob/main/LICENSE>
- 聚合处理：读取官方 CSV 的 `Overall Acc`，保留 FC、Prompt、thinking 等调用配置和同名并列名次；调用配置不同则视为不同成绩记录，不取模型在多种配置中的最好值。

## Terminal-Bench 4.1

- 项目与归属：Terminal-Bench / Laude Institute；完整著作权及条款以原仓库许可证为准。
- 榜单：<https://hub.harborframework.com/datasets/terminal-bench/terminal-bench/latest?tab=leaderboard&leaderboard=4-1-0>
- 原始数据：Harbor 官方 GitHub 仓库 `leaderboard/submissions/` 中的公开 JSON submissions。
- 许可证：Apache License 2.0；原文：<https://github.com/laude-institute/terminal-bench/blob/main/LICENSE>
- 聚合处理：保留 Agent、模型、推理档位和官方 95% 置信区间；缺少任一配置字段时拒绝整榜更新，不把 Agent 成绩写成模型独立能力。页面结构变化会让该来源失败并保留旧数据。

## Stanford HELM Classic

- 项目与归属：Stanford CRFM / HELM；完整著作权及条款以原仓库许可证为准。
- 官方入口：<https://crfm.stanford.edu/helm/classic/latest/>
- 固定评测版本：`v0.4.0`（2023-11-17）。这是历史评测版本，不是本站抓取日期，也不代表持续评测了最新模型。
- 原始数据：<https://storage.googleapis.com/crfm-helm-public/classic/benchmark_output/releases/v0.4.0/groups/core_scenarios.json>
- 元数据：同版本 `schema.json` 与 `summary.json`。
- 许可证：Apache License 2.0；原文：<https://github.com/stanford-crfm/helm/blob/main/LICENSE>
- 聚合处理：只读取 `Accuracy` 表的 `Mean win rate`，按官方值 0–1 存储为 0–100 的百分数并展示 `%`。该值是模型两两胜出的平均比例，**不是任务准确率**，也不与其他榜分数合并。并列分数共享同一名次；显示名在官方 schema 中对应多个部署而表内没有部署键时，保留不含歧义的行并跳过歧义行，不复制或猜测身份。

以上仅说明新增来源；并非本项目自身的开源授权声明。原始许可证副本已随 APK 资产分发，并在网页保留 [Aider 许可证](../web/licenses/Aider-Apache-2.0.txt) 与 [EvalPlus 许可证](../web/licenses/EvalPlus-Apache-2.0.txt)。Apache License 2.0 正文见 <https://www.apache.org/licenses/LICENSE-2.0>。
