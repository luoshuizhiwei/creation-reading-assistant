# S09-B-F2 结果报告（窗口 C / Qoder）— legacy EPUB 边界热区点击穿透父层并切换控制栏

- 任务：S09-B-F2｜上游：`agent-result-s09-b-qoder.md`（S09-B）、`agent-result-s09-b-f1-qoder.md`（F1）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（唯一工作仓库）
- 分支：`codex/recover-worktree-20260920`｜固定基点：`5aed5939600e491843e3c44453921bd6273a233c`
- 时间：2026-09-22 11:00–11:26（本机 +08:00）
- 结论：**缺陷成立并已修复。legacy EPUB 的整层点击仲裁抽成 `LegacyEpubTapArbiter`（父级中央兜底 Box + 子层分区 overlay），生产与 androidTest 渲染同一个函数；不可用的左/右/顶/底分区改为"照常消费但不分发"的静默 clickable，因此不再漏点给父层。三条门禁本轮全部真跑并全绿（JVM 34 条 / 主 Kotlin / androidTest Kotlin），退出码 0。**
- 仍未闭环：这些 Compose 用例依旧只有编译级证据，**未在设备执行**（本轮禁止操作手机）。修复的机制论证见第四节，经验证据待带设备窗口。

---

## 一、缺陷本体

`ReaderTapObservation.kt:96-105` 的父级观察器（4 个阅读分支共用，本轮禁止修改）契约是"不消费、只在子项都没认领时触发"：

```kotlin
awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (down.isConsumed) return@awaitEachGesture   // ← 子项消费过就跳过
    ...
    currentOnCenterTap()
}
```

而 F1 的 overlay 里，不可用的翻页分区用的是 `clickable(enabled = canPrev / leftCan / …)`。**禁用的 clickable 不消费手势**，所以：

- 第一页（`canPrev = false`）点左区或五分区顶部带 → 子层不认领 → 父层 `onCenterTap` → `onToggleControls()` → 控制栏被切换；
- 最后一页（`canNext = false`）点右区或底部带 → 同上。

用户看到的是"翻不动就算了，还把我正在看的菜单收起来/弹出来"。F1 的边界用例单独渲染 overlay（没有父层），所以 `toggle == 0` 是"父层根本不存在"给的假绿 —— 这条结论确实不成立，Codex 的判断正确。

## 二、修复前后的父子结构与事件流

### 修复前

```
Box( fillMaxSize + readerCenterTapToToggle(onToggleControls) )   ← 父：兜底唤出
 ├─ Box( 正文内容 PagedChapterContent )
 └─ LegacyEpubTapZoneOverlay                                     ← 子：分区热区
      └─ Box( clickable(enabled = canPrev) { onPrev() } )  等 6 个分区
```

| 场景 | 子层是否认领 | 事件落点 | 修复前结果 |
|---|---|---|---|
| 可用左区（standard） | 是（clickable 消费） | 子 | `onPrev` 1 次；父层看到 down 已消费 → 跳过 |
| 中央区 | 是 | 子 | `onToggleControls` 1 次 |
| **不可用左区 / 顶带** | **否（enabled=false 不消费）** | **穿透到父** | **`onToggleControls` 被误触发** ← 缺陷 |
| **不可用右区 / 底带** | **否** | **穿透到父** | **同上** ← 缺陷 |

### 修复后

```
PagedEpubView（正文与仲裁层并列，最外层 Box 不再挂观察器）
 └─ LegacyEpubTapArbiter                         ← 生产唯一入口（PagedEpubView 只调它）
     └─ Box( fillMaxSize + readerCenterTapToToggle(onToggleControls) )   ← 父：兜底不变
         └─ LegacyEpubTapZoneOverlay             ← 子：分区热区
             └─ 6 个分区改用 Modifier.legacyEpubTapZone(available) { … }
```

| 场景 | 子层是否认领 | 修复后结果 |
|---|---|---|
| 可用左/右/顶/底 | 是（`clickable { onTurn() }`，与修复前完全一致） | 只回调 `onPrev` / `onNext` 1 次，无 toggle |
| **不可用左/右/顶/底** | **是 —— `clickable(interactionSource, indication = null, onClick = {})`：启用、照常消费 down/up，但不分发任何动作** | **prev / next / toggle 全 0（真正的 no-op）** |
| 中央区 | 是（未改） | toggle 恰好 1 次 |

对应改动（`PagedEpubContent.kt`，行号为本轮结束时）：

