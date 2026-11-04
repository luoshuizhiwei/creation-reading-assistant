# S15-F3-B-DESIGN（窗口 D / Qoder，严格只读）— 四个带父级点击的时长节点的事件所有权

- 任务：S15-F3-B-DESIGN｜上游：`docs/coordination/investigations/agent-investigation-s15-qoder.md`（S15 盘点）、`agent-result-s15-f4-r1-qoder.md`（状态层已测）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜固定基点 `5aed5939600e491843e3c44453921bd6273a233c`
- 时间：2026-09-22 11:45–12:27（本机 +08:00）
- 本轮唯一新增文件：`docs/coordination/designs/agent-design-s15-f3-b-qoder.md`（本文件）
- **本轮未修改任何 Kotlin / 测试 / Gradle / 看板 / handoff；未运行 Gradle、ADB、APK、真机或模拟器。**
- 编号沿用窗口 B 设计文档 `agent-design-s15-f3-claude.md` 的节点号（#3 / #10 / #12 / #15），便于跨窗口对账；**本轮不评价窗口 B 的在途实现**，只标出文件交集与顺序依赖。

## 0. 结论先行

四处节点最终归属：

| 节点 | 普通点击归谁 | 长按切单位归谁 | 长按管理菜单 | 结论一句话 |
|---|---|---|---|---|
| #3 `SessionMicroIsland` | 父行（展开/收起该条） | **时长 pill** | 无 | pill 保留长按，但必须把"空点击"改成转发父级展开 |
| #10 `CompletedBookCard` | 父卡（打开该书） | **时长 pill** | 已有显式按钮（非长按） | 同上；**不要**为此改公共 `SectionCard` |
| #12 `TimelineBookRow` | 父行（打开该书） | **移除该处入口** | 父行（整行唯一长按） | 一行不得有两个长按语义；本页切单位入口保留在页首聚合时长 |
| #15 `HomeReadingArchiveSection` | 父卡（进入阅读档案） | **父卡** | 无 | 时长是整句聚合文本，长按归卡片代表区域 |

三条统一的归属规则（后续节点也应照此判定，避免逐处再吵）：

1. **规则 A｜点击不被子级吞**：卡片/行级的导航或展开语义永远归父级。子级时长元素**不得**再声明"空点击"。现状 `Modifier.durationUnitToggle()` 写的是 `onClick = {}`（`ui/util/DurationDisplay.kt:56-61`），它既是一个真实的点击消费者（父级收不到落在文字上的点击），又向 TalkBack 申报一个"双击即可激活"却什么都不做的节点。
2. **规则 B｜一个组件只有一个长按**：父级已有业务长按时（只有 #12），长按唯一归父级，子级不得再有切单位入口；父级没有长按语义时（#3/#10/#15），长按可以给"时长代表区域"。理由写在使用处注释里：`DurationPrefs` 是全局单一偏好（`data/settings/DurationPrefs.kt:49-55`），切一次全屏跟随，所以**每屏有一个入口即可**，不需要每段重复文字都装手势。
3. **规则 C｜手势层必须等于可见容器**：现在 `DurationText` 把 `combinedClickable` 加在调用方传入的 `padding` **内侧**（`DurationText` 内 `modifier.durationUnitToggle()`，`DurationDisplay.kt:80-82`；调用方如 `BookDetailStatsSection.kt:275`、`HomeCompletedScreen.kt:506`），所以命中盒是文字行盒，胶囊的内边距那一圈仍归父级 —— 同一颗胶囊中心长按生效、边缘长按会走到父级。凡是决定"pill 拥有长按"的地方，手势都要挂在 **pill 容器**（含图标与 padding）上。

> 规则 A/B/C 的行为后果（点击被吞、边缘漏触、TalkBack 播报内容）都属于**静态推断**：Compose 的手势仲裁与读屏播报必须真机确认，见 §8。本轮没有据此定现役 P2。

---

## 一、四处节点的源码位置与父子 Modifier 顺序

**#3 `SessionMicroIsland`**（`ui/screen/shelf/BookDetailStatsSection.kt`）

