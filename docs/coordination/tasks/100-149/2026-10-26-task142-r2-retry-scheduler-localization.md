# 任务142-r2：先定位重试红的调度边界，最小闭环

2026-10-26香港14:34；原142 owner，同Claude global Flash，不重派原142/r1。

````text
Root已读r1实际patch前200行、REPORT、retry测试完整关键段、run4原log/XML，六候选文件sizeSHA核同。原r1 BLOCKED_PARTIAL/1test1failure原件冻结，不改写。唯一工程仍E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。

当前retry红真实存在，但“需新增failure generation + wakeup”只是执行者猜测，尚无证据，不授权先加框架/信号或改SaveOrder。

先验证最具体的测试调度原因：retry boundary.scope=backgroundScope；第2步dispatch=false后requestExit()只advanceUntilIdle()，没有foreground VM工作时，backgroundScope任务可能仍未执行；第3步requestExit因exitJob.isActive直接被忽略，因此根本没有派发成功保存。先用runCurrent精确驱动每次requestExit，并用调用计数/失败回调数量/完成事件证明第2次退出确已执行结束、第3次新保存确派发正常完成。不sleep/轮询，不删除或放宽sticky/成功一次导航断言，不抄actual。若此假设成立，只修测试调度，不改生产唤醒。若不成立，落事件证据再在原VM局部修真实根因，禁止猜StateFlow版本号/改SaveOrder扩大框架。

仍须满足原性质：失败后空保存退出不假成功；后到成功保存后可重试恰一次导航；保存取消原因可观察且自身协程取消保留；真实等待期间后到事件全部完成才navigate->store.clear；成功退出gate抑制onDispose重复派发，失败不提前标成功。新增失败/取消结算不能丢失较新的失败，若发现同类局部问题依实际证据最小修正，不无关泛审。

本批同owner负责实施/必要fixture编译/目标验证/REPORT，不把当前5PASS从旧source直接拼成最终6绿。只跑原r1六个目标case的必要单case定位和最终六case定向结果，原所有红原件保留；仅若生产/AndroidTest接线实际变化才新增一次必要compileDebugAndroidTestKotlin，否则复用r1已有compile原log。无整VMTest/全JVM/lint/assemble/APK/设备。若仅测试调度修复，REPORT明确“旧红由测试未驱动background任务导致”，不得声称生产发生不存在的新修复。若仍红准确阻塞，不放宽断言。

先核r1 ready.changedFiles当前六文件SHA（VM48bcefab/Screenc1727db4/Platform6de3b254/Session19c5ddc5/VMTest397c1396/AndroidTestca28dab9），冲突停。原142-v1/r1与141红冻结，只新E:/creation-reading-assistant/task142-reader-exit-save-drain-v1/recheck-r2/ready.json及patch相对r1/REPORT/前后SHA/必要原logXML；不复制工程/cache/APK/旧历史。差量256KiB/raw1MiB，15分钟。

原owner仍独占reader/Gradle，不Search/B草稿/host/inputs/公共配置/依赖/SaveOrder，不Git/云/正式库/私库自动记忆，任何ADB/VM/安装设置/hostmain/attempt/F1皆禁。Root不旁路build/设备。产品74d106b6/test28173170冻结不含修复。完成等Root实际核再决定B集成，不自动接后批。最终总结末行：这是任务142-r2。
````