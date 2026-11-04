# A01 独立代码复核 — Qoder

> 复核 Agent：**Qoder**（独立复核方，不采信执行方结论，不代写代码）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收工 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 被复核报告：`docs/coordination/agent-results/agent-result-a01-zcode.md`（Zcode / `deepseek v4.1Flash`）
> 本报告：`docs/coordination/reviews/undated/agent-review-a01-qoder.md`
> 判定：**代码轴 PASS（无 P0/P1/P2 阻断）** —— 建议进入真机验收
> 状态：**等待 Codex 主任务复核**（未领取 D-2 / S08–S20）

---

## 0. 结论速览

| 复核面 | 结果 |
|---|---|
| 根因是否与报告一致 | **一致，且我用基线提交 `git show HEAD:` 独立复现了全部 5 条旧代码事实**（不是采信对方叙述，见 §2） |
| 数据源 | **PASS**：未新增 DAO/查询/缓存，`tocNoteEntries` 是纯映射（§3） |
| Tab 归属 | **PASS**：高亮 + 非书签笔记进笔记 Tab；书签只进书签 Tab；空态判据是"两类都空"；inspiration 未混入（§4） |
| typed navigation | **PASS**：三种前缀各自生成、跨表同 rawId 不冲突、消费端严格按类型解析且**不回退另一张表**（§5） |
| API 与回调 | **PASS**：`ReaderSheetHostCallbacks` 只剩 `onJumpToTypedAnnotation(String)`；生产/测试 9 个 `TocSheet(` 调用点全部显式传 `notes`/`onPickNote`，并由 `compileDebugAndroidTestKotlin` 真实重编译作类型级证明（§6） |
| 修改范围 | **PASS**：A01 窗口内 9 个文件（5 产品 + 2 新测试 + 2 androidTest）；DAO/DB/schema/Repository/ViewModel/D-2 文件全部未动（§7） |
| SEAM REQUEST | **接受**：`ReaderRulesSheetTest.kt` 对基线的 diff 只有 +7 行、全是补实参与注释，零断言改动（§8） |
| 独立 JVM | 定向 **3 类 / 34 tests / 0 F / 0 E / 0 S / 退出码 0**；代表性回归 **116 类 / 1018 tests / 0 F / 0 E / 0 S / 退出码 0**（§9） |
| 与对方数字的差异 | **一处，且对我方有利**：Zcode 回归里有 1 项 `ProfileWebDavManagerTest` 失败；我三次跑（含全量回归内）该类 **11 tests / 0 F**。判为 flaky / 环境相关，**不算 A01 通过证据，也不归因给 A01**（§9.4） |
| Standards | 5 项，全 P3，最严重 = **测试文件归属存疑的一处断言新增（S-P3-⑤，需 Codex 一句话确认）** |
| Spec | 2 项，全 P3，最严重 = **笔记 Tab 是"高亮段 + 笔记段"拼接、无小节标题，且划线卡片与书签卡片共用同一图标**（§11） |
| 是否进入真机验收 | **是** |

---

## 1. 取证方法（为什么不能直接用 `git status`）

本仓库工作区在开工时已有 **351 条未提交改动**（恢复分支既有资产），因此：

- **不能用 `git diff` 判断 A01 的改动面** —— 它把 A01 与更早的未提交工作混在一起（例如 `ReaderSheetHost.kt` 对基线的 diff 是 183 行，其中大部分不是 A01）；
- 我改用三条互相独立的证据链交叉定位：
  1. **文件级 SHA256** 与 Zcode `before.sha256` / `after.sha256` 逐一比对（§7.1）；
  2. **mtime 窗口**：`find android/app/src -name '*.kt' -newermt '2026-09-21 17:00' ! -newermt '2026-09-21 17:30'` 精确圈出 9 个文件（§7.2）；
  3. **基线提交对照**：`git show HEAD:<path>` 读取 A01 之前的**已提交**内容，用来独立验证根因，而不是读对方的"修改前"叙述（§2）。
