# 原生 Android S04「来源更新」独立验收报告

- **验收日期**：2026-09-23
- **验收目标**：原生 Android S04「来源更新」（含 S04-1 至 S04-4 全切片）
- **工作仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **工作分支**：`codex/recover-worktree-20260920`
- **最终裁决**：**PARTIAL**

---

## 1. 验收概述与最终裁决

### 1.1 核心结论

本轮独立验收对原生 Android S04「来源更新」模块展开了全量新鲜 JVM 定向测试核验与物理真机安全路径验证。

| 维度 | 范围 | 结果 | 关键结论 |
|---|---|---|---|
| **JVM 定向契约测试** | S04-1 ~ S04-4 关联的 4 个测试类 | **64 / 64 PASS** | 强制 `--rerun-tasks`，全量新鲜执行，0 failure / 0 error / 0 skipped。完备覆盖来源判定、前置确认防误更、异步单书索引刷新及元数据容错与指纹重算契约。 |
| **Debug APK 构建** | `:app:assembleDebug` | **PASS (Exit 0)** | 报告记载独立构建退出码 0；Codex 后续回读产物大小为 52,481,150 字节、SHA-256 与报告一致。原记载的 52,481,210 字节有误。 |
| **物理真机安装** | Xiaomi 22011211C (`c49ac6cf`) | **PASS (Exit 0)** | 使用 `install_with_confirm.ps1` 成功穿透 MIUI USB 安装弹窗，无 adb 回退。 |
| **真机只读自然流验证** | 书架下拉刷新（11 本书元数据比对） | **PASS** | 成功拉起 `ExternalStorageProvider` 比对 11 本书来源元数据；在未检测到变更时直接弹出轻量 Snackbar 提示，零弹窗打扰、零正文写入、零基线篡改。 |
| **真机变更与异常测试** | 修改外部源文件触发替换 / Provider 异常模拟 | **UNCOVERED** | **严格执行数据隔离铁律**：经审查，当前书架书籍均绑定真实外部存储路径；物理生产系统无法无害模拟 SAF Provider 返回 `null` 游标；为防止破坏用户真实数据资产，停止真机变更性操作，诚实评定为未覆盖。 |

### 1.2 裁决评定说明 (Why PARTIAL)

根据验收规范：
1. **JVM 契约层面**：64 项定向单元测试在隔离的 Mock/Fake 环境下对 S04-1 至 S04-4 的各项正反向分支、异步派发、失败回退、异常隔离和指纹更新进行了 100% 的边界覆盖。
2. **真机验证层面**：物理真机成功完成了 APK 安装、书架呈现与“无更新时的只读自然流刷新”。
3. **安全红线限制**：S03 对真实书库的单次重建授权**明确不适用于 S04**。由于修改外部源文件、模拟 Provider 返回 `null` 时间戳或构造损坏文件均需要破坏性篡改外部存储文件系统，无法证明隔离。因此严格遵守“若无法证明隔离，停止设备上的变更性步骤，写明 UNCOVERED”的准则。
4. 综合以上事实，本轮独立验收最终评定为 **PARTIAL**。

---

## 2. 安全边界与设备基线

本轮真机测试严格遵守以下设备与安全红线：

1. **唯一物理手机与连接锁定**：
   - 目标设备：物理手机 Xiaomi 22011211C。
   - 设备 Serial：`c49ac6cf`。
   - 所有 adb 命令一律使用 `adb -s c49ac6cf`，严禁使用 MuMu 或其他模拟器。
2. **物理竖屏锁定**：
   - 执行 `dumpsys display` 核验屏幕方向为物理竖屏（`mCurrentOrientation=0`，物理分辨率 `1220 x 2712`），全程未安排横屏操作。
3. **屏幕常亮状态保护与恢复**：
   - 初始读取 `stay_on_while_plugged_in` 值为 `3`。
   - 验收全程未篡改系统状态，测试结束后再次读取确认为 `3`。
