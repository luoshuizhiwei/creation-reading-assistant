# R2-S1.9 规则变更→索引重建（P1）与 Android 端检查点提交

**日期**: 2026-09-11
**执行人**: WorkBuddy（洛水之蔚 协作会话）
**基线**: `e31db92`（提交后 HEAD `21c5dbe`）
**范围**: `android/**` 的 `feature/search` + `ui/viewmodel` + `data/repository` + 规则接线
**状态**: **Dev-verified**（`testDebugUnitTest` 全量绿）+ **已本地提交（未 push）**

> 本报告只覆盖本轮收口。S1.6 双通道搜索见 `workbuddy-r2-s1-6-*.md`；P0 索引分片续建见
> `workbuddy-r2-s1-7-index-sweep-fix.md` / `workbuddy-r2-s1-8-handoff-to-next-agent.md`。

---

## 0. 对前一份交接的更正（重要）

`workbuddy-r2-s1-8-handoff-to-next-agent.md` §6.4 写「**P1（替换规则变更不触发 display 重建）本轮未处理**」——
**该条已作废**：本轮已按用户裁决实现并验证，见 §2.1。

同时更正 §一 早期清单里「P0-b 仅剩真机复验」：**P0-b 已由另一 WorkBuddy 会话真机终验 PASS**
（26/26 活跃书覆盖、锚点达末本 `#25`、`#22`（10.3MB 最大 EPUB）1774/1774 章建完、`search_terms`
2954 万行、`search_index_state=SUCCEEDED`），本机不再保留该 TODO。

---

## 1. 用户裁决（本轮前置）

| 决策点 | 用户选择 |
|---|---|
| R2-D2（`REMOVE_CONTENT` 语义） | **A 真删文件** |
| P1（规则变更→索引重建） | **按建议实施**：PER_BOOK 立即重建本书；GLOBAL 置脏 + 后台惰性全库 |
| 提交 | 授权本地 commit（**不含 push**） |
| §二 大小写敏感 | **加非行为提示**（不做行为改动） |
| 提交范围（二次裁决） | **B1 全 android 检查点** |

---

## 2. 改动清单

### 2.1 P1：规则变更 → 搜索索引刷新

**问题**：替换规则改变后，display 通道的旧索引行即失真，而游标单向 ⇒ **后加的规则永不生效**。

**实现**（4 处）：
- `feature/reader/rules/RulesRepository.kt`：新增 `data class RuleDescriptor(kind, scope)` 与
  `suspend fun describeRule(ruleId): RuleDescriptor?`。**必须在 `execute` 删行之前调用** ——
  删除类命令执行后规则行已不在，无从再查其类型/作用域。
- `ui/viewmodel/ReaderViewModel.kt`：注入 `SearchIndexRepository` / `SearchIndexScheduler` /
  `@ApplicationScope CoroutineScope`；新增 `SearchIndexRefreshPlan(global)`、
  `searchIndexRefreshPlan(command)`、`replaceRulePlan(ruleId)`、`applySearchIndexRefresh(bookId, plan)`。
  `ExecuteRuleCommand` 分支：**先算 plan，再 `execute`**，仅 `Success`/`Saved` 后 apply。
- `data/repository/SearchIndexRepository.kt`：新增
  `reindexBookById(bookId)`（→ `indexSingleBook`）与 `invalidateSweepForFullRebuild()`
  （重置 `search_index_state`：tokenizer_version + 清锚点 + `built_at=0`）。
- `data/repository/SearchIndexScheduler.kt`：`start()` 拆为 `start()` = `enqueueOnce()` + `enqueuePeriodic()`；
  **`enqueueOnce()` 改 public** 供 P1 复用（`ExistingWorkPolicy.KEEP` 保证不重复插入）。

**口径**：只有 **REPLACE** 规则影响索引（索引按内置 TOC 分章，TOC 规则只改目录，不参与分章）。
**线程**：一律 `appScope.launch`，**绝不在阅读器交互路径同步重建**（全库重建可达数十分钟 / 数 GB）。

