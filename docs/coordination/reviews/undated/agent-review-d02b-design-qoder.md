# D-2-B-DESIGN-REVIEW 独立复核报告 — Qoder

> 复核 Agent：Qoder　|　声明模型：`qwen3.8Flash`
> 任务编号：D-2-B-DESIGN-REVIEW（只读设计复核，不实现代码）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 复核开始/结束 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 被审报告：`docs/coordination/designs/agent-design-d02b-zcode.md`（**未修改**）
> 本报告路径：`docs/coordination/reviews/undated/agent-review-d02b-design-qoder.md`
> **最终结论：PARTIAL**
> 状态：等待 Codex 主任务裁决。**未领取下一任务，未实施任何代码。**

---

## 1. 最终结论

**PARTIAL。**

Zcode 的**问题诊断正确**（三个子页拿不到 `scope`、页面无任何作用域提示、26 项清单与源码一致），
**推荐方向（页级提示）也正确**，但报告在三个必须修订的点上不成立：

| # | 不成立的主张 | 我的独立证据 |
|---|---|---|
| **①** | **「最小实现只需修改 4 个产品文件」** | 26 个字段所用的 6 类行控件**没有任何一个有 trailing/slot 参数**。逐行徽标必须改 `CompactSettingComponents.kt`、`SettingsComponents.kt`、`CustomTipDialog.kt`、`ReaderSettingsCommon.kt` —— 真实边界 **8 个文件**，其中 3 个被 profile 设置页共用（§4） |
| **②** | **「A′ 以 ThemeSheet 为模板，是既有已验证模式」** | `ReaderThemeSheet` 只有**一条页级提示**，其内部 `SettingBrightnessRow`、`CompactSliderRow` **没有任何行内徽标**。它只能证明「页级说明」有先例，**不能**证明「逐行徽标」有先例（§5） |
| **③** | **把 P3-1 当作 D-2-B 的问题证据** | 我在真机报告里的 P3-1 说的是**「高级兼容」子页**，那一页的两个引擎字段在 D-2-A 后**已经是 per-book**，缺提示的误导方向与 D-2-B **正好相反**。把它记在 D-2-B 账上是**张冠李戴**（§9） |

另外两处**新发现的清单缺口**（Zcode 未记录）：`pageTurnSpeed` 在**根页**也有编辑入口（26 字段 / 27 个书内编辑点）；
6 个 `customTip*` 行显示的是 `effective*()`，其值可能来自**本书可覆盖**的 `headerLeft` 等字段。

**测试设计需要实质修订**：提出的「提取 `settings.copy(field)`」守卫会被多参 `copy` 与
三个页面文件之外的写点绕过；「字段附近有徽标调用」的文本扫描本身不可靠；
建议的 26 项参数化路由测试与**两个既有测试用例重复**。

**建议的替代方案（方案 4′）用 1 个产品文件即可完成 D-2-B 的目标**，见 §7。

---

## 2. Zcode 主张逐项核对表

| # | 报告主张 | 我的判定 | 独立证据 |
|---:|---|---|---|
| 1 | 26 项 = 翻页 7 + 显示 9 + 护眼 10 | ✅ **成立** | 三页逐行读取；`settings.copy(field)` 机械提取（§3） |
| 2 | 无增减、无改名、无迁移 | ✅ 成立 | 26 项均不在 `ReaderOverrideKey` 的 22 个 id 中（脚本比对，交集为空） |
| 3 | 所有 26 项均可从 UI 到达，无死字段 | ✅ 成立，但**不完整** | 每项都有**两个**编辑入口：书内页 + `ui/screen/profile/ReaderSubPage.kt`（26/26 命中） |
| 4 | 三个页面当前拿不到 `scope` | ✅ **成立** | `ReaderSettingsSheet.kt:117-121` 只传 `(settings, onSettingsChange)`；`SettingsRoot` 才收到 `scope`（`:108`） |
| 5 | 26 项在 BOOK 下写全局（路由推理） | ✅ 成立 | `ReaderSettingsRouter.kt:75-81` + `ReaderOverride.kt:142-143 mask()` 只 fold `keys`，非可覆盖字段保留 `edited` → `plan.global` → `ReaderSettingsEditApplier.kt:34-36` |
| 6 | 推荐 A′（页级提示 + 逐行徽标） | ⚠️ **半成立** | 页级提示可行且成本低；**逐行徽标在当前组件 API 下不可能只改 4 个文件** |
| 7 | 最小实现只需 4 个产品文件 | ❌ **不成立**（对徽标那半） | §4：真实边界 8 文件 |
| 8 | 不修改路由、存储、`ReaderOverrideKey`、D-2-A | ✅ 成立且正确 | 与任务书一致，我复核后无异议 |
| 9 | ThemeSheet 是「子面板接收 scope + 页内明示全局项」的既有模式 | ⚠️ **只对前一半** | 页内明示 ✅；但「明示」的形式是**一句话点名例外项**，不是逐项徽标 |
| 10 | 亮度是 App 窗口亮度不是系统亮度 | ✅ 成立 | `ReaderPlatformEffects.kt` / `ReaderWindowPolicy`；但报告**没发现现有 UI 已写着「跟随系统亮度」**（§9） |
| 11 | 真机 P3-1 支持 D-2-B 的问题存在 | ❌ **不成立** | P3-1 讲的是高级兼容页（引擎两项，已 per-book） |
| 12 | 亮度措辞风险记 P2 | ❌ **分级错误** | 假设性、尚未实施方案中的预防约束，不应计入 P 数量（§9） |
| 13 | 结构守卫可「提取 `settings.copy(<field>)`」 | ⚠️ **需修订** | 多参 `copy` 会漏（实测 `ReaderDisplaySettings.kt:30` 一行写两个字段）；写点也存在于三页之外（`ReaderSheetHost.kt:579`、`ReaderScaffold.kt:411`） |
| 14 | 26 项参数化路由测试有必要 | ❌ **重复** | `ReaderSettingsRouterTest:72` 与 `:254` 已覆盖同一契约（§10） |
| 15 | 现有 `ReaderSettingsSheetTest` 不受影响 | ✅ 成立 | `renderSettings`（`:190-227`）不传 `scope`，依赖 `SettingsSheet.scope` 默认值 `BOOK`（`ReaderSettingsSheet.kt:69`） |
| 16 | 本轮无 SEAM REQUEST | ❌ **不成立**（若坚持逐行徽标） | §12 |

---

## 3. 26 字段独立核对结果

