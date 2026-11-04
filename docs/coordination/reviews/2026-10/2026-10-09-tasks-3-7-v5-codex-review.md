# 任务3-v5与7-v1成果核查

核查时间：2026-10-09，香港时间。报告可见2/2；整项限定退出1/2（仅7机械准备）。不计算全产品百分比。

## 采纳与拒采

- 7-v1：Root实算996源码SHA无差错；相对992基树14条增量、before/after映射与B/C来源完全一致；55条androidTest未漂移。tree SHA `13bd73cc2d7c5973aa4d2bebc59fa1d09c5a4b96e7206b1004875dd16a76eaa8`。SOURCE_JOIN_PREPARED限定接受；未构建、未联合JVM、未设备、未应用D。
- 3-v5：992源码SHA、双APK SHA成立，仅2条授权测试变化。真实单项仍FAIL。A页起始>0且<=offset1通过，但后置闭区间未到达，拒绝“精确位置已验收”。搜索UI偏移8对应任意B索引行，而beta偏移171；观察值重写expected构成目标脱钩，拒绝“目标搜索卡已验收”。
- run3 logcat约7800行显示测试线程在ActivityScenario.close/tearDown。card_clicked之后进入failure_finally，再销毁Activity时NavBackStackEntry非法状态成为fatal；首个导致测试退出的异常未定。不能认定点击直接触发产品崩溃，也不能推断10秒marker超时。禁止据最终栈猜修产品或升级Navigation。

## 恢复与边界

Root自主短暂启动同一隔离实例，复用原基线进行只读取证：保护71文件size/nsmtime/SHA、DB/WAL/SHM、原APK对、8设置、包缺席状态与映射一致，shell uid2000。夜间模式实际基线为no，已对比上一轮原始证据确认，未擅自改yes。最终实例process/android均false。外部报告中baseline已root及控制info命令错误不作为前置电源/权限证明，以Root当前证据补足。

Root证据：`E:/creation-reading-assistant/tasks-3-7-v5-codex-review-20261009/source-verification.json`、`root-readonly-restore-proof.json`、`root-power-final.json`。HEAD仍e39a78da9c62f02614207cffb36969a32492eaea，index空；既有WIP保留。

## 下一步及阶段复核

产品主目标保持稳定阅读与搜索回源。现瓶颈为J1合同与失败归因，不扩新功能，不重复4/5/6/7已验交付。

- 3-v6：独立E测试/夹具/定向采集修正；唯一MuMu owner；冻结输入后捕获首个测试异常、正向查询目标与完整页区间；不改产品，真实产品缺陷经Root确定后另授范围。
- 8-v1：独立E复制7已验996树，只执行合流后的搜索/TTS定向JVM门禁（预期64项，实际计数），无设备/最终APK/产品修复，与3并行。联合构建通过也不解锁31设备批次。

最终双APK/31联合仍等待Root核查J1合同通过后由唯一owner实施。D产品回灌/Git/发布仍无授权。X整机中断、Back专属完成归因、pending队列排空与初始新save仍未覆盖。
