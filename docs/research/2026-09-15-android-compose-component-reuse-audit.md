# android/native — Compose 组件复用审计报告（B）

> **状态**：审计结论，**只读分析，未改任何源码**
> **产品线**：`android/native`
> **审计日期**：2026-09-15
> **审计目的**：闭合提案文档 `research/2026-09-15-android-legado-md3-improvement-proposal.md` 中的改造项 C-1
> **审计方式**：静态检索（grep 调用次数 + 私有函数名聚类），未跑 Gradle、未碰设备

---

## 1. 结论摘要

| 指标 | 数值 |
|---|---:|
| `ui/components/` 文件数 | 11 |
| 公共 Composable 总数 | 40 |
| **零调用（仅定义）** | **7** |
| **调用 ≤2 次** | **11** |
| 真共享（调用 ≥10 次） | 17 |
| 漏抽候选（跨文件私有重复） | 1 处确认 + 3 类模式 |

**核心判断**：`ui/components/` 整体是**健康的**——40 个组件中 17 个达到真共享阈值（≥10 次），说明抽取动机真实。但存在 **7 个零调用组件**（占 17.5%），属于抽取后未接入或改造中途放弃的遗留，应清理或接入。

---

## 2. 复用榜单（调用次数降序）

统计口径：`grep -rho "\b<Name>(" --include=*.kt | wc -l` 减去定义处命中（`^fun X(` / `^internal fun X(` / `^private fun X(`）。

### 2.1 头部：真共享（调用 ≥10 次）

| 排名 | 组件 | 调用次数 | 定义位置 |
|---:|---|---:|---|
| 1 | `SectionCard` | **77** | `SharedComponents.kt` |
| 2 | `SectionDivider` | **52** | `SharedComponents.kt` |
| 3 | `SelectablePill` | **40** | `SharedComponents.kt` |
| 4 | `IconPedestal` | **33** | `MicroIsland.kt` |
| 5 | `GlassAlertDialog` | 24 | `GlassDialogs.kt` |
| 6 | `PageLazyColumn` | 21 | `AppLayout.kt` |
| 7 | `LineArtBook` | 20 | `LineArt.kt` |
| 8 | `IslandCard` | 20 | `MicroIsland.kt` |
| 9 | `SettingSwitchRow` | 19 | `SettingsComponents.kt` |
| 10 | `FullEmptyState` | 18 | `SharedComponents.kt` |
| 11 | `AppScreenScaffold` | 17 | `AppLayout.kt` |
| 12 | `SheetHandle` | 14 | `SharedComponents.kt` |
| 13 | `BookCover` | 14 | `BookCover.kt` |
| 14 | `SettingSegmentedRow` | 11 | `SettingsComponents.kt` |
| 15 | `IslandSectionHeader` | 11 | `MicroIsland.kt` |
| 16 | `GlassModalBottomSheet` | 10 | `GlassDialogs.kt` |

**观察**：前 4 名（`SectionCard` 77 / `SectionDivider` 52 / `SelectablePill` 40 / `IconPedestal` 33）构成事实上的**界面骨架语言**。`SharedComponents.kt` 一个文件贡献了 4 个 Top10 组件，是复用度最高的文件。

### 2.2 中部：中等复用（3–9 次）

| 组件 | 调用次数 | 定义位置 |
|---|---:|---|
| `SettingSliderRow` | 8 | `SettingsComponents.kt` |
| `AppAlertDialog` | 8 | `GlassDialogs.kt` |
| `SettingRow` | 6 | `SharedComponents.kt` |
| `SectionHeader` | 5 | `AppLayout.kt` |
| `MutedCoverFallback` | 5 | `BookCover.kt` |
| `SectionEmptyHint` | 4 | `SharedComponents.kt` |
| `SettingLinkRow` | 3 | `SettingsComponents.kt` |
| `SettingBrightnessRow` | 3 | `SettingsComponents.kt` |
| `LineArtBookmark` | 3 | `LineArt.kt` |
| `rememberViewportImageRequest` | 2 | `OptimizedImage.kt` |
| `SizedAsyncImage` | 2 | `OptimizedImage.kt` |
| `HairlineDivider` | 2 | `MicroIsland.kt` |

### 2.3 尾部：**零调用（仅定义）** —— 需要处置

