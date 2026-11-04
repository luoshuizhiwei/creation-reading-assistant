# Android/native R3 来源目录切片集成 manifest

> 日期：2026-09-13
>
> 基线：`main` / `6cd29670e54c3fc2915d876896ecbaae8d000aa4`。
>
> 本文件冻结的是切片边界与审阅结论；不代表已 stage、commit 或 push。
>
> **2026-09-13 更新（完整只读审阅后，见 [`2026-09-13-android-r3-slice1-review.md`](2026-09-13-android-r3-slice1-review.md)）：**
> §2 归属表两处修正：`ShelfViewModel.kt` 的当前工作区改动**全部属 L1 书架动态视图**（codex-r5），不属本切片，已从下表移除；
> `ImportSourceSheet.kt` 属切片 4 导入面板拆分（与 HEAD 版 `ImportSheets.kt` 重复声明），**不得并入本切片**——
> R3 提交形态下的目录入口是 `ShelfImportRoute` 的「我的书籍目录」渠道行（`ImportChannelRow` 自包含）。
> `ShelfRoute.kt` / `AppNavigation.kt` / `ShelfViewModelTest.kt` 与数据库三件套（`AppDatabase.kt` / `DatabaseModule.kt` /
> `AppDatabaseMigrationTest.kt`）均为混合文件，提交时必须按审阅报告 §1.D 的 **hunk 级清单**拆分。

## 1. 切片目标

本切片收束“应用内书籍目录与来源索引 R3”：用户授权一个 SAF 根目录后，可在 App 内浏览/识别/批量导入；导入仍落入内部稳定副本；来源关系只用于“已入架、内容有更新、来源失效”等展示和判定，不替代正文事实源。

WorkBuddy 已独立验收真机 A–E PASS。这里的工作是代码集成审阅，不重复真机验收。

## 2. 归属清单

| 区域 | 切片 1 文件/职责 |
|---|---|
| SAF 根与浏览 | `feature/library/LibraryRootStore.kt`、`LibrarySource.kt`、`LibrarySourceModule.kt`、`SafBookSourceScanner.kt`、`LibraryBrowser{Models,Policy,ViewModel}.kt`、`LibraryBrowser{Route,Components}.kt` |
| 智能识别与大文件候选 | `SmartBookRecognizer.kt`、`SafSmartBookRecognizer.kt`、`SourceFingerprint.kt` |
| 导入与来源索引 | `ShelfImporter.kt`、`LibrarySourceIndex.kt`、`LibrarySourceRef{Dao,Entity}.kt`、`ShelfImportRoute.kt`、`ShelfRoute.kt`（仅 R3 hunks）、`AppNavigation.kt`（仅 R3 hunks） |
| 数据库 | `AppDatabase.kt`、`DatabaseModule.kt`、schema `13.json`/`14.json`、`AppDatabaseMigrationTest.kt` |
| 回归测试 | `LibraryRootStoreTest`、`LibrarySourceIndexTest`、`SafBookSourceScannerTest`、`SafLibrarySourceTest`、`SmartBookRecognizerTest`、`SourceFingerprintTest`、`LibraryBrowserPolicyTest`、`ShelfImporterTest`、`ShelfViewModelTest`（仅 `sourceIndex`/`rootStore` 编译桩 hunks，L1 动态视图用例不属本切片） |

## 3. 已确认的行为闭环

1. 授权根目录只走 SAF 持久化授权；不请求 `MANAGE_EXTERNAL_STORAGE`，也不按裸文件系统路径读取。
2. 浏览和识别不直接写书架；文件选择统一交现有 `ShelfViewModel` / `ShelfImporter`，因此内部稳定副本仍是阅读事实源。
3. 导入成功后 best-effort 写 `library_source_refs`；无法证明来源属于授权树时 `root_id=null`，不伪造归属。
4. 目录判定区分精确来源、内容更新、候选指纹同内容和弱疑似；弱判定不携带 `bookId`，不自动跳过导入。
5. >32 MiB 文件使用首尾 256 KiB 的版本化候选指纹，仅用于去重提示；不当作安全签名或正文覆盖依据。
6. 只有完整、未截断的识别扫描才能将同根且未出现的来源标为 `missing`；取消扫描不形成“扫描完成”结论。
7. `books` 删除会级联清理来源引用，但不删除外部来源文件；R3 验收已验证来源失效后内部副本仍可读。

## 4. 共享 seam：本切片不能被错误拆开

`AppDatabase` 当前同时包含 reader text correction 的 v12→v13 迁移和 R3 的 v13→v14 迁移。因数据库版本、实体、DAO、schema 和 `DatabaseModule` 是同一个编译/运行契约，切片 1 在形成本地提交时必须二选一：

1. 与最小的 ReaderCorrection 数据层前置提交串行落库；或
2. 由同一集成者在单一数据库版本序列中审阅并提交二者。

不能只挑 `MIGRATION_13_14`、却遗漏 v13 的实体/DAO/schema，否则 v14 schema 和迁移链不完整。这个是提交顺序约束，不是 R3 功能缺陷。

## 5. 审阅结论

### Standards

- PASS：R3 将 SAF 访问、来源索引、识别、导入和 UI 判定分开；业务写入仍集中在 `ShelfImporter`，没有新增轮询或全盘存储权限。
- PASS：迁移有 v13/v14 schema、迁移测试与外键级联断言；本轮修正了 `AppDatabaseMigrationTest.kt` 一处 `) {        val` 的格式粘连，不改变行为。
- 集成约束：数据库 v13 同时服务 ReaderCorrection，因此不得声称 R3 可以脱离该前置 schema 单独提交。

### Spec

- PASS：路线规定的单根 SAF、应用内浏览、智能识别、批选导入、稳定内部副本、来源更新/失效分层、扫描截断与取消边界均有实现和测试/真机证据。
- 非本切片：下拉刷新和识别结果预览页属于可选 P1；私有副本回收策略、安装脚本可靠性另行决定，不能倒灌为 R3 返工。

## 6. 本轮验证

在 `android/` 执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.library.*" --tests "com.creationreadingassistant.ui.viewmodel.LibraryBrowserPolicyTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest" :app:compileDebugAndroidTestKotlin
```

- 结果：`BUILD SUCCESSFUL`。
- JVM XML：15 个测试类、155 tests、0 failures、0 errors、0 skipped。该通配范围还包含既有的 library deletion/classifier 测试；直接列于本 manifest 的 9 个 R3 测试类共 88 tests，均通过。
- `compileDebugAndroidTestKotlin`：通过。
- `git diff --check`：本切片的已跟踪文件通过。

## 7. 完成与下一步

切片 1 的代码审阅、边界冻结和定向构建检查完成；它尚未提交。下一次操作应由单一集成者按第 4 节先处理共享 schema 顺序，再在用户明确授权后 stage/commit。不要在这个提交中混入 Reader UI、首页/Profile/灵感拆分、Shelf 视觉重构、搜索/同步或安装脚本。

2026-09-13 的完整只读 diff 审阅（候选/排除文件清单、hunk 级归属、migration 依赖顺序、seam 调用链、定向测试与风险清单）见 [`2026-09-13-android-r3-slice1-review.md`](2026-09-13-android-r3-slice1-review.md)，结论：可有条件进入候选独立提交。
