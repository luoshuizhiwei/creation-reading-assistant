# 阶段 E：G6 单一集成候选复核（consumers3 + settings + stats = 22/22 生产 + 5/5 测试）（2026-09-30）

> **窗口**：G6 时长单位 —— 单一集成候选（以 consumers3 为底座合入 settings/stats 两个隔离候选）。
> **共享恢复仓库（只读）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`；**HEAD `513891d6a66a9b78e072ba759d34ed580d377ecf`**（= 首批 A+B 两提交后）；**暂存区空**。
> **本窗口独占目录**：`E:\creation-reading-assistant\stage-e-g6-integrated-20260930\`（候选树 / `_cache` / `_evidence` / 补丁均在此，全在 E 盘）。
> **输入**：[G6 边界更正](../../decisions/2026-09/2026-09-30-stage-e-g6-scope-correction.md)、[G6 分层候选](../../legacy-stages/2026-09/2026-09-30-stage-e-g6-partial-isolation.md)、[G6 设置页隔离](../../legacy-stages/2026-09/2026-09-30-stage-e-g6-settings-isolation.md)、[G6 统计页隔离](../../legacy-stages/2026-09/2026-09-30-stage-e-g6-stats-isolation.md)。
> **纪律**：共享恢复仓库内**唯一写入 = 本文件**。未 stage / commit / push / reset / checkout；未迁目录；未触碰原仓库、正式包、正式书库；**未使用 MuMu / ADB / 实体手机**。三个来源候选目录只读取、未修改（§二.3 复核）。

---

## 一、结论摘要

1. **G6 单一集成候选已建成：22/22 生产文件 + 5/5 测试文件**，全部逐文件 SHA 与已核对的三个来源候选一致；**编译 EXIT 0**；**G6 关联定向 JVM 49/49 全绿**（9 类，0 失败/错误/跳过）。
2. **最终补丁可复算**：`g6-integrated.patch`（consumers3 → 集成树，7 文件 28 hunk，22,494 字符，SHA `57638361044764d55d2ee6bea681c47f72f3900d2afe9f1eb5a70af771b0b95a`）；全新 consumers3 源码副本 + `git -c core.autocrlf=false apply` == 集成树，**7/7 字节级一致**。
3. **四类交叠改动零夹带**（word-boundary grep 全 0）：设置搜索（SettingsAnchor / rememberSettingsPageListState / SettingsSearchIndex / ProfileTopBarSearchField）、外观重设计（ReaderPaperGrid / ReaderPaperCapsuleCard）、MetricMicroCard 抽取（`ui/components/MetricMicroCard.kt` 未跟踪新文件未带入；全树唯一命中是 `StorageSubPage.kt` 既有的 `StorageMetricMicroCard`，词边界核实为不同符号）、热力图格子微气泡（HeatmapCellTooltip）。
4. **ProfileHomeScreen 点击范围变化（F3-B）已逐行审查并如实登记**（§五）：「查看阅读档案」的点击从**整卡**收窄到**身份行**，统计行成为无手势祖先的安全节点，长按切换入口全页唯一——这是**真实交互行为变化**，**不得**默认为视觉等价或产品验收通过；手势/读屏/产品取舍留待设备轮。
5. **未验项**（§八）：全部 UI 手势与 TalkBack、持久化重启路径、与 S06 联合态、lint/assemble/APK、设备验收。不得把 49/49 外推为 G6 完整通过。

---

## 二、基线核对与候选来源

### 2.1 共享恢复仓库（开工时）

| 项 | 值 |
|---|---|
| HEAD | `513891d6a66a9b78e072ba759d34ed580d377ecf`（首批 B 提交；两份隔离报告的基线与此一致） |
| 暂存区 | 空 |
| 分支 | `codex/recover-worktree-20260920` |

### 2.2 三个 E 盘候选的 7 个目标文件（实测 SHA-256，与各报告逐项一致）

| 文件 | consumers3（基线） | settings / stats 候选 | 共享工作树（仅参考，未采用） |
|---|---|---|---|
| `ui/screen/profile/AppearanceSubPage.kt` | `948fe081cb…c8d63d5`（=HEAD） | **`99d5458130…d3cbe`**（settings） | `70175585…5430` |
| `ui/screen/profile/ProfileHomeScreen.kt` | `4ef43fb383…91103b`（=HEAD） | **`2c5ce23867…5dc7c`**（settings） | `2b8b3d32…3ece` |
| `ui/screen/stats/components/CreationSection.kt` | `336fdc9e60…990cc`（=HEAD） | **`e9e627dc94…32c3a`**（stats） | `2721a642…650f4` |
| `ui/screen/stats/components/HeatmapSection.kt` | `b79aa554a5…501a9`（=HEAD） | **`5c6f2154dd…6cd2b`**（stats） | `156ad2b2…8201a` |
| `ui/screen/stats/components/SummaryGroup.kt` | `021a9fb3dc…236a5`（=HEAD） | **`0f018e5766…fe35f`**（stats） | `9bf3e164…5553` |
| `ui/screen/stats/components/YearBillHeroCard.kt` | `47990220a3…c0de5`（=HEAD） | **`6b7c5a9cd3…075c`**（stats） | `ae243452…f8da5b` |
| `app/src/test/…/stats/components/YearBillHeadlineTest.kt` | `a1cf0ad0e5…f39ce`（=HEAD） | **`2c07ce2228…0624da`**（stats，=工作树） | 同左 |

### 2.3 来源候选只读性复核（收工时）

合流完成后重读三个来源候选的 7 文件：SHA 与开工前**完全一致**（前缀 `948fe081/4ef43fb3/99d54581/2c5ce238/e9e627dc/5c6f2154/0f018e57/6b7c5a9c/2c07ce22`）——**只读取证成立，未修改任何来源**。

---

## 三、合流方法与覆盖验证

| 步骤 | 做法 |
|---|---|
| 底座 | `cp -r stage-e-g6-consumers3-20260930/android → stage-e-g6-integrated-20260930/android`，随后删除 `.gradle`/`.kotlin`/`app/build`/`benchmark/build`（恢复纯源码 838 文件） |
| 叠放 | settings 2 文件 + stats 5 文件，共 **7 个文件覆盖**（文件集与两窗口所有权**不相交**，无同文件冲突） |
| 全树差异 | `diff -rq`（consumers3 vs 集成树）= **恰好 7 个叠放文件**，零意外差异 |
| 22/22 生产 + 5/5 测试存在性 | 按[边界更正](../../decisions/2026-09/2026-09-30-stage-e-g6-scope-correction.md)清单逐文件 `test -f` 实测 **22/22、5/5** ✓ |
| 符号引用复扫 | `grep -rlE 'LocalDurationUnit\|DurationPrefs\|DurationUnit'`（main 源码）= **恰 22 文件**，与边界更正清单一一对应 ✓；test 源码 5 文件 ✓ |
| 15 个非叠放 G6 生产文件 | 与 consumers3 逐字节一致（全树 diff 已证明）✓ |
| 排除项泄漏 | 16 符号 word-boundary grep（app/src 全树）：**15 符号 = 0**；`MetricMicroCard` 唯一命中为 `StorageSubPage.kt` 既有的 **`StorageMetricMicroCard`**（不同符号，假阳性），词边界复核 0 ✓ |

## 四、可复算补丁

| 项 | 值 |
|---|---|
| 路径 | `E:\creation-reading-assistant\stage-e-g6-integrated-20260930\g6-integrated.patch` |
| 规模 | 22,494 字符；7 文件；28 hunk（difflib n=3 上下文；settings 报告按 `-U0` 计 3+8、stats 按报告 4+2+4+7+2，相邻 hunk 在 n=3 下合并属正常） |
| SHA-256 | `57638361044764d55d2ee6bea681c47f72f3900d2afe9f1eb5a70af771b0b95a` |
| 复算 | 全新 consumers3 源码副本 + `git -c core.autocrlf=false apply`（EXIT 0）== 集成树，**7/7 文件字节级一致** |
| EOL 说明 | 首次复算用默认 `autocrlf=true` 的 `git apply`，写出文件为 LF（内容 EOL 归一后与候选一致，字节级不同）；以 `autocrlf=false` 重做后字节级一致。诊断记录 `_evidence/roundtrip.log`（含逐文件 CR/LF 计数与首差字节） |

## 五、ProfileHomeScreen 点击范围变化（F3-B）重点审查

### 5.1 实际差异（集成源码逐行核实，`ProfileHomeScreen.kt`）

| 检查点 | consumers3 基线 | **本集成候选** |
|---|---|---|
| 档案卡容器 | `SectionCard(onClick = { OpenSubPage(READING) })` —— **整卡可点** | `SectionCard {`（**无 onClick**，渲染为非可点 Surface）——行 90 |
| 「查看阅读档案」点击 | 整卡任意处 | **仅身份行**：`Row(Modifier.fillMaxWidth().clickable { OpenSubPage(READING) })`（行 91–94，覆盖图标+标题+副标题+右箭头） |
| 统计行 | 位于可点卡片内部 | `Row(Modifier.fillMaxWidth())` **无任何 clickable**（行 140–144）——无手势祖先的安全节点 |
| 时长格 | 本地 `when(totalMinutes)` 硬编码天/小时 | `formatDuration(v * 60_000L, durationUnit)` + `valueModifier = Modifier.durationUnitToggle()`（行 153/158）——**全页唯一长按入口** |
| 「读完」格 | — | 无 valueModifier，纯文本 ✓ |
| 「灵感」格既有入口 | — | `onClick = OpenInspirations` **保留未动**（行 171）✓ |

### 5.2 长按入口与点击手势的关系

- `durationUnitToggle()`（`DurationDisplay.kt`）= `combinedClickable`，**空 `onClick` + 长按切换**。其 KDoc 写明边界：挂在**有可点父级**的子节点上时，子节点空 `onClick` 仍参与命中竞争，会截获本该交给父级的点击；且父子两层重复挂会让 TalkBack 读到两个操作。
- 因此 F3-B 是长按入口**安全性的必要前提**：统计行成为无手势祖先节点后，长按入口与卡片点击**各自唯一**（本页 `durationUnitToggle` 恰 1 处：行 158；`AppearanceSubPage.kt` 0 处，只有显式开关行 142）。
- **代价（必须如实登记的行为变化）**：点击**统计行区域**（时长/读完两格及间隙）**不再**打开「我的阅读」；只有身份行打开。此前整卡任意处可点。
- **未采纳的替代**：保留整卡可点 + 长按 `onClick` 转发卡片动作——需改 `DurationDisplay.kt`（空 `onClick` 为硬编码），该文件不属本候选所有权，已登记为 seam。
- **判定边界**：以上为**源码结构核查**。手势命中竞争、读屏朗读、视觉手感**均未验证**；该变化**不得**默认为视觉等价，**不得**当作产品验收通过（产品是否接受「统计行不再触发卡片点击」留待确认；`AppearanceSubPage` 的显式开关是可发现入口，长按仍属隐藏手势）。

## 六、编译与 G6 关联定向 JVM（E 盘独占缓存）

### 6.1 环境

| 项 | 值 |
|---|---|
| JAVA_HOME | `D:\develop\Java\jdk-17.0.14` |
| GRADLE_USER_HOME | `E:\creation-reading-assistant\stage-e-g6-integrated-20260930\_cache\gradle-home`（本窗口独占；由空闲的 `gradle-home-g0` 复制 2379 MB，0 错误——settings 窗口缓存正被其窗口占用，lock 文件 busy，未采用） |
| 参数 | `--offline --max-workers=1`（构建产物/缓存/日志全在 E 盘本窗口内） |

### 6.2 编译

```
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --offline --max-workers=1 --console=plain
```
退出码 **0**，`BUILD SUCCESSFUL in 3m 26s`（22 tasks: 10 executed, 12 from cache——缓存命中为内容寻址，键为输入哈希，安全）。日志 `_evidence/compile-integrated.log`（SHA `7e26ae6f…39c4a`）。

### 6.3 G6 关联定向 JVM（9 类 = consumers3 4 类 ∪ stats 6 类）

```
.\gradlew.bat :app:testDebugUnitTest --offline --max-workers=1 --console=plain \
  --tests DurationPrefsTest --tests DurationUnitTest --tests TimeUtilsTest \
  --tests HomeReadingArchiveSectionTest --tests YearBillHeadlineTest \
  --tests HeatmapBuildTest --tests StatsComputationTest \
  --tests ComputeTodayReadingMsTest --tests StatsFileStructureTest
```
退出码 **0**，`BUILD SUCCESSFUL in 39s`。

| 测试类 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `data.settings.DurationPrefsTest` | 8 | 0 | 0 | 0 |
| `data.settings.DurationUnitTest` | 2 | 0 | 0 | 0 |
| `ui.util.TimeUtilsTest` | 13 | 0 | 0 | 0 |
| `ui.screen.home.HomeReadingArchiveSectionTest` | 2 | 0 | 0 | 0 |
| `ui.screen.stats.components.YearBillHeadlineTest` | 10 | 0 | 0 | 0 |
| `ui.screen.stats.HeatmapBuildTest` | 4 | 0 | 0 | 0 |
| `ui.screen.stats.StatsComputationTest` | 1 | 0 | 0 | 0 |
| `ui.screen.stats.ComputeTodayReadingMsTest` | 6 | 0 | 0 | 0 |
| `ui.screen.stats.StatsFileStructureTest` | 3 | 0 | 0 | 0 |
| **合计** | **49** | **0** | **0** | **0** |

> 49 = consumers3 的 25（8+2+13+2）∪ stats 的 37（10+13+4+1+6+3）的并集（TimeUtilsTest 去重）。XML 原件 9 份与日志哈希见 `_evidence/sha256-evidence.txt`（如 `YearBillHeadlineTest.xml` = `1d2d1f5f…e1c5`）。
> 注意：`test-results` 目录内另有构建缓存恢复的其他类 XML（G0 时代产物），**不属于本候选证据**，已按 mtime 鉴别剔除，仅以上 9 类入档。

### 6.4 与两窗口历史证据的关系

- consumers3 基线 25/25、settings 25/25、stats 37/37 的**类集合**均被本次 49/49 覆盖；三份历史结果分别在其各自候选上产生，本轮是**首次在同一棵树上**跑并集。
- 首批复核提到的「联合候选 MuMu qahost 定向 8/8」为 G0 迁移+父表 5/5 等的设备证据，与 G6 无关，不并入本报告口径。

## 七、与两窗口报告的排他声明核对

| 排除族 | settings 报告排除 | stats 报告排除 | 本集成树实测 |
|---|---|---|---|
| 设置搜索 | SettingsAnchor 等 4 符号 | —（不涉） | **0** ✓ |
| 外观重设计 | ReaderPaperGrid 等 2 符号 | — | **0** ✓ |
| MetricMicroCard 抽取 | —（不涉） | 3 处本地卡片适配、共享组件未引入 | 词边界 0 ✓（`StorageMetricMicroCard` 为假阳性） |
| 热力图格子微气泡 | —（不涉） | HeatmapCellTooltip 等 18 hunk | **0** ✓ |
| 档案卡重设计（`ArchiveStat`/Compact*） | 全排除（保留基线 `HomeStat`+count-up，`valueModifier` 平移） | — | **0** ✓ |

## 八、未验项（不得当作通过）

| # | 项 | 说明 |
|---|---|---|
| 1 | **UI 手势运行态** | 长按切换、时长文本命中、AppearanceSubPage 开关翻转——无设备，全部 NOT RUN |
| 2 | **TalkBack** | 「每个语义组件仅一个长按入口」「读屏不重复」为源码结构核查 + KDoc 边界推理，未在设备读屏验证 |
| 3 | **持久化重启** | 「切 DAYS → 杀进程 → 重启仍 DAYS」有 `DurationPrefsTest` DataStore 往返覆盖，未做真实进程重启验证 |
| 4 | **F3-B 行为取舍** | 「统计行不再打开我的阅读」是交互变化，需产品确认；未采纳整卡可点+转发变体（seam：`DurationDisplay.kt` 归他窗所有权） |
| 5 | **与 S06 联合态** | G6 根部依赖 `MainActivity`（consumers3 底座含 S06 最小候选）；未与其他 S06 变体联合验证 |
| 6 | **lint / assembleDebug / APK / connectedAndroidTest** | 未运行（本轮只要求所属编译 + 关联定向 JVM） |
| 7 | **视觉布局** | 本候选保留 HEAD 的 `listOf`+`chunked(2)` 与本地私有卡片（`HomeStat`/`CreationMicroCard`/`SummaryMicroCard`/`YearBillMetricItem`），与工作树的静态网格+共享卡片形态**视觉不同**；不得宣称视觉等价 |
| 8 | **设备/真机/MuMu** | 本窗口未使用任何设备（任务约束） |

## 九、跨所有权 seam（只登记，不越界）

1. **设置搜索锚点**：工作树把 G6 开关包在 `SettingsAnchor("appearance_duration_day")` 里供设置搜索定位；候选刻意未引入（属设置搜索功能）。
2. **长按入口的整卡可点变体**：若产品要求统计行仍触发卡片点击，需改 `DurationDisplay.kt` 的 `durationUnitToggle()` 支持转发（现硬编码空 `onClick`）——该文件不属本候选。
3. **档案卡重设计**：工作树 `ArchiveStat`（String 形态）+ 共享卡片组件落地后，本候选的 `valueModifier` 需平移到 `ArchiveStat`。
4. **MetricMicroCard 抽取**：3 个本地私有卡片上的 `valueModifier` 适配届时应替换为共享组件调用。
5. **热力图格子点击微气泡**：独立新功能；纳入时须按 F3-B 口径复核格子 `clickable` 与长按的父子关系（本候选下该问题不存在，格子点击未纳入）。

## 十、纪律声明

- 共享恢复仓库内**唯一写入 = 本文件**。未改共享产品源码 / 测试 / 配置 / 其他窗口报告 / handoff。
- **未** stage / commit / push / reset / checkout / stash；**未**迁目录；**未**触碰原仓库、正式包、正式书库、设备、MuMu、ADB。
- 三个来源候选目录（consumers3 / settings / stats）**只读取**；收工复核 SHA 与开工前一致，未被修改。
- 本窗口全部写入在 `E:\creation-reading-assistant\stage-e-g6-integrated-20260930\`（候选树、`_cache` 2379 MB、`_evidence`、补丁）。
- 未把未运行项写作通过：§八 8 项逐条标注；§五.F3-B 明确区分「源码结构核查通过」与「手势/读屏/产品验收未验证」。
- 阶段 E 整体进度不在本报告内重新裁定；本报告只记录 G6 单一集成候选的合流与验证结果。
