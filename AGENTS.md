# AGENTS.md

本仓库包含两个活跃产品线（桌面端 Electron 与原生 Android `android/`），源码边界清晰，不共享运行时代码。旧 Capacitor `mobile/` 产品线已删除，历史存档见 `archives/frozen-mobile/`。

## 桌面端 (Electron)

| 维度 | 说明 |
|------|------|
| 源码目录 | `src/`（渲染进程 React）、`electron/`（主进程 + preload） |
| 配置入口 | 根 `package.json`、`electron.vite.config.ts`、`tsconfig.*.json` |

### 命令（均在仓库根执行）

```bash
npm install            # 安装依赖
npm run dev            # 启动开发（electron-vite dev）
npm run build          # 类型检查 + 构建（tsc + electron-vite build）
npm run preview        # 预览构建产物（electron-vite preview）
npm run dist:dir       # 构建 + 打包为目录
npm run dist           # 构建 + 打包安装程序
```

---

## 移动端 — 独立原生 Android（当前主线）

| 维度 | 说明 |
|------|------|
| 源码目录 | `android/`（Kotlin + Jetpack Compose + Room） |
| 构建体系 | Gradle（`android/gradlew`），不依赖 Capacitor 或 WebView |
| 命令目录 | `android/` |

### 命令（在 `android/` 目录执行）

```bash
./gradlew :app:testDebugUnitTest     # JVM 单元测试
./gradlew :app:lintDebug             # Lint 检查
./gradlew :app:assembleDebug         # 构建 Debug APK
./gradlew :app:assembleRelease       # 构建 Release APK（需配置签名）
```

---

## 移动端 — Capacitor（已删除，历史存档于 `archives/frozen-mobile/`）

`mobile/` 旧 Capacitor 产品线已于 2026-07-30 彻底删除（P0-A2 冻结后的收尾），当前移动端主线为 `android/`：

- **代码已删除**，仅保留许可证与上游归属存档于 `archives/frozen-mobile/`（`LICENSE`、`NOTICE.md`、`THIRD_PARTY_NOTICES.md`、`legado-reader-core/` 及本说明 `FROZEN.md`）。
- `android/` 不依赖 `mobile/` 的任何运行时代码，删除 `mobile/` 不影响 `android/` 构建与运行。
- 旧 `mobile/android` Release APK 的构建与上传已在 CI/Release 工作流中退役；原生 `android/` Release 迁移属于后续 **P0-A3**。
- 详见 [`archives/frozen-mobile/FROZEN.md`](archives/frozen-mobile/FROZEN.md)。

---

## 如何判断改动归属

| 改动涉及 | 归属 |
|-----------|------|
| `src/**`、`electron/**`、根 `package.json` 的 `dependencies`/`devDependencies`、`electron.vite.config.ts` | 桌面端 |
| `android/**` | 移动端 — 独立原生（当前主线） |
| `docs/**` | 共享文档，不属于任一端的运行时 |

---

## 权威架构文档

深入了解阅读器引擎设计与技术选型，请参阅：

- [`docs/architecture/native-android-reader.md`](docs/architecture/native-android-reader.md) — 当前独立原生 Android 阅读器架构
- [`docs/plans/legado-feature-backlog.md`](docs/plans/legado-feature-backlog.md) — 面向阅读体验的候选功能清单
- [`docs/README.md`](docs/README.md) — 当前文档、历史快照与归档边界

## Android 真机验证约束

- `android/` 的 UI 和设备行为只在已连接的真实 Android 手机上验证，不使用 MuMu 模拟器。
- 每次先运行 `adb devices` 核对当前设备，再对所有设备命令使用 `adb -s <serial>`。
- 长时间测试可临时启用 `stay_on_while_plugged_in`，但结束时必须恢复测试前的值。
- 测试报告、TODO、截图说明和交接记录只写"测试 EPUB""测试 TXT"等中性名称，不记录真实测试书名；这不授权修改设备上的书籍数据。