- 结论：A01 的真实改动面 = mtime 窗口内的 9 个文件，且这 9 个文件的当前哈希与对方 after 值**逐字节一致**。

---

## 2. 根因复核：用基线提交独立验证，不采信对方叙述

| # | 报告声明 | 我的独立证据（`git show HEAD:` 基线内容） | 结论 |
|---:|---|---|---|
| 1 | 宿主只把书签传给 `TocSheet` | 基线 `ReaderSheetHost.kt` 的 `TocSheet(` 实参块内只有 `bookmarks = inputs.notes.filter { it.kind == "bookmark" }`（:282）与 `onPickBookmark = { bm -> sheetCallbacks.onJumpToBookmark(bm.id) }`（:283），**不存在 `notes =`** | ✅ 成立 |
| 2 | `notes` 带 `emptyList()` 默认值，漏接线不报错 | 基线 `ReaderTocSheet.kt:84` `notes: List<NoteEntity> = emptyList()`、`:85` `onPickNote: (NoteEntity) -> Unit = {}` | ✅ 成立 |
| 3 | `NotesTabContent` 装不下高亮 | 基线 `ReaderTocBookmarkList.kt:78-82` `NotesTabContent(notes: List<NoteEntity>, onPickNote: (NoteEntity) -> Unit, ...)` —— 高亮**没有参数入口** | ✅ 成立 |
| 4 | 普通笔记借用书签回调 | 基线 `ReaderNotesSheet.kt:78` `onJumpToBookmark: (NoteEntity) -> Unit`，`:338` `onJump = { onJumpToBookmark(n) }` 对"笔记/书签"整段统一用书签回调 | ✅ 成立 |
| 5 | 裸 rawId 走「先高亮、后笔记」兼容回退 | 基线 `onJumpToBookmark(bm.id)` 传裸 id；现网 `AnnotationNavigationTarget.kt:73-74` 的 `typed == null` 分支仍是 `highlights.firstOrNull … ?: notes.firstOrNull` | ✅ 成立 |
| 6 | 数据源本来就正确 | 基线即已有 `HighlightDao.observeByBook` / `NoteDao.observeByBook`（`data/local/dao/InspirationDao.kt`，均带 `deleted_at IS NULL`），且 `ReaderSheetHost` 的 `NotesSheet(...)` 分支已在用 `inputs.highlights` / `inputs.notes` | ✅ 成立 —— **A01 确实不需要碰 DAO/DB/ViewModel** |

**根因一句话与对方一致**：漏接线 + 默认值兜底 + 数据模型装不下第二类内容 + 回源类型借用，四层叠加以页面稳定显示空态的形式表现，且不产生任何异常。

---

## 3. 数据源

```kotlin
internal fun tocNoteEntries(
    highlights: List<HighlightEntity>,
    notes: List<NoteEntity>,
): List<TocNoteEntry> =
    highlights.map(TocNoteEntry::Highlight) +
        notes.filter { it.kind != NOTE_KIND_BOOKMARK }.map(TocNoteEntry::Note)
```

| 检查项 | 结果 |
|---|---|
| 是否新增 DAO 查询 | **否**。`InspirationDao.kt` mtime `2026-09-17 14:43:03`，早于 A01 窗口；`grep` 全仓无新增 `@Query` |
| 是否新增缓存 / 第二套数据源 | **否**。`tocNoteEntries` 不查询、不排序、不去重、不持有状态（KDoc 亦如此声明，与实现一致） |
| 是否只做纯映射 | **是**。输入两个 List，输出一个 List，无副作用 |
| 数据是否来自既有 `inputs.highlights` / `inputs.notes` | **是**。`ReaderSheetHost.kt:339-342` 实参即 `highlights = inputs.highlights, notes = inputs.notes` |
| 顺序 | 沿用入参顺序（DAO 的 `ORDER BY created_at DESC`），`TocNoteEntriesTest.entry order follows the incoming list order` 钉住；行为测试断言为 `h2,h1,n2,n1` —— **高亮段整体在前、笔记段在后**，见 Spec-P3-① |

---

