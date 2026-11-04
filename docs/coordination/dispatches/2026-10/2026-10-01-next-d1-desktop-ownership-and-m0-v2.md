# D1-v1 桌面当前交付归属核对与 M0-v2 纠正矩阵报告

## 任务编号/版本

D1-v1，2026-10-01。来源：Codex review（[docs/coordination/reviews/2026-10/2026-10-01-next-w2-m0-codex-review.md](../../reviews/2026-10/2026-10-01-next-w2-m0-codex-review.md)）授权。
性质：只读归属核查与 M0-v2 纠正矩阵产出；不重新开发桌面功能，不启动构建，不操作设备，不改动公共账本。
执行应用/模型：Antigravity Agent（模型：Google DeepMind Gemini 2.5 Pro）。
产出：恢复仓库唯一新增本报告；本地工件目录 `E:\creation-reading-assistant\next-round-d1-desktop-scope\` 下产出 `m0-journey-matrix-v2.csv`（34行×13列）与 `m0-evidence-index-v2.csv`（28行×8列）。原 M0-v1 原件完整保留。

---

## 输入指纹

- 恢复仓库活动基线：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区干净，保留共享 WIP（未纳入输入）。
- 原 M0 输入资产（保持原样）：`E:\creation-reading-assistant\m0-product-scope-20261001\` 下的 `m0-journey-matrix-v1.csv` 与 `m0-evidence-index-v1.csv`，及原报告 [docs/coordination/decisions/2026-10/2026-10-01-m0-current-product-scope-and-evidence.md](../../decisions/2026-10/2026-10-01-m0-current-product-scope-and-evidence.md)。
- 上位规划与权威核查：[Codex-review-W2-M0-v1](../../reviews/2026-10/2026-10-01-next-w2-m0-codex-review.md)、[当前执行入口](../../../handoff/current.md)、[产品总规划](../../../plans/2026-10-01-product-roadmap-realignment.md)。
- 桌面需求与交付记录依据：
  - [桌面 P1 规格 2026-08-09](../../../plans/desktop-reference/2026-08-09-desktop-creation-workbench-phase-1-spec.md)；
  - [桌面 v2.0 需求与阶段 0–4 验收记录 2026-09-10（2026-09-12 更新）](../../../plans/2026-09-10-desktop-global-card-library-requirements.md)；
  - [桌面迁移预检 2026-09-10](../../../plans/desktop-reference/2026-09-10-desktop-global-card-library-migration-preflight.md)；
  - [桌面存储架构契约](../../../architecture/desktop-creation-workspace-contract.md)。
- Android 需求依据（修正相对路径）：
  - [Android 八组需求 2026-09-09](../../../plans/android-parallel-delivery-2026-09-09/README.md)；
  - [本地书库路线 2026-09-12](../../../plans/android/2026-09-12-android-library-folder-smart-recognition-roadmap.md)；
  - [书架成熟化 2026-09-18](../../../plans/android/2026-09-18-android-shelf-maturity-plan.md)。

---

## 所有权与改动

- **所有权边界**：仅写 `E:\creation-reading-assistant\next-round-d1-desktop-scope\` 与本报告 `docs/coordination/dispatches/2026-10/2026-10-01-next-d1-desktop-ownership-and-m0-v2.md`。
- **严格只读原则**：
  - 未修改桌面产品源码（`src/`、`electron/`）、测试文件或构建配置；
  - 未修改公共账本、`package.json`、lockfile、当前交接入口（`docs/handoff/current.md`）或历史报告；
  - 未修改原 M0 资产 `E:\creation-reading-assistant\m0-product-scope-20261001\`；
  - 零 ADB / 设备操作，零网络服务启动，未执行 `git commit/push/reset/checkout`。

---

## 环境

- Windows 11，Python 3.13.2，PowerShell；
- 零 ADB / 零设备操作（MuMu 与 WebDAV 服务由其他窗口独占）；
- 只读解析 TypeScript/TSX 源码、Preload IPC 暴露层、SQLite 表结构、`package.json` 脚本与历史 Markdown 验收日志。

---

## 执行与结果

### 一、G6 六条桌面旅程从用户入口到持久化的深度穿透与归属核定

原 M0 报告中，G6-01 至 G6-06 均笼统标记为“历史已交付但当前版本归属未确认”，理由是“未见 `release-beta-stage4-final` 打包工件”。本次只读穿透审计沿 **用户交互入口（UI TSX） → IPC 桥接层（Preload） → 主进程处理与数据库（Main & SQLite） → 历史验收与测试契约** 进行了逐链路核查，明确其归属与五类状态界定。

#### 1. G6-01 桌面新建项目/卷章场景/正文编辑与自动保存
- **用户交互入口**：
  - 新建作品向导：[src/features/creation/CreateProjectWizard.tsx](../../../../src/features/creation/CreateProjectWizard.tsx)（13,368 字节，SHA-256 `6e20b54c...`）；
  - 写作台与章节编辑器：[src/features/creation/editor/WritingDesk.tsx](../../../../src/features/creation/editor/WritingDesk.tsx)（16,774 字节，SHA-256 `4daab918...`）、[ContinuousChapterEditor.tsx](../../../../src/features/creation/editor/ContinuousChapterEditor.tsx)（5,477 字节，SHA-256 `e5a1e387...`）；
  - 正文会话与自动保存：[src/features/creation/editor/scene-document-session.ts](../../../../src/features/creation/editor/scene-document-session.ts)（9,105 字节，SHA-256 `6c6fef56...`）。
- **IPC 桥接层**：[electron/preload/index.ts](../../../../electron/preload/index.ts) 暴露 `creation.createProject`、`creation.readSceneBody`、`creation.updateSceneBody`、`creation.watchProject`。
- **主进程与持久化**：
  - 目录树结构管理：[electron/main/creation-workspace/structure.ts](../../../../electron/main/creation-workspace/structure.ts)（59,436 字节，SHA-256 `3f2b3356...`）；
  - 存储工厂与 SQLite：[electron/main/creation-workspace/index.ts](../../../../electron/main/creation-workspace/index.ts)（340,491 字节，SHA-256 `9cf71a1c...`），持久化至 `projects`、`volumes`、`chapters`、`scenes`、`scene_documents` 表。
- **历史验收与契约**：
  - 契约验证：`scripts/verify-creation-workspace.mjs`、`scripts/verify-creation-editor.mjs`；
  - 打包态端到端验收：`scripts/packaged-stage3-outline-focus-acceptance.mjs`（18/18 项全过）；
  - 历史记录：v2.0 需求文档 §3.1 明确记录三步向导、场景输入、`Ctrl+S` 保存、进程重启读回均通过。
- **状态界定**：**当前实现已确认，历史打包验收记录完备**。恢复仓库缺编译二进制工件，需按需通过 `npm run dist:beta:stage4` 补产物，无需重新开发。

#### 2. G6-02 桌面全局卡片库/关系/全局附件与封面（v10 全局资产 + 项目投影）
- **用户交互入口**：
  - 全局卡片库与项目卡片：[src/features/creation/cards/GlobalCardLibraryPage.tsx](../../../../src/features/creation/cards/GlobalCardLibraryPage.tsx)（15,724 字节，SHA-256 `9ba8719a...`）、[CardsPage.tsx](../../../../src/features/creation/cards/CardsPage.tsx)（26,037 字节，SHA-256 `44271afe...`）；
  - 封面与缩略图：[src/features/creation/cards/components/CardCoverImage.tsx](../../../../src/features/creation/cards/components/CardCoverImage.tsx)（2,217 字节，SHA-256 `55c27853...`）、[CardCoverThumb.tsx](../../../../src/features/creation/cards/components/CardCoverThumb.tsx)（1,841 字节，SHA-256 `1d7beb74...`）；
  - 项目关联弹窗：[src/features/creation/cards/ProjectCardLinkDialog.tsx](../../../../src/features/creation/cards/ProjectCardLinkDialog.tsx)（3,713 字节，SHA-256 `5354ef1a...`）。
- **IPC 桥接层**：[electron/preload/index.ts](../../../../electron/preload/index.ts) 暴露 `creation.cardsList`、`creation.cardLink`、`creation.cardUnlink`、`creation.cardRelations`、`creation.resourceList`、`creation.attachResource`。
- **主进程与持久化**：
  - 数据模型与架构：[electron/main/creation-workspace/global-card-migration.ts](../../../../electron/main/creation-workspace/global-card-migration.ts)（17,718 字节，SHA-256 `afe4a9ae...`）将 SQLite 升至 v10，引入 `global_cards`、`project_card_links`、`global_card_relations`；
  - 资源与附件持久化：[electron/main/creation-workspace/resource.ts](../../../../electron/main/creation-workspace/resource.ts)（11,097 字节，SHA-256 `00a8cf1a...`），落盘于 `global_card_resources` 表与 `resources/cards/<cardId>/` 物理目录。
- **历史验收与契约**：
  - 契约验证：`scripts/verify-global-card-library.mjs`、`scripts/verify-global-card-migration.mjs`；
  - 真机验收：`scripts/card-cover-acceptance.mjs`（列表缩略图真机 8/8 通过，`naturalWidth: 1700` 证实字节由 `creation-asset:` 协议成功加载）；
  - 历史记录：v2.0 需求文档 §5 记录 M1-A 到 M1-G 全量通过，Stage 1 打包态验收 17/17 通过。
- **状态界定**：**当前实现已确认，历史打包验收记录完备**。恢复仓库缺编译二进制工件，无需重新开发。

#### 3. G6-03 桌面大纲/沉浸写作/统计（场景摘要与状态/卷章全书字数/Markdown 大纲/壳级专注）
- **用户交互入口**：
  - 大纲树与卡板视图：[src/features/creation/outline/OutlinePage.tsx](../../../../src/features/creation/outline/OutlinePage.tsx)（15,108 字节，SHA-256 `c6246fc2...`）、[OutlineTree.tsx](../../../../src/features/creation/outline/OutlineTree.tsx)（29,010 字节，SHA-256 `40c9954a...`）、[CardBoard.tsx](../../../../src/features/creation/outline/CardBoard.tsx)（16,132 字节，SHA-256 `102d5482...`）；
  - 专注模式壳层：[src/components/layout/DesktopFrame.tsx](../../../../src/components/layout/DesktopFrame.tsx)（8,698 字节，SHA-256 `efcc3b3e...`），支持 `.desktop-root--focus` 隐藏导航栏与侧栏，ESC 键退出；
  - 写作统计页：[src/features/creation/stats/StatsPage.tsx](../../../../src/features/creation/stats/StatsPage.tsx)（15,676 字节，SHA-256 `ad10d305...`），展示 30 天趋势折线与场景完成度。
- **IPC 桥接层**：[electron/preload/index.ts](../../../../electron/preload/index.ts) 暴露 `creation.readProjectOutline`、`creation.runStructure`、`creation.statsView`、`creation.exportDraft`。
- **主进程与持久化**：
  - 场景状态与摘要：[electron/main/creation-workspace/structure.ts](../../../../electron/main/creation-workspace/structure.ts)（SQLite v11 增加 `scenes.summary` 与 `scenes.scene_status`）；
  - 统计会话聚合：[electron/main/creation-workspace/scene-stats.ts](../../../../electron/main/creation-workspace/scene-stats.ts)（1,553 字节，SHA-256 `2548dc60...`），无独立字数真源，完全从正文与写作会话计算。
- **历史验收与契约**：
  - 打包态端到端验收：`scripts/packaged-stage3-outline-focus-acceptance.mjs`（18/18 项全过，修复了专注模式 grid 坍塌与大纲双栏容器滚动等真实几何缺陷）；
  - 历史记录：v2.0 需求文档 §5 记录开发门禁 78/78、Beta 819 项通过、打包产物验证通过。
- **状态界定**：**当前实现已确认，历史打包验收记录完备**。恢复仓库缺编译二进制工件，无需重新开发。

#### 4. G6-04 桌面 AI 候选/校对/关系图（续写/精简/角色一致性/位置级忽略/关系可视化）
- **用户交互入口**：
  - AI 评审面板：[src/features/creation/ai/SceneCandidateReview.tsx](../../../../src/features/creation/ai/SceneCandidateReview.tsx)（4,272 字节，SHA-256 `0a4232b4...`）、[build-ai-context.ts](../../../../src/features/creation/ai/build-ai-context.ts)（5,731 字节，SHA-256 `1004d4c2...`），严格要求 AI 结果只进候选评审、绝不直接静默覆写正文；
  - 本地校对面板：[src/features/creation/proof/ProofPanel.tsx](../../../../src/features/creation/proof/ProofPanel.tsx)（11,061 字节，SHA-256 `81988eaf...`）、[proof-ignore.ts](../../../../src/features/creation/proof/proof-ignore.ts)（3,814 字节，SHA-256 `76b4c268...`），支持按位置忽略（`rule#paragraphIndex#fnv1a32(normText)`）；
  - 关系图谱可视化：[src/features/creation/cards/RelationGraphView.tsx](../../../../src/features/creation/cards/RelationGraphView.tsx)（10,460 字节，SHA-256 `bc34430b...`）、[relation-graph.ts](../../../../src/features/creation/cards/relation-graph.ts)（7,836 字节，SHA-256 `21f23262...`），确定性按卡片类型分簇环形布局。
