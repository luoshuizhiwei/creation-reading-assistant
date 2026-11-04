# S07-F4 独立复核（Qoder）

- 复核 Agent：Qoder　|　实际模型：`qwen3.8Flash`（按任务书标注；本 Agent 无法自证底层型号）
- 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`
- 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`；我复核时 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
- 已读输入：`AGENTS.md`、`docs/code-review/2026-09/2026-09-20-android-s07-selection-review.md`、`docs/coordination/2026-09-20-zcode-audit-board`（`2026-09-20-zcode-qoder-audit-board.md`）、`agent-result-s07-f31-zcode.md`、`agent-review-s07-f31-qoder.md`（我上一轮）、`agent-result-s07-f4-zcode.md`（mtime `23:49`，14 节完整；我 00:26 与后续两次读取内容稳定 → 已停止写入）
- F4 实际写入窗口（mtime）：测试 `23:09 / 23:16 / 23:21 / 23:29 / 23:38`，产品 `23:20 / 23:25 / 23:26 / 23:32`
- 标注：**[事实]** 我读到的代码或跑过的命令；**[推断]** 静态推导；**[UNVERIFIED]** 需真机/Compose 运行期

## 总结判定：**FAIL**

一个由 F4 新引入的 **P1 功能回归**：ViewModel 的 capability 门把**分页 EPUB** 的单处纠错也一并拒掉了，而工具栏与规则页仍如实提供该入口（引擎侧对分页 EPUB 的能力裁决是 `Available`）。F4 引入这道门的目的（纵深防御）是对的，但事实源选错了层级，并且唯一的"同源"测试把待证结论写进了期望值。

| 轴 | 总数 | P0 | P1 | P2 | P3 | 最严重 |
|---|---:|---:|---:|---:|---:|---|
| Standards | 6 | 0 | 1 | 2 | 3 | **ST-1 分页 EPUB 被误伤（同源测试为循环论证）** |
| Spec | 5 | 0 | 1 | 1 | 3 | **SP-1 同一命令在 UI 允许、VM 拒绝，用户点保存收到"格式不支持"** |

（P1 只有一条，两个轴上是同一件事的两面：工程上事实源选错，行为上功能被砍。）

---

## Standards