```
:221 private fun SessionMicroIsland(...)                 ← private，androidTest 直测不到（§6/§10）
:237 Surface(shape, color, border,
:241   modifier = Modifier.fillMaxWidth()
:243     .clip(RoundedCornerShape(10.dp))
:244     .clickable { itemExpanded = !itemExpanded })    ← 父级：普通点击＝展开/收起【该条】记录
:246   Column(… .animateContentSize(...))                ← 展开动画挂内容层
:265     Surface(shape 6.dp, 琥珀色)                      ← 时长 pill 的可见容器（当前不可点击）
:270       DurationText(ms = session.duration_ms,
:275         modifier = Modifier.padding(h 6, v 2))      ← 内含 combinedClickable(onClick={}, onLongClick=toggle)
:281     Surface(… "进度 N%" pill)                        ← 无手势
:297     Icon(展开箭头, contentDescription = null)
```
另有**同级**（不冲突）的列表级按钮：`:186 Surface(onClick = { expandedAll = !expandedAll })`，文案"展开更多记录 / 收起阅读记录"。注意任务书把父级写成"展开/收起会话记录"，实际父级点击是**该条记录自身**的详情展开（`:227 itemExpanded`），列表级展开是另一个兄弟节点 —— 实施时别把两层混成一个。

**#10 `CompletedBookCard`**（`ui/screen/homearchive/HomeCompletedScreen.kt`）

```
:385 internal fun CompletedBookCard(item, onOpen, onManage, modifier)
:391 SectionCard(modifier = … .testTag("completed-book-${id}"),
:395   onClick = onOpen, contentPadding = 12.dp)         ← 父级：普通点击＝打开该书
        └ 公共组件内部：SharedComponents.kt:130 Box(cardModifier)
                        :132 Surface(onClick = { haptic(); onClick() }, interactionSource = cardInteraction)
                                                   ← 点击层在【内层 Surface】，不是外层 Box
:499  if (item.progress.total_reading_time_ms > 0)
:500    Surface(琥珀色 pill)                             ← 可见容器（当前不可点击）
:505      Row(modifier = Modifier.padding(h 6, v 2))
:510        Icon(AccessTime, contentDescription = null)
:516        DurationText(…)                             ← combinedClickable(onClick={}, onLongClick=toggle)
:533    Surface(onClick = onManage, size 36.dp)          ← 管理动作＝显式按钮
:543      Icon(contentDescription = "管理《${title}》")
```
父级**没有**长按（`Surface(onClick)` 只有点击）。

**#12 `TimelineBookRow`**（`ui/screen/readinghistory/MyReadingScreen.kt`）

```
:435 private fun TimelineBookRow(...)
:485 IslandCard(modifier = Modifier.weight(1f)
:488   .combinedClickable(onClick = onOpen, onLongClick = onManage)   ← 父级：点击＝打开书；长按＝管理菜单
:489   .testTag("reading-item-${id}"), contentPadding = 12.dp)
       └ MicroIsland.kt:56 IslandCard → SharedComponents.kt:72 SectionCard
         本调用点未传 onClick ⇒ SectionCard 走 :143 的【不可点击】Surface 分支；
         所以整条链上父级手势层只有一个（外层 Box 上的 combinedClickable），干净。
:560   Row(horizontalArrangement = spacedBy(6.dp))
:564     ReadingStateBadge(item)                         ← 无手势
:565     DurationText(ms = item.readingTimeMs, …)        ← combinedClickable(onClick={}, onLongClick=toggle)
:573   IconButton(onClick = onManage, size 36.dp)        ← 管理动作【另有】显式按钮
:579     Icon(contentDescription = "管理《${title}》")
```
**本页保留的切单位入口**（不冲突）：`:246 DurationText`，位于 `:231 Surface(...)`（无 onClick、无长按）内的统计胶囊里，是页首聚合时长。

**#15 `HomeReadingArchiveSection`**（`ui/screen/home/components/HomeReadingArchiveSection.kt`）

```
:46 fun HomeReadingArchiveSection(totalReadingMs, totalReadBooksCount, onClick, modifier)
:58 Surface(modifier = modifier.fillMaxWidth()
:61   .animateEnter(30, reducedMotion)
:62   .bounceable(interactionSource)
:63   .clickable(role = Role.Button, interactionSource = interactionSource, indication = null,
:67     onClick = { haptic(…); onClick() })              ← 父级：普通点击＝进入阅读档案
:72   .testTag("reading-archive-card"), …)
:80   Box(… .padding(layout.cardPadding))
:94     Row(…)
:100      Box(36dp 琥珀底座) → Icon(contentDescription = null)
:126      Column(
:130        Text("累计阅读")
:135        Text("${formatDuration(totalReadingMs, LocalDurationUnit.current)} · $totalReadBooksCount 本")
                                                       ← 时长与「N 本」挤在同一整句 Text，当前无 toggle、无手势
:142      Box(26dp 箭头底座) → Icon(contentDescription = null)
```

