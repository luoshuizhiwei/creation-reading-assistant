# R2-S1.6 双通道搜索 · 交接文档（WorkBuddy → 下一任 agent）

**日期**: 2026-09-11
**执行人**: WorkBuddy（洛水之蔚 协作会话）
**状态**: **Dev-verified**（compileDebugKotlin + 全量 `testDebugUnitTest` 绿，2005 tests / 0 fail）；**未 commit / push**
**范围**: `android/**` 的 feature/search + ui/viewmodel/SearchViewModel + data/repository/SearchIndexRepository（R2-S1.6 / #58）

---

## ⚠️ 复验更新（2026-09-11，同日晚些时候）——先读这段再看下文

设备 `c49ac6cf` 本轮**在线**，本文档 §4 中「真机端到端未验收，留 CI」**已不成立**。最新状态见
`docs/plans/android-parallel-delivery-2026-09-09/reports/workbuddy-r2-s1-6-device-verification.md`（复验通过版）。要点：

1. **全文索引在生产中从未接线**（`SearchIndexScheduler` 零引用）——已在 `App.kt` **+18 行**最小修复（`SearchIndexEntryPoint.searchIndexScheduler().start()`；方法名不能叫 `scheduler()`，Hilt 会把所有 `@EntryPoint` 合成到同一 `SingletonC`，与 `GoalSchedulerEntryPoint.scheduler()` 冲突）。修复版 APK SHA-256 `fae5346d4a350d97f773d6141d656f3af9149a8b0e6ffae1c6682c5d17e5abca`。
2. **真机 E2E 全项 PASS**：`原文 · 第 164 章` / `替换显示文 · 第 164 章` 双通道来源标注、精确到达（0.0% → 39.2%）、返回保留（查询/Tab/滚动）。Room 11→12 迁移真机 **OK (17 tests)**，S1.1 声明的唯一验证缺口闭环。
3. **两个新阻断项（需集成者/owner 裁决，详见报告 §5）**：
   - **P0** 全库构建被 JobScheduler 执行时限掐断（`timeout-reg`/`timeout-total` 已计数），只建了 9/26 本；`processChunked` 每 20 本才写游标 ⇒ `search_index_state` **恒空**、无进度可读；`incompleteBookIds()` / `rebuildAll()` / `progress` **全零调用方** ⇒ 无续建/重试入口。
   - **P1** 改替换规则不触发显示文重建 + 游标单向 ⇒ **后加的规则永不生效**。建议规则变更后对受影响 bookId 调 `indexSingleBook()`（已实现且幂等，只缺调用方）。
4. **索引规模实测**：约 231 字节/行、约 43× 正文体积、约 2.2 MB/s；8 本/44MB → 827 万行 → 1.91 GB。全库 26 本预计数十分钟 + 数 GB。
5. **环境适配修正**：本文档 §0 要求「构建只能走 PowerShell `.\gradlew.bat`」在本机**不成立**（PowerShell 工具无法执行任何外部进程）。已改为 **Bash 直调 java 跑 Gradle wrapper**，命令见报告 §8。另：拉二进制必须 `adb exec-out`（`shell cat` 会 CRLF 损坏 SQLite 库）；设备无 `sqlite3`。
6. 可复用工具：`.workbuddy/skills/cra-android-device-e2e/`（真机验收 + 索引取证技能，含 `ui.py` / `db_probe.py`）。

---

## ⚠️ 复验更新（2026-09-11 晚，代码复核）——其他 agent 已大幅改代码，再读这段

WorkBuddy 本轮对 `android/**` 做了**整仓级**复核（git status 显示 100+ 文件改动，含 electron / 桌面 TS / 安卓三端）。针对 S1.6 搜索的相关结论：

1. **`SearchIndexRepository.kt` 被整体重写（+1252 行）**，由其他 agent 完成。新增纯函数层 `feature/search/`：
   - `SearchHit.kt`（`SearchHit` / `SearchMatchSpan` / `SearchHitContext` / `SearchContext`）
   - `SearchCoverage.kt`（`SearchTextBasis` / `SearchCoverageState` / `SearchCoverageReason` / `SearchCoveragePolicy`）
   - `SearchHitSelection.kt`（`SearchOffsets` / `RawTermMatch` / `SearchHitSelection` 纯函数打分选坐标）
   - `SearchTokenAggregator.kt`（`SearchTokenAggregator` / `IndexUnit` / `DisplayChannelIndexer`，与前会话同文件同口径）
   **S1.6 契约全部保留**：双基准搜索、display 通道经 `DisplayChannelIndexer.project` 构建、`resolveLegacyOffset` 支持 DISPLAY 反查、`RuleEngine.applyReplace` 内部按 `enabled` 过滤、coverage 从 `search_index_coverage` 实读不推断。
