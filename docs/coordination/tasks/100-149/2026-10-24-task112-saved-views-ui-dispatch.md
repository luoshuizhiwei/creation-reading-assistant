# 112-v1 L1动态视图真实UI窄旅程

编号112-v1；2026-10-24 13:46准备；官方Start Plan GLM-5.3；同owner真实UI仪器、必要test构建、一次验收和恢复。107离线持久化已充分，111素材旅程闭合；本批填L1实际UI缺口，不重造功能。共享testAPK/host/device串行，当前无独立有价值第二实现。

````text
执行112-v1：继续既有L1书架动态视图真实UI保存/复用闭环。仍原ZCode恢复项目creation-reading-assistant-recovery-20260919/分支codex/recover-worktree-20260920/既有“Codex自动化对话框”，官方Start Plan GLM-5.3，唯一E仪器/host/build/device owner。Root独占D协调/current。先有效AGENTS/current页首、docs/coordination/tasks/100-149/2026-10-24-task111-root-review.md、107 Root报告B小节和下面直接入口，已加载未变复用，不扫全历史。

111完整窄旅程原stdout OK1/12.657s、四阶段source/adoption/filter/reopen和完整恢复Root接受，旧FAIL不改绿，不重复此旅程。下一已有授权L1：107 Store8df4f192/test2a2493f8真实PreferenceDataStoreFactory三scope关闭重建/保存删除保留对照1/1已充分，勿再跑。当前FilterSheet.kt（ui/screen/shelf直接目录）131–194已提供动态视图姓名输入/保存/套用/删除；ShelfRoute498–511接线saveCurrentFilter/applySavedFilter/deleteSavedFilter；ShelfViewModel247–294保存快照和整体替换语义。旧workbuddy-r3-final-integration的UI未定位为历史PARTIAL，不能当现在未实现。只核当前调用及直接近期证据，若找到同输入已充分真实UI证据，引用并停止重复执行，交Root重排；否则继续本批真实缺口。

目标：在真实ShelfRoute/FilterSheet+生产VM/Store链，以本批中性数据配置至少两项相互独立筛选（从当前实际可操作维度选，例如格式+分类/标签；UI实际配置，不用直接VM/Store.save假造UI成功），命名保存本批唯一目标视图；改变/清除当前筛选，再真实点击该保存视图套用，核筛选组合整体恢复及正反对照书籍可见性。准备另一独立本批视图作删除保留对照，按该目标行内删除，重开筛选面板核目标缺席/对照仍在；可只读核真实Store的非空结果，但不能代替UI断言。核书单/分类/标签等原实体关系前后不被“保存动态视图”改写，说明动态快照与静态书单区别。保存→关闭/重开面板→套用即可，本批不追加进程kill/restart/设备重启耐久性矩阵，107真实文件重建已充分。成功标准为正向断言，不是无崩溃。UI控件定位使用实际源码和既有辅助，不猜页面或吞异常；两视图/本批书及关系run绑定，行内删除精准到本批目标，勿点不确定的“删除”。

可写仅新独占app/src/androidTest/java/com/creationreadingassistant/acceptance/ShelfSavedViewsJourneyDeviceTest.kt（先核初始缺席；若已有直接可复用仪器，先证其归属并只做必要本批hunk）及E:/creation-reading-assistant/task112-saved-views-ui-v1下必要host/fixture/日志/报告。111的MaterialAdoptionJourneyDeviceTest.kt b62225e5…冻结不改；不改生产FilterSheet/ShelfRoute/VM/Store/数据层/公共配置/Manifest/公共runner，不新增框架。准入前若确证生产缺陷，给Root具体证据并停止依赖设备，不猜修产品；设备失败立即停业务完整取证恢复，不批内修码再试。仪器编译错误在入场前由同owner修好。当前无独立有价值的第二任务，共用test APK/host/设备必须单owner串行。

唯一workspace E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android；Gradle home D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。产品6a6c65af30f8f7744e1ac666275ccb2b92a26a818a2b79cf84266cf38ce50ae4(52763781B)已110/preserved/app-debug-6a6c65af-run.apk，保持不改不重建不复制。当前test717530a23c54baf482a2f7c0fbdb47a3266ea1766944e6de15e95387a3ae8b67(10588807B)已111/preserved/app-debug-androidTest-717530a2-run.apk，覆盖前核同只引用。只必要test编译/assembleDebugAndroidTest；不重复107JVM/111设备/旧充分结果。新实际执行test先找同SHA保全，缺才必要单份约11MiB另列。产品输出/引用及测试APK实际SHA和仪器源码与fixture输入冻结绑定。

