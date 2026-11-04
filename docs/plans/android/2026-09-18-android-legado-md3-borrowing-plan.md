# 对照 legado-with-MD3 的移动端优化清单

> 取证时间：2026-09-18。对照样本：`D:\Application\Desktop\legado-with-MD3-main`
> （legado 上游 + Material3 改版分支，约 1538 个 Kotlin 文件）。
> 路径缩写 `~` = `app/src/main/java/io/legado/app`。
> 本清单只针对 Android 端（`android/`），不碰 `src/`、`electron/`、`archives/`。
> 与 `docs/plans/legado-feature-backlog.md` 的编号在「关联」行互相引用，不重复立项。

## 0. 总体结论

| 维度 | 我们 | legado-with-MD3 |
|---|---|---|
| 引擎 | 纯 Compose 自绘分页（`feature/reader/layout/ChapterPaginator.kt`、`pager/PageTurner.kt`） | Compose 外壳 + 自定义 View 引擎（`StaticLayout`/自研 `ZhLayout`） |
| 测试 | 2437 例 JVM 单测 + Room 迁移 androidTest，**明显更强** | 103 例 JVM 单测，无 Compose 测试 |
| 性能基建 | 真 baseline profile + benchmark 模块 + 邻章预排 | 同类 baseline + RenderNode 录制缓存 + 全书页码索引 |
| 主题 | 6 外壳 + 9 纸张 + 动态取色（`ui/theme/Theme.kt`、`ReaderPaperPalette.kt`） | 12 手写色板 + 封面取色 + 主题包分享 + E-Ink 档 |
| 可配置性 | 选区工具条可配、设置搜索、两级路由 + 预设 | 九宫格点击动作、自定义键码、页眉页脚模板、工具栏自由排布 |
| 包袱 | 少量巨型文件（`SearchIndexRepository.kt` 1436 行） | `ReadBookViewModel.kt` 6655 行 God 对象、双主题栈、176 个旧 XML——**反面教材** |

我们的工程化与测试是优势，保持；差距集中在**排版引擎深度、全书级体验、交互可配置性**三块。

---

## 1. A 批 — 阅读引擎（高价值，中成本）

### A1. 全书页码与进度（backlog #7）
> **状态（2026-09-18）**：核心已落地——`feature/reader/pager/WholeBookPageModel.kt`
> （精确页 + 估页 + 前缀和增量修正 + revision）与 `WholeBookPageModelTest.kt`
> 11 例 JVM 全绿（独立编译门跑通，共享工作区当时被其他切片占编）。
> **待接线**：ViewModel 灌入 PageIndexStore 命中章的精确页数、进度条/全书 %/剩余页数改读模型。
- **对方证据**：`~/.ui/book/read/pageestimate/*`——启发估页 → 精确页码入库 → 校准 →
  `WholeBookPageIndex`（IntArray 前缀和 + `correct()` 增量修正）；`~/.service/FullBookPageService.kt` 后台预排。
- **我方现状**：仅章内 X/Y（`pager/PageIndexStore.kt` 已按章落库，缺全书聚合与估页）。
- **做什么**：新增全书页码表（bookId + 章起始页偏移前缀和），已排章用精确值、未排章用
  「字数/平均页容量」估值；任何一章重排后做增量修正；进度条、剩余时间、全书 % 全部改读该索引。
- **验收**：任意章拖动进度条跳章后，全书 % 不回跳抖动；JVM 单测覆盖前缀和 `correct()`
  的插入/删除/重排三种变更；10 万字 TXT 首开 3 秒内显示全书 %。
- **成本**：中。

### A2. 行/页级 RenderNode 录制缓存
- **对方证据**：`~/.utils/canvasrecorder/*`（API23/29 双实现 + 对象池 + 锁），
  `page/entities/TextLine.kt:65` 每行一个 recorder、`TextPage.kt:56` 每页一个。
