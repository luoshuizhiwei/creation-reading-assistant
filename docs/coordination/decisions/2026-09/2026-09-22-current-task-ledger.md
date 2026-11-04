# Android 当前任务账本

> **2026-09-29 阶段 D 限定退出：A–E 全局 80%（4/5）。** [D 退出复核](../../reviews/2026-09/2026-09-29-stage-d-limited-exit-review.md)已核全量 JVM 3208/3208、Lint 0 error、V5 MuMu 62/62、S05 A/B 与 C 的 420dpi 手机布局补验、S04 隔离 Room 8/8、S03 并发 XML 19/19。C 在重开时仅 UI 显示 47.4%，DB 直到实际轻滑后才从残余 100% 落到 47.42%；qahost DB/49 个既有文件和设备密度已恢复。实体手机、S14 真 kill/整包原子性、正式书库与 Release 未验。E 已做只读资产保护和原仓库缺对象诊断，下一步准备候选 hunk/依赖/资产处置方案；提交和目录切换需按计划 §E 征得用户明确授权。以下 60% 等记录均为历史快照。

> **2026-09-28 C 阶段限定退出：A–E 全局 60%（3/5）。** 依据 [`阶段 C 退出复核`](../../reviews/2026-09/2026-09-28-stage-c-exit-review.md)：S14 JVM 34/34 + MuMu UTP 11/11、S12 隔离连续链原始 DB/日志/UI 已补、S05 七站设备范围通过；V4 XML 另次 21/21、V5 62 条待 D。D 集中冻结验收尚未开始，E 版本整理未开始；实体手机/正式书库未触碰。

> **2026-09-28 最新：** S14 W5/W6 34/34 JVM 通过，返修后 MuMu 未验；S12 书架在读 99% 与重开提示截图成立，但逐阶段 DB/seed/事后完整哈希未落 E，连续链不能独立复算；V4 后次 XML 21/21 可回读但不同原报告哈希；V5 62 条 MuMu qahost 未跑，用户既有 MuMu 授权有效。C 未退出，A–E **40%**。

> **2026-09-28 并行三窗复核：** S05 420 dpi MuMu qahost 七站与 DB/文件/设备恢复限定通过。S14 32/32 JVM 修正无证据同名文件与数字溢出，但日志后、第一次 rename 前 kill 时旧 dst + 完整 staging 分支会删 staging 留旧正文；日志写入失败仍继续破坏性 rename，故安全收口未过。C 审计为并行旧快照，V2/V3/V4/V5 仍待。A–E **40%**，C 未退出。

> **2026-09-28 三窗回读：** S06 主题 MuMu qahost 有启动帧与双向切换证据，限定通过；一次全局 night 翻转归因未定。S05 跨页完成态 40/40 JVM，MuMu UI 未验。S14 残留处理 22/22 JVM，但完整后缀普通文件可能被误改名或漏出备份，安全验收未过，需加同格式碰撞及数字溢出测试并修正。A–E 仍 **40%（A/B 退出，C 未退出）**。来源：2026-09-28 三份 S05/S06/S14 报告顶部 Codex 复核。

> 2026-09-28 ZCode 三窗回读：S06 跟随系统主题代码/JVM 34/34、编译/Lint 有证，MuMu 显式主题→system 实际清覆盖与冷启动首帧未验；S05 取消读完书架口径 Codex 重跑 70/70、EXIT 0，但残余 100% 与首页/统计等跨页百分比消费者未统一、UI 未验；S14 postcommit 隔离 JVM 17/17，报告 W4 过强——保护性 backup 换入两次 rename 之间被 kill 可令目标暂时缺失，旧字节仅在 backup，**不得盲删 backup**。三份报告顶部均有 Codex 限定复核。A–E 仍 **2/5=40%**，C 未退出。

> 2026-09-28 S12 导入末章完成态修复回读：导入器新增估算百分比 99.4% 上限和仅在导入边界的状态保护，未动共享 merge/书架。Codex 串行重跑导入器 **12/12** + EPUB locator 契约 **2/2**，合计 **14/14、EXIT 0**；执行者报告写 3 项为计数错误。当前只接受源码/Mock DAO/JVM 范围，真实 Room/MuMu 完成态展示未验。A–E 全局仍 **2/5=40%**，C 未退出；下一项转交 ZCode GLM 5.3 Flash，核实并处理 S06“跟随系统”映射。旧账本所列启动镜像回填缺口已见当前代码 `backfillBootThemeIfNeeded` 与测试，不能重复实现。

> 2026-09-28 S12 TEMPORARY 补验经 Codex 回读：搜索正文结果点击进入阅读器、翻页后返回搜索页，DB 前后 Legado marker 完整相同；普通阅读相同动作写入原生 locator，MuMu 行为对照限定 PASS。`beginTemporaryInspection` 无直接运行态日志，内部路径据生产源码与行为推断；不将其写为直接观测。APK SHA 沿用上轮，原 qahost 主 DB 冷备份恢复 SHA 一致、既有 36 文件 diff=0、密度恢复。下一 S12 优先消除导入章号百分比静默标 FINISHED；全局 A–E 仍 **2/5=40%**，C 未退出。

> 2026-09-28 Antigravity S12 新报告独立复核：受控 ZIP 生产 `importArchive`→真实 Room DAO→普通阅读器提示/静止退出 marker 保留/主动位移后原生 locator 有 MuMu `4/4` 原始日志、DB 与 APK/ZIP 哈希证据；首次提示仅 XML 命中，所附截图实际是加载中。报告的 TEMPORARY PASS 越界：测试只手动设布尔值、调用守卫函数和解析路由，未进入真实临时 ReaderRoute，端到端 **NOT RUN**。下轮只补这一条真实路由，不重做已通过的导入/UI 链。A–E 全局仍 **2/5=40%**，C 未退出。

> 2026-09-28 Antigravity MuMu 两项回读：S10 双 Picker 在 420 dpi 手机尺寸竖屏的两入口截图/UI dump 均显示同一中文折叠行且编辑框保留 token，APK/源码 SHA 已核，接受模拟器限定 PASS。S12 预置未换算 marker 的大 TXT 实际提示、不移动退出后 marker 保持、主动翻页后原生 locator 保存均有截图/DB 工件，接受这段加载/UI/保存路径的 MuMu 限定 PASS；但 `insert_test_book.sql` 是直接落库，**未执行 Legado 导入生产链**，目录 sheet **不是** `navigationMode=TEMPORARY` 临时导航，二者不得记为通过。事后仅 checkpoint 主 DB 哈希与事前一致、既有 36 文件哈希清单无差异，未有 WAL/SHM 事后哈希；报告的“DB 三件”口径已收窄。整体 A–E 仍 **2/5=40%**，S12 剩余两条真实路径待补。

> 2026-09-28 三窗交付及 Codex 集成回读：S12 marker UI/保存保护已补 `temporaryInspection` 接线与即时锚定，Codex 再补三处 effect key 防协程持旧状态；新鲜生产编译及 JVM **27/27、EXIT 0**，MuMu TXT 导入→提示→不移动退出重开→移动保存仍未验。S14 ZIP 安全修复在 MuMu qahost 隔离持久库原始 `OK (6 tests)` ×2、ADB 0，APK SHA 与 qahost DB/WAL/SHM 前后 SHA 已回读；限定接受隔离 6 项，整包非原子、既有 files/books 未逐文件哈希。S10 双 Picker 已共用组件、编译 EXIT=0、JVM 5/5+21/21，MuMu 折叠行视觉未验；报告旧禁 MuMu 边界已更正。三项均未等于整个阶段 C 退出，A–E 全局仍 **2/5=40%**。

> 2026-09-28 S12 窗口 1 补交已回读：报告及 E 盘定向 JVM **29/29、EXIT 0**，UI 提示和退出守卫已有源码接线。但 Codex 静态发现两处未被纯状态机测试覆盖的生产路径：`temporaryInspection` 未传 null 给 `observe`，以及首次锚定仅发生在 500ms debounce 之后，用户提前移动可能被误锚为初始落点。见 [`窗口 1 报告顶部 Codex 注记`](../../legacy-stages/2026-09/2026-09-28-s12-streaming-txt-marker-ui-guard-result.md)。S12 此项仍部分完成，需窄范围补修及验证；整体 A–E 仍 **2/5=40%**。

> 2026-09-28 最新回读：窗口 2 的 S14 跨平台替换修复已落盘，Codex 回读 E 盘 `codex-s14-fix-20260928` 原始 XML/日志哈希与源码：定向 JVM **10/10、0 失败/错误/跳过、EXIT 0**；旧文件保护性 backup 与输入流关闭已实现。此结论仅限 JVM，MuMu qahost 隔离持久库 6 项仍未运行，DB 先提交/文件后复制仍非整包原子。窗口 1 约定的 `2026-09-28-s12-streaming-txt-marker-ui-guard-result.md` 在恢复仓库及原仓库均不存在，`unconvertedLegadoPosition` 仍仅定义/赋值、无 UI 消费；S12 marker 保护**未核实交付**，不能据口头“都完成了”关闭。整体 A–E 仍 **2/5=40%**。

> 2026-09-28 S14 新鲜集成门禁：Codex 串行执行 `:app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.LocalZipBackupTest`，生产/测试 Kotlin 编译通过，但 **9 tests / 1 failure / EXIT 1**。失败为 staging 成功替换既有目标时 `File.renameTo(dst)` 返回 false（本机 Windows JVM，`LocalZipBackup.kt:182`）；原始 XML 已保全到 `E:\creation-reading-assistant\codex-s14-review-20260928\LocalZipBackupTest-failed.xml`，SHA-256 `F874A043407615584AE2D917E0F545B764AA4E2D954875D5D32AB90930F94582`。窗口 2 报告预期 8 例，实际当前 9 例。S14 修复暂不通过，需在其独占文件内修复跨平台替换与失败回滚，再跑 JVM，MuMu 6 项随后串行验收；不得把编译通过当作数据安全通过。

