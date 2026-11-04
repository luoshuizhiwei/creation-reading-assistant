# D-2-B-CODE-REVIEW 独立复核报告 — Qoder

> 复核 Agent：Qoder　|　声明模型：`qwen3.8Flash`
> 任务编号：D-2-B-CODE-REVIEW（只审查 + 定向验证，不改任何代码）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`　固定基线：`origin/main = 5aed5939…`
> 开始/结束 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 被审报告：`docs/coordination/agent-results/agent-result-d02b-zcode.md`（**未修改**）
> 本报告路径：`docs/coordination/reviews/undated/agent-review-d02b-qoder.md`
> **最终结论：PASS**（全部发现为 P3；SP-1 是唯一可能把 PASS 改判为 PARTIAL 的项，判据见 §2 末）

---

## 1. 最终结论

**PASS。**

我**不采信** Zcode 报告中的任何数字，全部独立复算。结论：

1. **五页作用域事实全部正确** —— 我用脚本从各页源码提取 `settings.copy(...)` 字段，
   与 `ReaderOverrideKey.entries` 的 22 个 id 求交，得到的可覆盖/全局划分
   与文案中的例外清单**逐项吻合**（PAGING 3/3、DISPLAY 4/4、EYE_CARE 0、TYPOGRAPHY 6/6、ADVANCED 3/3）；
2. **数据语义零变化** —— 15 个禁改文件哈希逐一与我**上一轮（D-2-A）自己记录的哈希**一致，
   这是跨轮独立佐证，不依赖 Zcode 的清单；
3. **Compose 布局安全** —— `weight(1f)` + 子页 `LazyColumn(fillMaxSize)` 的高度/宽度推导成立（§7）；
4. **三项验证全绿** —— 定向 JVM 2 类 / 36 项 / 0 失败退出码 0；androidTest 编译退出码 0；
   主 Kotlin 编译退出码 0，唯一警告在未触碰文件；
5. **文件边界真实** —— 产品改动确实只在 `ReaderSettingsSheet.kt`，测试改动 110/0 纯新增。

**没有 P0/P1/P2。** 10 项 P3 全部登记（§3、§4），其中：
1 项文案（SP-1）、1 项守卫缺失（ST-1）建议在下一次触碰该文件时一并处理，
其余为测试精简与注释归因。

### 为什么是 PASS 而不是 PARTIAL

任务书 PASS 条含「文案能与真实 UI 控件对应」。显示页四个例外里
「页眉页脚开关」不是任何可见控件的名字 —— 严格按字面判，这一条不满足，应为 PARTIAL。
我判 PASS 的理由是三条同时成立：

- **例外集合本身完全正确**（4/4，无多无漏），文案不会引导用户改错东西；
- 该词仍可定位（分组标题「页眉页脚」+ 该组内唯一的开关行），不是凭空术语；
- 它是纯字符串，改动零风险、无需再跑布局或数据验证。

**若 Codex 采用「文案必须逐字等于可见控件名」的严格读法，本报告结论应改判 PARTIAL，
且唯一必修项就是 SP-1，修复即 §8 给出的那一条文案。** 我把这个依赖显式暴露出来，不做含糊处理。

---

## 2. 实际审查范围

| 类别 | 我实际读过/跑过的东西 |
|---|---|
| 产品 | `ReaderSettingsSheet.kt`（1-353 行全读，含新函数、`ScopeNoticeRow`、`AnimatedContent` 重构、根页说明） |
| 交叉验证 | `ReaderPagingSettings.kt` / `ReaderDisplaySettings.kt` / `ReaderEyeCareSettings.kt` / `ReaderTypographySettings.kt` / `ReaderAdvancedSettings.kt` / `ReaderOverride.kt` / `ReaderSettingsRouter.kt` / `ReaderSettingsCommon.kt` / `ReaderSheetScaffold.kt` / `SettingsStore.kt` |
| 测试 | `ReaderSettingsScopeNoticeTest.kt`（279 行全读，20 用例逐条）；`ReaderSettingsSheetTest.kt`（新增 110 行全读，6 用例逐条）；`ReaderSettingsRouterTest.kt`（既有用例清单，判重复） |
| 报告 | `agent-result-d02b-zcode.md` 全文 + 其引用的设计复核 |
| 执行 | 3 条 Gradle 命令（§10）、XML 逐类解析（§11）、15 文件哈希（§5）、页面字段脚本提取（§5） |
| **未做** | 未改任何文件；未跑 instrumentation；未构建/安装 APK；未操作手机；未跑 290 项附加回归（定向已足够判断） |

### 既有改动的归因隔离

`git diff` 该文件 = **+148 / −25**，其中下列内容**不是** D-2-B（任务书已预告，我用 diff 行内容逐条确认）：

```
+ maxHeightFraction = 0.62f
+ contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)   ← 原 12.dp
+ verticalArrangement = Arrangement.spacedBy(8.dp)                        ← 原 14.dp
+ isOverridden = overrides.containsKey(ReaderOverrideKey.FONT_SIZE)
+ subtitle = if (overrides.containsKey(...LINE_HEIGHT)) "本书" else null
+ options = listOf(... "fade" to "淡入", "slide" to "平移" ...)            ← 原 fade=平移/slide=滑动
+ text = "💡 复杂动画每帧需GPU合成多张位图…"
```

**我没有把这些要求回退，也没有计入 D-2-B 的改动面。**

⚠️ 报告证据瑕疵（ST-7）：Zcode §2/§16 写「+173 / −25」，
`173 = 148 + 25` 是 `git diff --stat` 的**合计变更行数**，不是插入数。真实为 **+148 / −25**。

---

## 3. Standards findings（0 / 0 / 0 / 7）

| ID | 级别 | 文件:行 | 事实 | 后果 | 最小修复 | D-2-B 引入 |
|---|---|---|---|---|---|---|
| **ST-1** | **P3** | `ReaderSettingsSheet.kt:96`；`ReaderSettingsScopeNoticeTest.kt:77-113` | 生产 KDoc 写「例外清单必须与该页实际可覆盖字段一致（**守卫测试双向对齐**）」，但测试只做 `copy.contains("阅读模式")` 之类的**子串包含**检查，且用例名里的 "exactly" 没有任何 exact-set 断言支撑。我实测：给 PAGING 文案**多加**一个不属于它的例外（如「沉浸模式」）会被 `paging copy does not name exceptions…` 抓住；但**给翻页页新增一个本书可覆盖字段而不改文案，没有任何测试会失败** | 设计复核方案 4′ 的核心安全属性（第 8 条「测试可以证明」）未落地；且注释**声称存在**一个不存在的保护，会误导后续维护者以为改字段有护栏 | 新增一条 JVM 用例：从页面源码提取 `settings\.copy\(([^)]*)\)` 的字段名 → 与 `ReaderSettingsScopeNoticeTest` 反射取到的 `ReaderOverrideKey.entries` 求交 → 取各 `label` → 断言「每个 label 出现在该页文案中」且「其他页的可覆盖 label 不出现」。我本轮已用等价脚本跑出真值（§5），可直接固化为测试 | **是** |
| **ST-2** | P3 | `ReaderSettingsScopeNoticeTest.kt:247-278` | `the notice function has no catch all else branch` 做源码花括号配平扫描。①与行为用例 `every page is explicitly answered for both scopes`（:231）**语义重复**：若有人加 `else -> null`，新页面返回 null，行为用例即失败；②配平**不剥离字符串与注释**，而本仓库确有含花括号的模板字面量（`ReaderDisplaySettings.kt:100` 的 `"作用于模板里的 {BatteryPercent}"`），这类字符串一旦进入函数体，`depth` 即错位，产生假通过或假失败 | 维护成本 + 潜在假信号；不阻断 | **删除该用例**（行为用例已覆盖同一风险）。若坚持保留，先剥离 `"` 包裹段与 `//` 注释再配平 | **是** |
| **ST-3** | P3 | 同上 :216-226 | `no notice ends with a full stop` 断言文案不以「。」结尾，注释给的理由是「避免测试只靠标点区分」—— 这不是业务约束 | 未来任何合法整句文案被无端禁止 | 删除 | **是** |
| **ST-4** | P3 | 同上 :125-131 | `eye care copy carries no exception list` 用**单字**「除」「外」做反向断言。「外」是高频汉字（额外/对外/中外），未来合理文案会被误伤 | 脆弱断言，无对应真实风险（护眼页是否全全局由 ST-1 的集合断言管） | 改为断言不含「除」+「外」的**成对结构**，或直接删除并靠 ST-1 覆盖 | **是** |
| **ST-5** | P3 | 同上 :189-202 | 禁词表含「覆盖项」。但根页**现有可见 UI** 就是「覆盖 N 项」「清除本书覆盖」「全部跟随全局」（`ReaderSettingsCommon.kt`），即「覆盖」是产品既定用户词。禁掉它会让未来必须引用该词的文案（例如 GLOBAL 说明想写"不会清除其他书的覆盖"）被测试阻挡；「默认值来源」是不会自然出现的死断言 | 过度约束未来合法文案 | 禁词缩窄为 `override` / `DataStore` / `router`（ASCII 技术词），移除「覆盖项」「默认值来源」 | **是** |
| **ST-6** | P3 | `ReaderSettingsSheetTest.kt` 新增 6 用例；`ReaderSettingsSheet.kt:178-222` | `AnimatedContent` 的进出场淡入 160ms / 淡出 120ms 期间，**新旧两个 target 的内容同时存在**。用例用 `onNodeWithTag(SCOPE_NOTICE_TAG)`（单节点匹配）与 `returningToRootRemovesTheScopeNotice` 的 `assertCountEquals(0)`，都落在过渡窗口上 → 已知 flake 形态（"Expected exactly 1 node but found 2" / 计数为 1） | 一旦执行可能出现非产品缺陷的红灯；本轮**未执行**，无法证伪，故只记 P3 | 交互后 `composeRule.mainClock.advanceTimeBy(200)` 再断言，或改用 `onAllNodesWithTag(TAG).onFirst()` | **是** |
| **ST-7** | P3 | 报告 §2/§16 | 「+173 / −25」实为 `--stat` 合计；真实 numstat **+148 / −25** | 证据数字口径不一致 | 报告改口径（本轮不改代码，仅登记） | **是** |

