# D-2 只读根因调查与方案设计 — Zcode 报告

> 执行 Agent：Zcode　|　实际模型：`deepseek v4.1Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 调查期间 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 性质：**只读调查 + 方案设计，未实施任何产品修复**
> 报告路径：`docs/coordination/investigations/agent-investigation-d02-zcode.md`
> 状态：**等待 Codex 复核与裁决**

标注约定：**[事实]** = 我读到的源码或跑过的命令；**[推断]** = 静态推导，未运行验证；
**[未验证]** = 需真机 / 运行期确认。

---

## 1. 执行摘要

**根因确认：成立，且影响面比 Codex 初步判断更大。**

用户现象「作用域选『本书』时改 EPUB 分页引擎模式，实际写全局」的机制已逐跳确认：

1. `pagerEngineMode` / `epubPagerEngineMode` **不在** `ReaderOverrideKey` 枚举里 →
   它们不是「可覆盖项」；
2. `ReaderSettingsRouter.plan` 对**不可覆盖项**无论 `BOOK` 还是 `GLOBAL` 作用域都写进
   `plan.global` → 于是「只改本书」的编辑落进了全局；
3. 设置面板的 5 个分页**没有任何一个**接收 `scope` 参数 → 面板本身无法对不可覆盖项
   做任何差异化展示或提示；
4. `ReaderPagerEngineState` 从**有效设置**读这两个字段 → 因为第 2 步把值写进了全局，
   所以有效设置里也变了，当前书立刻生效、其它书随后同样生效。

**超出 Codex 初步判断的三点（本轮新增发现）**：

- **不止 2 个字段**：在「本书」作用域下**可编辑但实际只能写全局**的字段共 **28 个**
  （含护眼 10 个、显示 9 个、翻页 7 个、引擎 2 个）。分页引擎只是其中最容易被用户察觉的
  两个（因为它们**直接改变排版路径**，视觉差异明显）。
- **Markdown 也受影响**：`pagerEngineMode` 的注释写「仅影响 TXT / Markdown」，
  `ReaderPagerEngineState` 对 `epubBook == null` 一律读 `pagerEngineMode`，
  因此 Markdown 与 TXT 共用同一个开关。改一次会同时影响 TXT 与 Markdown。
- **现有测试把缺陷当契约锁住了**：`ReaderSettingsRouterTest` 的
  `non overridable keys always go global regardless of scope` 用例**显式断言**
  「`BOOK` 作用域下不可覆盖项必须进全局」，并已通过。这是缺陷能长期存活的直接原因。

**推荐方案：B（明确保持全局设置，UI 明示 + 分组）**，理由与最小实现边界见 §9–§10。

---

## 2. 根因结论

### 2.1 一句话根因

**作用域只对「可覆盖项」生效；`pagerEngineMode` / `epubPagerEngineMode` 不是可覆盖项，
因此无论选「本书」还是「全局」，改动都写进全局 —— 而设置面板在「本书」作用域下
照常展示并允许编辑它们，且没有任何提示说明它们其实是全局设置。**

### 2.2 三层机制（逐层有代码证据）

| 层 | 机制 | 证据 |
|---|---|---|
| L1 数据模型 | 两个引擎字段不在 `ReaderOverrideKey` 枚举 → 结构上不可能有本书覆盖 | `ReaderOverride.kt:14-35`（枚举 20 项，无 `pagerEngineMode` / `epubPagerEngineMode`） |
| L2 路由 | 不可覆盖项无条件进 `plan.global` | `ReaderSettingsRouter.kt:77-81`：`mustStayGlobalBacked` 对 `BOOK` 取**整个** `OVERRIDABLE`，于是 `mask(base=edited, maskFrom=global, keys=OVERRIDABLE)` 只把可覆盖项还原成全局值，**其余字段（含两个引擎字段）保留 `edited` 的值** → 写进全局 |
| L3 UI | 5 个分页都不接收 `scope`，无法差异化 | `ReaderSettingsSheet.kt:117-121` 只传 `settings` 与 `onSettingsChange`；`ReaderAdvancedSettings.kt:11-13` 签名无 `scope` |

### 2.3 关键代码（L2 的机制核心）

`ReaderSettingsRouter.kt:75-87` [事实]：

```kotlin
val touched = changedOverridableKeys(displayed, edited)      // 只看可覆盖项
val mustStayGlobalBacked = when (scope) {
    ReaderSettingsScope.BOOK -> OVERRIDABLE                  // BOOK：全部可覆盖项不进全局
    ReaderSettingsScope.GLOBAL -> OVERRIDABLE - touched
}
val nextGlobal = ReaderOverrideKey.mask(base = edited, maskFrom = global, keys = mustStayGlobalBacked)
//                                ^^^^^^ base 是 edited（含用户对引擎字段的改动）
```

`mask` 只还原 `keys` 指定的字段（`ReaderOverride.kt:109-110`），
**两个引擎字段不在 keys 里 → 它们在 `nextGlobal` 里保持 `edited` 的值**。
`ReaderSettingsEditApplier.kt:34-36` 随后在 `plan.globalDiffersFrom(global)` 为真时调用
`settingsVm.updateReader { plan.global }` → 落盘全局。

---

## 3. 完整读写调用链

### 3.1 写链（用户改 EPUB 引擎模式）

```
① UI 作用域选择
   ReaderSettingsCommon.kt:391 SettingsScopeSection(scope, onScopeChange)
     → ReaderSettingsSheet.kt:151-153 item("settings-scope")
     → ReaderSettingsScope.BOOK | GLOBAL（label：ReaderSettingsRouter.kt:12-16「本书」/「全局」）
   默认值：ReaderScaffold.kt:153  remember(bid) { mutableStateOf(BOOK) }
   宿主默认：ReaderSheetHost.kt:124  settingsScope: ReaderSettingsScope = BOOK

