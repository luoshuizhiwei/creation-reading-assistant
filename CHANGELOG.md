# 更新日志

本项目从现在开始使用 GitHub Release 记录大版本更新。每次大改动都应更新本文件，并在 GitHub Release 中上传可下载的电脑端应用和 Android APK。

## 未发布

- 后续大修改会先写入这里，发布时再移动到对应版本。

## v0.1.1 - 2026-06-30

### 新增

- Android 端主界面重构为 5 栏：`首页 / 书架 / 灵感 / 统计 / 我的`，布局参考 Reeden 移动端信息架构，但保留本项目纸墨/铜色视觉体系。
- 首页新增累计阅读、阅读时长、继续阅读横向卡片、灵感快捷入口和同步状态；按产品决策移除“阅读目标”模块。
- 书架改为三列书封网格，支持搜索、整卡打开阅读、本地 TXT/Markdown/EPUB 导入、重复导入标签。
- 灵感中心升级为 Android 一级入口，支持快速记录、来源摘录卡片、按书籍/标签搜索、AI 候选版本展示位。
- 阅读器 MVP 支持 TXT/Markdown/EPUB 文本渲染、目录抽屉、字号/行距/背景设置、进度保存、选中文字“记为灵感”。
- 移动端新增 SQLite schema、Capacitor Filesystem 文件保存、旧 localStorage 快照迁移和 WebDAV 同步骨架。
- “我的”页新增电脑局域网同步、WebDAV 设置、标签/分类/书单/笔记/AI 设置/数据管理入口。

### 验证

- 新增 `verify:mobile-ui`、`verify:mobile-storage`、`verify:mobile-reader`、`verify:mobile-webdav`、`verify:mobile-inspiration`，并接入 `verify:beta`。

## v0.1.0 - 2026-06-30

### 新增

- 完成产品重定位：应用主线从“小说正文写作工作台”改为“灵感中心 + 本地书库 + 阅读统计”的本地优先创作阅读助手。
- 新增全局灵感中心，支持灵感记录、标签、状态、来源卡片和 AI 候选版本。
- 新增 OpenAI-compatible AI 设置，API Key 只保存在 Electron 主进程安全存储中。
- 新增 TXT、Markdown、EPUB 本地书库和阅读器，支持阅读进度、阅读统计和“记为灵感”。
- 新增 Android 手机端 MVP，可离线记录灵感，并通过局域网与电脑端同步数据。
- 新增电脑端手机同步服务，支持局域网配对、manifest、pull、push、书籍文件下载和分块下载。

### 修复

- 修复 EPUB 真实书籍恢复阅读时可能卡在目录页或打开中的问题。
- 修复阅读设置、主题、阅读背景、Markdown 渲染、EPUB 鼠标滚轮翻页和目录折叠等阅读体验问题。
- 修复手机同步配对只显示文本码的问题：现在电脑端会生成真实二维码，并显示优先配对 URL、二维码载荷和备用局域网地址。
- 修复 Android APK 无法连接电脑端局域网 HTTP 服务的问题：新版 APK 已声明网络权限和 cleartext 局域网访问策略。

### 发布产物

- Windows 桌面端：`release-beta/win-unpacked/创作阅读助手.exe`
- Android APK：`mobile-release/creation-reading-assistant-mobile-debug.apk`

### 验证

- `npm run build`
- `npm run verify:beta:release`
- `npm run verify:sync-server`
- `npm run verify:mobile-adapter`
- `npm run mobile:build`
- `npx cap sync android`
- `mobile/android/gradlew.bat assembleDebug`
