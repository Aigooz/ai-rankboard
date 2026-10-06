# AI 排行榜 Web

纯静态网页端，无需后端。页面优先从本仓库的 GitHub Contents API 读取最新快照，并保留更新仓库和镜像源兜底。

## 本地运行

```bash
cd web
python -m http.server 8000
```

打开 `http://127.0.0.1:8000` 即可。仓库不需要安装依赖，也不需要构建。

## 发布

推送 `web/` 后，`.github/workflows/pages.yml` 会把该目录发布到 GitHub Pages。
