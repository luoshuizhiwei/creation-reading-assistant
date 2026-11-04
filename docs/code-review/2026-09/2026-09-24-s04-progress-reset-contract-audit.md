# S04 进度重置契约审查：「同步后将重置章节与阅读进度」是否成立

- **性质**：**只读代码审查**。未修改源码/测试，未运行 Gradle/ADB，未操作真机或用户书库，
  未执行 `commit / push / stage / reset / checkout / clean`。
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **HEAD**：`5aed5939600e491843e3c44453921bd6273a233c`
- **日期**：2026-09-24

### 证据分级

| 标记 | 含义 |
|---|---|
| **【源码事实】** | 本轮直接读到代码/调用点，可复核 |
| **【静态推断】** | 由代码路径推出，但依赖运行时条件（用户手势、分支选择） |
| **【未验证】** | 本轮无证据，不做判断 |

### 审查对象哈希（本轮亲自计算）

| 文件 | SHA-256 |
|---|---|
| `feature/library/ShelfImporter.kt` | `5120e75c269e14f3b68a2065151908460656d8752e64831caf398dea8d70f8e5` |
| `ui/screen/shelf/ShelfRoute.kt` | `25019af1d13c51b38a1b8b4c31b958d3a4fee01ccb0c1dfe0dd83ce9f2bdc7e7` |
| `ui/screen/shelf/BookActionSheet.kt` | `a4fbef6376897b2bdc429165f6c0409dfbf1aa92c815f12f02499ef319e75f38` |
| `ui/viewmodel/ReaderDocumentLoader.kt` | `44abebc053d7b421fdb0c7aa684c83f651ac0795d1705d41ae7d198421e234a7` |
| `data/local/entity/ReadingEntities.kt` | `af3703d11a15535be49e606a0bcb528fab02cb50396eacb8ff1e0e8bf9bdf241` |
| `data/local/dao/ReadingDao.kt` | `919b9a3eee1137d74f80d46417882d99ddb57867100d1cd43200bd39bb2eaebe` |

---

## 0. 结论先说

| 结论 | 等级 |
|---|---|
| **确认弹窗文案与代码不一致**：没有任何代码路径会「重置」阅读进度 | **【源码事实】** |
| 替换链路本身**不写** `reading_progress` | **【源码事实】** |
| 但「进度一定不变」**是错的** —— 阅读器**重新打开时**会把进度**改写**为按新正文夹取后的位置 | **【静态推断】**（依赖用户是否重新打开该书） |
| 因此真实契约既不是「重置」也不是「保留」，而是「**保留记录行、坐标按新正文重新映射**」 | **【静态推断】** |
| 两个入口在进度行为上**同源**（都经 `repairFileTyped`），差别只在 UI 文案与是否显式提示 | **【源码事实】** |

> **重点**：D 的方案已经正确指出「不得只凭 `ShelfImporter` 没有直接调用
> `ReadingProgressDao` 就断定进度一定不变」。本审查沿完整调用链复核后，
> **同意该判断并给出具体机制**（见 §3）。

---

## 1. 文案出处

**【源码事实】** `ui/screen/shelf/ShelfRoute.kt:643`：

```kotlin
"检测到 ${count} 本书${titleText}的源文件已被修改，是否重新同步正文？同步后将重置章节与阅读进度。"
```

- 弹窗标题「同步源文件更新？」、确认按钮「重新同步」、取消按钮「取消」。
- 由 `pendingSourceRefreshIds`（`ShelfRoute.kt:631`）驱动，仅在**书架来源更新**入口出现。

**另一入口没有等价文案。**【源码事实】`BookActionSheet.kt:308-310`：

```kotlin
title = "重新定位文件",
subtitle = if (readiness.tone == ReadinessTone.READY) "更换本地文件并保留阅读记录" else "修复缺失的本地正文",
```

⇒ 同一套替换逻辑，**两个入口给出互相矛盾的用户预期**：
来源更新说「将重置章节与阅读进度」，重新定位说「**保留**阅读记录」。

---

## 2. 完整调用链（两入口同源）

```
入口 A｜来源更新（书架下拉刷新 → 确认弹窗「重新同步」）
  ShelfRoute.kt:649  viewModel.confirmSourceRefresh(refreshIds) { … }
                     （另一处等价调用：ShelfRoute.kt:881 ShelfAction.ConfirmSourceRefresh）
  ShelfViewModel.kt:678  confirmSourceRefresh
    └─ ShelfViewModel.kt:691  importer.refreshFromSource(bookId)
         ShelfImporter.kt:261  refreshFromSource
           └─ ShelfImporter.kt:281  repairFileTyped(bookId, uri)
                ShelfImporter.kt:316-317  when(format) → reselectEpub / reselectPlainText
           ShelfImporter.kt:283  refreshSourceBaseline(ref, uri)      ← 写 library_source_refs
           ShelfImporter.kt:287  indexRefreshCoordinator.request(bookId) ← 异步重建搜索索引

入口 B｜重新定位文件（书卡菜单 → 选文件）
  BookActionSheet.kt:308-310  「重新定位文件」onRepair(book)
  ShelfRoute.kt:112  viewModel.reselectFile(bid, uri) { showMessage(it) }
                     （另一处等价调用：ShelfRoute.kt:882 ShelfAction.ReselectFileResult）
  ShelfViewModel.kt:406  reselectFile → importer.repairFile(bookId, uri)
  ShelfImporter.kt:242  repairFile = repairFileTyped(bookId, uri).message
    └─ 同一 repairFileTyped（无 refreshSourceBaseline、无索引重建）
```

