# M0-v1 当前版本产品范围与证据归属（2026-10-01）

## 任务编号/版本

M0-v1；来源：产品总规划 M0 与 W2 核查后的并行授权——空出的构建窗口做八域必需用户旅程、当前版本归属与证据映射。适用：只读产品、历史报告与现存资产；不操作设备、不启动联合构建、不改公共账本。产出为 `E:\creation-reading-assistant\m0-product-scope-20261001` 下的矩阵与证据索引，以及本报告（恢复仓库内唯一新增）。矩阵 34 条用户旅程、13 字段；证据索引 25 条。命名一律中性。

## 输入指纹

- 活动仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；实读 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空，工作树保留共享 WIP（未纳入本轮输入）。
- 唯一当前联合候选：[J1-v3 android](<E:/creation-reading-assistant/next-round-joint-j1-v3/android>)，六文件 SHA 实算 6/6 一致。
- 并行只读引用：[W2 构建/设备核查](../../reviews/2026-10/2026-10-01-next-w2-codex-review.md)、[产品总规划](../../../plans/2026-10-01-product-roadmap-realignment.md)、[当前执行入口](../../../handoff/current.md)。
- 需求依据：桌面 P1 规格 2026-08-09、桌面 v2.0 需求 2026-09-10、Android 八组需求 2026-09-09、本地书库路线 2026-09-12、书架成熟化 2026-09-18。均有明确日期，旧顶部状态一律不作为当前执行命令。

## 所有权与改动

- 本轮只读产品源码、历史报告与既有 E 资产；未改 current、项目页、TODO、源码、测试、配置、旧报告或 HOLD。
- 唯一新增两处：`E:\creation-reading-assistant\m0-product-scope-20261001\{m0-journey-matrix-v1.csv,m0-evidence-index-v1.csv}`；本报告 `docs/coordination/decisions/2026-10/2026-10-01-m0-current-product-scope-and-evidence.md`。
- 未执行构建/设备测试，未 stage/commit/push/reset/checkout，未搬目录；未读取 Claude 独立记忆库；未输出凭据或私有书名。

## 环境

Windows、PowerShell/Python；仅读取相关规格、现有 E 候选/冻结清单/验收目录与桌面源码入口。零 ADB/设备操作，未重跑构建、JVM、Room 或服务自检。未做全仓审计：源码入口只按需求文档指向的文件名做存在性核对（例如 `ShelfModels.kt`、`ShelfImporter.kt`、`data/repository/SyncRepository.kt`、`feature/sync/WebDavBackup.kt`、`LibraryBrowserPolicy.kt`、`SafSmartBookRecognizer.kt`、`LibraryRootStore.kt` 在 J1-v3 候选中均存在；`electron/main/creation-workspace/**`、`creation-card-io/**`、`creation-bundle/**` 在恢复仓库存在）。

## 执行与结果

### 1. 当前明确必需用户旅程清单与纳入/排除依据

矩阵共 34 条旅程，标注"是（当前明确必需）"28 条，"待产品决定"6 条。

**纳入依据（至少满足其一，且不与更新版需求冲突）**：属于两条产品线已声明的主工作流（桌面创作：创建/导入→卷章场景/大纲→正文与自动保存→全局卡片/资料引用→搜索/版本恢复/导出；Android 阅读：授权目录或文件导入→书架管理→阅读→搜索/笔记/灵感/TTS→位置恢复→安全备份/恢复及已有 LAN 协同）；或属于当前活动候选与 M2 下一优先范围。

**必需清单（28 条，按域）**：

- G1（4）：G1-01 授权目录导入与浏览、G1-02 智能识别与批量导入、G1-03 来源索引与增量更新、G1-04 书架状态分组与读完归类。
- G2（5）：G2-01 位置恢复与返回阅读处、G2-02 滚动TXT替换（缺陷）、G2-03 普通/高级纠错、G2-04 选区动作与离线词典、G2-05 阅读内核与排版。
- G3（6）：G3-01 统一阅读笔记、G3-02 正文索引与全局搜索、G3-03 全局/本书设置、G3-04 灵感/素材回顾、G3-05 TTS、G3-06 首页/历史/灵感/统计回归。
- G4（2）：G4-01 E 限定备份/迁移/恢复、G4-02 N1 中断恢复与残留导出防护（缺陷）。
- G5（3）：G5-01 LAN 同步限定实现、G5-02 WebDAV 独立 UI 宿主、G5-03 跨端真实兼容。
- G6（6）：G6-01 桌面新建/正文/自动保存、G6-02 全局卡片库/关系/附件封面、G6-03 大纲/沉浸写作/统计、G6-04 AI/校对/关系图、G6-05 导入导出与项目包、G6-06 旧数据迁移与灵感收件箱。
- G7（1，另 3 条待产品决定）：G7-04 隔离 MuMu 设备验收（当前授权验证方式）。
- G8（1，另 3 条待产品决定）：G8-01 联合候选构建与冻结。

