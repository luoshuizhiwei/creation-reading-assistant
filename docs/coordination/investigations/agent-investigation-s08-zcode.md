# S08 章节修改层只读盘点 — Zcode 报告

> 执行 Agent：Zcode　|　实际模型：`deepseek v4.1Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 调查期间 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 性质：**只读盘点，未实施任何功能，未修改任何产品或测试源码，未运行 Gradle**
> 报告路径：`docs/coordination/investigations/agent-investigation-s08-zcode.md`
> 状态：**等待 Codex 复核与产品裁决**

标注约定：**[事实]** = 我读到的源码 / 跑过的只读命令；**[推断]** = 静态推导，未运行验证；
**[未验证]** = 需真机或运行期确认。

---

## 1. 结论摘要

**S08「章节修改层」目前不是「半成品」，而是「一个未接线的纯算法 + 一条已经独立完成的单点纠错通路」。**
两者尚未相遇，且按现有代码结构**不能直接相遇**。

四条最重要的事实：

1. **[事实] `feature/reader/edit/` 只有一个产品文件**：`ChapterTextDiff.kt`（207 行，纯 JVM 差异引擎），
   配一个 JVM 测试 `ChapterTextDiffTest.kt`（13 tests）。整个 `edit/` 包
   **在 git 中不存在**（`git ls-files` 为空，`git status` 为 `??`）——它是未跟踪的新增文件，
   不在 HEAD，也不在 `origin/main`。
2. **[事实] `ChapterTextDiff` 的生产调用方数量为 0。** 全仓 `rg` 只命中它自己的文件与自己的测试。
   它当前是**死代码**（未接线，非缺陷）。
3. **[事实] 「编辑」动作已声明但被双向封死**：`SelectionActions.ALL` 里有
   `SelectionActionDef("edit", "编辑", "编辑本章正文", MORE)`，但 id 同时在
   `SelectionActions.DORMANT_IDS = setOf("edit")` 中，于是**不可配置、不可渲染、写侧拒绝**。
   工具条分发 `when` 里**没有** `"edit"` 分支，落入 `else -> Unit`。
4. **[事实] 保存链路确实真实存在，但它服务的是「单处纠错」，不是「章节编辑」。**
   `reader_text_corrections`（Room v13）+ `RuleCommand.SaveSingleCorrection` +
   `RulesRepository.saveSingleCorrection` + `ReplaceProjection.overlayAnchored` 是一条**完整、已生产接线、
   已有多层能力门控、已接搜索索引刷新**的通路；入口是选区工具条的「替换」动作 →
   规则页的「单处纠错」目标。它与 `ChapterTextDiff` **没有任何代码连接**。

**核心结论：S08 的真实缺口不是「保存做不到」，而是「章节级批量写入 + 编辑器 UI」这两块，
而且现有单点纠错通路在结构上拒绝被复用**（详见 §3.3 与 §5）。

---

## 2. 真实入口与可达性

### 2.1 入口矩阵

| 声称入口 | 代码位置 | 真实可达性 | 证据 |
|---|---|---|---|
| 选区工具条「编辑」 | `SelectionActionSettings.kt:85` | **不可达** | 在 `DORMANT_IDS`（`:146`）→ `pickableDefs()` 排除（`:150`）→ 设置页无勾选项；`effectivePrimary/effectiveMore` 过滤掉；`SelectionActionStore.setActionEnabled` 写侧 `if (id in DORMANT_IDS) return`（`:66`） |
| 设置页「常用动作」勾选「编辑」 | `SelectionSubPage.kt:115/162` | **不可达** | 两处都用 `SelectionActions.pickableDefs()`，dormant 已被排除 |
| 工具条分发点击「编辑」 | `ReaderSelectionToolbar.kt:162-175` | **不存在** | `when(id)` 无 `"edit"` 分支，`else -> Unit` |
| 选区工具条「替换」→ 规则页「单处纠错」 | `ReaderLayerBuilders.kt:382` → `ReaderRulesSheet.kt:43` → `RuleEditorDraft.kt:112` | **完全可达**（TXT 分页 / 分页 EPUB） | 见 §3.2 |
| 规则管理页「撤销纠错 / 恢复纠错」 | `ReaderRulesListSection.kt:631/633` | **完全可达** | `onCommand(RuleCommand.UndoCorrection/RestoreCorrection)` |
| 章节编辑器（`ChapterTextEditorSheet`） | — | **不存在** | 全仓无此文件；`ReaderSheet` 枚举 12 值，无 `CHAPTER_EDIT`（`ReaderScreenState.kt:4-18`） |

### 2.2 「UI 有入口但后端未接线」与「后端有代码但 UI 不可达」

- **UI 有入口、后端未接线**：**不存在**。这正是 S07-F1 的 P1 修复结果——
  当时「编辑」是死按钮，现已用 `DORMANT_IDS` 双向封死。结构守卫
  `ReaderConsumerAndActionGateTest.every renderable action id has a toolbar dispatch branch`
  会断言「每个可渲染 id 必须在分发 `when` 里有分支」，防止再次出现死按钮。
- **后端有代码、UI 不可达**：**存在，且是 S08 的主体**。
  `ChapterTextDiff`（差异引擎）无任何调用方；`PagedChapterSource.loadChapterText(index)`
  与 `chapterStartAbs(index)`（章节正文与章基址）在生产层**没有为编辑服务的消费点**。
- **第三种、也是最重要的一种**：**后端链路存在但入口语义被别的功能占用**。
  `SaveSingleCorrection` 的入口是「替换」动作，语义是「改这一处选中的文字」，
  且 `ReaderViewModel.resolveSingleCorrectionAnchor` 会用**当前选区**覆盖命令里的锚点
  （`ReaderViewModel.kt:635-655`）。它**不是**章节编辑入口，也不能被章节编辑复用。

---

## 3. 文件 / 类型 / 调用链

### 3.1 `feature/reader/edit/` 现有内容（全部未跟踪）

```
android/app/src/main/java/com/creationreadingassistant/feature/reader/edit/ChapterTextDiff.kt   (207 行, ??)
android/app/src/test/java/com/creationreadingassistant/feature/reader/edit/ChapterTextDiffTest.kt (13 tests, ??)
```

| 类型 | 定义位置 | 语义 |
|---|---|---|
| `ChapterEditBlock(localStart, localEnd, originalText, replacementText)` | `ChapterTextDiff.kt:11` | **章节局部**半开区间的一处改动 |
| `ChapterDiffResult`（sealed） | `:19` | `NoChange` / `Blocks(List<ChapterEditBlock>)` / `TooLarge(blockCount)` |
| `ChapterTextDiff`（object） | `:45` | `diff(original, edited): ChapterDiffResult`；`MAX_BLOCKS = 200` |

算法 **[事实]**：按行切段（保留换行符）→ 段级 LCS 对齐 → 对齐空隙即改动块 →
块内字符级公共前后缀裁剪 → 纯插入归一化为「1 字符替换」（左锚点优先，左邻空白则取右锚点）。

设计意图 **[事实，来自源码注释]**：每块与 `RuleCommand.SaveSingleCorrection` **一一对应**，
`localStart/localEnd` 加上章节在全书的 source 起点即得锚点区间。

### 3.2 已存在的「单处纠错」完整调用链（S08 的真实可复用底座）

```
[UI] 选区工具条「替换」
  ReaderLayerBuilders.kt:382   onReplace = { onAction(OpenSheet(RULES)) }
