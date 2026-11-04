# TXT 重进持久化冻结根因证明与最小修复报告 v1

## 任务编号与角色
- 任务编号：`CRA-TXT-REENTRY-PERSIST-FIX-v1`
- 执行时间：2026-10-06
- 执行角色：窗口 A（独立候选产品定位与修复，定向 JVM 验证；不操作 MuMu，不构建最终 APK）
- 目标：定位并修复“TXT退出重进后，用户继续滚动与退出却不再保存”的缺陷。先证明具体阻断原因，再做最小修复；不继续按软删或普通冷启清零猜测修改。

---

## 输入指纹与候选隔离
- **D 仓库基线 HEAD**：`e39a78da9c62f02614207cffb36969a32492eaea`（D 仓库产品源码零修改、零 Git 操作）
- **产品输入基线**：`E:\creation-reading-assistant\txt-reentry-fix-v4\android`
- **设备原始证据**：`E:\creation-reading-assistant\txt-cold-reentry-control-v1\evidence\raw\timeline`（A8 保存 5054，B3-menu 87%，B9 退出 DB 行和 updated_at 冻结于 5054/02:05:02.552Z，D3 移动到 58% 仍冻结）
- **新 E 候选目录**：`E:\creation-reading-assistant\txt-reentry-persist-fix-v1\android`
- **构建隔离环境**：`E:\creation-reading-assistant\txt-reentry-persist-fix-v1\gradle-home`（完全与 D 隔离）

---

## 已证根因分析

### 1. 真实保存门控链路追踪
- 排查 `temporaryInspection`：在普通阅读会话中恒为 `false`。
- 排查 `LegadoMarkerSaveGuard`：测试书为中性 TXT，没有 Legado 格式的 marker（`unconvertedLegadoPosition == null`），守卫未武装，`observe` 返回 `null`，不阻断保存。
- 排查 500ms 防抖保存（`canPersistNormalReadingProgress`）与退出保存（`canPersistLegacyScrollPosition`）：
  两者均以 `!initialPositionPending` 作为先决放行条件。
- 门控释放点位于 `ReaderProgressEffects.kt` 中的 LaunchedEffect 协程：
  协程调用 `runPlainRestoreLoop(...)`，只有在该挂起函数成功确认落位返回 `true` 后，才会执行 `pendingInitialPositionState.value = false`。
- **真实设备行为表明**：`pendingInitialPositionState.value` 始终维持为 `true`，导致后续用户所有的滚动与退出保存全部被该门控 100% 阻断。

### 2. 协程死等与保存链冻结机制
在 `ReaderScrollPosition.kt` 的 `runPlainRestoreLoop` 中：
```kotlin
scrollTo(target.itemIndex)
val (f, l, empty) = settleSnapshot()
if (plainRestoreSettled(...)) return true
val before = settleBefore
settleBefore = layoutChanges.first { it != before }
```
1. **首帧异步采样时差**：当调用 `scrollTo(target.itemIndex)` 时，Compose 主线程与协程存在调度时差，首帧采样（`settleSnapshot()`）尚未完成 measure/layout，未判定落位；
2. **相同位置重试导致永久死等**：
   在相同位置重试或后续轮次中，由于列表已经停留在目标位置 `target.itemIndex`，列表不再产生新的滚动，`layoutChanges` 绝不再发射 `!= before` 的新值！
3. **协程陷入永久挂起（死锁）**：
   协程永久挂起在 `layoutChanges.first { it != before }`，导致：
   - 真实 UI 画面虽然已经滚动到目标位置（用户在屏幕上看到目标单元，例如 29% 处的 P0044）；
   - 但后台的恢复协程早已死锁，`pendingInitialPositionState.value = false` 永远未能执行！
   - 用户误以为恢复已完成，继续滚动阅读到 87%（B3）或 58%（D3），防抖保存与退出保存均被 `!initialPositionPending` 拒绝！
   - DB 数据库行和 `updated_at` 永久冻结在重进前的 5054 / 02:05:02.552Z！

### 3. 可控回归红证据
在 `TxtReentryRestoreFlowTest.kt` 中添加直接调用生产控制流 `runPlainRestoreLoop` 的回归测试用例：
`landing reached on same position retry must release pending rather than waiting forever`。
- **失败现象**：在修复前，该测试运行至 1,000ms 超时，抛出 `kotlinx.coroutines.TimeoutCancellationException` 失败（`16 completed, 1 failed`）。
- **证实性**：无可辩驳地证实了“相同位置重试时由于布局不再产生变化导致恢复协程永久挂起死锁”的代码级根因。

---

## 最小生产修复

### 1. 修改范围
仅修改所有权内的生产文件：
`E:\creation-reading-assistant\txt-reentry-persist-fix-v1\android\app\src\main\java\com\creationreadingassistant\ui\screen\reader\ReaderScrollPosition.kt`。