### 3.1 核对方法（不采信报告）

```bash
# 每页机械提取写入字段
grep -o "settings\.copy([a-zA-Z]*" <page>.kt | sed 's/settings.copy(//' | sort -u
# 与 ReaderOverrideKey 的 id 集合求交（脚本比对，非人工）
grep -E '^    [A-Z_]+\("' ReaderOverride.kt     # 22 个 id
```

`ReaderSettings` 共 **58** 个字段 = 22 可覆盖 + 26（D-2-B 清单）+ 10 其他。
**26 ∩ 22 = ∅**（脚本输出 `set()`）⇒ 契约「这 26 项不在枚举中」独立成立。

### 3.2 翻页页（`ReaderPagingSettings.kt`，158 行）

| 字段 | 行 | 控件 | 显示条件 | 是否可覆盖 |
|---|---|---|---|---|
| `pageTurnSpeed` | :60-72 | `MicroTrackSliderRow` | 始终 | ❌ 全局 |
| `tapZoneMode` | :74-81 | `MicroOptionPillRow` | 始终 | ❌ 全局 |
| `tapActionPreset` | :83-94 | `MicroOptionPillRow` | 始终 | ❌ 全局 |
| `volumeKeyPaging` | :116-122 | `MicroSettingsSwitchRow` | 始终 | ❌ 全局 |
| `volumeKeyInvert` | :125-133 | `MicroSettingsSwitchRow` | **仅 `volumeKeyPaging`**（:123） | ❌ 全局 |
| `volumeKeyPagingDuringTts` | :135-141 | `MicroSettingsSwitchRow` | 同上 | ❌ 全局 |
| `autoPageSpeed` | :144-155 | `MicroTrackSliderRow` | 始终 | ❌ 全局 |
| （对照）`readerMode` :31-38、`pageTurnEffect` :40-49、`screenOrientation` :100-107 | | | | ✅ 本书可覆盖 |

**分组连续性实测**：第 1 组「翻页动画与点击区域」内顺序为
`readerMode(本书) → pageTurnEffect(本书) → pageTurnSpeed(全局) → tapZoneMode(全局) → tapActionPreset(全局) → screenOrientation(本书)`
⇒ 3 个全局项**在中间连续**，但被本书项首尾夹住；第 2 组「自动翻页与按键」**4 项全为全局**。

### 3.3 显示页（`ReaderDisplaySettings.kt`，135 行）

| 字段 | 行 | 控件 | 显示条件 | 备注 |
|---|---|---|---|---|
| `brightness` | :27-33 | `SettingBrightnessRow` | 始终 | **联动写 `lastFixedBrightness`**（:30，同一 `copy` 两个字段） |
| `keepAwake` | :52-58 | `MicroSettingsSwitchRow` | 始终 | 与 `immersiveMode`/`showProgressBar`（本书）同组，**交错** |
| `batteryTipStyle` | :93-101 | `MicroOptionPillRow` | 仅 `showReaderInfo` | |
| `customTipHeaderLeft/Center/Right`、`customTipFooterLeft/Center/Right` | :103-131 | `HeaderFooterPicker` | 仅 `showReaderInfo` | **显示值来自 `effective*()`**，见 §3.5 |
| （对照本书项）`immersiveMode` :35-42、`showProgressBar` :44-50、`autoHideSeconds` :67-75、`showReaderInfo` :84-90 | | | | ✅ |

### 3.4 护眼页（`ReaderEyeCareSettings.kt`，180 行）

10 项全部为全局，**无任何本书可覆盖项**（机械提取结果与 26 清单完全吻合）。
条件显示：`eyeCareStartMinute`/`EndMinute` 仅 `eyeCareScheduleEnabled`（:83）；
`eyeCareTemperature`/`Intensity`/`OledBlackCompat` 仅 `filterEnabled || scheduleEnabled`（:97）；
`readingRhythmReminderMinutes` 仅 `readingRhythmReminderEnabled`（:163）。
⇒ 报告「护眼页可整页一条提示」的判断**成立**，且这是三页中唯一成立的。

### 3.5 报告未记录的三处事实（必须补进设计前提）

**(a) `pageTurnSpeed` 有两个书内编辑入口。**
`ReaderSettingsSheet.kt:216-228` 根页「快捷排版」里也有一个 `翻页速度` 滑块
（testTag `reader-page-turn-speed`），与翻页页的那个（testTag `reader-page-turn-speed-paging`）绑定同一字段。
⇒ 只在 `ReaderPagingSettings.kt` 加标记，**根页那一行仍然无任何提示**；
而根页的字号/行距行**已经**有 `本书` 徽标（`QuickFontSizeRow(isOverridden=…)`、`:190` subtitle），
所以这个不对称是**当前就存在**的透明度缺口，属于 D-2-B 的射程，报告完全没提。

**(b) 6 个页眉页脚行显示的是 `effective*()`，不是它写入的字段。**

```kotlin
// SettingsStore.kt:279-294
fun effectiveHeaderLeft() = customTipHeaderLeft.ifBlank { headerItemToTemplate(headerLeft) }
```

`customTipHeaderLeft` 是全局（在 26 里），而回落源 `headerLeft` **是 `ReaderOverrideKey.HEADER_LEFT`（本书可覆盖，label 恰好也叫「页眉左侧」）**。
⇒ 该行显示值可能来自本书、写入值一定进全局。给这一行贴「所有书籍」不算错（写侧确实是全局），
但**本书页眉内容目前无法通过 UI 真正按书设置**：`headerLeft` 在全仓**没有任何 UI 写点**
（`grep copy(headerLeft` 只命中 `ReaderOverride.kt:108` 的枚举写分支）。
⇒ 报告把「这 26 项本来就应该保持全局」当作不证自明的前提，对 `customTip*` 这 6 项而言**前提不完整**。

**(c) 亮度还有第三个入口，且完全绕过作用域机制。**
`ReaderScaffold.kt:407-414` 把屏幕左/右边缘上下滑手势接到
`settingsVm.updateReader { withCommittedGestureBrightness(next) }`（`ReaderBrightnessGesture.kt:65-69`），
**不经 `applyReaderEdit`、不经 router**。语义上仍是全局（正确），
但它是这 26 项里**最常用**的写入路径，且任何"页内提示"都覆盖不到它。
同理 `autoPageSpeed` 也由 `ReaderScaffold.kt:611-615` 直接 `updateReader` 写入。

### 3.6 数量结论

