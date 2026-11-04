# TXT 重进回开头的最小修复（统一候选恢复链）

- 任务编号/版本：`CRA-TXT-REENTRY-FIX-v1`；2026-10-05 指定，实际执行 2026-10-05T09:33–10:25+08:00，应用 ZCode / GLM（account:zai-start-plan / GLM-5.3-Flash）。角色=TXT 恢复链最小修复；未操作设备/ADB。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工）；暂存区前后 0 条。源码基座只读 [unified-candidate-integration-v1/android](<E:/creation-reading-assistant/unified-candidate-integration-v1/android>)；**Codex 978 文件指纹复制后逐文件实算 978/978 匹配，零漂移**。设备失败原始证据 [unified-mumu-accept-v1/evidence](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/>)（J1 两轮：退出保存 870/2553 落库正确，重进 UI=段落 1、DB 被改写 0）。
- 所有权与改动：写入仅 `E:\creation-reading-assistant\txt-reentry-fix-v1\`（android 副本、payload、freeze、test-results、scripts、execution.json）与本唯一报告。D 产品源码/原仓库/current/TODO/其他窗口产物零改动；不 commit/push/reset/checkout；未触碰模拟器/正式书库；Gradle home（3.1G）为本任务私有（初次复制不完整导致两次构建失败，整目录重拷后成功，与任何并行窗口零共享）。
- 环境：Windows / Git Bash / Python 3.13；JDK 17.0.14；Gradle 8.9 `--offline --no-daemon`；仅定向 JVM，无联合 APK 构建。

## 执行与结果

### 根因（已查证，"恢复消费链"方向的证实与收窄）

调用链逐段核实：生产 DB 进度 `{"offset":2553,"locator_v2":{offset:2553,ci:0,co:2553}}` → `LegadoProgressMarker.decode`=null（无 legado_position_v1 键）→ `LocatorCodec.locatorFromProgress` 读 v2 顶层 offset → `parseStoredAbsoluteOffset`=2553 → `ReaderLoadedContent.Text.initialAbsoluteOffset`=2553 → `ReaderScreen.savedPlainOffset`=2553。**加载侧与保存侧全部正确**——与设备证据（退出保存 2553 落库成功）互相印证。

缺陷锁定在恢复应用 effect（[ReaderProgressEffects.kt](<E:/creation-reading-assistant/txt-reentry-fix-v1/freeze/vs-base_ReaderProgressEffects.kt.patch>) 原 L443-477）：`pendingInitialPositionState=true && readingUnits 非空` 即执行 `plainListState.scrollToItem(...)` 并**无条件置 pending=false**。设备时序窗口：LazyColumn 尚未完成首次测量（`totalItemsCount==0`）时 scrollToItem 被静默忽略（列表停在 (0,0)），pending 却被释放；随后任何退出/暂停落库（`ReaderPlatformEffects` ON_PAUSE/ON_STOP/onDispose → `persistOnLeave`）经 `currentPlainListOffset` 读到 0，`mergeReaderProgress` 无条件采纳 incoming，把 2553 覆盖为 0。该模型同时解释 UI=段落 1、DB=0、保存侧正确三个事实。

### 修复（2 个生产文件 + 1 个新测试，补丁可应用于 978 基座）

1. **ReaderScrollPosition.kt**（+57 行）：新增纯函数 `plainRestoreTarget(...)` 统一裁决恢复目标——非零目标 + `layoutReady=false` → 返回 null（不滚动、不清 pending；pending=true 维持 effect 下一帧重试）；`layoutReady` 生产判据 = `layoutInfo.totalItemsCount > 0 && firstVisibleItemIndex in units.indices`；0 目标（新书/损坏 locator）立即放行书首不卡 pending；越界 offset clamp 到 totalChars 所在末单元；仅 percent 时按 totalChars 换算；分页在途偏移优先。
2. **ReaderProgressEffects.kt**（改 39 行）：恢复 effect 消费该裁决，`?: return@LaunchedEffect` 延迟重试；滚动、锚定、清 pending 仅在裁决通过后执行。
3. **TxtReentryRestoreTest.kt**（新 7 用例）：①非零 2553 + 就绪 → 解析到包含该偏移的单元且 targetOffset=2553；②布局未就绪 → null + pending 门控拦截退出保存（契约联动断言）；③恢复后退出保存读出目标单元 charStart（≠0）；④0 进度新书放行且不被阻塞；⑤越界 clamp / 仅 percent / totalChars=0 安全；⑥分页在途偏移优先；⑦空 units 不崩溃。

### 验证（[green-run.log](<E:/creation-reading-assistant/txt-reentry-fix-v1/test-results/red-green.log>) EXIT=0）

定向 6 套件 **29 tests / 0 failures / 0 errors / 0 skipped**：新增 TxtReentryRestoreTest 7/7 + 邻接回归 ReaderScrollPositionTest 5、ReaderProgressActionsPersistTest 5、TxtScrollChapterTargetTest 7、EpubRepositorySaveProgressLocatorTest 3、MarkdownVisibleSourceOffsetTest 2（MD canonical 保存、EPUB S12-①、TXT 实时列表保存、章导航均未被本次修复破坏）。红证据：[red-evidence-run.txt](<E:/creation-reading-assistant/txt-reentry-fix-v1/freeze/evidence/red-evidence-run.txt>)（旧决策路径程序化复现"DB 被覆盖为 0"违反新契约）+ 新测试在基座上不可编译（`plainRestoreTarget` 不存在）。424 项未变证据按输入不变条件复用（生产改动仅 2 文件，本批受影响套件已全量重跑）。

## 原始证据

`E:\creation-reading-assistant\txt-reentry-fix-v1\`：`execution.json`（输入指纹/根因/修复/验证/收工核对）；`freeze/`（`vs-base_*.patch` ×2 生产 hunk 补丁、`source-sha256.json` 979 项完整输出冻结、`payload-manifest.json`、`evidence/green-*` ×8、`evidence/red-evidence-run.txt`）；`payload/android/`（3 文件）；`test-results/`；`scripts/`（apply_fix.py、red_evidence.py 可复跑）。

## 恢复与副作用

D 仓库/Git/基座候选只读未动（收工 HEAD/暂存区复核一致）；无设备操作；测试数据仅在本任务 E 目录；候选树内构建产物已清理。

## 未覆盖项

- **设备回归未做**（本任务无 ADB 权限）：Compose 布局竞态在 JVM 以 `layoutReady` 参数表达，"重进恢复到 ¶51 区域且 DB 不再被清零"须由下一设备 owner 在冻结基座上绑定本 payload 验证 J1；JVM 绿不等于设备已修复。
- `scrollToItem` 未逐项检查 scrollOffset 精细定位（恢复到单元起点，与设备既有"单元级恢复"口径一致；J2 的 35 字符同屏偏差同口径）。
- 未改 EPUB/Markdown 行为、DB schema、同步/恢复实现、公共构建配置；ON_PAUSE 路径未单独造测（与 onDispose 共用同一 persistOnLeave 门控）。

## 交付状态与下一步

payload 3 文件（2 生产 + 1 测试）+ 完整 979 项源码冻结交付，补丁可直接应用到 978 已核基座。本轮到此停止；设备验收由后续唯一 owner 接手：绑定本 payload 重建双 APK → J1 回归（滚动→退出→重进→段落 50+ 区域且 DB 保持非零）→ 复跑 J2/J4 邻接确认。