**Standards 无 P0/P1/P2。** 代码本身（纯函数 + 私有 composable + 穷尽 `when` 无 `else`）
是我在这条链路上见过的较干净的一次增量实现：`else ->` 全文 0 命中、函数无副作用、
未新增公共组件、未越界。ST-1～ST-5 都是**测试与注释**层面的取舍问题。

---

## 4. Spec findings（0 / 0 / 0 / 3）

| ID | 级别 | 文件:行 | 事实 | 后果 | 最小修复 | D-2-B 引入 |
|---|---|---|---|---|---|---|
| **SP-1** | **P3**（可能升 P2，见 §1） | `ReaderSettingsSheet.kt:101` | 显示页例外写作「沉浸模式、**进度条**、**菜单隐藏**和**页眉页脚开关**」。对照该页可见文案（`ReaderDisplaySettings.kt`）：行标题「沉浸模式」:38 ✓、行标题「显示进度条」:47（子串）、**分组标题**「菜单隐藏」:64 ✓、行标题「**显示安静阅读信息**」:87（**无一处叫"页眉页脚开关"**）。而「页眉页脚」是**分组名**，其下正好挂着 6 个 `customTip*` 选择器（:103-131，**写全局**） | 读者把「页眉页脚开关」读成"页眉页脚那一组"时，会误以为 6 个模板选择器也是本书设置 —— **方向正好相反**的误解，而 D-2-B 存在的目的就是消除这类误解 | 用真实可见控件名重写，见 §8 最终文案 | **是** |
| **SP-2** | P3 | `ReaderSettingsSheet.kt:115` | GLOBAL 文案「本页修改将保存为全局默认；其他书已有的单书设置不会被清除」只说了**其他书**不受影响，未说**当前书**：GLOBAL 分支 `overridesToClear = touched`（`ReaderSettingsRouter.kt:88-95`）会清掉当前书**这一项**的本书覆盖，使其转为跟随全局 | 文案不完整但**无错误承诺**（比 Qoder 设计稿那句更安全）。用户可能不理解"为什么我刚改的这项不再单独记在这本书上" | 若可接受变长：「本页修改将保存为全局默认；当前书籍的这一项将改为跟随全局，其他书已有的单书设置不受影响」。若嫌长，保持现状亦可 | 部分（措辞来自 Codex 修正版） |
| **SP-3** | P3 | `ReaderSettingsSheet.kt:70`；`ReaderSettingsScopeNoticeTest.kt:16`；`ReaderSettingsScopeNoticeTest.kt:68` | 三处注释/断言理由都把动机写成「真机验收 P3-1」。我在设计复核 §9 与真机报告里，P3-1 指的是**高级兼容子页**缺提示 —— 那一页的字段在 D-2-A 后是 **per-book**，缺提示造成的误导方向与这 26 个全局字段**相反**（本轮代码自己也把这页写成「只对当前书籍生效」，正说明两者不同） | 归因错误进入生产注释，后续读者会以为「26 项全局字段」曾被真机记为缺陷（实际没有） | 注释改为「三个子页此前无作用域提示（D-2-B 设计复核 P3-A）」 | **是** |