---

## 二、当前事件矩阵（静态推断，未真机验证）

| 节点 | 落在时长文字上 | 落在 pill 内边距圈 | 落在卡片/行其它区域 | 父级长按 | TalkBack 焦点 |
|---|---|---|---|---|---|
| #3 | 点击：**无动作**（子级 `onClick={}` 吞掉）；长按：切单位 | 点击：展开该条；长按：**无动作** | 点击：展开该条；长按：无 | 父级无长按 | 文字节点自成一个可点击节点，读作"双击即可激活"但无动作；pill 容器不可聚焦 |
| #10 | 点击：**无动作**（吞掉"打开该书"）；长按：切单位 | 点击：打开书；长按：无 | 点击：打开书 | 父级无长按（管理是 `:533` 显式按钮） | 同上 |
| #12 | 点击：**无动作**（吞掉"打开该书"）；长按：**切单位**（吞掉"管理菜单"） | 点击：打开书；长按：**管理菜单** | 点击：打开书；长按：管理菜单 | 父级有长按 | **同一行两种长按语义**；整行是一个可点击节点，pill 是另一个可点击节点 |
| #15 | 点击：进入档案；长按：**无**（该处根本没有切单位入口） | 同左 | 同左 | 父级无长按 | 整卡 `Role.Button` 一个节点；时长与"N 本"同句播报 |

两点必须写明：
- 上表"无动作"均来自 `combinedClickable(onClick = {})` 这一处源码事实（`DurationDisplay.kt:59`），但**"是否真的吞掉父级点击"要靠真机确认**（Compose 的手势仲裁在嵌套 `pointerInput` 下的实际行为受命中区、`clip`、滚动容器影响）。在没有设备证据前，它是 **推断 / readiness**，不定为现役 P2。
- #15 与任务书一致：时长文本**确实没有** `durationUnitToggle`。而它现在被 `:136` 的整句拼接结构挡住 —— 给文本加子级手势会把"· 5 本"一起变成手势区。

---

## 三、推荐后的事件矩阵

| 节点 | 时长 pill 点击 | 时长 pill 长按 | 卡片/行其余区域 | 长按语义数量 | 需要新增的语义标签 |
|---|---|---|---|---|---|
| #3 | 展开/收起该条（与父级一致） | 切单位（不展开） | 点击＝展开；长按＝无 | 1（pill） | pill 容器 `onLongClickLabel = "切换时长单位"` |
| #10 | 打开该书（与父级一致） | 切单位（不打开书） | 点击＝打开书；长按＝无 | 1（pill） | 同上 |
| #12 | —（pill 不再有任何手势，回归纯文本） | — | 点击＝打开书；长按＝管理菜单 | 1（整行） | 整行 `combinedClickable(onLongClickLabel = "打开管理操作")`（对齐 `BookGrid.kt:282` 既有惯例） |
| #15 | 整卡：点击＝进入档案；长按＝切单位（不导航） | 同左（同一层） | 同左 | 1（父卡） | `onLongClickLabel = "切换时长单位"`，点击侧保留 `Role.Button` |

推荐后的共同点：每处**只有一层手势**（一个 `combinedClickable`），子级不再出现空 `onClick`；长按与点击在同一节点上互斥，由 Compose 的 tap detector 自己分流，不需要位置命中判断。

---

## 四、逐节点的唯一推荐方案（不给选项）

### 4.1 #3 `SessionMicroIsland` —— toggle 留在 pill，pill 改为"点击转发 + 长按切换"

- **长按 pill 时是否应阻止父级展开**：是。父级今天没有长按语义，pill 的长按是唯一入口；抬手后不得展开该条详情。
- **普通点击 pill 是展开还是无动作**：**展开**（转发父级 `itemExpanded = !itemExpanded`）。"无动作"是当前的实现副作用，不是设计。
- **是否把 toggle 上移到整行**：**不上移**。整行长按＝偏好开关会让"行级长按"这个未来可能被会话级操作（如删除该条记录）占用的手势提前被吃掉，也违反"时长代表区域"的直觉。
- **实现**：`BookDetailStatsSection.kt:265` 那颗 pill 的 `Surface` 加 `Modifier.combinedClickable(onClick = { itemExpanded = !itemExpanded }, onLongClickLabel = "切换时长单位", onLongClick = toggle)`，`toggle = LocalDurationUnitToggle.current` 在 `:221` 处取；`:270` 的 `DurationText` 换成 `Text(formatDuration(session.duration_ms, LocalDurationUnit.current))`，避免两层点击。**只动这一个文件**，不碰 `DurationDisplay.kt`。
- **直测前提**：`:221` 是 `private`，androidTest 无法直接渲染；改为 `internal`（同文件、同包，Kotlin 的 androidTest 是 main 的 friend compilation，仓库既有 `internal CompletedBookCard` 就是这个用法）。

