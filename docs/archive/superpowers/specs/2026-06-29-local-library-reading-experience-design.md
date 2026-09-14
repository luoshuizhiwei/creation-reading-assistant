# 本地书库与阅读体验修复设计

## Summary

本阶段把应用继续收拢到“灵感中心 + 本地书库”的阅读取材工作流。首页只保留两个主入口，并把搜索显性化；AI 不再作为首页模块，而是作为灵感详情里的加工能力。阅读器优先照顾读者体验：主题和书籍背景分离、EPUB 支持滚轮翻页和目录折叠、Markdown 使用完整渲染器、阅读页记录灵感不强制跳走。

## Key Decisions

- 应用主题属于全局外观，书籍背景属于阅读器；阅读背景提供白纸、暖纸、护眼、夜间。
- EPUB 默认保留原书样式；只有用户选择“统一阅读样式”时才强制注入字号和行距。
- 数据目录优先使用安装目录旁 `data`；若不可写则回退，设置页允许迁移数据目录和书籍目录。迁移只复制，不自动删除旧目录。
- “记为灵感”保存结构化来源快照，正文保持给用户写想法；选中文本进入来源摘录。
- 进入灵感中心后保留“返回阅读”入口，恢复原书和原进度，避免阅读流程被打断。
- TXT/Markdown/EPUB 重复导入不覆盖旧记录，但新增重复导入标签辅助辨认。

## Interfaces

- `ReaderSettings`: `appTheme`、`readerBackground`、`epubStyleMode`。
- `StorageSettings`: `dataDirectory`、`libraryDirectory`、`storageMode`、`lastMigratedAt`。
- `InspirationSourceSnapshot`: 保存书名、作者、格式、章节/位置、进度、摘录、EPUB href/CFI。
- 新增 IPC: `settings:resetReaderSettings`、`settings:chooseDataDirectory`、`settings:chooseLibraryDirectory`、`settings:migrateDataDirectory`、`settings:migrateLibraryDirectory`、`storage:getLocations`。

## Acceptance

- 首页显示灵感中心、本地书库和显性搜索，不显示独立 AI 主入口。
- TXT 作者识别、重复导入标签、整行点击阅读可用。
- Markdown 标题/目录/表格/代码等由 `markdown-it` 渲染，标题随字号缩放。
- EPUB 支持滚轮/键盘/按钮翻页，目录可收起，设置在抽屉里，默认保留原书样式。
- 阅读页记录灵感后可继续阅读，也可查看灵感；灵感详情显示来源卡片和来源摘录。
- 新增验证脚本和 `verify:beta` 全部通过。
