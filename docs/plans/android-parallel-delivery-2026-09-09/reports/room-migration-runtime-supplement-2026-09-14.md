# Room v12 → v13 → v14 运行时迁移补充证据

> 日期：2026-09-14（GMT+8）  
> 执行者：Codex（用户明确要求完成 WorkBuddy 报告中标为 BLOCKED 的受控迁移运行时证据）  
> 范围：只执行既有 `AppDatabaseMigrationTest` 的三条迁移用例；不修改 Android 产品源码、Room migration、schema 或用户数据。

## 结论

WorkBuddy 最终报告中“缺少受控旧版本 fixture”的 Room 迁移执行 BLOCKED 已获得真实设备上的运行时证据：下列三条既有 Android instrumentation 测试均以独立测试数据库执行，分别输出 `OK (1 test)`，且 ADB 退出码为 0。

| 路径 | 用例 | 结果 |
| --- | --- | --- |
| v12 → v13 | `migrate_12_to_13_adds_reader_text_corrections` | PASS |
| v13 → v14 | `migrate_13_to_14_creates_library_source_refs` | PASS |
| v12 → v13 → v14 | `migrate_12_to_14_full_chain_preserves_data_and_creates_both_tables` | PASS |

三条用例由 `MigrationTestHelper` 在设备上创建旧版本数据库，实际调用 `MIGRATION_12_13`、`MIGRATION_13_14`，并在迁移后校验既有书库/进度/高亮数据、两张新表、可写性、索引和外键级联。它们是运行时迁移证据，不是 AndroidTest 编译替代品。

## 受测冻结版本与构建

- 冻结 commit：`fd7f908e19c2ee2269103333804bdd5927201fd2`
- 冻结 worktree：`D:\develop\Code\Codex\cra-g0-gate`
- 受测设备：真实设备 `c49ac6cf`（Xiaomi 22081212C，Android 15）；未使用 MuMu。
- 构建命令（worktree 的 `android/`）：

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest --console=plain --no-daemon
```

- 构建结果：`BUILD SUCCESSFUL`，退出码 `0`。
- 测试 APK：`app-debug-androidTest.apk`，包名 `com.creationreadingassistant.test`，SHA-256 `76291B2BA2897D8ADFF5EE87A7364E285F90C4A532766E40A567F71D878E76E4`。

## 隔离与设备数据保护

主应用数据库名为 `creation_reading_assistant_native`；迁移测试固定使用不同名称的 `migration-test`。测试安装前：

- 主应用 `com.creationreadingassistant` 已安装；
- 独立测试包 `com.creationreadingassistant.test` 未安装；
- 主数据库目录不存在 `migration-test`；
- 主数据库文件大小为 `345,489,408` bytes，WAL 为 `524,288` bytes。

仅安装了独立测试 APK，不重装或清除主应用。安装使用仓库脚本：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\install_with_confirm.ps1 `
  -Serial c49ac6cf `
  -Apk "D:\develop\Code\Codex\cra-g0-gate\android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
```

脚本实际输出 `RESULT: CONFIRM_LOOP`，并以 `INSTALL_SCRIPT_EXITCODE=0` 结束；未使用 direct ADB FALLBACK，未要求人工点击或变更设备安全设置。

测试 APK 声明的 instrumentation 为：

```text
com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner
target=com.creationreadingassistant
```

未使用 `connectedDebugAndroidTest`，因为现有交接已记录该 Gradle 任务在此设备上可能卸载主应用。改为直接、逐条启动 instrumentation，避免该部署/清理路径。

## 实际执行命令与退出码

每条测试前均清除已确认仅属 fixture 的 `databases/migration-test*`；这不匹配也不接触主数据库名。

```powershell
adb -s c49ac6cf shell am instrument -w -r `
  -e class com.creationreadingassistant.data.local.AppDatabaseMigrationTest#migrate_12_to_13_adds_reader_text_corrections `
  com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner

adb -s c49ac6cf shell am instrument -w -r `
  -e class com.creationreadingassistant.data.local.AppDatabaseMigrationTest#migrate_13_to_14_creates_library_source_refs `
  com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner

adb -s c49ac6cf shell am instrument -w -r `
  -e class com.creationreadingassistant.data.local.AppDatabaseMigrationTest#migrate_12_to_14_full_chain_preserves_data_and_creates_both_tables `
  com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner
```

| 用例 | 设备 runner 输出 | ADB 退出码 |
| --- | --- | --- |
| `migrate_12_to_13_adds_reader_text_corrections` | `OK (1 test)`，0.112 s | 0 |
| `migrate_13_to_14_creates_library_source_refs` | `OK (1 test)`，0.117 s | 0 |
| `migrate_12_to_14_full_chain_preserves_data_and_creates_both_tables` | `OK (1 test)`，0.123 s | 0 |

## 收尾与边界

执行后：

- 已删除 `databases/migration-test`、`-journal`、`-wal`、`-shm` fixture 文件；
- 已卸载 `com.creationreadingassistant.test`，`pm path` 确认该测试包不存在；
- `pm path com.creationreadingassistant` 确认主应用仍在；
- 主数据库和 WAL 文件大小与测试前一致；未读取、导出、清除或修改用户书籍数据；
- `stay_on_while_plugged_in` 未修改，查询值仍为 `3`；
- 未修改产品源码、测试源码、脚本或提交历史，未 push。

本补充只消除 Room 迁移运行时执行的 BLOCKED，不替代 WorkBuddy 报告中明确列出的扫描取消/深层目录/大文件、替换实际执行、保存视图创建流程、详情页全区块，以及低置信 UI 现象的覆盖或复现工作。