### 4.2 #10 `CompletedBookCard` —— 同上，且**不改公共 `SectionCard`**

- **点击 pill 是否仍应打开书**：是（规则 A）。
- **长按 pill 是否只切单位、不打开书**：是（父级无长按，pill 是时长代表区域）。
- **是否把父卡 `SectionCard(onClick)` 改成 `combinedClickable`**：**不需要，且不推荐**。原因有三层：①`SectionCard` 的点击在**内层** `Surface(onClick=…)`（`SharedComponents.kt:131-141`），调用方在 `modifier` 上再套 `combinedClickable` 只会形成父子双点击，父级拿不到 tap；②给 `SectionCard` 加 `onLongClick` 是公共组件签名扩张，仓库现有 **73 处 `SectionCard(` 调用点、跨约 20 个文件**（`grep -c` 实测），还会牵动 `bounceable`/`haptic`/`cardInteraction` 的共享手感语义；③`IslandCard`（`MicroIsland.kt:56`）委托 `SectionCard`，公共改动会同时漂到微岛体系。
- **不改公共组件的优先方案（即本片的最终方案）**：手势留在 **pill 自身**——`HomeCompletedScreen.kt:500` 的 pill `Surface` 加 `combinedClickable(onClick = onOpen, onLongClickLabel = "切换时长单位", onLongClick = toggle)`，`:516` 的 `DurationText` 换成纯 `Text`。父卡、`onManage` 按钮（`:533`）一律不动。
- **管理菜单**：这里已经是显式按钮 + `contentDescription = "管理《…》"`，不需要长按承载，也不产生与本切片竞争的长按语义。

### 4.3 #12 `TimelineBookRow` —— **移除 pill 的 toggle**，整行长按唯一归管理菜单

明确推荐：**去掉这一处的 `durationUnitToggle`（即该 pill 不再有任何手势），保留整行 `combinedClickable(onClick = onOpen, onLongClick = onManage)` 不变。**

理由（按权重）：
1. 这是四处里**唯一**父级已有业务长按的节点。同一行"在封面旁长按＝管理菜单，在时长上长按＝切单位"是无法靠命名解决的：用户看不见边界，读屏用户也得不到两个可预期动作。规则 B 要求"已有业务长按优先级更高"。
2. 切单位是**全局偏好**（`DurationPrefs.kt:49-55` 明示"全局单一偏好，任意页面长按都会切换它"），一次切换该行文本立即跟随，移除入口不减少用户能看到的信息。
3. 本页仍有合规入口：`:246` 页首聚合时长（父级无点击、无长按）。**前置确认项**：`:246` 的 toggle 由 F3-A 负责带 `onLongClickLabel`；本片不动 `DurationDisplay.kt`，只依赖其存在（§10 依赖表）。
4. 整行长按是既有的肌肉记忆入口，且行内另有 `IconButton(onClick = onManage)`（`:573`）—— 说明"管理"既能点也能长按；把它的长按在手心位置抢走，风险大于收益。
- **用户在同一行不同区域长按能否触发不同动作**：**不能**（这是本片的产品裁决落点，见 §9）。
- **TalkBack 会不会得到两个名称清楚的长按动作**：不采用这个形态。现状（两个嵌套可点击节点）下读屏用户到底听到几个动作、焦点是否被 pill 截断，是**未验证的静态推断**；移除后行级只剩一个长按动作，再补 `onLongClickLabel = "打开管理操作"`（仓库已有同名惯例：`BookGrid.kt:282` `onLongClickLabel = "打开书籍操作"`），播报面在真机上验收（§8）。

### 4.4 #15 `HomeReadingArchiveSection` —— 父卡升级为 `combinedClickable`