> 2026-09-28 总指挥复核：阶段 B 按“风险有证据和明确处置、未验数据完整性不写 PASS”**限定退出**，见 [`阶段 B 退出复核`](../../reviews/2026-09/2026-09-28-stage-b-exit-review.md)。A–E 整体按完整阶段退出计 **2/5=40%**，C 修复与定向验证、D 候选冻结验收、E 版本整理仍未退出。窗口 1 的 S12 流式 TXT 25/25 定向 JVM 已核，但无可靠章节时 UI 未消费 `unconvertedLegadoPosition`，自动保存可覆盖 marker，故仅部分完成；窗口 3 的 S03/S04 只读归并接受其缺口分类。窗口 2 的 S14 ZIP 文件保护/版本偏斜修复已落代码和测试报告，**未编译/未执行**，不得写通过。以下较早 20% 记录为历史快照，不代表当前进度。

> 2026-09-28 S01/S02 阶段 B 回读：`ParentUpsertCascadeSafetyTest` 原始真机 5/5、`AppDatabaseMigrationTest` 23/23 经 Codex 复读日志与当前测试数，当前迁移链 1→15、无破坏性兜底；接受两项在报告定义范围内关闭，不重复设备轮。见 [`S01/S02 复核与 Codex 注记`](../../reviews/2026-09/2026-09-28-stage-b-s01-s02-data-risk-closure-review.md)。报告的 mtime“零漂移”只作辅助，不是字节同一性证明；未来相关源码/Schema 改动需重测。S03/S04 及新 S14 风险仍待阶段 B 归并，A–E 总进度仍 1/5=20%。

> 2026-09-28 阶段 B 只读增量：S14 本地 ZIP 恢复存在当前源码支持的两类数据风险——DB 提交后文件直接覆盖失败可能留下中间态；较新本地书籍行跳过合并时，对应旧 ZIP 文件仍无条件覆盖，成功路径也可能发生行/文件版本偏斜。窗口 2 报告及 Codex 复核见 [`S14 失败窗口`](../../legacy-stages/2026-09/2026-09-28-s14-zip-restore-failure-atomicity.md)；故障注入与修复未执行。S10 两份 `HeaderFooterPicker` 的折叠文案分叉已核实，可独立小修；卷标题计章/跳过/扩判定均待产品裁决，见 [`S10 决策材料`](2026-09-28-s10-display-contract-decision-brief.md)。全局 A–E 仍仅 A 退出，1/5=20%。

> 2026-09-28 S12/S14 新证据：S12 EPUB locator 契约定向 JVM 108/108（8 类、0 失败/错误/跳过）经 Codex 回读日志与 XML，接受代码/JVM 范围；S12 设备端到端、TXT 导入与静默 FINISHED 仍开放。S14 新增隔离持久库/本地 ZIP androidTest 在 MuMu qahost 最终 `OK (3 tests)`，宿主/测试 APK SHA-256 与 qahost 原库 DB/WAL/SHM 前后哈希见 [`S14 QA`](../../../qa/2026-09/2026-09-28-s14-mumu-persistent-zip-acceptance.md)。第一次 3 项中 1 项失败为测试路径断言，修正后重跑 3/3；不扩大到 WebDAV、正式库、实体手机或 DB 已提交后文件复制失败的原子性。全局 A–E 仍仅 A 退出，1/5=20%。

> 2026-09-28 S14 测试准备：新增隔离持久库/本地 ZIP androidTest 3 项与准备报告，Codex 静态复核时补齐 `LocalExport` 构造所缺两项必填空列表。测试目前 **未编译、未执行**，S14 数据恢复验证缺口尚未关闭；须在唯一构建/设备窗口空出后串行编译和 MuMu qahost 执行，并核 APK 哈希及隔离数据前后对账。见 [`S14 准备报告及 Codex 补记`](../../2026-09-28-s14-persistent-backup-test-preparation.md)。全局 A–E 仍 1/5=20%。

> 2026-09-28 S09-B 分诊更新：四个 androidTest 类的 `@Test` 数经 Codex 独立核对为 37+10+7+8=62，真实 FQCN 与 `TapZonePreviewCard` 当前 12%/76%/12% 几何也已核对。见 [`窗口 3 只读报告`](../../legacy-stages/2026-09/2026-09-28-s09-ui-suite-triage.md)。62 项至今仍是 **NOT RUN**；报告提出的 qahost 批次 1 和硬件键/生产点按批次 2/3 是验收计划，不是 PASS。新测试文件可能改变测试 APK 内容，执行前须重核源码/测试 APK 新鲜度与哈希；人工批次须先建立隔离测试书与前后快照，不直接动 qahost 既有书。旧 SR-ENV-1 对 MuMu/qahost 全面阻塞的表述已过时，但实体手机 MIUI 仍未验证。全局 A–E 仍仅 A 退出，1/5=20%。

> 2026-09-28 S06 最新：MuMu qahost 定向 instrumentation 快翻 8/8、滑杆 RTL 4/4，原始日志 `OK (12 tests)` / ADB exit 0；实际进程 `[ar] ldrtl` 与同值 LTR/RTL 页面截图支持该页方向镜像，设置已恢复。见 [`S06 QA 及 Codex §9 复核`](../../../qa/2026-09/2026-09-27-s06-mumu-page-turn-rtl-acceptance.md)。截图是 280 dpi、`sw617dp` 平板布局，不能当手机布局视觉 PASS；同帧合并、切书队列、拖动/像素贴合/书内进度条仍未覆盖。全局 A–E 阶段退出仍 1/5=20%。

> 2026-09-27 S05-④ 最新：MuMu qahost 420 dpi 手机布局下，书名排序后导入点击「查看」滚到非首位本批测试 TXT、跨批目标正确、筛选排除有反馈且恢复筛选不发生过期滚动，原始截图/XML 见 [`S05 报告及 Codex §11 复核`](../../../qa/2026-09/2026-09-27-s05-mumu-import-focus-acceptance.md)。功能范围 3/3 限定 PASS；第一次 Snackbar 超时误点打开既有书，缺该书前后 DB/进度对账，不能宣称零旁路影响。测试密度 420→280 已回读；报告文本已去除既有书具体标题。S06 设备测试仍待跑；全局 A–E 阶段退出仍 1/5=20%。

> 2026-09-27 最新：S04 真正来源刷新已在 MuMu qahost 隔离测试书上执行，确认框仅 1 本、`library_source_refs` 基线推进、索引 coverage +2 / terms +127、其余书与进度快照无变化；见 [`报告及 Codex §13 复核`](../../../qa/2026-09/2026-09-27-s04-mumu-true-source-refresh.md)。本轮截图为平板式左导航，且正文操作前已同字节，因此手机布局视觉和“新字节刷新正文”仍待验。B/C/D 三份只读报告已到，S05 UI 点击、S06 快翻/RTL 设备测试未跑。全局 A–E 阶段退出仍 1/5=20%；自动接力 PAUSED。

> 2026-09-27 增量：S04 EPUB 回滚保护代码、定向 JVM/集成构建与 qahost 隔离包已有新证据；MuMu 上 8/8 instrumentation、UI 来源刷新、非零进度、不同长度 `size` 更新、残留拒绝、诊断页，以及 V4→V5 DB/WAL/SHM 一致快照见 [`S04 MuMu qahost UI 对账`](../../../qa/2026-09/2026-09-27-s04-mumu-qahost-ui-reconciliation.md)。用户明确允许 MuMu 继续设备验证；此证据不等于实体手机/正式书库验收。以下 2026-09-22 账本条目是当日快照，不能据其旧数字推断当前 S04 状态。全局 A–E 仍仅 A 完整退出，1/5=20%。

> 2026-09-27 入口口径更正：上条所称 S04 UI“来源刷新”实际走书架「重新定位文件」→ `repairFile`，只证明内部正文/书籍行/进度；`library_source_refs` 基线仍为 V1，真正的 `refreshFromSource`（书架下拉后确认）待在隔离测试书上核验。S14 独立内存 Room 验收 2/2 通过，范围及证据收窄见 [`S14 MuMu Room 报告`](../../../qa/2026-09/2026-09-27-s14-mumu-room-rollback-acceptance.md) §8。

更新：2026-09-22，阶段 A 对账。调度入口以本页和 [收口计划](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md) 为准；旧报告保留为证据。

本轮仅保全、查证和文档整理，没有修改产品/测试源码、运行 Gradle、操作手机或 Git 提交。外部窗口尚未自动启动。

## 1. 固定快照

