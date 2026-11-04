# 63-v3-device-1核验：ABC/clamp采纳，SAF失败，恢复审计待补证

## 任务编号/版本

63-v3-device-1，Root核验日期2026-10-19。单run CRA63_DE_JOINT_20261018_232820，attempt_v3_device1_20261018_232820；业务入口23:28:41→23:42:16，owner继续恢复补完，末次原始收尾记录23:56:49。联合FAIL，没有新设备重试。

## 输入指纹

实算入口 `21c46382512763fbd5670fcc03ad80879560ec8f8ceddfa13dcb82ddedde4f30`、101绑定 `9e6b2eb76ad69f183591bf1325b4916f0129a4bdbd931dfbb4612c507f6abbd8`、冻结 `31f0f8594bd2f096c3d3e3534c4254e9c8878c66ca464f3e403196968e0090dc` 与接收基线匹配。launch.json绑定原APK及正确cwd。复用不变源码/JVM/Lint/APK证据，不重建、不重新整套核工具。

## 所有权与改动

设备owner为外部执行者；Root只读REPORT-v3-device-1.md、ready及本run直接原始证据，未见相关活动Python/Gradle任务。Root仅写本报告、[小型实算结果](../../2026-10-19-task63v3-device1-root-checks.json)及current，不改原报告、attempt或绑定，不操作设备。

## 环境

本run记录MuMu实例1、fresh serial 127.0.0.1:16416；实例0未启动。cwd为原63任务目录，实际worker stderr包含v3适配安装标记。实例/ADB状态取本run结束时原始记录，不冒称Root当前实时查询结果。

## 执行与结果

- **ABC采纳。** abc-entry-result ok/admitted/forensics_ok/restored均true；六个journey均PASS，严格consumer中六个ABC item均all_ok=true。DE.strict为false/未运行，不能计入ABC或整链通过。三份长目标JSON实际261–270字符采集成功；短路径路由已获本次真实ADB证据，Windows底层原因仍未作A/B对照。
- **clamp采纳。** seed及10/20/30/35/40五步result均PASS，整体ok=true。没有为核验重跑仪器或严格消费者。
- **SAF失败确认。** 实际phase worker rc3；binding交付中的精确unlink+rmdir rc1使producer allOk=false；随后清理/授权恢复仪器报缺本run pre-grants快照。原始stderr、saf-delivery-diagnostics和仪器断言保持。不以相似历史问题认定权限/命令的底层根因已查明。
- **Room4、DE和总消费者NOT_RUN。** SAF失败后停止推进，不能用已有离线结果替代。

## 原始证据

所有E路径基于 `E:/creation-reading-assistant/task63-common-device-entry-v1`：

- `attempts/attempt_v3_device1_20261018_232820/attempt-state.json`、`a/abc-entry-result.json`、`logs/abc-stderr.txt`、`logs/clamp-execution-result.json`、`phase_scratch/saf_execution.out.json`、`evidence/raw/saf-delivery-diagnostics.json`。
- 同attempt `device_recovery/protected-baseline-initial.json`，与 `root-review/device-run-v3d1/recovery-completion/final-check/protected-original-final.json` 由Root独立比对路径/size/SHA。
- 补完目录的leftover-inventory.txt、cleanup-log.txt、post-cleanup-verify.txt、final-state.txt及final-check原件，未改写自动恢复中的FAIL。

## 未覆盖项

**恢复证据分层，不能直接采纳报告“当前无未解决恢复义务”的全部含义：**

1. 原受保护数据：Root独立确认71私有、820外部及其中616 tmp的前后路径/size/SHA全相同；设置值相同。最终保护ok=true/diffs=[]、候选包absent证明、实例回关闭及定向断连后ADB空记录存在。此合同范围的恢复可采纳；没有发现这些保护文件的字节差异。
2. 新创建物/唯一证据：自动cleanup_tmp因未登记SAF _binding.json拒绝，未进入owned-tmp归档清理；owner随后手工对三个目录rm -rf。交付cleanup-log.txt中的rc为空，且一个rmdir仍报Directory not empty。剩余文件数和最终保护一致不能反证每个被删新文件已保全。
3. 在交付attempt及补完目录中未见完整owned-tmp归档，也未见116个删除前文件的集合/size/SHA与留存原件逐项映射。已有binding.json、本地clamp材料等可能覆盖部分内容，但需用现有原件证明，不能凭run/serial/freeze内容或路径名直接证明整棵目录可删且证据齐全。故新创建物归属、证据保全和精确执行审计尚未全部闭合。

这是**已交证据缺口**，不是认定原受保护数据损坏或设备仍运行。不得重新生成旧run证据、事后伪造删除rc或启动设备补演历史。若原件确实未留存，应如实登记不可追补缺口与影响。

## 恢复与副作用

Root设备/ADB0、构建0、删除0。原记录中的手工恢复动作由原owner执行，Root不重做。两个自动化保持PAUSED，无外部派单、Git、发布或D产品回灌。

原报告给出新增87,639,846字节：换算为约83.58MiB（约87.64MB），其“87.6MiB”为单位口径错误，不作为业务阻断。唯一证据/恢复资产继续保留，不清理旧目录。Root新增材料仅小型D文本。

## 交付状态与下一步

**本次设备批次结束：ABC/clamp实证采纳，联合FAIL；受保护文件恢复核同，新创建物恢复审计待补证。禁止自动下一次设备attempt。**

下一批可安排两个真正独立的离线工作包：

- A：SAF任务专属适配最小定位/修复，查清精确unlink+rmdir rc1及其与pre-grants缺失的真实因果，保持未知资产拒绝和清理失败阻塞；不改共享producer/产品，不放松消费者。修复后通过实际适配入口回归、由唯一owner更新真实依赖绑定。
- B：只读对账本次116个被删创建物的既有归属、归档/留存原件、size/SHA和执行回执；只写独立报告，不改A源码，不补造证据、不操作设备。可证明的直接采纳，缺失的明确记不可证明，不另搭框架。

两包合流由Root维护唯一现态；修复和审计有结论后，再由用户决定一次新的设备批次。现有ABC/clamp与构建证据复用，不为等待SAF重开已完成开发。全产品分母未知，无实体手机/MIUI通过结论。
