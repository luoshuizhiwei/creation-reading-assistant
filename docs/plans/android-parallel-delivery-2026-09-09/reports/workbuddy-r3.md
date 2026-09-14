# WorkBuddy R3 交付报告（P1 两级设置 + X1 选区动作/首选词典/StarDict 离线词库）

> 角色说明：本轮 WorkBuddy **不是只验不修**。用户明确指示「我让你自己完成 r3，然后另一个 agent 来完成之前的缺陷和 r1r2 的闭环」，
> 因此本文件记录的是 **R3 的实现交付 + 门禁证据**，而非第三方验收结论（`R3-WORKBUDDY.md` 中的「只验不修」口径由用户指令覆盖）。
> 产品源码 / 测试的缺陷与 R1-R2 闭环由另一个 agent 负责，见 `workbuddy-r1r2-closure.md`。

## 0. 结论速览

| 项 | 结果 | 证据 |
| --- | --- | --- |
| P1 全局 / 本书两级设置 | **实现完成**，JVM 门禁 PASS | 113 个 R3 相关用例 0 失败 |
| X1 选区动作配置 + 搜索提供方 | **实现完成**（含设置页入口），JVM 门禁 PASS | 同上 |
| X1 首选词典三模式 + StarDict 离线词库 | **实现完成**（导入 / 卸载 / 阅读器内查询 / 设置页管理），JVM 门禁 PASS | 同上 |
| `:app:compileDebugKotlin` | PASS | `BUILD SUCCESSFUL`，0 error |
| `:app:testDebugUnitTest` | **部分 BLOCKED** | 2159 tests / 2 failed，**2 个失败均在他人未完成的 `CorrectionProjectionTest`** |
| `:app:lintDebug` | PASS | `0 errors, 5 warnings`（无一条落在本轮改动文件） |
| `:app:assembleDebug` | PASS | `app-debug.apk` 51,395,848 B，sha256 `ad05c1ed…f2276f` |
| `:app:compileDebugAndroidTestKotlin` | **BLOCKED** | 2 处 `No value passed for parameter 'context'`，文件属他人改动 |
| 真机验收矩阵（A/B/C） | **未执行（TODO）** | 见 §6 |
| commit / push | **未做**（用户未授权） | — |

**一句话**：R3 的两条功能线代码已全部写完并通过编译、Lint、打包与本人负责的全部单测；
全量单测与 androidTest 编译的残留失败**全部落在同一工作区里另一个 agent 尚未收敛的改动上**（纠错投影 / `BookRepository` 构造签名），与 R3 改动无交集。

## 1. 纪律与改动边界

- 共享脏工作区，**未执行** `reset` / `checkout` / `clean` / `stash` / `stage` / `commit` / `push`。
- 改动只落在 `android/app/src/**` 与 `docs/plans/android-parallel-delivery-2026-09-09/reports/`。
- 未触碰 `archives/`、`electron/`、`src/`、`scripts/` 及他人正在改的 `feature/reader/rules/**`、`data/local/**`。
- 唯一一次对构建产物的操作：删除被上次中断留下的**损坏的** `app/build/intermediates/classes/debug/transformDebugClassesWithAsm`
  （症状：运行时 `ClassNotFoundException: …AiClient` / `…DatabaseSafetyNet` 等 686 个假失败；`find` 显示该目录只含 3540 个类而非全量）。删除后该目录由 Gradle 重新生成，未动其他构建产物。

## 2. 证据基线

- 仓库：`D:/develop/Code/Codex/creation-reading-assistant`
- branch：`main`　HEAD：`a9a3bceff5087a0256d03c6263871d9ffc5b663f`
- 本轮 WorkBuddy 改动（已跟踪文件 diffstat，13→11 个命中路径）：