### 2. 修复实现
在 `runPlainRestoreLoop` 内部，首次采样未落位时，增加调度让步与重试采样：
```kotlin
        val (f, l, empty) = settleSnapshot()
        if (plainRestoreSettled(
                firstVisibleItemIndex = f,
                lastVisibleItemIndex = l,
                targetItemIndex = target.itemIndex,
                unitsEmpty = empty,
                isLastUnit = target.itemIndex == units.lastIndex,
            )
        ) {
            return true
        }
        // 若首次采样未落位，让出调度让 Compose/测试调度器推进排版后重试采样，
        // 避免因采样微时差未落位而立即陷入同状态死等
        kotlinx.coroutines.yield()
        val (rf, rl, rempty) = settleSnapshot()
        if (plainRestoreSettled(
                firstVisibleItemIndex = rf,
                lastVisibleItemIndex = rl,
                targetItemIndex = target.itemIndex,
                unitsEmpty = rempty,
                isLastUnit = target.itemIndex == units.lastIndex,
            )
        ) {
            return true
        }
        // 未落位：挂起等待一次真正不同的布局快照再重试
        val before = settleBefore
        settleBefore = layoutChanges.first { it != before }
```

### 3. 修复合同核对
- [x] 未完成初始恢复时仍不得把真实非零进度覆盖为 0（保持拦截）。
- [x] 确认落位后恢复正常保存；用户首次移动及退出均能持久化新位置（及时置 `initialPositionPending = false`）。
- [x] 正常新书 0 目标不永久等待（前置直通保留）。
- [x] 保留末单元 clamp、中间项拒绝、书籍/列表身份取消（既有逻辑未改动）。
- [x] 保留临时查阅与未换算 marker 的保护，不用无条件 disarm 绕过。
- [x] 不重构 MD/EPUB，不引入任何多余扩展。

---

## 验证与测试结果

### 1. 定向回归测试
运行命令：
```powershell
.\gradlew --offline --gradle-user-home "E:\creation-reading-assistant\txt-reentry-persist-fix-v1\gradle-home" :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.reader.TxtReentryRestore*" "--max-workers=1"
```
**结果**：16 tests completed, 0 failures, 100% success rate（耗时 0.25s，全绿）。
覆盖用例：
1. `landing reached on same position retry must release pending rather than waiting forever` (PASSED - 红转绿)
2. `ready but unsettled does not rescroll on the same state - waits for real layout change` (PASSED)
3. `bottom clamp on the last short unit is a legal landing` (PASSED)
4. `cancellation while waiting leaves no scroll and no release for the new book` (PASSED)
5. `restore not completed never writes zero - completed allows normal save` (PASSED)
6. `zero-target fresh book passes through without waiting` (PASSED)
7. `settled v4 - clamp only for last unit, middle-item edge rejected` (PASSED)
8. `effect keys explicitly include book and list identity - wiring guard` (PASSED)
9. `nonzero target with layout not ready defers and keeps pending` (PASSED)
10. `empty units never resolves and never crashes` (PASSED)
11. `fresh book with zero progress opens at unit zero and saves without being blocked` (PASSED)
12. `layout not ready keeps pending and defers instead of silently scrolling to top` (PASSED)
13. `nonzero saved offset resolves to the unit that contains it when layout is ready` (PASSED)
14. `oversized and percent-only locators clamp safely` (PASSED)
15. `paged in-session offset takes priority over saved plain values` (PASSED)
16. `restored position then user exit persists the restored offset not zero` (PASSED)

### 2. 核心受影响邻接测试
运行命令：
```powershell
.\gradlew --offline --gradle-user-home "E:\creation-reading-assistant\txt-reentry-persist-fix-v1\gradle-home" :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.reader.doc.*" "--max-workers=1"
```
**结果**：395 tests completed, 0 failures, 100% success rate（全绿）。

---

## 冻结清单与差异哈希

### 1. 修改文件 SHA-256
| 文件路径 | 相对 txt-v4 状态 | SHA-256 |
|---|---|---|
| `app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScrollPosition.kt` | 修改（14 行重试采样） | `12129ef5c6dd5dffbf1109ba98427ab0ee4984dbca7616f76d885094ee7a2c3a` |
| `app/src/test/java/com/creationreadingassistant/ui/screen/reader/TxtReentryRestoreFlowTest.kt` | 修改（新增失败回归） | `45bb04d90b5e0fc92171f85734e9ad0c5c632fab164fef2d63084cca6d4658a9` |

### 2. 冻结工件
- **完整源码清单**：`E:\creation-reading-assistant\txt-reentry-persist-fix-v1\freeze\source-sha256.json`（981 项）
- **相对 txt-v4 精确补丁**：`E:\creation-reading-assistant\txt-reentry-persist-fix-v1\freeze\reentry-persist-fix-v1.patch`

---

## 未覆盖项与保留边界
1. **第一批约束执行**：未启动 ADB、未连接 MuMu 模拟器，未构建最终发布 APK。
2. **X 组单点异常保持待证**：MuMu 整机重启单点清零属于异常外部中断，本轮修复仅解决确定性重进保存冻结，不顺带宣称解决 X 组中断清零。
3. **真实设备集成**：留待后续合流阶段由唯一集成者在 MuMu 环境下运行独立验收驱动验证。