- **IPC 桥接层**：[electron/preload/index.ts](../../../../electron/preload/index.ts) 暴露 `creation.proofQuery`、`creation.proofIgnoreList`、`creation.relationGraph`。
- **主进程与持久化**：
  - 批注与校对持久化：[electron/main/creation-workspace/annotation.ts](../../../../electron/main/creation-workspace/annotation.ts)（18,482 字节，SHA-256 `e7af64d0...`）、[proof-contract.ts](../../../../electron/main/creation-workspace/proof-contract.ts)（23,514 字节，SHA-256 `bf1d2f9f...`）。
- **历史验收与契约**：
  - 真实集成验收：`scripts/scene-ai-acceptance.mjs`（25/25 真机通过）、`scripts/proof-ignore-acceptance.mjs`（位置级忽略 PASS）、`scripts/relation-graph-acceptance.mjs`（20/20 真机 + 16/16 契约通过）；
  - 打包态验收：`scripts/packaged-stage4-acceptance.mjs`（12/12 覆盖 Stage 4 全部 IPC 通道）。
- **状态界定**：**当前实现已确认，历史打包验收记录完备**。恢复仓库缺编译二进制工件，无需重新开发。

#### 5. G6-05 桌面导入导出与项目包（TXT/MD/DOCX 预览/.cra 稳定 ID 冲突/加密包）
- **用户交互入口**：
  - 导入导出向导：[src/features/creation/import/ImportDraftDialog.tsx](../../../../src/features/creation/import/ImportDraftDialog.tsx)（6,459 字节，SHA-256 `33490c3a...`）、[ExportDraftDialog.tsx](../../../../src/features/creation/export/ExportDraftDialog.tsx)（3,499 字节，SHA-256 `388d4572...`）；
  - 全书预览与打印页：[src/features/creation/preview/PreviewPage.tsx](../../../../src/features/creation/preview/PreviewPage.tsx)（8,182 字节，SHA-256 `adc66fa7...`）；
  - 卡片导入导出对话框：[src/features/creation/cards/import-export/CardImportDialog.tsx](../../../../src/features/creation/cards/import-export/CardImportDialog.tsx)（16,266 字节，SHA-256 `7acf7e6a...`）。
