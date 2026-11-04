# 任务3—6成果核查与下一批

## 任务编号/版本

2026-10-08，用户报告全部完成。报告可见4/4；本批原目标限定通过2/4（5、6），3设备阻断、4用户取消闭环未完成。3/4返修保留编号并升级v2，不新派原v1；5/6不重复派发。全产品分母未核。

## 输入指纹

活动HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，index空。冻结输入源码992/992匹配。Root证据：`E:/creation-reading-assistant/tasks-3-6-codex-review-20261008/fingerprints.json`。

- 3原证据清单12/12 SHA成立；原有效config SHA `4fd90bd7006359725e4b93791befc992df4d9a2adbe15f4b64bb16f1f18ec371`；结果0 PASS/0方法FAIL/31 NOT_RUN，原失败`precheck failed: protected_readable`，restore_ok=false。
- 4候选993源码逐条匹配（4改1增），changed manifest SHA `55c41a51909b00b1322e63c6bce09d15813ef2229fe09a8b057c2ffa8d12cddb`，6原XML合计54/54绿、强跑日志exit0。
- 5候选994源码逐条匹配（1改2增），changed manifest SHA `4b99ff48f472e184fa8cf902a820ed5f8aedfad4e1454063ffd55705648d7a0c`，原XML8/8、run2 exit0；run1三夹具失败保留。
- 6共56patch，逐条SHA一致；计划SHA `55e6f887326a8a7a2939aecffa5f97aee94d209ec652fe89e67d4ef3d31aa52d`，input fingerprints SHA `004690a3a49ff7d7ca7739a2dbbe91545e3747195489cafedfaee4e2c3e67b95`。

## 所有权与改动

三位只读reviewer独立核4、5、6；Root核3与输入/原日志，补齐仅在独立E新副本。原外部报告及候选保留。实际外部报告均自报Claude Code/DeepSeek路由，5/6与建议GLM5.3Flash不同，记录实际自报不冒充建议模型执行。D仅维护交接与本核查，无产品/Git写入。

## Standards

遵守互斥owner、比例验证与充分证据复用；不重跑未变产品JVM，不为增加并发制造任务。任务3执行者守住冻结输入，遇门禁停止正确；任务4写SEAM_REQUEST遵守原文件所有权，但不能把用户接线称为可选。本次v2明确扩给其所需四个Profile文件。

## Spec

3继续31项限定MuMu及恢复合同；4须用户可见取消、真实停机、正确覆盖状态和重建/周期竞态；5限定计时协调器与接线补证；6仅准备可审查payload，不应用D。

## 环境

Windows、隔离MuMu `127.0.0.1:16416`（Root本轮只读实核在线）。原host/test字节SHA不变。4/5原定向Gradle按mutex串行；本轮Root未构建/安装/卸载、未执行仪器测试。

## 执行与结果

### 3：配置接缝已Root补齐，设备仍未验

原模板把已确认缺席的正式包加入必须installed的protected清单，与`2026-10-05-unified-acceptance-contract-v1.md`既定原qahost两包合同不一致。这是Root设备准备遗漏；执行者未擅改，31项未运行，不算产品方法FAIL。

新唯一输入：`E:/creation-reading-assistant/joint-protected-contract-v2-20261008`。只改生成器保护清单为原`qahost`/`qahost.test`、重绑路径与manifest；正式包仍保留production身份与缺席只读观察，不能为过门禁安装或清除它。driver/保护守卫字节不变，Android992源码及双APK字节不变，31item完整相同。严格静态校验与当前MuMu只读cmd_precheck的11检查全通过；未run_all、未建device baseline、未执行设备方法，D2 stat/touch完整精度能力仍须执行前安全验证。

新锚点：APK manifest `12f9cf573d80d8132569e6633a208b424e0d244bed96f2ff8b210107992fdb48`；41工件manifest `9464d45a977f29af74a62e3596499c2de55d7be21f34800fb246475972abb497`；源码manifest `9fbc5ae0909254d972daf4ee65a49912b7edb7be865a5af270f563c436c9b7ad`；未绑定模板 `13eed995b40f7715d494a958b8c155e2bf4445c3a43938a7c9102e511613fe5f`。详见新E的`evidence/root-preparation-proof.json`。

