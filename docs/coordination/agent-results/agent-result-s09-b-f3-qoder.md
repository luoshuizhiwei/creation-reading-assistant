# S09-B-F3 结果报告（窗口 C / Qoder）— 不可翻页热区的匿名可点击节点

- 任务：S09-B-F3｜上游：`agent-result-s09-b-f2-qoder.md`（F2）及其 P2「空 clickable 暴露匿名 enabled OnClick」
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（唯一工作仓库）
- 分支：`codex/recover-worktree-20260920`｜固定基点：`5aed5939600e491843e3c44453921bd6273a233c`
- 时间：2026-09-22 12:10–12:33（本机 +08:00）
- 结论：**触摸层与无障碍层已分离。不可用分区不再挂任何 clickable：触摸由 `pointerInput + detectTapGestures(onTap={})` 消费（保留 F2 的不穿透语义，且该消费行为已对 1.7.6 产物做字节码核对），语义改为"有名称 + disabled + 无 OnClick"的边界节点，文案按该分区实际映射的动作选择。三条门禁（JVM 三类 34 条 / 主 Kotlin / androidTest Kotlin）两轮均退出码 0、无失败重试。**

---

## 一、触摸语义与无障碍语义如何分离

F2 的做法是"用空 `clickable(onClick = {})` 消费触摸"，缺陷正如复核指出：clickable 同时会写 `OnClick` 语义，而这个分区在不可用时不画图标也没有描述，于是语义树里留下一个匿名、可激活、激活后什么都不做的节点。

F3 把两件事拆成两个互不依赖的机制，挂在同一个 Box 上：

| 层 | 可用分区 | 不可用分区 |
|---|---|---|
| 触摸层 | `Modifier.clickable { onTurn() }`（原样，未动） | `Modifier.pointerInput(Unit) { detectTapGestures(onTap = { }) }` —— 只消费、不分发 |
| 无障碍层 | clickable 自带的 `OnClick` + 图标 `contentDescription = "上一章"/"下一章"` | `Modifier.semantics { contentDescription = 边界文案; disabled() }` —— **没有 clickable，因此不可能有 OnClick** |
| 测试钩子 | `testTag(LEGACY_EPUB_ZONE_*)` | 同左（同一常量，两种状态都能定位到同一个生产节点） |

关键取舍：**不可用分区彻底不用 `clickable`**，而不是"clickable + `clearAndSetSemantics` 抹掉 OnClick"。理由是后者依赖"清除型语义能否覆盖同一节点上更早的 clickable 语义"这种修饰符顺序细节，而我无法在本轮执行 instrumentation 去验证它；不挂 clickable 则"没有 OnClick"是定义级的确定事实，不需要任何运行时前提。

代价是触摸消费改由 `detectTapGestures` 保证。为不把这条换成未验证的假设，我对项目实际使用的产物做了字节码核对：

```
androidx.compose.foundation:foundation-android:1.7.6 → classes.jar
TapGestureDetectorKt$detectTapGestures$2$1:
   83: invokestatic  TapGestureDetectorKt.awaitFirstDown$default(...)
  113: invokevirtual androidx.compose.ui.input.pointer.PointerInputChange.consume()   ← 按下即消费
  492: invokestatic  TapGestureDetectorKt.access$consumeUntilUp(...)
```

即 `detectTapGestures` 在 `awaitFirstDown` 之后立刻 `consume()`，与 clickable 走的是同一条消费路径；父层 `ReaderTapObservation.kt:98-99` 的 `if (down.isConsumed) return@awaitEachGesture` 因此成立，边界轻点不会变成"中央点击"。

同时按任务约束**没有恢复 `clickable(enabled = false)`**：它不消费触摸，会重新引入 F2 的穿透缺陷；这一点在报告第四节的断言设计里也被专门盯住（禁用态虽然同样"没有 OnClick"，但第 10 组的 `toggle == 0` 会立刻变红）。

改动落点（`PagedEpubContent.kt`，行号为结束态）：`:304-316`（两个边界文案常量 + 四个分区 testTag 常量）、`:319-352`（`Modifier.legacyEpubTapZone(available, boundaryDescription, tag, onTurn)`）、`:397-402`（按映射动作推导的 `leftBoundaryDescription` / `rightBoundaryDescription`）、`:408,416,432,440,450,466`（六个分区改带参调用）；中央区 Box 与 `LegacyEpubTapArbiter` 未改。新增 import：`detectTapGestures`、`pointerInput`、`testTag`、`semantics`、`contentDescription`、`disabled`。

途中一次自我修正：一开始还加了 `LEGACY_EPUB_ZONE_CENTER` 常量，但中央区既不需要边界文案、本轮也没有针对它的语义断言，留着就是无人使用的导出常量 —— 已删除并重跑三条门禁（见第五节的 `F3b-*` 批次）。

