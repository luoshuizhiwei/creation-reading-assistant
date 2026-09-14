# 发给 zcode：应用内书籍目录与智能识别 —— 第 3 组（来源索引与增量更新）接手包

> 本文替代 WorkBuddy 在该路线上的工作。前序：[`../../2026-09-12-android-library-folder-smart-recognition-roadmap.md`](../../2026-09-12-android-library-folder-smart-recognition-roadmap.md)（需求与路线）、
> [`reports/workbuddy-r4-library-folder.md`](reports/workbuddy-r4-library-folder.md)（第 1–2 组实施报告）。
> 工作目录：`D:/develop/Code/Codex/creation-reading-assistant`（主仓库，Android 主线在 `android/`）。
> 本文是任务包，不是「已完成」的证明；所有结论都要用你自己的实测覆盖。

## 0. 一句话接手

路线第 1–2 组（目录授权 + App 内浏览 + 智能识别最小闭环）已由 WorkBuddy 接线完毕并通过门禁；
**第 3 组（来源索引与增量更新）尚未开始**，它是本轮你要做的唯一目标。第 4 组（阅读器高价值优化）不在本轮。

## 1. 开工先做（不得跳过）

1. 在 `android/` 下跑 `git -C .. status --short` 与 `git -C .. rev-parse HEAD`，记录真实起点。
2. 完整读：仓库 `AGENTS.md`；本目录 `README.md`、`COMMON.md`；上面两份前序文档。
3. **工作区不是干净基线**（272+ 项未提交改动，含桌面端 `src/`、`electron/`、`scripts/` 与另一路的
   Android 改动）。这些全部视为用户资产：
   - **不 reset / checkout / clean / stash / commit / push**，不做全仓格式化；
   - **不碰** `src/`、`electron/`、`package.json`、`package-lock.json`（属桌面端另一条会话）；
   - 只 `git add <你自己写的具体文件>`，且必须由用户明确要求才提交。
4. 门禁前查最近 mtime：`find android/app/src -name "*.kt" -newermt "-3 minutes"`。若有他人写到一半的
   文件，等它落定再跑，**不要去修别人的半成品**。

## 2. 起点实测事实（WorkBuddy 交付，已过门禁）

### 2.1 已存在的 Module（先读再动手，不要重写）

| 位置 | 关键 Interface / 类型 | 说明 |
|---|---|---|
| `feature/library/LibraryRootStore.kt` | `LibraryRoot(treeUri, displayName, lastLocationDocumentId, sortMode)`、`LibrarySortMode`、`root: StateFlow<LibraryRoot?>`、`saveRoot/setLastLocation/setSortMode/clearRoot` | DataStore 单根目录；**不负责授权**，调用方必须先确认持久授权 |
| `feature/library/LibrarySource.kt` | `LibraryEntry`、`LibraryListing(documentId, entries, truncated, issue)`、`LibraryListingIssue.{NONE,UNREADABLE}`、`interface LibrarySource { suspend fun list(root, documentId) }`、`SafLibrarySource` | 单目录快照上限 1000；目录优先排序；不递归 |
| `feature/library/SafBookSourceScanner.kt` | `BookSourceScanResult`、`SafBookSourceScanner`、`BookFileClassifier` | 有界递归（2000 文件 / 500 候选 / 深度 16） |
| `feature/library/SmartBookRecognizer.kt` | `RecognitionRequest/Limits/Event/Summary/Confidence/Decision/Reason`、`RecognizedBookCandidate`、`DefaultSmartBookRecognizer`、`BookSampleClassifier` | 冷 Flow，取消即停止；≤64 KiB 小样本 |
| `feature/library/SafSmartBookRecognizer.kt` | `SafSmartBookRecognizer` + `@Binds SmartBookRecognizer` | 生产 Adapter |
| `feature/library/LibrarySourceModule.kt` | `@Provides LibrarySource` | WorkBuddy 补的绑定 |
| `feature/library/ShelfImporter.kt` | `importFiles(uris, sourceLabel)` / `importFolder` / `retryFailedImports` / `repairFile` / `booksProvider` | **唯一**写书架与私有副本的 Module |
| `feature/library/FormatClassifier.kt` | `FormatClassifier.classify(claimedFormat, fileName, firstBytes): Verdict` | 格式真源；支持 epub / txt / md |

### 2.2 已接线的 UI（第 1–2 组）

