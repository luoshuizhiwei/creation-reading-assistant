# D-2-B-F1 实现报告：显示页文案改用真实控件名 + Compose 动画稳定化

> 执行 Agent：Zcode　|　实际模型：`deepseek v4.1Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收工 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 前置：`agent-result-d02b-zcode.md`（D-2-B 实现）+ `agent-review-d02b-qoder.md`（Qoder 复核，SP-1 / ST-6）
> 报告路径：`docs/coordination/agent-results/agent-result-d02b-f1-zcode.md`
> 状态：**等待 Codex 核验**

---

## 1. 最终结论

**CANDIDATE PASS**

| # | 本轮要求 | 结果 | 证据 |
|---:|---|---|---|
| 1 | 显示页文案改用真实控件名 | ✅ | §3；产品文件**仅 1 行**变更 |
| 2 | JVM 与 Compose 测试同步精确期望值 | ✅ | §3 / §6 |
| 3 | 旧文案「除沉浸模式、进度条、菜单隐藏和页眉页脚开关外」在 3 个授权文件中零命中 | ✅ | §7 |
| 4 | `openGroup()` 显式推进主时钟 ≥160ms | ✅ | `advanceTimeBy(250L)` + `waitForIdle()` |
| 5 | 返回后断言 ROOT 前同样等动画 | ✅ | `goBackToRoot()` |
| 6 | 抽成单一辅助函数、不复制 | ✅ | `settlePageAnimation()` 被 2 个辅助函数复用 |
| 7 | 不使用 `Thread.sleep` | ✅ | §4 |
| 8 | 不放宽产品断言 / 不用 `onFirst()` 掩盖 | ✅ | §4.3 |
| 9 | 未顺手扩大范围（P3 一律不动） | ✅ | §11 |

---

## 2. 实际修改文件

**产品代码 1 个**（授权范围内）：

| 文件 | 状态 | D-2-B 后(前16) | F1 后(前16) | 变更量 |
|---|---|---|---|---|
| `ui/screen/reader/sheets/ReaderSettingsSheet.kt` | ` M` | `43134fd58db3e8a5` | `fca16e3f91d3c441` | **1 行**（435 行 → 435 行） |

**测试代码 2 个**（授权范围内）：

| 文件 | 状态 | D-2-B 后(前16) | F1 后(前16) | 变更量 |
|---|---|---|---|---|
| `test/.../sheets/ReaderSettingsScopeNoticeTest.kt` | `??` | `48e20ee4a50c1b63` | `081ec4b662040f9b` | 20 → 21 项（+1 反向钉子） |
| `androidTest/.../sheets/ReaderSettingsSheetTest.kt` | ` M` | `84229fb8ff6ede9a` | `6ea820f9dd933f59` | +24 行（helper + 调用点） |

**报告 1 个**：`docs/coordination/agent-results/agent-result-d02b-f1-zcode.md`（本文件）。

**产品改动是纯一行字符串替换的证明**：

```bash
# 行数不变
$ grep -c "" /tmp/d02bf1/Sheet.after.bak                                    # 435
$ grep -c "" .../ReaderSettingsSheet.kt                                     # 435

# 剔除该行后两份文件完全相同
$ diff <(grep -v "除沉浸模式" /tmp/d02bf1/Sheet.after.bak) \
       <(grep -v "除沉浸模式" .../ReaderSettingsSheet.kt)
