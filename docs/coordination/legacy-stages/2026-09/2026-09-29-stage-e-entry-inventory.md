# E 阶段入口预对账：恢复仓库待整理资产清单（2026-09-29，只读）

> **2026-09-29 后续状态：** [阶段 D 已限定退出](../../reviews/2026-09/2026-09-29-stage-d-limited-exit-review.md)，A–E 为 4/5=80%。本文件下方“D 未退出 / 60% / S05 NOT RUN”是写入时点的历史快照；E 仍只完成预对账，未 stage、commit 或切换目录。

> **Codex 2026-09-29 更新：** 本文件写入时 S05 三项仍 NOT RUN；后续报告已有 A/B/C MuMu 行为证据，但 C 手机布局仍缺，D 尚未退出。原仓库只读深查从当前 HEAD 历史追到 425 个缺失对象，恢复仓库 fsck 无报错；E 盘已保留此时点文档、tracked patch 与 145 个未跟踪源文件快照。见 [后续复核](../../reviews/2026-09/2026-09-29-stage-d-s05-e-preflight-review.md)。以下 NOT RUN 为本文件写入时快照。

> **性质声明：本文件是 E 阶段入口的只读预对账，不是执行令。**
> 未执行任何提交、迁移、目录切换、清理或删除；未运行 Gradle/ADB/MuMu；未改任何源码、测试或配置。
> **D 阶段未退出**：S05 三项残留设备场景（真实书末自动完成 / 首页完成卡逐卡 / 取消后再阅读自愈）仍为 **NOT RUN**，
> 本文件不把它们写作已通过，也不因本清单而推进 E。A–E 全局仍 **3/5=60%**。

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`，HEAD `5aed5939600e491843e3c44453921bd6273a233c`）
- 原仓库：`D:\develop\Code\Codex\creation-reading-assistant`（HEAD `e740ef6daaddf055c2831e8c1b1f82f3c5222661`，main）
- 依据：[收口计划 §E](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md)（第 90–100 行）、[AGENTS.md](../../../../AGENTS.md)、[当前交接](../../../handoff/current.md)、[V5/S05 复核](../../reviews/2026-09/2026-09-29-stage-d-v5-s05-review.md)

---

## 0. 结论先行

1. 工作区 `git status --porcelain` 共 **543 条**（260 改性 + 283 未跟踪），其中 **363 条在 `android/app`**。
   **HEAD `5aed593` 与工作树不同**——该提交是 2026-09-14 的文档提交，此后全部恢复工作都未提交。
2. 这批改动**不是一次改动**：它跨越桌面端（Electron）、Android 主线、多个 Agent 的历史调查产物与本地调试残留，
   来源与归属**无法单凭 git 状态判定**。按可审查性分四类后，**能明确归属 Android 恢复必需**的只是其中一部分。
3. **原仓库 Git 仍不健康**（read-only `git fsck` 报 51 项 broken link / invalid reflog / invalid sha1 pointer），
   且原仓库自身 **289 条未提交改动**、挂 7 个 worktree（其中一个在 `C:\Users\...\superpowers\worktrees`）。
   **收口计划 §E 的前提「先对原/恢复目录做文件与历史差异核对及 Git 对象完整性诊断」因此尚未完成。**
4. **切目录前必须先解决的三件事**（详见 §5）：Room v15 schema 未入库、S05 三项 NOT RUN 与冻结期冲突、原仓库 Git 损坏未定性。

---

## 1. 资产分类清单

### 分类口径

| 类 | 定义 | E 阶段处置方向 |
|---|---|---|
| **① Android 恢复必需（代码/测试/配置）** | 有报告证据链支持的 Android 修复与对应测试、构建配置、Room schema | 候选提交，需 hunk 级拆分 |
| **② 对应报告证据** | `docs/coordination/`、`docs/qa/`、`docs/code-review/`、`docs/research/`、计划文档 | 默认**不**随代码提交（收口计划 §E 第 93 行） |
| **③ 其他既有修改** | 桌面端 `src/`、Agent 工具目录、脚本产物、文档元数据 | 单独保护，不混入 Android 提交 |
| **④ 无法判断** | 无报告、无所有权标注、来源不明的中间产物 | 逐项登记，交总指挥裁决后再定 |

### ① Android 恢复必需：代码 / 测试 / 配置

**统计**：`android/app` 改性 **188 个 main 源码**、**39 个 test 源码**、**8 个 androidTest 源码**；未跟踪 **41 个 main 源码**、**74 个 test 源码**、**10 个 androidTest 源码**。合计约 **360 个 Android 源码/测试文件**。

**配置（3 个改性 + 1 个未跟踪）**

| 文件 | 状态 | 说明 |
|---|---|---|
| `android/app/build.gradle.kts` | 改性 | 含 qahost 变体、`craAndroidTestBuildType`、`CraIsolatedTestRunner` 等 D 阶段门禁依赖 |
| `android/app/src/main/AndroidManifest.xml` | 改性 | — |
| `android/app/src/main/res/values/colors.xml`、`themes.xml` | 改性 | 主题相关（S06 范围） |
| `android/app/schemas/…/15.json` | **未跟踪** | **Room schema v15 快照未入库** —— 见 §5.1 |
| `android/app/src/main/res/values-night/themes.xml` | 未跟踪 | 夜间主题（S06 范围，与 D1「夜间模式翻转」相关） |

**已知有报告支撑的修复子集（举例，非全部）**

| 主题 | 代表文件 | 报告 |
|---|---|---|
| S05 完成态契约 | `ShelfModels.kt`、`BookGrid.kt`、`BookActionSheet.kt`、`HomeViewModel.kt`、`HomeContinueModels.kt`、`HomeArchiveViewModel.kt`、`MyReadingViewModel.kt`、`ProfileViewModel.kt`、`StatsRepository.kt`、`StatsPage.kt`、`ui/util/BookReadiness.kt` | [S05-①](2026-09-28-s05-cancel-finished-state-result.md)、[S05 跨页](2026-09-28-s05-cross-page-finished-consumers-result.md) |
| S12 Legado 导入与临时查阅守卫 | `LegadoReadingProgressImporter.kt`（未跟踪）、`LegadoProgressMarker.kt`（未跟踪）、`ReaderProgressEffects.kt`、`ReaderDocumentLoader.kt` | `docs/qa/2026-09-28-s12-*` |
| S14 ZIP 恢复与归属日志 | `feature/sync/LocalZipBackup.kt`、`JsonBridge.kt` | [S14 日志 kill 窗口](2026-09-28-s14-journal-kill-window-fix.md)、`docs/qa/2026-09-28-s14-*` |
| V5 时长 pill / 单位切换 | `ui/screen/shelf/DurationPillGestureTest.kt`（未跟踪）、`ui/screen/stats/DurationUnitToggleEntryTest.kt`（未跟踪）、`data/settings/DurationPrefs.kt`（未跟踪） | [V5 复跑](../../../qa/2026-09/2026-09-28-stage-d-v5-rerun.md)、[V5/S05 复核](../../reviews/2026-09/2026-09-29-stage-d-v5-s05-review.md) |
| 隔离测试基建 | `androidTest/…/testing/`（`CraIsolatedTestRunner`/`CraIsolatedTestApplication`，未跟踪）、`IsolatedTestContext.kt`（未跟踪） | [D 独立审查](../../reviews/2026-09/2026-09-28-stage-d-independent-review.md) |
| Room 迁移 | `AppDatabase.kt`（新增 `MIGRATION_14_15`，版本升至 **15**）、`DatabaseModule.kt`、`androidTest/…/AppDatabaseMigrationTest.kt` | 需与 15.json 一起落 |

**未跟踪的 main 源码里，有相当一批是"新功能/新组件"而非"修复"**（如 `SettingsSearchIndex.kt`、`SearchHitRanker.kt`、`CoverImageCache.kt`、`CraSlider.kt`、`CharacterTopologySection.kt`）。
**这些不能默认算入"Android 恢复必需"** —— 需要按报告逐项确认哪些是恢复必需、哪些是既有增强功能（收口计划 §E 第 93 行：Android 提交只包含直接相关源码）。

### ② 对应报告证据（默认不随代码提交）

| 目录 | 数量 | 状态 |
|---|---|---|
| `docs/coordination/` | **165 个 md**，整目录**未跟踪**（`git ls-files` 为空） | 全是 2026-09-20 以后的协调/复核报告 |
| `docs/qa/` | 69 条未跟踪 | 设备验收报告 |
| `docs/qa/evidence/` | **776 个文件**未跟踪 | 含 `.tmp/`、`jvm-test-results-*/` 等原始 XML 与脚本 |
| `docs/code-review/` | 18 条未跟踪 | — |
| `docs/research/` | 6 条未跟踪 | — |
| `docs/plans/` | 9 条未跟踪 + 3 条改性 | 含收口计划本身 |
| `docs/README.md`、`docs/handoff/current.md`、`docs/plans/legado-feature-backlog.md` 等 | 5 条改性 | — |

> **`docs/coordination/` 整个目录不在版本控制内** —— 意味着本文件、S05/S12/S14/V4-V5 全部复核报告、阶段 A–E 计划都在工作树里裸奔。
> 这是 §5.3 的风险点：一旦误执行 `git clean`，全部证据链丢失且无 Git 可恢复。

### ③ 其他既有修改（单独保护，不混入 Android）

| 组 | 内容 | 说明 |
|---|---|---|
| **桌面端 Electron** | `src/components/ui.tsx`、`src/features/library/ReaderSettings{Drawer,Panel}.tsx`、`src/features/settings/SettingsPage.tsx` + 6 个 `sections/*.tsx`（共 **10 条改性**）；未跟踪 `src/components/ui/{FontPicker,NumberStepper,Slider,Switch}.tsx`、`src/features/settings/SettingsSearch.tsx`、`search-registry.ts`、`settings-controls.css`、`src/features/settings/reader/`、`__tests__/`、`src/features/library/ReaderSettingsMorePanel.tsx`（**11 条**） | AGENTS.md：桌面端与 Android 是两条运行时隔离的产品线，**不得顺手跨线提交** |
| **构建/验证脚本** | `scripts/packaged-stage3-outline-focus-acceptance.mjs`、`scripts/verify-reader-settings.mjs`（改性）；`scripts/visual-evidence/smoke-final.png`、`smoke-log.json`（二进制改性） | 桌面端 QA 产物 |
| **Agent 工具目录** | `.opencode/`（1 文件）、`.scratch-shots/`（3 张 png）、`.workbuddy-ai/memory/`（8 个 md，含 2026-09-11…09-15 记忆）、`.zcode/`（1 个 plan）、`.zcodeignore` | **工具私有目录，不应进版本库**；`.workbuddy-ai/memory` 是别的 Agent 的记忆库 |
| **`.codex/config.toml`** | 改性：加 `web_search="live"`、`sandbox_mode="workspace-write"`、`model_verbosity="medium"`、`[sandbox_workspace_write] network_access=true` | 工具配置，**与 Android 恢复无关**，且开了 workspace-write + network |
| **Android 脚本** | `android/scripts/install_with_confirm.ps1`、`…contract.tests.ps1`（改性）；未跟踪 `s04_private_archive_manifest*.py`、`diag_usb_confirm.py`、`tap_usb_install_confirm.py`、`ui_now.xml`、`__pycache__` | 真机安装/诊断脚本 |
| **Benchmark** | `android/benchmark/…/BaselineProfileGenerator.kt`、`BenchmarkHelpers.kt`（改性） | — |

### ④ 无法判断（需逐项登记）

| 项 | 数量 | 疑点 |
|---|---|---|
| **根目录 `dump*.xml` 系列** | `dump2.xml`–`dump30.xml`、`dump_secrets.xml`、`dump_secrets2.xml`、`window_dump.xml` —— **34 个** | 真机 `uiautomator` dump 残留（2026-09-23 18:00 前后）。已抽查 `dump_secrets.xml`：内容是**全局搜索页 UI 层级**（text 为「全局搜索」「搜索书籍、灵感、笔记、高亮」「搜索历史」「清空」等），**未见真实书名或隐私数据**；但**文件名带 `secrets` 且无人认领**，须人工确认后处置，不得直接删也不得提交 |
| 未跟踪的 `main` 源码中无报告对应关系者 | 约 20–30 个 | 见 §1.① 末段 |
| 未跟踪的 74 个 `test` 源码 | 74 个 | 多数可从文件名对应到主题（`Reader*Guard*Test`、`*Legado*Test` 等），但**是否有对应的执行报告、是否曾实际跑过**需逐项核 |
| `docs/qa/evidence/.tmp/` | 至少 3 个（`last_xy`、`needle.txt`、`tap_ui.xml`） | 临时文件，明确不入库 |

---

## 2. 归属判定的现实约束

1. **HEAD 不能标识这棵脏工作树**：`5aed593` 是 2026-09-14 的提交，此后 A–D 四阶段的全部产出都在工作树里。收口计划 §1 已明写「两个目录均有未提交资产。HEAD 相同或 APK 构建成功均不能证明工作树相同」。
2. **共享工作区，无单一作者**：AGENTS.md 第 63–69 行要求「工作区中的既有修改均视为用户资产」「不得为了清空状态执行 reset/checkout/全仓格式化」「预先分配互不重叠的文件所有权」。当前 543 条**没有所有权标注**，报告里各窗口只声明了**自己那一轮**的 hunk（例如 S05-① 报告 §四明写「这些文件在工作区还携带他人既有未提交改动」）。
3. **`.codex/config.toml` 已开 `workspace-write` + `network_access=true`**（未授权变更记录）—— 文件所有权纪律在配置层面已经放宽，更需要在 E 阶段开始前把清单冻结。

---

## 3. 原仓库现状（只读诊断，未修改）

| 项 | 实测 | 判定 |
|---|---|---|
| HEAD | `e740ef6daaddf055c2831e8c1b1f82f3c5222661`（main） | — |
| 未提交改动 | **289 条** | 原仓库自身也是脏的 |
| `git fsck` | **51 项**：`invalid reflog entry`（4）、`invalid sha1 pointer in cache-tree`（主 index 1 + 3 个 worktree index 5）、`broken link`（多组 tree→tree） | **不健康** |
| worktree | 7 个，其中 `C:\Users\23254\.config\superpowers\worktrees\creation-reading-assistant/android-reeden-mobile`（`c18d84ad`）**不在 `D:\develop\Code\Codex\` 下** | 换目录前必须处理 |

**收口计划 §E 的要求 vs 现状**：计划第 94 行要求「先对原/恢复目录进行文件与历史差异核对及 Git 对象完整性诊断」；
第 96 行「原 Git 仍不完整：优先用健康仓库作为最终基础……不强行往坏仓库移植提交」。
**本轮只读 fsck 已证明原 Git 不完整**，但**"损坏范围是否只影响历史 worktree、是否波及可达对象"尚未定性** —— 这是 §5.3 的第一项。

---

## 4. 建议的操作顺序（只提顺序与风险，不执行）

```
步骤 0  冻结：取当前 HEAD、543 条逐文件清单、android/app 源码/配置/测试 SHA-256 清单
        （收口计划 §A 第 49-50 行的做法，未授权提交时用补丁 + 未跟踪副本保留可回退记录）
        ↓ 风险：冻结期间任何窗口改源码都会让哈希清单失效，D 阶段冻结纪律必须延续

步骤 1  先补 Room v15 schema 入版本控制（android/app/schemas/…/15.json）
        ↓ 风险：不补则 v14→v15 迁移在干净检出上无法校验（详见 §5.1）

步骤 2  分类清单落地：把 543 条逐条标注为 ①②③④，④ 类逐项给所有权建议
        ↓ 风险：这一步是纯人工判断，不要靠文件名自动化批量归类

步骤 3  处置 ③ 与 ④：
        3a 桌面端 src/ 单独保护，不进 Android 提交
        3b Agent 目录（.opencode/.zcode/.workbuddy-ai/.scratch-shots/.zcodeignore）加入 .gitignore 或明确排除
        3c 根目录 dump*.xml 人工确认后处置（先确认无隐私数据）
        3d .codex/config.toml 的改动单独裁决是否保留
        ↓ 风险：3c 未经确认删除会丢掉可能的证据；未经确认提交会污染版本库

步骤 4  原仓库 Git 完整性定性诊断（隔离目录内做，不在脏 main 上操作）
        ↓ 风险：这是 §E 的前置；未定性就切目录等于把恢复资产迁进坏仓库

步骤 5  D 阶段退出后，才进入 §E 的提交与目录切换
        ↓ 风险：S05 三项 NOT RUN 期间动 E，会让"冻结候选"与"提交边界"同时漂移
```

---

## 5. 切目录与版本整理前必须解决的具体问题

### 5.1 Room schema v15 未入库（**最高优先**）

- 现状：`AppDatabase.kt` 已 `APP_DATABASE_SCHEMA_VERSION = 15` 并新增 `MIGRATION_14_15`（第 543 行），
  但 `android/app/schemas/…/15.json` 是**未跟踪文件**，而 1.json–14.json 都已入库。
- 后果：干净检出（换目录、新 worktree、CI）拿不到 v15 快照，Room 的 schema 校验与迁移测试失去基线；
  收口计划 §B 第 62 行与 §3 都把「S02 支持的数据库升级链是否保留数据」列为首要数据门槛。
- 处置：作为**独立前置提交**（与 R3 集成审阅里「先落最小 v13 前置提交」同款做法），先于任何 v15 功能提交。

### 5.2 冻结期与 S05 NOT RUN 的冲突

- 收口计划 §E 第 92 行：提交前要展示候选 hunk、依赖顺序，经授权才 stage/commit；§D 第 78 行：冻结期间不改构建输入。
- 现状：S05 三项设备场景仍 NOT RUN（[V5/S05 复核](../../reviews/2026-09/2026-09-29-stage-d-v5-s05-review.md)），且已识别出 HY3 的 A→B→C 共用夹具存在**前置矛盾**（A 把 `locator_v2` 写到书末，C 再打开通常仍在书末，不能预期回落到中途百分比）。
- 后果：若在 S05 未跑完时就开始 E 的 hunk 拆分与提交，**冻结基线会与提交目标同时漂移**；一旦 S05 暴露缺陷需窄修，已拆好的提交边界要重来。
- 处置：**先让 D 退出（S05 三项有明确 PASS/FAIL/BLOCKED），再动 E**。若总指挥决定不等，须明确接受「提交边界可能被 D 的返修推翻」这一后果。

### 5.3 原仓库 Git 损坏未定性

- 现状：`git fsck` 51 项错误，含多个 `broken link`（tree→tree）与 worktree index 的 `invalid sha1 pointer`；
  原仓库还有 289 条未提交改动和 7 个 worktree（一个在 `C:\Users\23254\.config\superpowers\worktrees\`）。
- 后果：收口计划 §E 第 96 行的分支判断（「原 Git 健康 → 在隔离集成目录处理」vs「原 Git 仍不完整 → 优先用恢复仓库作为最终基础」）**目前两难**：已证明不完整，但**损坏是否只影响不可达的历史对象、是否影响可达提交**未定性。
- 处置：在**隔离副本**上做 `git fsck --full` + 可达性检查，定性后再按 §E 选路径。**不在脏 main 上直接操作，不强行往坏仓库移植提交。**

### 5.4 `docs/coordination/` 整个目录未入库

- 165 个 md（含 A–E 计划、S05/S12/S14/V4-V5 全部复核、本文件）都不在版本控制内。
- 后果：任何 `git clean` / `checkout` / 换目录时的目录同步遗漏都会**一次性丢失全部证据链且无 Git 可恢复**。
- 处置：换目录前先把 `docs/` 整体做**文件系统级副本**（不是靠 git），再谈目录切换。

### 5.5 桌面端与 Agent 资产混入风险

- 543 条里约 **21 条属桌面端 `src/`**、约 **13 条属 Agent/脚本/配置**。
- 后果：AGENTS.md 第 57 行与收口计划 §E 第 93 行都要求两条产品线**不混提交**。一次 `git add -A` 会把它们全带进去。
- 处置：提交时**必须显式路径清单**，禁止 `git add -A` / `git add .`。

### 5.6 未跟踪源码的「修复 vs 新功能」未区分

- 41 个未跟踪 main 源码中，`LegadoReadingProgressImporter.kt`、`LocalZipBackup` 相关等有报告支撑；
  但 `SettingsSearchIndex.kt`、`SearchHitRanker.kt`、`CoverImageCache.kt`、`CharacterTopologySection.kt` 等更像是**功能**而非**恢复修复**。
- 后果：收口计划 §E 第 93 行只让「直接相关源码、必要配置和测试」进 Android 提交；混进去等于把候选增强当成恢复成果交付。
- 处置：按报告逐项标注；无报告对应的进「既有增强」清单，单独立项，**不在本轮 E 提交内**。

---

## 6. 本文件的纪律

- 未运行 Gradle / ADB / MuMu；未占用设备。
- 未修改任何源码、测试、配置或他人报告。唯一写入文件即本文件。
- 未 commit / push / reset / checkout / clean；未删除任何文件；未触碰正式书库。
- 对原仓库只执行了只读 `git rev-parse` / `git status --porcelain` / `git fsck --no-progress` / `git worktree list` / `git branch`，**未做任何写操作**。
- 未把 D 阶段 NOT RUN 的 S05 三项写作已通过。
- 不覆盖其他窗口的文件（本文件为 E 阶段入口清单，与 `2026-09-28-stage-d-s05-device-plan.md`、`2026-09-29-stage-d-v5-s05-review.md` 互不重叠）。
