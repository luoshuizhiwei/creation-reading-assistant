# 补做：设置页顶部搜索框（只差这一项）

> 前置：这是「重做移动端设置与阅读设置」的收尾任务。
> 上一轮的完整方案见 `docs/plans/android/2026-09-17-android-settings-redesign-prompt.md`。
> 那里面 7 项已验收通过 6 项，本文件只针对**唯一遗漏的一项**。

## 0. 任务

在**设置首页**（「我的」那一整页）顶部加一个**设置项搜索框**，
要求能搜到**具体设置项**——输入「字号」直接跳到字号那一行并高亮。

只动 Android 端（`android/`），不碰 `src/`、`electron/`、`archives/`。

---

## 1. 为什么还要做（上一轮的验收结论）

上一轮我实跑验证：
`./gradlew :app:compileDebugKotlin` 通过、
`./gradlew :app:testDebugUnitTest` **2437 个用例全过**（0 失败 0 跳过）、
`./gradlew :app:lintDebug` 通过。

但你没做 §2.6 的搜索框。证据：

- `ui/screen/profile/ProfileHomeScreen.kt` 里搜不到任何搜索框代码，
  文件仍是 **443 行**（与改动前一致）
- `ui/screen/profile/` 目录里出现的「搜索」全是**书籍全文索引维护**
  （`DiagnosticsSubPage.kt` 的「重建搜索索引」卡片、`RebuildSearchIndexDialog.kt`），
  与「搜索设置项」是两回事

其余 17 个子页确实改了（`ReaderSubPage` 639→730、`GoalSubPage` 752→682 等），
本轮不需要再动它们。

---

## 2. 要你交付什么

### 2.1 搜索框 UI

- 位于 `ProfileHomeScreen` **顶部**（顶部同步状态卡之后、4 个 `MenuGroup` 之前）
- 样式沿用现有微岛语言，与本轮统一的组件库一致
  （参考 `ui/components/SettingsDensity.kt` 的密度体系）
- 中文占位文案，要让用户一眼知道能搜什么（比如「搜索设置项」）

### 2.2 搜索能力

**必须能搜到具体设置项**，不只是子页名称：

- 输入「字号」→ 结果指向「阅读设置 › 字号」
- 点击结果 → 导航到对应子页 → **滚动到那一项** → **短暂高亮**
- 支持搜索子页名称（输入「同步」能出「局域网同步」）作为兜底

### 2.3 索引方案（重点，请务必先想清楚）

这是我认为最容易做歪的部分。设置项有**几百个**，分布在 17 个子页里。

请说明并实现：

- 索引**怎么组织**：硬编码维护一份清单？还是从 UI 结构自动派生？
- **怎么保证不随改版失效**——这是关键。如果硬编码一份「字号 → READER 子页」的映射，
  将来有人移动了字号的位置或改了标题，索引就静默失效（用户搜到却跳不到，
  或搜不到明明存在的项）。请给出一个能对抗漂移的方案，
  并说明你为什么选它、代价是什么。
- 至少要覆盖哪些子页和项。
- 是否需要给设置项加稳定的 key（如果加，说明加在哪、怎么保证唯一）。

### 2.4 滚动与高亮

说明实现方式：

- 怎么滚动到目标项（`LazyListState.animateScrollToItem`？需要给 item 建 key 吗？）
- 高亮怎么呈现、持续多久、怎么自动消失
- 目标项被折叠在「高级」折叠区里时怎么办
- 尊重 `rememberReducedMotion`（减少动效设置开启时不要做滚动动画）

---

## 3. 现状参考（我已替你核实过）

### 3.1 导航机制

`ui/screen/profile/ProfileRoute.kt` 381 行起处理 `ProfileAction.OpenSubPage`：

```kotlin
is ProfileAction.OpenSubPage -> {
    if (action.page == ProfileSubPage.READING) {
        navController?.navigate("my-reading")
    } else {
        navController?.navigate("profile/${action.page.routeSegment}")
    }
}
```