- **我方现状**：`PagedReaderPageSurface.kt` 每帧 `TextPaint` 重画整页文本。
- **做什么**：分页宿主把每页文本录制为 `RecordingCanvas`（`Picture`/`RenderNode` 均可），
  翻页动画帧只做重放 + 变换；纸张/字号/行距变更时整批失效；缓存上限与 `ReadingUnitCache` 同口径。
- **验收**：Macrobenchmark 翻页帧 P95 较基线下降 ≥20%（`benchmark/` 模块出数）；
  动画期间无 `TextMeasure` 调用（binder 探针或帧 trace 证明）。
- **成本**：中。

### A3. 章节流式排版（backlog #5）
- **对方证据**：`page/provider/TextChapterLayout.kt`——`CoroutineScope + Channel` 逐页产出，
  `LayoutProgressListener.onLayoutPageCompleted` 回灌 UI。
- **我方现状**：整章排完才上屏，长章（>3 万字）首屏可感等待。
- **做什么**：`ChapterPaginator` 输出改 Flow，宿主拿到第一页即渲染，后续页到达后补全
  章内页码与相邻预排队列；排版协程可被下一次跳章取消。
- **验收**：长章首屏时间 ≤ 短章 1.5 倍；排版中跳章不产生僵尸协程（测试证明取消传播）。
- **成本**：中。

### A4. 中文标点禁则排版
- **对方证据**：`page/provider/ZhLayout.kt`（prePanc/postPanc 避头尾 + CPS 断行代价），
  与 `StaticLayout` 双路由可切换（`ReadBookConfig.useZhLayout`）。
- **我方现状**：`Android/PaintTextRuler.kt` 逐字测宽，行断点不做禁则。
- **做什么**：行尾禁则（避头：，。；」等不落行首；避尾：前引号、破折号前半不落行尾），
  作为排版设置开关，默认开；断行代价函数放 JVM 可测的纯 Kotlin。
- **验收**：禁则单测（行首标点 0 出现）；真机抽查 3 章无「标点孤悬」；关闭开关后与现行为逐页一致。
- **成本**：中。

---

## 2. B 批 — 交互可配置（用户可感知）

### B1. 九宫格点击区自定义（backlog #14）
- **对方证据**：`read/sheet/ClickActionConfigSheet.kt`——3×3 区映射 14 种动作。
- **我方现状**：三/五区固定（`ReaderInteractionLayer` 手势分区）。
- **做什么**：设置页新增点击布局选择（三区/五区/九宫格）+ 每区动作下拉；落 `ReaderSettings`
  走全局 + 本书覆盖路由；默认保持现状。
- **验收**：动作映射表持久化往返测试；误触保护（正文区双击仍可唤菜单）。
- **成本**：中。

### B2. 翻页键扩展（backlog #20）
> **状态（2026-09-18）**：代码已落地——`ReaderHardwareKeys.directionOf` 常量表补
> PAGE_UP/DOWN、SPACE、DPAD 左右；滚轮走 `dispatchScroll`（ACTION_SCROLL 的轴值经
> `KeyEvent#getAxisValue` 反射读取，公开 android.jar 无此 API，取不到不消费），
> `MainActivity.dispatchKeyEvent` 截获滚轮。9+11=20 例 JVM 全绿。
> **待真机验证**：`adb shell input keyevent 92/93/62` 与鼠标滚轮各翻一页（需共享工作区编译恢复后出包）。
- **对方证据**：`ReadBookController.onKeyDown:1186-1264`（音量/PAGE_UP/SPACE/DPAD/MEDIA_NEXT）
  + `mouseWheelPage`；`sheet/PageKeyConfigSheet.kt` 自定义键码。
- **我方现状**：仅音量键（`ReaderHardwareKeys.kt`）。
- **做什么**：补 PAGE_UP/DOWN、SPACE、DPAD 左右与鼠标滚轮；键码映射先做常量表，配置 UI 后置。
- **验收**：androidTest 注入 KeyEvent 逐键断言翻页方向；不劫持系统组合键。
- **成本**：小。

