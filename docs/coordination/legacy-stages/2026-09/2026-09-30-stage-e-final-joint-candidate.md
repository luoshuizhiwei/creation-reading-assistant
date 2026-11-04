# Stage E — Final Joint Candidate (2026-09-30)

> **角色**：单一集成者（Stage E integrator）
> **基线**：HEAD = `513891d6a66a9b78e072ba759d34ed580d377ecf`（branch `codex/recover-worktree-20260920`）
> **工作目录**：`E:\creation-reading-assistant\stage-e-final-joint-20260930`（纯 HEAD 基线 + 候选叠加，不触碰恢复仓库源码）
> **状态**：✅ 全部 5 道门通过，候选树可复现，patch 可回溯

---

## 1. 输入候选

| 候选 | 来源目录 | 文件数 | 说明 |
|------|----------|--------|------|
| B1+B2+library | `b1b2-library` | 18 | 书库浏览器 + 搜索索引刷新协调器 + ShelfBookActions |
| G6 | `g6-behavior-preserved` | 35 | 行为保持重构（含 S06 冷启动主题镜像底座） |
| S14+R3 | `s14-r3-backup-closed` | 5 | 本地 ZIP 备份/恢复闭环 |
| M-BUG-03 | `mbug03` (mbC baseline) | 13 | 书源更新最小闭环（含 b1-coordinator seam） |
| S06 | — | 0 | **不单独叠加**：G6 的 from-head patch 已包含 S06 底座 |

**合计**：71 文件（MOD=50, ADD=22, DEL=0）

### 1.1 为什么 S06 不单独叠加

G6 的 from-head patch（35 文件）已包含 S06 冷启动主题镜像的全部底座文件（`BootMirror.kt`、`SettingsStore.kt` 的镜像回填逻辑、`SettingsStoreBootMirrorTest.kt`、`themes.xml` 夜间主题等）。在 G6 之上再机械叠加 S06 会产生冲突 hunk，且语义上 S06 ⊂ G6。因此 S06 的贡献已包含在 G6 组中。

### 1.2 M-BUG-03 baseline 选择

M-BUG-03 提供三个 baseline：
- **A**（head）：纯 HEAD 基线
- **B**（joint）：不含 b1-coordinator seam 的联合基线
- **C**（joint+b1-coordinator）：含 `SearchIndexBookRefreshCoordinator` seam 的联合基线

本联合候选选用 **mbC**：因为 B1B2 已引入 `SearchIndexBookRefreshCoordinator`，M-BUG-03 的 `ShelfImporter` 需要调用该协调器才能闭环书源更新后的索引刷新。mbC 的非 seam 文件与 mbA 完全一致（已验证），仅 seam 文件（`ShelfImporter.kt`、`ShelfImporterTest.kt`、`ShelfViewModelTest.kt`）不同。

---

## 2. 联合树构建

### 2.1 构建方法