| # | 检查项 | 判定 | 证据 |
|---:|---|---|---|
| 1 | 纠错命令四层（UI / action / VM / repository）重新追踪 | **PASS（我的追踪）** | UI：`ReaderScaffold.kt:562 canCreateReplaceRule = readerReplacementCapability(pagerEngine.replacementAvailability) is Available` → `selectionToolbarActions` 摘槽；action：`RuleCommand.SaveSingleCorrection`（`RuleEditorDraft.kt:136`）；VM：`ReaderViewModel.kt:502-519` 新门 + `resolveSingleCorrectionAnchor:605-627`；repository：`RulesRepository.kt:309-340`（我逐条核对：只校验区间顺序、`findText.isBlank()`、`replaceText == findText`，**不校验偏移处文字**） **[事实]** |
| 2 | legacy/非分页 EPUB 绕过 UI 直接派发是否被拒 | **PASS** | `ReaderLoadedContent.Epub → ESTIMATED_COORDINATES → Unavailable → NotAnchorable`，且 `ReaderSingleCorrectionCapabilityTest."a bypassed correction on epub is rejected at the capability gate"` 断言 `FakeReaderCorrectionDao` 行数 = 0（不只是不报错）—— 我这次跑过 11/11 绿 **[事实]** |
| 3 | **支持替换的 TXT / Markdown / 分页来源有没有被误伤** | **FAIL —— 见 ST-1** | 分页 EPUB 被误伤 **[事实]** |
| 4 | capability 是否来自可信事实源，而非"坐标存在/文字相同"推断 | **部分 FAIL** | 新函数确实不看坐标与文字（只用 `ReaderLoadedContent` 类型）✓；但"内容类型"对 EPUB **不足以**决定量纲安全性 —— 真正的裁决在引擎侧：`preparePagedReplacement`（`ReplacedChapterSource.kt:99-118`）对 `EpubChapterSource` **不走** `ESTIMATED` 分支，而是按 `classifyEpubChapterScopes` 的 verdict 给 `APPLIED / PARTIALLY_APPLIED / ALL_SCOPES_OVERSIZED / UNVERIFIED_CHAPTER_LENGTHS`，无生效规则时给 `NO_EFFECTIVE_RULES`；`availabilityFor:307-314` 与 `ReaderReplacementCapability.kt:25-38` 把这五种全部映射为 **`Available`**。**[事实]** |
| 5 | 拒绝路径是否先写入后报错 / 仍显示虚假成功 | **PASS** | 门在 `command == null → return@io`（`:521-522`）之前，`rulesRepository.execute` 未调用；DAO 行数 = 0 的行为断言覆盖 **[事实]**；提示走 `_ruleMutationResult = NotAnchorable(...)`，无成功文案 **[事实]** |
| 6 | 是否只有字符串守卫、缺状态级行为测试 | **PASS（这道门有真行为测试）** | `ReaderSingleCorrectionCapabilityTest` 11 项里 7 项是"真实 `documentLoader.load` + 真实派发 + 真实 DAO 断言"，非源码字符串 **[事实]**。但同文件的"同源"用例是循环论证（ST-1 附） |
| 7 | 切书 / 文档身份变化是否清活动选区与快照 | **PASS（快照）**，见 SP-2 保留项 | `ReaderViewModel.kt:644-656` 切书分支链上补 `.withSelectionSnapshotReleased()`；`ReaderSelectionSnapshotTest` 现 21 项（F4 +4）含反向钉子 `reopening the same book does not release an in flight snapshot` **[事实]** |
| 8 | 消费者/动作分类是否需要显式声明、新增枚举是否会失败 | **PASS** | `ReaderScreenState.kt:53-69` 的 `consumesSelection` 是**无 `else` 的穷举 `when`**（我逐行核对：4 true + 8 false，无默认分支）→ 新增 `ReaderSheet` 值**编译失败**，不是静默 false **[事实]**；`ReaderConsumerAndActionGateTest` 再加反射枚举集合 + 源码级"无 else"双保险 **[事实]** |
| 9 | dormant `edit` 四个方向是否仍封闭 | **PASS** | `pickableDefs()` 排除 `DORMANT_IDS`（读侧/渲染侧/设置页/兜底）；写侧 `setActionEnabled` 仍不拒绝 dormant（我 F1 起的 ST-1 遗留，本轮未改也未被要求改）→ 记为 P3 遗留；`dormant actions are neither configurable nor renderable` 用例在场并通过 **[事实]** |
| 10 | copy 是否仍服从剪贴板真实返回值 | **PASS** | `ReaderLayerBuilders.kt:352-355` 未变（F3.1→F4 跨轮段比对 `ReaderLayerBuilders.kt` 不在本轮改动清单，且我核对 `copyText` 调用仍取返回值喂 `readerCopyNotice`）；`ReaderCopyNoticeTest` 3 项绿 **[事实]** |
| 11 | 外部查询提示是否"真打开成功后才提示" | **FAIL（账本标记失实）** | `ReaderSelectionExternalActionsTest:129` 仍断言 `[NOTICE_OPENING_BROWSER, NOTICE_NO_BROWSER]`、`:190` 仍断言在线词典失败复用"未找到可用浏览器" —— 即**先报"正在打开"、失败后再报另一条**的时序（我 F1 报告的 Q-3）从未修过，而 F4 §2 把 L7 记为"已核实/已固化，未重复造轮子" **[事实]**。见 ST-2 |
| 12 | 结构守卫与行为测试是否分开计数 | **PASS，且我重算** | 见 Verification 的分列计数 |
| 13 | 是否改授权外文件 / 夹带 F4 之外内容 / 无关格式化 | **PASS** | 见 Scope integrity：4 个产品文件 + 3 新测试 + 2 测试改写；F3.1 那 4 个跟踪文件里 3 个段 `IDENTICAL`，只有声明过的 `MarkdownScrollHighlight.kt` 变化 **[事实]** |
| 14 | before/after 是否同一文件集、同一命令、numstat 可复现 | **本轮无 before/after 快照（方法退化）** | 见 Scope integrity ST-3；我用哈希 + 跨轮段比对补偿性验证 |

### ST-1 [P1] ViewModel 能力门误伤分页 EPUB 的单处纠错，且"同源"测试是循环论证

**回归链路（每一环我都读到源码）：**

