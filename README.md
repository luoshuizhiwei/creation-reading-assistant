# 创作阅读助手

创作阅读助手是一套本地优先的创作与阅读应用。仓库包含 Electron 桌面创作工作台和独立原生 Android 阅读端；两条产品线源码隔离，不共享运行时代码。

> 当前恢复项目执行范围（2026-10-30）：只推进原生Android稳定阅读与轻量取材；桌面资产保留但不在当前开发/构建/验收范围。接手先读[当前交接](docs/handoff/current.md)与[接手说明](docs/handoff/next-codex.md)。

## 产品线

| 产品线 | 目录 | 当前定位 |
|---|---|---|
| Electron 桌面端 | `src/`、`electron/` | 独立桌面资产；当前Android恢复任务排除桌面实施/构建/验收 |
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

设备类型和权限以 [当前交接](docs/handoff/current.md) 为准。本恢复项目已获准在无实体手机时使用隔离 MuMu；结果标注模拟器证据，不外推实体手机/MIUI。先用 `adb devices` 确认设备，再用 `adb -s <serial>` 明确目标；保存并恢复本轮数据、原 APK 对及系统设置。

Capacitor 历史实现已删除，不再作为可构建或可发布的产品。如需追踪许可证和上游来源，见 [`archives/frozen-mobile/FROZEN.md`](archives/frozen-mobile/FROZEN.md)。[现存 Release 工作流](.github/workflows/release.yml) 已包含原生 Android 分支；流水线存在不代表签名、升级或实际分发已验收。

## 当前交付状态

- 原生 Android 的 JVM 单元测试、Lint 和 Debug APK 已有构建入口。
- Room 迁移测试位于 `android/app/src/androidTest/`；设备环境与当前验收范围见交接入口。
- 旧 `mobile/android` 发布已退役；原生 Android 正式签名/升级及F发布仍为独立验收范围。当前目标、优先级与退出条件见 [Android交付规划](docs/coordination/2026-10-09-workflow-replan.md)；[产品总规划](docs/plans/2026-10-01-product-roadmap-realignment.md)保留历史两端目标，不能将A–E恢复100%视为全产品完成。

## 安全与数据

- 不提交 API Key、账号凭证、真实书籍内容或本地私密数据。
- 测试报告和截图说明只使用“测试 EPUB”“测试 TXT”等中性名称，不记录真实测试书名。
- 不使用破坏性数据库迁移；导入、同步、恢复和升级都必须保留用户已有数据。
