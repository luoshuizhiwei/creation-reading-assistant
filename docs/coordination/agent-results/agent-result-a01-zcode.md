# A01：目录与标记中的划线/笔记索引接线修复 — Zcode 执行报告

> 执行 Agent：Zcode　|　实际模型：`deepseek v4.1Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收工 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 报告路径：`docs/coordination/agent-results/agent-result-a01-zcode.md`
> 状态：**等待 Codex 主任务复核**（未领取下一片）

---

## 1. 复现链与根因逐跳证据

### 1.1 用户可见现象

打开「目录与标记 → 笔记」，即使当前书存在未删除高亮，页面仍显示
「暂无划线与笔记」/「阅读选中文本添加划线或思考，灵感随时沉淀。」

### 1.2 Codex 给出的 6 条根因边界 —— 逐跳核对结果

**全部成立，且第 6 条比描述更严重。** 证据如下。

| # | 声明 | 核对结果 | 证据 |
|---:|---|---|---|
| 1 | `ReaderViewModel.bookData` 同时提供 `inputs.highlights` 与 `inputs.notes` | **成立** | `ReaderSheetHost.kt` 的 `ReaderSheet.NOTES -> NotesSheet(...)` 同时传 `highlights = inputs.highlights`（:346）与 `notes = inputs.notes`（:347）；同一 `inputs` 对象在 `TocSheet(...)` 处可用 |
| 2 | `HighlightDao.observeByBook` / `NoteDao.observeByBook` 已按当前书读未删除记录 | **成立** | 两个 `@Dao` 接口与查询都定义在 `data/local/dao/InspirationDao.kt`（文件名与接口名不同，`HighlightDao` 在 :98、`NoteDao` 在 :50）：`SELECT * FROM highlights WHERE book_id = :bookId AND deleted_at IS NULL ORDER BY created_at DESC`（:108）与 notes 同形（:78） |
| 3 | `TocSheet(...)` 只传了 `bookmarks`，没传 notes、没传 highlights | **成立** | 修改前 `ReaderSheetHost.kt:308` 只有 `bookmarks = inputs.notes.filter { it.kind == "bookmark" }`；调用实参块内**不存在** `notes =`，也不存在任何高亮实参 |
| 4 | `TocSheet` 的 `notes` 参数有 `emptyList()` 默认值，漏接线不报编译错 | **成立** | 修改前 `ReaderTocSheet.kt:98` `notes: List<NoteEntity> = emptyList()`、:99 `onPickNote: (NoteEntity) -> Unit = {}` |
| 5 | `NotesTabContent` 只接受 `List<NoteEntity>`，与「划线与笔记」文案不一致 | **成立** | 修改前 `ReaderTocBookmarkList.kt:78` `NotesTabContent(notes: List<NoteEntity>, onPickNote: (NoteEntity) -> Unit, ...)`；空态文案在 :87 就是「暂无划线与笔记」——**文案承诺两类，数据模型只装一类** |
| 6 | `ReaderNotesSheet` 中普通笔记点击复用 `onJumpToBookmark`，typed resolver 要求按类型区分 | **成立，且比描述更严重** | 修改前 `ReaderNotesSheet.kt:79` 只有 `onJumpToBookmark: (NoteEntity) -> Unit`；:378 `itemsIndexed(notes, ...)` 渲染的是**整个 notes 列表（含书签）**，:383 `onJump = { onJumpToBookmark(n) }` 对**全部条目**统一用书签回调 → 普通笔记也被赋予 BOOKMARK 类型。resolver 侧口径见 `AnnotationNavigationTarget.kt:75-77`：`note:{id}` 只匹配 `kind != "bookmark"`，`bookmark:{id}` 只匹配 `kind == "bookmark"`，**不回退另一张表** |

### 1.3 根因一句话

**生产宿主只把书签传给了 `TocSheet`，而 `TocSheet` 的 `notes` 参数带 `emptyList()` 默认值 —— 漏接线既不报错也不显形，页面稳定显示空态；同时 `NotesTabContent` 的数据模型只装笔记、装不下高亮，普通笔记的回源还借用了书签类型。**

### 1.4 为什么长期未被发现

三层原因叠加，每一层单独看都不像缺陷：

1. **默认值兜底**：`notes = emptyList()` 让「忘了传」编译通过、运行无异常；
2. **空态文案自洽**：页面显示「暂无划线与笔记」在语法上完全正常，不像崩溃那样刺眼；
3. **数据源本身正确**：DAO 与 ViewModel 都在正常工作，只有最后一跳没接 —— 这也是为什么本轮**不需要**碰 DAO/DB/ViewModel（任务书明确禁止，且事实也确实不需要）。

---

## 2. RED → GREEN 证据

### 2.1 RED（修改生产代码之前）

新建两个测试文件后先跑编译，确认引用的生产符号尚不存在：

```
./gradlew --console=plain :app:compileDebugUnitTestKotlin
e: .../sheets/TocNoteEntriesTest.kt:49:23 Unresolved reference 'tocNoteEntries'.
e: .../sheets/TocNoteEntriesTest.kt:55:54 Unresolved reference 'TocNoteEntry'.
e: .../sheets/TocNoteEntriesTest.kt:68:19 Unresolved reference 'navigationTarget'.
... （共 20+ 条，全部指向尚未实现的 tocNoteEntries / TocNoteEntry / navigationTarget）
BUILD FAILED
```

**为什么这是有效的 RED**：`tocNoteEntries` 与 `TocNoteEntry` 是本轮**新增**的生产契约，
它们不存在正是「笔记 Tab 没有统一数据源」的编译期表现。测试先于实现写出，
因此 RED 是真实的第一步而非事后补记。

随后（生产代码已就位、但**守卫断言尚未对齐实现**时）又出现一次真实 RED：

```
TocNotesWiringGuardTest > the host passes non bookmark notes into the toc sheet FAILED
TocNotesWiringGuardTest > every toc jump callback uses a typed navigation target FAILED
TocNotesWiringGuardTest > the host keeps bookmarks separate from the notes tab source FAILED
TocNotesWiringGuardTest > the notes sheet routes plain notes through NOTE not BOOKMARK FAILED
31 tests completed, 4 failed
```

这 4 条失败暴露的是**我自己的守卫写错了**：我按「内联字面量」写断言
（例如要求源码含 `kind != "bookmark"`），而实现改用了 `tocNoteEntries(...)` 与
`NOTE_KIND_BOOKMARK` 常量。已把守卫改为断言**真实契约**（实参必须由
`tocNoteEntries(highlights = inputs.highlights, notes = inputs.notes)` 产出），
而不是断言某种写法。这一点如实记录：**守卫必须钉契约，不能钉写法**。

### 2.2 GREEN

```
./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.ui.screen.reader.sheets.TocNoteEntriesTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.TocNotesWiringGuardTest" \
 --tests "com.creationreadingassistant.feature.annotations.AnnotationNavigationTargetTest"