## 4. Tab 归属矩阵（代码 + 行为测试双向核对）

| 场景 | 笔记 Tab | 空态 | 书签 Tab | 我的核对位置 |
|---|---|---|---|---|
| 只有高亮 | 高亮 | 不显示 | 空 | `tocNoteEntries` + `highlights alone populate…` |
| 只有普通笔记 | 笔记 | 不显示 | 空 | `plain notes alone populate…` |
| 只有书签 | **空** | **显示** | 书签 | `bookmarks never enter the notes tab` / `the empty state is produced only when both sources are empty` |
| 高亮 + 普通笔记 | 两类都在 | 不显示 | 空 | `highlights and plain notes are both shown` |
| 高亮 + 书签 | 只有高亮 | 不显示 | 书签 | `a bookmark sharing a raw id with a highlight still stays out…` |
| 三者都有 | 高亮 + 笔记 | 不显示 | 书签 | 上述组合覆盖 |
| 全空 | 空 | 显示 | 空 | `tocNoteEntries(emptyList(), emptyList()).isEmpty()` |

补充核对：

- **书签排除是双层的**：宿主侧 `bookmarks = inputs.notes.filter { it.kind == NOTE_KIND_BOOKMARK }`（取补集给书签 Tab），`tocNoteEntries` 内部再 `filter { it.kind != NOTE_KIND_BOOKMARK }`。两处用同一常量，方向相反 —— 幂等防线，不是重复 bug。
- **空态判据正确**：`NotesTabContent` 的 `if (entries.isEmpty())` 中 `entries` 已只含"高亮 + 非书签笔记"，所以"只有书签"时笔记 Tab 显示空态是**符合产品语义**的（书签不属于"划线与笔记"）。这一点容易被误判为 bug，我确认它是对的。
- **inspiration 未混入**：`grep -i inspiration` 在 `ReaderTocSheet.kt` 与 `ReaderTocBookmarkList.kt` 中**零命中**；`TocNoteEntry` 只有 `Highlight` / `Note` 两个分支。

---

## 5. typed navigation 调用链（三条端到端）

### 5.1 生成端

| 条目 | 生成函数 | 结果 |
|---|---|---|
| 高亮 | `highlightNavigationTarget(h)` = `navigationTargetId(HIGHLIGHT, h.id)` | `highlight:{id}` |
| 普通笔记 | `noteNavigationTarget(n)` → `noteAnnotationType(n)` = `NOTE`（`kind != "bookmark"`） | `note:{id}` |
| 书签 | `noteNavigationTarget(bm)` → `noteAnnotationType(bm)` = `BOOKMARK` | `bookmark:{id}` |

类型判定集中在 `noteAnnotationType` 一处，调用方不再各自写 `if (kind == "bookmark")`。✅

### 5.2 四个跳转调用点（全部实读源码确认）

| # | 位置 | 表达式 |
|---:|---|---|
| 1 | `ReaderSheetHost.kt:322` TocSheet 书签 Tab | `onPickBookmark = { bm -> sheetCallbacks.onJumpToTypedAnnotation(noteNavigationTarget(bm)) }` |
| 2 | `ReaderSheetHost.kt:344-346` TocSheet 笔记 Tab | `onPickNote = { entry -> sheetCallbacks.onJumpToTypedAnnotation(entry.navigationTarget) }` |
| 3 | `ReaderSheetHost.kt:426-428` NotesSheet 高亮 | `onJumpToHighlight = { h -> …onJumpToTypedAnnotation(highlightNavigationTarget(h)) }` |
| 4 | `ReaderSheetHost.kt:429-431` NotesSheet 笔记/书签 | `onJumpToNote = { n -> …onJumpToTypedAnnotation(noteNavigationTarget(n)) }` |

**第 4 条即 A01 缺陷 6 的修复点**：`ReaderNotesSheet.kt:385` 现为 `onJump = { onJumpToNote(n) }`，基线是 `onJump = { onJumpToBookmark(n) }`。参数也已从 `onJumpToBookmark` 更名为 `onJumpToNote`，KDoc 写明"调用方须按 `noteAnnotationType` 分派"。✅

