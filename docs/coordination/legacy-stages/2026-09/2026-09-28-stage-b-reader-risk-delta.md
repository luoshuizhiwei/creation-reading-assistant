# 阶段 B：S07–S13 当前风险增量核对（2026-09-28）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`；HEAD 40 位、前缀 `5aed5939`（与账本一致）。
- 性质：**只读增量核对**。复用既有调查/QA 报告；仅对「旧结论冲突 / 输入已变化 / 证据缺口」核对了当前源码（S11 组件调用点、S13 设置搜索接线、S07 验收结论与账本口径的出入）。未运行 Gradle / ADB / MuMu；除本文件外零写入；未触碰他窗口正在做的 S06 代码任务。
- 输入：`docs/handoff/current.md`、`docs/coordination/decisions/2026-09/2026-09-22-current-task-ledger.md`、`docs/plans/android/2026-09-22-android-recovery-convergence-plan.md`、四份 S08/S09/S10/S12 调查报告、S07 系列 result/review/QA、`2026-09-24-compose-test-host-investigation.md`。
- 事实核对方法说明：本轮发现单次 grep 输出曾出现漏报/串流（`EpubFootnotePopover` 首查零命中、复查命中），所有负向结论（零调用方、零测试）均经第二角度复核后才落笔。

## 1. 七切片一行总表

