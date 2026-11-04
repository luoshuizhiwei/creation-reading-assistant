# S04 EPUB `.backup` 残留守卫：错误路径的界面流向审查

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`；**HEAD**：`5aed5939600e491843e3c3c44453921bd6273a233c`
- **日期**：2026-09-25
- **性质**：**只读源码审查**。未修改产品源码或测试，未运行 adb / 安装 / instrumentation，
  未触碰真实书库、E 盘备份或原始仓库，未执行 `commit / push / stage / reset / checkout / clean`。
- **唯一新增文件**：本报告。
- **边界声明**：本文**不是**真机 UI 验收。所有「界面看起来如何」的结论都标注为
  **【静态推断】**，未经设备验证；真机 UI 行为另见
  `docs/qa/2026-09/2026-09-25-s04-epub-residue-qahost-device-acceptance.md`（该报告只覆盖 2 条
  instrumentation 方法，**不覆盖界面**）。

## 0. 证据分级

| 标记 | 含义 |
|---|---|
| **【源码事实】** | 直接读当前工作区源码 / 亲自 grep 得到，可复核 |
| **【静态推断】** | 由源码结构推断的界面效果，**未经真机验证** |
| **【UNKNOWN】** | 无足够证据，不判断 |

### 0.1 审查基线（本人亲自计算，SHA-256）

| 文件 | SHA-256 |
|---|---|
| `feature/reader/EpubRepository.kt` | `f3846ef7f2af78458ee8bdf4e302b12ae31edd1f47af331ad76c53d6ffc2214b` |
| `feature/library/ShelfImporter.kt` | `5120e75c269e14f3b68a2065151908460656d8752e64831caf398dea8d70f8e5` |
| `ui/viewmodel/ShelfViewModel.kt` | `dd567de0c12736ad0cdecc4106990e28be661e525a67536fd5ab40e884998adb` |
| `ui/screen/shelf/ShelfRoute.kt` | `7de972f60181ee913628978a556171f9c8b2a3229b49ba0084faae1b442eb6a8` |
| `ui/screen/shelf/BookActionSheet.kt` | `a4fbef6376897b2bdc429165f6c0409dfbf1aa92c815f12f02499ef319e75f38` |
| `ui/screen/homearchive/HomeCompletedScreen.kt` | `4385b64ccc7b6b9b4dcee03cf4bde003050fe281ffc23507bf1d1b6122c01a87` |
| `ui/screen/readinghistory/MyReadingRoute.kt` | `f7c5d53eafc4f7d710f3e6a8b2da0b15e4f0f30610bb06ef3f64117a3dff6a61` |
| `data/repository/BookRepository.kt` | `875e2c628accdaaf371913e08a7f68f84624fe571e3e5035df52aac8ff7699f1` |

---

## 1. 完整调用链（逐跳给出文件与行号）

### 1.1 起点：抛异常

**【源码事实】** `EpubRepository.kt:96-105`：

```kotlin
val backup = File(target.parentFile, "${stored.id}.backup")   // :92
if (backup.exists()) {                                        // :96
    throw IllegalStateException(
        "检测到未完成的替换残留（${backup.name}），本次替换已停止；" +
            "旧正文若仍存在，则未被本次操作修改，残留备份已保留。" +
            "重复重试不会自动恢复该备份",
    )
}
```

守卫在 `:96`，**先于** `EpubParser.parse`（`:119` 起）与一切 DAO 调用 ⇒ 抛异常时未落任何
临时文件、未写任何库表。TXT/MD 侧是**同形且同文案**的另一处守卫：
`ShelfImporter.kt:962-971`（路径 `books/<id>/<safeName>.backup`，见 `:948`/`:952`）。

### 1.2 中间层：异常被谁捕获

**【源码事实】** 唯一调用 `replaceStoredEpub` 的生产代码是
`ShelfImporter.reselectEpub`（`ShelfImporter.kt:923`），它在
`repairFileTyped` 的 `withContext(ioDispatcher)`（`:305`）内被调用（`:316`）。

**【源码事实】** `repairFileTyped` 的 catch（`ShelfImporter.kt:331-338`）把异常转成三样东西：

```kotlin
} catch (e: Throwable) {                                      // :331
    val msg = e.message ?: "修复失败"                          // :332
    _importTasks.value = ... it.copy(phase = "修复失败：$msg", status = "error")  // :334
    recordImport("unknown", "unknown", 0, "failed", bookTitle = existing.title, error = msg)  // :336
    RepairOutcome(false, "修复失败：$msg")                     // :337
}
```

⇒ **原始残留文案被原样保留**在 `msg` 中，前缀是「修复失败：」。`ok = false`。

### 1.3 两个入口

| | 入口 A：重新定位文件 | 入口 B：来源更新确认 |
|---|---|---|
| 公开方法 | `repairFile`（`ShelfImporter.kt:242`） | `refreshFromSource`（`:261`） |
| 结果形态 | **裸 String**（`.message`） | `SourceRefreshResult`（`:281-282`） |
| 视图模型 | `ShelfViewModel.reselectFile:406-407` / `BookOperationsViewModel.reselectFile:54-55` | `ShelfViewModel.confirmSourceRefresh:678-706` |

**【源码事实】** 入口 B 在 `:282` 显式分流：`if (!outcome.ok) return SourceRefreshResult.Failed(outcome.message)`
⇒ **不会被计入成功**。

---

## 2. 界面呈现逐项核对

### 2.1 入口 A（重新定位文件）：3 个调用点，反馈不一致

**【源码事实】** 全仓 `onRepair` 共 4 处（`ShelfRoute.kt:424`、`BookActionSheet.kt:310`、
`HomeCompletedScreen.kt:136`、`MyReadingRoute.kt:102`），最终汇入两个 `reselectFile`：

| 调用点 | 行号 | 回调 | 用户看到什么 |
|---|---|---|---|
| 书架页 | `ShelfRoute.kt:141` | `{ showMessage(it) }` | **Snackbar 完整文案**（含残留详情） |
| 书架页（动作分发器） | `ShelfRoute.kt:911` | `{ showMessage(it) }` | 同上 |
| 已读完成归档页 | `HomeCompletedScreen.kt:94` | **`{}`** | **什么都没有** |
| 我的阅读页 | `MyReadingRoute.kt:55` | **`{}`** | **什么都没有** |

**【源码事实】** `showMessage` = `snackbarHostState.showSnackbar(msg)`（`ShelfRoute.kt:123`），
**未传 `duration`** ⇒ 用 Compose 默认 `SnackbarDuration.Short`。

**【静态推断】** 书架页用户会看到一条约 90 字的 Snackbar，停留约 4 秒后自动消失，
**无操作按钮、无法回看**。文案较长，窄屏是否换行截断**未验证**。
**【源码事实】** 归档页与我的阅读页**丢弃了回调**，失败完全静默 —— 用户点了「重新定位文件」、
选完文件后界面**没有任何变化**，不会知道操作被拒绝。

### 2.2 入口 B（来源更新确认）：**残留详情被丢弃**

**【源码事实】** `ShelfViewModel.kt:690-705`：

```kotlin
for (bookId in targets) {
    when (val result = importer.refreshFromSource(bookId)) {
        is SourceRefreshResult.Success -> succeeded++                    // :692
        SourceRefreshResult.NoSourceRef -> unlocatable++                 // :693
        is SourceRefreshResult.Failed -> failedTitles += titleById[bookId] ?: "未知书籍"  // :694
    }
}
refreshSourceUpdates()                                                    // :697
val message = buildString {
    append("已同步 $succeeded 本书的源文件更新")                            // :699
    if (unlocatable > 0) append("；$unlocatable 本无法定位来源，请长按选择「重新定位文件」")
    if (failedTitles.isNotEmpty()) {
        append("；${failedTitles.size} 本刷新失败：${failedTitles.joinToString("、")}")  // :702
    }
}
```

**关键**：`:694` 只取书名，`Failed(message)` 里的 `message` **被丢弃**。用户看到的是
「已同步 0 本书的源文件更新；1 本刷新失败：《某书》」——**完全不知道失败原因是残留**，
也不知道「重试不会自动恢复」。残留文案本身只在 §2.3 的导入记录里才能看到。

### 2.3 残留详情实际可见的两个次要出口

**【源码事实】**

1. **导入记录**：`recordImport(..., error = msg)`（`:336`）→ `ImportHistorySheet`
   渲染 `entry.error` 为红色小字（`ShelfImportRoute.kt:804-812`，`maxLines = 2`，
   `overflow = Ellipsis`）。入口在 `ShelfRoute.kt:582-591`（`showImportHistory`）。
   **【静态推断】** 残留文案约 90 字，`maxLines = 2` 下**大概率被省略号截断**，
   用户看不到结尾的「重复重试不会自动恢复该备份」。
2. **导入任务行**：`_importTasks`（`:334`）→ `ImportTaskRow`（`ShelfImportRoute.kt:260`、
   `:625-628`）显示 `task.phase` = 「修复失败：检测到未完成的替换残留…」。

**【源码事实】** 两者都在**导入页**，不在书架页；书架页用户需要主动切页才能看到。

### 2.4 是否误报成功：**否（三处均已核对）**

- 入口 A 文案以「修复失败：」开头（`:337`），不含成功字样。
- 入口 B 走 `Failed` 分支（`:282` → `:694`），`succeeded` 不递增。
- `refreshSourceBaseline`（`:283`）在 `:282` 的 `return` **之后**，失败时**不执行**
  ⇒ 来源基线不推进。

**【源码事实】** 因此不存在「报成功但实际没换」的路径。

### 2.5 是否允许反复重试：**允许，且无限循环**

**【源码事实】** 因为基线不推进，`refreshSourceUpdates()`（`ShelfViewModel.kt:697` →
`:732-755`）重新检测后该书**仍被标记「可更新」**（`BookGrid.kt:423`、`ShelfScreen.kt:537-538`）。
下拉刷新再次进入 `checkUpdatedSources`（`:644-670`）→ 再次置 `pendingSourceRefreshIds`
→ 再次弹确认框 → 再次失败。

**【源码事实】** 入口 A 同样可无限重试：守卫只看 `backup.exists()`，**与所选来源是否合法无关**
（`EpubRepository.kt:96` 在 `parse` 之前）。用户即便选一个全新、完全合法的 EPUB，
也会被同样拒绝。

**【静态推断】** 用户会经历「点 → 失败 → 再点 → 再失败」的循环，且文案明确说
「重复重试不会自动恢复该备份」——**说了实话，但没给出任何出路**。

### 2.6 是否存在删书/清备份的引导：**守卫文案本身干净，但外部入口有真实风险**

**守卫文案本身（已由测试钉住）**：
**【源码事实】** `ShelfImporterTest.kt:650-651`、`:687-688` 断言文案
**不含「再重试」、不含「删除」**。EPUB 侧对应测试为
`EpubReplaceRoomIsolatedTest.kt` 的
`backupResidueWithTargetPresentIsRefusedAndPreservesBothSides`（本窗口读取时位于 `:498`）
与 `backupResidueWithTargetMissingIsRefusedAndNeverAutoRestores`（`:586`）
（已真机通过，见 §0 引用的验收报告）。

> **行号漂移提示**：该测试文件在本报告撰写期间**被其他窗口修改过**（本窗口先后读到
> `:464`/`:552` 与 `:498`/`:586` 两组行号，文件现为 8 条 `@Test`）。因此本报告一律
> **以方法名而非行号**指代这两个用例。本窗口读取时该文件 SHA-256 为
> `4b03fa12f4d510098bfc6d5acfc4a198e94398e7562f0904e31b33095e31e36a`。

**但仓内存在会销毁残留备份的用户可达路径**：

| 入口 | 位置 | 对残留 `.backup` 的影响 | 文案是否提示 |
|---|---|---|---|
| **清空所有离线书籍与资源** | `StorageSubPage.kt:647` `File(filesDir,"books").deleteRecursively()` | **连同 `.backup` / `.repairing` 一起删除**（EPUB 与 TXT 都中） | **否** —— 文案（`:635`）只说「书架目录、阅读进度、书签和笔记均会完整保留，后续若需阅读可随时重新下载」 |
| **清缓存**（批量） | `ShelfRoute.kt:387-392` / `:818-822` → `ShelfBookActions.kt:154-157` → `BookRepository.clearBookCache:270-278` → `deleteBookContentFiles:287-291` | **TXT/MD：`books/<id>/` 被 `deleteRecursively()`，残留 backup 一并销毁**；**EPUB：只删 `books/epub/<id>.epub`，`.backup` 幸存** | **否 —— 该动作没有任何确认弹窗** |
| 删除书籍 | `BookRepository.applyDeletionLocked:245-262` | **不触碰任何文件**（纯 DB 软删除） | 文案（`ShelfSharedComponents.kt:30`）已正确说明「不删除本地正文文件」 |

**【源码事实】** 三点重要后果：

1. **EPUB 残留没有任何非破坏性的清除入口** —— 全仓 grep 确认：能删 `.backup` 的代码只有
   `EpubRepository.kt:195`（成功路径）、`:210`/`:214`（回滚路径），**三处都只在守卫未触发时才执行**。
   唯一能清掉残留 backup 的用户操作是「清空所有离线书籍与资源」，而那会**同时删掉全部书的正文**。
2. **TXT/MD 与 EPUB 行为不对称** —— TXT 残留会被「清缓存」静默销毁（该操作**无二次确认**），
   EPUB 残留则永久留存。同一守卫的两条实现，用户可达的销毁路径完全不同。
3. **TXT 侧存在一条无意的逃生口** —— 守卫匹配的是**所选文件显示名**派生的
   `"$safeName.backup"`（`ShelfImporter.kt:948`、`:952`）。用户若选择一个**显示名不同**的文件，
   `safeName` 变化 ⇒ 守卫**不触发**，替换照常进行，旧残留备份被留在原地成为孤儿文件。
   EPUB 侧无此口（名字恒为 `${stored.id}.backup`，`:92`）⇒ **EPUB 残留是真正卡死的**。

---

## 3. 问题清单（按严重度）

### P1 — EPUB 残留无出路，唯一解除手段是毁灭性的

**事实**：守卫拒绝一切替换（`EpubRepository.kt:96`），重试永不生效，文案也如实说了
「重复重试不会自动恢复该备份」（`:104`）。但仓内**不存在**任何面向用户的残留清理/恢复入口
（§2.6 事实 1）。用户唯一能做的「清空所有离线书籍与资源」会删掉整个 `books/` 目录。

**风险**：残留 `.backup` 在「K2 现场」（旧副本已挪进 backup、新副本未落位）下**可能就是该书
唯一完好的正文**。此时该入口的文案只承诺保留「书架目录、阅读进度、书签和笔记」，
**没有提醒用户它同时会销毁这份仅存正文**，且「可随时重新下载」对来源已消失的书不成立。

**最小修复边界**（三选一，均不改替换逻辑）：

- **A（最小，纯文案）**：`StorageSubPage.kt:635` 的危险确认正文补一句
  「同时会清除未完成替换的残留备份（可能是某本书仅存的正文）」。改动 1 个字符串。
- **B（补出口）**：给守卫拒绝后的界面提供「保留备份并重试」以外的出路，
  例如在书架长按面板加一个**需要二次确认、且明确说明风险**的「清理未完成的替换残留」动作。
  需新增 UI 动作 + 一条 repository 方法，**属产品决策**（是否允许用户主动放弃那份备份）。
- **C（不做 UI，改行为）**：实现已设计但未落地的自动恢复状态机
  （见 `docs/coordination/legacy-stages/2026-09/2026-09-24-txt-backup-crash-recovery-design.md` 一系）。**范围最大**。

**建议测试**：A 可用现有的文案一致性测试模式（参照 `ShelfDeleteCopyConsistencyTest`）钉住；
B/C 需真机用例，本报告不设计。

### P2 — 入口 B 丢弃残留详情

**事实**：`ShelfViewModel.kt:694` 只把书名放进 `failedTitles`，`Failed.message` 被丢。
用户看到「N 本刷新失败：书名」，得不到任何原因与「重试无效」的提示。

**最小修复边界**：`ShelfViewModel.kt:698-704` 的 `buildString`。可把首条失败消息附上
（如「；N 本刷新失败：《甲》《乙》（原因：检测到未完成的替换残留…）」）。
**注意**：多本失败时文案会很长，需决定只显示第一条还是全部 —— 属产品口径。

**建议测试**：`ShelfViewModelTest` 已有 `confirmSourceRefresh` 用例（`:571`、`:575`），
可在同处断言失败文案包含原因片段。

### P2 — 「清缓存」静默销毁 TXT 残留且无二次确认

**事实**：`ShelfRoute.kt:387-392` / `:818-822` 直接调用 `clearCacheForBooks`，
**没有确认弹窗**；底层 `BookRepository.kt:289` 的 `dir.deleteRecursively()` 会连带删掉
`books/<id>/*.backup`。而 `DeletionScope.REMOVE_CONTENT` 的确认文案
（`strings_deletion.xml:32-35`，含「清掉的正文不是备份，无法从本地还原原文件内容」）
**在 UI 中从未被构造** —— 全仓 grep 确认 `REMOVE_CONTENT` 只出现在
`DeletionCopy.kt` / `DeletionScope.kt` / `ShelfSharedComponents.kt` 的注释里，
**没有任何 UI 调用点**。即：为「移除正文」写好的风险文案是死代码，真实入口是无确认的「清缓存」。

**最小修复边界**：二选一 ——
(a) 给批量「清缓存」补上 `DeletionScope.REMOVE_CONTENT` 的确认弹窗（复用既有文案，不新增字符串）；
(b) 若产品认为清缓存不该有确认，则删除死代码 `DeletionScope.REMOVE_CONTENT` 相关分支以免误导后续维护者。
**属产品决策**，本报告不选边。

**建议测试**：若选 (a)，需真机/Compose 测试；若选 (b)，纯删除 + 编译即可。

### P3 — 两个归档页对「重新定位文件」失败零反馈

**事实**：`HomeCompletedScreen.kt:94` 与 `MyReadingRoute.kt:55` 传 `{}` 作为 `onResult`。
**【静态推断】** 用户在「已读完成」或「我的阅读」页点「重新定位文件」并选完文件后，
界面不会有任何提示 —— 成功与失败都静默。

**最小修复边界**：两处各加一个 Snackbar。`MyReadingRoute.kt:47` 已有 `snackbar` 状态与
`:85-90` 的 `SnackbarHost`，**只需把 `{}` 换成 `{ msg -> scope.launch { snackbar.showSnackbar(msg) } }`**；
`HomeCompletedScreen.kt` **没有** Snackbar 宿主（grep 计数为 0），需先补一个，改动较大。

**建议测试**：难以纯 JVM 覆盖（Compose 层），需真机。

### P3 — `ShelfAction.ReselectFileResult` 是死分支

**事实**：`ShelfAction.kt:117` 声明、`ShelfRoute.kt:911` 处理，但全仓**无任何构造点**
（grep 只命中这两处）。实际走的是 `ShelfRoute.kt:141` 的 launcher 回调。

**影响**：低（不产生错误行为），但会误导后续维护者以为存在该分发路径。
**最小修复边界**：删除声明与分支，或补注释说明保留原因。**非本任务范围**。

---

## 4. 明确标注为 UNKNOWN / 未验证的部分

1. **Snackbar 在真机上的实际呈现**：字数、换行、是否被系统截断、4 秒是否够读 —— 全部
   **【UNKNOWN】**。本报告未运行 adb、未截图。
2. **导入记录里残留文案是否被 `maxLines = 2` 截断** —— **【静态推断】**，未在真机确认。
3. **用户能否凭现有信息自行判断「该不该清空所有离线书籍」** —— 属可用性判断，
   **【UNKNOWN】**，需真人测试。
4. **K2 现场（残留 backup 是唯一副本）在真实用户设备上的发生概率** —— **【UNKNOWN】**，
   需真机日志或故障样本。
5. **TXT 侧「换名绕过守卫」是否在实际文件选择器下可达** —— 依赖 `OpenableColumns.DISPLAY_NAME`
   （`ShelfImporter.kt:1075-1082`）的返回，**不同 SAF provider 行为未验证**。

---

## 5. 本报告**不**主张的结论

- 不主张「残留守卫有缺陷」—— 守卫的**拒绝语义本身是正确的**，且已被真机用例覆盖
  （2 条方法 `OK (1 test)`，见 §0 引用的验收报告）。
- 不主张「界面会把失败报成成功」—— §2.4 已核对三处，**均不会**。
- 不主张「守卫文案引导用户删书」—— §2.6 已核对，文案干净且由测试钉住。
- 不主张「清空所有离线书籍」一定会毁掉用户数据 —— 只在**残留 backup 是唯一副本**时成立，
  该前提的发生率 **【UNKNOWN】**。
- **不主张任何真机 UI 行为** —— 全文界面效果均为**【静态推断】**。

---

## 6. 审查动作自证

| 动作 | 是否执行 |
|---|---|
| 读源码 / grep / 计算 SHA-256（只读） | ✅ |
| 修改产品源码或测试 | ❌ 未执行 |
| adb / 安装 / instrumentation / force-stop | ❌ 未执行 |
| 触碰真实书库 / E 盘备份 / 原始仓库 | ❌ 未执行 |
| commit / push / stage / reset / checkout / clean | ❌ 未执行 |
| 修改他人窗口文件或既有报告 | ❌ 未执行 |

唯一新增文件：本报告。HEAD 未变。
