# 任务155-r1：修正155隔离差量漏return回归

同B owner立即闭环，只新E:/creation-reading-assistant/task155-reader-exit-thread-fix-v1/recheck-r1/。原155-v1源/patch/REPORT/ready全部冻结，不覆盖，不改candidate/APK/设备/Gradle；A154仍唯一设备恢复owner。10分钟从实际UI发送计，source128KiB/raw16KiB，不复制工程缓存，不删移旧件。

Root/Luna实核原candidate ReaderViewModel.kt1043-1049：failure回调后return@launch；你155新source1045-1059在withContext(Main.immediate){onFailure(failure)}后漏return，无条件onDrained/navigated=true/navigate，破坏保存失败不pop及可重试。这是新增回归，原patch可应用不是行为验证；不得改测试断言/失败语义迎合。

最小修复完整保留failure return，成功仅一次navigate，退出自身取消保持取消语义。重新核实际ReaderExitBoundary整个函数，而非只复制谓词，明确withContext(Main.immediate)的适用理由/测试拦截器与真实生产scope，不能把测试主线程问题无证据称生产缺陷。根因栈复用152原log，不重刷充分项。核新patch实际重放副本与交付source同字节、最小diff仅需要的线程边界加原return，candidate固定55808B SHA f8762da6326972c3c4582c6a38aa142656dbbeedd6a5fd04b73265edd5c2e3e3不变。没有执行真实函数验证则标明UNCOMPILED_NOT_RUN，不能用静态谓词/patch重放宣称行为绿；编译/设备待A恢复退出后集中执行。

交付最小新源/patch/before-after SHA、REPORT纠正原新增漏return、必要原log、ready.json，现有测试failure-no-pop/retry断言完全保持。提交前自己核完整成功/失败/取消/重试分支，不再留明显控制流缺口。禁Git提交发布/云/私库/自动记忆。最终总结末行：这是任务155-r1。