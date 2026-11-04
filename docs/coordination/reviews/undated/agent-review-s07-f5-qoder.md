# S07-F5 独立复核（代码 / JVM 轴）— Qoder

> 复核 Agent：Qoder　|　仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`　|　固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 复核时 `git rev-parse HEAD` = `5aed5939600e491843e3c44453921bd6273a233c`（基线未推进）
> 被复核对象：`docs/coordination/agent-results/agent-result-s07-f5-zcode.md`（Zcode / deepseek v4.1Flash）
> 缺陷来源：`docs/qa/2026-09-21-s07-qoder-device-acceptance.md` 的 D-1、D-3（均 P2）
> 性质：**只复核，不修复**。未修改任何产品源码或测试源码。
> 真机轴结果另见：`docs/qa/2026-09/2026-09-21-s07-f5-qoder-device-retest.md`
> 状态：**等待 Codex 主任务复核**

---

## 0. 结论速览

| 轴 | 结论 |
|---|---|
| D-1 修复位置与判据 | **合格**。修在 selection eligibility policy（`markdownUnitIsBlockSelectable`），不是保存层补丁、不是 UI 隐藏工具栏；判据读 **span 类型**，零文本猜测 |
| D-3 修复位置与判据 | **合格**。宿主层单点判据 + 纯策略函数 + 跃变去重门；无固定 `delay`、无 per-item 互清；`DisposableEffect` 降级为最终防线 |
| 独立 JVM 复跑 | **与 Zcode 声明逐项一致**：验证集 12 类 / 145 tests / 0F / 0E / 0S；回归 205 类 / 1953 tests / 0F / 0E / 0S；两次均 `--rerun`、退出码 0 |
| 改动面 | **干净**。F5 窗口内只有 2 个产品文件 + 3 个新测试；无 D-2 / D-7 / 设置页 / S08–S20 夹带；`git diff --cached` = 0，`git stash list` = 0 |
| 代码轴判定 | **PASS（代码与 JVM 证据成立）**；D-1 / D-3 是否真正关闭由真机轴决定 |
| 新发现 | 3 项 P3 + 1 项需产品确认的显式降级；无新增 P0/P1/P2 |

---

## 1. D-1 复核：判据是否落在正确的层

### 1.1 根因链独立复核（不采信报告叙述，逐跳读代码）

| 跳 | 代码位置 | 复核结果 |
|---|---|---|
| parser 把 alt 写进 canonicalText | `feature/reader/doc/MarkdownDocument.kt`（`is MdInline.Image -> alt`） | 成立。这是 D-1 的**必要前提**，本轮未改 parser（正确：改 parser 会牵动 offset 空间与既有 locator） |
| 图片不是独立 kind | `MarkdownRenderKind` 只有 `HEADING/PARAGRAPH/LIST_ITEM/TASK_ITEM/CODE_BLOCK/HORIZONTAL_RULE/TABLE`；图片是 `MarkdownRenderSpan.Image` 嵌在行内 spans 里 | 成立。这解释了为什么 F3 的「非文本单元不得生成伪选区」判据（`when (unit.kind)`）**结构上够不到图片** |
| 选择资格只看 kind | 修复前 `markdownUnitIsBlockSelectable` 对 `PARAGRAPH` 一律 true | 成立 → 纯图片段落被当成正文段落 |
| 轻点 → 提交 alt | `ReaderMarkdownRender.kt:276` `selectable` → `markdownBlockSelectionArgs` | 成立，与真机现象（摘录 = `示意图`）一致 |

**根因判定与 Zcode 报告一致，我独立读码复核后无异议。**

### 1.2 修复本体（`MarkdownScrollHighlight.kt:262-295`）

```kotlin
internal fun markdownUnitIsBlockSelectable(unit: MarkdownRenderUnit): Boolean = when (unit.kind) {
    MarkdownRenderKind.HORIZONTAL_RULE -> false
    MarkdownRenderKind.TABLE -> false
    MarkdownRenderKind.HEADING, PARAGRAPH, LIST_ITEM, TASK_ITEM, CODE_BLOCK ->
        !markdownUnitContainsImage(unit) && unit.canonicalRange.last >= unit.canonicalRange.first
}

