# 任务183-v1：定位并修复181普通退出超时，独立源码差量

你是原180 owner，本批唯一退出接缝源码差量owner。GLM-5.3-Flash最高，20分钟从实际发送起评估。A182原owner正恢复设备；你与其立即并行，不等恢复、不读未完成产物。你不是唯一工作者，不覆盖他人修改。先读活动仓库AGENTS/current最新页首。

仓库D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919；candidate E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。本轮candidate/源码/APK只读，唯一可写E:/creation-reading-assistant/task183-reader-exit-source-delta-v1/。Root待设备恢复后决定源码集成/唯一构建位，你仍同owner接续必要编译修复与交付；本轮不占Gradle、不复制工程或缓存。

## 实际故障与冻结输入

181业务真实两方法均-2，唯一终态-1、Tests run2 Failures2；原回执 E:/creation-reading-assistant/task176-contract-b-host-binding-offline-v1/recheck-r3/attempt-T165V1-20261029-010954/evidence/instrument/actual-instrument-argv-T165V1-20261029-010954.json 7825B SHA243e4c8718ba8fa72cf20404cf70a676fb246fa399057843cc7485ffb6ab2df6。两方法都在NestedTemporaryReaderEntryTest.kt:275 exitReaderToTopLevel等待shelf-screen15秒超时，callers482/507。故障点成立，根因未定，不能先定为产品或测试缺陷。

Nested源码candidate/app/src/androidTest/java/com/creationreadingassistant/acceptance/NestedTemporaryReaderEntryTest.kt28819B4ade45ab3b63eba779be6f9d84445f496265cc324a7139592fbb84831161bd57；J1RealReaderFixtures.kt同目录55008B0aea2c5eed35d7f189df72590013fdea1bdf658045b5ae7373d16617d9265ce9。当前product APK4d302624d603c06b12b1a30480dd1a9d5b7ce5e44cb24efafecd81d4d6c62577、test47f2a66ce35a3937ee7c6fd3d0649a3eae6d6849abfecba71cdf9638a4e26541只读冻结，不复制。

## 定位和最小实现

读原实际失败段及必要logcat/已有回执，不新取设备日志。沿真实ReaderScreen/ReaderRoute的BackHandler、退出保存ReaderExitBoundary、导航返回、shelf-screen语义和测试scenario/pressSystemBack调用追根因。当前helper只dispatch一次Back后等待shelf；pressSystemBack为scenario.onActivity调用onBackPressedDispatcher。不能据此直接把Back加两次/循环直到书架来掩盖错误；必须用生产状态流/既有真实证据解释被哪个层消费/为何不导航。

只修有证据的最小接缝：测试错则只交该测试源差量；若实际生产退出缺陷不可用测试放宽掩盖，给准确生产文件/hunk最小修复（仅外部差量，不写candidate）。证据不足就明确还缺哪个运行可观察状态，不猜改行为。不得任意多Back/循环/sleep/延长15s/跳过assert/直接Nav或VM状态注入/进度归零。保持真实退出保存后S0、外部Notes精确目标正文/无返回阅读处、Back精确Notes、第二不同源、12业务列exact隔离和普通重开S0全部强断言。保留reader内部LIFO未覆盖缺口，不夸大业务通过。

必要验证与风险相称：复用输入不变的既有原log/编译，不重复大suite。新逻辑可用机械提取的真实核心定向离线验证时仅最小一项；若必须Compose/Gradle/设备才能验证，如实待Root唯一资源，不造替代测试绿。不要因为不能设备就停止定位与最小差量交付。

## 交付与停止

最小patch+改动源、前后完整sizeSHA、REPORT、必要原证引用与定向原log/独立rc；唯一ready E:/creation-reading-assistant/task183-reader-exit-source-delta-v1/ready.json。REPORT给根因证据链、修复为何不放宽合同、验证层级/设备未跑、待集成构建文件清单与风险，明确A设备恢复独立。source/doc/patch/report/ready≤128KiB、全部raw≤64KiB；复制前估空间，不复制项目/APK/cache/历史证据，不建重复环境，不删移证据。若只读诊断发现无足够证据安全修，交精确BLOCKED及最小下一观察需求，不伪完成。

禁止candidate写入/设备ADB/VM/Gradle/安装instrument/stopuninstall/其它应用、恢复driver或全局state/current/共享配置修改、Git暂存提交pushPR发布/云/正式库/私库sessionDB/.codex/memories/自动记忆。最终末行：这是任务183-v1。
