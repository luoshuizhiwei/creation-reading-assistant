# 阶段 E 首批集成方案独立反证（Claude，2026-09-29）

> **Codex 复核更正：** 本报告的“G0-only 隔离编译日志不存在”是误读；`2026-09-29-stage-e-g0-isolated-candidate.md` §3–4 和 E 盘原件证实 G0-only app/androidTest 编译 EXIT 0、259 XML / 2286 JVM 全过。Codex 此后又分别隔离构建 G0+U 和 G2+runner 两个拟提交边界，均 EXIT 0。见 [首批双边界复核](2026-09-29-stage-e-first-batch-ready-review.md)。本报告关于九项 H 类摘要遗漏及后续八批未证可编译的判断仍有效；“首批可提交”须按新复核的精确清单与项目 §E 授权执行。

> **性质声明**：只读反证，不执行任何 Git 写操作。唯一可写文件即本文件。  
> 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`，HEAD `5aed593`，当前 porcelain 547 条）。  
> 未进入原仓库，未读取 Obsidian 记忆库，未运行构建，未操作设备。

---

## 一、首批拆分为"G0+U"和"G2+runner"两个提交的可编译边界

### 1.1 联合候选已构建通过 ≠ 两个提交各自可编译

**已证（联合态）**  
- G0+U+G2+runner 联合候选：E 盘隔离目录 `stage-e-g0-u-g2-runner-20260929/` 两次构建 EXIT 0（debug androidTest 3m32s、qahost androidTest 3m23s），日志 SHA 已记录（见 [`2026-09-29-stage-e-g0-u-g2-combined-mumu.md`](../../../qa/2026-09/2026-09-29-stage-e-g0-u-g2-combined-mumu.md) §3）。
- G2-only 隔离候选：E 盘 `stage-e-g2-isolated-20260929/` 两次构建 EXIT 0（见 [`2026-09-29-stage-e-qahost-isolated-build.md`](../../legacy-stages/2026-09/2026-09-29-stage-e-qahost-isolated-build.md) §2）。**G2 独立可编译已证。**
- U-only 隔离候选：E 盘 `stage-e-u-isolated-20260929/` `:app:compileDebugKotlin`+`compileDebugAndroidTestKotlin` BUILD SUCCESSFUL，JVM 11 套件 143 tests 全通过（见 [`2026-09-29-stage-e-upsert-isolated-build.md`](../../legacy-stages/2026-09/2026-09-29-stage-e-upsert-isolated-build.md) §四）。**U 独立可编译已证。**

**仍需验证**  
- **G0-only 隔离编译**：尚无 HEAD+仅 G0 候选的独立 E 盘构建日志。现有 G0 构建证据均来自"G0+G2 组合态"（见 [`2026-09-29-stage-e-three-isolation-review.md`](2026-09-29-stage-e-three-isolation-review.md) 第 1 条："E 盘 HEAD+G0+G2 确认候选……OK (1 test)"），不等于 G0-only 单独可编译。

**对拆分边界的判断**  
- G0 与 U 的源文件集合完全不相交（已证：G0={AppDatabase, DatabaseModule, SearchTermDao-S5, 15.json, AppDatabaseMigrationTest}；U={BookDao-B1~B4, InspirationDao, TaxonomyDao, ParentUpsertCascadeSafetyTest, build.gradle.kts-版本hunk}），编译闭包上无符号交叉依赖。
- G2 与 G0/U 文件不相交，且 G2 基线闭包在 HEAD 已自包含（见 [`2026-09-29-stage-e-qahost-candidate.md`](../../legacy-stages/2026-09/2026-09-29-stage-e-qahost-candidate.md) §5），G2-only 可独立落在 HEAD 上。
- **结论**：G2+runner 拆为独立提交已具隔离编译证据（**已证**）。G0+U 合并为一个提交，两者已各自独立编译，也已联合编译，合并提交的编译可由独立证据**合理推断**成立——但需明确说明 G0-only 隔离构建结果尚未单独记录，是当前文件中唯一的隔离编译空白。

### 1.2 关键 hunk 逐项核对

| hunk | 文件 | 状态 |
|------|------|------|
| 版本号 hunk（B=versionCode/versionName）| `build.gradle.kts` | **已证归属 U**：注释逐字引用 `BookDao.upsert`，与 Room v15 无关。隔离构建中 G2-only 的产物 versionCode 停在 2（0.5.0），联合候选为 3（0.5.1），双向验证了归属（见隔离报告 §3 "判别性副证"）。 |
| SearchTermDao 索引 hunk（S5）| `SearchTermDao.kt` | **已证归属 G0**：候选 SHA `038adc4eea8aa8b3` 与 G0 报告 SHA 逐字节一致（联合报告 §2 "跨窗口自证"），Room 硬约束（`TableInfo.equals` 集合比较）要求 S5 必须与 15.json 原子绑定——缺一侧则开库崩溃，已在决策包 §1.1 说明。 |
| BookDao B5 排除（`getAllActive`/`activeIds`）| `BookDao.kt` | **已证排除**：候选 vs 工作树差异 = 仅 B5（61a62,81），与报告声明完全吻合（见 `hunk-listing-vs-worktree.txt` §3.3）。归属后续批次 4（Legado 导入）已有明确说明。 |
| runner 三个新测试文件 | `CraIsolatedTestRunner.kt`（RF 修正版）、`CraIsolatedTestApplication.kt`、`IsolatedTestContext.kt` | **已证存在并可编译**：联合报告候选表列出 SHA，runner 已为窄修后版本（`cc5e1fd6…`），编译、manifest/dex 均核对；qahost 分支运行时实证 logcat 一行（见联合报告 §4 末尾）。普通 debug 透传分支设备 **NOT RUN**，明确登记，不外推。 |

---

## 二、后续 8 个"最小可编译批次"的隔离构建证据

**已证**：无一个批次有各自 HEAD+精确 hunk 隔离编译的独立日志。

**合理推断（不影响首批执行决定）**：  
批次 1（S03）、批次 2（S04）、批次 7（G8a-T8）文件内部语义有专项报告支持，各自 hunk 明细已给出，结构上合理可独立编译。

**跨批次硬依赖——影响首批提交顺序的发现**：

> [!IMPORTANT]
> **批次 1（S03）→ `SearchIndexBookRefreshCoordinator.kt`**：决策包 §2.1 明确指出该文件曾被误判为 EXCLUDED，若不随批次 1 同批纳选，`BookRepository.kt` 立即产生未解析符号编译错误。这是一个**已知的硬依赖修正**，不影响首批（G0+U+G2）的执行，但批次 1 执行时必须包含该文件，否则编译必然失败。

其余批次之间的依赖（B2→B4、B1+B2→B5、B2+B7→B8、B3→B6）均为决策包中的图示推断，**尚无隔离编译证据**。无证据表明这些批次间依赖会改变**首批**（G0+U+G2+runner）的提交顺序或造成数据/源码遗漏。

**结论**：后续 8 个批次标"**候选分组，未证可编译**"。不影响首批执行决定。

---

## 三、"65 项原目录独有代码均可不迁"的反证

**核查结果：问题不在"缺 9 项"，在于分类体系不同。**

集成决策包（`integration-decision-package.md`）§3.2 的七类摘要：  
`S05-①(11) + formatDuration(10) + ThemeResolver(3) + TapZone(4) + DORMANT_IDS(3) + 搜索/并发(7) + 翻页/选区(18) = 56`

而原始语义报告（`original-semantic-review.md`）的分类体系为：

| 分类 | 项目编号 | 数量 |
|------|---------|------|
| A. S05-① 完读重构 | #1–11 | 11 |
| B. formatDuration 统一迁移 | #12–21 | 10 |
| C. ThemeResolver 提取 | #22–24 | 3 |
| D. TapZoneGeometry/TurnPlan 提取 | #25–28 | 4 |
| E. DORMANT_IDS 过滤 | #29–31 | 3 |
| F. 搜索/BookRepository/ShelfImporter 重构 | #32–38 | 7 |
| G. 阅读器/翻页/选区重构 | #39–56 | 18 |
| **H. 其他** | **#57–65** | **9** |

**决策包 §3.2 的七类摘要完全忽略了原始语义报告第 H 类"其他 9 项"（#57–65）。**

该 9 项在 [`2026-09-29-stage-e-original-semantic-review.md`](2026-09-29-stage-e-original-semantic-review.md) 第 146–158 行（H 类）有完整列出：

| # | 文件路径（缩略）| 语义变化摘要 |
|---|------|------|
| 57 | `android/app/build.gradle.kts` | AndroidJUnitRunner → CraIsolatedTestRunner + testBuildType + qahost |
| 58 | `ui/screen/home/components/HomeContinueSection.kt` | S05-① 完读状态透传 |
| 59 | `ui/screen/reader/ReaderContentHostEpubBranch.kt` | S07-F3 epub 源区间 |
| 60 | `ui/screen/reader/ReaderContentHostPlainTextBranch.kt` | S07-F5 可见性失效 |
| 61 | `ui/screen/reader/ReaderProgressEffects.kt` | ReaderLoadedContent + 替换可用性效果 |
| 62 | `ui/screen/reader/sheets/ReaderNotesSheet.kt` | typed 回源 A01 |
| 63 | `ui/util/DurationDisplay.kt` | KDoc 更新 + toggle label |
| 64 | `ui/viewmodel/ShelfImporterTest.kt` | SearchIndexBookRefreshCoordinator + 断言 |
| 65 | `ui/viewmodel/ShelfViewModelTest.kt` | BookCacheClearOutcome mock + Coordinator |

**判定**：这 9 项均在原始语义报告中完成了逐项语义判定（"确认覆盖"），结论与其余 56 项相同——均为有意架构演进，可不回合原目录旧代码。集成决策包的七类摘要遗漏了这 9 项的**摘要说明**，但未遗漏判定结论（总摘要 §6 写明"确认覆盖：65 项"）。因此"65 项全覆盖"的结论**已证**；摘要摘漏本身不构成实质错误，但应在集成日志中明确补记第 H 类的存在，防止歧义。

> [!NOTE]
> `#57`（`build.gradle.kts`）属 G2 首批；`#58` 属 S05-① 后续批次；`#59–60` 属 S07（G8e HOLD）；`#61` 属批次 4 部分；`#62` 属阅读器 HOLD；`#63–65` 属后续批次 2/6。这些归属与决策包各批次说明一致，无孤悬资产。

