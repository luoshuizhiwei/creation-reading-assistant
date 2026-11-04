# 阶段 E — Git 健康入口与提交执行方案（修订版 2，2026-09-29）

> **性质声明：本文件是调查结果与可执行方案，不是执行令。**
> 未 stage、commit、push、reset、checkout、revert、clean；未移动或删除目录；未运行构建；未操作设备。
> 唯一写入文件即本文件（窗口 3）。窗口 1（`2026-09-29-stage-e-android-hunk-plan.md`）与窗口 2（`2026-09-29-stage-e-asset-reconciliation.md`）正在按[三报告复核](../../reviews/2026-09/2026-09-29-stage-e-three-report-review.md)并行修订，本文件不覆盖其内容。

> **修订版 2 变更摘要**（针对三报告复核 §3「Git 方案不可直接执行」逐项修正）：
> 1. **删除一切以 `git reset HEAD~1`、checkout 或移动目录作为默认失败回退的步骤**（原 4a–4g 的"失败停止点"与 6d"改回原名"式回退全部移除）。新增统一失败处理协议（§3）：停止 → 保留现场 → 记录证据 → 窄修或单独授权方案。
> 2. 新增**提交前增量保护范围**（§4）：按资产报告 §3 的旧备份缺口列出提交前必须补的保护，并明确时点快照 ≠ 持续备份。
> 3. **验证与实际改动相称**（§6）：取消逐提交重跑全量 JVM / V5 / APK 构建；树级证据按"同输入"条件适用；大型构建与产物规划到 E 盘。
> 4. 原仓库表述修正：`blob:none` partial clone **当前离线缺对象** + 本地 reflog/cache-tree 损伤行，**不据此宣称远端历史永久损坏**（§0.2、§2.4、§7）。
> 5. 与窗口 1/2 相关的**最终提交顺序、旧提交等价性、目录处置**标注"待其报告"（§5 步骤 1/3、步骤 5/6、§7）；不再要求用户对可通过只读检查查明的事实先行裁决（原步骤 1 重构，`2a428851` 不得凭单个 contentType 命中推断等价）。

---

## 0. 结论先行

1. **恢复仓库 Git 健康，可作为最终开发入口候选。** HEAD `5aed5939600e491843e3c44453921bd6273a233c` = `main` = `origin/main`（本轮逐 ref 复核均解析为 `5aed593`），fsck `--full --no-reflogs --connectivity-only` 零输出、退出码 0。全部恢复工作（本轮实测 porcelain **544 条：260 改性 + 284 未跟踪**；随并行窗口活动漂移）在工作树中未提交。
2. **原仓库为 `blob:none` partial clone（`promisor=true`，`partialclonefilter=blob:none` 本轮复核确认），当前离线状态下 HEAD 树存在缺失对象**（本轮 400 个；preflight 时 425——差异来自 partial clone 对部分 blob 的惰性补取），且本地存在 reflog / cache-tree / broken-link 损伤行（fsck 4739 行）。**这些观察与"离线 + partial clone"的机制一致：部分缺对象可经联网 fetch 惰性补全（未验证，当前无网络），现有证据不证明远端历史永久损坏。** 准确结论是：当前离线状态不适合作为集成基础；原仓库目录及其独有资产须原样保留。HEAD `e740ef6d`（main）比 `origin/main` 多 2 个未推送提交（§2.4）。
3. **E 盘 preflight 快照在位且 SHA 匹配——但它们是 2026-09-29 02:09~02:10 的时点快照，不是持续备份。** 资产报告 §3 已列出快照未覆盖的缺口（81 个未跟踪文件、docs tar 子目录覆盖缺口、tracked patch 缺 `AppLog.kt`、preflight 后新增/变更文档），提交前必须先补增量保护（§4）。
4. **阶段 E 目前不可执行提交。** 阻塞：① 窗口 1 的完整 hunk 矩阵与 G8/B4/B5/B8/B9 拆分仍在补正，最终提交顺序待其修订报告；② 原仓库 2 个未推送提交的等价性对账待窗口 2（`2a428851` 离线不可读，单个 contentType 命中不构成等价证据）；③ 收口计划 §E 要求用户明确授权后才 stage/commit。

---

## 1. 调查对象与路径