② 进入高级设置页
   ReaderSettingsSheet.kt:121  ReaderSettingsPage.ADVANCED -> AdvancedSettings(settings, onSettingsChange)
   ★ 注意：AdvancedSettings 只收到 settings 与 onSettingsChange，**没有 scope**

③ 用户点「EPUB 分页兼容模式」
   ReaderAdvancedSettings.kt:31-38  MicroOptionPillRow(
       selected = settings.epubPagerEngineMode,
       onSelect = { onChange(settings.copy(epubPagerEngineMode = it)) })   ← :36

④ 回调上抛
   ReaderSheetHost.kt:528  onSettingsChange = stableOnSettingsChange
   ReaderSheetHost.kt:258-269  applyReaderEdit = { updated -> applyReaderEdit(...) }
       scope = state.ui.settingsScope, global = state.ui.globalReaderSettings,
       displayed = state.ui.readerSettings, edited = updated

⑤ 路由计算（★ 缺陷发生点）
   ReaderSettingsEditApplier.kt:28-33  ReaderSettingsRouter.plan(scope, global, displayed, edited)
   ReaderSettingsRouter.kt:75-81  epubPagerEngineMode ∉ OVERRIDABLE
       → 不参与 mustStayGlobalBacked 的还原 → 保留 edited 值进入 nextGlobal

⑥ 落盘
   ReaderSettingsEditApplier.kt:34-36  if (plan.globalDiffersFrom(global)) settingsVm.updateReader { plan.global }
   SettingsViewModel.kt:138-140  updateReader → settings.updateReader(block)
   SettingsStore.kt:608-609  prefs[KEY_PAGER_ENGINE] = next.pagerEngineMode
                             prefs[KEY_EPUB_PAGER_ENGINE] = next.epubPagerEngineMode
   ★ 写的是**全局键**（app_settings），不是 book_reader_override_* 键

⑦ 读回（全局）
   SettingsStore.kt:454-455  pagerEngineMode = prefs[KEY_PAGER_ENGINE] ?: "on"
                             epubPagerEngineMode = prefs[KEY_EPUB_PAGER_ENGINE] ?: "auto"
```

**关键否定证据** [事实]：`PerBookSettingsStore.kt:47-51` 的 `setOverride` 只接受
`ReaderOverrideKey` 类型入参，而该枚举不含两个引擎字段 → **结构上无法为它们写本书覆盖**。
全仓 `grep book_reader_override` 也只命中 `PerBookSettingsStore.kt:106` 一处 key 构造，
其 `${key.id}` 来自枚举 → 不会出现 `book_reader_override_<bookId>_epubPagerEngineMode` 键。
这与真机证据（DataStore 只有全局 `epub_pager_engine_mode = off`、无任何
`book_reader_override_*_epubPagerEngineMode`）完全一致。

### 3.2 读链（阅读器消费）

```
ReaderScreen.kt:173-175
  globalReaderSettings ← settingsVm.reader            （全局）
  bookReaderOverrides  ← settingsVm.overrides(bookId) （本书覆盖）
  readerSettings       ← settingsVm.effectiveReader(bookId)
                          = ReaderSettingsOverlay.apply(global, overrides)

ReaderScreen.kt:626  settingsScope = settingsScope → ReaderScaffold → ReaderContentHost
ReaderPagerEngineState.kt:151-158
  val configuredPagerMode = if (epubBook != null) readerSettings.epubPagerEngineMode
                            else                    readerSettings.pagerEngineMode
  val pagerEngineOn = readerSettings.readerMode == "paged" && when (configuredPagerMode) {
      "on" -> true; "auto" -> !pagerHealth.isAutoDisabled; else -> false }

ReaderContentHost.kt:174-190
  pagerEngineOn && pagedSource != null → ReaderContentHostPagedBranch（新引擎）
  else epubBook != null                → ReaderContentHostEpubBranch
  else markdownDocument != null        → ReaderContentHostMarkdownBranch
  else                                 → ReaderContentHostPlainTextBranch