**两入口都收敛到 `repairFileTyped`**（`ShelfImporter.kt:296`），
再分派到 `reselectEpub` / `reselectPlainText`。
**【源码事实】** 因此两入口的**正文替换与进度影响机制完全相同**，差异只在：
入口 A 额外推进来源基线 + 触发索引重建；UI 文案不同。

---

## 3. 替换链路本身是否写 `reading_progress`？—— 不写

**【源码事实】** 三条独立证据：

1. `grep -c "ReadingProgressDao" ShelfImporter.kt` = **0**（该类完全不出现在文件中）。
2. `ShelfImporter` 的构造函数注入项中**没有**任何 progress DAO。
3. `ReadingProgressDao`（`ReadingDao.kt:12-31`）**没有任何 delete / clear 方法** ——
   只有 `upsert` / `getByBook` / `getByBooks` / `observeAllActive` / `observeStatsRows` / `getDeleted`。
   ⇒ **「重置」在 DAO 层根本没有对应操作**。

全仓 grep `progressDao.delete` / `readingProgressDao.delete` / `clearProgress` / `resetProgress`
**零命中**。⇒ **没有任何代码会删除或清空阅读进度行。**

**⇒ 文案「重置章节与阅读进度」在字面意义上不成立。**【源码事实】

---

## 4. 但「进度一定不变」也不成立 —— 阅读器重开时会改写坐标

这是 D 提醒的关键点。沿调用链继续追到**阅读器重新加载**：

### 4.1 重新加载的入口

**【源码事实】** `ui/viewmodel/ReaderDocumentLoader.kt:54-58`：

```kotlin
suspend fun load(bookId: String): ReaderLoadedBook {
    val metadata = bookRepository.getById(bookId)
    val progress = readingProgressDao.getByBook(bookId)     // ← 读旧进度
```

EPUB 分支（`:67-68`）：

```kotlin
val chapterIndex = epubRepository.loadProgress(bookId)
    .coerceIn(0, book.chapters.lastIndex)                   // ← 按**新**章节数夹取
```

TXT/Markdown 分支（`:313-321` `resolveImportedTextOffset`）：

```kotlin
return (chapter.startOffset + legado.charOffset).coerceIn(0, textLength.coerceAtLeast(0))
```

⇒ 旧坐标被**按新正文的章节数 / 总长度夹取**（clamp）。

### 4.2 夹取后的值会不会写回？

**【静态推断】** 会 —— 两条写回路径都会把**夹取后的位置**当作"当前阅读位置"落库：

| 路径 | 位置 | 条件 |
|---|---|---|
| **分页（翻页）模式** | `ReaderLayerBuilders.kt:102-108`：`onPagedPositionChanged` → 先 `onPendingInitialPositionChange(false)` 打开闸门，再节流调用 `onPersistProgress()` | 用户在重开后的书上**翻一次页**（或跨章），且距上次落库 ≥5s |
| **滚动模式** | `ReaderProgressEffects.kt:192`：`if (!canPersistNormalReadingProgress(...)) return@collect`，闸门开启后 500ms 防抖落库 | 用户在重开后的书上**滚动**，且 `pendingInitialPosition` 已结算 |

闸门定义 **【源码事实】** `ReaderProgressEffects.kt:642-645`：

```kotlin
internal fun canPersistNormalReadingProgress(initialPositionPending: Boolean, temporaryInspection: Boolean) =
    !initialPositionPending && !temporaryInspection
```

`pendingInitialPosition` 在 `loadedBook != null` 时置 true
（`ReaderDocumentLoadEffects.kt:79`），随后由「恢复上次位置」的 effect 置 false
（`ReaderProgressEffects.kt:313` / `:331`，或分页模式 `ReaderLayerBuilders.kt:102`）。

⇒ **一旦初始定位结算完成，之后任何位置变化都会把「当前（已夹取）位置」写回 `reading_progress`。**
这正是「行还在，但内容已被改写」的机制。

### 4.3 后果

**【静态推断】** 若新正文比旧正文短（或章节数更少），用户重开该书并阅读后：

- `reading_progress` **行仍存在**（没有任何删除）；
- 但 `current_location_json` / `progress_percent` / `updated_at` 会变成
  **按新正文夹取后的坐标** —— 与替换前的值**不同**；
- 表现上接近用户所说的「回到某处」，但**不是**「重置到 0」：
  夹取只在越界时发生，未越界时坐标保持不变。