| 角色 | 路径 | 分支 | HEAD |
|------|------|------|------|
| 恢复仓库 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` | `codex/recover-worktree-20260920` | `5aed5939` |
| 原仓库 | `D:\develop\Code\Codex\creation-reading-assistant` | `main` | `e740ef6d` |
| E 盘快照 | `E:\creation-reading-assistant\codex-e-preflight-20260929\` | — | — |

上游约束：
- [收口计划 §E](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md)（第 90–100 行）
- [AGENTS.md](../../../../AGENTS.md)（文件所有权、并行协作）
- [三报告复核](../../reviews/2026-09/2026-09-29-stage-e-three-report-review.md)（本轮修订依据）
- [入口清单](2026-09-29-stage-e-entry-inventory.md)（543 条资产分类）
- [hunk 拆分计划](2026-09-29-stage-e-android-hunk-plan.md)（窗口 1，修订中）
- [资产对账报告](2026-09-29-stage-e-asset-reconciliation.md)（窗口 2，修订中）
- [D 限定退出复核](../../reviews/2026-09/2026-09-29-stage-d-limited-exit-review.md)、[E 入口复核](../../reviews/2026-09/2026-09-29-stage-d-s05-e-preflight-review.md)

---

## 2. 原件核查结果

### 2.1 E 盘快照 SHA-256（本轮独立计算）

| 文件 | SHA-256 | 大小 | 与 preflight 记录匹配 |
|------|---------|------|----------------------|
| `recovery-docs-preE.tar.gz` | `efa8b6eb...07b408` | 867,607,990 B | ✓ |
| `recovery-tracked-worktree.patch` | `567b6d31...8f3864` | 3,049,776 B | ✓ |
| `untracked-source-preE.tar.gz` | `f41c318b...26f2d84` | 328,486 B | ✓ |
| `original-head-missing.txt` | `0211932b...5c5fc5` | 18,275 B | ✓ |
| `original-allrefs-missing.txt` | `545bed42...7737d` | 20,855 B | ✓ |
| `original-fsck.txt` | `52228390...44752c` | 270,562 B | ✓ |
| `original-recovery-file-diff.csv` | `e307b996...20343` | 74,873 B | ✓ |
| `android-source-candidate-inventory.csv` | `e94c2a38...fdbe9` | 64,765 B | ✓ |
| `original-only-preserved/.../bundle-merge.ts` | `7df2971b...ddcc99` | 8,803 B | ✓ |
| `original-only-preserved/.../proof-engine.ts` | `4674f09c...89857d` | 17,135 B | ✓ |
| `original-only-preserved/.../validate.ts` | `fe12bcf0...d2ea7` | 5,226 B | ✓ |

> 以上均为**时点快照**。它们与当前工作树的差异（含缺口）见 §4；不能把"SHA 与 preflight 记录一致"理解为"备份覆盖了现状"。

### 2.2 tracked patch reverse-apply 检查（含资产报告补充）

- patch 含 259 个 `diff --git`（258 文本 + 1 二进制 `smoke-final.png`）
- 逐文件 `git apply --reverse --check`：**258 通过，1 失败**
- 唯一失败：`docs/handoff/current.md`（在 preflight 后被 D 限定退出复核更新，属预期漂移）
- **资产报告补充的缺口**：工作树已修改 260 个文件，patch 只含 259 个——`android/app/src/main/java/com/creationreadingassistant/feature/log/AppLog.kt`（3,258 B）**不在任何备份中**（§4）
- **结论：** patch 是 259 个文件的 preflight 时点快照；`current.md` 与 `AppLog.kt` 需按 §4 增量保护补齐

### 2.3 恢复仓库 Git 状态（2026-09-29 本轮复核）

| 项 | 值 |
|----|-----|
| HEAD | `5aed5939600e491843e3c44453921bd6273a233c` |
| 分支 | `codex/recover-worktree-20260920` |
| `main` / `origin/main` | 均解析为 `5aed593`（= HEAD，逐 ref 复核） |
| ahead/behind origin/main | 0 / 0 |
| porcelain | **544 条（260 M + 284 ??，本轮实测；随并行窗口活动漂移）** |
| `android/app/src` 子集 | 234 改性 + 126 未跟踪（本轮实测；三报告复核时为 129 未跟踪，存在漂移） |
| fsck `--full --no-reflogs --connectivity-only` | **零输出，退出码 0（健康，本轮复核）** |
| remote | `origin = https://github.com/luoshuizhiwei/creation-reading-assistant.git`（当前离线，未 fetch） |

### 2.4 原仓库 Git 状态（表述已按复核修正）