## 二、边界文案矩阵（standard / five-zone / left_hand / both_next）

文案取自两个常量（`internal const val LEGACY_EPUB_BOUNDARY_FIRST = "已到第一章"`、`..._LAST = "已到最后一章"`），由 `isLeftAdvance` / `isRightAdvance` 决定，**不看物理左右**；顶/底带在引擎与 legacy 语义里固定是回退/前进。

| preset | 分区 | 映射动作 | 可用时的语义 | 该动作不可用时的语义 |
|---|---|---|---|---|
| standard | 左 | prev | OnClick + 图标"上一章" | disabled + "已到第一章" |
| standard | 右 | next | OnClick + 图标"下一章" | disabled + "已到最后一章" |
| standard | 顶带 | prev | OnClick + 图标"上一章" | disabled + "已到第一章" |
| standard | 底带 | next | OnClick + 图标"下一章" | disabled + "已到最后一章" |
| left_hand | 左 | next | OnClick + 图标"下一章" | disabled + **"已到最后一章"** |
| left_hand | 右 | prev | OnClick + 图标"上一章" | disabled + **"已到第一章"** |
| both_next | 左 | next | "下一章" | "已到最后一章" |
| both_next | 右 | next | "下一章" | "已到最后一章" |

## 三、每个边界的 prev / next / toggle 次数（触摸层）

坐标仍由 `ReaderTapZoneGeometry` 从实测像素换算，点击 `performTouchInput { click(Offset) }`，渲染对象仍是生产 `LegacyEpubTapArbiter`（父 Box + 子 overlay）。

| 结构 / 入参 | 点击 | prev | next | toggle |
|---|---|---|---|---|
| three-zone standard, `canPrev=false` | left | 0 | 0 | **0** |
| three-zone standard, `canNext=false` | right | 0 | 0 | **0** |
| five-zone standard, `canPrev=false` | top（再 left） | 0 | 0 | **0** |
| five-zone standard, `canNext=false` | bottom（再 right） | 0 | 0 | **0** |
| three-zone left_hand, `canNext=false` | left（映射到 next） | 0 | 0 | **0** |
| three-zone standard 全可用 | left → center → right | 1 | 1 | **1**（且 `total==3`） |
| five-zone standard 全可用 | top → bottom → center | 1 | 1 | **1**（且 `total==3`） |

（"边界必须全 0"的三条 F2 用例与"中央只一次"的用例本轮原样保留，见第四节；新语义不改变触摸结果，这一点由这几条继续守。）

## 四、semantics 断言（contentDescription / disabled / OnClick）

新增第 11 组 6 条用例，全部走 `assertBoundarySemantics(zoneTag, expectedDescription)`：

```kotlin
private fun assertBoundarySemantics(zoneTag: String, expectedDescription: String) {
    val node = composeRule.onNodeWithTag(zoneTag, useUnmergedTree = true)
    node.assertContentDescriptionEquals(expectedDescription)   // ① 有名称
    node.assertIsNotEnabled()                                   // ② disabled
    assertFalse(
        "边界分区 $zoneTag 不得暴露 OnClick（否则读屏会撞上'可激活但什么都不做'的节点）",
        node.fetchSemanticsNode().config.contains(SemanticsActions.OnClick),   // ③ 无 OnClick
    )
}
```

| 用例 | 断言 |
|---|---|
| `legacyEpubFirstChapterLeftZoneIsNamedDisabledBoundary` | 左区：desc="已到第一章"、disabled、无 OnClick；点击后 prev+next+toggle=0 |
| `legacyEpubLastChapterRightZoneIsNamedDisabledBoundary` | 右区：desc="已到最后一章"、disabled、无 OnClick；点击后全 0 |
| `legacyEpubFiveZoneFirstChapterTopBandIsNamedDisabledBoundary` | 五分区顶带：desc="已到第一章"、disabled、无 OnClick；点击后全 0 |
| `legacyEpubFiveZoneLastChapterBottomBandIsNamedDisabledBoundary` | 五分区底带：desc="已到最后一章"、disabled、无 OnClick；点击后全 0 |
| `legacyEpubBoundaryLabelFollowsTheMappedActionNotThePhysicalSide` | left_hand + `canNext=false`：**左区**（映射 next）读作"已到最后一章"、disabled、无 OnClick；右区仍有 OnClick 且图标 desc="上一章"；点左区仍全 0 |
| `legacyEpubAvailableZonesKeepClickActionAndTurnDescription` | 全可用：左/右区 `assertHasClickAction()`、"上一章"/"下一章"图标描述分别落在左/右区内、**语义树里不存在任何"已到第…章"边界节点**；点击后 prev=1、next=1、toggle=0 |

