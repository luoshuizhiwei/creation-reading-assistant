# D-2-A：分页引擎兼容模式的 per-book override — Zcode 执行报告

> 执行 Agent：Zcode　|　实际模型：`deepseek v4.1Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收工 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 前置调查：`docs/coordination/investigations/agent-investigation-d02-zcode.md`
> 报告路径：`docs/coordination/agent-results/agent-result-d02a-zcode.md`
> 状态：**等待 Codex 主任务复核**（未领取下一片）

---

## 1. 结论

**D-2-A 完成。** 两个分页引擎兼容模式已成为**真正的** per-book 覆盖项：

| 目标 | 结果 |
|---|---|
| BOOK 作用域修改只写本书覆盖、不污染全局 | ✅ 已实现并有测试 |
| GLOBAL 作用域写全局并清除本书覆盖 | ✅ 已实现并有测试 |
| 无覆盖的旧书继续跟随全局、不迁移、不自动写键 | ✅ 已实现并有测试 |
| 合法值 `off/auto/on`，非法值拒绝回退 | ✅ 已实现并有测试 |
| `auto` 仍受进程级 `PagerHealthStore.isAutoDisabled` 约束 | ✅ 未改动该机制 |
| 视觉预设不静默重置引擎 | ✅ 已实现并有测试 |
| `applyPresetToBook` 不再无条件 `clearAllOverrides` | ✅ 已实现并有测试 |
| 「全部跟随全局」仍清全部覆盖（含引擎） | ✅ 已实现并有测试 |
| 「恢复全局默认」显式重置全部可覆盖项（含引擎） | ✅ 已实现并有测试 |
| 对话框项目数 20→22 并明确列出两类分页兼容模式 | ✅ 已实现 |
| 预设自身不新增引擎值 | ✅ 未新增 |
| 其余 26 个设备/交互字段作用域未变 | ✅ 有回归护栏测试 |

**关键实现判断**：`ReaderSettingsRouter.kt`、`ReaderSettingsEditApplier.kt`、
`ReaderPagerEngineState.kt`、`SettingsStore.kt` **全部零改动** ——
它们本就以 `ReaderOverrideKey` 枚举为准，枚举扩展后自然得到正确行为。
这四个文件的哈希与改动前**逐字节一致**（见 §8），是对「最小修复」最直接的证明。

**本轮无 SEAM REQUEST**（见 §10）。

---

## 2. 红测证据

### 2.1 RED 方式

先写测试、后写实现。RED 表现为**编译失败**（引用的生产符号尚不存在），
这是最直接的「契约先于实现」证据。

```
cd android && ./gradlew --console=plain :app:compileDebugUnitTestKotlin
```

退出码 **1**，`BUILD FAILED`，共 20+ 条错误，全部指向本轮新增契约：

```
e: .../PerBookSettingsStoreTest.kt:193:54 Unresolved reference 'PAGER_ENGINE_MODE'.
e: .../PerBookSettingsStoreTest.kt:213:53 Unresolved reference 'PAGER_ENGINE_MODE'.
e: .../PerBookSettingsStoreTest.kt:214:53 Unresolved reference 'EPUB_PAGER_ENGINE_MODE'.
e: .../PerBookSettingsStoreTest.kt:298:51 Unresolved reference 'PRESET_OWNED_KEYS'.
e: .../ReaderSettingsOverlayTest.kt:211:59 Unresolved reference 'PAGER_ENGINE_MODE'.
e: .../ReaderSettingsOverlayTest.kt:212:63 Unresolved reference 'EPUB_PAGER_ENGINE_MODE'.
e: .../ReaderSettingsRouterTest.kt:... Unresolved reference 'PAGER_ENGINE_MODE'.（同类多条）
e: .../ReadingPresetsTest.kt:... Unresolved reference 'PRESET_OWNED_KEYS'.（同类多条）
```

**RED 的根因**：`ReaderOverrideKey.PAGER_ENGINE_MODE` / `EPUB_PAGER_ENGINE_MODE` /
`ReadingPresets.PRESET_OWNED_KEYS` / `ReadingPresets.resetAllOverridableKeys` 尚未存在 ——
这正是 D-2-A 要建立的四项契约。

### 2.2 RED 的补充证据（实现中途的真实失败）

实现后首轮编译又暴露一处真实问题（如实记录）：

```
e: ReadingPresets.kt:72:89 Variable 'ENGINE_KEYS' must be initialized.
```

原因：`PRESET_OWNED_KEYS` 由 `ENGINE_KEYS` 派生，但我把它声明在前，
而 Kotlin `object` 属性**按声明顺序**初始化。已调整声明顺序并加注释说明该约束。

### 2.3 最终绿测

```
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.data.settings.ReaderSettingsOverlayTest" \
 --tests "com.creationreadingassistant.data.settings.ReaderSettingsRouterTest" \
 --tests "com.creationreadingassistant.data.settings.PerBookSettingsStoreTest" \
 --tests "com.creationreadingassistant.data.settings.ReadingPresetsTest"
