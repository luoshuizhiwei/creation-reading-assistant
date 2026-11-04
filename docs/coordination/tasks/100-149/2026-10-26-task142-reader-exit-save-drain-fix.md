````text
任务142-v1｜2026-10-26香港11:44后｜同Claude桌面WorkBuddy AI/global:deepseek-v4.1-flash 1M｜35分钟
作用：修复141-A确定性红证据暴露的“应用内退出时已派发进度保存尚未完成即销毁VM”风险；单一reader源码/Gradle/JVM owner闭环。141-B仍独立只在交付目录写scroll补丁，不写candidate或构建，不互相阻塞。Root授权本批精确必要生产修复，不要再问用户，不自动扩展APK/设备。

141-A Root已读实际patch/单case1m17s原log/XML：tests1 failures1 errors0 skipped0，失败在1826 pending保存completed断言，entered与未完成未取消前置通过。候选ReaderViewModelTest83501B SHAd121bdfe2f727474f5a787772c2ca083e5cfa469d9af404da5048808943911a1；原before79447B/ff161e57，红记录永久留E:/creation-reading-assistant/task141-offline-implementation-v1/lane-a，不改写。ReaderViewModel48312B c637402375bbd5c71dff7d50696e212c6f1fcf370c791e5d07dcdb9b722b3dea；ReaderProgressSaveOrder2461B 61b2ca9dd3633d475e7f6bc3d990d7512ff41d160714f05ea49bf5cfe31111ad，现均未改。140-A调用链事实可复用，不重跑全量定位。

必须先区分语义：141-A直接store.clear()证明在途保存受VMscope取消的真实风险，但“强制清store后最终completed”为真不等于“真实导航退出前已drain”。不靠改变framework强制销毁语义修复，也不把应用scope后台最终完成当作退出前完成。目标是实际应用内reader退出边界先完成已派发进度保存，再允许route退出/VM销毁；进程kill/系统回收不在范围。禁止runBlocking主线程、onCleared里等待、全io转NonCancellable/appScope、超时后仍pop或吞保存异常。

文件所有权允许最小必要hunk：
- ui/viewmodel/ReaderViewModel.kt：只阅读进度SaveProgress/SaveEpubProgress所需任务完成/等待接缝，不把笔记/标签等所有io全局记账；派发边界登记与完成/失败信号可靠，不能只mutex空/最终DB相等推断。维护现dispatch order与临时读/marker守卫。
- ui/screen/reader/ReaderRoute.kt 与必要ReaderScreen.kt：真实系统Back/顶部返回/临时返回已有入口经相同等待边界，保存派发必须先于等待，导航只在正常完成之后。若ReaderPlatformEffects.kt/ReaderScrollPosition.kt的onDispose/lifecycle重复保存与退出边界直接冲突，只准必要最小hunk并明确理由，原防重复保存与临时阅读裁决顺序不降。无关联UI重构。
- data/repository/ReaderProgressSaveOrder.kt仅确实必须才动，优先保持排序本体不变。app/src/test/java/com/creationreadingassistant/ui/viewmodel/ReaderViewModelTest.kt及最多一个小纯Kotlin退出边界测试文件用于验证真实共用边界。不得改SearchScreen/SearchVM/141-B交付/host/inputs/APK/公共Gradle/依赖/其他生产功能；确需跨出以上即停报Root。

实施原则：先依据真实persistOnLeave→callback→pop与onDispose/ON_PAUSE时序选最小入口，不能只给测试自建一个生产从未调用的await函数。任务登记在launch前/返回前可靠，不遗漏排队任务或完成前后新派发事件；只声称明确定义的本reader/本次退出已派发保存。退出等待时重复Back不触多次导航；保存失败/取消不能当作drained正常成功，保留原异常、保持reader不离开并沿现有错误反馈路径，不宽catch静默默认。不能为本批新建持久队列/WorkManager/全局任务框架，不能猜超时预算而自动丢保存。若最小方案涉及业务取舍无法保持以上边界，准确BLOCKED描述具体问题，不制造假绿。

测试与红→绿：
1. 141-A红原样留原件；新测试演进必须走本次真实产品共用退出入口：两个受控pending保存（含后到事件），发起退出后runCurrent断言仍未导航/未clear且待保存未完成，逐个释放gate，断言所有事件正常返回且pending为空，之后恰一次导航再真实store.clear，并核无取消。必须保留“完成先于退出”的严格顺序，不能只将旧completed断言删改成相反以让绿。直接store.clear无等待的旧hazard可作为单独明确characterization保留，不Ignore/跳过隐藏失败；报告解释为何新实际退出触发取代原直接clear风险触发，不能称旧强clear语义已改变。
2. 至少一个保存失败或取消不触导航、且错误可观察的定向用例；一个无pending/临时阅读空保存时正常一次退出、不得误持久化进度。合理复用现有门禁/排序充分测试，不全套重跑。
3. 只新改相关目标JVM用例必要执行，新增编译/fixture错误同owner修复保留红log；再做一次必要:app:compileDebugAndroidTestKotlin确认main接缝不破原仪器编译（无assemble）。不全VMTest/全JVM/lint。记录具体选中tests数量/实际断言/exit与rawXML。当前产品APK74d106b6和test28173170不含此新main修复，不重建不覆盖，不冒充设备或新产物验收。

唯一工程E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android；JDK D:/develop/Java/jdk-17.0.14 SDK D:/develop/Android/Sdk Gradlehome D:/develop/env/gradle；锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock，活冲突停不kill/stopdaemon。源码有变后准确新sizeSHA，旧141红/137hostfalse/139恢复与138保全永久冻结。
交付E:/creation-reading-assistant/task142-reader-exit-save-drain-v1/ready.json、REPORT、最小patch、必要改前后源/输入指纹、定向红绿log/XML/compile原始；差量<=1MiB/raw<=2MiB，不复制工程cacheAPK或旧历史原件。实际空间/未完成/未证范围写同REPORT，无新收据体系。Root核真实调用边界和新断言后裁，不因JVM绿宣称设备/所有pending永恒无写。无ADB/VM/安装卸载/force-stop/settings/hostmain/attempt/F1重跑/Git/云/私库自动记忆/正式书库/实体/存量删移kill共享进程，完成停止不自动后批。最终单独一行这是任务142-v1。
````