- **给文本加子级长按，还是父卡升级**：**父卡升级**。文本是 `:136` 的整句 `"累计 12 小时 0 分钟 · 5 本"`，给文本加手势会把"N 本"一起变成手势区，且这是**全仓唯一的"累计阅读"主信息面**，不该被降级成一小块可长按的字。
- **普通点击必须继续进入档案**：`:63` 的 `.clickable(role = Role.Button, …)` 原地换成 `.combinedClickable(role = Role.Button, interactionSource = interactionSource, indication = null, onClick = { haptic(…); onClick() }, onLongClickLabel = "切换时长单位", onLongClick = toggle)`。`interactionSource` 与 `:62` 的 `bounceable` 共用同一个 source，按压手感不变；点击回调、`testTag("reading-archive-card")`、`animateEnter` 全部保持。
- **长按切单位后不得同时导航**：由同一个 detector 分流（Compose 判定为长按后不会再发 click）；抬手不得导航需真机确认（§8）。
- **TalkBack 播报**：卡片是**一个** `Role.Button` 节点，应得到两条动作 —— 默认"双击即可激活"（进入阅读档案）+ `onLongClickLabel` 的"切换时长单位"。切换结果已有 Toast 回执（`MainActivity.kt:81-96`）。这样"整张大卡劫持导航语义"对用户是**可读的**：导航动作不变、新增一条具名动作，而不是静默多一种手势。
- **可选（不在本片默认做）**：把 `:136` 的整句拆成"时长 + 分隔 + 本数"三段以便未来复用；拆分属排版改动，与手势归属无关，本片不做。

---

## 五、是否需要修改公共组件

**结论：本片不需要，也不应改公共组件。**

| 公共件 | 本片是否需要改 | 说明 |
|---|---|---|
| `ui/components/SharedComponents.kt`（`SectionCard`） | **否** | 73 处调用点；加 `onLongClick` 会外溢到全部卡片与 `IslandCard` 委托链 |
| `ui/components/MicroIsland.kt`（`IslandCard`） | **否** | 只是 `SectionCard` 的同签名委托（`:62-67`） |
| `ui/util/DurationDisplay.kt` | **否**（本片只读引用） | 它是**窗口 B 的 S15-F3-A 在途文件**；本片用"pill/父卡自带 combinedClickable + 纯 `Text`"的方式绕开，零交集 |
| 公共 `formatDuration` / `LocalDurationUnit` / `LocalDurationUnitToggle` | **只读使用** | `LocalDurationUnitToggle` 是 `public`（`DurationDisplay.kt:43`），节点文件可直接取用，不需要新公共 API |

代价与取舍：pill 侧从"复用 `DurationText`"变成"用 `Text(formatDuration(…, LocalDurationUnit.current))` + 自己挂手势"，即**三处节点重复 2 行代码**。这是有意的：把手势所有权写在各节点的容器层（可读、可测、可单点回滚），好过为省 2 行去扩张公共 modifier 的参数面 —— 后者会立刻和 F3-A 撞同一个文件。

---

## 六、文件所有权清单（本片视角）

| 文件 | 本片是否要改 | 现状态 | 其他窗口占用 |
|---|---|---|---|
| `ui/screen/shelf/BookDetailStatsSection.kt` | 是（#3） | ` M`（既有未提交工作流） | 无（B 的 F3-A 文件集不含它） |
| `ui/screen/homearchive/HomeCompletedScreen.kt` | 是（#10） | ` M` | 无 |
| `ui/screen/readinghistory/MyReadingScreen.kt` | 是（#12） | ` M` | 无 |
| `ui/screen/home/components/HomeReadingArchiveSection.kt` | 是（#15） | ` M` | 无 |
| `ui/util/DurationDisplay.kt` | **否** | `??`（F3-A 在途） | **窗口 B / F3-A** |
| `data/settings/DurationPrefs.kt` | **否** | `??` | B 仅改注释，窗口 D 的 F4 已收尾 |
| `ui/components/SharedComponents.kt`、`ui/components/MicroIsland.kt` | **否** | ` M` | 公共件，默认单一维护者 |
| `android/app/src/androidTest/.../ui/screen/*`（新增手势测试文件） | 是（新文件） | — | 不与 reader/`MyReadingScreen` 无关的现有 androidTest 重叠；**不得**碰窗口 C 的阅读器点击热区文件 |
| `android/app/src/test/.../ui/screen/home/HomeReadingArchiveSectionTest.kt` | **否** | ` M` | 它是 formatter 常量断言护栏，见 §7 末注 |
| `android/app/build.gradle.kts`、协调看板、handoff、Obsidian | **否** | — | 任务明令 |

窗口 C 的 legacy EPUB / 阅读器点击热区文件：本片**完全不涉及**（四处节点都在外壳页面，非阅读器路径）。

## 七、可自动化的 Compose androidTest（androidTest，不是 JVM）