- `:297-317` 新增 `@Composable private fun Modifier.legacyEpubTapZone(available, onTurn)`（定义在 `:306`）；
- `:250-295` 新增 `internal fun LegacyEpubTapArbiter(...)`（KDoc 起 `:250`，定义 `:268`，观察器挂在 `:282`），把 `readerCenterTapToToggle` 从 `PagedEpubView` 最外层 Box 移进来（该 Box 现在只剩 `Box(Modifier.fillMaxSize())`，`:185`）；
- `:365`（顶带）、`:372` 与 `:403`（左区 two 处）、`:387` 与 `:418`（右区两处）、`:394`（底带）共 6 个翻页分区从 `clickable(enabled = X) { … }` 换成 `legacyEpubTapZone(X) { … }`；中央区 `clickable(interactionSource, indication = null) { onToggleControls() }`（`:379-384`、`:410-415`）未动；
- 全文件已无任何 `clickable(enabled = …)` 活代码（`:256`、`:300` 两处只是 KDoc 里解释缺陷的文字）。

**为什么把观察器移到 overlay 包装层是等价的**：overlay 用 `Row/Column(fillMaxSize())` + 权重把三区/五区铺满整页，无任何空隙，所以父层观察器今天实际只可能收到两类事件 —— 被中央区认领前的漏点、以及被禁用分区漏上来的点。前者本就被中央 Box 自己消费；后者正是本缺陷。移动后父层仍然覆盖 overlay 全体，正文 `PagedChapterContent` 与仲裁层并列且被 overlay 遮在上层，与修复前一致。

## 三、边界 no-op 为什么不会再穿透

1. 不可用的分区现在挂的是**启用状态**的 `clickable`（`onClick = {}`）。Compose 的 clickable 在按下时会消费手势，父层 `awaitEachGesture` 里的 `down.isConsumed` 于是为真，直接 `return` —— 走的是与"可用分区能正常翻页而不切换菜单"完全同一条、且本 App 生产中已在跑的机制，不是我新发明的推断。
2. 我没有依赖"父层自己判断坐标是否在中央" —— 观察器在禁改文件里，且拿不到坐标；把判断做在子层（谁被点谁负责消费）是唯一不越界的落点。
3. 双触发也被同一机制挡住：中央区只有子层 clickable 生效，父层因 down 已消费而跳过 —— 这正是新增 `legacyEpubCenterTapTogglesControlsExactlyOnceThroughBothLayers` 要盯的数字（若真机上出现 `toggle == 2`，说明这条前提在 Compose 版本上并不成立，测试会直接变红，而不是留下一个"看起来通过"的结论）。
4. **可证伪性（任务第 7 项）**：测试渲染的是 `LegacyEpubTapArbiter`（父 + 子），因此
   - 把 `legacyEpubTapZone` 的不可用分支改回 `clickable(enabled = false)` → 4 条边界用例的 `toggle` 从 0 变 1 → 红；
   - 把整层观察器删掉/挪走 → 中央用例 `toggle` 从 1 变 0 → 红；
   - 父子两层都放行 → 中央用例 `toggle` 从 1 变 2 → 红；
   - 六个分区里任何一个接线接反 → 对应计数落到错误的计数器 → 红。

## 四、坐标点击与回调次数对照（androidTest 第 10 组）

渲染：`renderEpubArbiter(tapZoneMode, preset, calls, canPrev, canNext)` → `LegacyEpubTapArbiter`，回调写进 `TapZoneCalls{prev,next,toggle}`。坐标由 `ReaderTapZoneGeometry` 从实测像素换算（`left = centerLeftEdge(w)/2`、`center = (centerLeftEdge+centerRightEdge)/2`、`right = (centerRightEdge+w)/2`、`top = topBandBottomEdge(h)/2`、`bottom = (bottomBandTopEdge(h)+h)/2`，纵向统一 `h/2`；仲裁层根节点 `fillMaxSize` 铺满 root，容器相对坐标与 root 绝对坐标重合）。点击方式 `performTouchInput { click(Offset) }`。

