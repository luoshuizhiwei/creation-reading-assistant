# 112-v1 Root核验与113修正边界

## 任务编号/版本、输入指纹

112-v1，唯一run T112V1-20261024-140939。E:/creation-reading-assistant/task112-saved-views-ui-v1。REPORT 92d714a86f24020efafd6b2ba802d32f05ce0fc109375fad51d4201416032248（6867B）；ready5a8721321a76df3880a09c62481b0a69db2d73fbdad5d6f1c2c48c0469d1126f（4890B）。仪器c7766ed5b9b46b1286a386391dd31ffacce3e8c982a7fdd456839a7c531ff88a（24927B），Root核workspace与交付source同；host08b78faa95113658914f2ac9a3c881ff3f4f830fed55654de0083f72b02b6e8d（19151B），实际testd4145683c87971dff0779ceb946a9aa091f9c46485c22ab47a4fd1dd3ad3d2c3（10602001B）单份preserved原字节核同。产品6a仍110引用，不重建复制。

## 所有权与改动、环境

Root D协调/current；ZCode官方Start Plan GLM-5.3为E唯一owner。新独占ShelfSavedViewsJourneyDeviceTest，必要host绑定，未改产品。系统繁忙原终态后同模型有限续接产生实际工具进展，原01/02失败编译日志保留。04 compile9s/05 assemble10s原日志BUILD SUCCESSFUL/EXIT_CODE0，唯一task59环境/锁复用。build增量未独立测。

## 执行与结果、原始证据

原04-run.json：1test1failure/状态-2，仪器21.833s，:176“书架页面必须出现”超时；host159.297s，attempt已耗。fixture两个中性BookEntity+Tag/book_tag成功，原件00/phase_marker/99已collect，lastPhase=t112a_10_launch。动态视图保存/套用/删除全部NOT_RUN，原FAIL保持。

Root直接源码核：AppNavigation.kt401 startDestination=Home.route；仪器launch后直接等待shelf-route，未点击书架tab。因此测试入场导航缺口确定，不能归为产品缺陷。113应套用111已验证的顶层就绪→真实书架tab→等shelf-route模式。

Root对同一实际旅程的必要连续性核还发现第二个确定测试设置缺口：:50套用目标恢复TXT+tag；:60_control_saved仅改EPUB就断言无tag书B可见，未取消仍生效tag，预设“EPUB无标签”与代码不符。113必须明确真实UI取消该tag后再断言/保存对照，保留原正反对照及全部验收，不等待下一设备批再发现此离线可证问题。点击helper未滚动，仪器应先按真实FilterSheet滚动容器/节点可操作性处理可见目标，不把查到语义文本视为触摸已送达。此为本旅程必要测试接线核，不扩全仓审查或产品修复。

## 恢复与副作用

Root核00 verified与06 restored的apkSha256/inventory/userState逐字段同，原test0d18完整恢复。initial/final rc0，consumer ok，71/820/diffs=[]，db_groups_match=true。summary recoveryErrors=[]，collect3成功、Download初始179新增0，产品UNINSTALLED，blocked=[]，testPackageRestored=true。night=no，keepalive pid66432/serialattached/boot存在，manager android_started=false不单独当关闭。恢复接受，业务仍FAIL。

目录Root实测14214902B，其中test10602001B，其余3612901B；报告14203145B差11757B为收尾时点差不重跑，无存量清理。

## 未覆盖项、交付状态与下一步

L1真实UI完全未验，107真实存储及111素材旅程充分证据复用。方向自审：仍原L1目标，不新增功能/不重跑充分JVM；同owner在入场前一次闭合已定位导航+对照筛选设置及必要UI可达性/断言接线，必要test构建、新输入一次run、失败完整恢复。不把逐批修明显测试前提当产品进展，原失败保留。共享仪器/testAPK/host/device串行，无可独立有价值第二实现，不造泛审查。详见[113完整派发](2026-10-24-task113-saved-views-navigation-dispatch.md)。