internal fun markdownUnitContainsImage(unit: MarkdownRenderUnit): Boolean =
    markdownUnitSpans(unit).any { it is MarkdownRenderSpan.Image }
```

逐条对照任务书的禁止项：

| 任务书要求 | 复核结果 |
|---|---|
| 不接受只在保存层过滤 alt 文本的补丁 | **满足**。改动在 eligibility 谓词，位于 `markdownUnitCanonicalRange`(:201) 与 `markdownBlockSelectionArgs`(:221) **两个生产入口的上游**，保存层没有任何 alt 字符串比较 |
| 不接受用 `"!["`、alt 内容、文件名或文本猜测节点类型 | **满足**。`markdownUnitContainsImage` 只做 `it is MarkdownRenderSpan.Image` 类型判定；全文件无 `"!["`、无 `endsWith(".png")`、无 alt 值比较。`MarkdownImageBlockSelectionTest` 内 `readText/File(` 出现次数 = **0**（不是源码文本守卫） |
| 纯图片段落不可选择 | **满足**（谓词返回 false） |
| 不产生 selectable canonical range | **满足**。`markdownUnitCanonicalRange` 首行即 `if (!markdownUnitIsBlockSelectable(unit)) return null` |
| 不产生 selection callback / locator | **满足**。`markdownBlockSelectionArgs` 返回 null → 无 `text/rangeStart/sourceEnd`，locator 无从构造 |
| 普通文本段落仍可选择 | **满足**。无 Image span 时条件退化为原判定 |
| 分隔线 / 整表继续不可选 | **满足**，两条 `false` 分支未动 |
| 代码块契约不回退 | **满足且需说明**：`markdownUnitSpans` 对 `CodeBlock` 返回 `emptyList()` → `containsImage` 恒 false → 代码块条件**逐字等价于**修复前。加进去是无害的一致性，不是行为变更 |
| 混合文字+图片段落采用明确安全契约 | **满足，但属显式降级**，见 §1.4 |

### 1.3 历史伪记录的投影（任务书 D-1 第 4 条）

`MarkdownScrollHighlight.kt:413-414`（持久高亮投影的 `else ->` 分支）开头即
`if (!markdownUnitIsBlockSelectable(unit)) continue` → 库里已存在的那条 `示意图`
伪高亮**不再被投影回图片块**。这条我逐行读过，成立。

**但必须如实说明三件事**（Zcode 报告只说了第一件）：

1. **记录仍在库中**。修复只挡渲染投影，不删数据，也不迁移历史数据。
2. **可删除性未被破坏**：删除路径 `ReaderAction.DeleteHighlight` →
   `ReaderViewModel.kt:424` → `NoteRepository.deleteHighlight`，其列表来源是
   `ReaderNotesSheet`（DB 驱动），**不经过** `markdownUnitIsBlockSelectable` 投影。
   因此该伪记录应仍可在「笔记与标注」里被删掉 —— 这一点只能真机确认，已列入复验清单。
3. **潜在复活面**：若将来该段落被编辑成纯文本（去掉图片 span），历史记录的坐标
   会重新满足投影条件而再次显示。当前 `feature/reader/edit` 只有 1 个测试类、
   S08 编辑入口未实现，故本轮不构成缺陷，但**应在实现编辑功能时一并复核**。

**表格分支的口径不一致（新发现 P3-1）**：`:396-411` 的 `TABLE` 分支把高亮投影到
**单元格**，且**不**调用 `markdownUnitIsBlockSelectable`。因此「单元格内含图片」的
历史记录不会被这次修复挡住。它与 D-1 的现象不同源（D-1 是 paragraph 载体），
不构成回归，但属同一安全不变量的未覆盖角落，建议后续切片统一。

### 1.4 需产品确认的显式降级（不是缺陷，但必须记账）

含图片的**混合**段落（`前面文字 ![图](a.png) 后面文字。`）现在**整块不可选**。
Zcode 在 §11 已如实登记为 Spec P3。我复核其理由成立：整块选择提交的是**连续**
canonical 区间，alt 位于区间内部，要只选真实正文就必须提交两个不连续区间或靠
文本搜索定边界 —— 后者正是任务书禁止的猜测。

**代价的真实大小需要产品知道**：Markdown 里「文字 + 行内图片」是常见写法，
这类块将完全没有选区入口（连复制都不行）。这是取舍而非 bug，但**它把 D-1 从
"图片块误选"扩大到了"含图段落整体不可选"**。建议 Codex 明确确认接受，或把
"提交多区间"列入后续切片。

---

## 2. D-3 复核：可见性判据是否名副其实

### 2.1 根因独立确认

Zcode 的根因叙述是：唯一失效机制是 `DisposableEffect.onDispose`，它回答「离开组合」
而非「离开可见范围」，而 LazyColumn 的预取/缓存窗口使两者不等价。

我独立复核：全仓搜索修复前对 `layoutInfo.visibleItemsInfo` 的使用，
确认**宿主层此前从未观察过可见性**（只有 `ReaderProgressComputations` 用它算滚动位置）。
根因成立，且这正好解释真机上「无障碍树只剩第6章节点、工具栏仍挂第1章摘录」——
第1章 item 当时可能仍在缓存窗口内未 dispose。

### 2.2 接线（`ReaderContentHostPlainTextBranch.kt:115-157`）

| 任务书判据 | 复核结果 | 依据 |
|---|---|---|
| 不是固定 delay | **满足** | `snapshotFlow` + `distinctUntilChanged`，无 `delay(` / 无 `sleep`；守卫测试第 80-82 行显式 `assertFalse(effect.contains("delay("))` |
| 不是所有 item 各自清别人的选区 | **满足** | 判据在**宿主层单点**（持有全局 `s.plainListState` 的那一层），item 内没有新增第二套清除决策；每个 item 仍只有原有的 `DisposableEffect` 最终防线（:523-529） |
| owner 可见不清 | **满足** | 策略第 4 条：`owner.unitIndex in visibleUnitIndices → KEEP` |
| owner 离开清一次 | **满足** | 第 5 条 `CLEAR` + `ScrollSelectionInvalidationGate.shouldDispatch` 只在「上次非 CLEAR → 本次 CLEAR」跃变上返回 true |
| 相邻非 owner 不误清 | **满足** | 判据只考察**唯一 owner** 是否在可见集合内，相邻项是否可见完全不参与结论 |
| 用户手势与 TTS/程序化滚动同契约 | **满足（结构上）** | 策略输入只有「可见集合」，不含滚动来源字段，两条路径无法分叉。运行时是否真的都触发 `visibleItemsInfo` 更新 → 交真机 |
| viewport 未初始化 / 短暂为空行为明确 | **满足** | 三值枚举里的 `UNDECIDABLE`（`totalItemsCount == 0`）→ **不清**；且 `shouldDispatch` 中 `invalidated = decision == CLEAR`，UNDECIDABLE 不消耗门（否则真离开时会漏清）——这个细节做对了 |
| snapshot 不随活动选区清除 | **满足** | 派发信号固定 `onSelect("", -1, -1, -1)` → `ReaderLayerBuilders.onSelect` 的 `text.isBlank()` 分支 → `ReaderAction.ClearSelection` → `withSelectionCleared()`，而该函数只写 `selectedText/selectedRangeStart/selectedGlobalOffset/selectedSourceEnd/showColorRow` 五字段，**不碰 `selectionSnapshot`**；`ReaderViewModel.kt:407-410` 还有显式注释守住这条 |
| owner 返回时旧选区不复活 | **满足（结构上）** | 清除后 `selectedRangeStart = -1` → 策略第 1 条恒 `KEEP`，没有任何路径把已清选区恢复 |
| DisposableEffect 只作最终防线、不冒充可见性 | **满足** | 保留于 :523-529，`readingUnitShouldClearOnDispose` 的 KDoc 已注明降级；接线注释（:115-127）明确写「宿主层唯一判据」 |

### 2.3 两个值得记的接线细节（均判为正确，但属高风险点）

1. **`rememberUpdatedState` 用在三个输入上**（`latestSelectedRangeStart` /
   `latestReadingUnits` / `latestClearSelection`）。effect 的 key 是 `s.plainListState`
   （列表身份），选区变化不重启 effect —— 若直接捕获组合期快照，判据会永远用第一次
   组合的选区起点。这正是 F2.3 登记过的同类缺陷，本轮处理正确。
2. **`initialized` 用 `totalItemsCount > 0` 而非 `isInitialized`**，注释解释了原因
   （该 API 在本模块可见的 `LazyListState` 版本上不可用）并论证语义等价。可接受。

**新发现 P3-2（文档与代码不一致）**：策略 KDoc 第 331 行仍写
`[viewportInitialized]：LazyListState.isInitialized`，而接线处（:139-142）明确说明
**没有**用 `isInitialized`。属注释漂移，建议改为实际判据名。

**新发现 P3-3（门的存活域未论证）**：`invalidationGate` 由 `remember` 持有，
其生命周期等于本分支的组合生命周期。分支在「滚动↔分页切换」「切书」时离开组合会重置门
（行为安全），但**没有测试覆盖「门被重置后是否会对同一选区重复派发一次清除」**。
影响仅为一次多余 `ClearSelection`（幂等），故 P3，不阻断。

### 2.4 测试性质分列（我独立分类，不采信报告口径）

| 类别 | 类 | tests |
|---|---|---:|
| **结构 / 源码守卫**（`readText` 读源文件文本） | `ReaderSelectionRangeWiringGuardTest` 9、`ReadingUnitDisposeWiringGuardTest` 8、`ScrollSelectionVisibilityWiringGuardTest` 7 | **24** |
| **行为 / 策略测试**（真实 parser + 真实 render model；真实 `ReadingUnit` 列表 + 真实可见集合） | 其余 9 类 | **121** |
| 合计 | 12 | **145** |

交叉验证：`MarkdownImageBlockSelectionTest` 与 `ScrollSelectionVisibilityPolicyTest`
中 `readText|File(` 命中数均为 **0** → 确为行为测试；
`ScrollSelectionVisibilityWiringGuardTest` 的 KDoc 自己写明「运行时时序只能由真机复验确认」
→ 口径诚实，未冒充运行时证据。

---

## 3. 独立 JVM 复跑（不采信对方数字）

### 3.1 验证集（12 类，`--rerun`）

```
./gradlew.bat --console=plain :app:testDebugUnitTest --rerun \
  --tests "...MarkdownUnitSourceRangeTest" ... （12 项，包名见 §3.3）
```

| 项 | 我的结果 | Zcode 声明 | 一致性 |
|---|---|---|---|
| 开始 / 结束 | 12:53:32 → 12:53:47 | 12:43:39 段 | — |
| 退出码 | **0** | 0 | ✅ |
| 构建 | `BUILD SUCCESSFUL in 14s` | BUILD SUCCESSFUL | ✅ |
| XML 文件数 | **12** | 12 | ✅ |
| distinct 类名 | **12** | 12 | ✅ |
| tests | **145** | 145 | ✅ |
| failures / errors / skipped | **0 / 0 / 0** | 0/0/0 | ✅ |
| 缺失类 / 零匹配 | **0 / 0** | 0 / 0 | ✅ |

### 3.2 S07 代表回归（`--rerun`）

```
./gradlew.bat --console=plain :app:testDebugUnitTest --rerun \
  --tests "com.creationreadingassistant.ui.screen.reader.*" \
  --tests "com.creationreadingassistant.ui.viewmodel.*" \
  --tests "com.creationreadingassistant.feature.reader.*"
```

| 项 | 我的结果 | Zcode 声明 | 一致性 |
|---|---|---|---|
| 时间 | 12:54:40 → 12:58:04（`BUILD SUCCESSFUL in 3m 23s`） | — | — |
| 退出码 | **0** | 0 | ✅ |
| XML 文件数 / distinct 类 | **205 / 205** | 205 类 | ✅ |
| tests | **1953** | 1953 | ✅ |
| failures / errors / skipped | **0 / 0 / 0** | 0/0/0 | ✅ |

按包分布（我实测）：`ui.screen.reader` 65、`feature.reader.pager` 36、
`ui.viewmodel` 30、`feature.reader.doc` 25、`feature.reader.rules` 12、
`ui.screen.reader.sheets` 7、`feature.reader.layout` 8、`ui.screen.reader.tts` 5、
`feature.reader` 5、`feature.reader.locator` 4、其余 8 包共 18。

### 3.3 逐类计数（我的独立运行，与报告 §10.2 逐项对照）

| 测试类 | 我的 tests | 报告 | F | E | S |
|---|---:|---:|---|---|---|
| `ui.screen.reader.MarkdownUnitSourceRangeTest` | 10 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownBlockSelectionLocatorTest` | 10 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownPersistentHighlightProjectionTest` | 11 | 11 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionRangeWiringGuardTest` | 9 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitSelectionOwnershipTest` | 10 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitDisposeWiringGuardTest` | 8 | 8 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionSnapshotSemanticsTest` | 12 | 12 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderSelectionSnapshotTest` | 24 | 24 | 0 | 0 | 0 |
| `ui.screen.reader.TxtScrollChapterTargetTest` | 7 | 7 | 0 | 0 | 0 |
| **`ui.screen.reader.MarkdownImageBlockSelectionTest`（F5 新增）** | **17** | 17 | 0 | 0 | 0 |
| **`ui.screen.reader.ScrollSelectionVisibilityPolicyTest`（F5 新增）** | **20** | 20 | 0 | 0 | 0 |
| **`ui.screen.reader.ScrollSelectionVisibilityWiringGuardTest`（F5 新增）** | **7** | 7 | 0 | 0 | 0 |
| **合计** | **145** | 145 | 0 | 0 | 0 |

### 3.4 陈旧结果防护

两次运行前均 `rm -f app/build/test-results/testDebugUnitTest/*.xml`，且带 `--rerun`；
XML mtime（12:53:46 / 12:58:02）落在各自运行窗口内 → 非陈旧文件。
回归集 205 个 XML 全部为本次新生成。

---

## 4. 改动面与证据链核对

### 4.1 文件哈希（我独立 `sha256sum`，对照报告 §8）

| 文件 | 我的 after(前16) | 报告 after(前16) | 一致 |
|---|---|---|---|
| `main/.../MarkdownScrollHighlight.kt` | `0e7d0ebc8349ac6c` | `0e7d0ebc8349ac6c` | ✅ |
| `main/.../ReaderContentHostPlainTextBranch.kt` | `784199b715bcb806` | `784199b715bcb806` | ✅ |
| `test/.../MarkdownImageBlockSelectionTest.kt` | `4e8e4d72de515e53` | `4e8e4d72de515e53` | ✅ |
| `test/.../ScrollSelectionVisibilityPolicyTest.kt` | `a69fd631b045b5e5` | `a69fd631b045b5e5` | ✅ |
| `test/.../ScrollSelectionVisibilityWiringGuardTest.kt` | `824a2c8a5dba2a1c` | `824a2c8a5dba2a1c` | ✅ |

完整 64 位哈希亦逐字相同（见附录 A）。

`before` 值交叉验证：`MarkdownScrollHighlight.kt` 的 before `89bd78e2c77d637c`
与我 F4.1 真机验收时登记的边界外文件值一致 → before 基线可信。
`ReaderContentHostPlainTextBranch.kt` 的 before `ddab14689a84e51c` 我**无独立历史登记值可比**，
记为「未独立验证」（不影响 after 侧结论）。

### 4.2 未跟踪文件

3 个新测试均为 `??`（未跟踪），与报告一致；本轮未使用 `git add -N`。

### 4.3 F5 窗口内的全部文件改动（我独立按 mtime 扫描，窗口 ≥ 11:00）

```
11:57:49  docs/code-review/2026-09/2026-09-20-android-s07-selection-review.md      （文档）
11:57:49  docs/coordination/reviews/2026-09/2026-09-20-zcode-qoder-audit-board.md          （文档）
12:32:20  test/.../MarkdownImageBlockSelectionTest.kt                      （F5 新增）
12:34:50  main/.../MarkdownScrollHighlight.kt                              （F5 产品）
12:39:34  test/.../ScrollSelectionVisibilityPolicyTest.kt                  （F5 新增）
12:42:24  main/.../ReaderContentHostPlainTextBranch.kt                     （F5 产品）
12:42:36  test/.../ScrollSelectionVisibilityWiringGuardTest.kt             （F5 新增）
12:49:28  docs/coordination/agent-results/agent-result-s07-f5-zcode.md                   （F5 报告）
```

**结论：产品侧只有 2 个文件、测试侧只有 3 个新文件，与报告 §7 完全一致。**
两份 11:57 的文档改动早于 F5 编码窗口（12:32 起），属主任务/看板维护，非代码夹带。

### 4.4 禁改项（我独立复核 mtime，全部早于 F5 窗口）

| 文件 | 我的 mtime | 属 |
|---|---|---|
| `data/settings/ReaderOverride.kt` | 2026-09-18 02:23:08 | **D-2 根因文件** |
| `data/settings/SettingsStore.kt` | 2026-09-18 19:35:33 | D-2 |
| `feature/reader/pager/ReplacedChapterSource.kt` | 2026-09-19 23:58:15 | **D-7 相关** |
| `ui/screen/reader/ReaderContentHostEpubBranch.kt` | 2026-09-20 21:20:46 | D-4 / D-5 |
| `ui/screen/reader/ReaderPagerEngineState.kt` | 2026-09-19 23:58:15 | 分页引擎 |
| `ui/viewmodel/ReaderViewModel.kt` | 2026-09-21 01:10:31 | F4.1 收工值 |
| `ui/screen/reader/ReaderScreenState.kt` | 2026-09-20 23:25:10 | 状态机 |

`ReaderMarkdownRender.kt` 与 `ReaderContentHostMarkdownBranch.kt` 的 before/after 同为
`81a07cd47934ee3a` / `cc3c1ccbc9d986d5`（IDENTICAL）→ **渲染层未被改动**，
D-1 确实靠上游谓词生效而非在渲染处打补丁。

**未夹带 D-2、D-7、设置页、S08–S20：确认。**

### 4.5 Git 纪律

| 检查 | 结果 |
|---|---|
| `git diff --cached --name-only` | **0 行**（未 stage） |
| `git stash list` | **0** |
| `git rev-parse HEAD` | `5aed593`（基线未推进，未 commit / 未 push） |
| 全仓格式化 | 未发生（tracked 修改文件数 217，与 F5 前同一恢复工作区状态一致） |

### 4.6 我未做的独立动作（如实声明）

- **未重跑 Zcode 的 4 项变异验证（§6.3）**。变异需改产品源码，与本轮「不修改产品/测试源码」
  约束冲突。我改为读码论证不变量：去掉 `!markdownUnitContainsImage(unit) &&` 会让
  `a pure image paragraph never produces block selection args` 等断言直接失败（该断言的
  期望值是 null，而修复前生产入口确实返回非 null —— 报告 §6.1 的 RED 输出
  `expected null, but was:<MarkdownBlockSelection(text=示意图, …)>` 与此完全自洽）。
  **判定：RED→GREEN 叙述内部一致，但我没有独立复现它。**
- **未运行 androidTest 插桩测试**（MIUI 拦插桩包安装，既有记录已证）。
- **未运行 `:app:lintDebug`**（任务书未要求）。

---

## 5. 双轴剩余问题（我的独立计数）

### Standards 轴

| 级别 | 数量 | 项 |
|---|---:|---|
| P0/P1/P2 | 0 | — |
| P3 | 3 | ①**P3-1** 持久高亮投影的 `TABLE` 分支不过 eligibility 谓词，含图单元格的历史记录不会被本次修复挡住（与 D-1 不同源，非回归）；②**P3-2** 策略 KDoc 仍写 `LazyListState.isInitialized`，与接线处实际使用的 `totalItemsCount > 0` 不一致；③**P3-3** 去重门的存活域（`remember`）无测试覆盖「重置后是否重复派发」 |

最严重：**P3-1**（不变量覆盖不全，但当前无用户可见后果）。

Zcode 自报的 Standards P3-①（接线守卫不证运行时时序）我**认可且认为口径诚实** ——
该守卫的 KDoc 自己写明了边界，没有冒充运行时证据。

### Spec 轴

| 级别 | 数量 | 项 |
|---|---:|---|
| P0/P1 | 0 | — |
| P2 | 0 | D-1、D-3 的代码轴不变量已成立；是否真关闭待真机 |
| P3 | 2 | ①**含图片的混合段落整体不可选**（连复制入口一并失去）——需产品确认取舍；②图片本体仍只渲染 alt 文本（上轮标 NOT TESTED，本轮未触碰） |

最严重：**Spec P3-①**。这是 D-1 修复的直接代价，不是 bug，但影响面比 Zcode 表述的更具体：
「文字 + 行内图片」段落在 Markdown 中常见，修复后该类块**完全无选区**。

### 保留为独立缺陷、不得删除或宣称已解决

| ID | 级别 | 状态 |
|---|---|---|
| **D-2** 设置作用域：`pager_engine_mode` / `epub_pager_engine_mode` 无 per-book 覆盖能力（`ReaderOverride.kt` 枚举缺项），但设置页在「只改本书」下仍暴露该控件 → 静默写全局 | **P2** | **未修**（F5 未触碰，已用 mtime 证明）。继续作为独立 P2 挂账，归属建议交 Codex 裁决（很可能属设置子系统） |
| **D-7** legacy EPUB 滚动帧率 | P2（待定性） | 未优化；本轮补做 A/B 对照，结论见真机报告 |

---

## 6. 代码轴结论与真机复验清单

**代码与 JVM 轴：PASS。** D-1 修在正确的层、判据不可被文本伪造；D-3 建立了真正的
可见性判据、未用 delay、未让相邻项互清、快照与活动选区保持正交、`DisposableEffect`
已降级为最终防线。改动面干净，无越界、无夹带。

**但代码轴不能单独判定 D-1/D-3 已关闭**，两处的运行时前提都只能真机确认：

| # | 必须真机确认的前提 |
|---:|---|
| 1 | Compose 的 `clickable` 是否真的没挂到图片块上（JVM 结构上推不出） |
| 2 | `snapshotFlow` 观察 `visibleItemsInfo` 的实际时机 —— owner 滚出后是否真的一帧内清掉 |
| 3 | `distinctUntilChanged` 是否会把「owner 已不可见」这次跃变吞掉（若被吞则 D-3 未修好） |
| 4 | 小距离滚动（owner 仍可见）是否真的不误清 |
| 5 | TTS/程序化滚动与用户手势是否走同一判据 |
| 6 | 清除后消费者（批注/灵感）快照是否仍保留摘录与 locator |
| 7 | owner 滚回来时旧选区是否真的不复活 |
| 8 | 历史 `示意图` 伪高亮是否不再回显、且仍能在「笔记与标注」里删除 |

以上 8 条已逐条写入真机复验报告。

**本报告不写「发布完成」，不领取 S08–S20。等待 Codex 主任务复核。**

---

## 附录 A：F5 五文件完整 SHA256（我独立计算）

```
0e7d0ebc8349ac6c6b272cb2782643d486a14f8e7493741a322784b5c3c487b9  main/ui/screen/reader/MarkdownScrollHighlight.kt
784199b715bcb8061b7b88f791fe4ab7e8a789f5d450af5dca1dcdc90f97123e  main/ui/screen/reader/ReaderContentHostPlainTextBranch.kt
4e8e4d72de515e53cfec545ef3c5cdaa8d6e5c825f06d8ada8d70937f36b0653  test/ui/screen/reader/MarkdownImageBlockSelectionTest.kt
a69fd631b045b5e5012f34cc169a3e82a6ccc4c948ec7d0b5e7d2a7af3a8f7d1  test/ui/screen/reader/ScrollSelectionVisibilityPolicyTest.kt
824a2c8a5dba2a1ca3882d5fafe9776b217d3b6ba017080484f57b54063ad606  test/ui/screen/reader/ScrollSelectionVisibilityWiringGuardTest.kt
```

## 附录 B：F5 复验 APK

| 项 | 值 |
|---|---|
| 构建命令 | `./gradlew.bat --console=plain :app:assembleDebug` |
| 结果 / 退出码 | `BUILD SUCCESSFUL in 25s` / **0** |
| 路径 | `android\app\build\outputs\apk\debug\app-debug.apk` |
| 大小 | 51,786,667 字节 |
| SHA256 | `3164f41aae9616fd98d4d7ac63a74597d412429e494c1b56edfd08423881909c` |
| 安装 | `install_with_confirm.ps1 -Serial c49ac6cf -TimeoutSeconds 120` → `RESULT: CONFIRM_LOOP`，退出码 **0**，未回退 |
