# 阶段 E：G6 手势收口与可验收 APK（行为保留版候选）（2026-09-30）

> **窗口**：G6 时长单位 —— 手势收口（保留档案卡原点击区域 + 容器统一手势）+ qahost host/test APK。
> **共享恢复仓库（只读入口）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`；**HEAD `513891d6a66a9b78e072ba759d34ed580d377ecf`**；暂存区空。
> **本窗口独占目录**：`E:\creation-reading-assistant\stage-e-g6-behavior-preserved-20260930\`（候选源码 / `_cache` / `_evidence` / 补丁 / READY.json 全在 E 盘）。
> **来源候选（只读）**：`E:\creation-reading-assistant\stage-e-g6-integrated-20260930\android`（22 生产 + 5 测试，原定向 JVM 49/49）。
> **输入**：[G6 集成复核](../../reviews/2026-09/2026-09-30-stage-e-g6-integrated-review.md)、[三窗复核与关键路径重排](../../reviews/2026-09/2026-09-30-stage-e-replan-codex-review.md)、[G6 设置页隔离](2026-09-30-stage-e-g6-settings-isolation.md)。
> **纪律**：共享仓库**唯一写入 = 本文件**。未 stage / commit / push / reset / checkout；未迁目录；**未操作设备（MuMu/ADB/真机均未使用）**；未修改来源候选与其他窗口目录。

---

## 一、结论摘要

1. **手势收口完成且行为保留**：档案卡恢复**整卡点击区域**（点击身份区 / 时长区 / 原可点空白区都打开「我的阅读」），长按切换单位由**卡片这一个代表容器统一拥有**；卡片内不再有父子重复的手势入口，时长文本**不带**空 onClick 子手势（不会吞掉原导航）。方案镜像代码库既有模式 `MyReadingScreen` 的「整行唯一手势层」。
2. **零共享组件改动**：手势通过 `SectionCard(modifier = Modifier.combinedClickable(...))` 从调用点挂载（`SharedComponents.kt` 与 `DurationDisplay.kt` **逐字节未改**——比任务允许的"最小接缝"更小）。
3. **6 条真手势 Compose 仪器测试**（非源码字符串断言）已新增并通过编译；**测试 APK 实测包含该测试类**（classes6.dex）。
4. **本地检查全过**：三变体编译 EXIT 0；定向 JVM 9 类 **49/49**（0 失败/错误/跳过）；`assembleQahost` + `assembleQahostAndroidTest` EXIT 0。
5. **双基线可复算补丁**：`from-head`（35 文件 / 104 hunk，`1751cc61…`）与 `from-integrated`（2 文件 / 8 hunk，`c40e0b44…`），两者 round-trip 均 **838/838 文件字节级一致**。**明确声明**：旧 `g6-integrated.patch` 是 consumers3→integrated 的 7 文件增量，不是 HEAD→完整 G6 补丁。
6. **READY.json 已写出**（仅在所有本地检查通过后）：host/test APK 绝对路径与 SHA、包名、runner、测试类、构建命令与退出码齐备。
7. **设备未验**：手势运行态、TalkBack 朗读、持久化重启、与 S06 联合态均 NOT RUN——**编译与 JVM 通过不等于设备通过**，交独立设备窗口（窗口 4）。

## 二、手势设计（行为保留版）

### 2.1 与来源候选（integrated）的实际差异

| 检查点 | integrated（F3-B 收窄版） | **本候选（行为保留版）** |
|---|---|---|
| 档案卡容器 | `SectionCard {`（无 onClick，非可点 Surface） | `SectionCard(modifier = Modifier.combinedClickable(onClick=…, onLongClick=…))`——**容器是唯一手势层** |
| 点击区域 | 仅身份行 `Row.clickable` | **整卡**（含身份行、时长/读完统计区、格间空白）——恢复改动前行为 |
| 长按切换 | 时长文本 `HomeStat(valueModifier = Modifier.durationUnitToggle())` | **卡片手势层**（`onLongClick = toggleDurationUnit`） |
| 子文本手势 | 时长文本带空 `onClick` 的 combinedClickable（父子两层） | **无**（文本全部免手势，不吞父级点击） |
| 读屏 label | 仅子文本一处长按 label | 卡片一处：`onClickLabel="查看阅读档案"` + `onLongClickLabel="切换时长显示单位"`（`DURATION_UNIT_TOGGLE_LABEL` 公共常量） |
| `HomeStat` | 含 `valueModifier` 参数 | **回退**为基线形态（参数删除，纯文本） |
| 灵感格 | `HomeStat(onClick = OpenInspirations)` | **原样保留**（独立合并节点，自己的点击） |
| `SharedComponents.kt` / `DurationDisplay.kt` | — | **未改动**（零接缝） |

### 2.2 为什么这样能满足"不吞导航"

`DurationDisplay.durationUnitToggle()` 的空 `onClick` 挂在子节点时会参与命中竞争、截获父级点击（KDoc 边界）。本候选把**点击与长按收敛到同一个节点**（卡片的 combinedClickable），子文本不再有任何手势——不存在父子两层竞争，也不存在子级空 `onClick` 吞导航。这与 `MyReadingScreen.kt:488-500`（"整行是这条记录**唯一**的手势层…行内时长文本刻意不带任何手势"）完全同款。

### 2.3 行为变化登记（相对 integrated，恢复为相对 HEAD 的原状）

- 相对 **integrated**：点击区域从"仅身份行"**恢复为整卡**；长按入口从时长文本**移到卡片**。这是本次的目标（integrated 的收窄是上一窗口的产品取舍，本候选按 replan 复核"保持既有点击功能"的要求恢复）。
- 相对 **HEAD**：点击行为与 HEAD 一致（整卡可点打开「我的阅读」）；**新增**的是整卡长按切换时长单位（G6 功能本身）。
- **仍需产品确认**：整卡长按触发单位切换，而点击仍是导航——同一区域两种手势；若产品认为长按入口应更显性，`AppearanceSubPage` 的显式开关仍是可发现入口（未改动）。

## 三、手势仪器测试（真手势，非源码字符串）

文件：`app/src/androidTest/java/com/creationreadingassistant/ui/screen/profile/ProfileHomeScreenGestureTest.kt`（SHA `16bd6c2b…`）。用 `createComposeRule()` 直接组合 `ProfileHomeScreen`（真实 Compose 树；`ProfileUiState(homeSummary=3 天/5 本读完/7 条灵感)`，`LocalDurationUnit=HOURS`、`LocalDurationUnitToggle` 计数注入）。面积点击用 `useUnmergedTree=true` 的叶子节点定位到**具体区域**（合并节点会退化为整卡中心，无法区分区域）。

| # | 用例 | 断言（真手势） |
|---|---|---|
| 1 | `click_identity_row_opens_reading_archive` | 身份标题（`创作阅读助手`）区域 `click()` → 触发 `OpenSubPage(READING)` |
| 2 | `click_duration_value_still_opens_reading_archive` | **时长值文本**区域 `click()`（等 count-up 到终值后）→ 仍打开阅读档案（原整卡区域保留） |
| 3 | `click_non_gesture_stat_label_still_opens_reading_archive` | 另一统计格标签（无手势子节点 = 原"空白区"等价物）`click()` → 打开阅读档案 |
| 4 | `long_press_duration_value_toggles_exactly_once_and_does_not_navigate` | 时长文本上 `longClick()` → **toggle 计数恰为 1**，且未触发 `OpenSubPage(READING)` / `OpenInspirations` |
| 5 | `inspiration_stat_click_goes_to_inspirations` | 灵感格 `performClick()` → 触发 `OpenInspirations`，且不顺带触发档案卡导航 |
| 6 | `card_semantics_hold_the_single_toggle_and_leaf_text_holds_none` | 合并树卡片节点：含 `OnClick` + `OnLongClick`，且长按 label == `DURATION_UNIT_TOGGLE_LABEL`；**未合并树**时长文本叶子：无 `OnClick`/`OnLongClick`（子级不吞点击、读屏不重复）；灵感格节点：有 `OnClick`、**无** `OnLongClick` |

> 用例 4 的"恰为 1"直接判别"父子重复挂"回归（重复挂会让一次长按触发两次或一次点击被吞）；用例 6 判别"同一语义组件只有一个切换操作"。**这些用例只在设备/仪器环境执行**，本窗口仅验证其编译与入包。

## 四、本地构建与验证

### 4.1 环境与命令（全部在 E 盘本窗口目录）

```
JAVA_HOME=D:\develop\Java\jdk-17.0.14
GRADLE_USER_HOME=E:\creation-reading-assistant\stage-e-g6-behavior-preserved-20260930\_cache\gradle-home
# 编译（三变体）
gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --offline --max-workers=1   → EXIT 0
# 定向 JVM（9 类，G6 关联）
gradlew :app:testDebugUnitTest --tests <9 classes> --offline --max-workers=1                                                  → EXIT 0
# host/test APK
gradlew :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --max-workers=1           → EXIT 0
```

日志：`_evidence/compile-bp-4.log`、`_evidence/test-bp-jvm.log`、`_evidence/assemble-bp.log`。

### 4.2 定向 JVM（49/49）

| 测试类 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| DurationPrefsTest | 8 | 0 | 0 | 0 |
| DurationUnitTest | 2 | 0 | 0 | 0 |
| TimeUtilsTest | 13 | 0 | 0 | 0 |
| HomeReadingArchiveSectionTest | 2 | 0 | 0 | 0 |
| YearBillHeadlineTest | 10 | 0 | 0 | 0 |
| HeatmapBuildTest | 4 | 0 | 0 | 0 |
| StatsComputationTest | 1 | 0 | 0 | 0 |
| ComputeTodayReadingMsTest | 6 | 0 | 0 | 0 |
| StatsFileStructureTest | 3 | 0 | 0 | 0 |
| **合计** | **49** | **0** | **0** | **0** |

与 integrated 基线逐类一致 ⇒ 手势收口**无 JVM 回归**（详情 `_evidence/verify-artifacts.log`）。

### 4.3 APK 与测试类入包（实测）

| 项 | 值 |
|---|---|
| host APK | `…\android\app\build\outputs\apk\qahost\app-qahost.apk`，SHA-256 `7d39b0a02ca230f2aa4eaa4a211ea478e4d7ee196854afa9c5fa483b335780ea` |
| test APK | `…\android\app\build\outputs\apk\androidTest\qahost\app-qahost-androidTest.apk`，SHA-256 `428d58f4c7045c84422d6a531387b16052e892789ab0fe48bce4de4de879240e` |
| test APK 包名 / target | `com.creationreadingassistant.qahost.test` → `com.creationreadingassistant.qahost` |
| runner | `com.creationreadingassistant.testing.CraIsolatedTestRunner`（aapt 实测） |
| **手势测试入包** | `ProfileHomeScreenGestureTest` **实测存在于 test APK 的 classes6.dex**（zipfile 扫描 dex 字节） |

### 4.4 排除项复核（全 `app/src` word-boundary）

设置搜索 4 符号、外观重设计 2 符号、`MetricMicroCard`、`HeatmapCellTooltip`、`StreakMicroCapsule`、`YearBillHeroBanner`、`ArchiveStat`、`Compact*`、`readerPaperOptions` —— **泄漏 0**。未重设计卡片、未夹带设置搜索、未改统计布局。

## 五、双基线补丁（来源基线明确）

| 补丁 | 基线 | 规模 | SHA-256 | round-trip |
|---|---|---|---|---|
| `g6-behavior-preserved-from-head.patch` | **HEAD `513891d`**（git archive 提取） | 35 文件（27 M + 8 A）/ 104 hunk / 130,037 字符 | `1751cc6179d5cf3a39b260c3e5091f8e11bbc465576b9921bb47a5d6cb984c98` | HEAD 提取 + 补丁 == 候选，**838/838 字节级一致** |
| `g6-behavior-preserved-from-integrated.patch` | **integrated 候选树** | 2 文件（1 M + 1 A）/ 8 hunk / 12,153 字符 | `c40e0b44e0ba64b74c725a8232da9ebd73dab1e7048e09e8a27e349be011a82b` | integrated + 补丁 == 候选，**838/838 字节级一致** |

**from-head 补丁内容构成（如实登记）**：G6 22 生产 + 5 测试 + 本窗口新增手势测试 + **S06 最小候选底座文件**（`SettingsStore.kt`、`App.kt`、`MainActivity.kt`、`values/themes.xml`、`values-night/themes.xml`、`values/colors.xml`、`SettingsStoreBootMirrorTest.kt`——G6 根部注入依赖的已单独验证底座）。`local.properties` 属构建基建，**已从补丁排除**。

**警告重申**：`g6-integrated.patch`（SHA `57638361…`）是 consumers3→integrated 的 7 文件增量，**不能**当作 HEAD→完整 G6 补丁；本报告的两份补丁才是基线明确的完整/增量形态。

## 六、READY.json（已写出）

`E:\creation-reading-assistant\stage-e-g6-behavior-preserved-20260930\READY.json`：候选源码清单及 SHA（12 个关键文件）、本窗口变更文件（1 M + 1 A）、host/test APK 绝对路径与 SHA、包名、runner、测试类（1 手势仪器类 + 9 定向 JVM 类）、构建命令与退出码、手势契约摘要、设备验证状态（NOT RUN，交窗口 4）。**仅在全部本地检查通过后写出**。

## 七、未验项（不得当作通过）

| # | 项 | 状态 |
|---|---|---|
| 1 | **手势运行态**（整卡点击、长按恰一次、不导航） | **NOT RUN**（无设备）——由窗口 4 在 MuMu 执行本候选的 `ProfileHomeScreenGestureTest` |
| 2 | **TalkBack 朗读唯一性** | NOT RUN（源码结构 + 语义断言已备，真机读屏未验） |
| 3 | **持久化重启** | NOT RUN（`DurationPrefsTest` 有 DataStore 往返，真实进程重启未验） |
| 4 | **与 S06 联合态** | 未与其它 S06 变体联合验证（底座为 S06 最小候选） |
| 5 | `lintDebug` / `assembleDebug` / release APK / connectedAndroidTest | 未运行（本窗口只做所属编译 + 定向 JVM + qahost APK） |
| 6 | 视觉/手感（整卡 ripple、长按触感） | NOT RUN；本候选按压反馈由 `Modifier.combinedClickable` 默认 indication 提供，与 integrated 的 Surface 内 ripple 存在**表现差异**（未做视觉验收） |
| 7 | 实体手机 / 正式包 | 未触碰（任务约束） |

## 八、过程事故与修复（如实记录）

1. **缓存残缺**：首次离线编译失败于 AGP 8.6.0 无法解析——`gradle-home-g0` 副本缺 AGP 与 gradle 发行版。勘察四份缓存后改用 `stage-e-s06-boot-isolated-20260929/gradle-home`（AGP ✓ dist ✓ 7554MB）全新复制为 `_cache/gradle-home`。
2. **孤儿 daemon 占用**：失败构建遗留的 java 进程持有缓存句柄（rm/rename 报 busy），按命令行定位并终止 PID 38628 后清理成功。
3. **编译迭代**：`combinedClickable` 需 `@OptIn(ExperimentalFoundationApi::class)`（对齐 `MyReadingScreen` 写法）；测试文件三处修复（补 `import …test.click`、`SemanticsActions` 改 `androidx.compose.ui.semantics`、`getOrNull` 改 operator `get`——该 Compose 版本无 `getOrNull`）。全部修复后三变体 EXIT 0。
4. 首次构建日志重定向失败（`_evidence` 目录未建）——建目录后重跑。

## 九、纪律声明

- 共享仓库**唯一写入 = 本文件**；未改共享产品源码 / 测试 / 配置 / 其他窗口报告 / handoff。
- **未** stage / commit / push / reset / checkout / stash；**未**迁目录；**未**触碰原仓库、正式包、正式书库、实体手机；**未**使用 MuMu / ADB（设备验收交窗口 4）。
- 来源候选 `stage-e-g6-integrated-20260930` 只读取；本窗口全部写入在其独占目录 `stage-e-g6-behavior-preserved-20260930`（含 `_headref` 只读参照、`_cache`、`_evidence`、补丁、READY.json）。
- 未把编译/JVM 通过写作设备通过；§七 7 项未验逐条标注。
