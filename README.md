# 创作阅读助手

创作阅读助手是一款本地优先的桌面应用，定位为“灵感中心 + 本地书库 + 阅读统计 + AI 润色辅助”。它不再把重点放在应用内写完整小说正文，而是帮助你记录灵感、从阅读中沉淀素材，并把灵感交给 AI 做润色、扩写或平台风格化。

## 主要功能

- 灵感中心：记录想法、标签、状态、来源摘录和 AI 候选版本。
- 本地书库：导入 TXT、Markdown、EPUB，保留阅读进度和阅读时长。
- 阅读到灵感：阅读时可把当前书籍、章节、位置和选中文字记录为结构化来源。
- AI 辅助：支持 OpenAI-compatible Base URL、API Key 和模型配置，AI 结果默认保存为候选版本，不覆盖原文。
- 手机同步：Android 手机端可通过局域网与电脑端同步灵感、书库、阅读进度和阅读统计。

## 下载

每次正式版本会发布到 GitHub Releases：

[GitHub Releases](https://github.com/luoshuizhiwei/creation-reading-assistant/releases)

Release 页面会提供：

- `creation-reading-assistant-windows-win-unpacked.zip`：Windows 电脑端免安装包。
- `creation-reading-assistant-mobile-debug.apk`：Android 手机端测试 APK。
- `SHA256SUMS.txt`：下载文件校验值。

## 本地开发

安装依赖：

```powershell
npm install
npm install --prefix mobile
```

运行验证：

```powershell
npm run build
npm run verify:beta
```

本地打包电脑端：

```powershell
npm run dist:beta:offline
```

本地打包 Android APK：

```powershell
npm run mobile:build
Set-Location mobile
npx cap sync android
Set-Location android
.\gradlew.bat assembleDebug
```

## 发布规则

以后每次大的修改都要：

1. 更新 `CHANGELOG.md`。
2. 运行验证命令。
3. 提交并推送到 GitHub。
4. 如需提供下载包，创建 `v*` 标签触发 GitHub Actions。

详细流程见：[GitHub 发布流程](docs/GITHUB_RELEASE_PROCESS.md)。

## 安全说明

- 不要提交 API Key、账号凭证或本地私密数据。
- AI Key 只应保存在 Electron 主进程安全存储中。
- 当前 Android APK 是测试用 debug 包；正式公开分发前应增加签名 release APK。