### 5.3 消费端

```kotlin
// ReaderProgressEffects.kt:467-475
val typed = parseAnnotationNavigationTarget(hid)
val target = resolveAnnotationTarget(typed, typed?.rawId ?: hid, highlights, notes)
if (target == null) { pendingHighlightIdIdState.value = null; if (typed != null) showNotice("无法定位该阅读笔记，记录可能已删除或类型不匹配"); return@LaunchedEffect }
```

`resolveAnnotationTarget`（`AnnotationNavigationTarget.kt:67-79`）：`highlight:` 只匹配 highlights；`note:` 只匹配 `kind != "bookmark"` 的 notes；`bookmark:` 只匹配 `kind == "bookmark"`；**类型不符不回退另一张表**。裸 rawId（`typed == null`）才保留旧兼容语义 —— 而现在生产链上已不存在裸 rawId 入口（§6）。

### 5.4 跨表同 rawId

`TocNoteEntry.listKey` 直接等于 `navigationTarget`（`highlight:same-id` vs `note:same-id`），因此 LazyColumn key 与跳转目标同源、同前缀。行为测试 `the same raw id across tables yields distinct list keys` / `…keeps its own typed target` / `a plain note target never resolves against the highlight table` 三条覆盖，且第三条是**真实调用 `parseAnnotationNavigationTarget` + `resolveAnnotationTarget` 的往返**，不是字符串比对。✅

---

## 6. API 与回调

| 检查项 | 结果 | 证据 |
|---|---|---|
| `ReaderSheetHostCallbacks` 是否只收 typed 目标 | **是**。基线的 `onJumpToHighlight: (String) -> Unit` / `onJumpToBookmark: (String) -> Unit` 已合并为 `onJumpToTypedAnnotation: (String) -> Unit`（`ReaderSheetHost.kt:177`） | 全仓 `grep "val onJumpToHighlight: (String)\|val onJumpToBookmark: (String)"` 零命中 |
| 是否仍有调用点把裸 rawId 传给 typed 回调 | **否**。四个调用点全部经 `highlightNavigationTarget` / `noteNavigationTarget` / `entry.navigationTarget`（§5.2） | 逐行实读 |
| 残留的 `onJumpToHighlight` 名字是否安全 | **安全**。`ReaderNotesSheet.kt:78` 的 `onJumpToHighlight: (HighlightEntity) -> Unit` 收的是**实体**而非裸 id，由宿主负责转 typed；这是 NotesSheet 自己的组件参数，不是宿主回调面 | 实读两处 |
| `TocSheet` 去默认值后所有调用点是否显式传参 | **是，且有类型级证明**：生产 1 处（`ReaderSheetHost.kt:302`）+ androidTest 9 处（`ReaderTocSheetTest.kt` 7 处、`ReaderRulesSheetTest.kt` 2 处）全部含 `notes =` 与 `onPickNote =`；我**真实重跑** `:app:compileDebugAndroidTestKotlin --rerun`（1 executed，非 UP-TO-DATE）→ BUILD SUCCESSFUL。若任一调用点缺参，Kotlin 会因缺必填实参编译失败，因此编译成功本身就是"全部显式传参"的证据，不必依赖源码字符串守卫 | §9.2 |
| 是否只靠源码字符串守卫 | **否**。接线"形状"由守卫钉；"是否真的传对参数"由 Kotlin 类型系统 + 真实重编译保证 | — |

---

## 7. 修改范围

### 7.1 文件级 SHA256（我的实测 vs 对方声称）

