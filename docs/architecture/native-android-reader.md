# 独立原生 Android 阅读器架构

状态：当前执行依据  
更新日期：2026-08-08  
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

- 系统 TTS + MediaSession 通知控制、续读、句级高亮。
- 书签、笔记、高亮、阅读灵感和书摘导出。
- 书内搜索、目录、进度跳转、自动翻页、音量键翻页。
- 用户自配 OpenAI-compatible AI 阅读辅助；Key 单独加密保存，不进入同步和导出。
- 阅读纸张与应用外观分离，最终视觉规范见 `docs/WorkBuddy/theme_visual_plan.md`。

## 5. 当前已知边界

1. 大 TXT 流式已闭环（`TextStreamLoader` 5MB 阈值 + `TxtFileScanner` 索引 + 有界窗口读取），小文件路径保持整本解码；个别消费者（如导入预览、锚点跳转）仍需复核不整文件 `readText()`。
2. AI 阅读辅助（A11）未闭环：`AiClient` 固定 `stream=false`、OkHttp `execute()` 阻塞调用不可协程取消、无 401/429/5xx 错误分类与响应脱敏，关闭 Sheet 后旧请求仍占用线程最长 60s。
3. EPUB 全书搜索无进度与取消 UI（底层已逐章流式且可取消）。
4. Room 外键索引（A8）未完成：schema v7 中 11 个带外键实体均未声明索引。
5. 自动化：JVM 单测 809 项全绿；设备侧已有 Room 迁移测试与 Compose 关键路径（ReaderScreen/ReaderAccessibilityLayout/EpubParser 等 androidTest 11 个文件），但 CI 只编译不执行设备测试。
6. GitHub Release 已在 P0-A2（2026-07-29）退役旧 `mobile/android` APK 的构建与上传；原生 `android/` Release 迁移属于后续 P0-A3，完成前 Release 工作流只构建桌面端。

完整任务与验收见 `docs/testing/native-android-gap-audit-2026-07-29.md`。

## 6. 验证

```powershell
Set-Location android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

Room 迁移与 UI/设备行为必须在已连接的真实手机上验证，所有 ADB 命令显式指定当前序列号。禁止使用 MuMu 模拟器；临时常亮设置在测试结束后恢复。
