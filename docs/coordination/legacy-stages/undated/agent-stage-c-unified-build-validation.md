# 窗口 C — stage-C 统一构建验证（本轮唯一 Gradle 执行者）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`（验证前后一致）；HEAD：`5aed5939600e491843e3c44453921bd6273a233c`（验证前后一致）
- 执行目录：`…\creation-reading-assistant-recovery-20260919\android`
- 起止时间：前置取证 **22:47:33** 起，最后一步（完整 JVM）于 **22:56:26** 结束；本报告写于 22:57 后
- 本轮性质：**只验证，不修改**。未改任何产品代码/测试代码/Gradle 配置/schema；未删构建缓存；未 stage/commit/push/checkout/reset/clean；未进入原始仓库；未操作手机
- 命令统一追加 `--console=plain`（不影响门禁语义，只为可解析日志），其余参数按要求原样执行

## 结论一句话

四步全部 exit 0 且 `BUILD SUCCESSFUL`：**主代码编译通过、小 TXT 定向测试 9/9 通过、androidTest 测试源码编译通过（仅编译，未运行）、完整 JVM 套件 2928 项 0 失败**。这不等于任何真机功能通过。

## 逐步记录

| # | 命令（在 `android/` 执行） | 退出码 | 结果 | 时间 |
|---|---|---|---|---|
| 1 | `.\gradlew.bat :app:compileDebugKotlin --no-parallel` | **0** | BUILD SUCCESSFUL in 1m 22s | 22:47:47 → 22:49:10 |
| 2 | `.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.reader.ReaderDerivedChapterMetadataTest" --no-parallel` | **0** | BUILD SUCCESSFUL in 54s | 22:49:20 → 22:50:15 |
| 3 | `.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-parallel` | **0** | BUILD SUCCESSFUL in 16s | 22:50:36 → 22:50:53 |
| 4 | `.\gradlew.bat :app:testDebugUnitTest --no-parallel` | **0** | BUILD SUCCESSFUL in 5m 20s | 22:51:05 → 22:56:26 |

### 步 1 主代码编译
`15 actionable tasks: 2 executed, 13 up-to-date` —— 实际执行的是 `:app:kspDebugKotlin` 与 `:app:compileDebugKotlin`（不是整任务 up-to-date 的空过）。无 `e:` 错误、无警告级失败。

### 步 2 小 TXT 定向 JVM 测试
- `30 actionable tasks: 7 executed`，含 `:app:compileDebugUnitTestKotlin` 与 `:app:testDebugUnitTest` → 确实编译并运行了测试。
- **实际执行数量（按结果 XML 计数，非报告数字）**：`tests="9" skipped="0" failures="0" errors="0"`，XML 内 `<testcase>` 亦为 9 条。
- 9 个用例名：`present but empty markdown chapter table does not fall through to small txt` / `small txt chapters supply both offsets and titles` / `epub book index outranks every txt chapter source` / `markdown source keeps precedence over streaming and small txt` / `offsets and titles never come from different sources` / `chapter detection failure on small txt degrades honestly to empty metadata` / `shelf chapter label follows the saved locator instead of pinning chapter one` / `offset inside the second small txt chapter resolves to chapter index one` / `streaming txt source keeps precedence over small txt chapters`。
- 失败名称：**无**。

### 步 3 androidTest 测试源码编译
- `30 actionable tasks: 2 executed, 28 up-to-date`（`:app:kspDebugAndroidTestKotlin`、`:app:compileDebugAndroidTestKotlin`）。
- **本轮状态：仅编译**。androidTest 源码树共 31 个 `.kt` 文件参与编译；**没有任何 instrumentation 测试被安装或执行**（未跑 ADB、未连手机、未装 APK）。
- 因此本步只证明「androidTest 源码类型正确、能被编译」，不证明其运行时行为，也不证明真机可过。

### 步 4 完整 JVM 套件
- 前置三步全绿后才执行本步（按要求顺序）。
- 追加了 `--rerun`：步 2 已让 `testDebugUnitTest` 处于带过滤的历史状态，去掉过滤虽会使其输入变化，但为避免「UP-TO-DATE 假全绿」，强制整批重跑。日志显示 `30 actionable tasks: 1 executed`，执行任务即 `:app:testDebugUnitTest`。
- **实际数量（对 316 个结果 XML 逐个解析累加）**：`tests=2928 failures=0 errors=0 skipped=0`，失败类列表为空。
- 结果 XML 316 个 vs `app/src/test` 下 309 个 `*Test.kt`：一个文件可含多个测试类，故类数可大于文件数；两者均按实际产物计数。
- 步 2 的定向类在本步中再次执证：`tests=9, failures=0, timestamp 2026-09-22T14:55:16Z`。
- 失败名称：**无**。

