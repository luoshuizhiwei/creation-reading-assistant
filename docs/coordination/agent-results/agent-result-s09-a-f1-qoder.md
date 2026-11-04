# S09-A-F1 结果报告 — 书签不得使用其他书残留的分页位置

> 执行 Agent：窗口 C / Qoder　|　声明模型：`qwen3.8Flash`
> 任务编号：S09-A-F1（最小止血修复）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`　固定基点：`origin/main = 5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收工 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 上游依据：`docs/coordination/investigations/agent-investigation-s09-qoder.md`（S09 盘点，SN-1/P1）
> 报告路径：`docs/coordination/agent-results/agent-result-s09-a-f1-qoder.md`
> 状态：**完成，等待 Codex 核验。未领取下一任务。**

---

## 1. 修复摘要

创建书签时，`ReaderPageIndexManager.position` 现在**只有 `position.bookId == 当前 bid` 才可用**。
判断集中在**一个生产纯函数** `bookmarkPositionFields(position, bookId, isEpub, chapterStartOffsets)` 里，
`absOffset` / `chapterIndex` / `charOffset` / `locatorJson` **四个字段全部由同一份过滤后的位置推导**，
不存在"locator 过滤了、坐标没过滤"的漂移面。

position 为 null 或属于其他书时，返回与修复前 `position == null` **逐字段相同**的回退形态：

```
absOffset = -1, chapterIndex = 0, charOffset = -1, locatorJson = null
```

`offset = state.stats.progressPercent.toInt()` 一字未改 ⇒ 既有「locator 为空 → 用 progress_percent
近似回跳」的回退链完全保留。**没有**伪造 `absOffset = 0` 的假锚点。

测试先行：先写 8 个用例 → RED（编译失败，9 条 `Unresolved reference 'bookmarkPositionFields'`）
→ 实现 → GREEN → 两组变异验证 → 还原并复核哈希 → 最终绿色重跑。

---

## 2. 根因

| # | 事实 | 位置 |
|---|---|---|
| 1 | `ReaderPageIndexManager` 是 `@Singleton`，`position` 为进程内可变状态 | `feature/reader/pager/ReaderPageIndexManager.kt:31-42` |
| 2 | `update()` **只有一个调用点**，且位于**分页阅读分支** | `ui/screen/reader/ReaderContentHostPagedBranch.kt:75` |
| 3 | `clear()` **全仓零调用点**（`grep pageIndexManager.clear` = 0 命中） | 同上文件 KDoc 声称"离开本书/关闭阅读器时调用" |
| 4 | 书签创建直接读 `position.value`，**不校验 `bookId`** | 修复前 `ReaderSheetHost.kt:374` |
| 5 | 该 position 同时供给 `absOffset`、`chapterIndex`、`charOffset`、`locatorJson` 四个持久化字段 | 修复前 `ReaderSheetHost.kt:375-396` |

⇒ 触发路径：**先读 A 书（分页引擎）→ 再打开 B 书（滚动模式，或新书首帧尚未上报页索引）
→ 在 B 书点"添加书签"** ⇒ A 书的 `absStart` 被写进 B 书书签，
并据此编出 A 书坐标系的 locator。回跳时落到另一本书的位置。属**跨书数据污染**，P1。

第二形态：**纯滚动模式**下 `position` 从未被写入 ⇒ 为 null ⇒ 书签 `absOffset = -1`、
`locatorJson = null`。这一形态**不是本片要修的缺陷**（按 Codex 纠正，它靠 progress_percent 仍可近似回读），
本片只关闭"残留坐标被误写"。

---

## 3. 修改文件清单

