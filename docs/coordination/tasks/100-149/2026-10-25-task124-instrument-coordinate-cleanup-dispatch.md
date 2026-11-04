# 124-v1 仪器章内坐标回溯及清理预检修复（仅离线）

2026-10-25香港11:07。Root已读A诊断11464B SHA2277bb223c4abf55c7c9a0d81e8ddab954d7105d347179046f51d3b1a27bb5a6并实核关键源码：生产章内offset3991+章起始15=全书4006；J1 helper原文回溯错用章内偏移。旧J1冻结，124修正限定当前119仪器调用处，不改共享历史helper。清理预检已有登记集合/路径/字节校验，尾部错误要求预删除目录为空。B同owner合并修，A独立关键delta审依赖修复完成，本步不造并行任务。

````text
新任务124-v1 B，同唯一仪器源码owner，当前仅离线实现/必要编译/定向验证。先读123/lane-a/coordinate-diagnosis/REPORT.md（Root已读实核11464B/2277bb22），两缺陷已确定，不再泛审或重跑旧设备。

唯一可改源码E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/src/androidTest/java/com/creationreadingassistant/acceptance/SearchContentHitJourneyDeviceTest.kt，基线72207B SHA9f7261bd0058cad89d76b551fbaa2210ea832c5c5d57f965a6162b45a6bf5e22。旧J1测试/共享J1RealReaderFixtures.kt（SHA0aea2c5eed35d7f189df72590013fdea1bdf658045b5ae7373d16617d9265ce9）冻结，不按A建议直接改共享helper；产品/host/公共配置/保护适配器不改。

1)在119仪器本地deriveSearchCoordinate调用处，按生产同一TxtChapterSplitter/章节起始合同，用winningChapterIndex及expectedCharOffset推导真实原文全书偏移，回溯term与bodySnippet；章2起始15+章内3991=全书4006，不能硬编码15或写特例补偿。保留derived expectedCharOffset/legalOffsets/expectedLocationSnippet章内口径供UI真实匹配；只替换错误的全书正文回溯计算，保留严格断言并记录章起始/章内/全书/term值。注意traceTerm必须对应winningChapter和span，不能同offset跨章误取。复用现生产分章/真实索引合同，不另造tokenizer/平行算法，不改查询词/marker/阈值、不重启Activity绕过。若现数据类型无法局部最小处理先报告具体依赖，不改J1历史。

2)verifyTrackedFixtures只移除删除前错误的remaining非空判断，或等价严格实际集合==登记集合；保留未知文件/缺登记文件/读取失败/路径越界/sizeSHA漂移均BLOCKED，保留deleteTrackedFixtures删除后的目录非空检查、精确逐件删除与后缺席校验。不能用清理成功覆盖主错，不递归、不删未知兄弟。禁止在设备执行任何修复或清理。

必要验证由同owner闭环：一次compileDebugAndroidTestKotlin，唯一E工程/Gradlehome/JDK/SDK/锁沿用，不assemble/JVM/旧全套。用实际当前编译函数/实际生产分章调用（不手工字符串假模型）验证真实A正文索引章内3991+chapterStart15对应QUERY_A全书4006/term匹配，UI期望仍3991；再有意义验证非零章起始与章起始0的映射不硬编码。清理预检用本任务E内新建中性合成fixture目录真实函数核：准确两登记文件校验通过；未知兄弟、缺失、字节漂移、读取失败（可必要mock真实边界）均BLOCKED。只对本任务新合成测试资产按精确登记处理，不触存量/真实设备；无需全套回归。保留原红日志/新日志，不掩饰无法真实执行的缺口。

输出E:/creation-reading-assistant/task124-instrument-coordinate-cleanup-v1/lane-b/REPORT.md、ready.json，≤2MiB差量/4MiB必要原始，最小patch/单份before/前后SHA/原始验证日志；基线owner冲突停报不覆盖。若编译失败只本两处必要范围同owner修复。产品74d106b6/test77ed0a3e/host1236fbfe76f/inputs9c90560b不改，旧APK不含新修复，构建另派。

当前所有ADB/设备探测/安装卸载/清理/host主入口/新attempt禁止，12313:00预算不是124许可。123产品、DB两本书相关行、两TXT和14件results继续KEPT_BLOCKED，TXT实际字节/部分DB原件尚未保全，不能因此次代码修复自动解除或补卸载；后续保全/解除需Root另明确范围。不要改123红summary/报告/attempt，不批内重跑。

你不是独自工作，不覆盖A/D/current；所有私库含自动记忆/Claude/Cowork/DSH/.codex/memories、Git/云SFTP/存量删移压迁/正式库HOLD实体手机/杀VM共享ADB其他进程禁令保持。完成冻结等Root，后续A关键delta审依赖本次实际交付，不自行派发。最后单独一行：这是任务124-v1 B-仪器坐标与清理修复。
````
