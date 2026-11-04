# Android S07 选区工具栏与选词粒度审查

> 日期：2026-09-20  
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`  
> 审查对象：恢复分支 `codex/recover-worktree-20260920` 的未提交 S07 工作区差异与当前可发布行为  
> 比较命令：`git diff origin/main -- <S07 paths>`（未提交改动不能只用三点式比较提交）  
> 性质：只读审查；未修改产品源码，未 stage、commit 或 push

## 规格来源

- `docs/research/2026-09-19-android-worktree-product-inventory.md` 的 S07 边界；
- `docs/handoff/current.md`、`AGENTS.md` 的原生 Android、共享工作区和真机验收约束；
- `docs/coordination/reviews/2026-09/2026-09-20-zcode-qoder-audit-board.md` 的外部 Agent 任务契约；
- Zcode 链路报告 `agent-input-s07-zcode.md` 与 Qoder 独立审查 `agent-input-s07-qoder.md`。

## 初学者先读：这次到底检查了什么

选区功能不是只有“长按后出现一个菜单”。完整链路至少包括：正文能否选中、选中的位置是否对应原文、工具栏按钮是否真的有处理方、保存的高亮是否能再次显示，以及翻页或切章后旧选区是否清理。

本轮两名外部 Agent 已完成分工：Zcode 负责 TXT、Markdown、EPUB 三种格式的代码链路；Qoder 负责独立缺陷与测试。Codex 再对高风险结论逐项回看真实源码。结论是：**没有发现 P0 数据破坏或隐私泄漏，但 S07 目前不能作为公开发布候选。** 100 项 JVM 测试全绿只证明所运行的纯函数和数据逻辑通过，不证明真实页面上的选区旅程正确。

## 外部报告可信度处理

Qoder 的报告先于 Zcode 最终文件约 3 分钟完成，所以其 §5b 关于“Zcode 文件不存在、引用无法落地”的判断只描述了较早状态，不能用于评价最终 Zcode 报告。Codex 已直接核对最终 Zcode 报告引用的真实文件；保留 Qoder 自己的 Standards/Spec 与测试证据，丢弃这段过时的交叉结论。

两名 Agent 均未修改 Android 产品源码。Codex 还以 SHA-256 对照恢复仓库与保留的原始仓库，Android 既有/新增路径不一致数均为 0；它们只新增各自的报告。

## 精确切片边界

S07 的核心是选区设置、正文选区捕获、工具栏模型/渲染、动作分发和相应测试。以下共享文件只能按 hunk 集成：

- `SelectionActionSettings.kt`、`SelectionActionStore.kt`、`SelectionSubPage.kt`；
- `ReaderSelectionToolbar.kt`、`ReaderLayerBuilders.kt`、`ReaderInteractionLayer.kt`；
- `ReaderContentHostPlainTextBranch.kt`、`ReaderContentHostPagedBranch.kt`、`ReaderContentHostEpubBranch.kt`、`ReaderContentHostMarkdownBranch.kt`；
- `PagedReaderPageSurface.kt`、`PageSelection.kt`、`ReaderViewModel.kt`；
- S07 对应的 JVM/Compose 测试。

`edit` 属于 S08 章节编辑器能力。S08 尚未闭合时，S07 只能隐藏或明确禁用该入口，不能把死按钮混入 S07。替换净化、TTS、页码、硬件键和通用设置重构也不得因共享文件而整段带入。

## Standards

### [P1] S07 分散在多组共享文件中，按整文件集成会夹带 S08 和其它阅读器改动

设置、工具栏、正文宿主、分页表面和 ViewModel 都同时承载其它切片。尤其 `SelectionActionSettings.kt` 已声明 S08 的 `edit`，`ReaderLayerBuilders.kt` 同时包含进度、TTS、弹层与灵感保存，四个 ContentHost 又含搜索、TTS、替换投影和页码逻辑。S07 必须由单一集成者按 hunk 串行收口；任何“把上述文件整体暂存”的方案都不可审计、不可归因，也无法安全回滚。

证据：`SelectionActionSettings.kt:72-91`；`ReaderLayerBuilders.kt:228-363,482-622`；Zcode 报告 §6。

### [P1] 现有 Compose 测试绕过了真实用户配置路径，无法发现“配置可见但点击无处理”的死动作

`ReaderSelectionToolbarTest` 确实挂载了真实 `SelectionToolbar`，因此 Qoder 把全部仪器测试概括成“no-op”并不准确；但该测试依赖组件的默认 `primaryActions` / `moreActions`，没有从 `SelectionActionSettings` 生成用户实际清单，也没有验证“每个可渲染 id 都有生产分发分支”。因此设置页允许 `edit`、工具栏渲染 `edit`、点击落入 `else -> Unit` 的完整用户路径仍能全绿。

应增加配置驱动的 Compose 用例和分发表完整性断言，而不是只检查默认标签存在。

证据：`ReaderSelectionToolbarTest.kt:20-52,57-91`；`SelectionActionSettings.kt:78-91,128-155`；`ReaderSelectionToolbar.kt:106-123,160-176`。

### [P2] 选区清理是分散的调用约定，没有形成事件级不变量或旅程测试

VM 只有收到 `ReaderAction.ClearSelection` 才会清掉文本与 source 坐标；高亮、搜索和取消按钮各自显式发送它，但翻页、切章、滚动单元回收等状态变化没有统一 reducer 规则。现有测试主要覆盖区间算术和动作纯函数，没有断言“页面/章节身份变化后 VM 选区必为空”。这使下面的旧坐标残留问题长期存在，并且任何新增导航入口都可能再次漏掉清理。

证据：`ReaderViewModel.kt:317-331`；`PagedReaderPageSurface.kt:212-214,499-507`；`ReaderProgressActions.kt:53-98`。

## Spec

### [P1] 用户可以启用“编辑”，工具栏也会显示，但点击完全无响应

`edit` 在 `SelectionActions.ALL` 和 `pickableDefs()` 中，所以设置页可把它加入第一屏或更多菜单；能力门控只处理 `replace` 和 `ai`。生产分发 `when` 没有 `edit` 分支，最终落入 `else -> Unit`。这与源码“接线前绝不显示死按钮”的注释直接矛盾。

S08 编辑器未完成前，应把 `edit` 从可配置集合排除或加显式能力门控；不能仅依赖“默认集合里没有它”。

证据：`SelectionActionSettings.kt:78-91,128-155`；`ReaderSelectionToolbar.kt:111-123,160-176`。

### [P1] 剪贴板写入失败时仍无条件提示“已复制”

`ClipboardUtils.copyText()` 用 Boolean 表达成功/失败，空文本或系统异常会返回 false；S07 调用点丢弃这个返回值并立即显示“已复制”。用户会收到虚假成功反馈，也无法知道内容没有进入剪贴板。

应按返回值显示成功或失败，并补失败分支测试。

证据：`ReaderLayerBuilders.kt:341`；`ClipboardUtils.kt:14-17,30-32`。

### [P1] 某些翻页和切章路径只清本地选区，不清 VM 中的旧文本与旧坐标

普通点按在已有选区时会先调用 `onSelect("", -1, -1)`，这条路径正确；但拖动翻页、硬件/自动翻页和外部翻页请求在提交页码时只把 `selRange` 置空，`onGesturePageTurn` 又只隐藏控制栏。VM 中的 `selectedText`、`selectedGlobalOffset`、`selectedSourceEnd` 仍保留，章节跳转也只发 `LoadChapter`。

因此风险不是“所有翻页都坏”，而是特定非普通点按路径换页/换章后，工具栏可能继续引用旧页文字；若用户随后保存高亮或笔记，可能使用旧页/旧章坐标。该视觉与落库后果仍需 WorkBuddy 真机复现，但缺失清理调用是已确认事实。

证据：`PagedReaderPageSurface.kt:212-214,332-345,499-507`；`ReaderContentHostPagedBranch.kt:73-88`；`ReaderViewModel.kt:317-331`；`ReaderProgressActions.kt:53-98`。

### [P1] Markdown 滚动模式没有选区入口，旧 EPUB 滚动模式只支持点击整段

Markdown 分支调用 `RenderMarkdownUnit` 时没有传 `onSelectBlock`，所以用户没有选区动作入口。非分页 EPUB 对 `DocBlock.Text` 使用整块 `clickable`，选中的是整段文字，不是长按句子或可拖动范围。TXT、Markdown、EPUB 三种格式因而不是同一种功能，产品盘点中“选区工具栏与选词粒度已实现”的表述过强。

首版若不支持这些路径，应明确降级说明和能力矩阵；若宣称跨格式支持，则必须接入真实选区与统一 source 坐标契约。

证据：`ReaderContentHostMarkdownBranch.kt:107-140`；`ReaderContentHostEpubBranch.kt:143-188`；`ReaderMarkdownRender.kt` 的可选 `onSelectBlock` 契约。

### [P2] Markdown 与非分页 EPUB 不重新渲染已保存高亮

纯文本分支会读取 `s.highlights`、解析 locator/source length 并叠加背景色；Markdown 和非分页 EPUB 只渲染搜索/TTS 高亮，没有消费持久高亮列表。用户即使在其它路径成功保存标注，切到这些渲染路径也看不到它，容易误以为数据丢失。

证据：`ReaderContentHostPlainTextBranch.kt:252-293`；`ReaderContentHostMarkdownBranch.kt:130-140`；`ReaderContentHostEpubBranch.kt:166-188`。

### [P2] 外部查询提示会先宣称正在打开，失败后又给出不准确原因

浏览器/在线词典动作先显示“正在打开…”，没有 handler 时再显示失败；在线词典失败复用“未找到可用浏览器”。这不会破坏数据，但会让用户误判问题来自浏览器而不是词典/处理器，也会让两条互相冲突的提示连续出现。

证据：`ReaderSelectionExternalActions.kt:24-47`；`ReaderSelectionExternalActionsTest.kt` 的 NO_HANDLER 断言。

## 未升级为正式问题的风险

- Zcode 的“替换删除区间映射成零长度，高亮落库后不可见”有完整静态链，但尚无测试或真机复现，本报告暂列为待证伪/证实，不计入 finding 数。
- 第一屏最多 5 个动作的窄屏拥挤、触摸目标、TalkBack 手柄语义和焦点顺序仍需真实手机验证；静态宽度估算不能代替视觉证据。
- 跨页选择明确不属于一期；需要确认真实降级是“限制在当前页且有可理解反馈”，不能只写文档边界。
- Zcode 关于切书残留的延伸判断不成立：`openBook()` 会建立新的 `ReaderUiState`，从而清掉旧选区；本报告未采纳该子结论。

## 验证证据

Qoder 执行了两次定向 JVM 命令和一次 androidTest Kotlin 编译：12 个测试类、100 tests，0 failures、0 errors、0 skipped；`:app:compileDebugAndroidTestKotlin` 成功/UP-TO-DATE。第一次按文件名过滤 `SelectionActionSettingsTest` 实际匹配 0 类，因为类名是 `SelectionActionsTest`；Qoder随后用正确类名补跑 22 项并通过。这说明 Gradle 的 `--tests` 过滤不匹配可能静默漏测，后续门禁应按 XML 结果核对测试类数。

Codex 本轮没有重复占用 Gradle，也没有运行设备测试；只做源码复核、Git 状态核查和恢复仓库/原始仓库 Android 路径 SHA-256 对照。未使用 MuMu，未安装 APK，未操作真实手机或用户书籍数据。最终小屏、TalkBack、翻页残留、外部 Intent 与跨格式旅程仍由 WorkBuddy 在真实手机上独立验收。

## 结论与下一步

S07 应从“补小屏/无障碍后候选”降为“需先修 P1 再候选”。推荐按以下顺序形成小修复片：

1. 隐藏/门控 `edit`，同时建立可渲染动作必须有处理方的测试；
2. 修复复制结果提示；
3. 把页面/章节身份变化的选区清理收敛成单一不变量，并补状态级测试；
4. 明确 Markdown/旧 EPUB 的首版能力：要么完成真实选区与持久高亮，要么诚实降级并隐藏不成立的入口；
5. 再交 WorkBuddy 做小屏、字体放大、TalkBack、翻页/切章和三格式真机矩阵。

S08 及后续切片继续保持 `HOLD`，未因本轮报告完成而自动放行。

汇总：Standards 3 项（最严重 P1：共享文件必须按 hunk 集成；真实配置驱动的工具栏旅程无有效门禁）；Spec 6 项（最严重 P1：`edit` 死按钮、复制虚假成功、特定翻页/切章后旧选区与坐标残留、Markdown/旧 EPUB 选区能力不完整）。

## 2026-09-20 F1 修复复核

Zcode 已完成前两项最小修复，Qoder 独立复核结论为 `PASS`：

- `edit` 定义继续保留给 S08，但通过 `DORMANT_IDS` 从可配置、读盘收敛和工具栏渲染路径排除；
- 复制提示改为由同步 `copyText()` 的真实 Boolean 结果驱动；
- 4 个目标 JVM 测试类共 49 tests，0 failures / 0 errors / 0 skipped；Qoder 从 XML 确认目标类和新增用例真实执行；
- 未发现翻页、切章、Markdown、EPUB、AI 或其它剪贴板路径被夹带；未 stage、commit、push 或操作设备。

F1 仅关闭本文 Spec 前两项 P1，不代表 S07 完成。Qoder 记录的写侧 dormant 判据、分发表完整性门禁和复制接线级回归保护为 P2 加固项；剩余两个 P1（选区状态清理、Markdown/旧 EPUB 能力）仍未修。

## 2026-09-20 F2 修复复核

F2 主体实现有效：选区五字段已收敛到 `withSelectionCleared()`；翻页、跨章、TXT 章导航、普通撤选和中央点击均有状态级/接线级证据。Zcode 的 F2 定向 92 tests 与 Qoder 含 F1 哨兵的 119 tests 全绿，且 before/after patch 证明没有夹带其它 hunk。

但 Codex 不接受“整体关闭”：Qoder 同时确认 `jumpRequest -> controller.open(j)` 的同章程序化跳转仍不清选区，而非 reveal 点按还会在页码提交前乐观派发 `PageTurn`。这与“可见页面身份变化后清理、未变化时不清理”的不变量冲突。F2 记为“主体通过，待 F2.1 闭环”，不进入真机候选。

### F2.1 结果

F2.1 由 Zcode 修复、Qoder 独立复核 `PASS`：同章程序化跳转按可见页半开区间判断是否失效选区；非 reveal 点按只在真实提交后派发一次；本地 `selRange` 回流已有纯函数断言。10 类 / 158 tests 全绿，before/after patch 未发现范围污染。

仍需 F2.2 收尾：程序化跳页目前复用 `PageTurn`，会额外隐藏顶栏/底栏；应改为纯 `ClearSelection` 语义。滚动大 TXT 的 ReadingUnit 身份切换也应独立确认，避免分页链闭合而滚动链仍残留旧选区。

### F2.2 结果

F2.2 由 Zcode 修复、Qoder 独立复核 `PASS`：程序化跳页改走纯 `ClearSelection`；滚动 ReadingUnit 仅在拥有 VM 当前选区时于 dispose 请求清除。13 类 / 175 tests 全绿，范围污染为 0。

S07 状态清理仍有一个 F2.3 安全边界：单元 dispose 可能发生在笔记/规则/AI 等弹层仍依赖实时选区时，直接清除会让摘录静默为空。另需补滚动分支接线守卫并统一两处 ownership 判据。关闭 F2.3 后，S07 还剩跨格式能力、P2 门禁/提示收口两个开发片，最后交 WorkBuddy 真机验收。

### F2.3 结果

F2.3 记为 `FAIL（部分加固有效，消费竞态未关闭）`。Zcode 已让折叠清除与 dispose 共用 `readingUnitOwnsSelection(...)`，并增加 5 条滚动接线结构守卫；Zcode 与 Qoder 分别运行同一组 14 类 / 180 tests，均为 0 failures / 0 errors，增量范围没有夹带。Qoder 同时指出两项 P2：负向 `remember` 断言检查的是不可编译形态，且当前字符串守卫没有证明 ownership 判断与空选区回调确实位于 `onDispose` 块内。

Spec 轴仍有 1 个 P1：滚动 TXT 在 TTS 跟读时会经 `scrollToItem` 程序化滚动，拥有选区的 ReadingUnit 可在笔记、规则、AI 解读或灵感弹层仍打开时 dispose。当前消费者读取实时 VM 选区，因此笔记/AI/灵感会丢摘录；规则页还可能因 `initialReplaceText` 变化而重建 `tab` 与 `editing`，丢失草稿并退回目录标签。该链路静态可达且未修，不能以 180 项绿灯或尚未真机复现替代关闭。

下一片改为 F2.4：为所有长生命周期选区消费者建立“打开时不可变快照”，至少覆盖文字和保存定位所需坐标；正文选区仍可在宿主 dispose 时正常清除，弹层不得再回读已清空的活动选区。禁止用 `consumerOpen -> 永远不清选区`，也不得仅修笔记而遗漏规则、AI 解读、灵感及其 locator。F2.4 关闭后，才进入跨格式 F3。

F2.3 汇总：Standards 2 项（最严重 P2：结构守卫不能证明关键调用位于 `onDispose`）；Spec 1 项（最严重 P1：弹层消费中的文字、草稿和定位快照可被 TTS 驱动的单元 dispose 静默清空）。

### F2.4 结果

F2.4 由 Zcode 修复、Qoder 独立复核 `PASS`。长生命周期消费者现在在打开时原子捕获不可变 `ReaderSelectionSnapshot`，文字与 `rangeStart`、`globalOffset`、`sourceEnd` 同源；笔记、规则、AI 解读、灵感、AI 伴读及两条灵感保存路径不再回读会被 dispose 清掉的活动选区。关闭或切换到非消费者时释放快照，正文 `ClearSelection` 与 ReadingUnit dispose 仍照常工作。

规则页的初始替换文字已在首次组合时固定，活动选区清空不会再重建 tab/editing；dispose 接线守卫也改为提取真实 `onDispose` 块后断言，并移除了检查不可编译写法的空负向断言。Qoder 独立运行 19 类 / 263 tests，0 failures / 0 errors / 0 skipped；其中结构守卫 17 项、语义/行为 35 项，二者已分开统计。授权文件哈希、补丁和越界临时编辑的零残留均经独立复算。

本片无 P0/P1。遗留的切书同 ViewModel 快照释放风险列为 P2，连同 `consumesSelection()` 手工分类表、保存参数成对约束和“弹层打开后新选区不覆盖旧快照”的产品口径一起进入 F4。真实 TTS→滚动→dispose 与 Compose remember 行为仍待 WorkBuddy 真机验收，不以 JVM 绿灯替代。

F2.4 汇总：Standards 4 项遗留（最严重 P2：同一 ViewModel 切书时快照释放未显式收敛）；Spec 0 项阻断。下一片放行 F3：只处理 Markdown / 非分页 EPUB 的选区与持久高亮能力边界，无法保证 source 坐标时必须诚实降级，禁止写入 display offset。

### F3 结果

F3 记为 `FAIL（安全映射基础有效，但 Markdown 保存接线存在 P1）`。Zcode 完成了 Markdown canonical 区间、流式章节 base、半开区间、重复文本隔离、Markdown 持久高亮投影，以及非分页 EPUB 整块选择的真实终点；图片/分隔线不产生伪选区，旧 `"[Table]"` 伪选区已删除。Qoder 独立运行 26 类 / 397 tests 全绿并确认范围干净，但绿灯没有覆盖参数槽位语义。

阻断 P1：`ReaderContentHostMarkdownBranch` 把 Markdown canonical 起点传到 `onSelect` 的 `globalOffset` 槽，而 `computeLocatorJson` 在非 EPUB 文档只读取 `rangeStart`。因此 Markdown 看似能选择并保存，高亮/笔记/灵感 locator 实际恒为 `null`，新增持久高亮投影端到端不可达。这属于“看似可保存、实际无坐标”，F3 不能通过。下一片改为 F3.1：交换到正确槽位，并补穿过 `computeLocatorJson` 的行为测试，不能只锁源码字符串。

已记录并分配到 F3.1/F4 的 P2：生产路径 stale-chapter 判据使用当前章回填而恒成立；非分页 EPUB 新增 `sourceEnd` 可能意外放开单处纠错路径且无测试；Markdown canonical 区间被命名为 source，与真实 `sourceRange` 并存；“没有 SelectionContainer 所以只能整块”的架构论证不成立，整块只是当前产品选择；非分页 EPUB 已保存高亮仍静默消失且没有用户可感知的降级边界。

同时保留的 P3/证据问题：表格单元格可回显高亮但不可选，两个 range helper 生产零调用；流式 base 参数默认 0，漏传会静默错位；Markdown 三种 base 参数并存且分页适配靠丢弃新区间；持久高亮缺省色与搜索色相同；EPUB 全书偏移属于旧混合量纲而非精确字符坐标；报告增量少记 19 行、before/after 文件集不一致、一个回归类计数少 1。详细证据以 `agent-review-s07-f3-qoder.md` 为准，不因后续修复 P1 而删除。

F3 汇总：Standards 8 项（最严重 P2：canonical/source 命名与三基准接口容易静默误接）；Spec 6 项（最严重 P1：Markdown 选区参数槽位错误导致 locator 恒空）。Compose/真机行为仍未验证。

### F3.1 结果

F3.1 由 Zcode 修复、Qoder 独立复核 `PASS`，F3 的阻断 P1 已关闭。Markdown 整块选择参数改由具名 `MarkdownBlockSelection` 传递：`globalOffset=-1`、canonical 全局起点进入 `rangeStart`、`sourceEnd` 为半开终点；生产分支使用同一纯函数。行为测试把生产参数继续送入真实 `computeLocatorJson` 与 `LocatorCodec.decode`，证明 locator 非空且坐标正确，并以反向用例证明旧槽位放法会返回 null，不再只依赖源码字符串守卫。

Markdown 持久高亮改用真实章节区间相交，移除了由当前章回填造成的恒真 stale-chapter 判据；非分页 EPUB 也以全书区间与当前内容块区间的相交投影回显持久高亮，不做文本猜测，纸质高亮色与搜索色已分离。两个生产零调用的 range helper 已删除，canonical/source 命名及流式 base 默认值问题也已收紧。Qoder 独立运行 30 类 / 342 tests，0 failures / 0 errors，缺失类为 0；未发现范围污染。

Qoder 同时纠正了 Zcode 对纠错链路的解释：非分页 EPUB 的 `rangeStart`/`sourceEnd` 现在可以完整形成，`findText` 通常也与选中文字相同；当前真正阻止不安全“单处纠错”的是两个 UI 入口都受 `replacementCapability is Available` 约束，而不是文字或坐标的“双重门禁”。因此本片不新增 P0/P1，但 ViewModel 的 `SaveSingleCorrection` 命令边界仍缺少能力级纵深防御；未来新增入口可能绕过 UI，作为 P2 必修进入 F4。

F3.1 汇总：Standards 6 项遗留（最严重 P2：安全结论曾依赖不准确解释，且未追溯命令边界防御）；Spec 2 项遗留（最严重 P2：纠错只在 UI 门控，Markdown `charCount=0` 时章节边界降级尚未定案）。其余 P3 包括三基准接口、旧 EPUB 混合量纲文案、未跟踪文件补丁证据、额外高亮遍历性能与越界自检强度，均保留到 F4 或 WorkBuddy，不以本次 PASS 删除。

### F4 结果

F4 记为 `FAIL（多数收口有效，但新增分页 EPUB P1 回归）`。Zcode 已完成 legacy EPUB/Markdown 命令绕过防御、消费者/动作分类守卫、Markdown 未知章节长度显式降级和部分证据收口；Qoder 独立运行 23 类 / 311 tests，0 failures / 0 errors / 0 skipped，且核对 23/23 测试类实际命中。上述绿灯不能覆盖能力事实源选错的问题。

阻断 P1：ViewModel 新增的 `readerLoadedContentReplacementAvailability` 只按 `ReaderLoadedContent` 类型判断，把所有 EPUB 固定映射为 `ESTIMATED_COORDINATES`；但 UI 与分页引擎使用的是实时 `pagerEngine.replacementAvailability`，分页 EPUB 可得到 `APPLIED`、`NO_EFFECTIVE_RULES` 等 `Available` 状态。结果是 UI 仍提供“单处纠错”，保存时 ViewModel 却返回格式不支持。所谓“与工具栏同源”测试把 `ESTIMATED_COORDINATES` 同时写进两边期望，只形成循环论证，没有覆盖真实 UI→VM 一致性。

另一个必须在 F4.1 关闭的状态风险：切书分支只释放 `selectionSnapshot`，刻意保留 `selectedText` 与三项坐标，并用反向测试锁定。旧活动选区可在新书中重新打开消费者时再次被捕获，进而用新书 id 保存旧书文字/坐标；这违反 F4 明确要求的“切书同时清活动选区与快照”，不能作为可选 P3。外部浏览器/词典动作也仍会先提示“正在打开”再报告失败，在线词典失败复用浏览器文案；F4 账本将其写成“已固化”与事实不符。

F4 汇总：Standards 6 项（最严重 P1：VM 与 UI/分页引擎能力事实源分裂）；Spec 5 项（最严重 P1：分页 EPUB 显示可纠错但保存必然被拒；另有跨书活动选区可被重新捕获的高风险状态缺口）。S07 暂不交 WorkBuddy；下一片 F4.1 只修实时能力同步、切书完整清选区、外部动作真实提示及相应行为测试，不扩大到 S08。

### F4.1 结果

F4.1 由 Zcode 修复、Qoder 独立复核 `PASS`，F4 的三个阻断问题均已关闭。UI 与 ViewModel 现在共同消费生产层 `pagerEngine.replacementAvailability`：发布值携带 `bookId`，未发布、书身份不符、切书后未重发均默认拒绝；分页 EPUB 的 `APPLIED`/`NO_EFFECTIVE_RULES` 可保存单处纠错，legacy EPUB 的 `ESTIMATED_COORDINATES`/`PAGER_ENGINE_DISABLED` 与 Markdown 的 `NON_SOURCE_COORDINATES` 在 repository 写入前拒绝且 DAO 为 0。

切书与 force 重载会同时清除活动选区五字段、消费者快照、旧消费者 UI 状态和旧规则反馈；同书无操作重开保留合法在途快照。浏览器、系统词典和在线词典改为 launcher 返回后只显示一条准确结果，不再预报“正在打开”；dormant `edit` 的写侧也拒绝落盘，未实现 S08。

Qoder 独立运行 29 类 / 366 tests，0 failures / 0 errors / 0 skipped，实际 XML 29/29、缺失类 0；其中行为/语义 24 类 331 项，结构守卫 5 类 35 项。`androidTest` 编译成功，index 为空，未发现范围污染。F4.1 汇总：Standards 5 项，均为 P3（最严重为 UI↔VM 同源测试仍部分依赖接线事实）；Spec 2 项，均为 P3（最严重为切书直接清 `sheet` 形成 reducer 不变量例外）。无 P0/P1/P2，S07 可交 WorkBuddy 真机验收，但不能据此宣称真机或发布通过。

### Qoder 真机验收结果

用户明确改派 Qoder 代替 WorkBuddy 执行真实手机验收。报告结论为 `PARTIAL`，不是 PASS：真实设备 `c49ac6cf`、Android 15，Debug APK SHA256 `cb4b921e0db8566f1a595983f2a02b68fbfd252e443444da3fa019a57320663f`；`assembleDebug` 真实退出码 0，`install_with_confirm.ps1` 退出码 0 并走 `CONFIRM_LOOP`，未使用直接 ADB 回退。核心分页 EPUB 单处纠错保存→重开→只改一处→撤销恢复、跨书隔离、TXT/Markdown/EPUB 保存重开均有真机证据。

验收发现 P0=0、P1=0、P2=4、P3=5。S07 内两个 P2 阻止关闭：① Markdown 图片块的 alt 文本可被当成正文选区并持久化伪高亮；②滚动 TXT 的拥有者 ReadingUnit 已离开/回收后，旧摘录与工具栏仍常驻，可从不可见选区继续执行动作。截图 `51/52/53` 与 `18/19/20` 序列经 Codex 抽查支持报告结论。另两个 P2 分别是“只改本书”下 EPUB 分页兼容模式实际写全局（设置子系统，单独登记）和 legacy EPUB 合成 fling 的高卡顿率（缺少零高亮对照，先作为性能调查而非直接归因）。

设备恢复并非完全完成：stay-awake、方向、IME、通知权限、全局 EPUB 模式与字号已恢复，但 3 本本轮新建测试书仍保留 `readerMode=scroll` 覆盖；不影响用户原有书籍或全局默认。真机验收汇总：Standards 3 项（最严重 P2：验收状态未完全恢复，且性能结论缺对照）；Spec 9 项（最严重 P2：Markdown 伪选区/伪高亮与滚动 TXT 不可见旧选区）。下一片 S07-F5 只修这两个选区 P2；设置作用域 D-2 单独入队，D-7 由 Qoder 用同设备同手势零高亮/多高亮对照复测。

### F5 与最终真机补验

S07-F5 最终记为 `PASS`。Zcode 在 Markdown render model 层拒绝含图片 span 的整块选择与伪高亮投影，并在滚动 TXT 的列表宿主层以 `visibleItemsInfo` 建立选区 owner 可见性失效策略；Qoder 的代码/JVM 独立复核和 D-1/D-3 手势、搜索跳转真机复验均通过。Codex 新鲜复跑 F5 三类 44 tests，0 failures / 0 errors / 0 skipped，Gradle 退出码 0。

最后缺失的 TTS 分支由 Qoder 在真实手机 `c49ac6cf` 补验：媒体会话为 `PLAYING(3)`，TTS 自主逐句推进；第 4 章 owner 仍可见时工具栏持续存在，`16:31:03` TTS 跨入第 6 章且可见单元变为第 5/6 章的同一秒，工具栏节点由 2 变 0。停止后返回第 4 章旧选区不复活，新选区仍可建立。Codex 抽查原始轮询日志、TTS logcat、关键截图，并现场回读设备仍在线、`POST_NOTIFICATIONS=false`、stay-awake `3`、旋转 `1/0`、font scale `1.0`；测试 TXT per-book 模式已恢复 `paged`。消费者工作表与 TTS 同时打开因 UI 互斥仍为 NOT TESTED，但已有 F2.4 快照语义测试与独立真机证据，且该组合不属于最后核心判定。

D-7 已由相同 shell fling 在系统设置控制组得到同分布而确认为测量伪影，不再作为产品 P2。D-2 设置作用域与 N-1“目录与标记→笔记”未展示已有高亮继续独立挂账，不属于 S07。S07 至此关闭；这不等于整个 Android 产品或发布流程完成。