| # | 用例 | 结构/入参 | 点击 | 断言（prev / next / toggle） |
|---|---|---|---|---|
| 1 | `legacyEpubArbiterThreeZoneStandardRoutesEachZoneToItsCallback` | three-zone, standard, 可用 | left → center → right | 1/0/0 → 1/0/1 → 1/1/1，且 `total == 3` |
| 2 | `legacyEpubArbiterFiveZoneAddsWorkingTopAndBottomBands` | five-zone, standard, 可用 | top, bottom, center, left, right | prev 2、next 2、toggle 1 |
| 3 | `legacyEpubArbiterLeftHandPresetSwapsTheSideZones` | three-zone, left_hand | left, right | next 1 / prev 1（左右互换），toggle 0 |
| 4 | `legacyEpubThreeZoneFirstChapterLeftZoneIsSilentNoOp` | three-zone, **canPrev=false** | left | **0 / 0 / 0** |
| 5 | `legacyEpubThreeZoneLastChapterRightZoneIsSilentNoOp` | three-zone, **canNext=false** | right | **0 / 0 / 0** |
| 6 | `legacyEpubFiveZoneFirstChapterTopBandIsSilentNoOp` | five-zone, **canPrev=false** | top，再 left | 两次点击后 **prev+next+toggle 仍为 0** |
| 7 | `legacyEpubFiveZoneLastChapterBottomBandIsSilentNoOp` | five-zone, **canNext=false** | bottom，再 right | 两次点击后 **prev+next+toggle 仍为 0** |
| 8 | `legacyEpubCenterTapTogglesControlsExactlyOnceThroughBothLayers` | three-zone, 可用 | center | toggle **恰好 1**，prev+next 0 |
| 9 | `legacyEpubAvailableEdgeTurnsExactlyOnceWithoutToggling` | five-zone, 可用 | top, bottom, center | 1 / 1 / 1，`total == 3`（反向对照，排除"整层没接线"式假绿） |

任务第 6 项要求的 6 类逐条落点：`canGoPrev=false` 左区 → 用例 4（three-zone）+ 6（five-zone 中带左）；`canGoNext=false` 右区 → 用例 5 + 7；五分区顶带不可用 → 用例 6；底带不可用 → 用例 7；中央只 toggle 一次 → 用例 8（three-zone）与 2（five-zone）；可用边缘只翻一次 → 用例 9 与 1。three-zone 与五分区都覆盖边界 ✓。

androidTest 用例总数：F1 结束时 27 条 → 本轮 **31 条**（第 10 组由 5 条扩到 9 条：2 条合并式边界用例拆成 4 条精确边界 + 新增中央单次 + 可用边缘单次）。第 9 组预览卡 6 条与全部既有用例未改动语义（只把渲染目标从 overlay 换成 arbiter、重命名 `legacyEpubOverlay*` → `legacyEpubArbiter*`、`renderEpubOverlay` → `renderEpubArbiter`、tag 常量 → `EPUB_TAP_ROOT_TAG`）。

## 五、任务前四节"已通过项"是否被保留

| 已通过项 | 状态 |
|---|---|
| 五分区顶/底带的"上一页/下一页" TalkBack 语义 | **保留**。`TapZonePreviewCard.kt` 本轮零改动（SHA-256 与 F1 结束时逐字节相同，见第七节） |
| androidTest 使用生产组件 | 保留并加强：从"生产 overlay"升级为"生产整层仲裁 `LegacyEpubTapArbiter`" |
| 三分区/五分区/left-hand/canPrev/canNext 坐标点击测试 | 保留（用例 1-3 原样，边界用例拆细） |
| 主 Kotlin 与 androidTest Kotlin 编译 | 保留，本轮各自重跑通过 |
| `ReaderTapZoneGeometry` 唯一比例事实源 | 保留。本轮新增代码零比例字面量；`ReaderTapAction.kt` 与三个 JVM 测试文件逐字节未变，其 JVM 守卫本轮**真跑通过**（13 条，含三条源码守卫） |

## 六、命令与结果

均在 `android/` 下，经 `powershell.exe -NoProfile -Command` 启动（`cmd.exe /c` 在 MSYS 下会假退 0），退出码取 `$LASTEXITCODE`，日志 `Out-File -Encoding utf8 -Width 5000`（默认 120 列会折断 Gradle 长错误路径）。

| 命令 | 退出码 | 结果 | 日志 |
|---|---|---|---|
| `:app:testDebugUnitTest --tests …ReaderTapActionTest --tests …ReaderTapActionPresetTest --tests …ReaderTapZoneGeometryTest --rerun` | 1 | `BUILD FAILED in 22s` — `java.io.IOException: Unable to delete directory 'app\build\test-results\testDebugUnitTest'`（并行窗口同一构建目录并发跑测试，Windows 文件占用） | `F2-tests-1.log` |
| 同上（间隔 20s 重试） | **0** | 34 条全绿（XML 见下）；但这份日志随后被我自己的一次误覆盖重跑冲掉（见"取证事故"） | — |
| 同上 | 1 | `BUILD FAILED in 9s` — `Failed to load cache entry … for task ':app:compileDebugKotlin': Unable to delete directory`（build cache 读写竞争） | `F2-tests-2.log` |
| 同上 | **0** | `BUILD SUCCESSFUL in 16s` | `F2-tests-3.log` |
| 同上（并在同一命令内立即快照 XML） | **0** | `BUILD SUCCESSFUL in 25s`，**34 tests / 0 failures / 0 errors / 0 skipped** | `F2-tests-4.log` |
| `:app:compileDebugKotlin --rerun` | **0** | `BUILD SUCCESSFUL in 1m 24s` | `F2-main-compile.log` |
| `:app:compileDebugAndroidTestKotlin --rerun` | **0** | `BUILD SUCCESSFUL in 16s` | `F2-androidtest-compile.log` |
| `git diff --check -- <2 个受跟踪授权文件>` | **0** | 无输出 | — |

