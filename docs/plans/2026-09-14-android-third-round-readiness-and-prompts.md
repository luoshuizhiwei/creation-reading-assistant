# Android 第三轮前置包与任务提示词

> 日期：2026-09-14  
> 范围：仅 `android/` 与本文件；不修改 Electron/desktop。  
> 基线：`main` / `6cd29670e54c3fc2915d876896ecbaae8d000aa4`。  
> 权威输入依次为：`docs/handoff/current.md`、`docs/plans/2026-09-14-android-integration-freeze-manifest.md`，以及后者引用的 R3、共享 seam、Slice 3/4 报告。

## 1. 前置包结论

第三轮的**执行包已准备完成**，但第三轮尚未达到启动条件。

已确认的现场事实：

- 当前仍是混合 WIP 工作区；`main` 仍指向上列基线，A–F 尚无可列出的独立候选提交 hash。
- Room 代码当前同时包含 `MIGRATION_12_13` 与 `MIGRATION_13_14`；`13.json`、`14.json` 均为未跟踪候选工件。因此不能把当前工作区或此前的混合 WIP 定向测试称作候选提交、全量门禁或最终验收。
- 当前未发现本项目 Gradle/Kotlin 构建进程。该观察不等于证明全部写入 Agent 已停止；每次启动集成或门禁前仍须重新核对。
- 已有冻结清单规定唯一允许的顺序：`A ReaderCorrection v13 → B R3 v14 → C 安装脚本 → D Reader/选区设置 → E Home/Profile/Inspiration 消费侧 → F Shelf UI/路由`。
- `library_source_refs` 没有进入 `JsonBridge` 备份导入导出；这是显式产品决定/风险，不能在第三轮擅自补入 Sync，也不能报告为已解决。

因此，本文件提供两层可投递提示词：先是使第三轮具备输入的 A–F 串行集成提示词；只有六个候选提交均完成后，才可投递第三轮的 JVM/Lint/APK 门禁与 WorkBuddy 真机验收提示词。

## 2. 第三轮不可跳过的启动门

门禁负责人必须逐项给出证据；任一项不满足即停止，不以全量编译“碰巧通过”替代。

| 门 | 通过证据 | 失败或缺失时动作 |
|---|---|---|
| G0：候选身份 | A、B、C、D、E、F 六个按顺序的提交 hash，及每个 commit 的 `git show --name-status --format=fuller` | 回到对应切片；不得开始第三轮。 |
| G1：Room 版本链 | A 的 tip 为完整 v13，`13.json` 只含 `reader_text_corrections`；B 从 A tip 再升 v14，`14.json` 同时含两个新表；三份共享 Kotlin 文件和迁移测试的 hunk 与清单一致 | 停止并回交 A/B 集成者；不得“补一小段”混入 D/F。 |
| G2：跨切片 seam | D 已交付 `SelectionActionPreferences`（或等价、同等窄的 Reader facade）；E 仅作为消费者。B 不带 `ShelfRoute` 的新 `ImportSourceSheet` 调用；F 同批带该调用和 ImportSheets 拆分。 | 回交拥有该 seam 的切片；不得复制 Store 或临时改函数签名绕过。 |
| G3：单切片证据 | 每片的定向 JVM/脚本命令、退出码、测试结果与 `git diff --check` 已记录；B、D、E、F 的编译门禁分别在其 commit 上通过。 | 只重跑失败切片的门禁；不扩大为全量回归。 |
| G4：冻结快照 | 全部写入者已停止；在 A–F tip 创建一个**临时、detached 的 Git worktree**供第三轮使用，令未提交的其他用户 WIP 不参与编译或验收。记录 base、tip、worktree 绝对路径。 | 不在共享混合工作区运行第三轮全量门禁或构建 APK。 |

临时 worktree 是隔离验证工件，不得覆盖原工作区、不删除任何用户 WIP、不 `reset`/`checkout`/`clean` 原工作区。创建它之前应先确认目标目录不存在并记录目标 commit。

