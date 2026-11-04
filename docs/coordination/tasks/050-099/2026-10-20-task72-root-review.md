# 72-v1 Root核验：来源状态收口，恢复入口离线反例阻塞

## 任务编号/版本
72-v1-A/B，2026-10-20香港时间。用户最新设备保持不动优先；Root并行只读核入口与来源状态，自核工件及生产函数离线反例。源码/构建部分采纳，设备NOT_RUN且持续暂停。

## 输入指纹
host ceb60dd1a24455efbfcbcac4143a058b2d30e45b6f42659653ec7034925ae79a，test004a36c1ea95021fbbf2da9109429caf6eba2448585c3458a365f6db3bba9e23实算匹配。B最终4产品+2测试在B/src、A合流及59工作区逐项一致（独立核验六项），A重新合入ViewModel最后修订并真实打包。Root核最终来源/绑定/冻结实际通过，预算5100=3180+1680+240。

## 所有权与改动
相关task72/59 Python/Java查询为空。Root只写task72/root-review的校验JSON、小型依赖字节码及javap输出、本D报告/current；子智能体只读。未改原候选/工件、未执行设备/ADB/MuMu或构建，未Git或外部投递。

## 环境
59唯一工作区、D:/develop/env/gradle及既有依赖复用。比例核验技能用于实际变化；MD判定/分类/回执等未变充分证据沿用。用户禁止设备命令包含只读探测，本轮遵守，反例中的命令仅字符串，subprocess边界已替换。

## 执行与结果
Root实跑verify_binding、verify_freeze、audit_import_closure、check_budget_plan全ok，两APK实算匹配。真实最终构建日志BUILD SUCCESSFUL。来源状态B实现闭合：Loading/Loaded/Failed经Route/Screen/Detail消费，Missing保留快照并明确缺书，动作与显示同源；不再派B产品返修。8项新测试仅编译，5Compose/Room及3VM逻辑均未运行。
MD/取材已接新退出helper，严格退出/持久位置/续读断言仍在。独立审阅曾怀疑performClick仅语义点击，Root直接解本机ui-test-android1.9.0 AAR并javap：ActionsKt.performClick→AndroidActions.performClickImpl→performTouchInput，明确是真实触摸，不将该怀疑写成缺陷或据此改代码。

## 原始证据
E:/creation-reading-assistant/task72-parallel-closeout-v1/A/REPORT、src、logs/02-final-merge-build-rev2.log；B/REPORT/ready/src/patch；integration/ready/STATUS及candidate；Root root-review/checks-and-counterexamples.json、offline-recovery/shutdown-verify.json、compose-actions-javap.txt及compose-android-actions-javap.txt。所有原报告/FAIL保留，不复制历史或APK。

## 未覆盖项与明确反例
1. Root调用生产_process_alive，边界模拟超时rc=None/空输出，返回alive=false；实际_instance_alive在PID/netstat/ADB探测全失败时也返回alive=false。这不是设备状态证明，未知应阻塞，不能被当成已关闭。
2. Root调用生产shutdown_and_verify，owned_instance=None，替换的shared worker仍收到shutdown_mumu请求；在后态unknown却alive=false时整个步骤返回ok=true。实际代码先调用共享worker才检查ownership，而main在启动返回前就置started_instance=true，故启动失败/超时路径可能进入未确证归属的通用关机。离线仅记录请求，无真正关机。必须在任何worker/manager/guest关机前以本轮身份硬门禁；不确定保全阻塞。
3. MaterialGatheringJourneyTest先写mat_40_exit pass=true再断言readerGone和持久进度，阶段可能假绿；应在必需退出断言满足后写通过。退出helper中央点击/等待位于Attempt.failure捕获外，异常可能跳过阶段诊断；捕获范围修正须保留原异常，不添加成功兜底。
这些是边界真实风险，不要求重写状态产品逻辑或全量重测。

## 恢复与副作用
本轮未发设备命令，无新恢复欠账；71先关闭归属不明运行实例的历史缺口保留，用户决定不确定且保持设备不动，不能改写为已恢复/已授权接管。automation-2/3不改，保持PAUSED。Root仅少量JSON与依赖类分析，未删除迁移资产。

## 交付状态与下一步
72来源状态实现/最终合流/构建采纳；实例生命周期适配不按安全完成采纳。73-v1-A唯一runner/恢复/绑定owner用Root反例红绿修两项生命周期问题；73-v1-B独占退出helper/MD/取材阶段收据，并把3项不需设备的VM回归按现有JVM规则迁到src/test以便真实运行（5项Compose/Room留instrumentation，不冒充）。两线实施互斥并行，B最终差量交A一次必要test构建、定向JVM运行及新绑定；产品未变host复用，不重跑充分旧证据。
设备保持暂停，不发ADB/MuMu探测，不启动/关闭/重启/接管实例或改设置。离线修复/必要构建可继续，无需为设备决定停整个项目。全产品分母未知，不报产品比例，不称实体手机/MIUI通过。