要求逐条对应：① standard 三分区第一章左区 ✓ / 最后一章右区 ✓；② five-zone 顶带 ✓ 底带 ✓；③ left-hand 按实际动作选文案 ✓；④ 边界点击 prev/next/toggle 全 0 ✓；⑤ 中央仍 toggle=1 ✓（F2 用例 `legacyEpubCenterTapTogglesControlsExactlyOnceThroughBothLayers` 保留）；⑥ 可用边缘保留正确 contentDescription 与点击行为 ✓；⑦ `TapZonePreviewCard` 的"上一页/下一页"语义未回退 —— 本轮**未修改该文件**（哈希逐字节同 F1，mtime 停在 03:42），其用例 `previewCardFiveZoneBandsExposeTurnSemanticsToTalkBack` 原样保留。

**可证伪性**：把不可用分支改回 `clickable(onClick = {})` → ③ 的 OnClick 断言红；改回 `clickable(enabled = false)` → ③ 仍绿但第三节的 `toggle == 0` 红（穿透）；把文案按物理左右硬编码 → left_hand 用例红；给可用分区加边界文案 → "不存在边界节点"的反向断言红。测试全程渲染生产 `LegacyEpubTapArbiter`，节点标签也来自生产常量，测试内没有自造语义节点。

androidTest 用例数：F2 结束 31 条 → 本轮 **37 条**（新增第 11 组 6 条）。

## 五、命令与结果（两轮均一次通过，无失败重试）

均在 `android/` 下，经 `powershell.exe -NoProfile -Command` 启动（`cmd.exe /c` 在 MSYS 下假退 0），退出码取 `$LASTEXITCODE`，日志 `Out-File -Encoding utf8 -Width 5000`。

| 命令 | 退出码 | 结果 | 日志 |
|---|---|---|---|
| `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.reader.pager.ReaderTapActionTest" --tests "com.creationreadingassistant.feature.reader.pager.ReaderTapActionPresetTest" --tests "com.creationreadingassistant.feature.reader.pager.ReaderTapZoneGeometryTest" --rerun` | **0** | `BUILD SUCCESSFUL in 32s`，**34 tests / 0 failures / 0 errors / 0 skipped** | `F3b-tests.log` |
| `.\gradlew.bat --console=plain :app:compileDebugKotlin --rerun` | **0** | `BUILD SUCCESSFUL in 52s` | `F3b-main-compile.log` |
| `.\gradlew.bat --console=plain :app:compileDebugAndroidTestKotlin --rerun` | **0** | `BUILD SUCCESSFUL in 11s` | `F3b-androidtest-compile.log` |
| `git diff --check -- <2 个受跟踪授权文件>` | **0** | 无输出 | — |

上面这一批是**删除多余的 `LEGACY_EPUB_ZONE_CENTER` 之后重跑的**，对应第六节的最终哈希。此前一轮（改动等价、仅多那个未使用常量）也全部通过、无重试、无失败：`F3-tests-1.log` `BUILD SUCCESSFUL in 57s`、`F3-main-compile-1.log` `1m 12s`、`F3-androidtest-compile-1.log` `11s`，退出码均为 0。

JVM 三类计数（`timestamp="2026-09-22T04:36:06"` UTC = 12:36:06 +08:00，快照副本在 gitignore 的 `android/app/build/tmp/s09b-f3-evidence/`，避免上一轮"共享 `test-results/` 被并行窗口整体重写"导致证据丢失）：

| 类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `ReaderTapActionTest` | 12 | 0 | 0 | 0 |
| `ReaderTapActionPresetTest` | 9 | 0 | 0 | 0 |
| `ReaderTapZoneGeometryTest` | 13 | 0 | 0 | 0 |
| **合计** | **34** | **0** | **0** | **0** |

`ReaderTapZoneGeometryTest` 的三条源码守卫本轮真跑通过（几何仍是唯一事实源；`PagedEpubContent.kt` / `TapZonePreviewCard.kt` 引用 `ReaderTapZoneGeometry` 且无退役比例字面量）。未运行全量 JVM、Lint、APK、ADB、真机或模拟器。

## 六、文件范围与 SHA-256

