# TXT 重进恢复 v2 返修：布局等待真正接入恢复协程

- 任务编号/版本：`CRA-TXT-REENTRY-FIX-v2`（v1 返修）；2026-10-05 指定，实际执行 2026-10-05T10:41–11:30+08:00，应用 ZCode / GLM（account:zai-start-plan / GLM-5.3-Flash）。未操作设备/ADB。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工）；暂存区前后 0 条。输入 = [v1 候选](<E:/creation-reading-assistant/txt-reentry-fix-v1/android>) 及其 [source-sha256.json](<E:/creation-reading-assistant/txt-reentry-fix-v1/freeze/source-sha256.json>)（979 项复制后逐文件实算 979/979 匹配，零漂移）；旧 978 合流基座仍为补丁基准。同编号任务占用核查：`txt-reentry-fix-v2` 此前不存在。
- 所有权与改动：写入仅 `E:\creation-reading-assistant\txt-reentry-fix-v2\`（android 副本、payload、freeze、test-results、scripts、execution.json）与本唯一报告。D 产品源码/公共配置/EPUB/Markdown 行为/其他窗口产物零改动；不 commit/push/reset/checkout；不触模拟器/正式书库；Gradle home 3.1G 本任务私有。
- 环境：Windows / Git Bash / Python 3.13；JDK 17.0.14；Gradle 8.9 `--offline --no-daemon`；仅定向 JVM。

## 执行与结果

### P1 修复：等待/重试真正接入

Codex 核查 P1 成立：v1 的 `plainRestoreTarget(...) ?: return@LaunchedEffect` 在布局未就绪时直接结束协程，布局状态不是 effect key、体内无等待，pending 可能永久 true；v1 测试只分别调用纯函数，未覆盖实际协程。

v2 修复（仍限原两生产文件）：
1. **ReaderScrollPosition.kt**（+plainRestoreSettled 纯确认函数）：滚动执行后「首个可见项 == 目标单元且列表非空」才算落位——把 v1 缺失的"事后确认"拆为可 JVM 回归的纯函数。
2. **ReaderProgressEffects.kt**（恢复循环重写，[补丁](<E:/creation-reading-assistant/txt-reentry-fix-v2/freeze/vs-978_ReaderProgressEffects.kt.patch>)）：非零目标进入**同一次恢复协程内**的「`snapshotFlow` 观察真实布局（totalItemsCount>0 且首项命中 units）→ `filter{非空}` 挂起等待 → scrollToItem → `plainRestoreSettled` 落位确认」循环：
   - **等待接入**：未就绪时协程挂起在 snapshotFlow/first 上（不忙等、无固定延时、不依赖偶然重组）；
   - **等待期保护**：pending 保持 true，TXT 初始 0 位置防抖/退出保存被 `canPersistNormalReadingProgress` 持续拦截，已保存非零进度不会被覆盖；
   - **确认后释放**：`plainRestoreSettled` 通过才锚定并置 pending=false；确认失败（列表在滚动与布局提交之间又被清空）不释放，回到 snapshotFlow 重试；
   - **取消安全**：effect key（书/模式/pending）失效即取消协程，挂起点被取消后不再执行，绝不用旧恢复目标操作新列表；
   - 0 目标（新书/损坏 locator）立即放行书首，不进等待循环。

### 验证（[green.log](<E:/creation-reading-assistant/txt-reentry-fix-v2/test-results/green.log>) EXIT=0）

定向 7 套件 **36 tests / 0 failures / 0 errors / 0 skipped**——v1 原 29 项全部保留通过（TxtReentryRestoreTest 7 + 邻接 22），新增动态回归 [TxtReentryRestoreFlowTest](<E:/creation-reading-assistant/txt-reentry-fix-v2/android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/TxtReentryRestoreFlowTest.kt>) 7/7：
①未就绪→就绪**同一次协程**继续并完成（同一 runRestoreLoop 调用内跨帧，非两次纯函数调用）；②等待帧期间滚动次数=0 且退出保存被拦截（2553 不会被 0 覆盖）；③恢复完成后正常保存且读出目标单元起点（≠0）；④落位确认失败保持 pending 并重试直到落位（两次滚动尝试）；⑤切书/切模式取消旧协程——取消前 0 滚动、取消后 0 滚动、剩余帧未被消费（旧目标不串用新列表）；⑥0 目标直通；⑦落位确认要求精确目标单元。harness 与生产循环逐语义同构（同一裁决/确认/释放次序），帧确定性预置，无忙等/固定延时。

**根因表述修正**：v1 报告中"未测量时 scrollToItem 静默忽略"按 Codex 要求降级为**待验证假设**——本 v2 不再依赖该论断成立：无论 scrollToItem 在未测量列表上的真实行为如何（忽略、clamp 或落位后又被清），等待-确认循环都在「落位经真实列表状态确认」之前不释放 pending、不恢复保存，缺陷路径被结构封死。真实轨迹仍以设备 J1 回归为准。

### 冻结交付

- 补丁（相对 978 基座，可直接应用）：[vs-978_ReaderScrollPosition.kt.patch](<E:/creation-reading-assistant/txt-reentry-fix-v2/freeze/vs-978_ReaderScrollPosition.kt.patch>)（75L）、[vs-978_ReaderProgressEffects.kt.patch](<E:/creation-reading-assistant/txt-reentry-fix-v2/freeze/vs-978_ReaderProgressEffects.kt.patch>)（104L）+ 两测试新文件（v1 的 TxtReentryRestoreTest 原样包含）。
- 完整源码指纹 [source-sha256.json](<E:/creation-reading-assistant/txt-reentry-fix-v2/freeze/source-sha256.json>)（980 项 = 979 v1 + 新 FlowTest）；[payload-manifest.json](<E:/creation-reading-assistant/txt-reentry-fix-v2/freeze/payload-manifest.json>)（4 文件 base978/v1/v2 三方 SHA）；`payload/android/`；原始 XML×7 + 日志 + 退出码在 `freeze/evidence/`。

## 恢复与副作用

D 仓库/Git/输入目录只读未动（收工 HEAD/暂存区复核一致）；无设备操作；测试数据仅在本任务 E 目录；候选树构建产物已清理（一次测试 JVM 残留锁导致 output.bin 删除失败，已终止残留 JVM 后清理，未影响任何结果文件）。

## 未覆盖项（后续 MuMu 待验）

- Compose 真实 LazyListState 布局提交、snapshotFlow 在生产帧上的发射时序无法在 JVM 复现——「重进恢复到原偏移所在单元 + DB 不再被清零」须由唯一设备 owner 冻结新 APK 做 J1 回归。
- J1 负向谓词按 Codex 要求应绑定**原偏移对应的阅读单元 + 范围 + UI 可见标记**（TXT 按单元起点恢复有自然偏移差，不逐字相等、也不只查非零）。
- 设备上「scrollToItem 在未测量列表的真实行为」轨迹（logcat/布局 dump）仍未实测；本修复不再以该假设为前提，但其历史定位价值待设备证据补充。
- J5 真实中断已独立核查通过，本轮不重做；v1 的 29 绿证据保留复用（受影响套件已在本批 36 项内全量重跑）。

## 交付状态与下一步

v2 payload（4 文件）+ 三方 SHA manifest + 动态/静态回归 36/36 绿交付完毕。本轮到此停止：不回灌 D、不提交、不冻结 APK。Codex 核查通过后，由唯一设备 owner 冻结新统一 APK 并执行 J1 回归与受影响邻接验收。
