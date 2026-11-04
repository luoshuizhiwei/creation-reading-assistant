# 207-r3：生产导航接缝复用及可执行回归

2026-10-31香港时间；用户纠正Root未接续给ZCode提示词，本合同承接其自动交接授权。目标ZCode，电脑操控GPT-6 Luna，实施模型按实际可用菜单记录。复杂度中高，预计40–60分钟，硬预算40分钟，实际收到起计。发送前不造派发时间。

本轮1个任务；导航生产/消费测试/唯一candidate及Gradle强依赖，必须同owner闭环。没有已明确输入且互斥的独立B线，不制造并行审计。Root先改变失败方法，再交同一ZCode任务继续，避免重复完整旧合同。不能因现有测试接缝失败把日常技术决定全部退给用户。

````text
你接续同一项目207，执行207-r3。用户已明确要求Root把下一轮提示词实际交给ZCode。207-r2的文件/构建证据已核，整项未接受；不要重新实现无关功能或重跑充分旧审计。本任务修正具体测试错误，并将测试接到生产实际调用点。

先读活动仓库AGENTS.md、docs/handoff/current.md、E:/creation-reading-assistant/task207-reader-exit-stack-repair-v1/recheck-r2/root-review/REPORT.md及本合同。唯一工作区E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。原205业务红永久保留，206有限恢复不重跑；异步保存失败未被排除，导航不是205唯一已证根因。

基线：r2 ready SHA934039cd2a85ca087ad65d188fe66b53a2540dafe8917f67a7d6d1d012975966；ReaderRoute.kt SHA5953dd9d8be64cbd4557d965d9e762fddeb8ffd05c8709e1323fdddc6cc17ada；ReaderTemporaryBackTest.kt SHA6a37e45b6c16986d04dabccfe90ee691b6ca16b53909e6ac03cd635ef6a26bba；ReaderTemporaryNavigationStackTest.kt SHA4e4640152a44bfde67f89959ab18dc271924bca25ec2a91f04115895a1039dfb。先核三文件与candidate，不覆盖新WIP。原JVM12项rc0和r2产物/签名证据按未变输入范围复用。

文件所有权：你为本轮唯一导航生产/相关测试/candidate/Gradle owner。可改ReaderRoute.kt、ReaderTemporaryBackTest.kt、ReaderTemporaryNavigationStackTest.kt；为直接覆盖真实生产入口，本轮明确允许新增app/src/androidTest/java/com/creationreadingassistant/acceptance/ReaderTemporaryNavigationProductionTest.kt。其他生产文件、公共构建配置、AppNavigation.kt、J1RealReaderFixtures.kt及冻结ReaderInternalTemporaryLifoTest.kt不可写。其他执行者可能在库中工作，不能回滚/覆盖其资产。无设备/ADB/VM/恢复、Git暂存提交pushPR发布、D产品回灌、删移已有资产、正式库/私库session DB/记忆权限。

Root技术决定及验收边界：
1. 依据Root已保存的2.5.1官方NavController与NavBackStackEntry复制构造，singleTop替换对象、保留entry.id。修正图测试440行“id改变”的断言；观测实际destination参数、previous entry身份及pop结果。不能把UI Composition存活数量当全栈，也不能通过删关键断言消除失败。
2. 图测试120行remember(entry.id)不足以反映S0→T1→T2参数切换；必须随生产同样的bookId/sourceLocator/navigationMode/协调器变化登记新请求，正确保存ownedRequestId及只作废旧pending。一个@Test不得反复compose.setContent：将三个入口拆为独立@Test，或只setContent一次后由明确可重置的图状态驱动。核当前ComposeTestRule真实约束，不凭印象。
3. 禁止继续复制navigate选项再称生产回归。最小接缝放在同一个ReaderRoute.kt：必要时将现有“进入临时route”和“生产返回决策→navigate/pop”的实际分派提为internal函数，让ReaderRoute原回调和图测试共同调用同一实现。提取只能服务当前两个真实调用点与测试，不新增策略框架、接口层或改变既有保存语义。测试必须对生产受改函数敏感；按受影响改动证明测试并非始终自洽的镜像。仅纯函数调用不等于ReaderRoute生命周期/保存await已覆盖。
4. 如需覆盖完整生产组合，不必手工构造有19个依赖的ReaderViewModel：现有冻结ReaderInternalTemporaryLifoTest.kt已展示createEmptyComposeRule+ActivityScenario<MainActivity>及J1RealReaderFixtures启动真实Hilt应用的模式。只读参考，新增独立ReaderTemporaryNavigationProductionTest.kt复用既有合法fixture和原宿主。至少为真实reader内临时入口→返回→普通退出接上生产链；能按现有fixture构造失败来源时补失败/取消原reader保留场景。不得造伪测试钩子、修改冻结fixture/host或吞异常；若具体场景确需范围外能力，只列确切缺口与建议文件，不停止可完成的共享分派/图测试修正。
5. 覆盖成功S0→T1→T2→T1→S0→一次退出回真实原入口；一级pending失败回S0，二级pending取消回T1不消费合法LIFO，原请求的真实迟到提交被拒；不要另造一个stale请求冒充被取消的原requestId。书架/搜索/笔记实际入口语义不硬编码成书架。保留r2纯函数修复，不绕过ReaderScreen awaitProgressSaves或放宽203超时/位置断言。

验证：原JDK17/SDK/Gradle home、tools/gradle_locked.py唯一锁。仅新增或改变的JVM定向验证；未改的12项证据可复用，新增生产提取需要定向编译，图/生产UI测试compileDebugAndroidTestKotlin。必要assembleDebug/assembleDebugAndroidTest生成匹配APK对，不全suite、不重跑历史D或206恢复。设备/Android专属用例本轮NOT_RUN，报告必须明确“编译交付可供Root审查”，不能称行为通过。Root可以在源码/编译/产物核验接受后另定fresh设备合同；当前合同不包含执行它。

交付E:/creation-reading-assistant/task207-reader-exit-stack-repair-v1/recheck-r3/：REPORT.md、ready.json、必要before/after、最小标准patch、实际正反apply结果、原rc/log/XML、candidate对应APK路径/size/SHA及完整证书05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1、runner CraIsolatedTestRunner/target com.creationreadingassistant与目标方法。报告不得用“壳/镜像”当生产闭环；分别写共享分派、完整真实UI、保存/加载边界的实际覆盖。r2原件不覆盖；r1旧pins索引实际为recheck-r1/raw/deliverable-pins.json。pins无自引用、最终数值与实际字节一致。

存储：复制前估算新增体积/检查E可用空间；source/runtime/report/ready≤256KiB，普通raw≤256KiB；before引用既有稳定原件优先，不重复复制工程/依赖/cache或已保全APK。r2 APK若构建覆盖前尚无build外唯一保全，仅缺时留一对，约64.62MB，单列依据。新增空间/保全/临时scratch统一记REPORT，不另造收据。40分钟到交实际进展与具体缺口，不随机重跑；产品改动、必要编译修复、核验报告由同owner闭环。

用户stop/Esc、实际权限/认证/输入冲突停对应动作。交ready后停止，Root核验，不自行发消息/设备测试/自动接新任务。最终面向用户总结最下面独立一行：这是任务207-r3。
````