| 文件 | 我的实测（前 16） | 对方 after | 判定 |
|---|---|---|---|
| `sheets/ReaderTocBookmarkList.kt` | `64d82870f7a54af4` | `64d82870f7a54af4` | ✅ CHANGED |
| `sheets/ReaderTocSheet.kt` | `96d672016cc3f4db` | `96d672016cc3f4db` | ✅ CHANGED |
| `ReaderSheetHost.kt` | `fe85593afdf0f587` | `fe85593afdf0f587` | ✅ CHANGED |
| `ReaderLayerBuilders.kt` | `d0eab583fbda9b8e` | `d0eab583fbda9b8e` | ✅ CHANGED |
| `sheets/ReaderNotesSheet.kt` | `76046e3f70a4144c` | `76046e3f70a4144c` | ✅ CHANGED |
| `sheets/ReaderTocModels.kt` | `82659b04553c2a6c` | `82659b04553c2a6c` | ✅ **IDENTICAL**（未动） |
| `test/.../AnnotationNavigationTargetTest.kt` | `e7eb3016bc722690` | `e7eb3016bc722690` | ✅ **IDENTICAL**（既有哨兵测试未被改，仍独立验证 resolver 口径） |
| `test/.../sheets/TocNoteEntriesTest.kt` | `3905e29f0cafe55e…` | 同 | ✅ 新增 |
| `test/.../TocNotesWiringGuardTest.kt` | `9047ad2d08bb7431…` | 同 | ✅ 新增 |
| `androidTest/.../ReaderTocSheetTest.kt` | `e983009b494152c9` | 同 | ✅ CHANGED |
| `androidTest/.../ReaderRulesSheetTest.kt` | `695080a759dec26d` | 同 | ✅ CHANGED（SEAM，§8） |

### 7.2 A01 窗口内的全部改动（mtime 17:00–17:30）

```
17:08 test/.../sheets/TocNoteEntriesTest.kt
17:10 main/.../sheets/ReaderTocSheet.kt
17:12 main/.../sheets/ReaderNotesSheet.kt
17:13 main/.../ReaderLayerBuilders.kt
17:15 androidTest/.../sheets/ReaderRulesSheetTest.kt
17:17 androidTest/.../sheets/ReaderTocSheetTest.kt
17:18 test/.../TocNotesWiringGuardTest.kt
17:22 main/.../ReaderSheetHost.kt
17:22 main/.../sheets/ReaderTocBookmarkList.kt
```

**恰好 9 个文件，与 §7.1 完全吻合，窗口内无任何其他 `.kt` 被改。**

### 7.3 禁改面（独立 mtime 核对，全部早于 A01 窗口）

| 文件 | mtime | 归属 |
|---|---|---|
| `data/local/AppDatabase.kt` | 2026-09-16 21:52:09 | DB schema —— 未动 |
| `data/local/dao/InspirationDao.kt`（含 `HighlightDao`/`NoteDao`） | 2026-09-17 14:43:03 | DAO —— 未动 |
| `data/repository/NoteRepository.kt` | 2026-09-19 23:58:14 | Repository —— 未动 |
| `data/settings/ReaderOverride.kt` | 2026-09-18 02:23:08 | **D-2 根因文件** —— 未动 |
| `ui/viewmodel/ReaderViewModel.kt` | 2026-09-21 01:10:31 | ViewModel —— 未动（F4.1 收工值，早于 A01） |

**未夹带 DB/schema/DAO/Repository/ViewModel/D-2/S08–S20：确认。**

---

## 8. SEAM REQUEST 判定：**接受**

```
$ git diff --stat -- .../androidTest/.../sheets/ReaderRulesSheetTest.kt
 1 file changed, 7 insertions(+)
```

该文件对基线的 diff **只有 7 行新增、0 删除**，逐行内容为：

- `:105` 后 `notes = emptyList(),` + `onPickNote = {},`（含 2 行说明注释）
- `:126` 后 `notes = emptyList(),` + `onPickNote = {},`（含 1 行说明注释）

判定要点：

1. 该文件对基线**没有其他未提交改动**，所以这 7 行 100% 是 A01 造成的，不存在归属混淆；
2. **零断言改动、零测试意图改动** —— 两个用例仍只验证"替换净化"入口显隐；
3. 补参是 `TocSheet` 签名收紧的**必然波及**，不是功能改动；
4. 这正是 A01 核心防线（漏接线必须编译失败）应有的代价。

