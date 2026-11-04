# CRA-UNIFIED-ACCEPTANCE-CONTRACT-v1 —— 统一候选验收合同（只读规划）

## 任务编号/版本

`CRA-UNIFIED-ACCEPTANCE-CONTRACT-v1`；2026-10-05。性质：**只读验收合同规划**——为并行任务 `unified-candidate-integration-v1` 即将构建的新统一候选准备最小、可执行的验证合同。本轮不构建、不安装、不运行测试、不操作 ADB/MuMu、不执行 Git 写操作；合同状态 **READY_FOR_BINDING**（依赖尚未交付的候选输入明确待绑定，不虚称运行通过）。候选集成报告完成后由 Codex 绑定合同并决定实际设备批次；本任务不抢设备、不修改集成者工件、不自行开启下一任务。

## 输入指纹

- 恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空（开工与收工只读核实）。
- 四份 v3 语义决策（实读）：
  - [semantic-v3-codex-review](../../reviews/2026-10/2026-10-05-semantic-v3-codex-review.md)（SHA-256 `920046fb…`）：3/3 任务核查成立，12 文件 = 9 采用候选 + 1 保 D（ProfileViewModelTest）+ 2 精确组合（ReaderProgressActions、EpubRepository）；"不能只应用 12 路径"，直接依赖按机械清单绑定。
  - [reader-progress-merge-decision-v3](../../decisions/2026-10/2026-10-05-reader-progress-merge-decision-v3.md)（`9216db3f…`）：A1–A7/E1–E7 hunk 级组合；A6 else 链两侧组合代码；调用方核查；ci/co 合同与消费端核查。
  - [remaining-semantic-merge-review-v3](../../reviews/2026-10/2026-10-05-remaining-semantic-merge-review-v3.md)（`13db1d86…`）：7 文件 41 hunk；Sync/Shelf/Parser/WebDav 采用候选；EpubRepository 精确组合（D 的 `globalOffset = absoluteOffset.takeIf { it >= 0 }` 必须保留）；复用/必跑判定。
  - [profile-merge-decision-v3](../../decisions/2026-10/2026-10-05-profile-merge-decision-v3.md)（`c9d5ffea…`，本任务同日先期交付）：Profile 三文件方向纠正（候选含横幅与状态依赖，v2 清单方向读反）；ProfileViewModelTest 以 D 为基座；横幅块依赖候选 LocalZipBackup + 新增 LocalZipRestoreStateStore.kt 同批。
- 机械清单 v2（`2026-10-04-next-joint-integration-manifest-v2.md`，`814ee9b1…`）：980 行分类；其 Profile 方向误判已被 profile-merge-decision-v3 纠正，本合同不沿用其方向结论。
- 最终验收（[joint-final-v3](../../../qa/2026-10/2026-10-04-next-joint-final-v3.md)，`b5749b78…`）：303/303 JVM；MuMu 10/10 限定行为（EPUB 2 + SE4 3 + MD 滚动 5）；冻结宿主 `2a1473b4…`/测试 `98fc9cf4…`；**未覆盖项 r3：MD 默认左右翻页下 TOC 点击第 2 章 toolbar 显示第 2 章但正文/底栏仍第 1 章，根因未定位**。
- 分页恢复收尾（[md-paged-restore-close-v2](2026-10-04-next-md-paged-restore-close-v2.md)，`96bdca85…`）：111 JVM = 15 XML 合计 111 tests/0 失败；七设置/原宿主 71 文件/原 APK 对恢复成立；夜间模式 10-01 至 10-04 多次翻转、来源未知、无任务在 restore 中管理它（最近可信读数 yes @2026-10-04 23:48）。
- 驱动 v4（实读 + 复算 SHA）：[runbook](<E:/creation-reading-assistant/next-round-joint-mumu-driver-v4/runbook.md>) `544eb266…`、config.template.json `c9a7cc08…`、runner.py `a634a344…`（与 v1/n1-runner-hardening-v1 逐字节相同）、joint_driver.py `72c38e5b…`、offline_test.py `4bbf5527…`。
- 分页冻结清单 `next-round-md-paged-chapter-fix-v1/freeze/source-sha256.json`（`b503d75a…`，范围内文件全部 changed:false）。
- 交接入口 [current](../../../handoff/current.md)（`aa518dd7…`）：2026-10-05 状态 = 三组 v3 语义决策已核 3/3，下一单 owner 保全 D WIP 构建新 E 统一候选与受影响验证，尚未实施合流、回灌 D 或 Git。
- **未读取** `unified-candidate-integration-v1` 尚未冻结的工作文件，不冒充最终输入。