**26 项清单正确**（字段名、页面归属、7/9/10 全部与源码一致）。
但「26 项 = 26 个待标注位置」**不成立**：书内实际是 **27 个编辑点 + 3 个非面板写点**
（根页翻页速度、亮度边缘手势、自动翻页速度写点）。

---

## 4. 当前组件 API 与真实实现边界

### 4.1 六类行控件的可用插槽（逐个读签名）

| 组件 | 定义文件 | 有无 trailing/slot | 有无 subtitle | 覆盖的 26 项 |
|---|---|---|---|---|
| `CompactOptionPillRow` | `ui/components/CompactSettingComponents.kt:134` | ❌ 无 | ✅ 有 | tapZoneMode、tapActionPreset、batteryTipStyle（3） |
| `CompactSwitchRow` | 同上 :429 | ❌ 无 | ✅ 有 | volumeKey×3、keepAwake、eyeCare×4（8） |
| `CompactSliderRow` | 同上 :335 | ❌ 无 | ❌ **无** | pageTurnSpeed、autoPageSpeed、色温、强度、两个提醒分钟（6） |
| `CompactLinkRow` | 同上 :478 | ❌ 无 | ✅（必填，已被占用） | 开始/结束时间（2） |
| `SettingBrightnessRow` | `ui/components/SettingsComponents.kt:119-146` | ❌ 无 | — | brightness（1，内部含 1 开关 + 1 条件滑块） |
| `HeaderFooterPicker` | `ui/components/CustomTipDialog.kt:220-266` | ❌ 无 | — | customTip×6（6） |

唯一有 `trailing: @Composable (()->Unit)?` 的是 `CompactSettingRow`（:259-266），
**而这 26 项一个都没用它**。

⇒ **任务疑点一-1 的答案：不能。** 逐行徽标在现有组件 API 下**无法**稳定实现。

### 4.2 若坚持逐行徽标，真实文件边界

| 文件 | 为什么必须改 | 是否被书外页面共用 |
|---|---|---|
| `ui/components/CompactSettingComponents.kt` | 给 4 个 Compact 行加 `trailing`（或 `scopeLabel`）参数 | 否（仅 `ReaderSettingsCommon.kt` + `ReaderThemeSheet.kt` 使用，实测 2 个消费文件） |
| `ui/screen/reader/sheets/ReaderSettingsCommon.kt` | 4 个 `Micro*Row` 包装器要透传新参数；徽标本体也应在此定义一次 | 否（reader sheets 内部） |
| `ui/components/SettingsComponents.kt` | `SettingBrightnessRow` 内部两行需要徽标位 | **是**：`ui/screen/profile/ReaderSubPage.kt` |
| `ui/components/CustomTipDialog.kt` | `HeaderFooterPicker` 的 `Row(SpaceBetween)` 只有 label + value chip | **是**：`ReaderSubPage.kt`（18 个 callsite 中的多数） |
| `ReaderSettingsSheet.kt` | 传 `scope` | — |
| `ReaderPagingSettings.kt` / `ReaderDisplaySettings.kt` / `ReaderEyeCareSettings.kt` | 逐项加标记 | — |

**= 8 个产品文件**，其中 2 个是**跨页面共用组件**（profile「我的 → 阅读设置」也调同一批控件）。
报告 §8.1 的「4 个」把 §8.4「用页内私有 `AllBooksBadge`」当成了免费午餐，
但**页内私有徽标只能贴在控件外面**，控件内部没有位置。

### 4.3 页外包裹（`Box`/`Column`/额外 `Row`）能否形成清晰关联

技术上可行：`MicroSettingsSection` 的 `content` 是 `ColumnScope` lambda
（`ReaderSettingsCommon.kt:118` → `CompactSettingsGroup` 的 `Column`），
调用方可以在两个控件之间插入一行 `Text`/`Surface`。但：

1. **归属不清**：徽标成为独立一行，位于 `MicroSectionDivider()` 之间，
   视觉上无法判断它属于上一行还是下一行（翻页页第 1 组是 本书/本书/全局/全局/全局/本书 的交错序列，最坏情况徽标夹在两个全局项之间仍会被误读）；
2. **亮度行必然歧义**：`SettingBrightnessRow` 内部是「跟随系统亮度」开关 +（条件）「亮度」滑块，
   **没有外层标题**。外面包一个徽标 → 用户会以为它只修饰其中一行。**任务疑点一-4 成立。**
3. **`HeaderFooterPicker` 六行**：`Row(SpaceBetween){ Text(label) … Surface(value ≤140dp + chevron) }`，
   竖屏 393dp 下剩余空间足够塞一个 10sp 徽标（估算 label 56dp + 徽标 48dp + value 140dp + chevron 14dp + 内边距 28dp ≈ 286dp < 393dp），
   **但必须改 `CustomTipDialog.kt`**（无插槽），而该组件被 profile 页共用。
4. **`MicroTrackSliderRow` / `CompactLinkRow` 完全没有文字槽**：滑块的 title 行右侧已被 `valueLabel` 占用，
   `CompactLinkRow` 的 subtitle 已被「点击调整生效时刻」占用。

### 4.4 三个页面各定义私有 `AllBooksBadge` 是否重复

**是**，三份近似重复（且根页也需要 → 第四份）。
正确位置是 `ReaderSettingsCommon.kt`（`internal`，与既有 `OverrideBadge:199-219` 同处），
**不构成公共 API 扩张**（该文件不是 `ui/components/`）。
报告 §10.3 把「放进 `ReaderSettingsCommon.kt`」说成需要 SEAM REQUEST 的理由是
**文件所有权未获授权**，这在协作规则上成立（AGENTS.md：不得直接越界改他人所有文件），
但技术上它比"三份私有副本"更好。我按**所有权**而非技术理由登记 SEAM（§12）。

---

## 5. ThemeSheet 先例成立到什么程度

`ReaderThemeSheet.kt:240-267` 实测：

```kotlin
Surface(shape = PillShape, color = primary.copy(alpha = 0.08f),
        modifier = Modifier.align(Alignment.CenterHorizontally)) {
    Row { Icon(Icons.Outlined.Info, size 12.dp); Text(
        if (scope == BOOK) "调整仅作用于当前书籍 · 亮度为全局"
        else               "调整将作用于全局默认设置", style = labelSmall) }
}
```

