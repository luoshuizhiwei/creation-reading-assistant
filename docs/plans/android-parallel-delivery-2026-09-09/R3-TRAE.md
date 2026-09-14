# 发给 Trae：R3 阅读设置的全局/本书两级、预设与恢复默认

> 本文件是 **R3** 轮任务包，替代 R1 的 `TRAE.md`（R1 原文保留作记录，不要覆盖它）。
> 工作树与分支由 Codex 在 R3 冻结后创建并指定（建议 `codex/android-trae-r3`）。**不要在主仓库凑合施工**；
> 若沿用 R1 工作树，先确认基线是否已被 Codex 集成，否则不得开工。

## 先读

1. 仓库 `AGENTS.md`；
2. `docs/plans/android-parallel-delivery-2026-09-09/{README.md, COMMON.md}`（纪律以 COMMON.md 为准，本文不重复）；
3. 本目录 `reports/` 里 Trae/Qoder/Codex 的 R1、R2 报告，与 R1 的 `TRAE.md`。

## 开工先做（不许跳）

输出 `pwd` / 分支 / `HEAD` / `git status --short`。当前主仓库 HEAD 参考 `a9a3bce`，但**你自己的工作树起点以你首次报告的 HEAD 为准**。
然后**逐条核对下列"现况"是否与代码一致**，不一致以代码为准并在报告里写明差异：

- `data/settings/SettingsStore.kt` 里 `AppearanceSettings` / `ReaderSettings` 是**全局单例**设置（DataStore 支持）。
- `data/settings/ReaderDefaultsMigration.kt` 已有 legacy 默认值迁移（`migrateLegacyReaderDefaults` / `migrateEpubEngineReenable`）。
- **已存在、不要重写**：`ReaderSettings.traditionalChinese`（繁体显示）、`ReaderSettings.ttsTimedStopMinutes`（TTS 定时停止）。
- 目前**没有**本书级覆盖（per-book override）机制 —— 这是本轮核心增量。

## 目标（产品语）

用户在「我的 → 阅读设置」与书内设置面板里看到**同一份设置**；可以只对**当前这本书**做覆盖，全局改动**不会**掀掉用户在某本书上的显式选择；能一键**恢复默认**并清楚知道作用范围；能套用**预设**。跨窗口、旋转、进程重启后表现一致。

## 拥有的路径

以 `android/app/src/main/java/com/creationreadingassistant/` 为根：

- `ui/screen/profile/ReaderSubPage.kt`、`ui/screen/profile/ProfileRoute.kt`
- `ui/screen/reader/sheets/ReaderSettingsSheet.kt`
- `ui/viewmodel/SettingsViewModel.kt`
- `ui/components/SettingsComponents.kt`
- 新增 `ui/screen/settings/**`（设置主页 / 预设管理页，如需要）
- 新增 `res/values/strings_settings.xml` 及同名 locale 资源

**不归你**：`data/settings/**`（含 `SettingsStore.kt`，Codex 拥有）、`ui/navigation/AppNavigation.kt`、
`ui/screen/reader/ReaderRoute.kt` / `ReaderScaffold.kt` / `ReaderLayerBuilders.kt` / `ReaderSheetHost.kt`、
全部 DAO / entity / AppDatabase / schema。需要新的持久化字段、迁移、路由或 reader 接线 → 写 `SEAM REQUEST` 并**继续做不依赖它的部分**。

## 必须完成

1. **本书级覆盖语义**：只有用户**显式改过**的项写入本书覆盖；未覆盖项一律跟随全局；全局值变更**不得**覆盖本书显式选项。
2. **清除本书覆盖**：提供"本项恢复跟随全局"与"清除本书全部覆盖"两个粒度，操作后 UI 立即反映真实来源（哪项来自本书、哪项来自全局）。
3. **恢复默认**：明确说明作用范围（全局 / 本书、覆盖哪些分组），二次确认；**不得**顺带改动阅读进度、替换规则、笔记、高亮、统计。
4. **预设**：提供最小可用集（至少"默认" + 1 个非默认，如大字号/护眼，最终集合按实际 UI 空间定）；预设只写显式项，套用后可被用户逐项改回。
5. **两端同源**：「我的 → 阅读设置」与书内设置面板读同一 Flow，任一改动另一处即时刷新；不要复制两套状态。
6. **跨窗口 / 旋转 / 重启**：多窗口或旋转后一致；进程重启持久化；不出现"设置回退到全局"的静默覆盖。
7. 保留既有纸墨主题、圆角、触控尺寸、减少动态效果、手机/平板/横屏适配。

## 验证与交付

- 定向 JVM：覆盖项语义（只有显式项入覆盖）、全局变更不覆盖本书、清除覆盖回落全局、恢复默认范围、
  预设应用后可逐项改回、序列化/反序列化往返、缺字段向后兼容。
- 不留编译红状态给集成者；设备验收交 WorkBuddy，安装脚本失败后允许 `adb install` 回退（见 COMMON.md）。
- 报告写 `docs/plans/android-parallel-delivery-2026-09-09/reports/trae-r3.md`：基线 SHA、改动路径、
  完成项、定向命令/退出码/测试数、**实际未完成项**、SEAM REQUEST、给 WorkBuddy 的验收路径。
- **不要自动进入 R4**；不 stage / commit / push。