| 项 | 值 |
|----|-----|
| HEAD | `e740ef6daaddf055c2831e8c1b1f82f3c5222661` |
| 分支 | `main` |
| `origin/main` | `5aed5939`（≠ HEAD） |
| ahead/behind origin/main | **2 / 0（本轮复核）** |
| porcelain | 289 条（198 M + 91 ??） |
| partial clone | `promisor=true`，`partialclonefilter=blob:none`（本轮复核） |
| HEAD 缺失对象 | 本轮 400（preflight 时 425；差异来自 partial clone 惰性补取部分 blob） |
| fsck 错误行 | 4739（invalid reflog + invalid sha1 pointer in cache-tree + broken link） |

**准确表述**：原仓库是 `blob:none` partial clone，**当前离线**。缺失对象分两类——可经联网 fetch 惰性补全的 blob，与本地 reflog/cache-tree 层面的损伤行；fsck 错误行数本身不能区分两者占比，现有证据**不证明远端历史永久损坏**，也未知联网补全后可恢复到什么程度（联网验证属单独授权事项）。当前离线状态使其不适合作为集成基础；**不使用它作为开发入口则无需修复，目录原样保留**。

**原仓库 main 上 2 个未推送提交（本轮复核）：**

| commit | 日期 | message | 可读性 |
|--------|------|---------|--------|
| `2a428851` | 2026-09-15 | `perf(android-ui): declare contentType on mixed-type lazy lists` | **tree 对象缺失，离线不可读**；仅 commit message 提及 6 个 Android 文件名 |
| `e740ef6d` | 2026-09-27 | `feat(desktop): refine settings and search interface` | 29 文件（2839+/897-）tree 可读，可逐文件对账 |

**证据边界（不得据此宣布等价）：**
- `e740ef6d` 的 29 个文件中，3 个 Electron 文件（`electron/main/creation-workspace/{bundle-merge,proof-engine,validate}.ts`）在恢复仓库不存在（已存 E 盘 `original-only-preserved/`，SHA 已核）；其余 26 个桌面端 `src/` 文件在恢复仓库工作树有**路径级**对应改动——路径命中 ≠ 内容等价，逐文件 diff 尚未完成（待窗口 2，§5 步骤 1）。
- `2a428851`：恢复仓库 `SearchScreen.kt` 含 `contentType` 是**单文件、单特征命中**。commit message 的文件名清单不是 tree 证据；6 文件的恢复侧逐文件核对与等价判定**不可据此推断**，标"待窗口 2 报告 + 开放项"（§5 步骤 1、§7）。

### 2.5 原仓库 worktree 列表

| 路径 | HEAD | 分支 |
|------|------|------|
| `D:\develop\Code\Codex\creation-reading-assistant` | `e740ef6d` | `main` |
| `C:\Users\23254\.config\superpowers\worktrees\...android-reeden-mobile` | `c18d84ad` | `codex/android-reeden-mobile` |
| `D:\develop\Code\Codex\cra-a12-shelf-a11y-fix` | `edac6dc1` | `a12-shelf-a11y-fix` |
| `D:\develop\Code\Codex\cra-a12-shelf-accessibility` | `82aea7d4` | `codex/a12-shelf-accessibility` |
| `D:\develop\Code\Codex\cra-g0-gate` | `fd7f908e` | (detached) |
| `D:\develop\Code\Codex\cra-r3-gate` | `eebd59d8` | (detached) |
| `D:\develop\Code\Codex\cra-slice-verify` | `eebd59d8` | (detached) |

7 个 worktree 中 1 个在 `C:\Users\` 下（越出 `D:\develop\Code\Codex\`）。**其工作树内容是否含独有改动未对账**（资产报告 §4）——若未来做目录处置，这 7 个 worktree 须先逐一清点，属目录处置方案的输入（待其报告）。

### 2.6 路径依赖审计

| 检查项 | 结果 |
|--------|------|
| `android/local.properties` | `sdk.dir=D\:\\develop\\Android\\Sdk`（仓库外部，不随仓库路径变化） |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` 环境变量 | 均未设置 |
| `android/`、`scripts/`、`electron/` 配置中的仓库绝对路径 | **无命中** |
| `.codex/config.toml` | `workspace-write` + `network_access=true`（工具配置，非路径依赖） |

**结论：** 仓库内无绝对路径依赖。**原仓库保留原样时，恢复仓库可直接作为开发入口，不需要移动任何目录。** 换路径后唯一需重核的是 `local.properties` 与 IDE/Agent 项目路径设置。另注（资产报告 §5）：原目录 `.claude/settings.local.json` 含 `git checkout *` 允许项，**任何目录处置方案都不得原样复制该文件**。

### 2.7 其他窗口在途工作

