# 本地书库与阅读体验修复 Implementation Plan

## Summary

围绕用户本地书库测试反馈，修复主题、设置、便携存储、阅读到灵感、首页搜索、TXT/Markdown/EPUB 阅读体验，并更新自动验证与 Beta checklist。

## Implementation Steps

1. 新增验证脚本：`verify:reader-settings`、`verify:portable-storage`、`verify:reading-inspiration`、`verify:reader-formats`，并挂入 `verify:beta`。
2. 扩展类型和 IPC：阅读设置、存储位置、结构化灵感来源、目录选择/迁移、阅读设置重置。
3. 改造设置页和主题系统：应用主题作用于 CSS 变量，阅读背景只影响阅读器；高级阅读记录增加中文说明。
4. 实现便携存储：优先安装目录旁 `data`，不可写时回退；设置页支持复制迁移数据/书籍目录。
5. 改造阅读到灵感：保存来源快照、来源摘录、返回阅读状态；阅读页弹出“查看灵感/继续阅读”。
6. 改造首页、搜索和书库：双主入口、首页搜索、搜索命中高亮、整行点击阅读、作者识别、重复导入标签。
7. 升级阅读器：Markdown 改用 `markdown-it` 和目录；EPUB 支持滚轮翻页、目录折叠、设置抽屉和 publisher 样式模式。
8. 跑构建、完整验证、更新 Obsidian Codex 记忆库。

## Verification

- `npm run build`
- `npm run verify:reader-settings`
- `npm run verify:portable-storage`
- `npm run verify:reading-inspiration`
- `npm run verify:reader-formats`
- `npm run verify:beta`

## Notes

- 旧数据不删除。
- AI IPC 和 AI 设置保留；独立 AiPage 后续已清理，AI 入口收敛到灵感中心候选版本工作流。
- 不引入书源、爬虫、云同步或平台发布。
