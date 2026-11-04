# Android「开源阅读进度导入」切片归属与门禁

> 日期：2026-09-14
> 基线：`main` / `5aed593`（与 `origin/main` 同步）
> 状态：**工作区 WIP，未提交**。本切片不在
> [`2026-09-14-android-integration-freeze-manifest.md`](../historical-integration/2026-09-14-android-integration-freeze-manifest.md)
> 的 A–F 清单内，本文件为它补上所有权、坐标契约与定向门禁口径。
> 本文件不 stage、commit、push、reset、checkout、clean、stash，也不修改产品行为。

## 1. 切片范围

从 HapeLee/legado-with-MD3 的 WebDAV 备份（`backup*.zip`）中读取 `bookshelf.json`，
把**阅读位置**导入到本地**已存在且唯一匹配**的书籍上。

明确不做：不导入书源、替换规则、配置、书架分组或正文；不创建本地书；不调用
`JsonBridge`；不把 `library_source_refs` 补进备份导出（该产品决定仍悬置，见冻结清单第 9 节）。

## 2. 精确文件集合（所有权）

本切片由单一实现者持有；其他 Agent 不得并行修改下列文件。

新增（生产）：

- `android/app/src/main/java/com/creationreadingassistant/feature/sync/LegadoReadingProgressImporter.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/locator/LegadoProgressMarker.kt`

修改（生产）：

- `android/app/src/main/java/com/creationreadingassistant/feature/sync/WebDavBackup.kt`
  （新增 `getBytes`、`listLegadoBackups`；`resolveTarget` 改返回 `HttpUrl` + `addPathSegment`；
  `parsePropFind` 不再自行过滤扩展名，改由调用方过滤）
- `android/app/src/main/java/com/creationreadingassistant/data/local/dao/BookDao.kt`（新增 `getAllActive`）
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ReaderDocumentLoader.kt`
  （新增 `resolveImportedTextOffset`，仅 TXT 分支接入）
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ProfileWebDavManager.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ProfileViewModel.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/WebDavSubPage.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileRoute.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileUiState.kt`

新增（测试）：

- `android/app/src/test/java/com/creationreadingassistant/feature/sync/LegadoReadingProgressImporterTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/reader/locator/LegadoProgressMarkerTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ReaderDocumentLoaderLegadoProgressTest.kt`

修改（测试）：

- `android/app/src/test/java/com/creationreadingassistant/feature/sync/WebDavBackupTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ProfileWebDavManagerTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ProfileViewModelTest.kt`

不在本切片、必须保持原样：

- `docs/handoff/current.md`（单集成者维护）
- `AppDatabase.kt` / `DatabaseModule.kt` / `schemas/**`：本切片**不改 Room 版本**，仍是 v14
- 未归属 WIP：`Search/Sync/Security` 相关文件、`GlassDialogs.kt`、`MyReadingViewModel.kt`、
  `AppNavigation.kt` 的 `MenuBook` hunk
- 非 Android：`scripts/**`、`src/**`、`electron/**`

## 3. 坐标契约（不可违反）

导入器**只**处理 EPUB 与纯文本；其余格式按「暂不支持」计数并跳过。

| 书籍格式 | 导入时写入 | 打开时读取 | 是否可无损恢复 |
|---|---|---|---|
| `epub` | `LocatorBuilder.progressJson`（locator v2：`ci` + `co`） | `EpubRepository.loadProgress` / `loadProgressOffset` 读 locator v2 | 是 |
| 纯文本（非 Markdown） | `LegadoProgressMarker.encode(ci, co)` | `ReaderDocumentLoader` 的 TXT 分支经 `resolveImportedTextOffset`，用**本应用自己的**目录检测换算为源文本绝对偏移 | 是（启发式：要求两边目录规则近似一致） |
| **Markdown**（`md` / `markdown`） | **不写入** | — | **否，故排除** |

**为什么排除 Markdown**：`MarkdownDocument` 按 H1/H2 切章，且 `DocChapter.startOffset`
基于**规范阅读文本**（canonical）偏移；而开源阅读的 `durChapterIndex` 来自它自己的目录
正则、`durChapterPos` 基于源文本。两者既不同目录规则、也不同坐标空间。若按章号取值，
结果不是回到书首，而是**跳到错误位置**——比不导入更糟。

