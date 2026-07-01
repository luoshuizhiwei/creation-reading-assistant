# 更新日志

本项目从现在开始使用 GitHub Release 记录大版本更新。每次大改动都应更新本文件，并在 GitHub Release 中上传可下载的电脑端应用和 Android APK。

## 未发布

- 后续大修改会先写入这里，发布时再移动到对应版本。

## v0.1.6 - 2026-07-02

### Android 体验迭代

- 继续向 Legado / 开源阅读类移动阅读体验靠拢：书架新增“全部 / 在读 / 已下载 / 待下载”分段筛选，支持最近阅读、书名、进度排序。
- 书架新增三列封面网格和紧凑列表切换，书卡显示格式、阅读进度条、下载状态和重复导入标签，减少“网页卡片感”。
- 阅读页新增章节/进度胶囊，滚动时实时识别当前章节，让用户知道自己在书里的位置。
- 阅读正文支持轻触手势：左侧跳上一章、右侧跳下一章、中间唤起/隐藏菜单；菜单显示时给出轻量提示。
- 阅读设置抽屉新增滚动/分页预留、加粗、重置入口，保留字号、行距、边距和背景设置，为后续分页/仿真翻页继续扩展。

### 验证

- 新增 `verify:mobile-reading-experience` 并接入 `verify:beta`，专门守住书架布局、阅读手势、章节进度和阅读设置抽屉。

## v0.1.5 - 2026-07-02

### 修复

- 修复 Android 点击“扫码”后空白的问题：改为 App 内可见视频扫码弹层，使用 WebView 摄像头预览和二维码识别；失败时保留“粘贴配对 URL / 二维码载荷”备用路径。
- 修复“立即同步”后因大书文件下载导致卡顿或退出的风险：同步只同步元数据、灵感、进度和 session，正文改为书架单本“下载正文”。
- 新增同步日志和待下载正文数量，方便真机排查同步失败或退出前状态。
- 修复 EPUB/TXT/Markdown 文件保存策略：TXT/Markdown 继续按 UTF-8 文本保存，EPUB 按二进制/base64 保存，不再用 `blob.text()` 破坏文件。
- 重构手机阅读页：正文使用独立滚动容器，支持滚动进度计算、返回前保存、点击隐藏/唤起顶部栏和底部菜单。
- 阅读设置移入底部抽屉，目录改为抽屉；阅读页默认更接近移动阅读 App，不再让控件长期挤占正文。

### 验证

- 新增 `verify:mobile-scan`、`verify:mobile-sync-stability`、`verify:mobile-reader-layout` 并接入 `verify:beta`。

### 发布产物

- Windows 安装版：`release-artifacts/creation-reading-assistant-windows-setup-v0.1.5.exe`
- Windows 免安装版：`release-artifacts/creation-reading-assistant-windows-win-unpacked-v0.1.5.zip`
- Android APK：`release-artifacts/creation-reading-assistant-mobile-debug-v0.1.5.apk`

## v0.1.4 - 2026-07-01

### 修复

- 修复部分 Android 手机上扫码失败的问题：当 Google 系统扫码模块不可用时，自动切换到本地相机扫码，并把冗长英文错误改成中文排查提示。
- 修复手机同步后可能直接退出的问题：书籍正文不再整本写入 `localStorage`，改为只通过 Capacitor Filesystem 保存，降低大书同步后的 WebView 崩溃风险。
- 修复同步完成后反馈不明确的问题：现在会明确提示“已上传”的手机数据、电脑返回的数据和书籍文件上传/下载结果。
- 优化移动端首页、书架和阅读器的宽度约束，避免页面像网页一样横向溢出、卡片被放大裁切、底部导航需要滑到底才看到。
- 优化阅读页控制区：改为阅读 App 风格的底部面板，保留阅读时长、进度、速度、灵感数、保存进度、夜间模式、目录和字号/行距设置。

### 工程

- 新增 `release:clean-local`：本地 `release-artifacts` 只保留当前版本安装包/APK，旧版本交给 GitHub Release 保存，减少电脑磁盘占用。
- 加强 `verify:mobile-adapter`、`verify:mobile-storage`、`verify:mobile-ui`，覆盖扫码降级、本地文件存储和移动端布局守门。

### 发布产物

- Windows 安装版：`release-artifacts/creation-reading-assistant-windows-setup-v0.1.4.exe`
- Windows 免安装版：`release-artifacts/creation-reading-assistant-windows-win-unpacked-v0.1.4.zip`
- Android APK：`release-artifacts/creation-reading-assistant-mobile-debug-v0.1.4.apk`

## v0.1.3 - 2026-07-01

### 新增

- 手机端局域网同步补齐“手机 → 电脑”方向：同步时会先上传手机本地新增/修改的灵感、书籍元数据、阅读进度和阅读 session，再拉取电脑端最新数据。
- 手机端同步新增书籍文件上传：手机导入的 TXT/Markdown/EPUB 会随同步上传到电脑端书库目录，电脑端不再只看到一条无法打开的空书籍记录。
- 电脑端新增安装版构建入口：`npm run dist:beta:installer` 会生成 Windows 安装包，同时保留免安装目录。
- GitHub Release 发布流程新增 Windows 安装包资产：以后 Release 会同时提供安装版、免安装包和 Android APK。

### 修复

- 修复手机从电脑同步书籍后只能看到占位正文的问题：现在会下载电脑端书籍文件并保存到手机本地，离线也能重新打开。
- 修复手机端同步反馈不清楚的问题：同步完成提示会区分已上传的本地数据、上传的手机书籍文件、拉取的数据和下载的电脑书籍文件。

### 验证

- 加强 `verify:mobile-adapter`、`verify:mobile-storage`、`verify:mobile-reader`、`verify:sync-server`，覆盖手机端双向同步、书籍文件上传/下载和真实本地文件读取。
- 新增 `verify:installer-release`，检查安装版构建入口、GitHub Release 安装包上传和本地安装包产物。

### 发布产物

- Windows 安装版：`release-beta/创作阅读助手-0.1.3-win-x64.exe`
- Windows 免安装版：`release-beta/win-unpacked/创作阅读助手.exe`
- Android APK：`mobile/android/app/build/outputs/apk/debug/app-debug.apk`

## v0.1.2 - 2026-07-01

### 修复

- 修复 Android 端“扫码连接电脑”实际上只能粘贴文本的问题：新增原生二维码扫描按钮，支持相机权限请求和扫码模块安装提示。
- 修复手机端连接电脑容易失败的问题：现在会解析电脑端二维码载荷里的所有备用局域网地址，并逐个尝试连接，失败时显示最后一个错误和排查提示。
- 修复手机阅读器“记为灵感”缺少页面内反馈的问题：保存后会在阅读页弹出提示，支持“查看灵感 / 继续阅读”，选中文字会进入来源摘录。

### 验证

- 加强 `verify:mobile-adapter`、`verify:mobile-reader`、`verify:mobile-inspiration`，覆盖扫码入口、多地址配对和阅读记灵感反馈。

### 发布产物

- Android APK：`mobile-release/creation-reading-assistant-mobile-debug.apk`

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
