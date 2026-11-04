# TXT-v2与J1验收准备：独立核查及窄返修

## 任务编号/版本
CRA-TXT-V2-PREP-CODEX-REVIEW-v1；2026-10-05。

## 输入指纹
TXT-v2完整980文件SHA重算零不一致，7份JUnit XML共36 tests/0 failures/0 errors/0 skipped。验收准备12工件SHA逐项匹配，Root独立在新E目录复跑18/18离线场景，与原期望一致。原件不改。

## 所有权与改动
Root仅写本报告/current/TODO和E独立核查证据；不改产品，不构建、不ADB、不Git。两项已交付但不能直接关闭实现与最终验收准备。

## 环境
Windows/Python3.13；离线重跑使用--work/--results指定Root新E目录，避免覆盖外部证据。

## 执行与结果
### Standards
无新增所有权/工程规范违规发现。旧证据保留。
### Spec
[P1] TXT落位失败可能形成无状态进展的重复滚动：while每轮新建snapshotFlow().filter(nonnull).firstOrNull()，布局已就绪会立即返回相同目标；scrollToItem后firstVisibleItemIndex不是目标则继续循环。没有等落位或新布局变化，可能反复强制重测量并持续pending。plainRestoreSettled只接受目标为首项，目标位于书尾短项、底部clamp导致目标可见但首项仍为上一项时，合法恢复也会被持续拒绝。需等待实际布局进展/落位、避免同状态忙重试，并明确定义底部合法落位。现有动态测试Frames.next消耗自造帧，测试重新实现runRestoreLoop，不调用生产等待控制流，没覆盖该风险。缺书籍bid/list身份key的取消保证也应在实际effect接入验证，不能仅job.cancel冒充书切换触发。
v1未就绪直接退出的P1已在v2等待路径关闭，保留此进展；不将新的循环风险视为全部无效。

[P1] J1证据检查器可缺原始执行/恢复账仍输出接受：Root重跑good_unit_start通过，evidence只有j1观察JSON和run-j1-offline-001.json，其内容仅{run_id}，没有逐方法runner输出/sidecar，也没有restore.json。checker仅核manifest存在/run_id，不复核真实方法判定，也不核真实restore合同；所有检查绿后输出J1_EVIDENCE_ACCEPTED。名为reject_missing_recovery_evidence的用例实际删除j1_10_recordExitSaved，证明不了缺恢复证据会拒绝。当前文档称“批次判定来源”不成立。应复用已验驱动的report/原始门控，与J1行为合同组成明确双门禁。新增反例必须覆盖行为JSON全绿但缺/失败恢复、原始runner失败/缺失、只有run_id空manifest。

准备工件另有可执行接缝：prepare_binding依赖cfg[_config_path]和cfg[driver_dir]，模板/直接joint_driver.run_all调用未保证注入；ADB动作未强制非零退出检查。小修在验收准备范围处理，不重写全驱动。Kotlin尚未编译保持NOT_RUN，不提前占MuMu。

## 原始证据
- [TXT-v2外部报告](../../dispatches/2026-10/2026-10-05-txt-reentry-fix-v2.md)
- [验收准备外部报告](../../dispatches/2026-10/2026-10-05-txt-final-acceptance-prep-v1.md)
- [Root独立18例结果](<E:/creation-reading-assistant/txt-v2-prep-codex-review-20261005/offline-results/SUMMARY.json>)
- [只有run_id仍接受的manifest](<E:/creation-reading-assistant/txt-v2-prep-codex-review-20261005/offline-work/good_unit_start/evidence/run-j1-offline-001.json>)
- [该例实际门禁输出](<E:/creation-reading-assistant/txt-v2-prep-codex-review-20261005/offline-results/good_unit_start.json>)
生产与测试精确文件见外部freeze与payload，保持原件。

## 恢复与副作用
本轮无设备变更，J5限定行为和恢复不重跑。文档编辑前副本存本轮E目录before。

## 未覆盖项
最终TXT修复APK未冻结/未验；J1产品缺陷未关闭；验收Kotlin未编译；全产品分母未核。

## 交付状态与下一步
两任务均窄返修：TXT-v3只处理等待落位/底部clamp与共享真实控制流回归；验收prep-v2只补原始runner+恢复门禁及可执行接缝。仍可独立并行，不新开功能，不重做已验J5。两项就绪后单owner合流构建+MuMu。跨候选旅程证据4/5=80%维持，不能当最终同APK联合进度。
审查汇总：Standards 0项；Spec 2项P1。