| 窗口 | 唯一可写报告 | 当前状态 |
|------|--------------|----------|
| 窗口 1 | `2026-09-29-stage-e-android-hunk-plan.md` | 修订中（完整 hunk 矩阵、G8/B4/B5/B8/B9 拆分） |
| 窗口 2 | `2026-09-29-stage-e-asset-reconciliation.md` | 修订中（92 个 ORIG_UNIQUE 逐文件差异、原目录独有资产精确清单） |

两窗均只写各自报告，无源码改动；与本文件不重叠。

---

## 3. 失败处理协议（本方案所有步骤的统一约束）

**背景**：恢复工作树是**共享脏工作树**（544 条未提交、多窗口并行）。任何历史改写操作（`reset`/`checkout`/`revert`/`rebase`/`clean`）与目录移动在此状态下都可能波及他人现场。因此：

**禁止事项（无例外，除非用户对具体操作单独授权）**：
- 不以 `git reset HEAD~1`（或任何 reset 形式）作为提交失败的回退；
- 不以 checkout / worktree 切换作为验证或回退手段；
- 不以移动/重命名目录作为失败回退；
- 不使用 `git add -A` / `git add .`（会把 `.workbuddy-ai/`、`.zcode/`、`.opencode/`、`dump*.xml` 等一并带入——资产报告 §5 建议 5）。

