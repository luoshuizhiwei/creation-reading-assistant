# Android S06 阅读核心可靠性切片审查

> 日期：2026-09-20  
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`  
> 审查对象：恢复分支 `codex/recover-worktree-20260920` 的未提交工作区差异  
> 比较命令：`git diff origin/main -- <S06 paths>`（未提交改动不能使用只比较提交的三点式）  
> 性质：只读审查；未修改产品源码，未 stage、commit 或 push

## 规格来源

- `docs/qa/2026-09/2026-09-18-android-device-verification.md` 的翻页、夜间启动、目录浏览、真实章号和统一滑杆记录；
- `docs/research/2026-09-15-reader-page-turn-fluency-vs-md3.md` 的翻页性能基线与建议；
- `docs/handoff/current.md` 与 `AGENTS.md` 的原生 Android、共享工作区和真机验收边界；
- 当前产品盘点 `docs/research/2026-09-19-android-worktree-product-inventory.md`。

## 初学者先读：这个切片到底是什么

S06 不是一个功能，而是五个互不依赖的小包：

1. 翻页动画：页面移动时不能闪、不能露出空白，也不能吞连续点按；
2. 启动主题：用户选了深色，系统画出的第一帧也必须是深色；
3. 真实章号：书架显示的“第 N 章”必须来自最后一次保存的真实位置；
4. 目录浏览：排序、搜索与批量选择必须看到同一批文件；
5. 统一滑杆：所有滑杆视觉一致，同时不能破坏无障碍和 RTL（从右向左）布局。

历史真机报告证明特定设备、特定操作在 2026-09-18/19 得到改善，但它不能自动证明当前混合工作区的全部分支和升级场景都正确。

## 精确切片边界

建议拆成五个独立候选，不做一个大提交：

- S06-A 翻页：`PageTurner.kt` + `PageTurnerHighSpeedTest.kt`；
- S06-B 启动主题：`App.kt`、`MainActivity.kt`、Manifest/theme/night 资源，以及 `SettingsStore.kt` 中仅属于 `BootMirror` 的 hunk；
- S06-C 章号：三个进度保存点、`ShelfModels.extractChapterLabel()` 及对应测试；
- S06-D 目录浏览：`LibraryRootStore.kt`、`LibrarySource.kt`、`LibraryBrowserViewModel.kt`、目录 UI 与两组测试；
- S06-E 滑杆：`CraSlider.kt` 及九个实际调用点。

不得整文件夹带：

- `App.kt` 的崩溃日志、搜索索引和后台启动调度；
- `MainActivity.kt` 的时长单位、硬件键和启动性能重构；
- `SettingsStore.kt` 的灯绳、TTS、页眉页脚和大量 S13 设置；
- `ReaderProgressActions/Effects.kt` 的上一章、精确回源、Markdown/EPUB 坐标等其它修复；
- `ShelfModels.kt` 的 S05 状态、拼音搜索和定位逻辑；
- `AiSubPage.kt`、`GoalSubPage.kt`、`ReaderTtsBar.kt` 等页面的布局重构。

## Standards

### [P1] 五个候选共享多个“超级文件”，按整文件集成会把其它未审切片一起带入

`SettingsStore.kt` 同时包含启动镜像、灯绳、TTS、点击区、页眉页脚等改动；`App.kt` 和 `MainActivity.kt` 同时承载启动性能与其它产品功能；两个 Reader progress 文件还包含章节跳转和精确回源修复；`ShelfModels.kt` 已在 S05 证明包含状态、搜索、定位等多片。特别是滑杆调用文件相对基线有上千行页面重构，不能因为只改了一行 `Slider` 调用就整文件暂存。

这不是代码风格问题，而是回滚、归因和独立验收边界。五个子片必须按 hunk 集成；共享文件由单一集成者串行处理。

证据：`App.kt:45-170`；`MainActivity.kt:50-185`；`SettingsStore.kt:320-610`；`ReaderProgressActions.kt:129-275`；`ReaderProgressEffects.kt:186-300`；`ShelfModels.kt:37-294`。

### [P2] 新增高速翻页仪器测试只检查“中心像素不是黑色”，不能证明翻到了正确页面

三个 `PageTurnerHighSpeedTest` 用中心白色像素证明没有透明洞，这是有价值的视觉门禁；但前两个用例的 `onNext/onPrevious` 是空函数，第三个也只继续采样白色中心，没有断言最终页号、连续翻页次数或旧页是否被错误复用。因此“画了一张不透明但内容错误的页”“第二次快点被吞”都能通过。

应补至少三类断言：一次请求只推进一页、连续 N 次请求推进 N 页、每个提交帧内容身份与目标页一致。当前仪器测试仅完成编译，未在本轮由 Codex 执行真机测试；历史视频证据只覆盖当时的画面，不覆盖这些状态断言。

证据：`PageTurnerHighSpeedTest.kt:25-132`；`PageTurner.kt:338-363`。

### [P2] 章号和统一滑杆缺少针对它们真实风险的自动化测试

现有 locator 测试验证 JSON 编解码，目录测试验证排序/搜索，但没有测试三个真实章号保存点在“小 TXT 分页、小 TXT 滚动、流式 TXT、Markdown、EPUB”五种路径下的最终 `ci`；也没有 `extractChapterLabel()` 的旧数据兼容测试。`CraSlider` 没有 RTL、禁用态、步进、TalkBack range 语义或边界值测试。

这正是下面多个 P1/P2 缺陷能在 2515 项历史 JVM 测试全绿时仍然存在的原因。

证据：全仓测试中无 `extractChapterLabel` 调用；`CraSlider.kt:51-151`；`LocatorCodecTest.kt`。

## Spec

### [P1] “跟随系统”被写成了“按时间/位置自动判断”，语义已经改变

`BootMirror.applyApplicationNightMode()` 把 `system` 映射到 `UiModeManager.MODE_NIGHT_AUTO`。Android 官方 API 明确定义：`MODE_NIGHT_AUTO` 根据位置、传感器/时间自动切换，不等于“跟随手机当前深浅设置”。结果是用户手机明确设为浅色时，本应用仍可能在夜间进入深色；反之亦然。

这是已确认的映射错误，不是推测。需要为“跟随系统”建立明确方案，不能用 `MODE_NIGHT_AUTO` 冒充；并补系统深浅切换后的配置变化验证。

证据：`SettingsStore.kt:371-377`；Android `UiModeManager.setApplicationNightMode` 官方文档。

### [P1] 从旧版本升级且已经选了深色的用户，启动镜像永远不会自动补齐

`BootMirror` 只在 `updateAppearance()`（用户再次修改外观）时写入；启动时若镜像缺失，直接回退 `system`。旧版本用户的真实主题仍在 DataStore 中，但当前代码没有“首次读到 DataStore 后回填镜像”的迁移。也就是说，老用户升级后只要不重新点一次主题选项，冷启动首帧仍可能按系统浅色绘制，原缺陷持续存在。

历史真机报告验证了“已有镜像/重新选择主题”路径，不能覆盖“升级后从未再次改主题”路径。应增加一次性迁移或启动收敛，并测试 DataStore=dark、镜像缺失的升级场景。

证据：`SettingsStore.kt:343-360,420-449,589-600`。

### [P1] 小文件 TXT 的滚动保存和分页防抖保存仍可能把真实章号写回 0

小文件 TXT 的真实章节来自 `txtChapters`，但 `chapterStartOffsets` 只从 `bookIndex`、Markdown 或流式 TXT 文档取得；小文件 TXT 会得到空列表。滚动防抖和 TXT 分页防抖都用这个空列表调用 `EpubLocatorMapping.toChapterOffset()`，该函数对空列表返回章索引 0，于是仍写出“第 1 章”。

退出保存的分页路径可能通过 `pagedSource.chapterIndexFor()` 得到真实章号，所以历史“读到第 3 章再退出”能通过；但这不能覆盖应用进后台、进程被杀、仅靠防抖落库或滚动模式。必须让小 TXT 的 `txtChapters.startOffset` 进入同一章起点真源，并以最终落库实体做测试。

证据：`ReaderScreenDerivedState.kt:63-67,81-115`；`ReaderProgressEffects.kt:221-230,273-282`；`EpubLocatorMapping.kt:17-29`；`ReaderProgressActions.kt:246-264`。

### [P1] 搜索后“全选/反选”操作的是隐藏文件，用户会导入自己看不见的项目

UI 展示的 `uiState.files` 已按关键词过滤，但 `selectAllVisible()`、`invertSelection()` 和 `selectedUris()` 操作的是未过滤的 `core.snapshot.files`。用户搜索到 1 个文件后点“全选”，实际会选中整个目录；按钮的 `allFilesSelected` 又按过滤后的列表计算，界面看起来完全合理，最终却把隐藏文件一起交给导入器。

应以当前可见 documentId 集合执行全选/反选，并明确搜索词变化后隐藏选择是保留、提示还是清除；测试必须断言最终 `selectedUris()`，不能只断言屏幕上剩几行。

证据：`LibraryBrowserViewModel.kt:101-122,282-301`；`LibraryBrowserModels.kt:38-45`；`LibraryBrowserRoute.kt:378-382`。

### [P1] 连续快点仍会在动画期间被直接吞掉

`turnRequest` 一出现就先调用 `onTurnRequestConsumed()` 清零，再异步调用 `settle()`；而 `settle()` 在 `turning == true` 时立即返回。于是第一张页面还在动画时的第二次点按会被清掉且不排队，用户必须等动画结束再点，仍会感到“点了没反应/停顿一下”。

这是代码路径可确定的行为。两帧提交保护被移出 `turning` 窗口只缩短了吞键窗口，并没有解决动画期间的输入队列。应定义最多一个待处理方向或显式的连续翻页队列，并补“快速连点 N 次最终推进 N 页”的状态测试。

证据：`PageTurner.kt:260-263,310-320,338-352`；`PagedReaderPageSurface.kt:360-379,490-505`。

### [P2] `CraSlider` 的轨道固定从左向右绘制，RTL 下拇指与已走进度方向相反

M3 `Slider` 会按布局方向处理手势和拇指位置，但自绘 Canvas 始终从 x=0 画 `activeW`，也没有读取 `LayoutDirection`。在阿拉伯语/希伯来语等 RTL 环境，拇指从右向左移动，彩色已走段却仍从左向右增长，视觉状态相互矛盾。

应在 RTL 下从右边绘制 active 段并把末端停点镜像；同时补截图/像素或 Compose UI 测试。

证据：`CraSlider.kt:108-145`。

### [P2] “最近添加”和“修改时间”是两个菜单项，但实现完全相同

SAF 只提供 `COLUMN_LAST_MODIFIED`，当前两个选项都按同一字段倒序。代码注释把“拷进目录时修改时间就是拷贝时间”当成普遍事实，但文件提供器、MTP/同步工具或保留时间戳的复制操作不保证这一点。用户选择“最近添加”时可能得到旧文件修改时间顺序，而且两个菜单项永远给出同一结果。

若产品确实要“最近添加”，需要由应用在首次观察文件时记录时间；若不建立这份数据，应只保留诚实的“修改时间”文案，避免制造不存在的能力。

证据：`LibraryRootStore.kt:30-42`；`LibrarySource.kt:140-151`；`LibraryBrowserComponents.kt:294-315`。

## 已确认的正向结果

- `TurnScene` 冻结动画三帧、常驻不透明纸色和位图身份校验，针对历史闪屏/透明洞的技术方向成立；
- `PageTurnerHighSpeedTest` 至少建立了“不能露出透明黑底”的可执行视觉门禁，且当前能编译；
- 深浅两套启动资源、`setTheme()` 的调用时机和 API 31+ 告知系统的总体方向正确；
- 目录排序切换不再回读滞后的 DataStore StateFlow；搜索已同时过滤文件夹和文件；
- `CraSlider` 继续委托 M3 Slider 处理手势、步进和基础无障碍语义，没有自行重写手势系统；
- 当前产品源码、Hilt 图和 androidTest 源码均能编译。

## 验证证据

本轮执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.creationreadingassistant.ui.viewmodel.LibraryBrowserSortTest" `
  --tests "com.creationreadingassistant.feature.library.SafLibrarySourceTest" `
  --tests "com.creationreadingassistant.feature.reader.locator.LocatorCodecTest" `
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderProgressNavigationTest"

.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

结果：4 个 JVM 测试类、18 tests，0 failures、0 errors；androidTest Kotlin 编译成功。该结果属于当前混合恢复工作区的开发门禁，不是五个独立候选的证明，也不替代 WorkBuddy 在真实手机上的最终验收。本轮未运行 MuMu、未安装 APK、未操作用户书籍数据。

历史报告的 2515 项 JVM 测试、Redmi 逐帧录像和 Mate 70 Pro 用户反馈仍是有价值的历史证据，但没有覆盖旧版升级镜像、system→AUTO 语义、小 TXT 滚动/防抖章号、搜索后批量选择、连续快点和 RTL 滑杆。

汇总：Standards 3 项（最严重 P1：共享超级文件不能整文件集成）；Spec 7 项（最严重 P1：跟随系统映射错误、旧用户镜像不迁移、小 TXT 章号仍可写 0、搜索后批量误选隐藏文件、连续快点仍被吞）。