**结论：接受，无需回退。** 回退的两种替代方案（恢复默认值 / 让 androidTest 编译失败）都会削弱 A01 的修复价值。

---

## 9. 独立测试结果（全部我自己跑，不引用对方输出）

命令均在 `android/` 下执行，全部带 `--rerun`。

### 9.1 定向 JVM（任务书要求的 3 类）

```
./gradlew.bat --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.ui.screen.reader.sheets.TocNoteEntriesTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.TocNotesWiringGuardTest" \
 --tests "com.creationreadingassistant.feature.annotations.AnnotationNavigationTargetTest"
BUILD SUCCESSFUL in 10s   退出码 0   （17:50:08 XML）
```

| 测试类 | tests | F | E | S | 类别 |
|---|---:|---:|---:|---:|---|
| `TocNoteEntriesTest`（A01 新增） | 16 | 0 | 0 | 0 | **行为/契约** |
| `TocNotesWiringGuardTest`（A01 新增） | 9 | 0 | 0 | 0 | **结构守卫** |
| `AnnotationNavigationTargetTest`（既有） | 9 | 0 | 0 | 0 | 行为（resolver 口径哨兵） |
| **合计** | **34** | **0** | **0** | **0** | 请求 3 类 == XML 3 文件，缺失 0 |

与对方声称的 34 项**逐类一致**。

### 9.2 编译（强制重跑，不接受 UP-TO-DATE）

```
./gradlew.bat --console=plain :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --rerun   → 退出码 0
./gradlew.bat --console=plain :app:compileDebugKotlin --rerun                                      → 1 executed, BUILD SUCCESSFUL in 1m 1s, 退出码 0
```

主源码集与 androidTest 源码集**均真实重新编译通过**（后者显示 `> Task :app:compileDebugAndroidTestKotlin` 无 UP-TO-DATE 标记）。这是 §6 "所有 `TocSheet` 调用点显式传参"的类型级证据。

### 9.3 代表性回归

```
./gradlew.bat --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.feature.annotations.*" \
 --tests "com.creationreadingassistant.ui.screen.reader.*" \
 --tests "com.creationreadingassistant.ui.viewmodel.*"
BUILD SUCCESSFUL in 1m 10s   退出码 0   （17:57:34 XML）
```

| 项 | 我的计数 | 对方计数 |
|---|---:|---:|
| 测试类 | **116** | 116 ✅ |
| tests | **1018** | 1018 ✅ |
| failures | **0** | 1 ⚠️ |
| errors | 0 | 0 |
| skipped | 0 | 0 |

XML 全量已归档到 `docs/qa/evidence/2026-09-21-a01-qoder-device-acceptance/jvm-test-results-a01-regression/`（116 个文件）。

### 9.4 与对方的唯一数字差异（必须如实说明）

对方回归里失败的 `ui.viewmodel.ProfileWebDavManagerTest > downloadRestore fetches and imports backup json`，在我的三次运行中**全部通过**：

| 我的运行 | 该类结果 |
|---|---|
| 116 类全量回归内 | 11 tests / 0 F |
| 单独 `--rerun` 第 1 次 | BUILD SUCCESSFUL |
| 单独 `--rerun` 第 2 次 | BUILD SUCCESSFUL |

判定：该用例是 **flaky / 环境相关**（JVM Main dispatcher / Handler），**不是确定性失败**。

- 我**不**把它算作 A01 的通过证据；
- 我**不**把它归因给 A01（A01 未触碰任何 ViewModel 与协程调度代码，`ReaderViewModel.kt` mtime 早于 A01 窗口）；
- 也**不**据此宣称 S14 WebDAV 已解决 —— 归属与处置仍由 Codex 裁决。

### 9.5 行为测试 / 结构守卫 / 已编译未执行（三分法）

