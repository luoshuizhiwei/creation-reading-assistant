# 阶段 E 机械性增量保护与逐行清单（2026-09-29，只读预检）

> **Codex 复核限制：** 28 份增量副本中含 `.opencode/.../preference.json` 一份和 `android/scripts/__pycache__/*.pyc` 两份，超出必要项目资产范围；原清单不应作为已清理备份使用。Codex 尝试精确删除这 3 个 E 盘副本被自动审批以 `blocked by policy` 拒绝，未删除；原仓库文件未动。另 180 HOLD 有 86 行未映射，八批仅是候选分组。两个首批边界此后由 Codex 独立隔离编译通过，详见 [复核](../../reviews/2026-09/2026-09-29-stage-e-first-batch-ready-review.md)。

> **（Codex 复核位：留空待总指挥转交复核）**

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`；HEAD `5aed5939600e491843e3c44453921bd6273a233c`（开工时核对）。未误入原目录 `D:\develop\Code\Codex\creation-reading-assistant`。
- 性质：**机械性预检**——增量保护、逐行映射、补丁生成与并集复核；**不做语义裁决**（65 项行为取舍、批次最终归属归 Antigravity Claude 反证与总指挥裁决）。未操作 MuMu/ADB；未修改产品源码或共享配置；未 stage/commit/push/reset/checkout；未移动/删除任何原件。
- 仓库内唯一写入 = 本报告；全部大清单/补丁/备份在 `E:\creation-reading-assistant\stage-e-incremental-20260929\`。
- 输入：[联合候选与集成方案复核](../../reviews/2026-09/2026-09-29-stage-e-combined-and-decision-review.md)、[集成决策材料](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md)、[原目录语义审查](../../reviews/2026-09/2026-09-29-stage-e-original-semantic-review.md)、既有 E 盘快照（`codex-e-preflight-20260929/` 各清单 + Stage-A 快照 `D:\develop\Code\Codex\recovery-snapshots\20260922-stage-a\manifest.json`）、[收口计划 §E](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md)。
- **当前实态（本轮实测，取代 544/81 旧数）**：`git status --porcelain=v1 -uall` = **1521 行（260 M + 1261 ??）**；逐行展开后按本轮口径全部纳入下述计算。

## 1. 任务一：E 盘增量保护（按当前实态逐文件哈希）

**覆盖判定方法**：对 1521 行展开后的每个文件计算当前 SHA-256，与五个既有快照源逐一比对——① Stage-A manifest（1146 项，09-22 内容哈希）；② preflight `android-source-candidate-inventory.csv`（367 项，09-29 晨内容哈希）；③ preflight `untracked-source-preE.tar.gz` 成员哈希；④⑤ preflight 两个 docs tar 成员哈希；另对 tracked-M 文件验证 `recovery-tracked-worktree.patch` 可对 HEAD **干净应用**（apply-check rc=0 ⇒ 该补丁与当前 tracked diff 一致，被覆盖文件无需重复备份）。**排除面**：本机私有配置（`.claude/`、`.workbuddy-ai/`、`.codex/`、`local.properties` 等 9 项）、构建缓存（1 项）、设备 dump（根目录 `dump*/qa_pk*.xml` 32 项）、APK/密钥——共 42 项，均未备份、原件未动。

**结果**：1521 = **1451 已被现有快照覆盖**（stageA 1083 + inventory 104 + docs-tar 244 + docs-android-tar 20）+ **28 未覆盖 → 已增量备份**（全部为 preflight 之后新增/变更的未跟踪文件：runner 修复版、本轮与并行窗口的新报告等；tracked 文件 0 项——即 preflight tracked 补丁仍与当前 tracked 实态一致）。

- 备份根：`E:\creation-reading-assistant\stage-e-incremental-20260929\files\`（按相对路径保留结构）
- 清单：`incremental-manifest.csv`（SHA-256 `5c7e48e82d939201…dd671`，28 数据行，列 = 来源绝对路径/相对路径/大小/SHA-256/快照路径）
- **复算结果：28/28 备份副本 SHA 与源文件一致（reverify 全 OK，0 失败）**；未移动、未删除任何原件。

## 2. 任务二：约 180 行 HOLD → 候选批次 / 保留 HOLD 逐行映射

数据源：`codex-e-preflight-20260929/stage-e-full-coverage-matrix.csv`（376 数据行，其中 group=HOLD **180 行**）。映射规则严格取自[集成决策材料 §2](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md) 的批次成员定义（文件名 + 指定 hunk），未新增语义判断：

| 映射结果 | 行数 | 说明 |
|---|---|---|
| **首批已验**（G0/U/G2+runner，MuMu 8/8） | 2 | InspirationDao.kt 等——矩阵成文时仍标 HOLD，现已由已验联合候选覆盖 |
| **首批已验 + 候选分组（重复归属）** | 1 | `BookDao.kt`：B1–B4 属已验首批 U，B5 归候选分组 B4（跨归属拆分件） |
| **候选分组 B1–B8（未隔离编译）** | 22 | 含 1 个跨批次拆分件 `AndroidManifest.xml`（configChanges→B3 + TTS→B4）；所有"候选分组"行一律标注**未隔离编译**，不称"最小可编译已证" |
| **保留 HOLD（§2.9 工作流桶）** | 69 | G8c Locator 破坏性 API、SettingsStore(29 hunks)/PerBook/SelectionAction、Profile 大重写（44/73 hunks）、按路径归入 G8e 阅读器工作流与 Profile/设置页面的长尾（包未逐行枚举，已标注"按路径归类"） |
| **未映射** | 86 | 集成方案 §2 **未点名**的页面/公共组件域（Home*/My*/Stats*/Library*/Inspiration* 页面、GlassDialogs/GlassSurface/MicroIsland/AppNavigation/App.kt 等公共组件）——机械映射不代为分组 |

- **统计**：总数 180；候选分组 22（+1 重复归属行中的 B4 部分）；保留 HOLD 69；首批已验 2；**未映射 86**；**重复归属 2 行**（`AndroidManifest.xml` → B3+B4；`BookDao.kt` → 首批+候选分组 B4）。
- 逐行映射：`hold-180-row-mapping.csv`（180 数据行，列含 file_path/status/sub_group/hunk_detail/mapping/rule_provenance；SHA `e9e52388…11333`）；未映射名单 `hold-unmapped-list.txt`。
- 复核注意：集成方案"8 个最小可编译批次"在本清单中仅呈现为**候选分组**；其 144 行规模差（180−22−2 首批−69 保留−86 未映射 + 计数口径）与"批次覆盖 180"的表述需 Claude 反证核对——尤其 86 行未映射与 §2.9"58+39 文件"两个粗粒度桶的数量关系。

## 3. 任务三：65 项原目录差异 → 集成报告分类逐行映射

数据源：`codex-e-preflight-20260929/stage-e-recon/orig-unique-full-reconciliation-corrected.csv`（92 行，SHA `e2237264…53F34B` 与集成方案 §3.2 记录一致）；65 项 = 23 LIKELY_COVERED + 42 NEED_REVIEW（口径同[语义审查](../../reviews/2026-09/2026-09-29-stage-e-original-semantic-review.md)）。映射目标 = 集成方案 §3.2 的七类摘要。

- **结果：56/65 项落在七类摘要内**（A 完读契约 11 + B formatDuration 10 + C ThemeResolver 3 + D ReaderTapZone 4 + E DORMANT_IDS 3 + F 搜索并发 7 + G 翻页选区队列 18）。
- **九类细分实为 A–I**：语义报告 §3.1 除 A–G 外还有 **H 其他（9 项）** 与 **I 文档（2 项）**。**七类摘要未覆盖的 9 项 = H57–H65**：`build.gradle.kts`、`HomeContinueSection.kt`、`ReaderContentHostEpubBranch.kt`、`ReaderContentHostPlainTextBranch.kt`、`ReaderProgressEffects.kt`、`ReaderNotesSheet.kt`、`DurationDisplay.kt`、`ShelfImporterTest.kt`、`ShelfViewModelTest.kt`（与"56 = 65−9"精确闭合）。I66–I67 为文档资产（`current.md` 等），不计入 65 代码项。
- 逐行映射：`orig-65-to-seven-category-mapping.csv`（SHA `b57cfc2b…cb489`，列 = item_no/semantic_sub/csv_path/in_seven_summary）。
- **只做证据映射**：这 65 项"行为是否可舍弃/是否需合并"由 Antigravity Claude 按产品合同判断，本清单不裁决。

## 4. 任务四：首批两补丁与并集复核

生成方法：临时目录内 `git archive HEAD` + 候选文件 overlay + `git diff --cached -U1 --binary`（-U1 为规避 build.gradle.kts 上 B 与 A/C/D/E 两组 hunk 的 U3 上下文交叠；两补丁可**以任意顺序独立应用**于干净 HEAD，已双向验证）。**不宣称两边界各自编译通过**（各自隔离编译属后续窗口任务）。

| 补丁 | 内容 | SHA-256 |
|---|---|---|
| `patches/g0u-first-batch.patch` | G0（AppDatabase 2h + DatabaseModule 3h + 15.json + SearchTermDao S5 + MigrationTest 8h）+ U（BookDao B1–B4 + InspirationDao 2h + TaxonomyDao 4h + ParentUpsertCascadeSafetyTest）+ 版本号 B | `9d8d33607deb6059…0b772a3`（全值 `9d8d33607deb6059b4abb9a58f7505be2609c8a5c96a78e309d788a8c0b772a3`） |
| `patches/g2-runner-first-batch.patch` | build.gradle.kts A/C/D/E + `CraIsolatedTestRunner.kt`（窄修版 `cc5e1fd6…`）+ `CraIsolatedTestApplication.kt` + `IsolatedTestContext.kt` | `b54c9cbf569a0c19…41b434`（全值 `b54c9cbf569a0c1955779c83a795591577d1daaa7394726460106bfc8241b434`） |

- **并集复核**：干净 HEAD 依任一顺序应用两补丁后，13 个文件与[已验联合候选](../../../qa/2026-09/2026-09-29-stage-e-g0-u-g2-combined-mumu.md)基线**内容逐文件一致（13/13；差异仅行尾，源于 git apply 对混合行尾文件的规范化——已用忽略行尾 diff 逐文件确认零内容差异）**。
- 构造输入分拆件：`patches/candidate-build-gradle-B-only.gradle.kts`（SHA `12fac8a1…bff73`，内容 == HEAD+版本 hunk）与 `candidate-build-gradle-ACDE.gradle.kts`（SHA `18eed550…77190b`，与 G2 隔离构建已验证文件一致）。
- **排除清单**：`patches/first-batch-exclusion-list.csv`（SHA `e45f56eb…058c`）——patch1 排除 build.gradle.kts A/C/D/E（→patch2）、SearchTermDao S1–S4/S6、BookDao B5（→B4）；patch2 排除 build.gradle.kts B（→patch1）。
- 未执行任何 git 暂存/提交；补丁仅落 E 盘。

## 5. 边界声明

- 本轮全部结论以开工时点的实际 Git 状态与逐文件 SHA-256 为准；旧"544 条 / 81 文件 / 1519 行"数字一律未沿用（本轮实测 1521 行，与复核记录的 1519 差 2 行——即本轮前后窗口自身的文件新增，已在 28 项增量备份内）。
- 快照校验结果：**28/28 复算一致、0 失败**；1451 项被既有快照覆盖、42 项按策略排除（9 私有配置 + 1 构建缓存 + 32 设备 dump）。
- 未操作 MuMu/ADB；未修改 Antigravity 报告、产品源码、共享配置或他人 E 盘目录；未移动/删除原件；未 stage/commit/push/reset/checkout；未切换目录。
- 落盘即停，交总指挥复核；下一步为 Claude 只读反证（65 项完整映射核对、180 HOLD 覆盖与误归属、首批原子边界可提交性）→ Codex 向用户展示首批清单并按 §E 请求授权。