**Spec 无 P0/P1/P2。** 关键点是：**没有一条文案说错事实**（SP-1 是"说得不够准"，SP-2 是"说得不够全"）。

---

## 5. 五页作用域矩阵（我独立提取，非采信报告）

方法：`settings\.copy\(([^)]*)\)` 提取实参字段名 → 与 `ReaderOverride.kt` 的 22 个 id 求交 → 取枚举 `label`。

| 页面 | 写入字段数 | 本书可覆盖（枚举 label） | 全局字段 | 文案例外清单 | 是否一致 |
|---|---:|---|---|---|---|
| **PAGING** | 10 | 3：阅读模式 / 翻页效果 / 屏幕方向 | 7：pageTurnSpeed, tapZoneMode, tapActionPreset, volumeKeyPaging, volumeKeyInvert, volumeKeyPagingDuringTts, autoPageSpeed | 「阅读模式、翻页效果和屏幕方向」 | ✅ **逐字一致** |
| **DISPLAY** | 14 | 4：沉浸模式 / 进度条 / 菜单自动隐藏 / 页眉页脚 | 10：brightness, lastFixedBrightness, keepAwake, batteryTipStyle, customTipHeader×3, customTipFooter×3 | 「沉浸模式、进度条、菜单隐藏和页眉页脚开关」 | ✅ **集合一致**（措辞见 SP-1）；`lastFixedBrightness` 是 `brightness` 的联动记忆位，不是独立可编辑项，未列入正确 |
| **EYE_CARE** | 10 | **0** | 10：eyeCare×7 + readingRhythm×2 + … | 无例外（整页全局） | ✅ |
| **TYPOGRAPHY** | 6 | **6**：字号 / 行距 / 段间距 / 页边距 / 粗体 / 自定义字体 | 0 | 「只对当前书籍生效」 | ✅ |
| **ADVANCED** | 3 | **3**：分页引擎（TXT/Markdown）/ 分页引擎（EPUB）/ 中文排版 | 0 | 「只对当前书籍生效」 | ✅ **D-2-A 未回退** |
| **ROOT** | 5 | 4：字号 / 粗体 / 行距 / 翻页效果 | **1：pageTurnSpeed** | 根页不显示页级提示；仅对 pageTurnSpeed 标注 | ✅ 唯一全局项被精确覆盖 |

### 12 条需求逐项

| # | 要求 | 判定 | 证据 |
|---:|---|---|---|
| 1 | ROOT 在 BOOK/GLOBAL 下均不显示 | ✅ | `:90`、`:109` 各一个 `ROOT -> null`（全文命中 2 处）；Compose 用例 `assertCountEquals(0)` |
| 2 | 根页 pageTurnSpeed 明确标注所有书籍共用 | ✅ | `:331-338`，文案「全局设置，所有书籍共用」+ testTag |
| 3 | TYPOGRAPHY 全部可覆盖 | ✅ | 6/6（脚本） |
| 4 | ADVANCED 三项均可覆盖 | ✅ | `pagerEngineMode`/`epubPagerEngineMode`/`chineseTypography` 均在枚举（label 已打印） |
| 5 | PAGING 三例外与源码完全一致 | ✅ | 逐项吻合 |
| 6 | DISPLAY 四例外与源码完全一致 | ✅ 集合一致 / ⚠️ 措辞见 SP-1 | 4/4 |
| 7 | EYE_CARE 无任何本书可覆盖字段 | ✅ | 0 |
| 8 | GLOBAL 文案准确反映三件事 | ⚠️ 前两件准确、第三件（当前书被清）未提 | `ReaderSettingsRouter.kt:88-95`；SP-2 |
| 9 | 是否错误暗示全局修改一定覆盖所有书 | ✅ 没有。旧的不准确承诺「影响所有书」在全仓 **0 命中**（我复算） | `grep` = 0 |
| 10 | D-2-A 两引擎字段仍 per-book | ✅ | `ReaderOverride.kt` 哈希 `69d55677fb48961e` = 我 D-2-A 复核记录的 after 值 |
| 11 | 26 项仍全局 | ✅ | 枚举仍 22 项；各页 GLOBAL 字段集合与上轮一致 |
| 12 | 路由/存储/PerBookSettingsStore/ReaderOverrideKey 未变 | ✅ | §11 哈希表，15/15 与我**上一轮自己记录的值**相同 |

### 禁改文件独立哈希（不采信 Zcode 的 before/after 清单）

