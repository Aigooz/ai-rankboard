# AI 排行榜聚合（Android + FastAPI）

聚合 modelsage.cn 各榜单（综合 / 代码 / Agent / 搜索 / 写作 / 视觉 / 图像 / 视频 / 速度 / 性价比），安卓端以静态快照启动，支持 WorkManager 后台更新快照。首页开放综合、代码、写作、多模态、智能体、搜索、速度、性价比维度，支持搜索、排序、筛选、下拉刷新、分页加载、模型对比、价格计算器、本地收藏与离线缓存。

## 目录结构

- `backend/` Python FastAPI 后端：定时抓取 modelsage.cn 榜单页，解析入库 SQLite，暴露 REST API。
- `android/` Kotlin + Jetpack Compose (Material 3) 安卓端：Retrofit 拉取 + Room 离线缓存/收藏。
- `web/` 纯静态网页端：读取公开快照仓库，支持排行、趋势、选型与模型详情，由 GitHub Pages 自动发布。

## 网页端

网页端不需要构建，直接运行：

```powershell
cd web
python -m http.server 8000
```

推荐使用 Node 静态服务器或其他能返回 `text/javascript` 的本地服务器；Windows 自带的 `python -m http.server` 可能在部分系统上把 `.js` 标成 `text/plain`，导致浏览器拒绝模块脚本。生产环境由 `.github/workflows/pages.yml` 发布到 GitHub Pages，数据直接来自本仓库的快照文件，榜单快照更新后网页无需重新发版。

## 后端启动

```bash
cd backend
pip install -r requirements.txt
python run.py            # http://127.0.0.1:8000
```

首次启动会自动抓取全部 12 个榜单；之后每 6 小时自动刷新。手动刷新：

```bash
curl -X POST http://127.0.0.1:8000/v1/admin/refresh
```

主要接口：

| 接口 | 说明 |
|---|---|
| `GET /v1/meta` | 各榜单健康状态与最后更新时间 |
| `GET /v1/leaderboards` | 榜单列表（可按 dimension 过滤） |
| `GET /v1/leaderboards/{slug}?sort=rank\|score\|updated&q=关键词&limit=50&offset=0` | 榜单条目（分页，返回 total） |
| `GET /v1/models?q=` | 跨榜单模型搜索 |
| `GET /v1/models/{slug}` | 模型详情（全部榜单成绩 + 价格） |

## 安卓端

用 Android Studio 打开 `android/` 目录即可构建运行（AGP 8.7 / Kotlin 2.0 / minSdk 26）。包名 `com.ai.rankboard`。
项目已包含 Gradle Wrapper。首次构建：

```powershell
cd android
.\gradlew.bat :app:testReleaseUnitTest :app:assembleRelease
```

当前版本已改为“离线优先”：不再需要电脑保持后端运行。APK 内置 `android/app/src/main/assets/leaderboards.json` 初始快照；可选远端更新见下文。

## 远端快照更新

后端导出 schema v3 快照：

```bash
cd backend
python -m app.snapshot ../android/app/src/main/assets/leaderboards.json
```

会同时生成：

```text
leaderboards.json
leaderboards.json.sha256
```

仓库内置了数据自动更新通道：快照直接发布在本仓库根目录（`leaderboards.json`），Android / iOS / Web 的默认数据源会读取它。GitHub Actions（`.github/workflows/snapshot.yml`）每 6 小时抓取一次全部数据源，通过 `github.token` 自动提交新快照，不再依赖跨仓库 `SNAPSHOT_TOKEN`。WorkManager 按设定频率检查，下拉首页也会立即检查；更新需通过 SHA-256 校验且 `schemaVersion` 受支持。无网络、校验失败或解析失败时，继续使用内置快照或最后一次成功下载的快照。

从 v0.4.0 起，后台 Worker 检测到新快照后会发送系统通知“AI 排行榜数据已更新”；v0.17.0 起通知会附带新增模型。首次启动时 App 会请求 Android 13+ 的通知权限；如果拒绝，后续可到系统设置里重新开启“通知”。点击通知会回到应用首页。

## 应用内自更新

