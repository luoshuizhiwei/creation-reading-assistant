# 任务144-v1：新产品与测试APK构建冻结，旧原件先保全，无设备

2026-10-26香港14:57；原143 owner，Claude桌面WorkBuddy global Flash，同候选唯一Gradle owner。

````text
Root已读143实际集成patch/REPORT/54s compile原log，候选SearchScreen45026B/80181fa42605dc9616635795af26623bfa20aa309cc8b58052ca78331110570f、新SearchListScrollRetentionTest32650B/11092359b9857571d304646d9694e1e74e1f0763853cbf0555855ac0b40a012f均sizeSHA核同；接受源码集成+编译，不设备。142-r3原两红/八目标绿与compile充分复用，不重跑reader JVM/F1。

143报告B-r1REPORT不一致为路径误读，不是资产漂移：Root本轮实际核lane-b/REPORT.md6966B/c4e3b672935d17ce13459eb4e5998472b167862d98175b8441fc87eaf9a3d0b4为v1，lane-b/recheck-r1/REPORT.md5735B/c08617b4936d431c9266e44dcecda479d559a6e9ed53918b7843080405e5ad19为r1，两件都未漂移。143原件冻结不追改；本REPORT准确订正此路径事实，不另造r1任务/扫描历史。

本批Root明确新授权：仅在旧构建APK原件已保全核同、前置源码冻结核同后，一次必要 :app:assembleDebug :app:assembleDebugAndroidTest --console=plain，生成含142reader修复/143SearchScreen观察接缝+新scrolltest的匹配产品/测试APK。此授权精确覆盖“旧74d106b6/28173170构建输出不得覆盖”在本批构建输出路径上的限制，前提是以下单件原件保全完成。不是设备授权，不自动进入安装/host/attempt。

唯一工程 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。
构建输出 app/build/outputs/apk/debug/app-debug.apk旧52763832B/74d106b6b1349cfea1f4b4bb5f49e41c27691d6621ca1424e4c55effa0de84e4；app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk旧10658820B/281731702481a5a92f65efe8c64558a38577c0e711105726f6f9c6e1dc3db25b。

先估新增体积/可用空间：只新增必要旧产品单件~51MiB和旧test单件~11MiB，新增构建产物仍原build输出，不复制完整项目/cache/历史APK对。若已知独立冻结同sizeSHA原件存在，引用核同复用，不重复复制；不全E盘扫hash找副本。否则分别复制到 E:/creation-reading-assistant/task144-matched-apk-build-v1/preserved/old-product-74d106b6.apk 与 old-test-28173170.apk，任何已存在目标先核同不覆盖。两旧原件实际sizeSHA核同后才可构建；原件保全差量预算80MiB，文档patch256KiB/raw1MiB；若空间不足或源指纹漂移准确BLOCKED，不删移资产腾空间。

构建前核源码冻结（仅必要输入，不历史全扫描）：
VM55808B/f8762da6326972c3c4582c6a38aa142656dbbeedd6a5fd04b73265edd5c2e3e3；VMTest103388B/cec25280cc84d17ecf784bd83742d665b37d241b65b8aa2229f7baf77473c19b；ReaderScreen c1727db4、Platform6de3b254、Session19c5ddc5、Route3c0cd8bd、ReaderNavLateDisposeTestca28dab9、F1cc429bb8、SaveOrder61b2ca9d、AppNavigation7a6046a5、helper原指纹不变；Search/test完整指纹见上。禁止源改/业务/公共Gradle/deps调整。若构建失败，本批留原始红停报，不为第二次构建猜改码或扩大；新编译缺陷给Root具体证据后裁。

JDK D:/develop/Java/jdk-17.0.14 SDK D:/develop/Android/Sdk Gradlehome D:/develop/env/gradle，独占锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock冲突停不killdaemon。Root不旁路build/ADB。一次构建成功后对新APK真实bytes/SHA、package/version/runner/instrumentation target、debug签名与旧同角色签名一致性作离线核验；可用现有SDK工具，不装新软件/下载依赖。离线查DEX可见新产品ReaderExitBoundary/awaitProgressSaves与SearchListSnapshot、测试APK新SearchListScrollRetentionTest与既有F1类，支持代码包含但不冒充运行验收。签名密钥不导出/不输出敏感值。不得只测试APK沿旧product作为新输入。

新匹配APK仅留原build输出，建立新freeze清单引用其绝对路径sizeSHA及相关生产/测试源码输入指纹。不得覆盖旧冻结清单/旧inputs/host或创建device run-id/attempt/budget。当前APK冻结输入有变化，后续新host/inputs/设备批另派，不沿旧137/139运行合同。

输出 E:/creation-reading-assistant/task144-matched-apk-build-v1/ready.json、REPORT、必要构建原log/离线APK证据/新freeze清单、旧单件保全指纹/空间分类。20分钟。结果区分旧原件已保全/新匹配APK离线构建冻结/未设备；不重跑整JVM/VMTest/lint/仪器/F1，不夸全产品分母。

禁任何ADB/VM探测启停/安装卸载force-stop设置/hostmain/attempt；禁Git暂存commit push PR发布/云SSH SFTP/D产品回灌/正式库HOLD/实体/购买换号切供应商/旧存量删移压迁/kill共享进程/私库自动记忆。139当前恢复/137限定/138保全历史原件不动。完成等Root核真实APK闭包后再派下一批，不自动设备。最终总结末行：这是任务144-v1。
````