| 文件 | 当前(前16) | 我上轮记录值 | 判定 |
|---|---|---|---|
| `ReaderSettingsRouter.kt` | `d6e6de3d8e0cc12e` | D-2-A 复核同值 | 未改 |
| `ReaderSettingsEditApplier.kt` | `f85ad026e022b04c` | 同值 | 未改 |
| `ReaderOverride.kt` | `69d55677fb48961e` | D-2-A **after** 值 | 未改（D-2-A 不回退） |
| `ReadingPresets.kt` | `1c655b9bff4f2aa6` | D-2-A after | 未改 |
| `PerBookSettingsStore.kt` | `bef8c822c52c6616` | D-2-A after | 未改 |
| `SettingsStore.kt` | `b9eb035ee41938a9` | D-2-A 复核同值 | 未改 |
| `ReaderSettingsCommon.kt` | `c2580fbb4d9d656c` | D-2-A after | 未改 |
| `ReaderPagerEngineState.kt` | `ea29e7886424cb4d` | D-2-A 复核同值 | 未改 |
| 另 7 个（三设置页 / 排版页 / 高级页 / ThemeSheet / SheetScaffold） | 与 Zcode §15.1 表**逐字节相同** | — | 未改 |

⇒ **不存在越界修改。** 唯一产品改动文件 = 授权文件本身。

---

## 6. 显示页文案专项结论

**结论：修改（保持现状不构成阻断，但我建议改）。严重级别：P3。**

逐问回答：

1. **普通用户能否把「页眉页脚开关」对应到界面上的「显示安静阅读信息」？**
   勉强能 —— 靠"页眉页脚分组里唯一的开关"这一推理链，而不是靠字面匹配。
   一条需要推理才能定位的作用域说明，达不到"修改前明确知道影响范围"的目标。
2. **会不会被误解为六个槽位模板也是本书设置？**
   **会，而且这是主要风险。** 六个 `HeaderFooterPicker` 就在标题为「页眉页脚」的分组内、
   紧接在该开关下方（`ReaderDisplaySettings.kt:103-131`）。
   「除…和页眉页脚开关外」把"页眉页脚"整体列为例外语境，读者很容易把整组读成例外。
   而这六项实际**写全局** —— 于是得到与 D-2-B 目的**方向相反**的错误认知。
3. **`customTip*` 写全局但显示值可能回落到本书 `headerLeft`（`SettingsStore.kt:279-294`），是否加重歧义？**
   会加重。用户看到的槽位内容本身可能来自本书，再看到"页眉页脚"被列为本书例外，
   两条线索同向叠加 → 误判概率上升。（该回落在当前 UI 下不可触发，因为 `headerLeft` 无编辑入口；
   属既有模型遗留，不是本轮引入，登记为设计风险而非缺陷。）
4. **是否应改用真实可见控件名？** 是。
5. **最终完整文案（可直接替换 `ReaderSettingsSheet.kt:101` 与测试期望值）**：

```
除沉浸模式、显示进度条、自动隐藏和显示安静阅读信息外，本页其他设置均为全局设置，对所有书籍生效
```

四个例外全部等于该页**行标题原文**（`ReaderDisplaySettings.kt:38 / :47 / :68 / :87`），
不再借用分组名，也不再出现"开关"这种界面不存在的说法。长度 45 字，
在 320dp / 1.3 字号下约 4 行，仍位于 `weight(1f)` 之上、不挤压正文（§7.6）。

同步需改：`ReaderSettingsScopeNoticeTest.kt:101` 的例外数组、
`ReaderSettingsSheetTest.kt` 中两处该整句字符串（`bookScopeNotice…` 与 `scopeNoticeRemainsVisibleAt320Dp…`）。

---

## 7. Compose 布局审查

实际结构（`ReaderSettingsSheet.kt:173-223`）：

```
ReaderSheetScaffold(title = page.title, onBack = …, maxHeightFraction = 0.62f)   ← 未改
└── AnimatedContent(targetState = page, transitionSpec = {160/120ms fade}, label = "reader-settings-page",
                    modifier = Modifier.fillMaxSize())                            ← 逐字保留
    └── Column(Modifier.fillMaxSize())                                            ← 新增
        ├── readerSettingsScopeNotice(target, scope)?.let { ScopeNoticeRow(it) }   ← 自适应高度
        └── Column(Modifier.weight(1f))                                            ← 剩余高度
            └── when (target) { ROOT -> SettingsRoot(…) / 五个子页 }                ← 内容未变
```

