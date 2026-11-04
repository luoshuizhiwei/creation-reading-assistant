# S15-F3-A-R1 执行结果（Claude / 窗口 B）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`
- 固定基点：`5aed5939600e491843e3c44453921bd6273a233c`
- 执行日期：2026-09-22
- 上一片：[agent-result-s15-f3-a-claude.md](agent-result-s15-f3-a-claude.md)

本片是**收尾片**：不重写任何生产接线，只修 KDoc 边界、报告口径、以及 androidTest 的
弃用手势 API。

---

## 一、最终计数口径（23 / 18 / 5）

| 口径 | 数值 |
|---|---|
| 独立时长**渲染位置**总数 | **23** |
| F3-A 之前已有 toggle | **13** |
| F3-A 之前没有 toggle | **10** |
| F3-A 新增代表性入口 | **5** |
| **F3-A 之后带 toggle 的渲染位置** | **18**（13 + 5） |
| **F3-A 之后不带 toggle 的渲染位置** | **5**（10 − 5） |

### YearBillHeroCard：3 个展示位置 / 1 个代表性入口

| 展示位置 | 是否带 toggle | 代码位置 |
|---|---|---|
| 主值 `cardDurationText` | **有**，且唯一 | `YearBillHeroCard.kt:115`；`valueModifier` 在第 125 行 |
| subtitle `"$cardDurationText $anchorYear"` | **无** | `YearBillHeroCard.kt:118` |
| headline 金句 `spoken`（两个互斥分支） | **无** | `YearBillHeroCard.kt:77` → `buildYearHeadline`（`spoken` 在第 255 行） |
| **合计** | **3 个展示位置 / 1 个代表性 toggle 入口** | |

**「YearBill 只有 1 个渲染点」这一说法已废弃**，不得再出现在任何文档里。

### 我的独立复核

对 `android/app/src/main/java` 全量 grep `formatDuration(` / `formatCompactDuration(` /
`DurationText(` 后，剔除 formatter 自身（`TimeUtils.kt`）、`StatsPage.kt` 的转发包装、
`DurationDisplay.kt` 的注释行与 `DurationText` 本体，剩余落在具体 UI 位置的调用点与
23 的口径一致。YearBill 的 `cardDurationText`（主值 + subtitle）与 `spoken`（headline）
确认为 3 个展示位置 —— 这三处均由 `formatCompactDuration` 产生，符合"渲染位置"定义。

---

## 二、KDoc 修改（剩余问题 2）

`DurationDisplay.kt` 的 `durationUnitToggle()` KDoc 被重写。**只改注释，modifier 运行
逻辑一行未动**（`combinedClickable` 的参数列表保持原样）。

### 修改前（错误表述）

> 刻意用 `combinedClickable` 挂在**时长文本自身**而不是外层卡片：文本原本没有点击
> 语义，消费它自己的点击不会影响卡片/列表的其它交互。……

问题：「消费它自己的点击不会影响卡片/列表的其它交互」被写成了**全局事实**，
但它是条件成立的。子级 `combinedClickable` 的空 `onClick` 同样会参与手势命中竞争，
可能截获本该交给父级的点击 —— 这正是 F3-B 要处理的问题。这条注释会误导后来者
把 modifier 挂到有父级点击的节点上。

### 修改后（准确边界）

新增的边界段落，逐条对应要求：

1. **适用范围**：本 Modifier 只适用于**没有父级点击 / 长按冲突的安全节点**
   —— 自身及祖先链上都没有 `clickable` / `combinedClickable` / `toggleable` 等手势
   容器，或该容器本身不再承担主交互的展示位。
2. **不保证子节点不影响父级手势**：`combinedClickable` 挂在子节点上时会参与命中竞争，
   空 `onClick` 仍可能截获本该交给父级的点击；`indication = null` 只关掉 ripple，
   不改变命中行为。
3. **必须由容器统一拥有**：处于可点击卡片、列表行或已有长按容器内的时长文本，
   手势必须由代表性容器统一拥有，而不是在子文本上再挂一个本 Modifier。
4. **不得父子两层重复挂**：两层各自消费手势会让父级主交互时灵时不灵，
   且 TalkBack 会读到两个同名操作。
