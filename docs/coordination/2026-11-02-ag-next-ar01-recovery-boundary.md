# AR01：恢复真实归属与失败关闭

版本：AG-NEXT-20261102-v2/AR01。日期：2026-11-02（香港时间）。状态：PLANNED，未派发、未实施。接任执行者：Antigravity获用户交办后登记真实owner。卡号不预占全局任务编号；先核state已有同项则接续。

## 共用合同

必须同时遵守[八项队列](2026-11-02-antigravity-next-queue-v2.md)的权限、单owner、资源、交付与停止条款；依据[Root复核](2026-11-02-antigravity-stage-delivery-review.md)，不得把旧生产者自报接受当已关闭本卡。接任Root可裁决常规技术方法，不等待原Codex在线。

## 输入和进入条件

AG01部分交付；Root新反例见offline-findings.json。输入为recover_E2_r2.py、真实EP013251原安装/身份回执和父166返回合同，不重做014321成功卸载。

## 文件所有权

本批recover_E2_r2.py及其离线入口测试、REPORT；冻结host136/158/166/179/184/192及R2原件只读。与AR02同runtime owner协作，不覆盖既有修改。

## 实施步骤

1. source runId/scope/serial/boot/user及真实安装回执解析并交叉绑定，缺失/错rc/错argv/错误归属快速拒绝；禁止mtime或任意参数充当真实创建时间。跨boot恢复仅走明确允许的父合同，不简单用当前boot覆盖原归属。
2. resolver核实serial与实际执行serial一致；pm/path/inventory等query的rc/stdout语义进入判定，不将查询失败当缺席。
3. 父结果None/空dict/缺合同必需字段拒绝；已缺席只算no-op，不跳过原test/data/userState、保护、设置/keepalive和最终身份核验便报恢复通过。
4. 最终identity置于所有保护查询之后；父恢复/各设置结果分项记录，不能仅检查night。
5. 离线从真实main/adapter调用点注入错误，确认mutation前拒绝及异常后非成功，而非仅测新的布尔函数。

## 验证和成功标准

正例实际source回执可绑定；错误runId/scope/serial/boot/SHA、空父结果、缺字段、rc失败、no-op缺保护、保护后身份漂移均拒绝。保留原17项充分用例，新负例覆盖本次Root复现。只交离线READY，不写设备恢复已验。

## 停止、预算和接续

首轮60分钟；同边界两轮无收敛调整方法。门未闭合仅阻塞新设备，AR06/07只读可继续。成功转AR02，设备恢复不为补证重复执行。

## 本卡交付

E盘报告根：`E:/creation-reading-assistant/task209-navigation-coverage-v1/continuation-20261102/next-v2/ar01/`。目录已有先核owner，不覆盖。交REPORT.md、ready.json、实际source/patch/前后SHA及必要原rc/log/XML；每卡普通新增预算512KiB，必要构建/设备保护原件单列实bytes与保留原因，不因超预算删原证。APK/工程/cache原路径引用，禁止按卡复制整套。

ready最后写并pin真实路径/size/SHA，不自引用；构建、业务、模拟器、恢复分别裁决。收尾核原证后更新current/state.nextContinuationQueue及受影响矩阵，成功直接转下一可执行卡；PARTIAL/NOT_RUN/BLOCKED如实，只有真实范围/权限/不可逆差额才问用户。