| 文件 | 状态 | 我的改动 |
|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSheetHost.kt` | ` M` | ①新增 1 行 import `ReaderPagePosition`；②`onAddBookmark` 改为调用 `bookmarkPositionFields`；③文件末尾新增 `BookmarkPositionFields` + `bookmarkPositionFields` + `NO_EXACT_BOOKMARK_POSITION`（含 KDoc） |
| `android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderBookmarkPositionPolicyTest.kt` | `??`（新增） | 8 个 `@Test`，全部调用生产纯函数 + 真实 `LocatorCodec.decode` |
| `docs/coordination/agent-results/agent-result-s09-a-f1-qoder.md` | `??`（新增） | 本报告 |

**未修改任何其他文件**；无需 SEAM REQUEST（§12）。

---

## 4. 生产行为 before / after

### Before（`ReaderSheetHost.kt` 修复前）

```kotlin
val pos = callbacks.pageIndexManager.position.value      // ← 不校验 bookId
val abs = pos?.absStart ?: -1
val (ci, co) = if (pos != null && isEpub) EpubLocatorMapping.toChapterOffset(pos.absStart, cso)
               else 0 to (pos?.absStart ?: -1)
val locatorJson = if (pos != null) { if (isEpub) LocatorBuilder.forPosition(...) else LocatorBuilder.forPlain(...) } else null
```

### After

```kotlin
val fields = bookmarkPositionFields(
    position = callbacks.pageIndexManager.position.value,
    bookId = bid,
    isEpub = state.document.epubBook != null,
    chapterStartOffsets = state.document.chapterStartOffsets,
)
// AddBookmark(bookId = bid, offset = state.stats.progressPercent.toInt(),  ← 未改
//             title = ..., absOffset = fields.absOffset,
//             chapterIndex = fields.chapterIndex, charOffset = fields.charOffset,
//             locatorJson = fields.locatorJson)
```

生产纯函数：

```kotlin
internal fun bookmarkPositionFields(
    position: ReaderPagePosition?, bookId: String, isEpub: Boolean, chapterStartOffsets: List<Int>,
): BookmarkPositionFields {
    val owned = position?.takeIf { it.bookId == bookId } ?: return NO_EXACT_BOOKMARK_POSITION
    val abs = owned.absStart
    val (chapterIndex, charOffset) =
        if (isEpub) EpubLocatorMapping.toChapterOffset(abs, chapterStartOffsets) else 0 to abs
    val locatorJson =
        if (isEpub) LocatorBuilder.forPosition(abs, chapterIndex, charOffset, null)
        else LocatorBuilder.forPlain(abs, null)
    return BookmarkPositionFields(abs, chapterIndex, charOffset, locatorJson)
}

private val NO_EXACT_BOOKMARK_POSITION =
    BookmarkPositionFields(absOffset = -1, chapterIndex = 0, charOffset = -1, locatorJson = null)
