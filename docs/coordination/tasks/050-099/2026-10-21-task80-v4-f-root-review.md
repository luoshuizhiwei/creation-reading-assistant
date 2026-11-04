# 80-v4-F Root核验（2026-10-21）

任务编号/版本：80-v4-F-Root。实现与恢复接线采纳；联合业务验收未通过。全产品分母未知。

## 输入指纹、所有权与改动、环境
Root只读核验F/v4、integration/v4和唯一attempt，未发现匹配v4执行入口的活动执行者。外部REPORT实际SHA256为9a2737ade9b492c4e931b603843b200dbc614f4927cb62bc04be935944ad0a85。00-input-bytes.json四份冻结/绑定/ready/provenance原快照及当前路径全部SHA匹配（4/4），不存在v3那样事后回写。唯一构建工作区仍为task59-common-candidate-v1/candidate/android，缓存D:/develop/env/gradle。本次Root不构建、不发设备命令、不改产品或外部交付。

## 执行与结果、原始证据
run 20261021-161703-6d3f53；核读10-probe_verify-verdict.json为PASS；10-material-verdict.json虽rc=0但JUnit失败、ok=false，不能按进程退出码判通过。99-summary.exit_code=1，main_error为material instrumentation failed，recovery_errors=[]。
直接原始10-material-instrument.log断言MaterialGatheringJourneyTest.kt:1565：prepared=false、rootsOpened=true、downloadVisible=true、downloadClicked=false，locationConfirmed/fileNameSet=false，无mat_70_export_asset。四业务旅程0/4通过：material FAIL，其余orphan/source_state/md按合同NOT_RUN；探针单独PASS。
F/v4/evidence/wiring-result.json为13/13离线边界模拟，复用不重跑；不是设备通过。22条serial-binding回执全部semantics_unchanged=true且current_serial=127.0.0.1:16417。

## 恢复与副作用、未覆盖项
实际5份20-recovery步骤回执均ok=true：finalize_owned_packages、cleanup_tmp、restore_settings、final_protection_check、keep_running_verify。初始与最终保护原始结果均71私有/820外部、diffs=[]。保活原始keep-running-verify.json为ok=true、shutdown_requests三个值均0。无新增导出及导出删除；不称设备全部未执行删除命令（tmp清理是独立恢复步骤）。这些只证明该次MuMu模拟器运行及恢复，不是当前实时设备状态或实体手机/MIUI验收。

## 交付状态与下一步、停止条件
独立只读explorer同时定位真实源码调用链：MaterialGatheringJourneyTest.kt 1563调用prepareDocumentsUiDestination，878-900的helper以全root中标签存在判断Downloads可见，811-820的clickLabel遍历精确标签及clickable祖先。原日志providerLocation已指向Downloads，但点击仍失败；现有标签日志不能证明抽屉目标节点及拒绝点击原因，不能直接认定产品故障或盲改为成功。
下一批80-v5-G同一owner修复该保存器仪器接缝，按实际节点/authority/当前位置证据选择最小策略，保留唯一文件名读回、冲突及正文链严格断言；只为新源码作必要编译并复用host与未变证据。完成新独立绑定/冻结后执行一次联合验收并恢复。APK来源及恢复合同不放松。
当前没有第二个已定位且独立的实现缺口；其余旅程NOT_RUN不能当作并行实现。已用独立只读分析与Root核验并行缩短等待，后续同一源码、联合构建及设备由唯一owner串行闭环，不制造泛审查任务。
未知owner、身份或输入漂移、预算不足、必要恢复失败即停止依赖操作；同批不第二次main。沿用用户“都运行，不用问我了”本批设备与本run已证新建且保全size/SHA匹配导出文件的精确收尾许可，不扩大旧资产范围。实例不关闭重启、结束保持运行。两个自动化保持PAUSED，无外部消息/Git/桌面/云/存量清理。
F报告新增空间约4.8MiB（交付自报，Root未另做整树扫描）。Root仅新增本报告及替换current当前段，不复制工程/APK/缓存，不重复已有测试或设备操作。