**排除/暂缓依据（6 条，均标"待产品决定"）**：G7-01 无障碍/TalkBack/适配、G7-02 实体硬件/MIUI、G7-03 R8 实际运行——当前少数派的发布前必验项，不并入当前功能阻塞；G8-02 Release 签名/升级链、G8-03 release.yml、G8-04 F 公开发布——属 F 独立范围，无当次许可不推进。

**未自动成为承诺**：历史候选清单、180 资产行、122 HOLD、文件/测试/报告数均不作产品任务分母；新增服务端、多人协作、手机端项目双向编辑、全量视觉改造不进入本轮队列。

### 2. 已交付项、证据缺口、真实缺陷与产品决定的去重列表

同一根因去重后如下（不按文件/测试数计）：

- **已交付且已核（限定范围）**：J1-v3 源码/构建（六 SHA 6/6、真实冻结 APK）；W2-build-v1（875 源码单 hunk、APK 0221d88f…、签名/manifest 成立）；R1/R2/R3 限定实现 3/3；Room 4/4；本批恢复 71/71；A–E 恢复 5/5；E 限定备份/迁移/恢复；书架 P0（2026-09-18 真机，当时范围）。
- **已交付但当前版本归属未确认**：G1-01/02/03（书库目录第 1–3 组接线，与 J1-v3 候选未逐文件核归属）；G1-04（书架 P0 基于 2026-09-18 Room v15 工作树）；G6-01～G6-06（桌面 v2.0 阶段 0–4；源码在恢复仓库存在，但 `release-beta-stage4-final` 打包工件不在恢复资产中）。
- **证据缺口（交付缺失，非产品缺陷）**：G5-02 的 W2-accept-v1——恢复仓库约定报告不存在、E 目录 evidence/logs 文件数均为 0，仅有前置准备物；不能从 APK/基线或 BUILD_READY 推导七 UI 项通过，也不能据文件缺失断言窗口绝对未执行。
- **真实缺陷（已证实）**：① G2-02 滚动TXT替换——保存、滚离返回、重进、进程重启仍显示原文，Debug ScrollReplaceTrace 存在但无新鲜成功证据；② G4-02 N1 中断——整包 DB+文件原子性不成立，重启单独不自动收敛，需人工重试，残留 staging/backup 的恢复与导出污染为下一优先数据安全任务。
- **产品决定（待 Codex/用户裁决）**：G7-01/02/03、G8-02/03/04 是否纳入发布前必验；G1-04 的 P1/P2/P3（分组条/position 激活/编辑模式）是否本期；G6 桌面历史交付是否按"已交付不重做"处理；G5-03 跨端合同需先冻结桌面候选与协议。

### 3. 实现交付与验收的分母核算

- **实现交付分母：全产品范围内暂不可可靠核算。** 只能在已冻结范围给局部计数（引用已核范围，不相加、不平均）：
  - 联合限定关卡 2（构建、设备）→ 构建 1/2 成立（W2-build 通过）；R1–R3 限定实现 3/3；Room 4/4；本批恢复 71/71；A–E 恢复 5/5。
  - 这 5 组数字口径不同、来源不同，不得相加成全产品实现百分比。
- **验收分母：暂不可核算。** 具体待定项与原因：
  1. 桌面 v2.0 的当前版本归属未确认——源码在恢复仓库存在，但打包工件（`release-beta-stage4-final`）不在恢复资产，无法判"当前版本已交付/已验"。
  2. 书库目录第 1–3 组与书架 P0 的历史真机验收（2026-09-13 / 2026-09-18）属历史范围，未纳入 J1-v3 候选的同批证据，不能直接记为当前候选已验。
  3. W2 设备七 UI 用例无执行证据，运行状态未经界面确认；不预先写七项 PASS。
  4. S08 等冻结能力的当前版本归属未确认（不得把差异引擎单测算完整编辑交付）。
  5. HOLD 与共享 WIP 尚未纳入候选，须按行为/hunk 归属后方可计入。
- **不得**：给猜测百分比、自定权重、删必需项制造进度；范围变更须保留前后对照。

### 4. 接下来最有价值的三项任务建议（依赖与所有权）

