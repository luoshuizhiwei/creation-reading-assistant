# 124-v1 B：当前数据库保全与受控停止r3

用户指出自动化没有推进，Root接续已授权恢复工作，明确下一受控步骤。唯一设备owner B，workbuddy/cn:deepseek-v4.1-flash 1M。此步保全→停止→复核有依赖，单owner闭环；A没有独立新目标，不制造审查任务。

````text
接续124-v1 B writer-preservation-r3。先核本文SHA。原启动恢复r2 REPORT6008B SHA14774cd0ca1c327256299beb8eec06ed705a4c2a2d8b07a6212eae94b25b291b及ready SHA11ae84411acf5e7eaa6647cb0be8f17ec4f0a680f240ef527af8ff6314ec5252已Root实核，官方instance1启动成功，新boot ed51bfd9-0fff-4652-a67e-3619eb7cfe67，产品启动后进程3818与主DB WAL/SHM出现。旧进程号仅历史，不直接kill旧PID。旧所有红报告/原件冻结不覆写。

Root现具体授权以下owned block，覆盖旧“无继续ADB/受控停止”的等待边界，仅限现有隔离MuMu1/user0/com.creationreadingassistant本次测试产品。不得停止VM、其他包或原test，不卸载、不安装、不清理、不写设置，不新业务/host主入口/runId预算attempt，不改源/host/input/APK。

1. fixed ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe，fresh官方instance1映射/serial/boot/user0/owner/night/前台/有效ps PID-NAME及pidof核包前缀，命令全-s。必须同ed51bfd9 boot与既有instance1磁盘归属；不启动重启VM/乱connect/探测其它实例。身份变化、表不可解析、未知归属立即停保留。产品存在进程是本步预期，不能仅因此停在原无writer门禁；test或其他未知归属干扰则停报。

2. 仅产品CE与external、DE精确三根只读清单type/size/mtime/SHA（不跟symlink），先估空间。保全当前主Room DB及实际存在WAL/SHM/journal、WorkManager DB及边车，每组所有文件按设备前stat/SHA→原始逐件pull到新raw/pre-stop→设备后stat/SHA与本地sizeSHA核同。窗口内所有组成员/存在性必须一致；最多两次完整窗口采集，每次独立路径不覆写，不无限重试，失败停而不停止产品。活动窗口一致仅称文件字节窗口一致，不称OS原子快照。原先已保全同SHA且当前可核同的TXT/14results/prefs小文件引用即可；新增/漂移的非秘密小状态必要单份保全，新未知/越界/读取错/超预算停，不以cache名当可删。ai_secrets只核结构条目数及sizeSHA，不展开/打印/复制秘密值，原仅2初始化keyset结构若变化停报。本步必要原始含pre/post总<=8MiB，REPORT/清单<=1MiB；超过停报，不删移压迁。已有历史字节单份引用，不复制APK/工程/cache。主Room旧快照e1d42bba和WorkManager旧三件保留，本次新快照不覆盖它们。

3. 只有步骤1身份和步骤2当前全部DB/实际边车/必要唯一状态原字节成功保全且窗口稳定，Root才具体允许一次 `am force-stop --user 0 com.creationreadingassistant`（fixed ADB全-s本轮serial），仅这个batch产品包，禁止kill PID/killall或stop test/VM/sharedADB。记录命令rc与时间，fresh完整有效ps/pidof确认pkg及pkg:前缀全缺席。仍活动或命令/验证错误停报，不循环force-stop、不卸载。此受控停止为冻结当前测试状态，不能冒充原运行自然结束。

4. 停止后重新完整清单，并将主Room与WorkManager DB/实际存在边车必要原字节保全到raw/post-stop，逐件前/本地/后sizeSHA核同。若同pre字节可精确引用，不重复复制。不得checkpoint/写DB/启动产品，也不把边车消失等同无数据损失：用已保全pre组与post组分别在本地任务专用副本只读核SQLite integrity/FK/user_version及相关业务表/本run两bookId引用。查询不要修改唯一raw原件；临时副本限任务自生、空间计入预算，无需清理旧资产。比较11:23/11:43冻结数据与当前逻辑差异，区分启动自动调度与未知变化；无证据别归因。DE未知文件列明保留不删除。若发现异常，不恢复旧快照掩盖，保留全部并报BLOCKED。

5. 只读复核原test完整代码/data inventory/userstate、71私有/820外部保护/Download179、night no、VM保持运行。任何恢复不足分列，不自动写回修复，不吞错误。报告业务NOT_RUN/当前取证/受控停止/数据核对/保护各列。本步不解除123 KEPT_BLOCKED，不卸载/清理；Root核明确差异与完整原件后再决定恢复，无需重复整套A审核。

输出E:/creation-reading-assistant/task124-instrument-coordinate-cleanup-v1/lane-b/writer-preservation-r3/REPORT.md、ready.json、必要manifest/原始命令回执/前后快照。ready最后写，含本文SHA、实际身份、保全sizeSHA/窗口限制、是否执行精确停止/证据、DB逻辑差异、未知项与下一具体缺口。每工具<=45秒回模型，不泛扫历史/全build/JVM/旧充分套件。完成停止等待Root，本次无新attempt许可。

你不是独自工作，不改A/D/current与历史原件。禁私库自动记忆Claude/Cowork/Claude-Code/DSH/.codex/memories、Git/云SFTP/正式库HOLD实体手机/购买换账号/存量删除迁移/杀VM共享ADB其他进程。最后单独一行：这是任务124-v1 B-保全与受控停止r3。
````