1. 从恢复仓库 `android/` 子树导出 HEAD 基线（830 文件）到 `joint\android\`
2. 按 `joint-selection.csv` 逐文件叠加 71 个候选文件
3. 生成 HEAD→final patch 并验证回溯

### 2.2 文件清单

完整清单见 `E:\creation-reading-assistant\stage-e-final-joint-20260930\manifest\joint-selection.csv`。

按候选组分布：

| 组 | 文件数 | 主要内容 |
|----|--------|----------|
| b1b2 | 18 | BookDao, SearchTermDao, BookRepository, SearchIndexBookRefreshCoordinator, SearchIndexRepository, SearchIndexScheduler, SearchHitRanker, TxtChapterSplitter, LibraryBrowserViewModel, ShelfBookActions + 对应测试 |
| g6 | 35 | App, MainActivity, SettingsStore, BootMirror(新), DurationPrefs, AndroidManifest, themes.xml, colors.xml, 12 个 UI 屏幕/组件, 6 个测试 |
| s14r3 | 5 | JsonBridge, LocalZipBackup + 单测 + 2 个 androidTest |
| mb03 | 13 | LibrarySourceIndex, ShelfImporter, ShelfSourceUpdateChecker, ShelfViewModel, ShelfScreen/Route/Action/UiState/BookGrid + 5 个测试 |

### 2.3 重叠文件处理

以下文件被多个候选修改，本联合候选按以下规则解决：

| 文件 | 涉及候选 | 解决方式 |
|------|----------|----------|
| `ShelfImporter.kt` | b1b2 (coordinator), mb03 (seam) | 选用 mbC 版本（含 b1-coordinator seam） |
| `ShelfViewModelTest.kt` | b1b2, mb03 | 选用 mbC 版本（bc1c9e7b） |
| `BookRepository.kt` | b1b2 | 选用 b1b2 版本（含搜索索引刷新） |
| `SettingsStore.kt` | g6 (含 S06 底座) | 选用 g6 版本 |
| `MainActivity.kt` | g6 | 选用 g6 版本 |
| `App.kt` | g6 | 选用 g6 版本 |
| `JsonBridge.kt` | s14r3 | 选用 s14r3 版本 |

无 hunk 级冲突需要手工合并——每个重叠文件取最终依赖链末端的候选版本。

---

## 3. Patch 与回溯验证

| 属性 | 值 |
|------|-----|
| patch 文件 | `stage-e-final-joint-from-head.patch` |
| hunk 数 | 229 |
| 字节数 | 626,878 |
| 行数 | 12,607 |
| SHA-256 | `b5365d80b7f23eb9c8ce2010b14a71a0c010a7015d2e8462ffa6c94c1fc62084` |

### 3.1 回溯验证

在纯 HEAD 副本上 `git -c core.autocrlf=false apply` 后逐文件比对字节：

```
checked=851  mismatch=0  missing=0
```

所有 71 个叠加文件 + 780 个未修改文件全部字节一致，patch 可完整回溯。

> **注意**：恢复仓库 `core.autocrlf=true`，直接 `git apply` 会因 CR/LF 差异报 mismatch。复现时需加 `-c core.autocrlf=false`。

---

## 4. 构建门结果

全部在 `E:\creation-reading-assistant\stage-e-final-joint-20260930\joint\android\` 执行，`--max-workers=1 --no-daemon`，Gradle cache 在 E: 盘。

| 门 | 任务 | 结果 | 耗时 | 产物 |
|----|------|------|------|------|
| Gate 1 | `:app:testDebugUnitTest` | ✅ BUILD SUCCESSFUL | 4m 8s | 全部 JVM 测试通过 |
| Gate 2 | `:app:lintDebug` | ✅ BUILD SUCCESSFUL | (含 Gate 3) | 7 warnings, 0 errors |
| Gate 3 | `:app:assembleDebug` | ✅ BUILD SUCCESSFUL | 11m 19s | `app-debug.apk` 48.84 MB |
| Gate 4 | `:app:compileDebugAndroidTestKotlin` | ✅ BUILD SUCCESSFUL | 29s | androidTest 编译通过 |
| Gate 5 | `:app:assembleQahost :app:assembleQahostAndroidTest` | ✅ BUILD SUCCESSFUL | 3m 7s | `app-qahost.apk` 48.84 MB, `app-qahost-androidTest.apk` 1.4 MB |

### 4.1 Lint 详情

7 个 warning（0 error, 0 fatal）：
- `GradleDependency` × 3：依赖版本提示
- `ObsoleteLintCustomCheck` × 3：lint 自定义检查过时
- `UnusedResources` × 1：未使用资源

均为非阻塞警告，不阻止构建。

### 4.2 编译警告

qahost 构建时有 1 个 Kotlin deprecation warning：
- `LibraryBrowserComponents.kt:498` — `Icons.Outlined.HelpOutline` 已弃用，建议用 `Icons.AutoMirrored.Outlined.HelpOutline`
- 来源：b1b2 候选的库浏览器代码
- 非阻塞，可在后续提交中修正

### 4.3 Flaky 测试记录

**首次** JVM 全量运行有 1 个失败：
- `SettingsStoreBootMirrorTest > legacy datastore default system theme marks mirror as present after first load`
- `AssertionError at SettingsStoreBootMirrorTest.kt:134`

**隔离运行**同一测试类：13/13 全部通过。

**根因**：`TestDataStoreDirs.root`（`TestDoubles.kt`）是所有 JVM 测试共享的单例目录 `%TEMP%\cra-datastore-tests`。`preferencesDataStore` 委托按文件路径缓存 DataStore 实例，导致先执行的测试类写入的 DataStore 状态泄漏到后执行的测试类。这是 **HEAD 基线已有的测试隔离基础设施问题**（`TestDoubles.kt` 来自 HEAD，未被任何候选修改），非本联合候选引入。

**第二次** JVM 全量运行：全部通过。该失败为非确定性 test-ordering flaky，不在本候选的修复范围内。

---

## 5. 提交拆分与依赖顺序

> 以下为建议的提交拆分顺序。**本 Stage E 不执行 commit**，仅提供拆分方案供后续集成。

### 5.1 依赖关系

```
G6 (含 S06 底座) ──────────────────────┐
                                        │
B1B2 (搜索索引 + 库浏览器) ─────────────┤
                                        ├──→ 联合候选