| # | 检查点 | 判定 | 依据 |
|---|---|---|---|
| 1 | `AnimatedContent(targetState = page)` 保留 | ✅ | `:178-189`，`transitionSpec` / `label` / `reducedMotion` 分支逐字未动 |
| 2 | 每个 target 内 `Column(fillMaxSize)` 是否安全 | ✅ | `AnimatedContent` 自身带 `fillMaxSize()` ⇒ 内容收到**紧约束**（min=max=父尺寸），子 `Column.fillMaxSize()` 只是填满，不会无界测量 |
| 3 | 内层 `Column(weight(1f))` 的宽高 | ✅ 高=剩余（`fill=true` ⇒ min=max=剩余高度，**有限**）；宽=继承父 maxWidth | `weight` 只作用于**主轴**（Column 即高度），交叉轴不受影响 |
| 4 | 五个子页的 `LazyColumn(fillMaxSize)` 仍能滚动 | ✅ | 全部子页根容器是 `SettingsList` = `LazyColumn(Modifier.fillMaxSize())`（`ReaderSettingsCommon.kt:97-104`），拿到有限高度 ⇒ 可滚动；`SettingsRoot` 同理（`:244-248`） |
| 5 | ROOT 无 notice 时是否多占空白 | ✅ 不占 | `notice == null` ⇒ `let` 不发射任何节点，外层 Column 只有一个 `weight(1f)` 子项，填满 |
| 6 | 长文案 320dp / 1.3 是否允许换行 | ✅ | `ScopeNoticeRow` 的 `Text` **未设** `maxLines` / `overflow` ⇒ 自然换行；行高由内容决定，Column 主轴自适应 |
| 7 | 横屏低高度会不会挤掉全部正文 | ✅ 实际安全，理论有界 | 提示在 `AnimatedContent` 之前测量，正文拿 `剩余`。本仓库 sheet 高 = `0.62 ×` 屏高；竖屏 393×780 时正文 ≈ 450dp，横屏 407dp 高时正文 ≈ 218dp，提示占 2 行 ≈ 34dp ⇒ 不会归零。仅当提示高度 > 0.62×屏高时 `weight(1f)` 才会把正文压到 0，当前最长 45 字文案在最坏缩放下约 4 行 ≈ 68dp，远未达到 |
| 8 | 返回 ROOT 后旧 notice 是否残留 | ✅ 行为上不残留；⚠️ **过渡期内共存** | `AnimatedContent` 在淡出 120ms 内仍组合旧 target ⇒ 旧 notice 短暂存在；动画结束即 dispose。**这正是 ST-6 的 flake 来源**（用例在过渡窗口做单节点/计数断言） |
| 9 | scope 改变后文案是否更新 | ✅ | `readerSettingsScopeNotice(target, scope)` 在组合期读取 `scope` 参数，`scope` 来自 `ReaderScaffold` 的 `remember(bid)` 状态 ⇒ 变化触发重组 |
| 10 | notice 放进 `AnimatedContent` 内是否正确随页进出 | ✅ 正确 | 与页面同生命周期、同淡入淡出，避免"提示先出现/后消失"的错位；代价即 #8 的共存窗口 |
| 11 | `testTag` 是否唯一稳定 | ⚠️ 稳定但**过渡期不唯一** | `reader-settings-scope-notice` 每 target 一个；`reader-page-turn-speed-global-note` 全文唯一（与既有 `reader-page-turn-speed` / `-paging` 命名前缀一致） |
| 12 | 图标 `contentDescription = null` + 可见文字自然朗读 | ✅ 正确做法 | 图标是装饰；文字是 `Text` 节点，TalkBack 读可见文本 |
| 13 | 是否有重复 TalkBack 节点 | ✅ 无 | `Row` 未做 `mergeDescendants`，Icon 无描述，仅 1 个文本节点被朗读 |
| 14 | 根页说明与滑块的关联 | ✅ 明确 | 说明紧跟滑块之后（`:315-338`，中间无 divider），`start = 14.dp` 与滑块行缩进一致；顺序为 字号→行距→翻页效果→[💡提示]→divider→翻页速度→**说明**，不会错挂到翻页效果上。⚠️ 与既有 💡 提示同款式同位置，两条不同性质说明视觉同构（观察项，不计级） |
| 15 | `weight(1f)` 是否还需 `fillMaxWidth()` | **不需要** | Compose 测量依据：`Column` 的子项在交叉轴（宽度）上收到的是**父传入的 maxWidth**，`weight` 不改它；而每个子页根节点自身声明 `fillMaxSize()`，会把该 maxWidth 用满 ⇒ 内层 `Column` 的宽度 = 其子项最大宽度 = 全宽。加 `fillMaxWidth()` 只是冗余，不加也不会塌陷 |

**结论：Compose 布局安全。** 唯一需要修的是测试断言方式（ST-6），不是布局本身。

---

## 8. 测试逐项审查（JVM 20 + Compose 6）

### 8.1 对我被要求回答的 15 点

| # | 问题 | 我的回答 |
|---|---|---|
| 1 | 纯映射测试是否只是把生产字符串抄进期望值 | **是，但形式正确**：期望值是测试文件里的**字面量**，不是调用生产函数算出来的。文案是产品契约物，锁字面量是合理做法（改文案必须显式改测试） |
| 2 | 精确文案测试属合理契约还是循环论证 | **合理契约，不循环**。循环的定义是"期望值由被测生产公式派生"（我在 D-2-A 复核 ST-2 判过的正是那种）。这里两端来源独立 |
| 3 | "例外清单完全正确"是否真验证 exact set | ❌ **没有**。只断言 `contains` 四个词 + `contains("所有书籍生效")`。用例名里的 "exactly" 名不副实：漏掉"新增本书字段未更新句子"这一方向（ST-1） |
| 4 | `paging/display copy does not name exceptions that belong to another page` 有无真实防回归价值 | **有，且是本轮除锁字面量外最有价值的一组**。我实测：给 PAGING 文案塞进「沉浸模式」会被它抓红；它同时是 ST-1 缺失时唯一的越界防线。保留 |
| 5 | `no notice ends with a full stop` 是否无业务价值 | **是，无价值**。删（ST-3） |
| 6 | `no notice exposes implementation terms` 是否过度约束 | **部分是**。「覆盖项」与根页既有用户词冲突；「默认值来源」是死断言。缩窄（ST-5） |
| 7 | 源码扫描 `has no catch all else branch` 是否必要 | **不必要**，与 `every page is explicitly answered for both scopes` 重复（ST-2） |
| 8 | 该扫描会被什么误导 | 会被：①**字符串**（本仓库真实存在 `"作用于模板里的 {BatteryPercent}"` 这类含花括号的字面量，一旦出现在函数体内，`depth` 立即错位）；②**注释**（注释里出现 `else ->` 会假失败）；③**文件路径变化**（用相对路径 `src/main/java/...`，依赖 Gradle 测试工作目录 = app 模块根；它先 `assertTrue(source.exists())`，所以失败信息是清楚的 —— 这一点做得对）；④表达式体格式变化（`indexOf(')')` 依赖"参数表右括号先于 when 括号"这一具体写法，改成多行/带默认值即可能取错锚点）。当前实现**恰好正确**，但依赖写法细节 |
| 9 | Kotlin 穷尽 `when` 编译检查是否已足够 | **不完全**：编译器只在**没有 `else`** 时强制穷尽。所以"防止新增 else"确有其事 —— 但行为用例已经覆盖了同一后果（新页面若被 `else -> null` 吞掉，`assertNotNull` 即红）。故文本扫描是**第二重冗余** |
| 10 | 279 行 JVM 测试对一个纯映射函数是否过度 | **偏多但不算失控**。20 项里约 8 项承担真实回归，其余是标点/单字/重复守卫。按 §9 精简后约 11 项，行数可降到 ~170 |
| 11 | Compose 用例是否真正验证页面切换与提示渲染 | **是**：`openGroup()` 真实点击根页分组入口、断言提示可见与精确文案、点「返回」（`ReaderSheetScaffold.kt:53` 的 `contentDescription = "返回"` 我已核实存在）回根页再断言计数 —— 覆盖导航 + 渲染 + 清除三件事。质量高于纯字符串测试 |
| 12 | `assertIsDisplayed` 能否证明长文案没截断 | **不能**（Zcode 自记 P3-2，我确认）。`assertIsDisplayed` 只判节点与视口相交；一个 `maxLines=1, overflow=Ellipsis` 的节点同样"displayed"。真正的保护来自生产代码**没设** `maxLines`（我核实 `ScopeNoticeRow` 的 `Text` 无该参数）⇒ 结构上不可能被省略号截断，所以这条测试的边际价值有限，可保留但别指望它证明什么 |
| 13 | 测试/注释是否错误引用 D-2-A 真机 P3-1 | **是**，三处（SP-3）：`ReaderSettingsSheet.kt:70`、测试 KDoc `:16`、`the advanced compatibility copy must not claim global scope` 的注释 `:68`（"真机 P3-1 的另一面"） |
| 14 | 建议保留/删除/合并 | 见 §9 精确清单 |
| 15 | 精简清单 | 见 §9 |