1. 分页 EPUB 引擎侧可用性：`preparePagedReplacement:99-118` 对 `EpubChapterSource` **提前 return**，永远不会落到 `ReplacementCoordinateSpace.ESTIMATED → ESTIMATED_COORDINATES` 那条分支；返回的是 `NO_EFFECTIVE_RULES`（无生效规则）或 `availabilityFor(verdict)` ∈ {`APPLIED`, `PARTIALLY_APPLIED`, `ALL_SCOPES_OVERSIZED`, `UNVERIFIED_CHAPTER_LENGTHS`}。
2. 这五种经 `ReaderReplacementCapability.kt:25-38` 全是 **`Available`**（`ReaderReplacementCapabilityTest:115` 就是断言 `NO_EFFECTIVE_RULES → Available` 的既有测试 **[事实]**）。
3. 于是 `ReaderScaffold.kt:562` 的 `canCreateReplaceRule = true` → 工具栏 `替换` 槽渲染 → `OpenSheet(RULES)`（`ReaderLayerBuilders.kt:384`）→ F2.4 捕获快照 → `allowCorrection = pinnedReplaceText != null` = true → 「单处纠错」可勾选。
4. 保存 → F4 新门 `currentReplacementCapability()` = `readerReplacementCapability(readerLoadedContentReplacementAvailability(Epub))` = `ESTIMATED → Unavailable` → `NotAnchorable("当前书籍格式暂不支持单处纠错：当前文档使用估算章节坐标…")`。

净结果：**F4 之前能保存并生效（锚点落在引擎自己声明的"章内真实字符 + 既有混合空间"，与 `EpubReplacedChapterSource` 的投影空间和 `LocatorBuilder.forEpub` 的保存/回读空间同一个）→ F4 之后 UI 照样给入口，保存被拒**。这既是我 F3.1 报告 ST-2 建议的"补 VM 门"被实现得过头，也正是任务书一-3 点名要查的"支持替换的分页来源被误伤"。

**"同源"测试为什么没抓住它：** `ReaderSingleCorrectionCapabilityTest.kt:384-398` 比较的是
`readerReplacementCapability(新helper(内容))` 与 `readerReplacementCapability(ESTIMATED_COORDINATES)`
—— 右值把"UI 会看到 ESTIMATED"**当输入写死**，因此只证明了同一个函数被调用两次，没证明**同一个枚举值**被两端使用。真正需要断言的是"UI 传入的 availability 与 VM 推导的 availability 相等"，而 UI 传的是 `pagerEngine.replacementAvailability`。**[事实]**

**最小修正建议（F4 范围内可闭合，不需扩大边界）：**

1. 让 VM 用**同一个事实源**：把 `pagerEngine.replacementAvailability` 发布进 VM（本仓已有同类模式：`ReaderScaffold.kt:640` 已经把它传进 `buildReaderSheetHostState`；再加一个 `ReaderAction.SetReplacementAvailability` 或复用现有 holder），`currentReplacementCapability()` 读该字段，未发布前按 `SOURCE_UNAVAILABLE` 拒绝。这样 TXT / 分页 EPUB 放行、legacy EPUB（`PAGER_ENGINE_DISABLED`）与 Markdown（`NON_SOURCE_COORDINATES`）拒绝，与 UI 完全一致。
2. 若本轮不想动 action 层：**至少**把 `ReaderLoadedContent.Epub` 从"一律 Unavailable"改为"分页引擎不可用时才 Unavailable"，或者暂时回退这道 EPUB 分支（保留 Markdown/null 两条），因为"UI 给入口 + VM 拒绝"比"没有 VM 门"更伤用户。
3. 把那条"同源"测试改成**行为级双钉**：`Epub + 引擎 APPLIED/NO_EFFECTIVE_RULES → 接受并落库（DAO 行数 1）`；`Epub + 引擎 PAGER_ENGINE_DISABLED → 拒绝（DAO 行数 0）`。这两条现在都没有，所以 11 项全绿仍然掩盖了回归。

### ST-2 [P2] 账本 L7 标记与事实不符（把"未修"记成"已固化"）

F4 §2 的 L7 写"外部动作提示真实性 —— TEST（已存在）—— 已核实 `ReaderSelectionExternalActionsTest`（14）全绿"。全绿是事实，但它钉住的是**当前行为**，而当前行为正是我 F1 报告 Q-3 指出的问题：`NO_HANDLER` 时先弹"正在打开浏览器…"再弹"未找到可用浏览器"，以及在线词典失败复用"未找到可用浏览器"文案。把它记为"已核实/已固化"会让 Codex 以为该遗留项已处理。建议改标为 **OPEN（P2，未修）**。L6（复制提示）我核对为**真已修**（`ReaderLayerBuilders.kt:352-355` 用返回值），两者不要混为一谈。 **[事实]**

