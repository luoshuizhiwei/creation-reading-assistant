# 79-v3-BC Root核验（2026-10-21）

任务编号/版本：79-v3-BC / Root。环境：既有59构建工作区、MuMu1合同。Root仅只读核原始材料及更新D交接，未编译、未发设备命令。

## 输入指纹、所有权与改动
实际test APK 9832275B/SHA d75bf600f851a8277048f82e5dfea233051110134f29d38b807ce2253bf0828f；host 53804439B/SHA ceb60dd1a24455efbfcbcac4143a058b2d30e45b6f42659653ec7034925ae79a，均与integration/v3/ready一致。共享Material源码ea16df663f39eb2a60424cbf5da4b79c440a1a6e9221b13c22ce06bac96eec16匹配。四项冻结输入原件及attempt input-bytes快照SHA逐项核同。C/v3/picker-interactive-v3.patch实际SHA为30bf0765c67335b3b26737bdd15fca4e883de61def5672aff2440d4df4648e38，报告/closeout ready声称9060a2…446c，不能采纳后者；差异原因未证，不改原件。报告host体积80000534错误，采用实际53804439。Root未重跑绑定闭包全套或既有JVM。

## 执行与结果、原始证据
原始integration/v3/logs/assemble-test-v3.log证BUILD SUCCESSFUL in48s、build-result-v3 exit0；报告称3次构建，但当前本轮读取的原始正式构建日志仅支持最终成功这一事实，不另背书前两次独立执行数。
唯一attempt目录20261021-102429-78ab21，台账及process-exit记10:24:29–10:29:19、exit1。原始10-probe_verify-instrument.log：rc0但JUnit1测试1失败，第一条非空root断言失败，节点packageName:null。四业务旅程NOT_RUN，整链0PASS/0FAIL/4NOT_RUN，前置probe FAIL。
实际源码测试root1=AccessibilityNodeInfo.obtain()未赋packageName，同时断言返回root并activePackage为空；这与真实入口严格包名白名单矛盾。错误在成功测试夹具；拒绝报告提出的“空候选且activeRoot非空即可放行”方案，它会放宽身份门禁。修夹具及对应预期，增加空包名拒绝，不应改生产包名白名单。

## 恢复与副作用
直接比较本run原protected-baseline-initial与recovery/protected-original-final：protected_files、external_files、db_groups、apk_pair、settings、uri_permissions、pkg_states全部相等；final-protection-result记录71私有/820外部、diffs空。mapping仅stage run_id标签不同，forward/reverse/serial相同。五个20-recovery回执ok，recovery_errors空；两个tar实际SHA分别19072ff84c6f70517cf4fda5a74248a97db634b9ce07a2bc4aa68a0975812364、7a5eeddaf781b1bddbd398f48d90cda3d24472be5a9eda1f3b99049989fa41eb，匹配卸载前回执。10:29:18 keep-running原始回执PID/端口alive、adb device，launch/shutdown请求0，保留原boot_id。采纳本run合同范围恢复，不称整虚拟磁盘回滚；Root没有新设备实时状态。tmp步骤回执成功，未扩展成重新逐字节盘点。

## 未覆盖项、交付状态与下一步
实现与编译交付已推进；真实选择器修复及保存/URI读回仍未验；四业务旅程未运行。原FAIL/冻结/报告保留。79根目录实测5604022B，统计时包含当前交付文件，旧78APK保全在78目录不重复计；没有新增工程缓存复制。
下一步79-v4-BC沿原owner精准修正夹具，保留身份门禁，核patch实际字节并以新报告记录差异，离线必要编译可迭代；新冻结与明确单次设备批次后再验。不可承诺修夹具就能四旅程全绿。无需重做A/旧ADB或制造泛审查。遇所有权/身份/恢复不明停止依赖工作。automation保持暂停；无Git/外部消息/清理/云/桌面。全产品分母未知。