| 文件 | 说明 |
|---|---|
| `ui/viewmodel/LibraryBrowserViewModel.kt` | 浏览 + 识别状态机；只读来源目录 |
| `ui/viewmodel/LibraryBrowserModels.kt` | `LibraryBrowserUiState`、`LibraryFileRow`、`LibraryRecognitionUiState/Row`、`RecognitionFilter`、`LibraryMessage` |
| `ui/viewmodel/LibraryBrowserPolicy.kt` | 纯规则：`isWeakDuplicate` / `matchesFilter` / `selectedByDefault` / `formatSize` / `formatTime` |
| `ui/screen/shelf/LibraryBrowserRoute.kt`、`LibraryBrowserComponents.kt` | 目录页与组件 |
| `ui/screen/shelf/ShelfSharedComponents.kt` | 路由常量 `SHELF_LIBRARY_ROUTE = "shelf/library"` |
| `ui/navigation/AppNavigation.kt` | `shelf-graph` 内注册 `shelf/library` 路由 |
| `ui/screen/shelf/{ImportSourceSheet,ShelfRoute,ShelfImportRoute}.kt` | 三入口 Sheet、目录入口接线、去 PDF 胶囊 |
| `app/src/test/.../ui/viewmodel/LibraryBrowserPolicyTest.kt` | 9 条纯规则用例 |
| `app/src/test/.../feature/library/{LibraryRootStoreTest,SafLibrarySourceTest,SmartBookRecognizerTest}.kt` | Codex 的前序用例 |

### 2.3 门禁基线（2026-09-12 21:52，覆盖当前源码）

- `:app:compileDebugKotlin` PASS
- `:app:testDebugUnitTest` **250 套件 / 2200 tests / 0 fail / 0 err / 0 skip**
- `:app:compileDebugAndroidTestKotlin` PASS
- `:app:lintDebug` **0 errors / 4 warnings**（3× `ObsoleteLintCustomCheck` 来自依赖 jar，1× `UnusedResources`
  指向 `strings_annotations.xml:37`；**均为既有告警**）

**没有真机验收，没有 commit / push。** 这四条是开发检查点，不是发布结论。

## 3. 本轮目标：方案第 3 组（来源索引与增量更新）

> 方案原文（§10 第 3 组）：`library_source_refs` 需求和 migration；精确已入架、移动/改名、来源失效、
> 内容更新；超大文件快速候选指纹；轻量刷新，不做持续轮询。

按优先级拆成可独立验证的四步：

1. **数据层**：`library_source_refs` 表 + Room migration + `android/app/schemas` 快照 + 迁移测试。
   字段见方案 §4.5：`bookId / rootId / providerAuthority / documentId / displayName / format / size /
   lastModified / contentHash / lastSeenAt / availability`。
   - 当前 Room 版本请以 `AppDatabase.kt` 实际值为准（v13 已用于 `reader_text_corrections`），
     升版必须走既有策略并保留全部历史 schema 快照。
   - **该表不是正文事实源**：来源不可用时阅读器仍只依赖内部稳定副本（方案 §4.5 末段）。
2. **写入路径**：`ShelfImporter` 导入成功后落一条来源引用。注意导入既可能来自「我的书籍目录」，
   也可能来自系统选择器（无 root）——后者按方案语义只记可用的部分，不要伪造 rootId。
3. **判定升级**：把当前 `LibraryBrowserPolicy.isWeakDuplicate` 的**弱判定**升级为分层判定
   （方案 §5.6）：① `authority + documentId` 精确匹配；② 内容哈希相同（改名/移动）；
   ③ 格式+文件名+大小相同 → 只能标「可能重复」；④ 来源相同但大小/时间/哈希变化 → 「内容有更新」。
   **UI 文案必须随判据升级**：有了精确判据后，疑似/确认要分别呈现，不要把弱判定继续写成事实。
4. **超大文件快速候选指纹**（方案 §5.6）：>32 MiB 文件用「大小 + 首尾分块哈希」做去重候选指纹。
   该指纹**只用于去重，不得当作安全签名**，真正覆盖原书前仍需完整导入校验。

可选（用户明确要求时再做）：方案 §4.2 的手势级下拉刷新、§4.4 的识别结果预览页（P1）。

## 4. 不可改坏的契约（违反即视为回归）

1. **导入只有一个 Module**：目录页勾选后把 URI 交给 `shelf-graph` 作用域共享的 `ShelfViewModel`
   （`hiltViewModel(graphEntry)`）。
   **禁止给 `LibraryBrowserViewModel` 注入 `ShelfImporter`** —— 会产生第二个导入队列，导入页看不到进度。
2. **写根目录前先 `takePersistableUriPermission`**，失败不落库（`LibraryRootStore` 的注释明确要求调用方保证授权）。
3. **弱判定不得作为跳过导入的依据**（方案 §12 拒绝项：仅按文件名判断重复）。升级为精确判定后也一样：
   最终去重仍由 `ShelfImporter` 的内容哈希裁决。
4. **不申请 `MANAGE_EXTERNAL_STORAGE`；不扫描未授权目录；不上传文件名/目录/正文样本**（方案 §8.2、§12）。
5. **不在浏览列表里删除 / 移动 / 重命名来源文件**（方案 §12）。
6. **不新增定时轮询**（方案 §12 明确拒绝 1.5 秒轮询）；刷新只发生在下拉/点击识别/重新进入/可选受约束后台任务。
7. **识别并发 ≤ 2，读取量有上限，达到上限必须提示「已截断」**（方案 §5.1、§8.1）。
8. 零新增 PDF 宣称；诊断日志与报告只用中性名称（「测试 TXT」「测试 EPUB」）。

## 5. 文件所有权（本轮）

你有权新建/修改：