```

**为什么「本书」作用域下改动能生效** [事实 + 推断]：因为改动被写进了**全局**，
而 `readerSettings = overlay(global, overrides)`，全局变了 → 有效设置也变了 →
当前书立即生效。这一步不是「本书覆盖生效」，而是「全局被改了」。

---

## 4. 各设置项的作用域矩阵

### 4.1 20 个可覆盖项（`ReaderOverrideKey.entries`，`ReaderOverride.kt:15-34`）

| 项 | id | 在设置页可编辑？ |
|---|---|---|
| 字号 / 行距 / 段间距 / 页边距 | `fontSize` / `lineHeight` / `paragraphSpacing` / `pageMargin` | ✅ 排版页 |
| 纸张 / 粗体 / 中文排版 / 繁体 / 自定义字体 | `background` / `fontWeightBold` / `chineseTypography` / `traditionalChinese` / `customFontPath` | ✅ 排版页 / 高级页 / 显示页 |
| **阅读模式** | `readerMode` | ✅ 翻页页 |
| 翻页效果 / 沉浸 / 页眉页脚 / 进度条 / 菜单隐藏 / 屏幕方向 | `pageTurnEffect` / `immersiveMode` / `showReaderInfo` / `showProgressBar` / `autoHideSeconds` / `screenOrientation` | ✅ 翻页页 / 显示页 |

→ 这 20 项在「本书」作用域下**确实**只写本书覆盖（`ReaderSettingsRouter.kt:83-87`）。

### 4.2 28 个「在本书作用域下仍可编辑，但实际只能写全局」的字段

由「设置页 `settings.copy(<field>)` 出现的字段」减去「可覆盖项」得出。
**这是问题 5 的完整答案，共 28 项，不是 2 项。**

| 设置页 | 字段 | 数量 | 行号来源 |
|---|---|---:|---|
| `ReaderAdvancedSettings.kt` | `pagerEngineMode`、`epubPagerEngineMode` | **2** | :27, :36 |
| `ReaderPagingSettings.kt` | `autoPageSpeed`、`pageTurnSpeed`、`tapActionPreset`、`tapZoneMode`、`volumeKeyInvert`、`volumeKeyPaging`、`volumeKeyPagingDuringTts` | **7** | 该文件 `settings.copy(...)` |
| `ReaderDisplaySettings.kt` | `brightness`、`keepAwake`、`batteryTipStyle`、`customTipHeaderLeft`、`customTipHeaderRight`、`customTipHeaderCenter`、`customTipFooterLeft`、`customTipFooterRight`、`customTipFooterCenter` | **9** | 同上 |
| `ReaderEyeCareSettings.kt` | `eyeCareFilterEnabled`、`eyeCareTemperature`、`eyeCareIntensity`、`eyeCareStartMinute`、`eyeCareEndMinute`、`eyeCareScheduleEnabled`、`eyeCareOledBlackCompat`、`eyeCareReminderMinutes`、`readingRhythmReminderEnabled`、`readingRhythmReminderMinutes` | **10** | 同上 |
| `ReaderTypographySettings.kt` | —（6 个字段全部可覆盖） | 0 | — |
| **合计** | | **28** | |

**已确认事实**：这 28 项**全部**走同一个 `onSettingsChange` → `applyReaderEdit` →
`ReaderSettingsRouter.plan`，因此**全部**具有「选本书也写全局」的行为。
`ReaderSettingsRouterTest` 的 `non overridable keys always go global regardless of scope`
用例只挑了 `tapZoneMode` 与 `keepAwake` 两个代表来断言这一行为 [事实]。

**[推断]** 为什么只有分页引擎被真机验收发现：这 28 项里绝大多数是**设备级偏好**
（亮度、音量键、护眼、TTS、屏幕常亮），用户心理上本就预期「跟设备走」，
跨书一致不觉得异常；而分页引擎**直接改变正文排版路径**（分页 ↔ 滚动视图），
用户改完打开另一本书会看到明显的排版差异，因此最先被察觉。

---

## 5. TXT / Markdown / EPUB 影响矩阵

| 格式 | 读哪个字段 | 代码位置 | 改一次「TXT 分页兼容模式」的影响 | 改一次「EPUB 分页兼容模式」的影响 |
|---|---|---|---|---|
| **TXT** | `pagerEngineMode` | `ReaderPagerEngineState.kt:154`（`epubBook == null` 分支） | **直接受影响** | 不受影响 |
| **Markdown** | `pagerEngineMode` | 同上（Markdown 的 `epubBook` 为 null） | **直接受影响** | 不受影响 |
| **EPUB** | `epubPagerEngineMode` | `ReaderPagerEngineState.kt:152`（`epubBook != null` 分支） | 不受影响 | **直接受影响** |

**关键发现** [事实]：`SettingsStore.kt:197` 的字段注释写
「**仅影响 TXT / Markdown**；EPUB 由 epubPagerEngineMode 单独控制」，
而 `ReaderPagerEngineState.kt:151-155` 的判据是 `epubBook != null` —— 两者一致。
因此：

- **TXT 与 Markdown 共用 `pagerEngineMode` 一个开关**：改「TXT 分页兼容模式」
  会**同时**改变所有 TXT 书与所有 Markdown 书的渲染路径；
- 由于改动写的是全局键，影响范围是**全部书籍**，不只当前这本。

**运行时分支** [事实]（`ReaderContentHost.kt:174-190`）：
`pagerEngineOn && pagedSource != null` → 新分页引擎分支；否则按 EPUB / Markdown / 纯文本
各自分支。因此引擎开关的可见后果是「正文从滚动视图切到分页视图（或反之）」。

**`auto` 的额外耦合** [事实]：`ReaderPagerEngineState.kt:156-158` 里
`"auto" -> !pagerHealth.isAutoDisabled`，而 `PagerHealthStore.kt:25-27` 用的是
**进程级** SharedPreferences 崩溃计数（`KEY_CRASH_COUNT >= 2` 即自动关闭）。
这一项是**设备级健康闸门**，与书无关 —— 若把引擎开关做成 per-book，
`auto` 语义会出现「本书 auto 但全局健康闸门已关闭」的组合，需要在方案 A 中明确。

---

## 6. 现有测试覆盖与缺口

### 6.1 已运行的测试（本轮实跑，仅只读用途）

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.data.settings.ReaderSettingsRouterTest" \
 --tests "com.creationreadingassistant.data.settings.ReaderSettingsOverlayTest" \
 --tests "com.creationreadingassistant.data.settings.PerBookSettingsStoreTest" \
 --tests "com.creationreadingassistant.data.settings.ReadingPresetsTest" \
 --tests "com.creationreadingassistant.data.settings.ReaderDefaultsMigrationTest"
```

