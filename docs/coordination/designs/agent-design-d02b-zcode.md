# D-2-B 只读设计调查：26 个全局字段的 UI 作用域透明度

> 执行 Agent：Zcode　|　实际模型：`deepseek v4.1Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 调查期间 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 性质：**只读调查与设计，未实施任何代码改动**
> 报告路径：`docs/coordination/designs/agent-design-d02b-zcode.md`
> 状态：**等待 Codex 复核与裁决**

标注约定：**[事实]** = 我读到的源码；**[推断]** = 静态推导；**[未验证]** = 需真机/运行期。

---

## 1. 结论摘要

**26 个字段清单重新核对后完全一致**（7 + 9 + 10），字段名、所在页面均与任务书给的一致，
无增减、无改名、无迁移。所有 26 项**均可从 UI 到达**（无残留死字段）。

**当前状态**：这 26 项在 BOOK 作用域下**可编辑**，写入的是**全局**，
且三个子页面（翻页 / 显示 / 护眼）**都拿不到 `scope`**，页面上没有任何提示。
真机验收已把它记为 **P3-1**：「高级兼容子页没有任何作用域提示」。

**关键发现（本轮新增，决定了推荐方案）**：同一设置体系里**已经存在**一个正确先例 ——
`ReaderThemeSheet`（主题面板）**已经接收 `scope`**，并在 BOOK 作用域下显示
「调整仅作用于当前书籍 · **亮度为全局**」（`ReaderThemeSheet.kt:257-261`）。
这证明「子面板接收 scope + 页内明示全局项」在本仓库是**既有、已验证的模式**，
不需要发明新机制，也说明产品此前已认可这种表达方式。

**推荐方案：A′（页面顶部统一提示 + 全局字段行内标记）** ——
以既有的 `ThemeSheet` 模式为模板，把 `scope` 传进三个子页面，
在页面顶部渲染一条作用域提示，并给全局字段加「所有书籍」标记。
**不推荐** B（分组重构：三个页面都混排本书项与全局项，强行分组会打散既有语义分组）
与 C（进入/修改时切换作用域：打断感强、状态同步复杂、误操作风险高）。

**最严重风险**：`brightness`（亮度）的措辞 —— 它是 **App 内阅读窗口亮度**
（`window.attributes.screenBrightness`，`ReaderPlatformEffects.kt:104-113`），
**不是系统亮度**。若文案写成「系统亮度」会引入新的错误认知。

---

## 2. 调查范围与基线

| 项 | 值 |
|---|---|
| 基线 | `5aed5939600e491843e3c44453921bd6273a233c`（未推进） |
| 分支 | `codex/recover-worktree-20260920` |
| `git diff --cached --name-only` | 0 |
| `git stash list` | 0 |
| D-2-A 状态 | **CLOSED**（代码复核 + 真机验收 PASS），本轮不讨论、不回退、不计入 26 项 |

**已读文档**：`AGENTS.md`、`docs/handoff/current.md`、
`docs/coordination/reviews/2026-09/2026-09-20-zcode-qoder-audit-board.md`（D-2 段 :314-325）、
`docs/coordination/investigations/agent-investigation-d02-zcode.md`、
`docs/coordination/agent-results/agent-result-d02a-zcode.md`、
`docs/coordination/reviews/undated/agent-review-d02a-qoder.md`、
`docs/qa/2026-09/2026-09-21-d02a-qoder-device-acceptance.md`（P3-1 在 :462）。

**只读检查的产品文件**（未修改）：三个设置页、`ReaderSettingsCommon.kt`、
`ReaderSettingsSheet.kt`、`ReaderThemeSheet.kt`、`ReaderSettingsRouter.kt`、
`ReaderOverride.kt`、`ReadingPresets.kt`、`ReaderScaffold.kt`、`ReaderScreen.kt`、
`ReaderSheetHost.kt`、`ReaderSettingsEditApplier.kt`、`SettingsStore.kt`、
`ReaderPlatformEffects.kt`、`ReaderWindowPolicy.kt`、`CompactSettingComponents.kt`、
`CustomTipDialog.kt`、`ReaderTapAction.kt`、`ReaderSubPage.kt`（profile 侧入口）。

---

## 3. 26 字段逐项事实表

### 3.1 共同列说明

| 列 | 含义 |
|---|---|
| **BOOK 写哪** | 当前 `ReaderSettingsRouter.plan(BOOK, ...)` 的落点。这 26 项都**不在** `ReaderOverrideKey`，因此全部走 `plan.global` **[事实]** |
| **GLOBAL 写哪** | 同为 `plan.global` |
| **误认风险** | 用户是否容易以为它是本书设置 |
| **推荐提示** | 建议的 UI 表达形式 |

**作用域落点依据**（26 项共用，逐项不再重复）：
`ReaderSettingsRouter.kt:75-81` 的 `mustStayGlobalBacked` 对 BOOK 取 `OVERRIDABLE`（= `ReaderOverrideKey.entries`），
`mask(base = edited, maskFrom = global, keys = OVERRIDABLE)` 只还原可覆盖项，
**其余字段保留 `edited` 值** → 进入 `plan.global` → `ReaderSettingsEditApplier.kt:34-36` 写全局。
两个分页引擎键在 D-2-A 已进入 `entries`，**不在此列**。

### 3.2 ReaderPagingSettings.kt（7 项）

| # | Kotlin 字段 | 中文名 | 控件 | 行号 | 显示条件 | 误认风险 | 推荐提示 |
|---:|---|---|---|---|---|---|---|
| 1 | `autoPageSpeed` | 自动翻页速度 | `MicroTrackSliderRow`（1–10 档） | :144-155 | 始终 | **中**：「阅读速度」听起来像本书偏好 | 行内「所有书籍」标记 |
| 2 | `pageTurnSpeed` | 翻页速度 | `MicroTrackSliderRow`（150–800ms） | :60-72 | 始终 | **中**：同上 | 行内标记 |
| 3 | `tapActionPreset` | 热区动作 | `MicroOptionPillRow`（标准/左手/双向） | :83-94 | 始终 | **高**：手持姿势像「因人而异」，不是「因书而异」，但用户仍可能以为是本书 | 行内标记 |
| 4 | `tapZoneMode` | 点击区域 | `MicroOptionPillRow`（左中右/上下扩展） | :74-81 | 始终 | **高**：同 3 | 行内标记 |
| 5 | `volumeKeyInvert` | 反转音量键方向 | `MicroSettingsSwitchRow` | :125-133 | **仅当 `volumeKeyPaging == true`**（:123） | **低**：明确是设备按键行为 | 行内标记 |
| 6 | `volumeKeyPaging` | 音量键翻页 | `MicroSettingsSwitchRow` | :116-122 | 始终 | **低**：同上 | 行内标记 |
| 7 | `volumeKeyPagingDuringTts` | 朗读时音量键仍翻页 | `MicroSettingsSwitchRow` | :135-141 | **仅当 `volumeKeyPaging == true`**（:123） | **低** | 行内标记 |

**该页同时含本书可覆盖项**：`readerMode`（:31-38）、`pageTurnEffect`（:40-49）、
`screenOrientation`（:100-107）**[事实]** → 页面**混排**两类字段。

### 3.3 ReaderDisplaySettings.kt（9 项）

| # | Kotlin 字段 | 中文名 | 控件 | 行号 | 显示条件 | 误认风险 | 推荐提示 |
|---:|---|---|---|---|---|---|---|
| 8 | `brightness` | 亮度 | `SettingBrightnessRow`（共用组件） | :27-33 | 始终 | **高**：用户以为调的是系统亮度，实际是 App 内窗口亮度（见 §6.2） | 行内标记 + 措辞避免「系统」 |
| 9 | `keepAwake` | 屏幕常亮 | `MicroSettingsSwitchRow` | :44-58 | 始终 | **中**：像「读这本书时别息屏」 | 行内标记 |
| 10 | `batteryTipStyle` | 电量显示方式 | `MicroOptionPillRow` | :93-101 | **仅当 `showReaderInfo == true`**（:91） | **低**：属页眉模板细节 | 行内标记 |
| 11 | `customTipHeaderLeft` | 页眉左侧 | `HeaderFooterPicker` → **二级对话框** | :103-106 | **仅当 `showReaderInfo == true`** | **低** | 行内标记（在折叠行上） |
| 12 | `customTipHeaderCenter` | 页眉中间 | 同上 | :108-111 | 同上 | **低** | 同上 |
| 13 | `customTipHeaderRight` | 页眉右侧 | 同上 | :113-116 | 同上 | **低** | 同上 |
| 14 | `customTipFooterLeft` | 页脚左侧 | 同上 | :118-121 | 同上 | **低** | 同上 |
| 15 | `customTipFooterCenter` | 页脚中间 | 同上 | :123-126 | 同上 | **低** | 同上 |
| 16 | `customTipFooterRight` | 页脚右侧 | 同上 | :128-131 | 同上 | **低** | 同上 |

**该页同时含本书可覆盖项**：`immersiveMode`（:35-42）、`showProgressBar`（:44-50）、
`showReaderInfo`（:84-90）、`autoHideSeconds`（:67-75）**[事实]**。

### 3.4 ReaderEyeCareSettings.kt（10 项）

| # | Kotlin 字段 | 中文名 | 控件 | 行号 | 显示条件 | 误认风险 | 推荐提示 |
|---:|---|---|---|---|---|---|---|
| 17 | `eyeCareFilterEnabled` | 开启护眼滤镜 | `MicroSettingsSwitchRow` | :68-74 | 始终 | **中**：护眼像「个人偏好」 | 行内标记 |
| 18 | `eyeCareScheduleEnabled` | 夜间自动开启 | `MicroSettingsSwitchRow` | :76-82 | 始终 | **低**：定时是设备行为 | 行内标记 |
| 19 | `eyeCareStartMinute` | 开始时间 | `EyeCareTimeRow`（平台 TimePicker） | :85-89 | **仅当 `eyeCareScheduleEnabled == true`**（:83） | **低** | 行内标记 |
| 20 | `eyeCareEndMinute` | 结束时间 | 同上 | :91-95 | 同上 | **低** | 同上 |
| 21 | `eyeCareTemperature` | 色温 | `MicroTrackSliderRow` | :99-110 | **仅当 `eyeCareFilterEnabled \|\| eyeCareScheduleEnabled`**（:97） | **中** | 行内标记 |
| 22 | `eyeCareIntensity` | 强度 | `MicroTrackSliderRow` | :112-123 | 同上（:97） | **中** | 同上 |
| 23 | `eyeCareOledBlackCompat` | OLED 纯黑兼容 | `MicroSettingsSwitchRow` | :125-132 | 同上（:97） | **低**：明确是屏幕技术特性 | 同上 |
| 24 | `eyeCareReminderMinutes` | 护眼提醒 | `MicroTrackSliderRow`（5–60 分） | :142-153 | 始终 | **中**：像「读书时长习惯」 | 行内标记 |
| 25 | `readingRhythmReminderEnabled` | 阅读节奏提示 | `MicroSettingsSwitchRow` | :155-162 | 始终 | **中** | 同上 |
| 26 | `readingRhythmReminderMinutes` | 提示间隔 | `MicroTrackSliderRow` | :165-176 | **仅当 `readingRhythmReminderEnabled == true`**（:163） | **中** | 同上 |

**该页不含任何本书可覆盖项**（`global=10 / edited=10`）**[事实]** → 全页都是全局项，
这是**唯一可以整页打一条提示**的页面。

### 3.5 数量核对结论

| 页面 | 字段数 | 与任务书一致？ |
|---|---:|---|
| `ReaderPagingSettings.kt` | 7 | ✅ |
| `ReaderDisplaySettings.kt` | 9 | ✅ |
| `ReaderEyeCareSettings.kt` | 10 | ✅ |
| **合计** | **26** | ✅ |

**差异**：无。**字段名、页面归属、数量全部与任务书一致**。
**补充发现**：任务书未提及的两点 ——（a）其中 **10 项受其他开关控制**（条件显示），
（b）三个页面中 **2 个混排**本书项与全局项，仅护眼页是纯全局页。

---

## 4. 当前作用域 UI 与数据流

### 4.1 用户在哪里选择作用域

`ReaderSettingsCommon.kt:391-468` 的 `SettingsScopeSection`，渲染两个胶囊：
「只改本书」/「改全局默认」（文案在 `:447`）。
入口在**设置根页**：`ReaderSettingsSheet.kt:151-153` 的 `item("settings-scope")`。
默认值 `BOOK`（`ReaderScaffold.kt:153`：`remember(bid) { mutableStateOf(BOOK) }`）。

### 4.2 作用域如何流转

```
ReaderScaffold.kt:153  settingsScopeState = remember(bid) { mutableStateOf(BOOK) }
   ↓ 经 ReaderScreenMutableHolders（:167）
