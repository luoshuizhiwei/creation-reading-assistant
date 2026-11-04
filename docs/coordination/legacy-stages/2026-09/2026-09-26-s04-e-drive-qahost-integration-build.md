# S04 qahost E 盘集成构建

- 日期：2026-09-26
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，HEAD `5aed593`，分支 `codex/recover-worktree-20260920`
- 性质：只构建并核对 APK；未安装、未运行 MuMu 或真机验收，未修改产品源码，未 stage/commit/push/reset/checkout/clean。

## 根因与定向验证

此前 `E:\creation-reading-assistant\codex-s04-build-20260925\redirect-build.gradle` 将 `buildDir` 直接设为 E 盘。`:app:properties` 与 JVM 测试虽可运行，但新鲜 `:app:assembleQahost` 在 `:app:kspQahostKotlin` 失败（Gradle EXIT 1）：Hilt/KSP 把生成文件相对于 D 盘 `android\app` 求路径，抛出 `this and base files have different roots`。不能把配置检查当成完整构建验证。

建立目录接合点 `D:\develop\Code\Codex\cra-s04-buildlink` → `E:\creation-reading-assistant\codex-s04-build-20260925\project-builds`。新初始化脚本 `E:\creation-reading-assistant\codex-s04-build-20260925\redirect-build-via-junction.gradle` 通过接合点给编译器同盘路径，实际数据落 E 盘。保留 `TEMP`/`TMP` 与 `--project-cache-dir` 在同一 E 盘专用目录。先跑 `:app:kspQahostKotlin`，Gradle EXIT 0，日志 `E:\creation-reading-assistant\codex-s04-build-20260925\ksp-junction.log`。

## 完整构建与产物

`android/` 下执行 `:app:assembleQahost -I <上述新脚本> --project-cache-dir <E盘专用目录>\project-cache --offline --console=plain`，Gradle **EXIT 0**，`BUILD SUCCESSFUL in 3m 35s`；41 个 task（19 executed、8 from cache、14 up-to-date）。完整日志在 E 盘 `assemble-qahost-junction.log`。编译仅见既有 `LibraryBrowserRoute.kt` 的图标弃用 warning；`stripQahostDebugSymbols` 对四个库提示无法剥离符号，打包继续成功。

| 核对项 | 结果 |
|---|---|
| 实体 APK | `E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\app\outputs\apk\qahost\app-qahost.apk` |
| 大小 | 51,699,420 B |
| SHA-256 | `24525998F69AB41A7FB1F6EDEA1E26783D0C4F45C922D87C4A575B0D95D0794B` |
| D 接合路径回读 | 文件存在，SHA-256 与 E 实体路径一致 |
| aapt badging | `com.creationreadingassistant.qahost`，versionCode 3，versionName 0.5.1 |
| 安装/设备验收 | 未执行 |

本构建对应恢复仓库当前未提交组合状态，不能据此宣称 S04 产品链已验收。B 下一步应先核新 APK 与 MuMu 安装包同哈希，再用隔离样本验证；模拟器结果须单列。C 的最终清理链独立审查报告截至本次构建尚未在仓库出现。
