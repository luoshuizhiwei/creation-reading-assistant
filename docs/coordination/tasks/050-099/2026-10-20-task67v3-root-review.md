# 67-v3 Root核验：离线契约收口，待单次MD/取材模拟器验收

## 任务编号/版本

任务：67-v3；日期：2026-10-20，香港时间。Root只读生产工具/交付与必要原始证据，写本报告、current现态及E盘Root检查。未修改原工具、绑定、冻结、APK或共享生产者。相关task65/67/59 Python/Java执行进程查询为空，不以此扩大其他资产所有权。

## 执行与结果
67-v3四项离线修复采纳。Root实际调用verify_binding、verify_freeze、audit_import_closure、check_budget_plan，全部ok；ready列7项交付文件SHA实算匹配，14工具、两APK及两仪器源码由生产校验入口核同。65-v5已采纳构建来源直接复用，构建0、设备0；未重复全量JVM/Lint/63链或19项充分离线测试。

原始日志logs/05-final-offline-tests.log记录Ran 19 tests / OK；含一条ResourceWarning，不掩盖也不推断为设备通过。evidence/counterexamples.json四个真实main+FakeDevice拒绝场景均设备调用0：缺freeze exit2、预算不足exit6、缺传递依赖exit2、freeze内容漂移exit2。模拟happy_path exit0、两安装，只是离线证据。真实run_step_bounded独立子进程spec往返及生产worker._run受控依赖测试，证明传递和分发；不等价于真实ADB归档/恢复验收。

## 输入指纹
- 入口task67_v3_runner.py：26c08e63619a606c1132a923e1d5e9201b9191016d733f2c69bef35ae2b25b0a。
- entry worker：cf647b710d97c973528230e8631ccb60b0365cea439492e15ae30b7f4b7c8bef。
- freeze-67-v3.json：539a7fe87d4ee90edf68307e6cb90ca45f978e5ff36c00ceb9a29492dfd3cdd3。
- binding-67-v3.json：1213941e0d54aa7b6a17060ad85f17c58e9b95241630e03da68a1e38eca21b0e。
- host：14e573ab964a122c3d4fbb83770423d70bebd57028eca26c455a442da1a282aa，51830508字节。
- test：db741d1dafc48201cc92ba139ba41dc4ce30d6b934a28874740ab04d8cbc1b55，10246316字节。
其余依赖/源码/65-v5来源沿原ready、freeze和binding引用，不复制资产。上一版输入见[67-v2/65-v5核验](../../reviews/2026-10/2026-10-20-tasks67v2-65v5-root-review.md)，旧FAIL保留。

## 代码接缝及预算
执行前检查先于device.info；总时钟在设备查询前建立。launch180、baseline300通过真实run_step_bounded传入entry worker；两安装各180，MD/取材各600，阶段和2040；恢复420/480/120/420/180合计1620，总预算3900。恢复worker实际读取freeze并使用serial/attempt/run/ledger上下文。既有RealCommandRunner已含窄stdin脚本传输，未因本轮worker再退回未经转义的sh -c传输。归档失败停止后续卸载/恢复序列；错误与JUnit失败独立保留。
Root检查首次结果聚合误把metadata当反例，KeyError后修正聚合并成功落盘；未改产品或工具。原始检查位于E:/creation-reading-assistant/task67-recovery-runner-v1/v3/root-review-20261020-125812/root-checks.json及同目录生产校验输出。

## 所有权与改动
唯一构建工作区E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android、Gradle home D:/develop/env/gradle及原锁不变，本批没有构建owner抢占。E盘Root新增精确逻辑字节14648，均为小型原始校验JSON；未新增工程/缓存/APK对，未删除迁移既有资产。D仅新增本报告及替换current页首；current改前SHA 0bc39e036479433adb11bc5f64d0b13b8108f9d7645712e8a1ff85908b7ea253，改后SHA见E盘root-review的handoff-update.json。
automation-2/3现场配置均PAUSED，未更改。夜间外部派发许可不延续；本轮未向Claude等外部应用发消息，未设备/Git/发布。

## 未覆盖项与交付状态与下一步
63有限公共链及D/F历史采纳继续复用；65-v5仪器实现/构建已采纳，MD与取材新设备结果仍未交付。65-v3历史入场缺证不能补造，67-v1恢复对历史基线的结论保留，不称65-v3完整前后恢复。
下一批68-v1仅单次新run/attempt，使用本次固定入口与工件，完成MD+取材两条定向旅程和必要恢复；开跑前fresh身份/原实例状态/保护基线，归档核验后才卸载，未知资产保留。失败停止后续、保全并恢复，不现场改码重绑第二试。当前仅剩共同APK/设备/恢复现场的依赖闭环，唯一owner串行，不为数量制造审查或重复设备任务。
本报告只形成可派发方案，不启动设备。待用户派发/决定批次。轻量分类最终DB断言及同批导出仍未被此两仪器充分覆盖，不能拿旧63 SAF替代整条取材闭环。全产品分母未知；不报全产品百分比，不称实体手机/MIUI通过。

## 环境

Windows / Python313；复用既有工作区与依赖。没有设备实时状态采集；当前两自动化状态来自其配置文件。

## 恢复与副作用

本轮仅离线读文件与小型报告输出，没有设备恢复义务。没有源码、APK、共享工具或自动化配置修改。后续设备恢复仍须现场证据，不能以本次离线采纳替代。