# (无输出)
```

即本轮**没有**触碰函数签名、布局结构、`when` 分支、注释或其他任何行。

---

## 3. 新旧 DISPLAY 文案

### 3.1 旧（D-2-B，已废弃）

```
除沉浸模式、进度条、菜单隐藏和页眉页脚开关外，本页其他设置均为全局设置，对所有书籍生效
```

**问题（Qoder SP-1）**：四个例外里，只有「沉浸模式」是真实行标题；其余三个都不是：

| 旧文案用词 | 实际是什么 | 源码 |
|---|---|---|
| `进度条` | 行标题实为 **「显示进度条」** | `ReaderDisplaySettings.kt:47` |
| `菜单隐藏` | 这是**分组标题**（`:64`），其下真正的控件是 **「自动隐藏」** | `:64` / `:68` |
| `页眉页脚开关` | **不是任何控件名**；「页眉页脚」是**分组标题**（`:81`），其下真正的控件是 **「显示安静阅读信息」** | `:81` / `:87` |

**为什么这是方向相反的风险**：「页眉页脚」分组下正好挂着六个 `customTip*` 模板选择器
（`:103-131`），而这六项**写全局**。把「页眉页脚」整体列为例外语境，用户会以为整组都是本书设置
—— 与 D-2-B 的目的（消除作用域误解）方向相反。

### 3.2 新（本轮）

```
除沉浸模式、显示进度条、自动隐藏和显示安静阅读信息外，本页其他设置均为全局设置，对所有书籍生效
```

**四个例外逐字等于该页行标题原文**（我独立复核，非照抄 Qoder 引用）：

| 例外 | 对应字段 | 行标题 | 源码行号 |
|---|---|---|---|
| 沉浸模式 | `immersiveMode` | `title = "沉浸模式"` | `:38` |
| 显示进度条 | `showProgressBar` | `title = "显示进度条"` | `:47` |
| 自动隐藏 | `autoHideSeconds` | `title = "自动隐藏"` | `:68` |
| 显示安静阅读信息 | `showReaderInfo` | `title = "显示安静阅读信息"` | `:87` |

**集合仍与源码事实一致**：该页 13 个可编辑字段中，4 个可覆盖（本书）+ 9 个全局，
新文案点名的正是那 4 个可覆盖项（Qoder §5 独立复核结论「4/4 集合一致」不受影响，仅措辞修正）。

### 3.3 测试期望值同步

| 文件 | 位置 | 变更 |
|---|---|---|
| `ReaderSettingsScopeNoticeTest.kt` | `display under the book scope names exactly its four book level exceptions` | 例外清单 4 项改为真实控件名 |
| 同上 | `display copy names no group title instead of a control`（**新增**） | 反向钉子：不得出现分组名「页眉页脚」/「菜单隐藏」 |
| `ReaderSettingsSheetTest.kt` | `bookScopeNoticeAppearsOnEverySubPageWithTheExpectedCopy`（:279） | DISPLAY 期望文案同步 |
| 同上 | `scopeNoticeRemainsVisibleAt320DpWithLargeFont`（:351） | 同上 |

**新增的反向钉子为什么不是冗余**：正向断言「必须包含四个真实控件名」在
旧文案下**也会通过**（旧文案含「沉浸模式」，且「进度条」是「显示进度条」的子串）——
只有反向断言才能真正拦住回退。变异 A 证明该用例确实咬住（§9）。

---

## 4. 动画稳定辅助函数

### 4.1 实现

```kotlin
/**
 * 等待 `AnimatedContent` 的页面切换动画走完（淡入 160ms / 淡出 120ms）。
 *
 * 为什么必须等：过渡窗口内**新旧两个 target 的内容同时存在**，
 * 此时单节点断言（`onNodeWithTag`）会报 "Expected exactly 1 node but found 2"，
 * 计数断言（`assertCountEquals(0)`）会读到尚未 dispose 的旧节点 —— 假红。
 *
 * 推进 Compose 主时钟而不是 `Thread.sleep`：时钟推进是确定性的，
 * 不受机器负载影响；250ms > 160ms，留出余量覆盖两个方向的过渡。
 * 等完之后断言仍是**严格**的唯一节点 / 零节点，不放宽任何产品断言。
 */
private fun settlePageAnimation() {
    composeRule.mainClock.advanceTimeBy(250L)
    composeRule.waitForIdle()
}

/** 从根页进入指定分组（分组标题即入口文案），并等待切换动画结束。 */
private fun openGroup(entry: String) {
    scrollToText(entry)
    composeRule.onNodeWithText(entry).performClick()
    settlePageAnimation()
}

