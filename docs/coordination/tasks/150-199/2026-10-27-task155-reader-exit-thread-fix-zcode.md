# 任务155-v1：页面退出导航线程约束根因修复（隔离差量）

B唯一源码修复owner，A154唯一设备恢复owner。当前产品KEPT_BLOCKED未恢复退出，不能构建覆盖APK/改candidate/运行设备。B153隔离稿完成，保留未编译未运行性质，本任务不为凑并行扩展产品功能。

已确认原红：E:/creation-reading-assistant/task146-quiesce-recovery-offline-v1/task152-T141BV1-20261027-023205/evidence/logs/20-instrument-stdout.log 四测试1PASS3FAIL，IllegalStateException: Method setCurrentState must be called on the main thread，NavController.popBackStack→ReaderScreen.kt234→ReaderViewModel.kt1049。152报告归因重复complete错误，必须在报告更正。releaseNow第147仅release.complete(Unit)，不能未查便改成幂等complete或削弱测试。

先按真实生产ReaderScreen的rememberCoroutineScope/ReaderExitBoundary及ControlledDrain调度、Compose instrumentation线程约束定位：完整读必要原栈/真实接缝，明确线程从何而来、为何无pending分支通过。不要先假定纯测试缺陷或产品缺陷。成功标准保持等待期间不pop、不重复派发、drain后仅一次真实导航、失败可见重试成功、普通退出一次，不能移除断言/延迟sleep/私有probe/吞错误改绿。

所有权：只新E:/creation-reading-assistant/task155-reader-exit-thread-fix-v1/源差量/patch/REPORT；候选E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android只读，旧149/150/152输入APK/原红不改。产品ReaderScreen47183B SHA72324af1ef348c240237ae2b66d3d58e73a88c03d94ca911139ec770da73db4e；ReaderVM55808B f8762da6326972c3c4582c6a38aa142656dbbeedd6a5fd04b73265edd5c2e3e3；测试17452B 7dcfa26ae31d3205a5b1f6a976483597854cea811e59a390c563ab541100c061。引用实际完整路径和SHA，禁止复制工程/依赖/APK。

最小修复真正的线程边界，若调度属于测试替身就修替身契约；若实际产品路径也有证据则提出相应最小产品delta，不能泛加withContext/捕获掩盖。保留测试真实ReaderScreen/真实NavHost/真实pop。可不需工程复制的定向离线验证，仅实际生产函数或实际测试接缝，不复制谓词自证；当前无Gradle/ADB/device授权，未编译/未运行如实，不造绿。必要编译与匹配新APK设备验证待A恢复释放资源后由Root安排，不自行覆盖构建输出。

自查新差量与153隔离稿公共文件交集，不动其合同、无需重审旧suite。交付原红栈引用/精确根因/最小源码patch及before-afterSHA/验证原log/尚缺编译设备项/ready.json，预计source128KiB raw32KiB，复制前核free，实际分类写同REPORT，25分钟从实际发送不重置。旧原件不删移；不Git暂存提交push发布/云/正式库/私库/自动记忆。最终总结末行：这是任务155-v1。