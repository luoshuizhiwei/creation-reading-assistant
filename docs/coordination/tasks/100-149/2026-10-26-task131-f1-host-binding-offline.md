# 任务131-v1：F1仪器与隔离宿主绑定（仅离线）

2026-10-26香港；Claude桌面Code / WorkBuddy AI/global:deepseek-v4.1-flash 1M，同130 owner闭环。预计25分钟。依赖130已Root读实际patch/原始编译成功13s并核四件sizeSHA及工程源码10b55b；130已停止。唯一共享测试文件与宿主接缝同owner串行，无有价值独立任务则不造审查。目标是可审查的宿主/仪器运行身份与证据合同，不运行设备、不构建APK。

````text
你是任务131-v1唯一测试/宿主绑定owner。Root协调/验原件；你不是唯一协作者，不覆盖他人修改。

当前事实：128真实MuMu搜索→临时阅读→Back与恢复Root已接受闭环，不重跑。129新增F1测试编译通过；130修复索引前置矛盾与Home搜索真实点击，Root接受这两项实现/编译。F1 recreate/rid身份/ViewModelStore时序未设备证，不写F1 PASS。用户选择沿用本Claude桌面当前global Flash路由继续自动化，禁CLI/私库自动记忆。

唯一工程 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。
唯一可改既有源码 app/src/androidTest/java/com/creationreadingassistant/acceptance/TemporaryReadingRecreationRegressionTest.kt：32780B SHA256 10b55bdc32e71e1cee6a3eaaeab5bd05001965bdc6f2e3e05f7304525652b08b。
已完成130报告/patch/log E:/creation-reading-assistant/task130-f1-test-precondition-repair-v1/，只引用不覆盖。
只读宿主 E:/creation-reading-assistant/task124-instrument-coordinate-cleanup-v1/lane-b/scripts/host124.py，40753B SHA bce4a930c516b21193f5d80163dc296fe4354354ac127d59c961f2592317d519；inputs.json2861B SHA ffc778e0198b3c5121fb1de746e76f6e2c3faaa4e5b76ded5d3f90a5128c76a8。
可按该host实际引用路径读取必要base/保护适配器/恢复合同，不扫历史，不复制历史原件。

已知不可直接运行旧host的具体接缝：
- host124固定旧SearchContentHitJourneyDeviceTest、T119V1 runId、task119-results/cra119 fixture路径和旧teardown清单；129类不同、自己生成随机f1rr runId、t129-results/cra129路径及t129_*.json实际字段，没有旧teardown_timeline。
- 旧testAPK a646c8de46116d75bb4c17dbcc1bd97b9d95de0e30056b3834e53c106a4ba392不含129/130，必须拒绝当新F1APK使用。

实施目标（最小必要）：
1. 只在新测试增加宿主必传 runId 参数，与旧仪器runId参数命名对齐但用明确新前缀如T131V1-YYYYMMDD-HHMMSS；无本机随机fallback。缺失/格式不符必须在打开DB/onboarding/fixture写入之前失败。不要改变130已修顺序/严格断言/F1业务语义，不改helper/生产/原仪器。
2. 新建 E:/creation-reading-assistant/task131-f1-host-binding-v1/lane-b/scripts/host131.py，从host124最小派生；旧host/inputs永不覆盖。ROOT仍文件相对新lane，所有未来输出/run-id/attempt/summary隔离在新lane。只引用既有APK/base/保护/恢复原件，不复制工程和APK。
3. 按F1仪器真实结果目录、资产账本和每阶段JSON建立严格收集/判定合同：runId与book归属必须一致，必须真有fixture/search命中/temp落点/recreated/back/journey_success/teardown资产账本等实际所需证据，F1同VM/首次请求身份/落点/空栈/前后12列冻结要求不能只相信status PASS。先核实际字段再写；若必须补少量证据字段，只改该测试的取证，不改生产或放松断言。失败证据与业务失败分别保留，不能缺证判PASS。
4. 保持既有安全合同：1800业务+1200恢复预留、deadline启动epoch+5400（尚无本批设备授权/不写实际run-id/budget/attempt）；unique终态-1/OK(1 test)/精确class+count、writer停止确认、未知残余BLOCKED保留、失败不重试、不rm未知/不越界卸载、原test完整APK/data/userstate、71/820/Download179/11settings/nightno/VM保护。不要为新证据适配重写已充分停止/恢复门禁；触及处给具体diff与理由。
5. 新inputs-template.json明确INCOMPLETE_NO_NEW_TEST_APK，不把旧a646填成新测试APK；可列待构建角色路径但缺SHA/bytes必须明确阻断。host实际入口必须拒绝未完成的新testAPK绑定，且这种离线缺输入失败发生于任何attempt/设备命令之前。未来构建后由Root另派冻结实际inputs，当前绝不自动assemble/设备/host主入口。
6. 新增少量纯离线定向检查，仅覆盖本次接缝：缺/错runId先拒绝、host类/目录/fixture路径与仪器一致、新/错runId证据、缺recreated/back/账本、假的journey_success/缺F1布尔或行冻结、旧a646与未绑定APK拒绝、独占输出新lane。不得导入并执行base带ADB路径；若模块加载本身有设备副作用，隔离纯函数或静态核对，不执行host main。禁止为绿而降低标准。旧全套/JVM不重跑。
7. 仪器源码确有变更才允许一次必要 :app:compileDebugAndroidTestKotlin（新增错误同owner定点修正保留红日志）；host只做纯离线检查，不运行主入口，不创建attempt文件。

所有权/存储/环境：
- 仅上述F1测试可改；新host/template/定向检查/patch/REPORT/ready只写 E:/creation-reading-assistant/task131-f1-host-binding-v1/。Root D/current你不写。
- 差量<=1MiB，必要raw<=2MiB；新host约40KiB/源码约33KiB/小日志，无新大目录。无工程/build/cache/node_modules/Gradlehome/APK对/历史原件复制，存量不删移压迁。
- JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，Gradlehome D:/develop/env/gradle；唯一构建空间即现android；锁 E:/creation-reading-assistant/locks/android-gradle-exclusive.lock按owner协议确认，存在非活锁，活冲突停，不杀共享进程。
- 生产AppNavigation7a6046a5/VM e4af886a/原仪器0557506f/helper0aea冻结。产品APK74d106b6只引用，不构建。原testAPK0d18d788与data恢复合同沿用原host引用，当前不设备核。
- 禁ADB/任何VM探测或启动重启/安装卸载/前台或night设置/仪器运行/host主入口/attempt/assemble；128一次设备窗口已结束，不推断新许可。
- 禁Git暂存提交pushPR发布/云SSH SFTP/正式书库HOLD/购买换号或切供应商/存量删移压迁/杀共享VM ADB进程/所有私库和自动记忆。

报告与停止：
25分钟预算；SHA/owner冲突、需越权生产或设备、接口无法最小闭合立即保留明确BLOCKED，不能默默扩任务。交付REPORT.md+ready.json（taskId131-v1）、最小实际patch/改后源码指纹、新host与template与定向离线raw日志。报告区分实现/纯离线/编译/未构建/设备NOT_RUN；列实际新增字节、保留分类、下一APK绑定/当前设备许可与恢复缺口。已有充分130证据引用不再全审。完成后停止等待Root，不自动构建APK/运行设备/接下一批。最终总结末行：这是任务131-v1。
````