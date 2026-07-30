# 创作阅读助手

创作阅读助手是一套本地优先的“阅读 + 灵感沉淀 + AI 辅助”应用。仓库同时保留桌面端和两条 Android 实现，但当前移动端主线是独立原生应用 `android/`。

## 产品线

| 产品线 | 目录 | 当前定位 |
|---|---|---|
| Electron 桌面端 | `src/`、`electron/` | 桌面书库、灵感、AI 与局域网同步端 |
| 独立原生 Android | `android/` | **当前移动端主线**，Kotlin + Jetpack Compose + Room |
| Capacitor Android | `mobile/` | 历史实现/对照代码，不是当前原生端的运行时依赖 |

深入的目录边界和命令见 [AGENTS.md](AGENTS.md)，文档状态见 [docs/README.md](docs/README.md)。

## 主要能力

- 本地导入 TXT、Markdown、EPUB。
- 原生中文分页、滚动阅读、目录、搜索、进度恢复、书签、笔记、高亮和阅读灵感。
- 系统 TTS、阅读统计、主题与阅读纸张设置。
- 用户自配 OpenAI-compatible 接口；密钥只保存在本机安全存储。
- 与桌面端局域网同步，以及 WebDAV 备份。

## 本地开发

桌面端：

```powershell
npm install
npm test
npm run build
```

当前原生 Android：

```powershell
Set-Location android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

需要 UI 或设备行为验证时，只使用已连接的真实 Android 手机；先用 `adb devices` 确认设备，再用 `adb -s <serial>` 明确指定目标。不要使用 MuMu 模拟器。长时间测试临时修改常亮设置后，必须恢复设备原值。

Capacitor 历史实现（`mobile/`）已于 2026-07-29 正式冻结，不再作为可构建或可发布的当前产品。它的构建命令不再列在本文档中；如需了解历史实现或许可证追踪，见 [`mobile/FROZEN.md`](mobile/FROZEN.md)。原生 Android Release 的正式发布迁移属于后续 P0-A3，完成前 Release 工作流只构建桌面端。

## 当前交付状态

- 原生 Android 的 JVM 单元测试、Lint 和 Debug APK 已有构建入口。
- Room 迁移测试位于 `android/app/src/androidTest/`，需要在真实设备上执行。
- `.github/workflows/release.yml` 已在 P0-A2 退役旧 `mobile/android` APK 的构建与上传，当前只构建桌面端（Windows 安装版 + 免安装版）。原生 `android/` Release 迁移属于后续 P0-A3，完成前不发布 Android APK。

## 安全与数据

- 不提交 API Key、账号凭证、真实书籍内容或本地私密数据。
- 测试报告和截图说明只使用“测试 EPUB”“测试 TXT”等中性名称，不记录真实测试书名。
- 不使用破坏性数据库迁移；导入、同步、恢复和升级都必须保留用户已有数据。