```

退出码 **0**，`BUILD SUCCESSFUL in 27s`。详见 §8。

---

## 3. 最终实现

### 3.1 `ReaderOverride.kt`：新增两个枚举项

在 `READER_MODE` 之后、`PAGE_TURN_EFFECT` 之前插入（保持「阅读模式 → 引擎 → 翻页」的语义相邻）：

```kotlin
PAGER_ENGINE_MODE("pagerEngineMode", "分页引擎（TXT/Markdown）"),
EPUB_PAGER_ENGINE_MODE("epubPagerEngineMode", "分页引擎（EPUB）"),
```

- `id` **逐字等于** `ReaderSettings` 的字段名（`pagerEngineMode` / `epubPagerEngineMode`），
  与 `ReaderOverrideKey` 其余项的既有口径一致，不创建第二套命名；
- 新增 `read` / `write` 分支，分别写 `settings.pagerEngineMode` / `settings.epubPagerEngineMode`；
- 新增合法值集合 `PAGER_ENGINE_MODE_VALUES = setOf("off", "auto", "on")`
  （与 `SettingsStore` 落盘口径一致），两键共用；
- 非法值走 `raw.takeIf { it in ... }` → `null` → 调用方保留原值，
  **不做近似修正**（`"ON"` / `"true"` / `"maybe"` / 空串均被拒）。

`ReaderSettingsOverlay.sanitize` 与 `apply` 无需改动 —— 它们遍历枚举，
新增项自动获得「脏值丢弃」「非法项跳过」的既有保护。

### 3.2 `ReadingPresets.kt`：把「预设负责」与「全部可覆盖」拆开

新增两个公开只读集合（最小 API）：

```kotlin
val ENGINE_KEYS: Set<ReaderOverrideKey> = setOf(
    ReaderOverrideKey.PAGER_ENGINE_MODE,
    ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE,
)

val PRESET_OWNED_KEYS: Set<ReaderOverrideKey> = ReaderOverrideKey.entries.toSet() - ENGINE_KEYS
```

- **`ENGINE_KEYS` 声明在前**：`PRESET_OWNED_KEYS` 由它派生，Kotlin object 属性按声明顺序初始化；
- `PRESET_OWNED_KEYS` = 原 20 个排版/显示键，**不再是** `ReaderOverrideKey.entries`；
- 由枚举**派生**而非硬编码 20 个名字：新增**视觉**项自动纳入（保持既有语义），
  新增**引擎/兼容**项必须显式加进 `ENGINE_KEYS`（否则被一致性测试拦下）。

`applyTo` 改为只重置 `PRESET_OWNED_KEYS`；新增显式 `resetAllOverridableKeys`：

```kotlin
fun applyTo(current, preset) = ReaderSettingsOverlay.apply(
    resetKeys(current, PRESET_OWNED_KEYS), preset.values)      // 视觉预设：不碰引擎