```
 ui/screen/ProfileScreen.kt                     |   2 +
 ui/screen/profile/ProfileHomeScreen.kt         |  25 +++
 ui/screen/profile/ProfileRoute.kt              |  51 +++++
 ui/screen/profile/ProfileUiState.kt            |  27 +++
 ui/screen/reader/ReaderInteractionLayer.kt     |  22 +++
 ui/screen/reader/ReaderLayerBuilders.kt        |  51 ++++-
 ui/screen/reader/ReaderScaffold.kt             |  42 ++++
 ui/screen/reader/ReaderScreen.kt               |  30 ++-
 ui/screen/reader/ReaderSelectionToolbar.kt     | 216 ++++++++++++++++-----
 ui/viewmodel/SettingsViewModel.kt              | 104 ++++++++++
 test/.../ReaderSelectionToolbarModelTest.kt    |  95 ++++++++-
 11 files changed, 608 insertions(+), 57 deletions(-)
```

- 本轮新增（未跟踪）产品文件：

| 文件 | 行数 | 用途 |
| --- | --- | --- |
| `data/settings/SelectionActionSettings.kt` | 136 | 动作清单 + 渲染 + sanitize（纯函数） |
| `data/settings/SelectionActionStore.kt` | 110 | DataStore 持久化（复用 `app_settings`，无新表） |
| `ui/screen/profile/SelectionSubPage.kt` | 461 | 「我的 → 选区与查词」设置页 |
| `feature/dictionary/StarDict.kt` | 401 | .ifo/.idx/.dict/.dict.dz 解析与查词 |
| `feature/dictionary/StarDictImporter.kt` | 172 | zip 导入（含 zip-slip 防护）/ 清单 / 卸载 |
| `feature/dictionary/DictionaryRepository.kt` | 99 | 私有目录词库仓储（LRU 2 句柄） |
| `ui/viewmodel/DictionaryViewModel.kt` | 106 | 词典面板状态机 |
| `ui/screen/reader/sheets/DictionarySheet.kt` | 333 | 阅读器内离线查词面板 |
| `test/.../SelectionActionSettingsTest.kt` | 314 | 13 + 13 用例 |
| `test/.../StarDictTest.kt` | 443 | 25 用例 |

> 本轮不引入 Room 迁移。选区配置与本书覆盖都复用 `SettingsStore` 的 `app_settings` DataStore，刻意避开 v12→v13 迁移窗口
> （同目录下 `android/app/schemas/.../13.json` 由另一 agent 的纠错表迁移产生，属其提交范围）。

## 3. 交付内容

### 3.1 P1 全局 / 本书两级设置

- **写入层级由纯函数路由**：`ReaderSettingsRouter.plan(scope, global, displayed, edited)` 产出
  `ReaderSettingsEditPlan(global, overridesToSet, overridesToClear)`；书内编辑操作的是「有效设置」（全局 + 本书覆盖），
  落盘层级由面板顶部的「只改本书 / 改全局」决定。不可覆盖项（亮度 / TTS / 护眼 / 音量键等）永远进全局。
- **不变量**：作用范围选「全局」时，本次改动的可覆盖项会**同时清除本书覆盖**——否则新全局值会被旧覆盖遮住，用户看到「改了没生效」。
- 本书覆盖仅存用户**显式**覆盖项（键存在 = 覆盖），优先级 `本书覆盖 > 全局 > 结构默认`。
- 预设、恢复默认（带二次确认 + 作用范围说明）、覆盖明细「本书 x · 全局 y」、逐项「清除覆盖」均已接线。
- 阅读器改读 `settingsVm.effectiveReader(bookId)`，不再直接消费全局 `reader`。

### 3.2 X1 选区动作配置 + 搜索提供方

- `SelectionActionGroup` 从**硬约束降级为「默认落位提示」**：可配置集合 = 全部非固定动作（9 个），
  因此用户可以把「书内搜索」放进工具条第一屏 —— 这就是验收项「搜索提供方切换（书内搜索 vs 浏览器）」的实现方式。
