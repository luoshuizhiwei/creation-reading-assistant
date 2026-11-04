# 任务4—6：与任务3并行推进

派发日期：2026-10-08。用户要求加快开发并增加独立并行任务。Codex只读窄查搜索与TTS实际代码/历史合同后，保留以下三项，不重开已充分的索引续建/自动重试、TTS跨章、J1导航、D2修复或已完成联合准备。

任务编号/版本：4/5/6，技术ID各v1，见下表。环境：Windows、只读D与最终E产品基线。执行与结果：两项只读路径核查完成，新增外部执行未核；本文件为派发计划。

## 输入与依赖

输入指纹与所有权与改动：基线SHA见下，各执行者只写独立E；Codex只改派发索引与交接。

唯一只读产品基线：`E:/creation-reading-assistant/joint-final-bound-20261008/candidate/android`，992项源码manifest SHA `198fe92a0104e663514b271cb37db30495fdcf11582af6fb7e2aec6ba1f61588`。当前联合设备输入与双APK保持冻结，不应用4/5增量。任务3的外部窗口状态不能仅凭未见输出目录/匹配进程认定；新增执行者仍自行核占用，不发送外部消息。

| 编号/技术ID | 建议模型与作用 | 所有权 | 退出条件 |
|---|---|---|---|
| 4 / CRA-SEARCH-INDEX-CANCEL-CONTROL-v1 | DeepSeek；已授权S1用户取消操作闭环 | 独立E的SearchIndexScheduler/Worker/Repository、ProfileViewModel及两个索引诊断UI和对应测试 | 先核真实UI取消入口；已有满足则复用，缺失才最小修；不重做充分的续建/覆盖/自动retry；源码/真实接线/定向证据可复查 |
| 5 / CRA-TTS-TIMER-LIFECYCLE-EVIDENCE-v1 | GLM5.3Flash；已有定时停止的必要生命周期回归补证 | 独立E的ReaderTtsController、必要的同包实际接入计时协调器与定向测试 | 用真实生产调用链覆盖到期、关闭/改时、stop/release及失效回调；现无确定缺陷，不无故重写TTS；无实体音频通过结论 |
| 6 / CRA-POST-ACCEPT-INTEGRATION-PAYLOAD-PREP-v1 | GLM5.3Flash；预先备好精确集成材料 | 仅读冻结E源码与D Android；只写独立E清单/diff/可逆脚本/报告，不应用 | 复用旧已核语义处置，只核新指纹差异；逐文件前置SHA、D独有WIP保留和冲突停止，设备未通过时不得标可应用 |

任务目录分别为 `E:/creation-reading-assistant/parallel-search-task4-v1`、`parallel-tts-task5-v1`、`integration-payload-task6-v1`，各自固定`REPORT.md`。建议模型不是实际执行模型，执行报告另记。

## 并发资源与边界

3/4/5/6可并行启动。任务3独占MuMu，4/5不运行adb、不构建最终APK，各自复制隔离Android候选并以任务专用E build/cache执行必要定向JVM。Gradle使用共同命名mutex `Local\CRA_Android_Gradle_Execution`串行并检查现有非该mutex编译，不能抢占或终止他人的进程。任务6不构建、只读，不能吸收运行中的4/5工件，后续由Codex核查后再重绑定。

D产品/Git回灌、暂存、提交、push、发布和HOLD仍未授权。Android测试包名、构建配置、数据库schema、ReaderScreen/位置保存、source/display坐标公共合同及冻结设备脚本不在4/5写入范围。共享接缝不足时写SEAM_REQUEST并完成独立部分，不要求用户中转，也不擅自扩权。

## 原始核查与未覆盖

原始证据及未覆盖项见以下源需求与代码路径归属；没有新增运行通过结论。

S1源需求：`docs/plans/android-parallel-delivery-2026-09-09/README.md`的覆盖率与取消/续建/重试合同。只读查见scheduler无cancel API、诊断入口为触发重建；仍要求任务4开工排除其他已有可见取消入口。旧S1.7/8/9续建与自动重试证据可复用。

TTS源需求：同目录`R3-CODEX.md`明确定时停止已实现、先确认覆盖；候选controller有schedule/stop/release清理，现无确定缺陷。已有跨章设备证据不覆盖定时到期或退出旧回调；本轮仅补该缺口，不能宣称整个TTS或硬件音频已验。

集成复用：`2026-10-05-semantic-v3-codex-review.md`、`2026-10-05-remaining-semantic-merge-review-v3.md`及后续已核合流，历史输入不同不得照抄原采用候选结论覆盖当前D WIP。

准备交付2/2已核；任务3联合设备尚待实际成果核查。任务4/5/6只提供完整提示词，不代表已向应用发送、窗口已运行或验收完成；全产品分母未核，X整机中断等未覆盖不扩大结论。

恢复与副作用：本轮排期无设备/构建/Git操作，不改冻结E源码；保留既有WIP。交付状态与下一步：提供4/5/6完整提示词，各自交付后Codex核查、决定增量合流，任务3设备所有权不变。
