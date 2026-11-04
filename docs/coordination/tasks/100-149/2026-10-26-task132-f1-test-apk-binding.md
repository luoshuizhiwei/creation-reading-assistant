# 任务132-v1：一次F1测试APK构建与实际输入冻结（不设备）

2026-10-26香港；Claude桌面Code / WorkBuddy global DeepSeek-v4.1-flash 1M；同131 owner，预计15分钟。131离线绑定已Root读REPORT/源码patch/host评估器与输入门禁、31/31离线raw、14s真实Kotlin编译；5新产物/报告/两raw与工程源码指纹一致。Root接受本批离线绑定，不作F1运行时PASS。下一依赖是测试APK，单一workspace同owner串行，不制造无价值并行。

````text
任务132-v1，唯一APK构建/输入冻结owner仍是你。Root协调与验原件，不旁路构建或设备。你不是工程唯一协作者，严禁覆盖他人修改。

目标：只构建含131最新F1测试的androidTest APK，保全将被覆盖的唯一旧a646证据，冻结实际inputs供Root后续审查。此批无设备许可，不ADB/VM/host主入口/attempt/安装卸载/设置改动。

唯一工程 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。
固定输入：
- F1测试 app/src/androidTest/java/com/creationreadingassistant/acceptance/TemporaryReadingRecreationRegressionTest.kt 34852B SHA982f9771d3aa5a41cc1bd2b39b9f61979c3862540cf8ff68b6ce1a7c2e96aad0。
- 新host E:/creation-reading-assistant/task131-f1-host-binding-v1/lane-b/scripts/host131.py 50937B SHAb28362a2368f11cdf5e1e66de226e08b02176036d305a1517e22e27590194dc3。
- inputs-template.json同lane，4023B SHA1a68504de988337f810d860ff8ae9c167be4d41d889dadf6f495641c08aff3c9，INCOMPLETE，不执行。
- 产品APK app/build/outputs/apk/debug/app-debug.apk 52763832B SHA74d106b6b1349cfea1f4b4bb5f49e41c27691d6621ca1424e4c55effa0de84e4，生产与其源码冻结不改，不重建产品APK。
- 旧testAPK app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 10643866B SHAa646c8de46116d75bb4c17dbcc1bd97b9d95de0e30056b3834e53c106a4ba392。
- 131已充分日志和旧128恢复证据只引用，不重跑编译/JVM全套/31项合成检查。

操作与所有权：
1. 先核上面关键输入/唯一活owner。将要覆盖旧testAPK前必须确保其同SHA独立原件保全：若131/124已知报告明确指向现存同SHA保全文件，可核后直接引用；否则只将该唯一旧testAPK复制一次到 E:/creation-reading-assistant/task132-f1-test-apk-binding-v1/preserved/old-test-a646c8de.apk，并核SHA/bytes。不得复制产品APK或APK对/工程/cache，不宽扫整个磁盘找副本。保全没完成不构建。
2. 唯一既有workspace执行一次必要 :app:assembleDebugAndroidTest --console=plain，不跑assembleDebug/全套JVM/lint。生产构建产物保持原SHA；若工具会要求生产重建/源码漂移或编译失败需改源，则停报具体原因，不批内改测试/生产或重试构建。
3. 新testAPK留在既有build输出路径，只读记录实际SHA/bytes与APK包名/仪器runner/新F1类存在的本地证据（可用既有SDK apkanalyzer/dexdump等，勿下载工具/不ADB）。包含旧原仪器不等于运行它，不作运行时通过声明。
4. 仅新建 E:/creation-reading-assistant/task131-f1-host-binding-v1/lane-b/inputs.json，基于原template复制小JSON并补全部实际冻结角色、testApk新SHA/bytes、status FROZEN；task仍131-v1以匹配host。不覆盖template/host/旧inputs或其他原件。输入同实际执行路径，instrument仍982f9771，生产/恢复件沿template充分指纹引用。
5. 只纯离线调用host的load_and_verify_inputs（不能run_attempt/main）；导入前设subprocess守卫，无任何设备命令/attempt/run-id/budget/summary文件产生。9角色实际核同并保存结果原件；只补此新增APK实际绑定正例，不重跑充分31项。若导入有外部进程动作立即停。模板仍INCOMPLETE原封保存。

输出仅 E:/creation-reading-assistant/task132-f1-test-apk-binding-v1/（REPORT.md、ready.json taskId132-v1、必要构建/本地APK检查/输入闭包raw）及上述唯一新inputs.json；禁止改任何源码/host/公共配置/D current。当前并无设备运行许可，完成后停等Root。

存储预算：差量<=1MiB/raw<=2MiB，必要旧单testAPK保全<=12MiB（10643866B）；新APK只保留既有build输出，不再复制。构建前检查可用空间，不删移压迁存量腾空间。只有此必要旧件保全可复制，非默认APK对备份。
JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，Gradlehome D:/develop/env/gradle，锁 E:/creation-reading-assistant/locks/android-gradle-exclusive.lock；存在非活锁，按owner协议核，无活owner才构建，不杀共享进程。

停止：输入SHA/owner/活锁冲突、保全缺失、生产APK变化、构建失败或预算不足，保留现有原始证据并BLOCKED，不暗改码/换路由/重跑。禁止ADB/任何VM探测与启停/设备/host main/attempt/安装卸载/前台night设置；禁止CLI登录代发/私库自动记忆/Git暂存提交pushPR发布/云SSH SFTP/正式库HOLD/实体手机/购买换号/存量删移压迁/杀共享进程。
报告列原旧APK保全实际路径/sizeSHA、新APKsizeSHA与本地类runner证据、原始构建命令exit、完整inputs9角色、host未运行/设备NOT_RUN、实际新增字节分类。成功只是新APK构建和绑定，F1运行时仍未证。15分钟后收口，末行：这是任务132-v1。
````