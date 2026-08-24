# 原生 Android UX 问题台账

更新日期：2026-08-24

适用范围：`android/` 独立原生 Android 产品线。真机验证只使用当前连接设备；记录统一使用
“测试 TXT”“测试 EPUB”“测试 Markdown”等中性名称。真实书籍截图只保留在本地临时目录，
不提交到仓库。

状态：`OPEN`、`FIXED_CODE`、`DEVICE_VERIFIED`、`BLOCKED`。`FIXED_CODE` 仅表示代码和自动化门禁通过，
不等同于完整真机矩阵通过。

| 编号 | 严重度 | 问题 | 根因与修复 | 验证 | 状态 |
|---|---|---|---|---|---|
| RUX-001 | P1 | 普通控制栏隐藏后正文下方仍永久空出约 160dp | 正文视口错误依赖控制栏实测高度。正文现只避让系统安全区及页眉页脚，普通栏改为覆盖层；控制栏开关不再触发重分页 | `ReaderChromeLayoutTest`；真机隐藏前后截图，隐藏后正文延伸至页脚且页首不变 | DEVICE_VERIFIED |
| RUX-002 | P1 | 横向翻页中出现移动白色矩形纸片，存在文字透叠风险 | `PageTurner` 只覆盖正文列且各页面纸面契约不一致。翻页动画提升到完整阅读视口，正文边距内移，静止页、相邻页、快照页和揭页共用 `ReaderPaperSurfaceSpec` | `PagedReaderViewportGeometryTest`；AndroidTest 编译通过；真机中间帧未见白色纸片或文字透叠 | DEVICE_VERIFIED |
| RUX-003 | P1 | 满高图片可能与分页页眉重叠或被裁切 | 图片最大高度未扣除 `contentTopPaddingPx` | `ChapterPaginatorTest` | FIXED_CODE |
| RUX-004 | P2 | Unicode 忽略大小写搜索可能返回错误原文坐标 | 对全文 `lowercase()` 后索引，某些字符大小写映射会改变字符串长度。改为在原文上用 `regionMatches(ignoreCase = true)` 扫描 | `SearchResultOffsetTest` | FIXED_CODE |
| RUX-005 | P1 | MIUI 安装确认依赖用户手动点击，且旧脚本会把受限安装误报为成功 | 脚本改为推送唯一临时 APK、启动 MIUI 安装器、按包名限定的资源 ID 自动确认，并在完成页核验安装；失败返回非零且清理临时文件 | 主 APK 与 androidTest APK 均在 `c49ac6cf` 上自动安装成功；未修改手机安全设置 | DEVICE_VERIFIED |
| RUX-006 | P1 | 选区工具栏两行七按钮，遮挡正文且单手操作密集 | 主栏收敛为“高亮、笔记、复制、更多”；AI 解读、记为灵感、搜索和取消选择进入更多菜单。颜色选择为独立二级状态，每个色点具名且触控区为 48dp | `ReaderSelectionToolbarModelTest`；Compose 测试编译；测试 TXT 真机截图与 UI dump | DEVICE_VERIFIED |
| RUX-007 | P2 | 搜索空查询时是整片空白；搜索中或取消后会误报“未找到匹配结果” | 搜索面板按查询和会话阶段显式区分引导、搜索中、已取消、无结果和结果列表；空查询显示简短提示，结果区显示总数及当前位置 | `ReaderSearchSheetBodyStateTest`；`ReaderSearchSheetTest` 编译；测试 TXT 真机空查询、80 条结果与下一处 UI dump | DEVICE_VERIFIED |
| RUX-008 | P1 | TXT/EPUB/Markdown 跨格式分页、图片和恢复矩阵缺少完整真机证据 | 补齐 Markdown 代码/表格、测试 EPUB 图片、约 6 MB 多章流式 TXT、约 6 MB 超长单章 TXT、横竖屏、快速翻页及强停恢复；TTS 环境问题拆到 RUX-014 | 测试 Markdown 表格/代码、测试 EPUB 图片横竖屏、测试 TXT 136 章目录与第 10 章同段恢复均通过 | DEVICE_VERIFIED |
| RUX-009 | P1 | Compose 真机仪器测试进入测试类后挂起，无结果返回 | MIUI Activity/仪器测试启动或 Compose idling 通道异常；本轮两次定向运行均停在测试类入口。已强停清理，不判通过也不判业务失败 | `am instrument` 现场日志；JVM、Lint、APK 和 AndroidTest 编译不受影响 | BLOCKED |
| RUX-010 | P1 | Markdown 目录显示 `0 / 0` 且无法跳转 | 目录状态与点击路由只覆盖 EPUB/TXT，漏传 Markdown 章节标题并把 Markdown 点击错误落到 TXT 偏移路径。现统一 EPUB/Markdown/TXT 的目录状态，并让 Markdown 走章节跳转 | `ReaderTocStateTest`；测试 Markdown 真机目录为 `1 / 1`，点击章节可返回正文 | DEVICE_VERIFIED |
| RUX-011 | P0 | EPUB 跳章后退出、强停、重进会回到第 1 章并把正确进度覆盖成 0 | 两个竞态叠加：保存闭包捕获旧偏移；分页宿主首帧又先以章节 0 创建，再等待 `LaunchedEffect` 设置恢复章节。保存改为执行时读取 State-holder，已保存章节在分页宿主首帧前同步进入本地状态 | `ReaderProgressSnapshotTest`、`ReaderInitialChapterStateTest`；真机第 2 章 32.5% → 返回 → 强停 → 重开仍为第 2 章 32.5%，数据库为 chapter 1 / 32.46% | DEVICE_VERIFIED |
| RUX-012 | P2 | 分页 Markdown 表格显示为无层级的普通竖线文本 | 分页适配层已有 TABLE 角色，但 Canvas 未消费。新增纯视觉策略：表头/数据行使用随纸面板，等宽绘制，表头加粗；不改 canonical 文本和可逆坐标 | `PagedMarkdownVisualPolicyTest`；测试 Markdown 真机表格与代码页截图 | DEVICE_VERIFIED |
| RUX-013 | P2 | Markdown 被误提示为“流式大文件不支持净化”，旋转后提示重复遮挡页脚 | INCOMPLETE_SCOPE 被硬编码猜成大文件，且提示消费状态未跨配置变化保存。改为按真实 capability 文案，并以 `rememberSaveable(bookId)` 保证同一阅读会话仅提示一次 | `ReaderReplacementCapabilityTest`；测试 Markdown 不再误报，测试 EPUB 横屏不重复弹出 | DEVICE_VERIFIED |
| RUX-014 | P1 | 当前真机无法完成 TTS 听感、跟读和跨章验收 | 当前设备系统语音引擎初始化失败；已确认不是本轮排版修复造成，但没有可用引擎就不能判应用通过 | 当前设备现场错误；需换系统语音引擎可用真机复验 | BLOCKED |
| RUX-015 | P1 | 同一批导入完成提示在离开书架、重进后反复出现并遮挡下方内容，重复导入还误报为“成功 0、失败 0” | 页面级 `remember` 在路由重建后丢失。将一次性提示消费状态放入批次状态，提示前按 batch id 原子标记，同时保留导入页批次结果；摘要补充重复、跳过和未处理数量 | `ShelfImporterTest`、`ShelfImportSummaryPolicyTest`；真机重复导入测试 TXT 后只提示一次，往返首页不复发，文案显示“重复 1 本” | DEVICE_VERIFIED |
| RUX-016 | P1 | 进入书架搜索后输入框虽获得焦点，MIUI 首次不显示软键盘 | 键盘显示请求早于页面过渡和平台输入连接稳定。请求焦点后等待一帧及短暂入场稳定期再调用输入法显示 | 真机进入搜索页后 `dumpsys input_method` 为 `mInputShown=true`；搜索结果、最近搜索和清空入口复核正常 | DEVICE_VERIFIED |
| RUX-017 | P1 | 图书详情已有多条阅读会话，却把总阅读时长显示为 0；封面更换按钮触控区仅 24dp | 详情只读取可能滞后的进度累计值，未与会话明细兜底；封面按钮显式缩小。总时长改取进度累计与非负会话合计的较大值，避免重复相加；触控区恢复 48dp | `BookDetailStatsPolicyTest`；最新真机测试 TXT 从错误的 0 分钟恢复为 36 分钟，与 8 条记录一致；UI dump 点击区 144×144px（48dp） | DEVICE_VERIFIED |
| RUX-018 | P1 | 书架及其子页面只有手机布局；平板会继续使用底部导航、书格列数无上限，搜索/导入/详情内容被横向拉伸 | 新增窗口宽度策略：600dp 起顶层导航切换为侧边栏；书架手机固定 3 列、600dp 为 4 列、720dp 起最多 5 列；列表与详情内容最大 720dp 并居中 | `LayoutTokensTest`、`ShelfAdaptiveLayoutPolicyTest`、`AdaptiveNavigationPolicyTest`、`ShelfScreenComposeTest`；真机临时 240dpi 得到约 813dp 宽窗口，书架 5 列、侧栏及搜索/导入/详情限宽均通过，随后恢复 480dpi | DEVICE_VERIFIED |

