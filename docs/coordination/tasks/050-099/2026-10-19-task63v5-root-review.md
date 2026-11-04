# 63-v5核验：SAF仪器全过，最终目录回收仍阻塞

## 任务编号/版本

63-v5，2026-10-19 Root核验。一次run CRA63_DE_JOINT_20261019_V5，attempt_v5_20261019_100715，10:07:26→10:22:16，联合FAIL；不重新执行设备。

## 输入指纹

实际输入快照与v5/input-versions.json、launch记录一致：Root入口 `ef424628414f75ce8e96ec4b38f41363c247cfdcdd23581a42b346dab35b0431`、107绑定 `65b8cf8ff71a4b721d42b2c3b63d191849402324d2cc55a42a5085ed8728bc88`、Root适配 `9b574bc791b90135fdf8d66039aa8d28c3e4aaca3139f35808393fe386bafe5c`。原冻结/APK复用，无构建。当前Root文档中的ea4329d6是104项候选基线，9b574bc7才是Root107适配；是两个版本的区分，不是要把二者改成相同SHA。原107输入快照已留存，不覆盖。

## 所有权与改动

未见相关活动Python/Gradle任务。Root只读原REPORT-63-v5.md、ready、实际命令/阶段结果、输入快照和本run归档，写本报告、[紧凑核验结果](../../2026-10-19-task63v5-root-checks.json)及current；不改源码或证据，不操作设备、构建或清理。

## 环境

本run使用MuMu实例1、fresh serial及原63任务目录cwd；预算3960秒含1200恢复。实际结束状态用本run收尾记录表述，不冒称当前实时设备状态。充分JVM/Lint、ABC/clamp及已闭合B审计证据复用。

## 执行与结果

- ABC六项journey/严格消费PASS；clamp seed+五步PASS，原始阶段结果可采纳。
- SAF四个仪器方法从实际_commands核得rc0及OK (1 test)，binding/process-end精确清理CRA56CLEAN:ok、rc0。逐文件root/base64回读取得合法导出JSON，设备接缝修复已实证。
- **最终目录回收FAIL。** 实际producer `_finalize` 第610行仍调用普通 `adb("pull", remote_run, raw_dir)`，rc1、remote open failed: Permission denied；collected空、missing5。修复仅覆盖_cat_device_file，未覆盖同一生产回收合同的目录pull。SAF整体未通过，Room4/DE/总消费者NOT_RUN。
- 本run包卸载前tar含8份SAF材料。Root在内存读取，记录成员size/完整SHA；导出JSON5418字节、SHA `2b2151bf6d4964e0f27ea2fd137c7d54b089762293772b404f276aeb48de4980`，与本runroot/base64命令读取字节一致。这证明同run证据保全，不重填collected、不回填SAF PASS、不用tar补算未运行DE/总消费者。

## 原始证据

E:/creation-reading-assistant/task63-common-device-entry-v1 下：v5/input-snapshot与input-versions.json、attempts/attempt_v5_20261019_100715 的attempt-state、logs/saf-execution-result.json、evidence/task35_evidence/_commands.json、device_recovery/final-uninstall-forensics双包tar、final-tmp-recovery-v6及保护前后清单。共享producer原件位于tasks51-52-codex-review-20261016/saf-root-fix/task52_saf_producer.py，只读确认_finalize回收/命名/资源清单流程。

## 未覆盖项

SAF采集/严格消费闭环未过，不能用四仪器通过替代。Room4、DE和总消费者未执行。全产品分母未知，无实体手机/MIUI通过。当前不把取证tar转成新PASS产物；后续可以用其构造明确标注的离线回归输入，原业务结果保持FAIL。

## 恢复与副作用

Root独立核同71私有/820外部前后路径size/SHA。115项owned-tmp.tar成员全集与verified-before-cleanup逐size/SHA核同，tarSHA一致，恢复结果tmp616/unknown空；自动恢复五步通过，候选包absent及原实例状态收尾有本run原件。可采纳本次约定范围恢复；8份SAF原件随本run包取证保全。无新设备恢复义务。

Root设备/构建/删除0，仅小型D文本；原报告/FAIL/快照/归档保留，两个自动化保持PAUSED。未改产品/共享producer/消费者，无Git/发布或外部消息。

## 交付状态与下一步

63-v5一次设备验证和恢复交付采纳，整链FAIL。连续两个回读接缝被逐个暴露，Root调整下一批方法：**不再只修单个cat或pull；由同一owner覆盖实际生产_finalize的完整回收→原始落盘→五项消费文件命名→资源清单/严格消费合同。** 不扩展成全项目泛审查。

下一批63-v6完成任务专属、窄绑定host/serial/run的root目录回收适配，保持共享producer原件和严格消费者；使用本run保全的8份材料做直接生产_finalize离线回归，验证五项collected、ledger/restoration原字节映射、失败阻塞、历史run拒绝及错误保留。若使用tar，外部归档路径/链接/覆盖必须严格限定，不解包未知成员到任意目录。

修复、真实worker接线及绑定完成后，在同一任务内执行一次必要新设备run，SAF通过后继续Room4/DE/总消费及恢复，不再把常规验证拆成新提示词。输入未变复用APK；确有必要构建按唯一现有工作区/owner完成。失败不现场改码重绑或自动第二次attempt。已通过成果及B审计不重开；本次Root核验不直接启动新设备。
