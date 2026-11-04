````text
任务141-A-v1｜2026-10-26香港11:35后｜Claude桌面当前WorkBuddy AI/global:deepseek-v4.1-flash 1M｜20分钟
作用：把140-A定位的退出save取消缺口变成一次真实、确定性、仅JVM红复现。A唯一候选测试源码/Gradle owner；B并行只在自己交付目录起草scroll补丁，完全不碰候选，因此A编译输入不受B变化。Root不旁路构建，A不自动改生产。

基线：E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android；140-A REPORT/minimal-test-spec已实际Root读，关键VM saveProgress/SaveEpubProgress→io(viewModelScope.launch)与onCleared无等待、ReaderRoute popBackStack源码Root核。仍只是源码可达风险，未运行红不得写已复现缺陷。ReaderViewModel48312B SHAc637402375bbd5c71dff7d50696e212c6f1fcf370c791e5d07dcdb9b722b3dea；ReaderViewModelTest79447B SHAff161e574ea4537b7a82af392e3cf1663b1994406d9cddef1b1c60979f02840c；ReaderProgressSaveOrder2461B SHA61b2ca9dd3633d475e7f6bc3d990d7512ff41d160714f05ea49bf5cfe31111ad。生产/F1/host/APK均不改。

唯一允许源文件app/src/test/java/com/creationreadingassistant/ui/viewmodel/ReaderViewModelTest.kt，只新增一个准确命名exitWithPendingSaveMustComplete的@Test及必要import，复用createViewModel/ioDispatcher现有夹具，不复制工程/测试框架。真实ViewModelStore+ViewModelProvider持有VM，再store.clear()，不反射onCleared。使用StandardTestDispatcher同testScheduler、CompletableDeferred受控悬挂saveProgress，先runCurrent核已进入且未完成/未取消，再真实store.clear，释放gate/advanceUntilIdle，断言派发保存正常完成且未取消。可用两个唯一book/event覆盖已开始的两个保存，但不为凑“all”扩大框架。准确范围只是已派发阅读进度保存遇ViewModelStore销毁，不冒充Nav Back端到端/Room事务/进程杀死/所有io写。

红复现要求：测试本身必须有效执行，失败在预期“pending save must complete/未取消”性质断言；依赖/Android stub/MockK/fixture异常不是目标红。保留原始编译/失败日志，所属测试新增编译错误由同owner小修闭环，不改生产或放宽断言。不要先写假绿，不用Thread.sleep/轮询/最终DB一行相等当任务drained，不把advanceUntilIdle本身当无悬挂证明；记录entered/completed/cancelled事件及书ID，保证闩锁前置成立，finally清理store/gate/main dispatcher避免污染其它测试。

仅一次必要目标JVM执行 :app:testDebugUnitTest --tests 'com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest.exitWithPendingSaveMustComplete' --console=plain；不要整ReaderViewModelTest/全JVM/lint/assemble。若新测试编译或夹具异常导致未测到目标，可同owner仅修新增测试后必要再定向执行，所有红log保留并报告计数。若需要动生产/公共配置/其它测试/下载新依赖，则停报Root，不擅改。JDK D:/develop/Java/jdk-17.0.14 SDK D:/develop/Android/Sdk Gradlehome D:/develop/env/gradle；锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock，存在不等活owner，冲突停不kill，不结束daemon。

交付E:/creation-reading-assistant/task141-offline-implementation-v1/lane-a/REPORT.md、ready.json、最小patch/改前后SHA、必要单caseXML与raw日志。差量<=256KiB/raw<=1MiB，引用源码不复制原工程/APK/cache/历史原件。精确记录测试选中数/实际失败断言/未复现时原因、产品修复所需最小接口与取舍（只建议不实施）。红符合目标则状态REPRODUCED_EXPECTED_FAILURE，不能PASS或“修好”。Root核红后再派最小修复，不自动实现S1/S2/S3。

禁ADB/VM探测启停/设备/安装卸载force-stop/settings/hostmain/attempt/F1重跑，禁生产源/host/inputs/APK改动构建、Git暂存commit push PR发布/云SSH SFTP/正式库HOLD/实体手机/存量删移压迁/共享进程kill/私库自动记忆/CLI登录代发。139恢复与137F1充分证据保持，旧红不涂绿。最终单独一行这是任务141-A-v1。
````