新文件建议：`android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/duration/DurationPillGestureTest.kt`，沿用仓库既有范式（`createComposeRule()` + `CompositionLocalProvider` + `AppTheme` + 回调计数，参考 `ui/screen/shelf/ShelfScreenComposeTest.kt:1-45`）。

```kotlin
@get:Rule val composeRule = createComposeRule()

private var toggleCount = 0
private fun setContent(card: @Composable () -> Unit) = composeRule.setContent {
    AppTheme {
        CompositionLocalProvider(
            LocalDurationUnit provides DurationUnit.HOURS,
            LocalDurationUnitToggle provides { toggleCount++ },
            content = card,
        )
    }
}
```

| # | 用例 | 宿主 / 操作节点 | 操作 | 断言 |
|---|---|---|---|---|
| 1 | #3 点击 pill 仍展开该条记录 | 渲染 `SessionMicroIsland`（需先 `internal`）；`onNodeWithText("30 小时 0 分钟")`（pill 现文本） | `performClick()` | 展开后出现的 `InfoRow` 标签（`"开始时间"`，`BookDetailStatsSection.kt:314`）由不存在变存在；`toggleCount == 0` |
| 2 | #3 长按 pill 切单位、不展开 | 同上 | `performTouchInput { longClick() }` | `toggleCount == 1`；`onNodeWithText("开始时间")` **不存在** |
| 3 | #10 点击 pill 打开该书 | `CompletedBookCard(onOpen = {openCount++}, onManage = {manageCount++})`；pill 文本节点 | `performClick()` | `openCount == 1`；`toggleCount == 0`；`manageCount == 0` |
| 4 | #10 长按 pill 只切单位 | 同上 | `performTouchInput { longClick() }` | `toggleCount == 1`；`openCount == 0` |
| 5 | #12 整行长按仍是管理菜单 | `TimelineBookRow`（需 `internal`）或走 `testTag("reading-item-<id>")` | 在**行根**`longClick()` | `manageCount == 1`；`toggleCount == 0` |
| 6 | #12 时长文本不再是手势节点 | 同上 | 对时长文本 `performClick()` / `longClick()` | 行为与行根一致：`openCount == 1` / `manageCount == 1`；`toggleCount == 0` |
| 7 | #15 点击导航、长按切单位且互斥 | `HomeReadingArchiveSection(onClick = {count++})`；`onNodeWithTag("reading-archive-card")` | `performClick()` → `onNodeWithText("累计阅读")` | `clickCount == 1` 且 `toggleCount == 0`；再 `performTouchInput { longClick() }` → `toggleCount == 1` 且 `clickCount` **仍为 1** |
| 8 | 语义标签 | 同上 + #12 行根 | 读 semantics | `assertSemanticsContains(node, SemanticsActions.OnLongClick, onLongClickLabel = "切换时长单位")`；#12 为 `"打开管理操作"`；**断言时长节点自身不再暴露 `SemanticsActions.OnClick` 的空动作**（钉住规则 A） |
| 9 | 唯一入口约束 | 每屏分别渲染 | 统计带 `onLongClick` 动作的节点数 | 每个语义组件 `== 1`（#12 所在列表**每条 0 个**、页首 1 个） |

实现注意（都是踩过的点，写进测试注释）：
- 计数回调要在 `remember` 之外持有（用测试类字段），否则重组会重置。
- **文字行盒 ≠ 容器 bounds**：断言命中区时不要用 `getBoundsInRoot()` 的文字节点去代表 pill；要量 pill 就给 pill 容器加 `testTag`（常量在节点文件里 `internal` 暴露给测试，别在测试里硬编码字符串）。
- `getBoundsInRoot()` 在本项目 Compose 版本返回 Dp 坐标（`ShelfScreenComposeTest.kt:41-43` 已注明）。
- 现有 `android/app/src/test/.../HomeReadingArchiveSectionTest.kt` 是 **JVM 上的 `formatDuration` 常量断言**（不渲染 composable、不涉及手势），**不能**当作本片的验收证据；手势归属必须在 androidTest。
- 本项目 Compose UI androidTest 在 MIUI 测试机上受 instrumentation 安装限制（历史结论：push+pm install 可自动，`am instrument` 仅非 UI 用例可用），因此用例落地需要一个可跑 Compose 测试的设备时段 —— 见 §8、§9 readiness。

## 八、必须真机确认的项目（只能连真实手机，禁用 MuMu）

