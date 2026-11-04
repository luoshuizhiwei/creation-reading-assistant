# 126-v2 ZCode桌面端GLM-5.3-Flash：启动后条件式已保全基线恢复

126-v1已如实FAIL收口：instance1关机（官方映射is_process_started=false）、serial 16416无监听、旧boot ed51bfd9不可验证；条件3本地49/49核同；设备零写入。ready854092f8/REPORTd61532b5在E:/creation-reading-assistant/task126-zcode-desktop-baseline-recovery-v1/。用户2026-10-26明确选择方案A并授权开始：一次官方启动既有instance1，记录真实新boot，显式覆盖"旧boot ed51bfd9仍一致"门禁（r2先例同款条款）；其余门禁与126-v1等强度，不放宽数据保护。

````text
任务126-v2，ZCode桌面端GLM-5.3-Flash，唯一Android恢复执行owner。先核本文SHA再执行。接续126-v1全部只读成果，不重开目标。输出E:/creation-reading-assistant/task126-zcode-desktop-baseline-recovery-v2/REPORT.md、ready.json、必要原始回执与最小脚本。REPORT/差量<=1MiB，必要原始<=4MiB，复制前估体积；本地现成原件与APK全部只引用不复制。

1. 仅一次官方启动既有instance1：MuMuManager.exe control --vmindex 1 launch（D:/Application/NetEase/MuMuPlayer/nx_main/，r2已验证形式），执行前先--help核实语法。启动失败/超时/映射不显示instance1运行→停保留，不二次启动、不重启、不重置、不启其他实例。启动后以官方映射取本轮实际serial（不假设16416），命令全-s该serial；记录真实新boot_id（/proc/sys/kernel/random/boot_id）替代旧ed51bfd9条件。等待Android启动完成（is_android_started=true、boot_completed=1）。

2. 分支1（产品自启预案）：fresh ps完整有效表+pidof核产品com.creationreadingassistant/test/pkg:前缀；若有活跃进程，执行者绝不force-stop/kill，只做只读pidof轮询（间隔>=60秒，自首次检查起上限90分钟）等自然退出；超时未退或退出后复现→停保留另批处置。进程全部缺席后才开始门禁。

3. 门禁（新boot下全部fresh重做）：
   a. 身份：instance1运行、serial、新boot_id记录、boot_completed=1、user0、night=Night mode: no、owner=u0_a86:u0_a86、前台Lawnchair、ps表有效（stderr空、可解析）且产品/test/前缀无进程。
   b. 三根52条目：CE/external/DE完整type/size/mtime/SHA清单与writer-preservation-r3/manifests/current-inventory.json核同，30文件/22目录/0symlink、无新增/缺失。分支2预授权漂移组仅限r3已保全7件（Room db/-wal/-shm、WM db/-wal/-shm、DE shader cache）：该组size/mtime/SHA允许变化，但新字节必须先按r3稳定窗口法单份保全（设备前后stat/SHA→pull→本地sizeSHA核同）至v2 raw/pre-uninstall/（合计<=8MiB），保全核同后才继续；组外任何漂移/新增/缺失立即停保留，不清理不追认。两DB journal成功缺席。ai_secrets严格：仍1144B/8663c532…/仅2个AndroidX初始化keyset条目，不打印/复制值；任何结构变化停。
   c. 本地原件：按现有manifest复验49件sizeSHA（r3七件/12证据、两TXT/DB、7小状态、14results、APK对/archive/adapter、保护基线与设置基线），只引用。productApk 74d106b6、原test 0d18d788、inventory d6ac3c5f、userstate合同仍匹配。

4. 以上全满足才执行一次正常包管理卸载com.creationreadingassistant（不-k）；失败/超时/状态未知停保留不循环重试。仅该产品，不原test/其他包；不rm残余、不递归清理、不checkpoint/写回DB、不装/启产品、不kill/stop VM/sharedADB、不改设置。

5. 卸载后核：包缺席（pm path/list精确判，读错不算absence）、CE/DE/external三根缺席（ENOENT才算，残余报BLOCKED不手动删）。只读后置保护：原test APK sha0d18d788、test data inventory重算=d6ac3c5f、userstate合同串逐字同、qahost私有71+外部820（Download204+tmp616、顶层含隐藏179）对root-runtime-device-20261015/device/protected-original-baseline-v2.json逐件核（存在文件不等于锁活跃，按元数据+SHA核）、11项设置/night no/VM仍运行（按original-settings.json与r3 15-settings-check同一组命令）。后置失败分类报告，不自动写回修复。

6. WM未归因差异政策不变：原因未知不阻塞恢复，不猜归因；Room主库字节与逻辑以保全为准。旧123 FAIL/121越界冻结不改；业务NOT_RUN；本步不冒充123当时正常收尾。禁Claude/Cowork/Claude-Code/DSH/.codex/memories私库含自动记忆、Git提交push、云SSH/SFTP、D回灌、实体手机、购买换号、存量删移、源码/build/JVM/host主入口/新attempt/runId预算。每工具<=45秒回模型（分支1后台监视器与等待轮询除外，须透明记录）。用户停止优先。完成后停止设备动作等Root/Codex核，不自称通过。最后单独一行：这是任务126-v2。
````