fun resetAllOverridableKeys(current) = resetKeys(current, ReaderOverrideKey.entries.toSet())  // 恢复全局默认：含引擎
```

私有 `resetKeys(current, keys)` 取代原 `resetOverridableKeys(current)`，
把「重置哪些键」变成**显式入参**而不是隐式的「所有枚举项」。

### 3.3 `PerBookSettingsStore.kt`：预设与恢复默认各走各的语义

`applyPresetToBook` 不再无条件 `clearAllOverrides`：

```kotlin
ds.edit { prefs ->
    ReadingPresets.PRESET_OWNED_KEYS.forEach { prefs.remove(overrideKey(bookId, it)) }
    preset.values.forEach { (key, value) ->
        if (key.write(ReaderSettings(), value) != null) prefs[overrideKey(bookId, key)] = value
    }
}
```

- 只清**预设负责的**覆盖键 → 引擎覆盖存活；
- 保留 `setOverride` 的「非法值不落盘」校验口径（`key.write(...) != null`），
  避免同一个 store 里出现两套校验标准；
- 改为单次 `ds.edit` 事务：旧实现是 `clearAllOverrides` + 逐项 `setOverride`（多次写），
  现在一次事务完成，顺带消除「中途失败留下半清状态」的窗口。

`resetGlobalReaderToDefaults` 改用显式重置：

```kotlin
settingsStore.updateReader { ReadingPresets.resetAllOverridableKeys(this) }
```

旧实现走 `ReadingPresets.applyTo(this, ReadingPresets.DEFAULT)` —— 那是**视觉预设**路径。
改用 `resetAllOverridableKeys` 后，「恢复默认会不会重置引擎」是一个**显式决定**，
而不是「碰巧取决于预设键集合怎么算」。

### 3.4 `ReaderSettingsCommon.kt`：对话框文案

项目数已由 `${ReaderOverrideKey.entries.size}` 自动从 20 变 22（无需改代码），
但**列举内容**必须同步，否则文案与行为不符。已插入两项：

```
字体、阅读模式、TXT/Markdown 分页兼容模式、EPUB 分页兼容模式、
翻页效果、沉浸、页眉页脚、进度条、菜单隐藏、屏幕方向
```

---

## 4. 两个引擎键的读写与作用域矩阵

| 维度 | `PAGER_ENGINE_MODE` | `EPUB_PAGER_ENGINE_MODE` |
|---|---|---|
| 枚举 id | `pagerEngineMode` | `epubPagerEngineMode` |
| 写入的字段 | `ReaderSettings.pagerEngineMode` | `ReaderSettings.epubPagerEngineMode` |
| 合法值 | `off` / `auto` / `on` | `off` / `auto` / `on` |
| 非法值 | `write` 返回 null，覆盖不落盘、叠加时回退全局 | 同左 |
| 消费方 | `ReaderPagerEngineState`（`epubBook == null` 分支，TXT **与 Markdown** 共用） | `ReaderPagerEngineState`（`epubBook != null` 分支） |
| DataStore 键 | `book_reader_override_<bookId>_pagerEngineMode` | `book_reader_override_<bookId>_epubPagerEngineMode` |
| 在 `PRESET_OWNED_KEYS` | ❌ 否 | ❌ 否 |
| 在 `ENGINE_KEYS` | ✅ 是 | ✅ 是 |

**作用域矩阵**（`ReaderSettingsRouter.plan`，代码未改，靠枚举扩展自然生效）：

| 作用域 | 引擎项进 `overridesToSet` | 引擎项进 `plan.global` | 引擎项进 `overridesToClear` | 全局是否因此产生差异 |
|---|---|---|---|---|
| `BOOK` | ✅ 对应键 | ❌ 保持全局原值 | ❌ | ❌ 否（`globalDiffersFrom` 为 false） |
| `GLOBAL` | ❌ | ✅ 新值 | ✅ 对应键 | ✅ 是 |

**预设矩阵**：

| 操作 | 全局引擎 | 本书引擎覆盖 | 预设负责项 |
|---|---|---|---|
| `applyPresetToGlobal(LARGE_FONT/EYE_CARE/DEFAULT)` | ❌ 保持 | ❌ 保持 | ✅ 按预设重置 |
| `applyPresetToBook(LARGE_FONT/EYE_CARE)` | ❌ 保持 | ✅ **保留** | ✅ 清旧覆盖 + 写预设值 |
| `applyPresetToBook(DEFAULT)` | ❌ 保持 | ✅ **保留** | ✅ 清除 |
| `clearAllOverrides`（全部跟随全局） | ❌ 保持 | ✅ **删除** | ✅ 删除 |
| `resetGlobalReaderToDefaults`（恢复全局默认） | ✅ **恢复结构默认** | ❌ 保持 | ✅ 恢复结构默认 |

---

## 5. 两本书隔离证据

测试 `a book engine override is isolated from another book`（`PerBookSettingsStoreTest`）：

```
book-A 写入 PAGER_ENGINE_MODE = "off"
  → effectiveReader(book-A).pagerEngineMode == "off"        （自己的覆盖生效）
  → effectiveReader(book-B).pagerEngineMode == 全局值        （book-B 未被污染）
  → snapshotOverrides(book-B)[PAGER_ENGINE_MODE] == null     （book-B 无覆盖键）