- `feature/library/**`（含新 `LibrarySourceIndex*`、`LibrarySourceDao`、指纹工具）
- `data/local/entity/**`、`data/local/dao/**`、`AppDatabase.kt`、`DatabaseModule.kt`、`android/app/schemas/**`
- `ui/viewmodel/LibraryBrowser*.kt`、`ui/screen/shelf/LibraryBrowser*.kt`
- `ui/screen/shelf/{ShelfImportRoute,ShelfRoute,ImportSourceSheet,ShelfSharedComponents}.kt`（改动要最小化并说明）
- 上述区域对应的 `app/src/test/**`、`app/src/androidTest/**`

需要改到下表区域时先停下来报告，不要直接越界：

- `ui/navigation/AppNavigation.kt`、`feature/reader/**`、`feature/search/**`、`data/repository/**`、
  `ui/screen/reader/**`、`ui/screen/profile/**`、`*.gradle.kts`、根 `strings.xml`
- 任何 `src/`、`electron/`、`package.json`（桌面端，另一条会话）

## 6. 门禁与验证要求

**跑命令（PowerShell 常不可用，用 Bash 直调 wrapper）：**

```bash
cd android
GRADLE_USER_HOME=D:/develop/env/gradle JAVA_HOME=D:/develop/Java/jdk-17.0.14 \
  "$JAVA_HOME/bin/java" -Dorg.gradle.appname=gradlew \
  -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug --no-daemon
```

**统计真实数字别用 grep**（全绿时 Gradle 不打印 `tests completed`）：

- 单测：解析 `app/build/test-results/testDebugUnitTest/*.xml` 汇总 `tests/failures/errors/skipped`
- Lint：解析 `app/build/reports/lint-results-debug.xml` 的 `issue` 明细（含文件与行号），
  逐条判断是否为「你引入的」；沿用既有 4 条作为对照。

**真机（只在用户给出设备时段后做）**：方案 §11.2。完整执行手册、逐条判定表、安装回退与现场保护要求见
[`../../2026-09-12-android-library-folder-device-acceptance.md`](../../2026-09-12-android-library-folder-device-acceptance.md)——
**直接沿用该表，不要另造一套判定口径**。要点：`adb devices` 先核对 serial，所有命令 `adb -s <serial>`；
不用 MuMu；长测前记录 `stay_on_while_plugged_in` 原值并在结束时恢复；报告不得记录真实书名。

必须覆盖的真机项（方案 §11.2）：首次授权 / 取消 / 重新授权 / 重启恢复；当前目录浏览与智能识别；
搜索、排序、面包屑、返回、多选、取消扫描；大目录截断与长任务不假死；测试 TXT/测试 EPUB 的推荐/重复/
失败/部分成功；SD 卡或云盘暂不可用时恢复提示；**删除书架项不会删除共享目录原文件**；
**来源撤销后内部副本仍可阅读、搜索、TTS**。

## 7. 已踩过的坑（直接省你时间）

1. `SelectablePill` 的签名是 `(text, selected, onClick, modifier, …)`，**尾随 lambda 会绑定到最后一个
   参数 `selectedBorderTint: Color` 而报类型错**。必须显式写 `onClick = { … }`。
2. `ui/screen/shelf` 包内**已有** `formatBytes(size: Int)`（`ShelfUtils.kt`），不要再定义同名重载；
   目录页用 `LibraryBrowserPolicy.formatSize(Long?)` / 组件层 `formatLibrarySize`。
3. `Modifier.combinedClickable` 需要 `@OptIn(ExperimentalFoundationApi::class)`；本路线已在
   `LibraryBrowserComponents.kt` 内用 `combinedClickableCompat` 收敛，别在调用点到处 OptIn。
4. Hilt 绑定缺失会让编译在**依赖图校验**阶段失败而不是类型阶段：`LibrarySource` 的 `@Provides` 就是这样
   补上的。新增 Interface 时同步确认绑定。
5. `LibraryRootStore` 只存 1 个 `lastLocationDocumentId`，无法反推中间层级。现有实现只在命中 root 直接
   子目录时恢复面包屑，否则退回 root 并清除过期 id —— 不要为了「恢复更深位置」去伪造面包屑；
   若要做，先扩展持久化结构（存路径），而不是猜。
6. `transformDebugClassesWithAsm` 目录损坏会产生数百个假失败（`NoClassDefFoundError: …AiClient`）；
   解法是删该目录让 Gradle 重建（直接 `rm -rf` 会卡，用 `timeout 90` 或 Python `shutil.rmtree`）。

## 8. 交付要求

1. 报告：`docs/plans/android-parallel-delivery-2026-09-09/reports/zcode-r4-library-source-index.md`
   —— 含：真实门禁数字、migration 与 schema 快照、判定升级前后的语义差异、SEAM 处理、
   未完成项与下一步。**不把开发检查点写成 PASS，不写没跑过的证据。**
2. 同步更新路线文档的「实施记录」小节（**追加**，不要覆盖第 1–2 组记录）与 `docs/handoff/current.md`
   的顶部活动结论块。
3. 不 commit / 不 push（除非用户明确要求）；改动边界要在报告里逐文件列出。