BUILD SUCCESSFUL
```

| 测试类 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `ui.screen.reader.sheets.TocNoteEntriesTest`（**A01 新增**） | 16 | 0 | 0 | 0 |
| `ui.screen.reader.TocNotesWiringGuardTest`（**A01 新增**） | 9 | 0 | 0 | 0 |
| `feature.annotations.AnnotationNavigationTargetTest`（既有） | 9 | 0 | 0 | 0 |
| **合计** | **34** | **0** | **0** | **0** |

---

## 3. 修改文件与每个 hunk 的目的

### 3.1 产品文件（5 个）

| 文件 | hunk 目的 |
|---|---|
| `sheets/ReaderTocBookmarkList.kt` | ①新增 `TocNoteEntry` sealed 类型（Highlight / Note，各自携带 `type`、`rawId`、`navigationTarget`、`listKey`）；②新增 `highlightNavigationTarget` / `noteNavigationTarget` / `noteAnnotationType` / `NOTE_KIND_BOOKMARK` / `tocNoteEntries`；③`NotesTabContent` 签名由 `List<NoteEntity>` 改为 `List<TocNoteEntry>`，空态文案**未改**，LazyColumn key 由 `"note_${id}"` 改为 `entry.listKey`（类型前缀）；④新增 `HighlightMicroIsland` 渲染高亮条目（与 `NoteMicroIsland` 同构，只换图标/色调）；⑤新增私有 `JumpPill`（仅本轮新增卡片使用，不回改既有两处内联胶囊） |
| `sheets/ReaderTocSheet.kt` | ①`notes` / `onPickNote` 的类型改为 `List<TocNoteEntry>` / `(TocNoteEntry) -> Unit`；②**去掉这两个参数的默认值**（漏接线必须编译失败）；③`ReaderTocTab.NOTES` 分支改为传 `entries = notes` |
| `ReaderSheetHost.kt` | ①`ReaderSheetHostCallbacks` 的两条裸 id 回调 `onJumpToHighlight(String)` / `onJumpToBookmark(String)` **合并为** `onJumpToTypedAnnotation(String)`（只收 typed 目标）；②`TocSheet(...)` 显式传 `notes = tocNoteEntries(highlights = inputs.highlights, notes = inputs.notes)` 与 `onPickNote = { entry -> ...entry.navigationTarget }`；③`bookmarks` 改用 `NOTE_KIND_BOOKMARK` 常量、`onPickBookmark` 改用 `noteNavigationTarget(bm)`；④`NotesSheet(...)` 的 `onJumpToBookmark` 改为 `onJumpToNote`，并按类型分派 `highlightNavigationTarget(h)` / `noteNavigationTarget(n)` |
| `ReaderLayerBuilders.kt` | `onJumpToHighlight` + `onJumpToBookmark` 两个 builder 回调合并为 `onJumpToTypedAnnotation`（直接把 typed 目标写入 `pendingHighlightId`）；删除因此不再使用的 `AnnotationType` / `navigationTargetId` import |
| `sheets/ReaderNotesSheet.kt` | 参数 `onJumpToBookmark: (NoteEntity) -> Unit` 更名为 `onJumpToNote`（语义由「书签」改为「笔记/书签，按 kind 分派」），KDoc 写明调用方须用 `noteAnnotationType` 分派；`itemsIndexed(notes, ...)` 的 `onJump` 改为 `onJumpToNote(n)` |

### 3.2 测试文件

| 文件 | 类型 |
|---|---|
| `test/.../reader/sheets/TocNoteEntriesTest.kt` | **新增**（16 项，行为/契约测试） |
| `test/.../reader/TocNotesWiringGuardTest.kt` | **新增**（9 项，结构守卫） |
| `androidTest/.../reader/sheets/ReaderTocSheetTest.kt` | 修改：4 处既有 `TocSheet(...)` 调用补 `notes` / `onPickNote`（签名收紧的必然结果）+ 新增 3 项 A01 instrumentation 测试 |
| `androidTest/.../reader/sheets/ReaderRulesSheetTest.kt` | 修改：2 处既有 `TocSheet(...)` 调用补同样两个实参 → **SEAM REQUEST，见 §10** |

**未修改**：`ReaderTocModels.kt`（哈希逐字节一致）、`AnnotationNavigationTargetTest.kt`（既有测试未动，作为第三方哨兵继续验证 resolver 口径）、DAO / Room schema / Repository / ViewModel / `InspirationDao.kt`。

---

## 4. typed navigation 三种类型的最终映射

| 条目来源 | 表 | 判定 | typed 目标 | 生成点 |
|---|---|---|---|---|
| 高亮 | `highlights` | 恒为高亮 | `highlight:{id}` | `highlightNavigationTarget(h)` → `navigationTargetId(AnnotationType.HIGHLIGHT, h.id)` |
| 普通笔记 | `notes`，`kind != "bookmark"` | `noteAnnotationType` 返回 `NOTE` | `note:{id}` | `noteNavigationTarget(n)` |
| 书签 | `notes`，`kind == "bookmark"` | `noteAnnotationType` 返回 `BOOKMARK` | `bookmark:{id}` | `noteNavigationTarget(n)` |

三处调用点全部经 `onJumpToTypedAnnotation` 写入 `pendingHighlightId`：

| 调用点 | 表达式 |
|---|---|
| TocSheet 书签 Tab | `sheetCallbacks.onJumpToTypedAnnotation(noteNavigationTarget(bm))` |
| TocSheet 笔记 Tab | `sheetCallbacks.onJumpToTypedAnnotation(entry.navigationTarget)`（条目自带，类型由条目决定） |
| NotesSheet 高亮 | `sheetCallbacks.onJumpToTypedAnnotation(highlightNavigationTarget(h))` |
| NotesSheet 笔记/书签 | `sheetCallbacks.onJumpToTypedAnnotation(noteNavigationTarget(n))` |

**结构性保证**：`ReaderSheetHostCallbacks` 已**不存在**收裸 rawId 的跳转入口
（守卫测试 `the callback surface no longer exposes raw id jumps` 钉住），
因此调用方在类型上无法再传裸 id 去走「先高亮、后笔记」的兼容回退。

消费端不变：`ReaderProgressEffects` 的 `parseAnnotationNavigationTarget` →
`resolveAnnotationTarget` 按类型严格匹配，类型不符或记录不存在时清请求并提示
「无法定位该阅读笔记，记录可能已删除或类型不匹配」。

---

## 5. 高亮 / 普通笔记 / 书签 / 空态矩阵

| 场景 | 笔记 Tab 内容 | 空态 | 书签 Tab | 点击目标 |
|---|---|---|---|---|
| 只有高亮 | 显示高亮（`HighlightMicroIsland`） | **不显示** | 空 | `highlight:{id}` |
| 只有普通笔记 | 显示笔记（`NoteMicroIsland`） | **不显示** | 空 | `note:{id}` |
| 只有书签 | **空** | **显示**（书签不算笔记 Tab 内容） | 显示书签 | `bookmark:{id}` |
| 高亮 + 普通笔记 | 两类都显示 | 不显示 | 空 | 各自类型 |
| 高亮 + 书签 | 只显示高亮 | 不显示 | 显示书签 | 各自类型 |
| 高亮 + 笔记 + 书签 | 显示高亮 + 笔记 | 不显示 | 显示书签 | 各自类型 |
| 三者都空 | 空 | **显示** | 空 | — |
| 跨表同 raw ID（高亮与笔记） | 两条都显示 | 不显示 | — | `highlight:X` 与 `note:X`，**不同** |
| 跨表同 raw ID（高亮与书签） | 只显示高亮 | 不显示 | 显示书签 | `highlight:X` 与 `bookmark:X`，**不同** |

列表 key：`highlight_...` / `note_...` 的实际形式是 `entry.listKey`，即
`highlight:{id}` / `note:{id}` —— **带类型前缀**，跨表相同 UUID 不会冲突。

---

## 6. 测试命令、退出码与 XML 计数

全部在 `android/` 下执行。

### 6.1 编译（三个 source set 全通过）

```bash
./gradlew --console=plain :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin
```
退出码 **0**，`BUILD SUCCESSFUL`。

### 6.2 A01 定向 JVM 测试

```bash
./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.ui.screen.reader.sheets.TocNoteEntriesTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.TocNotesWiringGuardTest" \
 --tests "com.creationreadingassistant.feature.annotations.AnnotationNavigationTargetTest"