ReaderScreen.kt:178     var settingsScope by mutableHolders.settingsScopeState
   ↓ :626-627
ReaderScaffold.kt:192-193  settingsScope / onSettingsScopeChange
   ↓ ReaderScaffold 内 :677
ReaderSheetHost.kt:124  val settingsScope: ReaderSettingsScope = BOOK   ← 进入宿主
   ↓
ReaderSheetHost.kt:530  SettingsSheet(scope = state.ui.settingsScope, ...)
   ↓
ReaderSettingsSheet.kt:69  scope: ReaderSettingsScope = BOOK
   ↓ :102-116 仅传给 SettingsRoot
ReaderSettingsSheet.kt:108  SettingsRoot(scope = scope, ...)
   ↓ :152  渲染 SettingsScopeSection（作用域切换胶囊）
   ↓ :158  传给 BookOverrideSection（覆盖明细）
   ↓ :117-121  五个子页面：
        TYPOGRAPHY -> TypographySettings(settings, onSettingsChange, paper)
        PAGING     -> PagingSettings(settings, onSettingsChange)
        DISPLAY    -> DisplaySettings(settings, onSettingsChange)
        EYE_CARE   -> EyeCareSettings(settings, onSettingsChange)
        ADVANCED   -> AdvancedSettings(settings, onSettingsChange)
