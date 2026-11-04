# 88-v1 A/B Root核验、最小补齐及阶段决定

## 任务编号/版本
88-v1-A/B；88-v1-A/root-r1；2026-10-22。A失败收尾实现及原10项离线交付采纳，但原A不能直接充当安全通用入口。Root已闭环补齐实际剩余缺口，不再为同一工具拆一轮外部返修。B关于停止重复TXT/MD、复用充分证据的方向采纳；不采纳其五合同无条件全部退出及过强的数据安全表述。

## 输入指纹、环境
原A根`E:\creation-reading-assistant\task88-failure-closeout-v1\A`。Root实核ready所列8份交付及binding12项全部SHA匹配；REPORT正文若干SHA/体积已旧，当前ready与实物为准。原入口31c879a959bf4f42d2c79a352ffb6480835077b2ce93d3fd8089752bb8eeefd3；原binding8d1107c4e91b497abf3f18d68de8a90d219f3c818e2985644edfe422451fb034。

实际采纳的离线MD单用途候选：`E:\creation-reading-assistant\task88-failure-closeout-v1\A\root-r1\candidate\t88_run.py`，SHA256 `49c3d42b3134c191cba4faa0db599f495088f29b5e468e10969abf3767b07672`，31,496B。binding SHA256 `181e294de9f14986864858e19f947a7d20c3a7466dd220ed4bbdab9dd2027eb5`。Root实核两SHA，实际导入并调用该候选verify_inputs，20项运行闭包全部匹配，包括所选ADB、task53及保护8项依赖、实际Flow/UI/DB、准入/恢复、APK/材料/manager；无设备命令。

APK继续复用`E:\creation-reading-assistant\task59-common-candidate-v1\candidate\android\app\build\outputs\apk\debug\app-debug.apk`，SHA256 `33a08a05a75e482dc73cba00ce5f5eec15ddb070ad5cbe16b41299b5657d86df`。本轮不构建。工作区仍task59共同候选，Gradle home D:\develop\env\gradle、唯一构建锁沿current；下批设备身份现场绑定，历史serial不是当前许可依据。

## 所有权与改动
外部A写自身候选、B只写自身报告。Root本轮安排两个真正独立的子任务并行：worker仅写A/root-r1补齐实际入口；explorer只读B引用及必要代码关系。没有修改旧A/B/87、Android产品、严格消费者、共享producer或APK。Root唯一维护本报告/current。未发现匹配task88/t88的活动Python执行者后才开始；未关闭或打断其他进程。

## 执行与结果
原A的原始日志为10/10通过（2个旧缺陷复现、8个绿用例），读取复用，不冒充本轮新运行。其可信缺席仍为非零+空stdout结合错误关键词黑名单，rc2未知错误、rc127命令不存在仍可获得缺席判定；build_binding异常不落summary；日志收尾有pass吞错；恢复仅凭admission文件存在；清单并未被真实run消费。这些属于真实入口风险，不能以原绿测放行。

Root-r1改为成功返回精确CRA_ABSENT的探测协议，独立包列表/root目录查证，探测错误拒绝写入；真实入口先消费20项绑定；前置异常留首错；身份、boot、同run准入receipt及SHA参与恢复授权。旧/预置输出拒绝。无完整可信准入记录的部分副作用明确BLOCKED，不猜删；所有恢复/保护/保活及日志进程收尾错误分别保留并阻塞，不覆盖首错。

新增定向回归10/10、exit0：旧rc2/127红例、显式缺席绿、package-service失败拒绝、真实cmd_run绑定异常、准入失败和恢复错同时保留、初始失败不危险恢复、日志terminate异常阻塞、正常路径、真实phase_recovery拒绝旧receipt、真实verify_inputs拒绝依赖漂移。Root读取原始日志并核对实际调用位置，未重复运行旧10项、产品单元、构建或设备。候选仅MD单用途离线交付，不能改格式字符串后直接宣称EPUB入口可用。

## 原始证据
- A REPORT/ready/binding及evidence/test-red-green.log、red-green-results.json。
- A/root-r1 REPORT.md、ready.json、binding.json、patch.diff、tests/test_root_r1.py、evidence/test-root-r1.log、binding-verification.json。
- B `E:\creation-reading-assistant\task88-v0-exit-v1\B\REPORT.md`、ready.json，旧结论保留；本报告为采纳范围及勘误。
- 86、87成功及恢复原件继续引用[86](2026-10-22-task86-ab-root-review.md)、[87](2026-10-22-task87-root-review.md)，不重跑。