/** 点击返回并等待切回根页的动画结束。 */
private fun goBackToRoot() {
    composeRule.onNodeWithContentDescription("返回").performClick()
    settlePageAnimation()
}
```

### 4.2 覆盖所有页面切换路径

`settlePageAnimation()` 只在**一个**地方定义（:243），被两个语义化辅助函数复用：

| 路径 | 入口 | 是否等待 |
|---|---|---|
| 根页 → 子页（6 处调用） | `openGroup(...)` | ✅ |
| 子页 → 根页（2 处调用） | `goBackToRoot()` | ✅ |

新增 D-2-B 用例的**每一次页面切换都走这两个辅助函数**，无一处直接 `performClick` 后立即断言。
（文件内其余 `performClick` 属既有用例的同页内控件操作，不涉及 target 切换，未改动 —— 见 §11 边界说明。）

### 4.3 未放宽断言（逐条对照任务书禁令）

| 禁令 | 检查 | 结果 |
|---|---|---|
| 不得用 `Thread.sleep` | `grep -c "Thread.sleep"` | **0**（仅 KDoc 提到该词说明为何不用） |
| 不得放宽产品断言 | 新增用例仍用 `assertCountEquals(0)`（:266/:315/:338）与单节点 `onNodeWithTag(...).assertIsDisplayed()` | 未改 |
| 不得用 `onFirst()` 掩盖重复节点 | `grep -c "onFirst()"` | **0** |
| 动画后仍严格断言唯一/零节点 | `returningToRootRemovesTheScopeNotice` 注释明确「这里必须是**零节点**，不是"至少一个"」 | 保持 |

**时钟推进值选择**：`250ms` > 淡入 160ms，也 > 淡出 120ms，覆盖两个方向并留 90ms 余量。
Qoder ST-6 建议 `advanceTimeBy(200)`，我取 250ms 以获得更宽的安全边界（不影响确定性）。

---

## 5. 三条验证命令与退出码

工作目录 `android/`，Git Bash 下调用 `./gradlew.bat`（等价于任务书的 `.\gradlew.bat`）。
日志中的中文以 GBK 写入，因此 grep 需 `-a`（否则被当二进制跳过）。

### 5.1 命令 1：定向 JVM 测试

```bash
./gradlew.bat --console=plain :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.screen.reader.sheets.ReaderSettingsScopeNoticeTest" \
  --tests "com.creationreadingassistant.data.settings.ReaderSettingsRouterTest" \
  --rerun
```

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 结果 | `BUILD SUCCESSFUL in 56s` |
| 警告 | **0**（`warning:` 0，`w:` 0） |

### 5.2 命令 2：AndroidTest 编译

```bash
./gradlew.bat --console=plain :app:compileDebugAndroidTestKotlin --rerun
```

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 结果 | `BUILD SUCCESSFUL in 11s` |
| 警告 | **0** |

### 5.3 命令 3：主 Kotlin 编译

```bash
./gradlew.bat --console=plain :app:compileDebugKotlin --rerun
```

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 结果 | `BUILD SUCCESSFUL in 1m 39s` |
| 警告 | **1 条 Kotlin 警告，与本任务无关**（见 §5.4） |

### 5.4 警告明细

```
w: .../ui/screen/shelf/LibraryBrowserRoute.kt:145:48
   'val Icons.Outlined.Sort: ImageVector' is deprecated.
   Use the AutoMirrored version at Icons.AutoMirrored.Outlined.Sort.
