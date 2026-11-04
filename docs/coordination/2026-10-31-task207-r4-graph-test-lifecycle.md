# 207-r4：图测试观测清理与返回落地提交

2026-10-31香港时间；Root核207-r3后定点接续。电脑操控GPT-6 Luna，ZCode实施沿用实际可用模型。1个独立任务，预计15–25分钟，硬预算25分钟；同owner持有测试与Gradle。无设备权限，不并行另一候选写入。

````text
接续同一207会话执行207-r4。Root已核207-r3：17文件pins/candidate/patch/两APK签名匹配，生产共享分派与真实MainActivity/Hilt UI测试已编译交付；有两处图测试逻辑尚需修正，不重复r3生产改动或充分旧证据。

唯一candidate：E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。只允许修改app/src/androidTest/java/com/creationreadingassistant/acceptance/ReaderTemporaryNavigationStackTest.kt；其r3基线SHA de954fa8fec2a2fe836617718642d310cdd652d638905a30e9134c0551ec167d。生产ReaderRoute.kt SHA5368065b25a85fe1437e638ac52e02c4e4041c1bf7352bc926a0765a07ee7a0e、新生产测试SHA811cae6d1d66c9bfb50a5f2d6c8a8397a7d6aeeae79b86a4bd23e0f9cd4f00be、JVM6a37e45b、203冻结1f289540必须保持。r3 ready SHA5766b5dc48e81575534a89575cfee2b24d6a4e0a740aace6d40b6a679321d4cb。先核输入，冲突停写，不回滚他人资产。

Root已静态定位（不是设备实测失败）：
1. r3删除了退出时的全局观测清理，只在DisposableEffect(ownedRequestId)作废pending。真实pop回入口后landedBookId/currentReaderBackAction等仍留最后reader值，assertLandedOnEntry却要求null，正常退出用例会被测试自身残留判错。为reader组合真正离开增加恰当的观测清理；不得在每次requestId变化时清空已发布的新route动作，也不得删assertNull或用无条件默认值遮错。考虑singleTop保留id/组合，清理边界须说明。
2. runHappyChain的T2→T1后，新的temporary T1按LaunchedEffect登记新ownedRequestId，但此壳不自动模拟真实加载提交。代码随即再次Back，生产step会按未提交分支还原T1，无法回S0。必须等“返回目标T1参数匹配的新请求”完成登记，并通过currentReaderCommitAction提交（active相同去重、不重压栈），再Back；其他恢复临时层同样核真实request身份，不能仅等currentOwnedRequestId非空（可能仍为旧id）。迟到测试继续使用原真实取消的T2 requestId，不伪造stale。

最小修复上述两点，保留生产navigateReaderRouteSingleTop/dispatchTemporaryReturn同源调用、id保留断言、三入口独立@Test和合法栈不消费/真实原request迟到拒绝的强断言。正常成功链与失败/取消链分别核每步的mode、bookId、pending/request身份、LIFO深度和返回入口；不新增生产策略、不造测试钩子。不得修改超时或把生产UI/保存await未执行称通过。

原锁tools/gradle_locked.py、JDK17/SDK/Gradle home。只需受影响compileDebugAndroidTestKotlin及assembleDebugAndroidTest；生产输入未变复用r3宿主APK c396ebe14b1583180c005dabbc51184826d05a0838933203a21adf5bb72b1d31（53899310B）、原JVM12项及生产compile rc0，不重跑全suite/宿主构建/旧D/206恢复。全部androidTest仍NOT_RUN，本轮没有ADB/VM/设备许可。

交付E:/creation-reading-assistant/task207-reader-exit-stack-repair-v1/recheck-r4/ready.json、REPORT.md、最小before/after/标准patch、真实正反apply（core.autocrlf=false）、原rc/log、新测试APK实际size/SHA、05249完整签名证书、runner CraIsolatedTestRunner/target com.creationreadingassistant和目标方法。宿主只引用r3实际路径+SHA，不默认复制54MB。若新测试APK覆盖前缺唯一保全，仅保全旧r3测试包一份（约10.73MB），先查现存原件。source/runtime/report/ready≤256KiB、raw≤256KiB，全部新增存储及scratch在REPORT汇总；不复制工程/缓存/依赖、不删移原证。before优先引用r3原件；报告明确完成/未完成/下一步。

25分钟从实际收到起，发送未知不造时间，到点交具体缺口，不随机重跑。你是唯一测试/candidate/Gradle owner，不覆盖其他执行者修改。禁止Git暂存提交pushPR发布、D回灌、正式库/私库sessionDB/记忆或其他应用消息。205原业务红不变。交ready后停，Root核验，不能自行设备测试。最终面向用户总结最下面独立一行：这是任务207-r4。
````