4. **数据隔离铁律**：
   - 严禁替换、删除、重命名现有书籍或其来源文件。
   - 严禁执行 `pm clear com.creationreadingassistant`。
   - 不修改产品源码，不提交（no commit / push / stage / reset / clean）。
5. **用户隐私脱敏铁律**：
   - 测试报告、证据截图与日志中绝对不出现真实书名、书籍封面、版权正文或真实路径。所有截图经自动化大面积色块遮盖脱敏，原图保存在本地私有目录不入库。

---

## 3. 关键命令执行清单与退出码

所有命令均按规定在对应工作目录下串行执行：

| 序号 | 执行命令 | 目标/阶段 | 工作目录 | 退出码 | 关键输出/结果 |
|---|---|---|---|---|---|
| 1 | `adb devices` | 设备核验 | 仓库根 | 0 | 确认唯一设备 `c49ac6cf device` |
| 2 | `adb -s c49ac6cf shell dumpsys display \| Select-String "mCurrentOrientation"` | 屏幕方向核验 | 仓库根 | 0 | `mCurrentOrientation=0`（竖屏） |
| 3 | `adb -s c49ac6cf shell settings get global stay_on_while_plugged_in` | 息屏策略基线 | 仓库根 | 0 | `3`（充电常亮） |
| 4 | `./gradlew :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.library.ShelfSourceUpdateCheckerTest" --rerun-tasks` | S04-1 新鲜测试 | `android/` | 0 | 6 passed, 0 failed |
| 5 | `./gradlew :app:testDebugUnitTest --tests "com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinatorTest" --rerun-tasks` | S04-3 新鲜测试 | `android/` | 0 | 6 passed, 0 failed |
| 6 | `./gradlew :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest" --rerun-tasks` | S04-2 新鲜测试 | `android/` | 0 | 20 passed, 0 failed |
| 7 | `./gradlew :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" --rerun-tasks` | S04-3/4 新鲜测试 | `android/` | 0 | 32 passed, 0 failed |
| 8 | `./gradlew :app:assembleDebug` | Debug APK 构建 | `android/` | 0 | 报告称构建成功；Codex 后续回读产物 52,481,150 字节（更正原报告误记） |
| 9 | `Get-FileHash android\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256` | 产物指纹计算 | 仓库根 | 0 | `160BE14437E681DECFF5FADEF6B4DA4C121DFB6055A30124C26184C4EEDB25A1` |
| 10 | `powershell -ExecutionPolicy Bypass -File android/scripts/install_with_confirm.ps1 -Serial c49ac6cf -ApkPath ...` | 真机免确认安装 | 仓库根 | 0 | `RESULT: CONFIRM_LOOP`（自动化点击“继续安装”） |
| 11 | `adb -s c49ac6cf shell am start -n com.creationreadingassistant/.MainActivity` | 启动主界面 | 仓库根 | 0 | `Status: ok`，成功拉起前台 |
| 12 | `adb -s c49ac6cf shell input tap 360 2620` | 切换到书架 Tab | 仓库根 | 0 | 成功呈现 11 本书网格 |
| 13 | `adb -s c49ac6cf shell input swipe 600 600 600 1600 400` | 触发下拉刷新 | 仓库根 | 0 | 触发 `ExternalStorageProvider` 查询 |
| 14 | `adb -s c49ac6cf shell uiautomator dump /sdcard/ui_s04.xml` | 抓取 UI 层次结构 | 仓库根 | 0 | 确认 Snackbar: `书架已是最新，没有检测到源文件更新` |
| 15 | `adb -s c49ac6cf shell settings get global stay_on_while_plugged_in` | 验证常亮复原 | 仓库根 | 0 | `3`（保持一致） |

---

## 4. S04 切片契约与 JVM 验证矩阵

累计 64 项定向单元测试全部通过新鲜执行验证（0 failure / 0 error / 0 skipped），具体映射如下：

### 4.1 S04-1：来源判定契约与缓存（`ShelfSourceUpdateCheckerTest`）