## 2026-08-24 截图证据（本地临时文件）

- 修复前隐藏控制栏：`C:\Users\23254\AppData\Local\Temp\cra-reader-hidden-before.png`
- 修复后隐藏控制栏：`C:\Users\23254\AppData\Local\Temp\cra-reader-hidden-after.png`
- 修复后显示控制栏：`C:\Users\23254\AppData\Local\Temp\cra-reader-controls-after.png`
- 修复后横向滑动中间帧：`C:\Users\23254\AppData\Local\Temp\cra-reader-swipe-after.png`
- 紧凑选区主栏：`C:\Users\23254\AppData\Local\Temp\cra-reader-selection-toolbar.png`
- 选区更多菜单：`C:\Users\23254\AppData\Local\Temp\cra-reader-selection-more.png`
- 高亮颜色二级状态：`C:\Users\23254\AppData\Local\Temp\cra-reader-selection-colors.png`
- 搜索空查询引导与键盘：`C:\Users\23254\AppData\Local\Temp\cra-reader-search-prompt.png`
- 搜索结果列表与结果计数：`C:\Users\23254\AppData\Local\Temp\cra-reader-search-results.png`
- 测试 EPUB 竖屏正文：`C:\Users\23254\AppData\Local\Temp\cra-epub-open.png`
- 测试 EPUB 横屏正文：`C:\Users\23254\AppData\Local\Temp\cra-epub-landscape.png`
- 测试 EPUB 强停重开恢复第 2 章：`C:\Users\23254\AppData\Local\Temp\cra-epub-progress-final-restored.png`
- 测试 Markdown 正文与引用页：`C:\Users\23254\AppData\Local\Temp\cra-markdown-open.png`、`C:\Users\23254\AppData\Local\Temp\cra-md-quote-page.png`
- 测试 Markdown 目录修复：`C:\Users\23254\AppData\Local\Temp\cra-md-toc-fixed.png`
- 测试 Markdown 表格与代码：`C:\Users\23254\AppData\Local\Temp\cra-md-table-fixed.png`、`C:\Users\23254\AppData\Local\Temp\cra-md-code-fixed.png`
- 测试 EPUB 图片横屏分页：`C:\Users\23254\AppData\Local\Temp\cra-epub-landscape-no-repeat.png`、`C:\Users\23254\AppData\Local\Temp\cra-epub-landscape-next.png`
- 测试 TXT 多章流式分页与恢复：`C:\Users\23254\AppData\Local\Temp\cra-large-reader-open.png`、`C:\Users\23254\AppData\Local\Temp\cra-large-page4.png`、`C:\Users\23254\AppData\Local\Temp\cra-large-restored.png`
- 测试 TXT 超长单章：`C:\Users\23254\AppData\Local\Temp\cra-long-reader-ready.png`
- 书架初始审查：`C:\Users\23254\AppData\Local\Temp\cra-shelf-current.png`
- 重复导入摘要与返回不复发：`C:\Users\23254\AppData\Local\Temp\cra-import-duplicate-summary-fixed.png`、`C:\Users\23254\AppData\Local\Temp\cra-import-summary-not-repeated.png`
- 书架搜索自动输入法：`C:\Users\23254\AppData\Local\Temp\cra-shelf-search-ime-fixed.png`
- 图书详情统计修复：`C:\Users\23254\AppData\Local\Temp\cra-book-detail-stats-fixed.png`

这些截图不进入 Git；后续持久化视觉回归应使用仓库内中性 fixture。

## 当前验收边界

- 已满足：普通栏不永久挤压正文、控制栏开关不重分页、横向翻页无移动白色纸片、滑动中间帧无文字透叠、行高默认值统一为 `1.85`。
- 尚未宣称：所有设备尺寸、厂商字体、分屏/折叠屏、TTS 听感与完整无障碍矩阵均已通过。
- 当前设备的系统语音引擎初始化失败，TTS 记为设备环境阻塞，不作为应用通过或失败证据。
- 下一轮顺序：阅读器核心跨格式矩阵已收口；导入/书架/搜索/详情第一轮 P1 及其平板自适应已修复。RUX-009 仪器测试通道和 RUX-014 TTS 设备环境分别追踪，随后扩展到首页继续阅读、历史和全局搜索；这些页面仍需各自的大屏信息架构审查。
