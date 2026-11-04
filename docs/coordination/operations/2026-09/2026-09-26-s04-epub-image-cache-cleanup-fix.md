# S04 单书 EPUB 图片缓存清理：窄范围修复

- **日期**：2026-09-26
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支 / HEAD**：`codex/recover-worktree-20260920` / `5aed593`（全程未变）
- **依据**：`docs/coordination/reviews/2026-09/2026-09-26-s04-final-cleanup-integration-review.md` §2.1
  （EPUB 图片缓存目录不被单书清理回收 —— 真实空间泄漏）
- **性质**：离线实现 + JVM 定向测试。**未运行 adb / MuMu / 真机 / instrumentation**；
  未操作书库；未 commit / push / stage / reset / checkout / clean。

---

## 1. 改动清单（仅两个文件，均在授权范围内）

| 文件 | 改动 | sha256 |
|---|---|---|
| `data/repository/BookRepository.kt` | `deleteBookContentFiles` 增加第 ③ 处清理位置 `books/epub/<id>/`；`scanBookContentResidue` 同步增加该目录的预检；抽出局部 `clearDirSparingResidue` 统一三处语义 | `1e3aadb669b8cbaf22aa852b11ac1c06a4f75f35ee5b4af6d048063c0b368060` |
| `data/repository/BookResidueSafeCleanupTest.kt` | 新增 4 条定向测试（25 → **29** 例） | `99bbb6033f902230628fe5af956fd0d007ae093ec8db0e2663c9748df6db835c` |
| `docs/coordination/operations/2026-09/2026-09-26-s04-epub-image-cache-cleanup-fix.md` | 本报告 | — |

**未改动**：`ShelfBookActions.kt`、`StorageSubPage.kt`、`OfflineBooksResidueClear`、
任何 Gradle / manifest / 其他窗口文件。**未新增接缝、未改公共 API 签名。**

---

## 2. 缺陷与修复

### 2.1 缺陷（修复前）

`EpubParser.kt:242` 在渲染含图章节时创建 `books/epub/<id>/img/` 并写入抽取的图片。
而 `clearBookCache` 的 EPUB 分支**只删 `<id>.epub` 这一个文件**，该目录被留成无主文件
（DB 指针已清空 ⇒ 不可达），**空间不释放** —— 与「清缓存释放空间」的承诺不符。

### 2.2 修复

**（a）删除阶段**：在 `deleteBookContentFiles` 中把 `books/epub/<id>/` 纳入清理，
复用既有残留安全删除器 `deleteTreeSparingResidue`。

**（b）预检阶段**：`scanBookContentResidue` 同步增加该目录的递归预检
（`scanDirForResidue`），使**预检与删除处理的位置一一对应** ——
避免出现「预检说干净、删除器才发现残留」这种只能靠兜底的情况。

**（c）语义统一**：把三处位置的结果折叠逻辑抽成局部函数 `clearDirSparingResidue`，
使 TXT/MD 目录、图片缓存目录共用**同一套**安全语义（而不是复制三份判断）。

**（d）执行顺序**：图片缓存目录刻意排在删除 `<id>.epub` **之前** ——
若在图片目录处中止，更重要的正文副本仍被保留。

### 2.3 安全性论证

| 要求 | 落实 |
|---|---|
| 只处理目标书 ID 对应目录 | 路径为 `books/epub/<id>/`；`books/epub/` 下其他条目一概不碰（有专测） |
| 删除与 DB 写入前做残留/不可靠扫描检查 | 新增预检；`clearBookCache` 的两处 `return` 仍在任何 DB 写入之前 |
| 删除阶段复用残留安全删除器 | `deleteTreeSparingResidue`，未新造删除逻辑 |
| 新出现残留或不可读必须中止，且不清空 DB 指针 | 复用既有 `abortCause` → `Aborted` → `AbortedMidway` 路径，DB 写入在其后 |
| 普通文件删除失败须如实返回 `ClearedWithRemains` | 复用 `COMPLETED_WITH_REMAINS → remainsAfterAttempt` 映射（有专测） |
| 保持 TXT/MD、EPUB 同级标记、清空离线、既有 DB 写入语义 | 三处路径的既有测试全部保留并通过（§4） |

---

## 3. 新增测试（4 条）

| # | 用例 | 断言 |
|---|---|---|
| 1 | `clearBookCache reclaims clean epub image cache directory` | 干净图片目录被**回收**（`img/` 及其中图片均不存在）；`<id>.epub` 亦删；DB 指针清空 |
| 2 | `clearBookCache preserves epub image cache and db when residue inside it` | 目录内出现 `.backup` ⇒ `RefusedResidue(1)`；残留、图片目录、`<id>.epub` **全部保留**；三张 DAO 表 `upsert` **零调用** |
| 3 | `clearBookCache only touches the target book image cache directory` | 目标书 `mine/` 被回收；**其他书 `other/` 及其图片原样保留**（关键边界） |
| 4 | `clearBookCache reports remains when epub image cache deletion fails` | 图片被占用 ⇒ `ClearedWithRemains`（**不得**报完全成功）；被占用图片保留；DB 指针照常清空 |

用例 4 与既有两条「删除失败」用例一致，以 `Assume.assumeTrue(os.name contains Windows)`
门控（依赖 Windows 独占删除语义）；本次在 Windows 上运行，`skipped=0`。

### 3.1 变异验证（证明新测试真的能抓到回归）

把修复段替换回「不回收图片缓存目录」（`// MUTANT`），重跑：

