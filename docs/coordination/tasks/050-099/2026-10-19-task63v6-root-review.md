# 63-v6核验：SAF回收闭环采纳，Room证据通道阻塞

## 任务编号/版本

63-v6，2026-10-19 Root核验。run CRA63_DE_JOINT_20261019_V6，attempt_v6_20261019_105322，10:59:26→11:13:39。一次真实attempt，联合FAIL；第一次shell重定向缺目录未执行设备进程，不另算设备attempt。

## 输入指纹

实算runner `2807507195bdce049bf6164617c0f2cf26b12f4e99e281acbab9176f32a5a0b0`、适配 `f177503e6504aac2ddae22b4fd8fc2e0b29caf74185c1a5b8420083e5449fd96`、111项绑定文件SHA `44f90060430a438a985b708cc6d078417d5445798d5a0e4983fb8098d0b49c0e` 与launch匹配。绑定manifest_digest与文件SHA口径不同，不混用。原冻结/APK保持，构建0。

## 所有权与改动

未见相关活动Python/Gradle任务。Root只读报告、ready、实际实现及必要原始证据；写本报告、[实算结果](../../2026-10-19-task63v6-root-checks.json)和current，不改E交付/源码/绑定，不启动设备。原报告实际在任务根目录，v6/存差量和日志。

## 环境

原63任务cwd、隔离MuMu实例1与本轮serial见launch及现场记录。设备状态只引用本run结束记录。源码/JVM/Lint、原APK及已采纳ABC/clamp/恢复审计证据复用，不重跑全量门禁。

## 执行与结果

**SAF完整生产回收闭环通过，采纳。** 任务专属适配包装共享producer实际_finalize的adb实参，窄接本run精确remoteRunDir；原collect=False、五项命名、资源清单、allOk仍由共享代码执行。8项离线回归及真实worker日志范围明确，Root未重跑已充分验证的原脚本。

Root独立核验本次真实SAF result allOk=true；root-pull回执7文件均与本地原始文件size/SHA匹配；三份SAF JSON及ledger/restoration→__ledger/__restoration五对文件**逐字节相同**。本次SAF收集通过不是用旧run归档回填；ABC六项及clamp seed+五步继续PASS。

**Room仪器4/4通过，阶段合同FAIL。** 实际instrument原始输出OK (4 tests)，但TASK60_DATABASE_EVIDENCE_DIR标记数0；包装器据此拒绝。测试源码tearDown使用println，生产阶段只从instrument stdout用正则提取，输出通道接缝有直接证据。报告所说“println走logcat”是合理解释，但本run未交对应logcat原文，Root不将其当已实证的唯一原因，也不直接采用未绑定本run/PID的全局logcat提取。

**DE strict及总消费者NOT_RUN。** 不把Room仪器通过改成阶段通过，不补跑旧attempt下游并覆盖原状态。

## 原始证据

原任务 REPORT-63-v6.md、ready-63-v6.json、v6/evidence/finalize-regression.log、worker-wiring.out.json及launch；本attempt logs/saf-execution-result.json、evidence/task35_evidence/_raw_CRA63_DE_JOINT_20261019_V6/_root_dir_pull.json、五项消费文件、logs/room-persistence-instrument.txt、原保护清单及恢复归档。Root实算记录只保存紧凑结果，无整套复制。

## 未覆盖项

Room证据目录未绑定并登记，本轮阶段不可采纳。卸载前包取证tar中可见4个task60-accept唯一根、4份数据库和4个lck；按task60相关名称核得16个成员（8文件），原报告33个成员口径未核同。数据库文件存在于tar不替代仪器标记/当前run归属，也不回填room-created-assets.json。

新版本必须证明证据来自当前run/serial/host与真实仪器执行，拒绝旧日志、旧PID、重复或伪造标记、错误包/根目录及数量不足；不能仅从全局logcat凑4条路径就通过。设备日志读取不授权清空公共logcat或删除未知资产。

## 恢复与副作用

Root独立核同前后71私有/820外部路径size/SHA；115项owned-tmp.tar与verified-before-cleanup逐文件集合/size/SHA一致，tarSHA核同。自动恢复五步通过，tmp616/unknown空，包级取证保全Room数据库，候选包absent及实例/ADB结束状态有本run记录。约定范围恢复采纳，不称整虚拟盘逐字节回滚。

Root设备/构建/删除0，仅小型D报告/JSON。既有资产、旧FAIL、报告/绑定保持。无Git/发布、外部消息、产品回灌或自动化恢复；automation-2/3保持PAUSED。

## 交付状态与下一步

SAF里程碑收口：实现、实际回收和资源清单已通过；产品范围继续原生Android稳定阅读/轻量取材与数据安全，不扩展功能。剩余关键路径转为Room取证接缝及最后DE/总消费。全产品分母未知，无实体手机/MIUI通过。

下一批63-v7安排两个独立工作包并行：A负责可信Room证据通道及唯一候选集成；B用本run现有同源ABC/clamp/SAF材料，预串联DE和总消费中可纯离线执行的实际接口，提前发现字段/命名/绑定缺口。B不伪造Room PASS、不改原v6结果、不把诊断写成设备DE通过，缺项如实BLOCKED，不调用设备。合流前B只写互斥诊断目录，不改A实现或严格消费者。

A选择可靠最小方案：如任务专属日志采集能够严格绑定本run可复用APK；如需要结构化instrumentation状态输出，则做必要测试差量、受影响打包和真实来源/冻结合同，不以“免编译”优先于可靠性，也不强制全图构建。合流后一次必要新run验证Room并继续DE/总消费与恢复，不再拆常规验证批次。旧SAF/ABC/clamp证据继续复用；若真实入口要求前序则按合同执行，不跨run拼成整链PASS。