```

**行为差异只有一处**：`position` 属于其他书时，从"照用"变成"视为不可用"。
当前书命中与 null 两条路径的产出**逐字段不变**（由 §5 矩阵与测试钉住）。

---

## 5. current / mismatched / null 三态矩阵

| 输入形态 | isEpub | absOffset | chapterIndex | charOffset | locatorJson | 钉住它的用例 |
|---|---|---|---|---|---|---|
| **当前书** position（absStart=1234） | false | `1234` | `0` | `1234` | 非 null，`decode().legacyOffset == 1234` | `a position belonging to the current book keeps its exact coordinates` |
| **当前书** position（absStart=1050，章表 [0,1000,2000]） | true | `1050` | `1` | `50` | 非 null，解码出 1050 / 1 / 50 | `an epub position of the current book is still mapped through chapter starts` |
| **其他书** position（absStart=1234） | false | `-1` | `0` | `-1` | **null** | `a position from another book never contributes coordinates` |
| **其他书** position（absStart=1234） | true | `-1` | `0` | `-1` | **null**（`EpubLocatorMapping` 根本不被调用，否则会得到 1/234） | `an epub bookmark of another book does not even run the chapter mapping` |
| **null** position | false | `-1` | `0` | `-1` | null | `a null position falls back without faking exact fields` |
| **null** position | true | `-1` | `0` | `-1` | null（不编造章锚点） | `a null position in an epub does not fabricate a chapter anchor` |
| mismatched vs null（同为 EPUB） | true | 两形态**完全相等** | | | | `the mismatched and null shapes are identical so the fallback cannot branch` |
| bookId 前缀相似（`bk-a` vs `bk-a-extra`） | false | `-1` | `0` | `-1` | null（精确相等比较，非前缀匹配） | `book id comparison is exact so a prefix does not pass as the same book` |

---

## 6. 为什么 null position 仍保留现有 progressPercent 回退

1. `offset = state.stats.progressPercent.toInt()` 这一行**我一个字都没改**（diff 中它不带 `+/-`）。
2. 按 Codex 纠正：locator 为空时 `ReaderProgressEffects` 会用书签已有的 `progress_percent` 回退，
   所以"无精确位置"≠"书签废掉"，只是回读精度降为百分比级。
3. 因此本片对 null 路径的要求是**保持原样**，而不是"顺手补一个精确锚点"。
   给滚动模式新增精确 locator 属于独立能力（S09 报告 SN-3 / Q5），本片明确不碰。
4. 反向保护：测试专门钉了"**不得**伪造" —— 若有人为了"让书签看起来能用"而写
   `absOffset = 0` 的假 locator，`a null position falls back without faking exact fields`
   与 `a null position in an epub does not fabricate a chapter anchor` 会失败。
   变异 B 正是这样改的，**5 个用例立刻变红**（§8.4）。
5. `mismatched` 与 `null` 被断言为**同一形态**（`assertEquals(fromNull, fromOtherBook)`），
   保证"无可用精确位置"只有一种回退形态，不会因来源不同而分叉。

---

## 7. 明确未做的事（禁止扩大清单逐条核对）

| 禁止项 | 本片是否做了 | 证据 |
|---|---|---|
| 接入 `ReaderPageIndexManager.clear()` | ❌ 未做 | manager 文件未修改（不在授权清单，`git status` 无该文件） |
| 修改 manager 生命周期 / DI scope | ❌ 未做 | 同上 |
| 为滚动模式新增精确 locator | ❌ 未做 | null 路径产出仍为 `(-1, 0, -1, null)` |
| 接入 `WholeBookPageModel` | ❌ 未做 | 全仓对该符号零改动 |
| 修改页眉页脚 / 全书页码 / 进度公式 | ❌ 未做 | `PagedReaderHeaderFooter.kt`、`TxtPagedController.kt`、`ReaderProgressActions.kt` 均未修改 |
| 修改音量键 / 媒体键 / 蓝牙翻页器 | ❌ 未做 | `ReaderVolumeTurn.kt`、`ReaderMediaButtonBridge.kt`、`ReaderHardwareKeys.kt` 均未修改 |
| 修改点击区域 / 热区比例 / 预览卡 | ❌ 未做 | `ReaderTapAction.kt`、`PagedEpubContent.kt`、`TapZonePreviewCard.kt` 均未修改 |
| 修改 D-2-B 设置文案 | ❌ 未做 | `ReaderSettingsSheet.kt` 未修改（窗口 D 正在验收，我未触碰） |
| 修改书签数据库结构 / DAO / Repository / ViewModel / locator 编解码 | ❌ 未做 | 授权外文件一律未改；`LocatorBuilder` / `LocatorCodec` 只被**调用**，未改其实现 |
| 顺手处理 S09 其他 P2/P3 | ❌ 未做 | SN-2…SN-14 全部留档待切片 |

---

## 8. 测试命令、退出码、数量与 XML 证据

工作目录：`android/`。全部经 `powershell.exe -NoProfile -Command ".\gradlew.bat …"` 执行。
（我未使用 `cmd.exe /c` —— 那条路在 MSYS 下会返回**假 exit 0**。）

### 8.1 RED（实现前）

```
.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.reader.ReaderBookmarkPositionPolicyTest" --rerun
```

| 项 | 值 |
|---|---|
| 退出码 | **1** |
| 结果 | `BUILD FAILED in 9s` |
| 失败原因 | 9 条 `e: …ReaderBookmarkPositionPolicyTest.kt: Unresolved reference 'bookmarkPositionFields'.`（行 41/59/80/97/114/129/146/147/156） |

即测试先于实现落地，RED 表现为编译失败 —— 契约不存在，不是断言写错。

### 8.2 最终绿色（实现后，三命令合一，作为最终证据）

```
.\gradlew.bat --console=plain :app:testDebugUnitTest \
 --tests "com.creationreadingassistant.ui.screen.reader.ReaderBookmarkPositionPolicyTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.sheets.TocNoteEntriesTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.ReaderProgressNavigationTest" --rerun
