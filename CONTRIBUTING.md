# 参与贡献

欢迎通过 [Issues](https://github.com/Aigooz/ai-rankboard/issues) 反馈问题，通过 Pull Request 提交改进。

## 反馈问题

请提供应用版本、Android/浏览器版本、复现步骤、期望行为和实际结果。榜单问题请附数据来源链接、榜单名称和应用内数据时间；更新问题请区分“榜单数据更新”与“APK 应用更新”。截图、日志请先隐藏密钥、私人地址和个人信息。

## 开发和验证

环境与启动方式见 [README](README.md)。提交前运行与改动相关的检查：

```bash
# 仓库根目录：发布脚本测试
python -m unittest discover -s scripts/tests

# backend 目录：安装依赖后执行后端测试
python -m unittest discover -s tests -t .

# android 目录：JDK 17 / Android SDK 35
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

网页改动需要检查桌面、移动视口及深色模式，验证榜单、模型详情、图表和导航。Android 改动应注明是否完成真机/模拟器安装验证。请如实记录无法运行的检查。

数据解析器改动应添加或更新回归测试，保留来源、评测版本和更新时间，不用猜测值填补缺失分数，不混同不同榜单的分数尺度。避免在无关改动中整份重写快照。

## Pull Request

说明修改原因、影响平台、测试结果；界面改动附截图。不要提交私钥、令牌、数据库、构建产物和本地配置。版本号与正式发布由维护者统一处理，发布流程见 [RELEASING](docs/RELEASING.md)。涉及安全问题请先阅读 [SECURITY](SECURITY.md)，不要在公开 Issue 中粘贴凭据。