- **IPC 桥接层**：[electron/preload/index.ts](../../../../electron/preload/index.ts) 暴露 `creation.exportProjectBundle`、`creation.importProjectBundle`、`creation.printProject`、`creation.cardImportApply`。
- **主进程与持久化**：
  - 项目包引擎：[electron/main/creation-bundle/index.ts](../../../../electron/main/creation-bundle/index.ts)（22,055 字节，SHA-256 `66bbcfaf...`）；
  - 稳定 ID 冲突仲裁：[electron/main/creation-card-io/card-import-apply.ts](../../../../electron/main/creation-card-io/card-import-apply.ts)（9,864 字节，SHA-256 `96af7310...`），同内容自动复用、异内容弹窗选择保留/副本/取消，禁止静默覆盖。
- **历史验收与契约**：
  - 契约验证：`scripts/verify-creation-bundle.mjs`、`scripts/verify-creation-bundle-files.mjs`；
  - 真实打印验收：`scripts/packaged-stage4b-print-acceptance.mjs`（12/12 通过，真实写出 402,151 字节 PDF，magic `%PDF-`）。
- **状态界定**：**当前实现已确认，历史打包验收记录完备**。恢复仓库缺编译二进制工件，无需重新开发。

#### 6. G6-06 桌面旧数据迁移与灵感收件箱（旧灵感/书库/阅读数据兼容期，不复制成项目卡）
- **用户交互入口**：
  - 灵感收件箱页：[src/features/creation/inbox/InboxPage.tsx](../../../../src/features/creation/inbox/InboxPage.tsx)（23,528 字节，SHA-256 `742af868...`）、转为卡片对话框 [InboxConvertToCardDialog.tsx](../../../../src/features/creation/inbox/components/InboxConvertToCardDialog.tsx)（4,924 字节，SHA-256 `781dbf6d...`）；
  - 迁移对话框：[src/features/creation/migration/MigrationDialog.tsx](../../../../src/features/creation/migration/MigrationDialog.tsx)（5,077 字节，SHA-256 `ae15218a...`）。
