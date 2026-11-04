# 任务161-r2：真实fixture缺席门闭环

编号161-r2；2026-10-27 HK13:23后；推荐保持当前WorkBuddy global:deepseek-v4.1-flash 1M。仅离线，与162诊断独立；设备仍阻塞，不启动模拟器。实际UI收到任务才开始20分钟预算，原161/r1时间保留，不循环重派。

基线：E:/creation-reading-assistant/task161-temporary-entry-host-offline-v1/recheck-r1/ready.json。host161_r1.py 14668B SHA b2d74341d3bf1ae32232e55fd665cdb3bf5ddf0287f3d7d45ee0db0ee69ed79f；collector161_r1.py 20398B SHA0267792e0c63e6fef4589bc6cc65e8b1b16bc263658951f57840226756cfd3a2。原件冻结，不改旧日志/报告。157 APK/source/JVM/compile与充分旧case直接复用，不重build/全suite。

Root发现具体缺口：host161_r1.py fixture门使用dev.sh('shell', "if [ -e target ] || [ -L target ]; then echo PRESENT; else echo ABSENT; fi")，未证明/data/user/0/pkg/files父链可达，内部test权限错误可被else echo掩成rc0/ABSENT。只mock整个probe rc非0不证明实际脚本拒错。

所有权：仅新增 E:/creation-reading-assistant/task161-temporary-entry-host-offline-v1/recheck-r2/ 内host/必要inputs/最小patch/定向validator/REPORT/ready。collector若无需改仅引用r1实算pin。candidate、APK、Gradle、设备、A160/162、旧任务目录只读。禁止任何真实mainattempt/device/ADB/安装/卸载/VM操作、Android源改动、Git/云/私库/自动记忆和删移旧件。

修复：真实默认main的fixture门复用已pin host158.py的probe_absent_158（30578B SHA22ce70f8c547466a79ca74a833b0fb9ed1650f99452fbf898f6e4fa4c1d22ded），或最小等价生产接线。必须真实su0外部调用，逐祖先stat成功且directory非链接、直接父完整枚举rc0且精确名缺失才NAME_ABSENT；目标列为存在则拒进入instrument，任何权限/枚举/命令/链接错误总裁决拒。不把任意stat非0解释ENOENT。证据保存实际脚本/argv/rc/stdout/stderr，不由复制谓词validator自证。

必要定向验证走真实默认main/parent run_attempt/原cmd体，strictmock只外部subprocess/RUN_FN：正常缺席、已存在、祖先权限拒、父枚举权限拒、祖先或target链接拒；保留原红→绿或准确标注新增负例。检查旧testfalse+echo body的错误被掩与新真实stat/enum原回执拒错差异。不要StubParent/测试内override生产门/cmdlambda0/手改summary。复用r1两出口exactowner与真实host captured instrumentation充分日志，不重刷全部9case。仪器argv若从父合同重建，说明不是独立捕获的实际argv；不能升级声明。

存储：新增源码与文档≤128KiB、新增raw+全部synthetic生成证据合计≤128KiB（不能将offline-run文件另列排除fake预算）；不复制APK/整树/旧suite。复制前核空间、REPORT写实际分类总bytes；超限先如实停止报告，旧件不删。保留r1 offlineRunEvidence875816B单列排除预算的限制，不补称其原预算通过。

交付：REPORT列实际改动/pins/红绿rc/真实接缝/未运行设备/总存储；ready.json含完整绝对路径/size/SHA/实际时间；唯一ready E:/creation-reading-assistant/task161-temporary-entry-host-offline-v1/recheck-r2/ready.json。遇用户停止/Esc立即停；安全或权限边界不绕过。最终总结末行单独写“这是任务161-r2。”