最终 JVM 批次（`F2-tests-4.log`，同一批 XML 快照，时间戳 `2026-09-22T03:24:53` UTC = 11:24:53 +08:00）：

| 类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `…pager.ReaderTapActionTest` | 12 | 0 | 0 | 0 |
| `…pager.ReaderTapActionPresetTest` | 9 | 0 | 0 | 0 |
| `…pager.ReaderTapZoneGeometryTest` | 13 | 0 | 0 | 0 |
| **合计** | **34** | **0** | **0** | **0** |

快照副本：`android/app/build/tmp/s09b-f2-evidence/TEST-*.xml`（在 `build/` 内，被 gitignore，不会污染 `git status`）。F1 报告里"JVM 门禁 BLOCKED"的结论已被本轮解除：并行窗口的 `formatArchiveDuration` / `LocalDurationUnit` 中间态在 11:13 之前已经收敛，`compileDebugUnitTestKotlin` 可过。

**取证事故（我的操作失误，如实记录）**：为了抓"立即快照"，我把两次运行写进了同一个日志文件名 `F2-tests-2.log`，第一次（退出码 0、34 条全绿）的日志被第二次失败的运行覆盖。因此"第一次绿"只有退出码回显，没有日志留存；**最终对外的绿证据统一以 `F2-tests-4.log` + 其 XML 快照为准**（同批、可复核）。教训：重试循环必须给每次尝试唯一文件名。

**并发环境注意**：本轮期间并行窗口在同一个 `android/app/build/` 上跑自己的测试，11:23:14 它把 `test-results/testDebugUnitTest/` 目录整体重写成了它自己的两个结果文件（`YearBillHeadlineTest`、`TimeUtilsTest`），我的 pager XML 一度消失 —— 这就是必须"跑完立刻快照"的原因；也说明在共享工作区里"看一眼 build 目录的 XML"并不足以证明某一轮的归属。

## 七、文件范围与 SHA-256

| 文件（相对仓库根） | 本轮 | 本轮开始前 | 本轮结束后 |
|---|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt` | 改（566 行） | `b0f0c48dd67a9f536c239dbfab086144326f43f1f17d7ff2cac551bfd4d71d25` | `3dcd720d58db504b653bb3bcfa913de1d049f18958ea4968c421682ed353221c` |
| `android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/ReaderScreenTest.kt` | 改（714 行，31 条 `@Test`） | `03f2a97b34030dba8f2e612aaf6b9c05c4faa27828710908e349d690e0eec405` | `23d1e40f9412a7c201f98f31e63e86decaba54a6b35d18b2253373dc3ac5127c` |
| `…/ui/screen/reader/sheets/TapZonePreviewCard.kt` | **未改** | `a71d857b2ce991ee9b4866bd5cea28eac58833f95a6595fc2f2d80d8f89769bc` | 同左（逐字节未变） |
| `…/feature/reader/pager/ReaderTapAction.kt` | **未改** | `7f1ed408e3f24daf360259fee50470d0238dd21a26d08721dfbb00a4a9ce2db4` | 同左 |
| `test/…/pager/ReaderTapZoneGeometryTest.kt` | **未改** | `f3c098a59811e2a6bda91f3c5d7accc34339b5eb4ffb40e2699ec08d23f7d300` | 同左 |
| `test/…/pager/ReaderTapActionTest.kt` | **未改** | `edddb6d3ed139956c2b88c90c89fc913b7cdf47ba3f84efeb965001f8aaf2f22` | 同左 |
| `test/…/pager/ReaderTapActionPresetTest.kt` | **未改** | `f7a83478b0dd46e46e69cf3db9b98b15b1fb15df024f4c82c5025d7e2fb594d6` | 同左 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderTapObservation.kt` | **未改**（禁改；只读过 `:96-105` 用于定位父层契约） | — | — |

"开始前"哈希 = F1 报告附录 A 记录的 F1 结束值，即本轮起点；改动仅限前两个文件 + 本报告。

## 八、开始前 / 结束后的 git status --short