```

配套 `a book engine override never changes the global value`：

```
book 写入两个引擎覆盖后
  → store.reader 的 pagerEngineMode 保持改动前的全局值
  → store.reader 的 epubPagerEngineMode == ReaderSettings().epubPagerEngineMode
```

两者合起来证明：**BOOK 作用域的引擎改动既生效于本书，又完全不外溢**。

---

## 6. 预设保护证据

| 测试 | 断言 | 结果 |
|---|---|---|
| `the preset owned key set excludes the pager engine keys` | 两键均不在 `PRESET_OWNED_KEYS` | PASS |
| `the preset owned key set covers every non engine overridable key` | `PRESET_OWNED_KEYS` **恰好等于** `entries - ENGINE_KEYS`（不多不少） | PASS |
| `applying any preset leaves the pager engine modes untouched` | 三个预设 × 两个引擎字段，全部保持调用前的 `off`/`on` | PASS |
| `applying a preset still resets the keys it owns` | 字号/行距/纸张仍被重置，引擎保持不变 | PASS |
| `applying a preset keeps device level settings untouched` | 亮度、点击区域在三个预设下均不变 | PASS |
| `applying a preset to a book preserves its engine overrides` | 本书已有引擎覆盖 + 套用大字号 → 引擎覆盖存活，字号按预设写入 | PASS |
| `applying the default preset to a book keeps engine overrides` | 套用「默认」→ 引擎覆盖存活，纸张覆盖被清 | PASS |
| `applying the default preset still clears every preset owned override` | 套用「默认」后剩余覆盖中**没有**任何 `PRESET_OWNED_KEYS` | PASS |
| `reset global defaults restores both engine modes and keeps device settings` | 全局两引擎恢复结构默认，亮度 70 保留 | PASS |

**「不多不少」那条（`the preset owned key set covers every non engine overridable key`）
是本轮最有价值的长期防线**：它同时防住两个方向 ——
漏项（预设清不干净旧覆盖）与多项（预设误碰引擎）。

---

## 7. 恢复默认与全部跟随全局的语义

三种语义现已**显式分开**，不再共用同一个「重置所有枚举项」的隐式实现：

| 语义 | 入口 | 实现 | 作用范围 |
|---|---|---|---|
| **视觉预设** | `ReadingPresets.applyTo` / `applyPresetToGlobal` / `applyPresetToBook` | `resetKeys(current, PRESET_OWNED_KEYS)` | 20 个视觉键；**不含**引擎 |
| **全部跟随全局** | `PerBookSettingsStore.clearAllOverrides` | 遍历 `ReaderOverrideKey.entries` 删键 | 当前书**全部**覆盖；**含**引擎 |
| **恢复全局默认** | `PerBookSettingsStore.resetGlobalReaderToDefaults` | `ReadingPresets.resetAllOverridableKeys` → `resetKeys(current, entries.toSet())` | 全局**全部**可覆盖项；**含**引擎 |

「全部跟随全局」的行为**未改变**（仍是清全部），并有测试钉住：
`clear all overrides still removes the engine overrides`。

「恢复全局默认」的对话框：项目数由 `${ReaderOverrideKey.entries.size}` 自动从 20 变 22，
列举内容已补入两类分页兼容模式（§3.4）。该文案无法由 JVM 测试断言（Compose 文本），
属**已编译未执行**，见 §11。

---

## 8. 测试命令、退出码及 XML 统计

全部在 `android/` 下执行。

### 8.1 任务书要求的定向测试

```bash
./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.data.settings.ReaderSettingsOverlayTest" \
 --tests "com.creationreadingassistant.data.settings.ReaderSettingsRouterTest" \
 --tests "com.creationreadingassistant.data.settings.PerBookSettingsStoreTest" \
 --tests "com.creationreadingassistant.data.settings.ReadingPresetsTest"