```
BookResidueSafeCleanupTest > clearBookCache only touches the target book image cache directory FAILED
BookResidueSafeCleanupTest > clearBookCache reclaims clean epub image cache directory FAILED
BookResidueSafeCleanupTest > clearBookCache reports remains when epub image cache deletion fails FAILED
29 tests completed, 3 failed
```

**恰好是被修复行为影响的那 3 条失败**，其余 26 条不受影响。还原后 29 例全绿
（`sha256sum` 核对与变异前一致、`grep -c MUTANT` = 0）。

> 调试过程中曾有 1 次失败：`only touches the target book…` 因**我的测试夹具**未先建
> `img/` 父目录而抛 `FileNotFoundException`。已修正夹具（非生产代码问题）。

---

## 4. 命令、退出码与测试数

工作目录 `…/creation-reading-assistant-recovery-20260919/android`，全部 `--offline`。

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `./gradlew :app:testDebugUnitTest --tests "…BookResidueSafeCleanupTest" --offline` | **0** | 29 例通过（变异时 1 次 EXIT 1，见 §3.1） |
| 2 | `./gradlew :app:testDebugUnitTest --tests "…data.repository.*" --tests "…ShelfBookActionsTest" --tests "…ShelfViewModelTest" --tests "…ShelfSourceRefreshFeedbackTest" --offline` | **0** | **224 例，0 失败 0 错误 0 跳过**（17 个类） |
| 3 | `./gradlew :app:compileDebugKotlin --offline` | **0** | BUILD SUCCESSFUL（确认主源码可编译，未触发跨盘 KSP/Hilt 问题） |

**回归明细**（#2）：`BookResidueSafeCleanupTest` 29 / `BookRepositoryTest` 23 /
`SyncRepositoryTest` 21 / `TaxonomyRepositoryTest` 21 / `SearchIndexRepositoryRebuildConcurrencyTest` 19 /
`ShelfViewModelTest` 21 / `ShelfBookActionsTest` 18 / `ShelfSourceRefreshFeedbackTest` 14 /
`SearchIndexWorkerPolicyTest` 12 / `NoteRepositoryTest` 11 / 其余 7 类合计 35。

**构建产物位置**：本次只跑**定向 JVM 测试**与 `compileDebugKotlin`，
产物落在默认 `android/app/build/`（增量很小）。

> **未使用 E 盘重定向**：任务指明「不要使用已知会在 KSP/Hilt 跨盘失败的
> `redirect-build.gradle`」。已核实该脚本的实现方式是
> `project.layout.buildDirectory.set(new File('E:/…'))` —— 把整个构建目录指向另一盘符，
> 正是跨盘失败的来源。**完整 APK 构建由集成者随后串行进行**，本窗口不参与。

---

## 5. 未覆盖边界

1. **未运行真机 / MuMu / instrumentation**：本修复的**设备侧行为零验证**。
   尤其「真机上图片目录的实际体积占比」与「清缓存后释放量」未测量。
2. **未构建完整 APK**：由集成者串行执行。
3. **`AbortedMidway` 仓储层映射仍无直接测试**（审查报告 §2.2 的覆盖缺口）——
   **本次未补，理由如下**（这是刻意的，不是遗漏）：
   - 已核对源码：预检 `scanDirForResidue` 与删除 `deleteTreeSparingResidue`
     使用**同一套判定谓词**，两者之间不存在仅靠文件系统可达的状态差异；
   - 因此在仓储层确定性触发 `AbortedMidway` **必须**在「预检之后、删除之中」注入状态变化，
     即需要**新增接缝**（如把 `listFiles()` 或删除器抽成可注入参数）；
   - 任务明确「若需扩大设计，请记录为待办，**不为凑测试引入复杂接缝**」⇒ 记录为待办。
   - 现状：`deleteTreeSparingResidue` 的三种中止结果**已有单测**（`sparing deleter …` 三条），
     缺的只是「仓储把它翻译成 `AbortedMidway` 并拦截 DB 写入」这一层的直接断言；
     `ShelfBookActionsTest` 已从消息侧覆盖该 outcome 的文案。
4. **`OfflineBooksResidueClear`（清空离线）未改动**：它本就能回收该目录
   （目录落入 `file.isDirectory` 分支被整棵删除）。两条路径**行为现已一致**，
   但**测试未覆盖两者的等价性**（无跨路径一致性断言）。
5. **非 Windows 主机**：用例 4 会被 `Assume` 跳过，该失败分支在 Linux/macOS 上不被执行。
6. **D 盘剩余空间约 36 GB（88% 已用）**：本次增量构建未造成压力，但后续完整构建建议留意。

---

## 6. 对集成者的交接

1. **E 盘 APK `24525998F69AB41A7FB1F6EDEA1E26783D0C4F45C922D87C4A575B0D95D0794B`
   已因本次源码修改失效**，**不得**用它证明本修复已验收 —— 需重新构建。
2. 本次改动**只影响 `clearBookCache`（单书/批量清缓存）的删除文件集合**：
   新增回收 `books/epub/<id>/`。**不影响**导入、替换、残留守卫、清空离线与 DB 语义。
3. 建议集成者重构建后，优先复验：
   - 含图 EPUB 执行「清缓存」→ 确认 `books/epub/<id>/` 目录消失、空间释放；
   - 目录内预置 `.backup` → 确认整本拒绝、DB 指针不动。
4. 待办（非本窗口）：`AbortedMidway` 仓储层接缝（§5-3）；两条清理路径的等价性断言（§5-4）。
