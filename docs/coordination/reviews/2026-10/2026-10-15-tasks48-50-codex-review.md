# 任务48–50交付核验与定向返修

## 任务编号/版本
48-v1、49-v1、50-v1均已交付，核验后均返修，未合流。下一批沿用48-v2、49-v2、50-v2，不新造功能任务。2026-10-15 21:40 +08:00。

## 输入指纹
以[原派发](../../dispatches/2026-10/2026-10-15-tasks48-50-dispatch.md)与[Root v9停点](../../dispatches/2026-10/2026-10-15-root-stage-runtime-reader-v9.md)为准。Root现场重核v9全部1023文件无差异。48实际6改0增；50实际1改1增；两者基线/候选声明SHA均匹配，未声明的源路径变更未见。49的backup_restore.py和新增测试前后SHA匹配。D HEAD e39a78da9c62f02614207cffb36969a32492eaea，index空。

## 所有权与改动
本轮仅Root核验记录和E盘反例工件，两个独立只读审查分别检查49、50；未修改交付候选。48继续独占独立main业务与JVM测试，49独占主机恢复与Python测试，50独占原探针与专属测试；全局文档、合流和设备归Root。v2交付各用原任务名末尾-v2目录，保留v1资产，不自动删除多余副本。

## 环境
所有新验证在 E:/creation-reading-assistant/tasks48-50-codex-review-20261015。无设备操作、无APK构建、无D产品回灌或Git变更。48反例编译使用原始ReadingEntities.kt、LocatorCodec.kt、ReaderProgressMergeTest.kt，仅给Android注解/BookEntity提供编译桩；测试实际合并函数，范围不含Compose/Room/设备。

## 执行与结果
- 48原六份XML合计40项0失败属实，但没有原始Gradle执行日志。Root新执行实际纯合并/codec：v9原3测试+3反例=6/6通过；48原9测试通过、同3反例失败（12项中3失败）。不能以原40绿证明新边界安全。
- 49 Root新跑8项Python测试8/8通过；另执行实际restore_and_readback配FakeDevice，基线run ROOTABCV9R1_20261015、调用run-a，仍restored=true且两配置被删。明确归属门禁失败。仅离线模拟，无设备命令。
- 50原4项机制XML与两次androidTest编译成功日志均实存；三份日志size/SHA与回执匹配。不为核交付重建同输入APK，不把机制测试或编译当设备通过。

## Standards
1. 48仅有XML及报告中命令/成功摘要，缺可核实的原始Gradle命令、退出码执行日志；v2补新受影响命令回执，不补造v1日志。
2. 49复制candidate/cache、多轮evidence和mirror超过任务必要范围。v2仅提供必要候选/差量/本轮证据，旧资产保持，不自动清理。
标准轴2项交付问题，文件所有权及声明差量未见交叉。

## Spec
1. 48全局merge冻结误挡正常更新：实际反例证明同位置普通阅读时间不更新、50到50.005百分比写入被丢弃、legacy chapter1/offset20到chapter2/offset20被误判相同并保留旧章。定位ReadingEntities.kt:67、113、148。三例v9均通过，48均失败。应把临时返回保护限定到明确流程，不泛化为全局不动即冻结，不另造JSON解析回退或无依据浮点容差。
2. 48分页/Markdown在debounce(500)之后才将首个合法样本定为初始锚并return（ReaderProgressEffects.kt:305、520）。若500ms内已有真实位移，首个样本即新位置却仍丢弃；现有测试未覆盖真实消费链。此为代码路径缺口，未声称设备已复现。v2需以实际恢复基线判断，覆盖快速位移，不把第一条防抖输出假设为恢复点。goToChapter新增可覆盖persist参数目前无必要调用，不得形成temporary保护绕过。
3. 49基线完整窗口仅核run_id非空，未与恢复参数一致；生产入口收到run_id仍未绑定，测试自身跨run后删文件也通过。baseline_proves_run_window约1568，调用约1740。v2需在任何副作用前拒绝身份不符，归属账本也不得跨run消费。
4. 49新增文件所谓取证仅stat/sha回执，未保存可恢复的原文件字节；且rm/stat路径直接拼入嵌套shell。v2在独立E证据目录保全真实字节并核size/SHA后才可删，失败保留原文件；对库存外部路径做包根/精确路径约束及正确shell引用，不扩大删除目录。
5. 50第438行仅比较current_location_json/updated_at，第509行却声明row_unchanged；需按现有12列完整合同比较并输出变列，单改last_read_at等也必须拒绝保护结论。
6. 50第407–437、448–452、509–512行允许窗口变化后任意同书防抖事件签裁决，stage/protectionClaim不要求post-change observe或remeasureObserved；变化前排队、变化后才落日志的事件可能误归本次重测量。v2需可核实的本轮PID/book/事件先后与坐标关系；不足inconclusive。方法标签另应使用实际请求尺寸。
需求轴6项未满足边界（第1项包含3个实际失败反例）；均须定向处理，最严重为49归属及恢复保全缺口，未放行合流/设备。

## 原始证据
E审查根内 delivery-verification.json、remaining-evidence-verification.json、merge-counterexamples-baseline.log、merge-counterexamples-candidate.log及对应receipt、run_counterexamples.py、49-pytest-fresh.log、49-mismatched-run-result.json和reproduce49_mismatched_run.py。v1报告/候选/补丁保留在各自任务目录。50探针具体代码行见只读审查及原始候选。

## 未覆盖项
新48 Compose快速位移、三线合流、修订后androidTest、MuMu设备复验均未执行；原短末项/SAF等充分且输入未变证据保留。全产品分母未核，不报整体完成率。

## 恢复与副作用
本轮未接触设备，上一停点外层恢复及MuMu关闭证据不改写；也不以本轮只读核查声称刷新了设备现态。不读正式书库或其他应用私有记忆，不清理v1多余资产。

## 交付状态与下一步
实现交付3/3，核验放行0/3。48-v2、49-v2、50-v2仍可并行，缩小到以上实证缺口；不重做已充分验证的内容。模型按原建议48/50 DeepSeek、49 GLM5.3Flash（其数据恢复难度提高，可优先换DeepSeek），Gemini3.8仅在用户实际应用可用时作候选。最终总结最下独立写实际任务编号。

方向复核：仍以原生Android稳定阅读/笔记回源/轻量导出为目标。本轮发现来自验收后的反例，不扩功能和桌面；保持独立所有权，先返修再统一合流/构建/限定设备，避免昂贵无效设备循环。