退出码 **0**，`BUILD SUCCESSFUL in 45s`。

| 测试类 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `ReaderSettingsRouterTest` | 11 | 0 | 0 | 0 |
| `ReaderSettingsOverlayTest` | 16 | 0 | 0 | 0 |
| `PerBookSettingsStoreTest` | 11 | 0 | 0 | 0 |
| `ReadingPresetsTest` | 10 | 0 | 0 | 0 |
| `ReaderDefaultsMigrationTest` | 10 | 0 | 0 | 0 |
| **合计（5 类）** | **58** | **0** | **0** | **0** |

### 6.2 为什么这些测试没能阻止问题

**根本原因：测试断言的是「不可覆盖项必须进全局」这一实现事实，而不是
「用户以为只改本书时不应改动全局」这一产品契约。**

`ReaderSettingsRouterTest.kt:72-82` [事实]：

```kotlin
@Test
fun `non overridable keys always go global regardless of scope`() {
    listOf(ReaderSettingsScope.BOOK, ReaderSettingsScope.GLOBAL).forEach { scope ->
        val edited = displayed.copy(tapZoneMode = "five-zone", keepAwake = true)
        val plan = ReaderSettingsRouter.plan(scope, global, displayed, edited)
        assertEquals("$scope 下点击区域必须进全局", "five-zone", plan.global.tapZoneMode)
        assertEquals("$scope 下屏幕常亮必须进全局", true, plan.global.keepAwake)
    }
}
```

这条用例**把 BOOK 作用域下的泄漏写成了期望值**，并且注释还强调「必须进全局」。
它证明的是路由实现符合设计文档（`ReaderSettingsRouter.kt:44` 的规则 1），
但它**没有**回答「用户在 BOOK 作用域下改这些项，是否会被误导」——
两者是两个不同的问题，测试只覆盖了前者。

### 6.3 缺失的最小测试契约

| # | 缺失契约 | 为什么需要 |
|---:|---|---|
| C1 | **枚举一致性守卫**：`ReaderOverrideKey` 覆盖的字段集合，必须与「设置页声明为本书可编辑」的字段集合一致 | 当前 28 个字段可编辑却不可覆盖，没有任何测试会发现这种漂移；新增设置项时会持续漂移 |
| C2 | **不可覆盖项的 UI 归属**：在 BOOK 作用域下，不可覆盖项必须被标记为「全局」（隐藏 / 禁用 / 分组），并有可断言的信号（如 scope 提示文案或 testTag） | 防止「面板看起来能改、实际改全局」再次发生 |
| C3 | **作用域提示存在性**：BOOK 作用域且页面含不可覆盖项时，必须渲染「以下设置对所有书生效」类提示 | 这是方案 B 的核心用户可见契约 |
| C4 | **引擎字段的作用域行为**：明确断言 `pagerEngineMode` / `epubPagerEngineMode` 在 BOOK 作用域下的**预期**行为（方案 B 下：UI 不可编辑或标注全局；方案 A 下：只写本书覆盖） | 当前这两个字段完全无测试覆盖（全仓仅 `ReaderViewportProfileTest` 提到 `pagerEngineMode`，且只验证视口适配不改变它） |
| C5 | **`auto` 与健康闸门的交互**：若采用方案 A，需断言 per-book `auto` 与进程级 `isAutoDisabled` 的组合语义 | 防止方案 A 引入语义歧义 |

