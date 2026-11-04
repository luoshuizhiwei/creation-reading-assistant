# 129-v1 F1配置重建回归缺口最小闭环

````text
任务129-v1，2026-10-26香港，ZCode桌面GLM-5.3-Flash。作用：针对已知F1配置重建仍NOT_COVERED缺口形成最小可编译回归测试差量。与128-r1只读设备补证独立并行，你完全不ADB/VM/业务/安装/运行仪器。你是本批新测试源码及必要一次Kotlin定向编译唯一owner，128仅设备、不build；Root集成/设备后续放行。

里程碑事实：128-v1 T119V1-20261026-082756已真实完成搜索→命中→临时阅读→Back，Root核原始stdout OK(1 test)/唯一-1、20件sizeSHA、global3991+15=4006、12字段基线前中后精确同/乙书无普通行、test恢复d6ac/保护71/820/settings/boot。当前不要重复该旅程或改已有仪器，它不是F1配置重建的证明。旧123FAIL不改。F1既有生产修复AppNavigation.kt SHA7a6046a50550dad54a1a85f2cbf097b009fafb79d8169b889de1d27fb46f9fd9已源审/编译，配置重建场景运行时未证。

唯一工程E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android；只读与F1直接相关的AppNavigation.kt、TemporaryReadingNavigationViewModel.kt、SourceNavigationContract.kt和现成测试基础设施。只允许新增你独占的app/src/androidTest/java/com/creationreadingassistant/acceptance/TemporaryReadingRecreationRegressionTest.kt（若已有则停报告不覆盖），或本次E输出的最小patch建议；不得修改生产源码、现有SearchContentHitJourneyDeviceTest.kt 0557506f/helper0aea2c5e、既有测试、Gradle公共配置或Root D/current。你不是独自工作，不撤销任何修改。

目标：定义并用真实生产入口支持的最小测试检验临时阅读时Activity配置重建（ActivityScenario.recreate或项目已用等价方式）后安全处理临时导航：不触发null导航、不污染普通阅读进度，返回栈行为符合生产合同；按实际代码识别确切期望，不猜跨进程持久化承诺。测试必须能真实到达目标分支、不能仅复制实现逻辑或assert不崩溃的空壳。尽可能复用现成可访问helper/fixture，禁止为本测试复制整套75KB旅程或创建测试框架。如果现有入口无法以小差量驱动并观察生产行为，快速给具体文件/行/缺少观察点与最小建议patch，标BLOCKED，不伪造覆盖、不擅改生产代码。

若可小差量实现：只新增上述测试文件（预计<=16KiB；超出先说明理由，不复制工程），附最小patch/新增前不存在证据与新增后SHA。完成必要定向test Kotlin编译至成功或真实失败；JDK D:/develop/Java/jdk-17.0.14、SDK D:/develop/Android/Sdk、Gradlehome D:/develop/env/gradle，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock（存在非活锁，按当前owner协议确认不能杀进程）。只一次必要:app:compileDebugAndroidTestKotlin，针对新增编译错由同owner小修再定向修复，不全量JVM/lint/assemble、不复制cache/APK、不产生新APK绑定、不运行设备。旧test APK a646c8de仍冻结引用，不冒充包含新测试。

输出E:/creation-reading-assistant/task129-f1-recreation-regression-v1/REPORT.md、ready.json、最小patch/新源码引用与必要编译原始日志；总差量<=1MiB，必要raw<=2MiB，复制前估体积/空间。20分钟有界；若9点模型额度失效，保留现有产物真实报告不换号/购买/CLI/新服务，不缩测试标准。REPORT分实现、编译、运行时未证，不能声称设备或配置重建已通过。ready最后写model/provider/本文SHA/读取输入SHA/所有权/实际改动/验证/未知/下一明确授权缺口。

禁Git暂存提交pushPR发布/云SSH/SFTP/D产品回灌/正式库HOLD/实体手机/存量删移压迁；禁Claude/Cowork/DSH/.codex/memories及自动记忆，ZCode不自动记忆收尾；禁CLI代发登录/内部API私有DB/购买换号/杀VM共享ADB其它进程。用户停止优先。最后单独一行：这是任务129-v1。
````