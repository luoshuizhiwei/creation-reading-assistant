# 91-v1 Root核验与限定Android稳定自用V0收口

## 任务编号/版本、输入指纹
91-v1-Root，2026-10-22。采纳本次EPUB完整普通使用模拟器旅程及严格恢复。最后一个已明确的受影响EPUB增量缺口关闭；限定V0合同收口，不等同全产品、实体手机/MIUI或公开发布。

Root实核binding28项SHA全部匹配，入口及binding、REPORT SHA与ready匹配。入口9bfedaac843e392e0a5a48a5a2ac0c82d45d7b9618581e650273b638118e6707，binding697aebad70b90aaa32f7f33c552fd9b35422d5ad8f2bce43a371a24543025f48。与90入口差异仅任务/输出根、Flow指向、准入恢复改为原路径引用、schema/task标识，未放松业务或恢复门禁。

当前APK继续复用：`E:\creation-reading-assistant\task59-common-candidate-v1\candidate\android\app\build\outputs\apk\debug\app-debug.apk`，SHA256 `22f6d8c1778136c81a9a2ed904757d2e73d2ed4d5b0eacacbf74af56dfd227ae`，53,895,731B。Flow d237a0bbe2049a260c2a6b4f30d6b54b48ffefe2ea0d4197212d47184bf24c47及其binding2daa219db6df6541b1b3cd58a3fc81be07592dfffb049bb0507d4cb52e929dbf匹配。不构建、不改产品/Flow，旧33a08唯一APK在task90/preserved保留。

## 所有权与改动、环境
91唯一运行owner执行设备并交接，Root只核证据/维护公共入口。未发现匹配task91/t91的活动执行进程；Root设备命令0、构建0、外部消息0。不为里程碑制造并行任务或重复充分验证。

唯一工作区task59-common-candidate-v1/candidate/android、Gradle home D:\develop\env\gradle、独占构建锁不变。实际run20261022-200856-f20738，20:08:56—20:16:20 +0800，serial127.0.0.1:16416，boot e4e516fd-4291-4dec-8d21-17cbad195b56，实例pid30076/headless28088。身份仅为本run历史采样；报告称实例已运行，本批未启动/关闭/重启，也未作night准备写入。

## 执行与结果
Root直接读取run-summary/flow逐阶段DB原始行及reader-open、after-exit、reopen、cold-reopen XML：
- 原入口exit0，first_error/stopped_at均null，initial/admission/install/business/recovery/final/keepalive全部ok；唯一run/attempt。
- 通过真实产品导入本run新bookId epub_qxfvdw，区别于90的epub_-hambcz；未使用旧DB行作为新基线。reader-open具分页正文已就绪，证明共享分页分支。
- 真实翻页后中间页第13段marker，B/C/D/E/F同bookId、progress64.8309173583984、completion=reading；locator始终为{chapter:1,offset:0,locator_v2:{v:2,offset:1791,ci:1,co:0}}。
- BACK后书架原始XML已留，C持久位置不回退。
- D与F原始XML同时包含底部64.8%和顶栏EPUB·第二章乙·64%；flow观测记录两身份，并逐一按小数显示精度/整数截断消费同DB，全部通过。
- force-stop rc0且pid为空，冷启动MAIN成功后再次打开同marker与locator，B/F locator非空且相同。
- logcat冷启动新进程31764记录target1791/percent64.83092；重排时reported=false记录保留，86防临时页首回写路径仍可观察。

## 原始证据
交付根`E:\creation-reading-assistant\task91-epub-selfuse-accept-v1`：REPORT.md、ready.json、binding.json、candidate/t91_run.py；evidence/run-summary.json、binding.json、console-run.log、run/20261022-200856-f20738/flow.json及reader-open/after-exit/reopen/cold-reopen-EPUB.xml；logs/logcat同run日志及01—07阶段原件。Root未重跑已充分的90产品24项或解析9项。