| 切片 | 当前用户可达能力 | 已确认状态 | 证据路径 | 仍存在的产品故障 / 验证缺口 / 产品决定 | 阻挡恢复候选 | 下一项最小行动 |
|---|---|---|---|---|---|---|
| **S07** 选区/标注/复制/跨格式 | 三格式选区→高亮/记为灵感/复制、笔记与标注弹层、搜索跳转、单处纠错（TXT 分页/分页 EPUB） | **已关闭（历史）**：D-1 真机关闭、D-3 手势+程序化路径关闭（TTS 分支由 TTS 补验覆盖）、D-7 帧率判为测量伪影；D-2 移交设置作用域并已按 D-2-B 关闭 | `docs/qa/2026-09-21-s07-qoder-device-acceptance.md`（自判 PARTIAL+条件）、`docs/qa/2026-09/2026-09-21-s07-f5-qoder-device-retest.md`、`docs/qa/2026-09/2026-09-21-s07-f5-tts-qoder-device-retest.md`、`docs/coordination/agent-result-s07-f1..f5-zcode.md` + `agent-review-s07-f*.md` | 无现役故障。回归触发条件=Reader 输入链改动（9/22 滚轮 generic-motion 修复动了 `ReaderHardwareKeys`/`MainActivity`；9/24 `PageTurner` 队列化动了翻页消费侧），届时需定向选区回归；G 矩阵（规则消费者快照/locator 跳转）与 TalkBack 为**明确接受的未覆盖**，不默认视为通过 | 否 | 无独立行动；Reader 输入链合并时附带选区定向回归 |
| **S08** 章节修改 | **用户不可达**（唯一可达的是单处纠错链路，属 S07 范围） | **延期（产品合同未定）**：`feature/reader/edit/ChapterTextDiff.kt`（207 行纯 JVM 差异引擎+13 测试）生产调用方为 0；「编辑」动作被 `SelectionActions.DORMANT_IDS` 双向封死（不可配置/不可渲染/分发无分支），`ReaderConsumerAndActionGateTest` 防死按钮回归 | `docs/coordination/investigations/agent-investigation-s08-zcode.md`（本轮未发现与现状冲突的输入变化） | 产品决定：章节级批量写入+编辑器 UI 是否立项、完整保存/撤销合同。未跟踪文件是**未接线的用户资产**，不是死代码缺陷，不删除、不接 UI | 否 | Codex 明确产品合同前不动；仅防止后续窗口把未跟踪文件当垃圾清理 |
| **S09** 页码/点击区/硬件键 | 分页引擎章内页码（仅 `PagedTxtReaderHost` 渲染页眉页脚）；全书进度=字符%；音量/PAGE/SPACE/DPAD/滚轮/媒体键翻页；点击翻页热区 | **部分关闭/待验证**：S09-A-F1 书签 bookId 过滤已关闭；调查确认的接线缺口无后续修复证据。S09-B 62 条 UI 自动化完成 0 | `docs/coordination/investigations/agent-investigation-s09-qoder.md`、`docs/coordination/agent-results/agent-result-s09-b-qoder.md`、`docs/qa/2026-09/2026-09-22-s09b-s15-duration-gesture-device-acceptance.md`、`docs/qa/2026-09/2026-09-24-compose-test-host-investigation.md` | **验证缺口（主导）**：S09-B 阻塞于 MIUI 启动拦截，SR-ENV-1 未裁定。**输入已变化须登记**：S09-B 真机证据全部早于 9/22 `ReaderHardwareKeys.dispatch` 重构（Codex fresh XML 9/9+Lint 0 error）与 9/24 `PageTurner` 队列化（JVM 11/11、插桩 0 结果）。**产品缺口**（调查记录、无修复证据）：滚动模式无任何页码、`WholeBookPageModel` 212 行+11 测试零接线、点击区三套比例互不一致（33/33/33 vs 16/68/16 vs 无热区，预览卡第三套 24/52/24）、媒体键桥仅分页引擎激活、TXT 滚动章末音量键不能跨章、TTS「朗读时音量键翻页」判的是面板可见性 | 是——但其根源是 SR-ENV-1 与 S06-⑤⑥ 未关单，不是 S09 自身代码；裁决前 S09 不新增设备轮次 | SR-ENV-1 三选一裁决（换真机 / CI / 接受 JVM 替代门禁），裁决后再排设备验收 |
| **S10** 目录卷标题、页眉页脚 | 目录列表卷标题分组；自定义页眉页脚六槽位+10 占位符+预览（全局作用域，已声明契约）；仅分页引擎消费 | **待决定**：S10-A 正则收紧已落地（`ReaderTocModels.kt:87` + `VolumeHeaderTest` 10 例）但仅覆盖 1/9 消费端；S10-B 数据/纯函数层完整，UI 层双实现并存 | `docs/coordination/investigations/agent-investigation-s10-zcode.md` | **产品决定+用户可见分叉**：① 卷标题仍是真章节——目录里被吞成分组名，但点击跳转、章序号、`{ChapterTitle}`、搜索、进度、TTS、已读、书签 8 处无特殊处理（同一数据两套语义）；② `HeaderFooterPicker` 两份实现文案互反：`ui/components/CustomTipDialog.kt:220` 显示中文回显、`ui/screen/reader/sheets/ReaderSettingsCommon.kt:783` 显示原始 token——同一模板在「我的→阅读设置」与书内设置显示不一致；③ 13 条纯函数测试、UI/渲染零覆盖 | 否（能力可用），但与 S09 页眉页脚/热区收敛同区，宜合并裁决 | Codex 裁决卷标题跨消费端合同 + 双 Picker 收敛归属（建议与 S09 消费端收敛一个包） |
| **S11** EPUB 图片/脚注/引用 | 图片查看（`EpubImageViewerDialog` ← `PagedEpubContent.kt`）与引用卡（`QuoteCardDialog` ← `ReaderNotesSheet.kt`）**已接线可达**；脚注弹层**不可达** | **本轮核实（取代“待核实”）**：三个组件中 2 个有真实生产调用方、1 个（`EpubFootnotePopover.kt`，未跟踪）**全仓零调用方**——是无入口的未接线组件，不是坏了的入口。三文件均无专项测试 | 本轮 grep（file:line 见 §3）；`docs/handoff/current.md` §15（测试 EPUB 图片显示为 2026-08-24 旧证据，代码已变不能沿用） | 验证缺口：图片查看/引用卡的真机行为无当前证据（S04 EPUB 链近期多次改动）。产品决定：`EpubFootnotePopover.kt` 接线立项还是明确保留。未跟踪文件归属登记 | 否 | Codex 登记 `EpubFootnotePopover.kt` 处置；EPUB 相关改动合并时把图片查看纳入回归 |
| **S12** Legado 进度导入 | 完整可达：我的→WebDAV→「从开源阅读导入记录」→扫描→确认→下载（64 MiB 上限）→解析→事务写入→首次打开换算 | **待决定（调查已接受）**：链路完整无半成品；Markdown 往返断裂 P1 已修+2 条回归；TXT 写 `legado_position_v1`、EPUB 写 locator v2 | `docs/coordination/investigations/agent-investigation-s12-qoder.md`；JVM：`ReaderDocumentLoaderLegadoProgressTest.kt` 等 | **现役候选问题 3 个（P2，非阻断但须修或显式接受）**：① EPUB `current_location_json` 有 4 个写入点、3 套量纲，导入器写的是不自洽的一套（`locator_v2.offset`=章内偏移冒充全局）；② 流式大 TXT 导入后静默回书首；③ 导入可把长篇末章静默标 FINISHED（`approximatePercent` + 99.5% 阈值 + 永久保持）。**预览链缺失**：导入前无匹配摘要、导入中无状态、按钮不禁用。**验证缺口**：Manager 层 `importLegadoBackup`/`listLegadoBackups` 与 EPUB 首开消费、UI 预览零测试 | 否 | 先裁决 EPUB locator 写入契约 owner（见 §4-R2），再排预览链与 FINISHED 静默提升两个小修 |
| **S13** 设置搜索/密度/页面 | 设置搜索（`ProfileHomeScreen.kt:94` `SettingsSearchIndex.search` + `SettingsAnchor`/`SettingsFocus` + `AppNavigation` 跳转）；Appearance/Reader/Library/Privacy/Goal/Ai/Diagnostics/About 等子页 | **待核实（部分收窄）**：设置作用域契约 D-2-B 已关闭（9/22 真机）；`SettingsSearchIndexTest.kt` 存在（JVM）；本轮 grep 确认搜索链 5 文件接线完整 | `docs/qa/2026-09/2026-09-22-d02b-qoder-device-acceptance.md` + 本轮 grep（`SettingsSearchIndex|SettingsAnchor|SettingsFocus|ProfileHomeScreen|AppNavigation`） | 验证缺口：以「已接入入口清单」的整体验收未执行；依赖 S17 公共组件收口；与 S16 的混合改动归属未分 | 否（等 S17） | S17 收口后做一轮「搜索→跳转→改值生效」的入口清单定向验收 |