| 类别 | 类 | 项数 | 我的评价 |
|---|---|---:|---|
| **行为 / 契约**（真实实体 + 真实 resolver 往返） | `TocNoteEntriesTest` 16、`AnnotationNavigationTargetTest` 9 | **25** | 质量高：断言的是"哪条进哪个 Tab、生成哪个 typed 目标、跨表会不会串"，并真实调用 `parseAnnotationNavigationTarget` + `resolveAnnotationTarget` 做往返 |
| **结构守卫**（读源文件文本） | `TocNotesWiringGuardTest` | **9** | 必要（证明宿主真的传了 highlights），但**不证明渲染**；KDoc 已自陈此边界 |
| **已编译未执行**（Compose instrumentation） | `ReaderTocSheetTest` 的 3 项 A01 新增 + 7 处签名补齐 | 3 项新用例 | **仅通过编译，未在设备执行**。对方已明确声明未执行，措辞诚实；渲染与点击落点由我的真机阶段负责 |

---

## 10. Standards 轴

**问题数：5，全部 P3，无 P0/P1/P2。**

| ID | 级别 | 项 |
|---|---|---|
| S-P3-① | P3 | 守卫测试仍**部分钉写法而非契约**：如 `args.contains("onPickBookmark = { bm -> sheetCallbacks.onJumpToTypedAnnotation(noteNavigationTarget(bm)) }")`（整段 lambda 字面量）、`"bookmarks = inputs.notes.filter { it.kind == NOTE_KIND_BOOKMARK }"`。任何纯格式化/换行都会让守卫假红。对方 §2.1 自己得出"守卫必须钉契约，不能钉写法"的结论，但只部分落实（`notes = tocNoteEntries(` 那几条已经改成钉契约） |
| S-P3-② | P3 | `TocNoteEntry.type` 在生产代码中**无任何使用点**（我 grep 全 reader 目录零命中），只被测试断言。且 `Note.type` 硬编码 `AnnotationType.NOTE`，而 `navigationTarget` 走 kind 感知的 `noteAnnotationType` —— 一旦将来有书签条目绕过过滤进入笔记 Tab，`type` 与 `navigationTarget` 会互相矛盾。建议要么删掉 `type`，要么让它等于 `noteAnnotationType(note)` |
| S-P3-③ | P3 | `"bookmark"` 字面量现有 **4 处**（`NOTE_KIND_BOOKMARK`、`AnnotationNavigationTarget.kt:76`、`:77`、`AnnotationExport.kt`），对方披露 3 处、少算 1 处。均在 A01 授权边界外，但常量收敛应记为后续项 |
| S-P3-④ | P3 | `JumpPill` 是"跳转"胶囊的**第三份同形实现**（`BookmarkMicroIsland`、`NoteMicroIsland` 各自内联）。为控制 diff 不回改既有两处是有意的取舍，但技术债已 +1 |
| S-P3-⑤ | P3（**需 Codex 确认**） | **测试文件归属存疑的一处断言新增**：`ReaderTocSheetTest.kt` 对基线的 diff 里，除 A01 的补参与 3 项新用例外，还含一条落在既有用例内部的**新断言**：`composeRule.onAllNodesWithText("阅读中").assertCountEquals(0)`（附注释"当前章已由书签线 + 主色序号 + 加粗主色标题标识，右侧不再重复渲染「阅读中」"）。该断言与"划线/笔记索引"主题无关，而对方 §3.2 对该文件的描述只写了"4 处补参 + 新增 3 项"，**未提及这条断言**。`/tmp/a01/` 只保留了 2 个 `.after.bak`，**没有该文件的 before 备份**，因此我无法判定它是 A01 顺带加的、还是更早的未提交视觉去重改动。它不影响产品行为与任何 A01 契约，故列 P3；但若确认属 A01，则对方报告的改动面描述需更正 |

**Standards 最严重项：S-P3-⑤**（测试改动面的可归因性）。它不是代码质量问题，而是"独立复核能否验证改动面声明"的问题 —— 恰好是本次任务书要求我做的第 5 项（"区分 A01 新 hunk 与此前已存在的工作区修改"）里唯一没能干净闭环的点。其余 4 项均为可控技术债。

---

## 11. Spec 轴

**问题数：2，全部 P3。**