## 恢复与副作用
Root消费本run initial/final真实proof：consumer=true，比较及capture均71私有/820外部，diffs=[]、DB组匹配、reasons=[]。保护根`E:\creation-reading-assistant\task83-selfuse-delivery-v1\B\v2\protection-root-r1\outputs\20261022-200856-f20738\{initial,final}`。

恢复原件RECOVERED、blockers=[]、包卸载成功、本批唯一EPUB按SHA精确收尾、11设置无差异/night=no；keepalive为device、boot_completed1、boot与准入一致。完整恢复采纳，无新增恢复欠账；目标测试包不保留安装。实例结束保持运行，不把历史末态当作之后实时状态。

## 交付状态与下一步：五项限定合同收口
本次完成最后一个确定增量，不重开全部历史审计。按[88阶段裁定](2026-10-22-task88-ab-root-review.md)、[90增量边界](2026-10-22-task90-root-review.md)复核依赖与退出条件：

| 合同 | 退出依据与限定 |
|---|---|
| V0-1日常阅读 | 86修后TXT、87 MD普通使用全旅程已采纳；90仅改EPUB估算章长显示，真实render回归保留TXT/MD原行为，因此既有TXT/MD设备证据继续复用。当前APK的EPUB共享分页完整旅程由91补齐。D/F按原Root纠正后的限定证据保留，不扩展到所有布局/会话竞争。 |
| V0-2查阅返回 | 80-H真实material旅程含搜索→查阅原文→返回阅读处并逐字段保持普通进度，旧搜索接缝已补；后续显示修复不改变其路由/临时凭证/保存guard。沿已采纳范围复用，不重跑相同取材链。 |
| V0-3数据保全 | 依64v2/65v2及81 Root裁定限定复用N1/G1/Room/WebDAV；本次不改这些路径。71/820仅证明本run存量保护/恢复，不能替代产品全部备份/撤销或网络/原子性保证。 |
| V0-4明确边界 | 沿ABC C、SAF可识别失败及80-H孤立摘录/来源状态等既有范围保留；设备类型声明不替代产品错误边界。D2/LAN/X等原独立范围不因收口消失。 |
| V0-5同版交付门 | 当前共同E源码/90最小patch与新APK22f6身份已核，受此次改动影响的EPUB由91同版通过，恢复后账完整。未受影响项按明确输入关系复用；不是声称所有历史仪器都曾在22f6上重跑。 |

因此当前授权的Android稳定自用V0限定范围可以收口。已验取材/搜索等不再次派同样旅程；目前没有新的已证独立产品实现阻塞，不为任务编号、模型额度或并行数量造任务。

下一步进入限定自用与真实问题反馈；若用户明确扩展目标或出现新复现，再按该增量安排最小修复。无需另发92重复确认、重新冻结/复制APK或追加字号转屏矩阵；不自动安装到实体手机、发布或启动自动化。

## 未覆盖项、空间与停止条件
仅MuMu隔离实例限定证据；不同字号、转屏、其他宿主布局及全部竞争条件未穷尽。实体手机/MIUI、正式签名/升级/R8实际运行、实际弱网、整机中断、D2/LAN/X按既有独立范围保留。没有已复现新故障时不把未覆盖排列组合变成无穷验收。全产品分母未知，不报全产品百分比，不称全产品开发完成。

Root实测task91为4,210,946B；本run保护另2,638,068B，总6,849,014B，低于8MiB预算。必要原始日志/XML/截图与保护证据保留，无工程、缓存、APK对复制或既有资产清理。旧89/90 FAIL不改写。

本阶段设备验收与补修在此停止；实例不关闭不重启、结束保持运行，两个自动化PAUSED。后续设备/构建需新明确批次和现场身份，不复用已消费run-id。无D产品回灌、Git暂存提交push/发布、外部消息、正式库、桌面、HOLD/私库或云。