### 2.2 §二 非行为提示
- `feature/search/SearchTokenizer.kt`：文件头 docstring 示例修正为与实现一致
  （`第/3/章/这是/是一/一本/本中/中文/文小/小说/说/hello/v2/0`），并注明「数字不与相邻汉字成 Bigram」
  「latin 连续段整体成词」。**不改分词口径**（会波及已建索引）。
- `ui/screen/reader/sheets/ReaderRulesSheet.kt`：替换文本框下新增提示——规则区分大小写（正则默认），
  英文请按原文大小写填写；搜索命中词按小写归一，故词面不一致时可能显示「命中 0 处」。

### 2.3 R2-D2（A 真删文件）
- `data/repository/BookRepository.kt`：注入 `@ApplicationContext Context`；
  `clearBookCache(id)` → 私有 `deleteBookContentFiles(id)`，`runCatching` 删
  `filesDir/books/<id>` 与 `filesDir/books/epub/<id>.epub`。
- 测试构造补 `context = mockk(relaxed = true)`：`BookRepositoryTest`、`DeletionLibraryFixture`。

### 2.4 §一-#5（前序）：`resolveLegacyOffset` JVM 可测化
- 新增纯函数 `feature/search/SearchOffsetResolver.kt`（`resolve(...)`）；仓储方法改为委托，
  删私有 `mapDisplayOffset` 与无用 `RuleEngine` import；新增 `SearchOffsetResolverTest`（11 tests）。
- ⚠️ `TextOffsetMap.toSource` 是 **floor 语义**：删除段之后的显示偏移会塌回删除起点。

---

## 3. 验证

| 项 | 结果 |
|---|---|
| 命令 | `testDebugUnitTest --no-daemon`（Bash 直调 java 跑 wrapper，`dangerouslyDisableSandbox:true`） |
| 退出码 | **EXIT=0，BUILD SUCCESSFUL**（5m11s） |
| 测试 | **237 suites / 2016 tests / 0 failures / 0 errors / 0 skipped** |

---

## 4. 提交

- **`21c5dbe`** `chore(android): checkpoint R2 search dual-channel, index sweep and reader wiring`
  （parent `e31db92`，**非孤立**）；**148 files / +20707 / −582**。
- 暂存使用**显式 pathspec**：`git add android/app docs/plans/android-parallel-delivery-2026-09-09/reports`
  （**未用 `git add -A/.`**）。
- **未纳入**：`electron/**`、`src/**`（桌面端 TS）、`android/` 根目录 ~130 个 adb 取证临时产物
  （`*.xml` / `*.png` / `*_out.txt`）、`.codex/config.toml`、`cra2.db`、`.workbuddy-ai/`。
- **未 push**。
- 复核：`git rev-list --parents -n1 HEAD` 含父提交；`git show --name-only` 无目标目录外路径、无散件。

### 4.1 为什么是「整端检查点」而不是「S1 切片」
尝试按切片隔离提交时实测：**同包（同目录）符号不需要 `import`**，用 import 反查依赖闭包会**漏掉同包兄弟文件**
（例：`feature/library/deletion/` 9 个文件里只能反查到 2 个）。因此「只提交搜索切片」无法得到可编译快照；
`SearchIndexRepository` 与 Codex 的 DAO/schema、`ReaderViewModel` 与 Qoder 的删除切片均物理同文件。
最终按仓库既有 `chore(android): checkpoint ...` 约定提交整端检查点。

---

## 5. 未完成 / 移交

- **push**：未执行，需单独授权。
- **`#21 epub_pc57ry` 停在 783/784 章**：旧锚点语义残留，非本轮回归，交集成者裁决。
- P1 的 GLOBAL 路径会触发**全库重建**（小时级 / 数 GB）；建议集成者评估是否加 UI 提示
  或改为「仅重建受影响书」的增量策略。
- R3–R6 未开工。
- 未覆盖：display 反查边界场景、EPUB 双通道、`preview_only`/`failed` 覆盖率状态。
