# 124-v1 B：DE 补证与条件式原基线恢复

2026-10-25 香港14:25。推荐模型：既有 B workbuddy/cn:deepseek-v4.1-flash 1M。唯一设备 owner B；恢复依赖本步检查，A 已有充分独立审核，无新独立目标，不造复审。

````text
接续124-v1，同B既有会话执行。用户已要求继续本机自动化。Root已实核release-inventory/REPORT.md 17308B SHA24334ee1f558b3133568a9b343c15808357f62e8b4012f7e81b5742d7e83c6d9及ready、7个新保全原件sizeSHA、有效ps与边车回执；27文件前后同SHA，2TXT/RoomDB/14results均已有同SHA原件。当前仅剩DE目录范围和生成密钥集处置。本步是123失败run的恢复收口，不是新业务/新attempt，旧红结果与121越界不追认。

Root现精确授权一次条件式恢复，仅com.creationreadingassistant这个123入场前缺席、本run安装的产品包；不触碰原test包或其他包。先执行以下全部门禁，任何一项失败都停在KEPT_BLOCKED，不执行卸载：
1. fixed ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe，fresh核现有MuMu1实例/serial/boot/user0/owner/前台/night，命令全-s；历史boot582add53-11fe-43b6-ae82-6f75c84984b1，身份或boot漂移停。不得启动/重启VM或杀共享ADB/其他进程。fresh有效完整ps PID/NAME+pidof核产品及test的pkg与pkg:前缀，无活动writer；错误、空表或不可解析非absence，不force-stop。
2. 只读扩大到精确/data/user_de/0/com.creationreadingassistant根，列完整type/size/mtime/目录及文件SHA，不跟随symlink、不扫其他包。条件式卸载只允许DE树全为空目录、无文件无symlink；只要DE有任何文件/特殊项/读错/归属不明即停报，绝不自行判cache可删。此时保留CE/external全部资产。
3. fresh CE/external完整清单和所有27文件sizeSHA必须与release-inventory/inventory.json逐项一致，无未知新增/缺失/漂移，Room主DBe1d42bbae3145436aea95fc27ae09c611471ed074dc3cb10d626b036babd5c0d，主DB WAL/SHM/journal仍成功缺席；WorkManager DB三件必须与已保全同SHA。核全部本地唯一保全原件仍在且同SHA（引用13/13、14results和本次7件即可，不重复制）。产品本地APK74d106b6仍在且同SHA，原test0d18d788完整代码/数据inventory/userstate保持原状。
4. ai_secrets.xml须仍1144B SHA8663c532947dcf7dfe9e3a48d4f931de7d4073ad9886110348a4a44953f137af，结构仍只有两个AndroidX EncryptedSharedPreferences自动生成keyset条目，无其他普通/密文业务条目。只核条目类型/数量与SHA，不展开、打印或上传值，不复制原始密钥。本run入场产品原本缺席，Root将这两个仅本次安装生成、无业务秘密条目的keyset列为此次精确卸载的可丢弃初始化状态；这是明确处置决定，不声称字节已保全或毫无数据损失。任何其他条目/结构无法确认则停报。

只有1–4全满足，Root在此具体解除123这些设备端测试产品资产的KEPT_BLOCKED保留门禁，允许一次对精确com.creationreadingassistant包进行正常包管理卸载（不-k），以恢复入场前产品缺席。沿用已经审核的包卸载方式，全-s且限定本轮user/包；不使用host主入口、不启动instrumentation、不新runId/budget/attempt、不安装任何APK，不手动rm/删除/移动残余根，不递归清理。卸载失败或状态不明停报，不重试绕过。

卸载后只读核包确实缺席、CE/DE/external三精确根成功缺席，失败/权限错不算缺席；若有残余保留并报BLOCKED。原test代码SHA0d18d788、data inventoryd6ac3c5f及完整userstate与已冻结恢复合同核同；71私有/820外部保护及Download179、night no、VM继续运行按既有已审protection_adapter/合同只读复核。不得因复核失败自动写设置或修原test，独立恢复问题明确报告。禁止改123原summary/attempt/日志；新恢复回执另列采集窗口，不把123 FAIL改成PASS。保留所有本地TXT/DB/results/新增小文件和APK，不删移压迁。

输出E:/creation-reading-assistant/task124-instrument-coordinate-cleanup-v1/lane-b/baseline-recovery/REPORT.md与ready.json及必要原始回执。报告<=1MiB，必要原始<=4MiB；预计空间后写，已有原件只引用不复制。列门禁逐项证据、DE清单、keyset处置非字节保全、精确卸载命令/rc/时间或未执行原因、后置/保护恢复事实、仍未闭合项。每工具<=45秒回模型，不运行充分编译/JVM/旧套件，不修改冻结source0557506f/testa646c8de/hostbce4a930/inputsffc778e0。完成即停止设备动作，等Root核后另具体放行新业务。

你不是独自工作，不覆盖A/D/current或历史原件。禁私库/自动记忆（Claude/Cowork/Claude-Code/DSH/.codex/memories）、Git、云SFTP、正式库HOLD、实体手机、购买换账号、存量清理迁移、杀VM共享ADB。此范围是Root本次恢复具体许可，仅覆盖之前no-ADB/no-uninstall在上述严格条件内，不延伸为新业务许可。最后单独一行：这是任务124-v1 B-条件式基线恢复。
````
