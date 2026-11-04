# 63-v4 A/B合流：清理修复已实证，字节审计收口，Root回读候选待设备

## 任务编号/版本

63-v4-A、63-v4-B；Root最小回读修正版v4-r1，2026-10-19。A完成修复与一次真实设备验证但联合FAIL；B完成旧run证据映射。Root本轮没有重试设备。

## 输入指纹

原63冻结与APK原路径复用。A运行实际launch记录：runner `62430770202d2adaf2fbf19c3bcf72764263e6b57efc565a052c21369594224a`；当时103项绑定 `63842fbc8557f2b343a2ab5cc08c0af52006ae7019a9c67c7c4e3ce060deb32a`、adapter `eeee86013a2ab3e98c25e6d7f8aca6dbc00cb072f010a9675ecc1ccffe9a9c56`。

交付时adapter已增加回读修复，实算SHA `ea4329d626703a525279c05f1a66c8af6f71ec25516d17e5c1f831b97d5d5da3`；104项绑定 `e410b83ae58a1db950e082bf0255fb636fb9b4e9299269718dc43d6d26caf9bb`。**103项运行版与104项待验证版分开，不把最新候选当本次已完整实测版本。** 原报告ABC包装一行SHA误写，实际v3包装以绑定中的 `8a2fb0fda7fbb7a8de9a5933844ac987a5431c409717e5c5114eaadcbbfca622` 为准。

## 所有权与改动

未见相关活动Python/Gradle执行者。Root只读A/B报告、源码及直接证据，不修改其交付；在 `E:/creation-reading-assistant/task63-common-device-entry-v1/root-review/v4-r1` 交回读错误边界最小差量、薄入口、注入回归、107绑定和小型核验记录，并更新本报告/current。未复制工程、缓存、旧证据或APK。

## 环境

A一次run `CRA63V4_DE_JOINT_20261019_071209`，attempt_v4_saffix_20261019_071209；业务07:12:33→07:25:50，原63目录cwd。MuMu仅模拟器结论。A构建0，Root设备/构建0；复用已有JVM/Lint及冻结证据。

## 执行与结果

**A：原清理根因与修复采纳。** Python百分号格式化把shell最长后缀剥离`${actual%% *}`折叠成`${actual% *}`，两空格sha256sum输出留下尾空格，比较失败使set-e在rm前退出。任务专属修复保留最长剥离、inode/SHA/唯一后代门禁和精确unlink/rmdir。原始追踪与真实设备回归存在；这些是设备定向验证，不应笼统称离线Mock。此次真实联合运行binding从失败变为成功，导出仪器rc0，清理/授权恢复仪器报告成功；ABC及clamp仍PASS。

**A：联合仍FAIL。** shell uid exec-out cat读取应用导出证据返回Permission denied文本，producer按JSON解析失败；冷读/分享未执行，Room4、DE、总消费者NOT_RUN。原共享producer磁盘未改。新增root/base64逐字节回读候选已经交付，但没有第二次整链设备结果，不能宣称SAF整体通过。

**Root：不吞错误的小修。** 最新回读候选对log.add异常pass，base64解码异常变b''，Root直接调用该实际适配的无设备反例均复现。Root去掉日志吞错，解码失败携带remote/stdout/stderr并保留cause抛错，保留仪器失败守卫、原字节、JSON校验、读失败记录及producer原缺证据语义。**8项注入检查通过**（含2个原版红例），没有设备调用。薄入口沿A原runner加载，只改候选适配和绑定，实际worker重新加载仍指向Root入口；完整阶段2760秒+1200恢复、clamp720、Room300保持。

**B：116/116字节保全采纳，不再继续派审计任务。** Root逐项核源设备清单size/SHA和23份留存文件：116个唯一路径、0不匹配，114项同run原件+2项共享fixture字节相同；未引用A新run回填旧证据。可以缩小之前“116项原件映射缺失”的结论。旧run最终完整保护库存与基线相同，可支持这些新增文件不在最终受保护文件集合；B所说“只有计数”忽略了Root已有完整库存核验。历史手工rm-rf的rc未记录仍不可追补，无owned-tmp tar及目录级rmdir失败记录仍保留，不能编成精确删除执行全有回执；此限制不再扩大为原数据未恢复或唯一字节已丢失。

## 原始证据

A/B原件位于原任务v4/saf-fix/、v4/recovery-audit/。Root目录：B-independent-check.json（116项源与留存实算）、device-recovery-checks.json、read-boundary.log、test_read_boundary.py、read-error-boundary.patch、candidate-checks.json及生产薄入口/适配/107绑定。原始设备FAIL和launch身份保留。

Root下一候选唯一入口：

- `root-review/v4-r1/task63_root_runner.py`，SHA `ef424628414f75ce8e96ec4b38f41363c247cfdcdd23581a42b346dab35b0431`。
- `root-review/v4-r1/tool-bind.task63.root-v4-r1.json`，107项，SHA `65b8cf8ff71a4b721d42b2c3b63d191849402324d2cc55a42a5085ed8728bc88`。
- 适配SHA、实际worker路径及预算见candidate-checks.json；原104/103版不冒充Root候选。

## 未覆盖项

Root回读候选未经历完整SAF设备运行；Room4、DE、总消费者仍未有本候选设备通过证据。原设备103版的完整源码/绑定历史快照留存不充分，当前104及Root107不能替代历史运行输入；不因此重建或回填旧记录。下一次run前固定实际版本，运行中及其后不得覆盖该版本证据输入。

原116项执行审计限制与疑似空目录残留按历史事实记录，不为补历史rc重启设备。全产品分母未知，无实体手机/MIUI通过结论。

## 恢复与副作用

Root独立核同本次A设备前后71私有、820外部路径size/SHA。自动恢复五步均ok、recovery_error=null；owned-tmp.tar **115项**成员集合/size/SHA全核、tarSHA一致，结束tmp616。A本次恢复证据可采纳；本run结束实例关闭/定向断连记录保留，不冒称当前实时状态。A额外复现夹具处理的size/SHA清单是本轮记录，不能拿来补旧run116项的删除rc。

Root回读核验只产生小型候选及文本，无设备、构建、既有资产删除、Git或外部消息。B包中间文件收尾为其本轮临时材料，不作为既有资产清理授权。两个自动化保持PAUSED。候选代码/patch为必要交付，原始日志/映射为唯一证据，历史冻结/APK/恢复档案仅引用；新增字节统计见Root目录candidate-checks.json补充字段。

## 交付状态与下一步

A清理修复的设备实证与B字节对账采纳，Root回读错误边界小修及107候选离线采纳。**不另派整批返修或重复审计。** 下一批直接使用Root候选完成一次新run的必要SAF→Room4→DE/总消费验证；若真实联合入口要求ABC/clamp前序则正常运行，不跳准入。正确cwd、fresh基线、一个run/一次attempt、完整预算和恢复，失败不现场改码重绑重试；新输入保持独立，不覆盖本次103版历史记录。新批次按用户派发执行，不在本次核验中自动启动。