## 3. 统一报告格式与停止条件

每一份执行报告都必须含以下字段：

```text
范围/所有者：
基线与候选 hash：
实际命令（逐条）：
退出码：
测试 suites/tests/failures/errors/skipped（可取得时）：
git diff --check：
不在范围内的工作区改动：
未覆盖项与风险：
是否改变设备状态、以及恢复结果：
```

共用停止条件：发现未归属 hunk、Room 版本跳跃、`SelectionActionStore` 被 Profile 复制、`ImportSourceSheet` 重复声明、把 direct ADB 回退写成脚本成功、或 Android 源码之外的修改时，立即停止并报告事实。不要自行猜测所有权、跨文件硬改，或顺手吸收 Search/Sync/Security/desktop WIP。

## 4. 任务提示词 A–F：生成第三轮的候选输入

以下提示词只在用户明确授权该执行者进行对应 slice 的 stage/commit 时使用；没有该授权时，执行者只可做只读定位、测试和报告，不能阶段性“代为提交”。六个任务必须**串行**投递给同一集成者或明确交接的下一位集成者，不能并行处理共享 seam。

### Prompt A — ReaderCorrection v12→v13

```text
你是 Android 主线集成者。只处理 A：ReaderCorrection 最小数据层前置；工作目录
D:/develop/Code/Codex/creation-reading-assistant。先完整阅读：
- docs/handoff/current.md
- docs/plans/2026-09-14-android-integration-freeze-manifest.md（第 2、3、9、10 节）

这是共享 dirty workspace。保留所有其他未提交改动；禁止 reset、checkout、clean、stash、revert，禁止改 desktop/Electron。
只有在调用方已明确授予 stage/commit 权限时，才可创建 A 的候选提交；否则停在证据报告。

唯一允许进入 A：
1) 完整文件 ReaderCorrectionEntity.kt、ReaderCorrectionDao.kt、13.json；
2) AppDatabase.kt 的 ReaderCorrection import、version=13、entity/DAO 声明、MIGRATION_12_13；
3) DatabaseModule.kt 的相应 migration/DAO hunk；
4) AppDatabaseMigrationTest.kt 中仅 migrate_12_to_13_adds_reader_text_corrections。

严禁带入 LibrarySourceRef、14.json、MIGRATION_13_14、12→14 连续迁移、任何 Reader UI/设置/规则或
CorrectionProjectionTest。A 完成后必须是可编译的完整 v13，不得留下混合 Room 悬置。

先逐文件核对 hunk、13.json 结构与 git diff --check；若 hunk 无法安全分开，停止并报告文件/行意图。
在 android/ 运行：
  .\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain --no-daemon
CorrectionProjectionTest 归 D，且不在 A 候选中；不得为执行该 JVM test 把 D 文件混入 A。此处 compile gate 只证明 migration test source 已能随 A 编译，不能伪称 v12→v13 运行时迁移验收。
报告实际命令、退出码、候选 hash、文件清单、未覆盖项；不要跑全量 JVM/Lint/APK/真机，也不要推送。
```

### Prompt B — R3 来源索引 v13→v14

