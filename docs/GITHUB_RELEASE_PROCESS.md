# GitHub 发布流程

本项目的发布规则：大修改要进入 GitHub，更新说明要可追踪，电脑端应用和 Android APK 要能从 GitHub Release 下载。

## 第一次设置

1. 安装 GitHub CLI。

```powershell
winget install --id GitHub.cli -e
```

2. 登录 GitHub。

```powershell
gh auth login
gh auth status
```

3. 创建私有仓库并推送当前项目。

```powershell
git init
git add .
git commit -m "chore: prepare GitHub release pipeline"
gh repo create creation-reading-assistant --private --source . --remote origin --push
```

如果你已经在 GitHub 网页上创建了仓库，则改用：

```powershell
git remote add origin https://github.com/<your-name>/creation-reading-assistant.git
git push -u origin main
```

## 每次大修改的本地流程

1. 修改代码。
2. 更新 `CHANGELOG.md` 的“未发布”区域。
3. 运行验证。

```powershell
npm run build
npm run verify:beta
npm run verify:release-readiness
```

4. 提交并推送。

```powershell
git status -sb
git add .
git commit -m "feat: 简短说明本次大改"
git push
```

## 发布新版本

1. 把 `CHANGELOG.md` 中的“未发布”内容移动到新版本标题下，例如 `v0.1.1 - 2026-07-01`。
2. 提交 changelog。

```powershell
git add CHANGELOG.md
git commit -m "docs: update changelog for v0.1.1"
git push
```

3. 打 tag 并推送。

```powershell
git tag -a v0.1.1 -m "v0.1.1"
git push origin v0.1.1
```

4. GitHub Actions 会自动构建并创建 GitHub Release。

Release 页面会提供这些下载文件：

- `creation-reading-assistant-windows-setup.exe`：Windows 电脑端安装包，适合普通用户安装到开始菜单和桌面快捷方式。
- `creation-reading-assistant-windows-win-unpacked.zip`：Windows 电脑端免安装包。
- `creation-reading-assistant-mobile-debug.apk`：Android 手机端测试 APK。
- `SHA256SUMS.txt`：下载文件校验值。

## 手动本地打包

如果 GitHub Actions 暂时不可用，可以在本地生成当前候选包：

```powershell
npm run dist:beta:installer
npm run mobile:build
Set-Location mobile
npx cap sync android
Set-Location android
.\gradlew.bat assembleDebug
Set-Location ..\..
Copy-Item mobile\android\app\build\outputs\apk\debug\app-debug.apk mobile-release\creation-reading-assistant-mobile-debug.apk -Force
```

## 注意事项

- 不要把 `release-beta/`、`mobile-release/`、`node_modules/`、`out/` 提交到 Git。
- 不要把 API Key、密钥、账号凭证写入仓库、Release notes 或日志。
- Android 当前上传的是 debug APK，适合测试安装；正式对外分发前应再加入签名 release APK。
- 如果手机无法连接电脑端同步服务，先确认手机和电脑在同一 Wi-Fi，再检查 Windows 防火墙，并尝试电脑端显示的备用配对地址。
