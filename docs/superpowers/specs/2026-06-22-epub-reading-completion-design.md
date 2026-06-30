# EPUB 阅读体验补齐设计

## 背景

“小说作者工作台”当前已经支持 TXT、Markdown、EPUB 的本地导入与阅读，并通过 `ReadingLocation` / `ReadingSession` 记录进度和有效阅读时长。Beta 基线命令 `npm run verify:beta` 已通过。现有缺口集中在 EPUB：导入时只复制文件并以文件名作为标题，阅读器可以翻页和保存 CFI，但没有目录面板；全局搜索对 EPUB 只搜索书名、路径，不搜索正文；阅读统计页仍缺少自动化 UI 验收。

## 目标

本轮把 EPUB 从“能打开”升级为“可管理、可跳转、可检索”，并补一条阅读统计 UI 验收链路。完成后，用户导入 EPUB 时能看到更像正式书籍的书库信息，阅读时能通过目录跳转，全局搜索能命中 EPUB 正文片段，统计页有自动化检查防止基础渲染回退。

## 范围

### EPUB 元数据与封面

- `LibraryBook` 增加可选元数据字段：作者、简介、语言、出版社、封面路径。
- 导入 EPUB 时解析内部 metadata；如果解析失败，保留现有文件名标题回退。
- 封面提取到应用数据目录下的安全缓存位置，书库卡片使用该路径展示封面。
- 不做元数据编辑器，不修改 EPUB 原文件。

### EPUB 目录面板

- `ReaderEpubPayload` 增加目录列表。
- `EpubReaderPage` 显示目录区域。
- 点击目录项调用 `rendition.display(href)`；后续仍通过现有 `relocated` 事件保存 CFI 和进度。
- 目录为空或解析失败时显示“未检测到目录”，不阻塞阅读。

### EPUB 正文搜索

- 导入 EPUB 后生成轻量 sidecar 搜索索引，放在 `AppLibrary/search-index/<bookId>.json`。
- 索引按章节保存标题、href、纯文本内容片段。
- 全局搜索对 EPUB 使用 sidecar 索引，命中时返回 `book` 类型结果，snippet 显示正文上下文。
- 点击 EPUB 正文搜索结果时打开该书并传递目标 href；阅读器加载后跳转到目标章节。
- 若 sidecar 缺失或损坏，搜索回退到书名/路径，不影响现有功能。

### 阅读统计 UI 验收

- 新增一个轻量脚本检查统计页源码中关键 UI 文案与数据字段映射。
- 新增 `npm run verify:stats-ui`。
- `npm run verify:beta` 保持当前职责，可在脚本中提示单独运行统计 UI 验收。

## 非目标

- 不做 EPUB 全文高亮。
- 不做跨章节精确字符级定位。
- 不做云同步、AI、插件系统。
- 不改变 TXT/Markdown 的导入、阅读、搜索、统计语义。
- 不引入数据库或服务端。

## 架构

主进程继续负责本地文件读取、EPUB 解析、索引生成和搜索。Renderer 只通过 preload 暴露的 IPC API 读取结构化数据，不直接访问 Node/Electron 能力。EPUB 解析复用已安装的 `epubjs` 传递依赖 `jszip` 和 `@xmldom/xmldom`，避免引入新的重依赖。

新增一个主进程辅助模块 `electron/main/epub-metadata.ts`，集中处理 EPUB zip、OPF、metadata、cover、TOC 和正文索引解析。`electron/main/index.ts` 只负责编排：导入时调用解析器、写入库索引、写入 sidecar；搜索时读取 sidecar；打开 EPUB 时返回目录与可选目标 href。

## 数据模型

`LibraryBook` 增加：

- `author?: string`
- `description?: string`
- `language?: string`
- `publisher?: string`
- `coverPath?: string`
- `epub?: { toc?: EpubTocItem[]; searchIndexedAt?: ISODateString; searchIndexPath?: string }`

新增：

- `EpubTocItem`：`id`、`label`、`href`、`level`。
- `EpubSearchIndex`：`version`、`bookId`、`updatedAt`、`items`。
- `EpubSearchIndexItem`：`id`、`title`、`href`、`text`。

`SearchTarget` 增加 `epubHref?: string`，用于搜索结果打开后跳转到章节。

## 错误处理

- EPUB metadata、cover、TOC、正文索引任一阶段失败，都不让导入失败；只记录日志并保存最小书籍记录。
- 封面提取只允许写入 `AppLibrary/covers`，避免任意路径写入。
- sidecar 搜索失败时跳过正文搜索，保留标题搜索结果。
- 目录跳转失败时在页面上显示错误 toast，并保留当前阅读位置。

## 验收标准

- `npm run build` 通过。
- `npm run verify:beta` 通过。
- `npm run verify:stats-ui` 通过。
- EPUB 导入后，书库卡片能显示解析到的标题、作者和封面；解析不到时仍显示文件名。
- EPUB 阅读页显示目录；点击目录项能跳转并继续保存 CFI 进度。
- 全局搜索能命中 EPUB 正文 sidecar 片段；点击结果能打开 EPUB 并跳到对应 href 或至少打开该书。
- TXT/Markdown 书籍导入、阅读、正文搜索仍按原逻辑工作。

## 当前约束

当前工作目录不是 Git 仓库，无法执行设计文档和实现计划中的 commit 步骤。后续实现用文件变更清单、验证命令输出和 Obsidian Codex 记忆收尾替代 commit 记录。