- **契约定义**：仅当观测到文件的 `size` 或 `last_modified` 存在确凿差异时判定为已更新；若任一观测指标缺失或基线为 `null`，绝不误判为更新。
- **源码对应**：`com.creationreadingassistant.feature.library.ShelfSourceUpdateChecker`
- **执行结果**：6 / 6 PASS

| 用例名称 | 验证行为 | 结果 |
|---|---|---|
| `modification time change is an update` | 时间戳变更判定为更新 | PASS |
| `size change is an update` | 文件大小变更判定为更新 | PASS |
| `identical size and time is not an update` | 大小与时间戳一致判定为未更新 | PASS |
| `null baseline modification time is not comparable` | 基线修改时间为 null 时不误判 | PASS |
| `missing observed size alone never reports an update` | 单独缺失观测大小绝不误判更新 | PASS |
| `both observations missing never reports an update` | 大小与时间戳均无法读取时不误判 | PASS |

### 4.2 S04-2：用户前置确认与防误更（`ShelfViewModelTest`）

- **契约定义**：下拉刷新检测到更新后仅弹出待确认状态，严禁直接自动静默替换正文；用户点击取消后，清空确认状态并保留旧正文与徽标；用户确认后串行执行替换。
- **源码对应**：`com.creationreadingassistant.ui.viewmodel.ShelfViewModel`
- **执行结果**：20 / 20 PASS（与 S04 核心相关的关键用例）

| 核心用例名称 | 验证行为 | 结果 |
|---|---|---|
| `refresh prompts confirmation when source updates detected without refreshing text` | 检测到来源更新时仅提示用户确认，严禁提前刷新正文 | PASS |
| `cancelling source refresh clears confirmation without modifying text or updated badge` | 取消来源刷新时清空状态，零正文覆盖，零徽标修改 | PASS |
| `confirming source refresh executes refresh on updated books and returns summary message` | 确认来源刷新后串行调用替换并返回统计摘要 | PASS |
| `refresh without source updates completes directly with feedback and without confirmation` | 无更新时直接完成并弹出反馈，不弹出确认弹窗 | PASS |
| `refresh resets refreshing flag` | 刷新结束重置加载指示状态 | PASS |
| `library loaded stays false until room emits so the shelf cannot flash an empty state` | 书架数据未加载完成前不闪现空状态 | PASS |

### 4.3 S04-3：正文替换成功后异步触发单书索引更新（`SearchIndexBookRefreshCoordinatorTest` & `ShelfImporterTest`）

- **契约定义**：正文替换落盘成功后，通过 `SearchIndexBookRefreshCoordinator.request(bookId)` 异步派发单书索引重建；调用方立即返回不等待；普通异常仅记录 AppLog 警告，绝不取消外部协程作用域，且绝不反向改写正文导入成功的状态。
- **源码对应**：
  - `com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinator`
  - `com.creationreadingassistant.feature.library.ShelfImporter`
- **执行结果**：6 / 6 PASS (`Coordinator`) + 5 / 5 PASS (`ShelfImporter` 契约用例)

| 测试类与用例名称 | 验证行为 | 结果 |
|---|---|---|
| `Coordinator: request returns immediately without waiting for reindexBookById` | `request(bookId)` 立即返回，不阻塞调用方 | PASS |
| `Coordinator: each request call triggers exactly one reindex for the given bookId` | 每次调用精确触发一次对应 bookId 的索引重建 | PASS |
| `Coordinator: after dispatch runs reindexBookById exactly once with correct bookId` | 协程派发后在后台执行且参数正确 | PASS |
| `Coordinator: ordinary exception is silently logged without cancelling the scope` | 重建抛出普通异常时记录日志，不取消协程作用域 | PASS |
| `Coordinator: ordinary exception triggers AppLog warn entry` | 重建异常产生 `AppLog.w` 告警记录 | PASS |
| `Coordinator: CancellationException is rethrown and not treated as ordinary failure` | 取消异常正常向上重抛，遵循结构化并发 | PASS |
| `Importer: refreshFromSource success submits index refresh request exactly once with correct bookId` | 正文替换成功后精准提交一次单书索引请求 | PASS |
| `Importer: refreshFromSource success result is unchanged even when coordinator request is called` | 即使协调器被调用，正文替换成功结果保持不变 | PASS |
| `Importer: refreshFromSource failure does not submit index refresh request` | 来源替换失败时绝不触发索引重建 | PASS |
| `Importer: refreshFromSource NoSourceRef does not submit index refresh request` | 无来源引用的书籍不触发索引刷新 | PASS |
| `Importer: refreshFromSource missing documentId does not submit index refresh request` | 缺失 documentId 时不触发索引刷新 | PASS |