- 高频槽 `MAX_PRIMARY_SLOTS=3`，`MIN_PRIMARY_SLOTS=1`；全关写入被仓储**拒绝**（选中文字后不能没有任何入口）。
- 已上第一屏的动作**不再在「更多」里重复出现**（`effectiveMore` 排除已占高频位的 id）。
- **能力门控**收敛到单一纯函数 `selectionToolbarActions(settings, canCreateReplaceRule, aiConfigured)`：
  - 分页引擎不可用 → 「替换」不出现；
  - 未配置 AI Key → 「AI 解读」不出现（新增 `aiConfigured` 由 `ReaderScreen` 计算并透传到覆盖层）；
  - 门控把配置项全部摘掉时，高频槽回退到定义顺序第一个可用动作，**绝不渲染空工具条**；
  - 无外部 handler 时保留原有 `showNotice` 明确反馈。
- 视觉映射 `selectionActionVisual(id)` 覆盖全部 9 个动作（此前只认高亮 / 浏览器 / 复制，任意动作上第一屏会渲染成灰点）。
- 高频槽与溢出菜单共用同一个 `onActionClick` 分发，消除「同一动作换位置接错回调」的隐患。

### 3.3 X1 首选词典三模式 + StarDict 离线词库

- 三模式：`offline`（内置离线词库 → 阅读器内面板）/ `system`（系统 PROCESS_TEXT，缺失回退在线并提示）/ `online`（模板打开浏览器）。
- StarDict：`.idx` 常驻原始字节 + 4 个定长数组做 O(1) 定位与字节序二分；`.dict` 用 `RandomAccessFile` 按需随机读；
  `.dict.dz` 流式解压为 `.dict`（临时文件 + 原子改名）。**内存有界**，不整文件载入。
- zip 导入：只取 entry 文件名（天然免疫 zip-slip）、限制 entry 数与解压总字节（防炸弹）、只接受 ifo/idx/dict/dict.dz、
  失败整目录删除不留半截；`.staging` 临时目录 + 原子改名保证中断安全。
- 离线优先降级提示：无词库 / 查不到时把「已装词库清单 + 导入入口」一并给出，用户能自己判断是缺词库还是没这个词。
- 设置页入口 `我的 → 选区与查词`（`ProfileSubPage.SELECTION`）：第一屏动作胶囊、「更多」开关、浏览器 / 在线词典 URL 模板
  （草稿本地维护 + 合法才可保存，避免「打的字被吞」）、首选词典分段选择、离线词库列表 / 卸载 / 导入、恢复默认。
- 词库导入与阅读器内面板**共享同一个 `@Singleton` `DictionaryRepository`**，两处看到同一份词库。

## 4. 门禁执行记录

全部命令在 `android/` 目录，`GRADLE_USER_HOME=D:/develop/env/gradle`、`JAVA_HOME=D:/develop/Java/jdk-17.0.14`，`--no-daemon`。

| # | 命令 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 1 | `:app:compileDebugKotlin` | 0 | `BUILD SUCCESSFUL`（23:16 首次通过，此时全工作区尚无他人未收敛改动） |
| 2 | `:app:testDebugUnitTest --tests "*SelectionAction*" --tests "*ReaderSelectionToolbarModelTest*" --tests "*StarDict*" --tests "*Dictionary*" --tests "*SettingsViewModelTest*" --tests "*ReaderSettingsRouterTest*" --tests "*ReaderSettingsOverlayTest*" --tests "*PerBookSettingsStoreTest*" --tests "*ReadingPresetsTest*"` | **0** | `BUILD SUCCESSFUL`，0 error |
| 3 | `:app:testDebugUnitTest` | 1 | **2159 tests completed, 2 failed**（见 §5） |
| 4 | `:app:lintDebug` | **0** | `BUILD SUCCESSFUL`；`lint-results-debug.txt`：`0 errors, 5 warnings` |
| 5 | `:app:assembleDebug` | **0** | `app-debug.apk` 51,395,848 B，mtime 2026-09-12 00:04:14 |
| 6 | `:app:compileDebugAndroidTestKotlin` | 1 | 2 error（见 §5） |