5. **判定方法**：需要判定"挂在这里安不安全"时，向上检查祖先链有无手势容器，
   不要靠猜；并指出 F3-A 选的 5 个节点正是安全节点，F3-B 负责处理父级已有点击的节点。

保留的原有内容（仍在 KDoc 中，表述未变）：`indication = null` 的理由、
`onClick` 传空 lambda 是必填参数、一个语义组件只允许挂一次。

---

## 三、过时手势 API 清理

`DurationUnitToggleEntryTest.kt` 中的弃用 API **机械替换**，语义/节点选择/断言完全未变：

| 项 | 修改前 | 修改后 |
|---|---|---|
| import | `androidx.compose.ui.test.performGesture` | `androidx.compose.ui.test.performTouchInput` |
| 调用（第 216 行 `longPressEntry`） | `entryIn(rootTag).performGesture { longClick() }` | `entryIn(rootTag).performTouchInput { longClick() }` |

- `androidx.compose.ui.test.longClick` 的 import **保留**（它仍是 `performTouchInput`
  作用域内的合法手势，未弃用）；
- 全文件已无 `performGesture`（grep 确认，exit 1）；
- 编译后**无任何** deprecation warning（见第四节）。

---

## 四、验证

### 命令与 exit code

| 命令 | exit code | 结果 |
|---|---|---|
| `.\gradlew.bat --console=plain :app:compileDebugKotlin --rerun` | **0** | BUILD SUCCESSFUL in 1m 8s |
| `.\gradlew.bat --console=plain :app:compileDebugAndroidTestKotlin --rerun` | **0** | BUILD SUCCESSFUL in 20s |

### 弃用 warning 核查

对 `:app:compileDebugAndroidTestKotlin` 的完整输出执行
`grep -iE "^w:|deprecat|performGesture|longClick"`：**无任何匹配（grep exit 1）**。
即 `DurationUnitToggleEntryTest` 不再产生 `performGesture` / `GestureScope.longClick`
相关弃用警告。

> `:app:compileDebugKotlin` 仅有 1 条既有 warning，
> 位于 `LibraryBrowserRoute.kt:145`（`Icons.Outlined.Sort` 应改用 AutoMirrored 版本），
> 与本片无关且不在授权修改范围内，未处理。

### git diff --check

对 3 个授权文件（`DurationDisplay.kt`、`DurationUnitToggleEntryTest.kt`、
`agent-result-s15-f3-a-claude.md`）执行 `git diff --check`：**exit 0，无输出**。

### 文件 SHA-256

```
7c567f98ab78766c087c92c112822d13774644dad2247eb54fc0d0ca66ec3d48  android/app/src/main/java/com/creationreadingassistant/ui/util/DurationDisplay.kt
e9e23890903d2b15d1b8160febe29228610d5ecde7a60e9bf6c658825f2bfac5  android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/stats/DurationUnitToggleEntryTest.kt
416481968ac2386f1961d6a20e5c32ff0dee5ec9790b76ac9c277335235fd644  docs/coordination/agent-results/agent-result-s15-f3-a-claude.md
```

> **本文件（R1 报告）不自列哈希**：任何把自身哈希写进自身内容的做法，写入的瞬间就会让
> 该值失效，留下一个永远对不上的数字。需要校验本文件时，直接对它跑 `sha256sum` 即可
> —— 上面三行是 R1 实际改动的**产物**，它们才是需要冻结的。

---

## 五、收窄后的自动化覆盖声明（剩余问题 3）

**已从报告中删除**「测试能防止 Trend 柱出现重复入口」的声明。

### 修改前（报告原第 6 条）

> | 6. subtitle/headline/趋势柱不出现重复动作 | 递归计数整棵子树，计数 >1 即失败 |

问题：`DurationUnitToggleEntryTest` **根本不渲染 `TrendSection`**。`sampleStats()` 里的
`trend` 字段只是入参 —— 它不会让 `TrendSection` 被组合，更不会被断言遍历到。
把「趋势柱」列进覆盖范围是虚假声明。

### 修改后

> | 6. subtitle / headline 不出现重复动作 | 递归计数整棵子树，计数 >1 即失败（**仅在已渲染的 YearBill 子树内**） |

并在报告「四、代码改动」之后新增「自动化覆盖的**实际边界**」小节，明确：