**[事实]** 全仓 `grep pagerEngineMode` 在 `android/app/src/test` 下仅 2 处命中，
均在 `ReaderViewportProfileTest.kt:65,73`，且断言的是「视口适配不改变该字段」，
与作用域无关 → **两个引擎字段目前没有任何作用域测试覆盖**。

---

## 7. 方案 A 评估（支持真正的 per-book override）

### 7.1 需要的改动面

| # | 改动点 | 文件 | 说明 |
|---:|---|---|---|
| A1 | 枚举新增 2 项 | `ReaderOverride.kt:14-35` | `PAGER_ENGINE("pagerEngineMode", …)`、`EPUB_PAGER_ENGINE("epubPagerEngineMode", …)` |
| A2 | `read` / `write` 分支 | `ReaderOverride.kt:38-86` | 新增两个 `when` 分支 + 合法值集合 `setOf("off","auto","on")` |
| A3 | overlay | `ReaderOverride.kt:125-131` | 无需改（按枚举遍历，自动包含新项） |
| A4 | router | `ReaderSettingsRouter.kt:55,77-81` | 无需改（`OVERRIDABLE` 取自枚举） |
| A5 | preset | `ReadingPresets.kt` | 无需改；但需决定预设是否包含引擎项（建议不包含：预设是「排版观感」，引擎是「兼容性开关」） |
| A6 | 恢复默认 | `PerBookSettingsStore.kt:92-94` + `ReaderSettingsCommon.kt:492` | `resetGlobalReaderToDefaults` 走 `ReadingPresets.DEFAULT` → `resetOverridableKeys` 遍历枚举 → **会自动把全局引擎模式重置为结构默认（`on`/`auto`）**。这是行为变更，需确认是否可接受；同时对话框文案里的 `${ReaderOverrideKey.entries.size}` 会从 20 变 22，文案需复核 |
| A7 | DataStore key | `PerBookSettingsStore.kt:105-106` | 无需改（键由 `<bookId>_<key.id>` 生成，自动得到 `book_reader_override_<id>_pagerEngineMode`） |
| A8 | 运行时消费 | `ReaderPagerEngineState.kt:151-158` | 无需改（已读有效设置） |
| A9 | 旧数据兼容 | — | 见 §7.2 |
| A10 | UI 标注 | `ReaderSettingsCommon.kt` / `ReaderAdvancedSettings.kt` | 可复用既有 `overrides.containsKey(...)` 机制标注「本书」（如 `ReaderSettingsSheet.kt:190` 的行距 subtitle 写法） |
| A11 | 测试 | `ReaderSettingsRouterTest`、`ReaderSettingsOverlayTest`、`PerBookSettingsStoreTest`、新增 A01 式守卫 | 需**改写** §6.2 那条把缺陷当契约的用例（它会变成「引擎项现在是可覆盖项」） |

### 7.2 是否需要数据迁移

**结论：不需要数据迁移，但存在一个「用户预期落差」需要产品确认。** [推断]

- **不需要 schema 迁移**：本书覆盖存的是 DataStore Preferences 键值对
  （`PerBookSettingsStore.kt:14-26` 已说明「键存在 = 该项被本书显式覆盖」，
  无版本号、无表结构），新增 key 只是新增一种可能的键名 → 老数据读不到新键，
  自动回退全局，行为与今天完全一致（`ReaderSettingsOverlay.apply` 不因缺项写默认值）。
- **没有既有 per-book 引擎数据要迁移**：因为今天结构上写不出这种键（§3.1 否定证据）。
- **但有一个语义变化**：今天用户在 BOOK 作用域下改引擎 → 全局被改（所有书生效）；
  改成方案 A 后，同样的操作 → **只有当前书生效**。对已经这样改过设置的老用户，
  其全局值仍是当时写入的那个值，行为不会突变（全局值不变），只是**今后**的改动
  作用范围变小。这一点建议在报告/发版说明里写明。

### 7.3 复杂度、回归风险、用户收益

