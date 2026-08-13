# GitHub 发布流程

> **状态（2026-07-29，P0-A2）**：旧 `mobile/android` Capacitor APK 的构建与上传已在
> `.github/workflows/release.yml` 中退役。当前 Release 工作流只构建桌面端
> （Windows 安装版 + 免安装版）。原生 `android/` Release 迁移属于后续
> **P0-A3**（见 `docs/testing/native-android-gap-audit-2026-07-29.md`），完成前不发布 Android APK。
>
> 下文中涉及旧 Capacitor Android 构建与签名的内容保留为**历史快照**，仅供
> P0-A3 迁移参考，不能作为当前操作指南。

本项目采用“私有源码仓库 + 公开下载仓库”的分发结构：

- 私有源码：`luoshuizhiwei/creation-reading-assistant`
- 公开下载：`luoshuizhiwei/creation-reading-assistant-releases`
- Android 移动端按 GPL-3.0 发布。每个 APK 必须同时公开该版本的完整对应源代码，不能只上传二进制。

## 本地环境准备

安装并登录 GitHub CLI：

```powershell
winget install --id GitHub.cli -e
gh auth login
gh auth status
```

Android Release 构建必须提供四项签名配置。密钥和密码只能放在本机环境变量或 GitHub Secrets，禁止提交到仓库：

```powershell
$env:CRA_ANDROID_KEYSTORE_FILE = "D:\\path\\to\\android-release.jks"
$env:CRA_ANDROID_KEYSTORE_PASSWORD = "<本机密钥库密码>"
$env:CRA_ANDROID_KEY_ALIAS = "<别名>"
$env:CRA_ANDROID_KEY_PASSWORD = "<本机私钥密码>"
```

当前 `0.1.x` 公开发行包使用旧兼容签名。为了覆盖安装并保留用户数据，`0.2.0` 不能直接更换证书。若以后迁移到新的安全密钥，必须先验证 Android APK Signature Scheme v3/v3.1 的签名轮换和目标系统兼容性。

## 每次大修改（当前：桌面端）

1. 更新 `CHANGELOG.md` 的"未发布"区域。
2. 递增根目录 `package.json` 的版本号。
3. 运行门禁：

```powershell
npm run build
npm run verify:beta
npm run verify:release-readiness
```

4. 查看改动后提交并推送：

```powershell
git status -sb
git add .
git commit -m "feat: 简短说明本次大改"
git push origin main
```

## 本地生成 Android 正式包（历史 — P0-A3 待恢复）

> 以下内容描述旧 `mobile/android` Capacitor 构建流程，保留为 P0-A3 迁移参考。
> 当前 `mobile/` 已删除；冻结说明与上游归属见 [`archives/frozen-mobile/FROZEN.md`](../archives/frozen-mobile/FROZEN.md)，这些命令不可用。

```powershell
npm run mobile:build
npm run cap:sync --prefix mobile
Push-Location mobile\android
.\gradlew.bat testDebugUnitTest assembleRelease
Pop-Location
```

必须使用 `apksigner verify --print-certs` 验证 Release APK，且证书摘要要与目标升级链兼容。未签名的 `app-release-unsigned.apk` 不能发布。

## Android GPL 对应源码（历史 — P0-A3 待恢复）

> 以下清单描述旧 `mobile/android` 发布产物要求，保留为 P0-A3 迁移参考。

公开 Release 至少包含：

- `creation-reading-assistant-<version>-android.apk`
- `creation-reading-assistant-android-source.zip`
- `LICENSE`
- `NOTICE.md`
- `THIRD_PARTY_NOTICES.md`
- `SHA256SUMS.txt`
- 中文更新说明

源码归档必须包含构建该 APK 所需的 `archives/frozen-mobile/` 固定上游归属与许可证说明（迁移到独立原生 `android/` 后改为包含 `android/` 与上游 Legado/EpubLib 对应来源）。由于主源码仓库是私有仓库，不能依赖 GitHub 自动生成的 Source code 压缩包履行 Android GPL 源码提供义务。

## GitHub Actions Secrets（当前：桌面端 + 历史 Android）

> Android 签名 Secret 保留为 P0-A3 迁移参考；当前桌面端 Release 只需要 `PUBLIC_RELEASE_TOKEN`。

若使用 `.github/workflows/release.yml` 自动构建，需要在私有源码仓库配置：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `PUBLIC_RELEASE_TOKEN`：只授予公开下载仓库 Release 和内容写入权限

工作流不得把任何 Secret 输出到日志。缺少签名 Secret 时应直接失败，不能退回 Debug APK。

## 发布前检查

- 工作区没有密钥、Token、用户数据库、书籍正文或真机导出文件。
- APK 可以覆盖上一公开版本，安装后原有书籍、进度、灵感和设置仍存在。
- APK 签名、SHA256、版本名、`versionCode` 与更新清单一致。
- 公开 Release 同时包含 APK 和完整对应源码。
- 本地只保留当前版本产物；旧版本由 GitHub Release 保存。