```text
你是 Android 主线集成者。只处理 B：R3 书籍来源索引 v13→v14；工作目录
D:/develop/Code/Codex/creation-reading-assistant。先阅读 current.md、冻结清单第 2、4、9、10 节，以及
2026-09-13-android-r3-slice1-review.md。确认 A 的候选提交 hash 已存在，且其 schema 已完整到 v13；否则停止。

保留其他 WIP，禁止 reset/checkout/clean/stash/revert，禁止 desktop/Electron。只有在明确得到 stage/commit
授权时才创建 B 候选提交，不 push。

带入冻结清单 B 的精确文件集合：LibrarySourceRefEntity/Dao、R3 library source/root/index/recognizer/fingerprint
模块、LibraryBrowser UI/ViewModel、ShelfImporter、SafBookSourceScanner、ShelfImportRoute、14.json、R3 tests；
以及 AppDatabase/DatabaseModule/AppDatabaseMigrationTest 的完整 v13→v14 hunk、AppNavigation 的 shelf/library hunk、
ShelfSharedComponents 的 SHELF_LIBRARY_ROUTE hunk、ShelfViewModelTest 的 sourceIndex/rootStore stub hunk。

绝不带入 13.json、ReaderCorrection/12→13、ShelfViewModel 动态视图、ShelfSavedViewsStore、Filter/BookDetail、
ImportSheets/ImportHistory/DesktopBooks/ImportSourceSheet、Reader/Home/Profile/Inspiration 或安装脚本。
特别规则：ShelfRoute 的新 ImportSourceSheet(onOpenLibrary/onImportFromDesktop) 调用归 F，因为它依赖 F 的
ImportSheets 拆分；B 仅保留 ShelfImportRoute 与 shelf/library 导航。不得为了 B 编译临时复制或改变函数声明。

核验 v14 同时包含 reader_text_corrections 和 library_source_refs，并且 13.json 不随 B 变更。运行：
  .\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.library.*" --tests "com.creationreadingassistant.ui.viewmodel.LibraryBrowserPolicyTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest" --console=plain --no-daemon
  .\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain --no-daemon
再执行该 slice 的 git diff --check。报告命令、退出码、候选 hash、迁移 hunk 归属、未覆盖项。
library_source_refs 未进 JsonBridge 备份是已登记产品风险：不擅自补 Sync，也不得报告为已解决。
```

### Prompt C — 安装脚本可靠性

```text
你是 Android 主线集成者。只处理 C：android/scripts/install_with_confirm.ps1 及
android/scripts/install_with_confirm.contract.tests.ps1。先阅读 current.md 的 2026-09-13 安装脚本结论和冻结清单第 5 节。

保留所有其他 WIP；禁止 reset/checkout/clean/stash/revert、禁止 Android Kotlin/Room/schema/desktop 改动。
只有明确的 stage/commit 授权才可创建独立 C 候选提交，不 push。

必须保留 UTF-8 UI dump、PowerShell 原生 adb STDERR 处理、CONFIRM_LOOP、SCRIPT_EXITCODE、
FALLBACK_DIRECT_INSTALL 与 -NoDirectInstall 的语义。不得以 2>$null 掩盖 adb 输出；不得强制 exit 0、要求人工点
MIUI、修改设备安全设置或把 direct adb install 伪装成自动安装成功。

在 android/scripts/ 实际运行：
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\install_with_confirm.contract.tests.ps1
记录 exit code、断言数和输出。只有真机脚本输出 RESULT: CONFIRM_LOOP 且 shell 的 SCRIPT_EXITCODE=0，才可写
“脚本自动安装通过”。如果真机确认循环失败，先保存完整失败证据；确认真实 serial 后，adb -s <serial> install -r
仅能作为继续功能测试的 FALLBACK。此任务不需安装 APK、不做真机验收。
```

### Prompt D — Reader 核心、词典与选区设置