R3 相关用例明细（全部 `failures=0`，合计 **113**）：

| 测试类 | tests | failures |
| --- | --- | --- |
| `SelectionActionsTest` | 13 | 0 |
| `SelectionActionStoreTest` | 13 | 0 |
| `ReaderSelectionToolbarModelTest` | 8 | 0 |
| `StarDictTest` | 25 | 0 |
| `ReaderSettingsRouterTest` | 11 | 0 |
| `ReaderSettingsOverlayTest` | 14 | 0 |
| `PerBookSettingsStoreTest` | 11 | 0 |
| `ReadingPresetsTest` | 10 | 0 |
| `SettingsViewModelTest` | 8 | 0 |

产物校验：

- APK：`android/app/build/outputs/apk/debug/app-debug.apk`
  sha256 `ad05c1edcadb6cf1b1c6e4ff2f4883a66e8ccd8aa65ad69247ee9162f8f2276f`，51,395,848 B。
  ⚠️ 该 APK 是**含他人未收敛改动的工作区快照**，不是干净冻结快照；用它做真机验收需在结论里标注对应关系。
- Lint：`android/app/build/reports/lint-results-debug.{html,txt,xml}`，0 error / 5 warning，5 条告警均不在本轮改动文件内。

## 5. 阻塞项（BLOCKED，均属他人在途改动）

执行门禁期间，同一工作区有**另一个 agent 正在改动**以下文件（mtime 23:24–23:48，WorkBuddy 改动全部 ≤ 23:09，无交集）：

`feature/reader/rules/RuleModels.kt`、`RulesRepository.kt`、`RuleEngine.kt`、`ReplaceProjection.kt`、
`BoundedReplaceProjector.kt`、`EpubReplaceProjector.kt`、`ReplaceProfile.kt`、`pager/ReplacedChapterSource.kt`、
`ui/screen/reader/sheets/ReaderRulesSheet.kt`、`ui/viewmodel/ReaderViewModel.kt`、
`data/local/entity/ReaderCorrectionEntity.kt`、`data/local/dao/ReaderCorrectionDao.kt`、`AppDatabase.kt`。

由此产生两类残留失败：

1. **`:app:testDebugUnitTest` 2 个失败**
   `CorrectionProjectionTest > growing correction keeps round trip floor`
   `CorrectionProjectionTest > correction applies after plain rules on display space`
   该测试类是对方本轮新增的**未跟踪**文件（`git status` 显示 `?? .../CorrectionProjectionTest.kt`），属纠错投影特性，与 P1/X1 无关。
   编译错误数随对方收敛由 26 → 7 → 0 递减，期间 `RuleEditorDraftTest` 的 5 个失败也已自行消失。
2. **`:app:compileDebugAndroidTestKotlin` 2 个失败**
   `data/repository/BookDeletionPersistenceTest.kt:62` 与 `feature/reader/session/ReadingSessionRecorderPersistenceTest.kt:63`：
   `No value passed for parameter 'context'` —— 生产侧 `BookRepository` 构造新增了必填 `context`，两个**旧** androidTest 调用点未同步更新（两文件 mtime 为 08-20 / 09-10，非本轮产物）。

**WorkBuddy 未自行修改上述任何文件**（跨 agent 边界，且对方的重构语义未定）。

## 6. 真机验收矩阵（未执行，TODO）

按 `R3-WORKBUDDY.md` 的 A/B/C 矩阵，逐项状态如下。设备侧尚未执行 —— 需要一份**干净冻结快照**的 APK，
且需用户确认设备（真机 serial 按纪律记录为 `c49ac6cf`，禁用 MuMu）。