| 维度 | 评估 |
|---|---|
| **复杂度** | **低**（约 10 行产品代码）：枚举 +2 项、`read`/`write` +2 分支、合法值集合 +1。router / overlay / store / 运行时**全部零改动**，因为它们都以枚举为准 |
| **回归风险** | **中**。① `resetGlobalReaderToDefaults` 会开始重置引擎模式（A6），这是行为变更；② 既有 `non overridable keys always go global regardless of scope` 用例会失败（需要改写，属预期）；③ 若某本书覆盖了 `pagerEngineMode = off`，而用户之后在「我的 → 阅读设置」改全局引擎，该书的覆盖会**遮住**全局改动 —— 用户可能再次报「改了没反应」（与 R3-P1 既有设计一致，但引擎项比排版项更容易被当成故障） |
| **用户收益** | **高且直接**：真正实现「只改本书」，与面板顶部「只改本书」的承诺一致；对「某本书分页异常要单独回退」这一真实场景（`ReaderAdvancedSettings.kt:20` 的文案就是这个场景）**只有方案 A 能满足** |
| **语义歧义** | `auto` 依赖**进程级** `PagerHealthStore.isAutoDisabled`（§5）。per-book 覆盖 `auto` 后，健康闸门仍是设备级 → 需要在实现时明确「闸门关闭时，per-book `auto` 也视为关闭」（当前 `ReaderPagerEngineState.kt:156-158` 天然如此，无需改，但需测试钉住） |

### 7.4 方案 A 的关键判断

**方案 A 在结构上非常划算**：因为 R3-P1 的设计把「可覆盖项」收敛成了**一个枚举**，
router / overlay / store / 运行时全部以枚举为准，所以扩展一个字段的成本极低。
真正的成本不在代码，而在**产品语义**（预设是否含引擎项、恢复默认是否重置引擎、
覆盖遮住全局改动的提示）。

---

## 8. 方案 B 评估（明确保持全局设置）

### 8.1 需要的改动面

| # | 改动点 | 文件 | 说明 |
|---:|---|---|---|
| B1 | 把 `scope` 传进需要区分的设置页 | `ReaderSettingsSheet.kt:117-121` | 至少 `ADVANCED`；若要求彻底，`PAGING` / `DISPLAY` / `EYE_CARE` 也需要（共 28 字段） |
| B2 | 不可覆盖项在 BOOK 作用域下明示归属 | `ReaderAdvancedSettings.kt` 等 | 加分组标题或行内提示 |
| B3 | 页面级提示 | 同上 | 「以下设置对所有书生效」 |
| B4 | 测试 | 新增守卫 | C2/C3 契约 |

### 8.2 三种做法的比较

| 做法 | 优点 | 缺点 | 判断 |
|---|---|---|---|
| **隐藏** | 用户完全不会误操作 | ① 用户在「本书」作用域下**无法调整**引擎兼容性 —— 而 `ReaderAdvancedSettings.kt:20` 的文案明确说这是「特定书籍分页异常时可临时切回」的**救急入口**，隐藏等于砍掉这个能力；② 面板布局随作用域跳变，用户切换作用域时选项忽然消失，容易误以为功能没了；③ 可访问性差（TalkBack 用户无法感知「被隐藏了」） | **不推荐** |
| **禁用（置灰）** | 保留可见性，暗示「当前不可改」 | ① 用户看到自己需要的开关却点不动，且**不知道该怎么办**（除非给额外说明）；② 引擎兼容是救急功能，禁用会挡住真实故障排查；③ 需额外解释「为什么禁用」 | **不推荐** |
| **分组显示 + 明确提示** | ① 保留全部能力（用户仍可改，只是知道改的是全局）；② 不隐藏、不禁用，无布局跳变；③ 提示可精确说明作用范围；④ 与既有 UI 结构（`MicroSettingsSection` 分组）天然契合 | ① 需要新增分组与提示文案（工作量略大）；② 提示文案需要产品确认措辞 | **推荐** |

### 8.3 复杂度、回归风险、用户收益

| 维度 | 评估 |
|---|---|
| **复杂度** | **中**。若要覆盖全部 28 个字段，需要改 4 个设置页 + 传参链；若只覆盖引擎 2 项（最小），改动集中在 `ADVANCED` 页 |
| **回归风险** | **低**（不改数据模型、不改路由、不改 DataStore）。风险主要是 UI 布局变化与文案，需要视觉验收 |
| **用户收益** | **中**：消除误导，但**不满足**「某本书单独回退引擎」这一真实需求；用户仍无法为单本书关闭新引擎 |
| **可访问性** | 分组 + 提示对 TalkBack 友好（有文本可读）；比隐藏/禁用都更易理解 |
| **测试** | 结构守卫（页面在 BOOK 作用域下必须渲染提示）+ 行为测试（`scope` 传参链） |

---

## 9. 推荐方案及理由

### 推荐：**方案 B**（分组显示 + 明确提示），但**建议分两步走**

**理由：**

1. **本任务的定位是「修复误导」，不是「扩展能力」。** 用户的抱怨是「我以为只改本书，
   结果影响了其他书」—— 这是**信息缺失**问题，不是**功能缺失**问题。方案 B 用最小
   语义变更直接消除误导，不触碰数据模型、路由、DataStore、运行时消费。