2. **display 通道现已真正构建**（不再是 PENDING）：`indexOneBook`（私有，约 L903）在逐章/预览路径中对每单元调 `DisplayChannelIndexer.project`（全仓 5 处调用点），覆盖率记 `NOT_APPLICABLE`（无生效规则）/ `FAILED`（规则失效），**明确不再用占位 `PENDING`**（见仓库 L899-900 注释）。
   > ⚠️ **文档漂移**：`SearchCoverage.kt` 中 `SearchCoverageState.PENDING` 的 doc comment（"当前替换显示文通道即处于此状态"）**已过时**——display 通道现已构建。该 enum 值仍保留仅为 wire 兼容，调用方不应再当作「本版未建」解释。下一任若清理，应把该注释改为「历史占位 / 防御用，display 通道现已构建」。
3. **`App.kt` 调度接线确认在场**（与 §顶部复验更新第 1 点一致）：`App.onCreate` 经 `SearchIndexEntryPoint.searchIndexScheduler().start()` 启动索引 worker；方法名 `searchIndexScheduler()` 是为规避与 `GoalSchedulerEntryPoint.scheduler()` 的 Hilt 合成冲突。
4. **测试全量绿，且数量上升**：`testDebugUnitTest` 重新跑（Bash 直调 java wrapper，`--no-daemon`，`dangerouslyDisableSandbox:true`）→ **BUILD SUCCESSFUL，2005 tests / 0 fail**（前次 1993）。S1.6 相关 12 个套件全绿：
   `SearchIndexRepositorySearchTest`(16/0)、`DisplayChannelIndexerTest`(8/0)、`SearchCoveragePolicyTest`(11/0)、`SearchTokenAggregatorTest`(7/0)、`SearchTokenizerTest`(9/0)、`SearchHitSelectionTest`(13/0，新增)、`SearchContextTest`(9/0，新增)、`SearchViewModelTest`(17/0) 等。我的 `SearchIndexRepositorySearchTest` 构造函数（8 参）与重写后的仓库**完全一致**，无需改。
5. **新索引续建机制疑似已缓解 §顶部复验更新第 3 点的 P0**：仓库新增 `INDEX_BUILD_BUDGET_MS=8min` 预算主动让出 + `ensureIndexedIncremental` / `processChunked` / `lastChapter` 章节级续建 + `progress` StateFlow + `incompleteBookIds()`。这直接针对「单本超窗被掐断 / 无续建入口 / 游标恒空」三点。是否真在设备端闭环（26 本全库）**仍需真机复验**，不在本机 JVM 验证范围。
6. **构建通道实测**：本会话 PowerShell 工具**完全无输出**（连 `Write-Host` 都空），只能走 **Bash 直调 java wrapper**（见 §0.29-30 及复验报告 §8）。Gradle 跑测试时偶发 `Unable to delete .../test-results/.../binary/output.bin`（文件锁，疑似沙箱/杀软），手动 `rm -rf app/build/test-results/testDebugUnitTest` 后 `--rerun-tasks` 即通过。

---

## 0. 必读纪律（违反即事故）

- **共享脏工作区**：绝不 reset / checkout / clean / stash / stage / commit / push 全仓。只允许改动本片文件。
- **构建只能走 PowerShell** `.\gradlew.bat <task> --no-daemon`（先 `$env:GRADLE_USER_HOME="D:\develop\env\gradle"`）；Git Bash 的 `./gradlew` 缺 cygpath 会失败。调用 gradle 需 `dangerouslyDisableSandbox:true`（沙箱持有 `app/build/**` 句柄会卡 dexBuilder/测试输出）。
  > **2026-09-11 复验修正**：本机 PowerShell 工具**无法执行任何外部进程**，上述通道不可用。改用 **Bash 直调 java 跑 Gradle wrapper**（等价），命令见复验报告 §8 / 技能 `cra-android-device-e2e`。`dangerouslyDisableSandbox:true` 的注意事项仍然适用。
- **验收口径 = compile + JVM 测试绿 + 本报告**；真机 c49ac6cf 本轮**已在线**，设备项**已完成**（见顶部「复验更新」）。
- 报告与交接文档统一放 `docs/plans/android-parallel-delivery-2026-09-09/reports/`。

## 1. S1.6 交付内容（已验证）

**锁定的产品决策（用户确认，勿改）**：原文（original）与替换显示文（display）**都建索引、都搜、命中按 `textBasis` 标注来源**。