### 8.2 Compose 6 个用例逐条

| 用例 | 判定 |
|---|---|
| `rootPageShowsNoScopeNoticeButMarksPageTurnSpeedAsShared` | ✅ 断言对了（ROOT 无提示 + 全局说明存在且文本精确）。这是唯一能证明"根页没被漏掉"的用例 |
| `bookScopeNoticeAppearsOnEverySubPageWithTheExpectedCopy` | ✅ 五页遍历 + 精确文案 + 回根页。⚠️ 依赖 ST-6 修复 |
| `advancedCompatibilityNoticeNeverClaimsGlobalScope` | ✅ **最有价值的 Compose 用例**：把 D-2-A 的 per-book 语义钉在 UI 层，防止将来有人把引擎页标成全局 |
| `globalScopeNoticeUsesTheCorrectedCopyOnSubPages` | ✅ 同时钉住"新文案正确"与"旧不准确承诺不再出现" |
| `returningToRootRemovesTheScopeNotice` | ✅ 意图正确。⚠️ ST-6 风险最高的一条（淡出 120ms 内旧 notice 仍在树里） |
| `scopeNoticeRemainsVisibleAt320DpWithLargeFont` | ✅ 复用既有 `nightSettingsRemainUsableAt320DpWithLargeFont` 的密度配置，方向对；但见 #12 的局限 |

**六个用例全部"已编译未执行"** —— Zcode 报告 §8.2 与 §13 U1 已明确写出，
我核对报告全文**没有**任何一处把它们写成"执行通过"。✅ 该项无夸大。

---

## 9. 建议保留 / 删除 / 合并的测试（精确到方法名）

### 保留（11 项，均能阻止一次真实回归）

```
the root page has no notice under the book scope
the root page has no notice under the global scope
typography under the book scope says only this book
advanced compatibility under the book scope says only this book
the advanced compatibility copy must not claim global scope
paging under the book scope names exactly its three book level exceptions
paging copy does not name exceptions that belong to another page
display copy does not name exceptions that belong to another page
every sub page under the global scope returns the same corrected copy
the global copy makes no promise it cannot keep
every page is explicitly answered for both scopes
```

### 合并（3 → 1）

```
no notice uses markdown emphasis
no notice describes the app brightness setting as a system setting
no notice exposes implementation terms
→ 合并为 no notice uses forbidden user-facing forms（保留 **、系统设置、override/DataStore/router）
```

理由：三者都是"遍历全部 (page, scope) 检查禁用子串"的同一形状，合并后循环一次即可，
并顺带按 ST-5 缩窄禁词。`no notice uses markdown emphasis` **必须保留其实质** ——
`**全局**` 被原样渲染是我在 D-2-A 真机验收里实测过的既有缺陷。

### 删除（4 项）

```
the notice function has no catch all else branch      ← ST-2：与 every page is explicitly answered… 重复，且扫描依赖字符串/注释/写法
no notice ends with a full stop                       ← ST-3：无业务价值
eye care copy carries no exception list               ← ST-4：单字「除」「外」过度脆弱
the global copy states both the write target and the non destructive guarantee
                                                      ← 与 every sub page under the global scope returns the same corrected copy 的精确等值断言完全重复
```

### 新增（1 项，补 ST-1）

```
each page names exactly its overridable fields as exceptions
  for page in PAGING, DISPLAY:
      expected = 从 <page> 源文件提取 settings.copy(...) 字段 ∩ ReaderOverrideKey.entries → label 列表
      assertTrue(expected.all { it in copy })
      assertTrue(其他页的 label 均不在 copy 中)
```

净结果：**20 → 13 项**，覆盖面反而更强（多了一条真正的对齐守卫）。

---

## 10. 独立命令与退出码

工作目录 `android/`。全部经 `powershell.exe -NoProfile -Command ".\gradlew.bat …"` 执行。

> ⚠️ **我自己的一个失误必须记录**：第一次我用 `cmd.exe /c "gradlew.bat …"` 跑，
> MSYS 把 `/c` 当路径转换掉，cmd 变成交互式空输入退出，日志里只有 Windows 版本横幅，
> 却返回**假 exit 0**。我据此发现没跑成，改用 PowerShell 重跑才拿到真实结果。
> 因此下文退出码全部来自 PowerShell 运行，且我用 `BUILD SUCCESSFUL` 文本 + XML mtime 双重确认。

