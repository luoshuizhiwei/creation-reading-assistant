# S04 qahost MuMu 手机布局设备验证（部分通过）

- 日期：2026-09-27
- 授权：用户明确要求在无法使用实体手机期间使用 MuMu 继续设备验证；本报告只称“模拟器”，不称实体手机或真机 PASS。
- 设备：`adb -s 127.0.0.1:16416`，MuMu Android 12 / x86_64；原尺寸 `1080x1920`、原密度 `280 dpi`。
- 工作区：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；正式包 `com.creationreadingassistant` 在该模拟器未安装（`pm path` 无输出）。

## 本轮实测

1. 核对 E 盘两份隔离 APK SHA-256：宿主 `A4669F8532055656BE6341444F29EAF3A972678533E5CEB351FF34E40021A638`，测试包 `6D19C24449D75F893C20FEF947F165FD005F08733187AD1F3E5C5662C1456EC7`，与集成交接一致。两个 APK 均以 `adb install -r -t` 安装成功，保留 qahost 原有数据；此为模拟器 direct ADB 安装，不是 MIUI 自动确认脚本的 PASS。
2. 仅运行 `EpubReplaceRoomIsolatedTest`：`adb -s 127.0.0.1:16416 shell am instrument -w -r -e class com.creationreadingassistant.feature.reader.EpubReplaceRoomIsolatedTest com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner`。原始输出 `OK (8 tests)`、`INSTRUMENTATION_CODE: -1`、ADB exit 0。覆盖真实 Android Room/SQLite 与临时文件目录下的同 URI V1→V2、阅读进度保留、损坏/不可读来源拒绝、写库失败回滚、target 有/无时备份残留拒绝，以及隔离断言。测试数据库是内存库，文件根是测试唯一临时目录；这不等于 qahost 现有正式库的 UI 端到端验收。
3. qahost 原沙箱已有数据，测试前未清除。启动新版宿主时将 MuMu 密度临时改为 `440 dpi`，界面显示手机底部五项导航，应用正常启动。截图只保存在 E 盘；设备临时截图已删除，密度已恢复 `280 dpi`、尺寸保持 `1080x1920`。
4. 测试前后 `files/books/epub/` 的文件名、大小和修改时间清单相同；主数据库文件大小/修改时间和 WAL 大小/修改时间相同。启动应用后 SHM 修改时间变化，因此不能宣称 qahost 数据库逐字节零变化。没有对 qahost 执行清数据、卸载、删书或强停；没有操作实体手机与正式书库。

## 证据与边界

- 原始 instrumentation 及命令退出码记录：`E:\creation-reading-assistant\codex-s04-mumu-20260927\epub-room-instrumentation.log`，SHA-256 `F08749A3045DBA6786E4E48394EA74B3B5A030D4D74971FEF5CB9CA6FAB3AA26`。
- 手机布局截图：`E:\creation-reading-assistant\codex-s04-mumu-20260927\phone-ui.png`，SHA-256 `D3D33970747D74E2343DA952236BBB1C000730493A7782861E51FE8FAF191C1D`。图中仅为既有隔离沙箱的测试内容，不作为书籍行为验收。
- 本轮 PASS：隔离的 8 条 Android instrumentation、qahost 启动及手机布局显示。
- 本轮未覆盖：手工来源刷新 A、>32 MiB B、UI 残留拒绝 C、诊断页 D、替换中断 E、正式包/书库逐内容前后对账；实体手机 ROM/MIUI 行为也未覆盖。原 `2026-09-27-s04-qahost-device-acceptance.md` 的“实体手机飞行前 BLOCKED”历史记录仍成立，但不再阻止 MuMu 限定验证。
- 进度：全局 Android 恢复收口仍为 A–E 中 `1/5=20%`；S04 五关仍 `4/5=80%`，设备关卡已有部分模拟器证据，但尚未按完整验收单闭合。
