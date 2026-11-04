# TXT 重进恢复 v3 窄返修：共享生产控制流 + 落位等待 + 书尾 clamp

- 任务编号/版本：`CRA-TXT-REENTRY-FIX-v3`（TXT-v2 窄返修）；2026-10-05 指定，实际执行 2026-10-05T14:46–15:40+08:00，应用 ZCode / GLM（account:zai-start-plan / GLM-5.3-Flash）。未操作设备/ADB。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工）；暂存区前后 0 条。只读输入 = [v2 候选](<E:/creation-reading-assistant/txt-reentry-fix-v2/android>) 及其 [source-sha256.json](<E:/creation-reading-assistant/txt-reentry-fix-v2/freeze/source-sha256.json>)（980 项复制后逐文件实算 980/980 匹配，零漂移）；最终补丁以原 978 合流基座为应用基准。同编号任务占用核查：`txt-reentry-fix-v3` 此前不存在。
- 所有权与改动：写入仅 `E:\creation-reading-assistant\txt-reentry-fix-v3\`（android 副本、payload、freeze、test-results、scripts、execution.json）与本唯一报告。生产所有权仍限 ReaderProgressEffects.kt、ReaderScrollPosition.kt 及 TXT 恢复测试；D 源码/旧候选/公共配置/EPUB/Markdown/其他窗口产物零改动；不 commit/push/reset/checkout；Gradle home 3.1G 本任务私有（初次复制不完整致 wrapper 下载超时，整目录重拷后恢复）。
- 环境：Windows / Git Bash / Python 3.13；JDK 17.0.14；Gradle 8.9 `--offline --no-daemon`；仅定向 JVM。

## 执行与结果

### P1 修复（Codex 核查 txt-v2-prep 全部三点）

1. **共享生产控制流**：整条等待/滚动/确认循环收进生产 suspend 函数 `runPlainRestoreLoop(...)`（[ReaderScrollPosition.kt](<E:/creation-reading-assistant/txt-reentry-fix-v3/android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScrollPosition.kt>)）；恢复 effect 与动态测试**调用同一实现**（effect 接入点见 [vs-978_ReaderProgressEffects.kt.patch](<E:/creation-reading-assistant/txt-reentry-fix-v3/freeze/vs-978_ReaderProgressEffects.kt.patch>)）。v2 FlowTest 的"同构循环"复刻已删除。
2. **同状态不重滚**：落位确认失败后挂起等待**真正不同的布局快照**再重试——`StateFlow.first { it != before }`（StateFlow 会立即重放当前值，必须过滤基准快照，否则已就绪布局上毫无等待）；重试后更新基准。等待期间 pending 保持 true，0 位置保存持续被拦截；不可恢复的挂起由协程取消（effect key 失效）终止，无忙循环。
3. **书尾 clamp 合法落位**：`plainRestoreSettled` v3 明确定义——正常落位 = 首项==目标；书尾 clamp = 目标==末单元 && 首项<目标 && 末项==目标（目标在屏内被 clamp 推到视口下部）；目标完全不可见仍拒绝；未挂载拒绝。
4. **身份取消**：书籍/列表/模式身份即恢复 effect 的 key（readingUnits/savedPlainOffset/pagerEngineOn/bid 会话），key 失效取消协程；测试验证"相同 units、相同偏移下切书取消后零滚动、零释放、剩余布局帧不被旧协程消费"。
5. **未证实注释修正**：v1/v2 残留的"等下一帧重试"表述改为描述真实挂起等待语义；"scrollToItem 必然静默忽略"假设在 v2 已降级，v3 全部生产注释不再以它为前提。

### 生产接入说明

effect 内：`runPlainRestoreLoop(savedPlainOffset, savedPlainPercent, totalChars, readingUnits, pagedAbsOffset, scrollTo = plainListState::scrollToItem, settleSnapshot = layoutInfo 首末可见项+unitsEmpty, layoutChanges = snapshotFlow(布局三元组).conflate())`。返回 true 才锚定（LegadoMarkerSaveGuard）并释放 pending。`conflate` 保证布局风暴只取最新快照；`first { it != before }` 保证同状态挂起。

### 验证（[green.log](<E:/creation-reading-assistant/txt-reentry-fix-v3/test-results/green.log>) EXIT=0）

定向 7 套件 **36 tests / 0 failures / 0 errors / 0 skipped**——v1/v2 原 36 项证据所涉套件全部保留（TxtReentryRestoreTest 7 + 邻接 22 + FlowTest 重写 7）。新动态回归（[TxtReentryRestoreFlowTest](<E:/creation-reading-assistant/txt-reentry-fix-v3/android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/TxtReentryRestoreFlowTest.kt>)，全部调用生产 `runPlainRestoreLoop`）：①就绪但未落位时同状态恰好 1 次滚动（不重滚），布局真实变化后完成（共 2 次）；②书尾短项 clamp（首项 38/末项 39/目标 39）整流程确认释放；③等待中取消——0 滚动、0 释放、剩余帧不被旧协程消费；④恢复未完成时退出保存被门控拦截（2553 不被写 0）、完成后正常保存读出目标单元起点；⑤settled v3 边界五种；⑥未就绪 defer + pending 拦截；⑦0 目标直通。

### 冻结交付

- 补丁（相对 978 基座）：[vs-978_ReaderScrollPosition.kt.patch](<E:/creation-reading-assistant/txt-reentry-fix-v3/freeze/vs-978_ReaderScrollPosition.kt.patch>)（157L）、[vs-978_ReaderProgressEffects.kt.patch](<E:/creation-reading-assistant/txt-reentry-fix-v3/freeze/vs-978_ReaderProgressEffects.kt.patch>)（86L）+ 两测试新文件（v1 布局门测试原样、v2 FlowTest 被 v3 版替代）。
- 完整源码指纹 [source-sha256.json](<E:/creation-reading-assistant/txt-reentry-fix-v3/freeze/source-sha256.json>)（980 项）；[payload-manifest.json](<E:/creation-reading-assistant/txt-reentry-fix-v3/freeze/payload-manifest.json>)（4 文件 base978/v3 双方 SHA）；`payload/android/`；原始 XML×7 + 日志 + 退出码在 `freeze/evidence/`。

## 恢复与副作用

D 仓库/Git/输入目录只读未动（收工 HEAD/暂存区复核一致）；无设备操作；测试数据仅在本任务 E 目录；候选树构建产物已清理。开发过程中三个测试用例的 harness 缺陷（Channel 悬挂、settle 快照未推进、StateFlow 重放语义）均当轮发现当轮修复，最终 36/36 为最终代码状态的结果，失败中间态未混入任何冻结产物。

## 未覆盖项

- Compose 真实 LazyListState 布局提交、conflate 在生产帧上的实际发射时序无法在 JVM 复现——「重进恢复到原偏移所在单元（含书尾 clamp 书）+ DB 不再被清零」须由唯一设备 owner 冻结新 APK 做 J1 回归；J1 负向谓词绑定原偏移对应阅读单元 + 范围 + UI 可见标记（Codex 已核口径）。
- 设备上「scrollToItem 在未测量列表的真实行为」轨迹仍未实测（本修复不以该假设为前提）。
- v2 的 36 项证据被本批 36 项替代（同套件全量重跑），非增量叠加；`settleSnapshot` 的 visibleItemsInfo 为空列表时的 `?: firstVisibleItemIndex` 兜底未经设备验证。

## 交付状态与下一步

v3 payload（4 文件）+ 980 项源码冻结 + 36/36 绿交付完毕。本轮到此停止：不回灌 D、不提交、不冻结 APK。交 Codex 核查；通过后由唯一设备 owner 冻结新统一 APK 做 J1 回归与受影响邻接验收。