| # | 命令 | 退出码 | 结果 | 耗时 | 任务执行 |
|---|---|---:|---|---|---|
| G1 | `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "…ui.screen.reader.sheets.ReaderSettingsScopeNoticeTest" --tests "…data.settings.ReaderSettingsRouterTest" --rerun` | **0** | `BUILD SUCCESSFUL in 5s` | 00:37:30→00:37:35 | `30 actionable tasks: 1 executed, 29 up-to-date`（执行的是测试任务本身；编译任务 UP-TO-DATE，因源码未变） |
| G2 | `.\gradlew.bat --console=plain :app:compileDebugAndroidTestKotlin --rerun` | **0** | `BUILD SUCCESSFUL in 7s` | 00:40→00:41 | 0 条 `w:` / 0 条 `e:` |
| G3 | `.\gradlew.bat --console=plain :app:compileDebugKotlin --rerun` | **0** | `BUILD SUCCESSFUL in 56s` | 00:41→00:42 | `15 actionable tasks: 1 executed`；**1 条警告** |

**第一条也是唯一一条真实警告（G3）**：

```
w: .../ui/screen/shelf/LibraryBrowserRoute.kt:145:48
   'val Icons.Outlined.Sort: ImageVector' is deprecated. Use the AutoMirrored version…
```

我独立核实：该文件 mtime **2026-09-18 23:19:21**，远早于本轮窗口（09-22 00:0x），
`git status` 显示为他人的既有 ` M` 改动 ⇒ **与 D-2-B 无因果关系**，
Zcode §9.2/§11 的归属判断**准确**。三个被审文件产生 **0 条警告** —— 与我 G2/G3 输出一致。

未跑 290 项附加回归：定向 2 类 + 主/androidTest 双编译已足以判定本轮问题，
且 `data.settings.*` 全量我在 D-2-A 复核时已跑过（19 类 / 191 项 / 0 失败）。

---

## 11. XML 统计（我本次运行）

来源：`android/app/build/test-results/testDebugUnitTest/TEST-*.xml`，逐文件解析根属性。

| 测试类 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `ui.screen.reader.sheets.ReaderSettingsScopeNoticeTest` | **20** | 0 | 0 | 0 |
| `data.settings.ReaderSettingsRouterTest` | **16** | 0 | 0 | 0 |
| **合计（2 类）** | **36** | **0** | **0** | **0** |

| 项 | 值 |
|---|---|
| 请求类数 / 实际 XML 文件数 | **2 / 2** |
| 缺失类 / 零匹配 | **0 / 0** |
| XML mtime | **2026-09-22 00:37:34**（与我的运行时刻一致，非陈旧；Zcode 自己那次是 00:18:21） |
| 与 Zcode 数字是否一致 | 一致（20 + 16 = 36），但这是**我独立复算**的结果，不是引用 |

禁改文件哈希见 §5 末表；`git diff --numstat` 实测：
`ReaderSettingsSheet.kt 148 / 25`，`ReaderSettingsSheetTest.kt 110 / 0`（**纯新增，0 删除** ⇒
"既有 6 个 Compose 用例未改动"这条主张由 numstat 直接证明），
`ReaderSettingsScopeNoticeTest.kt` 未跟踪（`??`），Zcode 的 `git diff --no-index` 证据 279 行与 `wc -l` 一致。

我也独立复核了 `/tmp/d02b/after.sha256`：从仓库根 `sha256sum -c` → **3/3 OK**，
当前工作树三个文件与 Zcode 声明的 after 逐字节一致 ⇒ **其变异实验后的还原是真的**
（我未重复变异，任务书禁止改码）。

---

## 12. P0 / P1 / P2 / P3

| 轴 | P0 | P1 | P2 | P3 |
|---|---:|---:|---:|---:|
| Standards | 0 | 0 | 0 | **7**（ST-1…ST-7） |
| Spec | 0 | 0 | 0 | **3**（SP-1…SP-3） |
| **合计** | **0** | **0** | **0** | **10** |

### 与 Zcode 自评（P3×3）的差异

| 项 | Zcode | 我 |
|---|---|---|
| 显示页「页眉页脚开关」 | P3-1 ✅ | 同意 P3，但给出**可直接替换的完整文案**（§6） |
| `assertIsDisplayed` 不证截断 | P3-2 ✅ | 同意，并补充：生产端未设 `maxLines`，结构上不可能省略 ⇒ 该测试边际价值有限 |
| 根页说明与子页说明重叠 | P3-3 ⚠️ | **不认同这是缺陷**：根页与翻页页是不同屏幕，任务书明确要求根页不得省略 |
| 例外清单对齐守卫缺失 | **漏** | ST-1 |
| `else ->` 源码扫描冗余且脆弱 | **漏** | ST-2 |
| 句号断言 / 单字断言 / 禁词过宽 | **漏** | ST-3 / ST-4 / ST-5 |
| AnimatedContent 过渡期 testTag 竞争 | **漏** | ST-6 |
| diff 数字口径 +173 实为 148+25 | **漏** | ST-7 |
| P3-1 归因错误进入生产注释 | **漏** | SP-3 |
| GLOBAL 文案未说当前书该项会被清 | **漏** | SP-2 |

⇒ Zcode 的自评**方向诚实、无夸大**（尤其"已编译未执行"写得很清楚），但**覆盖面偏窄**，
10 项里只自认 3 项，且其中 1 项（P3-3）我认为不成立。

---

## 13. 未覆盖项

| # | 项 | 说明 |
|---|---|---|
| U1 | 6 个 Compose 用例的**实际执行** | 任务书禁止 instrumentation；我只验证了可编译 + 选择器存在（`contentDescription = "返回"`、`SettingsList` 的 scroll action、分组入口文案）。ST-6 的 flake 风险因此**未被证伪** |
| U2 | 提示在真机 320dp / 横屏 / 大字号下的观感 | 未跑；§7.6/7.7 是**静态推导**，不是实测 |
| U3 | TalkBack 实际朗读 | 未跑；结论（装饰图标 + 可见文本 = 单次朗读）来自语义树推理 |
| U4 | 变异实验 A/B/C | **未复现**（需改码，任务书禁止）。我只复核了其"还原"证据（§11 的 3/3 OK） |
| U5 | 290 项附加回归 | 未重跑；定向 2 类 + 双编译 + 哈希比对已覆盖判断所需 |
| U6 | `headerLeft` 无 UI 写点导致 `customTip*` 回落不可触发 | 属既有模型遗留（上轮 §3.5(b) 已记），本轮未处理，也不该在本片处理 |
| U7 | SP-2 的文案加长是否破坏 `scopeNoticeRemainsVisibleAt320DpWithLargeFont` | 未验证（若采纳 SP-2 修复需重估行数） |