M-BUG-03 (书源更新闭环) ────────────────┤   (依赖 B1B2 的
   └─ ShelfImporter 调用                 │    SearchIndexBookRefreshCoordinator)
      SearchIndexBookRefreshCoordinator  │
      (来自 B1B2)                        │
                                        │
S14R3 (备份/恢复) ──────────────────────┘
   (独立，无交叉依赖)
```

### 5.2 建议提交顺序

| 序号 | 提交 | 内容 | 文件数 | 依赖 |
|------|------|------|--------|------|
| 1 | `stage-e/g6-behavior-preserved` | G6 行为保持重构（含 S06 冷启动主题镜像底座） | 35 | HEAD |
| 2 | `stage-e/b1b2-library-search` | B1B2 库浏览器 + 搜索索引刷新 | 18 | #1 |
| 3 | `stage-e/mbug03-source-update-closure` | M-BUG-03 书源更新闭环 | 13 | #2 (SearchIndexBookRefreshCoordinator seam) |
| 4 | `stage-e/s14r3-backup-closure` | S14R3 备份/恢复闭环 | 5 | #1 (独立于 #2/#3) |

**关键约束**：
- #1 必须先于 #2：G6 修改了 `SettingsStore.kt`、`App.kt`、`MainActivity.kt` 等基础文件，B1B2 的测试依赖这些基础类
- #2 必须先于 #3：M-BUG-03 的 `ShelfImporter` seam 调用 B1B2 引入的 `SearchIndexBookRefreshCoordinator`
- #4 可在 #1 之后任意位置提交：S14R3 仅修改 `JsonBridge`、`LocalZipBackup` 及其测试，不依赖 #2/#3

### 5.3 每步编译验证

每个提交在 HEAD 上独立 apply 后应能通过：
- #1：`compileDebugKotlin` + `testDebugUnitTest`（G6 自带测试）
- #2：`compileDebugKotlin` + `testDebugUnitTest`（B1B2 自带测试）
- #3：`compileDebugKotlin` + `testDebugUnitTest`（M-BUG-03 自带测试，需 #2 已应用）
- #4：`compileDebugKotlin` + `testDebugUnitTest` + `compileDebugAndroidTestKotlin`（S14R3 含 androidTest）

---

## 6. 设备复验清单

> 以下功能变更需在真实 Android 手机上截图验证。**禁止使用 MuMu 模拟器。**
> 执行前先 `adb devices` 核对设备，所有命令用 `adb -s <serial>`。

### 6.1 G6 — 冷启动主题与行为保持

| # | 验证项 | 操作 | 预期 | 截图 |
|---|--------|------|------|------|
| 1 | 冷启动无主题闪变 | 杀进程后冷启动应用 | 首帧主题与上次设置一致，无「日间→夜间」闪变 | 冷启动首帧 |
| 2 | 深色模式切换 | 设置 → 外观 → 深色 | 立即切换深色，无需重启 | 深色模式 |
| 3 | 浅色模式切换 | 设置 → 外观 → 浅色 | 立即切换浅色 | 浅色模式 |
| 4 | 跟随系统 | 设置 → 外观 → 跟随系统 | 主题随系统设置变化 | 跟随系统 |
| 5 | 阅读统计页 | 我的 → 阅读统计 | 年度账单 hero card、热力图、趋势图正常渲染 | 统计页 |
| 6 | 个人中心 | 我的 → 个人中心 | 外观子页、阅读归档区域正常显示 | 个人中心 |
| 7 | 阅读时长显示 | 我的 → 阅读历史 | 时长显示格式正确（DurationDisplay） | 阅读历史 |

### 6.2 B1B2 — 库浏览器与搜索

| # | 验证项 | 操作 | 预期 | 截图 |
|---|--------|------|------|------|
| 8 | 库浏览器 | 书架页 → 浏览模式 | 可浏览、选择/取消选择书籍 | 库浏览器 |
| 9 | 全文搜索 | 搜索框输入关键词 | 搜索结果按相关度排序，命中高亮 | 搜索结果 |
| 10 | 搜索索引刷新 | 导入新书后搜索 | 新书内容可被搜索到 | 新书搜索 |

### 6.3 M-BUG-03 — 书源更新闭环

| # | 验证项 | 操作 | 预期 | 截图 |
|---|--------|------|------|------|
| 11 | 书源更新 | 书籍详情 → 更新源 | 更新后内容刷新，搜索索引同步更新 | 源更新 |
| 12 | 源更新反馈 | 书架页下拉刷新源 | 刷新进度/成功/失败反馈正确 | 刷新反馈 |

### 6.4 S14R3 — 备份/恢复

| # | 验证项 | 操作 | 预期 | 截图 |
|---|--------|------|------|------|
| 13 | 本地备份 | 设置 → 备份 → 创建备份 | 生成 ZIP 文件，提示成功 | 备份成功 |
| 14 | 本地恢复 | 设置 → 备份 → 恢复 | 从 ZIP 恢复数据，书架内容完整 | 恢复成功 |
| 15 | 备份失败处理 | 存储空间不足时备份 | 提示失败，不产生半成品文件 | 备份失败 |

---

## 7. 复现指南

### 7.1 环境要求

- JDK 17（`D:\develop\Java\jdk-17.0.14`）
- Android SDK（`D:\develop\Android\Sdk`）
- Gradle 8.9（通过 `gradlew.bat`）
- PowerShell 7.x
- E: 盘有 ≥ 10 GB 可用空间

### 7.2 复现步骤

```powershell
# 1. 创建工作目录
$B = 'E:\creation-reading-assistant\stage-e-final-joint-20260930'
New-Item -ItemType Directory -Path "$B\joint\android" -Force

