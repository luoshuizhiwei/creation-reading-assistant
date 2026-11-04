# TXT双交付核查与新对话交接

## 任务编号/版本

CRA-TXT-PERSIST-DELIVERY-HANDOFF-CODEX-REVIEW-v1；2026-10-06，Codex总指挥。用户报告“都完成了”并要求换对话接手。本轮只有成果核查与交接维护，无新外部派发、设备运行、源码修复或Git授权。

## 输入指纹与基线

活动恢复仓库HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，本轮暂存区空。D WIP/桌面/HOLD保留。

- [产品原报告](../../dispatches/2026-10/2026-10-06-txt-reentry-persist-fix-v1.md)：候选 `E:/creation-reading-assistant/txt-reentry-persist-fix-v1/android`，比较基线 `E:/creation-reading-assistant/txt-reentry-fix-v4/android`。
- [验收准备原报告](../../dispatches/2026-10/2026-10-06-txt-persist-final-accept-prep-v1.md)：工件 `E:/creation-reading-assistant/txt-persist-final-accept-prep-v1`。
- [既有冷启/保存冻结与采集核查](2026-10-06-cold-control-harvest-v2-codex-review.md)：5054/updated_at冻结、UI发生移动是已核原始事实；pending取值与调度根因没有当时设备直接观测。

## Standards

1项P2：`driver/j1_ui_callback.py:97`继续使用共享 `/sdcard/j1-ui.xml`，可能覆盖已有历史资产。进入设备前改任务/run专属路径并只清理本批创建物，或精确保全原文件并恢复；不允许借清理删除旧证据。

## Spec

2项P2，最终验收准备暂不接受为可执行闭合合同：

1. `assert_in_reader`只拒绝特定主页/launcher，实际独立调用 `<hierarchy/>` 和无关Settings页面均正常返回。移动判定仅在前后marker都非空且相同才拒绝，缺marker会放过；需要正向阅读器/宿主身份、前后非空marker与位移证据，未知页面必须拒绝。这不声称整个最终checker已被证明false-PASS，只证明UI前置合同不成立。
2. `clamp_reenter_and_persist`回调只有打开/dump/back，没有恢复后的继续移动；Kotlin使用同一数据库snapshot作为reentered与persisted，不足以证明clamp落位后恢复后续保存。须独立保存S/R/S2完整行，真实UI后续位移、offset/percent/updated_at更新及退出/冷读回一致。

产品时序风险（未定级为已证真实设备缺陷）：新修复只增加一次yield和第二次采样；新测试按采样次数返回已落位，未证明Compose真实后续帧或两快照一致关系。原报告“设备pending始终true/代码级根因无可辩驳”超出原设备观测。新窗口先检查实际flow/settle快照时序能否产生问题；增加真实生产控制流定向时序用例或MuMu实测证据，不凭猜测重写，也不放松pending/marker保护。

## 执行与结果：原始证据

- 产品源码冻结清单981项，实算0不匹配；与TXT-v4对应文件比较只有ReaderScrollPosition.kt和TxtReentryRestoreFlowTest.kt变化。清单981不是新增源码981项；该输入清单口径与此前980略异，另核多出的路径后才更新精确集成清单。
- 当前实际JVM XML两份：FlowTest 9，RestoreTest 7，共16/16、0 failures/0 errors。报告395邻接测试没有在当前test-results取得对应XML，本轮不计其独立验收。
- 验收准备22项payload/driver/config/offline/README哈希全部一致。
- 编译日志SHA `b17585ccbda45395c52f2757a2b3a2ff9bd72cfec9d9762604544d1c06cd6342`实算一致，日志BUILD SUCCESSFUL。只有测试源编译，不是最终APK或设备通过。
- 原离线结果9/9和接缝5/5账已读取；本轮不重跑、不称Root独立14通过。此前采集v2 Root14已验输入未变可复用。
- 页面反例通过Python -B只导入模块并调用Ctx.__new__对象的assert_in_reader，不执行ADB；两类输入均输出ACCEPTED_WITHOUT_READER_PROOF。

## 所有权与改动

### 恢复与副作用

只新写本报告与E交接备份，更新current/TODO/项目页日期/skill使用日志；写前SHA与副本位于 `E:/creation-reading-assistant/session-handoff-20261006/before`。产品源码、原报告、设备、Git未修改。未读取Claude/DSH私有记忆库；未改自动生成memory索引。

## 交付状态与下一步

### 停止条件

1. 新对话读current与本报告，先核两个任务的执行占用和产物指纹，原v1不重复派发。
2. 第一批可并行：产品时序补证仅负责独立E产品候选+定向JVM；验收返修仅负责新E工件（正向页面/位移门禁、clamp新保存合同、任务专属dump路径）。公共文档仍Codex独占，两者不设备、不最终构建、不D产品。
3. 两者经Codex风险核查通过后，第二批一个owner合流、绑定新UID/双APK SHA/source/driver/config/fixture，MuMu限定真实阅读/保存/冷读与clamp，卸载前逐项身份归档。原数据/DB/WAL/SHM/APK对/设置/前台与映射前后对账。
4. D应用/暂存/提交继续暂停，不能沿用旧授权；原仓库和正式书库/真实手机不触碰；X整机崩溃单点异常保持未验证，不扩发布。

本批交付2/2=100%；本批最终MuMu NOT_RUN。跨候选五旅程历史4/5=80%只作范围化现态，不是同一最终APK或全产品完成率；全产品分母未核。

## 资源临时事实

2026-10-06本对话中Antigravity旧日本出口出现location unsupported，用户换订阅后报告任务运行；FlClash当时新选马来西亚节点，两服务域名连接经过它。根因未证，不能据此固定模型/订阅路由；不为换窗改节点或自动接力。

## 环境

Windows/PowerShell；Python 3.13使用-B。本轮只读产品候选与已有日志，无构建、无ADB设备调用。

## 未覆盖项

最终APK/MuMu未运行；395邻接测试未取得本轮XML；产品真实Compose时序根因待核；页面/位移与clamp合同返修未实施。
