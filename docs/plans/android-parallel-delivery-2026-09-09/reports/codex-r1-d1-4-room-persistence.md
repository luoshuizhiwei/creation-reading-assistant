# Codex R1-D1.4：真实 Room 删除快照回归

日期：2026-09-10
状态：**开发测试已加入并完成 AndroidTest 编译；未运行 connected AndroidTest，未做真机/视觉验收。**未 stage、commit 或 push。

## 范围

Qoder 的删除/撤销 JVM 测试以替身仓储模拟 Room 查询语义。此切片不改生产逻辑、schema、迁移或构建依赖：`room-testing` 已在 `app/build.gradle.kts`。新增真实内存 Room 的回归，补足 SQLite 外键、软删除和连接表写回这一层。

## 改动

| 文件 | 改动 |
| --- | --- |
| `android/app/src/androidTest/java/com/creationreadingassistant/data/repository/BookDeletionPersistenceTest.kt` | 新增真实 Room/SQLite 测试：删除目标书的主行、进度、文件、会话、笔记、高亮、正文缓存、标签/分类/书架连接和已读章节；恢复同一次快照后逐项恢复。断言跨书隔离，并断言本次删除前已经软删除的笔记/高亮不被复活。 |
| `android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderRulesSheetTest.kt` | 将默认值从旧 `Available` object 调整为 `Available()`。这是 R1-S3 将其改为带正文降级说明的数据类后遗漏的 AndroidTest 调用点；否则整个 AndroidTest Kotlin 源集无法编译。 |

## 验证

在 `android/` 目录执行：

```text
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
```

退出码 0，`BUILD SUCCESSFUL in 13s`。这确认新增真实 Room 测试和既有 Compose AndroidTest 均可编译。

```text
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.reader.ReaderReplacementCapabilityTest" --console=plain
```

退出码 0，XML 实测 `18 tests, 0 failures, 0 errors, 0 skipped`。

`git diff --check`（既有测试）与 `git diff --no-index --check`（新增未跟踪测试）均无空白错误。

## 未覆盖与冻结后要求

- 未执行 `connectedDebugAndroidTest`，没有安装 APK、没有操作真机；这不是删除/撤销的功能验收，也不代表新增 Room 测试已在 Android 运行时通过。
- 用户已决定把设备验收后置。R1 冻结后，WorkBuddy 应在隔离测试数据库/测试 APK 上运行该测试，并与删除确认、跨路由撤销和恢复的真实 UI 路径一并验收。
- 本切片与 Trae 正在执行的 R1-S2.1（格式判定与同步元数据）没有共享源码文件；不等待其报告而修改其路径。
