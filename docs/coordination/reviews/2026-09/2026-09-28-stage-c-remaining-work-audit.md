# C 阶段剩余项对账（2026-09-28，只读）

> **Codex 2026-09-28 回读：这是并行窗口完成前的快照，§0、§1、§2 和两轮排期不可直接当当前状态。** S05 V1 已有 420 dpi MuMu 七站报告及 Codex 限定复核；S14 M1 第一轮返修 32/32，但“日志已记、旧 dst 与完整 staging 并存”恢复分支会删除 staging 留下旧字节，且日志写入失败被吞后仍进入破坏性 rename，须继续修正。S12 V2、S14 V3、S04 V4、SR-ENV-1 V5 仍待按原清单逐项处理。15 项关闭和 12 项延期分类可作为排期候选，未经总指挥逐项裁决不得自动视为 C 退出。整体 A–E 仍 2/5=40%。

> **（Codex 复核位：留空待总指挥转交复核）**

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`。未进入/未修改原仓库。
- 性质：**只读对账**。唯一写入 = 本报告。未改源码、测试、其他文档；未运行 Gradle / ADB / MuMu；未触碰真实手机、正式书库。
- 输入：`AGENTS.md`、[交接入口](../../../handoff/current.md)、[任务账本](../../decisions/2026-09/2026-09-22-current-task-ledger.md)、[收口计划](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md) §C 退出条件、以及下列最新报告**顶部 Codex 复核**：S05 取消读完 / S05 跨页消费者 / S06 跟随系统主题（代码报告 + MuMu 验收）/ S14 postcommit 决策 / S14 crash residue 结果 / S12 marker UI 守卫 / S12 导入完成态 / S10 Picker 收敛 / B 阶段退出复核 / 阅读与平台风险增量。
- 方法与口径：① 区分**旧报告**（历史数字，仅作证据链）、**当前源码**（本轮对关键争议点做了 mtime + 内容抽查：`LocalZipBackup.kt`、`EpubRepository.replaceStoredEpub`、`ReaderProgressEffects.kt`、`BookReadiness.kt`、`ShelfModels` 消费端）与**真正的新证据**（E 盘可回读 XML/日志/截图）；② **同一根因只计一次**：S05-① 书架口径与跨页 10 站点同属「取消读完后完成态口径」一根因链（合并为一条验收 V1）；S06 主题映射与平台增量所登记的 S17 `else -> MODE_NIGHT_AUTO` 冲突同根因（S06 主题修复已落地，关闭区一条）；S04 stage-q P1 候选与 09-27 EPUB 回滚终版同根因（一条 V4）；S12-②（流式 TXT 回书首）与 S12-③（末章静默 FINISHED）是两个缺陷，分开计数但合并一条设备批 V2。
- 进度口径：**不自行改任何百分比；A–E 维持 2/5=40%（A、B 限定退出，C 未退出）**，仅 C 完整退出后由总指挥调整。

## 0. 结论先行

1. C 阶段**未退出**。剩余项：**必须修复 1 项**（S14 crash residue 归属判定）、**必须验收 5 项**（其中 2 项有前置依赖/裁决门）、**可明确延期 12 项**（均已可归类登记）、**关闭确认 15 项**（有充分证据，不重做）。
2. 三条防误读声明（总指挥特别要求）：
   - **S14 22/22 不是安全通过**：Codex 复核已判「暂不验收，须返修」——同格式命名的普通书籍文件会被 `exportZip` 过滤（导出 ZIP 缺书）或被 `importZip` 误改名；`matchBackupResidue` 对任意长数字直接 `toLong()` 可溢出。见 [S14 residue 结果报告顶部复核](../../legacy-stages/2026-09/2026-09-28-s14-crash-residue-safety-result.md)。
   - **S05 40/40 不是 UI 通过**：纯 JVM 定向结果；取消读完后的跨页 UI 行为（首页/归档/我的阅读/我的页/统计页）无 MuMu 截图与 DB 前后对账。见 [S05 跨页报告顶部复核](../../legacy-stages/2026-09/2026-09-28-s05-cross-page-finished-consumers-result.md)。
   - **S06 主题 MuMu 截图不是手机/实体机通过**：截图为 280dpi 平板导航布局；手机布局、实体手机、厂商 ROM 未验；E1 出现一次全局夜间模式意外翻转、归因未隔离（qa 复核明示不阻塞 C，但保留边界）。见 [S06 MuMu 验收顶部复核](../../../qa/2026-09/2026-09-28-s06-mumu-follow-system-acceptance.md)。
3. 两轮以内可收口 C 的并行组合、互斥所有权与退出检查单见 §5–§7。核心路径：第 1 轮「S14 返修（唯一 Gradle）∥ S05+S12 设备验收（唯一 MuMu，用预构建 APK）」，第 2 轮「S14 返修后 MuMu 复跑（Gradle+设备同一所有者串行）∥ 文档复核与退出核对（零构建）」。

## 1. 必须修复（1 项）

### M1 · S14 crash residue 归属判定不可靠（含溢出缺陷）

- **行为/缺陷**：`matchStagingResidue` / `matchBackupResidue` 仅凭文件名归属：① 完全同格式的普通书籍文件（`X.restore-<≥13位数字>.staging` / `X.restore-backup-<≥13位数字>`）在 `exportZip` 被过滤（**导出 ZIP 可缺书**——数据丢失，推翻上轮「零删除即无丢失」论断），在 `importZip` 还可能于目标缺失时被误改名；② 数字后缀任意长直接 `.toLong()`，超长可抛 `NumberFormatException` 使导入中断。
- **负责文件**：`android/app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt`、`android/app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt`（两文件为 S14 窗口独占所有权，返修不扩权）。
- **最新证据**：[S14 crash residue 结果报告顶部 Codex 复核（2026-09-28，「暂不验收，须返修」）](../../legacy-stages/2026-09/2026-09-28-s14-crash-residue-safety-result.md)；红/绿 XML 在 `E:\creation-reading-assistant\zcode-s14-residue-jvm-20260928\`（仅证明已测夹具）。
- **依赖**：无；可立即开始。阻塞 V3（MuMu 复跑须在返修后同批）。
- **最小下一任务**：仅在本窗口所有权内——先补判别测试（严格同格式碰撞：同名普通文件在导出/导入两侧的零损失断言；`Long.MAX_VALUE` 与超 Long 数字后缀的不抛异常断言），再实现**可验证归属**的处理（优先方向：不可靠认定时一律保留文件、显式登记「残留未清理」并可向用户报告；如需删除级判定则按上轮 §4 恢复日志接缝立项，本轮不强制）；红→绿后定向 JVM，XML 落 E 盘并登记哈希。

## 2. 必须验收（5 项）

### V1 · S05 取消读完完成态口径——书架 + 跨页 10 站点 MuMu UI 一批验收

- **验收面**：qahost 隔离夹具、**手机尺寸（420dpi 竖屏）**：书架分组/徽章/长按/批量/搜索口径 + 首页继续阅读/最近读完/归档、我的阅读、我的页完读数、统计页完读计数与书态分布、继续阅读卡片文案；同一批做文件/DB/设置与设备密度恢复对账。
- **负责面**：MuMu 设备窗口（唯一设备所有者）执行，Codex 复核。
- **最新证据**：[S05 跨页顶部复核](../../legacy-stages/2026-09/2026-09-28-s05-cross-page-finished-consumers-result.md)（40/40 限定代码/JVM，明示「尚无 MuMu 截图及 DB 前后对账」）＋ [S05-① 顶部复核](../../legacy-stages/2026-09/2026-09-28-s05-cancel-finished-state-result.md)（70/70 同口径；两报告同根因链，合并验收）。
- **依赖**：需一个包含当前 S05 源码的 qahost 构建（见 §5 Round 1 前置串行构建）。**不与在途书架筛选窗口争文件**（`ShelfStatusFilterTest.kt`、`shelf/ShelfStatusCountsTest.kt`，16:25–16:29 mtime，他人资产）。
- **最小下一任务**：预构建 APK → 单批 MuMu 验收 → 报告 + Codex 复核；绝不把 40/40 写作 UI 通过。

### V2 · S12 设备收尾：完成态真实 Room/MuMu 展示 + marker 链连续端到端一条

- **验收面**：① 导入末章 99.4% 钳制后，真实 Room 落库与 MuMu UI 展示为「99.4%/在读」而非静默 FINISHED（`LegadoReadingProgressImporter` 改动的设备面）；② 同一隔离书补一条**连续**端到端：生产导入 → 提示可见 → 不移动退出 → 重开 marker 保留 → 主动移动后原生 locator 保存（现有证据为分段：预置 marker 链 MuMu 限定 PASS + 生产导入→入库→提示 PASS + 真实 TEMPORARY 路由 PASS，拼接覆盖但无一镜到底记录）。
- **负责面**：MuMu 设备窗口（与 V1 同批串行执行）。
- **最新证据**：[S12 导入完成态顶部复核](../../legacy-stages/2026-09/2026-09-28-s12-import-finished-state-result.md)（14/14 限定源码/JVM，Mock DAO；「真实 Room/MuMu 完成态展示未验」）；[S12 marker UI 守卫顶部 Codex 集成补记](../../legacy-stages/2026-09/2026-09-28-s12-streaming-txt-marker-ui-guard-result.md)（effect-key 修复 27/27，mTime 08:23 早于三份 MuMu 验收 09:43/10:07/12:45，字节新鲜度成立）；[分段 MuMu 证据](../../../qa/2026-09/2026-09-28-s12-mumu-marker-ui-acceptance.md)、[../qa/2026-09-28-s12-mumu-real-import-temporary-acceptance.md]、[../qa/2026-09-28-s12-mumu-actual-temporary-route-acceptance.md]。
- **依赖**：同 V1 的预构建 APK；无代码依赖。
- **最小下一任务**：并入 V1 同批；报告明确「一镜到底」或「分段合并」口径，交 Codex 复核。

### V3 · S14 返修后 MuMu 隔离复跑（依赖 M1）

- **验收面**：`LocalZipBackupPersistentRestoreTest` 6 项在 qahost 隔离持久库复跑（返修后的 `LocalZipBackup` 同构建）；可选增补一条 W4 kill 注入（两次 rename 间终止 → 重启 → 导入恢复旧字节），不强求。
- **负责面**：唯一 Gradle+设备所有者串行（构建 → 安装 → instrumentation → APK/DB 哈希对账）。
- **最新证据**：[S14 MuMu 验收](../../../qa/2026-09/2026-09-28-s14-mumu-zip-safety-acceptance.md)（6 项 PASS 属**返修前**构建；residue 识别路径不在其夹具内，但复跑须证同构建无回归）。
- **依赖**：M1 完成。
- **最小下一任务**：Round 2 串行执行；登记 APK/源码 SHA 与 XML 哈希；整包非原子边界照旧登记，不写「原子已修复」。

### V4 · S04 EPUB 回滚终版独立复核（轻量在 C，设备部分归 D）

- **验收面**：当前 `EpubRepository.replaceStoredEpub` 已实现入口守卫 + `backupReady/promoteStarted` 分流（本轮源码抽查证实，mtime 09-28 00:59 的变更来自同文件 `saveProgress` 的 S12 locator 修正，非回滚函数）；09-27 终版门禁自报 21/21 一次通过，**但无 Codex 复核登记，且其 XML 已被后续定向运行覆盖（共享构建目录，不可回读）**。
- **负责面**：Codex（复核登记）；设备路径（隔离测试书 EPUB 替换/中断）归 D 冻结批（B 退出复核已如此归类）。
- **最新证据**：[EPUB 回滚终版代码门禁](../../legacy-stages/2026-09/2026-09-27-s04-epub-rollback-final-code-gate.md)（自报 21/21）；[09-24 夜间复核 P1 候选](../../legacy-stages/2026-09/2026-09-24-overnight-results-corrigendum.md)（当前源码已按其两个窗口修复）。
- **依赖**：D 冻结（设备部分）。
- **最小下一任务**：Codex 对当前源码做一次代码级复核并登记「21/21 XML 不可回读、D 冻结时重跑」的归类；不因 mtime 重跑。

### V5 · S09-B/S15 62 条 UI 自动化——SR-ENV-1 三选一裁决（决策门）

- **验收面**：四个 androidTest 类 62 条（37+10+7+8）至今 0 项运行；MIUI 启动拦截，`compose-test-host-investigation` 结论「暂无可信绕过方案」。
- **负责面**：**总指挥裁决**（换真机 / CI / 接受本机不可验并改 JVM 替代门禁）；裁决前不新增设备轮次（阅读风险增量已登记 S09 不新增设备轮）。
- **最新证据**：[S09-B 分诊](../../legacy-stages/2026-09/2026-09-28-s09-ui-suite-triage.md)、[平台/阅读风险增量](../../legacy-stages/2026-09/2026-09-28-stage-b-reader-risk-delta.md) S09 行。
- **依赖**：总指挥时间；不影响第 1/2 轮并行。
- **最小下一任务**：C 退出前完成**裁决与归类登记**（执行本身按计划归 D.4）。

## 3. 可明确延期（12 项，登记理由与依赖；不阻塞 C 退出，但 C 退出时必须逐项归类在案）

| # | 项 | 归类理由与依赖 | 最小下一任务 |
|---|---|---|---|
| D1 | S06 E1 全局夜间翻转归因 | qa 复核明示「不阻塞继续 C」；G1/G2 归因实验已提示 MuMu ROM 侧，E4 未复现 | 保留异常登记；复现时做一次隔离实验（D 阶段 MuMu 批顺带） |
| D2 | S06 交互缺口（同帧合并、拖动/像素贴合、书内进度条）+ 主题手机尺寸复验 | 验证缺口非确证故障；属 D「隔离 MuMu 手机尺寸验 UI」范围 | 并入 D 阶段手机尺寸 UI 批；主题链加一屏手机尺寸截图 |
| D3 | S09 五项接线缺口（滚动模式无页码、全书页码模型零接线、点击区三套比例、媒体键桥、章末跨章） | 产品决定 + 验证缺口（非回归）；被 SR-ENV-1 与 D2 门控 | V5 裁决后随 D 排期；先由 Codex 出产品语义裁决 |
| D4 | S10-A 卷标题 8 处消费端语义 + 滚动展示合同 | 待产品决定；S10-B1 双 Picker 文案已收敛关闭（见 §4） | Codex 裁决后另行立项；不与 S09 页眉页脚混包 |
| D5 | S11 `EpubFootnotePopover.kt` 零调用方处置 + EPUB 图片/引用卡当前证据 | 处置决定 + 证据刷新；EPUB 链近期多次改动 | Codex 登记处置；EPUB 相关改动合并时纳入回归 |
| D6 | S12 导入预览链（导入前匹配摘要/导入中状态/按钮禁用） | 功能缺口非缺陷；B 增量排期在 locator owner 裁决之后 | 待产品排期；不阻塞 V2 |
| D7 | S15 F-2 完读卡 vs 详情页取数口径 + duration/slider 三类 instrumentation（0 运行） | 口径待决（调查项）；三类测试可用已打通 MuMu 通道 | 并入 D MuMu 批先跑三类，再裁决 F-2 |
| D8 | S08 章节编辑（产品合同未定） | 明确延期（保留不动，防误清理） | 维持登记；`SelectionActions.DORMANT_IDS` 守卫已在 |
| D9 | S19/S20 仓库根 32 个 dump XML（含真实藏书目录名）与桌面资产处置 | 资产治理决策，隐私敏感；E 阶段「干净工作树」前置 | Codex 独占出 quarantine 清单；未经授权不删除 |
| D10 | S03-1 独立真实 Room 并发证据刷新（历史 XML 已被覆盖） | 代码+JVM 已关；真实库自然路径 PASS WITH LIMITATIONS；D 冻结时补 XML 哈希 | D 冻结批一次补跑；不重复设备重建 |
| D11 | S18 性能基线（环境不可用）＋ S13 设置入口清单验收（依赖 S17 公共组件收口） | 环境阻塞 / 依赖未齐 | 维持登记；S17 收口后随 D 验收入口清单 |
| D12 | S05 markFinished 抬高 100% 残余的自愈语义（产品决策点） | 现行为「保留残余、下次真实阅读自愈」已登记；是否改为写入时对齐属产品语义 | Codex 产品裁决；不阻塞 V1 |

## 4. 已有充分证据关闭（本轮确认，不重做）

| 项 | 关闭口径与证据 |
|---|---|
| S06 切书队列「旧书积压翻到新书」 | 不可达（导航换 `NavBackStackEntry` 销毁旧组合）；零改动只读核查，[报告](../../legacy-stages/2026-09/2026-09-28-s06-book-switch-queue.md) |
| S06 跟随系统主题（含 S17 `else -> MODE_NIGHT_AUTO` 旧冲突，同根因一条） | 代码/JVM 34/34 + MuMu qahost 启动帧/双向切换/归因实验限定 PASS；边界（E1、平板布局、真机）归 D1/D2，[qa 顶部复核](../../../qa/2026-09/2026-09-28-s06-mumu-follow-system-acceptance.md) |
| S10-B1 双 HeaderFooterPicker 折叠文案收敛 | 共用组件落地 + JVM 5/5+21/21 + MuMu 420dpi 两入口目视限定 PASS（账本 09-28 Antigravity 回读）；[qa](../../../qa/2026-09/2026-09-28-s10-mumu-picker-visual-acceptance.md) |
| S12 TEMPORARY 真实路由端到端 | MuMu PASS（12:45 报告；marker 毫发无损 + 普通阅读对照 + 主 DB 恢复一致） |
| S12 Legado 生产导入链（解析→Room→UI 提示） | MuMu 受控 ZIP 限定 PASS（「生产解析/入库已验，WebDAV 传输未验」） |
| S12 marker 提示/保存保护（分段） | 预置 marker 链 MuMu 限定 PASS（三处口径收窄已登记）；连续端到端并入 V2 |
| S05-④ 导入后定位 | MuMu 420dpi 功能 3/3 限定 PASS；首跑 Snackbar 误点缺 DB 对账的旁路 caveat 保留登记 |
| S04 TXT/MD 小文件 Book hash（V4 真机）＋ S04-1..4 代码/JVM 关闭 | 历史账本多轮复核；EPUB 终版归 V4；多书批量/Provider 故障归 D |
| S14 MuMu 隔离 6 项（staging/替换失败保护/版本偏斜双轴） | 返修前构建的设备证据仍证明其夹具范围；residue 返修后同批复跑归 V3 |
| S14 上轮四窗口 JVM（W1/W2/W3/残留表征） | 22/22 中未被 M1 推翻的窗口判别仍有效；M1 返修后随定向 JVM 一并重跑（同文件所有权内） |
| S01/S02 | 真机 5/5 + 23/23，报告范围内关闭（Schema/DAO 再改才重验） |
| S03 全部代码/JVM 范围（含 S03-1 并发协议 19/19）与真实库自然路径 PASS WITH LIMITATIONS | 证据链完整；XML 刷新归 D10 |
| S07 主体（D-1/D-3/D-7、F 系列） | 历史关闭；Reader 输入链合并时附带定向回归 |
| S09-A-F1 书签 bookId 过滤、S19-F2 守卫、D-2-B 设置作用域、C01/S03-2/S03-3/S04-2 索引链 | 各自报告范围内关闭 |
| S06-⑤⑥ 快翻/RTL 设备证据 | MuMu 12 tests（快翻 8/8、RTL 4/4）；后续交互缺口归 D2 |

## 5. 两轮以内并行任务组合

**Round 1（两窗并行 + 一次前置串行构建）**

- 前置（串行，约 15 分钟）：**唯一构建所有者 = 设备窗口**先构建 qahost + androidTest APK（含当前已落地 S05/S12/S06 源码），登记 SHA 并安装；随后 Gradle 所有权移交实现窗口。
- **窗口 A（S14 返修）**：独占 `LocalZipBackup.kt` + `LocalZipBackupTest.kt`；M1 红→绿；**唯一 Gradle 所有者**（`:app:testDebugUnitTest --tests ...LocalZipBackupTest`）；XML+日志落 E 盘并登记哈希。
- **窗口 B（MuMu 设备所有者）**：用预构建 APK 执行 V1 + V2 同批验收（不跑 Gradle、不改源码；仅写 qa 报告与 E 盘工件）；完成后恢复设备基线（密度/设置/隔离数据清理对账）。
- 互斥性：A 只碰 sync 两文件；B 零源码；无交集。

**Round 2（收尾）**

- **窗口 A′（Gradle+MuMu 同一所有者，串行）**：重建含返修后 `LocalZipBackup` 的 qahost/androidTest APK → 安装 → V3 六项复跑（+可选 kill 注入）→ 哈希对账。Gradle 与设备在本轮同属一人，无交叉。
- **窗口 B′（零构建，并行）**：V4 复核登记 + V5 裁决材料整理 + 对照 §7 检查单核对全部桶项归类；产出 C 退出候选结论交总指挥。
- Round 2 结束即具备 C 退出条件；**百分比调整由总指挥执行**。

## 6. 互斥文件所有权与唯一所有者

| 资产 | Round 1 | Round 2 |
|---|---|---|
| `LocalZipBackup.kt` / `LocalZipBackupTest.kt` | 窗口 A 独占 | 只读（A′ 构建输入） |
| Gradle（构建/JVM） | 窗口 A（前置构建由设备窗口完成后移交） | 窗口 A′ 唯一 |
| MuMu/ADB | 窗口 B 唯一 | 窗口 A′ 唯一 |
| `docs/qa/2026-09-28-s05-*`、`s12-*` 验收报告与 E 盘工件 | 窗口 B 独占 | 窗口 B′ 只读 |
| 他人**在途禁触**：`ShelfStatusFilterTest.kt`、`shelf/ShelfStatusCountsTest.kt` 及其 ShelfModels hunk（书架筛选窗口）；`EpubRepository.kt`/`ShelfImporter.kt`（Codex 共享 owner）；`ProfileViewModel.kt`/`ProfileRoute.kt`（A-1 UI 轮）；`JsonBridge.kt`、schema、Gradle 配置 | 全体只读 | 全体只读 |

## 7. C 阶段退出检查单（全部勾选方可报退出）

1. [ ] M1 返修红→绿完成，同格式碰撞与超长数字判别用例在案，定向 XML+E 盘哈希登记（S14 窗口）。
2. [ ] V1 S05 全链 MuMu 验收报告 + Codex 复核（含文件/DB/设置/密度恢复对账）。
3. [ ] V2 S12 完成态展示 + marker 链端到端报告 + Codex 复核（口径明确一镜到底或分段合并）。
4. [ ] V3 S14 返修后 MuMu 6 项复跑 OK，APK/源码/XML 哈希登记；「整包非原子」边界照旧登记。
5. [ ] V4 S04 EPUB 终版复核登记（含「XML 不可回读、D 冻结重跑」归类）；设备路径归 D 已登记。
6. [ ] V5 SR-ENV-1 三选一**裁决已登记**（62 条执行归 D.4）。
7. [ ] §3 全部 12 项延期逐项归类在案（理由+依赖+下一任务），无未归类开放项。
8. [ ] §4 关闭项核对一遍（无重做、无把关闭项当开放项）。
9. [ ] 账本追加 C 退出条目；A–E 百分比由总指挥统一调整（本审计不改）。

## 8. 边界声明

- 本轮零构建、零设备命令；所有「已运行」表述均引自可回读证据（E 盘 XML/日志、qa 报告），未重新执行；「未验证」一律不写 PASS。
- 在途窗口资产（书架筛选测试等）已识别并排除在任务分配外；未撤销、未覆盖任何他人改动。
- 未触碰原仓库、真实手机、正式书库；未 commit/push/reset/checkout。
- 落盘即停，交总指挥复核。