## 2. 已关闭/延期 与 现役必须处置 的分界

- **已关闭（历史，勿重开）**：S07 主体（D-1/D-3/D-7 及 F1–F5 系列）、D-2-B 设置作用域、S09-A-F1 书签 bookId 过滤、S12 的 Markdown 往返 P1、S10-A 正则收紧本身。
- **明确延期（保留，不动）**：S08 章节编辑（产品合同未定）；S10 滚动展示合同；S12 与 S14 的解耦边界。
- **现役必须处置（有当前源码链路或用户可见）**：
  1. S12-① EPUB locator 多写入点量纲不自洽（数据契约，跨切片）；
  2. S10-B 双 `HeaderFooterPicker` 文案互反（用户可见不一致）；
  3. S10-A 卷标题 8 处消费端语义分叉（可点击/计章/TTS/书签）；
  4. S09 接线缺口五项（滚动模式无页码、全书页码模型零接线、点击区三套实现、媒体键桥、章末跨章）——属产品决定+验证缺口，不是回归；
  5. S12-②③（流式 TXT 回书首、末章静默 FINISHED）与预览链缺失；
  6. S11 `EpubFootnotePopover.kt` 零调用方（处置决定）。
- **登记不裁决**：S09-B 62 条保持 BLOCKED；SR-ENV-1 未裁定前不把任何 JVM/编译结果写成设备验收。

## 3. 本轮新增源码证据（仅核对，未调查历史）