```

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 结果 | `BUILD SUCCESSFUL in 18s` |
| 请求类数 / XML 文件数 | **3 / 3**（零匹配 0，缺失 0） |
| tests / failures / errors / skipped | **31 / 0 / 0 / 0** |
| XML mtime | **01:51:26**（三个文件同一时刻，非陈旧） |

| 类 | tests | F | E | S |
|---|---:|---:|---:|---:|
| `ui.screen.reader.ReaderBookmarkPositionPolicyTest`（**新增**） | 8 | 0 | 0 | 0 |
| `ui.screen.reader.sheets.TocNoteEntriesTest`（既有回归，typed 书签回源） | 16 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderProgressNavigationTest`（既有回归，进度回跳） | 7 | 0 | 0 | 0 |

XML 目录：`android/app/build/test-results/testDebugUnitTest/`
（`TEST-com.creationreadingassistant.ui.screen.reader.ReaderBookmarkPositionPolicyTest.xml` 等 3 个）

**说明**：Gradle 每次执行会重写整个 `test-results/testDebugUnitTest/` 目录，
所以分开的两条命令（新测试类 / 两个回归类）各自的 XML 会被后一次覆盖。
我因此**追加执行了上面这条三合一命令作为最终证据**，让 3 个类的结果同时落盘；
分开跑的两次（`PSEXIT1=0` 于 01:40:58、`PSEXIT2=0` 于 01:47:02）也各自记录在
`/tmp/s09a/green.log`、`/tmp/s09a/final_tests.log`。**最终留存的 XML 是绿色的，没有把失败 XML 当证据。**

包名/类名核对：`TocNoteEntriesTest` 实际在 `…ui.screen.reader.sheets`（不是 `…ui.screen.reader`），
`ReaderProgressNavigationTest` 在 `…ui.screen.reader`。我用 `grep '^package'` 现场确认后才使用 ——
避免 `--tests` 零匹配仍打印 BUILD SUCCESSFUL 的假绿陷阱。

### 8.3 主 Kotlin 编译

```
.\gradlew.bat --console=plain :app:compileDebugKotlin --rerun
```

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 结果 | `BUILD SUCCESSFUL in 1m` |
| 警告 | **1 条**，`ui/screen/shelf/LibraryBrowserRoute.kt:145:48 'Icons.Outlined.Sort' is deprecated` |

该警告**与本片无关**：该文件我未修改，其 mtime 为 2026-09-18（他人既有改动），
且我在 S09-A-F1 之前的 D-2-B 复核中已记录过同一条警告。
**我修改的 `ReaderSheetHost.kt` 与新增测试产生 0 条警告。**

### 8.4 变异验证（证明测试咬得住）

每次变异前 `cp` 备份，变异后 `cp` 还原并复核 SHA-256。
还原后哈希：`382b485458f00528…` == 备份 == **逐字节一致**；残留扫描 `grep MUTANT` = 0 命中。

| 变异 | 内容 | 结果 |
|---|---|---|
| **A** | 去掉当前书校验：`position?.takeIf { it.bookId == bookId }` → `position`（即修复前语义） | `8 tests completed, 4 failed`，`BUILD FAILED in 40s`，退出码 **1** ✅ |
| **A** 变红的用例 | `a position from another book never contributes coordinates`、`an epub bookmark of another book does not even run the chapter mapping`、`the mismatched and null shapes are identical…`、`book id comparison is exact…` | 4 条全部命中跨书方向 ⇒ 校验真的被锁住 |
| **B** | 伪造精确位置：不可用时返回 `BookmarkPositionFields(-1, 0, -1, LocatorBuilder.forPlain(-1, null))` | `8 tests completed, 5 failed`，`BUILD FAILED in 35s`，退出码 **1** ✅ |
| **B** 变红的用例 | 上述 4 条 + `a null position falls back without faking exact fields`、`a null position in an epub does not fabricate a chapter anchor` | 「不得伪造」被独立锁住 |

变异 A/B 都在**还原并重跑**之后才产出 §8.2 的最终绿色证据。