```

**退出码 0**，`BUILD SUCCESSFUL in 27s`。

| 测试类 | 改前 | 改后 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|---:|---:|
| `data.settings.ReaderSettingsOverlayTest` | 16 | **23** | 23 | 0 | 0 | 0 |
| `data.settings.ReaderSettingsRouterTest` | 11 | **16** | 16 | 0 | 0 | 0 |
| `data.settings.PerBookSettingsStoreTest` | 11 | **20** | 20 | 0 | 0 | 0 |
| `data.settings.ReadingPresetsTest` | 10 | **17** | 17 | 0 | 0 | 0 |
| **合计（4 类）** | **48** | **76** | **76** | **0** | **0** | **0** |

**请求 4 类 == XML 4 个文件；缺失类 0；零匹配 0。**
XML 路径：`android/app/build/test-results/testDebugUnitTest/TEST-com.creationreadingassistant.data.settings.*.xml`
（XML mtime 20:36:01，与运行时刻一致，非陈旧文件。）

### 8.2 编译

```bash
./gradlew --console=plain :app:compileDebugKotlin --rerun
```

**退出码 0**，`BUILD SUCCESSFUL in 1m 4s`（`--rerun` 确保真实执行，非 UP-TO-DATE）。

### 8.3 附加回归（同一 package 全量）

```bash
./gradlew --console=plain :app:testDebugUnitTest --rerun --tests "com.creationreadingassistant.data.settings.*"
```

**退出码 0**：

| 项 | 数 |
|---|---:|
| 测试类 | 19 |
| tests | 191 |
| failures | **0** |
| errors | 0 |
| skipped | 0 |

（19 类 > 4 类，是因为该 package 还含 `SettingsStoreTest`、`SelectionActionSettingsTest`、
`ReaderDayNightToggleTest`、`ReaderDefaultsMigrationTest` 等既有测试 —— 一并验证未被我改动波及。）

另单独验证了 `ui.viewmodel.SettingsViewModelTest`（它 mock 了 `applyPresetToBook`）：
**8 tests / 0 failures**。

### 8.4 未运行

`:app:lintDebug`、APK 打包、`androidTest` 执行、真机 / 模拟器 —— 均未运行（任务书未要求）。
本轮**不要求真机**，未安装 APK，未使用 MuMu。

---

## 9. 修改文件清单

### 9.1 产品文件（4 个，全部在授权边界内）

| 文件 | 改动 |
|---|---|
| `data/settings/ReaderOverride.kt` | +2 枚举项、+2 `read` 分支、+2 `write` 分支、+1 合法值集合 `PAGER_ENGINE_MODE_VALUES`、+KDoc |
| `data/settings/ReadingPresets.kt` | +`ENGINE_KEYS`、+`PRESET_OWNED_KEYS`、+`resetAllOverridableKeys`、`applyTo` 改用 `PRESET_OWNED_KEYS`、`resetOverridableKeys` → `resetKeys(current, keys)` |
| `data/settings/PerBookSettingsStore.kt` | `applyPresetToBook` 改为只清预设负责键（单次事务）、`resetGlobalReaderToDefaults` 改用显式重置 |
| `ui/screen/reader/sheets/ReaderSettingsCommon.kt` | 恢复默认对话框列举内容补入两类分页兼容模式（1 处字符串） |

### 9.2 测试文件（4 个，全部在授权边界内）

| 文件 | 新增用例 |
|---|---:|
| `test/.../ReaderSettingsOverlayTest.kt` | +7（引擎键读写/合法值/拒绝/交叉字段/叠加/回退/sanitize） |
| `test/.../ReaderSettingsRouterTest.kt` | +5（BOOK×2、BOOK 全局无差异、GLOBAL、设备项回归护栏） |
| `test/.../PerBookSettingsStoreTest.kt` | +9（隔离×2、旧数据兼容、非法不落盘、预设保护×3、全部跟随全局、恢复默认） |
| `test/.../ReadingPresetsTest.kt` | +7（预设键集合×2、预设不碰引擎、仍重置自有键、设备项不变、恢复默认×2） |

**未新增测试文件**（4 个既有文件的追加已覆盖全部 10 条契约）。

### 9.3 明确未修改（哈希逐字节一致）

| 文件 | before | after | 判定 |
|---|---|---|---|
| `data/settings/ReaderSettingsRouter.kt` | `d6e6de3d8e0cc12e` | `d6e6de3d8e0cc12e` | **IDENTICAL** |
| `data/settings/SettingsStore.kt` | `b9eb035ee41938a9` | `b9eb035ee41938a9` | **IDENTICAL** |
| `ui/screen/reader/ReaderSettingsEditApplier.kt` | `f85ad026e022b04c` | `f85ad026e022b04c` | **IDENTICAL** |
| `ui/screen/reader/ReaderPagerEngineState.kt` | `ea29e7886424cb4d` | `ea29e7886424cb4d` | **IDENTICAL** |

这直接证明任务书「不需要修改」的判断成立：**枚举扩展后，路由 / 应用器 / 运行时消费 /
全局键名全部自然得到正确行为**。

### 9.4 before / after 完整哈希（授权文件）

| 文件 | before(前16) | after(前16) |
|---|---|---|
| `main/.../ReaderOverride.kt` | `fa61c39f3c8b5bc0` | `69d55677fb48961e` |
| `main/.../ReadingPresets.kt` | `43ea9867dd9f0d25` | `1c655b9bff4f2aa6` |
| `main/.../PerBookSettingsStore.kt` | `acb2565087f6734c` | `bef8c822c52c6616` |
| `main/.../ReaderSettingsCommon.kt` | `0f927fd1dc6babc4` | `c2580fbb4d9d656c` |
| `test/.../ReaderSettingsOverlayTest.kt` | `e5e2a210c4d1fcd2` | `f1092e50e4d54929` |
| `test/.../ReaderSettingsRouterTest.kt` | `2ffa64239f7977de` | `383fefcdc6a3d981` |
| `test/.../PerBookSettingsStoreTest.kt` | `dd9e6814ff8a9fbc` | `c057d0f112d9e52b` |
| `test/.../ReadingPresetsTest.kt` | `dd305f8ff76c5545` | `023c3993f210b41f` |

快照存于仓库外 `/tmp/d02a/{files.txt,before.sha256,after.sha256}`。

### 9.5 变异验证（证明测试有效）

每次变异后从 `/tmp/d02a/*.after.bak` 还原并复核哈希。

| 变异 | 内容 | 失败用例 | 结果 |
|---|---|---|---|
| **A** | `PRESET_OWNED_KEYS` 退回 `ReaderOverrideKey.entries.toSet()`（即 D-2-A 之前的预设清理范围） | `applying a preset to a book preserves its engine overrides`、`applying the default preset to a book keeps engine overrides`、`the preset owned key set covers every non engine overridable key`、`the preset owned key set excludes the pager engine keys`、`applying any preset leaves the pager engine modes untouched`、`applying a preset still resets the keys it owns` | **6 FAILED** ✅ |
| **B** | `applyPresetToBook` 恢复无条件 `clearAllOverrides(bookId)` | `applying a preset to a book preserves its engine overrides`、`applying the default preset to a book keeps engine overrides` | **2 FAILED** ✅ |
| **C** | `EPUB_PAGER_ENGINE_MODE.write` 交叉写到 `pagerEngineMode` 字段 | `the two pager engine keys write to their own ReaderSettings fields`、`pager engine keys accept exactly off auto and on` | **2 FAILED** ✅ |

**恢复证据**：三个产品文件的当前哈希与 `/tmp/d02a/*.after.bak` **逐字节一致**（RESTORED OK）；
残留扫描 `grep "clearAllOverrides(bookId)"` 仅命中其自身定义处（:60），
`grep "entries.toSet() - ENGINE_KEYS"` 命中 `ReadingPresets.kt:88`（正确实现）→ 无残留。

**补充说明**：首次尝试的「变异 A」是直接删除两个枚举项，结果**编译失败**
（因为 `ReadingPresets.ENGINE_KEYS` 引用了它们）。这本身是一条有价值的证据：
**两个键已成为其他代码的编译期依赖**，不可能被静默移除。随后改用编译有效的等价变异。

---

## 10. SEAM REQUEST

**无。**

本轮全部改动落在 4 个授权产品文件 + 4 个授权测试文件内。
`ReaderSettingsRouter.kt`、`ReaderSettingsEditApplier.kt`、`ReaderPagerEngineState.kt`、
`SettingsStore.kt` 经测试证明**无需修改**，未越界。

---

## 11. 未覆盖项

| # | 项 | 状态 | 说明 |
|---:|---|---|---|
| U1 | 恢复默认对话框的**实际文案渲染** | **已编译未执行** | 文案改动在 Compose 层，JVM 无法断言。已确认项目数由 `entries.size` 自动变 22、列举内容已补两项；实际渲染需真机或 instrumentation |
| U2 | 高级设置页引擎开关的**作用域标注** | **未做** | 本轮只让「只改本书」变成真的，**未**在 UI 上标注「该开关可按书覆盖」。若产品希望显式提示，属后续 UI 工作 |
| U3 | 真机上「BOOK 作用域改引擎 → 只影响本书」 | **未验证** | JVM 已证明路由/落盘/隔离，但 Compose 运行期与真实 DataStore 交互未跑 |
| U4 | 真机上「套用预设后引擎覆盖仍存活」 | **未验证** | 同上 |
| U5 | 已有用户此前用「本书」作用域改过的全局引擎值 | **未处理** | 那些全局值已被改动且**无法区分来源**，不做自动回改（任务书禁止迁移）。若需告知用户，属发版说明范畴 |
| U6 | `auto` 与进程级健康闸门的组合（per-book `auto` + `isAutoDisabled`） | **行为未变但无专项测试** | `ReaderPagerEngineState.kt:156-158` 未改，`auto -> !pagerHealth.isAutoDisabled` 天然对覆盖值同样生效；本轮未加专项用例（该文件不在授权测试范围） |
| U7 | 其余 26 个设备/交互字段的作用域 | **按裁决留待 D-2-B** | 本轮只加了回归护栏（`book scope keeps the other device level keys global as before`），未改其语义 |

---

## 12. 明确声明

**未处理 D-2-B**：其余 26 个设备/交互型全局字段（`brightness`、`volumeKeyPaging`、
`eyeCare*`、`tapZoneMode`、`keepAwake`、`autoPageSpeed`、`pageTurnSpeed` 等）
作用域**一字未改**。仅新增一条回归护栏测试断言它们仍是全局语义，
并断言这两个字段**不在** `ReaderOverrideKey` 中。

**未处理 S08–S20**：未触碰任何相关文件。**未处理 A01**。

**未修改**：数据库、Room schema、DAO、`SettingsStore` 全局键名（`pager_engine_mode` /
`epub_pager_engine_mode` 未变）、高级设置页面布局、`ReaderSettingsRouter.kt`、
`ReaderSettingsEditApplier.kt`、`ReaderPagerEngineState.kt`。

**未做数据迁移**：旧书无引擎覆盖键时继续跟随全局，不自动写键（有测试钉住）。

**未执行**：commit / push / stage / stash / reset / checkout / clean / 全仓格式化；
未安装 APK；未使用 MuMu；未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`。

**索引与分支状态**：

```
HEAD                          = 5aed5939600e491843e3c44453921bd6273a233c
git diff --cached --name-only = 0
git stash list                = 0
branch                        = codex/recover-worktree-20260920
```

工作区其他未提交修改均为**用户资产**，本轮未触碰、未回退、未整理。

---

## 13. 回传

- **D-2-A 是否完成**：完成
- **红测是否先失败及原因**：先失败。首次失败是**编译失败**（20+ 条 `Unresolved reference`），
  因为 `PAGER_ENGINE_MODE` / `EPUB_PAGER_ENGINE_MODE` / `PRESET_OWNED_KEYS` /
  `resetAllOverridableKeys` 四项契约尚未实现；实现中途另有 1 次真实失败
  （`ENGINE_KEYS` 初始化顺序），已修正
- **最终测试数量与退出码**：定向 4 类 / **76 项** / 0 失败 / 0 错误 / 0 跳过，**退出码 0**；
  同 package 回归 19 类 / 191 项 / 0 失败，退出码 0；`compileDebugKotlin` 退出码 0
- **修改的产品文件数 / 测试文件数**：**4 / 4**（均在授权边界内，无新增测试文件）
- **是否有 SEAM REQUEST**：**无**
- **报告路径**：`docs/coordination/agent-results/agent-result-d02a-zcode.md`

**等待 Codex 主任务复核，未领取下一片。**