| 问题 | 我的回答 |
|---|---|
| 能否证明「页级说明」有先例 | ✅ **能**，而且是唯一先例，形态就是 `Info 图标 + 一行 pill` |
| 能否证明「每个全局字段都有行内徽标」有先例 | ❌ **不能**。ThemeSheet 内部就是 `CompactSliderRow`（字号）+ `SettingBrightnessRow`（亮度）+ 纸张网格，**没有任何行内徽标**。报告 §6.1「以 ThemeSheet 为模板」把只覆盖了 A′ 一半的先例当成全案先例 |
| 亮度是否同一数据同一写路径 | ✅ 同一。两处都走 `applyReaderEdit`（`ReaderSheetHost.kt:573-582` vs `ReaderDisplaySettings.kt:27-33`），且都联动写 `lastFixedBrightness`。⇒ ThemeSheet 那句「亮度为全局」是**准确**的 |
| 既有文案是否已足够清晰 | **部分**。它只点名了亮度，但选纸张时同一次 `copy` 还联动写了 **`dayNightLightPaper` / `dayNightDarkPaper`**（`ReaderSheetHost.kt:556-563`，两者均非可覆盖项 → 进全局），提示里没说。⇒ **照抄这句会连带继承它的不完整** |
| 是否应复用同一用词 | ✅ **应该**，且这是我对报告 §7.2/§7.6 的主要异议（见下） |

### 5.1 用词：报告建议的「所有书籍」会与既有词汇打架

产品当前的用户词是**「本书 / 全局」**这一对，已出现在三处：
根页胶囊「只改本书」/「改全局默认」（`ReaderSettingsCommon.kt:447`）、
ThemeSheet 提示「亮度为**全局**」、恢复默认对话框「作用范围：全局」。
`ReaderSettingsRouter` 的 KDoc 也用「不可覆盖项永远进全局」。

报告 §7.6 把「单独的『全局』」列入**禁用词**，理由是可能被理解成账号/云同步。
但这个风险在**根页已经存在**——用户必须先点「改全局默认」才能进入 GLOBAL。
若子页改说「所有书籍」而根页仍说「全局默认」，用户需要自己建立两者等价的映射，
**这是新引入的理解成本，不是消除的**。

⇒ **我的建议**：主词沿用「全局」，在同一句里给一次白话释义「（所有书共用）」，
之后不再重复。这样既满足任务书「不得误解为账号/云端」，又不制造第二套术语。
最终文案见 §8。

---

## 6. 四种 UI 方案比较

| 维度 | 方案1 仅页级提示 | 方案2 页级 + 26 徽标（Zcode A′） | 方案3 分组/区块 | 方案4 混合（**我推荐**） |
|---|---|---|---|---|
| 修改前能否知道影响范围 | ✅ 需读一句 | ✅ 最直观 | ⚠️ 只有被分组的项知道；显示页 brightness/keepAwake 与本书项**交错**（G,B,B,G），无法成组 | ✅ 一句规则 + 纯全局分组再补一行 |
| 「全局」被误解为云端 | ⚠️ 靠释义 | ⚠️ 同 | ⚠️ 同 | ✅ 同（用「全局（所有书共用）」一次性钉死） |
| 视觉密度 / 横屏 / 大字号 | ✅ 每页 +1 行 | ❌ 26 个徽标；书内 sheet 高仅 `maxHeightFraction=0.62`（横屏 ≈252dp 可视） | ⚠️ 需重排 | ✅ 每页 1 行 + 最多 2 个分组 description |
| TalkBack | ✅ 1 句 | ❌ 进页即连续 7/9/10 次「所有书籍」 | ⚠️ 分组标题会重复朗读 | ✅ 1 句 + 分组各 1 次 |
| 当前组件 API 能否支持 | ✅ **能**（`SettingsSheet` 里渲染一行） | ❌ **不能**（§4.1） | ⚠️ 需重排 + 空分组处理（护眼页「本书区块」必空） | ✅ **能**：`MicroSettingsSection` 已有 `description` 参数（`ReaderSettingsCommon.kt:117` → `CompactSettingsGroup:109-116` 真实渲染），**零组件改动** |
| 真实文件边界 | **1 个** | **8 个**（2 个跨页共用） | 5–6 个 + 布局回归 | **1 个（必做）/ 4 个（含可选加强）** |
| 未来新增全局字段的成本 | 0（句子是「除…外」的例外式） | 每个新字段都要加徽标，漏加即静默 | 要判断归哪个区块 | 0（例外式句子自动覆盖） |
| 测试可靠性 | ✅ 断言 3 条句子 + 例外集合与枚举求交 | ⚠️ 需"徽标紧邻字段"的脆弱文本扫描 + 数量断言 | ⚠️ 依赖层级 | ✅ 同方案1，且**不需要**数量断言 |
| 是否影响 D-2-A | ❌ 否 | ⚠️ 报告 §5.2-8 自己承认分区方案有把引擎键误归全局的风险 | ⚠️ 同 | ❌ 否 |

### 6.1 方案3（分组）为什么不够

- 显示页「屏幕显示」组实测顺序 `brightness(G) → immersive(B) → progress(B) → keepAwake(G)`，
  **全局项不连续**，无法在不打散既有语义分组的前提下成组；
- 护眼页 10/10 全局 ⇒ 分组后「本书设置」区块恒空，需要额外空态；
- 翻页页第 1 组的 3 个全局项被本书项首尾夹住（§3.2）。
⇒ 报告对方案 B 的否定理由**我认同**，但它同样适用于我推荐的方案4 的"分组加强"部分——
所以我只对**已经天然连续**的两个组（翻页页「自动翻页与按键」4 项、护眼页两组）用 `description`，
**不重排任何控件**。

### 6.2 方案 C（弹窗/自动切作用域）

报告的不推荐结论**我完全认同**，理由（自动改写会静默推翻用户的显式选择，
正是 D-2 原始缺陷的同构再现）成立，且我复核了 `applyReaderEdit` 后确认它必须改路由入参才能做到。

---

## 7. 最终推荐方案

### 方案 4′：**页级「例外式」规则句（必做）+ 纯全局分组 `description`（可选加强）**

**必做部分（1 个产品文件）**

在 `ReaderSettingsSheet.kt` 的 `ReaderSheetScaffold` 内容里、`AnimatedContent` **之前**
渲染一行提示（形态照抄 ThemeSheet 的 `Info + pill`），文案由 `when(page, scope)` 给出。
`page`（:80）与 `scope`（:69）都在该文件内可用 ⇒
**不需要给三个子页加 `scope` 参数，也不需要新增任何公共组件。**