- ✅ 5 个已渲染组件的「入口唯一性 + 长按一次 + 点击不触发」**有**自动化保证；
- ❌ **`TrendSection` 没有自动化渲染覆盖**；
- Trend 柱不带入口这一点，本片是作为**产品契约**明确排除的（依据：图表刻度不构成
  语义组件），**不是**由本测试文件防住的；
- 若将来有人给 Trend 柱挂上入口，**当前测试不会失败**；要防住需另开切片，
  **本片（含 R1）刻意不扩大到新增 Trend 测试**。

同时把「入口唯一性用遍历计数」的说法加上限定：该保证只在**已渲染的**子树内成立。

---

## 六、允许修改范围的自查

本片实际修改 **3 个文件**，全部在授权清单内：

| # | 文件 | 改动性质 |
|---|---|---|
| 1 | `android/app/src/main/java/com/creationreadingassistant/ui/util/DurationDisplay.kt` | 仅 KDoc（`durationUnitToggle()` 的边界说明） |
| 2 | `android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/stats/DurationUnitToggleEntryTest.kt` | 仅 import + 1 处调用，机械替换弃用 API |
| 3 | `docs/coordination/agent-results/agent-result-s15-f3-a-claude.md` | 口径更正 + Trend 声明收窄 + SHA 失效标注 |

新增 **1 份**报告：`docs/coordination/agent-results/agent-result-s15-f3-a-r1-claude.md`（本文件）。

**未修改**（明确确认）：

- 五个生产组件的接线（HomeMetrics / SummaryGroup / CreationSection / Heatmap 汇总 /
  YearBill 主值）—— 未重写，未触碰；
- `DurationPrefs.kt`、`MetricMicroCard.kt`、`HomeMetricsSection.kt`、`SummaryGroup.kt`、
  `CreationSection.kt`、`HeatmapSection.kt`、`YearBillHeroCard.kt`；
- 业务实现、公共 formatter（`TimeUtils.kt`）、`StatsPage.kt`；
- 窗口 C / D 的任何文件；
- 协调看板、handoff、Obsidian 记忆库。

---

## 七、未执行项（按约束明确不做）

- **未**运行全量 JVM 测试（仅跑编译，未跑 `testDebugUnitTest`）；
- **未**运行 Lint、APK 构建、ADB、真机或模拟器验证；
- **未**执行 `reset`、`checkout`、`clean`、`stash`、`stage`、`commit`、`push`；
- **未**做全仓格式化；
- **未**进入或修改原仓库 `D:\develop\Code\Codex\creation-reading-assistant`。

`DurationUnitToggleEntryTest` 的 10 个用例仍是 instrumentation，
**只过编译门禁，未在设备执行** —— 真机 / TalkBack 验证属于 F3-C 切片。

---

## 八、开始与结束 git status

**开始**：`git status --short` = **382 行**；HEAD = `5aed593`
**结束**：`git status --short` = **382 行**；HEAD = `5aed593`（未变）

对起止两份 `git status --short` 排序后 `diff`：**零差异**，逐行一致。

行数不变是**正确且预期的**，原因有二：

1. 本片只修改**已存在**的文件内容，未改动任何文件路径 —— 修改已跟踪文件只改变其状态
   标记（`M`），不新增行；`DurationDisplay.kt` 与 `DurationUnitToggleEntryTest.kt`
   **在 F3-A 期间就已分别处于 `M` 与 `??` 状态**，本片继续修改不改变其条目数。
2. 新增的 R1 报告落在 `docs/coordination/` 下，而**该目录整体是 untracked**，
   Git 只输出目录一行（`?? docs/coordination/`）而不逐个列出其中文件。
   因此新增一个 .md 不会改变 `git status --short` 的行数。

本片对工作区的净影响：**0 个新增路径，2 个已存在文件的内容变更，1 个新报告文件
（被 untracked 目录吸收）**。

`git diff --cached` **为空**（无任何 staged 改动）。

---

## 九、声明

- 未修改原仓库 `D:\develop\Code\Codex\creation-reading-assistant`。
- 未 commit、未 push、未 stage。
- 未修改窗口 C / D 文件、业务实现、公共 formatter、协调看板、handoff、Obsidian。
- 生产接线的 5 个安全语义组件**保持原样**，本片未重写。

完成后停止，未领取下一任务。
