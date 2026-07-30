# archives/frozen-mobile/ — 已删除的 Capacitor 历史产品线存档

> **状态：已删除（2026-07-30，P0-A2 冻结后的收尾）**
>
> 原 `mobile/` 目录（旧 Capacitor Android 产品线）已彻底删除，仅本存档保留许可证与上游归属材料。当前移动端主线为 `android/`。

## 当前移动端主线

当前移动端实现位于仓库根目录的 `android/`（Kotlin + Jetpack Compose + Room），独立构建，不依赖本存档的任何运行时代码。当前移动端构建命令在 `android/` 目录执行：

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## 本存档保留的内容与目的

仅保留以下材料，用于许可证追踪与上游归属证明：

1. `LICENSE`、`NOTICE.md`、`THIRD_PARTY_NOTICES.md` — 原 `mobile/` 的 GPL 许可证与第三方声明。
2. `legado-reader-core/` — 上游 legado 阅读器核心的源码、补丁与来源记录（`UPSTREAM.md` / `PATCHES.md` / `LICENSE`），属归属证明；
   其 `ZhLayout` 在当前仓库中是死代码，接进来拿不到中文排版（原生 `android/` 阅读器为自研内核）。
3. 本文件 `FROZEN.md` — 记录冻结与删除决策。

## 桌面端与移动端的局域网同步协议

同步协议最初在旧 `mobile/` 中定义，其规范与校验脚本现在位于仓库根 `scripts/verify-sync-schema.mjs` 与 `docs/`（如 `docs/superpowers/plans/2026-06-30-mobile-lan-sync.md`），不依赖本存档。

## 不再做的事

- **不再接受旧 Capacitor 功能开发，也不再恢复 `mobile/` 目录。**
- 不保证旧 Capacitor 代码可独立构建（`mobile/src/App.tsx` 引用的约 20 个模块在冻结前已删除，且 `mobile/` 现已删除）。

## 后续

旧 Capacitor APK 的退役**已由 P0-A2 完成**：CI 不再构建 `mobile/`，Release 工作流不再构建或上传 `mobile/android` APK（许可证文件改从本存档 `archives/frozen-mobile/` 复制，见 `.github/workflows/release.yml`）。

只有原生 `android/` Release 迁移属于后续任务 **P0-A3**（见 `docs/testing/native-android-gap-audit-2026-07-29.md`）：完成签名、版本号、R8、源码包、SHA-256 和更新清单后，才能恢复 Android Release。在 P0-A3 完成前，Release 工作流只构建桌面端。
