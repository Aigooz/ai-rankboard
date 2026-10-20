# AI 排行榜

[![CI](https://github.com/Aigooz/ai-rankboard/actions/workflows/ci.yml/badge.svg)](https://github.com/Aigooz/ai-rankboard/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Aigooz/ai-rankboard)](https://github.com/Aigooz/ai-rankboard/releases/latest)

聚合 ModelSage、Arena、LiveBench 与 SWE-bench 的 AI 模型榜单，提供模型详情、能力可视化、趋势、选型与对比。Android 离线优先，网页无需安装。

**[下载 Android 安装包](https://github.com/Aigooz/ai-rankboard/releases/latest/download/app-release.apk)** · **[打开网页版](https://aigooz.github.io/ai-rankboard/)** · [全部版本](https://github.com/Aigooz/ai-rankboard/releases) · [更新日志](CHANGELOG.md) · [反馈问题](https://github.com/Aigooz/ai-rankboard/issues)

## 下载与使用

| 平台 | 入口与要求 |
| --- | --- |
| Android | [正式 APK](https://github.com/Aigooz/ai-rankboard/releases/latest/download/app-release.apk)，Android 8.0+；当前版本 **0.20.23** |
| 网页 | [GitHub Pages](https://aigooz.github.io/ai-rankboard/)，现代浏览器直接使用 |
| iOS | 提供 [源码](ios/) 和 [构建工作流](https://github.com/Aigooz/ai-rankboard/actions/workflows/ios.yml)，iOS 17+；产物未签名，不是可直接安装的正式版 |

GitHub Release 下载不畅时，可使用 [官方更新通道 APK](https://raw.githubusercontent.com/Aigooz/ai-rankboard-updates/main/app-release.apk)。网页及下载服务受网络环境影响。

安装时选择 `app-release.apk`，不是 GitHub 自动生成的 **Source code (zip)**。首次安装需要 Android 的“允许安装未知应用”授权。官方新版沿用原签名，可覆盖升级，无需先卸载旧版。

### 最近更新

- **0.20.23**：完善模型详情规格、逐榜真实成绩、价格记录与同厂商跳转；支持暂无成绩的模型资料页，雷达默认折叠。

- **0.20.22**：移除过时的 HLE 评分榜，保留 HLE 测评说明与公开示例入口。
- **0.20.21**：修复更新下载被"过慢"误打断的问题；HLE 首页直达入口与推理榜优先排序。
- **0.20.20**：新增 HLE（Humanity's Last Exam）测评，官网公开的原版 9 条与 HLE-Diamond 8 条成绩，附测评说明与官方公开示例题。
- **0.20.19**：行业资讯应用内原生阅读，不再依赖 WebView/iframe；正文随快照抓取，最新 6 篇可原生阅读，其余显示未获取并可打开原文。
- **0.20.18**：说明默认精简为一行、点击展开；修复来源筛选；新增 Aider Polyglot、EvalPlus HumanEval+ 和 MBPP+ 三张真实榜单，网页排名优先显示。

- **0.20.17**：Android 完善榜单来源、指标、版本、过期与失败状态及历史观测；网页新增收藏和独立对比；后端保留真实抓取历史。

- **0.20.16**：更新中心默认连接官方源，无需填写地址；榜单同步与应用升级分别设置提醒，完善检查状态、通知权限及安装授权流程。
- **0.20.15**：Android 新增国内/国外模型筛选，可与搜索、厂商、许可等条件组合。
- **0.20.14**：更新 Android 自适应及单色启动图标。
- **0.20.13**：接入 LiveBench 八类榜单，网页增加多维能力矩阵。

完整历史见 [CHANGELOG](CHANGELOG.md)。

## 功能与平台

| 功能 | Android | 网页 |
| --- | --- | --- |
| 多维榜单、搜索与筛选 | 支持，含国内/国外归属筛选 | 支持，具体筛选项与 Android 有差异 |
| 模型详情、成绩与价格 | 支持 | 支持 |
| 能力雷达、分数可视化 | 支持 | 支持，含 LiveBench 能力矩阵 |
| 趋势与选型 | 支持 | 支持 |
| 独立模型对比、收藏 | 支持 | 支持本机收藏与最多 4 个模型逐榜对比；浏览器存储不可用时仅本次会话有效 |
| 离线缓存 | 支持 | 页面及快照仍需联网加载；收藏不等于离线缓存 |
| 榜单解释与数据状态 | 支持来源、指标、版本和抓取状态 | 支持来源、指标、版本、数据状态与历史变化 |
| 中转站地址历史 | 支持下拉选择，不保存访问密钥 | 暂未提供 |
| 后台数据检查、应用升级 | 支持 | 无 APK 安装流程 |

各平台独立实现，并非所有设置完全一致。不同评测的分数尺度、版本和时间不同，不应直接把跨榜分数当成同一标准。

## 数据与更新

当前后端注册 **26 个榜单**，来自 **5 个独立抓取来源**：ModelSage/Arena 相关 12 个、LiveBench 8 个、SWE-bench 3 个、Aider Polyglot 1 个、EvalPlus HumanEval+ 与 MBPP+ 2 个。新增三榜均在“代码”分类中，可切换来源查看，不混合原始分数排序。数据来自公开来源，不是各评测机构的官方客户端。Aider、EvalPlus 的数据可能不包含最新模型，抓取日期不代表评测发布日期；商用前仍请确认上游条款。

**榜单数据更新**与**应用版本升级**是两条独立通道：

- 主仓库的 `leaderboards.json` 为官方快照，Android、iOS 和网页默认读取它。[快照工作流](https://github.com/Aigooz/ai-rankboard/actions/workflows/snapshot.yml)计划每 6 小时抓取一次；GitHub 调度可能延迟，上游网站也未必每次有新数据。
- Android 更新中心已内置官方地址。可立即同步榜单、检查应用版本，分别管理数据提醒和应用升级提醒，不需要手动填写 URL。
- 应用前台约每 5 分钟检查版本，后台计划每 15 分钟检查。后台任务受系统省电、强行停止、网络等限制，**不是服务器实时推送**；系统通知需要通知权限，关闭提醒仍可手动检查。
- 下载快照会校验 SHA-256 和结构；APK 会校验哈希、包名和版本，并交给 Android 系统安装器确认。网络或数据校验失败时保留可用缓存，不以失败内容替换它。

“快照生成时间”“某榜单抓取时间”“上游评测版本”不是同一个概念。抓取成功不意味着上游成绩刚刚发布，不能保证榜单实时变化。

### 如何阅读榜单与变化

- 分数只在同一榜单、同一评测版本下比较，不将 Arena 分、LiveBench 分与其他能力指数相加生成“统一总分”；缺失成绩显示为未知，而不是 0 分。
- 榜单页默认仅显示紧凑的来源、抓取日期和必要状态；完整说明通过网页展开入口或 Android“详情”打开，不再常驻占据榜单首屏。说明中保留原始来源、指标含义和评测版本。未采集到的上游更新时间或版本明确显示为未知；LiveBench 的配置版本不是本站抓取日期。
- 抓取数据超过 48 小时会提示可能陈旧，含义是本站缓存年龄，不代表上游 48 小时没有更新；部分来源抓取失败时保留此前可用成绩并展示失败状态。若所有来源均失败，发布流程保留整份旧快照，因此客户端只能看到上次发布状态与缓存年龄，无法显示这一轮失败。
- 新版导出快照带有可选健康信息和成功抓取历史。历史从实际采集开始积累，每榜最多保留 12 次、每次前 100 个模型；旧快照没有历史时不推算历史，首次采集不显示升降。
- 历史名次变化只比较同榜且相同已知评测版本；版本未知或发生切换时仅展示抓取记录，不推断能力提升。大幅模型数或名次变化属于复核提示，不等同于数据错误。
- 网页收藏及对比选择仅存储于当前浏览器，不上传、不跨设备同步；清除网站数据会移除记录。中转站工具仍为 Android 独有，iOS 本轮未改动。


## 常见问题

**为什么以前在 GitHub 找不到安装包？** 之前安装包只放在独立更新通道，主仓库没有正式 Release。现在统一从本仓库 [Releases](https://github.com/Aigooz/ai-rankboard/releases) 下载；Actions 的未签名测试产物不作为正式安装包。

**为什么没有更新通知？** 先在更新中心手动检查，确认通知总开关及分类权限；系统禁止后台运行或强行停止应用时，定时检查可能延后。关闭提醒不会关闭手动检查。已安装最新版时不会提示新版本。

**数据日期没变化怎么办？** 查看来源和检查结果，手动同步后仍旧，可能是上游尚未更新或抓取失败。反馈时请提供版本、榜单名称、数据时间和来源链接。

**iOS 的 IPA 能直接安装吗？** 不能。当前工作流输出未签名开发产物，需要自行完成 Apple 签名与分发，不是 App Store/TestFlight 发行版。

## 开发

```text
android/              Kotlin + Jetpack Compose / Room / WorkManager
ios/                  iOS 客户端与 XcodeGen 配置
web/                  无需构建的静态网页
backend/              FastAPI、来源解析、SQLite 与快照导出
scripts/              正式发布准备与校验
.github/workflows/    CI、网页部署、快照同步、iOS 构建、Android 发布
docs/RELEASING.md     签名、兼容升级与正式发版说明
```

### Android

用 Android Studio 打开 `android/`。需要 JDK 17、Android SDK 35；包名 `com.ai.rankboard`，minSdk 26。

```powershell
cd android
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

没有本地发布密钥时构建的是未签名 release，不能拿它覆盖正式版。正式发布必须沿用原密钥，详见 [发布指南](docs/RELEASING.md)。Windows 中文路径出现测试 worker 异常时，复制到纯 ASCII 路径构建。

### 网页

```powershell
npx --yes http-server web -p 8080 -c-1
```

访问 `http://127.0.0.1:8080`。也可使用能正确返回 JavaScript MIME 的其他静态服务器；部分 Windows 的 Python 静态服务器会将 `.js` 返回为 `text/plain`。推送 `main` 后由 [Deploy Web](https://github.com/Aigooz/ai-rankboard/actions/workflows/pages.yml) 自动部署。

### 后端

```powershell
cd backend
python -m pip install -r requirements.txt
python -m unittest discover -s tests -t .
python run.py
```

本地 API 地址 `http://127.0.0.1:8000`，接口文档 `/docs`。主要接口为 `/v1/meta`、`/v1/leaderboards`、`/v1/models`。对外部署前设置 `ADMIN_TOKEN`，管理刷新请求使用 `X-Admin-Token`，不要把默认空令牌配置暴露在公网。

导出快照：在 `backend/` 执行 `python -m app.snapshot ../leaderboards.json`，同时生成 SHA-256 文件。自动发布逻辑见 `.github/workflows/snapshot.yml`。

## 维护

[贡献指南](CONTRIBUTING.md) · [安全说明](SECURITY.md) · [正式发布与验证](docs/RELEASING.md) · [Actions 状态](https://github.com/Aigooz/ai-rankboard/actions)

仓库未提供开源许可证授权声明；公开可见不等于自动获得任意复制或商用授权。第三方榜单数据权利归相应来源所有。
