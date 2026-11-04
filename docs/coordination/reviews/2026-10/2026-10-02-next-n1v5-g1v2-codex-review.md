# N1-v5 / G1-v2 独立核查与组合反例收口（2026-10-02）

## 任务编号/版本

Codex-review-N1v5-G1v2-v1，来源用户“都完成了”。本轮只核既定成果，不据该表达确认grilling产品草案，不扩产品功能或Git许可。

## 输入指纹

恢复HEAD `e39a78da9c62f02614207cffb36969a32492eaea`、暂存区空。N1父v4/候选v5各878非生成文件，4改/874相同，无增删，八父SHA与前轮核查一致；JsonBridge及Profile保持原字节。G1父v1/候选v2各877文件，单仪器类改动/876相同。完整SHA与树差异见[核查JSON](<E:/creation-reading-assistant/n1v5-g1v2-codex-review-20261002/verification.json>)。

## 所有权与改动

Root只写本报告、现有current/项目页/TODO/skill记录及自有E审查文件；只读reviewer核N1真实路径。编辑前原文保存在[E before](<E:/creation-reading-assistant/n1v5-g1v2-codex-review-20261002/before>)。未改候选、共享产品、原损坏仓库、桌面/HOLD、外部原报告或Claude独立库。未暂存/commit/push/reset/checkout。

## 环境

Windows/Python3.13、SDK34 aapt/apksigner。本轮只执行证据解析、SHA、manifest/签名与静态调用链审查；Root未重跑Gradle、安装或操作MuMu。原应用/模型身份只来自报告，不是实际界面核验。

## 执行与结果

### Standards

0项独立规范发现；两项候选改动均在所有权内。

### Spec：N1-v5仍1P1，两个原单步反例局部成立

原绿XML20+57+16=93 testcase、0 failure/error/skip，副本等候选build/test-results；原红XML2 testcase/2 failure。新ZIP用例确用真实JsonBridge+受控DAO，而非缺equal字段的结果桩。报告只提供green-run-summary文本，未发现完整Gradle控制台原日志；采纳实际XML结果，不冒充Root重跑或真实Room回滚。

**P1 冲突保护没有约束全部补偿分支。** `LocalZipBackup.kt:261–282`的conflict只参与equalAuthorized，`isSameFingerprintCandidate`/`isStartedEmptyFingerprint`仍可单独授权；`StateStore.kt:160–165`按路径删前序义务，Backup331–340随后清状态。同一根因有三条可达路径：

1. A正文复制中断留下hash(A)→第一次B（同JSON不同正文）被挡，但当前指纹存为B、unresolved保留A→第二次B指纹相同且路径属继承义务，补偿OR绕过冲突，复制B、清掉A并放行导出。可直接延长新增真实JsonBridge测试，再导入一次B。
2. A中断→缺backup.json坏ZIP：begin继承A义务但新指纹为空→B经empty-fingerprint OR绕过冲突，复制B并清A。
3. legacy长度义务→B→B：第一次保留未知义务，但保存B指纹；第二次同指纹分支把长度义务替成B SHA并清除，未证明B为原来源。

原两P1的单步equal冲突/直接长度clear都已修；整条import/retry合同仍部分关闭。应把冲突作为整个计划/复制/义务转移的统一守卫，而非只修某个OR。若源不满足已继承义务，不得因空指纹、最近指纹或路径相同消除它。旧长度证据不足时继续阻断，不猜测来源。事务接缝、strictly-newer、匹配SHA的A→B→A、finally journal无需重做。

上述为真实静态可达路径，非本轮执行的组合测试或设备复现。

### Spec：G1-v2原两P2已修，L1仍1P2与冷启动证据缺口

单类SHA `7add5e80acb645d36a17b7f89ad8b6958c0e385b53f9323be7091c24422db373`与报告一致；测试APK `ebcf45bc533d6ba3a690857589d4f981190d304b9384b5cd44483c1b713d7744`，宿主复用`4bf670ab4006c6d60a7e18d8d31d0856d55755d6b8ff6994b3f41edc3b272531`。aapt实核包名/target/runner；双apksigner验证exit0、同证书。原构建日志成功成立，Root未重建。