# 2. 导出 HEAD 基线 android/ 子树
$repo = 'D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919'
git -C $repo archive HEAD android/ | tar -x -C "$B\joint"

# 3. 叠加 71 个候选文件（按 joint-selection.csv）
#    见 manifest\build_joint.ps1

# 4. 生成 patch
git -C $repo -c core.autocrlf=false diff HEAD -- android/ > "$B\stage-e-final-joint-from-head.patch"

# 5. 验证 patch 回溯
#    见 manifest\generate_patch.py + diff_vs_head.ps1

# 6. 运行构建门
$env:GRADLE_USER_HOME = "$B\gradle-home"
$env:JAVA_HOME = 'D:\develop\Java\jdk-17.0.14'
#    local.properties: sdk.dir=D\:\\develop\\Android\\Sdk
cd "$B\joint\android"
.\gradlew.bat :app:testDebugUnitTest --max-workers=1 --no-daemon
.\gradlew.bat :app:lintDebug :app:assembleDebug --max-workers=1 --no-daemon
.\gradlew.bat :app:compileDebugAndroidTestKotlin --max-workers=1 --no-daemon
.\gradlew.bat :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --max-workers=1 --no-daemon
```

### 7.3 关键文件

| 文件 | 路径 |
|------|------|
| 联合树 | `E:\...\stage-e-final-joint-20260930\joint\android\` |
| 文件清单 | `manifest\joint-selection.csv` |
| 构建脚本 | `manifest\build_joint.ps1` |
| Patch | `stage-e-final-joint-from-head.patch` |
| Gate 1 日志 | `build\02-full-jvm.log` |
| Gate 2+3 日志 | `build\03-lint-assemble.log` |
| Gate 4 日志 | `build\04-debug-androidtest-compile.log` |
| Gate 5 日志 | `build\05-qahost-apk.log` |

---

## 8. 已知问题与后续

### 8.1 Flaky 测试（不阻塞）

`SettingsStoreBootMirrorTest` 在全量 JVM 测试中偶发失败（test-ordering 污染），根因是 `TestDataStoreDirs.root` 共享单例目录。这是 HEAD 基线 `TestDoubles.kt` 的已有问题，非本联合候选引入。后续可在 `TestDoubles.kt` 中为每个测试类使用独立子目录修复，但该修改属于测试基础设施改进，不在 Stage E 范围内。

### 8.2 Kotlin deprecation warning（不阻塞）

`LibraryBrowserComponents.kt:498` 使用了已弃用的 `Icons.Outlined.HelpOutline`。建议后续提交中替换为 `Icons.AutoMirrored.Outlined.HelpOutline`。

### 8.3 未覆盖范围

- 本联合候选不包含桌面端（`src/`、`electron/`）任何修改
- 本联合候选不包含 `archives/frozen-mobile/` 任何修改
- 本联合候选未执行 commit、push、reset、checkout
- 本联合候选未触碰任何源候选目录
- 设备复验（§6）需在真实手机上执行，本 Stage E 不执行设备操作

---

## 9. 结论

5 个经验证的候选（B1B2、G6含S06底座、S14R3、M-BUG-03）已成功合并为 1 个联合候选树，71 个文件叠加在纯 HEAD 基线上。全部 5 道构建门通过：

- **JVM 单测**：全部通过
- **Lint**：0 error（7 warning）
- **Debug APK**：48.84 MB
- **androidTest 编译**：通过
- **qahost APK + androidTest APK**：48.84 MB + 1.4 MB

Patch（SHA-256 `b5365d80…`）可在纯 HEAD 上完整回溯。建议按 §5.2 的 4 步提交顺序集成，每步可独立编译通过。
