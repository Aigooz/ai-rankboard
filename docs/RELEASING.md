# Android 正式发布

## 三种产物不要混淆

| 位置 | 用途 |
| --- | --- |
| [主仓库 Releases](https://github.com/Aigooz/ai-rankboard/releases) | 用户下载的正式签名 APK、校验和、更新清单 |
| [更新通道仓库](https://github.com/Aigooz/ai-rankboard-updates) | 现有客户端使用的固定更新地址，必须保持兼容 |
| Actions 构建产物 | CI 的未签名 APK，仅用于开发验证，不作为正式安装包 |

Android 包名是 `com.ai.rankboard`。正式包必须继续使用原来的发布密钥，不能重新生成密钥替换；否则已有用户无法覆盖升级。私钥、密码、`keystore.properties` 不得提交 Git。现有正式签名证书的 SHA-256：

```text
47093e6d34cce8f3ee449637cb36083f0bb205b0f36d4d156b3286469035942c
```

## 1. 准备源代码和签名包

1. 在 `android/app/build.gradle.kts` 设置 `versionName`、递增的 `versionCode`，在 `CHANGELOG.md` 添加同版本记录。
2. 使用 JDK 17、Android SDK 35 和原发布密钥构建。`android/keystore.properties` 的字段为 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，该文件仅保存在本地。
3. 执行验证并构建：

```powershell
cd android
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

Windows 中文路径可能导致测试 worker 类路径异常。此时把 `android/` 复制到纯 ASCII 路径构建，不要只建立路径联接。不要把签名文件上传到 Actions 日志或构建产物。

单测和 lint 不能代替安装验收：在可用的 Android 设备上验证从上一正式版覆盖安装，收藏/设置保留，榜单、详情、数据同步和更新中心可用。无法使用设备时必须在发布说明中注明未完成真机验收。

## 2. 更新兼容通道

把签名后的 `app-release.apk` 与更新后的 `app-update.json` 放入更新通道仓库，**在同一个提交中上传**。清单的 `versionName`、`versionCode`、`sha256`、`sizeBytes` 必须对应 APK，保留已有 iOS 字段。不要更改现有用户使用的固定地址：

```text
https://raw.githubusercontent.com/Aigooz/ai-rankboard-updates/main/app-update.json
https://raw.githubusercontent.com/Aigooz/ai-rankboard-updates/main/app-release.apk
```

主仓库的 `leaderboards.json` 才是当前官方榜单数据通道，和应用安装包分开更新。

## 3. 发布主仓库 Release

源代码和更新通道提交后，在主仓库 **Actions → Publish Android Release → Run workflow**，选择 `main`，填写版本号（例如 `0.20.16`，不含 `v`）。发布工作流不接收私钥，它复用更新通道已经签名的正式包。

流程会：

1. 运行发布脚本单测、Android 单测和 lint。
2. 下载官方更新清单及 APK，比对源码版本、文件大小和 SHA-256。
3. 用 Android SDK 校验 APK 包名、版本、原发布证书及 zipalign。
4. 创建指向本次源码提交的 `v<版本>` 标签和草稿 Release。
5. 上传 `app-release.apk`、`SHA256SUMS`、`app-update.json`，全部成功后公开 Release。

已经公开的同版本 Release 不会被覆盖；草稿可在修复失败原因后重跑。已存在但指向其他提交的标签会阻止发布，避免安装包和源代码错配。发布期间应避免并行修改更新通道中的 APK 和清单。

本地也可以执行准备和校验：

```powershell
python -m unittest discover -s scripts/tests
python scripts/prepare_release.py --version 0.20.16 --output dist/release
```

脚本只校验版本和文件完整性；签名、包名与对齐校验由工作流中的 SDK 工具完成。`dist/` 已忽略，不要把安装包提交主仓库历史。

## 4. 发布后验收

- 确认 [latest](https://github.com/Aigooz/ai-rankboard/releases/latest) 显示正确版本且 Assets 包含 APK。
- 从 Release 实际下载 APK，比对 `SHA256SUMS`，再次执行 `apksigner verify --verbose --print-certs`。
- 核对应用内清单仍可访问，且版本和哈希与 Release 相同。
- 检查 CI、网页部署、数据快照工作流，不把“工作流已触发”当成“已成功发布”。

失败时先保留上一正式版下载入口，不发布未完成的草稿，不通过更换密钥或关闭完整性校验来绕过错误。