---

## 四、集成方案中已过时或不必要的授权步骤

**已过时：**

1. **"联合验收待回读"阻塞状态**：决策包写作时（§5 结论）声明"当前唯一阻塞：ZCode 联合设备验收日志待回读"。该阻塞已解除——ZCode 联合候选 8/8 通过、53/53 持久文件恢复一致，证据已在 [`combined-and-decision-review.md`](2026-09-29-stage-e-combined-and-decision-review.md) 正式确认。任何继续引用"联合验收待回读"的表述均为**过时**，不应再作为阻塞首批提交的理由。

**不必要的授权步骤（决策包 §4.2 五阶段）：**

2. **阶段 0"E 盘增量保护"需要"用户授权 1"**：  
   - 收口计划 §E（`convergence-plan.md` 第 92 行）仅要求"提交前展示候选文件/hunk……经用户明确授权后才 stage/commit"，**未要求对 E 盘快照写入单独设置授权门槛**。  
   - E 盘快照是零仓库写入操作（不改源码、不 stage/commit），其时效性保护价值随时间递减，等待额外授权反而增加资产遗漏风险。  
   - **结论（已证）**：E 盘增量保护不需要单独的"用户授权 1"，可由执行方直接完成后向用户报告结果。仅 stage/commit 操作需要用户明确授权。

