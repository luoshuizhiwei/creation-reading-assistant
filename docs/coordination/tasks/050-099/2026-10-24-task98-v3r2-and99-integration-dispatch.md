# 98-v3修正与99-v1集成连续块

| 编号 | 时间香港 | 当前模型 | 作用 | 并行/依赖 |
|---|---|---|---|---|
| 98-v3 revision2 | 2026-10-24 02:22 | workbuddy/cn:deepseek-v4.1-flash 1M不变 | 修已复现宿主漏判 | E脚本离线，不构建设备 |
| 99-v1集成 | 同块 | 同上 | 词典旧请求门控/取消差量与定向验证 | 独立差量已并行完成，Claude唯一workspace/build owner |

````text
接续98-v3 revision2和99-v1集成，不等用户再次确认。先读有效AGENTS/current及docs/coordination/tasks/050-099/2026-10-24-task98-v3-task99-root-review.md，已加载未变复用。Root只维护D，你只写E授权范围。保持现模型，确证quota才保全报Root转ZCode，网络错不当额度。禁止任何Claude/Cowork/Claude-Code/DSH/.codex/memories读取写入，包括自动记忆；不建自动化不发外部消息。

98-v3接线与9个旧离线样本采纳，但Root对实际函数AST离线调用发现两漏判：原stdout最终INSTRUMENTATION_CODE:-1改0仍(True,'ok')；首个测试状态0改-2再追加同名0仍True。当前只判断final字段存在及last覆盖，尚未闭环。修实际evaluate_instrument_result：本runner明确最终成功码-1且必须唯一完整最终记录，任何曾出现失败/错误/忽略/中止终态一律失败，不得被后续同名0抹掉；重复终态/计数不一致拒绝，不用任意OK子串当证明。保留class/count/exit/逐项原门控，不造通用解析框架。新增两原反例及重复/缺失最终记录必要边界，调用实际host判定函数离线验证，不执行脚本设备入口。保留v3初次日志/ready/SHA与Root反例；本revision2放E:/creation-reading-assistant/task98-tts-focus-feedback-v1/v3-host/revision2，最小patch/必要source/原日志exitcode/REPORT/ready，预算1MiB，无Gradle/ADB/设备。v2历史FAIL/raw/report不改绿，不再扩工具矩阵。

然后同块直接集成99-v1独立差量，worker已结束。E:/creation-reading-assistant/task99-dictionary-query-lifecycle-v1六artifact大小SHA核同，34549B；fingerprints与minimal.patch实际核：VM before d6df0281ced9e0ea63e40c42d255811d54b0df5a03ae6bf73ad920677339407a→0842cc744ab7e697fea5fab5663d79e0fca31acbedef428d75b55a91a098e9bf，新app/src/test/java/com/creationreadingassistant/ui/viewmodel/DictionaryViewModelTest.kt原缺席→82db3af96363dc9fa9d26a1d7c9cfc5b138cb99503ea5f8bd3b0686f4588db3a。仅拥有DictionaryViewModel.kt与此测试，fresh核输入再应用patch或source必要hunk，不回退WIP，不扩Repository/StarDict/DAO/ReaderSheetHost/TTS/搜索。

目标：A挂起→B完成→A成功或失败迟到均不能覆盖B的word/results/error；取消继续传播不误报词库IO；真实仓储异常保留原提示；空查询和VM销毁撤销在途，原词库管理/外部查询用户控制/选区位置不变。Job取消+generation+ensureActive门控如有具体问题同owner最小修，不抽新框架。关闭sheet生命周期不自动扩接缝，准确列本批边界。6项测试实例化真实VM并用非协作挂起门闩，不能降为复制policy。

必要定向验证：:app:testDebugUnitTest --tests com.creationreadingassistant.ui.viewmodel.DictionaryViewModelTest --tests com.creationreadingassistant.ui.viewmodel.DictionaryEmptyNoticeTest --console=plain，以及必要main编译；保存原日志exitcode/XML。若能用before源码在同现有构建环境证明关键竞态红并安全恢复输入，则可留行为红；不为红样本破坏既有源，不用编译红冒行为红。失败只修这两文件并重跑受影响项到闭环，不重复98八仪器/旧48/161/97二十二/96设备矩阵。必要一次产品候选构建以交付实际改动，覆盖前现17bbfe54ca930f9ae6d1639249b25d017aa7c1502f0af659679b2684177b4f97(52763889B)已经在98/v2-integration/preserved/app-debug-17bbfe54-run.apk原件核同，只引用不复制；当前test23a2618e也已保全。无设备操作，不构建test APK。

唯一workspace E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android，Gradle D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。99集成输出E:/creation-reading-assistant/task99-dictionary-query-lifecycle-v1/integration，源码/日志XML/原证据预算4MiB，必要build预计256MiB；worker REPORT/ready不覆。保留最小patch/source前后SHA/实际输入产物绑定/日志rc/XML/REPORT/ready/实际字节与理由/build测量限制。禁止复制工程/build/.gradle/.kotlin/缓存/依赖/历史证据/APK对，不删移动压缩迁移存量。

无Git暂存提交pushPR发布、D产品回灌、桌面/正式库/HOLD/云部署/关闭其他进程。分实现/离线/JVM/构建/设备未验，不能全产品百分比。两块交付后Root核验继续已有未闭合目标，不以一批完成停止项目。最终最末单独写：这是任务98-v3 revision2及99-v1集成。
````