1. **N1 最小中断恢复与残留导出防护（保持最高优先）**。依赖：M1 产品基线确定后可并行互斥领域；所有权归单一 owner（DSH/设备侧领域）。最低合同：中断状态可识别、有明确修复/恢复入口、重试不污染数据、残留不混入后续备份；不默认要求架构重写成跨文件系统事务。**不得**再归为"全量真机阻塞"。
2. **W2 设备七 UI 用例续执行与恢复（原设备 owner）**。依赖：Codex 已核准 W2-build 依赖；接续原任务、不重做准备。产出约定报告 + evidence/logs；完成后再交 Codex 做 M1 联合里程碑复盘。所有权归原 DSH 窗口、独占 MuMu 新 UI 包。
3. **桌面 v2.0 当前版本归属核对（只读，可与 1、2 并行）**。依赖：无设备；核 `electron/main/**`、`src/**` 与 `release-beta-stage4-final` 打包工件在恢复资产中的归属与版本，区分"源码存在"与"当前版本已交付/已验"。所有权归构建/集成窗口。完成后才能回答 G6 是否计入实现交付分母。

## 原始证据

- 矩阵：`E:\creation-reading-assistant\m0-product-scope-20261001\m0-journey-matrix-v1.csv`（34 条 × 13 字段）；证据索引：同目录 `m0-evidence-index-v1.csv`（25 条 × 8 字段）。
- [产品总规划](../../../plans/2026-10-01-product-roadmap-realignment.md)、[当前执行入口](../../../handoff/current.md)、[W2 核查](../../reviews/2026-10/2026-10-01-next-w2-codex-review.md)、[J1-v3/J2-v4/W1-v2 核查](../../reviews/2026-10/2026-10-01-next-j1v3-j2v4-w1v2-codex-review.md)、[N1/N3 审阅](../../reviews/2026-10/2026-10-01-next-n1-n3-codex-review.md)、[阶段E限定关闭](../../dispatches/2026-10/2026-10-01-stage-e-final-v2-codex-closure.md)。
- [桌面 P1 规格](../../../plans/desktop-reference/2026-08-09-desktop-creation-workbench-phase-1-spec.md)、[桌面 v2.0 需求](../../../plans/2026-09-10-desktop-global-card-library-requirements.md)、[Android 八组需求](../../../plans/android-parallel-delivery-2026-09-09/README.md)、[本地书库路线](../../../plans/android/2026-09-12-android-library-folder-smart-recognition-roadmap.md)、[书架成熟化](../../../plans/android/2026-09-18-android-shelf-maturity-plan.md)。
- E 资产：[J1-v3 六 SHA](<E:/creation-reading-assistant/next-round-joint-j1-v3/freeze/six-file-sha256.txt>)、[W2 冻结](<E:/creation-reading-assistant/next-round-w2-webdav-ui-build/freeze>)、[W2 READY](<E:/creation-reading-assistant/next-round-w2-webdav-ui-build/READY.json>)、[W2 设备准备目录](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance>)、[N1 证据](<E:/creation-reading-assistant/next-round-n1/acceptance>)。

## 未覆盖项

- 未全面复审桌面/Android 源码或所有历史报告；未核实际签名 Secrets、外部账户额度与运行窗口界面。
- 未核 J1-v3 候选内各历史功能（书库第 1–3 组、书架 P0、S08 冻结能力）的逐文件归属；矩阵相应行的验收状态按"历史已交付但当前版本归属未确认"记录，未升级为已验。
- W2 七 UI 用例无执行证据，运行状态未经界面确认；不预先判定通过或失败。
- 实体硬件/MIUI、TalkBack、R8 实际运行、正式签名/升级链、F 发布仍未验；HOLD 与共享 WIP 未纳入候选。
- 八域导航用于防遗漏，不表示全部新增功能已承诺或已完成。

## 恢复与副作用

- 本轮零设备操作、零构建、零 Git index/HEAD 变更；HEAD 保持 `e39a78da…`，暂存区空。
- 未改 current、项目页、TODO、源码、测试、配置、旧报告或 HOLD；未改外部工件；未读取 Claude 独立库；未发送外部消息、未创建自动任务。
- 本轮唯一新增写入为上述两处（E 矩阵/证据索引与恢复仓库本报告）。

## 交付状态与下一步

- 本轮交付：M0-v1 当前版本需求/归属/证据矩阵与证据索引（只读规划产物），以及本九字段报告。范围明确：必需 28 条、待产品决定 6 条；实现与验收分离；无猜测百分比。
- 可核算范围：仅已冻结的局部口径可按引用计数（构建 1/2、R1–R3 3/3、Room 4/4、71/71、A–E 5/5）；全产品实现与验收分母仍待桌面归属、W2 设备证据、S08 归属与 HOLD 归属到位后核算。
- 仍需 Codex 裁决：① G6 桌面 v2.0 是否按"历史已交付不重做 + 仅补真实遗漏"处理；② G7-01/02/03 与 G8-02/03/04 是否进入发布前必验；③ 书库第 1–3 组与书架 P0 的历史验收能否作为当前版本归属证据，或需在 J1-v3 候选内重取定向证据；④ HOLD 与 S08 冻结能力的归属核对范围与 owner。
- 本轮到此停止，不操作设备、不启动联合构建、不改公共账本。