- **IPC 桥接层**：[electron/preload/index.ts](../../../../electron/preload/index.ts) 暴露 `creation.inboxList`、`creation.inboxCount`、`creation.inboxUpdate`、`creation.migrationRun`。
- **主进程与持久化**：
  - 收件箱存储：[electron/main/creation-workspace/inbox.ts](../../../../electron/main/creation-workspace/inbox.ts)（15,377 字节，SHA-256 `87208f7e...`）；
  - 旧数据迁移模块：[electron/main/creation-migration/index.ts](../../../../electron/main/creation-migration/index.ts)（15,785 字节，SHA-256 `67baca11...`）。
- **历史验收与契约**：
  - 契约验证：`scripts/verify-creation-inbox-count.mjs`、`scripts/verify-creation-migration-audit.mjs`；
  - 历史记录：v2.0 需求文档 §5 阶段 0 记录 M0.1–M0.4 迁移夹具完整往返通过。
- **状态界定**：**当前实现已确认，历史验收记录完备**。恢复仓库缺编译二进制工件，无需重新开发。

#### 缺少真实打包工件的原因与构建所有权建议
- **原因**：`release-beta-stage4-final/`（约 188MB）为编译生成的解包二进制目录，按标准软件工程实践受 `.gitignore` 保护，不入 Git 仓库。
- **对应构建与验收脚本**：
  - 构建产物脚本：`npm run dist:beta:stage4`（`package.json` 第 86 行：`npm run build && electron-builder --dir --config.directories.output=release-beta-stage4-final --publish never`）；
  - 打包验收脚本：`npm run verify:packaged-stage4`（`node scripts/packaged-stage4-acceptance.mjs`）。
