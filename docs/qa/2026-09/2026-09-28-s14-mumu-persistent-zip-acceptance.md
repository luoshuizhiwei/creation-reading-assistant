# S14 MuMu 隔离持久库与本地 ZIP 定向验收（2026-09-28）

## 范围与构建

- 设备：MuMu `emulator-5556`，x86_64；仅安装和运行 `com.creationreadingassistant.qahost` / `.qahost.test`。此结论仅适用于模拟器，正式包未安装；未操作真实手机、正式书库或真实 WebDAV。
- 源码：恢复仓库工作树。新测试为 `LocalZipBackupPersistentRestoreTest`，使用独立命名的持久 Room 库、合成测试 EPUB/TXT 及测试根目录。执行前 Codex 补齐手工 `LocalExport` 构造缺少的两个必填空列表，并将 KDoc 中触发未闭合注释的 `books/**` 改写。
- 首次未加 `-PcraAndroidTestBuildType=qahost`，Gradle 找不到目标任务；随后带该属性运行。`compileQahostAndroidTestKotlin` 首轮在 KSP 阶段因未闭合注释失败；修正后编译退出 0。`assembleQahost` 与 `assembleQahostAndroidTest` 退出 0。原始日志均在 `E:\creation-reading-assistant\codex-s14-compile-20260928\`。
- 最终宿主 APK SHA-256 `CA700ACCB5B5CE579E084732864C7B03A689D8AFC24167792D497F1F1E99D08F`；最终测试 APK SHA-256 `AEA17EDC9EA5C76C5A2E884AA9A3984B64504CD2E396490449582A0DC5BE39E7`。两包 `adb install -r` 均显示 `Success`、退出 0；qahost 原有包数据未清除。
- 两个最终 APK 已复制到上述 E 盘证据目录，复制后的 SHA-256 与构建输出逐字相同；Gradle 的既有仓库构建目录及既定缓存仍在 D 盘，本轮未改全局构建配置。

## 原始结果

| 轮次 | 原始结果 | 处理 |
|---|---|---|
| 1 | `Tests run: 3, Failures: 1`；失败在恢复文件集合断言，把 `books/` 作为相对于 `books` 根的前缀重复计算 | 测试断言改用相对于恢复 `books` 根的路径；生产代码未改 |
| 2 | `OK (3 tests)`、`INSTRUMENTATION_CODE: -1`、ADB 退出 0；三个用例状态码均为 0 | 接受本次定向 MuMu 范围 PASS |

三项分别覆盖：本地 ZIP 导出/恢复后的五类实体与文件哈希、关闭重开持久性；纠错外键失败时事务回滚及重开保持；缺少 `backup.json` 的 ZIP 在导入前拒绝。第一轮虽 ADB 退出 0，但 JUnit 报 1 失败，因此没有计为通过。原始输出：`s14-instrument-run1.log`、`s14-instrument-run2.log`。

## 隔离与边界

- 测试前和两次运行后，qahost 原有 `creation_reading_assistant_native` 的 DB/WAL/SHM 三件 `sha256sum` 逐项一致，原始文件为 `qahost-db-before.sha256`、`qahost-db-after-run1.sha256`、`qahost-db-after-run2.sha256`。测试专用数据库名在运行后未列出。此处只证明原有数据库三件字节未变；没有做原有 `files/books` 逐文件前后哈希对账。
- 设备回读为 280 dpi、`user_rotation=0`、`accelerometer_rotation=1`；本轮未修改这些设置。
- 3/3 不覆盖真实 WebDAV 网络、跨设备 SAF URI、正式库、实体手机 ROM，也不证明 `LocalZipBackup.importZip` 在**数据库成功后文件复制失败**时仍原子。后一个故障窗口由独立 S14 调查继续核实。

## 证据目录

`E:\creation-reading-assistant\codex-s14-compile-20260928\`：编译与打包日志、安装日志、两轮 instrumentation 原文、APK SHA-256 和 qahost DB 三件前后哈希。大工件与原始设备记录留在 E 盘，不纳入仓库。