```
退出码 **0**。

| 测试类 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `TocNoteEntriesTest` | 16 | 0 | 0 | 0 |
| `TocNotesWiringGuardTest` | 9 | 0 | 0 | 0 |
| `AnnotationNavigationTargetTest` | 9 | 0 | 0 | 0 |
| **合计（3 类）** | **34** | **0** | **0** | **0** |

请求 3 类 == XML 3 个文件，缺失类 0。

### 6.3 标注/阅读器代表性 JVM 回归

```bash
./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.feature.annotations.*" \
 --tests "com.creationreadingassistant.ui.screen.reader.*" \
 --tests "com.creationreadingassistant.ui.viewmodel.*"
```
退出码 **1**（见下方已知失败）。

| 项 | 数 |
|---|---:|
| 测试类 | 116 |
| tests | 1018 |
| failures | **1** |
| errors | 0 |
| skipped | 0 |

**唯一失败：`ui.viewmodel.ProfileWebDavManagerTest > downloadRestore fetches and imports backup json`**
（`IllegalStateException`，JVM 主调度器/Handler 环境）。

- 该文件 mtime = `2026-09-15 03:41:53`，早于本轮窗口（2026-09-21 16:5x–17:25），
  且已是 `M`（工作区既有用户资产），**本轮未触碰**；
- 属 S14 WebDAV 切片，与 A01 零关系；
- 如实记录：**该失败不作为 A01 的通过证据，也不作为 S14 已解决的结论**，归属由 Codex 裁决。

**A01 直接相关子集全部绿**：

| 子集 | 类 | tests | F | E | S |
|---|---:|---:|---:|---:|---:|
| `feature.annotations.*` | 4 | 31 | 0 | 0 | 0 |
| `ui.screen.reader.sheets.*`（含 A01 新类） | 8 | 79 | 0 | 0 | 0 |

### 6.4 Compose instrumentation 测试：**已编译未执行**

`ReaderTocSheetTest.kt` 的 A01 新增 3 项（`notesTabShowsHighlightsInsteadOfEmptyState` /
`notesTabShowsPlainNotesAndKeepsBookmarksOut` / `notesTabShowsEmptyStateOnlyWhenBothSourcesAreEmpty`）
与 4 处既有用例的签名补齐**已通过 `:app:compileDebugAndroidTestKotlin`**，
但本轮**未在设备上执行**（任务书：真机是否需要由 Codex 在 Qoder 复核后决定；
本机安装受 MIUI 限制的历史记录也见既有文档）。

**明确声明：这 3 项是「已编译未执行」，不得写成执行通过。**

### 6.5 行为测试与结构守卫分别计数

| 类别 | 类 | tests |
|---|---|---:|
| **行为 / 契约测试**（真实实体构造 + 真实 resolver 往返，无源码文本） | 1（`TocNoteEntriesTest`）+ `AnnotationNavigationTargetTest` 9 | **25** |
| **结构守卫**（读源文件文本、配平括号定位实参块） | 1（`TocNotesWiringGuardTest`） | **9** |
| 合计 | 3 | **34** |

A01 新增的 25 项中，**16 项是行为/契约测试**（真实 `HighlightEntity`/`NoteEntity`、
真实 `parseAnnotationNavigationTarget` + `resolveAnnotationTarget` 往返），
**9 项是结构守卫**。任务书要求「不写源码字符串测试来冒充全部行为验证」——
本轮的守卫只负责**接线形状**这一件事，Tab 归属与 typed 目标由行为测试覆盖。

---

## 7. 变异验证及恢复证据

三处临时变异，每次变异后从 `/tmp/a01/*.after.bak` 还原并复核哈希。

| 变异 | 内容 | 失败用例 | 结果 |
|---|---|---|---|
| **A** | 宿主 `notes = tocNoteEntries(highlights = inputs.highlights, ...)` 改为 `highlights = emptyList()`（模拟重新不传 highlights） | `the host passes the book highlights into the toc sheet`、`the host passes non bookmark notes into the toc sheet` | **2 FAILED** ✓ |
| **B** | `NotesSheet` 的 `onJumpToNote` 退回 `navigationTargetId(AnnotationType.BOOKMARK, n.id)`（模拟普通笔记重新借用书签类型） | `the notes sheet routes plain notes through NOTE not BOOKMARK` | **1 FAILED** ✓ |
| **C** | `NotesTabContent` 的 LazyColumn key 由 `entry.listKey` 退回 `entry.rawId`（去掉类型前缀） | `the notes tab list key is the typed target so cross table ids never collide` | **1 FAILED** ✓ |

**恢复证据**：

```
ReaderSheetHost.kt        fe85593afdf0f5872240b964dc63c719dd35d5f37c7fb2059f0822d5ecdd9d83  == /tmp/a01/SheetHost.after.bak
ReaderTocBookmarkList.kt  64d82870f7a54af458d150d06817532204bd1d5502cc382b9a2f7ffde768b9e4  == /tmp/a01/TocBookmarkList.after.bak
```

残留扫描：`grep "navigationTargetId(AnnotationType.BOOKMARK, n.id)\|highlights = emptyList(),"`
在 `ReaderSheetHost.kt` 中**零命中**；恢复后重跑三个 source set 编译与 34 项测试，全绿。

---

## 8. before / after 同文件集证据

同一文件清单、同一命令（`sha256sum`），仓库外临时目录 `/tmp/a01/`。

| 文件 | before(前16) | after(前16) | 判定 |
|---|---|---|---|
| `main/.../sheets/ReaderTocBookmarkList.kt` | `6a5ef627a8ccc64e` | `64d82870f7a54af4` | **CHANGED** |
| `main/.../sheets/ReaderTocSheet.kt` | `5f07a75dc92470bc` | `96d672016cc3f4db` | **CHANGED** |
| `main/.../ReaderSheetHost.kt` | `b330a66d7549be1d` | `fe85593afdf0f587` | **CHANGED** |
| `main/.../ReaderLayerBuilders.kt` | `74a07e46765355fa` | `d0eab583fbda9b8e` | **CHANGED** |
| `main/.../sheets/ReaderNotesSheet.kt` | `0c4e96dbcdecb487` | `76046e3f70a4144c` | **CHANGED** |
| `main/.../sheets/ReaderTocModels.kt` | `82659b04553c2a6c` | `82659b04553c2a6c` | IDENTICAL |
| `androidTest/.../sheets/ReaderTocSheetTest.kt` | `6ae53f7edfa23843` | `e983009b494152c9` | **CHANGED** |
| `test/.../annotations/AnnotationNavigationTargetTest.kt` | `e7eb3016bc722690` | `e7eb3016bc722690` | IDENTICAL |

**新增文件（未跟踪）完整哈希**：

```
3905e29f0cafe55ee3cbb49cae2b271ff3617a76315be009b4982d02981a306e  test/.../sheets/TocNoteEntriesTest.kt
9047ad2d08bb7431c23c2e274f0e6352148c995767bd8120f841ee37617be80f  test/.../TocNotesWiringGuardTest.kt
```

**未跟踪文件的补丁证据（不使用 `git add -N`）**：

```
TocNoteEntriesTest.kt      -> git diff --no-index: 1 file changed, 281 insertions(+)
TocNotesWiringGuardTest.kt -> git diff --no-index: 1 file changed, 195 insertions(+)
```

**边界外但被签名收紧波及的文件**：

```
androidTest/.../sheets/ReaderRulesSheetTest.kt  before 未登记  after 695080a759dec26d
```

---

## 9. 未覆盖项

| 项 | 状态 | 说明 |
|---|---|---|
| Compose instrumentation 渲染验证 | **已编译未执行** | 3 项 A01 instrumentation 测试与 4 处签名补齐通过编译；未在设备执行 |
| 真机「目录与标记 → 笔记」实际观感 | **未验证** | 需 Qoder 真机复核：高亮条目视觉、跳转落点、书签不重复出现 |
| 高亮条目点击后的实际跳转落点 | **未验证** | JVM 只验证 typed 目标字符串与 resolver 往返；真实定位需真机 |
| 空态文案是否需再改 | 未改 | 文案「暂无划线与笔记」在修复后与内容一致（两类都展示），测试已证明不再矛盾 |
| `ReaderRulesSheetTest.kt` 越界 | **SEAM REQUEST** | 见 §10 |
| 书签 Tab 的 `BookmarkMicroIsland` 与新增 `HighlightMicroIsland` 视觉一致性 | 未验证 | 属视觉验收，需真机 |
| `NOTE_KIND_BOOKMARK` 与既有字面量三处并存 | P3 遗留 | 见 §11 Standards |

---

## 10. SEAM REQUEST

**文件**：`android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderRulesSheetTest.kt`

**所需改动**：2 处既有 `TocSheet(...)` 调用补 `notes = emptyList()` 与 `onPickNote = {}`。

**原因**：A01 按任务书要求去掉了 `TocSheet` 的 `notes` / `onPickNote` 默认值
（漏接线必须编译失败），该文件的两个用例调用 `TocSheet(...)` 时未传这两个参数，
因此 `:app:compileDebugAndroidTestKotlin` 失败。**这是签名收紧的必然波及，不是功能改动**：
两个用例只验证「替换净化」入口的显隐，与笔记 Tab 无关。

**已做的处置**：改动是**纯补参**（各 +2 行，注释说明原因），未改任何断言、未改测试意图。
该文件不在任务书允许列表内，故在此声明。**如需回退，请告知**——回退代价是
`compileDebugAndroidTestKotlin` 恢复失败，或改为给 `notes` 保留默认值（但那样 A01 的核心防线失效）。

**影响面**：仅该 androidTest 文件；`main` 与 `test` 不受影响。

---

## 11. Standards 与 Spec 双轴剩余问题

### Standards 轴

**问题数：3**（全部 P3，非阻断）

| 级别 | 数量 | 项 |
|---|---:|---|
| P0/P1/P2 | 0 | — |
| P3 | 3 | ①`"bookmark"` 字面量现有三处（`NOTE_KIND_BOOKMARK`、`AnnotationNavigationTarget.kt`、`AnnotationExport.kt`）—— 后两处在 A01 授权边界外，未一并提取为共享常量；②`JumpPill` 与既有两处内联「跳转」胶囊同形，为控制 diff 未回改既有两处（三份同形代码）；③`TocNotesWiringGuardTest` 是源码文本守卫，证明接线形状但不证明 Compose 渲染结果 |

**Standards 最严重项：P3-③**。A01 的修复有一半在接线层，而接线层只能靠
结构守卫 + 真机；这一分界已在守卫 KDoc 里写明，不冒充运行时证据。

### Spec 轴

**问题数：1**（P3，非阻断）

| 级别 | 数量 | 项 |
|---|---:|---|
| P0/P1/P2 | 0 | — |
| P3 | 1 | 笔记 Tab 内高亮与笔记**并列展示**（高亮在前、笔记在后，各沿用 DAO 的 `created_at DESC`），不是单一时间线。任务书允许分节展示，此处按「同一列表、按类型分派卡片」实现 —— 用户看到的是一串混排卡片，靠图标/色调区分划线与笔记 |

**Spec 最严重项：P3-①**。它不是缺陷，而是本轮的有意取舍：任务书明确
「可以在笔记 Tab 内分别显示『划线』和『笔记』两个小节；不要求强行合并成单一时间线」，
本轮选择了**更小的改动**（一个列表 + 类型化卡片），代价是没有小节标题。
若产品希望分节，需 Codex 裁决后再做（会引入小节头与计数）。

---

## 12. 是否建议交 Qoder 独立复核

**建议：是。**

理由：

1. A01 的**接线层证据止于源码结构守卫**，`snapshotFlow`/Compose 渲染与真实点击跳转
   只能真机确认 —— 这与 F5 的结论一致，本轮不例外；
2. 任务书 §「建议验证 6」明确「真机是否需要由 Codex 在 Qoder 独立复核后决定」；
3. 有一个 SEAM REQUEST 需要 Codex 裁决（`ReaderRulesSheetTest.kt` 补参）。

**定向复核清单（建议给 Qoder）**：

| # | 步骤 | 预期 |
|---:|---|---|
| 1 | 测试 EPUB（已有 3 条未删除高亮）→ 目录与标记 → 笔记 | **不再**显示「暂无划线与笔记」；至少看到 3 条高亮 |
| 2 | 同上，点其中一条高亮 | 跳回正文对应位置（不是跳到书签或笔记） |
| 3 | 同一本书建立一条普通笔记 → 笔记 Tab | 高亮与笔记**都在**，可分别点击 |
| 4 | 书签 Tab 与笔记 Tab 对比 | 同一条书签**只**出现在书签 Tab，笔记 Tab 不重复出现 |
| 5 | 无高亮无笔记的书 → 笔记 Tab | 仍显示「暂无划线与笔记」（空态未被误伤） |
| 6 | 完整「笔记/书摘」Sheet（更多 → 笔记与标注）→ 点普通笔记 | 跳到该笔记位置（修复前会按书签类型解析） |
| 7 | 构造同 raw ID 跨表（若可行） | 不跳错表 |

---

## 13. 明确声明

**本轮没有修改**：

- 数据库 / Room schema / 数据库版本 / 迁移 —— 未触碰 `AppDatabase.kt`、`DatabaseModule.kt`
  （mtime 分别为 `2026-09-16 21:52:09` / `2026-09-16 21:52:20`，均早于本轮窗口）；
- DAO —— 未触碰 `InspirationDao.kt`（含 `HighlightDao` / `NoteDao` 接口，mtime `2026-09-17 14:43:03`）；
- Repository —— 未触碰 `NoteRepository.kt`（mtime `2026-09-19 23:58:14`）；
- ViewModel 数据流 —— 未触碰 `ReaderViewModel.kt`（mtime `2026-09-21 01:10:31`，F4.1 收工值）；
- **未新增任何数据库查询**：笔记 Tab 的数据源完全由既有的 `inputs.highlights` / `inputs.notes` 计算；
- **未复制第二套标注数据源**：`tocNoteEntries` 是纯映射，不查询、不缓存；
- **D-2**（分页设置作用域）—— 未触碰 `ReaderOverride.kt`（mtime `2026-09-18 02:23:08`）；
- **S08–S20** —— 未触碰任何相关文件；
- 未 commit / push / stage / stash / reset / checkout / clean / 全仓格式化；
- 未操作真机或 MuMu；未修改设备上的用户书籍、标注或笔记；未修改测试设备数据库。

**索引与分支状态**：

```
HEAD        = 5aed5939600e491843e3c44453921bd6273a233c   （基线未推进）
git diff --cached --name-only = 0                         （index 为空）
git stash list                = 0
branch      = codex/recover-worktree-20260920
```

---

## 14. 回传

- Agent 与实际模型：**Zcode / `deepseek v4.1Flash`**
- 判定：**PASS**（A01 范围内 8 条任务目标全部达成；无 P0/P1/P2 遗留）
- 根因一句话：**生产宿主只把书签传给了 `TocSheet`，而 `notes` 参数带 `emptyList()` 默认值 —— 漏接线不报错、页面稳定显示空态；同时 `NotesTabContent` 只装得下笔记、装不下高亮，普通笔记的回源还借用了书签类型。**
- 修改文件数量：**5 个产品文件** + 2 个新增测试 + 2 个既有测试修改（其中 1 个属 SEAM REQUEST）
- 测试数量与结果：A01 定向 **3 类 / 34 项全绿**（行为 25 + 守卫 9）；代表性回归 **116 类 / 1018 项**，仅 1 项既有 S14 WebDAV 失败（非本轮改动）；三个 source set 编译全通过
- 报告绝对路径：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\agent-results\agent-result-a01-zcode.md`
- SEAM REQUEST：**有 1 项**（`androidTest/.../sheets/ReaderRulesSheetTest.kt` 补 2 处实参，因签名收紧的必然波及）
- **等待 Codex 主任务复核，未领取下一片。**