⇒ **文案「重置章节与阅读进度」与行为不符；但「进度保留」同样不准确。**

---

## 5. 两入口的精确差异

| 维度 | 入口 A：来源更新 | 入口 B：重新定位文件 |
|---|---|---|
| 收敛点 | `repairFileTyped` | `repairFileTyped`（同） |
| 正文替换 | 同 | 同 |
| 直接写 `reading_progress` | **否** | **否** |
| 重开后坐标夹取 | 同 | 同 |
| `refreshSourceBaseline`（写 `library_source_refs`） | **是**（`ShelfImporter.kt:283`） | 否 |
| 异步搜索索引重建 | **是**（`:287`） | 否 |
| UI 文案 | 「同步后将**重置**章节与阅读进度」 | `READY`：「更换本地文件并**保留**阅读记录」 |
| 用户预期 | 进度会丢 | 进度会留 |

**【源码事实】** 两入口对 `reading_progress` 的**实际影响完全相同**，
但**给用户的说法相反**。这是本次审查发现的核心不一致。

---

## 6. 产品选择（不自行实施）

文案要「与行为一致」，只有两条路，**必须选一条**：

### 选项 1：改文案（把话说对）

- **做法**：把入口 A 的「同步后将重置章节与阅读进度」改为如实描述，例如
  「正文将被替换；阅读记录会保留，但章节位置将按新正文重新对应，可能发生偏移」。
- **影响面（最小）**：**单个字符串** —— `ShelfRoute.kt:643`。
  无逻辑改动、无 schema 改动、无迁移。
- **可测试性**：可写一条纯 JVM 断言（若有文案常量抽取）或 androidTest 断言弹窗文本；
  真正的「进度到底变没变」**必须真机实测**（见 §7）。
- **风险**：低。仅文案。

### 选项 2：改行为（把事做对）

- **做法**：让「重置」变成真的 —— 替换成功后显式把 `reading_progress`
  置为章节 0 / 进度 0（或在替换前按书备份、失败可还原）。
- **影响面（大）**：需要新增 DAO 写入口（当前 DAO **没有** delete），
  改动 `ShelfImporter` 的替换链路，且必须同时决定：
  ① 是否对入口 B 也重置（否则两入口行为分叉）；
  ② 已读章节（`chapter_reads`）、高亮、笔记是否同步处理；
  ③ 失败回滚时进度如何还原。
- **可测试性**：JVM 可测（MockK 计数 `readingProgressDao.upsert` 被调用及参数），
  但「用户实际看到回到开头」仍需真机。
- **风险**：中～高。**这是产品决策，不是缺陷修复** —— 会主动丢弃用户的阅读位置。

> **建议**：若本轮目标只是消除「文案与行为不一致」，**选项 1** 成本最低且不引入新的数据丢失面。
> 选项 2 需要产品方明确「来源更新是否应当丢弃阅读位置」这一语义。
> **本报告不实施任何一项。**

---

## 7. 必须真机实测的部分（不得由源码替代）

**【未验证】** 以下只能实测，源码无法替代：

1. **替换后 `reading_progress` 行的字段级前后对比**：`progress_percent`、
   `current_location_json`、`updated_at` 三个字段在替换前后的**具体值**。
   **「行还在」不等于「进度未变」** —— 必须给字段级对照。
2. **重开该书并阅读后**上述字段是否再次变化（验证 §4.2 的写回路径真的触发）。
3. **夹取是否真的发生**：取决于新旧正文长度/章节数关系，需构造「新正文更短」的样本。
4. **两入口分别实测**：确认 A 与 B 的实际进度影响一致（本报告为静态同源推断）。
5. 高亮 / 笔记 / 已读章节在替换后的表现（本报告**未审查**，见 §8）。

---

## 8. 本报告未覆盖的部分

1. **`chapter_reads`（已读章节）**：未追踪替换后是否受影响。**【未验证】**
2. **高亮 / 笔记的坐标**：`highlights` / `notes` 存的是 source 坐标，
   正文替换后是否偏移**未审查**。**【未验证】**
3. **`book_files` / `library_source_refs` 的同步**：只确认了写入点存在，未验证一致性。
4. **`ReaderProgressCalibration`**（EPUB 百分比校准）：替换后 `estimatedChapterLengths`
   会随新正文变化，可能影响 `percent` 计算。**未展开**。**【未验证】**
5. **未运行任何测试**：本报告全部为静态阅读，**不含任何运行结果**。
6. **未真机验证**：见 §7。

---

## 9. 附：本轮为只读的证据

- 未修改任何源码/测试文件（`git status` 中本轮涉及的 6 个文件哈希见文首，均非本轮写入）。
- 未运行 Gradle / ADB / 模拟器；未接触用户书库。
- 未执行 `commit / push / stage / reset / checkout / clean`。
- 报告内**不含**真实书名、私人目录名或私有路径（示例一律用 `<bookId>` / 中性描述）。
- 唯一写入：本报告文件。
