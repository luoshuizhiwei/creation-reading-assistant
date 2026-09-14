# GitHub 发布流程实现计划

> **给 agentic workers 的要求：** 实施本计划时必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，并按任务逐项勾选执行。

**目标：** 把项目纳入 Git/GitHub 管理，并让每次大版本更新都有中文更新说明、可追踪提交，以及可在 GitHub Release 下载的 Windows 电脑端应用和 Android APK。

**架构：** 源码进入 Git，生成的二进制产物不进入 Git。每次推送 `v*` 标签时，由 GitHub Actions 在 Windows 环境中重新构建桌面端和 Android APK，生成压缩包、APK 和 SHA256 校验文件，并上传到 GitHub Release。

**技术栈：** Git、GitHub 私有仓库、GitHub Actions `windows-latest`、Node.js 24、npm、Electron Builder、Capacitor Android、Gradle/JDK 17、`softprops/action-gh-release`。

---

### 任务 1：发布说明和忽略规则

**文件：**
- 新建：`CHANGELOG.md`
- 新建：`docs/GITHUB_RELEASE_PROCESS.md`
- 修改：`.gitignore`

- [x] **步骤 1：新增中文更新日志**

创建 `CHANGELOG.md`，保留“未发布”区域，并记录当前 `v0.1.0` 基线。内容需要说明桌面端、Android 手机端、局域网同步、二维码配对和当前下载产物。

- [x] **步骤 2：新增 GitHub 发布流程说明**

创建 `docs/GITHUB_RELEASE_PROCESS.md`，写清楚首次设置、日常大更新、打 tag 发布，以及用户从哪里下载电脑端和 APK。

- [x] **步骤 3：扩展 `.gitignore`**

忽略 `mobile-release/`、`.github-release/`、`.claude/` 和本地生成压缩包，避免二进制产物进入 Git 历史。

- [x] **步骤 4：检查文档**

运行：

```powershell
rg -n "TODO|TBD|密钥|sk-" CHANGELOG.md docs/GITHUB_RELEASE_PROCESS.md .gitignore
```

预期：不出现占位内容或密钥。

### 任务 2：发布 readiness 守门

**文件：**
- 新建：`scripts/verify-release-readiness.mjs`
- 修改：`package.json`

- [x] **步骤 1：新增发布 readiness 脚本**

脚本检查 `.github/workflows/release.yml`、`CHANGELOG.md`、`docs/GITHUB_RELEASE_PROCESS.md`、`package-lock.json`、`mobile/package-lock.json` 是否存在，并确认 `.gitignore` 已忽略发布产物。

- [x] **步骤 2：新增 npm script**

添加：

```json
"verify:release-readiness": "node scripts/verify-release-readiness.mjs"
```

- [x] **步骤 3：运行守门**

运行：

```powershell
npm run verify:release-readiness
```

预期输出：`[verify-release-readiness] GitHub release pipeline guards verified.`

### 任务 3：GitHub Actions 发布工作流

**文件：**
- 新建：`.github/workflows/release.yml`

- [x] **步骤 1：新增触发条件**

推送 `v*` 标签时自动发布；也允许手动 `workflow_dispatch` 构建。

- [x] **步骤 2：配置构建环境**

使用 `windows-latest`、`actions/checkout`、`actions/setup-node` 的 Node 24、`actions/setup-java` 的 Temurin 17，并执行根目录 `npm ci` 和 `npm ci --prefix mobile`。

- [x] **步骤 3：验证并打包电脑端**

运行：

```powershell
npm run verify:release-readiness
npm run verify:beta
npm run dist:beta:offline
npm run verify:beta:release
```

然后把 `release-beta/win-unpacked` 压缩为 `release-artifacts/creation-reading-assistant-windows-win-unpacked.zip`。

- [x] **步骤 4：验证并打包 Android APK**

运行：

```powershell
npm run mobile:build
npx cap sync android
.\gradlew.bat assembleDebug
```

把 `mobile/android/app/build/outputs/apk/debug/app-debug.apk` 复制为 `release-artifacts/creation-reading-assistant-mobile-debug.apk`。

- [x] **步骤 5：生成校验值并上传构建产物**

为所有发布文件生成 SHA256，并通过 `actions/upload-artifact` 上传。

- [x] **步骤 6：发布 GitHub Release**

当 `github.ref` 是 tag 时，使用 `softprops/action-gh-release@v2` 上传 Windows zip、APK 和 SHA256 文件。Release 标题使用中文，正文读取 `CHANGELOG.md`。

### 任务 4：初始化 Git 和首次本地提交

**文件：** 所有需要进入仓库的源码、配置和文档；排除被忽略的构建产物。

- [x] **步骤 1：初始化仓库**

运行：

```powershell
git init
git status -sb
```

- [x] **步骤 2：暂存目标文件**

运行：

```powershell
git add .
git status -sb
```

确认 `node_modules/`、`out/`、`release-beta/`、`mobile-release/` 等生成目录没有被暂存。

- [x] **步骤 3：提交基线**

运行：

```powershell
git commit -m "chore: prepare GitHub release pipeline"
```

### 任务 5：GitHub 私有仓库和首次发布

**文件：** Git remote 配置和 GitHub Release。

- [x] **步骤 1：安装或配置 GitHub CLI**

已安装 GitHub CLI，但 `gh auth login` 因设备码/网络问题未成功。最终改用 Git Credential Manager 的 HTTPS 认证推送。

- [x] **步骤 2：推送到私有 GitHub 仓库**

用户创建仓库：

```text
https://github.com/luoshuizhiwei/creation-reading-assistant.git
```

本地设置 remote 并推送：

```powershell
git remote add origin https://github.com/luoshuizhiwei/creation-reading-assistant.git
git push -u origin main
```

- [x] **步骤 3：创建第一个 tag**

运行：

```powershell
git tag -a v0.1.0 -m "v0.1.0"
git push origin v0.1.0
```

- [ ] **步骤 4：确认 Release 下载产物**

GitHub Actions 完成后，Release 页面应包含：

- `creation-reading-assistant-windows-win-unpacked.zip`
- `creation-reading-assistant-mobile-debug.apk`
- `SHA256SUMS.txt`

如果工作流失败，优先检查 Actions 日志中 `npm ci`、`npm run dist:beta:offline`、`gradlew.bat assembleDebug` 或 Release 权限相关错误。