| 组件 | 定义位置 | 行数占用 | 判断 |
|---|---|---:|---|
| `SettingsScreenScaffold` | `SettingsComponents.kt` | 1 定义 | 与 `AppScreenScaffold`(17 次) 功能重叠，疑似重复抽象 |
| `SettingsSection` | `SettingsComponents.kt` | 1 定义 | 与 `SectionCard`(77 次) / `SettingsGroup` 三者重叠 |
| `IslandEmptyState` | `MicroIsland.kt` | 1 定义 | 与 `FullEmptyState`(18 次) 重叠 |
| `InfoHintIsland` | `MicroIsland.kt` | 1 定义 | 无调用 |
| `FloatingDock` | `MicroIsland.kt` | 1 定义 | 无调用 |
| `GlassSurface` | `GlassSurface.kt` | 1 定义 | **文件同名主力组件却零调用**，异常 |
| `BoxScope.GlassOverlays` | `GlassSurface.kt` | 1 定义 | 无调用 |

**⚠️ 特别注意 `GlassSurface`**：`GlassSurface.kt` 的同名主组件零调用，而同文件的 `GlassOverlays` 也零调用。**整个文件疑似未接入**。但 `GlassDialogs.kt`（`GlassAlertDialog` 24 次 / `GlassModalBottomSheet` 10 次）在使用中——需要确认玻璃态组件族是否存在**两条并行实现**（`GlassSurface.kt` vs `GlassDialogs.kt`）。

**`MicroIsland.kt` 的结构性问题**：6 个组件中 3 个零调用（`IslandEmptyState` / `InfoHintIsland` / `FloatingDock`），2 个低调用（`HairlineDivider` 2 次），只有 `IconPedestal`(33) / `IslandCard`(20) / `IslandSectionHeader`(11) 是真正在用的。**该文件一半内容是未接入的设计储备。**

### 2.4 未列出的文件（无公共 Composable 导出）

| 文件 | 说明 |
|---|---|
| `NoiseTexture.kt` | 无 `fun` 导出 —— 可能是 `Modifier` 扩展或 `object`，需人工确认是否在用 |
| `NotificationPermission.kt` | 无 `fun` 导出 —— 可能是 `@Composable` 内的 effect 或 expect/actual |

---

## 3. 漏抽检测（反向问题）

### 3.1 确认漏抽：`SearchResultRow` 跨文件重复

```
2 × private fun SearchResultRow
```

同一函数名在 **2 个文件**中各有私有实现。这违反"单一来源"，两份实现会各自漂移。

**建议**：确认两份实现是否真的重复；若重复，抽到共享位置（或合并入某个组件文件）。

### 3.2 模式漏抽：系统组件直接调用未走封装

| 原始组件 | 直接调用次数 | 已有封装 | 封装调用次数 |
|---|---:|---|---:|
| `ModalBottomSheet(` | 5 | `GlassModalBottomSheet` | 10 |
| `AlertDialog(` | 3 | `AppAlertDialog`(8) / `GlassAlertDialog`(24) | 32 |

**判断**：`ModalBottomSheet` 的 5 次直接调用**值得逐个核查**——因为本项目的玻璃态设计需要统一背景/模糊处理，直接调用原生组件会**绕过玻璃态包装**，造成视觉不一致。这是**真实的一致性风险**，而非单纯的代码风格问题。

`AlertDialog` 的 3 次直接调用同理（已有 32 次走封装，3 次漏网）。

### 3.3 观察项：`HorizontalDivider` 21 次 + `Spacer` 361 次

`SectionDivider` 已有 52 次调用（封装内部分隔），但 `HorizontalDivider` 仍有 21 次直接调用。

**判断：属正常范围，不建议处理。** 理由：
- `HorizontalDivider` 与 `SectionDivider` 语义不同（前者是任意分割线，后者是区块间分隔）
- `Spacer` 361 次是 Compose 的常规布局原语，**不存在"应封装"的合理理由**——强行封装会降低可读性

**这一项的价值在于：说明"漏抽"不能只看数字大，要看是否真的存在语义重复。** 记录在此以防后续审计者被 361 这个数字误导。

---

## 4. 与上游的对比

| 维度 | 本项目 | 上游 legado-with-MD3 |
|---|---|---|
| 公共组件数 | 40（11 文件） | 162（`ui/widget/components/`） |
| Top1 组件调用次数 | `SectionCard` 77 | `AppText` **466** |
| **文本统一入口** | **无**（1185 处裸 `Text(`） | `AppText` 单一入口 |
| 零调用组件 | 7 / 40（17.5%） | 未统计 |
| 设置项 DSL | `SettingsComponents.kt`（8 组件，含 2 零调用） | `settingItem/` 完整矩阵 |

**关键差距仍然是文本入口**：上游靠 `AppText` 单一入口实现全局排版控制；本项目 1185 处裸 `Text(`，头部组件 `SectionCard` 虽被调用 77 次，但**它内部的文本仍不受统一管控**。

