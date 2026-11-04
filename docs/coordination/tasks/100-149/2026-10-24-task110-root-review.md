# 110-v1 Root核验与111方向

## 任务编号/版本、输入指纹

2026-10-24，110-v1。唯一run T106A-T110V1-20261024-130644。E输出：E:/creation-reading-assistant/task110-material-selection-bar-fix-v1。REPORT SHA256 db392c66fc86f11b2ee439392da56786fd4d6dfba449e4d7dd25f7e633dd72d1（6991B）；ready 38511db12bcccf00851262e19dc3cd0d0ae9a4f7fae36c3b9cff76551d34650c（5319B）。Root直接核源/保全APK和原始回执，不以执行者结论代验收。

## 所有权与改动

Root D协调/current；ZCode官方Start Plan GLM-5.3为E唯一源码/build/device owner。InspirationList.kt从f6fea57c变8b3056b5ea8aaad440b827485976e572c8c62e753048a74e65f19498ab310cca（28812B），单处LazyColumn改weight(1f)+fillMaxWidth。仪器从a2559532变f72bda181a55f141201a2c7e018a3ab3b0247b17b4eb727ad648aeb2f8005064（35493B），增加节点唯一及可见高度>0断言。未改VM/Repository/生产导航。

## 环境、执行与结果

唯一task59候选工程、既定JDK17/SDK/Gradle home/互斥锁复用。原日志main编译25s、产品assemble27s、test assemble23s均BUILD SUCCESSFUL/EXIT_CODE=0。实际产品6a6c65af30f8f7744e1ac666275ccb2b92a26a818a2b79cf84266cf38ce50ae4（52763781B）及test349cf77f2ed89fa8d61e028f0829e545def1f825fe29491aa53d431cb05ac099（10588433B）110/preserved原字节核同。

109原按钮198x0/visible0；110点击前198x70、bounds(829,1818)-(1027,1888)、visible70，正高度断言通过。立即观测selectionBar=false、snackbarMerged=true、detailOpen=true、listVisible=false。本批卡22c22fe1-3068-4a88-9e94-ab073ad0dd77为reviewing，两原条目archived，awaitMergedCard通过。接受布局可操作性修复及此范围真实合并结果。

整例仍FAIL：原stdout 1 test/1 failure、INSTRUMENTATION_CODE=-1，在MaterialAdoptionJourneyDeviceTest.kt:290“新素材卡必须出现在列表”。仪器27.461s，host业务151.509s，单attempt已耗。生产InspirationRoute.kt:299起，成功回调明确state.page=Detail(cardId)，与立即观测相符；测试未先返回列表。因此下一步仅校正测试导航。不能把“合并数据链全通”扩大为全部payload/source断言已通过：line290后的来源数量、locator/book/chapter/excerpt及mergedInto细查尚未执行，采用/筛选/重开NOT_RUN。旧106/108/109和本次FAIL全部保留。

## 原始证据

E110的logs/01-compile-main.log、02-assemble-debug.log、03-assemble-androidtest.log、20-instrument-stdout.log；evidence/device/04-run.json、summary.json；evidence/instrument/task106a-results-T106A-T110V1-20261024-130644中21/23/22/99原件。Root直接读取23立即状态及原stdout，并对生产导航源码交叉核实。6原件collect成功。

## 恢复与副作用

Root核02-protection-initial/final rc0，consumer ok=true，71私有/820外部、diffs=[]、db_groups_match=true。00-original-test-verified与06-original-test-restored的apkSha256/inventory/userState逐字段相同，原test0d18字节恢复。summary recoveryErrors=[]，Download初始179/新增[]，产品UNINSTALLED，blocked=[]，testPackageRestored=true。settings night=no，keepalive pid66432/serial attached/boot记录存在；manager android_started=false不单独解释为实例关闭。恢复接受，原业务FAIL不改绿。

Root实测目录66989697B，其中APK63352214B，其余3637483B。ready所记66977387B相差12310B为后续收尾文件时点差；不为此重跑。保全必要执行件，build增量未独立测量。不移除既有资产。

## 未覆盖项、交付状态与下一步

110产品布局修复及恢复接受，整条采用旅程未完成。方向自审：剩余目标仍是原素材合并/采用可靠性；现已发现确定测试导航缺口，无需新产品功能或泛审查。111只修仪器：确认自动详情→真实返回列表→原卡片与完整payload断言→原openCard/采用/筛选/重开。产品6a不变不重建，107充分JVM及before设备不重复。共享仪器/test APK/host/device由同owner闭环；当前没有可独立推进且证据价值足够的额外实现，保持串行，避免重复检查。新输入、新run一次attempt及完整恢复合同保持。完整派发见[111](2026-10-24-task111-material-journey-navigation-dispatch.md)。