关键设计属性：**句子按"例外清单"表述**——
「除 X、Y、Z 外，本页其余设置都是全局（所有书共用）」。
⇒ 新增一个**全局**字段时这句话**自动仍然正确**，不需要改 UI；
只有新增**本书可覆盖**字段时才必须更新句子，而那正是守卫测试要盯的方向。

**可选加强（同 PR，纯参数，零组件改动）**

对天然整组全局的分组补 `MicroSettingsSection(description = …)`：
- 翻页页「自动翻页与按键」（4 项全全局）；
- 护眼页「护眼滤镜」「阅读提醒」（10 项全全局）。
显示页**不加**（其全局项与本书项交错，加了会错）。

**明确不做**

- 26 个逐行徽标（需 8 文件、2 个跨页共用组件、TalkBack 噪声、横屏拥挤）；
- 根页「翻页速度」行的徽标（改为：根页不显示提示，因为**根页本身就有作用域胶囊**，
  且该行的全局性由 §8 句子在子页承担；登记为残留风险 R-3）；
- 二级 `CustomTipDialog` 内加提示（需改公共对话框组件）。

### 7.1 为什么它满足任务书列出的 8 条硬要求

| 要求 | 满足方式 |
|---|---|
| 1 修改前知道影响所有书籍 | 进页第一行即见，早于任何点击 |
| 2 不误解为账号/云端 | 用「全局（所有书共用）」一次性钉死含义 |
| 3 不改 26 项存储与生效语义 | 纯渲染，router/store/枚举零改动 |
| 4 不影响 D-2-A | 高级兼容页用**另一句**（「本页设置只改当前这本书」），顺带把我真机 P3-1 记的那个方向相反的缺口补上 |
| 5 小屏/横屏/大字号不拥挤 | 每页 +1 行（12sp），无行高变化 |
| 6 TalkBack 不重复 | 每页 1 句，不是 26 次 |
| 7 文件边界真实 | 1 个文件，可当场核对 |
| 8 测试可证明 | 断言"句子中的例外集合 == 该页实际可覆盖字段集合"，从枚举与源码提取，**不与生产共用公式**（§10） |

---

## 8. 最终中文文案（可直接进代码与测试断言）

> 原则：主词沿用既有的「全局」，首次出现给一次白话释义；不使用 Markdown 星号；
> 不出现「覆盖 / scope / DataStore / 默认值来源」等技术词；不使用「系统亮度」描述本应用窗口设置。

### 8.1 页级提示（BOOK 作用域）

| 页面 | 文案（单行，12sp，`Info` 图标 + pill） |
|---|---|
| 翻页与操作 | `除阅读模式、翻页效果、屏幕方向外，本页其余设置都是全局（所有书共用）` |
| 显示与页眉页脚 | `除沉浸模式、进度条、菜单隐藏、页眉页脚开关外，本页其余设置都是全局（所有书共用）` |
| 护眼与提醒 | `本页设置都是全局（所有书共用），不跟随单本书` |
| 排版 | `本页设置只改当前这本书` |
| 高级兼容 | `本页设置只改当前这本书` |
| 根页（阅读设置） | 不显示（已有「只改本书 / 改全局默认」胶囊） |

### 8.2 页级提示（GLOBAL 作用域）

统一一句，所有子页相同：

`本页设置会写入全局默认，影响所有书`

### 8.3 分组说明（可选加强，`MicroSettingsSection.description`）

| 位置 | 文案 |
|---|---|
| 翻页页「自动翻页与按键」 | `这几项是全局设置，所有书共用` |
| 护眼页「护眼滤镜」 | `全局设置，所有书共用` |
| 护眼页「阅读提醒」 | `全局设置，所有书共用` |

### 8.4 亮度项

| 问题 | 裁决 |
|---|---|
| 是否新增「阅读页亮度」标签 | **不新增**。`SettingBrightnessRow` 内部现有标题是「跟随系统亮度」/「亮度」，改标题要动跨页共用组件（`SettingsComponents.kt`），代价与收益不匹配 |
| 现有「跟随系统亮度」是否算错 | **不算错**（`brightness < 0` = 窗口亮度跟随系统值），但它是报告 §7.6 禁用词表里被禁的字符串 ⇒ **报告的禁用清单与现有 UI 自相矛盾**，必须修正为「禁止把该设置**描述成会改变手机系统设置**」，而不是禁止出现「系统亮度」四字 |
| 亮度如何获得提示 | 由 8.1 显示页那句「除…外，其余都是全局」覆盖；**不**为它单独加徽标 |

### 8.5 无障碍

页级提示是**普通文本节点**，TalkBack 自然朗读，无需 `contentDescription`；
建议给它 `Modifier.testTag("reader-settings-scope-notice")`，
**不要**设 `contentDescription`（否则会把可见文字替换掉）。

### 8.6 禁用词（修正版）

| 禁用 | 原因 |
|---|---|
| `**全局**` 等 Markdown 源码 | 真机已证实星号原样渲染（既有 P3） |
| 「会改手机/系统设置」这类**承诺改变系统**的表述 | 亮度只作用于本应用窗口 |
| 「覆盖」「作用域」「回退」等技术词 | 用户不可理解 |
| 新增与「全局」并存的第二套同义词（只用「所有书籍」而不提「全局」） | 与根页胶囊、恢复默认对话框术语冲突 |

---

## 9. 风险重新分级

### 9.1 当前已存在问题（计入 P 数量）

| 级别 | 项 | 证据 |
|---|---|---|
| **P3-A** | 翻页 / 显示 / 护眼三个子页**完全没有作用域提示**，且**架构上拿不到 `scope`**；用户在「只改本书」语境下改这 26 项时得不到任何反馈 | `ReaderSettingsSheet.kt:117-121`；真机已复现同类现象（高级页） |
| **P3-B** | 根页「快捷排版」里**字号/行距有 `本书` 徽标、翻页速度什么也没有**，而翻页速度恰是全局项 —— 同一屏内的标注不对称 | `ReaderSettingsSheet.kt:180`（`isOverridden`）vs `:216-228`（无任何提示） |

**P0 = 0　P1 = 0　P2 = 0　P3 = 2**

> 说明：D-2-B 的本体是**信息透明度**，26 项的写入结果**全部符合设计意图**（router 已证明），
> 因此不存在数据正确性缺陷，最高只到 P3。这与任务书"目标只是让用户知道"的定位一致。

### 9.2 设计风险（**不计入** P 数量）