```

### 4.3 哪些页面拿得到 scope（关键结论）

| 组件 | 是否收到 `scope` | 证据 |
|---|---|---|
| `SettingsRoot` | ✅ 收到 | `ReaderSettingsSheet.kt:108` |
| `BookOverrideSection` | ✅ 收到 | `:158` |
| `TypographySettings` | ❌ **未收到** | `:117`（只传 settings/onChange/paper） |
| **`PagingSettings`** | ❌ **未收到** | `:118` |
| **`DisplaySettings`** | ❌ **未收到** | `:119` |
| **`EyeCareSettings`** | ❌ **未收到** | `:120` |
| `AdvancedSettings` | ❌ 未收到 | `:121`（D-2-A 的 P3-1 即此项） |
| **`ThemeSheet`（独立面板）** | ✅ **收到** | `ReaderSheetHost.kt:552` `scope = state.ui.settingsScope` |

**这是 D-2-B 的核心结构事实**：**三个含全局字段的子页面都拿不到 `scope`**，
因此**在架构上不可能**做出任何作用域差异化展示。修 D-2-B 必须先把 `scope` 传下去。

### 4.4 已有的作用域徽标 / 说明（可复用）

| 资产 | 位置 | 形态 |
|---|---|---|
| `OverrideBadge` | `ReaderSettingsCommon.kt:197-218` | 小圆角徽标，文本「本书」或「本书 (全局 X)」 |
| 行距 / 翻页效果副标题 | `ReaderSettingsSheet.kt:190, 202` | `subtitle = if (overrides.containsKey(...)) "本书" else null` |
| `SettingsScopeSection` | `ReaderSettingsCommon.kt:391-468` | 作用域切换胶囊（仅根页） |
| **`ThemeSheet` 作用域提示** | `ReaderThemeSheet.kt:257-261` | **`Icon(Info) + Text`：「调整仅作用于当前书籍 · 亮度为全局」/「调整将作用于全局默认设置」** |
| `MicroSettingsSection.description` | `ReaderSettingsCommon.kt:117` → `CompactSettingComponents.kt:63` | 分组标题下的说明行（**已存在但设置页未使用**） |
| testTag | `reader-settings-scope`（`ReaderSettingsCommon.kt:405`）、`reader-settings-presets`（:585） | 可扩展 |

**结论**：**不需要发明新组件** —— `ThemeSheet` 的「Info 图标 + 一行说明」已经是被产品接受、
且已在真机上跑过的形态；`MicroSettingsSection.description` 是现成的分组说明入口。

### 4.5 进入子页面后是否还能看到作用域

**不能。** `SettingsScopeSection` 只在 `SettingsRoot` 渲染（`:151-153`）；
子页面由 `AnimatedContent`（`:89-123`）整页替换，标题变为页名
（`ReaderSettingsPage` 的 `title`，`ReaderSettingsCommon.kt:87-94`），
**不携带任何作用域信息**。

### 4.6 作用域的保持与重置

| 场景 | 行为 | 证据 |
|---|---|---|
| 在根页切换作用域 | `settingsScopeState` 更新，会话内保持 | `ReaderScreen.kt:627` |
| 进入子页面再返回 | **保持**（`scope` 是 `SettingsSheet` 的参数，`page` 只是局部 `remember`） | `ReaderSettingsSheet.kt:80` |
| 关闭 sheet 再打开 | **保持**（`settingsScopeState` 存活在 `ReaderScaffold`，`sheet` 只是显隐） | `ReaderScaffold.kt:153` |
| **切书** | **重置为 BOOK** | `ReaderScaffold.kt:153` `remember(bid) { ... }` |

### 4.7 是否存在可复用的提示 / 徽标 / testTag

见 §4.4。**结论：充足**，无需新增公共组件（若需要新增，见 §10 的 SEAM REQUEST 判据）。

---

## 5. 三种方案比较

### 5.1 方案定义

- **方案 A**：页面顶部统一提示（BOOK 作用域 + 页面含全局字段时显示），并给全局字段加「全局」标记。
- **方案 B**：把本书设置与全局设备设置**分成两个区块**（「本书设置」/「所有书籍共用」）。
- **方案 C**：进入或修改全局字段时**显式切换作用域**（或弹一次说明）。

### 5.2 十维比较

| 维度 | A（顶部提示 + 行内标记） | B（分区） | C（切换/弹窗） |
|---|---|---|---|
| **1. 修改前能否知道影响范围** | ✅ 进页即见 + 每项有标记 | ✅ 分区标题直接说明 | ⚠️ 取决于触发时机（进入时切换=提前；修改时弹窗=已点下去） |
| **2. 「全局」是否易误解为云同步/账号** | ⚠️ 需措辞规避（见 §6 文案用「所有书籍」） | ⚠️ 同 | ⚠️ 同 |
| **3. 是否造成大量重复文案** | 低：1 条页级提示 + 26 个小标记 | 中：每页 2 个区块标题 + 2 条说明 | 高：每次进入/修改都出现说明 |
| **4. 是否适合三个页面** | ✅ 统一模式，逐页套用 | ❌ **翻页页与显示页混排**（§3.2/§3.3），分区会打散既有的语义分组（如「翻页动画与点击区域」「屏幕显示」），且**护眼页全是全局项**、分区后只剩一个空区块 | ⚠️ 可行但每页行为不同 |
| **5. 是否支持后续新增全局字段** | ✅ 新增字段加一个标记即可 | ⚠️ 新增字段要判断归哪个区块 | ❌ 每个新字段都要考虑是否触发切换 |
| **6. 是否容易写可靠测试** | ✅ 提示与标记都是稳定文本/testTag；可对 26 项逐一断言 | ⚠️ 分区容器层级变化大，既有测试需较多改写 | ❌ 依赖交互时序，Compose 测试需模拟点按与状态回滚 |
| **7. 是否需修改路由语义** | ❌ 不需要（只传参 + 渲染） | ❌ 不需要 | ✅ **需要**（作用域要能被程序化改写，且要能回滚） |
| **8. 是否影响 D-2-A 两个引擎键** | ❌ 不影响（不触碰 `ReaderOverrideKey` / 预设集合 / 路由） | ⚠️ 若分区需判断「哪些是全局」，可能诱发把引擎键错误归入全局区 | ❌ 不影响 |
| **9. 预计修改文件** | **4 个**（`ReaderSettingsSheet.kt` + 三个设置页） | **5-6 个**（上列 + 可能新增分区组件） | **6+ 个**（+ `ReaderSettingsEditApplier.kt` 或路由，+ 状态回滚） |
| **10. 风险与回归面** | **低**：纯增量渲染，不改数据与路由 | **中**：布局重构，`ReaderSettingsSheetTest` 的滚动/可见性用例可能受影响 | **高**：作用域自动改写会让「用户以为在改本书、实际改成全局」以另一种形式复现（**反而制造新的误导**） |

### 5.3 方案 C 的专项分析（任务书要求不得默认推荐弹窗）

| 子问题 | 分析 |
|---|---|
| **弹窗频率** | 翻页页 7 项、显示页 9 项都是全局项 → 用户几乎每次改设置都会撞到弹窗 |
| **打断感** | 高。用户在「只改本书」语境下连续调 3 个滑杆 → 可能连续弹 3 次 |
| **状态同步** | 复杂。若「修改全局字段」要自动把作用域切成 GLOBAL，则之后用户改**本书**项（同页混排）时又要切回 BOOK —— 页面内作用域会随字段跳变，**用户无法预测下一个控件属于哪一层** |
| **误操作风险** | **最高**：自动切换让「只改本书」这个显式选择被静默推翻。用户以为选了本书，界面却在改全局 —— 这正是 D-2 原始缺陷的**同构再现** |
| **实现复杂度** | 高：需在 `onChange` 路径上判断字段归属并改写作用域状态、处理回滚、处理同页混排 |

**结论：方案 C 不推荐。** 它把「信息缺失」问题换成了「状态自动跳变」问题，风险更高。

### 5.4 方案 B 的专项分析

方案 B 在**纯全局页（护眼页）**表现良好，但在**混排页**（翻页页、显示页）有硬伤：

- 翻页页的「翻页动画与点击区域」分组里**同时**有 `readerMode`（本书可覆盖）与
  `pageTurnSpeed`（全局）**[事实]** —— 强行分区会把「阅读模式」和「翻页速度」
  这两个用户心里的同一件事拆到两个区块；
- 显示页的「屏幕显示」分组里**同时**有 `immersiveMode`/`showProgressBar`（本书）
  与 `brightness`/`keepAwake`（全局）**[事实]** —— 同样会被拆散；
- 护眼页全是全局项（`global=10`）→ 分区后「本书设置」区块为空，需额外处理空区块。

**结论：方案 B 的语义收益不足以抵消分组打散与空区块问题**，作为独立方案不推荐；
但其「区块说明」的**文案**可以并入方案 A 使用。

### 5.5 方案 A 的专项分析

- **天然契合既有先例**：`ThemeSheet` 已经在做「scope 感知 + 一行说明」（§4.4）；
- **不改数据语义**：只传参 + 渲染，`ReaderSettingsRouter` / `applyReaderEdit` / `ReaderOverrideKey` 全部不动；
- **覆盖 26 项的方式**：页级 1 条提示（告知本页含全局项）+ 行内标记（精确到项）——
  两者结合既解决「整体不知道」，也解决「不知道具体哪一项」；
- **对条件显示项**：标记随控件一起出现/隐藏，不需要额外逻辑（如 `volumeKeyInvert`
  只在 `volumeKeyPaging` 开启时出现，标记跟着出现）；
- **唯一注意点**：护眼页全页皆全局，页级提示可以写得更强（整页说明），
  而混排页需明确「本页**部分**设置」—— 措辞差异见 §7。

---

## 6. 推荐方案及理由

### 6.1 推荐：方案 A′（以 `ThemeSheet` 为模板）

**页面顶部作用域提示（BOOK 作用域且页面含全局项时）+ 全局字段行内标记。**

**为什么推荐**（一句话）：
**因为仓库里已经有一个被产品接受、真机跑过的同类先例（`ThemeSheet` 的「亮度为全局」提示），
方案 A′ 是把那个已验证的模式扩展到三个设置页，改动最小、语义最清晰、且不需要碰数据与路由。**

补充理由：

1. **修的是信息缺失，不是功能缺失** —— 这 26 项**本来就应该**保持全局（设备/交互偏好），
   方案 A′ 只补信息，不改语义，与任务书「不得改变存储位置或生效语义」完全一致；
2. **可精确到项** —— 页级提示解决「不知道本页有全局项」，行内标记解决「不知道是哪一项」；
3. **对混排页天然友好** —— 不需要像方案 B 那样把同组字段拆开；
4. **对条件显示项零成本** —— 标记挂在控件上，随控件的显示条件一起出现；
5. **测试友好** —— 提示文本与标记都可稳定断言，可对 26 项逐一验证；
6. **不碰 D-2-A** —— 不修改 `ReaderOverrideKey` / `ReadingPresets` / 路由 / 预设集合。

### 6.2 措辞必须规避的两个陷阱（专项发现）

**陷阱 1：`brightness` 不是系统亮度** **[事实]**

`ReaderPlatformEffects.kt:104-113` 用 `window.attributes.screenBrightness = ReaderWindowPolicy.screenBrightness(readerBrightness)`
设置的是**当前 Activity 窗口**的亮度，并在 `onDispose` 恢复原值。
`ReaderWindowPolicy.kt:10-28` 说明 0–4 档用「系统 floor + 黑色压暗遮罩」补足。

→ **文案不得写「系统亮度」**，应写「阅读页亮度」或「阅读时的屏幕亮度」。
→ 这也是 `ThemeSheet` 已用「亮度为全局」而非「系统亮度」的原因。

**陷阱 2：「全局」一词的歧义**

「全局」在开发者语境=「所有书籍共用」，但普通用户可能理解为「云同步 / 账号级设置」。
→ **统一使用「所有书籍」作为用户可见词**，仅在必要处保留「全局」作补充
（如「所有书籍（全局）」）。任务书 §四-8 也明确要求文案必须写出「会影响所有书籍」。

---

## 7. 最终中文文案

> 全部文案**不使用** DataStore / override / scope / router 等技术词；
> **不使用** Markdown 粗体源码（如 `**全局**`）—— 真机验收 P3-2 已证实星号会原样渲染。

### 7.1 页面顶部提示（混排页：翻页 / 显示）

BOOK 作用域：

```
本页有部分设置对所有书籍生效
这些设置与设备、按键和屏幕有关，改一次所有书籍都会跟着变。已单独标出。
```

GLOBAL 作用域：

```
本页所有设置对所有书籍生效
改动会同时写入全局默认设置。
```

护眼页（整页都是全局项）—— 措辞可更强：

```
本页设置对所有书籍生效
护眼、提醒与设备相关，不跟随单本书，改动会影响所有书籍。
```

### 7.2 全局字段旁的短标签

| 场景 | 标签文本 |
|---|---|
| 短标签（行内徽标） | `所有书籍` |
| 需要更明确时（如首项或强调） | `所有书籍共用` |

**不使用**「全局」单词作为标签（歧义风险，见 §6.2 陷阱 2）。
若产品坚持保留「全局」以与根页「改全局默认」胶囊呼应，则用 `全局（所有书籍）`。

### 7.3 标签的无障碍描述

Compose `contentDescription`（`Modifier.semantics`）：

```
此设置对所有书籍生效，不只是当前这本书
```

对开关类控件，建议把该描述**附加**到控件语义上，而不是替换控件本身的描述：

```
自动翻页速度，当前 5 档。此设置对所有书籍生效，不只是当前这本书
```

### 7.4 处于「只改本书」时的解释

页面顶部提示已经说明「本页有部分设置对所有书籍生效」。
若用户点开标记想了解更多，可用一行补充（可选，非必须）：

```
你选择了「只改本书」。上面这些带「所有书籍」标记的设置不受该选择影响，
它们始终作用于所有书籍。
```

> **[推断]** 这段文案解释了**为什么**「只改本书」对它们无效 —— 这是消除用户困惑的关键一句。
> 建议实现时至少把它放进标记的 `contentDescription`，避免页面上文案过长。

### 7.5 若采用确认提示（方案 C，不推荐，仅备文案）

若 Codex 最终选择 C，文案如下（供参考，本报告不推荐）：

| 项 | 内容 |
|---|---|
| 标题 | `这项设置对所有书籍生效` |
| 正文 | `「自动翻页速度」与设备有关，不跟随单本书。修改后所有书籍都会使用新速度。是否继续？` |
| 主按钮 | `继续修改` |
| 次按钮 | `取消` |
| 附加 | 正文中提供「以后不再提示」选项（否则每次修改都会打断） |

### 7.6 文案禁用词清单

| 禁用 | 原因 |
|---|---|
| `**全局**` | Markdown 星号会原样渲染（真机 P3-2 已证实） |
| `系统亮度` | `brightness` 是 App 窗口亮度，不是系统亮度（§6.2 陷阱 1） |
| `DataStore` / `override` / `scope` / `router` / `fallback` | 技术词，用户不可理解 |
| 单独的「全局」 | 歧义（可能被理解为账号/云同步）—— 必须写成「所有书籍」 |
| `覆盖` | 仅在根页「覆盖 N 项」既有语境中使用，不引入到子页面 |

---

## 8. 最小实现文件边界

### 8.1 需要修改（4 个产品文件）

| 文件 | 改动 |
|---|---|
| `sheets/ReaderSettingsSheet.kt` | `:118-120` 把 `scope` 传给 `PagingSettings` / `DisplaySettings` / `EyeCareSettings`（3 处实参） |
| `sheets/ReaderPagingSettings.kt` | 签名加 `scope: ReaderSettingsScope`；页首插入作用域提示；7 个控件加行内标记 |
| `sheets/ReaderDisplaySettings.kt` | 同上；9 个控件加标记（6 个 `customTip*` 标记加在折叠行上） |
| `sheets/ReaderEyeCareSettings.kt` | 同上；10 个控件加标记；页首提示用「整页」措辞 |

### 8.2 不应修改

| 文件 | 理由 |
|---|---|
| `ReaderSettingsRouter.kt` | 作用域路由已正确（这 26 项就该进全局） |
| `ReaderSettingsEditApplier.kt` | 落盘入口无需变化 |
| `ReaderOverride.kt` | 26 项**不得**加入枚举（会改变数据语义） |
| `ReadingPresets.kt` | 预设键集合与 D-2-A 已闭合，不得触碰 |
| `PerBookSettingsStore.kt` | 同上 |
| `SettingsStore.kt` | 存储位置不变 |
| `ReaderScaffold.kt` / `ReaderScreen.kt` / `ReaderSheetHost.kt` | `scope` 已在 `ReaderSheetHost` 可用（`:124`），无需上溯改动 |
| `ReaderThemeSheet.kt` | 已有正确实现，作为参照不改 |
| `CompactSettingComponents.kt` | 分组说明已支持 `description`，无需改公共组件 |

### 8.3 是否需要让三个子页面接收 `scope`

**需要。** 这是 D-2-B 的结构前提（§4.3）。三页都加一个 `scope: ReaderSettingsScope` 参数。

**注意**：`scope` 目前有默认值 `BOOK`（`ReaderSettingsSheet.kt:69`），
子页面参数建议**不给默认值**，让「忘了传」变成编译错误 ——
与 A01/D-2-A 的做法一致（避免默认值把漏接线藏起来）。

### 8.4 是否需要新增统一组件

**不需要新增公共组件。** 复用：

- 页级提示：参照 `ReaderThemeSheet.kt:257-261` 的 `Icon(Info) + Text` 形态，
  在设置页内新增一个**页内私有** composable（如 `GlobalScopeNotice`），
  **不放进** `ui/components/`（避免扩大公共面，也避免触发 SEAM REQUEST）；
- 行内标记：可复用 `OverrideBadge` 的**视觉形态**，但语义不同
  （`OverrideBadge` 表达「本书」，这里表达「所有书籍」）。
  建议新增页内私有 `AllBooksBadge`，**不改** `OverrideBadge`（它被根页使用，改动面更大）。

### 8.5 字段元数据 vs 逐项显式标记

**推荐：逐项显式标记。**

| 方案 | 评价 |
|---|---|
| 字段元数据表（如 `Map<String, FieldScope>`） | ❌ 需要维护第二份「字段→作用域」清单，与 `ReaderOverrideKey` 形成漂移风险；且 `ReaderSettings` 有 40+ 字段，元数据表要覆盖全部才安全 |
| **逐项显式标记** | ✅ 每个控件旁显式加标记，**新增字段时不会自动获得错误标记**；漏加是「少一个标记」，不是「标错」 |

**如何避免未来新增全局字段后忘记加提示**（任务书要求）：

1. **结构守卫测试**：读取三个设置页源码，提取所有 `settings.copy(<field>)` 的字段名，
   断言「不在 `ReaderOverrideKey.entries` 的字段，其所在行附近必须出现标记调用」
   （参考 A01 的 `TocNotesWiringGuardTest` 配平定位写法）；
2. **反向断言**：断言 `ReaderOverrideKey.entries` 中**没有**这 26 个字段名
   （防止有人为了让测试通过而把它们加进枚举 —— 任务书 §10 明确禁止）；
3. **数量守卫**：断言三页的「全局字段数」为 7/9/10（变化时强制复核）。

### 8.6 testTag / 语义节点建议

| 节点 | 建议 testTag |
|---|---|
| 页级作用域提示 | `reader-settings-global-notice` |
| 行内「所有书籍」标记 | `reader-settings-all-books-badge` |
| 具体字段的标记（可选，便于逐项断言） | `reader-settings-all-books-badge-<fieldName>` |

沿用既有命名前缀 `reader-settings-*`（与 `reader-settings-scope` / `reader-settings-presets` 一致）。

### 8.7 三层测试分别验证什么

| 层次 | 验证内容 | 说明 |
|---|---|---|
| **JVM 纯逻辑测试** | ①26 个字段**不在** `ReaderOverrideKey.entries`；②`ReaderSettingsRouter.plan(BOOK, ...)` 对这 26 项仍写全局（**既有契约的回归护栏**，证明 D-2-B 没改数据语义） | 纯函数，无 Compose |
| **结构守卫测试** | ①三个设置页的「可编辑字段 − 可覆盖字段」集合 == 预期 26 项；②每个全局字段附近有标记调用；③`scope` 参数已传入三页（配平定位实参） | 读源码文本，与 A01 的守卫同风格 |
| **Compose instrumentation 测试** | ①BOOK 作用域下三页均渲染 `reader-settings-global-notice`；②GLOBAL 作用域下提示文案不同；③26 项标记数量正确；④条件显示项（如 `volumeKeyInvert`）隐藏时标记也隐藏 | 需设备/模拟器（本轮不跑） |

### 8.8 哪些现有测试需要改写

| 测试 | 是否需要改 | 理由 |
|---|---|---|
| `androidTest/.../ReaderSettingsSheetTest.kt` | **需要新增**（现有 6 个用例不必改） | 它的 `renderSettings`（`:190-227`）不传 `scope`。若子页面参数**无默认值**，则其调用 `SettingsSheet(...)` 仍可编译（`scope` 在 `ReaderSettingsSheet.kt:69` 有默认值 `BOOK`）→ **现有用例不受影响**，只需新增作用域提示用例 |
| `test/.../ReaderSettingsRouterTest.kt` | **新增**（不改既有） | 加一组「26 项在 BOOK 下仍写全局」的回归护栏 |
| `test/.../ReaderSettingsOverlayTest.kt` | 不需要 | 与 UI 无关 |
| 其他 | 不需要 | — |

### 8.9 不得循环论证的测试

**明确禁止**：在测试里复制生产代码的公式再断言（如「把设置页源码里的字段名列表再抄一遍做比较」）。
正确做法是**从源码提取**字段集合，与 `ReaderOverrideKey.entries` 对照 ——
提取方式与生产实现无关（配平定位 `settings.copy(...)`），
若生产新增字段而测试未预期，测试会因「集合不匹配」失败，而不是「跟着一起变」。

同样禁止：Compose 测试里断言 `notice == 生产常量`（把结论写进输入）。
应断言**用户可见文本内容**（如 `onNodeWithText("所有书籍")`）。

### 8.10 是否需要真机验收

**需要，但只需最小路径**（4 步）：

1. 打开任一测试书 → 阅读设置 → **不切换作用域**（默认 BOOK）→ 进「翻页与操作」→ 截图确认顶部提示可见；
2. 在「翻页与操作」页把「自动翻页速度」从 5 调到 7 → 返回根页确认「覆盖 N 项」**未增加**（证明仍是全局写入，D-2-B 未改语义）；
3. 打开**另一本**测试书 → 阅读设置 → 翻页页 → 确认「自动翻页速度」已是 7（证明确实是全局生效）；
4. 切到「改全局默认」作用域 → 进翻页页 → 确认提示文案变为「本页所有设置对所有书籍生效」。

**不需要**逐项验证 26 个字段的真机表现 —— 行内标记是同一组件的重复渲染，
JVM 结构守卫 + 1 项 Compose 断言即可覆盖；真机只需确认「模式成立 + 语义未变」。

---

## 9. 测试与真机验收设计（汇总）

| 层次 | 类/文件 | 预计新增用例 |
|---|---|---|
| JVM 纯逻辑 | `ReaderSettingsRouterTest`（追加） | 26 项 BOOK 下仍写全局（可用 1 个参数化用例遍历 26 字段） |
| JVM 纯逻辑 | 新增 `ReaderGlobalFieldInventoryTest` | ①三页全局字段集合 == 7/9/10；②26 项均不在 `ReaderOverrideKey.entries` |
| 结构守卫 | 新增 `ReaderGlobalScopeNoticeWiringGuardTest` | ①`scope` 传入三页；②每个全局字段附近有标记调用；③三页均有页级提示调用 |
| Compose（instrumentation） | `ReaderSettingsSheetTest`（追加） | ①BOOK 下三页提示可见；②GLOBAL 下文案不同；③标记数量正确；④条件项隐藏时标记隐藏 |

**真机**：按 §8.10 的 4 步最小路径。

---

## 10. 风险、未确认项与 SEAM REQUEST

### 10.1 风险分级

| 级别 | 数量 | 项 |
|---|---:|---|
| **P0** | 0 | — |
| **P1** | 0 | — |
| **P2** | 1 | **R-1：`brightness` 措辞风险**。若文案写成「系统亮度」会引入新的错误认知（实际是 App 窗口亮度，§6.2 陷阱 1）。必须在实现时用「阅读页亮度」类措辞 |
| **P3** | 4 | **R-2**：26 个行内标记会增加视觉密度，小屏（横屏 407dp 高）下可能拥挤，需视觉验收；**R-3**：`customTip*` 六项的标记加在折叠行上，用户点开二级对话框后看不到标记（可接受，但需产品确认）；**R-4**：护眼页全页全局，页级提示与行内标记并存会有一定冗余（可考虑该页省略行内标记，仅保留页级提示）；**R-5**：根页「恢复全局默认」对话框文案（D-2-A 刚改成 22 项）与新的「所有书籍」措辞体系不完全统一，可能需在 D-2-B 后统一术语 |

### 10.2 未确认项

| # | 项 | 需要什么 |
|---:|---|---|
| U1 | 行内标记是否会造成小屏拥挤 | 真机/预览视觉验收（R-2） |
| U2 | 护眼页是否省略行内标记 | 产品决策（R-4） |
| U3 | 「所有书籍」vs「全局（所有书籍）」的最终用词 | 产品决策（与根页胶囊「改全局默认」的呼应度） |
| U4 | `customTip*` 二级对话框内是否也需要提示 | 产品决策（R-3） |
| U5 | 是否统一根页既有「全局」措辞（如「恢复全局默认设置？」） | 产品决策（R-5），可能扩大范围 |
| U6 | 26 项标记的**精确插入位置**（哪个控件参数位）需按各控件签名逐个确认 | 实现阶段按 `MicroOptionPillRow` / `MicroSettingsSwitchRow` / `MicroTrackSliderRow` / `SettingBrightnessRow` / `HeaderFooterPicker` 五个组件签名确定 |

### 10.3 SEAM REQUEST

**本轮无 SEAM REQUEST。**

理由：方案 A′ 的全部改动落在 **4 个授权设置页文件**内，
且 §8.4 明确要求把页级提示与行内标记做成**页内私有 composable**，
不修改 `ui/components/` 下的公共组件（`CompactSettingComponents.kt` / `CustomTipDialog.kt` 等）。

**条件性 SEAM REQUEST（仅当产品选择下列做法时才需要，本轮不申请）**：

| 触发条件 | 需要修改的文件 | 原因 |
|---|---|---|
| 若产品要求把「所有书籍」标记做成**全仓统一徽标**（与 `OverrideBadge` 并列放进公共组件） | `ui/screen/reader/sheets/ReaderSettingsCommon.kt`（该文件在 A01/D-2-A 授权范围内，但 D-2-B 未明确授权） | 统一徽标会被根页与三个子页共用，属公共面扩展 |
| 若产品要求 `MicroSettingsSection` 增加「作用域」专用参数（而非复用 `description`） | `ui/components/CompactSettingComponents.kt` | 公共组件签名变更 |
| 若要求在 `customTip*` 二级对话框内也显示标记 | `ui/components/CustomTipDialog.kt` | 公共对话框组件 |

上述三项**均非必需** —— 现有 `description` 参数与页内私有 composable 已足够完成方案 A′。

---

## 11. 是否建议进入实现

**建议：是，进入实现（方案 A′），但建议先由 Codex 裁决三项产品用词后再动手。**

理由：

1. **问题确认存在且已被真机记录**（P3-1），不是推测；
2. **方案 A′ 有仓库内既有先例**（`ThemeSheet`），风险低、回归面小；
3. **不触碰任何数据语义**（26 项仍在全局、不进枚举、不改路由、不碰 D-2-A）；
4. **改动面可控**：4 个产品文件 + 3-4 个测试文件；
5. **测试可分层验证**：JVM 契约回归 + 结构守卫 + 少量 Compose 断言；
6. **需要产品先定三件事**：U3（用词）、U2（护眼页是否省略行内标记）、U4（二级对话框是否加提示）——
   这三项会直接影响实现细节与测试断言，先定可避免返工。

**实现顺序建议**：先补 JVM 契约回归与结构守卫（红测）→ 再传 `scope` + 加页级提示 →
再逐页加行内标记 → 最后 Compose 断言与真机最小路径。

---

## 12. 回传摘要

| 项 | 内容 |
|---|---|
| 调查结论 | **PASS**（只读调查完成，26 项清单与任务书完全一致，无差异） |
| 26 字段重新核对 | **26**（7 + 9 + 10），字段名/页面归属全部一致，无增减 |
| 三页字段数 | `ReaderPagingSettings` 7 / `ReaderDisplaySettings` 9 / `ReaderEyeCareSettings` 10 |
| 推荐方案 | **A′：页面顶部作用域提示 + 全局字段行内「所有书籍」标记**（以既有 `ThemeSheet` 模式为模板） |
| 一句话理由 | 仓库内已有被产品接受、真机跑过的同类先例（`ThemeSheet` 的「亮度为全局」提示），方案 A′ 是把已验证模式扩展到三页，改动最小、语义最清晰、不碰数据与路由 |
| 预计实现文件 | 产品 4：`ReaderSettingsSheet.kt`、`ReaderPagingSettings.kt`、`ReaderDisplaySettings.kt`、`ReaderEyeCareSettings.kt` |
| 预计测试文件 | 4：`ReaderSettingsRouterTest`（追加）、新增 `ReaderGlobalFieldInventoryTest`、新增 `ReaderGlobalScopeNoticeWiringGuardTest`、`ReaderSettingsSheetTest`（追加 Compose 用例） |
| P0/P1/P2/P3 | **0 / 0 / 1 / 4**（P2 = `brightness` 措辞风险） |
| 未确认项 | 6 项（U1-U6），其中 U2/U3/U4 需产品先裁决 |
| SEAM REQUEST | **无**（条件性 3 项，均非必需，本轮不申请） |
| 报告路径 | `docs/coordination/designs/agent-design-d02b-zcode.md` |

**本轮为只读调查与设计，未实施任何代码改动，未运行真机，未领取下一任务。**
