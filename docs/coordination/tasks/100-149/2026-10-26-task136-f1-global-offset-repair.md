# 任务136-v1：F1测试章内/全书坐标修复、必要APK与隔离绑定

2026-10-26香港；Claude桌面Code/global DeepSeek Flash，同135 owner，预计25分钟。135唯一attempt已消耗且收口，不重试。Root已读原stdout/summary/4件失败证据、原test恢复/设置/keepalive/final保护及postrun记录，并核失败4件和本批交付sizeSHA；原testinventory独立核694B/d6ac。产品按真实全书4007导航，测试误以章内3991断言，F1 recreate尚未触达。原红证据永久保留，不判产品F1失败。仅修这一测试缺口，保持严格语义；Root协调，执行者同owner完成实现/必要构建/输入闭包，不设备。

````text
任务136-v1唯一测试/host/必要APK构建owner为你；不是唯一协作者，不覆盖其他修改。Root不旁路build/ADB，用户要求自主继续，不重复问已有范围许可。

输入：唯一工程 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。
F1测试 app/src/androidTest/java/com/creationreadingassistant/acceptance/TemporaryReadingRecreationRegressionTest.kt34852B SHA982f9771d3aa5a41cc1bd2b39b9f61979c3862540cf8ff68b6ce1a7c2e96aad0。
只读host131-r1 E:/creation-reading-assistant/task131-f1-host-binding-v1/lane-b/scripts/host131-r1.py51102B SHA7d7534c887958c6bf0e25e516d1062a08e8cedc2b54d574e1f11b07f116fa65e，原inputs3909B/e694d8b5、attempt/summary已用，禁止覆盖/删除/修改。
当前testAPK10658163B SHA564b5837a9b7ffadfea88cb5886c46f6f7863b6124a4e7eb36ea28ed99b1ab63；product52763832B SHA74d106b6b1349cfea1f4b4bb5f49e41c27691d6621ca1424e4c55effa0de84e4，build输出路径沿132。
135红原件 task131/lane-b/evidence/instrument/t129collect/t129-results-T131V1-20261026-101403/ 和device/04-run.json、summary.json、task135-f1-single-device-r1-v1/REPORT.md只引用不改。实际断言expected3991/actual4007发生源码512行，recreate未运行。host summary ok=false/businessOk=false/recoveryErrors=[]；ready.hostMainExit=0不能作业务通过，核包装返回与真实host状态解释，必要在136报告作澄清，不覆盖旧红summary。

最小修复：
1. 仪器目前把deriveSearchCoordinate.expectedCharOffset章内值直接作为SourceNavigationTarget.legacyOffset。按真实生产SearchIndexRepository.resolveLegacyOffset→SearchOffsetResolver与TxtChapterSplitter相同口径计算命中章全书start+章内offset；沿用原SearchContentHitJourneyDeviceTest.kt955-1020的生产分章回溯方法，不硬编码16/4007，也不直接用actual协调器状态作为expected。保留UI定位snippet和索引合法offset为章内3991的语义，目标legacyOffset用独立计算全书值；断言与bodyText查询词真实位置/回溯吻合。报告实际公式证据，不能仅放宽相等或offset阈值。
2. 命中阶段JSON明确分开chapterOffset/chapterStart/globalOffset/expectedTargetLocator，不能把UI章内与导航全书混一列；旧expectedOffset如保留必须注明它仍章内。其余F1同VM/rid1/marker/空栈/Back与12列断言保持。准确更新“搜索入口传章内legacyOffset”错误注释，不改生产。
3. 为后续新批在新 E:/creation-reading-assistant/task136-f1-global-offset-repair-v1/lane-b/scripts/host136.py 从r1最小派生，ROOT自然新lane。新仪器与host必传runId正则统一T136V1-YYYYMMDD-HHMMSS，技术task=136-v1；旧131全部冻结，禁止同lane第二attempt。新host只改新源码pin/身份与新的坐标证据判定接缝、保护输出子根改同OUT_BASE/task136-f1-global-offset-repair-v1；不改adapter/pin guard/恢复停止/业务超时等充分逻辑。输入从原9角色小JSON派生到新lane/inputs.json，实际指纹冻结后闭包。
4. 只必要定向离线检查新增坐标关系/新路径与输入闭包；保留旧135expected3991/actual4007回执证明修正不是改绿阈值。不得重跑31项/全套宿主审查/JVM/lint。源码改完允许一次必要 :app:compileDebugAndroidTestKotlin，其新增编译错误同owner定点修复保留红日志。
5. 编译闭合后允许一次 :app:assembleDebugAndroidTest（不assembleDebug），失败需改源或第二次构建则停报Root，不批内扩大。覆盖当前564b前必须确保该唯一旧testAPK历史原件保全：若没有已知同SHA独立副本，只复制该单件到本task/preserved/old-test-564b5837.apk，核10658163B/SHA一致。原a646已有132保全不再复制，产品APK不复制/重建，新APK只留原build输出。核产品仍74d106b6与新test实际class/runner，并写新inputs9角色实际SHAbytes；新host外层SHA另报Root。
6. 纯离线load_and_verify_inputs可在导入subprocess守卫后执行，不main/run_attempt/cmd_protection/adapter run_check/ADB；未创建run-id/budget/attempt/summary。新保护输出路径在实际OUT_BASE内且initial/final唯一不存在，旧133冲突不复发。不要重新全hash全部历史原件，把新/改输入和未来实际路径闭包一次核足。

所有权/存储：唯一可改既有文件为新F1测试；production/helper/原仪器/Gradle公共配置不改。新host/input/patch/REPORT/ready/raw仅task136根，旧131/135红证据不覆盖；Root D current不写。差量<=1MiB/raw<=2MiB，必要旧单564bAPK保全<=12MiB；仅该单件复制，无工程/cache/APK对/历史原件复制，存量不删移压迁。
JDK D:/develop/Java/jdk-17.0.14 SDK D:/develop/Android/Sdk Gradlehome D:/develop/env/gradle；唯一workspace同上，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock存在非活锁，活owner冲突停不杀共享进程。
禁止任何ADB/VM探测启停/设备/host main/attempt/安装卸载/设置，本批完全离线实现构建，135已用attempt不重试，后续Root核冻结新批才设备。不申请用户重复许可或自行放行新设备。禁CLI登录代发/私库自动记忆/Git暂存提交pushPR发布/云SSH SFTP/正式库HOLD/实体手机/购买换号/存量删移压迁/杀共享进程。
停止：输入/owner/活锁/空间冲突、需改生产或放松断言、超25分钟或构建失败停保留明确BLOCKED。交付REPORT/ready task136-v1、最小patch/前后SHA/必要编译构建原始与新APK/新host/inputs闭包；实际新增字节分类、F1未证/设备NOT_RUN分列，完成停等Root，不自动设备。末行：这是任务136-v1。
````