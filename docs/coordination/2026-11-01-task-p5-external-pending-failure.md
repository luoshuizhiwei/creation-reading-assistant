# 任务P5：清缓存后搜索命中→真实加载失败→Back回搜索（限定加签执行单）

日期：2026-11-01。授权链：Root裁决`2026-11-01-inventory-drift-root-adjudication.md`§6限定加签 + 用户R2确认消息（"D1/P5仍遵循既有裁决的条件"）。**设备运行前置**：8个ui-*.xml闸门闭合（清理授权未下，当前HOLD）；本文件先落离线准备（§6.1允许）。

## 1. 合同要点（裁决§6逐条落位）

- **范围**：一个独立测试侧任务，单class单method、expectedCount=1、独立fresh批、独立runId（`T210P5-`前缀）、166完整恢复。不涉及正式书库、不SQL伪造失败、不新增生产功能。
- **目标场景**：测试书清本地缓存后，搜索结果既有临时查看入口遇到真实加载失败，Back回原搜索页，查询/结果上下文和原普通阅读位置保留。
- **前置**：R2 test包门（已闭合）+ external_added闸门（待清理授权）均关；本P5执行单落盘后才许一次main/一次instrument。

## 2. 新测试身份

| 项 | 值 |
|---|---|
| 文件 | `candidate/android/app/src/androidTest/java/com/creationreadingassistant/acceptance/ReaderExternalPendingFailureProductionTest.kt`（新增，唯一所有者=本任务） |
| class#method | `com.creationreadingassistant.acceptance.ReaderExternalPendingFailureProductionTest#clearCacheThenSearchHit_realFailure_backToSearch` |
| expectedCount | 1 |
| runner209 spec | 类级spec（testMethod=null+expectedCount=1+knownMethods钉扎该方法；argv[10]=裸类名，同209批B v2判例） |
| runId前缀 | `T210P5-`（正则`^T210P5-\d{8}-\d{6}$`，独立runId不继承旧scope） |
| 恢复 | 166完整恢复（recover-only收口型，同209批C模式），不继承旧runId/scope |

## 3. 产品缝只读核验（源码事实，2026-11-01实读）

1. **清缓存产品行为**（`BookRepository.kt:281 clearBookCache`）：残留预检→删三处正文（`books/<id>/`、`books/epub/<id>.epub`、`books/epub/<id>/`）→清DB指针（`content_status="missing"`、`local_uri=null`、`local_content_path=null`、bookContent `reader_preview/epub_json=null`、bookFile软删`deleted_at`）。**该函数无任何搜索索引清理调用**（索引表不清）。
2. **清缓存UI入口**：书架顶栏"更多"（MoreHoriz，contentDescription="更多"，`ShelfScreen.kt:241`）→"批量选择"（`ShelfScreen.kt:262`）→BookTile点击切换选中（selectionMode分支`onToggleSelected`，`ShelfScreen.kt:556`）→BatchSheet"清缓存"胶囊（`BatchSheet.kt:149`，onClick直发`ShelfViewModel.clearCacheForBooks`→`ShelfBookActions.clearCacheForBooks`→repository，无确认对话框）→书架Snackbar结果消息。
3. **搜索命中→临时查阅**（`SearchScreen.kt:469-483`）：正文命中卡整体onClick→`resolvePreciseOffset(hit)`→`readerTemporaryRouteForSource(bookId, legacyOffset,…)`→navigate（临时LIFO路由，返回行为=既有临时返回栈）。
4. **打开失败UX**（`ReaderViewModel.kt:767-771`）：open失败→`uiState.errorMessage = error.message ?: "打开失败"`；`ReaderScreen.kt:226` `val error = runtimeError ?: documentUiState.errorMessage` 渲染错误UI。**实际用户可见文案与是否自动pop回搜索页为待运行假设，测试按观察分支记录，不预设字符串。**

**以上1-4为源码级事实；场景级命题（索引留存、UX形态、Back落点）一律待设备运行实证，不写成已验。**

## 4. 测试场景步骤（§6.2单场景强断言）

1. **夹具**：唯一runId测试TXT（book A，含唯一非空marker）；scenario启动。
2. **普通阅读基线**：打开book A→真实阅读（marker可见）→普通Back退出（落S0位置）→快照progress行+content_status= present。
3. **真实清缓存**：批量选择→选book A→清缓存→**断言DB真实变化**：`content_status=="missing"`且`local_uri==null`；快照对比仅许可字段变化（正文指针三字段+bookFile.deleted_at+updated_at）；记录实际Snackbar文本到回执。
4. **搜索命中留存**：书架→搜索图标→输入唯一marker→**断言命中卡出现**（索引留存——若未命中，如实记`P5_PREMISE_NOT_REACHED`，不改产品门/DB迎合）；记录查询词与结果身份。
5. **真实失败UX正向确认**：点击命中卡→waitUntil分支观察：(a) reader壳呈现（chrome/scrubber任一真标志）或错误UI可见，或(b) 自动回搜索页（若产品自动pop）。**记录实际可见文案集合到回执**（语义树文本遍历，不预设）；断言正文marker**未渲染**（真实不可读）。
6. **Back回搜索**：失败未提交路径按生产Back→**正向断言**：`全局搜索`标题可见+输入框EditableText==原查询词+book A命中卡仍在列表；普通阅读progress行关键位置字段与步骤2快照一致（许可白名单外零变化）。
7. **恢复**：166完整恢复+独立保护核验另列（PASS/FAIL均执行）。

## 5. 输入与编译

- 复用宿主（host209链+R2门适配层`host_r2_adapter.apply_r2_gate`）；测试APK变更须实核新pins/签名/runner/target/方法并冻结后才运行。
- 输入前SHA：编译后记录新test APK SHA/bytes于批REPORT；产品APK不变（c396ebe1）。
- 预算（裁决§6.2）：实现/编译20-30分钟（本文件+测试源码离线部分已完成），设备业务约20分钟+恢复预留20分钟。

## 6. 停止条件

- 设备业务失败保留原红、停止该批，不延长timeout/替换断言盲循环；需新修复由协调者给出改变方法后的具体小合同。
- 索引未留存（步骤4未命中）→`P5_PREMISE_NOT_REACHED`如实上报，不构造假命中。