```text
你是 Android 主线集成者。只处理 D：Reader 核心、词典、阅读设置和选区设置。先阅读 current.md、冻结清单第 6、9、10 节；确认 A 已完成。保留所有其他 WIP，禁止 reset/checkout/clean/stash/revert、禁止 desktop/Electron。
只有明确的 stage/commit 授权才可创建 D 候选提交，不 push。

带入清单第 6 节列出的 data/settings Reader 文件、feature/dictionary、Reader pager/rules、reader sheets、ReaderViewModel、
ClipboardUtils 和 D 专属 tests，以及 SettingsViewModel 的 Reader/per-book/SelectionAction facade hunk 与旧 sheet 薄壳的
同步删除/调用 hunk。不要带入 B 的 v14/library、E 的 SelectionSubPage/Profile route/state、F Shelf、Search/Sync/Security。

所有权不可违反：SelectionActionSettings、SelectionActions、SelectionActionStore、DataStore keys、sanitize/defaults、
Reader settings routing 与消费规则全归 D。实现或固定最小 Reader facade：
  SelectionActionPreferences.settings: StateFlow<SelectionActionSettings>
  setActionEnabled(id, enabled, target)
  setBrowserUrlTemplate(template)
  setDictionaryUrlTemplate(template)
  setDictionaryMode(mode)
  resetToDefaults()
Profile 不得注入 Store 或复制任何默认值、校验、过滤、排序或 DataStore key。

运行：
  .\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.data.settings.*" --tests "com.creationreadingassistant.feature.dictionary.*" --tests "com.creationreadingassistant.feature.reader.pager.ReplacedChapterSourceTest" --tests "com.creationreadingassistant.feature.reader.rules.*" --tests "com.creationreadingassistant.ui.screen.reader.*" --tests "com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest" --tests "com.creationreadingassistant.ui.viewmodel.SettingsViewModelTest" --tests "com.creationreadingassistant.ui.viewmodel.DictionaryEmptyNoticeTest" --tests "com.creationreadingassistant.ui.util.ClipboardUtilsTest" --console=plain --no-daemon
  .\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain --no-daemon
执行 slice git diff --check。报告所有 facade API、候选 hash、命令/退出码、未覆盖项；不跑全量门禁或真机验收。
```

### Prompt E — Home/Profile/Inspiration 消费侧

```text
你是 Android 主线集成者。只处理 E：Home、Profile、Inspiration 的页面拆分与 Reader 选区设置消费侧。先阅读
current.md、冻结清单第 6 的 SEAM REQUEST 及第 7、9、10 节；确认 D 的候选 hash 和 SelectionActionPreferences 已存在。

保留其他 WIP；禁止 reset/checkout/clean/stash/revert、禁止 desktop/Electron。只有明确 stage/commit 授权才可创建 E
候选提交，不 push。

只带入冻结清单第 7 节的 slice3 精确文件集、其 ViewModel/tests，和 SelectionSubPage 的 UI consumer hunk。
SelectionSubPage 只能渲染 settings 快照并把用户意图一对一委托给 D 的 facade；不得创建 Store、DataStore key、
sanitize/defaults、URL validation 或 action filtering/sorting 的第二份实现。

Motion.kt 的 rememberCountUp 签名是 rememberCountUp(target, key: String, reducedMotion = false)。若本 slice 碰到该 hunk，
必须连同所有受影响调用点及 CountUpStartValueTest 同批处理，每个调用点用唯一稳定 key；不得局部改回旧签名。

运行：
  .\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.viewmodel.InspirationViewModelTest" --tests "com.creationreadingassistant.ui.viewmodel.HomeViewModelTest" --tests "com.creationreadingassistant.ui.viewmodel.HomeArchiveViewModelTest" --tests "com.creationreadingassistant.ui.viewmodel.InspirationMaterialOpsTest" --tests "com.creationreadingassistant.ui.screen.inspiration.InspirationDetailStateTest" --tests "com.creationreadingassistant.ui.screen.inspiration.InspirationFileStructureTest" --tests "com.creationreadingassistant.ui.screen.home.HomeReadingArchiveSectionTest" --tests "com.creationreadingassistant.ui.screen.profile.UpdateCheckTest" --tests "com.creationreadingassistant.ui.theme.CountUpStartValueTest" --tests "com.creationreadingassistant.data.settings.ContinueReadingStoreTest" --console=plain --no-daemon
  .\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain --no-daemon
执行 git diff --check。报告候选 hash、实际命令/退出码、Selection facade 的消费证明、Motion API 覆盖与未覆盖项；
不做全量门禁或真机验收。
```

### Prompt F — Shelf UI、动态视图与路由收口

