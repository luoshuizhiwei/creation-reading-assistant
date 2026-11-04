# 任务179-r3：最小收口返回字段、ginstance回归及完整回执

原179 owner同会话，GLM-5.3-Flash最高；8分钟评估自实际发送起，不重置历史预算。仅离线，不再真实探针/设备/Gradle。只新 E:/creation-reading-assistant/task179-instance-identity-adapter-offline-v1/recheck-r3/，独占最小源码差量/必要验证/REPORT/ready；不改r2或冻结父。你不是唯一工作者，不覆盖他人修改。仓库AGENTS和current页首为准。

Root已读r2实际源码/报告/原JSON：entry_wiring_r2.py2978B cd6079c1fdfed00a24206159468cd423d4569b31af8996dcfa81e10b114326ee，identity_adapter_r2.py16631B c27fd5ac03adfeb349791d4afdcec0647f83645e49ee8d531aa64d94c3718ff6；ready4137B8bbdce7d67e9925ac5d1720071906ce47bb89654d71076acd1b4f51b2a416956。独立bind/run_main/真实loader出口绑定/finally恢复及r2边界有限接受，不重做176或r1/r2充分suite。剩余三项实际缺口必须一起闭环：

1. r2定义devices_raw=''后未赋dev.stdout，最终返回adb_devices永远空，这是r1回归。恢复真实原stdout与兼容返回字段，不用fixture串硬填；正常形态定向断言返回值与真正adb devices回执逐字相同。
2. r1在.nemu与config双方ginstance存在时拒绝不一致；r2读取ginstance_from_config却未比较，删掉了已有门。恢复此条件比较；任一侧缺失仍如实记录null限制，不补造。一个双方均有且不同的负例足够证明回归关闭。
3. _require_ok仅存rc及截断stderr，不存argv/stdout；listener局部rec被丢弃，ADB返回只存trim值且无argv，second boot未完整持久化。与任务“完整回执保留”不符。以单一实际IO recorder在新生产adapter边界记录每个本次真正执行的外部调用（argv/rc/完整stdout/完整stderr/顺序），包括before/after manager/listener/process/ADB命令，返回在bindingEvidence内；不靠验证器外层独有日志，不改stdout给判据前的原回执。失败不吞错；未知外部异常如实传播，不能假造rc。必要文件读取rawSHA/size继续记录，不复制大磁盘。避免再建抽象体系，只最小接缝。

生产入口可复用r2已接受的薄层或新极小入口明确调用新adapter，不复制旧工程/测试夹具全集。不改变host scope/owner/baseline/恢复/collector门。原r1只读probe和r2实际main到owner停点直接引用，不重复main回放建scope，更不运行真实main。验证仅所改三项：一个真实形态正例确认非空adb_devices逐字一致及所有执行命令完整recorder原文（包含特意带换行且stderr超过200字的拒绝例，确认不截断），一个ginstance不一致负例，复用既有证明其余门。不修改验收语义迎合绿，不全suite。

独立子进程实际rc/stdout/stderr写原始小文件，别只在ready写rc0。引用r2已有验证脚本/数据时只读，避免覆盖其fixture；本r3仅必要小fixture/源码/日志。source/doc/patch/报告/ready≤128KiB；全部raw/fixture≤32KiB，无attempt目录/无需APK或缓存复制。复制前核空间，统计所有新文件互斥分类；索引不自引用、ready不hash自身。REPORT区分已交生产入口/离线验证/业务NOT_RUN。r2报告“18分钟超时”不可直接照抄，Root实际发送00:47:42.697，交付文件时间以真实mtime核对。原r1/r2错误与原件不覆盖。

唯一ready E:/creation-reading-assistant/task179-instance-identity-adapter-offline-v1/recheck-r3/ready.json；交付最小patch/新源码/完整sizeSHA、上述定向原log/进程rc、REPORT和剩余限制。无设备探针/ADB/VM/安装/instrument/stop/uninstall/Home/launch/reboot/connect/kill/设置/Gradle，无Git暂存提交pushPR发布/云/正式库/私库sessionDB/.codex/memories/自动记忆。边界冲突就交实际BLOCKED，不扩任务。最终末行：这是任务179-r3。