| 项 | 为什么只能真机 | 判定方法 |
|---|---|---|
| 落在时长文字上的点击**实际是否**被子级吞掉（现状 3 处） | Compose 手势仲裁 + `clip`/`animateContentSize`/Lazy 复用叠加，静态读码不能定论 | 现状 APK 上点 pill 中心：是否展开 / 是否打开书 |
| 长按命中盒是否覆盖**整个可见 pill**、是否 ≥48dp | 11sp 文字 + 6/2dp padding 的真实触摸盒需量 | `uiautomator` 边界 + 大字 1.3×、320dp 宽度两档 |
| 长按抬手后**是否同时导航**（#15 尤其） | 单一 detector 的分流行为需实测（长按→抬手是否补发 click） | #15 长按后观察是否发生页面跳转 |
| 快速连点/连续长按是否丢切换 | `DurationPrefs.toggle()` 读-改-写竞态（F4 已测状态层，UI 层未测） | 20 次连点后比对磁盘值 |
| TalkBack 播报：#15 的两条动作、#12 移除后是否只剩一条、空 `onClick` 现状是否被读成无意义"双击即可激活" | 语义树合并与读屏引擎行为无法在 JVM 断言 | 开 TalkBack 逐节点探听，记录原文 |
| 切换后的 Toast 是否被 TalkBack 播报 | `MainActivity.kt:85-93` 用 Toast 回执 | 真机开读屏验证 |
| Lazy 列表项切换单位后是否**所有行**立即更新 | 复用 + `remember` 缓存可能只更新可见项 | #10/#12 滚动前后比对 |

## 九、分级（P0/P1/P2/P3 / 静态推断 / 产品裁决 / readiness 分开）

- **现役 P0/P1/P2：本片不新定。** 任务明令"不得因为源码存在嵌套手势就直接定现役 P2"，且没有设备证据。
- **静态推断（待 §8 真机确认后才可能升级）**
  - I-1 三处节点的 pill 文字上"普通点击无动作"（源码：`DurationDisplay.kt:59` `onClick = {}`）。
  - I-2 pill 内边距一圈行为与中心不同（规则 C，源码：`DurationDisplay.kt:80-82` + 调用方 padding）。
  - I-3 #12 同一行存在两种长按语义（`:488` vs `:565`）。
  - I-4 空 `onClick` 使读屏用户得到"可激活但无动作"的节点。
- **产品裁决（需 Codex/产品点头，非代码问题）**
  - D-1 **移除 #12 pill 的切单位入口**（本片推荐，代价是该行不再就地可切，改为整屏一次入口）。
  - D-2 #15 是否接受"整张导航卡承担长按"（本片推荐接受，配套 `onLongClickLabel` + 既有 Toast）。
  - D-3 长按是否加触觉反馈（与 F3-A 同一议题，本片不重复决策）。
- **P3（可读性/文档，不阻塞）**
  - P3-1 `DurationDisplay.kt:48-51` 的注释"消费它自己的点击不会影响卡片/列表的其它交互"与 I-1 的行为面存在张力；该文件属窗口 B 的 F3-A（其设计文档 §6 已把"承诺文案"列入 F3-A 范围），**移交，不在本片处理**。
  - P3-2 `BookDetailStatsSection.kt` 的父级点击是"该条记录"级，与页面口语"展开会话记录"易混，建议实施时在注释里写清两层。
- **readiness**
  - R-1 `SessionMicroIsland`（`BookDetailStatsSection.kt:221`）与 `TimelineBookRow`（`MyReadingScreen.kt:435`）是 `private` → 直测需先 `internal` 化（单文件、非公共 API）。
  - R-2 需要可运行 Compose androidTest 的设备时段（MIUI 限制，见 §7 末注）。
  - R-3 依赖 F3-A 先落：`onLongClickLabel` 的统一措辞与 toggle 语义若各节点自写，label 会漂。

## 十、推荐实施切片（与窗口 B 的 F3-A 文件集**零重叠**）

F3-A 的文件集（窗口 B 设计文档 §6）：`ui/util/DurationDisplay.kt`、`DurationPrefs.kt`（仅注释）、`stats/components/{SummaryGroup,CreationSection,HeatmapSection}.kt`、`home/components/HomeMetricsSection.kt`、`profile/ProfileHomeScreen.kt`、新增 `DurationDisplayTest.kt`。本片**一个都不碰**。

