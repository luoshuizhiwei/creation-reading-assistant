# 阅读主线需求与证据只读覆盖复核（联合候选期间）

- 任务编号/版本：`CRA-READING-COVERAGE-REVIEW-v1`；2026-10-04。
- 执行者：ZCode / GLM（account:zai-start-plan）。
- 性质：**只读需求与证据审核**。未构建、未测试、未 ADB/MuMu、未改产品、未改 current/TODO/总规划/其他报告、未 commit/push/reset/checkout。不读取正在生成的联合候选，不扫描全部历史与私有记忆库。
- 覆盖表工作副本：`E:\creation-reading-assistant\next-round-reading-coverage-review-v1\evidence\coverage-table.md`（与本报告同内容）。

## 任务编号/版本与方向基准

围绕既定方向复核：阅读优先、轻量灵感、深入整理属于桌面、稳定个人使用。注意：grilling 最终共享理解未答，roadmap §1.1–1.2 待校准（交接明示）——因此**需求共享理解尚未确认的部分全部保留"待确认"，本报告不自行定稿**，不把 §1.2 新增候选当需求，不把旧 backlog 当当前授权。

## 输入指纹

| 项 | 值 |
|---|---|
| 活动仓库 / HEAD（预期=实际） | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` / `e39a78da9c62f02614207cffb36969a32492eaea`（`git rev-parse` 实算一致） |
| 暂存区 | 未动；工作树含既有共享 WIP（未暂存），按交接不作为已提交输入。源码抽查证实：D 树 `ProfileHomeScreen.kt`/`ReaderProgressEffects.kt` 为 WIP 版本（2b8b3d32…/a7baa349…），≠HEAD（e295946d…/7e4b4ab8…） |
| 固定产品输入 1 | `E:\creation-reading-assistant\mdfix-review-20261003\payload32-preparation\manifest.json`，SHA `cf05ea577548de034dfbef16c612c658f60c48bdc4b62d352061a70ae7107358`；scope=32，status=`source-payload-only; not built/device accepted/applied` |
| 固定产品输入 2 | `E:\creation-reading-assistant\next-round-integration-ready-v1\android`（883 文件口径）；`final-manifest.json` SHA `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d`（实算一致） |
| 交接与指定文档 | `docs/handoff/current.md`、`docs/plans/2026-10-01-product-roadmap-realignment.md`、`docs/coordination/reviews/2026-10/2026-10-03-next-mdfix-codex-review.md`、仓库 AGENTS.md |
| 链式证据（只按交接与报告内相关链接） | 见"原始证据"节 12 份 |
| 排除 | 正在生成的联合候选未读；DSH 记忆库未读；历史全量报告未扫 |

## 所有权与改动

只读需求与证据审核者。唯一可写：`E:\creation-reading-assistant\next-round-reading-coverage-review-v1\`（execution.json、evidence/files-read-sha256.json、evidence/coverage-table.md）与本报告。零产品写入、零 Git 写入、零构建、零设备。不覆盖其他窗口修改，不向其他窗口发消息。

## 环境

Windows 11 / Git Bash；仅文件读取、`sha256sum`、`git show`/`grep` 只读抽查与 `git rev-parse`。未启动 JDK/Gradle，未连接任何设备。

## 执行与结果

### 源码抽查（对承重静态事实的直接验证，非纯转述）

1. **笔记磁贴移除注释属实**：`git show HEAD:…ProfileHomeScreen.kt` 第 189 行含「产品规划无独立笔记概念：原『笔记』磁贴改为灵感中心直达」；HEAD 与冻结 E 该文件同 SHA（e295946d…），precheck-v2 §2 的"文件=HEAD"判定复核成立。
2. **ReaderProgressEffects 三版本分层属实**：D 树 WIP `a7baa349…`（不可作输入）；基座 25 路径/HEAD `7e4b4ab8…`；MD 修复候选 `95f2e20f…` = 32 路径 payload `candidate_sha256`。**该文件属 32 路径变更集 → 基于旧版本的旅程 v2 回源/进度证据不自动绑定新联合 APK**（这是本复核最重要的证据绑定结论）。
3. `pendingSourceLocatorJsonState`/`pendingHighlightId` 在源码中存在（D 树与冻结 E 均可检），与 mdfix 报告的 canonical/SE3 延迟定位叙述一致。

### 覆盖表（证据层级标注：源码存在 / JVM通过 / MuMu实测 / 需求未定；MuMu 均为模拟器，非实体手机）

#### 行1：TXT/EPUB/Markdown 稳定阅读（打开/翻页/退出/重进）

| 维度 | 内容 |
|---|---|
| 需求来源 | roadmap §1.1 日常阅读链；G2 阅读内核域 |
| 实现路径 | ReaderScreen/ReaderViewModel/ReaderDocumentLoader/EpubRepository；进度写回与回源宿主 ReaderProgressEffects（SE3/SE4，属 32 路径变更集） |
| 实际证据 | 源码存在=三格式阅读内核在冻结 E 完整存在。MuMu实测=旅程v2（qahostjourneyv2）三格式真实 UI 打开/翻页/选区/强停/冷启动全链（15 方法 13 PASS+1 FAIL+1 OTHER）；mdfix-v1（qahostmdfixv1）MD 重开/跳转/退出保存（mdfix_20 offset=1036、65.08% 回写）。历史 stage E/s05/s06/s12 属旧候选 |
| 证据适用候选 | qahostjourneyv2（=25 路径基座，ReaderProgressEffects=HEAD）；qahostmdfixv1（基座+3MD 路径=32 路径内 3MD 同 SHA）。均非新联合 APK |
| 已通过范围 | MuMu实测：三格式打开/阅读/保存/冷启动读回；MD 单章非起点回源+退出进度回写；TXT/EPUB 回源（EPUB 含跨章） |
| 未覆盖范围 | 新联合 APK 上 ReaderProgressEffects 变更后 TXT/EPUB 进度写回与回源未设备复验（JVM通过=17/17 内 3 条 EPUB/plain 守卫+ReaderProgressNavigationTest 7/0 支持）；重进自动续读精度当前链未单列断言；实体手机/MIUI/TalkBack/R8 未验 |
| 当前是否阻断 | 否；但联合验收必须显式处理证据绑定（见输出1-a） |
| 建议下一动作 | 联合 MuMu 加 ≥1 条 TXT 非起点保存→强停冷读→回源 smoke，或在联合验收报告显式记录"按 JVM 守卫复用 TXT/EPUB"口径；二选一落报告 |

#### 行2：高亮保存、个人批注、冷启动读回

| 维度 | 内容 |
|---|---|
| 需求来源 | roadmap §1.1 阅读取材链；笔记与创作素材域（已授权核心特色） |
| 实现路径 | 选区→highlights 表（个人批注=highlights.note 字段）；AnnotationModels（excerpt/annotation 分区）；Room 持久化 |
| 实际证据 | MuMu实测=旅程v2：三格式高亮保存 locator 非起点（TXT 2191/73.91%、EPUB ci=2 co=108 第3章、MD 1270）+冷读值一致；「编辑高亮笔记」真实键入保存，落点 **highlights.note（notes 表=0）**+注释冷读 PASS；mdfix-v1 MD 高亮冷读逐字一致（offset=1247）。**highlights.note 是高亮行批注字段，不构成独立 notes 行验收（任务约束）** |
| 证据适用候选 | qahostjourneyv2 / qahostmdfixv1；非联合 APK |
| 已通过范围 | MuMu实测：三格式高亮+个人批注保存与冷启动读回；MD 回源（修复后） |
| 未覆盖范围 | 独立 notes 行（摘录笔记/书签）无设备证据且入口未出现（notes 表始终 0）；note typed 目标回源未单列；多条/跨章高亮、颜色变体未覆盖 |
| 当前是否阻断 | 否；独立 notes 行归属与笔记页入口裁决绑定 |
| 建议下一动作 | 联合验收如涉批注按旅程 v2 口径记录落点；独立笔记行并入 §2 裁决 |

#### 行3：书内回源及跨章定位

| 维度 | 内容 |
|---|---|
| 需求来源 | roadmap §1.1 阅读取材链（精确回到原文）；§1.2 带上下文的回源（已授权主体） |
| 实现路径 | ReaderNotesSheet→onPendingHighlightIdChange→ReaderProgressEffects（pendingHighlightId 消费 :528-546）；MD 修复后入 canonical 通道（pendingSourceLocatorJsonState→SE3 延迟定位，跨章保留 source 请求、加载后 apply focus） |
| 实际证据 | MuMu实测=旅程v2：TXT 24.4%→73.9% 标记可见；**EPUB 跨章**第4章 78%→第3章 44%（ci=2 断言）。mdfix-v1：MD 单章 0.0%→68.2% 标记可见+DB 对账。JVM通过=ReaderAnnotationNavigationTest 红 10/7→绿 17/17（含 deferred 移交断言、EPUB/plain 守卫） |
| 证据适用候选 | TXT/EPUB=qahostjourneyv2（旧 effect）；MD=qahostmdfixv1（3MD=32 路径同 SHA）。均非联合 APK |
| 已通过范围 | MuMu实测：TXT/EPUB 书内回源（EPUB 含跨章）；MD 单章非起点回源。JVM通过 17/17。**MD 单章通过≠跨章通过（任务约束）** |
| 未覆盖范围 | **MD 跨章回源未设备实测**（夹具单章；SE3 跨章仅 TXT/EPUB 旧证据+JVM 断言）——handoff"补MD跨章接缝"即此；到达精度=标记可见级；失败提示可见性未截图 |
| 当前是否阻断 | 否——MD 跨章已是联合验收计划内必验项，非额外遗漏；执行时不得以单章/EPUB 旧证据/JVM 断言替代 |
| 建议下一动作 | 联合 MuMu 以 MD 跨章夹具实测 SE3 延迟定位端到端；报告记录夹具章数与"单章≠跨章"口径 |

#### 行4：正文移除后的标注保留与缺失正文保护

| 维度 | 内容 |
|---|---|
| 需求来源 | roadmap §1.1 数据保全链+书库域（移除正文、删除撤销、重新关联）；D2 合同 |
| 实现路径 | BookRepository.clearBookCache（:291 missing，书行 active，notes/highlights 不动）；软删除/撤销=同戳 deleted_at+精确恢复；物理删行主源码不存在（CASCADE 不可达）；缺失正文失败文案四格式（ReaderDocumentLoader.kt:167/:104、EpubRepository.kt:57 等） |
| 实际证据 | MuMu实测=G1-fix-accept-v3（qahostlibrary）：真实 UI 批量选择→清缓存，纯读断言指针清空/missing/文件缺失/**进度笔记保留**；删除/撤销真实 UI 5/5。**真实 UI 路径，非 API 清正文（任务约束）**。precheck-v2：删除语义三分+失败文案出处（源码存在）。JVM通过=reselect-rollback v1→v3：暂存失败阶段敏感回滚+同名 backup 文件/目录残留拒绝，红 47/4→绿 47/0 |
| 证据适用候选 | G1 证据=qahostlibrary（旅程路径文件 BookRepository/ShelfRoute 未入 32 路径变更集→可复用）；rollback 4 路径=32 路径 payload 同 SHA（JVM，设备待联合验证）；失败文案=源码层未设备复现 |
| 已通过范围 | MuMu实测：仅移除正文后标注/进度保留；删除撤销。JVM通过：回滚保护 47/0 |
| 未覆盖范围 | 缺失正文失败文案当前链未设备复现（precheck 共同项旅程未执行）；「删除书籍行后摘录仍可读」不成立，不得列 PASS；重新关联 D2 未闭合（无内容身份校验、content_hash 不更新、missing 经导入被去重挡死、执行中取消窗口；Duplicate≠重关联） |
| 当前是否阻断 | 否——payload 不含 clearBookCache；暂存失败删旧正文 P1 已由 rollback v1–v3 修复并入 payload；失败文案属 precheck 旅程未执行，非新缺陷 |
| 建议下一动作 | 联合 MuMu 验证合流后回滚保护（已计划）；「仅移除正文+失败文案」共同项随 precheck-v2 最小旅程执行（§2 裁决后）；D2 按 C-5 产品决定后收口 |

#### 行5：跨书查找、临时查阅与独立笔记的当前归属

| 维度 | 内容 |
|---|---|
| 需求来源 | roadmap §1.1 搜索与资料查阅域+笔记域；任务要求核"当前归属"。§1.2 续读卡/专题视图等新增候选不属本行 |
| 实现路径 | 跨书回源 typed 路由（ProfileRoute.kt:256-261）与临时查阅（inspectSourceTemporarily :267、ReaderTemporaryRoute.kt，独立路由/可见性/LIFO 返回）实现存在；宿主 ReadingNotesSubPage 仅 page==NOTES 渲染；ProfileHomeScreen.kt:189 注释（本轮源码抽查证实）；跨书检索=索引/入口实现记录 |
| 实际证据 | 源码存在=precheck-v2 §2 静态可达性（本轮抽查复核）：NOTES 无任何 OpenSubPage 调用、路由可解析无人导航、两个 action 唯一 UI 宿主不可达；临时查阅不污染普通位置（canPersistNormalReadingProgress 源码支持）。JVM通过=测试类实算存在（TemporaryReadingJourneyTest 等 9 类；存在性非本轮运行）。MuMu实测=S12 属 2026-09-28 旧候选+搜索入口+route 未保存；跨书检索当前链无设备证据。需求未定=§2 裁决（退役 vs 缺口）未答，grilling 共享理解未答 |
| 证据适用候选 | 静态结论基于冻结 E（抽查证实与 HEAD 同源）；S12 属旧候选；均非联合 APK |
| 已通过范围 | 仅"源码存在+JVM 测试类存在"层；无当前链 MuMu 通过范围 |
| 未覆盖范围 | 三者均无当前候选设备验收；跨书检索全文覆盖未证；临时查阅进度保护仅源码+JVM；独立 notes 行入口未出现 |
| 当前是否阻断 | 不阻断 32 路径构建（payload 不含相关文件）；阻断 precheck-v2 最小旅程执行（分支 A/B 未裁决） |
| 建议下一动作 | Codex/用户就 §2 裁决：分支 A（退役）→旅程重定义为"书架→书籍详情+书内面板回源"并决定死代码去留；分支 B（缺口）→先恢复入口再验。裁决前保持待确认，不得自行定稿、不得据此开工 |

## 输出1：当前联合验收必须检查的遗漏（或明确无额外阻断）

**结论：没有发现新的功能缺口级阻断。** 32 路径 payload（25 基座+4 回滚+3 MD）与既定联合验收计划（回滚保护、Markdown 非起点、跨章定位接缝、受影响 Profile/同步链复用）覆盖了当前已证缺陷面；暂存失败删旧正文 P1 的修复已在 payload 内（JVM 47/0）；G1 移除正文证据的路径文件未入变更集，按复用规则可沿用。以下为联合验收**必须显式处理**的检查点（均为证据口径问题，不是新增需求）：

1. **(a) ReaderProgressEffects.kt 属 32 路径变更集**：旅程 v2 的 TXT/EPUB 回源/进度写回证据绑定旧版本 effect（7e4b4ab8…），**不自动绑定新联合 APK**。联合 MuMu 要么加 ≥1 条 TXT 非起点保存→强停冷读→回源 smoke（复用旅程 v2 驱动，成本低），要么在 CRA-INTEGRATION-BUILD-ACCEPT-v2 报告**显式记录**"以 JVM 守卫（EPUB/plain 3 守卫红阶段即过+ReaderProgressNavigationTest 7/0）复用"的采纳口径。二选一必须落报告，不能默认沿用。
2. **(b) "跨章定位接缝"的判据必须是 MD 跨章实测**（SE3 延迟定位端到端，夹具≥2 章）；不得以 MD 单章通过、EPUB 旧证据或 JVM deferred 断言替代。此点已在计划内，列出仅为固化判据。
3. **(c) 验收纪律**（上批已留教训，非新范围）：联合 APK 冻结=安装=回读 SHA 逐位一致；临时包后缀不入 payload（永久 `.qahost`）；恢复账明确 8 设置/临时文件/APK/DB/文件/映射（mdfix 批第 8 项屏幕超时未入基线的教训）；报告逐条记录"复用 vs 重锚"证据口径，不得将历史候选证据自动绑定新联合 APK。
4. **(d) 批注落点口径**：个人批注=highlights.note；独立 notes 行未出现且入口待裁决——联合验收如涉批注，按旅程 v2 口径如实记录，不把 highlights.note 写成独立笔记行通过。

## 输出2：可复用、无需重跑的证据

- **G1-fix-accept-v3 全部 5 旅程**（搁置/移除正文/删除撤销/动态视图保存/冷启动）：其旅程路径文件（BookRepository、ShelfRoute 等）未入 32 路径变更集，绑定 qahostlibrary 的证据按复用规则沿用。
- **integration-mumu-accept v1+v2 的 Profile/同步 9 合同**：25 基座路径在 32 路径中不变；v1 最终 test 回读缺口已由 v2 时间线落盘、v2 批冻结=安装=回读成立（按 handoff"门控补验2/2"口径采纳）。
- **JVM 证据**：reselect-rollback-fix-v3 的 47/0（=payload 4 回滚路径同 SHA）、mdfix-v1 的 17/17（=payload 3MD 路径同 SHA）、integration-ready-v1 定向 153（=25 基座）。
- **precheck-v2 静态结论**：SHA 实算、接线复核、删除语义三分、失败文案出处——可直接作为最小旅程合同，无需重查。
- 既定复用维持：J1-v3 冻结六文件 6/6、Room 4/4、W2 七类 7/7。
- **不可复用/需重锚**：旅程 v2 与 mdfix-v1 的设备 PASS 各自绑定候选后缀包（qahostjourneyv2/qahostmdfixv1），不自动转嫁给新联合 APK——联合验收计划本就安排 MD 非起点与跨章接缝实测，按计划执行即可。

## 输出3：联合验收之后最值得推进的 3 项已有待办

1. **CRA-INTEGRATION-BUILD-ACCEPT-v2（32 路径联合构建+限定 MuMu）**——依赖：32 路径源码包已备（32/32 SHA 核定）、MuMu 独占、单 owner；优先级最高，是当前闭环本身。交付：定向合流回归→构建→隔离新 UID MuMu（回滚保护、MD 非起点、MD 跨章接缝，建议含输出1-a 的 TXT smoke 或复用口径决定）→8 项恢复账。
2. **precheck-v2 最小旅程（三格式各一遍+共同项"仅移除正文+失败文案"）**——依赖：§2 笔记入口裁决（Codex/用户，分支 A/B 二选一）；MuMu 与任务 1 串行；优先级第二。它是"阅读→取材→回源"主线的首个完整旅程验收（roadmap 下一主线），分支 A 时同时覆盖行 1/2/4 的待执行共同项。
3. **D2 重新关联收口**——依赖：relink-v1 C-5 四项产品决定（异内容同格式处理/是否匹配预览页/EPUB 元数据/是否持久中断状态）先行；之后最小实施（repairFileTyped 三态内容判定+content_hash 回填，复用 shelfMatch 语义参照）+5 JVM+3 MuMu；优先级第三。数据保全链已授权未闭合部分；与任务 1/2 文件互斥（ShelfImporter 需新精确清单）。

## 输出4：暂缓或需产品选择的项目（不据此开工）

- **§2 笔记页入口裁决**（有意退役 vs 接线缺口）——未裁决前 precheck 旅程不开工；若判退役，需一并决定 ReadingNotesSubPage 与跨书分支死代码去留。
- **D2 C-5 四项产品决定**——relink-v1 明确"本任务不替产品选"。
- **跨书检索全文覆盖确认**——当前仅索引/入口实现记录，全文覆盖未证；待旅程安排，不列为阻断。
- **独立 notes 行（书签/摘录笔记）入口**——随 §2 裁决。
- **note typed 目标回源单列、失败提示截图可见性**——随 precheck 最小旅程执行，不单开任务。
- **§1.2 新增候选**（续读卡/专题视图/技法卡/AI 分析/分享收集等）与实体硬件/MIUI/TalkBack/R8/正式签名/升级/F/真实桌面 LAN——维持既定暂缓边界，需单独范围授权。

## 原始证据

本轮实读关键文件 SHA 全表：`E:\creation-reading-assistant\next-round-reading-coverage-review-v1\evidence\files-read-sha256.json`。要点：

| 文件 | SHA-256（前16） |
|---|---|
| docs/handoff/current.md | `9a8d912493ea3036` |
| docs/plans/2026-10-01-product-roadmap-realignment.md | `d7354a60700f02c4` |
| docs/coordination/reviews/2026-10/2026-10-03-next-mdfix-codex-review.md | `1e70f9f775b24ff9` |
| docs/coordination/dispatches/2026-10/2026-10-03-next-read-note-return-precheck-v2.md | `3543e9aef6d2120b` |
| docs/coordination/dispatches/2026-10/2026-10-03-next-md-annotation-return-fix-v1.md | `f4951a960c355341` |
| docs/qa/2026-10/2026-10-02-next-g1-fix-accept-v3.md | `3209203cc1dd22d0` |
| docs/qa/2026-10/2026-10-03-next-read-note-journey-accept-v2.md | `c6465c34ad3183e2` |
| docs/coordination/dispatches/2026-10/2026-10-02-next-integration-ready-v1.md | `a300020d14c3418c` |
| docs/coordination/dispatches/2026-10/2026-10-03-next-relink-precheck-v1.md | `e31b7e2004efff23` |
| docs/coordination/reviews/2026-10/2026-10-03-next-three-deliveries-codex-review.md | `ab480f97c5e51fcf` |
| docs/coordination/dispatches/2026-10/2026-10-03-next-reselect-rollback-fix-v3.md | `0dc144ce2505bc80` |
| docs/qa/2026-10/2026-10-03-next-integration-mumu-accept-v2.md | `c45ef0627d582d70` |
| payload32-preparation/manifest.json | `cf05ea577548de03` |
| integration-ready-v1/final-manifest.json | `a0240a395c20251f` |

链式证据来源：旅程 v2 与 mdfix-v1 的原始日志/XML/截图在各自 E 目录（`next-round-read-note-journey-accept-v2`、`next-round-md-annotation-return-fix-v1`）；G1 原始运行日志在 `g1-codex-takeover-20261002`；本复核未复制、未改写上述原件。

## 未覆盖项（本复核自身的局限）

- 未运行任何构建/测试/设备验证；全部结论为文档-指纹-静态抽查层，不构成任何产品的运行 PASS。
- 未读取正在生成的联合候选构建产物；未读 S12 等旧候选的原始工件（仅按引用报告转述其局限）。
- 源码行号引用（如 ReaderProgressEffects:528-546、clearBookCache:291）转引自 precheck-v2/relink-v1 的实算记录，本轮仅对两处最承重事实做了直接抽查（ProfileHomeScreen 注释、ReaderProgressEffects 三版本 SHA），未逐行重验其余行号。
- 未核全产品分母；本报告不给全产品百分比。各候选局部百分比沿用原报告口径，不相加。

## 恢复与副作用

- 零产品写入、零 Git 写入（HEAD 前后一致 `e39a78da…`，暂存区未动）；零构建、零 ADB/MuMu，无需设备恢复。
- 仅新建：`E:\creation-reading-assistant\next-round-reading-coverage-review-v1\`（execution.json、evidence/ 两文件）与本报告。未改 current.md、TODO、项目页、总规划、其他报告；未读其他应用私有记忆库；未发送外部消息。

## 交付状态与下一步

**已交付。** 只读覆盖表（5 行×8 列，含四类证据层级区分）、联合验收检查点（1 项证据绑定遗漏+3 项纪律固化）、可复用证据清单、3 项优先待办与暂缓清单如上。按任务边界**就此停止**：不向其他窗口自动发消息，不执行后续实施；§2 裁决与联合验收执行权在相应所有者。