- **最小所有权与操作边界**：桌面端所有权由单一桌面集成者持有。当前源码完整且契约完备，**严禁为了“补齐工件”盲目重写代码或打断当前移动端/数据安全任务**；后续若需重新产出桌面发布包，由集成者在独立打包窗口统一执行构建即可。

---

### 二、M0-v2 纠正矩阵关键变更与前后对照

依据 Codex review 裁决与核查事实，产出了 M0-v2 纠正矩阵（`m0-journey-matrix-v2.csv`）与证据索引（`m0-evidence-index-v2.csv`），完成六大关键纠偏：

| 纠正项目 | 原 M0-v1 状态 | M0-v2 纠正后状态 | 纠正依据与说明 |
|---|---|---|---|
| **1. 门禁与分母剥离** | G7-04（MuMu 验证方式）与 G8-01（构建冻结关卡）列入“是（必需项）”，分母误计为 28 | G7-04、G8-01 标注为“工程/验证门禁（不计入功能分母）”，**产品功能分母重算为 26 项** | G7-04 自注“验证方式不是产品旅程”，G8-01 为构建门禁；工程关卡不可与用户功能旅程混为一谈 |
| **2. G5-02 能力表述** | 标为“WebDAV 独立 UI 宿主（UI 包隔离 qahostwebdav，七 WebDAV UI 用例）” | 纠偏为“**云端与 WebDAV 备份/恢复/导入**（支持 WebDAV 远端备份、历史版本恢复与 Legado/JSON 格式导入）” | 测试包 `qahostwebdav` 仅为测试资产，产品功能本质是面向用户的云端备份/恢复/导入能力 |
| **3. G2-02 缺陷定性** | 标为“已证实缺陷（WorkBuddy 滚动TXT替换复验 FAIL）” | 纠偏为“**历史缺陷，当前候选待核**（非当前已证实缺陷）” | 仅引用 2026-09-09 旧记录，未在 J1-v3 候选上同路径证实失败；不按当前已证实缺陷排新开发 |
| **4. W2 验收状态** | 标为“交付缺失（无可核查交付，evidence/logs 为 0）” | 引用最新 [Codex-review-W2-M0-v1](../../reviews/2026-10/2026-10-01-next-w2-m0-codex-review.md)：**Case 2/6 已验，Case 1/3/7 部分成立，Case 4/5 需补，构建+设备 1/2=50%** | 剔除过时的“无执行证据”断言，实事求是记录实际设备图/SQL 证据与剩余定向缺口 |
| **5. 修复断链** | 引用 `android-parallel-delivery-2026-09-09/README.md` 等，相对路径脱落 | 全面修复为 `docs/plans/android-parallel-delivery-2026-09-09/README.md` 等 3 处有效路径 | 解决从 `docs/coordination/` 下访问时的相对路径失效问题 |
| **6. G6 归属细化** | 笼统标记为“当前版本归属未确认” | 细化为“**当前实现已确认，历史打包验收完备；恢复仓库缺编译二进制工件**” | 源码、IPC 桥接、存储工厂与 963 项契约完备存在，明确指向 `npm run dist:beta:stage4` |