3. **"只读验证"步骤（阶段 1 结果回读）不应额外等待批准**：  
   - "阶段 1：结果回读"的核验本质是对已有证据的确认，属执行方内部闭环，无需用户再次批准；用户授权门槛应设在 stage/commit 前（阶段 2），不应设在验证读取步骤本身。

4. **五阶段授权设计将 stage/commit 分离为两步（授权 2 = 首批暂存，授权 3 = 后续批次）是合理的**，符合收口计划 §E 要求，应予保留。

5. **当前 porcelain 为 547 条（260 M + 287 ??）**，而决策包 §4.1 仍引用历史快照"544 条"，且"补齐 81 个未跟踪文件"的表述已与实际不符（当前未跟踪 287 条，非 284+81）。授权清单展示时必须以当前实际状态为基准重算，不得复用历史数字。

---

## 五、总结：首批能否提交，缺哪一个最小证据

**已证可执行（首批 G0+U+G2+runner）**：
- G2 隔离编译已证；U 隔离编译+JVM 已证；联合 8 项设备测试已证；runner 修正范围已证；联合构建已证。
- 版本号/SearchTermDao-S5/BookDao-B5/runner 三文件的 hunk 归属均已证。
- 65 项"可不迁"结论已证，7 类摘要的 9 项遗漏为表述缺口，不影响实质结论。

**仍需验证（最小缺口，唯一阻塞可能）**：

> [!WARNING]
> **G0-only 隔离编译日志不存在**：现有 G0 编译证据均来自"G0+G2"或"G0+U+G2"组合态。若 G0 单独落在 HEAD 上需要验证可编译性（例如要求"G0+U"拆成两个独立提交而非一个），则 G0-only 的 E 盘隔离构建是唯一尚未完成的证据。

**可接受的替代方案**：若将 G0+U 合并为单一提交（而非拆成两个），则现有证据已足够——U-only 和 G0+U 联合均已证可编译，合并提交的编译边界合理推断成立，不需要 G0-only 独立构建。**建议采纳：G0+U 合并为一个提交，G2+runner 为第二个提交。**

**结论**：若采用"G0+U 合并提交"方案，首批具备完整的编译和设备证据，**可以提交**；唯一需完成的非技术动作是用户明确授权（§E 要求），授权前向用户展示精确 hunk 清单、资产归属与安全保护清单。

---

*报告生成时间：2026-09-29T17:47~17:52（只读，无构建，无设备操作）*  
*证据来源：`docs/coordination/` 下 13 份报告逐文件阅读，`docs/plans/convergence-plan.md` §E，`docs/qa/2026-09/2026-09-29-stage-e-g0-u-g2-combined-mumu.md`。*