其余不可违反项：

- **不覆盖**：本地已是原生进度（非 Legado 标记）的纯文本书一律跳过；本地 `FINISHED` 的书跳过。
- **不放宽歧义**：同名同作者只要有多个候选，即使其中只有一本可导入，仍判 `ambiguous`。
- **事务边界**：读取现有进度、判断、写入必须在同一事务内，避免与阅读器并发自动保存竞态。
- **上限**：归档 ≤ 64 MiB、`bookshelf.json` ≤ 16 MiB、ZIP 条目 ≤ 256。
- **写前快照**：`DatabaseSafetyNet.snapshotNow`。

## 4. 定向门禁

在 `android/` 执行（命令分开跑，不得把 `--tests` 与 compile task 混在同一 Gradle 调用）：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.sync.*" --tests "com.creationreadingassistant.feature.reader.locator.*" --tests "com.creationreadingassistant.ui.viewmodel.ReaderDocumentLoaderLegadoProgressTest" --tests "com.creationreadingassistant.ui.viewmodel.ProfileWebDavManagerTest" --tests "com.creationreadingassistant.ui.viewmodel.ProfileViewModelTest" --console=plain --no-daemon
.\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon
```

口径说明：`com.creationreadingassistant.feature.sync.*` 是包通配，会把该包下**全部**测试类
拉进来，因此报告的数字大于本切片新增用例数，必须同时说明口径。

### 4.1 本轮实测证据（2026-09-14 23:40–23:49，开发侧）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `:app:testDebugUnitTest`（上列 5 个 selector） | `0` | **12 suites / 87 tests / 0 failures / 0 errors / 0 skipped** |
| `:app:compileDebugKotlin` | `0` | BUILD SUCCESSFUL |
| `git diff --check -- android/` | `0` | 无 whitespace error |

命中本切片的 12 个 suite：`feature.sync`（JsonBridge / LegadoReadingProgressImporter /
LocalZipBackup / PairingManagerLanHost / WebDavBackup）、`feature.reader.locator`
（EpubProgressCalibration / LearningProgressEstimator / **LegadoProgressMarker** / LocatorCodec）、
`ui.viewmodel`（ProfileViewModel / ProfileWebDavManager / ReaderDocumentLoaderLegadoProgress）。

本次新增的 10 个用例全部实际执行并通过（逐个核对 testcase 名，非仅看 BUILD SUCCESSFUL）：

- `markdown book is counted unsupported instead of storing an unreadable marker`
- `mixed markdown and plain text candidates stay ambiguous`
- `round trip keeps the plain text position written by the importer`
- `out of range chapter index falls back to the start instead of a wrong position`
- `converted offset never exceeds the current text length`
- `native locator still resolves through the stored absolute offset`
- `marker round trips clamped chapter position`
- `marker rejects missing half and unrelated progress json`
- `imports only uniquely matched book and preserves epub coordinate`
- `plain text stores chapter marker instead of unsafe raw offset`

**门禁边界（不得省略）**：以上运行发生在**混合工作区**，其中含一项**非本切片**的并发改动
——`ui/viewmodel/ShelfViewModel.kt` 与 `ShelfViewModelTest.kt` 把 `shelfBooks` 的
`SharingStarted.WhileSubscribed(5_000)` 改为 `SharingStarted.Eagerly`（mtime 23:20:02，
早于本轮修复动手时间）。该改动由他人持有，本轮**未触碰、未暂存、未回退**，故上述
「编译/测试通过」是**混合工作区口径**，不构成「本切片独立候选提交成立」的证明。

### 4.2 全量开发门禁（2026-09-14 23:55–00:07）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `:app:testDebugUnitTest`（全量，不带 `--tests`） | `0` | **262 suites / 2302 tests / 0 failures / 0 errors / 0 skipped** |
| `:app:lintDebug` | `0` | **0 errors / 4 warnings**（3× `ObsoleteLintCustomCheck` + 1× `UnusedResources`） |

与修复前基线（262 suites / 2295 tests、Lint 0E / 4W）逐项比对：

- suites 不变（262）；tests **+7** = 本切片新增 6 + 并发写入者 `ShelfViewModelTest` 新增 1；
- failures / errors / skipped 均为 0；
- Lint 告警**无新增**，类型与数量与基线一致；
- 本切片涉及的文件（`LegadoReadingProgressImporter` / `WebDavSubPage` / `ProfileRoute` /
  `ReaderDocumentLoader` / `BookDao`）在 Lint 报告中**零告警**。

### 4.3 APK 与 androidTest 编译门禁（2026-09-15 00:11–00:13）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `:app:assembleDebug` | `0` | BUILD SUCCESSFUL in 55s |
| `:app:compileDebugAndroidTestKotlin` | `0` | BUILD SUCCESSFUL in 51s |

APK：`android/app/build/outputs/apk/debug/app-debug.apk`，52,373,840 字节，
SHA-256 `b9a3d12caa60bf22f4162d835edf95699b1b0d437f3fc57b3871c0eca94614e4`。

该 APK 为 **debug 兜底签名**（构建日志明示「未配置正式签名…绝不可发布」），且是**混合工作区**
产物，不得用作发布凭据或「独立候选提交成立」的证明。

仍未运行：真机与仪器化验收。注意 `connectedDebugAndroidTest` 在生产包
`com.creationreadingassistant` 上会**卸载主包且不恢复**，必须改在隔离包 `.r3gate` 上执行。
本节全部结论为**开发侧**，不含设备行为证据。

## 5. 本轮修复记录（P1：Markdown 往返断裂）

**缺陷**：导入器把非 EPUB（TXT **和 Markdown**）统一写入 `LegadoProgressMarker`，但
`ReaderDocumentLoader` 只有 TXT 分支接入 `resolveImportedTextOffset`；Markdown 分支仍用
`parseStoredOffset`，该函数按 `"offset"` 键取值，对 `{"legado_position_v1":{...}}` 返回
`offset=0, space=null`，于是走 `migrateLegacyOffset(0)` → **打开 Markdown 书回到书首**。
UI 仍报「已导入 N 条」，属静默失效。类注释承诺了 Markdown 会被换算，实现没有。

**修法**：不改为「Markdown 也换算」——如第 3 节所述，那会跳到错误位置。改为在匹配阶段
排除 Markdown 并如实计数：

- `LegadoReadingProgressImporter.supportsImportedPosition(book)`：`format` 不属于
  `{"md","markdown"}` 才放行。
- 匹配分支新增 `candidates.none(::supportsImportedPosition) -> unsupported++`，置于
  `size == 1` 之前、`else -> ambiguous` 之前，**不放宽既有歧义语义**。
- `Report` 新增 `unsupported` 字段，`skipped` 与 `summary()` 同步纳入（文案「暂不支持导入 N」）。
- 文案更新：`WebDavSubPage` 卡片说明与 `ProfileRoute` 的确认对话框均写明 Markdown 会跳过。

**测试**：

- `LegadoReadingProgressImporterTest` 新增
  `markdown book is counted unsupported instead of storing an unreadable marker`、
  `mixed markdown and plain text candidates stay ambiguous`。
- `ReaderDocumentLoaderLegadoProgressTest` 新增纯文本往返、章号越界回退、超长偏移钳制、
  原生 locator 不受影响四个用例。

**残留**：若在本次修复**之前**已对 Markdown 书导入过，其库中标记无法换算，打开仍会回到
书首；用户开始阅读后进度会被正常 locator 覆盖，可自愈，不做数据迁移。

## 6. 已知风险与未覆盖项

| 项 | 说明 |
|---|---|
| EPUB `globalOffset` 语义污染 | 导入时 `globalOffset = remote.charOffset`（章内偏移冒充全局偏移）。**已查证**：影响面仅限 `pendingSourceLocatorJson` 驱动的 source navigation（提示「此阅读位置与当前正文不一致」且不跳转），**不是错误定位**；按标注 / 笔记定位的 `AnchorResolver` 路径**完全正确**。修法初版方案已作废，见 6.1 / 6.2。**未修**。 |
| 标记用正则解析 | `LegadoProgressMarker` 以正则从 JSON 取 `ci` / `co`，字段顺序变化即失效；应改用 `kotlinx.serialization`。**未修**。 |
| 流式大 TXT 回书首 | 未取得完整目录时 `resolveImportedTextOffset` 返回 0（有意保守），但 UI 未提示该情形。**未修**。 |
| 纯文本换算的启发式前提 | 要求本应用目录规则与开源阅读近似一致。不一致时章号对不上，会落到近似而非精确位置；无等价性证明，属已知取舍。 |
| 真机验收 | **未做**。本文档所有结论均为开发侧门禁，不含设备行为证据。 |
| 备份往返 | `library_source_refs` 未进 `JsonBridge`，与本切片无关，仍按冻结清单第 9 节保持悬置。 |

### 6.1 EPUB `globalOffset` 语义污染的逐项查证（2026-09-15 00:10）

**背景更正**：本文档初稿曾把此项判为「无实际触发」。该判断**不准确**——`ReaderLocator.legacyOffset`
在生产代码中有 6 类消费者。以下为逐项核对后的准确结论。

**事实**：`locationFor()` 对 EPUB 传 `globalOffset = remote.charOffset`。`LocatorCodec.encode` 把
`globalOffset` 写进内层 `locator_v2.offset`；`locatorFromProgress` 只解码内层，故
`ReaderLocator.legacyOffset` == 内层 `offset` == 章内偏移（而非全局偏移）。

**消费者逐项核对**：

| 消费者 | 对导入 EPUB 的实际行为 | 结论 |
|---|---|---|
| `AnchorResolver.resolve`（line 24–29） | 优先用 `chapterIndex`，仅当 ci 越界才回落 `legacyOffset` | ✅ 正确 |
| `EpubRepository.loadProgress` / `loadProgressOffset` | 只读 `v2.chapterIndex` / `v2.charOffset` | ✅ 正确 |
| `ReaderProgressEffects:412` → `resolveChapteredPosition(preferGlobalOffset = false)` | 命中 line 111–115 一致性校验：`chapterStart + charOffset != legacyOffset` ⇒ **返回 null，拒绝跳转** | ⚠️ 安全失败，非错误定位 |
| `ReaderProgressEffects:413` → `preferGlobalOffset = true` | **仅 Markdown 调用点**；Markdown 已被本切片排除 | ✅ 不触发 |
| `parseStoredAbsoluteOffset`（`ReaderDocumentLoader:318`） | 只在 TXT 路径被调用 | ✅ 不触发 |
| `AnnotationModels.toAnnotationEntry`（69 / 97） | 读 `locator?.legacyOffset` 做展示；导入进度不产生标注 | ✅ 不触发 |
| `ReaderTemporaryRoute.encodeSourceLocator`（44） | `legacyOffset ?: return null` —— **依赖该字段必须存在** | ⚠️ 见 6.2 的反证 |
| `ReaderHelpers.parseLocatorOffset`（79）→ 两处高亮渲染 | 读**外层** `offset`；高亮用的是标注自存 locator，非导入进度 | ✅ 不触发 |

**定性**：不是「定位错乱」，而是 **`pendingSourceLocatorJson` 驱动的 source navigation 被拒绝**——
`ReaderProgressEffects:363` 会提示「此阅读位置与当前正文不一致」且不跳转。用户在该 EPUB 内正常
阅读一次，进度即被真实 locator 覆盖，**自愈**。本切片尚未提交、未发布，实际暴露窗口为零。

**但并非「所有跳转都坏」**：`ReaderProgressEffects:455+` 那条路径（按标注 / 笔记的 locator 定位）
用的是 `AnchorResolver`，它优先取 `chapterIndex` / `charOffset`，只在 ci 越界时才回落
`legacyOffset` ⇒ 对导入的 EPUB **完全正确**。受影响面比初判更窄。

### 6.2 修法：初版方案已作废，正确方向超出本切片

**初版建议（作废）**：让导入写出的 EPUB locator 不带内层 `offset`。**经查证不可行**：

- `ui/navigation/ReaderTemporaryRoute.kt:44`：`val offset = target.locator.legacyOffset ?: return null`，
  注释写明「无全局坐标（如仅章节元组）时返回 null，**绝不伪造 offset=0 的假位置**」⇒ 去掉 `offset`
  会让临时检视路由**无法编码**。
- `ui/screen/reader/ReaderHelpers.kt:79` 的 `parseLocatorOffset` 读**外层** `offset`，其两个调用点
  （`ReaderContentHostPagedBranch:124`、`ReaderContentHostPlainTextBranch:247`）均以
  `?: return@mapNotNull null` 中止 ⇒ 缺 offset 会导致高亮不渲染。
- ⇒ **`legacyOffset` 是 locator 的必需字段**，不能靠「留空」绕开。初版方案会把 1 处降级换成 3 处降级。

**正确方向（未实施）**：让导入写出的 EPUB locator 的 `legacyOffset` 与 `(chapterIndex, charOffset)`
**自洽**，即 `legacyOffset = chapterStart[chapterIndex] + charOffset`。本应用 EPUB 的章节字符偏移表
只有打开 EPUB 后才存在，导入阶段拿不到，因此必须**延迟到打开时换算** —— 与 TXT 的做法
（写标记、由 `resolveImportedTextOffset` 在取得目录后换算）保持一致。

**改动范围（均超出本切片边界，应由 EPUB / locator owner 决定）**：
`EpubRepository.loadProgress` / `loadProgressOffset` 需能识别导入标记并用章节字符偏移换算；
`ReaderDocumentLoader` 的 EPUB 分支调用方式需相应调整。**本切片不做。**

## 7. 与既有清单的关系

- 本切片**不在** A–F 冻结清单内，不改变 A–F 的提交顺序与依赖。
- 它不触碰 Room 版本线（无 `AppDatabase` / `DatabaseModule` / `schemas` 改动），因此不参与
  冻结清单第 2 节的版本链约束。
- 若要把它纳入某一轮集成，需在 A–F 之外单独建立候选提交与门禁快照；不得借「当前工作区
  能编译」跳过本文件的第 4 节定向门禁。

## 8. 只读发现：并发改动 `ShelfViewModel.shelfBooks` 的订阅代价

**归属声明**：以下文件**不属于本切片**，本轮**未修改、未暂存、未回退**。此处仅记录只读
分析，处置权归其 owner。

**事实**：`ui/viewmodel/ShelfViewModel.kt:305` 把 `shelfBooks` 的 `stateIn` 启动策略由
`SharingStarted.WhileSubscribed(5_000)` 改为 `SharingStarted.Eagerly`（mtime 23:20:02）。

**分析出的副作用**（静态推理，**非实测**）：

1. 同文件 `books`（line 199–200）与 `progressById`（line 216）**仍是** `WhileSubscribed(5_000)`。
   `shelfBooks` 经 `projectedBooks` 订阅这两条流；`Eagerly` 让它在 ViewModel 创建后立即常驻
   订阅，于是这两条上游的 `WhileSubscribed` **不再有机会触发停止**，整条
   `books + progressById + ShelfBookSorter.projectAll + ShelfBookSorter.sort` 链变成
   **ViewModel 生命周期常驻**。
2. `ShelfViewModel` 是**导航图作用域**：`ui/navigation/AppNavigation.kt:410 / 418 / 449`
   三处均为 `hiltViewModel(graphEntry)`，被书架、导入、目录浏览共用。常驻窗口因此比
   「书架页面可见期」长得多——只要该图条目仍在返回栈即成立。
3. 代价：每次 `books` / `progress` 表变化都会执行投影与排序（`defaultDispatcher` 上的 CPU
   工作），即使书架不可见。
4. 张力：`ui/viewmodel/ProfileViewModelTest.kt` 已有
   `home summary subscription must not trigger sub-page entity loads` 一类用例，说明项目
   **明确在意**「未订阅时不应加载实体」；本次改动方向与之相反。

**候选处置方向**（供 owner 参考，本轮未实施）：① 只把 `books` / `progressById` 提升为
`Eagerly`（Room 冷流代价小），`shelfBooks` 保持 `WhileSubscribed`，既预热首帧又不让投影
与排序常驻；② 保留 `WhileSubscribed`，在 UI 侧显式渲染加载态，而不是让派生流常驻。