---

### 三、分母重新计数与前后对比（严格分类统计，无猜测百分比）

#### 1. 结构与行数统计
- **总行数**：保持 34 条用户旅程/工程条目（G1–G8 八域）。
- **结构划分对比**：
  - **M0-v1**：标“是（当前明确必需）”28 项，标“待产品决定”6 项。
  - **M0-v2 纠正后**：
    - **产品功能旅程分母**：**26 项**（原 28 项剔除 G7-04 与 G8-01 两项工程门禁）；
    - **工程与验证门禁行（独立统计，不计入产品功能分母）**：**2 项**（G7-04, G8-01）；
    - **待产品决定项（独立统计，非当前功能阻塞）**：**6 项**（G7-01, G7-02, G7-03, G8-02, G8-03, G8-04）。

#### 2. 产品功能旅程（26 项）当前状态细分表

| 状态类别 | 项数 | 具体旅程 ID | 说明 |
|---|---:|---|---|
| **已限定验收 / 历史验收已核（可复用）** | 2 | G4-01, G5-01 | G4-01 E 限定备份恢复 PASS；G5-01 LAN 同步限定实现已核（Room 4/4=100%） |
| **当前实现已确认，历史打包验收完备（待按需补构建产物）** | 6 | G6-01, G6-02, G6-03, G6-04, G6-05, G6-06 | 桌面 v2.0 阶段 0–4 源码、契约与历史打包验收记录完备，无需重新开发 |
| **历史已交付但当前版本归属未确认（需候选内核对/补证据）** | 6 | G1-01, G1-02, G1-03, G1-04, G2-03, G3-04 | 书库第 1–3 组、书架 P0、纠错、灵感回顾等历史验收基于旧工作树，需核对 J1-v3 归属 |
| **当前候选有实现待验证（有源码，需补定向验证）** | 8 | G2-01, G2-04, G2-05, G3-01, G3-02, G3-03, G3-05, G3-06 | 选区、阅读内核、笔记统一入口、索引搜索、设置、TTS 等需排入后续 M3 批次定向验证 |
| **部分闭合 / 待补定向证据** | 1 | G5-02 | 云端/WebDAV 备份恢复，W2 实际执行 Case 2/6 已验，1/3/7 部分成立，4/5 需补测 |
| **跨端真实兼容待定** | 1 | G5-03 | 依赖桌面候选与跨端协议冻结，需在 M3 单独立项 |
| **历史缺陷，当前候选待核（非当前已证实缺陷）** | 1 | G2-02 | 滚动 TXT 替换，2026-09-09 旧记录，需在 J1-v3 候选上复核，不排新开发 |
| **真实缺陷（已证实，需修复）** | 1 | G4-02 | N1 中断恢复与残留导出防护（整包 DB+文件原子性不成立，staging 残留需修复） |
| **合计功能分母** | **26** | — | **严格拒绝把不同口径相加成全产品完成百分比** |