v0.7.0 起支持应用内自更新。流程是：设置页填写更新清单地址，App 下载新版 APK、校验 SHA-256、校验包名和版本号，然后调起系统安装器确认升级。因为 Android 系统限制，不能完全静默安装；首次安装需要在“允许安装未知应用”里授权一次。只要新版 APK 使用同一签名密钥，确认后会原位覆盖升级，不需要卸载。

更新清单示例：

```json
{
  "versionCode": 8,
  "versionName": "0.7.1",
  "apkUrl": "https://example.com/app-release.apk",
  "sha256": "APK_FILE_SHA256",
  "notes": "优化界面和更新流程"
}
```

把这个文件和签名后的 APK 上传到任意 HTTPS 静态地址，然后在 App 设置页填入 `app-update.json` 地址即可。

## 数据可信度与归一

- schema v2 记录 `schemaVersion`、`generatedAt`、`sources`，每个榜单保留原始 URL 和最后抓取时间。
- `backend/app/model_identity.py` 会把 “Claude 4.5 Sonnet” / “Claude Sonnet 4.5”、推理档位差异、fallback 标记等归一到稳定 canonical slug，避免跨榜漏配。
- 真实 HTML 保存在 `backend/tests/fixtures/`，每次网站改版会由回归测试提前发现。
- 后续可接入 Artificial Analysis、LMArena、OpenRouter、OpenCompass、HELM、LiveBench、SuperCLUE 等源；当前 `sources` 字段已为多源扩展预留。

核心结构：

```
com/ai/rankboard/
├── RankboardApp.kt        # Application + Retrofit/Room 初始化（API 地址在这里改）
├── MainActivity.kt        # 导航宿主（home / model/{slug} / favorites）
├── data/
│   ├── Api.kt             # Retrofit 接口 + DTO
│   ├── LocalDb.kt         # Room：离线缓存 + 收藏
│   └── LeaderboardRepository.kt
└── ui/
    ├── home/              # 首页：四维度 Tab + 搜索 + 下拉刷新 + 分页
    ├── detail/            # 模型详情：基础信息 / 各榜单成绩 / 价格 / 收藏
    ├── favorites/         # 我的收藏：取消收藏
    └── theme/             # Material 3 动态取色，自动适配深色模式
```

## 说明

- 数据来源于 modelsage.cn 公开榜单页，仅作聚合展示，应用内会展示数据来源；抓取频率已压到最低（每 6 小时一次），商用前请确认目标站点的使用条款。
- 榜单页改版时，解析器按表头文本映射列，有一定容错；某源失败不影响其他源，`/v1/meta` 可看到每个榜单的健康状态。

## 正式发布

1. 生成或替换 `android/release.keystore`，并在 `android/keystore.properties` 配置：

   ```properties
   storeFile=release.keystore
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```

2. 构建签名 release：

   ```powershell
   cd android
   .\gradlew.bat clean :app:assembleRelease :app:testReleaseUnitTest
   ```

3. 校验签名：

   ```powershell
   <android-sdk>\build-tools\35.0.0\apksigner.bat verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
   ```

`keystore.properties`、`release.keystore`、`local.properties`、构建产物和 APK 都已加入 `.gitignore`，不要提交私钥。GitHub Actions 会在 `push`/`pull_request` 时执行后端单测、Android 单测和 release 构建，并上传未签名 APK artifact。

> Windows 提示：如果源码路径包含中文导致 Gradle test worker/KSP 异常，可在同盘建立 ASCII 路径联接后从该路径构建，例如 `New-Item -ItemType Junction -Path D:\1Project\ai-rankboard -Target "D:\1Project\手机ai排行榜软件"`。注意：路径联接能解决编译，但 Gradle 会把项目目录还原为真实中文路径，`testReleaseUnitTest` 的 worker 仍会全部 ClassNotFoundException；跑单测需要把 `android/` 完整拷贝到纯 ASCII 目录（如 `D:\ai-rankboard-test`）后在该目录构建，首次可用 `tar -cf - --exclude=.gradle --exclude=build . | (cd /d/ai-rankboard-test && tar -xf -)` 同步。
