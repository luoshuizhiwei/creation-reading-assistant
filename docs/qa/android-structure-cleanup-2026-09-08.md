# 原生 Android 结构清理与剩余缺口（2026-09-08）

> 范围：仅 `android/`。本轮处理静态可证的死代码和小型结构债，不改桌面端，不处理工作树中归属未明的
> 并行改动与诊断工件，不代替 WorkBuddy 的真机/视觉验收。

## 结论

- 删除 9 个无生产调用的 Kotlin 文件、1 个只覆盖已死 ViewModel 的测试文件，以及 5 个无调用的局部组件；
  连同 Baseline/Startup Profile 的旧类描述符，共净减 1,767 行（5 行新增、1,772 行删除）。
- 保留注解、序列化或协议驱动的结构：Hilt/WorkManager 入口不能按普通文本引用数判断；
  `SyncContract` 中当前未被 Android 直接实例化的 wire 类型仍是桌面同步协议的一部分，本轮不删。
- 首次扫描曾把跨包复用的 `SettingsComponents.kt` 误判为死代码；Kotlin 编译立即暴露调用缺失，文件已按
  HEAD 原文完整恢复。后续删除必须同时满足“全仓引用核对 + 编译删除测试”，不能只看单目录命中数。

## 已清理

### 整文件删除

- 书架：`ShelfHeader.kt`（搜索已迁移到独立 route，旧 header 及内部 import/filter UI 无调用）。
- 阅读器分页：`ReaderLoadingSkeleton.kt`、`ReaderPageEstimator.kt`、`ReaderScrollBridge.kt`。
- 通用视觉：`BackdropBlur.kt`、`BookmarkIndicator.kt`、`SpineTexture.kt`、`ThemeSwitchButton.kt`。
- 统计：`StatsViewModel.kt` 与仅验证该死 ViewModel 的 `StatsViewModelTest.kt`；当前统计入口使用
  `StatsDashboardViewModel`。

### 局部结构与配置

- `SharedComponents.kt`：删除未被调用、与 `SectionCard`/`GlassSurface` 职责重复的 `GlassCard` 包装层。
- `SealMark.kt`：删除未被调用的 `SealBadge`，保留在用的 `SealMark`。
- `ReaderSheetHelpers.kt`：删除未调用的 `SettingsSwitchRow`、`StatCell`、`InfoRow`，保留在用的
  `OptionPill` 与 `HIGHLIGHT_COLORS`。
- `baseline-prof.txt`、`startup-prof.txt`：移除已删除 `BookmarkIndicator`、`StatsViewModel` 及旧
  `ProfileScreen(StatsViewModel, …)` 的描述符。
- `NotificationPermission.kt`：为已由 API 33 守卫覆盖的权限常量补局部 Lint 说明/抑制。
- `PagedChapterSource.kt`：为内部构造的 `Exact` 增加 `@ConsistentCopyVisibility`，提前适配 Kotlin 2.1
  对 data-class `copy()` 可见性的收紧。
- `SettingsComponents.kt`：仅移除一个未使用 import；其组件本体全部保留。

## 开发级验证

在 `android/` 执行：

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:lintDebug :app:assembleDebug --no-daemon
```

- 第一次编译按预期拦下 `SettingsComponents.kt` 的误删，恢复后第二次执行成功。
- 恢复后的全量 JVM 测试：207 suites / 1694 tests，0 failures / 0 errors / 0 skipped，
  `BUILD SUCCESSFUL`。
- Lint + Debug APK：`BUILD SUCCESSFUL`；最终 Lint 为 `0 errors, 4 warnings`（3 条依赖 lint registry
  版本提示 + 1 条 `ANDROID_ID` 的 `HardwareIds`）。
- 本轮未安装 APK、未运行 instrumentation、未做真机或视觉验收；这些仍由 WorkBuddy 独立执行。

## 仍需处理的不足

### P1：先隔离工作树

当前 Android、desktop、文档和诊断工件仍混在同一未提交工作树。WorkBuddy 已完成 T8 的只读清单，
但其 `git status` 基线是本次清理前的 273 项；提交前仍需与当前工作树重新求差，再由用户决定
暂存/提交/删除边界。本轮没有清理任何归属不明的截图、UI dump 或脚本。

WorkBuddy 随后已针对当前清理结果独立复跑两道 Gradle 门禁并给出最终 PASS：两条命令均 `EXIT=0`，
207 suites / 1694 tests 全绿，Lint 0 error/4 warnings，Debug APK 构建成功；静态复核未发现误删、
行为变化或 Profile 残留。本轮无预期视觉变化，因此未重复 T8 录像矩阵与真机验收。
另需注意其清单文字曾称有 5 个额外编译依赖，最终复核实际为 6 个文件；提交时应以路径为准。

### P2：巨型 UI 模块

以下文件把路由、状态装配、纯策略和大量 Compose section 放在同一编译单元，已影响审查与定向测试：

| 文件 | 当前行数 | 建议 seam |
|---|---:|---|
| `HomeArchiveScreens.kt` | 1720 | 按 Inspirations / Completed / Detail 三个 screen 拆分，共享 formatter 独立 |
| `ReaderSettingsSheet.kt` | 1611 | sheet 导航、微型控件、Typography/Paging/Display/Advanced 分层 |
| `BookDetailSheet.kt` | 1286 | header/CTA、sessions/stats、notes/relations、file/delete 分区 |
| `ReaderRulesSheet.kt` | 1144 | 将 draft/evaluate/reorder 等纯策略移出 Compose 文件 |
| `ReaderTocSheet.kt` | 1108 | chapters、rule recognition、bookmarks、notes 四块拆分 |
| `ReaderNotesSheet.kt` | 1043 | 列表/筛选、编辑器、导出策略拆分 |

这类拆分应逐个做垂直切片并跑定向测试，不应在当前多人脏工作树中进行全仓机械搬家。

### P2：工具链与 API 迁移

- Kotlin/Compose 编译仍报告一批弃用 API：旧剪贴板接口、旧 `menuAnchor()`、非 AutoMirrored 图标、
  ModalBottomSheet properties，以及 Markdown 列表起始序号 API。
- `JsonBridge` 仍用 `ANDROID_ID` 作为同步 envelope 的 device id。Lint 会给出 `HardwareIds`；替换它涉及
  已有导出/同步身份的迁移语义，需先设计稳定的 app-scoped 随机 ID 与兼容策略，不能只为消 warning 改值。
- 依赖提供的 Compose/Lifecycle lint registry 与当前 Lint API 有 3 条版本不匹配提示；应通过依赖/AGP
  对齐解决，不要全局 suppress。

### P3：已知产品与发布开放项

legacy/滚动净化、Markdown 源↔渲染映射、P3.2 通知真机证据、同步/EPUB 异常语料、无障碍/字符串资源化、
MIUI 自动安装超时和首次正式发布仍按 `docs/handoff/current.md` 与现行路线图推进。