### B3. 页眉页脚模板
- **对方证据**：`help/config/CustomTipPlaceholder.kt`——12 个 `{Token}` + 未闭合/白名单校验
  + `sheet/HeaderFooterPage.kt` chip 本地化标签。
- **我方现状**：页眉页脚内容固定。
- **做什么**：顶栏下/底栏上信息行支持模板串（书名/章节/页码/全书%/时间/电量等 token），
  校验器拒绝未知 token 与未闭合花括号；设置页 chip 点选插入。
- **验收**：模板解析纯函数 JVM 单测；非法模板保存时给出行内错误而非崩溃。
- **成本**：小。

### B4. TalkBack 翻页无障碍动作
- **对方证据**：`page/ReadView.kt:288-333`——自定义翻页 View 主动注册 AccessibilityAction。
- **我方现状**：灯绳已有 switch 语义（`ReaderDayNightCord` toggleable），正文翻页无自定义动作。
- **做什么**：分页宿主 `semantics { customActions(...) }` 注册「下一页/上一页/打开目录」，
  动作复用现有回调链。
- **验收**：TalkBack 实机读出并可执行三个动作；不改变既有触摸行为。
- **成本**：小。

### B5. 护眼色温 ColorMatrix 滤镜
- **对方证据**：`read/EyeProtection.kt`——2596–5500K → ColorMatrix，在合成层加滤镜而非改主题色。
- **我方现状**：`feature/reader/eyecare/EyeCareSchedule.kt` 直接换纸张/主题色，色温档有限。
- **做什么**：阅读容器 `graphicsLayer { colorFilter = ... }` 实现连续色温滑杆；
  与 9 档纸张正交（纸张管明暗，滤镜管色温）；夜间到点仍走灯绳同一状态。
- **验收**：色温滑杆 0 帧抖动（RenderThread trace）；截图对比纸张色不被二次偏移。
- **成本**：小。

---

## 3. C 批 — 主题与外观

### C1. 封面取色驱动外壳主题
- **对方证据**：`~/.ui/theme/ImageSeedColorExtractor.kt`（封面 → 种子色 → 主题）。
- **我方现状**：动态取色只吃壁纸（`Theme.kt:653`，`useDynamicColor`）。
- **做什么**：新增种子源枚举（壁纸/封面/手动），封面模式取当前阅读书籍封面主色，
  经 `MaterialYouTheme` 同管线生成；书架卡片不受影响。
- **验收**：切换 3 本不同封面书外壳色跟随且文字对比度 ≥4.5:1；关闭时零开销。
- **成本**：小～中。

### C2. E-Ink 纯黑白纸张档
- **对方证据**：`ReadStyleResolver.ReadStyleMode.{Day,Night,EInk}`。
- **我方现状**：9 档纸张无纯黑白（`ReaderPaperPalette.kt:126-330`）。
- **做什么**：加第 10 档：纯白底纯黑字、面板零圆角阴影、灯绳/装饰降为单色描边。
- **验收**：截图取色仅 {0,0,0}/{255,255,255}（正文区）；翻页动画保持 none 默认。
- **成本**：小。

### C3. 封面共享元素 + 预测返回（backlog #18）
- **对方证据**：`ui/main/BookCoverSharedElement.kt`，全库 62 处 sharedElement/predictiveBack。
- **我方现状**：书架 → 阅读器无转场连续性。
- **做什么**：基于 Compose SharedTransitionLayout（适配 Navigation3 或现有导航），
  书架封面卡片与阅读器顶栏书名区做共享元素；尊重 `rememberReducedMotion`。
- **验收**：转场不掉帧（benchmark 导航用例）；返回手势中断时动画正确收敛。
- **成本**：中。

### C4. 主题包导入导出
- **对方证据**：`help/config/ThemePackageManager.kt`（manifest + 内嵌资源 + 封面相册，
  带 legacy 迁移与回滚，`ThemePackageManagerRollbackTest`）。