### 4：2P1/2P2，54绿仅支持能力层

1. 用户入口未接：Action/Route/Storage/Diagnostics缺生产调用，新VM/弹窗闲置。原提示词未给必要四文件所有权，本次补正；原闭环0/1。
2. FULL coverage在尾部terms之前发布；小正文停在`term_tail`再取消会拒绝尾terms却遗留FULL，覆盖标记失真。
3. daily已处理至少一书时取消，Repository返回PROGRESSED、Worker转retry，而Scheduler只cancel once，可能短退避自行续跑。
4. 取消与重建reset交错时重建owner仍可清断点并入队；失败回滚可能恢复已作废代次，须确定取消与重建的线性化顺序。

后三项是生产路径和可控接缝的静态交错证据，尚未新增运行复现；v2须先真实失败反例，再窄修与定向回归。允许四Profile文件及确需repository/worker/policy，排除Reader公共模块/schema。

### 5：限定接受，修正报告语义

正常计时、淡出与跨章策略保持；但generation作废已出队旧回调、release终态拒绝arm及取消后剩余秒明确为0属于新增保护，不是完全等价搬移。8用例和真实生产协调器接线成立；未实例化完整Android控制器。淡出中操作、stop→cancel重入只有静态核查，真实Handler/音频未验；旧音量调节影响睡眠定时等观察不扩大本轮返修。限定采纳3文件，不重派任务5。

### 6：材料接受，Root补前置保护

56patch及旧语义复用按SHA成立，只准备不应用。原verifier漏D端31新增目标仍不存在和payload完整性。Root新副本：`E:/creation-reading-assistant/integration-payload-task6-root-v2-20261008`，补D目标absence、冻结input/INDEX身份、56patchSHA核，默认检查E指纹。实际只读输入通过、fixture正例通过、新增目标碰撞拒绝、独立补丁破坏拒绝；56payload字节不变。新verifier SHA `26fea46b8ea05cd1ddf754af3ec0125f95bb05ad8b059232ee0cce9af8845009`。

C1原qa后缀`.qahost`已有明确处置。C2 release默认签名变更超出当前集成实施范围，保持待决定，不能自动应用。C4来源补正：v4已在`2026-10-07-three-v4-deliveries-codex-review.md`限定接受；Root本轮实核ShelfImporter生产`cb7e7a0c…`与测试`72a6281c…`的旧冻结/原v4/联合候选三方SHA完全一致，复用77原XML及既有源码审查，不沿用旧v1/v2返修行覆盖最新结论。D2真实设备与整体D应用仍等待；其他回滚接缝按既有当前报告精确范围，不扩大C4结论。

## 原始证据

各原REPORT及Root`fingerprints.json`，3新E的`evidence/root-readonly-precheck.json`、`production-package-observation.json`、`root-preparation-proof.json`；6新E的`root-evidence/proof.json`及各原始log/exit。旧5/6模型自报、旧失败日志保留。

## 恢复与副作用

原3未进入安装，summary因baseline未创建而restore未验证，不能声称完整数据恢复通过；原报告后账只是有限只读观察。Root本轮仅安装前只读探针，无设备数据/设置写入，设备未安装/卸载/force-stop。6反例只写新E fixture和暂时破坏自有patch副本，finally恢复原payload字节。D源码与index/HEAD未写。

## 未覆盖项

31项设备仍NOT_RUN；4 UI与竞态运行反例未验；5真实音频/淡出中盲区保留；6未D应用/构建。X整机中断、实体手机/MIUI、退出归因/队列排空/初始new save、全产品分母均不扩大。

## 交付状态与下一步

并行3-v2（DeepSeek，唯一MuMu）和4-v2（DeepSeek，独立E实现/JVM）；5/6退出，Root维护报告补正与材料保护。3输出`E:/creation-reading-assistant/joint-mumu-accept-task3-v2/REPORT.md`；4输出`E:/creation-reading-assistant/parallel-search-task4-v2/REPORT.md`。两者核过后再决定下一集成批次，不提前混合设备APK或回灌D。无D产品、暂存、提交、push、发布授权。