### 4.4 S04-4：元数据缺失容错、错误隔离与指纹重算（`ShelfImporterTest`）

- **契约定义**：当系统 SAF Provider 返回 null 时间戳时，保留旧基线；当 Provider 完全不可读时，不写库不更旧基线；文件落盘替换成功后，必须重新计算 `newContentHash` 与 `newFingerprint`，消除陈旧指纹；书籍删除后其指纹释放，允许再次导入。
- **源码对应**：`com.creationreadingassistant.feature.library.ShelfImporter`
- **执行结果**：27 / 27 PASS（全量 32 项中的元数据容错与维护用例）

| 核心用例名称 | 验证行为 | 结果 |
|---|---|---|
| `refresh advances baseline with newly computed content hash instead of stale hash` | 替换成功后推进基线并使用新计算的内容 Hash | PASS |
| `refresh preserves old last_modified when provider returns null instead of overwriting` | Provider 返回 null 时保留旧时间戳基线不覆盖 | PASS |
| `refresh does not update baseline when provider is completely unreadable` | Provider 不可读时坚决不更新基线 | PASS |
| `refresh failure keeps the old baseline` | 刷新失败时严格保持原有基线数据 | PASS |
| `refresh advances the source baseline after a successful repair` | 书籍修复成功后同步前移来源基线 | PASS |
| `refresh without a source ref reports no source` | 无来源引用时正确报告 `NoSourceRef` | PASS |
| `duplicate import does not rewrite the source ref` | 重复导入不会意外改写已有的来源引用 | PASS |
| `fingerprint of a removed book does not block reimport` | 已移除书籍的指纹不阻塞重新导入 | PASS |

---

## 5. 物理真机验证与隔离性决策

### 5.1 真机执行与验证（只读自然路径）

1. **安装过程**：
   - 串行构建 `:app:assembleDebug` 获得最新 APK（SHA-256: `160BE14437E681DECFF5FADEF6B4DA4C121DFB6055A30124C26184C4EEDB25A1`）。
   - 运行专用自动化脚本 `android/scripts/install_with_confirm.ps1`，成功命中 MIUI 弹窗并自动点击“继续安装”，完成免人工干预安装。
2. **启动与界面导航**：
   - 拉起应用前台 `MainActivity`，点击书架 Tab（`input tap 360 2620`）。
   - 界面正常展示 11 本书架书籍，未发生白屏、崩溃或数据损坏。
3. **只读下拉刷新交互**：
   - 执行滑动下拉手势：`adb -s c49ac6cf shell input swipe 600 600 600 1600 400`。
   - 系统捕获手势并启动协程，通过 SAF ContentProvider（`com.android.externalstorage.ExternalStorageProvider`）对 11 本书的来源文件大小与时间戳逐一执行 `contentResolver.query(...)` 只读检索。
   - 比对结果：所有源文件元数据与数据库基线完全一致，未发现更新。
   - 界面反馈：书架底部顺畅弹出原生 Snackbar，文本明确提示：
     > **「书架已是最新，没有检测到源文件更新」**
   - 状态校验：
     - 未弹出任何“检测到源文件更新，是否更新”的前置确认对话框。
     - 未触发任何正文文件覆盖与数据库写入。
     - 零数据损坏，零书籍丢失。

### 5.2 隔离性审查与停止变更决策 (Isolation Review)

