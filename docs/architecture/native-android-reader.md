# 独立原生 Android 阅读器架构

状态：当前执行依据  
更新日期：2026-07-29  
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
- Markdown 目前只复用纯文本路径和标题目录规则，尚无原生 Markdown 视觉语义层。

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

1. 大 TXT 的流式改造尚未端到端闭合：部分锚点跳转仍会整文件 `readText()`，其他消费者也需要逐一确认有界读取。
2. Markdown 被宣传为支持格式，但当前正文按纯文本显示，标题之外的列表、引用、代码、表格和链接没有原生样式。
3. `ReaderScreen.kt` 超过 4,400 行，同时承担加载、会话、I/O、分页、TTS、AI、标注和大量 UI；后续应按职责拆分，但不能借重构改变 Locator 或数据语义。
4. 自动化主要是 JVM 测试；设备侧只有 Room 迁移测试，缺少 Compose 关键路径回归。
5. GitHub Release 已在 P0-A2（2026-07-29）退役旧 `mobile/android` APK 的构建与上传；原生 `android/` Release 迁移属于后续 P0-A3，完成前 Release 工作流只构建桌面端。

完整任务与验收见 `docs/testing/native-android-gap-audit-2026-07-29.md`。

## 6. 验证

```powershell
Set-Location android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

Room 迁移与 UI/设备行为必须在已连接的真实手机上验证，所有 ADB 命令显式指定当前序列号。禁止使用 MuMu 模拟器；临时常亮设置在测试结束后恢复。
