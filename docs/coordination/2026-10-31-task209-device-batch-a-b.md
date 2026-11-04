# 209设备批合同（批A图测试7项+批B生产UI 1项）——待用户授权，未派发

2026-10-31接任协调者编制。状态：**DRAFT_AWAITING_EXPLICIT_DEVICE_AUTHORIZATION**。本稿只准备，不执行；用户明确授权209设备批后，由接任协调者把该授权记入current/state并绑定唯一owner即开工，无需原Root确认（208先例）。D1接缝**不在本批**——如用户裁决新增，另立任务合同。

## 目标

在新APK（207-r4候选）上以fresh全套机制实际运行两个测试类，把209矩阵B/C级从NOT_RUN推进到真实结果：
- **批A**：`com.creationreadingassistant.acceptance.ReaderTemporaryNavigationStackTest` **整类7方法**，expectedCount=7（class级argv，无#method；spec钉扎known_methods=真实源7方法名单）。
- **批B**：`com.creationreadingassistant.acceptance.ReaderTemporaryNavigationProductionTest` 单方法 `productionChain_readerInternalInspect_lifoReturn_realExitBackToShelf`，expectedCount=1。
- 每批独立runId/scope/一次main+一次instrument+完整166恢复；两批可同owner连续执行，机制逐批完整不省略。

## 输入（开工实算，漂移停）

candidate `E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android`：宿主APK `c396ebe1…`/53899310B、测试APK `e4b7b70b…`/10724766B（与208实际安装对同源）；ReaderRoute `5368065b…`、203冻结 `1f289540…`、图测试 `d2a08684…`、生产测试 `811cae6d…`、fixture `0aea2c5e…`；runner209/host209以当轮实算SHA入inputs（28role=26基+runner209Source+runner209SpecSource）。机制只读：entry_lifo_192/host192/collector192/161/host165/166/184r2/179r3（冻结pin）。

## 执行者准备（离线，授权后先做）

1. **entry_209.py派生**（3行差量自entry_lifo_192：`EW.bind(host=host209, io_factory=io_factory)`+`host_mod.main(argv)`+finally还原）——host209与host192共享同一H/HOST158冻结对象，bind语义等价；派生后先跑184-r2式离线bind/restore身份核验。
2. inputs×2：批A/批B各一份209 schema绑定文档（fresh serial/bootId/owner/preflight真实证据+28role实算），批B spec=testMethod单方法+expectedCount=1。
3. 每批执行：`entry_209.run_main(['--run-id',…,'--inputs',…,'--run-scope','E:/creation-reading-assistant/task192-reader-internal-lifo-host-offline-v1/attempt-<runId>','--serial',…,'--boot-id',…,'--approve-device','--approve-quiesce'])`。

## 执行与恢复（每批）

fresh179r3完整身份+26+2role绑定（`verify_inputs_209`全过）→一次main+一次instrument→**166完整恢复**（冻结host166.run_recovery同源链，stop/uninstall按冻结合同次数）→独立postflight（产品缺席/三根/原test 0d18/inventory d6ac3c5f/八设置focus/71-820零差异/最终identity）。业务失败KEEP，不改断言/超时/fixture/源码，不重试；恢复失败KEEP并保全原证。

## 判定与交付

每批按C2四分支分开裁决业务与恢复；**图测试7项任何失败原样保留并进C3式定位（不迎合）**；批B生产链失败同样保留。交付各批`E:/creation-reading-assistant/task209-navigation-coverage-v1/device-A|B/REPORT.md`+ready+原rc/log/XML+恢复原证；raw≤512KiB、保护capture单列。V0矩阵受影响行随批更新。

## 权限与停止

仅本批设备/ADB/VM/恢复授权（原instance1官方单次launch如离线、在线不重启）；无Gradle/产品或测试源码修改/第二业务/Git/删移/记忆。用户stop/Esc停新增输入；已开始恢复按166合同收尾。两批合计预算约90分钟（每批业务30+恢复20），恢复不被预算截断。

## 待决（不在本批）

- D1接缝（真机会话内失败场景）——用户产品范围裁决；
- P5清缓存外部失败场景——如用户明确加签，作为批C并入（同机制），默认不含。

这是209设备批合同（待授权版）。