| 任务 | 内容 | 验证状态 |
|---|---|---|
| #59/#63（前会话） | 模型扩展 + 纯函数提取。注意：`DisplayChannelIndexer`、`IndexUnit` 与 `SearchTokenAggregator` **同在 `SearchTokenAggregator.kt` 一个文件里** | ✅ |
| #60（前会话） | `SearchIndexRepository.indexOneBook`（约 L950）经 `DisplayChannelIndexer.project` 建 display 通道；无生效规则 → 空列表（不重复建索引） | ✅ 生产调用点已核实 |
| #61（前会话） | `SearchViewModel.search` 查 `bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY)`；`SearchScreen` 命中标「原文 / 替换显示文」（L578-580），coverage `NOT_APPLICABLE` 不显示提示（L609），item key 含 `textBasis.wire`（L426） | ✅ 生产代码已核实 |
| #62（前会话） | `resolveLegacyOffset` 支持 DISPLAY：display 坐标偏移经 `mapDisplayOffset`（对**同一段原文**重放生效规则 `RuleEngine.applyReplace(...)` → `TextOffsetMap.toSource(displayOffset)`）反查 source 章内偏移 + 章起始。规则读取失败 / 正则失效一律 `null` 降级（只打开书），绝不拿错位偏移冒充精确位置 | ✅ |
| #64（本会话收口） | JVM 测试套件：新增 `SearchTokenizerTest`(9)、`SearchTokenAggregatorTest`(7)、`DisplayChannelIndexerTest`(8)、`SearchIndexRepositorySearchTest`(14，内存 Fake DAO + 真 tokenizer)；扩展 `SearchCoveragePolicyTest`（`NOT_APPLICABLE` 视为 complete / 稳定终态 / wire 值钉死）；`SearchViewModelTest` 增「search 请求双基准」（`slot` 捕获 bases 参数） | ✅ 全量绿 |

## 2. 本会话排障记录（重要教训）

1. **前会话「全量 JVM 崩溃、无 XML」的真相**：不是 JVM crash，而是 `SearchTokenAggregator.kt` 从未 import 过 `SearchTermRow`（潜在 bug，`DisplayChannelIndexer.project` 返回值类型无法解析）＋ 我误删 `ReplaceRule`/`RuleEngine` import → 编译失败。三个 import 均已修复。**教训：接手前必先全量编译验证；「前会话已完成」≠「已验证」。**
2. **6 个断言失败全部是测试期望错，实现零改动**：
   - `SearchTokenizer` 实际口径：孤立 CJK 尾字补单字 token（`中文小说` → `中文/文小/小说/说`）；数字与 CJK **不**合成 Bigram（`第3章 Hello` → `第/3/章/hello`）。⚠️ **`SearchTokenizer.kt` 文件头 docstring 示例与实现不符**（示例写「第3/3章」）。未改实现（改分词口径会波及已建索引），测试对齐实际行为；docstring 漂移遗留 owner 决策。
   - `RuleEngine.applyReplace` **内部按 `enabled` 过滤禁用规则**：禁用规则不参与投影 → 显示文==原文，display 通道仍产出**原文** token（body 非空时 project 不会返回空）。测试改为断言该契约。
3. Kotlin 反引号测试函数名**不能含 `;`**（编译报 "Name contains illegal characters: ;"）。
4. **同一文件并行发多个 Edit 会竞态**（实测 4 个 Edit 只落盘 1 个，其余静默回滚）→ 同一文件多处修改必须逐个 Edit 或用 Write 全量重写。
5. `SearchTokenizer` 单 term offsets 上限 **1000**（`offs.size < 1000`），因此聚合侧 16KB CSV 守卫实际打不满——写测试断言别按 16KB 满载设计。

## 3. 验证基线（复现命令）

```powershell
$env:GRADLE_USER_HOME="D:\develop\env\gradle"
cd android
.\gradlew.bat testDebugUnitTest --no-daemon
# 期望：BUILD SUCCESSFUL，2005 tests，0 fail（约 6 分钟冷跑；PowerShell 不可用，走 Bash 直调 java wrapper）
```

## 4. 遗留 / 下一步

- **未 commit / push**（纪律要求，需用户单独授权）。本片改动 = 新增 4 个测试文件 + 修改 `SearchViewModel.kt` / `SearchIndexRepository.kt` / `SearchTokenAggregator.kt` / `SearchCoveragePolicyTest.kt` / `SearchViewModelTest.kt`（git status 中 search 相关项）。
- `app/schemas/.../12.json` **untracked**：任何涉及 Room 迁移的提交必须把它一并入库。
- 真机端到端（搜索 → 点命中 → 临时查阅 → 返回，含 display 命中落位准确性）**已验收 PASS**（2026-09-11，见复验报告）；遗留未覆盖项见该报告 §6（display 反查边界场景、EPUB 双通道、`preview_only`/`failed` 覆盖率状态等）。
- `resolveLegacyOffset` 仍无 JVM 直测（依赖 DAO/解析器/磁盘）——S1.4 已声明的最大验证缺口，跳错位置不会崩溃，优先级由 owner 定。
- R2-D2 等 user 二选一（A/B），阻塞中；R3–R6 未开工。
- 清理：stray 日志 `android/test-debug-unit.log` / `test-isolated.log` 已删除。

## 5. 关键文件索引

- 实现：`feature/search/SearchTokenAggregator.kt`（含 `DisplayChannelIndexer` / `IndexUnit`）、`feature/search/SearchTokenizer.kt`、`data/repository/SearchIndexRepository.kt`、`ui/viewmodel/SearchViewModel.kt`
- UI：`ui/screen/search/SearchScreen.kt`
- 测试：`app/src/test/java/com/creationreadingassistant/feature/search/*.kt`、`ui/viewmodel/SearchViewModelTest.kt`
- 项目记忆：`.workbuddy/memory/2026-09-11.md`、`.workbuddy/memory/MEMORY.md`