### A. P1 全局 / 本书两级设置
| 检查项 | 状态 |
| --- | --- |
| 改全局 → 未覆盖的书跟随生效 | TODO（JVM 侧由 `ReaderSettingsRouterTest` 11 例覆盖路由；端到端跟随需真机） |
| 显式覆盖后改全局 → 该书不变、其他项仍跟随 | TODO |
| 「清除本书覆盖」回落全局；「恢复默认」有范围说明 + 二次确认 | TODO（UI 已实现，需目视确认文案与确认弹窗） |
| 恢复默认不触碰进度 / 替换规则 / 笔记 / 统计 | TODO |
| 预设套用后逐项可改回、无未声明副作用 | TODO |
| 繁体显示、TTS 定时停止未被重复实现或破坏（回归） | TODO |
| 跨窗口 / 旋转 / 进程重启后一致，无静默回落全局 | TODO |

### B. X1 选区动作 / 搜索提供方 / 首选词典
| 检查项 | 状态 |
| --- | --- |
| 调整常用动作 → 工具条内容/顺序随之变化 | TODO（需在真机确认「书内搜索」可上第一屏） |
| 门控不被绕过：无生效规则无「替换」、无 AI 配置无「AI 解读」、无 handler 有明确反馈、不允许全部隐藏 | JVM 已由 `ReaderSelectionToolbarModelTest` 8 例覆盖；**真机需确认视觉结果** |
| 搜索提供方切换生效 | TODO |
| 首选词典三模式；无词库有降级提示 | TODO |
| **StarDict 离线**：导入 → **断网**查询可用、命中正确；大词库不 OOM、不卡主线程 | TODO（`.dict.dz` 与 zip-slip 已由 `StarDictTest` 25 例覆盖；内存/主线程表现只能在真机看） |
| **往返不丢位置**：外部查询 / 词典 / 浏览器返回后正文位置不变、进度不重置、阅读模式不变 | TODO（R3 退出条件，必须在真机验证） |

### C. 回归
| 检查项 | 状态 |
| --- | --- |
| TXT 分页与滚动、EPUB、Markdown 能力门控不越界；替换规则生效范围不变 | TODO |
| 选区 / 高亮 / 笔记 / 书内搜索 / TTS / 进度恢复按 source 坐标一致 | TODO |
| 无 FATAL / ANR；结束清理临时数据并归还设备设置 | TODO |

## 7. JVM 覆盖不到的缺口（必须真机补）

延续 R2-S1 的结论：**JVM 单测结构上抓不到「接线 / 超时 / 内存」类缺口**（测试全注入假 DAO 直调仓储）。本轮的对应盲区：

- `App.onCreate` 之外的 Hilt 图接线（`SelectionActionStore` / `DictionaryRepository` 的 `@Singleton` 是否真被注入到设置页与阅读器）；
- `DictionaryViewModel` / `SettingsViewModel` 由 `hiltViewModel()` 在不同 NavBackStackEntry 下是否真的共享同一仓储实例；
- 大词库（数十 MB `.idx`）下的查词延迟与是否需要进一步下探主线程；
- 导入大 zip 时解压耗时与进度反馈；
- **往返位置不变**（R3 退出条件）—— 这只能在真机上验证。

## 8. 交接

- **给缺陷 / R1-R2 闭环 agent**：本报告 §5 列出的两类残留失败属你的改动范围（纠错投影 + `BookRepository` 构造签名）；
  WorkBuddy 未触碰你的文件。另请留意 `android/app/schemas/.../13.json` 未跟踪，迁移提交时需一并入库。
- **给用户**：R3 的 P1 + X1 代码已全部落地并通过编译 / Lint / 打包 / 本人负责的全部单测；
  真机验收需要一份干净快照的 APK 与设备时间窗，请确认何时开始。
- **未做**：commit / push（未授权）、设备侧 A/B/C 矩阵、`compileDebugAndroidTestKotlin` 绿。