| 编号 | 风险 | 若不处理的后果 |
|---|---|---|
| R-1 | 若实现时把亮度描述成"会改手机亮度" | 新的错误认知 —— 报告把它记为 P2，**实为尚未实施方案中的预防约束** |
| R-2 | 逐行徽标方案会牵动 2 个 profile 页共用组件 | 回归面从书内扩到「我的」页 |
| R-3 | 亮度边缘手势与自动翻页速度写点绕过 router（`ReaderScaffold.kt:411`、`:613`） | 任何"页内提示"都覆盖不到；若将来把它们改成可覆盖项会静默漏接 |
| R-4 | `customTip*` 显示 `effective*()`，回落源是本书可覆盖的 `headerLeft` 等 | 若将来给 `headerLeft` 加 UI，"所有书籍"标记会立刻变成错标 |
| R-5 | 例外式句子需要与新本书可覆盖项同步 | 靠守卫测试兜住（§10.2），不靠人记 |
| R-6 | 高级兼容页缺提示（我真机 P3-1）与 D-2-B 的提示是**反向**语义 | 若复用同一句模板而不区分页面，会把 per-book 项标成全局 |

### 9.3 尚未验证项

见 §13。

### 9.4 不应计入缺陷数量的假设

| 项 | 为什么不算 |
|---|---|
| 报告原 P2「亮度若写成系统亮度」 | 当前代码与文案中**不存在**该缺陷；它是未来实现的行为约束 |
| 「26 个徽标可能拥挤」 | 该方案是我**不推荐**的路径；拥挤只存在于假设的实现里 |
| 「未来新增全局字段忘记标注」 | 在例外式句子上不成立；在徽标方案上才成立 —— 属方案代价，不属现存缺陷 |

### 9.5 对报告 §10.1 分级表的直接纠正

报告写 **P0/P1/P2/P3 = 0/0/1/4**。我的重判：
**P2 那一项应移出 P 计数（改登记为设计风险 R-1）**；
4 个 P3 中 R-2/R-4 属"若采用徽标方案"的派生风险（在推荐方案下不成立），R-3 有效，R-5 有效；
同时报告**漏计**了 P3-A 之外真实存在的 P3-B（根页翻页速度不对称）。
⇒ 报告的问题计数**既虚高（假设性 P2）又漏计（现存 P3-B）**。

---

## 10. 测试设计审查

### 10.1 报告提出的守卫能否防回归

| 报告主张 | 我的判定 |
|---|---|
| 「从源码提取 `settings.copy(field)`，断言不在枚举的字段附近必须出现标记调用」 | ❌ **三个绕过点，实测存在**：①多参 `copy` —— `ReaderDisplaySettings.kt:30` 一次写 `brightness` + `lastFixedBrightness`，`ReaderSheetHost.kt:579` 同样；单字段正则只取到第一个。②写点在三页之外 —— 根页 `ReaderSettingsSheet.kt:221`（pageTurnSpeed）、`ReaderSheetHost.kt:556-582`、`ReaderScaffold.kt:411/613`。③若字段写点被包进局部变量/辅助函数（如 `EyeCareTimeRow(..., onChange = { onChange(settings.copy(x = it)) })` 这种间接），"字段附近"的窗口定义不稳定 |
| 「字段附近存在徽标调用的文本扫描」 | ❌ 徽标与字段之间没有语法关系可依据，相邻两行会互相误配；这正是我在 S07-F3 复核里判定为 FAIL 的那类"只查字符串是否出现"的守卫 |
| 「26 个徽标数量正确」的 Compose 断言 | ❌ 若采用推荐方案（无徽标）**应删除**；即便采用徽标方案，数量断言也只证明"个数对"，不证明"贴在对的行上" |
| 「26 项参数化遍历 `ReaderSettingsRouterTest`」 | ❌ **重复**。既有 `non overridable keys always go global regardless of scope`（:72，用 tapZoneMode + keepAwake 双作用域）与 `book scope keeps the other device level keys global as before`（:254，含"不得进枚举"的反向断言）已覆盖同一契约。再加一份 26 项遍历属过度测试 |
| 「反向断言 26 项不在 `ReaderOverrideKey`」 | ✅ 有效且**已有**（:254 已断言 tapZoneMode/keepAwake 不在枚举）。建议**扩展为全量**：断言"三页写点提取出的非枚举字段集合"与枚举不相交，而不是逐个手写 26 个名字 |

### 10.2 我建议的测试（3 条，都不循环）

**T1（JVM 结构守卫，新增 `ReaderGlobalScopeNoticeGuardTest`）—— 核心防线**

1. 从 `ui/screen/reader/sheets/` 与 `ui/screen/reader/ReaderSheetHost.kt` 提取所有
   `ReaderSettings` 字段的**写点**（**必须按括号配平解析 `copy(...)` 的完整实参表**，不能只取第一个）；
2. 用 `ReaderOverrideKey.entries.map { it.id }`（反射，非硬编码）把字段分成"可覆盖 / 全局"；
3. 断言：`全局 ∧ 属于三页/根页` 的集合 **== 测试内硬编码的 26 项权威清单**；
4. 断言：该集合与 `entries` 不相交（防止有人为让测试变绿而把 26 项塞进枚举）；
5. 断言：`ReaderSettingsSheet.kt` 里每页的例外句所点名的字段，
   **恰好等于**"该页写点 ∩ entries"（句子与事实双向对齐）。

**为什么不循环**：期望值不是生产公式的复制 —— 26 项清单是**产品决策记录**（人写的），
entries 与写点集合都来自**另一处**生产事实。新增一个全局写点 ⇒ 集合变大 ⇒ 测试红，
逼实现者要么更新清单（显式承认新增），要么改句子；
新增一个**可覆盖**字段而不更新例外句 ⇒ 第 5 条红。
两个方向都会失败，不是"跟着一起变"。

**T2（JVM 纯逻辑，扩展既有 `ReaderSettingsRouterTest`，不新建类）**
只补一条：对 26 项各取一个代表值，断言 `plan(BOOK,…).overridesToSet` 为空且 `plan.global` 携带新值
—— 这是 T1 第 4 条的**行为面**对应（T1 管"没进枚举"，T2 管"确实进全局"）。
**不重复** :72/:254 已做的双作用域断言。

