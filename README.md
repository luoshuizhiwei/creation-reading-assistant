# 创作阅读助手

创作阅读助手是一套本地优先的创作与阅读应用。仓库包含 Electron 桌面创作工作台和独立原生 Android 阅读端；两条产品线源码隔离，不共享运行时代码。

## 产品线

| 产品线 | 目录 | 当前定位 |
|---|---|---|
| Electron 桌面端 | `src/`、`electron/` | **当前桌面主线**：项目、正文写作、大纲、卡片、版本历史与资料阅读 |
| 独立原生 Android | `android/` | **当前移动端主线**，Kotlin + Jetpack Compose + Room |
| Capacitor 历史存档 | `archives/frozen-mobile/` | 已删除产品线的许可证、上游归属与冻结说明；不可构建 |

深入的目录边界和命令见 [AGENTS.md](AGENTS.md)，文档状态见 [docs/README.md](docs/README.md)。

## 主要能力

- 桌面端：本地创作项目、正文编辑、大纲与场景规划、创作卡片、统计、快照/回收站和项目导入导出。
- 桌面端：本地 TXT、Markdown、EPUB 资料阅读，以及灵感与收件箱迁移入口。
- Android：中文分页/滚动阅读、目录、搜索、进度恢复、书签、笔记、高亮、系统 TTS 与阅读设置。
- AI 接口由用户自行配置，密钥只保存在本机安全存储。

## 本地开发

桌面端：

```powershell
npm install
npm test
npm run build
npm run verify:beta -- --scope=desktop
```

当前原生 Android：

```powershell
Set-Location android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

需要 UI 或设备行为验证时，只使用已连接的真实 Android 手机；先用 `adb devices` 确认设备，再用 `adb -s <serial>` 明确指定目标。不要使用 MuMu 模拟器。长时间测试临时修改常亮设置后，必须恢复设备原值。

Capacitor 历史实现已删除，不再作为可构建或可发布的产品。如需追踪许可证和上游来源，见 [`archives/frozen-mobile/FROZEN.md`](archives/frozen-mobile/FROZEN.md)。原生 Android Release 的正式发布迁移属于后续 P0-A3，完成前 Release 工作流只构建桌面端。

## 当前交付状态

- 原生 Android 的 JVM 单元测试、Lint 和 Debug APK 已有构建入口。
- Room 迁移测试位于 `android/app/src/androidTest/`，需要在真实设备上执行。
- `.github/workflows/release.yml` 已在 P0-A2 退役旧 `mobile/android` APK 的构建与上传，当前只构建桌面端（Windows 安装版 + 免安装版）。原生 `android/` Release 迁移属于后续 P0-A3，完成前不发布 Android APK。

## 安全与数据

- 不提交 API Key、账号凭证、真实书籍内容或本地私密数据。
- 测试报告和截图说明只使用“测试 EPUB”“测试 TXT”等中性名称，不记录真实测试书名。
- 不使用破坏性数据库迁移；导入、同步、恢复和升级都必须保留用户已有数据。