- 上轮断言创建正文已修：contentPath纯读，创建只限seed；单次instrument内真实删除/撤销流程已编写，内存凭证不再依赖跨仪器调用。实际选择器、时限和异步写入仍未设备运行，不能提高为设备通过。
- **P2 TXT断言缺UI前置操作。** `savedFilterUiChain`436–445只点“在读”与面板保存，453却断言formatFilter=txt。真实`ShelfUiModels.kt:58`默认为空，`ShelfViewModel.kt:235–241`按当前session保存，不会因夹具全为TXT自动选择TXT。`FilterSheet.kt:206–213`已有TXT按钮，应真实选择后再保存，或者明确测试只验证状态；为覆盖组合条件采用前者。
- 463的scenario.recreate仅重建Activity；其后使用同一store，不能作为进程冷启动恢复证据。冷启动后必须有独立只读/真实应用视图阶段，不先seed或再次保存同名视图，否则覆盖了要证明的旧数据。
- 新createAndroidComposeRule会先启动UI，夹具另openDb建Room实例，需设备首跑核UI对种子写入的可见性，不假定多实例失效通知成立；准备在UI启动前seed，或使用应用同一DB实例。撤销后读取需等待异步完成，时间窗与等待实际取证，不延长产品期限。

## 原始证据

- [核查脚本](<E:/creation-reading-assistant/n1v5-g1v2-codex-review-20261002/audit.py>)及[JSON](<E:/creation-reading-assistant/n1v5-g1v2-codex-review-20261002/verification.json>)，含完整8SHA、XML、包名/签名输出；N1完整文本日志缺失如实登记，不再开纯报告任务。
- [N1-v5原报告](../../dispatches/2026-10/2026-10-02-next-n1-repair-v5.md)、[G1-v2原报告](../../dispatches/2026-10/2026-10-02-next-g1-library-build-v2.md)保持原件。

## 未覆盖项

真实Room、组合反例实测、强制终止/存储故障、UI/MuMu未运行；N1未合流。G1的Activity重建不等进程冷启动；D2/迁移/N1不包含在G1旅程。实体硬件/MIUI/R8/正式签名/升级/F、桌面/HOLD原边界保留。grilling最终共享理解未答，产品规划草案尚未替换，不派扩展功能。

## 恢复与副作用

本轮零设备变化，未重复旧71文件后账。4现有执行状态文档有E回退副本，TODO其他项目后缀和skill既有字节保留；HEAD与暂存区复核。自动memories索引未写。

## 交付状态与下一步

本批交付2/2，规范发现0；需求发现N1 1P1、G1 1P2。原两类单步/原两夹具问题已局部关闭，完整验收仍0/2，不继承旧联合2/2。

下一N1-repair-v6只改Backup与其Test，使用真实分类一次补齐A→B→B、A→坏ZIP→B、legacy→B→B及正向回原A表，统一保护整个计划/义务转移，不叠加另一个局部OR。

为减少构建-only往返，G1-fix-accept-v3由唯一执行者顺序完成：同一仪器类修TXT选择/独立冷启动读回及设备首跑的夹具时序→定向构建并自核精确测试-only差异/二进制→独占MuMu限定旅程→完整恢复→报告交Root独立审阅。这是当前已有书库验收许可内的执行排序，不是设备已通过。若新包预存、设备占用不明、差异越界或恢复边界无法保存，则停止依赖部分，继续独立构建/报告。与N1源码修复并行，无同设备第二owner，不触正式包/书库、不提交。

A–E5/5=100%及既定旧联合2/2=100%复用；当前新批尚未设备。全产品分母未核定，不能报可靠整体百分比；历史11/23=47.8%只是证据归属，待定位确认后重核范围。
