# 独立原生 Android 阅读器架构

状态：当前执行依据  
更新日期：2026-08-21 会话（流式大 TXT 回归三件套修复：有界分页/作用域拆分、LRU 3-chapter 缓存、availability 真实化）
范围：`android/`（Kotlin + Jetpack Compose + Room）

## 1. 运行边界

当前移动端主线是独立应用 `android/`。它不依赖 `mobile/src/`、Capacitor WebView 或 `mobile/android/legado-reader-core` 才能运行。`mobile/` 仅作为历史实现和协议/交互参考。

阅读入口是 `ui/screen/ReaderScreen.kt`，书籍与阅读数据由 Room、DataStore 和 Hilt 提供。TXT、Markdown、EPUB 共用同一套阅读设置、进度、Locator、标注、TTS、AI 和阅读统计。

## 2. 文档与解析

```text
SAF / 同步后的本地文件
        |
        +-- EPUB -> EpubRepository -> EpubParser -> EpubDocument
        |
        +-- TXT/Markdown -> PlainTextDecoder / TxtFileScanner
                           -> PlainTextDocument
        |
        v
ReaderDocument / DocBlock / Chapter
```

- `ReaderDocument` 定义章节、文本块和正文访问边界。
- `EpubDocument` 按章加载 EPUB 文本与图片元数据，`DocumentCache` 控制章节缓存。
- 小型 TXT/Markdown 仍可整本解码；大文件由 `TxtFileScanner` 建索引，再由 `PlainTextDocument` 按需读取。
- 流式 TXT 新增章级原始 source 读取：`PlainTextDocument.readChapterRawText(chapterIndex)`，
  小文件走全文切片、流式文档走 TXT 索引 chapter 条目 start/end 的文件范围精确 UTF-8 读取；
  返回的原文仍保持全局字符偏移（`chapterStartAbs` 即索引 charStart，不改坐标语义）。
- Markdown 已有原生语义层：`MarkdownParser`（commonmark-java + GFM 表格/任务列表/删除线扩展）产出语义块与规范文本，`MarkdownOffsetMap` 维护「规范偏移 ↔ 源偏移」双向映射，`MarkdownPageSource` / `MarkdownChapterSource` 负责分页与按章加载。搜索、TTS、Locator、选区、书签全部基于规范文本偏移。

## 3. 分页与定位

```text
ReaderDocument
   -> ChapterPaginator / LineComposer
   -> TxtChapterSource（按逻辑章节，不再依赖归档 ReadingUnit）
      或 EPUB 分页控制器
   -> PagedTxtReaderHost / EPUB 分页宿主
   -> PageTurner
```

- 中文排版核心位于 `feature/reader/layout/`，负责字形簇、禁则、行组成和图片分页。
- TXT 与 EPUB 均支持分页/滚动模式；分页引擎有健康保护与旧渲染回退。
- `feature/reader/locator/`、`LegacyOffsetCodec`、锚点缓存和 Room 页索引用于进度恢复、书签、高亮及搜索跳转。
- 任何新格式或流式优化必须保持渲染、搜索、TTS、Locator 使用同一个字符空间。
- `PagedChapterSource.replaceProjectionScopeIsComplete`：新能力位。TXT + 分页引擎路径
  置 true（小型整本 + 流式完整逻辑章级均满足）；legacy 渲染、滚动、EPUB、Markdown 均 false。
- 小型 TXT + 流式大 TXT 在分页引擎下统一可用 `ReplacedChapterSource` 派生 display 文本；
  持久化坐标继续以 source 为准，控制器与宿主统一执行双向映射；分页缓存包含替换规则指纹。
  EPUB 坐标为估算且含图片块、Markdown 含结构样式、legacy/滚动缺少章级 source，
  因此三者当前仍禁止近似包装。

## 4. 阅读工具

- 系统 TTS + MediaSession 通知控制、续读、句级高亮；章末自动接续（EPUB/MD）、拔耳机自动暂停、语速 0.5–2.0x 连续调节。
- 书签、笔记、高亮、阅读灵感和书摘导出（SAF 写 `.md` + 系统分享，四类内容）。
- 书内搜索（进度+取消）、全局搜索（含正文预览命中）、目录（卷分组/最近浏览/内嵌书签）、进度跳转（拖动百分比预览 + 精确输入）、自动翻页/滚动、音量键翻页、屏幕方向锁定。
- 灵感中心：类型/状态筛选、排序、AI 变体、定位来源跳转。
- 书架：格式/状态/书单/分类/标签多维筛选（标签多选）、批量管理、导入内容哈希查重、EPUB 内嵌封面提取。
- 用户自配 OpenAI-compatible AI 阅读辅助（流式+取消+无网预检）；Key 单独加密保存，不进入同步和导出。
- 阅读纸张与应用外观分离（色温滤镜/纸张纹理可调），最终视觉规范见 `docs/WorkBuddy/theme_visual_plan.md`。

## 5. 当前已知边界

