# 124-v1 B：修复启动入口后重试r2

用户明确：MuMu快捷方式丢失无法启动，请修复并再重试。Root已找到完好的开始菜单MuMu模拟器.lnk（目标D:/Application/NetEase/MuMuPlayer/nx_main/MuMuNxMain.exe，无参数），复制恢复到用户实际桌面D:/Application/Desktop/MuMu模拟器.lnk并核目标存在。未修改安装/实例配置，未启动设备。

````text
任务124-v1 B-启动恢复r2，同B workbuddy/cn:deepseek-v4.1-flash 1M，唯一设备owner。先核本任务SHA，再读原恢复文档D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/coordination/tasks/100-149/2026-10-25-task124-conditional-baseline-recovery.md SHA db0e2c92990b0d5b44246a763ed0b39b136253babb3ca957b646ba9e18b8ab91。原baseline-recovery与retry1失败原件冻结不覆写。

本次用户明确修复无法启动并重试，Root现具体允许启动既有隔离MuMu instance1一次；这精确覆盖旧“不得启动VM”和必须旧boot条件，不允许创建/克隆/重置/修复数据/重装/迁移实例、启动其他实例、重启或停止运行中实例、杀VM/共享ADB/其他进程。先只读现有官方MuMuManager命令帮助和官方实例清单，确认instance1的既有配置/磁盘归属；记录启动前进程/实例状态。使用实际帮助支持的官方启动方法指定已有instance1，不猜参数；若只能全局启动并可能进错实例，停报。不用新的后台服务。Root恢复了桌面正常入口，不意味着要启动默认instance0。已有instance1在运行则不重复启动。

启动后有限等待就绪，fixed ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe，通过官方实例映射fresh绑定instance1/实际serial/user0/owner/前台/night，命令全-s；不猜端口/不扫其他设备。旧boot582add53-11fe-43b6-ae82-6f75c84984b1仅历史参考，本次获准从停止状态启动产生新boot是允许变化，记录真实新boot与启动因果，不伪造同boot。实例归属无法证、启动失败/恢复提示/未知占用/超时即停，不循环启动/重启/换实例。

身份有效后，重新执行原恢复文档其余全部门禁：有效ps/pidof无产品/test前缀writer；DE精确根仅空目录无文件symlink；CE/external27项和本地已保全原件sizeSHA全部同，无未知新增/缺失/漂移；Room主DBe1d42bba及WAL/SHM/journal缺席，WorkManager三件同已保全SHA；ai_secrets1144B/8663c532且仅2初始化keyset，不打印/复制值。不能因本次新boot就放宽数据漂移/未知项。如果启动后系统/应用自动运行产生writer或数据变动，立即停保留，不force-stop、不checkpoint、不清理。所有门禁满足才沿用原精确许可一次正常卸载产品包com.creationreadingassistant恢复原缺席基线，并按原合同核包与CE/DE/external缺席、原test完整代码数据inventoryuserstate、71私有/820外部/Download179/nightno/VM运行；不手动rm残余，不改test包/其他包。不写设置、不安装新APK、不host主入口/新runId预算attempt/新业务。

输出E:/creation-reading-assistant/task124-instrument-coordinate-cleanup-v1/lane-b/launch-recovery-r2/REPORT.md、ready.json及必要启动/实例映射/身份/门禁/后置原始回执；REPORT<=1MiB，必要原始<=4MiB，引用现有唯一保全不重复复制。ready最后写，含本文SHA、原恢复文档SHA、启动方法与实例归属、实际boot、精确动作/rc/时间、门禁结果和未闭合项。每工具<=45秒回模型，有限就绪等待，不无限轮询。旧FAIL/越界/原件冻结，未证部分保持未证。完成即停止等Root核。

你不是独自工作，不写A/D/current、源码/host/input/APK，不build/JVM/充分复审。禁私库自动记忆Claude/Cowork/Claude-Code/DSH/.codex/memories、Git/云SFTP/正式库HOLD实体手机/购买换账号/存量清理迁移/杀VM共享ADB。A无独立新目标不造任务。最后单独一行：这是任务124-v1 B-启动恢复r2。
````