**T3（Compose instrumentation，`ReaderSettingsSheetTest` 追加）**
用**已有**的 `renderSettings(width, fontScale, darkTheme)` 形态（该文件 :144 已有
`nightSettingsRemainUsableAt320DpWithLargeFont`，正是密度风险的现成载体）断言：
① BOOK 下三个子页各出现 1 条 `reader-settings-scope-notice`；
② GLOBAL 下文案是另一句；
③ 高级兼容页文案是「只改当前这本书」（钉住 R-6，防止把 D-2-A 字段标成全局）；
④ 320dp + 1.3 字号下提示行不截断。
**本轮不运行**（任务书禁止真机与 instrumentation 执行），只作为实现阶段的验收项。

### 10.3 各层证明什么

| 层 | 能证明 | 不能证明 |
|---|---|---|
| JVM 守卫（T1） | 清单不漂移、句子与事实对齐、没被塞进枚举 | 用户看得见、排版不挤 |
| JVM 逻辑（T2） | 存储语义未变 | UI 表达 |
| Compose（T3） | 提示真的渲染、条件显示下存在、小屏大字号不截断 | 真机 MIUI 字体回退、TalkBack 实际朗读 |
| 真机 | 上述全部 + 无障碍实际听感 | — |

### 10.4 过度测试（建议删除）

- 26 项参数化路由遍历（与 :72/:254 重复）；
- 「26 个徽标数量正确」（推荐方案下无徽标）；
- 「字段附近有徽标调用」的文本扫描（不可靠）；
- 对 6 个 `customTip*` 逐个断言标记存在（同一 `HeaderFooterPicker` 的重复渲染，1 条足够）。

### 10.5 testTag 粒度

**标页面，不标每个字段**：`reader-settings-scope-notice`（每页 1 个实例，随 `page` 切换）。
分组 description 若采用，复用 `MicroSettingsSection` 现有渲染，不新增 tag。
理由：字段级 tag 只在徽标方案里有意义，而徽标方案本身被否。

---

## 11. 必改与禁止修改文件

### 11.1 必改产品文件（推荐方案 4′）

| 文件 | 改动 | 为什么必须 |
|---|---|---|
| `ui/screen/reader/sheets/ReaderSettingsSheet.kt` | 在 `AnimatedContent` 之前渲染页级提示；新增 `when(page, scope)` 文案表；加 `testTag("reader-settings-scope-notice")` | `page`（:80）与 `scope`（:69）都在此文件，**唯一能同时拿到两者的位置** |

### 11.2 可选加强（同 PR，纯参数，零组件改动）

| 文件 | 改动 |
|---|---|
| `ui/screen/reader/sheets/ReaderPagingSettings.kt` | 「自动翻页与按键」组补 `description` |
| `ui/screen/reader/sheets/ReaderEyeCareSettings.kt` | 两个组补 `description` |

（`ReaderDisplaySettings.kt` **不改** —— 其全局项与本书项交错，分组说明会失真。）

### 11.3 若 Codex 仍要逐行徽标，必须追加的文件（真实边界）

`ReaderSettingsCommon.kt`、`ui/components/CompactSettingComponents.kt`、
`ui/components/SettingsComponents.kt`、`ui/components/CustomTipDialog.kt`
—— 后两个被 `ui/screen/profile/ReaderSubPage.kt` 共用。

### 11.4 必改 / 新增测试文件

| 文件 | 动作 |
|---|---|
| `test/.../ui/screen/reader/sheets/ReaderGlobalScopeNoticeGuardTest.kt` | **新增**（T1） |
| `test/.../data/settings/ReaderSettingsRouterTest.kt` | **追加 1 条**（T2），不新建类 |
| `androidTest/.../ui/screen/reader/sheets/ReaderSettingsSheetTest.kt` | **追加 4 条**（T3），本轮不运行 |

### 11.5 明确不应修改

| 文件 | 理由 |
|---|---|
| `ReaderSettingsRouter.kt` / `ReaderSettingsEditApplier.kt` | 路由与落盘语义**当前正确**，改它就是把透明度问题误当成数据问题 |
| `ReaderOverride.kt`（含 `ReaderOverrideKey` / `ReaderSettingsOverlay`） | 把 26 项加进枚举会改变数据语义，任务书与我一致反对 |
| `ReadingPresets.kt` / `PerBookSettingsStore.kt` / `SettingsStore.kt` | D-2-A 已闭合；`effective*()` 是既有回落，不属于本片 |
| `ReaderPagerEngineState.kt` | 引擎运行时消费方，与 UI 提示无关 |
| `ReaderScreen.kt` / `ReaderScaffold.kt` / `ReaderSheetHost.kt` | `scope` 已在 `SettingsSheet` 调用点可用，无需上溯 |
| `ReaderThemeSheet.kt` | 先例参照物；若要补它漏掉的 dayNight 纸张联动说明，属另一片 |
| `ui/components/*`（三个共用组件） | 推荐方案不需要；一旦需要即触发 §12 |
| `docs/handoff/current.md`、看板、Obsidian | 边界外 |

### 11.6 文件所有权建议（避免与其他 Agent 冲突）

`ReaderSettingsSheet.kt` 是书内设置面板主文件，S07 链路的多个 Agent 曾改过相邻文件
（`ReaderSheetHost.kt`、`ReaderScreen.kt` 在工作区已有大量未提交改动）。
⇒ 本片必须由**单一实现 Agent** 独占 `ReaderSettingsSheet.kt`
（+ 可选的两个页面文件），且**不得**与 S07-F*/A01 的在途改动并行编辑同一文件；
若发生冲突，按 AGENTS.md 交集成者串行处理。

---

## 12. SEAM REQUEST

**推荐方案 4′：无 SEAM REQUEST**（1 个必改文件 + 2 个可选文件，全在书内 sheets 目录内）。

**若 Codex 采纳逐行徽标（报告原 A′）：必须提出 SEAM REQUEST，共 4 项**

| # | 需要授权的文件 | 原因 | 影响面 |
|---|---|---|---|
| S-1 | `ui/screen/reader/sheets/ReaderSettingsCommon.kt` | 4 个 `Micro*Row` 包装器要透传新参数；徽标本体应在此定义一次（避免三份私有副本） | 6 个书内 sheets 消费 |
| S-2 | `ui/components/CompactSettingComponents.kt` | `CompactOptionPillRow` / `CompactSliderRow` / `CompactSwitchRow` / `CompactLinkRow` 无 trailing 插槽；其中 **Slider 与 Link 连 subtitle 都没有** | `ReaderSettingsCommon.kt` + `ReaderThemeSheet.kt` |
| S-3 | `ui/components/SettingsComponents.kt` | `SettingBrightnessRow` 内部两行无外层标题，徽标无处可挂 | **含 profile 页 `ReaderSubPage.kt`** |
| S-4 | `ui/components/CustomTipDialog.kt` | `HeaderFooterPicker` 的 `Row` 只有 label + value chip 两段 | **含 profile 页，18 个 callsite 中的多数** |