### ST-3 [P2] 证据方法退化：本轮没有 before/after 同文件集快照

F1–F3.1 四轮都有 `/tmp/s07fXx-before.patch` + `after.patch`；本轮只有 7 个文件的 SHA-256 与 3 个 untracked 文件的 `git diff --no-index` 行数。哈希能证明"文件现在长这样"，**不能**证明"同文件内其它 hunk 未被顺手动过"。我做的是上一轮就建议的"用跨轮基线补"：把 F3.1 的 `after.patch` 当作 F4 的起点基线，对本轮 4 个共同跟踪文件逐段比对（见 Scope integrity），结论是无隐藏改动 —— 但这是**复核方代为重建的证据**，不该成为惯例。建议 F5 或后续轮次恢复"同清单同命令"双快照。

### ST-4 [P3] `RenderMarkdownUnit` 仍有 `blockGlobalOffset: Int = -1` / `chapterBase: Int = 0` 默认值

F3.1 把 `blockGlobalBase` 改成必填，但同一函数签名上仍并存两个同类型裸 Int 且带默认值（`ReaderMarkdownRender.kt:247-248`）→ 传错顺序/漏传仍可静默错位。任务书五-4 明确"不要求大重构，但可静默误接必须列为遗留"。 **[事实]** 另：`consumesSelection` 的编译期穷举已由 `when` 保证，反射与源码守卫属重复保险（无害）。

### ST-5 [P3] 额外一次 `addStyle` 遍历的性能未测

F3.1 为守边界选择在 `buildSentenceHighlighted` 结果上再叠一层；F4 未处理也未测量。按任务书六-8：这只审核"是否诚实记录并转交"→ 它 §10/§11 已记录，转交 WorkBuddy 待办清单**未**包含性能体感项。建议在交给 WorkBuddy 的清单里补一条"长章 + 多条高亮 + TTS 同开时的滚动流畅度"。

### ST-6 [P3] `ReaderSheetHost.kt` / `ReaderRulesSheet.kt` 的"未触碰"证据

它 §11 用"F3.1 标记计数为 0"+ mtime 说明；我改用哈希：`ReaderSheetHost.kt` 当前 `b330a66d7549be1d` = 我在 F2.4 复核登记的同一前缀值 **[事实]**；`ReaderRulesSheet.kt` 当前 `0d63026fdb25ce42`，同样与 F2.4 登记一致 **[事实]**。`ReaderSelectionToolbar.kt`（本轮 mtime `23:26` 落在 F4 窗口，它自称"读后重写、内容未变"）我**无法**用快照证明（前几轮清单都不含它），但内容侧证一致：分发表仍是 10 个 `-> on...` 分支、无 F4 标记、`every renderable action id has a toolbar dispatch branch` 用例通过。记为"未发现改动，但证据为间接"。 **[推断]**

---

## Spec

### SP-1 单处纠错（任务 D）：**FAIL**

- legacy/非分页 EPUB：即使绕过 UI 也会被拒，且**证明未落库**（DAO 行数 0）—— 这部分**达成** **[事实]**。
- 但**分页 EPUB 的正常纠错路径被同一道门砍掉**（ST-1）：UI 提供入口、保存被拒 → 新用户可见回归 **[事实]**。
- TXT：接受路径有测试（`source_start=100 / source_end=102` 落库断言）+ 两条拒绝路径（findText 不等、无锚点）—— 我跑过 11/11 绿 **[事实]**。
- Markdown：VM 门与 UI 门（`NON_SOURCE_COORDINATES → Unavailable`）结论一致，无"UI 给入口"的矛盾 **[事实]**。
- 混合量纲是否足以安全改写：按任务书"任何可执行纠错路径均判 FAIL" —— 现在 legacy EPUB 无可执行路径 ✓，但分页 EPUB 被一并禁掉，属于"该允许的没允许"。 **[事实]**

### SP-2 切书快照（任务 二）：**PASS**

