# S04 测试 Runner 隔离：集成与离线门禁

- 日期：2026-09-25
- 目标：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`，HEAD `5aed593`
- 性质：集成者仅接入既有 Agent 新增的测试专用 Application / Runner；**尚未做设备验收**。

## 本轮改动边界

1. `android/app/build.gradle.kts` 仅将 `testInstrumentationRunner` 改为 `com.creationreadingassistant.testing.CraIsolatedTestRunner`。该文件原有版本号等未提交修改完整保留。
2. `docs/handoff/current.md` §8 的手工 instrumentation 命令同步换成新 Runner 名。该文档原有未提交修改完整保留。
3. 新增的两个 Kotlin 文件及 Agent 原报告见 `2026-09-25-s04-isolated-runner-implementation.md`；本轮未修改产品源码、现有 androidTest 或原始仓库。

## 新鲜离线验证

| 检查 | 实际结果 | 能证明什么 |
|---|---|---|
| `android/gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain` | EXIT 0；`BUILD SUCCESSFUL in 1m 5s`；`compileDebugAndroidTestKotlin` 执行 | 当前测试源码与 Runner 签名可编译 |
| `android/gradlew.bat :app:assembleDebugAndroidTest --console=plain` | EXIT 0；`BUILD SUCCESSFUL in 19s`；测试 APK 已打包 | 测试 APK 可构建 |
| `packaged_manifests/debugAndroidTest/processDebugAndroidTestManifest/AndroidManifest.xml` | `<instrumentation android:name="com.creationreadingassistant.testing.CraIsolatedTestRunner" … android:targetPackage="com.creationreadingassistant" />` | APK 合并清单指向新 Runner |
| `Get-FileHash` 测试 APK | SHA-256 `7A5838E42F9C6A60011F869AC5BA51F67ACE4118A127137BCA34A24B61593C91` | 固定此次离线产物版本 |
| `git diff --check -- android/app/build.gradle.kts docs/handoff/current.md` | EXIT 0；仅有 CRLF 将转 LF 的 Git 提示 | 本次接线未引入空白错误；不代表全仓 diff-check 通过 |

## 未覆盖与下一步

- **未运行 adb、未安装、未执行任何 instrumentation。** 编译和清单不能证明真机启动时正式 Room 库零写入，也不能证明全部 Compose / Room 测试兼容。
- `androidx.startup` 的 WorkManager / 其他 ContentProvider 仍可能在 Application 前初始化，`androidx.work.workdb` 不在本轮隔离范围。
- `EpubParserInstrumentedTest.kt` 仍有 `@After` 在正式 `filesDir/books/epub` 按固定 id 删除的风险；在真机跑该类前须另行隔离或停测。
- 下一步先做独立只读代码审查及该旧测试的安全边界处理；之后由独立验收窗口准备恢复点、核对真机竖屏和设备 serial，记录正式库（含 `search_terms` / `search_index_coverage`）、文件与偏好项前后对账，才能宣称设备级隔离。
- 不 commit、push、stage、reset、checkout 或 clean；不碰用户书籍数据。

## 随后单文件测试清理的离线复验

外部窗口另行修正 `EpubParserInstrumentedTest.kt`，把合成源与解析内部副本放进每次唯一的 `IsolatedTestContext` 根，`@After` 不再按固定 id 删除正式 `filesDir`；详见 `2026-09-25-s04-epub-parser-test-cleanup-fix.md`。集成者阅读了该文件 diff；本节只记录**修正后**的新鲜门禁，不把先前测试 APK 当成现版本：

| 检查 | 实际结果 |
|---|---|
| `android/gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain` | EXIT 0；`BUILD SUCCESSFUL in 15s`；Kotlin 编译任务执行 |
| `android/gradlew.bat :app:assembleDebugAndroidTest --console=plain` | EXIT 0；`BUILD SUCCESSFUL in 10s`；测试 APK 重新打包 |
| 新测试 APK SHA-256 | `F78904E61F6B19A79B2BF214EE493FDC70BF34532072A62D7213F1AA2ADF354F`；**取代上表旧 APK 哈希** |
| 打包清单 | `instrumentation android:name="com.creationreadingassistant.testing.CraIsolatedTestRunner"` 仍在 |
| 定向 `git diff --check` | EXIT 0；仅见既有 Gradle 文件 CRLF 提示 |

仍未运行设备测试，不能宣称正式数据库或用户文件在真机上零副作用。下一步应由独立窗口审查这两处隔离改动与测试前后证据清单，再决定是否进入真实手机竖屏验收。

## 独立审查跟进后的离线复验（2026-09-25）

外部窗口随后完成 `IsolatedTestContext`、`EpubParserInstrumentedTest`、`CraIsolatedTestRunner` 的三处窄改，见 `2026-09-25-s04-isolation-review-followups.md`。集成者在该版本上重新执行：

| 检查 | 实际结果 |
|---|---|
| `android/gradlew.bat :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest --offline --console=plain` | EXIT 0；`BUILD SUCCESSFUL in 18s`；Kotlin 编译、测试 APK 打包任务均执行 |
| 打包清单 | `CraIsolatedTestRunner`，目标包 `com.creationreadingassistant` |
| 最新测试 APK SHA-256 | `FB27ECDCCC55DFA850486A0C7AB52581694B9FE6C97948026EAC0E79C9186DAA`；取代上述两次旧哈希 |

这仅证明当前测试源码可编译打包；没有执行 adb、安装或真机 instrumentation，也不能据此宣称 WorkManager 队列或正式库不变。设备方案须先通过独立安全审查及用户对残余副作用的决策。
