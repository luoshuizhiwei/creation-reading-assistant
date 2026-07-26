# P2 设计文档：原生 EPUB 阅读

> 本文件遵循「修改 → 文档 → 实现 → 一致性回检」纪律。P2 在 P1 工程（`android/`）上增量实现。

## 1. 目标与背景
- **背景**：P1 仅支持 txt/md 纯文本阅读。用户要求原生 Android 支持 EPUB（不引入 WebView / 不依赖 Web 技术）。
- **目标**：在阅读 Tab 打开 `.epub` 后，原生解析并渲染章节文本与图片、支持目录跳转与上/下章、进度持久化。
- **关键决策**：未采用 Readium（其 Maven 坐标/API 版本敏感，当前沙箱无网络无法验证、首编极易炸）。改用**自包含解析**（内置 `java.util.zip` + `XmlPullParser`，零外部依赖），仅依赖 P1 已有的 Compose + Coil + 协程，最大化首次构建成功率。Readium 可作为后续独立 module 替换 `EpubParser`，模型 `EpubBook` 保持不变。

## 2. 主要变更点
- 新增 `domain/model/Epub.kt`：`EpubBook` / `EpubChapter` / `EpubBlock`（Text | Image）。
- 新增 `feature/reader/EpubParser.kt`：SAF Uri → 缓存 → ZipFile → container.xml → OPF(metadata/manifest/spine) → 章节 XHTML → 去标签抽取文本块 + 抽图片到缓存。
- 新增 `feature/reader/EpubRepository.kt`：打开解析、登记到 `books` 表（满足 `reading_progress` 外键约束）、进度落库。
- 改造 `ui/viewmodel/ReaderViewModel.kt`：注入 `EpubRepository`；`openFile()` 按扩展名分流；新增 `epubBook`/`chapterIndex` 状态与 `goToChapter()`。
- 改造 `ui/screen/ReaderScreen.kt`：epub 走 `EpubReader`（目录 DropdownMenu + 上/下章 + 图片 `AsyncImage`）；txt/md 保留 `PlainTextReader`。
- `res/values/strings.xml`：新增 `reader_prev` / `reader_next`，`reader_open_file` 改为通用文案。
- `app/build.gradle.kts`：`versionName` → `0.2.0-p2`。

## 3. 功能描述
- 打开 `.epub`：解析并显示第一章；非 epub 按纯文本。
- 目录：TopAppBar 显示「当前/总数」，点击展开章节列表跳转。
- 导航：底部「上一章 / 打开文件 / 下一章」。
- 进度：每次翻章写入 `reading_progress.current_location_json = {"chapter":N}`，重开恢复。
- 图片：章节内 `<img>` 抽取到 `cacheDir/epub/<id>/img/`，由 Coil 加载。

## 4. 接口 / 关键参数
- `EpubParser.parse(context, uri: Uri): EpubBook`（suspend，IO 调度）。
- `EpubRepository.openEpub(uri): EpubBook` / `saveProgress(bookId, chapter, percent)` / `loadProgress(bookId): Int`。
- `ReaderViewModel.openFile(context, uri)` / `goToChapter(index)` / 状态 `epubBook`、`chapterIndex`、`isLoading`、`error`。
- DB 约束：`reading_progress.book_id` → `books.id` 外键 CASCADE；故 `openEpub` 先 `bookDao.upsert(BookEntity(format="epub", ...))`。

## 5. 使用方式（用户本机）
```
# 复用 P1 的构建/装包流程（Android Studio 打开 android/，Sync 联网下载依赖）
# 连真机 → Run 'app' 或：
D:\develop\Android\Sdk\platform-tools\adb.exe install -r android\app\build\outputs\apk\debug\app-debug.apk
# 阅读 Tab → 打开本地文件 → 选一个 .epub → 验证：章节文本/图片显示、目录跳转、上/下章、退出重进恢复进度
```

## 6. 一致性回检（文档 ↔ 代码）
- [x] `EpubBook`/`EpubChapter`/`EpubBlock` 模型：文档与 `domain/model/Epub.kt` 一致。
- [x] 外键约束处理：文档要求先建 `books` 行，`EpubRepository.openEpub` 确实先 `bookDao.upsert(...)` 再返回，与 `ReadingProgressEntity` 外键一致。
- [x] 进度字段：文档写 `current_location_json={"chapter":N}`，代码 `saveProgress` 与 `loadProgress` 正则 `"chapter"\s*:\s*(\d+)` 互相对齐。
- [x] 分支分流：`ReaderViewModel.openFile` 按 `.epub` 后缀判定，与文档一致。
- [x] 资源：文档列 `reader_prev`/`reader_next`，`strings.xml` 已加；`reader_open_file` 文案已更新。
- [x] 版本：`versionName 0.2.0-p2` 已写入。
- [!] 沙箱未编译：本环境无网络/Compose 依赖缓存，未跑 `assembleDebug`；上述一致性为静态核对，最终以用户本机构建+adb 测试为准。

## 7. 已知限制（P2 MVP）
- 仅做文本+图片提取，未实现 CSS 排版、分页/滚动定位精读、脚注、表格美化。
- 图片仅支持 `<img>` 直接引用，不支持 SVG/CSS 背景图。
- 进度仅记录章节序号，未记录章内行偏移。
- EPUB 解密（DRM）、字体嵌入未支持（个人非加密书足够）。