→ RulesSheet
  ReaderRulesSheet.kt:43       initialReplaceText = 选区文字
  RuleEditorDraft.kt:112       selectionReplaceDraft() → draft(saveAsCorrection = true)
  RuleEditorDraft.kt:136       toCommand() → RuleCommand.SaveSingleCorrection(-1, -1, findText, replacement)
→ ReaderViewModel
  ReaderViewModel.kt:496       识别 SaveSingleCorrection
  ReaderViewModel.kt:504       第 1 道门：currentReplacementCapability() 必须 Available
  ReaderViewModel.kt:635       第 2 道门：currentSelectionSourceRange() 必须非 null
  ReaderViewModel.kt:644       第 3 道门：draft.findText 必须 == 当前选区文字
  ReaderViewModel.kt:650       **用选区坐标覆盖命令锚点**
→ RulesRepository
  RulesRepository.kt:311       saveSingleCorrection()：区间有效 / findText 非空白 / 与原文不同
  RulesRepository.kt:328       correctionDao.upsert(ReaderCorrectionEntity(status = ACTIVE))
→ 投影生效
  RulesRepository.kt:248       buildSnapshot：生效纠错 → 锚定 ReplaceRule(id="correction:*", position=Int.MAX_VALUE)
  ReplaceProjection.kt:179     overlayAnchored()：source→display 换算 + findText 内容校验，漂移则诚实跳过
→ 索引刷新
  ReaderSearchIndexCoordinator.kt:42  SaveSingleCorrection → SearchIndexRefreshPlan(global = false)
  ReaderViewModel.kt:545              保存成功后 applySearchIndexRefresh（重建本书索引）
→ 选区清理
  ReaderViewModel.kt:536              成功后 withSelectionCleared()