**任一提交或验证失败时的固定动作（按序）**：
1. **停止**：不再执行任何 git 写操作；已产生的提交**保留在分支上不撤销**（提交本身不改变工作树内容，撤销它才是风险动作）。
2. **保留现场**：暂存区、工作树、失败命令的输出全部原样；不清理、不重试。
3. **记录证据**：失败命令原文与退出码、`git log -1 --stat`（如已产生提交）、`git diff --cached --name-status`（如暂存区非空）、`git status --porcelain` 全量输出，写入 E 盘事件目录（如 `E:\creation-reading-assistant\codex-e-entry-20260929\incidents\`），并同步到协调文档。
4. **定性**：区分（a）提交内容与授权清单不一致（清单级错误——可由**后续更正提交**修复，需新授权）；（b）验证失败（记录失败项，评估是否与该组改动因果相关）；（c）流程/环境故障（停止）。
5. **呈报**：给出窄修方案（最小、可单独复核、不改写历史）或请求单独授权；用户未裁决前不得继续后续批次。

---

## 4. 提交前增量保护范围（基于资产报告 §3 的旧备份缺口）

### 4.1 现有备份的缺口（资产报告 §3，2026-09-29 02:09~02:10 时点）

| # | 缺口 | 内容 | 后果（若不补） |
|---|------|------|----------------|
| G-1 | **81 个未跟踪文件未被两个 tar 覆盖** | `docs/code-review/` 18 个全部、`docs/research/` 6 个全部、`docs/coordination/` 4 个（含本文件与两窗报告）、`android/scripts/` 6 个、`.workbuddy-ai/memory/` 8 个、`.scratch-shots/` 3 个、`.zcode/plans/` 1 个、`.zcodeignore`、`.opencode/.../preference.json` 1 个、根目录 `dump*.xml` 35 个（资产报告口径） | 任何 `clean`/换目录即永久丢失 `docs/code-review`、`docs/research` 等 |
| G-2 | **docs tar 子目录覆盖缺口** | `recovery-docs-preE.tar.gz` 只含 `docs/{qa,coordination,plans,handoff}`；`docs/{code-review,research,architecture,archive,design,perf,testing,WorkBuddy}` 不在其内 | 同上 |
| G-3 | **tracked patch 缺 1 个已修改文件** | `AppLog.kt`（3,258 B）在 260 个已修改文件中，不在 259 个 `diff --git` 里 | 该文件的当前改动无任何备份副本 |
| G-4 | **preflight 后新增/变更文档** | 8 个 mtime 晚于快照的文档中 4 个完全未被备份（含两窗报告），另 4 个在 tar 内但是旧版本（`2026-09-22-current-task-ledger.md`、`2026-09-29-stage-e-entry-inventory.md`、`docs/handoff/current.md`、`docs/qa/2026-09/2026-09-29-stage-d-s05-three-ui-acceptance.md`） | 协调/验收记录的最近版本无备份 |

> 清单本身也是时点的：窗口 2 正在修订资产报告，且两窗报告与本文件都在持续产生新版本。**提交前必须重扫，不能照抄以上数字**（§4.3）。

### 4.2 提交前应补的增量保护（最小范围）

| # | 动作 | 说明 |
|---|------|------|
| P-1 | **增量快照 G-1 全部 81 个未跟踪文件** → E 盘新目录（tar + SHA-256 清单） | 覆盖 G-1/G-2 中未跟踪部分；`docs/code-review`、`docs/research` 优先 |
| P-2 | **增量快照 G-3 的 `AppLog.kt` 与 G-4 的 8 个文档**（按当前工作树版本） | 补齐 patch 与 tar 的两个洞 |
| P-3 | **提交序列开始前重扫全量状态**：`git status --porcelain` 全量输出与最近一次快照清单做差集，新增/修改/消失的文件逐项归因后并入增量快照 | 时点快照 ≠ 现状；差集是唯一能把"快照"升级为"当前保护"的手段 |
| P-4 | **每个提交批次前刷新增量快照**（仅对上一快照以来变化的文件） | 未提交余量在任何时刻只有最近一次增量快照保护，必须随批推进 |
| P-5 | **快照元数据**：每个 E 盘快照附 `status 时点 + 文件数 + 各文件 SHA-256` 清单 | 保证可校验、可对账，杜绝"不知快照对应哪个时刻" |

### 4.3 时点快照与持续保护的关系（如实声明）

- **任何 E 盘快照都只是时点副本**。在两次快照之间发生的改动不受保护；因此 P-4 的"每批次前刷新"是硬要求，不是可选项。
- **提交本身是更强的保护**：一旦一组内容按授权提交，它进入 git 对象库（受 HEAD/分支引用，fsck 健康仓库内持久存在）。因此"按授权尽早分批提交"本身是保护手段；但在**第一批提交落地前**，全部 544 条未提交内容只受最近一次增量快照保护——这是执行序列中保护最薄的窗口，P-1~P-3 必须先行。
- 提交完成后，工作树余量（未提交部分）继续按 P-4 保持增量快照，直到全部授权组提交完毕。

---

## 5. 执行方案（修订版：依赖项标注"待其报告"，失败处理见 §3）

> 以下步骤均需用户明确授权后才执行。与窗口 1/2 相关的顺序与清单**先标"待其报告"**；本节只固化与窗口无关的协议骨架。

### 步骤 0 — 增量保护（§4 的 P-1~P-3，提交序列的前置条件）

| 维度 | 内容 |
|------|------|
| **前置条件** | 用户授权写入 E 盘（仓库本身零写入） |
| **操作** | §4.2 P-1~P-3：重扫全量状态 → 差集归因 → 增量快照（G-1~G-4 全部缺口 + 差集）入 `E:\creation-reading-assistant\codex-e-entry-20260929\`，附 SHA 清单 |
| **预期证据** | 增量快照 + 覆盖对照表（缺口 G-1~G-4 逐项闭合或显式说明未闭合原因） |
| **失败停止点** | 快照写入失败或校验不一致 → 停止并报告；**不因快照问题动仓库** |
| **授权要求** | 写 E 盘授权 |

### 步骤 1 — 原仓库 2 个未推送提交的处置（重构：先对账事实，再请裁决）

| 维度 | 内容 |
|------|------|
| **1a 只读对账（事实层）** | ① `e740ef6d` 的 29 文件逐文件内容对账（恢复侧对应路径 vs 原提交 blob）——可离线完成，**待窗口 2 报告**（其"原目录独有资产精确清单"应覆盖此项；若未覆盖，由 Codex 汇总时指定执行窗）。② `2a428851`：原仓库侧 tree 缺失、**离线不可读**；恢复侧可按 commit message 的 6 个文件名逐个核对 contentType 特征，但**单个特征命中不构成等价**，且 message 文件名清单无 tree 佐证——结论标注"证据不可得（离线）"，列为**开放项**。联网 `git fetch` 补全原仓库对象可改变此局面，属**单独授权**事项（当前无网络）。 |
| **1b 用户裁决（取舍层）** | **仅在 1a 事实完成后**向用户展示逐文件差异表与具体取舍（并入恢复仓库 / 作为独立资产保留 / 放弃），不再要求用户对"可通过只读检查查明的事实"先行裁决。 |
| **当前状态** | **待窗口 2 报告。** 在其完成前，本步骤不产生任何用户询问。 |
| **失败停止点** | 对账中发现恢复侧缺失或差异超预期 → 记录证据，纳入 1b 差异表；不做任何写入。 |

### 步骤 2 — 3 个 original-only Electron 文件落位（待窗口 2）

| 维度 | 内容 |
|------|------|
| **前置条件** | **待窗口 2 报告**：原目录独有资产精确清单（确认 3 个文件之外是否还有应落位资产）→ 用户授权落位方式 |
| **操作（如授权复制）** | 从 E 盘 `original-only-preserved/electron/main/creation-workspace/` 复制到恢复仓库对应路径，逐文件 SHA-256 与 E 盘原件比对，`git status` 显示为未跟踪 |
| **失败停止点** | 任一 SHA 不匹配 → 停止并报告（不覆盖、不删除任何一侧） |
| **授权要求** | 写恢复仓库授权（这些文件属桌面产品线，按 AGENTS.md 与 Android 提交组分隔，不混入 Android 提交组） |

### 步骤 3 — 候选提交组审查与授权（待窗口 1）

| 维度 | 内容 |
|------|------|
| **前置条件** | **待窗口 1 修订报告**：完整文件/hunk 归属矩阵（当前 `android/app/src` 实测 234 改性 + 126 未跟踪，远多于上一版 G0–G8 所列）、G8/B4/B5/B8/B9 拆分、版本号归属修正、各组测试范围与编译状态如实标记 |
| **事实边界** | 窗口 1 上一版报告中"编译边界 ✓/⚠"标注**未经暂存快照编译验证**（三报告复核 §2 已指出），本方案不引用其为依据；"D 阶段 3208/3208 是整个冻结工作树的结果"不证明任何单个拟议提交独立可编译。 |
| **操作** | 以窗口 1 修订版的组清单与**最终提交顺序**（待其报告）逐组呈报用户授权；推荐顺序在本版中**不预设**。 |
| **失败停止点** | 任一组 hunk 边界未在窗口 1 修订版中闭环 → 该组 HOLD，不得提交 |
| **授权要求** | 用户逐组授权（或拒绝/修改），记录授权范围 |

### 步骤 4 — 分批提交（协议骨架；组清单与顺序待窗口 1 修订报告）

> 每批 = 一个已授权组。本节只定义**每批相同的协议**，不再逐组复述文件清单（避免与窗口 1 修订中的矩阵漂移）。原 4a–4g 的组级细节由窗口 1 修订版承载。

| 阶段 | 内容 |
|------|------|
| **提交前核对（每批）** | ① 按授权清单显式 `git add <路径>`（混合文件用 `git add -p` / hunk 级暂存；**禁止 `add -A`/`add .`**）；② `git diff --cached --name-status` + hunk 数与授权清单**逐项对照**，多一个文件/一个 hunk 都停下来查因；③ 对照 `git status --porcelain` 全量快照确认未误带他窗文件 |
| **提交动作** | `git commit -m "<组约定 message>"`（一次性完成，失败则按 §3 协议停止） |
| **提交后核对（每批，与改动相称）** | ① `git log -1 --stat` 留档；② `git status --porcelain` 行数对账（应恰好减少该组文件数）；③ **不重跑全量 JVM/V5/APK**——理由见 §6：提交不改变工作树内容，逐提交重跑同一棵树没有判别力 |
| **失败处理** | §3 协议：停止 → 保留（已产生提交不撤销）→ 记录 → 窄修或单独授权。**无 reset/checkout 回退。** |
| **授权要求** | 每批需用户授权 commit |

**逐提交独立可编译性的如实声明**：本方案**不验证**单个提交在隔离状态下是否可编译——那需要为每个提交建立独立检出（`git worktree add` 或 checkout），在共享脏工作树约束下属**单独授权的可选项**，且 worktree 目录应放 E 盘或经授权位置。默认路径下，可验证的是：授权清单↔暂存内容一致性（每批）+ 树级门禁（批次全部完成后一次，见 4z）。

**4z. 末尾树级门禁（整个提交序列一次）**：

| 维度 | 内容 |
|------|------|
| **前置条件** | 全部授权组提交完毕 |
| **操作** | `./gradlew :app:testDebugUnitTest` + `:app:lintDebug` + `:app:assembleDebug`（在恢复仓库；大型构建输出与产物目录放 E 盘，如 `E:\creation-reading-assistant\codex-e-entry-20260929\build\`） |
| **预期证据** | JVM 全量 XML、Lint 0 error、Debug APK（路径在 E 盘） |
| **与历史证据的关系** | D 阶段 3208/3208 是**当时冻结工作树**的结果；其后工作树已有源码级变更（如 2026-09-28 晚 S14 `LocalZipBackup.kt`/`LocalZipBackupTest.kt` 修订），**该数字不能作为当前树的通过证据**，仅作历史参照。本轮门禁是唯一对"提交完成后的树"有效的树级验证。 |
| **失败处理** | §3 协议：停止、保留全部已产生提交、记录失败模块与输出；窄修以**后续更正提交**形式单独授权，不以 reset 回退。 |
| **授权要求** | 运行构建授权 |

### 步骤 5 — 最终开发入口选择（待窗口 1/2 报告）

| 维度 | 内容 |
|------|------|
| **当前事实** | 恢复仓库 Git 健康、无仓库内路径依赖（§2.6）；**原仓库原样保留时，直接以恢复仓库为入口不需要移动任何目录** |
| **候选** | (A) 以恢复仓库现路径为最终开发入口（原仓库原样保留，不移动、不改名）；(B) 切回原路径（需目录处置方案，见步骤 6）；(C) 新建路径（另行 clone/复制，方案另议） |
| **依赖** | **待窗口 1**（提交组完整性与顺序——入口选择应在提交序列完成后才有意义）与**待窗口 2**（旧提交等价性——决定原目录是否仍有必须迁移的独有内容） |
| **授权要求** | 用户书面选择 + 理由；在两窗报告完成前不请求该决策 |

### 步骤 6 — 目录处置（整体"待其报告 + 单独授权"，本版删除移动式回退）

> 仅当用户未来选择步骤 5 选项 B/C 时才需要；选项 A 不需要任何目录操作。

| 维度 | 内容 |
|------|------|
| **状态** | **本版不给出可执行目录移动步骤。** 移动/重命名整个仓库目录是高风险动作：① 共享脏工作树上多窗口可能持有打开文件；② 失败时的"改回原名"式回退本身又是移动操作，在同一故障（文件占用）下可能同样失败并加剧混乱——已从本方案删除；③ 原仓库的 7 个 worktree 注册与 1 个 `C:\Users\` 下 worktree 未清点（§2.5）。 |
| **前置条件** | 待窗口 1/2 报告（提交序列完成、旧提交等价性结论、原目录独有资产清零确认）+ 所有并行窗口空闲 + 用户对该次移动的单独授权 + 移动前增量快照（§4 P-1~P-4） |
| **若未来单独立项时的固定原则** | ① 原仓库目录**保留原样、不删除**，命名使用中性描述（如 `creation-reading-assistant-orig-preserved-<日期>`），**不用"corrupted"**——原仓库并非"历史已损坏"，只是当前离线缺对象 + 本地索引损伤（§2.4）；② 每一步移动后立即校验（`git status`/`fsck`/`local.properties`/worktree 注册/IDE 路径，沿用上一版 6c 校验清单的检查项）；③ 任一步失败 → **停止并保留两个目录的当时名称与内容**，记录占用进程与错误原文，呈报单独授权方案；④ 原目录 `.claude/settings.local.json` 含 `git checkout *` 允许项，任何处置不得原样复制（资产报告 §5 建议 6）。 |
| **授权要求** | 单独授权，不在本方案默认路径内 |

---

## 6. 验证相称性原则（替代原"构建输入来源汇总"的逐提交套用）

| 验证层 | 内容 | 适用时机 | 理由 |
|--------|------|----------|------|
| **内容核对（每批，必做）** | 授权清单 ↔ `git diff --cached --name-status` ↔ `git log -1 --stat` ↔ porcelain 行数对账 | 每批提交前后 | 廉价、精确、无副作用；这是提交批次唯一"只关于该批"的验证 |
| **树级门禁（一次）** | 全量 JVM + lint + assembleDebug | 全部批次完成后（4z） | 提交动作不改变工作树——提交序列期间树内容恒定，逐批重跑同一棵树没有判别力；树级证据只在起点（提交前树）与终点（全部提交后同一棵树）各有效一次，且两者是**同一棵树** |
| **同输入历史证据** | D 阶段 3208/3208、V5 62/62、S04/S05/S12/S14 各专项 | 仅当"当前树与证据生成时的输入一致"才可引用 | D 门禁之后工作树已有源码级变更（S14 修订等），D 数字降级为历史参照；各专项验收是否仍适用，由窗口 1 修订版按"该组提交内容 vs 验证时输入"逐组标注（待其报告） |
| **逐提交独立可编译性** | 隔离检出编译 | **默认不做** | 需 worktree/checkout（§5 步骤 4 声明）；如用户要求，单独授权、产物与 worktree 放 E 盘 |
| **定向重跑** | 与该组改动直接相称的定向测试 | 仅当窗口 1 修订版标注"该组提交内容与既有验证输入存在差异"时 | 相称性由窗口 1 的矩阵驱动（待其报告），不机械全量 |

**大型构建与产物**：所有 Gradle 构建的输出/产物/测试报告统一放 E 盘（`E:\creation-reading-assistant\codex-e-entry-20260929\` 下分目录），仓库内只留 gitignore 范围内的常规构建目录。

---

## 7. 阻塞点与等待项

### 7.1 待窗口 1（hunk 修订报告）

| 项 | 影响步骤 | 等待内容 |
|----|----------|----------|
| W1-1 完整文件/hunk 归属矩阵（234 改性 + 126 未跟踪口径） | 步骤 3/4 | 覆盖全部改动（非部分列举），G8/B4/B5/B8/B9 拆分闭环 |
| W1-2 最终提交顺序与逐组清单 | 步骤 4 | 本方案不预设顺序，以修订版为准 |
| W1-3 各组测试范围与编译状态如实标记 | 步骤 4/§6 | 相称性定向验证的判别依据 |
| W1-4 版本号归属修正（`build.gradle.kts` 注释指向父表 `@Upsert`，不属 Room v15 迁移） | 步骤 4 | 修正后纳入对应组 |

### 7.2 待窗口 2（资产对账修订报告）

| 项 | 影响步骤 | 等待内容 |
|----|----------|----------|
| W2-1 92 个 `ORIG_UNIQUE` 逐文件差异判定 | 步骤 1 | 不能以 mtime/体积代替语义对账 |
| W2-2 原目录独有资产精确清单 | 步骤 1/2 | 3 个 Electron 文件之外的落位需求（含 4 项 PRESERVE 资产去向） |
| W2-3 `e740ef6d` 26 个 src 文件逐文件对账 | 步骤 1 | 路径命中 ≠ 内容等价；等价性结论以逐文件 diff 为准 |
| W2-4 备份缺口清单现状核对 | §4 | `recovery-untracked-not-backed-up.txt` 为时点清单；提交前按 P-3 重扫 |

### 7.3 开放项（非两窗可解）

| 项 | 说明 |
|----|------|
| `2a428851` 离线证据不可得 | tree 缺失；联网 `git fetch` 补全原仓库对象可解锁对账，需单独授权且当前无网络。解锁前该提交保留为"内容未核、未处置"。 |
| 原仓库 7 个 worktree 内容未对账 | 目录处置（步骤 6）的前置清点项 |
| `.claude/settings.local.json` 的 `git checkout *` 允许项 | 任何目录处置不得原样复制 |

### 7.4 用户决策项（均推迟到对应事实完成后）

| 决策 | 前置事实 |
|------|----------|
| 旧提交取舍（并入/保留/放弃） | W2-2/W2-3 完成后的逐文件差异表 |
| 3 个 Electron 文件及 PRESERVE 资产落位 | W2-2 精确清单 |
| 逐组提交授权 | W1-1/W1-2 + §4 增量保护落地 |
| 最终入口选择与目录处置 | 提交序列完成 + W1/W2 报告 |

---

## 8. 目前能否执行

**不能立即提交，可以立即做的只有 §4 增量保护（P-1~P-3，需写 E 盘授权）。** 理由：① 窗口 1/2 报告在补正中，提交组与等价性事实未闭环；② 收口计划 §E 要求用户明确授权；③ 增量保护未落地前，544 条未提交内容中至少 81 个文件 + `AppLog.kt` + 8 个文档无任何备份副本。

**明确的非目标**：本方案不以 reset/checkout/移动目录作为任何失败回退；不要求用户裁决只读检查可查明的事实；不把时点快照当持续备份；不逐提交机械重跑全量验证。

---

## 9. 本文件的纪律

- 未 stage、commit、push、reset、checkout、revert、clean；未移动或删除目录；未运行构建；未操作设备。
- 未修改任何源码、测试、配置或他人报告。唯一写入文件即本文件。
- 对两仓库仅只读 Git 命令（本轮新增：`rev-parse` 逐 ref、`status --porcelain` 计数、`diff --name-only` 子集计数、`rev-list --count`、`config --get`、`branch -a`、`remote -v`、`fsck`）。
- 未把原仓库描述为"历史永久损坏"；partial clone 离线缺对象与本地损伤行分开表述。
- 未引用窗口 1 上一版的"编译边界 ✓"作为依据；未把 `SearchScreen.kt` 单点命中表述为 `2a428851` 的等价证据。
- 不覆盖其他窗口文件（本文件与窗口 1/2 报告互不重叠）。
