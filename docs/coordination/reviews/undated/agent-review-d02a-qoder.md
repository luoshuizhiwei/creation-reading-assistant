# D-2-A-QA 独立复核报告 — Qoder

> 复核 Agent：Qoder　|　声明模型：`qwen3.8Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 复核开始时 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 被复核报告：`docs/coordination/agent-results/agent-result-d02a-zcode.md`
> 前置调查：`docs/coordination/investigations/agent-investigation-d02-zcode.md`
> 本报告路径：`docs/coordination/reviews/undated/agent-review-d02a-qoder.md`
> **结论：代码轴 PARTIAL**（功能契约 10/10 成立；1 项当前可达的用户可见文案 P2）
> 状态：等待 Codex 主任务复核。**未领取下一任务。**

---

## 1. 独立结论

**PARTIAL。**

我**不采信** Zcode 报告中的任何数字。以下全部由我独立读码 + 独立跑测得出。

任务书列出的 10 条预期契约，**逐条在源码层与 JVM 行为层成立**：

| # | 契约 | 独立判定 | 我的证据形式 |
|---:|---|---|---|
| 1 | 两个引擎字段成为真正的 per-book 覆盖项 | ✅ | `ReaderOverride.kt:36,43` 枚举 + `read`/`write` 分支；`ReaderSettingsRouterTest` 16 项 |
| 2 | BOOK 只写本书覆盖、全局不变、别书不受影响 | ✅ | `ReaderSettingsRouter.kt:69-95` 直读 + `PerBookSettingsStoreTest` 20 项 |
| 3 | GLOBAL 写全局并清除本书对应覆盖 | ✅ | 同上 `overridesToClear = touched` |
| 4 | 旧书无覆盖时继续跟随全局、不迁移、不自动写键 | ✅ | `ReaderOverride.kt:160-164` 叠加语义直读（`raw == null → acc`） |
| 5 | 两键仅接受 `off` / `auto` / `on` | ✅ | `PAGER_ENGINE_MODE_VALUES`（`ReaderOverride.kt:130`）+ `write` 的 `takeIf` |
| 6 | `auto` 仍受进程级 `PagerHealthStore` 闸门约束 | ✅ 未回退 | `ReaderPagerEngineState.kt` 哈希未变（见 §2），该文件逻辑未被触碰 |
| 7 | 视觉预设不改全局引擎、不清本书引擎覆盖 | ✅ | `ReadingPresets.applyTo` + `applyPresetToBook`；`ReadingPresetsTest` 17 项 |
| 8 | 「全部跟随全局」仍清全部覆盖（含引擎） | ✅ | `PerBookSettingsStore.kt:62` 遍历 `entries` |
| 9 | 「恢复全局默认」重置全部可覆盖项（含引擎），不动设备级全局、不动别书覆盖 | ✅ | `resetAllOverridableKeys` + `updateReader`；22 个枚举项全为排版/显示类，设备级字段**不在**其中（§5 逐项核对） |
| 10 | 其余 26 个设备/交互字段作用域未被顺手重构 | ✅ | `ReaderOverrideKey` 共 22 项，未新增设备级项；`ReaderSettingsRouter.kt`/`SettingsStore.kt` 哈希逐字节未变 |

**判 PARTIAL 而不是 PASS 的唯一理由**：`ReaderSettingsCommon.kt:491-494` 的「恢复全局默认」确认对话框
把项目数从 20 改成了插值 `${ReaderOverrideKey.entries.size}`（现渲染为 22），但**列举内容只列了 18 个名字**，
漏掉 `headerLeft` / `headerRight` / `footerLeft` / `footerRight` 四项，并且把两个**引擎兼容模式**
仍然框定为「排版与显示相关的 22 项」。这是**当前用户可达、且本轮刚被改动的文案**，
属真实的产品契约不一致，不是纯未来维护风险 → Spec 轴 P2。

**该 P2 不影响功能正确性**（实际清理范围由 `resetAllOverridableKeys` 决定，与文案无关），
一行文案即可修复，因此结论是 PARTIAL 而非 FAIL。

**本轮无 SEAM REQUEST 被我判定为越界**：Zcode 声明的 8 个改动文件全部在任务书边界内，
且我用哈希独立证明了 4 个「本应零改动」的文件确实零改动（§2）。

---

## 2. 实际改动归因（不把整份 `git diff` 归给 D-2-A）

### 2.1 `/tmp/d02a` 快照存在，归因可以闭环

```
/tmp/d02a/files.txt        12 行
/tmp/d02a/before.sha256    12 条
/tmp/d02a/after.sha256     12 条
/tmp/d02a/*.after.bak      3 个（ReaderOverride / ReadingPresets / PerBookSettingsStore）
```

三份清单的**文件集合完全一致**（我逐行比对，非仅比条数）：4 个授权产品文件 + 4 个授权测试文件
+ 4 个「声明零改动」文件。**无缺失、无多余、无换名。**

### 2.2 我独立复算的哈希（命令在**仓库根**执行）

```bash
sha256sum -c /tmp/d02a/after.sha256    # 12/12 OK
sha256sum -c /tmp/d02a/before.sha256   # 8 FAILED（= 本轮改动的 8 个文件）+ 4 OK
```

**必须记录我自己的一个操作错误**：我第一次在 `android/` 目录下执行了 `sha256sum -c`，
清单里是**仓库根相对路径**，于是 12 条全部报 `FAILED open or read`。这是**我的命令错误**，
不是快照失效，也不是 Zcode 造假；切到仓库根重跑后 12/12 OK。

那 4 条 `OK`（即「当前内容 == 改动前内容」）正是：

| 文件 | 结果 |
|---|---|
| `data/settings/ReaderSettingsRouter.kt` | **未改动** |
| `data/settings/SettingsStore.kt` | **未改动** |
| `ui/screen/reader/ReaderSettingsEditApplier.kt` | **未改动** |
| `ui/screen/reader/ReaderPagerEngineState.kt` | **未改动** |

⇒ Zcode §9.3 的「四文件逐字节一致」**独立成立**。这一点很重要：契约 6（`auto` 健康闸门未回退）
和契约 2/3（路由语义）之所以能被判定为「未破坏」，正是因为这 4 个文件根本没动。

### 2.3 `ReaderOverride.kt` 的 diff **不能**整体归给 D-2-A

```bash
git diff origin/main -- .../ReaderOverride.kt   # +41 −? 行
```

其中**只有 14 行**与本轮引擎契约有关（2 个枚举项 + 2 个 `read` 分支 + 2 个 `write` 分支 +
`PAGER_ENGINE_MODE_VALUES` + 相邻 KDoc/注释）。余下 27 行（纸张选项、屏幕方向、
`SHOW_PROGRESS` 标签文案等）是 D-2-A **之前**工作区既有的其他 Agent / 用户资产。
**我未把这些归因给 D-2-A**，也没有独立证据支持「它们属于 D-2-A」。

同一份 `+41` 与 Zcode §9.4 的 before/after 哈希不冲突：哈希证明「改动前后差了什么」，
但改动前的状态本身已相对 `origin/main` 有偏移。**两者不可混用。**

### 2.4 文件 mtime 与本轮时段一致

`android/app/src/{main,test}/java/.../data/settings/` 与
`sheets/ReaderSettingsCommon.kt` 中，mtime 落在 2026-09-21 20:26–20:35 窗口的
**恰好**是声明的 8 个文件；其余同目录文件 mtime 更早。

### 2.5 仓库状态

```
git diff --cached --name-only  = 0
git stash list                 = 0
HEAD                           = 5aed593
git status --porcelain         = 344 行（海量既有用户资产，本报告未触碰）
```

---

## 3. BOOK / GLOBAL 路由复核

`ReaderSettingsRouter.plan`（本轮**零改动**，见 §2.2）：

```kotlin
val touched = changedOverridableKeys(displayed, edited)
val mustStayGlobalBacked = when (scope) {
    ReaderSettingsScope.BOOK   -> OVERRIDABLE
    ReaderSettingsScope.GLOBAL -> OVERRIDABLE - touched
}
val nextGlobal = ReaderOverrideKey.mask(base = edited, maskFrom = global, keys = mustStayGlobalBacked)
```

`OVERRIDABLE = ReaderOverrideKey.entries.toSet()` ⇒ 两个引擎枚举项**天然**进入可覆盖集合，
`changedOverridableKeys` 又按 `key.read(displayed) != key.read(edited)` 逐字段比较，
所以 `pagerEngineMode` / `epubPagerEngineMode` 的改动**一定会被 touched 捕获**。

| 作用域 | 引擎键进 `overridesToSet` | 引擎键进 `plan.global` | 引擎键进 `overridesToClear` | 全局是否被写 |
|---|---|---|---|---|
| `BOOK` | ✅ | ❌（`mask` 把全部 `OVERRIDABLE` 掩回全局原值） | ❌（`emptySet()`） | ❌ |
| `GLOBAL` | ❌（`emptyMap()`） | ✅ | ✅（`touched`） | ✅，且本书覆盖被删 |

**关键结论**：GLOBAL 分支的 `overridesToClear = touched` 天然包含引擎键 ⇒
契约 3「写全局后必须清掉本书覆盖，否则旧覆盖会遮蔽新全局值」由**既有通用机制**保证，
不需要、也确实没有为引擎写特例。这是本轮改动面小的根本原因，也是我认为设计上正确的地方。

`ReaderSettingsEditApplier.kt`（零改动）把 `plan` 落成 `setOverride` / `clearOverride` / `updateReader`，
`setOverride` 的合法性校验（§7）与预设写入路径共用同一判据。

**我的独立测试**：`ReaderSettingsRouterTest` **16 tests / 0 / 0 / 0**。

---

## 4. 两本书隔离与旧数据兼容

**键命名**（`PerBookSettingsStore.kt`）：

```kotlin
private fun overrideKey(bookId: String, key: ReaderOverrideKey) =
    stringPreferencesKey("book_reader_override_${bookId}_${key.id}")
```

`key.id` 逐字等于 `ReaderSettings` 字段名 ⇒ 本书引擎键为
`book_reader_override_<bookId>_pagerEngineMode` / `..._epubPagerEngineMode`，
**没有第二套命名**，与既有 20 个覆盖键同一口径。

**隔离链**：`effectiveReader(bookId)` = `ReaderSettingsOverlay.apply(全局, snapshotOverrides(bookId))`。
`apply` 内部 `ReaderOverrideKey.entries.fold` 只对该书**存在**的键生效，
因此 book-A 写引擎覆盖**在结构上无法**影响 book-B（快照集合按 bookId 前缀取，B 的快照里没有 A 的键）。

**旧数据兼容（契约 4）**：`ReaderOverride.kt:160-164` 的叠加对 `raw == null` 直接返回累加值 ⇒

- 无覆盖键 = 跟随全局，**不需要迁移**；
- 打开旧书不会触发任何写路径（`effectiveReader` 只读）⇒ **不自动写键**；
- 非法落盘值经 `sanitize` 丢弃后同样回落全局。

**我的独立测试**：`PerBookSettingsStoreTest` **20 tests / 0 / 0 / 0**，
`ReaderSettingsOverlayTest` **23 tests / 0 / 0 / 0**。

**UNVERIFIED**：真实 DataStore 磁盘上的既有 `book_reader_override_*` 键分布我无法在本机抽查
（未连设备、未 pull 用户数据，任务书禁止）。「旧书零迁移」我证明的是**代码路径**，不是**存量数据快照**。

---

## 5. 预设 / 全部跟随全局 / 恢复默认：三种语义是否真的分开了

| 语义 | 入口 | 重置集合 | 含引擎？ | 作用对象 |
|---|---|---|---|---|
| 视觉预设 | `ReadingPresets.applyTo`（全局）/ `PerBookSettingsStore.applyPresetToBook`（本书） | `PRESET_OWNED_KEYS` = `entries - ENGINE_KEYS` | ❌ 否 | 全局展示值 / 本书覆盖 |
| 全部跟随全局 | `PerBookSettingsStore.clearAllOverrides` | `ReaderOverrideKey.entries` | ✅ **是** | 本书覆盖 |
| 恢复全局默认 | `PerBookSettingsStore.resetGlobalReaderToDefaults` → `resetAllOverridableKeys` | `entries.toSet()` | ✅ **是** | **仅全局** |

三者现在由**不同的键集合入参**表达（`resetKeys(current, keys)` 把「重置哪些」变成显式参数），
不再共用一个隐式的「所有枚举项」。这是本轮除新增枚举外最有价值的结构改进。

**契约 9 的两条「不得」我逐项核对了**：

- *不得清设备级全局*：22 个枚举项为
  `FONT_SIZE, LINE_HEIGHT, PARAGRAPH_SPACING, PAGE_MARGIN, BACKGROUND, FONT_BOLD,
  CHINESE_TYPOGRAPHY, TRADITIONAL_CHINESE, CUSTOM_FONT_PATH, READER_MODE,
  PAGER_ENGINE_MODE, EPUB_PAGER_ENGINE_MODE, PAGE_TURN_EFFECT, IMMERSIVE, SHOW_READER_INFO,
  SHOW_PROGRESS, AUTO_HIDE_SECONDS, HEADER_LEFT, HEADER_RIGHT, FOOTER_LEFT, FOOTER_RIGHT,
  SCREEN_ORIENTATION`（`grep -n '^    [A-Z_]*(' ReaderOverride.kt` 实测 22 条）。
  `brightness`、`volumeKeyPaging`、`eyeCare*`、`tapZoneMode`、`keepAwake`、`autoPageSpeed`、
  `pageTurnSpeed`、TTS 相关字段**全部不在**该枚举 ⇒ `resetKeys` 结构上碰不到它们。
  而 `resetGlobalReaderToDefaults` 只走 `settingsStore.updateReader { … }`，
  写入范围被限制在 reader 对象内部。
- *不得清其他书的覆盖*：该函数不触碰任何 `book_reader_override_*` 键（无 `bookId` 参数）。

**我的独立测试**：`ReadingPresetsTest` **17 tests / 0 / 0 / 0**。

**当前唯一的契约不一致就在这一档**：对话框文案（`ReaderSettingsCommon.kt:489-498`）

```kotlin
"作用范围：**全局**。会把排版与显示相关的 ${ReaderOverrideKey.entries.size} 项" +
"恢复为出厂默认值（字号、行距、段距、页边距、纸张、粗细、中文排版、繁简、" +
"字体、阅读模式、TXT/Markdown 分页兼容模式、EPUB 分页兼容模式、" +
"翻页效果、沉浸、页眉页脚、进度条、菜单隐藏、屏幕方向）。…"
```

- 数字 22（插值）↔ 名字 18 ⇒ **漏列页眉左侧、页眉右侧、页脚左侧、页脚右侧**；
- 「排版与显示相关的 22 项」现在包含两类**分页引擎兼容模式**，措辞把引擎改动包装成了外观改动。

**最小修复片（一行字符串）**：要么补齐 22 个名字，要么改为
「会把 **22 项**排版、显示与阅读引擎设置恢复为出厂默认值（字号、行距 … 、屏幕方向 等 22 项）」，
并把两个引擎项从「排版与显示」的定语里摘出来单列。同一串里 `**全局**` 的 Markdown 星号会被
`Text` 原样渲染（§10 P3-①，属既有问题，一并顺手改）。

---

## 6. 专项：`PRESET_OWNED_KEYS = entries - ENGINE_KEYS` 是否成立

任务书要求我**不得因为「三个内置预设没有引擎值」就跳过该问题**。我的结论分四层。

### 6.1 作为「当前实现」：成立

派生集合确实等于本轮语义所需的 20 个视觉键（我按 §5 的 22 项清单手工减去 2 个引擎项核对过）。
新增**视觉**键自动纳入（保持「预设清得干净」的既有语义），
新增**引擎/兼容**键必须显式进 `ENGINE_KEYS`。三个内置预设的 `values`
只含 `FONT_SIZE` / `LINE_HEIGHT` / `BACKGROUND`，无引擎值。

### 6.2 作为「分类机制」：是一个**开放式补集**，方向不安全

减法定义把**默认归属**判给了「预设负责」：

```text
新键没被任何人分类  →  自动落入 PRESET_OWNED_KEYS  →  预设会静默重置它
```

而正确的默认应该是「未分类 = 不许动」。也就是说，**未来一个「按书覆盖但不是视觉项」的键
（例如某类兼容开关、按书权限、按书同步标记）如果忘记加进 `ENGINE_KEYS`，
会被视觉预设静默重置，而且没有任何测试会失败** ——
因为 §6.3 的那条防线测的是公式而不是分类。这不是假设性洁癖：
`ReaderOverrideKey` 从 20 涨到 22 就是本轮发生的，增长率 10%，且**本轮正是靠人工判断**
才把两个新键放对了边。

### 6.3 作为「测试防线」：`the preset owned key set covers every non engine overridable key` 只有一半价值

`ReadingPresetsTest.kt:154-161` 的期望值写成

```kotlin
ReaderOverrideKey.entries.toSet() - setOf(PAGER_ENGINE_MODE, EPUB_PAGER_ENGINE_MODE)
```

被测表达式是 `entries - ENGINE_KEYS`。当 `ENGINE_KEYS` 增删一个键时，**两边同步变化** ⇒ 恒绿。
所以它**钉住的是「实现没被改成第三种公式」**（例如有人把 `PRESET_OWNED_KEYS` 直接写回 `entries`，
Zcode 的变异 A 就是这种，该测试确实红了，我不否认这一点），
但它**钉不住「某个键该不该被预设拥有」** —— 后者才是 §6.2 的风险，而它恰好管不到。

真正咬得住引擎漂移的是**行为**那条：`applying any preset leaves the pager engine modes untouched`
（三个预设 × 两个引擎字段）。但它同样只保护**已被点名**的两个键，对新键无保护。

⇒ **这条测试不算循环论证，但算「同义反复」，其长期价值被 Zcode §6 的说法（"最有价值的长期防线"）高估了。**

### 6.4 `ENGINE_KEYS` 这个名字是否够：不够

名字声称的是「引擎」，语义承担的却是「预设不得触碰的一切」。这两者今天重合，明天不重合。
一旦需要第二个排除类别，只有两种坏结局：把不相关的键塞进 `ENGINE_KEYS`（名字撒谎），
或再补一个 `XXX_KEYS` 并从减法里再减一次（补集越长越没人读）。

### 6.5 是否可达「自定义 `ReadingPreset` 写入 / 重置引擎」——按任务书要求实测过可达性

```kotlin
data class ReadingPreset(          // 公开 data class
    val id: String, val label: String, val description: String,
    val values: PerBookOverrides,  // Map<ReaderOverrideKey, String>
)
```

- 类型本身公开，`applyPresetToGlobal` / `applyPresetToBook` 都以它为入参 ⇒ **API 层可构造**；
- 但 `grep -rn "ReadingPreset(" android/app/src/main android/app/src/test` 只命中
  `ReadingPresets.kt` 内的 1 处声明 + 3 处内置实例化；预设选择器遍历 `ReadingPresets.ALL`；
- 即：**当前生产与测试代码中不存在自定义预设的调用点**，`values` 也不含引擎键。

⇒ 按任务书的分级规则（"如果风险只影响未来新增键，判 P3；如果当前已有调用能构造自定义
ReadingPreset 并写入/重置引擎，按实际可达性判 P2 或 P3"），
**我判 P3**：唯一需要「当前可达」支撑的路径不存在调用点。
但必须登记，因为下一片（D-2-B）或任何新增预设都会立刻让它变成真问题。

### 6.6 建议（更安全的形态，按性价比排序）

1. **首选：把所有权写进枚举自身。** `enum class ReaderOverrideKey(val id: String, val label: String, val ownedByPreset: Boolean)`，
   `PRESET_OWNED_KEYS = entries.filter { it.ownedByPreset }.toSet()`。
   新增键**必须在构造点回答**「预设是否拥有我」——把「忘记分类」从静默错误变成编译错误。
   这也让 `ENGINE_KEYS` 这个名字失去必要性（不再靠减法表达）。
2. **次选（零 API 变更）：显式白名单 + 穷举一致性测试。**
   `PRESET_OWNED_KEYS = setOf(20 个名字)`，另设 `PRESET_UNOWNED_KEYS = ENGINE_KEYS`，
   并把现有同义反复测试换成**不可两边同变**的形式：

   ```kotlin
   assertEquals(entries.toSet(), PRESET_OWNED_KEYS + PRESET_UNOWNED_KEYS)
   assertTrue(PRESET_OWNED_KEYS.intersect(PRESET_UNOWNED_KEYS).isEmpty())
   ```

   新键未分类 ⇒ 第一条断言失败；同时分类冲突会被第二条抓住。
3. 无论选哪种，都应加一条**「预设不拥有任何非排版/显示键」**的意图断言
   （按 label 或按显式 category，而非按枚举名硬编码）。
4. `ENGINE_KEYS` / `PRESET_OWNED_KEYS` 的 `public` 可见性可收窄
   （`internal` 或只提供查询函数）：公开集合把「补集」这个实现细节固化成了 API。

---

## 7. API 与并发审查

**事务合并（真实改进）**：`applyPresetToBook` 由「`clearAllOverrides` + 逐项 `setOverride`（N+1 次写）」
改为**单个 `ds.edit { }`**：

```kotlin
ds.edit { prefs ->
    ReadingPresets.PRESET_OWNED_KEYS.forEach { prefs.remove(overrideKey(bookId, it)) }
    preset.values.forEach { (key, value) ->
        if (key.write(ReaderSettings(), value) != null) prefs[overrideKey(bookId, key)] = value
    }
}
```

`DataStore.edit` 的 transform 对整份偏好原子应用 ⇒ 消除了旧实现「已清旧覆盖、尚未写完新覆盖」
的可观察中间态（此前该窗口内 `effectiveReader` 可能读到「既无预设值也无覆盖」的空档）。
这是行为等价的收敛，不是行为变更。

**校验口径统一**：预设写入复用了 `setOverride` 的同一判据 `key.write(ReaderSettings(), value) != null`，
同一 store 内**没有第二套校验**。代价是每次判据都新分配一个 `ReaderSettings()`
（纯 data class、字段有默认值，成本可忽略；登记为风格 P3，不要求改）。

**空 bookId**：`applyPresetToBook` 起始 `if (bookId.isBlank()) return`，与其他入口一致。

**未新增公共 API 表面积**：新增 4 个符号（2 枚举项、`ENGINE_KEYS`、`PRESET_OWNED_KEYS`、
`resetAllOverridableKeys`），`resetKeys` 保持 `private`。`resetAllOverridableKeys` 的引入
把「恢复默认」从「借用视觉预设」里独立出来，是**减少**耦合而不是增加。

**Kotlin object 初始化顺序**：`ENGINE_KEYS`（:66-69）声明在 `PRESET_OWNED_KEYS`（:88）之前，
且后者由前者派生。这是真实约束（object 属性按声明顺序初始化，反序会 `must be initialized`），
Zcode §2.2 如实记录了踩到并修正。我核对了当前顺序**正确**。风险：该约束**无测试保护**，
只有注释；若有人重排声明会编译失败而非静默错，所以不是缺陷（登记 P3）。

---

## 8. 我独立运行的测试（全部 `--rerun`，工作目录 `android/`）

### 8.0 前置：FQCN 由我自行解析，不抄 Zcode

```bash
grep -H '^package' .../data/settings/{ReaderSettingsOverlayTest,ReaderSettingsRouterTest,PerBookSettingsStoreTest,ReadingPresetsTest}.kt
grep -H '^class'   .../ui/viewmodel/SettingsViewModelTest.kt
```

历史教训（我在 S07-Q 与 F2.2 都踩过）：`--tests` **零匹配时 Gradle 仍打印 BUILD SUCCESSFUL、退出码 0**。
因此下文每条都给出「请求类数 vs 实际 XML 文件数 vs 零匹配」。

### G1 — 四个定向类

```bash
./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.data.settings.ReaderSettingsOverlayTest" \
 --tests "com.creationreadingassistant.data.settings.ReaderSettingsRouterTest" \
 --tests "com.creationreadingassistant.data.settings.PerBookSettingsStoreTest" \
 --tests "com.creationreadingassistant.data.settings.ReadingPresetsTest"
```

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 构建结果 | `BUILD SUCCESSFUL` |
| 请求类数 / XML 文件数 | **4 / 4** |
| 零匹配 / 缺失类 | **0 / 0** |
| tests / failures / errors / skipped | **76 / 0 / 0 / 0** |

| 类 | tests | F | E | S |
|---|---:|---:|---:|---:|
| `ReaderSettingsOverlayTest` | 23 | 0 | 0 | 0 |
| `ReaderSettingsRouterTest` | 16 | 0 | 0 | 0 |
| `PerBookSettingsStoreTest` | 20 | 0 | 0 | 0 |
| `ReadingPresetsTest` | 17 | 0 | 0 | 0 |

### G2 — `data.settings.*` 全包回归

```bash
./gradlew --console=plain :app:testDebugUnitTest --rerun --tests "com.creationreadingassistant.data.settings.*"
```

| 项 | 值 |
|---|---|
| 退出码 | **0**（重跑后；首次因构建目录争用失败，见 §8.4） |
| 请求通配 / 实际类数 | 1 通配 → **19 类 / 19 个 XML** |
| 零匹配 | **0** |
| tests / failures / errors / skipped | **191 / 0 / 0 / 0** |

19 类清单（我逐个点名，非只报总数）：`PerBookSettingsStoreTest`, `ReaderDayNightToggleTest`,
`ReaderDefaultsMigrationTest`, `ReaderSettingsDefaultsTest`, `ReaderSettingsOverlayTest`,
`ReaderSettingsRouterTest`, `ReadingPresetsTest`, `SearchHistoryStoreTest`,
`SelectionActionStoreTest`, `SelectionActionsTest`, `SettingsStoreBootMirrorTest`,
`SettingsStoreTest`, `SettingsStoreTxtTocMigrationMarkerTest`, `ShelfPrefsTest`,
`SyncPrefsStoreTest`, `ContinueReadingStoreTest`, `DurationUnitTest`, `GoalStoreClampTest`,
`ImportHistoryStoreTest`。

⇒ 本轮改动**没有波及**同 package 的既有测试（`SettingsStoreTest`、`ReaderDefaultsMigrationTest`
尤其关键，它们代表「全局键名不变」与「旧数据不迁移」）。

### G3 — `SettingsViewModelTest`

```bash
./gradlew --console=plain :app:testDebugUnitTest --rerun --tests "com.creationreadingassistant.ui.viewmodel.SettingsViewModelTest"
```

退出码 **0**；请求 1 类 / XML 1 个 / 零匹配 0；**8 tests / 0 / 0 / 0**。
该类 mock 了 `applyPresetToBook`，签名从多次写改到单事务后**未出现 mock 失配**。

### G4 — 生产编译

```bash
./gradlew --console=plain :app:compileDebugKotlin --rerun
```

退出码 **0**，`BUILD SUCCESSFUL in 3m 48s`（`--rerun` ⇒ 非 UP-TO-DATE 假绿）。

### 8.5 汇总（我自己的数字，与 Zcode 无关）

| 组 | 类 | tests | F | E | S | 退出码 |
|---|---:|---:|---:|---:|---:|---:|
| G1 定向 | 4 | **76** | 0 | 0 | 0 | 0 |
| G2 package 回归 | 19 | **191** | 0 | 0 | 0 | 0 |
| G3 VM | 1 | **8** | 0 | 0 | 0 | 0 |
| G4 编译 | — | — | — | — | — | 0 |

**我未做变异测试**（任务书禁止改产品/测试代码）；我改用**独立读码 + 哈希归因 + 独立跑测**替代，
因此**不背书** Zcode §9.5 的变异结果，只声明我的绿灯与其声明不冲突。

### 8.6 取证细节与我的操作失误（如实记录）

1. **XML mtime**：G2 后结果目录内 19 个 XML 全部为 `2026-09-21 20:56:35`，
   与我的运行时刻一致 ⇒ 非陈旧文件；其中四个定向类计数与 G1（20:51）**完全相同**（23/16/20/17），
   两次独立运行互为复算。
2. **`SettingsViewModelTest` 的 XML 现已不在结果目录**：Gradle 每次 `testDebugUnitTest`
   会重写整个 `test-results/testDebugUnitTest/`，G2 在 G3 之后运行并删除了它。
   这是 Gradle 行为，不是我记错数字；我的 G3 计数在**其自身运行后立刻**读取，退出码 0 也已记录。
3. **首次 G2 失败**：`:app:compileDebugKotlin FAILED` + `Could not load from local cache:
   Unable to delete directory … New files were found … a process is still writing`，
   且失败路径指向 `ui/screen/shelf`（**不属于本轮任何授权文件**）。
   判定为**与并发 Agent 的 Gradle 构建目录争用**，非代码失败；重跑即通过。
4. **`sha256sum -c` 的错误工作目录**：见 §2.2，我的错误，已纠正。
5. **CRLF**：`git diff --numstat` 对本目录文件输出 `\ No newline at end of file` 与 CRLF 警告。
   我**未**声称"无空白问题"，只声明 `git diff --cached` 为空。

---

## 9. Standards 轴（工程质量 / 可维护性 / 测试纪律）

**P0 = 0　P1 = 0　P2 = 0　P3 = 5**

| 编号 | 级别 | 问题 | 证据 |
|---|---|---|---|
| **ST-1** | P3 | **`PRESET_OWNED_KEYS` 采用开放式补集，默认把新键判给「预设拥有」** ⇒ 未来未分类的非视觉键会被静默重置。应改为枚举自带 `ownedByPreset` 或显式白名单 + 不可两边同变的穷举断言（§6.2 / §6.6） | `ReadingPresets.kt:88` |
| **ST-2** | P3 | **`the preset owned key set covers every non engine overridable key` 是同义反复**：期望值与被测值同为「entries 减引擎键」，公式变更可捕获、分类错误不可捕获。被报告称为"最有价值的长期防线"，**高估** | `ReadingPresetsTest.kt:154-161` |
| **ST-3** | P3 | **`ENGINE_KEYS` 命名窄于语义**（承担「预设不得触碰的一切」）。今日与「引擎」重合，新增排除类别时必然撒谎或叠加减法 | `ReadingPresets.kt:66-69` |
| **ST-4** | P3 | **`ENGINE_KEYS` / `PRESET_OWNED_KEYS` 公开可见**：把减法实现细节固化为公共 API，外部可绕过 `applyTo` 直接消费集合 | 同上 |
| **ST-5** | P3 | **风格与微小成本**：`ReaderOverride.kt` 两个 `write` 分支间有 2 处多余空行（`cat -A` 实测）；合法性判据每次分配临时 `ReaderSettings()`；`ENGINE_KEYS` 先于 `PRESET_OWNED_KEYS` 的声明顺序约束**只有注释、无测试**（重排会编译失败，故非缺陷） | `ReaderOverride.kt:96-101` |

**Standards 最严重项：ST-1（P3）。**
理由：它是**唯一会随时间自然变坏**的问题，且本轮 20→22 的增长正好演示了它依赖人工判断。
ST-2 是它的直接共犯（防线测的是公式而非分类）。
但两者都**只影响未来新增键**，按任务书规则判 P3，不阻断 D-2-A。

方法学正面记录：`ReaderSettingsRouter.kt` / `SettingsStore.kt` / `ReaderSettingsEditApplier.kt` /
`ReaderPagerEngineState.kt` **零改动**（§2.2 独立证明），说明改动面被真正压到了枚举层；
`resetKeys(current, keys)` 把隐式全量重置改成显式入参，方向正确。

---

## 10. Spec 轴（用户可感知行为 / 产品契约 / 数据正确性）

**P0 = 0　P1 = 0　P2 = 1　P3 = 4**

| 编号 | 级别 | 问题 | 证据 |
|---|---|---|---|
| **SP-1** | **P2** | **「恢复全局默认」确认对话框与实际清理范围不符**：项目数渲染为 22，列举内容只有 18 个名字，漏 `页眉左侧 / 页眉右侧 / 页脚左侧 / 页脚右侧`；且把两类**分页引擎兼容模式**框定在「排版与显示相关的 22 项」里，弱化了「这会改掉你为这本书选的引擎」的严重性。这是**当前用户每次点恢复默认都会看到**的文案 | `ReaderSettingsCommon.kt:491-494`；22 项枚举清单 vs 18 个名字（§5） |
| **SP-2** | P3 | 同一串里 `作用范围：**全局**` 的 Markdown 星号会被 `Text` **原样渲染**成 `**全局**`。**既有问题**：`git show origin/main:…ReaderSettingsCommon.kt \| grep -c '\*\*全局\*\*'` = 1，非 D-2-A 引入，但本轮改了这条文案，顺手修的成本≈0 | 基线与当前均命中 |
| **SP-3** | P3 | `ReadingPresets.DEFAULT` 的 `description` 仍按「排版预设」口吻描述；其现在会清 20 项（含本轮未纳入预设的两个新键之外的所有视觉项），语义边界未随 `PRESET_OWNED_KEYS` 显式化而复核 | `ReadingPresets.kt:23-29` |
| **SP-4** | P3 | 契约 6（`auto` + 进程级健康闸门）**行为未变但无专项用例**：`ReaderPagerEngineState.kt:150-158` 未被修改、其读取的是**有效**设置，因此覆盖值天然享受同一闸门；但「本书 `auto` + `isAutoDisabled`」的组合无 JVM 断言（该文件不在授权测试范围）。属**推理成立、证据缺位** | Zcode U6 + 我直读该文件未变（§2.2） |
| **SP-5** | P3 | 高级设置页两处引擎开关**未标注「可按书覆盖」**；BOOK/GLOBAL 作用域选择器对这两个字段的可见语义未变。用户不知道同一开关在不同作用域下含义不同 | 未改 UI 布局（授权边界外，属登记非缺陷） |

**Spec 最严重项：SP-1（P2）。**
判定依据：它**不是**数据正确性问题（实际重置范围由 `resetAllOverridableKeys` 决定，与文案无关），
但它是**本轮刚被改动的、每次触发都会展示给用户的确认对话框**，
且内容恰好是「会改哪些设置」这一**唯一用户决策依据**。数字与清单不符 + 引擎被归入「排版与显示」，
属真实的产品契约不一致，因此按「有当前可达 P2 ⇒ PARTIAL 或 FAIL」的规则给出 **PARTIAL**。

**数据正确性**：无问题。落盘键命名与既有 20 键同一口径；非法值不写盘；
GLOBAL 写必清本书覆盖（不会遮蔽）；BOOK 不产生全局差异（不会串全局）。

---

## 11. 是否建议进入真机

**建议：先做 SP-1 的一行文案修复（可与 D-2-A 收尾同片），再进入真机。**

理由：真机验收要验的正是「BOOK 改引擎只影响本书」「套预设后引擎覆盖存活」「恢复默认对话框说什么」——
第三条**就是** SP-1 本身。带着已知错误的确认文案进真机，
会让验收者要么把错文案判成缺陷（返工一次）、要么判成"看起来正常"（固化一次）。
修复成本 1 行字符串，不值得排队两轮。

真机必须覆盖、JVM 无法证明的项：

| # | 项 | 当前状态 |
|---|---|---|
| R1 | 高级设置页把 TXT/Markdown 引擎从 `auto` 改成 `off`，作用域选「只改本书」→ 退出重进：本书为 `off`、**别书仍为 `auto`**、全局仍为 `auto` | UNVERIFIED |
| R2 | 同一操作作用域选「全局」→ 本书覆盖消失并跟随新全局值（不被旧覆盖遮蔽） | UNVERIFIED |
| R3 | 本书设引擎覆盖后套用「大字号」/「护眼」→ 引擎选择**保持不变**、排版改变 | UNVERIFIED |
| R4 | 「全部跟随全局」清掉引擎覆盖；「恢复全局默认」不改动亮度/点击区域/TTS 且不删别书覆盖 | UNVERIFIED |
| R5 | 旧书（无覆盖键）升级后行为不变，不出现「引擎突然变 off」 | UNVERIFIED（存量数据未抽查，§4） |
| R6 | EPUB 与 TXT 各自使用**自己的**引擎覆盖，互不串台 | UNVERIFIED |

**未做**：真机、MuMu、`adb`、`androidTest`、`:app:lintDebug`、APK 打包 —— 全部未运行（任务书禁止或未要求）。

---

## 12. 未覆盖项（我这边）

| # | 项 | 说明 |
|---|---|---|
| Q1 | Zcode §9.5 的三个变异 | **未复现**（须改代码，任务书禁止）。我只声明我的独立绿灯与其不冲突 |
| Q2 | 契约 6 的 `auto` × 健康闸门组合 | **未测**（`ReaderPagerEngineState` 不在授权测试文件内）；仅直读确认逻辑未变 + 该文件哈希未变 |
| Q3 | Compose 对话框实际渲染 | **未测**：JVM 无 Compose 运行时。SP-1 的结论来自源码字符串与枚举清单差集，非截屏 |
| Q4 | 存量用户 DataStore 键分布 | **未抽查**（需设备/pull，任务书禁止） |
| Q5 | `ui.viewmodel.*` / `ui.screen.reader.*` 全量 | **未跑**（本轮只需 `SettingsViewModelTest`；D-2-A 未触碰这两个 package 的产品代码） |
| Q6 | Lint / APK / release 构建 | **未运行** |
| Q7 | D-2-B 的 26 个设备/交互字段 | **未评估**（任务书明令不处理）。我只核对了「本轮没有改变它们的作用域」，未审查 D-2-B 的方案 |
| Q8 | 并发写竞态（同一本书同时被套预设与手改覆盖） | **未测**：`ds.edit` 单事务内原子，但两个 `edit` 之间仍按 DataStore 串行；无专项用例 |

---

## 13. 边界遵守声明

| 约束 | 结果 |
|---|---|
| 唯一工作仓库为 recovery 仓库 | ✅ 全程只读该仓库；**未进入** `D:\develop\Code\Codex\creation-reading-assistant` 作任何读/写（该路径只是 shell 默认 cwd，未对其执行任何命令） |
| 除本报告外不修改任何文件 | ✅ 新增 1 个文件：`docs/coordination/reviews/undated/agent-review-d02a-qoder.md`。产品代码、测试代码、Zcode 报告、看板、TODO 均未改 |
| 不修改产品/测试代码、不"顺手修一下" | ✅ 未改；SP-1 只作为建议写在 §5 |
| 不 reset / checkout / clean / stash / stage / commit / push | ✅ 未执行；复核结束时 `git diff --cached` = 0、`git stash list` = 0、HEAD 仍 `5aed593` |
| 不做真机测试、不使用 MuMu | ✅ 未执行任何 `adb` 或设备命令 |
| 不处理 D-2-B、不处理 A01、不领取 S08–S20 | ✅ 未触碰 |
| 不修改数据库 / Room schema / DAO | ✅ 未触碰 |
| 不修改 Zcode 报告 | ✅ 只读 |
| 不读取/修改 `D:\Application\文档\Obsidian\Claude`、不改 Codex Obsidian | ✅ 未访问 |
| 工作区他人资产 | ✅ 344 行既有未提交改动**未回退、未覆盖、未格式化、未整理**；未使用 `git add -N`；未跟踪文件用「清单 + SHA-256」取证 |
| 只运行 JVM 定向测试 | ✅ G1–G4，全部 `--rerun`；未跑全量 JVM、未跑 lint/APK |
| 数字独立性 | ✅ 本报告全部计数为**我本次实测**，未引用 Zcode 数字作为证据；我的操作失误（`sha256sum` 错误目录、首次 G2 构建争用）已如实记录在 §2.2 / §8.6 |
| JVM 通过 ≠ 真机通过 | ✅ 已在 §11 单列 6 项真机未验证 |

---

## 14. 回传

- **代码轴**：**PARTIAL**
- **最小修复片（1 行）**：`ReaderSettingsCommon.kt:491-494` 对话框文案 —— 补齐 22 项名字或改述为「等 22 项」，
  并把两类分页引擎兼容模式从「排版与显示」定语中摘出；同一串顺带去掉 `**全局**` 的 Markdown 星号
- **独立测试**：G1 4 类 / **76** tests；G2 19 类 / **191** tests；G3 1 类 / **8** tests；G4 `compileDebugKotlin`
  —— 四组**退出码全部 0**，failures / errors / skipped 全部 **0**；请求类数 == XML 文件数，零匹配 0
- **Standards**：P0 0 / P1 0 / P2 0 / **P3 5**（最严重 ST-1 `PRESET_OWNED_KEYS` 开放式补集）
- **Spec**：P0 0 / P1 0 / **P2 1** / P3 4（最严重 SP-1 恢复默认对话框文案与 22 项实际范围不符）
- **`PRESET_OWNED_KEYS` 专项结论**：**当前实现正确，分类机制不安全 → P3**。
  风险只影响未来新增键（已实测：`ReadingPreset` 虽是公开 data class，但全仓仅 3 处内置实例化、
  无自定义预设调用点，因此不构成当前可达 P2）。现有同名测试属**同义反复**，
  钉住公式而非分类；`ENGINE_KEYS` 命名窄于语义。建议把所有权写进枚举（`ownedByPreset`）
  或改显式白名单 + 不可两边同变的穷举断言
- **是否建议进入真机**：**先修 SP-1 那一行文案，再进真机**；真机清单 6 项（R1–R6）JVM 无法覆盖
- **报告路径**：`docs/coordination/reviews/undated/agent-review-d02a-qoder.md`
- **未领取下一任务**：D-2-B、A01、S08–S20 与真机验收均保持 HOLD，等待 Codex 主任务复核