### 8.5 未运行

`:app:lintDebug`、`assembleDebug`、`androidTest`（编译与执行）、全量 JVM、
任何 `adb` / 手机操作 / MuMu —— **全部未运行**。
Gradle 未出现被其他窗口占用的情况（无锁等待、无构建目录争用报错）。

---

## 9. Git 范围核对

```
$ git status --short -- <三个授权路径>
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSheetHost.kt
?? android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderBookmarkPositionPolicyTest.kt
（报告文件在本节执行时尚未落盘，故未列出；它是唯一允许的第三个新增文件）

$ git diff --check -- <产品+测试>      → 退出码 0（无空白错误、无冲突标记）
$ git rev-parse --short HEAD           → 5aed593
$ git diff --cached --name-only | wc -l → 0
$ git stash list | wc -l               → 0
```

### 必须做的归因澄清（脏工作区）

`git diff --numstat` 对 `ReaderSheetHost.kt` 报 **226 / 55**，
但**这个数字不属于 S09-A-F1**：该文件在我动手之前就已带大量他人未提交改动
（S07 弹层整改、G26 模糊移除、callbacks 扩字段等）。
`git diff -U1` 里共 **34 个 hunk**，其中只有 **3 处**是我的：

| diff 位置 | 我的改动 |
|---|---|
| `+import …pager.ReaderPagePosition` | 1 行 import |
| `@@ -369,2 +421,9` 一带（`NOTES -> NotesSheet` 分支内） | `onAddBookmark` 由 13 行内联推导改为调用 `bookmarkPositionFields` |
| `@@ -535,0 +641,66`（文件末尾） | 新增 `BookmarkPositionFields` + `bookmarkPositionFields` + `NO_EXACT_BOOKMARK_POSITION`，共 66 行 |

其余 31 个 hunk（含 `@@ -217,15 +268,0 @@` 删 15 行、`@@ -162,3 +168,22 @@` 扩 callbacks、
以及两处 `locatorJson: String?,` 的 8 空格缩进新增行）**全部是既有他人改动**，
我没有回退、覆盖、重排或重新格式化它们 —— 我的三次编辑都用精确 `old_string` 匹配当前内容完成。

**未修改**：协调看板、`docs/handoff/current.md`、Obsidian、TODO、任何 D-2-B 文件、
任何 S08/S19 文件、其他 Agent 报告。

---

## 10. 未覆盖项

| # | 项 | 说明 / 需要什么 |
|---|---|---|
| U1 | **端到端"按一次书签按钮 → 落库的 absOffset 是当前书的"** | 我的测试覆盖到生产纯函数与真实 `LocatorCodec`，但**没有**穿过 `NotesSheet` 组合 + `ReaderAction.AddBookmark` 派发 + `noteRepository.addBookmark`。可用 JVM 级 VM 测试补（需构造 `ReaderSheetHost` 状态袋，成本较高），或真机验证 |
| U2 | `bid` 为空串时的语义 | 我**刻意保持原样**：`position.bookId == bookId` 是精确比较，若两者都为空串则视为匹配（与修复前一致）。未新增"空 bookId 一律拒绝"，因为那会改变现有行为且不在授权范围 |
| U3 | `ReaderPageIndexManager.clear()` 未接入 | 残留状态仍会**存在**（只是不再被误用）。彻底清理属下一切片 |
| U4 | 滚动模式书签仍无精确 locator | 本片明确不做（属 S09 的 SN-3 / Q5） |
| U5 | 其他读取 `position` 的潜在入口 | 我 grep 过：`pageIndexManager.position` 在 `main/` 中**只有 `ReaderSheetHost.kt` 一处读取**。若将来新增读取点，需复用 `bookmarkPositionFields` 或同等校验 |
| U6 | 真机行为 | 未操作设备（窗口 D 负责真机侧） |
| U7 | 全量 JVM / Lint / assemble | 未运行（任务书只要求定向） |

---

## 11. P0 / P1 / P2 / P3

| 级别 | 数量 | 说明 |
|---|---:|---|
| **P0** | 0 | — |
| **P1** | **0（本片已关闭 1 个）** | 关闭 SN-1：跨书残留坐标被写进当前书书签 |
| **P2** | 0 | 本片未引入新缺陷 |
| **P3** | 2 | 见下 |

