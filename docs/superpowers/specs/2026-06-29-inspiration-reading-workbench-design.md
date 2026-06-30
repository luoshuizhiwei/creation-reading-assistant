# 灵感与阅读工作台重定位设计

## 背景

小说正文最终会发布到番茄、起点、刺猬猫等平台，因此本应用不再承担“完整小说正文主写作阵地”的职责。现有项目已有本地书库、TXT/Markdown/EPUB 阅读、进度保存、阅读时间统计、搜索、备份和设置能力，这些能力适合保留并转向“创作前后的辅助台”。

## 目标

把应用第一阶段重定位为本地优先的“灵感记录 + AI 润色/扩展 + 本地小说阅读/计时”桌面工具。用户打开应用后应优先看到灵感箱、AI 助手、本地书库和阅读统计，而不是新建卷/章节。

## 产品结构

- 首页：三块主入口，分别是灵感箱、AI 润色、本地书库，并保留阅读统计和设置入口。
- 灵感中心：全局灵感箱，不依赖旧小说项目；支持类型、状态、标签、平台标签、正文、AI 候选版本和来源书籍。
- AI 助手：OpenAI-compatible 接口，主进程保存密钥并发起请求；输出作为候选版本保存，不直接覆盖原灵感。
- 阅读模块：继续支持本地 TXT/Markdown/EPUB 导入、阅读、进度恢复和有效阅读时长统计；新增从阅读页创建灵感。
- 旧写作工作台：从 renderer 入口、旧页面、旧服务、旧 IPC 和旧导出流程中删除；不自动删除用户磁盘上的旧项目数据。

## 数据与接口

新增 `InspirationItem`、`InspirationVariant`、`AISettings`、`AIRunInput`、`AIRunResult` 类型。灵感数据保存为 `appDataRoot()/inspirations.json`，AI 密钥保存为 `appDataRoot()/ai-secrets.json`，密钥通过 Electron `safeStorage` 加密，renderer 只看到 `hasApiKey`。

新增 IPC 命名空间：

- `inspiration:list/create/read/update/delete/addVariant`
- `ai:getSettings/updateSettings/saveApiKey/clearApiKey/test/run`

全局搜索会扩展到全局灵感；阅读页创建灵感会保存 `sourceBookId` 和 `sourceLocation`。

## 非目标

- 不做云同步。
- 不做平台发布。
- 不做书源、抓站、爬虫或自动获取网络小说。
- 不自动删除或迁移用户磁盘上的旧小说项目。
- 第一版不把旧项目内 ideas 卡片自动迁移到全局灵感箱。

## 验收标准

- 首页主入口是灵感箱、AI 助手、本地书库和阅读统计。
- 灵感可以新建、编辑、筛选、删除，重启后仍存在。
- AI 未配置时有明确提示；配置后可对灵感润色/扩写/改风格并保存候选版本。
- API Key 不出现在 `settings:get` 返回值和 renderer 类型面。
- 阅读模块原有导入、阅读、统计不回退。
- TXT/Markdown/EPUB 阅读页能把当前阅读上下文记录成灵感。
- renderer 不再暴露旧项目、章节、资料卡或项目导出能力。