2. **方案 A 虽然代码成本低（约 10 行），但它改变的是产品语义，风险不对称。**
   它会引入「本书覆盖遮住全局改动」的新困惑场景（§7.3 风险③），
   以及「恢复默认是否重置引擎」「预设是否含引擎」两个需要产品决策的问题。
   这些决策不该由一次缺陷修复顺带做掉。

3. **但方案 A 才是用户真实需求的正解。** `ReaderAdvancedSettings.kt:20` 的文案
   明确把引擎兼容定位为「**特定书籍**分页异常时临时切回」—— 这是**天然的 per-book
   场景**。方案 B 只能诚实告知「这里是全局」，不能真正满足该场景。
   因此建议 **B 先行（立即消除误导），A 作为后续切片由 Codex 排期**。

4. **两步走的具体建议**：
   - **本轮（B）**：把 `ADVANCED` 页在 BOOK 作用域下对两个引擎项做**分组 + 明确提示**，
     并把 `scope` 传进该页。**先只覆盖引擎 2 项**（最小改动、可立即验证），
     其余 26 项按同一模式后续补齐（避免一次性铺开 4 个页面）。
   - **后续（A）**：若产品确认「某本书单独回退引擎」是必须能力，再按 §7.1 实施，
     成本约 10 行 + 测试。

**如果 Codex 判断「一次做到位」更合适**：那么直接上方案 A 也是合理的 ——
它的代码成本确实低，且能同时消除误导（因为「只改本书」变成真的了）。
**此时方案 A 必须一并处理 §7.3 的三个语义问题**，否则会把一个信息缺失问题
换成一个行为歧义问题。

---

## 10. 建议实现文件边界

### 方案 B（推荐先行）

| 文件 | 改动性质 |
|---|---|
| `ui/screen/reader/sheets/ReaderAdvancedSettings.kt` | **主改动**：接收 `scope`，BOOK 作用域下把两个引擎项放进「全局设置」分组 + 提示 |
| `ui/screen/reader/sheets/ReaderSettingsSheet.kt` | 把 `scope` 传给 `AdvancedSettings`（当前 `:121` 未传） |
| `ui/screen/reader/sheets/ReaderSettingsCommon.kt` | 如需新的分组/提示组件（优先复用既有 `MicroSettingsSection` 的 `description`） |
| `androidTest/.../sheets/ReaderSettingsSheetTest.kt` | 若做 Compose 层验证（注意该文件不在本轮授权范围，需 Codex 裁定） |
| 新增 JVM 测试 | 见 §11 |

**不需要改**：`ReaderOverride.kt`、`ReaderSettingsRouter.kt`、`PerBookSettingsStore.kt`、
`SettingsStore.kt`、`ReaderPagerEngineState.kt`、`ReaderSettingsEditApplier.kt`。

### 方案 A（后续，若采纳）

| 文件 | 改动性质 |
|---|---|
| `data/settings/ReaderOverride.kt` | **主改动**：枚举 +2、`read`/`write` +2 分支、合法值集合 +1 |
| `data/settings/ReadingPresets.kt` | **需决策**：预设是否包含引擎项（建议不含） |
| `ui/screen/reader/sheets/ReaderSettingsCommon.kt:492` | 恢复默认对话框文案（项数 20 → 22）需复核 |
| `data/settings/PerBookSettingsStore.kt:92-94` | 复核 `resetGlobalReaderToDefaults` 是否应重置引擎（当前会自动重置） |
| 测试 | `ReaderSettingsRouterTest` 改写那条「缺陷当契约」的用例 + 新增覆盖项测试 |

**方案 A 明确不需要改**：`ReaderSettingsRouter.kt`、`PerBookSettingsStore` 的键构造、
`ReaderPagerEngineState.kt`、`ReaderSettingsEditApplier.kt`（全部以枚举为准）。

---

## 11. 建议红测清单

### 方案 B 的红测（先行）

| # | 测试 | 类型 | 断言 |
|---:|---|---|---|
| R1 | BOOK 作用域下高级页必须渲染「全局设置」提示 | 结构守卫 / Compose | 提示文本或 testTag 存在；GLOBAL 作用域下不渲染（或文案不同） |
| R2 | 引擎项在 BOOK 作用域下的归属声明 | 行为 | 两个引擎行被标注为全局（而非本书） |
| R3 | `scope` 传参链完整性 | 结构守卫 | `ReaderSettingsSheet` 必须把 `scope` 传给 `ADVANCED` 页 |
| R4 | 未标注项不得被误标为全局 | 行为 | 20 个可覆盖项在 BOOK 作用域下仍显示「本书」语义 |
| R5 | 回归：GLOBAL 作用域行为不变 | 行为 | 现有 `ReaderSettingsRouterTest` 全绿（除需改写的那条） |

### 方案 A 的红测（若采纳）