| ID | 级别 | 内容 |
|---|---|---|
| F1-P3-1 | P3 | U1：缺少穿过 UI/动作派发/仓储的端到端用例。当前保护止于生产纯函数边界 |
| F1-P3-2 | P3 | `bookmarkPositionFields` 与 `NO_EXACT_BOOKMARK_POSITION` 放在 `ReaderSheetHost.kt` 文件末尾 —— 受"只能改这一个产品文件"的边界约束。若后续再有书签位置需求，宜下沉到 `feature/reader/locator/` 之类的纯函数模块（需新的文件授权） |

**残留风险（不是本片缺陷，属未做完的事）**：`position` 仍会跨书残留，
只是不再被用于书签坐标。若下一个切片给它加了新的读取点而忘记校验，问题会以新形态复现 ——
建议 S09-A-F2 一并接入 `clear()` 并把"任何 `position` 读取点必须经 bookId 校验"写成结构守卫。

---

## 12. 是否需要 SEAM REQUEST

**不需要。**

任务书授权的三个路径（1 个产品文件 + 1 个测试文件 + 1 个报告文件）已足够完成修复与验证：
`ReaderPagePosition` 与 `EpubLocatorMapping` / `LocatorBuilder` 都只需**引用**，无需修改；
回归所需的 `TocNoteEntriesTest` / `ReaderProgressNavigationTest` 无需改动即通过。

若后续切片要（a）接入 `clear()`（需 `ReaderPageIndexManager.kt` + 阅读器生命周期文件）、
或（b）补 U1 的端到端测试（需 ViewModel/仓储测试夹具），届时再单独申请。

---

## 13. 回传摘要

| 项 | 内容 |
|---|---|
| 报告绝对路径 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\agent-results\agent-result-s09-a-f1-qoder.md` |
| 修改的产品文件 | `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSheetHost.kt`（1 个，仅 3 处：1 行 import + `onAddBookmark` + 文件末尾新增纯函数） |
| 新增的测试文件 | `android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderBookmarkPositionPolicyTest.kt`（8 个 `@Test`，调用生产纯函数 + 真实 `LocatorCodec.decode`） |
| 测试结果 | RED：退出码 1（9 条 Unresolved reference）。**最终绿色：3 类 / 31 项 / 0 failures / 0 errors / 0 skipped，退出码 0**，XML mtime 01:51:26，请求类数 == XML 数。`compileDebugKotlin` 退出码 **0**（1 条他人既有警告） |
| 变异验证 | A（去掉当前书校验）→ 4 红；B（伪造 locator）→ 5 红；均已还原并复核哈希 `382b485458f00528…` 逐字节一致，`grep MUTANT` 0 命中 |
| 是否操作设备 | **否**。未运行 adb、未安装 APK、未使用 MuMu；窗口 D 的真机验收文件（含 `ReaderSettingsSheet.kt`）我未触碰 |
| 是否修改报告之外的未授权文件 | **否**。`git diff --cached` = 0、`git stash list` = 0、HEAD 仍 `5aed593`；`git diff --check` 干净；未 commit / push / stage / reset / checkout / clean / 全仓格式化 |
| 是否建议进入独立代码复核 | **建议**。理由：①这是 P1 数据污染的止血，判定权不宜与实现同体；②复核只需读 `bookmarkPositionFields` + 三态矩阵 + 跑 §8.2 那条三合一命令，成本很低；③请复核方特别确认两点 —— **null 路径产出与修复前逐字段一致**（回退语义未变）、**未偷偷接入 `clear()` 或扩大到其他 S09 能力** |

**完成标准逐条自检**：其他书 position 不再参与当前书签坐标 ✅；当前书行为保持 ✅；
null 百分比回退保持 ✅；三态测试全绿 ✅；typed 书签回源回归全绿 ✅；主 Kotlin 编译成功 ✅；
未扩大文件边界 ✅；未运行设备操作 ✅；未 commit/push/stage ✅。

**已停止，不领取下一任务，等待 Codex 核验。**