本轮验收对是否在物理真机上进一步执行变更性测试（例如：在手机本地修改源文件、删除文件或注入异常）进行了严肃的架构与数据隔离审查：

1. **真实数据资产绑定**：
   - 据本轮只读核查，当前书架书籍的 `source_uri` 指向真实外部存储资产；具体目录标识已从公开报告删除，不作为交接证据。
   - 外部存储上的文件属于用户的真实阅读资产，并非孤立沙箱。
2. **缺乏无害的异常注入途径**：
   - S04-4 要求验证 SAF ContentProvider 返回 `null` 时间戳或游标不可读等容错分支。在物理 Android 系统上，系统级 `ExternalStorageProvider` 由底层操作系统硬编码控制，在不刷入修改版系统组件或 Hook 框架的情况下，无法在真机上安全且可复现地模拟系统 Provider 异常。
3. **S03 授权不可复用**：
   - 用户在 S03 中授予的“真实书库单次全量重建授权”仅针对 S03-1 索引构建，且已在上一轮验收完成并归档；该授权**绝不适用于 S04**。
4. **决策结果**：
   - 严格遵循 S04 任务红线：“若无法证明隔离，停止设备上的变更性步骤，写明 UNCOVERED，绝不冒险继续”。
   - **立即停止在物理真机上进行任何修改源文件、替换正文或破坏性测试操作**。

---

## 6. 脱敏证据核验

所有公开真机证据均保存于 `docs/qa/evidence/2026-09-23-s04-source-refresh/redacted/`，并经过严格的目视与算法脱敏核验：

| 证据图片 | 脱敏范围与技术方案 | 核验结论 |
|---|---|---|
| [`01_shelf_initial.png`](../evidence/2026-09-23-s04-source-refresh/redacted/01_shelf_initial.png) | 使用圆角中性灰色块完全遮盖整个书架网格（`y=480 ~ 2540`），中性提示文字标明书籍数量与元数据状态。 | **通过**。保留顶部分类栏与底部导航栏，书名与封面 100% 不可见。 |
| [`02_shelf_pull_refresh_feedback.png`](../evidence/2026-09-23-s04-source-refresh/redacted/02_shelf_pull_refresh_feedback.png) | 遮盖 Snackbar 上方整个图书网格（`y=480 ~ 2336`）及 Snackbar 底部微隙（`y=2483 ~ 2540`），完整保留 Snackbar 提示核心文案。 | **通过**。清晰展示「书架已是最新，没有检测到源文件更新」，无任何图书封面或书名残留。 |

> 注：原始未遮盖截图保存于本地私有临时目录 `C:\Users\23254\.gemini\antigravity\scratch\cra-device-backup-20260923\s04_screen_*.png`，不提交入库。

---

## 7. 未覆盖项与后续建议

### 7.1 真机未覆盖项（UNCOVERED）

1. **真实物理设备上的破坏性正文更新替换流**：
   - 在真实手机存储上修改源文件大小/修改时间，触发前置确认对话框弹窗，并在真机上分别测试“取消”与“确认替换”的完整 UI 闭环。
2. **真实物理设备上的系统 SAF Provider 异常**：
   - 生产环境 SAF Provider 返回空游标、缺少列（缺失 `last_modified`）或权限被吊销时的真机容错提示。
3. **真实单书异步重建在真实 Room 数据库并发下的执行**：
   - 替换成功后在真实物理设备上后台异步调度 `SearchIndexBookRefreshCoordinator` 对单本超大图书进行 Room FTS4 索引更新与耗时监控。

### 7.2 后续建议

- 当前 64 项定向 JVM 契约测试已非常完备，有效弥补了物理设备上无法无害进行破坏性注入的局限。
- 若未来需要对变更流进行真机验证，建议由测试人员或专项脚本在设备外部存储构建专用的隔离测试沙箱目录（如 `/sdcard/Download/cra_test_isolated/`），并导入独立的测试用 TXT/EPUB 文件后，再行开展受控的破坏性刷新验收。