| ID | 级别 | 项 |
|---|---|---|
| SP-P3-① | P3 | 笔记 Tab 是**"全部高亮 + 全部非书签笔记"两段拼接**，不是按时间合并的单条时间线，也**没有小节标题**。用户看到的是一串混排卡片，只能靠图标/色调分辨"这是划线还是笔记"。对方已披露为有意取舍（任务书允许分节，本轮选了改动更小的方案）。若产品要分节标题与计数，需 Codex 裁决后另做 |
| SP-P3-② | P3 | **划线卡片与书签卡片共用同一图标** `Icons.Outlined.Bookmark`，只差 pedestal 色调（划线用高亮自身颜色，书签固定 `0xFFD97706`）；而笔记卡片用 `EditNote`。在笔记 Tab 内不会出现歧义（那里没有书签），但"划线"用书签图标在语义上偏弱。另外划线卡片第一行显示的是 `chapter_title`（缺省回退"划线"），而笔记卡片第一行显示 `note.title` —— 同一视觉槽位承载两种语义 |

**Spec 最严重项：SP-P3-①**。它决定用户能否一眼分辨"划线"和"笔记"，属可感知体验取舍，不是缺陷；真机阶段我会重点观察它的实际可读性。

---

## 12. 是否建议进入真机验收

**建议：是。** 代码轴无 P0/P1/P2 阻断，根因、Tab 归属、typed 三链、签名收紧与改动面全部独立成立。

真机必须回答、而 JVM 无法回答的 4 件事：

1. 真实数据下「目录与标记 → 笔记」是否**真的不再显示空态**（守卫只证明宿主传了参，不证明 Compose 渲染出来）；
2. 点击高亮 / 普通笔记 / 书签后的**实际定位落点**是否正确、是否跨书或跨表串位；
3. 完整「笔记/书摘」Sheet 里点普通笔记是否**真的不再按书签处理**（会不会出现"无法定位该阅读笔记…类型不匹配"提示）；
4. 混排列表的**视觉可分辨性**与滚动是否有重复 key 异常。

---

## 13. 边界遵守

| 约束 | 结果 |
|---|---|
| 不修改产品源码 / 测试源码 / 配置 | **未修改任何 `.kt` / `.gradle`**；本轮全部为只读 + 编译 + 跑测试 |
| 不替 Zcode 补代码 | 未补 |
| 不 commit / push / stage / stash / reset / checkout / clean / 全仓格式化 | 未执行；收工 `git diff --cached --name-only` = 0、`git stash list` = 0、HEAD 仍 `5aed593` |
| 不处理 D-2 / S08–S20 | 未处理（仅核对 D-2 根因文件未被夹带） |
| 不采信对方数字 | 全部自己跑：34 / 116 类 1018 项 / 两个 compile 任务 / 11 个文件哈希 |
| 未跑对方 3 个变异（A/B/C） | **有意未跑**：变异需改产品源码，与"不修改任何产品源码或测试源码"冲突。此项登记为本次复核的证据缺口，不以对方变异结果充当我的验证 |

---

## 14. 回传

- Agent：**Qoder**（不披露底层模型标识）
- 代码轴判定：**PASS**（Standards 5 项全 P3；Spec 2 项全 P3；无 P0/P1/P2）
- 根因：**与报告一致**，并已用基线提交 `git show HEAD:` 独立复现 5 条旧代码事实
- 独立 JVM：定向 3 类 / 34 tests / 0 F / 0 E / 0 S；回归 116 类 / 1018 tests / **0 F** / 0 E / 0 S；两个 compile 任务 `--rerun` 真实重编译通过；全部退出码 0
- 与对方唯一差异：`ProfileWebDavManagerTest` 我 3 次全绿，判为 flaky，不算 A01 证据、不归因 A01
- SEAM REQUEST：**接受**（+7 行纯补参，零断言改动）
- 建议：**进入真机定向验收**
- 报告绝对路径：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\reviews\undated\agent-review-a01-qoder.md`
- **等待 Codex 主任务复核，未领取下一片。**
