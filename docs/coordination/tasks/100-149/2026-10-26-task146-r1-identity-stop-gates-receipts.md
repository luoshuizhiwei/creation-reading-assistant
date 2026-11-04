# 任务146-r1：身份与instrument停止硬门、停写回执持久化（仅离线）

2026-10-26香港20:06，同原global Flash owner，15分钟，delta256KiB/raw512KiB。

````text
Root已读146-v1完整host14107B SHA62c3579ff29dacefd6f7d9d9f110c36f4de2c7d1fc2eff7123570fb72aa3d167、test19181B SHA5df0e8ed9ae96b3e9d0eaf24c685e9fcc433847913a3c669cab15cae72a19f54、REPORT/原log，二件指纹核同，pre→stop→post主流程修改事实复用。以下是新代码具体硬门缺口，尚不能设备，原146v1及145所有原件冻结。

A. preserve_with_quiesce：当前 `if not ident.quiescent: if not approved:return`，所以带approved时任何身份/探测错误（serial/boot/user不符、pid/ps未知）也会进入pre和force-stop。许可只针对明确“fresh身份有效但确有本批产品writer”的情形，不允许身份错误被批准覆盖。必须先拒ident.problems；明确quiescent直接复用已有稳定三根保全，不需要force-stop/额外许可；明确needsApprovedQuiesce且批准才pre→stop。main current-user现在仅stdout0不核rc，比r8退化，恢复rc/stderr边界。pre耗时后实际stop之前重新核同本批fresh身份/状态，不能旧身份跨窗口复用。

B. install_quiesce_recover接收父stop_ok却未在preserve前拦；instrument超时停止未证时可仍执行pre/stop。stop_ok=False必须在任何本任务归档/stop/uninstall之前阻断依赖动作，保产品并交原父安全门恢复独立项。本批installed含product及fresh serial/boot/user/manager身份有效/本次归属才允许停写，不以任意外来包状态授权。保持父原恢复stop门、不杀其他进程。默认未批准live仍NEEDS_APPROVED_QUIESCE/stop0/uninstall0/整体假。

C. 当前stop dict和postStop仅放out，hook返回decisions时不含它们，唯一quiesce-proof只ok/phase，原stop rc/stdoutstderr丢失。pre仅persistpre-snapshot标签、archive_roots完整proof没落；失败分支也不存最终phaseproof。为本run新pre/post路径持久化完整原identity-before/stop前identity/stop原rcstdoutstderr/postStop以及完整pre清单归档收据引用和最终结果（成功失败皆保因），不只报告口头或计数。落盘失败保产品整体假且原test/settings/protection按父安全门独立恢复，原错与落盘次错分列。空间估算可先用源文件size合计+tar余量并检查host精确输出卷可用，不全盘扫，缺空间不stop/不删资产；归档stderr勿只截400bytes当完整原件。pre-live准确标unstable不强求sourceprepost一致，但tar本身必须可读/成员归属本根并有根目录，不能rc0非空乱码就批准stop。

只必要新场景：批准但身份错误（含rc错stdout似正常）→stop0/archive0/uninstall0/整体假；父stop_okfalse→本任务归档stop0/产品保留；真正quiescent无批准→不stop且稳定保全成功；live批准成功→三根pre完整可读原件早于唯一精确stop、stop前同身份、post稳定证明早于uninstall1且原命令回执实际落盘；pre/stop失败原性质复用未变部分，保留146v1原红绿，不刷旧145suite。真main/真父run_attempt/collect/recover/cmd_*只mock外部出口，不测试local另写hook。端点严格参数核验force-stop --user 0 product，不能任何force-stop都让fake产品死；未知外部命令抛。

只新E:/creation-reading-assistant/task146-quiesce-recovery-offline-v1/recheck-r1/修改host/最小相对v1patch/REPORT/必要原红绿raw/独立summary及必要小fake归档proof/ready.json，旧v1/raw/SYNTHETIC冻结不rmtree，避免裁掉本次唯一证据却称可审。存储delta256KiB/raw512KiB，实际分类新增体积同REPORT。r8固定host/inputs/15rolepins/144匹配APK/Android源码APKGradle/父136baseadapter全冻结引用不复制、不buildcompile。任何真实ADB/VM/stop/安装卸载settings/hostmain/runid预算attemptsummary/F1/实体禁；参数只是未来许可开关不等本批授权。Root只核具体delta后另裁，不能自动设备。不Git云正式库私库自动记忆删移旧资产kill共享进程。最后一行：这是任务146-r1。
````