1. 大 TXT 流式已闭环（`TextStreamLoader` 5MB 阈值 + `TxtFileScanner` 索引 + 有界窗口读取），小文件路径保持整本解码。2026-08-16 复核：生产路径已无整文件 `readText()`（仅存崩溃日志/EPUB nav/整库导入/更新检查 4 处合理使用）；2026-08-21 会话关键修正：**分页单元与完整投影作用域拆开** — `TxtChapterSource.fromStreaming(doc, index)` 以 `ReadingUnit`（上限 PlainTextDocument.MAX_WINDOW_CHARS）作为分页 segment 输出，50MB 无目录 TXT 分段数≥100；新增 `ReplaceProjectionScopeProvider.scopeForSegment(segmentIndex)` 提供完整逻辑章投影的 Exact/UnsupportedTooLarge/Incomplete 判定；超过 256K 字符的逻辑章仅靠 TxtFileIndex 元数据在整章读取前被拦截，保留原文+一次性超限提示，不触发整章分配。不再用"逻辑章节计数 = chapterCount"或"整章 ByteArray"来实现替换，避免无目录大 TXT 退化为整本读取。
2. AI 阅读辅助（A11）已闭环：`AiClient` 支持 SSE 流式（`chatStreaming`）与协程取消（`executeCancellable` + `invokeOnCancellation`），401/429/5xx/网络错误分类、错误消息不携带响应体（防 API Key 回显）、非加密 http 一次性警示（局域网白名单）；`AiClientTest` 16 个用例锁定。
3. EPUB 全书搜索已有进度与取消 UI：`ReaderSearchLogic` 逐章流式检索带 `onProgress` 回调与 `yield()` 协作取消，`ReaderSearchSheet` 展示「已扫描 X/Y」进度条并可取消在途搜索。
4. Room schema 已演进到 v10：外键索引（A8）保持完整；v9→v10 新增本地体验态表 `chapter_reads`，并为 tags/shelves 增加 `sort_order`。迁移会为分类/标签/书单旧数据生成稳定唯一序号，避免同值交换无效；9→10 与 1→10 全链迁移已在真实手机通过。`ChapterReadRepository` 负责 EPUB/Markdown 到达章幂等写入；首次打开和成功切章均接入，删书事务与 TOC 确认操作可按书清理。已读 Flow 进入阅读路由，TOC 展示非当前已读章弱化色与行尾点、分卷及全书计数；TXT 既不写入也不展示已读状态。分类、标签、书单管理页通过独立排序模式调用相邻交换，首尾操作禁用；书架筛选、Organizer 与 Selection 继续按 DAO Flow 顺序消费。
5. 替换净化 P3.1 覆盖：小型 TXT + 流式大 TXT（分页引擎模式）。ReaderReplacementCapability 直接消费 `ReaderPagerEngineState.replacementAvailability`（`PagedReplacementAvailability` 枚举：APPLIED/SOURCE_UNAVAILABLE/INCOMPLETE_SCOPE/ESTIMATED_COORDINATES/OVERSIZED_CURRENT_CHAPTER/NO_EFFECTIVE_RULES），不再自行猜测 `isTxt && pagerEngineOn`；滚动/legacy/EPUB/Markdown、source 未构建、章节为空、当前章超限等统一返回对应原因。ReplacedChapterSource 缓存从无界 `mutableMap<Int, CachedChapter>` 改为 LRU 3-chapter 有界容量（当前章 + 前一章 + 后一章），访问第 4 章后 LRU 淘汰；规则 key 变化后不复用旧投影；`UnsupportedTooLarge` 不缓存整章文本；长耗时文件 IO 与正则投影在 synchronized 锁外执行，锁内只保护缓存读写。完整逻辑章≤256K 时跨原 ReadingUnit 的正则投影、source↔display 往返已在 JVM 闭环；legacy/滚动路径正文投影、EPUB/Markdown 结构保真替换、并发线程下的重复投影去重 putIfAbsent 收紧、带书真机矩阵仍是开放项。
6. 自动化：2026-08-21 会话重跑全量门禁（旧 2026-08-20 数字作废）：定向 JVM 5 类 suites=5/tests=58/0 failures；全量 JVM `:app:testDebugUnitTest` suites=153/tests=1432/0 failures；`lintDebug`/`assembleDebug`/`compileDebugAndroidTestKotlin` 全通过。真机 serial `c49ac6cf`：Debug APK 安装、冷启动、FATAL/ANR 检查通过；ReaderRulesSheetTest Compose 6/6 实际通过（numtests=6, OK (6 tests)，MIUI 下测试 Activity 卡前台时以 MAIN+LAUNCHER+0x10008000 后台保活脚本拉前台）；带书矩阵仍待用户导入中性测试 TXT 后执行。`stay_on_while_plugged_in` 保持原值 7。
7. 原生 Android 发布（P0-A3）已收口（2026-08-16）：`release.yml` 按 tag 前缀分流（`v*` 桌面端 / `android-v*` Android 签名 APK + GPL 源码包 + SHA-256），版本注入与签名兜底/CI 缺签名即失败已落在 `android/app/build.gradle.kts`；应用内「检查更新」按 `android-v` 前缀过滤 releases 列表并做语义化版本比较（`UpdateCheck` + JVM 单测）。首次发版前需按 `docs/release/ANDROID_RELEASE.md` §2.4 配置签名 Secrets。

完整任务与验收见 `docs/testing/native-android-gap-audit-2026-07-29.md`。

## 6. 验证

```powershell
Set-Location android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

Room 迁移与 UI/设备行为必须在已连接的真实手机上验证，所有 ADB 命令显式指定当前序列号。禁止使用 MuMu 模拟器；临时常亮设置在测试结束后恢复。
