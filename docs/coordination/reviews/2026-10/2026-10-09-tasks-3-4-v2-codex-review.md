# 任务3/4-v2核查

任务编号/版本：3-v2、4-v2；Root核查2026-10-09。报告可见2/2，原目标完整退出0/2；5/6保持上一限定结论，不重派。

输入指纹：3原992源码/41工件及双APK与冻结一致；本次Root对原输入逐SHA验证，交付7证据SHA验证。4对比993原源码路径，确仅6路径变化，987路径字节一致，前后SHA与交付清单匹配。4报告SHA5607acc395ef4aa7df412d54035044a91cb49484b616e665fad0632e7c472eef；补丁SHA766d213edb1e8c9855e81beb9ae25224415d08cb0a12a34180770daafe156d3a。

所有权与改动：3未改冻结输入；Root独立E补时间格式与离线FakeAdb兼容，不改产品/APK。4改Repository、ProfileUiState/ProfileRoute/StorageSubPage/DiagnosticsSubPage及CancelControlTest六路径。独立只读审查已返回，无设备/构建写入。

环境：隔离MuMu；3报告实际Claude Code/DeepSeek路由。4报告未完整登记模型/owner；不得以建议模型冒充实际。HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。

执行与结果：
- 3：完整mtime前置探针失败，31 NOT_RUN。原touch空格格式bad date；同设备ISO形式纳秒恢复精确。判定驱动格式兼容缺陷，不能说设备不具备纳秒恢复能力。正确停在安装前；报告“没有任何写入”应限定为没有应用/设置写入，因为探针有任务暂存写改删。
- Root3-v3准备：[新候选报告](<E:/creation-reading-assistant/joint-mtime-contract-v3-20261009/REPORT.md>)；8定向正反例、37真实驱动离线用例exit0，严格绑定无错无警告。产品/APK和31合同不变。新完整设备能力探针尚未运行。
- 4：报告实际位于[deliverables/REPORT.md](<E:/creation-reading-assistant/parallel-search-task4-v2/deliverables/REPORT.md>)。词项先于coverage提交、rollback不恢复旧代次、界面真实动作接线源码成立。CancelControl绿色Gradle日志exit0成立，但声称13/13的原始XML已被后续筛选覆盖，不能确认逐项数量；当前WorkerPolicy XML实际12/12，与报告“8项”不符，以原XML为准。

剩余4-v3范围（静态可控交错推导，须实际红绿复现）：
1. 共享userCancelRequested在新sweep占权后清零，旧取消sweep恢复时可能读false并PROGRESSED；取消必须归属具体代次/执行者。
2. testHookBeforeRebuildReset后仍无取消代次校验即清cursor，triggerFullRebuildNow仍无条件force排队；取消reset和后续排队路径未闭合。
3. UI仅isRunning显示停止，排队/重试阶段入口缺失；反射类存在测试不能证明真实dispatch链。
4. allowDiskReads为JVM存根而新增catch Throwable吞策略恢复错误，需尽量用定向测试mock恢复既有生产策略；保留本次有效修复，不扩大重构。

原始证据：[Root实算](<E:/creation-reading-assistant/joint-mtime-contract-v3-20261009/evidence/delivery-verification.json>)、3原probe/绑定/基线与7SHA清单、4green-verify.log/policy-verify.log/red-evidence-rerun2.log及WorkerPolicy XML。旧专项缺XML不据此全量重跑；4-v3必要定向重跑后立即归档防覆盖。

未覆盖项：31设备方法、搜索取消完整时序与排队UI行为、实体手机、X整机中断、Back专属归因/队列排空/初始新save。全产品分母未核。

恢复与副作用：3报告未安装/卸载/force-stop/改设置，只任务探针并清理。原保护基线扫描59业务文件，不等同全71文件备份；终检只包状态等有限证据，不能宣称全文件精确恢复已证。下一owner重新保存完整约定基线。Root本次未设备变更。4无设备变更。原E候选、D WIP/桌面/HOLD均保留。

交付状态与下一步：3-v3唯一设备owner与4-v3独立E实现可并行；保留编号，不重做5/6，不提前发联合合流。3新输入joint-mtime-contract-v3-20261009，新输出joint-mumu-accept-task3-v3；4新基树parallel-search-task4-v2，新输出parallel-search-task4-v3。D产品回灌、暂存、提交、push、PR、发布无授权。