`ReaderViewModel.kt:644-656` 释放快照；`ReaderSelectionSnapshotTest` 21 项（+4）覆盖：切书释放、活动选区保留（正交）、同书重开不误清、`_ruleMutationResult` 一并归零 **[事实]**。
保留一点：**活动选区跨书仍不清**（`:652-655` 不动 `selectedText` 等四字段）—— 这是我从 F1 起登记的既有问题（正式报告曾以"新 ReaderUiState 会清掉"驳回，但 `screenState` 是独立 StateFlow，此分支只重置 chrome 与 `searchQuery`，我在 F2.4 复核 ST-1 已记过一次）。F4 只释放了快照，等于把"跨书最有害的那份数据"关掉了，但 live 选区仍可能跨书显示一次工具栏。**[事实 + 推断]** 建议 F5 顺手补 `.withSelectionCleared()`（同一处一行）。

### SP-3 Markdown `charCount = 0`（任务 四）：**PASS（降级面比它自己声明的更窄）**

- `ReaderContentHostMarkdownBranch.kt:58` 的 `chapterLength` 来自 `chapters[chapterIndex].charCount`，而 `MarkdownDocument.kt:105/119/136/151/303` 全部用 `canonicalText.length` 或 heading 区间差计算 ⇒ 对**正在渲染**的章节恒 > 0；`<= 0` 只在 `getOrNull` 越界（章节身份与文档不一致的瞬态）时出现 **[事实]**。
- 因此它 §7 承认的代价（"别章高亮可能被画进本章"）在生产里是**近不可达的防御分支**，且仍受"单元求交"约束（`the degradation is bounded by unit intersection not by wishful matching`）；搜索命中在未知长度时直接返回空列表（不猜），没有用文本搜索/标题匹配/当前章回填伪造 canonical 归属 ✓ **[事实]**。
- 半开边界与已知长度正向行为有独立用例（`a known chapter length restores the chapter span filter`、`a known chapter length highlights the search hit normally`）—— 8/8 我跑过绿 **[事实]**。
- 我按任务四-4 的口径评级：不升级；作为 P3 记录（"降级为有注释的显式裁决 + 有界"）。**两条路径降级口径不同**这一点也被显式钉住（`the two paths degrade differently on purpose`），这是诚实而非遗漏。

### SP-4 消费者与动作分类（任务 三）：**PASS（除 L7 记账外）**