---

## 原始证据

- 产出工件（E 盘）：
  - 矩阵 V2：`E:\creation-reading-assistant\next-round-d1-desktop-scope\m0-journey-matrix-v2.csv`（34 行 × 13 列）；
  - 索引 V2：`E:\creation-reading-assistant\next-round-d1-desktop-scope\m0-evidence-index-v2.csv`（28 行 × 8 列，含 EV01–EV28）。
- 桌面端核心源码与哈希证据（恢复仓库内）：
  - `electron/main/creation-workspace/index.ts`（340,491 字节，SHA-256 `9cf71a1ca22ddd3f9484b27427c039eb497c85f674114a59e63c72fc39c1e63c`）；
  - `electron/main/creation-workspace/structure.ts`（59,436 字节，SHA-256 `3f2b33563758bd4d8ccb3e5193a6e8ac86d71bcac9d10e6780f75bff876d5ae6`）；
  - `electron/main/creation-workspace/global-card-migration.ts`（17,718 字节，SHA-256 `afe4a9ae4b81439bd48d1ef085a8c2a0635becd8a47bb7d07f2a424e9840cb1c`）；
  - `electron/main/creation-workspace/global-card-library-contract.ts`（25,682 字节，SHA-256 `5968c082dd7da88011c290a0536525464c483624a37198fba6412de913e36ed6`）；
  - `electron/main/creation-workspace/resource.ts`（11,097 字节，SHA-256 `00a8cf1aef1cba200df90055ca3657c4600a1c9375d8d149e1e1c7db042b471a`）；
  - `electron/main/creation-bundle/index.ts`（22,055 字节，SHA-256 `66bbcfafae1da6e71e21d94dee4c51bf920334ea0dafc02861ecdf5bba044293`）；
  - `electron/main/creation-card-io/card-import-apply.ts`（9,864 字节，SHA-256 `96af7310274c3ee5f48a610d75075437eb4413be24dfacf33c1cbd2b36ca2107`）；
  - `electron/main/creation-workspace/inbox.ts`（15,377 字节，SHA-256 `87208f7ef64e15341de74f063f00cb6e15cd742942ca6daa3c09e66c16634b76`）；
  - `electron/main/creation-migration/index.ts`（15,785 字节，SHA-256 `67baca1190759a35c2c54ce4f64253dcf150df96bda4d87f06923cb7b21fd8f3`）；
  - `electron/preload/index.ts`（20,348 字节，SHA-256 `e08be14022646b9bbda9fcf2dca58a5da9826d97c55498eb1011689ea52bb4a2`）；
  - `scripts/packaged-stage4-acceptance.mjs`（15,732 字节，SHA-256 `c6a736cdf91d600fd5577c58bcda117dcf8ccc158ce803fb11c4881257f8cd90`）；
  - `scripts/packaged-stage3-outline-focus-acceptance.mjs`（17,980 字节，SHA-256 `354af921d1aa075055a26e7ce087ae2ad312479b84456a2d6cb93f3dc8dffa49`）。