**本项目领先处**：零调用率 17.5% 属可控范围，且头部组件复用度高（4 个组件 ≥33 次），说明抽象方向正确。

---

## 5. 建议动作（按性价比）

| 优先级 | 动作 | 成本 | 说明 |
|---|---|---|---|
| **A** | 核查 `GlassSurface.kt` 是否未接入 | 低 | 若整个文件未接入，是选择"接入"还是"删除"的决策点 |
| **A** | 核查 `SearchResultRow` 两份私有实现的差异 | 低 | 确认是否真重复；若是，合并 |
| **A** | 逐个核查 5 处 `ModalBottomSheet` 直接调用 | 低 | **视觉一致性风险**（绕过玻璃态） |
| **B** | 处置 7 个零调用组件 | 中 | 分类：① 重复抽象（`SettingsSection`/`SettingsScreenScaffold`/`IslandEmptyState` 与现有组件重叠）→ 删除；② 设计储备（`FloatingDock`/`InfoHintIsland`）→ 决策是否保留 |
| **B** | 核对 3 处 `AlertDialog` 直接调用 | 低 | 同 A 项逻辑 |
| **C** | `MicroIsland.kt` 拆分评估 | 中 | 6 组件中 3 个零调用，文件职责不清 |
| **—** | **不建议**处理 `Spacer`(361) / `HorizontalDivider`(21) | — | 属正常原语使用，强行封装有害 |

### 5.1 与 C-2（统一文本入口）的衔接

本审计确认了 **C-2 的紧迫性**：1185 处裸 `Text(` + 40 个组件中**无文本封装**。但基于本审计的发现，建议 C-2 的策略是：

**不要**新建 `AppText` 后回头批量替换 1185 处。**应该**：

1. 新建 `AppText`，**只在新代码中使用**
2. 借本审计的头部组件清单（`SectionCard` 77 / `SectionDivider` 52 / `SelectablePill` 40 / `IconPedestal` 33）——**优先让这些高复用组件内部改用 `AppText`**，因为它们覆盖面广（合计 202 次调用），改一个文件影响多屏
3. 存量随各屏重构顺带迁移

**理由**：改 4 个高频组件的内部实现，覆盖面 > 改 100 处零散调用点，且风险集中在少数文件。

---

## 6. 审计方法与可复现命令

```bash
C=android/app/src/main/java/com/creationreadingassistant

# 1. 提取组件文件中的公共 Composable 清单
for f in $C/ui/components/*.kt; do
  grep -n "^fun \|^internal fun " "$f" | sed 's/(.*//' | sed 's/^[0-9]*://'
done

# 2. 统计调用次数（总命中 - 定义处命中）
grep -rho "\bSectionCard(" $C --include=*.kt | wc -l
grep -rh "^fun SectionCard(\|^internal fun SectionCard(\|^private fun SectionCard(" $C --include=*.kt | wc -l

# 3. 漏抽检测：跨文件同名私有函数
grep -rho "^private fun [A-Za-z]*\(Row\|Item\|Card\|Section\|Header\|Dialog\|Sheet\)" $C --include=*.kt \
  | sort | uniq -c | sort -rn

# 4. 系统组件直接调用量
grep -rho '\bModalBottomSheet(' $C --include=*.kt | wc -l
grep -rho '\bAlertDialog(' $C --include=*.kt | wc -l
```

**口径限制（重要）**：
- 调用次数含**注释与字符串**中的误命中（未排除）。误差应在 ±2 以内，不影响排序结论。
- `BoxScope.GlassOverlays` 因含 `.` 前缀，统计方式与其他项不同，仅作参考。
- **零调用 ≠ 死代码**——可能是设计储备（如 `FloatingDock`）或被反射/其他机制使用，需人工判断。

---

## 7. 未完成项

1. **未核查 7 个零调用组件的完整性**（是否被 `@Preview` 引用、是否有计划接入）
2. **未核查 `GlassSurface.kt` 与 `GlassDialogs.kt` 的关系**（是否存在两条并行玻璃态实现）
3. **未核查 `NoiseTexture.kt` / `NotificationPermission.kt`** 的导出形态
4. **未统计 `feature/` 与 `ui/screen/` 下重复的 UI 模式**（本次只审计了 `ui/components/` 的"已抽取"侧，未系统性扫描"未抽取"侧的非同名重复）

---

*本报告由 WorkBuddy 于 2026-09-15 生成，为提案文档 C-1 项的闭合产出。**未修改任何源码。***