完整输入指纹表（含全部 SHA-256）见 [execution.json](<E:/creation-reading-assistant/unified-acceptance-contract-v1/execution.json>)。

## 所有权与改动

- 本任务唯一写入：
  - 新报告 `docs/coordination/dispatches/2026-10/2026-10-05-unified-acceptance-contract-v1.md`（本文件）。
  - 任务目录 `E:\creation-reading-assistant\unified-acceptance-contract-v1\`：acceptance-matrix.csv（16 门禁 × 12 列）、reuse-verification-list.md、binding-checklist.json、execution-and-recovery.md、execution.json、_gen_matrix.py（一次性生成脚本，可删）。
- 未改产品/测试/驱动/旧报告/current/TODO；未 commit/push/reset/checkout；未操作 ADB/MuMu/实体手机/正式书库；未读写其他应用私有记忆库。
- 合同执行权归 Codex；本合同只定义"验什么、怎么判、如何恢复"，不执行。

## 环境

- 只读分析环境：恢复仓库工作树 + E 盘既有任务目录；无设备连接、无构建、无测试运行。
- 目标环境（由 Codex 绑定后执行）：MuMu 模拟器，serial 运行时 `adb devices` 核实（历史 `127.0.0.1:16416`，别名 emulator-5556 为同一 VM，仅参考不沿用）；1080×1920、280dpi 竖屏宽布局——**MuMu 验收即模拟器验收，不称实体手机或手机窄布局通过**。
- 新隔离 UID（如 `com.creationreadingassistant.qahostjointv2` 与 `.test`）由集成者指定，运行时确认未安装无既有数据；protected = 原 `qahost`/`qahost.test` 两包（只读对账）；生产包 `com.creationreadingassistant` 在 MuMu 已确认缺席，不放入 protected 清单。

## 执行与结果

### 验证矩阵（16 门禁，详见 acceptance-matrix.csv）

| 区 | 门禁 | 验证域 | 当前判定 |
|---|---|---|---|
| A | A1/A2 | ReaderProgressActions 两侧组合（A6 else 链）与调用方 | NOT_RUN（待合流树） |
| A | A3/A4 | 定位风险登记：ci/co 合同偏离（已确认）/pagedSource.chapterIndexFor 语义（未验证） | 登记 |
| B | B1 | EpubRepository 缺失全书 offset 合同（S12-① 精确组合） | NOT_RUN（待合流树） |
| C | C1/C2 | Markdown 分页章内坐标（JVM）/分页 TOC 旅程（设备，含 r3 复核） | NOT_RUN（待绑定） |
| D | D1/D2 | Profile 恢复横幅及状态依赖（编译+JVM）/横幅行为与跨实例可见（设备/仪器） | NOT_RUN（待绑定） |
| E | E1–E5 | SyncRepository R1/ShelfImporter SWAP/WebDavBackup P2/EpubParser 闸+Repository 组合/Effects 候选超集 | NOT_RUN（待合流树/绑定） |
| F | F1/F2 | 构建配置后缀回 .qahost/行尾保持 LF | NOT_RUN（待合流树） |

每门禁含九字段：风险、生产入口、预置状态、动作、可观察结果、已有证据、复用条件、新增验证、恢复要求。

### 复用 / 新增判定（详见 reuse-verification-list.md）

- **可复用（JVM 级，SHA 绑定为前提，6 组）**：MarkdownChapterSourceTest 2 用例 + paged 其余 111 JVM 套件（绑定分页冻结 SHA）；ReaderAnnotationNavigationTest、MarkdownVisibleSourceOffsetTest（绑定候选 SHA）；ShelfImporterTest 63/63（`bfc37d71…`+`89e335be…`）；WebDavCompatibilityTest 27/27（`1291b147…`）；SyncRepositoryLocalFactsTest 25 项（`b01cfda9…`，合流后重跑一次闭合绑定）。
- **必须重跑（JVM 级，6 组）**：ProfileViewModelTest D 版（合流版 `4bcc8b2e…`≠sync-wip 冻结候选版 `cefea0a3…` → 55/55 整体复用条件不成立）；sync-wip 其余 30 项随捆绑重跑；Epub 五套件 147 项（候选闸包装实现 SHA `27a4644d…`/`15a92883…`≠v3 冻结 → 不可移植；RollbackTest stub 需按 `parseUnderSwapGate` 适配核查）；TxtScrollChapterTargetTest（D 版无已验绑定）；**新增** persistCurrentProgress 三模式定向回归（无任何既有直测）；**新增/核实** S12-① 定向回归（章切换落库→locator v2 无 offset 键→SourceNavigationContract 按 chapterStart[ci]+co 派生成功，或核实既有 SourceNavigationContractTest 覆盖该精确路径并落盘结论）。
- **设备级全部重跑**：所有旧设备 PASS（final-v3 10/10、md-paged 5/5、N1 7/7、j3 21+3 等）绑定旧 APK，新统一 APK 不继承安装身份或设备 PASS。
- **判弃不验证（4 项）**：WebDav 错误消息文案与白名单放宽；ShelfImporter 守卫前 staging.delete()；SyncRepository 105 行朴素实现。

### 设备旅程最小集（详见 binding-checklist.json）

- **J1** TXT 滚动退出重进（gates A1/A2）：核对落库 offset 为实时列表偏移。
- **J2** MD 滚动退出重进（gates A1/E5）：核对落库为 canonical source 偏移。
- **J3** MD 分页 TOC 1→2、2→3、3→2（等待 8 秒）、强停冷启动恢复（gates C1/C2）：专项复核 r3 现象（toolbar 章号与正文/底栏一致性），无论结果如实登记。
- **J4** EPUB 笔记/高亮→跳转回源（gate B1）：一致性校验通过。
- **J5** Profile 横幅跨实例（gates D1/D2；仪器类 LocalZipRestoreAcceptanceTest 承载，设备侧按集成者判定纳入 items）：无中断不显示/注入中断显示阶段文案与重试入口。
- **不纳入**：同步 UI 旅程（无新组合接缝——syncNow/SyncOptions 接线两侧逐字相同，仓库层由 E1 JVM 覆盖）；纯夹具准备与终态记录（mdpc_00/90 口径，不计功能通过）；直写 DB 制造数据（禁止）。

### 风险登记（按位置分离，不写"无影响"）

1. **已确认合同偏离**：D 在 persistCurrentProgress(A7) 与 ReaderProgressEffects(E7) 写 chapterIndex=真实章号但 charOffset=全局偏移，ci/co 元组内部不自洽（chapterStartOffsets[ci]+co≠offset）。合流树原样保留（按决策不修），登记在案。
2. **当前消费者屏蔽行为**：续读 parseStoredAbsoluteOffset 只读 v2 offset；MD 恢复读顶层 space；回源 MD 走 preferGlobalOffset=true 忽略显式 ci/co、TXT 走 resolvePlainPosition 只读 legacyOffset/charOffset、EPUB 走 preferGlobalOffset=false 启用一致性校验（仅 EPUB 存储 locator 走此路径）。当前消费端不因该偏离报错。
3. **未验证消费者**：D 注释称 pagedSource.chapterIndexFor "当前章作用域 source 章数恒为 1 只会返回 0（真机验证踩过）"，但四个 ChapterSource 的 chapterCount 均为全书章数，注释与实现不符；何者在真机成立未验证。J1/J3 落库章号仅作旁证，不构成正式验证。
4. **未授权扩展**：任何将来以 preferGlobalOffset=false 消费 TXT/MD 存储 locator 的新调用方会因一致性校验失败拒绝跳转；扩展修复须另立任务授权，本轮不修不扩。
5. **r3 现象**：MD 默认左右翻页 TOC 切章 toolbar/正文/底栏章号不一致（joint-final-v3 未覆盖项，根因未定位）；统一候选沿用同一接缝，J3 专项复核。
6. **夜间模式**：MuMu 上 10-01 至 10-04 在 yes/no 间多次翻转、来源未知、无任务在 restore 中管理；基线以本次运行前实读为准，不预设 yes/no。

### 绑定状态

合同状态 **READY_FOR_BINDING**：冻结锚点已固化（驱动 v4 五文件 SHA、protected 清单、生产包、adb 路径模板）；待绑定 12 项（统一候选 manifest、双 APK+SHA、新隔离 UID、run_id、config.json、唯一设备 owner/serial、items 与 before_item 回调、_ui_probe 文案、r3 根因状态、SE4 设备旅程可选纳入、夜间模式基线实读）。冻结完成前不可直接运行。

## 原始证据

- 本任务产出：`E:\creation-reading-assistant\unified-acceptance-contract-v1\`（acceptance-matrix.csv / reuse-verification-list.md / binding-checklist.json / execution-and-recovery.md / execution.json）。
- 被引用的既有原始证据（保持原样未改）：四份 v3 决策报告、joint-final-v3 全套（freeze/jvm-xml、device-core、device-md-r4、device-md-r3 失败批）、md-paged-chapter-fix-v1（freeze/source-sha256.json、device-mdpc-*）、md-paged-restore-close-v2（evidence/readback_*.json、restore_night_mode.json）、驱动 v4 目录（runner.py 等五文件）、sync-wip-reconcile-v1/rollback-wip-reconcile-v1/v3/v4 冻结 SHA。
- 驱动 v4 证据契约（执行时落盘）：evidence/run-<run_id>.json、baseline.json、raw/<id>.txt、items/<id>.json、ui/probe.xml、restore.json、summary.json。

## 恢复与副作用

- 本轮无设备操作、无构建、无测试运行 → 无恢复义务、无副作用；仓库 HEAD 与暂存区收工只读核实未变。
- 合同执行时的恢复义务（详见 execution-and-recovery.md 阶段 7 与对账明细）：七项设置逐项回写复验（night_mode 以本次实读基线为准）；原宿主 71 文件（含 DB/WAL/SHM）与原 APK 对 2/2 SHA 逐条对账；沙箱双包以显式状态探测复验 absent（pm path rc=1 空输出 + 成功且可解析的 pm list packages 精确匹配不存在双重确认——**pm path 失败单独不能证明包缺席**）；forward/reverse 本次新增逐条移除复验（基线有而现无如实报问题）；/data/local/tmp 无本任务 cra_* 临时文件；protected_packages 只读对账；finally 保证任何阶段失败都恢复。
- 执行口径：结果只写 MuMu 模拟器；ADB 非零/权限失败/空输出/无法解析一律按失败处理，绝不解释为"包不存在/无数据"。

## 未覆盖项

- 统一候选尚未交付：全部门禁 NOT_RUN（待绑定）；本合同不含任何运行结论。
- r3 根因未定位（仅现象与截图）；J3 只复核现象，不定位。
- 手机窄布局、实体设备/MIUI、TalkBack、R8、签名发布、全产品验收、永久 D 集成、Git 提交维持既有边界。
- 流式大 MD（>60k 源字符）设备验证、持久 locator_v2 的 ci/co 与重推章号关系未新增覆盖（沿用既有核查结论）。
- EPUB 设备使用 file URI；SAF/content URI 重新授权链未新增覆盖；全局 EPUB parse 串行闸性能影响未测量。
- SE4 注解延迟消费设备旅程为可选（由 Codex 绑定决定，不强制）。
- 夜间模式翻转机制未追查（只按任务规则以本次实读为基线）。

## 交付状态与下一步

- **交付状态：已交付（合同 READY_FOR_BINDING）。** 交付物 = 本报告 + E 目录五份文件。全部门禁 NOT_RUN，无任何 PASS/FAIL 运行结论。
- 下一步（归 Codex）：① 接收 unified-candidate-integration-v1 集成报告与统一候选 manifest；② 绑定 binding-checklist.json 12 项待绑定条目（config.json 逐项实际值、run_id、items 与 before_item 回调、_ui_probe 文案、设备 owner/serial）；③ 冻结后按 execution-and-recovery.md 八阶段执行；④ 设备批次范围由 Codex 按本合同 J1–J5 最小集决定（SE4 可选）；⑤ 执行结果回填判定（PASS/FAIL/NOT_RUN），本合同不预判结果。
- 本任务就此停止，不应用 patch、不启动下一任务、不抢设备。