| 核对点 | 结果 |
|---|---|
| S11 三组件调用方 | `EpubImageViewerDialog.kt`（未跟踪）被 `PagedEpubContent.kt`（已修改）引用；`QuoteCardDialog.kt`（未跟踪）被 `ReaderNotesSheet.kt`（已修改）引用；`EpubFootnotePopover.kt`（未跟踪）`grep -rln` 于 main **零命中**（复核两次） |
| S11 专项测试 | `android/app/src/test/.../ui/screen/reader/` 下无 image/footnote/quote 用例（仅 highlight/source-range/correction 类） |
| S13 接线面 | `SettingsSearchIndex.kt`、`SettingsAnchor.kt`、`SettingsFocus.kt`、`ProfileHomeScreen.kt`（`:94` 调 `SettingsSearchIndex.search`）、`AppNavigation.kt` 共 5 文件；`SettingsSearchIndexTest.kt` 存在 |
| S07 口径出入 | 9/21 大矩阵自判 **PARTIAL**（4 项 P2），并非 PASS；其条件（D-1 修复、D-7 对照复测、TTS 分支）已由 F5 复验 + TTS 补验逐项关闭，账本“已关闭（历史）”口径成立，但引用时应写“PARTIAL+条件后补验关闭”，不得写“9/21 QA PASS” |

## 4. 提请总指挥立即裁决的风险（最多 3 项，含文件所有权建议）

**R1（最高）：SR-ENV-1 设备验证通道未裁定。**
影响面：S09-B 62 条、C 的 S06-⑤⑥ 5 项、D 的 4 项共 9+ 项没有关单路径；S09/S10/S11/S13 的任何设备验收都被同一通道卡住。
建议：Codex 三选一（换真机 / CI / 明确接受本机不可验并改 JVM 替代门禁）。无产品文件所有权冲突；若选 JVM 替代，由各切片 owner 按调查报告 §测试层面的判断补“接线层”定向 JVM（`PagedReaderControllerEffects`、`ReaderSheetHost` 书签消费点），禁止用纯函数绿灯冒充接线证明。

**R2：EPUB `current_location_json` 写入契约无 owner（S12-①，横跨 S12/S06/S04）。**
影响面：4 个写入点 3 套量纲（`LegadoReadingProgressImporter.kt:235-249`、`ReaderProgressEffects.kt`、`ReaderProgressActions.kt:180-260`、`ReaderChapterLoaderCoordinator.kt:57-66`，`EpubRepository.kt` 为事实源）；章节变更自动保存反复重新引入不自洽状态。S04 正在动 EPUB 哈希链，若不先定契约，多窗口会各自写入同一字段。
建议：归属 S06 阅读进度 owner，先出「locator 单一写入契约」纯函数+定向测试（不改 schema），S12 导入器随后对齐；`ReaderChapterLoaderCoordinator.kt`、`ReaderProgressEffects.kt`、`ReaderProgressActions.kt`、`EpubRepository.kt`、`LegadoReadingProgressImporter.kt` 在契约落地前冻结跨窗口写入改动。

**R3：阅读器“展示层收敛包”缺裁决（S10-B 双 Picker 文案互反 + S10-A 卷标题 8 处分叉 + S09 页码/热区事实源）。**
影响面：用户可见的不一致（同一模板两处显示不同；卷标题在目录与正文两套语义；三套点击区比例）。三者在文件上同区（`ReaderTocModels.kt`、`CustomTipDialog.kt`、`ReaderSettingsCommon.kt`、`ReaderDisplaySettings.kt`、`PagedReaderHeaderFooter.kt`、`ReaderTapAction.kt`），分开裁决会互相踩。
建议：Codex 先裁产品合同（卷标题是否可点击/是否计章；页眉页脚六槽位保持全局；点击区/页码单一事实源），再作为一个实现包交单一窗口，独占上述文件 hunk；与 R2 不同 owner 时按 SEAM REQUEST 走。

## 5. 边界声明

本轮只新增本报告一个文件；未修改产品/测试/Gradle/账本/handoff/他人报告；未运行 Gradle、ADB、MuMu；未 stage/commit/push/reset/checkout/clean；未触碰 S06 在途代码任务（`PageTurner`/`PendingTurnQueue` 等归 C/D 窗口）；报告只使用「测试 EPUB/测试 TXT/测试 Markdown」等中性名称。S07–S13 各行结论均可回溯到 §1 证据路径列所列文件；凡标注“本轮 grep/核实”的是 2026-09-28 当前工作树事实，标注“调查报告”的是 9/22 快照事实——两者冲突时以本轮核实为准并已在正文标明。

落盘即停，不自行派发后续任务，不改账本。