| 切片 | 文件 | 内容 | 可并行性 |
|---|---|---|---|
| F3-B1 | `BookDetailStatsSection.kt` | #3：pill 容器挂 `combinedClickable(onClick=展开, onLongClick=toggle, label)`；`DurationText`→`Text`；`private`→`internal` | 与 F3-A 并行安全 |
| F3-B2 | `HomeCompletedScreen.kt` | #10：同上（`onClick = onOpen`） | 并行安全 |
| F3-B3 | `MyReadingScreen.kt` | #12：移除 pill 的 toggle（改纯 `Text`）；行根补 `onLongClickLabel = "打开管理操作"` | 并行安全；**建议最后合**，因为它减入口，需 D-1 裁决 |
| F3-B4 | `HomeReadingArchiveSection.kt` | #15：父卡 `.clickable` → `.combinedClickable` + `onLongClickLabel` | 并行安全 |
| F3-B5 | 新增 `androidTest/.../duration/DurationPillGestureTest.kt` | §7 的 9 条用例（依赖 B1/B2/B3 的 `internal` 化） | 需 B1–B4 之后 |
| F3-C（既有计划） | 无代码 | §8 真机 + TalkBack + 大字验收 | 依赖 F3-A + F3-B1…B4 |

建议顺序：**B1 → B2 → B4 →（D-1 裁决）→ B3 → B5 → §8 真机**。B1/B2/B4 是"只加不改语义"的收敛；B3 减入口、B5 与真机是验证面。四片都不需要公共组件、不需要新依赖、不需要碰窗口 B/C 的文件。

---

## 十一、开始与结束的 `git status --short`

- 本轮的快照捕获于**任何写入之前**（本轮第一次也是唯一一次写入是本文件），因此"开始"与"结束前"实测同为：`git status --short | wc -l` = **379**（多窗口共享脏工作区的既有状态，未做任何清理），HEAD = `5aed5939600e491843e3c44453921bd6273a233c`，分支 `codex/recover-worktree-20260920`，暂存区为空。
- 其中与本片相关的既有行（开始即存在，非本轮产生）：
  ` M android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookDetailStatsSection.kt`、
  ` M …/ui/screen/homearchive/HomeCompletedScreen.kt`、` M …/ui/screen/readinghistory/MyReadingScreen.kt`、
  ` M …/ui/screen/home/components/HomeReadingArchiveSection.kt`、` M …/ui/components/{SharedComponents,MicroIsland}.kt`、
  ` M android/app/src/test/…/ui/screen/home/HomeReadingArchiveSectionTest.kt`、` M` 若干 androidTest、
  `?? android/app/src/main/java/com/creationreadingassistant/ui/util/DurationDisplay.kt`。
- 结束后的差量（写入本文件之后重新采集，见文末"结束采集"）：**只新增** `?? docs/coordination/designs/agent-design-s15-f3-b-qoder.md`（`docs/coordination/` 目录整体在 `git status --short` 中折叠为一行 `??`）。

### 结束采集（写入本文件之后实测）

```
$ git status --short | wc -l
379                         # 与开始同数：本轮没增删任何被跟踪文件的改动
$ git status --short | grep -c "^ M"
234                         # 全部是他窗口/既有的未提交改动
$ git diff --cached --name-only | wc -l
0                           # 暂存区为空
$ git rev-parse HEAD
5aed5939600e491843e3c44453921bd6273a233c   # 基点未动，无 commit
$ git status --short -- docs/coordination/designs/agent-design-s15-f3-b-qoder.md
?? docs/coordination/designs/agent-design-s15-f3-b-qoder.md
```

计数口径要说明白：开始与结束的全仓条目数都是 **379**，但 `docs/coordination/` 整目录是 untracked，`git status --short` 默认把它折叠成一行 `?? docs/coordination/`（开始采集即如此）—— 所以"新增一个报告文件"不会让计数变化。因此差量按**显式路径**核对（最后一条命令）：本轮唯一新增的是本文件。

## 十二、边界声明

本轮**只新增本报告一个文件**：未修改任何 Kotlin 源码、测试、Gradle、协调看板、handoff、Obsidian；未运行 Gradle / ADB / APK / 真机 / 模拟器；未执行 reset / checkout / clean / stash / stage / commit / push；未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`；未修改或评价窗口 B 的 S15-F3-A 在途文件（`DurationDisplay.kt` 等仅只读引用，P3-1 按移交处理），未触碰窗口 C 的 legacy EPUB 与阅读器点击热区文件。最终手势与 TalkBack 验收只能使用连接的真实手机，不使用 MuMu/模拟器。

完成本报告后停止，等待 Codex 复核，不领取下一任务。