| 文件（仓库根相对） | 本轮 | 开始前（= F2 结束） | 结束后 | 行数 |
|---|---|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt` | 改 | `3dcd720d58db504b653bb3bcfa913de1d049f18958ea4968c421682ed353221c` | `f5d467dd518c59a7bacaa1b33fed206064e33bb5b0f15e52c593a686715e8e2c` | 614 |
| `android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/ReaderScreenTest.kt` | 改 | `23d1e40f9412a7c201f98f31e63e86decaba54a6b35d18b2253373dc3ac5127c` | `3b1c5d20c572a84087f5fbd6a1993ab75900faa90b688ec86d04ce9a1ec20c33` | 836（37 条 `@Test`） |
| `…/ui/screen/reader/sheets/TapZonePreviewCard.kt` | **未改** | `a71d857b2ce991ee9b4866bd5cea28eac58833f95a6595fc2f2d80d8f89769bc` | 同左（mtime 03:42） | — |
| `…/feature/reader/pager/ReaderTapAction.kt` | **未改** | `7f1ed408e3f24daf360259fee50470d0238dd21a26d08721dfbb00a4a9ce2db4` | 同左（mtime 02:44） | — |
| `…/ui/screen/reader/ReaderTapObservation.kt`（禁改） | **未改** | — | `695a31f46c985239…`（mtime **09-15 10:44**，本轮及之前均未触碰） | — |
| `test/…/pager/ReaderTapZoneGeometryTest.kt` 等三个 JVM 测试 | **未改** | `f3c098a5…` / `edddb6d3…` / `f7a83478…` | 同左（mtime 02:42 / 02:09 / 02:33） | — |

## 七、开始前 / 结束后的 git status --short

**开始前**：授权路径状态与 F2 报告第八节一致 —— `ReaderScreenTest.kt`、`PagedEpubContent.kt`、`ReaderTapAction.kt`、`ReaderTapActionTest.kt` 为 `M`；`TapZonePreviewCard.kt`、`ReaderTapActionPresetTest.kt`、`ReaderTapZoneGeometryTest.kt` 为 `??`；`pager/` 测试目录下另有他人的 2 个 `M` 与 6 个 `??`。

**结束后**（`git status --short -- <授权路径>`）：

```
 M android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/ReaderScreenTest.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt
?? android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/TapZonePreviewCard.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionPresetTest.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapZoneGeometryTest.kt
```

状态类别与开始前完全一致：本轮只写入内容，没有新增/删除任何版本库文件（除本报告），也没有把任何文件从 `??` 变成 `M`（反之亦然）。本轮为版本库新增的文件只有 `docs/coordination/agent-results/agent-result-s09-b-f3-qoder.md`；`android/F3-*.log` 被 `android/.gitignore:17` 的 `*.log` 忽略，XML 快照放在 gitignore 的 `app/build/tmp/s09b-f3-evidence/`，不污染 status。

## 八、遗留与下一步

1. **12 条 Compose 用例仍只有编译级证据**（第 9、10、11 组）。带设备窗口必须验证的四件事：① 边界分区触摸确实被 `detectTapGestures` 消费（`toggle == 0`）；② 中央 `toggle == 1` 不双触发；③ `assertContentDescriptionEquals / assertIsNotEnabled / 无 OnClick` 三条语义在真机语义树上成立；④ 坐标换算与容差在真机密度下通过。第一轮的字节码核对只能支撑"应当如此"，不能替代运行。
2. **本轮未解决的同源问题**（上一轮 SEAM 仍未批）：`ReaderTapObservation.kt:51` 的 `isCenterTapZone` 仍是第四份 `1/3` 几何副本；EPUB 的 preset→方向映射与引擎 `when (preset)` 仍是两份实现；`"five-zone"` / `"three-zone"` 模式字符串仍各处硬编码。本轮这三处均未触碰（禁改清单内）。
3. **建议补的 JVM 侧守卫**（需要新增测试文件，超出本轮允许清单，未做）：断言 `LegacyEpubTapZoneOverlay` 内不再出现 `clickable(enabled =`、且六个分区都带 `LEGACY_EPUB_ZONE_*` 标签 —— 这样"有人为了消音把边界改回 disabled clickable"在 JVM 阶段就会被抓，而不必等设备。

## 九、声明

- 未进入、未修改原仓库 `D:\develop\Code\Codex\creation-reading-assistant`；全部操作在 recovery 仓库内。
- 未 `commit` / `push` / `stage` / `reset` / `checkout` / `clean` / `stash`，未全仓格式化，未清理他人未提交改动，未杀任何进程。
- 仅改动 2 个允许文件（`PagedEpubContent.kt`、`ReaderScreenTest.kt`）+ 新增本报告。`ReaderTapObservation.kt`、`ReaderTapAction.kt`、`TapZonePreviewCard.kt`、其他阅读分支、时长展示文件、DurationPrefs、`android/scripts/**`、协调看板、handoff、Obsidian 均未修改（他人文件仅只读用于定位与字节码核对；Foundation 产物只在临时临时目录解包检查，未写回仓库）。
- 未修改页码、硬件键、页眉页脚、全书进度、滚动阅读器；未恢复 `clickable(enabled = false)`；未回退 F1 的 TalkBack 语义与 F2 的边界触摸消费。
- 未运行全量 JVM、Lint、APK 构建、ADB、真机或模拟器。

**未领取下一任务**：是。S09-B-F3 到此停止，等待 Codex 复核。