⇒ 报告 §10.3「本轮无 SEAM REQUEST，条件性三项均非必需」的**技术前提不成立**：
它假设徽标可以只靠页内私有 composable 完成，而 §4.1 证明控件内部没有位置。
「三项非必需」只在**放弃逐行徽标**时才成立 —— 而那正是本报告的建议。

---

## 13. 未确认项

| # | 项 | 需要什么 |
|---|---|---|
| U1 | 例外式句子在 320dp + fontScale 1.3 下是否换行、换行后是否遮挡首个分组标题 | Compose instrumentation（T3-④）或真机；本轮禁止 |
| U2 | 「除…外」句式对低文化水平用户的理解度 | 产品/用研判断，不是我能静态证明的 |
| U3 | 根页「翻页速度」行是否也需要提示（P3-B） | 产品裁决。我倾向**不加**（根页有作用域胶囊），但这是取舍不是事实 |
| U4 | `customTip*` 与 `headerLeft` 的关系是否应整理（当前 `headerLeft` 无 UI 写点，本书页眉实际不可按书设置） | 属**另一片**（可能是 D-2-C 或设置模型清理），本片不应顺带处理 |
| U5 | 亮度手势 / 自动翻页速度写点绕过 router 是否需要收口 | 属数据链路问题，与 UI 透明度无关，建议单列 |
| U6 | ThemeSheet 提示漏掉 dayNight 纸张联动说明是否补 | 一行文案，但会改 `ReaderThemeSheet.kt`，需 Codex 决定是否并入本片 |
| U7 | 「全局（所有书共用）」的括号释义是否每页各一次 | 产品裁决；我按"每页一次"给文案 |
| U8 | 报告 §8.3「子页面 `scope` 不给默认值」的建议 | 推荐方案下**子页不需要 scope 参数**，该建议自然失效；若走徽标方案则适用 |

**我没有确认、也不声称确认的**：任何真机渲染效果、任何 Compose 运行期行为、
`ReaderSettingsSheetTest` 追加用例的可运行性（本轮未跑 instrumentation）。

---

## 14. 是否建议进入实现

**建议：可以进入实现，但按方案 4′ 实现，不按报告原 A′ 的逐行徽标实现。**

前置条件（三条，缺一即返工）：

1. **Codex 裁决用词**：沿用「全局」+ 一次性括号释义（§5.1），还是接受报告的双术语方案。
   这直接决定 6 条文案与 T3 断言，不能留给实现者临场决定；
2. **确认放弃逐行徽标**，或明确接受 §12 的 4 项 SEAM 与 8 文件边界；
3. **确认 P 计数按 §9 修正**（假设性亮度措辞不计 P2；补计根页翻页速度 P3-B；
   P3-1 的引用从 D-2-B 账上撤下）。

实现顺序：T1 守卫先写（红）→ 加页级提示（绿）→ T2 补 1 条 → T3 追加 Compose 用例（不在本轮运行）→
真机最小 3 步（进三页看提示 / 改一个全局项后确认根页「覆盖 N 项」不增加 / 换一本书确认该项确实共用）。

**不建议**在裁决前进入实现：报告把"产品需先定 U2/U3/U4"列为前置，但那三项（护眼是否省略徽标、
二级对话框是否加提示、用词）在推荐方案下**只剩用词一项真正阻塞**，范围比报告自己估计的更小。

---

## 15. 回传摘要

| 项 | 内容 |
|---|---|
| **结论** | **PARTIAL**（诊断正确、方向可用；文件边界、先例引用、测试设计、P 分级需实质修订） |
| 26 项清单 | ✅ **正确**（7/9/10 与源码逐项吻合，均不在 `ReaderOverrideKey`）。但"26 项 = 26 个标注位置"不成立：书内实为 **27 个编辑点 + 3 个绕过 router 的写点**；`customTip*` 显示值来自 `effective*()` 回落 |
| 四文件边界 | ❌ **不成立**（对逐行徽标）。6 类行控件**无一有插槽**，真实边界 **8 文件**，其中 `SettingsComponents.kt` / `CustomTipDialog.kt` 被 profile 页共用。页级提示部分**只需 1 个文件** |
| ThemeSheet 先例 | **只成立到"页级说明有先例"**；不能证明逐行徽标有先例（ThemeSheet 内部零行内徽标）。且其文案自身漏了 dayNight 纸张联动，不宜照抄 |
| 最终推荐 | **方案 4′**：`ReaderSettingsSheet.kt` 渲染**例外式页级规则句**（1 文件）+ 纯全局分组补现成 `description`（可选 +2 文件）；**不做** 26 个徽标 |
| 必改产品文件 | `ui/screen/reader/sheets/ReaderSettingsSheet.kt` |
| 必改/新增测试 | 新增 `ReaderGlobalScopeNoticeGuardTest`（T1）；`ReaderSettingsRouterTest` 追加 1 条（T2）；`ReaderSettingsSheetTest` 追加 4 条（T3，本轮不运行） |
| 当前问题 | **P0 0 / P1 0 / P2 0 / P3 2** |
| 设计风险 | **6 项**（R-1…R-6，**不计入** P 数量） |
| SEAM REQUEST | 推荐方案下**无**；若坚持逐行徽标则 **4 项**（S-1…S-4），报告原「无 SEAM」不成立 |
| 未确认项 | 8 项（U1–U8），其中仅 U3/U4/U5 属"本片外或另一片"，真正阻塞实现的只有**用词裁决** |
| 是否建议进入实现 | **是**，按方案 4′；前置：用词裁决 + 放弃徽标 + P 计数修正 |
| 报告路径 | `docs/coordination/reviews/undated/agent-review-d02b-design-qoder.md` |

### 边界遵守

未修改任何产品源码 / 测试源码 / Gradle / Manifest / Room / DataStore；未修改 Zcode 报告；
未构建、未安装 APK、**未操作任何手机、未使用 MuMu**；未运行 instrumentation；
未 commit / push / stage / stash / reset / checkout / clean；未全仓格式化；
未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`；
未处理 A01 / S07 / S08–S20；未重新处理 D-2-A；未顺手修复任何发现的问题。
工作区既有未提交改动全部原样保留。

**未领取下一任务。**