**开始前**：与 F1 报告第 4.1 节的"结束后"完全一致 —— 授权路径里 4 个 `M`（`ReaderScreenTest.kt`、`ReaderTapAction.kt`、`PagedEpubContent.kt`、`ReaderTapActionTest.kt`）+ 3 个 `??`（`TapZonePreviewCard.kt`、`ReaderTapActionPresetTest.kt`、`ReaderTapZoneGeometryTest.kt`），另有他人 `pager/` 测试的 2 个 `M` 与 6 个 `??`。

**结束后**（限定授权路径）：

```
 M android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/ReaderScreenTest.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt
 M android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/ReaderTapAction.kt      ← 他人未提交差异（哈希未变，见第七节）
 M android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionTest.kt   ← 同上
 M …（pager/ 目录下他人的 PageSelectionHandleDragTest.kt、ReaderHardwareKeysTest.kt 等，本轮未触碰）
?? android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/TapZonePreviewCard.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionPresetTest.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapZoneGeometryTest.kt
?? …（pager/ 目录下他人新增的 CustomTip*/ProgrammaticJump*/WholeBookPageModelTest.kt 等，本轮未触碰）
```

状态类别与开始前相同（我改的两个文件本来就是 `M`；没有新增/删除版本库文件）。本轮为版本库新增的文件只有这一份报告；`android/*.log` 与 `android/app/build/**` 均被 gitignore，我一度创建的 `android/F2-evidence/`（未跟踪、会污染 status）已删除并把副本移入 `app/build/tmp/s09b-f2-evidence/`。

## 九、遗留与下一步

1. **必须带设备验的东西（本轮拿不到）**：`android/app/src/androidTest/.../ReaderScreenTest.kt` 第 9、10 组共 15 条 Compose 用例（预览卡 6 + 仲裁层 9）。特别是：不可用分区 `toggle == 0`（修复生效）、中央 `toggle == 1`（不双触发）、坐标约定（`click(Offset)` 相对/绝对）与比例容差 0.05。
2. **本轮修复的一个无障碍副作用（P3，需要设备窗口一并判断）**：不可用的翻页分区由"disabled clickable"变成"启用但空的 clickable"，语义上从一个"变暗不可用"的节点变成一个"可点但什么都不做"的匿名节点（该分区在不可用时不画图标，因此没有标签）。可选处理：改为只挂 `pointerInput { detectTapGestures(onTap = {}) }`（不产生任何语义节点，但依赖 detectTapGestures 消费 down 的行为，未经本 App 生产验证），或补 `onClickLabel = "已到边界"` 让读屏有明确提示。我选择当前写法是因为它复用了本 App 已在生产中生效的消费机制。
3. **仍未收口的同源问题（上一轮 SEAM 未批）**：`ui/screen/reader/ReaderTapObservation.kt:51` 的 `isCenterTapZone` 仍自带 `width / 3f` / `width * 2f / 3f` 第四份几何副本（本轮禁改该文件）；EPUB 的 preset→方向映射与引擎 `when (preset)` 仍是两份实现；`"five-zone"` / `"three-zone"` 模式字符串仍各处硬编码。
4. **建议下一轮补一条 JVM 结构守卫**：断言 `PagedEpubView` 调用 `LegacyEpubTapArbiter`、且 overlay 内不得再出现 `clickable(enabled =` —— 这样"有人把边界分区改回 enabled=false"就能在 JVM 阶段被抓住，而不必等设备。当前靠 grep 人工确认（本轮结果：活代码 0 处）。

## 十、声明

- 未进入、未修改原仓库 `D:\develop\Code\Codex\creation-reading-assistant`。
- 未 `commit` / `push` / `stage` / `reset` / `checkout` / `clean` / `stash`，未全仓格式化，未清理他人未提交改动。
- 只改动 2 个允许文件（`PagedEpubContent.kt`、`ReaderScreenTest.kt`）+ 新增本报告；`ReaderTapObservation.kt`、`ReaderTapAction.kt`、共享几何、其他阅读分支、时长展示文件、DurationPrefs、`android/scripts/**`、协调看板、handoff、Obsidian 均未修改（他人文件仅只读用于定位父层契约与阻断原因）。
- 未修改页码、硬件键、页眉页脚、全书进度、滚动阅读器；未回退 F1 的 TalkBack 语义。
- 未运行全量 JVM、Lint、APK 构建、ADB 或真机测试；未操作手机。
- 未杀任何进程；Gradle 竞争与缓存锁失败均通过等待重试处理。

**未领取下一任务**：是。S09-B-F2 到此停止，等待 Codex 复核。
