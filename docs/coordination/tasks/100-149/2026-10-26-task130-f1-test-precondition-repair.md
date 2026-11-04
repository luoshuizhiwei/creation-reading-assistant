# 任务130-v1：F1重建测试两处确定性前置缺口修正

时间：2026-10-26 香港；执行应用：Claude桌面Code；用户已明确沿用当前WorkBuddy AI/global:deepseek-v4.1-flash 1M路由。
作用：修复129新测试的索引前置自相矛盾和缺失真实搜索入口动作。依赖：129交付已完成，旧owner已停止。此批单一测试文件与必要Kotlin编译由同一Claude owner闭环；没有可同时执行且不重复的独立实现，不为并行制造审查。预计15分钟。
状态：FROZEN_FOR_DISPATCH；实际接收以Claude桌面新消息与运行/回执为准。本文不授权设备运行或新APK构建。

````text
你是任务130-v1唯一测试源码与必要Kotlin编译owner。Root总协调和验原件；你不是工程里唯一协作者，不覆盖其他人的改动。用户已要求继续自动化并改用Claude桌面。不得运行Claude/ZCode CLI或登录，不读写任何Claude/Cowork/DSH/.codex/memories私库或自动记忆。

基线与直接输入：
- 唯一工程 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。
- 唯一可改源码 app/src/androidTest/java/com/creationreadingassistant/acceptance/TemporaryReadingRecreationRegressionTest.kt，30527B SHA256 85e77c72fd2111f1e199bf2c551cf8381059adb2287caa7204d720baa28267f0。
- 129原报告与首次红/二次绿编译日志：E:/creation-reading-assistant/task129-f1-recreation-regression-v1/。历史证据只引用，禁止覆盖。
- 只读helper同目录J1RealReaderFixtures.kt SHA256 0aea2c5eed35d7f189df72590013fdea1bdf658045b5ae7373d16617d9265ce9；captureSearchTermRowsByTerm=SELECT * FROM search_terms WHERE term = ?，不排除当前book。
- 只读原旅程SearchContentHitJourneyDeviceTest.kt SHA256 0557506f1730a669257d4982b806dabc366a3bf0ab1210a4028a4e5758b1d05b；原933-945行已有Home搜索入口真实点击。
- AppNavigation.kt SHA256 7a6046a50550dad54a1a85f2cbf097b009fafb79d8169b889de1d27fb46f9fd9、TemporaryReadingNavigationViewModel.kt SHA256 e4af886a393f4596bceadcaca80302faab4d09c92f6905fefedaa7ab0e4949b5，只读不改。

Root定位的具体问题与成功标准：
1. 当前setUp在indexSingleBook(book)之后，查询全库term并断言preRows.isEmpty()，随后又断言本书存在相同term恰一行；二者不可能同时成立。把“既有索引冲突即停”放在本轮写入/索引之前，后置仍严格验证新索引恰一行且归属本轮book，不放松成忽略未知冲突、不删除未知行。尽可能把这一只读冲突检查放在onboarding/fixture/DAO写入之前；保留异常原样失败与既有证据采集语义。
2. 当前测试launch(MainActivity)后只等搜索页placeholder，缺少Home_SEARCH_CD的真实点击。按既有生产入口等可见并点击搜索按钮，再等待/输入搜索框；不可直接导航或改变初始route来绕过UI。
3. 只为以上两项修改必要hunk和对应描述，不借机重写30KB测试或生产实现。保持ActivityScenario.recreate、真实Back、进度12列冻结、同ViewModel与已提交请求身份等原有严格断言；其运行时正确性尚未证，不能用编译通过冒充。
4. 静态说明实际控制流如何消除前置矛盾、点击路径如何对应生产入口。已有充分128模拟器旅程与恢复不重跑。

所有权和禁止范围：
- 只改上述TemporaryReadingRecreationRegressionTest.kt；输出只写 E:/creation-reading-assistant/task130-f1-test-precondition-repair-v1/。
- 不改production、原仪器/helper、Gradle公共配置、D/current、任何旧报告/ready/日志。
- 完全离线：不ADB/VM探测/设备操作/安装卸载/仪器运行/host主入口/attempt/assemble。没有设备授权。
- 无Git暂存提交pushPR发布、云SSH/SFTP、正式书库/HOLD、购买换账号换供应商、删除移动压缩迁移存量资产或杀共享进程。

存储和必要编译：
- 本轮差量（源码/patch/REPORT/ready）<=1MiB；必要raw日志<=2MiB；无新大目录、无工程/cache/APK复制，旧原件只引用。新输出预计<100KiB。
- 唯一现有构建workspace如上，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，Gradlehome D:/develop/env/gradle。
- 唯一编译owner就是你；锁 E:/creation-reading-assistant/locks/android-gradle-exclusive.lock 按既有owner协议确认，文件存在不等于活锁；确有其他活owner则停，不杀进程/抢锁。
- 允许修完后一次必要 :app:compileDebugAndroidTestKotlin；若本次新增编译错误，只同owner定点修复保留每次红日志。不要JVM全套/lint/assemble，不重建旧APK。旧testAPK a646c8de不含129或130，不作新测试可运行声明。

交付：
- REPORT.md：两问题原始证据与实际patch、前后SHA、实际编译命令/退出码/原始日志路径、实现/编译/设备未证分列、实际新增字节和保留分类、剩余运行时风险。
- minimal.patch：仅该测试实际差量；新源码副本可选，禁止整工程复制。
- ready.json：taskId=130-v1、状态、REPORT和必要原件路径/size/SHA、修改文件SHA、编译结果、deviceRun=NOT_RUN。
- 预算15分钟；输入SHA不同、文件被其他owner改动、活锁冲突或需越权改生产/设备才可验证时立刻保留并BLOCKED。完成后停止，等待Root，不自动接下一批/运行设备/构建APK。
- 最终给用户总结最下面单独一行：这是任务130-v1。
````