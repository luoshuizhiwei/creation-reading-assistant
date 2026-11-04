# 任务48–50 v2核验与接缝返修

## 任务编号/版本
48-v2、49-v2、50-v2实际交付3/3，合流放行0/3。下一批48-v3、49-v3、50-v3定向返修，2026-10-15 22:55 +08:00。

## 输入指纹
原基线见[Root v9报告](../../dispatches/2026-10/2026-10-15-root-stage-runtime-reader-v9.md)，v9的1023文件现场全核无差异，source manifest SHA 505c35235e2ecafc8cc248870753e2ca3c0b06e9adffc02e74debb658ab18dc5。48-v2实际4改2增，所有声明SHA匹配；50-v2实际1改4增（含保留v1测试），所有声明SHA匹配；49-v2一生产改、一新增测试、一更新v1测试全部SHA匹配。D HEAD e39a78da9c62f02614207cffb36969a32492eaea，index空。

## 所有权与改动
Root只写核验工件与文档，两名独立只读审查检查49、50；未改外部交付候选。48-v3继续拥有独立Android main和相关JVM测试；49-v3拥有主机host/runtime恢复链与Python测试（明确包括生产账本创建端）；50-v3仅探针与本任务规则/测试。共享构建/设备/全局文档由Root唯一负责，各v3目录由原任务名后缀改为-v3，保留v2。50不可抢改48的ReaderProgressEffects；缺诊断身份则保守不签保护结论或交Root接缝，不伪造。

## 环境
新证据根 E:/creation-reading-assistant/tasks48-50-v2-codex-review-20261015。本轮无设备、无APK构建、无正式书库、无D产品回灌或Git写操作。不刷新旧MuMu状态，仅保留原恢复证据。

## 执行与结果
- 48九份XML合计65项0失败；旧全局merge/repository改动已撤回，与v9一致，原3回归不再存在。Root新编译实际TemporaryReturnSaveGuard和原6测试，加2个源码接线边界序列，6旧通过、2新增失败。仅纯JVM及抽取的实际退出谓词，不是Compose/设备调度实测。
- 49 Root现场22/22通过。另运行实际restore_and_readback配现有FakeDevice：不同run但库存相同会进入restore_apk_pair/restore_files且最终restored=true；同run基线配另一run账本，步骤9仍下发rm-rf并restored=true。此为离线边界反例，无设备删除。对步骤9的外/内POSIX词法解析证明a b路径实际脚本目标变为a（其余成sh参数），并非精确目标。
- 50 XML绿13/13、红9项2失败及androidTest编译日志已核，完整12列比较已正确修复。Root另编译实际规则，新增2边界反例均失败：窗口操作前observe可误签保护；observe缺index/offset被null不等比较当成有效坐标变化。仅实际纯规则及调用时序重建，无设备实验。

## Standards
48仍只有XML与报告命令/成功摘要，未见要求的原始Gradle执行日志；v3必须新记录真实命令/退出码/源码SHA，不补造v2日志。49、50交付所列失败迭代与最终绿需区分，不删旧证据。标准轴1项持续交付缺口，所有权/声明差量核对通过。

## Spec
1. 48会话归属：resolveTemporaryReturnRoute先按book武装，旧临时ReaderScreen的DisposableEffect(bid).onDispose随后无条件disarm同book，能释放新普通会话保护（guard的HashMap只有book键，无owner/generation）。新增真实guard API序列反例已失败；需用会话/请求所有权隔离，并覆盖同书临时返回旧route迟到dispose。
2. 48快速退出：guard仅在500ms消费者observe；persistOnLeave只读isArmed不根据实时位置裁决。返回后移动但500ms内退出，仍被抑制保存。Root抽取实际退出谓词反例已失败；需退出时用权威实时坐标和同会话身份判定，不能依赖防抖已运行。两处不能靠手动先observe的纯guard绿测证明完整接线。
3. 49入口run检查太晚：入口只核serial，run绑定仍在步骤7归属判断；步骤1/2恢复先执行，且没有新增文件时不同run还能签restored=true。需在任何副作用前统一检查基线/账本身份，测试不能仅断言无rm。
4. 49步骤9未接新保护：created_by_this_run仍直接授权rm-rf，未核run/serial/路径范围；嵌套引用仍旧写法。步骤7绿测试的对象已移账，根本未覆盖步骤9。生产integration_chain账本条目也未包含新增要求身份字段，应同步最小产账/消费合同。零字节新增文件当前安全失败但无法归档清理，需支持并补反例，不得将空字节当失败。
5. 50窗口边界：probe约374行tChange早于wm命令（391）/root变化，纯规则只用>=tChange过滤。原改变前的observe加后续blocked会签保护。需真实root变更的可核时间/序列边界，旧样本晚落日志不足因果，不能以单纯seq顺序声称根因已证。
6. 50证据完整性：changedObserves未要求index/offset非空，null与anchor不等便算变化；生产observe本身无bid，scopeEvents允许缺bid通过，不能声称逐事件书籍身份已绑定。缺可靠坐标/书籍会话/因果信息应inconclusive/not_claimed；完整12列比较和异步恢复成果保留。
需求轴6项接缝缺口；49最严重为恢复/删除身份与精确路径，48涉及阅读位移保存，50涉及错误结论。均未合流放行。

## 原始证据
E核验根：delivery-verification.json、final-source-verification.json、48-guard-counterexamples.log/receipt及run_guard_counterexamples.py、49-pytest-fresh.log、49-entry-ledger-counterexamples.json及reproduce49_entry_and_ledger.py、50-probe-counterexamples.log/receipt及run_probe_counterexamples.py。49独立复跑日志另在该v2任务review-codex-20261015；原v2所有文件保留。

## 未覆盖项
Compose实际路由dispose顺序、真实快速位移退出、特权恢复及D实际viewport实验仍待修后同版MuMu验证。全产品分母未核；旧短末项/SAF等输入未变证据保留，不重跑来凑任务。

## 恢复与副作用
本轮无真实设备副作用，所有新增反例仅在E模拟或纯JVM运行；不执行实际shell rm，不删除v1/v2资产。无D产品回灌、暂存、提交、push或发布。

## 交付状态与下一步
48-v3、49-v3、50-v3可并行，分别修实际保存/恢复/证据判定。改为优先真实接线和生产入口反例，不继续用孤立helper绿测替代完整合同。50遇信息不足可明确inconclusive，不为制造保护通过扩大产品改动。三线核过后才决定唯一合流与受影响设备批次。

阶段方向复核：仍为原生Android稳定阅读、笔记回源、轻量导出；优先收口真实保存和恢复风险。D诊断只保证证据诚实，不通过假阳性推进，也不无限追求强行重现。保留已修成果，避免未过接缝直接重复昂贵构建/设备。