- 恢复仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`。
- 分支：`codex/recover-worktree-20260920`；HEAD：`5aed5939600e491843e3c44453921bd6273a233c`。
- 快照：`D:\develop\Code\Codex\recovery-snapshots\20260922-stage-a`，时间 2026-09-22 16:53:33 +08:00。
- `manifest.json`：1,146 条展开文件状态、逐文件 SHA-256、分类、是否复制；当时删除条目 0、暂存文件 0。
- 435 个已修改/新增的源码、测试、配置、脚本和文档有独立副本；699 个证据文件、11 个本地 Agent 配置、1 个临时 UI 工件只登记哈希，保留原位。
- `base-head.zip` 保存基线源码；在 `reconstructed/` 解包基线并覆盖 435 个副本，逐文件哈希不一致 0。
- 回读校验：副本不一致 0、源文件变化 0、状态列表变化 0。仅证明捕获窗口静止，不表示外部窗口今后不会写入。
- 快照可重建选定源码内容，不含 Git 历史、ignored 文件、SDK/local.properties、私密配置或设备数据；不是整盘备份，也未进行重建目录构建。
- 本页及本轮指挥文档是快照之后的文档增量，最终验收前需另建新的候选快照。

| 资产类别 | 文件数 | 已复制 |
|---|---:|---:|
| Android 生产源码/资源 | 211 | 211 |
| Android 测试 | 98 | 98 |
| Android schema | 1 | 1 |
| Android 构建配置 | 1 | 1 |
| Android benchmark | 2 | 2 |
| Android 工具脚本 | 4 | 4 |
| 文档 | 91 | 91 |
| Desktop/共享脚本 | 27 | 27 |
| 截图、日志等证据 | 699 | 0 |
| 本地 Agent 配置 | 11 | 0 |
| 临时 UI 工件 | 1 | 0 |

## 2. 计数与状态规则

20 个 S 编号是范围分组，不是 20 个等量开发任务；子项不得和父项相加计算完成率。这里区分当前静态查证、历史验收、缺少证据。

已关闭的历史任务保留关闭结论，但不把历史测试数字说成本轮新跑结果。待核实不等于仍有缺陷；延期不等于删除；测试受阻不等于通过。

## 3. 20 个父切片

下表文件为范围入口，均相对恢复仓库；不授权整文件覆盖或提交。所有未细分 hunk 的共享文件暂由 Codex 保管。

| ID | 用户能力与源码入口 | 当前状态与依据 | 依赖/下一步 | 责任 |
|---|---|---|---|---|
| S01 | 父记录更新保留进度/标注；BookDao、InspirationDao、TaxonomyDao | 已关闭（当前范围）：真机 `ParentUpsertCascadeSafetyTest` 5/5 通过，覆盖五类父表及规则、纠错等子记录保留 | 保留 S02 分离；不扩张为任意数据库损坏恢复结论 | Codex 裁决；独立真机验收 |
| S02 | 数据库升级到 v15；AppDatabase、DatabaseModule、15.json | 已关闭（当前范围）：真机 `AppDatabaseMigrationTest` 23/23 通过，含 14→15、13→15 与正式开库回调路径 | 不宣称覆盖任意损坏数据库或所有厂商环境 | Codex 数据库 owner；独立真机验收 |
| S03 | 全文搜索与重建；SearchIndexRepository、SearchIndexScheduler、SearchTermDao | S03-1/2/3 代码与 JVM 范围已收口；S03-1 fresh 并发 JVM 19/19，S03-2 27/27，S03-3 29/29；未做 S03-1 独立真机/真实 Room 验收 | 下一步独立验收 S03-1 的隔离数据库与安全设备路径；不得操作用户书库 | Codex 共享 owner |
| S04 | 来源更新；ShelfSourceUpdateChecker、ShelfImporter、ShelfViewModel | 已关闭（当前代码与 JVM 范围）：S04-1 前置确认、S04-2 异步索引、S04-3 指纹重算、S04-4 元数据同口径防护全部收敛，fresh JVM 58/58 通过 | 来源引用不入备份属 S14 范围；真机端到端全量重算留待系统验收 | Codex 共享 owner |
| S05 | 书架状态/筛选/导入后定位；ShelfModels、ShelfBookActions、ShelfRoute | 待核实：9/20 报告中的状态与筛选问题未逐项对应新证据 | D 只读对账后再分文件实现；与 S04 的 ShelfViewModel 有共享 | Codex 共享 owner；D 调查 |
| S06 | 启动主题/章号/快翻/滑杆；SettingsStore、Reader progress、PageTurner、LibraryBrowserViewModel | 待核实：本轮仍见 theme else→MODE_NIGHT_AUTO；不能据该一行判定全链修复状态 | D 核对五个子片当前行为与既有修复；共享依赖 S17 | D 调查，Codex 裁决 |
| S07 | 选区/标注/复制/跨格式；Reader selection 与消费者快照 | 已关闭（历史）：F5 与 TTS 补验、主任务复核见旧看板和 9/21 QA | 相关 Reader 输入变化时回归；不因新账本重新开发 | Codex 维护历史结论 |
| S08 | 章节修改；feature/reader/edit | 延期：差异算法候选、编辑入口 dormant；未接入完整保存/撤销 | 保留代码；明确产品合同前不接 UI，不当死代码删除 | Codex 决策 |
| S09 | 书签坐标、点击区、页码/按键；ReaderSheetHost、ReaderTapAction、PagedEpubContent | 部分关闭/待验证：A 代码修复关闭；B 真机部分覆盖；页码/硬件键另有未决项 | A/B 与其他增强能力分别登记；依赖 S06/S17 | Codex 读者交互 owner |
| S10 | 目录卷标题、页眉页脚；ReaderTocModels、CustomTipPlaceholder、PagedReaderHeaderFooter | 待决定：已有 9/22 调查与严重度修正 | 明确卷分组与可读章节、滚动展示合同；既有入口保留 | Codex 决策 |
| S11 | EPUB 图片/脚注/引用；EpubImageViewerDialog、EpubFootnotePopover、QuoteCardDialog | 待核实：不能仅凭文件未跟踪就归为未启用实验 | 查真实调用和现有入口，再定必要验收或延期 | Codex 待分配 |
| S12 | Legado 进度导入；LegadoReadingProgressImporter、ReaderDocumentLoader | 待决定：调查已接受；3 个现役候选问题与 EPUB 坐标就绪风险需分开 | 完成态写入权限、预览和坐标 owner 优先；不混入 S14 大改 | Codex 决策 |
| S13 | 设置搜索/密度/页面；profile/*、SettingsSearchIndex | 待核实：D-2 作用域已关闭不代表全部设置重构完成 | 以已接入入口清单验收，依赖 S17 公共组件 | Codex 待分配 |
| S14 | 同步和备份；SyncRepository、SyncOptions、WebDavBackup、JsonBridge | 代码与 JVM 范围已收口：备份增加来源引用、规则和纠错，合并调用位于事务块内，C2/C3 文案对齐；定向 JVM 41/41 通过 | 模拟 DAO 测试未验证真实 Room 回滚；模拟 WebDAV 未验证真实网络；跨设备来源 URI 的可访问性需单独验收 | Codex 合同 owner |
| S15 | 时长单位与长按；DurationPrefs、DurationDisplay、各展示页 | 待验证：F1/F2/F4 与 F3 代码轴历史已过，D 本轮设备报告 PARTIAL | F-2 取数调查、>=24h/完读 pill/TalkBack 等覆盖；不以 UI 开关变化代替可见文本差异 | Codex；后续独立验收 |
| S16 | 首页/灵感/统计/历史/搜索页面 | 待核实：与 S03/S05/S15/S17 有混合改动 | 按用户旅程归类 hunk；当前功能默认保留 | Codex 共享 owner |
| S17 | 应用壳/导航/主题/公共组件 | 待核实：公共依赖，不可按编号等到最后 | App/Activity、导航、公共组件各成边界；被依赖的部分先收口 | Codex 独占集成 |
| S18 | 性能测试/profile | 待核实：历史 benchmark 与 profile 产物不能混判 | 环境不可用保持未验证；不改用 MuMu | Codex 待分配 |
| S19 | 安装脚本/诊断资产 | 部分关闭：F2 守卫历史 84 项契约通过；诊断脚本、临时 XML 去向未定 | 正式脚本保留；不自动删除工件；后续安装留原始日志 | Codex；D 后续使用 |
| S20 | Desktop/Agent/截图日志 | 待决定：已分类和保全，未执行删除 | Android 提交不夹带；独有资产迁移前逐项安排 | Codex 独占 |

## 4. 子任务与更正（不重复计算父项进度）

| ID | 状态 | 证据/处理 |
|---|---|---|
| A01 | 已关闭，历史验收 | 9/21 a01-qoder-device-acceptance；标注显示与 typed 回源 |
| D-2-A | 已关闭，历史验收 | 9/21 d02a-qoder-device-acceptance；引擎本书覆盖 |
| D-2-B | 已关闭，历史验收 | 9/22 d02b-qoder-device-acceptance；全局/本书说明；旧横屏结果不形成今后的测试要求 |
| S09-A-F1 | 代码修复已关闭，历史门禁 | 本书 bookId 过滤；设备旅程随相关 Reader 回归 |
| S09-B / S15-F3-QA | 待验证，UI 自动化受阻 | 9/22 联合报告四类 37+10+7+8=62，完成 0；MIUI 启动阻塞；无真实听读等覆盖 |
| S19-F2 | 守卫修复已关闭，历史门禁 | 本任务已有 fresh 84 项契约证据；旧看板停在 F1 不是待重修证据 |
| D-F1 | 体验建议，取消实施 | 保持翻页时收菜单的现有合同；旧 B 指令作废 |
| D-F2 | 待调查 | 完读卡只读 progress.total_reading_time_ms，与详情页策略不同；先查范围，不自动选相加/max |
| D-F3/F4/F5/F6 | 非阻断待办 | 档案卡长按导航、分隔符、图例换行、测试路径/package；不能据小问题无限追加整轮验收 |
| C01 | 已完成：恢复 `SearchIndexCoverageDao.rowsByStates` 与 `SearchIndexRepository.incompleteBookIds` | 见 [搜索索引完整性修复](../../legacy-stages/undated/agent-stage-d-search-index-integrity-repair.md)；其余清理候选保持原决定，不在本轮恢复 |

## 5. 下一轮三个互斥调查包

详见 [B/C/D 调查任务书](../../legacy-stages/2026-09/2026-09-22-stage-b-investigation-prompts.md)。提示词已交用户；B 已交报告、局部接受并待修正文案/测试设计，C/D 待用户回传。只读产品代码，各写一个报告，不跑构建和手机。

| 窗口 | 工作包 | 唯一可写报告 | 退出条件 |
|---|---|---|---|
| B | S01/S02 数据安全证据及迁移测试设计 | agent-stage-b-database-audit.md | 列出已执行证据/真实缺口、最小测试与独占文件 |
| C | S03/S04/S14 索引、来源替换、备份资产 | agent-stage-b-dataflow-audit.md | 每项有当前代码链路与证据等级；风险去重、给最小边界 |
| D | S05/S06 书架/阅读核心旧问题对账 | agent-stage-b-core-audit.md | 逐项 still-present/fixed/unproven/design-decision，并列后续最小验证 |

三者完成后由 Codex 一次汇总：决定真正的修复清单，安排最多两路互斥实现；C01 与 S03 共用文件，先串行处理。此次没有把整个计划自动授权给外部窗口。

### B 首次回传复核（2026-09-22）

- 报告：[S01/S02 调查](../../reviews/undated/agent-stage-b-database-audit.md)。修订后定向核对接受：五类父表当前使用 @Upsert；现有迁移测试最高目标 v14，未见 14→15 与正式回调组合的覆盖。属于源码/测试缺口结论，不是本轮运行通过。
- 报告待更正：15.json 实有 18 条外键（16 CASCADE、2 SET NULL），不是 14 条；books 的 13 条外键与全库统计须分开。
- 现有父表测试已断言进度、会话等保留，能针对改回 REPLACE 的级联风险；缺少规则/纠错直接断言不等于“改回 REPLACE 测试仍通过”。未执行变异测试，不声明实测结果。
- 测试设计待精简/校正：查询返回正确不能证明选用索引；v13 不存在 library_source_refs，13→15 用例须区分旧数据保留与新表建立。注册连续仅证明路径齐全，不能直接保证所有历史数据库升级成功。
- 本次快照比对：435 副本无差异；快照所列产品/测试源无变化，新增状态仅见阶段 A 三份文档与 B 报告，另三份已知指挥文档有变化。未运行 Gradle/ADB。
- 该次复核只要求 B 修正其唯一报告，未在当时授权产品或测试实现；其修订结果见下方汇总。

### B/C/D 汇总复核（2026-09-22）

- B 修订已接受，可进入只改数据库测试的实现片；不改生产迁移，后续数据库 androidTest 仍需独立真机执行才能形成运行证据。
- C 报告大部分链路可用于排期：coverage-only 孤儿、正文替换后索引/基线、备份覆盖缺口均有当前源码入口；但 C01 状态写错。`incompleteBookIds` 与 `rowsByStates` 当前工作树已删除，HEAD 中仍存在，正是可精确恢复的基线，不能写成“HEAD 也无匹配”。S03-3 与 S04-2 是相关的索引调度问题，但一个是同步等待、一个是缺写后更新，不合并成同一个缺陷。
- D 报告定向核对接受。优先实现小 TXT 章号真源；书架取消读完与筛选徽章仍含产品语义选择，暂不让执行窗口擅自决定。
- 新一轮最多两路实现：B 仅数据库 androidTest；D 仅小 TXT 章号真源与 JVM 测试。C 只修正报告。三者文件不重叠；不触碰原仓库、不提交、不推送、不验机。
- 阶段 A 快照复核：435 个副本仍无哈希差异；快照内生产/测试源没有 B/C/D 新改动。本轮报告均为静态审查，不构成构建或真机通过。

### 第一轮实现回传复核（2026-09-22）

- B 已在两个 androidTest 文件补充父表规则/纠错保留断言，以及 14→15、13→15、正式开库回调三类迁移测试；生产数据库代码和 schema 未改。代码尚未编译或执行。其报告需更正：`ParentUpsertCascadeSafetyTest.kt` 在 HEAD 中不存在，但阶段 A 快照已包含该未跟踪文件，本轮是在用户既有文件上追加 46 行，不是本轮新建。
- C 已正确修订 C01：两个 API 是当前工作树相对 HEAD 的未提交删除；并将 S03-3/S04-2 分开登记。报告修订接受，本轮无代码改动。
- D 已在 `ReaderScreenDerivedState.kt` 接入小 TXT 的章节偏移/标题同源选择，并新增 9 个 JVM 用例；EPUB、Markdown、流式 TXT 优先级保持在小 TXT 之前。代码尚未编译或执行，真机可见结果仍未验证。
- 快照核对显示本轮新增代码差异仅为上述三个既有文件和一个新 JVM 测试文件；阶段 A 的 435 个副本没有其他意外哈希变化。下一步由单一窗口串行运行 Gradle，其他窗口不得同时构建或修改源码。

### 第一轮统一构建证据（2026-09-22）

- 独立报告：[统一构建验证](../../legacy-stages/undated/agent-stage-c-unified-build-validation.md)。主代码编译 exit 0；小 TXT 定向 JVM 9/9；androidTest 源码编译 exit 0；完整 JVM 2,928 项、0 failure、0 error、0 skipped。
- Codex 回读 `app/build/test-results/testDebugUnitTest` 的 316 个 XML，重新累计得到 2,928/0/0/0；目标类 XML 为 9/0/0。构建日志末尾与报告所列 BUILD SUCCESSFUL、执行任务相符。
- 这些证据接受“可编译且 JVM 回归通过”，不接受“数据库迁移已运行”或“小 TXT 真机显示已通过”：androidTest 仅编译，未安装/运行；Lint、APK 打包和真机仍未执行。
- B 实现报告的阶段 A 文件归属已修正，HEAD 字符串由 Codex 更正为 `5aed5939600e491843e3c44453921bd6273a233c`，未改测试代码。
- 下一步交独立验收窗口串行执行 Lint、APK/androidTest APK 打包和真实手机上的两个隔离数据库测试类。小 TXT 竖屏旅程会写阅读进度，需另行建立不接触用户书籍数据的安全 fixture；本轮不打开书籍、不改用户书库。

### 首次真机验收尝试被 Lint 阻断（2026-09-22）

- 验收报告：[数据库真机验收](../../../qa/2026-09/2026-09-22-stage-c-database-device-acceptance.md)。`lintDebug` exit 1，3 errors / 9 warnings；按门禁立即停止，两个 assemble、安装和 instrumentation 均未执行。数据库测试是 BLOCKED/未运行，不是测试失败。
- 设备只做了只读基线检查；主包、测试包、通知权限、stay-awake 和方向前后相同，未打开书籍、未改用户数据。
- 三个 error 同源：`MainActivity` 覆写 AndroidX 受限的 `dispatchKeyEvent`，并在 `KeyEvent.action` 上比较 `MotionEvent.ACTION_SCROLL`。KeyEvent 的 action 域是 DOWN/UP/MULTIPLE，鼠标滚轮属于 MotionEvent，应走公开的 `dispatchGenericMotionEvent(MotionEvent)`；当前分支除触发 RestrictedApi 外也无法收到真实滚轮事件。
- 根因修复边界：MainActivity 改用 generic-motion seam；ReaderHardwareKeys 的 KeyEvent 入口删除不可达的滚轮反射分支，保留纯 `dispatchScroll(Float)`；不得用 `@SuppressLint` 或 lint baseline 掩盖。由阅读器输入 owner 先完成定向 JVM、主编译和 Lint，再重新交独立验收。

### Lint 根因修复回传（2026-09-22）

- 修复报告：[滚轮事件入口根因修复](../../legacy-stages/undated/agent-stage-c-wheel-event-root-fix.md)。`MainActivity` 已改走 `dispatchGenericMotionEvent(MotionEvent)` 并读取 `AXIS_VSCROLL`；`ReaderHardwareKeys.dispatch(KeyEvent)` 的不可达滚轮反射分支已删除。没有添加抑制、baseline 或放宽 `abortOnError`。
- Codex 回读产物：`ReaderHardwareKeysTest` XML 为 9 tests / 0 failure / 0 error / 0 skipped；最新 Lint XML 为 0 errors / 9 warnings，`RestrictedApi` 为 0。主编译报告 exit 0。只接受编译、纯逻辑和 Lint 门禁恢复，不接受真实鼠标方向已经验收。
- 下一步重新交独立验收窗口：从 fresh Lint 开始，再打包、安装并运行两个隔离数据库测试类；仍不打开书籍、不测横屏。数据库验收不得因滚轮功能尚未做硬件实测而阻塞。

### S01/S02 独立真机验收关闭（2026-09-23）

- 验收报告：[数据库真机验收 retry-01](../../../qa/2026-09/2026-09-22-stage-c-database-device-acceptance.md)，原始证据位于 `../qa/evidence/2026-09-22-stage-c-database-device-acceptance/retry-01/`。
- fresh Lint 使用 `--rerun-tasks` 强制实际执行 31/31 个任务：exit 0，0 errors / 9 warnings，`RestrictedApi` 0；`assembleDebug` 与 `assembleDebugAndroidTest` 均 exit 0。后续不得把 AGP Lint 的 `--rerun` UP-TO-DATE 结果当作 fresh 证据。
- 主 APK 与 androidTest APK 均由 `install_with_confirm.ps1` 自动确认安装成功，无直接 ADB 回退。真实手机 `c49ac6cf` 上 `ParentUpsertCascadeSafetyTest` 5/5、`AppDatabaseMigrationTest` 23/23，均 0 failures/errors、`INSTRUMENTATION_CODE: -1`。
- 未启动主应用、未打开或修改书籍、未清除应用数据、未横屏、未使用模拟器。主数据库与 WAL 的大小/mtime 未变；测试仅新建隔离的 `migration-test` 数据库。通知权限已恢复为 false，stay-awake 保持 3，方向保持 0/0。
- 据此关闭 S01 与 S02 的当前实现及运行证据缺口。仍未覆盖小 TXT 真机可见效果、鼠标滚轮硬件行为、Compose UI androidTest；9 个 Lint warning 为已登记的非阻断项，不能把本次结论表述为整个项目或发布验收完成。

### C01 恢复与 S03-2 搜索索引完整性修复（2026-09-23）

- 实现报告：[搜索索引完整性修复](../../legacy-stages/undated/agent-stage-d-search-index-integrity-repair.md)。已按 HEAD 原语义恢复 `rowsByStates` / `incompleteBookIds`，并让 `purgeOrphanIndexRows` 从 term 与 coverage 两侧 book ID 的去重并集识别孤儿；活跃的 coverage-only 书不会被误删，同一本书在两表中只按一本计数。
- Codex 发现普通定向命令只返回 `UP-TO-DATE` 后，改用 `--rerun-tasks` 强制 fresh 验证：30/30 Gradle tasks 实际执行，BUILD SUCCESSFUL；目标 XML 为 27 tests / 0 failures / 0 errors / 0 skipped，Kotlin/KSP 重新编译通过。
- 据此关闭 C01 与 S03-2 的 JVM/编译范围。没有运行新增 DAO 查询的真实 Room/真机路径，也未做变异测试；S03-1、S03-3 仍开放，不能据此关闭整个 S03。

### S04-2 来源刷新异步逐书索引更新接缝（2026-09-23）

- 实现报告：[来源刷新异步逐书索引更新](../../legacy-stages/undated/agent-stage-e-per-book-index-refresh.md)。已建立通用单例 `SearchIndexBookRefreshCoordinator`（位于 `data.repository` 层，依赖 `@ApplicationScope` 与 `SearchIndexRepository`，零 UI 依赖）；在 `ShelfImporter.refreshFromSource` 正文替换成功且来源基线推进后发起异步请求 `request(bookId)`，调用方立即返回不阻塞；普通重建失败仅记 AppLog，不改变正文刷新成功结果，且正确传播 `CancellationException`。
- fresh 验证：使用 `--rerun-tasks` 强制实际执行 30/30 Gradle tasks，BUILD SUCCESSFUL；目标 XML 验证：`SearchIndexBookRefreshCoordinatorTest` 6/6、`ShelfImporterTest` 29/29、`ShelfViewModelTest` 16/16，总计 51 tests / 0 failures / 0 errors / 0 skipped，Kotlin/KSP 编译通过。
- 据此关闭 S04-2 的 JVM/编译范围。真机端到端全量重算效果未测；S04-1（前置确认）、S04-3/4（指纹与基线同口径）、S03-3（撤销删除接入协调器）仍开放，不能据此关闭整个 S04。

### S04-1 来源刷新前置一次性确认与注释对齐（2026-09-23）

- 实现报告：[书架来源刷新前置一次性确认与注释对齐](../../legacy-stages/undated/agent-stage-f-source-refresh-confirmation.md)。已修正 `ShelfUiState.kt` 中 `contentUpdatedBookIds` 的注释声明，消除 C1 契约矛盾；在 `ShelfViewModel` 接入 `pendingSourceRefreshIds` 状态流，下拉刷新检测到更新时仅置待确认状态，绝不直接替换正文；用户取消时清空状态、保持书卡角标不变、正文零次替换；用户确认后串行执行 `refreshFromSource` 并汇总反馈；`ShelfRoute` 接入 `GlassAlertDialog` 确认弹窗（展示书本数与书名）。
- fresh 验证：使用 `--rerun-tasks` 强制执行 30/30 Gradle tasks，BUILD SUCCESSFUL；目标 XML 验证：`ShelfViewModelTest` 20/20（新增 4 项针对确认/取消/无变动/汇总的断言），交叉回归总计 55 tests / 0 failures / 0 errors / 0 skipped，Kotlin 编译通过。
- 据此关闭 S04-1 的 JVM/编译范围。真机 UI 弹窗视觉效果未测；S04-3/4（指纹与基线一致性）仍开放，不能据此关闭整个 S04。

### S04-3 / S04-4 来源基线重算与元数据缺失保护（2026-09-23）

- 实现报告：[来源基线重算与元数据缺失保护](../../legacy-stages/undated/agent-stage-g-source-baseline-integrity.md)。已修正 `ShelfImporter.refreshSourceBaseline`：
  1. **S04-4**：`size` 与 `last_modified` 统一实施同口径兜底防护，provider 缺失时间戳时保留旧基线；provider 完全不可读时保持旧基线不写库，严禁编造或抹除已有数据。
  2. **S04-3**：基于已落盘的内部正文副本重新计算 `newContentHash`（<=32MB 时）与 `newFingerprint`（>32MB 时），彻底解决正文刷新后去重判定沿用陈旧指纹的问题。
- fresh 验证：使用 `--rerun-tasks` 强制执行 30/30 Gradle tasks，BUILD SUCCESSFUL；目标 XML 验证：`ShelfImporterTest` 32/32（新增 3 项针对新哈希重算、时间戳保留、不可读不写库的断言），交叉全量总计 58 tests / 0 failures / 0 errors / 0 skipped，Kotlin 编译通过。
- 据此关闭 S04-3 与 S04-4 的 JVM/编译范围。至此 S04 在当前代码与 JVM 范围全部关闭。真机端到端全量重算留待后续验收。

### S03-3 撤销删除接入异步索引协调器（2026-09-23）

- 实现报告：[撤销删除接入异步索引协调器](../../legacy-stages/undated/agent-stage-h-undo-delete-async-reindex.md)。已在 `BookRepository` 构造函数中注入 `SearchIndexBookRefreshCoordinator`；在 `restoreBook` 与 `restoreDeletions` 成功恢复书籍后，删除原有阻塞等待的 `searchIndexRepository.reindexBookById` 同步调用，改为调用 `indexRefreshCoordinator.request(bookId)` 异步派发重建任务；恢复书籍立即返回，彻底解除界面卡顿与 ANR 风险；未注入协调器时自动平滑回退，维持既有测试夹具兼容。
- fresh 验证：使用 `--rerun-tasks` 强制执行 30/30 Gradle tasks，BUILD SUCCESSFUL；目标 XML 验证：`BookRepositoryTest` 23/23（新增 5 项针对成功异步触发、解除同步调用、未找到已删书不触发、删除戳为空不触发、快照未恢复不触发的断言），与 `SearchIndexBookRefreshCoordinatorTest` 联合回归 29/29 通过，Kotlin 编译通过。
- 据此关闭 S03-3 的 JVM/编译范围。S03-1（全量重建代次竞态）仍开放，不能据此关闭整个 S03。

### S14 备份覆盖面补充与文案对齐（2026-09-23）

- 实现报告：[备份覆盖面补充与文案对齐](../../legacy-stages/undated/agent-stage-i-backup-coverage-extension.md)。
  1. **备份覆盖扩展（S04-5）**：在 `LocalExport` 增设专用序列化 DTO，导出时打包 `library_source_refs`（来源引用）、`reader_text_rules`（替换规则）与 `reader_text_corrections`（正文纠错）；恢复导入时在单事务内按时间戳（`last_seen_at` / `updated_at`）比对执行“旧不覆新”合并，非法版本防御拒绝。
  2. **文案对齐（C2 & C3）**：`StorageSubPage.kt` 移除虚构的“同步账号信息”表述，准确列全导出的数据类别；`ProfileRoute.kt` 恢复确认弹窗准确列齐 13+ 类恢复数据。
- fresh 验证：使用 `--rerun-tasks` 强制执行 30/30 Gradle tasks，BUILD SUCCESSFUL；目标 XML 验证：`WebDavBackupTest` 23/23（新增 4 项针对 DTO 序列化、单事务安全合并、版本防御拒绝、WebDAV 传输的断言），`JsonBridgeTest` 18/18 全量通过，合计 41 tests / 0 failures / 0 errors / 0 skipped，Kotlin 编译通过。
- 据此关闭 S14 的 JVM/编译范围。局域网端对端真机传输留待系统验收。
 
### 全局系统性健康检查（2026-09-23 03:02 - 03:15）

- Antigravity 记录的 Android 门禁均使用 `--rerun-tasks`：Kotlin 编译、全量 JVM 和 Lint 的 Gradle 命令均报告 BUILD SUCCESSFUL。
- Codex 接回后重新汇总当前 317 个测试 XML：2,960 tests / 0 failures / 0 errors / 0 skipped；Lint XML 为 0 errors / 9 warnings。警告分别为 `DefaultLocale` 2、`GradleDependency` 3、`ObsoleteLintCustomCheck` 3、`UnusedResources` 1。
- 本次为交接抽查与产物回读，未重新运行 Gradle 或真机。S14 的 JVM 用例采用模拟 DAO 与模拟 WebDAV；真实 Room 事务回滚、真实网络传输及跨设备来源 URI 权限不在已完成证据内。

### S03-1 代次设计报告复核（2026-09-23）

- 窗口 D 的只读报告：[全量重建代次与游标设计](../../legacy-stages/undated/agent-stage-j-search-generation-design.md)。当前源码确有全量重建重置游标与旧扫描后续 `stateDao.set` 的交错窗口；实际设备或确定性测试尚未复现，不能写成已发生的数据丢失。
- 报告识别了读代次后再写游标的 TOCTOU 风险，但推荐的内存锁方案目前只覆盖游标：`indexOneBook` 还会删除/写入 term 与 coverage 行（含分批写入），旧任务晚到仍可能覆盖新一轮内容。`_progress.isRunning` 的读、令牌占有与释放也尚未形成原子协议；`indexSingleBook` 使用同一进度状态，需纳入所有权审查。
- 报告称锁内无挂起调用与“零数据风险”不准确：方案伪代码在 `Mutex.withLock` 内调用挂起的 Room DAO。屏障测试若拦在已持锁的 DAO 写入点，会阻塞重置并造成测试死锁。设计判为 **PARTIAL**，先修订完整写入边界和可执行测试时序，再授权产品实现；S03-1 仍开放。

### S03-1 修订设计复核（2026-09-23）

- 修订报告已列出书级/章节游标、term 批次、coverage、显示文回收及 `indexSingleBook` 的进度竞争；证据仍为静态审查，未运行并发复现。
- 推荐的“无锁写前取消检查”仍有检查通过后、DAO 实际写入前的 TOCTOU 窗口，无法保证旧任务不在重置之后晚到写入。`activeSweepToken.getAndSet(newToken)` 又与新 worker 只能 `compareAndSet(null, candidate)` 的启动规则冲突，新 worker 可能一直拿不到执行权。仅扩充门禁位置不能构成原子协议。
- 因此修订方案仍为 **PARTIAL**，不得按报告伪代码直接实施。下一轮跳过纯报告往返：先用确定性并发用例证明旧行为失败，再在单一仓储文件内实现真正的互斥写入/重置与可接管的任务所有权；若需要 DAO/schema 改动，先停下提交 SEAM REQUEST。S03-1 继续开放。

### S03-1 实现候选复核（2026-09-23）

- 窗口 D 已提交仓储实现与 5 个并发 JVM 用例；Codex 新鲜运行 `:app:testDebugUnitTest --tests "*SearchIndexRepositoryRebuildConcurrencyTest" --rerun-tasks`，退出码 0、30 个 Gradle task 执行完成。此结果仅证明现有 5 个用例通过，不代表竞态契约完整。
- **Standards：PARTIAL**。`ensureIndexedIncremental` 在进入 `try/finally` 前读取活跃书、状态、快照与进度；任一读取抛错或取消会泄漏 `currentSweepJobId`，后续扫描持续得到“已有扫描”。`indexSingleBook` 同样在计数加一后、`try/finally` 前读取进度，存在泄漏。代次增加和释放占用后，重置状态的挂起 DAO 尚未完成时新扫描即可进入前置读取，接管时序仍未封闭。
- **Spec：PARTIAL**。词条/coverage 用例只在 `indexOneBook` 全部写完后暂停，并只断言旧扫描不会继续写第二本，未覆盖重置与同一本旧批次写入、显示文清理、coverage 写入交错；取消用例可能在扫描开始前就取消，所谓坏书仅 mock `BookDao.getById`，而扫描用快照中的书，不一定触发失败。须补真正可失败的 DAO 注入和确定性屏障，验证新代次数据不被旧代次覆盖。
- S03-1 **未关闭**；下一轮由同一窗口 D 仅补生命周期所有权、重置与接管、真实写入交错测试，不运行真机、不扩展 DAO/schema/Worker。若局部方案无法闭合，先提交 SEAM REQUEST。

### S03-1 修正候选二次复核（2026-09-23）

- 窗口 D 提交 `agent-stage-l-search-generation-correction.md` 与仓储/测试修订。Codex 新鲜运行定向 JVM `--rerun-tasks`：BUILD SUCCESSFUL，XML 为 11 tests / 0 failures / 0 errors / 0 skipped；编译器同时明确警告测试第 417、540 行条件恒为 true。未运行真机或全量门禁。
- **Standards：PARTIAL**。前置读取已进入 `finally`，计数/所有权泄漏主路径得到修复；但两条“后续扫描可启动”测试只断言非空的 `IndexSweepResult != null`，不能区分真实执行与 `PROGRESSED` 忙碌返回。报告列出的部分测试名与实际文件不符，不能据报告清单扩大测试覆盖结论。
- **Spec：PARTIAL**。扫描先 CAS 取得所有权、再于锁外读取 `currentGeneration`（仓储第 344–349 行）；重建可在两步之间接管并递增代次，扫描于是拿到新代次却在重置 DAO 完成前读取旧游标。测试只在重建已占有后启动扫描，未覆盖该交错。tokenizer 版本清旧索引路径（第 359–362 行，`wipeAllTermsForAllBooks`）也没有代次/互斥保护，可在重建后清掉新代次 term/coverage。词条测试在写前拦截较前版进步，但用 `term.contains("正文预览")` 判别分词结果不可靠，且仍未断言 coverage/display 晚到写入。
- S03-1 继续开放。下一轮只修上述所有权/代次原子接缝及旧批次数据保护，并把测试改成可观察 DAO 调用与最终行状态的确定性断言；不据 11/11 宣称全量重建竞态已经关闭。

### S03-1 原子协议候选复核（2026-09-23）

- 窗口 D 提交 `agent-stage-m-search-generation-atomic-protocol.md`。Codex 新鲜执行并发定向 JVM `--rerun-tasks`：BUILD SUCCESSFUL，XML 为 15 tests / 0 failures / 0 errors / 0 skipped；本轮未运行真机或全量门禁。
- **Standards：PARTIAL**。报告把两个 `UncompletedCoroutinesError` 红灯超时写成具体竞态的“确证”，该错误只能证明原测试未完成，不能单独定位所称根因；绿灯与实际 XML 一致。测试专用 `testHookAfterAcquireOwnership` 位于取得所有权后的 `try/finally` 之外，若钩子取消/抛错会泄漏所有权，宜移入保护范围。
- **Spec：PARTIAL**。单次重建的占权/代次绑定和旧 term、coverage、display、tokenizer 清理竞态已有定向覆盖。但 `beginRebuild` 在获取 `sweepLock` 前无条件覆盖当前重建所有权；两次并发重建时，后一次若在重置 DAO 失败并回滚，会把 `isRebuilding` 设为 false、释放占用，即使前一次尚未完成其重置。此间新扫描可以取得代次并读旧游标；前一次之后重置也不会让该扫描失效。现有 15 项只测单次重建，没有覆盖“前一次暂停于 begin 后 / 后一次失败 / 扫描抢入”。
- S03-1 仍开放。下一轮只序列化并发重建的 begin→状态重置→成功/失败收尾，补确定性双重建失败屏障；不扩大 DAO/schema/Worker/设备范围。

### S03-1 双重建修正复核（2026-09-23）

- 本轮实际产品差异：`rebuildLock.withLock` 包住每次重建的 begin→状态重置→成功/失败收尾；扫描占权后测试钩子已纳入 `try/finally`。新增双重建取消、后次重置失败及钩子异常/取消测试。约定的独立 stage-n 报告未出现，以下以源码和 Codex 新鲜验证为准。
- Codex 新鲜运行 `:app:testDebugUnitTest --tests "*SearchIndexRepositoryRebuildConcurrencyTest" --rerun-tasks`，退出码 0，XML：19 tests / 0 failures / 0 errors / 0 skipped。构建有 Gradle Metaspace 偏紧提示但本次退出成功；未运行全量 JVM、Lint、APK、真实 Room 或设备。
- **Standards：本片无新增阻断**；测试钩子是生产仓储内的 `internal` 可变字段，保留维护性 P3，后续可评估测试注入边界，不在本片继续返工。**Spec：单进程内当前要求通过**：双重建由独立 mutex 串行，失败不能覆盖前一重建占用；旧扫描 term/coverage/display、tokenizer 清理及游标保护有定向测试。
- S03-1 **代码与 JVM 范围关闭**，不是设备/真实 Room/发布验收。下一步交独立验收窗口，只用隔离 fixture 检查真实 Room 和安全竖屏设备路径；不得在用户现有书库触发全量重建。若无法安全隔离，记录为未覆盖并停止该设备动作。

### S03-1 独立验收证据待取得（2026-09-23）

- 用户报告 WorkBuddy 已完成验收；Codex 查验恢复仓库 `docs/qa/`、`docs/coordination/`、`androidTest` 最近新增文件及当前测试结果，尚未找到本轮独立验收报告、真实 Room XML、设备/安装原始日志或状态恢复记录。当前 `testDebugUnitTest` 仍为 Codex 上一轮的 19 项定向 JVM 结果，不能冒充 WorkBuddy 验收。
- S03-1 仍维持“代码与 JVM 范围关闭、独立真实 Room/设备验收未核实”。待用户提供 WorkBuddy 报告正文或准确路径后核对；在此之前不把 S03 或 Android 项目记为正式验收通过。

### S03-1 WorkBuddy 截图补证（2026-09-23）

- 用户随后提供 WorkBuddy 验收报告的四张截图；Codex 已保存在 `docs/qa/evidence/2026-09-23-s03-1-independent-acceptance/`，并写入 [截图回读摘要](../../../qa/2026-09/2026-09-23-s03-1-independent-acceptance-screenshot-review.md)。前一条“未找到报告”只描述截图提供前的仓库状态，现由本条更新。
- 截图报告独立验收 **PARTIAL**：并发 JVM 19/19、相关搜索 40/40、Lint 0 errors/9 warnings、Debug/androidTest APK 构建与脚本 `CONFIRM_LOOP` 安装均通过；真机为 `c49ac6cf` 竖屏，亮屏设置前后均为 3。相关搜索 40 项 XML 和 APK 大小在当前工作区可回读；并发 19 项 XML 已被后续测试覆盖，设备/安装原始日志未取得，其余数字仅据截图转述。
- WorkBuddy 发现没有专用隔离 in-memory Room fixture，正确停止了可能触及用户现有书库的重建动作；真实 Room/设备重建路径 **UNCOVERED**。下一步先补隔离仪器化测试并审查安全边界，再交独立真机定向验收；不要求重复已完成的安装/JVM 工作，也不把 PARTIAL 升为 PASS。

### S03-1 真实书库验收授权更新（2026-09-23）

- 用户随后明确授权在**本人现有真实书库**上测试 S03-1，以发现大书库性能与真实数据兼容问题；这取代上条“不得在用户书库重建”的后续测试限制，但不改写 WorkBuddy 上一轮依当时规则停止测试的正确性。
- 下一轮可对真实书库执行一次用户可达的全量搜索索引重建，但必须先建立可恢复的一致性备份（包含数据库 WAL/SHM、相关应用数据，明确外部书籍来源边界）、校验备份并记录恢复方案；无法证明可恢复则停止变更性测试并报告阻碍。禁止 `pm clear`、删除/改名书籍、批量改阅读进度或人为反复打断重建；发现异常先停，不自动覆盖恢复用户数据。
- 真实书库验收与确定性并发测试互补，不能单靠一次自然运行证明竞态不存在。报告与证据必须去除具体测试书名/正文和敏感路径；私有数据备份不得放进仓库或提交。独立报告固定保存到 `docs/qa/2026-09/2026-09-23-s03-1-real-library-device-acceptance.md`，其摘要与结论再由 Codex 复核。

### S03-1 真实书库验收报告复核（2026-09-23）

- WorkBuddy 已按指定位置写入 [真实书库报告](../../../qa/2026-09/2026-09-23-s03-1-real-library-device-acceptance.md)与截图。Codex 只读回读：一次重建从 6/11 到 11/11，约 13 分 45 秒；私有备份 tar 存在、大小与 SHA-256 符合报告，且可列出 DB/WAL/SHM；未操作设备或演练恢复。详细独立裁决见 [Codex 回读](../../../qa/2026-09/2026-09-23-s03-1-real-library-codex-review.md)。
- 报告的“非索引数据 100% 不变”目前只由表行数支持，不能证明逐行内容、阅读点均未变。`11/11` 为书级处理完成，报告同时记录 **10 full + 1 partial**，搜索页面也提示 1 本部分索引，不能宣称全部正文完整覆盖。真机自然跑通不验证极端 SQLite 锁争用、断电或低 API 参数上限。
- 证据 README 的“截图均已去隐私化”与至少一张搜索截图显示具体书名矛盾；原始截图未删改，已在 README 标记不得暂存/提交/上传。S03-1 真实书库自然路径裁决 **PASS WITH LIMITATIONS**，下一步仅离线做非索引核心表逐行/关键字段摘要比较和证据匿名化，不重复手机重建。

### S03-1 离线对账与脱敏补证复核（2026-09-23）

- WorkBuddy 已完成[离线对账报告](../../../qa/2026-09/2026-09-23-s03-1-real-library-offline-reconciliation.md)和七张匿名副本。Codex 回读 JSON：SQLite 前后 integrity/quick check 均为 ok、外键零违规、schema v15；核心非索引表逐行摘要一致，仅 `reader_page_index` 增 3 行。后包只含数据库，因此 DataStore 和源文件一致性仍未覆盖。
- Codex 新鲜 SHA-256 发现后包报告哈希有误：实际为 `8C48ED61B923C40D19A511586F37ACAF508A8FB1245DDE4450D6FD2BFAB3EA96`；报告所称 `mode=ro` 与脚本普通 `sqlite3.connect` 不符。两处已做带归因修正，详见[Codex 追加复核](../../../qa/2026-09/2026-09-23-s03-1-real-library-codex-review.md)。
- Codex 目视检查匿名截图 05–07，无可见真实书名；原图仍在仓库目录内、不得暂存或提交。S03-1 自然重建路径仍为 **PASS WITH LIMITATIONS**（10 full + 1 partial）；不扩大成发布级全通过，也不重复手机重建。

### S04 独立验收复核（2026-09-23）

- WorkBuddy 已按指定位置提交[独立验收报告](../../../qa/2026-09/2026-09-23-s04-source-refresh-independent-acceptance.md)，最终自判 **PARTIAL**。两张脱敏截图支持真机书架 11 本及“无来源更新”反馈；用户真实来源不可改写，故确认/取消、正文替换后单书索引、Provider 异常真机路径均未覆盖。详见[Codex 回读](../../../qa/2026-09/2026-09-23-s04-source-refresh-codex-review.md)。
- 报告称四类 JVM 定向 64/64 通过；当前产物仅可回读协调器 6/6 XML，其他分次运行 XML 不在工作区。APK SHA-256 与报告一致，但原报告大小多记 60 字节，已更正；安装原始日志缺失，不能独立确认脚本自动点击。报告中的具体外部来源目录已去除。S04 代码/JVM 历史结论保留，端到端真机仍 **PARTIAL**；下一轮先建立可证明隔离的测试书来源。

### S04 隔离来源真机验收复核（2026-09-23）

- WorkBuddy 已建立隔离测试 TXT 并提交[端到端报告](../../../qa/2026-09/2026-09-23-s04-isolated-source-refresh-device-acceptance.md)：确认/取消 UI、V3 正文更新、来源引用 hash 推进、测试书索引新词落盘；原有 11 本所选核心字段摘要前后相同。Codex 目视 03–05 截图，并只读回读私有 V1/V3 文件 MD5、阶段数据库及原有 11 本选定字段哈希，详见[独立回读](../../../qa/2026-09/2026-09-23-s04-isolated-source-refresh-codex-review.md)。
- **P2 阻断**：确认后测试书 `books.size` 为 419，但 `books.content_hash` 仍是 V1；`library_source_refs.content_hash` 已为 V3。`reselectPlainText` 更新 size 时继承旧 Book hash，可能影响去重和同步输出；原报告 PASS 收窄为 **PARTIAL**。只检查确认前文件长度不证明字节未变，亦不证明崩溃原子性。报告中的用户私有来源父目录已脱敏、虚构的异常测试类引用已纠正。隔离测试书与来源留在手机，未清理。
- 下一步只修 `ShelfImporter` 小文件刷新后的 Book/source hash 一致性并补定向测试，然后用现存隔离测试书做最小真机复验；不在原有书上试验，不重复无关 Gradle/安装门禁。大文件/EPUB/Provider 故障与多书批量需各自限定未覆盖范围。

### S04 Book hash 定向修复复核（2026-09-23）

- Zcode 已在 `ShelfImporter.kt` 的 TXT/MD 重选路径增加内部新副本哈希，写入 `books.content_hash`；>32 MiB 清旧 hash。新增 4 项定向测试；Codex 回读现存 XML 为 36/36、0 failures/errors/skipped，时间晚于源码修改；两文件 `git diff --check` 为 0。详见[Codex 双轴回读](../../../qa/2026-09/2026-09-23-s04-book-hash-fix-codex-review.md)。
- **代码/JVM 候选接受，S04 整体仍 PARTIAL**：Zcode 未取得旧代码失败的独立 Red Phase，未运行新 APK 真机；大文件清旧 hash 无专项断言，EPUB 同类旧 hash 风险未修，Book/source 失败原子性也未由本片证明。下一步 WorkBuddy 只用现存隔离 TXT 做 V4 最小真机复验，核对 Book/source hash 和原有书籍选定字段；不触及原有来源文件。

### S04 Book hash V4 真机复验回读（2026-09-23）

- WorkBuddy 提交[定向复验报告](../../../qa/2026-09/2026-09-23-s04-book-hash-fix-device-retest.md)。Codex 新鲜校验 APK 与 V4 测试文件哈希，查询私有前后 SQLite+WAL 副本：测试书 Book hash 从 V1、source ref hash 从 V3 同步推进到 V4，二者等于 V4 文件 MD5；原文索引 full 1/1，新英文测试词 0→1、旧词 1→0。11 本原书在四表既定选取列前后相同；脱敏截图显示测试书“可更新”角标消失。详见[Codex 回读](../../../qa/2026-09/2026-09-23-s04-book-hash-fix-device-codex-review.md)。
- **TXT 小文件真机自然路径 PASS，S04 父项仍 PARTIAL。** 安装脚本/内部文件 md5sum/设备设置只据 WorkBuddy 报告，原始日志或内部文件副本未供独立回读；MD 真机、EPUB 同类旧 hash、大文件及系统 Provider 故障未由本轮覆盖。测试书与来源仍留在手机，未经决定不清理。下一步优先独立处理 EPUB 同类问题，不重复 V4 验收。

### S04 EPUB Book hash 修复候选复核（2026-09-23）

- Zcode stage-p 在 `EpubRepository.replaceStoredEpub` 中改为新内部副本解析后计算 Book hash，>32 MiB 清旧 hash；新增 3 项 JVM 用例。Codex 新鲜运行定向测试 3/3、退出码 0；`ShelfImporterTest` 36/36 XML 为较早 Zcode 产物。报告称旧代码红灯 2/3，但原 XML 已被覆盖，不能独立回读。详见[Codex 双轴回读](../../../qa/2026-09/2026-09-23-s04-epub-book-hash-codex-review.md)。
- **暂不放行真机，S04 仍 PARTIAL**：新 `computeFileHash` 对 ≤32 MiB 文件读取失败返回 null，随后仍可能写 Book、删备份并返回成功；报告要求的失败保护不成立。失败测试还会捕获自身 `fail` 的 AssertionError、仅比较旧副本长度且未断言阅读记录，不能证明替换后回滚。下一轮仅收敛哈希 I/O 异常与测试真伪，再决定 EPUB 隔离真机复验。

### 夜间总账对账追加裁决（2026-09-24，窗口 D，仅文档）

完整「旧状态 → 证据 → 正确状态 → 仍缺什么」对照表、并行/串行安排与所有权冲突见
[2026-09-24 夜间总账对账](2026-09-24-overnight-ledger-reconciliation.md)。本节只登记裁决，不改写上方任何历史条目。

- **总账尾部确实滞后**：§3 父切片表 S04 行仍写「已关闭（当前代码与 JVM 范围）… 58/58」，与本页 2026-09-23 起各节的 **PARTIAL** 结论相互矛盾。**以本页尾部各节为准**：S04 父项为 PARTIAL；该表行的「已关闭」措辞在此登记为过期，不做删除或改写，留待 Codex 统一修订。
- **S04 stage-q 候选存在且已落地到工作树**：`EpubRepository.kt:329-343` 现为 `internal fun computeFileHash` + `require(len > 0L)` 且不再 `runCatching` 吞异常；`EpubRepositoryReplaceTest.kt` 现有 **5** 个 `@Test`（未跟踪）。该修正直接针对上一节两条 P2。**裁决：代码/JVM 候选待 Codex 复核，S04 仍 PARTIAL。** 全仓最后一份 S04 的 Codex 回读是 `2026-09-23-s04-epub-book-hash-codex-review.md`（针对 stage-p），**不存在 stage-q 的 Codex 复核**；其 red/green XML 与 `ShelfImporterTest` 回归 XML 均留档在仓库外，工作树内不可回读。
- **跨报告测试数字冲突登记**：`ShelfImporterTest` 现为 **39** 个 `@Test`（stage-q 报 36/36 已过期，差异来自 B 在 `ShelfImporter` 上的并行改动）；`CraSliderTest` 现为 **8** 个 `@Test`（建档报告 6 条、统一构建门禁记 6/6 均为更早批次）。其 8/8 的定向 XML 曾在 09-24 01:01 存在于工作树，**现已被窗口 C 03:01 的定向运行覆盖**（`build/test-results/testDebugUnitTest/` 当前只剩 `PendingTurnQueueTest` 一份 XML）⇒ 8/8 目前**只有报告口径、工作树内不可回读**。**旧数字不得沿用为当前结论；共享构建目录会覆盖 JVM 结果 XML，需要留证的运行必须当场把 XML 复制或哈希登记。**
- **C 的 S06-⑤/⑥ 不得关单，且当前工作树有 C 在途的 JVM 红**：`PageTurner.kt` 工作树 mtime **2026-09-24 02:57**、`git diff` **+312/−89**，队列 API 已改为新文件 `feature/reader/pager/PendingTurnQueue.kt`（`enqueue/poll/requeueFront/clear`）+ `turnEpoch` + `TurnGate:867`；C 报告（01:04）所记的 `PENDING_TURN_CAPACITY`、`enqueueTurnRequest` **在当前源码中已不存在**，且该报告自称「PageTurner 一字未动」。**快照 03:01:37**：新建的 `PendingTurnQueueTest` 定向 JVM 结果为 `tests="11" failures="2" errors="0"`（XML sha256 前缀 `e5c59f403b7e9e42`）⇒ 快照时刻队列契约尚未收敛。**状态：并行进行中 / 报告与源码不一致 / 待 Codex 复核。** 由此推论：`compileDebugAndroidTestKotlin`（01:01）、test APK 打包（01:51）、`app-debug.apk`（09-23 22:58）三条门禁**全部早于 02:57 源码变更，对当前 PageTurner 一律失效**；当前源码能否编译为未知（本轮不跑 Gradle）。本条只是窗口 D 的只读观测快照，不构成对 C 代码质量的裁决。

- **C/D 真机 9 项仍 0 项取得结果**：D 4 项中 **1 项 BLOCKED**（`tapNearLeftEdgeYieldsHighValueInRtl` 已发起、`STATUS_CODE:1` 有、`finished` 无、外部超时 180 s 退出码 **124**）+ **3 项未运行**；C 5 项**全部未运行**。**既非通过也非失败。** 本轮首次取得**本轮自身**的宿主拦截实证（`01:54:44` 未截断 logcat：`MIUILOG- Permission Denied Activity`、`Abort background activity starts from 10949`、`result code=102`，前台仍为 `com.miui.home`），同时对照组 `AppDatabaseMigrationTest` **OK (23 tests)、1.125 s、退出码 0**（同设备同会话同 test APK 同 runner）⇒ 阻塞不得归因于通道。
- **不得把口头「完成」/编译通过/他批测试通过改写成正式验收 PASS**：`2026-09-24-compose-test-host-investigation.md` 的结论是「**暂无可信绕过方案**」（4 候选中 3 个机制同源，Robolectric 不满足真实手机要求）。因此 SR-ENV-1 的三选一（换真机 / CI / 明确接受本机不可验并改 JVM 替代门禁）**仍未裁定**，在裁定前 C/D 的 9 项没有关单路径。
- **B 的 S05-④ 保持 PARTIAL**：B-1 原始记录全程未点击「查看」（`tap 1073 2410` 缺失），B-2 的 fallback 与「按最近导入排第一」两种解释在现有截图/dump 下不可区分 ⇒ **不可判定**；现存证据无可补，需重新真机执行。JVM 侧 39/39 + 15/15 属统一构建门禁的记录，不等同于真机验收。
- **RTL 结论口径重申**：09-23 的 `d_rtl_*` 截图内容区与 LTR 差分 **0.0000%**，属「未镜像」的反证，**不得记作 RTL PASS**；`debug.force_rtl` 为何未生效仍**未证实**（全程只有 `settings put/get`，从未 `getprop`，从未取过带 `ldrtl/ldltr` 的配置输出）。
- **隐私与工件遗留（未处置，登记）**：仓库根 `dump3.xml`–`dump6.xml` 含真实藏书目录名（S04 轮遗留、未跟踪）；`docs/archive/mobile-legacy/` 两处历史文档含真实父路径（已提交）；`/sdcard` 根目录通配清理影响范围不可界定。三项均需 Codex 决策，本轮未删除、未改动、未在文档中复述具体名称或路径。
- **设备身份记录冲突登记**：历史报告记 `22011211C`，09-24 实测 `ro.product.model=22081212C`。本轮不核实、不推断，登记为 SR-DOC-1 待决。
- 本轮（窗口 D）**只新增文档**：本节为末尾追加，未改写上方任何条目；配套对账报告见本节开头链接的文件。未改任何源码/测试，未运行 Gradle、ADB、手机，未做 git 写操作；HEAD 仍 `5aed5939600e491843e3c44453921bd6273a233c`，staged 0 项。

### 最终快照追加（2026-09-24 夜间复核对账，窗口 D · 只读；上方各节一字未删）

详细依据见 [夜间结果勘误](../../legacy-stages/2026-09/2026-09-24-overnight-results-corrigendum.md) 与
[Stage Q 独立审查的追加勘误节](../../../qa/2026-09/2026-09-24-overnight-s04-epub-stage-q-review.md)（E1–E7）。

- **更正上一条 C 登记的快照口径**：`PendingTurnQueueTest` 现为 **11/11 通过**（Codex 新鲜复跑；本轮亲自回读
  `android/app/build/test-results/testDebugUnitTest/TEST-…PendingTurnQueueTest.xml`，mtime 09-24 **03:03**，
  `tests="11" skipped="0" failures="0" errors="0"`）。上方「03:01:37 快照 failures="2"」那条记录**保留不删**，
  但其性质已明确为**窗口 C 变异验证期间的中间状态，不是最终结果**。
- **`:app:compileDebugAndroidTestKotlin` 成功**（Codex 复跑报告）；产物佐证：
  `build/tmp/kotlin-classes/debugAndroidTest/…/PageTurnerHighSpeedTest.class` mtime 09-24 **03:02**，晚于 02:57 的
  `PageTurner.kt` 改动。由此上方「三条门禁对当前 PageTurner 一律失效」中的编译一条**已被 03:02 复跑取代**；
  但 `app-debug.apk`（09-23 22:58）与 test APK（09-24 01:51）**仍未包含** 02:57 后的改动，真机验收前必须重建。
- **C 的 S06-⑤/⑥ 仍不关单**：11/11 只是队列纯逻辑 JVM 结果；**5 项 instrumentation 用例仍 0 项取得运行结果**，
  真机翻页行为未验证。
- **S04 Stage Q 仍 PARTIAL**：独立审查的 P1（无 backup 时唯一副本被删）经路径复核后**撤回原触发链、降级为未经证实**
  （`validatedFile` 来自 `EpubParser.kt:57` 的 `epub_<uriHash>` 命名，与 `EpubRepository.kt:85` 的 `<stored.id>.epub`
  是两个独立文件）；但复核在同一段代码发现**更宽且未被任何用例覆盖**的窗口：异常落在 `EpubRepository.kt:92-96`
  （staging 复制 / `:95` 长度校验）时 `:98` 尚未建立 backup，而 `catch` 无条件 `:138 target.delete()` 会删掉本次
  **未触碰、仍完好**的旧副本，`:90` 又已先删掉残留 backup ⇒ 记为 P1 候选（静态分析，未编译未运行）。
  另：`catch CancellationException 后直接抛出` 的写法会**跳过文件回滚**，**不得**作为修复建议（现状 `:145` 已传播取消）。
  Stage Q **仍无 Codex 复核**，其 red/green 与回归 XML 均在仓库外不可回读。
- **B-1 / B-2 仍 PARTIAL**（未点「查看」；fallback 与排序两种解释不可区分），需重新真机执行取可区分证据。
- **C / D 真机 Compose 用例仍 0 项取得结果**（D：1 BLOCKED + 3 未运行；C：5 未运行），
  SR-ENV-1「换真机 / CI / 接受本机不可验」**未裁定**；`compose-test-host-investigation` §0 =「暂无可信绕过方案」。
- **B / C 新一轮修复并行进行中**，尚未验收，本页不替它们下结论。
- 以上任何一项都**不得**写为验收 PASS；口头「完成」、编译成功、JVM 定向通过、其他批次结果，均不构成验收结论。
- 本轮（窗口 D）写入仅限：本节 + 上两份文档；未改源码/测试，未运行 Gradle / ADB，未接手机，无 git 写操作；
  HEAD 仍 `5aed5939600e491843e3c44453921bd6273a233c`，staged 0 项。
