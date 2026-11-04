# 任务183-r1：同owner集成单测试文件并定向编译

编号183-r1；推荐原ZCode模型；20分钟评估从实际收到全文开始。接续原183会话，由你闭环单文件集成/必要编译修复/构建/交付。Root维护全局state/current，你不写。输出 E:/creation-reading-assistant/task183-reader-exit-source-delta-v1/recheck-r1/ready.json。旧183-v1和所有红证据不覆盖。

## 基线与阶段决策

Root有限接受183-v1源差量，未运行Kotlin/Compose/设备。source-after/NestedTemporaryReaderEntryTest.kt 31645B SHA cfc7af26b976127be77bb6eb1ad93941d8788fa46d26ee0e7a48bb373a0e05e4；patch4362B SHA d84b07de4c2f84ff3c1ffe506c20520cc3cdb35273e0fc6b2a320eb8dd0c79f6；ready3682B SHA10b5c985796021e3dca926f457b9af7261cea30025b2ebacde236c641298ef8a。Root确认实际BackHandler的controlsVisible分支先隐藏Chrome再requestExit，四个Back调用点前加单次hide helper且业务强断言保留。

限制必须准确：scrubber还受showProgressBar/selectedText/底栏mode门控，不等于所有状态controlsVisible；autoHide检查到Back之间时序未设备证。不能说已完全证明181运行根因/设备通过。183报告finished01:52预写不据，原ready实际mtime01:38:52，实际send01:30:32.509，约8m20，旧报告不改。

A182变更性执行已结束，Root只接受原tar内容保全+当前产品缺席部分证据，卸载原始回执缺失/阶段日志覆盖/完整保护未核永久留。新A182-r1仅只读身份/原test/设置/保护闭合，完全不读candidate或新APK；因此现在将候选源码/唯一Gradle构建资源交你，与A只读观察独立。你严禁设备/ADB/VM/instrument/main/恢复/设置/launch等。后续业务设备批需Root另行验收新鲜绑定，不能自行重跑181。

## 独占修改与输入pin

唯一candidate写文件：E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/src/androidTest/java/com/creationreadingassistant/acceptance/NestedTemporaryReaderEntryTest.kt，当前28819B SHA4ade45ab3b63eba779be6f9d84445f496265cc324a7139592fbb84831161bd57。核相同才集成上述after；漂移则停报，不覆盖他人修改。既有WIP均保护，不Git reset/checkout/stage/commit/push。

fixture J1RealReaderFixtures.kt 55008B SHA0aea2c5eed35d7f189df72590013fdea1bdf658045b5ae7373d16617d9265ce9只读。产品APK app/build/outputs/apk/debug/app-debug.apk 54969452B SHA4d302624d603c06b12b1a30480dd1a9d5b7ce5e44cb24efafecd81d4d6c62577只读不改。当前旧test APK app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 10688778B SHA47f2a66ce35a3937ee7c6fd3d0649a3eae6d6849abfecba71cdf9638a4e26541，构建前若已有明确冻结独份则按绝对路径/fullSHA引用；否则仅保全一次该旧test，不复制产品APK/工程/cache/旧日志。先估算新增空间，已有依赖直接复用。

## 实施验证

1. 核对旧candidate/source-after/patch与三个冻结输入。集成精确after。只为真实编译错误修本文件必要import/API，不扩大测试语义或改产品源码，不删断言/放宽timeout/加sleep/Back循环/内部Nav或VM注入。保存正常退出后S0、精确来源正文/无reader返回目标、BackNotes、第二不同源、12列exact隔离、普通重开仍S0/非两源offset的完整合同。
2. 同一owner串行使用已有 E:/creation-reading-assistant/task59-common-candidate-v1/tools/gradle_locked.py；cwd为现有candidate/android，JAVA_HOME D:/develop/Java/jdk-17.0.14，复用工具自带GRADLE_USER_HOME。只 :app:compileDebugAndroidTestKotlin 和 :app:assembleDebugAndroidTest（可同一有界调用，timeout900）。实际原始log/result保存新r1，锁忙即停报不杀锁，不另复制工程或建立新Gradlehome。旧充分suite/JVM/产品全量构建不重刷；真实编译失败保留原红后仅必要修复定向重编译。
3. 新test APK只在原buildoutput，核完整size/SHA、apksigner证书SHA、runner/target：com.creationreadingassistant.testing.CraIsolatedTestRunner / com.creationreadingassistant。预期签名05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1。产品APK与fixture前后同指纹；不要运行instrument/安装。raw真实argv、退出码、耗时及锁正常退出提供证据，shellrc0不代替BUILD SUCCESSFUL。

## 交付

最小patch/最终单文件或引用已存在唯一after/REPORT/ready，源码文档合计<=128KiB，raw<=128KiB。必要旧test独份另列，APK新buildoutput另列，不复制到r1。每个声明pin绝对path/fullSHA/bytes，报告实际总分类体积不漏scope，真实UTC/HK开始结束时间不预写估计。只称集成编译通过/设备NOT_RUN，LIFO入口缺口/历史181两FAIL/182历史缺回执保留。完整实现+定向验证+交付同owner完成，不额外拆纸面审查任务。完成后独立最后一行“这是任务183-r1。”