输出E:/creation-reading-assistant/task112-saved-views-ui-v1，差量4MiB/必要设备原始8MiB/testbuild预计256MiB，新实际test必要单份约11MiB另列、产品复制0；复制前核可用空间。不复制工程/build/.gradle/.kotlin/node_modules/Gradlehome/cache/历史证据/APK对，不硬链接cache，不删除移动压缩迁移存量。最小patch/新源码/前后SHA、原构建日志exit和仪器stdout/必要阶段JSON、REPORT/ready/完整恢复及实际字节保留原因；build增量未测如实写。

复用已核111 host2434cbe0b653b9b7c337e91d2598b1e7d7db598590d14a72cf249e3df4f83381(19115B)及既有保护恢复能力，仅本批必要绑定。新任务/新runId T112V1-YYYYMMDD-HHMMSS（新仪器应明确接受此格式），冻结前核实际Kotlin准入条件、host am craRunId、实际测试class/method selector与预期状态base class、结果目录与collect一致，保存一条该实际绑定离线核验，禁止108前缀失配或104selector判读失配重演，不造解析矩阵/通用工具项目。若为最小兼容复用保留T106A-前缀，必须明确T106A-T112V1-…并在本批输入统一核实，不能混用两个ID；旧111及更早inputs/attempt/FAIL冻结。核fresh进程/锁/owner，不重复派发或并发写共同环境。

设备仅项目MuMu1；ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe，每次fresh adb devices/serial/boot/owner/前台/包/night/预算，命令全部-s。本批一次主run含入场失败，业务1800秒+至少1200秒恢复预留，不够就停止依赖业务并恢复。失败完整取证恢复后停，不批内修码再试、不重算attempt。禁止关闭/重启/杀VM/共享ADB，结束运行；只可关闭已授权游戏。本批新中性书与筛选数据可通过生产DAO创建，但必须先记录fixture层级，不冒充系统导入/阅读全过程，不访问正式/旧测试库。

原71私有/820外部/DB-WAL-SHM/原宿主test代码完整数据inventory/userState/strict night=no保护合同不放宽；先原基线后核恢复，不仅看summary。collect失败/唯一证据未保全/归属不明保留依赖资产BLOCKED，独立安全恢复继续，业务/取证/清理/恢复错误分列不吞错不静默retry。仅本批初始缺席/runURI绑定/E原字节sizeSHA归属与当前设备同字节单资产可精确清理，后核缺席；未知兄弟后代不递归，探测失败不当不存在。不要复用108 complete_recovery.py吞探测失败的缺席探针，不为取证卸载丢唯一证据。结束原product缺席、原test恢复、原数据/状态/settings/VM运行完整取证。

严禁任何Claude/Cowork/Claude-Code/DSH或.codex/memories读写含自动记忆，禁止任何vault/记忆/项目页维护，不写D协调/current、不Git暂存提交pushPR发布、不D产品回灌、桌面/正式库/HOLD/云部署/实体手机/关闭其他进程/存量清理/创建自动化或外部发消息。所有权/输入/恢复不清停依赖工作，必要恢复优先。完成或失败均完整恢复并REPORT交Root，不自行下批，不把本批等同全产品完成，最终单独一行：这是任务112-v1。
````

## 2026-10-24 14:00 系统繁忙后的有限续接1

原GLM-5.3已停止，UI提示系统繁忙且自动重试达到最大次数，不是已证额度耗尽。Root只读核无本批wrapper/host进程，仅原Gradle daemon64784；attempt/inputs/budget/REPORT/ready尚缺席。仪器f8231c98ae8418f594d5e6293d082f39e1db00c03c7c2a0db7a7e6b538f865bc（24815B）；01编译原日志exit1，末次源码小修尚未新编译。无设备入场证据，不重算attempt。

````text
继续112-v1原半成品，不重开任务/目录或工程、不重复前段源码研究。你刚因“系统繁忙，自动重试已达最大次数”终止，这不是已证额度耗尽；本次保持官方Start Plan GLM-5.3，有限续接一次。Root未改你的源码：当前ShelfSavedViewsJourneyDeviceTest.kt f8231c98ae8418f594d5e6293d082f39e1db00c03c7c2a0db7a7e6b538f865bc(24815B)，原logs/01-compile-androidtest.log保留BUILD FAILED/EXIT_CODE=1。你末次已补getOrNull import及相关语义匹配修正，先核当前半成品再做必要定向编译；新日志用新序号，不能覆盖唯一失败原件。Root只读核attempt/inputs/budget/REPORT/ready缺席、无task112 host/wrapper进程，仅旧Gradle daemon，不是新设备许可或later状态免核。依旧先fresh owner/锁/输入，编译闭环后按原完整112一次新run及恢复合同完成，不重算attempt，不改变产品。全部原112目标、所有权、预算、保护/禁止私库与Git/存量边界不变，不重复107/111充分验证。若本次仍系统繁忙且无新工具进展则停追加请求，保留半成品供Root判断，勿自行改账号/购买/无关提供方或重启服务。完成或失败完整恢复后交REPORT/ready；最后单独一行：这是任务112-v1。
````