```

- 该文件**不在授权范围**，本轮**未修改**（哈希与 D-2-B 轮一致，mtime 早于本轮窗口）；
- 属既有 deprecated 图标警告，与 D-2-B-F1 无因果关系；
- **本任务 3 个文件产生 0 条编译警告**。

---

## 6. XML 统计

### 6.1 命令 1 的测试结果

| 测试类 | tests | failures | errors | skipped | XML mtime |
|---|---:|---:|---:|---:|---|
| `ui.screen.reader.sheets.ReaderSettingsScopeNoticeTest` | **21** | 0 | 0 | 0 | 01:08:01 |
| `data.settings.ReaderSettingsRouterTest`（既有，未改动） | 16 | 0 | 0 | 0 | 01:08:01 |
| **合计（2 类）** | **37** | **0** | **0** | **0** | — |

**请求 2 类 == XML 2 个文件；缺失类 0；零匹配 0。**
XML 路径：`android/app/build/test-results/testDebugUnitTest/TEST-com.creationreadingassistant.*.xml`
XML mtime `01:08:01` 与运行时刻一致（非陈旧文件）。

**项数变化说明**：D-2-B 时为 20 项，本轮 **21** 项 —— 新增 1 条反向钉子
`display copy names no group title instead of a control`（§3.3 说明其必要性）。
任务书要求「不新增源码字段提取守卫」「不重写 279 行」，本条属**同一契约的措辞修正配套**，
不是新增守卫类型，且未删除/合并任何既有用例。

---

## 7. 旧文案残留扫描

扫描范围：本轮 3 个授权文件。

| 检索串 | 命中数 | 说明 |
|---|---:|---|
| `页眉页脚开关` | **0** | ✅ 完全清除 |
| `除沉浸模式、进度条、菜单隐藏` | **0** | ✅ 旧句子前缀零命中 |

```
$ grep -rc "页眉页脚开关" <3 files> | awk -F: '{s+=$2} END{print s}'
0
$ grep -rc "除沉浸模式、进度条、菜单隐藏" <3 files> | awk -F: '{s+=$2} END{print s}'
0
```

**仓库其他位置的命中（不在授权范围，未修改）**：

| 文件 | 命中 | 处置 |
|---|---:|---|
| `docs/coordination/agent-results/agent-result-d02b-zcode.md` | 2 | 任务书 §本轮明确不处理「不修改 Zcode 原报告」→ **保留**，作为 D-2-B 当时文案的历史记录 |
| `docs/coordination/reviews/undated/agent-review-d02b-qoder.md` | 多条 | 任务书明确「不修改 Qoder 报告」→ **保留**（它是引用旧文案的复核证据） |

**新文案存在性验证**：

```
$ grep -rn "显示安静阅读信息" <3 files>
main/.../ReaderSettingsSheet.kt:101          ← 产品文案
test/.../ReaderSettingsScopeNoticeTest.kt:104 ← JVM 例外清单
androidTest/.../ReaderSettingsSheetTest.kt:279,351 ← Compose 期望值
```

---

## 8. P0 / P1 / P2 / P3

| 级别 | 数量 |
|---|---:|
| **P0** | **0** |
| **P1** | **0** |
| **P2** | **0** |
| **P3** | **2** |

### P3 明细

| ID | 内容 | 来源 | 本轮处置 |
|---|---|---|---|
| **P3-1** | `ReaderSettingsSheetTest.kt` 内**既有**用例（非 D-2-B 新增）的页面切换仍是 `performClick()` + `waitForIdle()`，未走 `settlePageAnimation()`。例如 `:93`/`:101`/`:104`/`:120`/`:177` 等。它们不做作用域提示断言，因此不受 ST-6 影响；但同一文件内存在两种等待风格 | Qoder ST-6 的同类风险 | **本轮不动**：任务书限定「不重写 / 不删除 / 不合并测试」，且这些用例与 D-2-B 契约无关。登记为风格不一致 |
| **P3-2** | 新 DISPLAY 文案长度增至 45 字（原 43 字），320dp + 1.3 字号下换行行数可能 +1 | 文案修正的副作用 | 已有 `scopeNoticeRemainsVisibleAt320DpWithLargeFont` 覆盖可见性；实际排版观感需真机 |

---

## 9. 变异验证

从 `/tmp/d02bf1/Sheet.after.bak` 还原并复核哈希。

| 变异 | 内容 | 失败用例 | 结果 |
|---|---|---|---|
| **A** | DISPLAY 文案退回旧的「除沉浸模式、进度条、菜单隐藏和页眉页脚开关外…」 | `display under the book scope names exactly its four book level exceptions`、`display copy names no group title instead of a control` | **2 FAILED** ✅ |

**恢复证据**：`ReaderSettingsSheet.kt` 当前哈希 `fca16e3f91d3c441…` 与
`/tmp/d02bf1/Sheet.after.bak` **逐字节一致**；残留扫描：旧串 0 命中（§7）。

**为什么没有做第二个变异**：本轮第二个修复（动画等待）属**测试稳定性**改动，
其效果只在 instrumentation **执行期**（过渡窗口）显现，而本轮按任务书**未执行** instrumentation
（§10）。在 JVM 层无法构造「动画中途断言」的等价变异，因此不编造一条无效变异。
该修复的正确性由**结构检查**保证（§4.3：无 `Thread.sleep`、无 `onFirst()`、断言未放宽、所有切换路径均经 helper）。

---

## 10. 未覆盖项

| # | 项 | 状态 | 说明 |
|---:|---|---|---|
| U1 | 6 个 Compose 用例的**实际执行** | **已编译未执行** | 本轮任务书：「如果已连接真实手机，可以执行…但本轮不是必需条件；**不要安装 APK，不要改变设备状态**」。设备 `c49ac6cf` 虽在线，但安装 APK 属「改变设备状态」，且任务书另列「不安装 APK」——两条约束下我**不执行** instrumentation，如实记为已编译未执行 |
| U2 | `settlePageAnimation()` 是否真的消除 ST-6 的 flake | **未验证** | 需 instrumentation 执行确认；本轮只做了结构检查与编译验证 |
| U3 | 新文案在小屏 / 横屏 / 大字号下的实际排版（是否 +1 行、是否截断） | 未验证 | P3-2 |
| U4 | TalkBack 实际朗读（新文案含「显示安静阅读信息」等较长控件名） | 未验证 | 需真机 |
| U5 | 同文件内既有用例的等待风格不统一 | 登记 | P3-1，本轮按边界不动 |

---

## 11. 边界遵守声明

### 11.1 授权文件 diff 摘要

```bash
git diff --stat -- \
  android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderSettingsSheet.kt \
  android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderSettingsSheetTest.kt