| # | 测试 | 类型 | 断言 |
|---:|---|---|---|
| R6 | `ReaderOverrideKey` 含两个引擎项且 round-trip | 行为 | `read`/`write` 往返一致；非法值（如 `"maybe"`）返回 null 不落盘 |
| R7 | BOOK 作用域下改引擎只写本书覆盖 | 行为 | `plan.overridesToSet` 含该键；`plan.global` 的引擎字段**等于 global 原值** |
| R8 | BOOK 作用域下改引擎**不污染全局** | 行为 | 另一本书的 `effectiveReader` 读到的引擎值与改动前一致 |
| R9 | GLOBAL 作用域下改引擎写全局并清除本书覆盖 | 行为 | `plan.global` 含新值；`overridesToClear` 含该键 |
| R10 | **枚举一致性守卫（C1）** | 结构守卫 | 「设置页可编辑的字段」集合 == 「可覆盖项 ∪ 显式声明为全局的字段」集合；新增字段未归类即失败 |
| R11 | `auto` 与进程级健康闸门的组合语义 | 行为 | per-book `auto` + `isAutoDisabled=true` → 引擎关闭（与全局 `auto` 同判据） |
| R12 | 既有「缺陷当契约」用例改写 | 行为 | `non overridable keys always go global regardless of scope` 改为只覆盖**真正**不可覆盖的字段（如 `brightness`），不再用引擎项作例子 |

**R10 是本次调查认为最有价值的长期防线**：28 个字段的漂移之所以无人发现，
就是因为没有任何测试把「UI 可编辑集合」与「数据可覆盖集合」对照起来。

---

## 12. 实际运行的命令与退出码

| # | 命令 | 退出码 | 关键输出 |
|---:|---|---|---|
| 1 | `git rev-parse HEAD` / `--abbrev-ref HEAD` | 0 | `5aed5939600e491843e3c44453921bd6273a233c` / `codex/recover-worktree-20260920` |
| 2 | `git diff --cached --name-only \| wc -l` | 0 | `0` |
| 3 | `git stash list \| wc -l` | 0 | `0` |
| 4 | `./gradlew --console=plain :app:testDebugUnitTest --rerun --tests …（5 类设置测试）` | **0** | `BUILD SUCCESSFUL in 45s`；5 类 58 项 / 0 failures / 0 errors / 0 skipped |
| 5 | 只读 grep / 源码阅读（`grep -n`、`sed -n`、Python 字段集合对比） | 0 | 见 §4.2、§6.3 的字段与行号 |

**未运行**（任务书未要求，且本轮为只读调查）：全量 JVM 测试、`:app:lintDebug`、
APK 打包、`androidTest` 执行、任何真机 / 模拟器命令。

**测试数量与失败数量汇总**：运行 5 个测试类 / **58 项** / **0 失败**。

---

## 13. 修改文件清单

**产品源码：0 个。测试代码：0 个。Gradle 配置：0 个。数据库 / schema / DAO：0 个。
项目看板 / 其他文档：0 个。**

**本任务唯一新建的文件：**

| 文件 | 性质 |
|---|---|
| `docs/coordination/investigations/agent-investigation-d02-zcode.md` | 本报告（新建） |

工作区中原有的其他未提交修改均为**用户资产**，本轮未触碰、未回退、未整理。
本轮未 commit / push / stage / stash / reset / checkout / clean / 全仓格式化。
未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`。

---

## 14. 未解决问题

| # | 问题 | 需要谁裁决 |
|---:|---|---|
| U1 | 方案 A 还是 B（本报告推荐 B 先行 + A 后续；若要求一次到位则 A） | Codex / 产品 |
| U2 | 若采纳方案 A：**预设**（`ReadingPresets`）是否应包含引擎项 | 产品 |
| U3 | 若采纳方案 A：**恢复默认**（`resetGlobalReaderToDefaults`）是否应把引擎模式重置为结构默认（当前会） | 产品 |
| U4 | 若采纳方案 B：其余 26 个不可覆盖字段是否同一轮补齐（`PAGING`/`DISPLAY`/`EYE_CARE` 三页），还是先只做引擎 2 项 | Codex |
| U5 | 若采纳方案 B：提示文案的最终措辞（需产品确认，避免「全局」一词对普通用户不够直观） | 产品 |
| U6 | `auto` 模式下进程级健康闸门（`PagerHealthStore`）与 per-book 覆盖的交互是否需在方案 A 中显式产品化 | Codex / 产品 |
| U7 | 已有用户若曾用「本书」作用域改过引擎，其全局值已被改动 —— 是否需要在发版说明中提示（无法自动区分，也不应自动回改） | Codex |
| U8 | `ReaderRulesSheetTest.kt` 等 androidTest 文件的授权边界（与 A01 的 SEAM REQUEST 同类问题），若方案 B 要加 Compose 验证会再次遇到 | Codex |

**本轮为只读调查，未开始任何修复实施，等待 Codex 复核与裁决。未领取下一片。**
