# 111-v1 素材旅程测试导航修正

编号111-v1；2026-10-24 13:20准备；官方Start Plan GLM-5.3；同owner完成最小仪器修复、test构建、一次设备验收与恢复报告。共享仪器/host/设备依赖须串行，当前无可独立推进且有价值的额外实现，不制造并行审查。

````text
执行111-v1，继续原素材合并→采用→已采用筛选→返回重开的未闭合旅程。你仍是ZCode原恢复项目creation-reading-assistant-recovery-20260919、codex/recover-worktree-20260920、既有“Codex自动化对话框”的唯一E源码/host/build/device owner，官方Start Plan GLM-5.3不变。Root独占D协调/current。先读有效AGENTS、current页首、docs/coordination/tasks/100-149/2026-10-24-task110-root-review.md及110 REPORT/ready/原FAIL与直接相关仪器和生产导航源码；不全历史扫描，不读写任何私库/记忆。

Root已核110零高度产品修复及完整恢复。110单次run T106A-T110V1-20261024-130644按钮198x70/visible70、合并成功创建card且两原条目archived，随后:290“新素材卡必须出现在列表”失败；生产InspirationRoute.kt MergeToMaterialCard成功回调本来就page=Detail(cardId)，实际detailOpen=true/listVisible=false。原仪器awaitMergedCard后直接cardPresent，再payload多字段断言、openCard，错误地假定仍在列表。110整例FAIL冻结不改绿，来源细字段断言及采用/筛选/重开尚未执行，不能冒称全链通过。

本批所有权只限E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/src/androidTest/java/com/creationreadingassistant/acceptance/MaterialAdoptionJourneyDeviceTest.kt与必要host绑定hunk。仪器当前f72bda181a55f141201a2c7e018a3ab3b0247b17b4eb727ad648aeb2f8005064（35493B）。先核产品实际导航与现有pressBack/list判定，最小确定性修正：合并ID产生后明确断言已到详情，通过真实返回动作回列表并等列表就绪，再保留原cardPresent和全部payload/source归档断言、原openCard及后续采用/筛选/重开。不要条件式兜底猜页面，不移除/放宽原数据和旅程断言，不用DAO写状态绕过UI，不改生产导航/布局/VM/Repository。原新正高度断言仍保留。若实际源码表明该具体顺序不可行，停在离线定位并如实报告，勿猜改产品。

产品InspirationList当前8b3056b5ea8aaad440b827485976e572c8c62e753048a74e65f19498ab310cca（28812B）。产品APK6a6c65af30f8f7744e1ac666275ccb2b92a26a818a2b79cf84266cf38ce50ae4（52763781B）在110/preserved/app-debug-6a6c65af-run.apk，保持不改不重建不复制；当前test349cf77f2ed89fa8d61e028f0829e545def1f825fe29491aa53d431cb05ac099（10588433B）已110/preserved/app-debug-androidTest-349cf77f-run.apk，覆盖前核同引用即可。仅必要test编译/assembleDebugAndroidTest，锁内由你闭环必要编译修正；不重复107生产链3/3、B真实DataStore1/1/mainKSPHilt、110 main/产品构建，不before设备，不扩矩阵。构建若触发up-to-date main任务如实记录，勿主动重建产品。新实际test先查同SHA保全，缺才必要单份约11MiB另列，不默认复制APK对。

唯一工程task59；Gradle home D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。输出E:/creation-reading-assistant/task111-material-journey-navigation-v1，差量4MiB、必要设备原始8MiB、test build预计256MiB，新实际执行test必要单份约11MiB另列，产品复制0。复制前核可用空间及已有同SHA；不复制工程/build/.gradle/.kotlin/node_modules/Gradlehome/cache/历史证据，不硬链接cache，不删除移动压缩迁移存量。交付最小patch/变更源码/前后SHA、原日志exit/仪器stdout与必要原件、REPORT/ready、完整恢复及实际字节保留理由；build增量未测如实写。

host基于110 scripts/host110.py SHA7b69f56b7b507e38e66c0013f616b77098adf545f5f6fae50218b4ffb2f67871（19028B），仅必要目录/前缀/新test路径。新实际runId=T106A-T111V1-YYYYMMDD-HHMMSS，task111-v1新输入新attempt；冻结前从实际仪器KotlinRegex核具体id匹配，am craRunId/结果目录task106a-results-完整id/collect必须一致，一条实际离线接线输出，不造解析矩阵；110及旧inputs/budget/attempt/FAIL冻结不覆盖。先核有无已运行状态和相关进程/锁/owner，不抢并发写入。

设备仅项目MuMu1；ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe。fresh adb devices/serial/boot/owner/前台/包/night/预算，所有命令绑-s。本批一次主run含入场失败，业务上限1800秒+至少1200秒恢复预留。失败立即停业务、完整取证恢复，不批内改码重试、不重算attempt；必要恢复优先做完。禁止关闭/重启/杀VM/共享ADB，结束保持运行，只可关闭已授权游戏。本轮新中性TXT/摘录/来源/DAO fixture不冒充阅读摘录全流程，不碰正式或旧测试库。

原71私有/820外部、DB/WAL/SHM、原宿主/test代码与完整数据/inventory/userState、strict night=no保护合同不放宽。collect失败/唯一证据未保全/归属不明保留依赖资产BLOCKED，独立安全恢复继续；业务/取证/清理/恢复错误分列，不吞错不静默retry。精确清理仅本轮初始缺席+run/URI绑定+E原字节sizeSHA与当前设备字节核同单资产，后核缺席；未知兄弟后代不递归、探测失败不当不存在，不复用108 complete_recovery.py吞探测失败的缺席探针，不为取证卸载丢唯一证据。

禁止Claude/Cowork/Claude-Code/DSH或.codex/memories任何读写（含自动记忆），不做任何vault/记忆/项目页维护，不写D协调/current，不Git暂存提交pushPR发布、不D产品回灌、桌面/正式库/HOLD/云部署/实体手机/关闭其他进程/存量清理/创建自动化或外部发消息。完整恢复后REPORT交Root验收，未验收不自行开下一批；最终用户可见总结最下面单独一行：这是任务111-v1。
````