- **做什么**：等纸张/配色扩展 ≥3 次后再立项；ZIP 格式含 schemaVersion，导入失败整体回滚。
- **验收**：坏包不产生半成品状态（回滚测试）。
- **成本**：中。**暂缓**。

---

## 4. D 批 — 工程与架构（防倒退）

### D1. MVI Contract 模板 + 巨型文件拆分
- **对方证据**：`{Feature}Contract.kt`（UiState/Intent/Effect/Sheet 同文件、`@Stable`、
  `ImmutableList`，规范见其 `AGENTS.md:155-320`）。
- **我方现状**：15 个 `@HiltViewModel` 无统一契约文件；`ReaderTtsBar.kt` 885 行、
  `SyncSubPage.kt` 871 行、`SearchIndexRepository.kt` 1436 行。
- **做什么**：新页面一律 Contract 模板；上述三个文件按「读/写」「UI 状态/数据」拆到
  各自 ≤400 行；参照规范 `docs/architecture/android-code-quality-and-evolution-guide.md`。
- **验收**：拆分后全量单测绿、行为 diff 审查通过；新文件行数上限进 lint 或 review checklist。
- **成本**：中。

### D2. 设置写路径 SSOT（pending overlay + 串行写队列）
- **对方证据**：`help/config/AppConfigStore.kt:141-301` + `SettingsWriter.kt`——
  DataStore 快照 + 乐观读 overlay + 串行写队列，核心状态机与 Android 解耦可 JVM 测
  （`PendingOverlayCoreTest.kt`）。
- **我方现状**：`SettingsStore` 有 bootAppearance 启动镜像，但读路径仍可能与写竞态。
- **做什么**：把「快照 + pending overlay + 串行写」抽成纯 Kotlin 状态机（JVM 可测），
  SettingsStore 只做 DataStore 适配层。
- **验收**：并发读写压测单测（仿其 `AtomicSettingsTestUtils`）；设置页改后立即进阅读器不读到旧值。
- **成本**：中。

### D3. review 规范工具化
- **对方证据**：`.agents|.claude|.codex/skills/legado-compose-{migration,review}/SKILL.md`
  + `references/review-checklist.md`（一份内容多工具目录共用）。
- **做什么**：把 D1 的 Contract 模板、行数上限、Compose 重组规则、真机验证约束写成
  本仓库可执行 review checklist，供各 agent 工具共用。
- **验收**：一次真实 PR 走查按清单产出问题列表。
- **成本**：小。

### D4. 锁版本必须写「为什么不能升」
- **对方证据**：`gradle/libs.versions.toml:32-42,53-57` 注释原因 + `AGENTS.md:378-380` 复述。
- **做什么**：约定：凡 pin/降级依赖，toml 注释 + AGENTS.md 同步一条。
- **成本**：≈0。

---

## 5. 明确不借鉴（反向参考）

- 书源/网络抓取/Cronet/Rhino/WebView Backstage（backlog §五 已定不做；我们只做本地导入）。
- `ReadBookViewModel` 6655 行 God 对象、全局可变 `ReadBookConfig.durConfig` 被排版线程直读。
- `GlobalContext.get()` 服务定位器打进渲染热路径（`TextChapterLayout.kt:109`）。
- View/Compose 双主题栈、Glide+Coil 双图片栈、176 个遗留 layout XML 的半途迁移。
- 其文档漂移教训：AGENTS.md 声称的 `ReadBookActivity` 实际已不存在——我们的 handoff 文档
  每次切片后要跑 doc-code 对齐（已在做，保持）。

## 6. 实施顺序建议

第一批：**A1 → A2 → B1 → A3 → B3**（引擎两项 + 交互两项 + 模板一项）；
第二批：A4 / B2 / B4 / B5 / C1 / C2；C3 / D1 / D2 随下一次大切片排入；C4 暂缓。
每项开工前在本文件对应条目标注「进行中/已完成 + 验收证据路径」，完成后同步
`docs/handoff/current.md` 开放项。