```

```
 .../reader/sheets/ReaderSettingsSheetTest.kt       | 134 ++++++++++++++++
 .../ui/screen/reader/sheets/ReaderSettingsSheet.kt | 173 ++++++++++++++++++---
 2 files changed, 282 insertions(+), 25 deletions(-)
```

> 上述 `--stat` 是**相对仓库基线**（D-2-B + F1 累计），因为 D-2-B 的改动尚未 commit。
> 本轮 F1 的**增量**单独证明：产品文件**仅 1 行**（§2 的 `diff <(grep -v ...)` 为空），
> 测试文件 **+24 行**（`settlePageAnimation` + `goBackToRoot` 定义与调用点）。

新增测试文件（未跟踪）：

```bash
git diff --no-index --stat /dev/null \
  android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderSettingsScopeNoticeTest.kt
# -> 1 file changed, 293 insertions(+)
```

### 11.2 未修改的文件（哈希比对，与 D-2-B 轮记录一致）

| 文件 | 当前(前16) | 判定 |
|---|---|---|
| `ReaderSettingsRouter.kt` | `d6e6de3d8e0cc12e` | **未改** |
| `ReaderOverride.kt` | `69d55677fb48961e` | **未改** |
| `ReadingPresets.kt` | `1c655b9bff4f2aa6` | **未改** |
| `PerBookSettingsStore.kt` | `bef8c822c52c6616` | **未改** |
| `SettingsStore.kt` | `b9eb035ee41938a9` | **未改** |
| `ReaderPagingSettings.kt` | `55233e3dc35f0d49` | **未改** |
| `ReaderDisplaySettings.kt` | `0310f601504e7fc1` | **未改** |
| `ReaderEyeCareSettings.kt` | `66e1a04bfd423e68` | **未改** |
| `ReaderAdvancedSettings.kt` | `32815139b73edf14` | **未改** |
| `ReaderTypographySettings.kt` | `ab1200a246e07107` | **未改** |
| `ReaderSettingsCommon.kt` | `c2580fbb4d9d656c` | **未改** |
| `ReaderThemeSheet.kt` | `65be0e9e5c4c674c` | **未改** |
| `ReaderSheetScaffold.kt` | `792981f1e0f96a43` | **未改** |

`ui/components/**`、Gradle、Manifest、Room / DAO / DB 未触碰；
`ReaderSettingsScopeNoticeTest.kt` 的 279 行既有内容**未被重写**（仅追加 1 条用例 + 修正 1 处期望值）。

### 11.3 未顺手扩大的 P3（逐条确认未动）

| 任务书要求不动 | 结果 |
|---|---|
| 不重写 279 行 JVM 测试 | ✅ 未重写（仅改 1 处例外清单 + 新增 1 条） |
| 不新增源码字段提取守卫 | ✅ 未新增 |
| 不删除 / 合并测试 | ✅ 未删除、未合并（21 项 = 20 + 1 新增） |
| 不修改 GLOBAL 文案 | ✅ 未改（`本页修改将保存为全局默认；其他书已有的单书设置不会被清除` 原样） |
| 不修改注释中的历史归因 | ✅ 未改（D-2-B 的 KDoc 原样保留，仅新增 F1 说明） |
| 不修改 Zcode 原报告 | ✅ 未改（`agent-result-d02b-zcode.md` 哈希不变） |
| 不修改 Qoder 报告 | ✅ 未改（`agent-review-d02b-qoder.md` 哈希不变） |
| 不修改路由 / 存储 / `ReaderOverrideKey` | ✅ 未改 |
| 不修改公共组件 | ✅ 未改 |
| 不修改其他页面文件 | ✅ 未改 |
| 不处理 S08 / S09 或其他切片 | ✅ 未触碰 |

### 11.4 未执行的操作

未 commit / push / stage / stash / reset / checkout / clean / 全仓格式化；
**未安装 APK**；未操作手机（仅执行只读 `adb devices` 确认设备在线，未推送任何文件）；
未使用 MuMu 或任何模拟器；未修改用户真实书籍或测试书状态；
未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`；未领取下一任务。
工作区其他 Agent 与用户的既有修改全部保留。

### 11.5 索引与分支状态

```
HEAD                          = 5aed5939600e491843e3c44453921bd6273a233c
git diff --cached --name-only = 0
git stash list                = 0
branch                        = codex/recover-worktree-20260920
```

---

## 12. SEAM REQUEST

**无。**

两处修复均落在 3 个授权文件内：产品改动 1 行字符串、测试改动为新增辅助函数 + 期望值同步。
未触发任何需要越界修改的编译或行为问题。

---

## 13. 回传摘要

| 项 | 内容 |
|---|---|
| 结论 | **CANDIDATE PASS** |
| 修改的产品文件数 | **1**（`ReaderSettingsSheet.kt`，**仅 1 行**） |
| 修改/新增的测试文件数 | **2**（1 改 + 1 已有未跟踪文件追加） |
| 新 DISPLAY 文案 | `除沉浸模式、显示进度条、自动隐藏和显示安静阅读信息外，本页其他设置均为全局设置，对所有书籍生效` |
| 动画辅助函数 | `settlePageAnimation()`（`advanceTimeBy(250L)` + `waitForIdle()`），被 `openGroup()` / `goBackToRoot()` 复用；无 `Thread.sleep`、无 `onFirst()` |
| 命令 1（JVM 定向） | 退出码 **0**，`BUILD SUCCESSFUL in 56s`，2 类 / **37 项** / 0 失败 / 0 错误 / 0 跳过，XML mtime 01:08:01，0 警告 |
| 命令 2（androidTest 编译） | 退出码 **0**，`BUILD SUCCESSFUL in 11s`，0 警告 |
| 命令 3（主 Kotlin 编译） | 退出码 **0**，`BUILD SUCCESSFUL in 1m 39s`，1 条**越界既有**警告（`LibraryBrowserRoute.kt` deprecated 图标） |
| 旧文案残留 | 3 个授权文件中 `页眉页脚开关` **0 命中**、`除沉浸模式、进度条、菜单隐藏` **0 命中** |
| 变异验证 | 变异 A 打红 2 项，已恢复并复核哈希 |
| P0/P1/P2/P3 | **0 / 0 / 0 / 2** |
| SEAM REQUEST | **无** |
| 报告路径 | `docs/coordination/agent-results/agent-result-d02b-f1-zcode.md` |

**完成后停止，等待 Codex 核验。未领取下一任务。**