即：**导航靠字符串路由** `profile/{routeSegment}`（`routeSegment` = 枚举名小写）。
注意 `READING` 是特例，走 `my-reading`。

子页枚举 `ProfileSubPage`（`ProfileUiState.kt` 17–23 行）17 个：
`SYNC, WEBDAV, APPEARANCE, READER, AI, GOAL, TAGS, CATEGORIES, SHELVES, READING,
NOTES, SELECTION, STORAGE, PRIVACY, ABOUT, DIAGNOSTICS`

已有两套中文标题映射（`ProfileUiState.kt` 内）：
`subPageTitle()`（完整标题，如「阅读设置」「局域网同步」）和
`subPageEyebrow()`（短标签，如「阅读体验」「外观」）。
**搜索请直接复用这两个函数，不要另造一份映射。**

### 3.2 子页结构

各子页用 `PageLazyColumn` + 若干 `item(key = "...") { SectionCard { ... } }`。
**很多 item 已经带了 `key`**（如 `key = "reader_typography_section"`），
但那是**分组卡片**的 key，**不是单个设置项**的 key——要滚到具体某一项，
你需要更细的锚点。

### 3.3 本轮已统一的组件库

`ui/components/SettingsDensity.kt` 定义了 `SettingsDensity { COMPACT, REGULAR }`
和 `SettingsMetrics`，统一控件通过密度参数切换。
新写的搜索框请沿用这套，不要另起一套。

---

## 4. 约束

- **硬约束：不能让现有功能失效。** 加搜索框不能影响现有导航和设置项读写。
- **不要顺手重构其它子页。** 本轮只做搜索框，其余已验收通过的部分保持原样。
  如果你发现某处确实需要小改才能支持搜索（比如给 item 加 key），
  改动要**最小化**并说明。
- 允许改 `ProfileHomeScreen.kt`、`ProfileUiState.kt`（加 Action/State）、
  `ProfileRoute.kt`（处理导航）、以及新增索引文件。
- 全部 UI 文案用**中文**。
- 尊重 `rememberReducedMotion` 与 `rememberHaptic`。

---

## 5. 验收标准（我会照这个查）

实现完请确保：

1. `./gradlew :app:compileDebugKotlin` 通过
2. `./gradlew :app:testDebugUnitTest` **全过**（现有 2437 个用例一个都不许挂）
3. `./gradlew :app:lintDebug` 通过
4. 补一个针对**搜索索引**的测试（至少覆盖：能搜到具体设置项、
   索引条目指向的子页和锚点确实存在）。这是 §7 里定好的测试范围。
5. 在回复里说明：索引怎么组织、怎么防漂移、覆盖了哪些子页和多少项。

---

## 6. 项目约定

- 只动 `android/`；命令在 `android/` 目录下执行：
  `./gradlew :app:testDebugUnitTest` / `:app:lintDebug` / `:app:assembleDebug`
- 真机验证只在已连接的真实 Android 手机上进行，**不用 MuMu 模拟器**；
  先 `adb devices` 核对，再用 `adb -s <serial>`
- 项目路径已改为 ASCII，**不要改回中文路径**（会导致 Gradle 测试编码失败）
- `gradle.properties` 的 `kotlin.daemon.jvmargs` 必须保留 `-Dfile.encoding=UTF-8`
- 工作区未提交改动是用户资产，不要 reset / checkout / 全仓格式化
- 未经我要求，不要 commit、不要 push

---

## 7. 另外一件需要你确认的事（不是本轮任务，但请回答）

上一轮你把同步的 3 个开关 + 冲突策略**真接线**了（新增 `SyncPrefsStore.kt` 落盘，
`SyncRepository` 也改了）。单元测试过了，但**真机行为没验证过**。

请在回复里说明：你有没有在真机上验证过三个开关真的生效？
如果没有，请给出**具体的验证步骤**（我会在真机上照着做），
说清楚怎么确认「关闭书籍同步后，同步时确实不同步书籍」。