- 新增 sheet 必须显式作答（编译期穷举）✓；新增 action 若没有分发表分支，`every renderable action id has a toolbar dispatch branch` 会红 ✓；dormant `edit` 在设置页/渲染/兜底/恢复四方向仍封闭 ✓（写侧 `setActionEnabled` 仍不拒绝，属 P3 遗留）[**[事实]**
- copy 真实返回值仍被遵守 ✓ **[事实]**
- 外部查询提示：**未达成**"真正打开成功后才提示成功"，见 ST-2 **[事实]**

### SP-5 坐标与范围（任务 五）：**PASS，含两项遗留**

- Markdown 四槽语义未被 F4 回退：`MarkdownBlockSelectionLocatorTest` 10 项（含 `swapping the canonical start back into globalOffset yields no locator` 反向钉）我跑过全绿 **[事实]**。
- legacy EPUB `gOff` 未被宣称为精确 source 字符坐标：`ReaderContentHostEpubBranch.kt` 本轮**未改**（跨轮段 `IDENTICAL`），其"旧版混合量纲"KDoc 仍在 **[事实]**。
- 遗留：`blockGlobalOffset` / `chapterBase` 默认值仍在（ST-4）。
- 跨页选择未扩展：分页侧把手钳制与"长按整句可越页"的差异仍是我 F2 轮登记的未关闭项；本轮未新增任何会落错坐标的路径 **[事实]**。

### SP-6 绝不能写成"已通过"的清单

1. 分页 EPUB 的单处纠错（本轮被误伤，修好后仍需真机确认落库→重开→回显）。
2. 外部查询/词典的提示真实性（先"正在打开"后失败，词典失败借用浏览器文案）。
3. 任何真机行为：小屏/横屏/大字体五动作拥挤与截断、触摸目标、TalkBack 焦点与手柄语义、无 handler 的真实回退、TTS 跟读 → `scrollToItem` → dispose → 消费者仍拿到快照。
4. 滚动流畅度（额外 `addStyle` 遍历的代价未测）。
5. 跨页选择能力与降级反馈。
6. S07 整体可发布性。

---

## Verification

### 独立解析 FQCN 后运行（避免我前几轮反复踩的零匹配陷阱）

我按"先解析真实 FQCN，再运行，再核对 XML 类数"的顺序做。本轮特意包含 `SelectionActionsTest` / `SelectionActionStoreTest`（声明在 `SelectionActionSettingsTest.kt` 里，文件名≠类名）与 `ui.screen.reader.tts.*` 子包，全部解析成功。

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests <23 个已解析 FQCN> :app:compileDebugKotlin
```

- 退出码：**0**；`BUILD SUCCESSFUL in 49s`；`:app:testDebugUnitTest` 真实执行（`--rerun`）
- `:app:compileDebugKotlin`：**UP-TO-DATE**（当前内容此前已成功编译，本轮未产生新编译动作）**[事实]**
- **请求 23 类 == XML 23 个文件，缺失类 0，零匹配 0** **[事实]**

| 测试类 | 归属 | tests | skipped | failures | errors |
|---|---|---:|---:|---:|---:|
| `ui.viewmodel.ReaderSingleCorrectionCapabilityTest` | F4 新增 | 11 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderSelectionSnapshotTest` | F4 改写（21，F2.4 为 17） | 21 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderConsumerAndActionGateTest` | F4 新增 | 6 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownUnknownChapterLengthTest` | F4 新增 | 8 | 0 | 0 | 0 |
| `ui.screen.reader.EpubSingleCorrectionReachabilityTest` | F4 改写 | 8 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownBlockSelectionLocatorTest` | F3.1 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownHighlightChapterSpanTest` | F3.1 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownPersistentHighlightProjectionTest` | F3/F3.1 | 11 | 0 | 0 | 0 |
| `ui.screen.reader.EpubBlockPersistentHighlightTest` | F3.1 | 15 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderReplacementCapabilityTest` | 能力映射 | 15 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionRangeWiringGuardTest` | F3.1 守卫 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderConsumerSnapshotWiringGuardTest` | F2.4 守卫 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionSnapshotSemanticsTest` | F2.4 | 12 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderNoteSnapshotSaveTest` | F2.4 | 6 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitDisposeWiringGuardTest` | F2.4 守卫 | 8 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitSelectionOwnershipTest` | F2.2 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderCopyNoticeTest` | F1 | 3 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionToolbarModelTest` | F1 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionExternalActionsTest` | F1 | 14 | 0 | 0 | 0 |
| `ui.screen.reader.TxtScrollChapterTargetTest` | F2.1 | 7 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderViewModelTest` | F2 | 72 | 0 | 0 | 0 |
| `data.settings.SelectionActionsTest` | F1 | 24 | 0 | 0 | 0 |
| `data.settings.SelectionActionStoreTest` | F1 | 13 | 0 | 0 | 0 |
| **合计（23 类）** | | **311** | **0** | **0** | **0** |

**它 §9 的 F4 类计数逐类命中**：11 / 21 / 6 / 8 / 8（以及 15、9、12、6 等哨兵项）全部与我的 XML 一致 **[事实]**。

### 行为测试 vs 结构守卫（我自己的分列口径）

| 类别 | 类 | tests |
|---|---|---:|
| **结构/源码守卫**（读源文件文本、反射枚举集合） | `ReaderConsumerAndActionGateTest` 6、`ReaderSelectionRangeWiringGuardTest` 9、`ReaderConsumerSnapshotWiringGuardTest` 9、`ReadingUnitDisposeWiringGuardTest` 8 | **32** |
| **行为/语义测试**（真实 loader、真实派发、真实 DAO/投影/状态断言） | 其余 19 类 | **279** |

它 §9 的"S07 子集 24 类 216 项（守卫 7 类 61 / 行为 17 类 155）"是另一个统计口径（含我没跑的 F2.x 类、且把 `EpubSingleCorrectionReachabilityTest` 等混合类整类归类），**不与我的数字矛盾**；我不采用它的分母，只说明：本轮 5 个新/改测试类里，**只有 6 项**（`ReaderConsumerAndActionGateTest`）属结构守卫，其余 35 项（11 + 8 + 4 + 12 的一部分）是真实执行的行为断言 —— 这一点它 §9 的说法与我一致。

### 未验证与未运行

- 我没跑全量 JVM / Lint / APK / androidTest / 设备（任务禁止）；未复现它的变异（要改产品源码，禁止）。**替代取证**：它声明的 7 个文件哈希我 7/7 复算命中（见下），且本轮工作树内容 = 它写报告时的内容（哈希一致即等价），所以"变异未还原"可排除。
- 它 §9 的 908 项通配运行里有 1 个失败：`ProfileWebDavManagerTest > downloadRestore fetches and imports backup json`（JVM 主调度器环境）。该类**不在我的运行范围内**，我未独立复现；它的"与 S07 无关、属他人在途 M 文件"的说法与我看到的 git 状态一致（该测试文件在 F4 的 mtime 窗口内未被写） **[事实：未写] / [未验证：失败根因]**。这条留给 Codex 归属裁决，我不替它定性为"环境噪声"。

### 我这一侧的边界自守

`git diff --cached --name-only` = 0；`git stash list` = 空；HEAD 仍 `5aed593`。未修改任何产品源码或测试、未修改 Zcode 报告/正式报告/盘点/Obsidian；只新增本报告；未 `reset`/`checkout`/`clean`/`commit`/`push`；未操作设备或 MuMu；未访问 `D:\Application\文档\Obsidian\Claude`；未动原仓库。

---

## Scope integrity

**方法学差异必须先说：本轮 Zcode 没有生成 before/after 快照**（前四轮都有 `/tmp/s07fXx-before.patch`/`after.patch`），`/tmp` 下只有 `s07f31-*`。我因此改用两条独立路径重建范围证据。

1. **文件哈希 7/7 命中**（我 `sha256sum` 前 16 位 vs 它 §3 全 64 位申报值）：

| 文件 | 我的实测 | 它的申报（前 16） |
|---|---|---|
| `ReaderViewModel.kt` | `1e83dd39ba89b068` | `1e83dd39ba89b068` ✓ |
| `MarkdownScrollHighlight.kt` | `89bd78e2c77d637c` | `89bd78e2c77d637c` ✓ |
| `ReaderSingleCorrectionCapabilityTest.kt` | `cea979a8a6b77bcd` | `cea979a8a6b77bcd` ✓ |
| `ReaderSelectionSnapshotTest.kt` | `e9c2785d17cdfef5` | `e9c2785d17cdfef5` ✓ |
| `ReaderConsumerAndActionGateTest.kt` | `942c57bdf71de256` | `942c57bdf71de256` ✓ |
| `MarkdownUnknownChapterLengthTest.kt` | `810596f8ca6bb72b` | `810596f8ca6bb72b` ✓ |
| `EpubSingleCorrectionReachabilityTest.kt` | `c770a357a332f427` | `c770a357a332f427` ✓ |

2. **跨轮段比对（我代建的基线）**：拿 F3.1 的 `after.patch`（F3.1 收工状态）当 F4 起点，对 4 个共同跟踪文件逐段比较 **[事实]**：

| 文件 | F3.1 段 → F4 段 |
|---|---|
| `MarkdownScrollHighlight.kt` | 267 → 288 **CHANGED**（它声明的 2 个 KDoc hunk，+21 行） |
| `ReaderContentHostEpubBranch.kt` | 170 → 170 **IDENTICAL** |
| `ReaderContentHostMarkdownBranch.kt` | 93 → 93 **IDENTICAL** |
| `ReaderProgressActions.kt` | 159 → 159 **IDENTICAL** |

即：F3.1 动过的文件里，F4 只动了它自己声明的那一个；`ReaderViewModel.kt` 从 F2.4 的 `fb13c6b58ad8db03` 变为 `1e83dd39ba89b068` = 本轮声明的 4 个 hunk **[事实]**。

3. **授权外与"显示为 M 但本轮未动"**：`ReaderSheetHost.kt`（`b330a66d7549be1d`）、`ReaderRulesSheet.kt`（`0d63026fdb25ce42`）与我在 F2.4/F3.1 登记的值一致 → 未动 **[事实]**；`ReaderSelectionToolbar.kt` 只有间接证据（ST-6）。
4. **未跟踪文件**：它按任务书六-5 的要求改用"清单 + `git diff --no-index` + SHA256"，未用 `git add -N`（我核 `/tmp` 里也没有 `git add -N` 的痕迹，`git diff --cached` 为空可佐证 index 未被触碰）**[事实]**。3 个新测试文件的 insertions 数（511/181/195）我未逐一重跑 `--no-index`，但行数与哈希一致，可信度高。
5. **并发覆盖**：`21:30–23:09` 之间无任何产品/测试文件被写入（mtime 扫描只见 22:09 我上一份报告与 22:17 Codex 侧文档）→ F4 起点无第三方插入 **[事实]**。
6. **夹带**：`git diff` 的新增行未出现 F4 之外的功能语义改动（我看到的产品侧改动集中在 4 处：helper、门控、锚点解析、切书释放 + 2 处 KDoc）**[事实]**；`git status --porcelain -uall` 371 → 375（+4：3 个新测试 + F4 报告），与我这次运行的读写足迹一致；本报告写出后为 376。

**方法缺陷结论**：本轮证据完整性靠"哈希 + 跨轮基线"成立，但**不应成为惯例**（ST-3）。这不是范围污染，是取证纪律回退。

---

## 逐项 F4 遗留账本复核

| # | Zcode 标记 | 我的独立裁决 |
|---|---|---|
| L1 | FIX + TEST（VM 能力门） | **部分 FAIL**：门方向正确但过宽，误伤分页 EPUB（ST-1 [P1]）；"同源"测试为循环论证 |
| L2 | FIX + TEST（切书释放快照） | **达成**（`ReaderViewModel.kt:655` + 4 项行为测试，含反向钉子）；live 选区跨书仍未清 = 既有遗留，未扩大 |
| L3 | TEST（分类表，源码已穷举） | **达成**（无 `else` 的穷举 `when` 我逐行核对；编译期即保证，反射/源码守卫属额外保险） |
| L4 | TEST（分发表完整性） | **达成**（`every renderable action id has a toolbar dispatch branch`；分发表 10 分支与可渲染集合一致，我核对） |
| L5 | FIX + DOCUMENT + TEST（`charCount=0`） | **达成**：两条路径降级口径不同且被显式断言；生产可达性比它自述更窄（SP-3）→ 我降为 P3 记录 |
| L6 | TEST（复制提示已固化） | **达成**（调用点确实取返回值） |
| L7 | TEST（外部提示已固化） | **标记失实**：钉住的是"先报正在打开、失败后再报另一条"的既有形态，F1 提出的 Q-3 未修 → 应记 OPEN P2（ST-2） |
| L8 | DOCUMENT（更正 F3.1 双重门控措辞） | **达成**：更正方向与我 F3.1 ST-1 一致，且现在把第二道门指向 VM 层（但见 L1 的实现问题） |
| L9 | DOCUMENT（术语 canonical/整块） | **达成**（`ReaderContentHostEpubBranch.kt` 本轮未改，KDoc 未退化） |
| L10 | DOCUMENT（`gOff` 混合量纲） | **达成**（同上） |
| L11 | TEST（dormant 写侧） | **部分**：读/渲染/兜底/设置页四方向封闭 ✓；写侧 `setActionEnabled` 仍不拒绝 dormant（我 F1 起登记的同一遗留）→ P3 |
| L12 | DOCUMENT（快照口径） | **达成**（写进报告 §2/§6，正式报告是否吸收由 Codex 决定） |

---

## 四个必答问题

1. **F4 是否关闭？** **未关闭。** L1 的实现引入 P1 回归（分页 EPUB 单处纠错被 VM 误拒），且缺两条本该有的行为钉子；L7 记账失实。其余 10 项达成。
2. **S07 是否可以进入 WorkBuddy 真机验收？** **建议暂不放行**：先修 ST-1（范围小：一处映射改为引擎事实源 + 两条行为测试），否则真机上会看到"分页 EPUB 点替换→填纠错→保存被拒"，与验收清单里的其它真机项混在一起，反而污染 WorkBuddy 的结论。修好后即可交，不必再等新切片。
3. **是否仍需开发修复？** **需要**，且都是低风险小改：①VM 门改为消费引擎 availability（或先回退 EPUB 分支）+ 两条行为测试；②"同源"测试重写为同值比较；③账本 L7 改标 OPEN；④（可选）切书处一并 `.withSelectionCleared()`；⑤（可选）`setActionEnabled` 拒绝 dormant。
4. **哪些绝不能写成"已通过"？** 见 SP-6 六条 —— 特别是"分页 EPUB 单处纠错可用"（现在不可用）、"外部动作提示真实"（未修）、以及一切真机/Compose 运行期项（小屏、字体放大、触摸目标、TalkBack、TTS→滚动→dispose→消费者、性能体感）与"S07 可发布"。

**未领取下一任务，等待 Codex 主任务复核。F5、S08 及其余队列保持 HOLD。**
