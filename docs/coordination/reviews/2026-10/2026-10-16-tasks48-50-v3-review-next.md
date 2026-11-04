# 48–50 v3核验及两线收口

## 任务编号/版本
48-v3、49-v3、50-v3交付3/3；50限定离线成果采纳1/3，48/49定向返修v4。2026-10-16 00:05 +08:00。下一批仅48-v4与49-v4并行，50不重派；未向外部应用自动发消息。

## 输入指纹
v9源码1023项本轮全核无差异，source manifest SHA 505c35235e2ecafc8cc248870753e2ca3c0b06e9adffc02e74debb658ab18dc5。48-v3实际5改5增（相对v9），50-v3实际1改4增（相对v9）；声明候选SHA均核同。49-v3候选2生产文件+3测试SHA核同。候选分别位于 E:/creation-reading-assistant/task48-temporary-return-freeze-v3、task49-inner-restore-cleanup-v3、task50-remeasure-probe-repair-v3。

49实际改了共享E工作根的host/integration_chain.py，当前SHA 4872800d2c6ab03d2336ef33b0ef9090c23541909d49c1b3c86bd899e8a2a984；改前原文patch/original可核回旧SHA b2de4347240ea1540a880f48890d33fbc8ff9d35f14bcb79cf2d573dba4653e0。旧tool-freeze-v9的own_files复核仅此一项漂移。保留现场，不回滚；旧31工具冻结结果不得沿用，合流时另行重绑定。D HEAD e39a78da9c62f02614207cffb36969a32492eaea，index空。

## 所有权与改动
Root维护核验/交接并决定采纳，独立只读审查分别核49、50；未修产品。48-v4拥有独立Android main/相关JVM测试，并承担50所需的session_guard_observe诊断增加bid这一处接缝。49-v4仅独立host/runtime及测试，不再直接改共同E工作根。v4各自新目录后缀-v4，v3与历史资产保留。50已完成的探针/规则交Root后续集成，执行者无需继续等待或重复实施。

## 环境
新增核验工件 E:/creation-reading-assistant/tasks48-50-v3-codex-review-20261016。本轮纯JVM/内存FakeDevice/只读日志；无真实设备、无APK构建、无D产品回灌/Git操作。不把原MuMu关闭证据写成当前设备现态复核。

## 执行与结果
- 48原始compileDebugKotlin/testDebugUnitTest日志exit0，14份XML106/106，补齐了原始日志；源码SHA核同。Root实际编译交付守卫及原13测试，新增旧临时回调反例1项：13旧绿、1新红。反例只覆盖真实guard及生产调用顺序，不冒充Compose调度设备实测。
- 49 Root现场复跑48/48通过；独立审查用真实restore_and_readback与现有FakeDevice复现跨run路径及Download目录删除，见原始回执。无真实删除。
- 50实核23/23绿、v2语义变体10项红、原反例7/7及androidTest编译原始日志。边界/坐标/12列/未知身份处理正确，可采纳限定离线实现；未做设备验收。

## Standards
48此次日志补齐、三线声明SHA通过。49直接改共同E工作根使旧工具冻结漂移，后续不复用旧身份，不擅自还原；v4转为仅独立候选。标准轴1项集成身份注意项，不单独制造回滚任务。

## Spec
1. 48所有权只落到release，没有落到observe。ReaderScreen的persistOnLeave先计算实时位置并调用TemporaryReturnSaveGuard.observe，再检查!temporaryInspection；旧同书临时页面的ON_PAUSE/迟到离开回调可用自己的查阅位置解除新普通会话保护。guard仍按bookId修改状态，不核持有Session。Root反例：旧owner=null，新arm offset244；旧临时回调observe100后，新Session由原凭证变null，断言失败。应隔离所有能修改守卫的入口，临时/旧owner回调不得触碰新普通会话，不只修onDispose。需实际生产边界测试，保留快退修复。
2. 49允许访问的scope根被当作可递归删除的staging根。runtime _cleanup_allowed_roots约231–248把全部非包私有scope列为staging，_cleanup_target_kind约251–266只看kind及广义子路径；当前ledger字段run/serial都正确仍可rm-rf OTHER_RUN/OTHER_SERIAL和/sdcard/Download/KEEP，最后restored=true。应将访问/取证范围与递归清理授权分离，精确绑定本轮staging_root/run/serial/对象路径；广义scope和kind声明均不足以授权。此为49-v4唯一主返修，不重做已过身份/字节保全/引用/空文件。
3. 50未改main符合所有权，缺bid已保守UNBOUND/inconclusive，不因交付缺此字段否定探针实现。Root决定由48-v4在ReaderProgressEffects.kt现有session_guard_observe日志加"bid" to bid（一处只读字段，不改守卫行为），使采集证据能绑定目标书。50只证明限定实测窗口下的观察/裁决，不能外推一般设备根因或X整机中断。
需求轴2项返修缺口，1项已决集成接缝；50限定采纳。

## 原始证据
E核验根delivery-verification.json、48-guard-counterexamples.log/receipt、run_guard_counterexample.py；49原始回执在 E:/creation-reading-assistant/task49-inner-restore-cleanup-v3/review-codex-20261016/fake-restore-foreign-path-receipt.json、fake-restore-missing-ledger-identity-receipt.json，以及pytest-review.log。50原始23绿/10红/最终编译和7反例工件均保留原任务logs/tools，未重复昂贵构建。

## 未覆盖项
48真实Compose交接及快退、49特权设备恢复、50加bid后的真实D实验、合流最终构建和限定MuMu仍未执行。全产品分母未核。旧已过且输入未变的短末项/SAF证据保留；受v4变化影响的场景由Root合流后确定复验。

## 恢复与副作用
本轮没有设备副作用，所有删除反例仅FakeDevice。无正式书库/桌面/HOLD/私有记忆操作，无暂存/提交/push/发布。E共同host已有49改动保全记录，不自动恢复或清理。

## 交付状态与下一步
本轮只发两个真实独立任务48-v4、49-v4；推荐48 DeepSeek或用户可用Gemini3.8，49 DeepSeek。50成果保留，不制造第三个重复任务。两线核过后由Root唯一集成者合流50、重绑定主机工具与源码/APK身份，再安排一次限定MuMu批次；当前不放行构建或设备。

方向复核：继续原生Android稳定阅读、笔记回源与轻量导出，优先用户位置保存和数据安全，不扩桌面。保留已通过成果，阶段退出以两处接缝关闭及同版验收为准，非全产品完成。