```text
你是 Android 主线集成者。只处理 F：Shelf UI、保存/动态视图、导入面板拆分和路由收口。先阅读 current.md、
冻结清单第 4 的 ShelfRoute 例外及第 8、9、10 节，并确认 B 已先完成。保留其他 WIP；禁止 reset/checkout/clean/stash/revert、
禁止 desktop/Electron。只有明确 stage/commit 授权才可创建 F 候选提交，不 push。

只带入冻结清单第 8 节的 Shelf UI 文件、ShelfSavedViewsStore、ShelfViewModel 的动态视图 hunk 与专属 tests。
在 ShelfRoute 中，新 ImportSourceSheet 调用必须与 ImportSourceSheet.kt、薄壳化 ImportSheets.kt、ImportHistorySheet.kt、
DesktopBooksSheet.kt 同批进入，避免与 HEAD 的旧 ImportSheets 声明重复。ShelfViewModelTest 的 SavedViews stub/tests 属 F；
sourceIndex/rootStore stub 属 B，不能重带。

BookActionSheet 与详情页删除说明必须保持真实契约：删除书籍资料、进度、书签和笔记，**不删除本地正文文件**。
不得实现回收站、保留期、孤儿扫描、自动清理、删除来源原文件或改变 feature/library/deletion 行为；“移除正文”才是独立的
私有正文清理动作。

禁止带入 ShelfImporter、LibrarySourceRef、LibraryBrowser、ShelfImportRoute 的 R3 入口、AppDatabase/migration/schema、
feature/library/deletion 行为、StorageSubPage 清理路径。

运行冻结清单第 8 节的 Shelf/deletion 定向 JVM selector 集合，再运行：
  .\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.shelf.*" --tests "com.creationreadingassistant.ui.screen.ShelfStatusFilterTest" --tests "com.creationreadingassistant.data.settings.ImportHistoryStoreTest" --tests "com.creationreadingassistant.data.settings.ShelfPrefsTest" --tests "com.creationreadingassistant.feature.library.deletion.*" --console=plain --no-daemon
  .\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain --no-daemon
执行 git diff --check。报告候选 hash、ImportSourceSheet 无重复声明的证据、删除文案/行为边界、命令/退出码和未覆盖项；
不跑全量门禁或真机验收。
```

## 5. 第三轮提示词：冻结快照的全量质量门禁

### Prompt G — 单一集成者：全量 JVM / Lint / APK / 差异门禁

```text
你是 Android 第三轮质量门禁执行者，不是实现者。工作目录
D:/develop/Code/Codex/creation-reading-assistant。只在收到 A、B、C、D、E、F 六个已提交候选 hash 和各自定向证据后开始。
先读 docs/handoff/current.md、2026-09-14-android-integration-freeze-manifest.md、
2026-09-14-android-third-round-readiness-and-prompts.md。

不得修改产品源码/测试/脚本/文档；禁止 stage、commit、push、reset、clean、stash、revert，且不得在原工作区运行 checkout；禁止 desktop/Electron。
不要相信历史“全绿”或共享 mixed-WIP 编译结果。检查全部写入者已停止、核对 A→F 的 commit range 与 G0–G4；
若不满足，报告 BLOCKED 并停止。

从 A–F tip 创建一个新的、绝对路径已记录的 detached 临时 Git worktree；这是唯一允许的 checkout 形式，目标必须不存在，原工作区不得改变。
仅在该 worktree/android 运行，且所有命令分开执行（不得把 --tests 与 compile task 混在同一 Gradle 调用）：
  .\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon
  .\gradlew.bat :app:lintDebug --console=plain --no-daemon
  .\gradlew.bat :app:assembleDebug --console=plain --no-daemon
  .\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain --no-daemon

解析 test-results XML，报告 suites/tests/failures/errors/skipped；记录每条命令和 exit code、Lint errors/warnings、
APK 绝对路径与 SHA-256。对 A 的父提交到 F tip 的范围运行 git diff --check（即 A_HASH^..F_HASH），并复核 schema 13/14 及上述 G1/G2 seam。
任一门禁失败，不修复、不重试以掩盖问题，保留原始输出并报告第一个失败。

完成仅表示“冻结候选通过开发质量门禁”，不表示真机/视觉验收。把 APK、commit hashes、报告交给 WorkBuddy；
不得自行进行最终设备验收。
```