---

## 未覆盖项

- **未重跑打包与端到端测试**：本任务遵守只读审计约束，未执行 `npm run dist:beta:stage4`（不生成数百兆解包产物），未在本地运行 Playwright 打包态测试。
- **未改动 Android 代码与候选**：未修改 J1-v3 候选或恢复仓库 Android 源码。
- **未覆盖待产品决定项**：G7-01（TalkBack/适配）、G7-02（实体真机）、G7-03（R8 实际运行）、G8-02（Release 签名链）、G8-03（release.yml Android 运行）、G8-04（F 发布）均保持待产品决定状态。

---

## 恢复与副作用

- 本任务为纯只读审计与协调产出任务，零设备副作用（0 ADB、0 设备操作）；
- 未运行任何 WebDAV 服务或后台进程；
- Git HEAD 严格保持 `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区完全干净；
- 恢复仓库除新增本报告外无任何受控文件变动，原 WIP 与 HOLD 资产完好无损。

---

## 交付状态与下一步

### 1. 交付状态
D1-v1 任务顺利完成：
1. 澄清并确立了 G6 六条桌面旅程在恢复仓库中的完整源码与契约归属，彻底推翻了“桌面功能缺失需重新开发”的误判；
2. 产出了纠正后的 M0-v2 矩阵与证据索引，排除了工程门禁对功能分母的污染，纠偏了 G5-02、G2-02 与 W2 的现体现态，并修复了 Android 需求相对断链。

### 2. 下一步最有价值的实际实施缺口与最小所有权建议
按照产品整体优先级与依赖关系，建议按以下最小所有权串行推进：

1. **第一优先级（P0，数据安全唯一真实缺陷修复）：N1 中断恢复与残留导出防护**
   - **缺口**：kill 中断后整包 DB+文件原子性不成立，自动收敛失败，且残留 staging/backup 存在混入导出备份的污染风险（已由真实证据证实）。
   - **最小所有权建议**：由单一所有者（独立数据/存储修复窗口）持有，修改范围限定在 `feature/sync/*` 与备份写路径，实现“中断状态可识别、重试不污染数据、残留物理隔离”。
2. **第二优先级（P0，设备验收收口）：W2-accept-v2 定向补测与恢复后账**
   - **缺口**：Case 1 UI 状态区分、Case 3 PUT401/403 反馈、Case 4 本地前拒绝信息、Case 5 真实 App 路径证明及 Case 7 原始对账后账。
   - **最小所有权建议**：由原设备 owner 独占 MuMu 执行定向补测，复用已通过的 Case 2/6 结果，不重新构建 APK，不跨盘覆盖数据。
3. **桌面端维护建议（低优先级，非当前阻塞）：保持当前源码完备基线**
   - G6 六项桌面功能源码完备且契约健全，**绝不排入当前重新开发队列**。
   - 未来若需进行桌面发布验证，由单一集成者在独立打包窗口统一执行 `npm run dist:beta:stage4` 生成 `release-beta-stage4-final`，并运行 `verify:packaged-stage4` 验收。
