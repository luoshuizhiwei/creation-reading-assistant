# 独立原生 Android 阅读器架构

状态：当前执行依据  
更新日期：2026-08-20
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
- Markdown 已有原生语义层：`MarkdownParser`（commonmark-java + GFM 表格/任务列表/删除线扩展）产出语义块与规范文本，`MarkdownOffsetMap` 维护「规范偏移 ↔ 源偏移」双向映射，`MarkdownPageSource` / `MarkdownChapterSource` 负责分页与按章加载。搜索、TTS、Locator、选区、书签全部基于规范文本偏移。

## 3. 分页与定位

```text
ReaderDocument
   -> ChapterPaginator / LineComposer
   -> TxtChapterSource 或 EPUB 分页控制器
   -> PagedTxtReaderHost / EPUB 分页宿主
   -> PageTurner
```

- 中文排版核心位于 `feature/reader/layout/`，负责字形簇、禁则、行组成和图片分页。
- TXT 与 EPUB 均支持分页/滚动模式；分页引擎有健康保护与旧渲染回退。
- `feature/reader/locator/`、`LegacyOffsetCodec`、锚点缓存和 Room 页索引用于进度恢复、书签、高亮及搜索跳转。
- 任何新格式或流式优化必须保持渲染、搜索、TTS、Locator 使用同一个字符空间。

## 4. 阅读工具

- 系统 TTS + MediaSession 通知控制、续读、句级高亮；章末自动接续（EPUB/MD）、拔耳机自动暂停、语速 0.5–2.0x 连续调节。
- 书签、笔记、高亮、阅读灵感和书摘导出（SAF 写 `.md` + 系统分享，四类内容）。
- 书内搜索（进度+取消）、全局搜索（含正文预览命中）、目录（卷分组/最近浏览/内嵌书签）、进度跳转（拖动百分比预览 + 精确输入）、自动翻页/滚动、音量键翻页、屏幕方向锁定。
- 灵感中心：类型/状态筛选、排序、AI 变体、定位来源跳转。
- 书架：格式/状态/书单/分类/标签多维筛选（标签多选）、批量管理、导入内容哈希查重、EPUB 内嵌封面提取。
- 用户自配 OpenAI-compatible AI 阅读辅助（流式+取消+无网预检）；Key 单独加密保存，不进入同步和导出。
- 阅读纸张与应用外观分离（色温滤镜/纸张纹理可调），最终视觉规范见 `docs/WorkBuddy/theme_visual_plan.md`。

## 5. 当前已知边界

1. 大 TXT 流式已闭环（`TextStreamLoader` 5MB 阈值 + `TxtFileScanner` 索引 + 有界窗口读取），小文件路径保持整本解码。2026-08-16 复核：生产路径已无整文件 `readText()`（仅存崩溃日志/EPUB nav/整库导入/更新检查 4 处合理使用）；剩余为 50MB 级真机压力验证。
2. AI 阅读辅助（A11）已闭环：`AiClient` 支持 SSE 流式（`chatStreaming`）与协程取消（`executeCancellable` + `invokeOnCancellation`），401/429/5xx/网络错误分类、错误消息不携带响应体（防 API Key 回显）、非加密 http 一次性警示（局域网白名单）；`AiClientTest` 16 个用例锁定。
3. EPUB 全书搜索已有进度与取消 UI：`ReaderSearchLogic` 逐章流式检索带 `onProgress` 回调与 `yield()` 协作取消，`ReaderSearchSheet` 展示「已扫描 X/Y」进度条并可取消在途搜索。
4. Room schema 已演进到 v10：外键索引（A8）保持完整；v9→v10 新增本地体验态表 `chapter_reads`，并为 tags/shelves 增加 `sort_order`。迁移会为分类/标签/书单旧数据生成稳定唯一序号，避免同值交换无效；9→10 与 1→10 全链迁移已在真实手机通过。
5. 自动化：JVM 全量单测、`lintDebug`、`assembleDebug` 与 androidTest 编译于 2026-08-20 通过；真实手机定向执行 28 项（全部迁移、ChapterRead DAO、三类真实 Room 排序）0 失败。CI 的 `android-migration-tests` 模拟器 job 仍只执行迁移测试一个类；调试构建已启用 StrictMode（penaltyLog，release 无影响）。
6. 原生 Android 发布（P0-A3）已收口（2026-08-16）：`release.yml` 按 tag 前缀分流（`v*` 桌面端 / `android-v*` Android 签名 APK + GPL 源码包 + SHA-256），版本注入与签名兜底/CI 缺签名即失败已落在 `android/app/build.gradle.kts`；应用内「检查更新」按 `android-v` 前缀过滤 releases 列表并做语义化版本比较（`UpdateCheck` + JVM 单测）。首次发版前需按 `docs/release/ANDROID_RELEASE.md` §2.4 配置签名 Secrets。

完整任务与验收见 `docs/testing/native-android-gap-audit-2026-07-29.md`。

## 6. 验证

```powershell
Set-Location android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

Room 迁移与 UI/设备行为必须在已连接的真实手机上验证，所有 ADB 命令显式指定当前序列号。禁止使用 MuMu 模拟器；临时常亮设置在测试结束后恢复。