## 五合同裁定与最小剩余工作
| 合同 | 现态及证据关系 | 下一动作 |
|---|---|---|
| V0-1日常阅读 | 修后同APK TXT/MD已通过。B称80-H包含EPUB不实：H是取材/孤立摘录/来源状态/MD四旅程。旧EPUB证据早于86共用保存策略修改。默认readerMode=paged、epubPagerEngineMode=auto，健康状态未禁用时EPUB进入同一PagedReaderHost，故有实际变更关联。 | 89只补当前APK共用分页EPUB的真实移动、退出、重开及冷重开一次，不做字号/转屏矩阵，不重跑TXT/MD。必须证明实际分页分支，不能悄悄用legacy分支代替。 |
| V0-2查阅返回 | H的material真实方法mat_journey_excerptIdea_persisted_searchReturns_correctBody_progressFrozen执行OK(1)，包含搜索runId→灵感卡→查阅原文→返回阅读处→普通进度逐字段不变；夹具为TXT。原搜索缺口已补，不能拿64早期缺口重派。86不修改临时查阅凭证/导航guard；按原边界复用。 | 不重跑取材/搜索返回。 |
| V0-3数据保全 | 按64v2/65v2及81 Root裁定复用旧N1/G1/Room/WebDAV限定范围；86未修改备份/schema/删除接口。86/87的71/820只证明各run存量保护和恢复，不能证明产品所有备份/撤销路径，更不能证明零泄漏/零越权的普遍命题。 | 保留限定采纳，不重开备份项目。 |
| V0-4明确边界 | 合同是原书/权限/不支持故障可解释，不是“仅MuMu”设备声明。沿64-v2归并中ABC C PASS/SAF失败可识别及63限定整链证据保留既定范围；D2/LAN/X另列，不新增穷尽故障矩阵。 | 交付说明必须写准该产品故障边界，不能用设备授权代替。 |
| V0-5同版交付门 | 当前共同APK及TXT/MD和恢复已核。EPUB受影响分支需补上述定向证据；旧host/test冻结原范围继续保留，debug包不附会为全部旧仪器已在新包跑过。 | 89完成后仅据其结果决定限定V0收口，不另建纯净包或强制构建。 |

代码依据均为实际E共同候选：`data/settings/SettingsStore.kt:195,203,520`；`ui/screen/reader/ReaderPagerEngineState.kt:151-159,218-235`；`ReaderContentHost.kt:184-190`；`ReaderContentHostPagedBranch.kt:10-28`；`feature/reader/pager/PagedReaderControllerEffects.kt:184-214`。普通EPUB legacy PagedEpubView与共享分页分支区分；不得仅依据文件名推断无影响。

H原始搜索返回证据为`E:\creation-reading-assistant\task80-export-content-v1\attempts\20261021-183022-d95de0\10-material-instrument.log`；对应E工作区`app/src/androidTest/java/com/creationreadingassistant/acceptance/MaterialGatheringJourneyTest.kt:1633-1677`。H取材链已闭合，不重新派一次相同“阅读→摘录→搜索→导出”。

D/F：采用[64v2/65v2 Root纠正](../../reviews/2026-10/2026-10-20-tasks64v2-65v2-root-review.md)及[81 Root采纳](2026-10-21-task81-ab-root-review.md)，F真实对象是ReaderNavLateDisposePersistenceTest，不是64原建议的ReaderSessionGuardAcceptTest；ReaderPlatformEffects本次只是诊断改动，不凭文件SHA变化重跑。D既有直接依赖清单未包含本次改变的分页策略，按原限定范围复用。不推广自然墙钟、全部会话竞争或实体手机。

## 恢复与副作用、存储
Root和子任务本轮设备命令0、构建0，无新设备恢复义务；87已采纳无欠账不变。A旧件现有133,336B，B报告+ready15,549B，Root-r1新增68,312B，未复制工程、缓存、APK或旧证据。原报告小体积/SHA表陈旧以实物和ready纠正，不为此再派治理。测试自身临时目录由测试收尾；既有资产无删除/移动。

## 交付状态与下一步、未覆盖项
实现/离线入口补齐采纳；没有新增模拟器/实体手机/MIUI结论。当前TXT/MD普通使用通过，旧取材/搜索/限定数据合同可复用，不能宣布五合同全部无条件通过或“所有数据安全绝对成立”。全产品分母未知。

下一89由一个owner完成最小EPUB适配→离线真实接线核对→一次共用分页EPUB定向旅程→严格恢复→交付；无需新产品代码、JVM或APK构建，不自动复制旧MD主入口运行。独立入口补齐/合同核对本轮已真正并行完成；剩余单设备与其实际适配强依赖，不为数量制造第二个任务。

## 停止条件
身份/owner/输入变化、未知既有包或材料、缺可信同run初态、严格保护/night违约或必要恢复失败均阻塞；首错/恢复错保留。入口离线验证不代替真实恢复；不能把旧run当89新设备证据。若现场实际走legacy分支则不得声称共享分页已验，需明确配置和合同关系。实例不关闭不重启，结束保持运行，两个自动化PAUSED。无D产品回灌、Git、发布、外部消息、正式库、桌面、HOLD/私库/云或存量清理。