```

### 3.3 为什么章节编辑**不能**复用这条链路（三条硬阻塞）

**[事实]** 全部有源码依据，与设计文档 §S3a-2 的取证结论一致：

1. **锚点被选区覆盖**：`resolveSingleCorrectionAnchor`（`ReaderViewModel.kt:635`）无条件用
   `currentSelectionSourceRange(_screenState.value)` 替换命令传入的 `sourceStart/sourceEnd`。
   章节编辑的锚点来自「章节 source 起点 + block.localStart」，不是选区 → 会被改写。
2. **文字校验拒绝**：同一函数要求 `draft.findText == _screenState.value.selectedText`。
   章节编辑的 `findText` 是块内原文，与选区无关 → 必然被 `NotAnchorable` 拒绝。
3. **没有批量通路**：`ReaderAction` 只有 `ExecuteRuleCommand(bookId, command)` 单命令；
   全仓 `rg` 无 `BatchCorrection` / `SaveChapterEdit` / `ApplyChapterEdit`。
   编辑器一次产生多块，需要 N 条写入 + **一次**索引刷新（设计文档明确警告「不要每条调一次，
   会重复重建」）。

**[推断]** 因此 S08 的最小实现必然包含「新增一条章节编辑专用命令 + VM 处理器」，
不能靠拼装现有命令绕过。

---

## 4. 三格式能力矩阵

依据 `readerReplacementCapability(availability)`（`ReaderReplacementCapability.kt:22`）
与 `effectiveReplacementAvailability(pagerEngineOn, prepared, scrollProjectionOn)`
（`ReaderPagerEngineState.kt:70`）。

| 格式 / 路径 | 引擎裁决 | 单处纠错可达 | 章节编辑（若实施） | 依据 |
|---|---|---|---|---|
| **TXT 分页**（`pagerEngineMode` 开） | `APPLIED` / `NO_EFFECTIVE_RULES` / `PARTIALLY_APPLIED` / `ALL_SCOPES_OVERSIZED` → **Available** | **是** | 坐标完备（`chapterStartAbs` + `localDisplayToGlobalSource`），可行 | `TxtPagedController.kt:94/107/118/209` |
| **TXT 滚动**（legacy / 无完整逻辑章投影） | `PAGER_ENGINE_DISABLED` → **Unavailable** | 否 | 不可行（见下） | `ReaderPagerEngineState.kt:74` |
| **TXT 滚动**（完整逻辑章投影已接入） | `scrollProjectionOn = true` → 走 `prepared` | 是（受 prepared 裁决） | 单位是 ReadingUnit，非逻辑章 | `ReaderPagerEngineState.kt:76`；`ScrollUnitProjection.kt:54` |
| **Markdown** | `NON_SOURCE_COORDINATES` → **Unavailable** | **否** | 不可行 | `ReaderReplacementCapability.kt:57` |
| **EPUB 分页** | `APPLIED` 等 → **Available** | **是** | 理论可行（`loadChapterText` + `chapterStartAbs`） | `PagedChapterSource.kt:52`；`EpubSingleCorrectionReachabilityTest` |
| **EPUB legacy / 非分页** | `ESTIMATED_COORDINATES` → **Unavailable** | **否** | 不可行（估算量纲） | `ReaderReplacementCapability.kt:54` |

**要点 [事实]**：

- **Markdown 被硬阻断**，理由写在能力文案里：「当前 Markdown 正文使用渲染坐标，尚未形成完整
  source 定位契约」。这与 `docs/handoff/current.md` §P3.1 的既有边界一致。
- **「整章」在两种阅读模式下含义不同**（设计文档 §S3a-2 ④ 已取证）：
  分页模式 = 逻辑章；滚动模式 = 一个 `ReadingUnit` 有界单元
  （`ScrollingTxtChapterSource.loadChapter(index)` 的 index 是 **unitIndex**）。
  所以 v1 只能说「当前渲染单元」，不能承诺「永远是整章」。
- **EPUB 编辑是「理论上可行但从未验证」**：`loadChapterText` 存在，但没有为编辑服务的调用点，
  也没有任何测试覆盖「取整章文本 → 编辑 → 写回纠错 → 重投影一致」。**[未验证]**

---

## 5. 保存与失败恢复链

### 5.1 修改落在哪里（回答问题 4）

| 候选 | 是否被修改 | 证据 |
|---|---|---|
| 内存正文 | **不改** | 纠错是投影叠加，`ReplaceProjection.overlayAnchored` 只产出新的 display 文本 |
| 源文件（SAF 用户原文件） | **绝不修改** | `ReaderCorrectionEntity` 注释「原文件永不修改」；全仓无任何对源文件的写路径 |
| 数据库副本（`filesDir/books/<id>/`） | **不修改** | 纠错只写 `reader_text_corrections` 表；无写副本代码 |
| **投影层（display 通道）** | **唯一被修改处** | `reader_text_corrections` → 锚定 `ReplaceRule` → `ReplaceProjection` |

**结论 [事实]：S08 当前的全部改动都落在「投影」，不落原文、不落副本、不落源文件。**
这正是设计文档 S3a「编辑存修改层」的形态，也是「重置功能」能成立的原因
（`UNDONE` 翻转即恢复原文）。

### 5.2 保存链是否真实存在

**存在且已生产接线**，但**仅覆盖单区间**（§3.2）。落库前提 **[事实]**：
`sourceEnd > sourceStart` 且 `findText` 非空白且 `replaceText != findText`
（`RulesRepository.kt:315-327`）。

### 5.3 撤销 / 重做 / 取消 / 脏状态 / 失败恢复

| 能力 | 状态 | 证据 |
|---|---|---|
| 撤销（已保存的纠错） | **存在** | `UndoCorrection` → `status = UNDONE`；UI 在 `ReaderRulesListSection.kt:633` |
| 恢复（撤销后的纠错） | **存在** | `RestoreCorrection` → `status = ACTIVE`；UI `:631` |
| **删除纠错** | **后端存在，UI 不可达** | `ReaderCorrectionDao.deleteById/deleteForBook`（`:36/:39`）**无任何调用方** |
| **编辑器级撤销/重做** | **不存在** | 无编辑器；无 undo 栈 |
| **脏状态（unsaved changes）** | **不存在** | 全仓 `rg dirty/unsaved/isDirty` 在 reader 包只命中 TTS 音频缓存 |
| **取消（丢弃未保存编辑）** | **不存在** | 无编辑器；`RulesSheet` 的返回只是 `editing = null` |
| **失败恢复（部分写入回滚）** | **不存在** | `RulesRepository` 与 `ReaderCorrectionDao` **没有任何 `@Transaction` / `runInTransaction`** |

**[推断，P1 级]** 批量写入（N 条纠错）若按现有代码风格逐条 `upsert`，
在进程被杀 / 磁盘满 / 中途异常时会产生**部分生效**的章节编辑——
用户看到的是「一半改了一半没改」，且没有任何回滚或提示。
单点纠错因为只有一行，天然原子；**批量不是**。这是 S08 必须新解决的问题。

### 5.4 保护阈值

`ChapterTextDiff.MAX_BLOCKS = 200`：超过即返回 `TooLarge`，设计意图是「提示改用替换规则」。
**[事实]** 该提示文案**不存在**（无调用方）。

---

## 6. 坐标、分页、索引和标注影响

### 6.1 失效与重建矩阵（若章节编辑写入多条纠错）

| 消费方 | 失效机制 | 是否自动重建 | 证据 |
|---|---|---|---|
| **分页缓存** | `ReplaceProfile.key` 覆盖 `anchorStart/anchorEnd/anchorFind` → key 变化 → `pageIndexContentKey` 变化 → 旧页索引自然不命中 | **是**（自动） | `ReplaceProfile.kt:70-80`；`TxtPagedController.kt:25-28` |
| **投影缓存** | `ReplacedChapterSource` 用 `replaceProfileKey` 作身份，LRU(3) 整体失效 | **是**（自动） | `ReplacedChapterSource.kt:385/489` |
| **搜索索引** | `SearchIndexRefreshPlan(global = false)` → `reindexBookById` | **是，但需显式调用一次** | `ReaderSearchIndexCoordinator.kt:42`；`ReaderViewModel.kt:545` |
| **进度 / locator** | 纠错**不改变 source 长度**（`totalChars = delegate.totalChars`） | **不受影响** | `ReplacedChapterSource.kt:387/491` |
| **高亮 / 笔记坐标** | `locator_json` 存 source 坐标，不随 display 变化；渲染时经 `globalSourceRangeToLocalDisplay` 换算 | **是（渲染侧）** | `ReaderContentHostPlainTextBranch.kt:437-455` |
| **高亮 / 笔记回源跳转** | `AnchorResolver.resolve` 用 excerpt 精确/近似回查，置信度降级时提示「原文可能已变化」 | **降级但安全** | `AnchorResolver.kt:22-50` |
| **`reader_anchor_cache`** | key = `(kind, entity_id, content_key = bookId)`，**不含规则身份** | **不会失效（风险）** | `AnchorCacheStore.kt:12`；`ReaderProgressEffects.kt:488` |
| **TTS 句高亮** | EPUB 分页：`ttsSourceLocalToDisplayLocal` 映射；TXT 滚动：`content.globalSourceToLocalDisplay` | **是** | `ReaderReplaceCoordinates.kt:45`；`ReaderContentHostPlainTextBranch.kt:432` |

### 6.2 两个真实风险

- **[推断，P2] `reader_anchor_cache` 不会被纠错写入失效。**
  缓存 key 只有 `bookId`（`AnchorCacheStore.kt:12`），不含 `ReplaceProfile.key`。
  纠错改变 display 文本后，缓存里的 `charOffset` 仍是旧值；虽然 `AnchorResolver`
  本身是 source 口径（不受 display 变化影响），但一旦 source 侧也变化（未来 S3b 写回源文件），
  缓存会直接返回过期结果且**不降级、不提示**。当前单点纠错场景下不构成可见缺陷，
  **批量章节编辑会放大命中面**，建议在 S08 实现时把 `contentKey` 扩展为含规则身份。
- **[推断，P2] 高亮/笔记的 `APPROXIMATE` 降级只在跳转时提示。**
  纠错使某段 display 变长/变短后，若用户此前的高亮锚点落在该段内，
  跳转会提示「原文可能已变化，已跳到最接近的位置」，但**阅读页上的高亮色块**
  是按 source 区间换算的，可能出现与文字错位而不提示。**[未验证]**，需真机。

---

## 7. 测试覆盖与缺口

### 7.1 与 S08 相关的现有测试（只读清点，未运行）

| 测试文件 | 类型 | 覆盖的契约 |
|---|---|---|
| `feature/reader/edit/ChapterTextDiffTest.kt`（13） | **真实行为** | 差异往返不变式、落库前提、`TooLarge` |
| `feature/reader/rules/CorrectionProjectionTest.kt`（14） | **真实行为** | 锚定投影：内容校验、漂移跳过、删除坍缩、顺序、key 变化 |
| `ui/viewmodel/ReaderSingleCorrectionCapabilityTest.kt`（14） | **真实行为** | 三层门控、切书失效、陈旧发布忽略 |
| `ui/screen/reader/EpubSingleCorrectionReachabilityTest.kt` | **真实行为（映射层）** | 能力映射 + 锚点契约 |
| `feature/reader/rules/RulesRepositoryTest.kt` | **真实行为** | 落库前提逐条拒绝 |
| `ui/screen/reader/sheets/RuleEditorDraftTest.kt` | **真实行为** | 草稿 → 命令转换 |
| `data/settings/SelectionActionSettingsTest.kt` | **结构守卫 + 行为** | dormant 双向封闭、声明顺序锁定 |
| `ui/screen/reader/ReaderConsumerAndActionGateTest.kt` | **结构守卫（含源码扫描）** | 可渲染 id ⊆ 分发分支；消费者分类完备 |
| `androidTest/.../ReaderRulesSheetTest.kt`（6） | Compose 仪器（仅编译，RUX-009 阻塞） | 规则页 tab / 草稿 |
| `androidTest/.../ReaderSelectionToolbarTest.kt` | Compose 仪器 | 主/次动作分层 |

### 7.2 覆盖的契约（已锁定的不变量）

- 差异块应用回原文**必须逐字还原**编辑后文本（`assertRoundTrip`）；
- 每个块满足纠错表落库前提（区间非空、`findText` 非空白）；
- 纯插入必须归一化为非空锚点；
- 锚定纠错在内容漂移时**诚实跳过**，绝不近似替换；
- 单处纠错三层门控（能力位 / 锚点存在 / 文字一致）；
- 切书与陈旧发布不污染新书会话；
- dormant `edit` 不可配置、不可渲染、写侧拒绝；
- 可渲染动作 id 必须有生产分发分支。

### 7.3 真实缺口（S08 必须补的契约）

1. **章节级批量写入的原子性**：无任何测试，且**无事务实现**。
2. **`ChapterTextDiff` 与真实 `RulesRepository` 的端到端**：现有测试只测纯函数，
   从未证明「diff → 多条纠错 → 重投影 → 编辑器所见 == 阅读器所见」。
3. **块数上限的用户提示**：`TooLarge` 无文案、无测试。
4. **`ChapterTextDiff` 输入规模防护**：无上限、无测试（见 §8）。
5. **编辑器级撤销 / 脏状态 / 取消**：不存在。
6. **EPUB 整章编辑的可行性**：零测试、零生产调用。

### 7.4 哪些测试只是占位或结构守卫

- **纯结构守卫**（不验证用户旅程，只防「漏项」）：`ReaderConsumerAndActionGateTest`
  的源码字符串扫描一项（`every renderable action id has a toolbar dispatch branch`）、
  `SelectionActionSettingsTest` 的 dormant 四条、`ReaderSettingsScopeNoticeTest`（D-2-B 新增）。
- **仅编译未执行**：`ReaderRulesSheetTest`、`ReaderSelectionToolbarTest`——受 RUX-009
  Compose 仪器启动阻塞，`docs/handoff/current.md` 多处明确「只记编译通过，不冒充真机测试通过」。
- **不是占位**：`ChapterTextDiffTest` / `CorrectionProjectionTest` /
  `ReaderSingleCorrectionCapabilityTest` 是真实行为测试，含往返不变式与负面用例。

---

## 8. 数据安全风险

按「若 S08 按最自然方式接线」评估。

| # | 风险 | 级别 | 依据 |
|---|---|---|---|
| R1 | **批量写入无事务 → 部分生效** | **P1** | `RulesRepository` 无 `@Transaction`/`runInTransaction`；`ReaderCorrectionDao` 无批量事务 API |
| R2 | **`ChapterTextDiff` 的 LCS DP 是 O(段数²) 且无输入上限 → OOM** | **P1** | `ChapterTextDiff.kt:96` `Array(n+1){IntArray(m+1)}`。**逐行切段**：256K 字符的单章若按 ~6 字符/行切分 → 约 4.3 万段 → 4.3万² × 4B ≈ **7.4 GB**。滚动路径 `MAX_WINDOW_CHARS = 100_000` 也可能切出上万段。**[推断]**，但数学上确定 |
| R3 | **归一化丢弃块 → 静默少改** | **P2** | `normalizeInsertion` 两侧都取不到非空白锚点时 `return null`，该块被丢弃；若**部分**块被丢弃，`applyBlocks` 无法还原编辑后文本，而返回的是 `Blocks`（不是 `NoChange`）。例：`"abc\n"` → `"abc\n\n"` 时 normalized 为空 → 静默返回 `NoChange`，用户的空行新增被无声忽略 |
| R4 | **`original.isEmpty()` 静默丢弃全部编辑** | P3 | `ChapterTextDiff.kt:52` 返回 `NoChange` |
| R5 | **备份/恢复会丢纠错与替换规则** | **P1（产品级）** | `JsonBridge.LocalExport` 的 13 类实体**不含** `reader_text_rules` 与 `reader_text_corrections`（`JsonBridge.kt:55-74`）；`LocalZipBackup` 的 `backup.json` 复用同一结构（`LocalZipBackup.kt:23`）。即：**用户做了章节编辑后做备份/换机，修改层全部消失** |
| R6 | **无自动备份或副本** | P2 | 纠错只存 Room 单表；无版本、无快照、无导出。`EpubRepository` / `ShelfImporter` 的 `.backup` 机制只覆盖**文件**，不覆盖纠错 |
| R7 | **存储权限变化 / 源文件变更** | 低（安全） | 纠错在 Room，与文件权限无关；`overlayAnchored` 内容漂移时诚实跳过，不产生错位替换 |
| R8 | **App 被杀（保存中）** | **P1** | 同 R1：单条 upsert 原子；批量不原子。Room 自身事务保证单条不损坏 |
| R9 | **破坏原始文件** | **不成立（当前架构）** | 全仓无任何对用户源文件的写路径。**S3b「写回源文件」是唯一被授权打破该边界的例外，且设计上只作用于私有副本** |

**关于「是否可能破坏原始文件」的明确回答 [事实]：
当前架构下不可能。** 纠错是纯投影；`ReaderCorrectionEntity` 的类注释即为此写。
风险只存在于未来 S3b。

---

## 9. P0 / P1 / P2 / P3

| 级别 | 条目 | 说明 |
|---|---|---|
| **P0** | 无 | 当前 `edit` 被 dormant 双向封死，`ChapterTextDiff` 零调用方，**不存在已生效的用户可见缺陷** |
| **P1** | R1 批量无事务 | 实现 S08 前必须先解决，否则产生部分生效的章节编辑 |
| **P1** | R2 DP 无规模上限 | 真实章节长度下可 OOM；接线前必须加段数/字符数上限或改用线性空间算法 |
| **P1** | R5 备份不含纠错 | **产品级**：用户编辑成果不进备份，换机即丢。需产品裁决是否随 S08 一并修 |
| **P2** | R3 归一化静默丢块 | 差异引擎正确性缺口，需补测试 + 显式失败信号 |
| **P2** | `reader_anchor_cache` 不随规则身份失效 | 单点场景无可见影响，批量编辑放大命中面 |
| **P2** | 高亮错位只在跳转时提示 | 阅读页色块可能错位而无提示，需真机确认 |
| **P2** | 无编辑器级撤销/脏状态 | md3 有「撤销」按钮语义；缺失会导致用户无法放弃误改 |
| **P3** | R4 空原文静默丢弃 | 边界 |
| **P3** | `TooLarge` 无用户文案 | 无调用方，暂无影响 |
| **P3** | 删除纠错后端存在但 UI 不可达 | `deleteById/deleteForBook` 零调用方 |
| **P3** | 设计文档未纳入文档索引 | `2026-09-15-reader-selection-edit-design.md` 未跟踪，且未被任何现行文档引用 |

---

## 10. 最小垂直切片建议

**前置判断 [推断]：S08 不应该一次做「整章编辑器」。** 原因：
整章编辑器同时引入（a）新 sheet 与新路由、（b）批量写入与事务、（c）编辑器状态机
（撤销/脏/取消）、（d）EPUB 与滚动模式的能力边界，四件事互相耦合，无法独立验收。

### 建议切片 A（最小、零新写入通路、风险最低）— **推荐先行**

**「选区范围内的多块编辑」**，不改章节语义：

- 入口：解冻 `edit`（从 `DORMANT_IDS` 移除，加入 `DEFAULT_PRIMARY`，落点已由声明顺序锁定在
  「替换」与「字典」之间）；或先复用「替换」页新增第三个目标。
- 范围：**当前选区**，而非整章。编辑器只呈现选区文本，用户改完 diff。
- 保存：块数 == 1 时走**现有** `SaveSingleCorrection`（零新通路）；
  块数 > 1 时才需要批量。
- 收益：把 `ChapterTextDiff` 接入真实链路，验证坐标换算（display→source）与重投影一致性，
  且**不引入批量事务风险**（单块）。
- 代价：**不满足**「整章编辑」的产品预期。

### 建议切片 B（真正的章节编辑，需产品裁决后实施）

在切片 A 已验证坐标链的前提下，按顺序补齐：

1. `ReaderAction` 新增批量命令（含 `bookId / chapterIndex / blocks`）；
2. `RulesRepository` 新增**事务内**批量写入（`runInTransaction`），全成或全不成；
3. `ChapterTextDiff` 增加输入规模上限（段数上限 + 显式失败结果）；
4. `ReaderSheet` 增 `CHAPTER_EDIT` + `ChapterTextEditorSheet`（对齐 md3：顶行 撤销·标题·保存）；
5. `consumesSelection()` 分类 + `ReaderConsumerAndActionGateTest` 的枚举计数（12 → 13）同步；
6. **一次** `applySearchIndexRefresh`（在循环之后，不在循环内）；
7. 能力门控复用 `readerReplacementCapability`，Markdown 与 legacy EPUB 保持不可用；
8. v1 范围明示为「当前渲染单元」，滚动模式不承诺整章。

**明确不做**：S3b「写回源文件」。理由：它会打破「原文件永不修改」这条当前唯一的
数据安全底座，并使进度/高亮/笔记/纠错/索引/TTS 全部失真，属独立立项。

---

## 11. 必须由 Codex / 用户裁决的问题

| # | 问题 | Zcode 的建议（仅建议，不裁决） |
|---|---|---|
| Q1 | **是否允许修改原文？** | **不允许**。保持「原文件永不修改」；S08 只写投影层 |
| Q2 | **是否只保存副本？** | 当前连副本都不改，只写 Room 纠错行。建议维持 |
| Q3 | **是否允许 EPUB 编辑？** | **一期不做**。EPUB 整章文本 → 纠错 → 重投影一致从未验证，且 legacy EPUB 坐标不可用。分页 EPUB 的单点纠错已可用，先维持现状 |
| Q4 | **撤销与恢复承诺是什么？** | 现有能力是「UNDONE 翻转 + 保留历史」。需裁决是否承诺**编辑器级撤销**（丢弃未保存草稿）以及是否承诺**批量编辑的一次性撤销**（一次操作 = 一个撤销单元） |
| Q5 | **数据模型 owner 是谁？** | 纠错表属 `RulesRepository`（已如此）；但**批量章节编辑语义**是否仍塞进「规则」概念，需要裁决——若塞进去，`RuleSnapshot`/规则页会继续承载「编辑」语义，概念会漂移 |
| Q6 | **R5 备份缺口是否随 S08 修？** | 建议**随 S08 一起修**，否则 S08 上线即产生「编辑成果不进备份」的新数据丢失面 |
| Q7 | **`edit` 入口形态** | 是「选区工具条动作」还是「章节菜单入口」？设计文档选前者，但它要求选区非空——与「编辑整章」语义不匹配 |
| Q8 | **块数上限与提示文案** | 沿用 200？超限时是拒绝还是提示改用替换规则？ |
| Q9 | **滚动模式的承诺** | 接受「当前 ReadingUnit」还是要求整章聚合（属另一档工程）？ |

---

## 12. 文件所有权建议

**[事实]** 以下文件都是多切片共享文件，S08 只能按 hunk 集成，不能整文件暂存。
D-2-B（窗口 A）当前占用 `ReaderSettingsSheet.kt`，S08 **不得触碰**。

### 切片 A（建议先行）所需文件

| 文件 | 改动性质 | 冲突风险 |
|---|---|---|
| `data/settings/SelectionActionSettings.kt` | 从 `DORMANT_IDS` 移除 `edit`；可能加入 `DEFAULT_PRIMARY` | **高**：S07 刚改过（+72 行），属共享 |
| `ui/screen/reader/ReaderSelectionToolbar.kt` | 补 `"edit"` 分发分支 + `selectionActionVisual("edit")` | 中：S07 共享 |
| `ui/screen/reader/ReaderInteractionLayer.kt` | 新增 `onEdit` 回调透传 | 中 |
| `ui/screen/reader/ReaderLayerBuilders.kt` | 回调落点接线 | **高**：`docs/code-review/2026-09/2026-09-20-android-s07-selection-review.md` 明确点名它是多切片混合文件 |
| `feature/reader/edit/ChapterTextDiff.kt` | 接入调用方；补规模上限 | 低（新文件，仅 S08 拥有） |
| 测试 | `SelectionActionSettingsTest` / `ReaderConsumerAndActionGateTest` / 新增接线测试 | 中 |

### 切片 B 追加

| 文件 | 改动性质 | 冲突风险 |
|---|---|---|
| `ui/screen/reader/ReaderScreenState.kt` | `ReaderSheet` 增 `CHAPTER_EDIT` + `consumesSelection()` 分类 | **高**：S07 刚改 |
| `ui/screen/reader/ReaderSheetHost.kt` | 新增 sheet 路由分支 | **高**：多切片共享 |
| `ui/viewmodel/ReaderModels.kt` | 新增批量 `ReaderAction` | 中 |
| `ui/viewmodel/ReaderViewModel.kt` | 批量处理器 + 一次索引刷新 | **高**：多切片共享 |
| `feature/reader/rules/RulesRepository.kt` | 批量命令 + 事务 | **高** |
| `ui/screen/reader/sheets/ChapterTextEditorSheet.kt` | 新文件 | 低（S08 独占） |

### 明确禁区

- `ui/screen/reader/sheets/ReaderSettingsSheet.kt`（**D-2-B / 窗口 A 所有**）
- `data/settings/ReaderOverride*.kt`、`ReaderSettingsRouter.kt`（D-2 所有）
- 数据库 schema / DAO 迁移（`AppDatabase.kt`、`DatabaseModule.kt`、`app/schemas/`）——
  S08 若维持「零迁移」则**不需要**触碰；一旦需要新表/新列，必须由集成者单独立项

---

## 13. SEAM REQUEST

本报告为只读，未申请任何写入。若 Codex 放行切片 A，需要以下 seam 授权：

1. **SEAM-1**：`SelectionActionSettings.kt` 的 `DORMANT_IDS` 移除 `edit` 与
   `DEFAULT_PRIMARY` 增补——**需与 S07 已落地的 dormant 守卫测试同步**，
   否则 `SelectionActionSettingsTest` 的 dormant 四条会红（这是设计好的门禁，不是回归）。
2. **SEAM-2**：`ReaderConsumerAndActionGateTest.every renderable action id has a toolbar
   dispatch branch` 会**立即**要求 `ReaderSelectionToolbar.kt` 存在 `"edit" ->` 分支。
   两者必须在**同一次**提交内完成，否则守卫必红。
3. **SEAM-3**：`ReaderLayerBuilders.kt` 属 S07 已点名的多切片混合文件，
   需要集成者分配 hunk 边界。
4. **SEAM-4**：若裁决要求「批量事务」，`RulesRepository` 需引入 `@Transaction`
   或 `AppDatabase.runInTransaction`，该文件同时被替换规则与目录规则使用，需独占授权。
5. **SEAM-5**：若裁决要求修 R5（备份缺口），`JsonBridge.kt` + `LocalZipBackup.kt` +
   `LocalExport` schema 版本需由集成者单独立项——**它会改变备份格式**。

**未申请**：任何数据库 schema 变更、任何源文件写入通路、D-2-B 文件、S09–S20 文件。

---

## 14. 未确认项

| # | 未确认内容 | 需要什么 |
|---|---|---|
| U1 | `ChapterTextDiff` 在真实章节长度下的耗时与内存 | 需要运行（JVM 基准或真机）；本轮**未运行 Gradle**，仅静态推导 |
| U2 | 纠错后高亮/笔记色块是否在阅读页可见错位 | 真机 + 已有高亮的测试书 |
| U3 | EPUB 分页整章编辑的端到端可行性 | 需先实现最小链路并真机验证 |
| U4 | `reader_anchor_cache` 过期是否产生可见错误跳转 | 真机复现路径未构造 |
| U5 | 200 块上限是否覆盖真实编辑行为 | 需产品/用户样本 |
| U6 | `ChapterTextDiffTest` 13 tests 当前是否全绿 | 本轮**未运行 Gradle**（按任务约束）；`docs/handoff/current.md` 的历史全量数字不能代替本轮证据 |
| U7 | `edit` 解冻后第一屏 5 槽在小屏的可点性 | 设计文档 §S1 已列为待真机验收项，至今无证据 |

---

## 15. 是否建议进入实现

**不建议现在直接进入实现。** 建议顺序：

1. **先由 Codex 裁决 Q1–Q9**（尤其是 Q3 EPUB、Q4 撤销承诺、Q5 数据模型 owner、Q6 备份缺口）；
2. **再放行切片 A**（选区范围内的多块编辑，零新写入通路、单块即可交付）；
3. **切片 B 前必须先解决 R1（事务）与 R2（DP 规模上限）**——这两项是实现的硬前提，
   不是可选优化；
4. **R5（备份不含纠错）建议与 S08 同批处理**，否则 S08 上线即引入新的数据丢失面；
5. **S3b（写回源文件）必须独立立项**，不应作为 S08 的一部分。

**理由摘要**：当前没有已生效的 P0/P1 用户可见缺陷，`edit` 被 dormant 正确封死，
因此**没有「必须马上修」的压力**；而 S08 一旦接线，引入的是数据写入面
（批量纠错）与新的数据丢失面（备份缺口），仓促实施的风险高于等待裁决的成本。

---

## 16. 回传摘要

- **Agent / 模型**：Zcode / `deepseek v4.1Flash`
- **报告绝对路径**：
  `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\investigations\agent-investigation-s08-zcode.md`
- **问题数量与最严重项**：P0 × 0；**P1 × 3**（批量写入无事务、`ChapterTextDiff` DP 无规模上限可 OOM、
  备份不含纠错表）；P2 × 4；P3 × 4。最严重为 **P1「备份/恢复会丢失全部章节修改层」**（产品级）
  与 **P1「批量写入无事务会产生部分生效的编辑」**（实现级）。
- **核心事实**：`feature/reader/edit/` 仅 1 个产品文件（207 行，**未跟踪**）+ 1 个测试（13 tests），
  **生产调用方为 0**；`edit` 动作被 `DORMANT_IDS` 双向封死、工具条无分发分支；
  单处纠错链路完整可达但**结构上拒绝被章节编辑复用**（选区覆盖锚点 + 文字校验 + 无批量通路）。
- **测试命令与结果**：**未运行任何测试，未运行 Gradle**（按任务约束，避免与窗口 A 抢构建锁）。
  本报告全部结论来自 `rg` / 文件读取 / `git diff` / `git status` 等只读操作。
- **是否修改了报告以外文件**：**否**。仅新增本报告一个文件；未修改产品源码、测试源码、
  Gradle、数据库、看板、handoff 或任何其他报告。
- **未处理**：D-2-B、S09–S20、S07 遗留项、原始仓库，全部未触碰。
- **状态**：**等待 Codex 主任务复核与产品裁决，未领取下一片。**
