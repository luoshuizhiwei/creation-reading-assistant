# Android 原生 Legado 阅读内核采用方案

> **历史快照（mobile/Capacitor，非当前 android/ 架构）**：本文描述
> `mobile/android/legado-reader-core` 与 React/Capacitor 的组合方案。当前独立原生
> Android 不依赖该模块，执行依据见
> [`native-android-reader.md`](native-android-reader.md)。

状态：TXT、Markdown、EPUB 默认使用 Android 原生 `native-legado` 内核；兼容 Web 阅读器只在本次原生打开失败时兜底

日期：2026-07-19

## 1. 当前决策

- Android 本地 TXT、Markdown、EPUB 默认进入独立的 `NativeReaderActivity`，不再把 WebView 阅读器作为首选。
- Markdown 会先转换为稳定的原生可分页文本，同时保留标题层级、列表、任务列表、引用、代码、表格、链接和图片说明；不创建全书 WebView DOM。
- 书籍详情不再暴露 `native-legado`、`legacy`、`v2` 等工程选项，用户只需要点击阅读。
- 原生打开失败会自动使用兼容 Web 阅读器，只影响当前这次打开；瞬时桥接故障不会永久降级该书。
- 旧 V2 源码保留作迁移期对照，但已退出生产路由和构建产物，不能与原生阅读器竞争进度写入。
- 桌面端不在本阶段范围内。

## 2. 许可与来源

`mobile/android/legado-reader-core` 是隔离的 GPL-3.0-only Android library module。采用的上游来源、固定提交、改动清单分别记录在：

- `mobile/android/legado-reader-core/UPSTREAM.md`
- `mobile/android/legado-reader-core/PATCHES.md`
- `mobile/android/legado-reader-core/LICENSE`
- `mobile/LICENSE`

当前实现不再是早期文档所述的“纯净室、不得引入 GPL 源码”方案。正式分发前必须继续保留完整 GPL 许可、源码获取方式、上游归属和修改说明。

## 3. 运行架构

```text
React / Capacitor 书架
        |
        | NativeReaderPlugin.openBook(...)
        v
NativeReaderActivity
        |
        +-- TXT: TextBookLoader -> TextPaginator -> LegadoTextReaderView
        |
        +-- Markdown: TextBookLoader -> MarkdownBookParser -> LegadoTextReaderView
        |
        +-- EPUB: EpubDocumentLoader -> EpubReaderDocument -> LegadoEpubReaderView
        |
        +-- NativeReaderCheckpointJournal（崩溃恢复）
        +-- NativeReaderActionJournal（书签/笔记/灵感回传）
```

React 层负责书库、同步和持久数据；原生 Activity 负责加载、排版、分页、触控和阅读菜单。退出原生阅读器后，位置、会话时长、设置和标注动作由 Capacitor 插件回传并写入移动端存储。

## 4. EPUB 懒加载设计

旧实现打开 EPUB 时会立即解析全部 spine、拼接全书正文并把全部图片读入内存。真实 8 MB EPUB 在手机上曾需要约 22 秒，进程约 286 MB PSS / 477 MB RSS。

当前实现改为：

- 使用 epublib `readEpubLazy()`，初次打开只读取容器、目录、spine 和资源元数据。
- `EpubChapter` 只保存章节索引、标题、href、层级；正文和 HTML 由 `chapterContent(index)` 按需解析。
- 章节缓存采用 access-order LRU：最多 4 章、约 2,000,000 字符；当前章在裁剪时优先保留。
- 每章独立锁，避免搜索、预取和前台翻页并发解析同一章。
- 单线程预取相邻章节，不阻塞当前页面显示。
- 图片通过 ZIP 流读取，先 `inJustDecodeBounds`，再按页面尺寸计算 `inSampleSize`，禁止持有全书图片 ByteArray。
- `content://` 源先复制到有大小上限的临时文件；退出时关闭 ZIP、清空缓存并删除自有临时文件。
- 全书搜索在后台逐章读取，可被新搜索或 Activity 销毁中止。

## 5. 原生阅读能力

- TXT：章节识别、中文排版、左右点击/滑动翻页、进度恢复。
- Markdown：标题目录、列表、引用、代码、表格、链接/图片说明转换、原生分页、搜索与位置恢复。
- EPUB：目录、章节跳转、按章分页、图片、左右点击/滑动翻页、位置恢复。
- 共享：字号、行距、边距、字重、白纸/暖纸/护眼/夜间主题、搜索、书签、笔记、灵感、系统返回键。
- 点击屏幕中央显示/隐藏菜单；菜单和二级页面优先消费系统返回键。
- 页面正文严格裁剪到可见高度，不绘制下一页的半行文字到页脚上方。

## 6. 兼容与回退

- 旧阅读位置继续通过统一 Locator/旧字段双写，不批量破坏历史数据。
- 原生 checkpoint 可在进程被系统结束后恢复章节、页码、进度、设置和 session。
- 原生不支持的来源或单次原生打开异常使用兼容 Web 阅读器；书籍详情不提供实现层切换。
- V2 不再进入生产运行时；兼容 Web 阅读器只作为故障兜底，不与原生 Activity 同时写同一本书的进度。

## 7. 验收门槛

- TXT、Markdown、EPUB 能打开、翻页、返回、重进恢复，且强制结束应用后仍能恢复最近 checkpoint。
- 目录跳转、全书搜索、书签、笔记、灵感、主题设置可用。
- 大 EPUB 首屏不解析全书、不加载全书图片；连续翻页后内存保持有界。
- 无 OOM、ANR、FATAL；加载和章节解析失败必须显示可退出的错误状态。
- TXT/Markdown/EPUB 不出现半页夹缝、上一页残影、页脚正文裁切或点击循环。
- Gradle 核心测试、移动端单测、完整 Beta 门禁、Capacitor sync 和 APK 构建全部通过。

## 8. 后续工作

1. 为 Markdown 增加更丰富的原生视觉样式；当前版本优先保证语义、目录、分页和稳定性。
2. 为 EPUB 搜索增加可见进度和取消按钮；当前 8 MB 样本全书搜索约 10 秒。
3. 继续扩充无封面、超长单章、复杂 CSS/SVG/字体和更多中文内部路径 EPUB 测试语料。
4. 在 360/390/430dp 和更多 Android 品牌真机上补兼容矩阵。
5. 为正式 Release 建立稳定签名，并完成 GPL 分发材料专项检查。
