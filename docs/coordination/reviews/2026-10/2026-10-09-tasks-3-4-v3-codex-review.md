# 任务3/4-v3核查

任务编号/版本：3-v3、4-v3；2026-10-09。报告2/2可见，完整目标退出0/2；交付中已成立部分限定采纳，下一保持编号返修v4。5/6不重派。

输入指纹：Root实算3证据索引50条全部一致；两run配置/日志/summary/restore原件匹配。4基树993及最终994源码SHA逐条一致，8修改1新增，仅9路径变化；其余985路径字节不变。4报告SHA10ae4548ce3ea2e19ad104d024a05f01fef64a735b0c0a8b177b8f13cb214bc4；patch SHA6f1f842fb0ae940a5a3c53f639358a19443b2eddad3303f72aac49fca92048e3。

所有权与改动：3原冻结输入只读，外部真实设备执行；Root仅恢复设置/电源。4候选9路径在授权范围；本次两只读explorer独立追踪J1/搜索取消，没有源码写入、构建或设备操作。D产品不回灌。

环境：隔离MuMu，无实体手机。外部实际模型为报告中不透明claude-ccr路由标识，不能按建议名称认定实际为DeepSeek。HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存区空。

执行与结果：
- 3：新完整mtime真实能力门8/8成立；两次独立运行均首项J1RealReaderJourneyTest:203超时，0 PASS/1 FAIL/30 NOT_RUN。第二run驱动restore=false（ADB daemon中断），保持原事实；不能用owner复验改写驱动结果。
- J1准确边界：起点Home继续阅读→普通Reader→一次系统Back→Home搜索节点等待。Home存在搜索入口，非Shelf误选。ReaderScreen:300-314的BackHandler先关闭弹层/选区/可见chrome，再普通导航；初始controlsVisible=true。单次Back必退Reader的假设未建模；双FAIL无失败帧route/chrome/语义树，不足以确认产品导航根因。3-v4优先真实状态补证和测试合同修正，不猜修产品、不盲增超时。
- 4：原始绿色8XML实算113 tests/0 failure/0 error/0 skipped；单类26/26及StrictMode探针1/1证据单独存在，不累加为独立功能数。取消归属、queued承载、真实ProfileRoute共用ViewModel分发、StrictMode生产恢复限定采纳。v2遗留旧代次测试与现合同冲突，其更新在未削弱游标/释放检查前提下合理。
- 4剩余P1两窗口（静态交错审查，不冒充实跑红例）：Repository:717检查后等待sweepLock并stateDao.set，取消可在检查与commit之间先到达但仍清断点；775检查后挂起bookDao.countActive，再标queued/triggerNow，取消先cancelUniqueWork后仍可新enqueue。现B1/B2测试都把取消放在检查之前，未覆盖真正动作窗口。v4须先针对这两窗口建立真实红例，再统一串行/原子合同修复；单加检查不能闭合。
- 红基线需纠正：make_red_baseline.py把取消查询恒false、移除分发等是故障突变，并非精确v2回退。A1/A2具体交错可解释共享布尔问题，但10红不能整体宣称原v2产品缺陷已实证。下一直接在未修v3与修复v4运行同一屏障测试。

原始证据：[Root实算](<E:/creation-reading-assistant/tasks-3-4-v3-codex-review-20261009/delivery-verification.json>)、任务3两run/raw/summary/restore、任务4evidence/raw/green-v3-directed八XML及_meta退出码；[Root恢复补正](<E:/creation-reading-assistant/tasks-3-4-v3-codex-review-20261009/ROOT_RESTORE_ADDENDUM.md>)与漂移/恢复两只读proof。

未覆盖项：首项J1通过与余30项；取消两动作窗口；Compose实际设备渲染/WorkManager端到端。新worker若在取消之后才启动并取新epoch，不可笼统声称epoch一定会阻止它执行。实体手机、X整机中断、Back完成归因/队列排空/初始新save不宣称关闭。全产品分母未核。

恢复与副作用：Root当前71文件size/纳秒mtime/SHA及原APK对、其余设置与映射一致；发现night_mode=no而基线yes，未推断改变者，恢复yes并完整读回。原shell uid2000成立。外部有意保持MuMu运行与启动前关闭不符，Rootcontrol-v1-shutdown返回0，info进程false/Androidfalse，adb该serial offline。保护数据/APK/8设置/映射及关机前精确指纹成立；关机后不启动重查。无产品/Git副作用。

交付状态与下一步：3-v4唯一MuMu owner修正J1测试前提，必要仅重建test APK；定向首项补证通过后交Root核冻结，不自动全31。产品rootcause未证不得修产品。4-v4独立E修两窗口；两任务可并行，但Gradle共享唯一mutex串行。3输出joint-j1-back-contract-task3-v4，4输出parallel-search-task4-v4。原输入只读，5/6保持限定退出；D产品/Git/发布无授权。