---

## 14. SEAM REQUEST

**无。**

本轮产品改动 100% 落在 `ReaderSettingsSheet.kt`（授权的唯一产品文件）内：
页级提示是该文件内 `private` composable，根页说明是内联 `Text`，
未触碰 `ui/components/**`、`ReaderSettingsCommon.kt`、三个子页文件、路由与存储 —— 全部由 §5 末的哈希表证明。
我提的 10 项修复建议**同样只需要 `ReaderSettingsSheet.kt` + 两个测试文件**，不产生新的边界需求。

---

## 15. 是否放行真机

**建议：先做两处小修（SP-1 文案 + ST-6 断言），再放行真机。**

理由：

1. **SP-1 是真机要验的东西本身**。真机验收这一片要看的就是"用户能否看懂哪一项影响所有书"，
   带着一个可能读反的措辞上真机，验收者要么报成缺陷（返工一轮），要么判"看着没问题"（把歧义固化）。
   修复 = 换 1 个字符串 + 同步 3 处测试期望值，成本远低于排队一轮。
2. **ST-6 会让真机/CI 上的 Compose 用例随机红**。如果验收流程包含跑这 6 个用例，
   一次假红会直接中断验收。修复 = 加一行 `mainClock.advanceTimeBy(200)`。
3. 其余 8 项 P3（守卫缺失、测试精简、注释归因、数字口径）**不阻断真机**，
   可与下一次触碰该文件时一并处理，或由 Codex 决定是否单列小片。

真机需要覆盖、JVM 无法证明的清单：

| # | 项 |
|---|---|
| R1 | 五个子页在 BOOK 作用域下进页第一屏即可见提示（无需滚动） |
| R2 | 提示不遮挡分组标题 / 返回按钮 / 第一个控件 |
| R3 | 320dp 级小屏 + 系统字号 1.3 下长句完整换行、不截断、正文仍可滚动 |
| R4 | 横屏（407dp 高）下提示 + 正文同时可用 |
| R5 | 切「改全局默认」后提示文案实时变化（不需重进 sheet） |
| R6 | 返回根页后提示消失、根页翻页速度说明仍在 |
| R7 | TalkBack 朗读顺序：标题 → 返回 → **提示整句** → 第一个控件（不重复、不逐字读图标） |
| R8 | 改一个全局项（如翻页速度）后，根页「覆盖 N 项」计数**不增加**（证明语义未变，D-2-B 只加信息） |
| R9 | 换一本书确认该项确实共用（全局语义的行为面） |

**R8/R9 是本轮"不改数据语义"承诺的真机闭环**，JVM 侧只能证明路由没动，不能证明观感。

---

## 16. 回传摘要

| 项 | 内容 |
|---|---|
| **结论** | **PASS**（无 P0/P1/P2；SP-1 是唯一可能改判 PARTIAL 的项，判据与替代方案已给出） |
| Standards | P0 0 / P1 0 / P2 0 / **P3 7** |
| Spec | P0 0 / P1 0 / P2 0 / **P3 3** |
| 显示页文案 | **修改**（P3）。最终文案：`除沉浸模式、显示进度条、自动隐藏和显示安静阅读信息外，本页其他设置均为全局设置，对所有书籍生效` |
| Compose 布局 | **安全**（`weight(1f)` 主轴约束 + 子页 `fillMaxSize` 交叉轴推导成立；ROOT 无 notice 不占位；长文案可换行；无需额外 `fillMaxWidth()`） |
| JVM 测试 | **2 类 / 36 项 / 0 失败 / 0 错误 / 0 跳过**，退出码 **0**，XML mtime 2026-09-22 00:37:34 |
| AndroidTest 编译 | **PASS**（退出码 0，0 警告） |
| 主 Kotlin 编译 | **PASS**（退出码 0，1 条警告在未触碰的 `LibraryBrowserRoute.kt`，mtime 09-18） |
| 建议删除/合并 | 删 4：`the notice function has no catch all else branch`、`no notice ends with a full stop`、`eye care copy carries no exception list`、`the global copy states both the write target and the non destructive guarantee`；合并 3→1（三条禁用子串用例）；新增 1（例外清单与页面事实对齐守卫）。净 20 → 13 |
| 越界修改 | **无**。15 个禁改文件哈希逐一等于我上轮独立记录值；产品改动仅 `ReaderSettingsSheet.kt`；androidTest 改动 110/0 纯新增 |
| SEAM REQUEST | **无** |
| 是否放行真机 | **先修 SP-1 文案 + ST-6 断言，再放行**；R1–R9 清单见 §15 |
| 报告路径 | `docs/coordination/reviews/undated/agent-review-d02b-qoder.md` |

### 边界遵守

未修改任何产品源码 / 测试源码 / Zcode 报告 / 看板 / handoff / Obsidian；
未 commit / push / stage / stash / reset / checkout / clean / 全仓格式化；
未构建或安装 APK；**未操作手机；未使用 MuMu 或任何模拟器**；未运行 instrumentation；
未处理 D-2-A / A01 / S07 / S08–S20；未顺手修复任何发现的问题；
未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`。
工作区 344+ 行他人既有改动全部原样保留，且我**明确未把**紧凑间距、`isOverridden`、
翻页效果改名、复杂动画提示、`maxHeightFraction` 归因给 D-2-B。
HEAD 仍 `5aed593`，`git diff --cached` = 0，`git stash list` = 0。

**未领取下一任务。**