### Prompt H — WorkBuddy：第三轮独立真机与视觉验收

```text
你是 WorkBuddy，负责 Android 第三轮独立验收，只验不修。工作目录
D:/develop/Code/Codex/creation-reading-assistant。只在收到第三轮 G 门禁报告、A–F tip hash、APK SHA-256 和
明确的受测 APK 绝对路径后开始。先读 current.md、冻结清单、第三轮前置包，以及 G 报告。

不修改任何产品源码/测试/脚本/文档，不 stage/commit/push/reset/checkout/clean/stash/revert。只使用已连接真实 Android
手机，禁止 MuMu；先 adb devices 确认 serial，之后所有 adb 命令显式 adb -s <serial>。记录
stay_on_while_plugged_in 原值，结束恢复。测试资料仅使用自行创建的“测试 TXT”“测试 EPUB”等中性 fixture；不触碰用户书籍。

先核对 APK SHA-256 与 G 报告一致。安装必须先运行：
  android/scripts/install_with_confirm.ps1 -Serial <serial> -Apk <absolute-apk>
只有 RESULT: CONFIRM_LOOP 且 SCRIPT_EXITCODE=0 可报告自动安装 PASS。若失败，先保存完整脚本输出；确认同一真实 serial 后
允许 adb -s <serial> install -r <absolute-apk> 继续功能验收，但只标注 FALLBACK，绝不可写成脚本恢复或通过。不得要求手动
点 MIUI，不得改变安全设置。

按冻结 snapshot 进行最小但跨切片的真实旅程：
1) Room 迁移：从受控 fixture 验证 v12→v13 ReaderCorrection 数据保留，以及 v13→v14 来源索引迁移可打开；
   无迁移 fixture 或权限时标 BLOCKED，不能以 AndroidTest 编译通过替代。
2) R3：在隔离目录验证授权、App 内浏览、导入/已入架、内容更新、来源失效后内部副本仍可读、删书不删来源、取消/清理恢复；
   大文件或 >1000/深层目录未实际覆盖必须如实列未覆盖。
3) Reader/设置：全局与本书覆盖、选区动作门控、词典降级/外部往返位置保持、规则/替换能力边界；不要把不支持的 Markdown/legacy
   路径说成已支持。
4) Home/Profile/Inspiration：Profile 变更能驱动 Reader 选区设置而不出现第二份状态；Home/灵感导航、返回和重建稳定；
   CountUp 跨页面不从 0 重播。
5) Shelf：保存/动态视图、导入来源面板、书籍详情和长按面板；删除文案与实际行为一致，删除书籍不删除本地正文，且不触发来源文件删除。
6) 冷启动/返回/旋转（可用时）、FATAL/ANR/关键日志检查。

输出 docs/plans/android-parallel-delivery-2026-09-09/reports/workbuddy-r3-final-integration.md：逐项 PASS/FAIL/BLOCKED、
设备/系统/serial、APK hash、所有命令/exit code、脚本安装判定、截图或录屏路径、日志、测试资料清理和设备设置恢复、
未覆盖项。历史 R3 通过可作风险清单，不能替代该确切 APK 的证据。失败回交对应 A–F owner，不自行修复。
```

## 6. 第三轮后的结论责任

- G 通过、H 未开始：只能称“冻结候选开发门禁通过，待独立真机/视觉验收”。
- H 通过：WorkBuddy 的报告是设备/视觉范围内的独立验收证据；仍须列出 BLOCKED 和未覆盖矩阵，不能把局部 PASS 扩写成无条件发布许可。
- H 失败：回交最小拥有者修复；修复后从受影响 slice 的定向门禁重新开始，并重新建立冻结快照。不得在验收 worktree 直接修复。
- C 的 `FALLBACK_DIRECT_INSTALL` 永远不是脚本 PASS；与其他功能验证的 PASS/FAIL 分开报告。
