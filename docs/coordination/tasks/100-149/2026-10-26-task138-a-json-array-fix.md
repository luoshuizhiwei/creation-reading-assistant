# 138-A-v1：仅修F1未覆盖项JSON数组序列化

2026-10-26香港；Claude桌面现有WorkBuddy global DeepSeek Flash；新独立源码owner，10分钟。与138-B仅设备保全真正并行：A独占测试源与必要Kotlin编译，B原设备owner不改源不构建。Root协调验原件。137 F1本体已OK(1 test)，全部核心阶段与12列证据Root已读核，但not_covered写成字符串导致host严格门禁拒绝，整体仍BLOCKED。不能为一个元数据类型问题重跑充分F1旅程。

````text
你是138-A-v1唯一源码/必要Kotlin编译owner，不是工程唯一协作者；原设备owner正在138-B保全，禁止ADB/设备/APK构建，不覆盖其证据。
唯一工程 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。
只改app/src/androidTest/java/com/creationreadingassistant/acceptance/TemporaryReadingRecreationRegressionTest.kt36700B SHA81c09c9584f2049ca059bc921b0455cf682960119daaaf772d072da0580f2abd。
原证据只读：E:/creation-reading-assistant/task136-f1-global-offset-repair-v1/lane-b/evidence/instrument/t129collect/t129-results-T136V1-20261026-104309/journey_success.json，not_covered实际字符串；对应host summary业务true但collect false产品KEPT_BLOCKED。旧源副本在task136/src，旧红/137所有原件不覆盖。
最小改动仅将journey_success.not_covered的Kotlin listOf写法改为显式org.json.JSONArray（两条原未覆盖文本原样保留），必要import。别改F1断言/坐标/rid/Back/业务、host strict list判断或生产；不改原始JSON去涂绿。保存实际两三行patch与前后SHA，解释实际org.json为何旧写法产生字符串、新显式JSONArray保类型。
允许一次必要 :app:compileDebugAndroidTestKotlin，新增编译错误同owner定点修复留红；不assemble/JVM全套/lint/设备/宿主主入口/attempt。编译通过不等新JSON在设备运行已证，当前APK28173170仍含旧not_covered写法，不重建不冒充含修复。137真实F1行为证据继续复用，不为了元数据重跑旅程。
输出 E:/creation-reading-assistant/task138-json-and-preservation-v1/lane-a/REPORT.md、ready.json taskId138-A-v1、minimal.patch、必要compile raw；差量1MiB/raw1MiB，源码副本可选，不复制工程/cache/APK/历史证据。JDK D:/develop/Java/jdk-17.0.14 SDK D:/develop/Android/Sdk Gradlehome D:/develop/env/gradle；唯一workspace同上，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock，存在非活锁，冲突停不杀共享进程。
不读其他私库/自动记忆，不CLI登录代发、不Git暂存提交pushPR发布/云SSH SFTP/正式库HOLD/实体手机/购买换号/存量删移压迁，不改D current。SHA/owner冲突或需扩修停报，完成停等Root。末行：这是任务138-A-v1。
````