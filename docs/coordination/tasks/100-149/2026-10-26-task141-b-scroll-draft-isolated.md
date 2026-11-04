````text
任务141-B-v1｜2026-10-26香港11:35后｜Claude桌面当前WorkBuddy AI/global:deepseek-v4.1-flash 1M｜20分钟
作用：依据140-B规格实施可审阅的列表scroll验收测试与最小观察接缝差量，但只在新交付目录写候选补丁，不应用共享工程、不构建。A同时间独占唯一工程的JVM红复现与Gradle，B隔离差量保证真正并行无编译输入竞争。Root核实际补丁后另派集成/必要构建；不要为并行复制工程。

只读基线E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android，140-B REPORT/list-scroll-spec Root已读：SearchViewModel query/tab/listState绑定search entry，SearchScreen真实LazyColumn无tag/snapshot，F1 Back仅query卡计数不足以证明index/offset。Root允许准备SearchScreen.kt最小纯观察semantics接缝和新SearchListScrollRetentionTest.kt的差量（尚不应用候选）。不修改列表行为/query/tab/排序/导航/生产持久逻辑。

隔离输出仅E:/creation-reading-assistant/task141-offline-implementation-v1/lane-b/。可放修改后SearchScreen.kt（单一必要源文件）、新instrumented测试、unified patch、REPORT与ready；不复制整个工程、Gradle/cache、APK对、历史原件或旧仪器全文。每一原文件读前实算sizeSHA，写patch基于此冻结精确前像；源文件在交付目录以相对android路径组织，不写共享candidate。A只修改ReaderViewModelTest，SearchScreen无并发owner；如发现其自身漂移先停告Root。

实现要求：
- 生产观察接缝最小：结果LazyColumn添加专用testTag及类型明确的semantics快照（index/offset、tab/query、首可见item key/itemcount）；保持原LazyListState/key/导航不变。仅必要小类型/key，不新架构、无产品UI文案暴露实现。root可见的SemanticsPropertyKey保证androidTest能可靠读；不用reflection私有NavController/抓图猜index。Category selected语义仅若实际断言必要再加，不装饰性扩展。
- 新唯一@Test类SearchListScrollRetentionTest，宿主必传runId T141BV1-YYYYMMDD-HHMMSS（缺错在任何DB/onboarding/fixture写前拒绝，无随机fallback）。独立全库空term预检也在写前；onboarding捕获标志避免未捕获null误恢复。可复用现有真实fixture/helper/runner模式但不改它们，不克隆旧巨大仪器文件；精确类名/runner与单@Test检查写REPORT。
- 25本隔离小TXT/对应BookContent/生产indexSingleBook、唯一query与目标marker；仅候选隔离产品DB，不访问正式库。书ID/TXT/results/runId/book归属全登记资产，捕获并恢复允许的测试前状态，不删除本地历史。不得假定排序后index20是某书；用真实稳定key选择可见目标，实证滚到index>0及offset>0，确保目标仍可点击，记录pre快照后真实点击、临时reader marker、真实Back、post快照。offset非零用确定性scrollBy/gesture+idle可观察完成，不仅swipe猜，失败不降低断言。
- pre/post精确比较index/offset/tab/query/topItemKey/itemCount（实际itemcount含header需按真实LazyColumn结构定义，不能盲写=25；另独立正文命中25且归属）；卡key仍在与会话合法结束、必要12列进度前后未污染。卡isDisplayed不能代scroll断言。
- results阶段为fixture/search/scrolled/reader/back/success/assets，全部严格runId/book绑定、明确布尔值；not_covered显式JSONArray，避免137同类串类型。只写测试源码契约，不生成伪运行JSON或host PASS。

重要构建依赖纠正140-B建议：main SearchScreen观察接缝若最终应用，旧产品APK74d106b6不含该semantics，单assembleDebugAndroidTest不能将接缝送到设备。后续必须Root另授权新产品APK构建/新冻结并保全旧唯一产物，或另选无需main接缝的合理实现；本批任何APK都不构建、不覆盖，旧74d106b6冻结保持。此依赖必须在REPORT明确，不能直接沿旧产品跑新测试。当前28173170testAPK也非此新类，不冒充可运行APK。

不新增host/inputs/run-id/budget/attempt，先让Root核最小instrumented差量与semantics，下一单一owner集成+compile/assemble+新冻结后另设备。无ADB/VM/安装/卸载/设置/main/设备业务，禁止共享candidate写入或Gradle/JVM/lint。必要纯离线patch应用检查仅临时内存或本交付单文件，不碰工程；不创建额外helper框架。差量<=512KiB/raw<=64KiB。REPORT写实际未编译/未运行、test seam取舍/构建依赖/输入sizeSHA和实际存储；不要口头声称测试通过。
禁Git暂存commit push PR发布/云SSH SFTP/D产品回灌/正式库HOLD/实体/购买换号切供应商/存量删移压迁/kill共享ADB或VM其它进程/Claude Cowork DSH .codex/memories私库及自动记忆/CLI登录代发。所有旧红与恢复原件永久保留，完成退出不自动接后批。最终单独一行这是任务141-B-v1。
````