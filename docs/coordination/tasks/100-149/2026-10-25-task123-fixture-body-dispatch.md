# 123-v1 乙书夹具修复及122恢复原件并行核对

2026-10-25香港10:22。Root已读122唯一run T119V1-20261025-101147的summary/REPORT/ready及原始仪器日志：generateBodyB:255→setUp:500，Time0.117，未进入UI。实际源码把TITLE_B含QUERY_A前缀变体写入正文，又断言正文不含QUERY_A及变体，确定性冲突。不是产品回归。first_failure已保全，恢复事实仍交A独立核；122不重跑。

## B 国内DeepSeek，唯一仪器owner，立即离线修复

````text
新任务123-v1 B，接续同唯一仪器owner。Root读122原始仪器输出与实际generateBodyB确定：正文拼入TITLE_B="测试 TXT 贰 CRAQX119LMX"后，check(!text.contains(QUERY_A))失败，QUERY_A="CRAQX119LM"；第二互斥变体断言也会失败。122仍FAIL/旅程NOT_RUN，不修改原attempt/summary/报告/红日志，不批内重跑。现所有ADB/设备探测/安装卸载清理/host主入口/attempt禁止，旧12:30预算不作新批许可。

唯一源码可改E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/src/androidTest/java/com/creationreadingassistant/acceptance/SearchContentHitJourneyDeviceTest.kt，基线71932B SHAab45b3624c195866f8e6efc131d32ecaade5faa3c545f8002969da6a4aef7ca5。只将generateBodyB正文页首改为中性不含QUERY_A/变体的文本（如测试 TXT 贰）；保留书籍元数据TITLE_B含CRAQX119LMX，使真实BookDao LIKE子串命中但Tokenizer完整词排除的非退化Tab对照不变。全部正文互斥/唯一/4000阈值断言、query/title元数据、索引与UI断言不改弱；不把TITLE_B去掉查询变体来规避真实语义。不产品/host/公共配置/恢复合同改。

修前保留必要单份源码，输出123/lane-b最小patch/前后sizeSHA。用当前生产级真实generateBodyA/B纯函数离线构造核所有既有自检（不要只用手工重写的字符串模型替代真实函数），明确B正文query与queryX均0、MARKER_B唯一、TITLE_B元数据仍含queryX；A两marker间距≥4000及唯一保持。顺带只读核setUp在首个DB/文件写入前其余纯夹具前置条件有无同类确定冲突；若发现确证新缺陷先报告具体行/值，不泛改。不重复全host/旧JVM。一次必要:app:compileDebugAndroidTestKotlin，环境/锁/唯一工作区沿用，不assemble任何APK。没有现成独立Kotlin运行入口可用时复用现有编译类/运行时，不复制工程或下载新依赖；无法真实执行则如实证据缺口，不能把手工模型称真实函数。

报告E:/creation-reading-assistant/task123-fixture-body-repair-v1/lane-b/REPORT.md、ready.json，≤2MiB差量/4MiB必要原始。同owner必要编译修复闭环，原始新日志不覆盖。产品74d106b6/test0ea4f600/host1220e8779a3/inputs7a8ed4fd不改；新源修复后旧test不含该修复，后续test构建/新设备都等Root另派。你不是独自工作，A独立核122恢复原件，不覆盖A/D/current。私库含自动记忆/Claude/Cowork/DSH/.codex/memories、Git/云SFTP/存量删移压迁/实体手机正式库HOLD/杀VM共享ADB等禁令保持。不新attempt/ADB。最后单独一行：这是任务123-v1 B-乙书夹具修复。
````

## A 全球DeepSeek，独立只读122原件，立即并行

````text
新任务123-v1 A，与B纯离线仪器最小修复真正独立并行。只读核122本run T119V1-20261025-101147最终交付原件与恢复依据，不读取B正在变更的仪器，不源码改/编译/ADB/设备探测/安装卸载/清理/host主入口/attempt。

输入E:/creation-reading-assistant/task122-search-fixture-repair-v1/lane-b/device-run/REPORT.md5785B SHA041b8413369fb6c96e20b44b07339ed5c83a7201e5bc9a1f61f920e2d71bf026、ready.json、evidence-index.json。只按此index必要本地原件sizeSHA核对和关键内容，不扫全部历史。Root已读原始rc0/唯一INSTRUMENTATION_CODE -1、generateBodyB255→setUp500/Time0.117，summary businessOk=false、journey NOT_RUN；first_failure6414B/5f34656f、teardown445B/b224e4c0/final_rows340B/6c06b548已collect missingRequired=[]。重点独立核首错来源/取证真实完整、O1路径初始缺席、正常仪器结束无需stop、host在collect成功后实际卸载的依据与时序，是否有额外手动动作的证据/无法确认边界。

恢复核原test完整APK0d18d788、数据inventoryd6ac3c5f/userstate精确值，71私有820外部DBWALSHM initial/final proof、Download179无增缺、settingsnightno/VM仍运行。读现有原件不新ADB追证，任何无法从原件证明的结论如实限缩，不把报告自称无偏差当合规证据。121授权偏差保持不追认，不重复审121历史，不补造缺失first_failure。122产品已由host卸载，不沿用KEPT_BLOCKED状态。

写E:/creation-reading-assistant/task123-fixture-body-repair-v1/lane-a/REPORT.md、ready.json，≤1MiB，含核对计数/真实sizeSHA/关键恢复结论/未证缺口与是否阻断，不复制大历史证据。你不是独自工作，不碰B/E源码或D/current，私库含自动记忆/Claude/Cowork/DSH/.codex/memories、Git/云SFTP/存量清理/杀VM共享ADB等禁令保持。此任务不含B新修复终核，完成冻结等Root，不造额外任务。最后单独一行：这是任务123-v1 A-122原件核对。
````