## 归因：本轮没有出现需要归因的失败

四步均 exit 0，因此**不存在**「B 数据库测试 / D 小 TXT 修复 / 工作区既有问题」的失败归属问题。为便于后续窗口对照，登记本轮观察到的**通过状态对应的在飞改动**（均为本轮之前既有的脏文件，本轮未触碰）：

- **窗口 D（小 TXT）**：`app/src/main/java/…/ui/screen/reader/ReaderScreenDerivedState.kt` 与 `app/src/test/java/…/ui/screen/reader/ReaderDerivedChapterMetadataTest.kt`（2 小时内 mtime）→ 步 2 与步 4 中其 9 项用例均通过。
- **窗口 B（数据库/迁移）**：`data/local/AppDatabase.kt`、`data/local/DatabaseModule.kt`、`data/local/dao/{BookDao,InspirationDao,SearchTermDao,TaxonomyDao}.kt`（均为工作树既有修改）；JVM 侧 `data/repository/BookRepositoryTest.kt` 通过；`androidTest/…/AppDatabaseMigrationTest.kt` **仅参与步 3 编译，未执行**。
- 共享工作区既有 387 项未提交改动整体在本门禁下可编译、JVM 全绿。

## 未覆盖项（本轮明确未验）

1. **androidTest / 真机行为**：31 个 androidTest 源文件只编译未运行；小 TXT 目录/章节标签的**真实渲染与手势**未验。
2. **数据库迁移运行时**：`AppDatabaseMigrationTest` 属 instrumentation，未执行 → schema 迁移的正确性本轮无证据。
3. **Lint**：未跑 `:app:lintDebug`。
4. **打包**：未跑 `:app:assembleDebug` / `assembleRelease`，未安装 APK。
5. **ADB / 手机 / 模拟器 / 横屏**：全部未执行（按要求）。
6. **桌面端**：未跑 `npm run build` / desktop Beta / diff-check，本轮范围只含 Android。
7. 全量 JVM 的**并发/时序类用例稳定性**：本轮单次通过，未做多轮重复，不能声称「无 flake」。

## git status 前后差异

- 前：387 条（`git status --porcelain`，22:47:33）；后：**387 条**（22:57:06）；`diff` 前后 → **逐行无差异**（diff_rc=0）。
- HEAD 前后均为 `5aed5939600e491843e3c44453921bd6273a233c`。
- 精确说明（避免误读）：本报告的写入**不会**让 status 多出一行 —— 该目录在本工作树中早已整体未被跟踪（快照第 353 行即为折叠条目 `?? docs/coordination/`，且 `git ls-files docs/coordination` 为空、`git check-ignore` 判定其**未被忽略**）。所以「387 → 387 无差异」表示的是：**本轮没有改动任何已跟踪文件，也没有新增/删除其他未跟踪条目**，而不是「报告文件不在 status 里」。

## 是否修改了范围外文件

**没有修改任何纳入 git 跟踪的文件**，前后 status 逐行相同即为证据。Gradle 自身的产物写在 `android/app/build/`（gitignore 范围内，故不出现在 status 中）。

另外如实说明：为留存证据，我在**已被 gitignore 的** `android/app/build/tmp/stage-c-validation/` 下新建了日志与结果快照（步 1–4 日志、步 2 的 XML、步 4 的 316 个 XML 副本、前后 git status 两份）。它们是构建目录内的临时取证文件，不进入版本控制，也不是「范围外源码改动」；按要求「唯一允许新建的仓库内文件」只有本报告。

## 给下一位执行者的口径约束

- 「步 3 通过」**只能**表述为 androidTest 源码编译通过；**不得**表述为测试已执行或真机通过。
- 「步 4 通过」**只能**表述为 JVM 单元测试 2928 项 0 失败；**不得**外推为小 TXT 目录、迁移或阅读体验在设备上正确。

未领取下一任务。
