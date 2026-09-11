# Qoder R1 报告：书籍删除与撤销完整性

状态：**代码完成，JVM 定向回归全绿；真机未验收**（无安装与视觉证据，见 §9）。
本轮不自动开 R2。

---

## 0. 基线、工作区与环境

| 项 | 值 |
|----|----|
| 工作树 | `D:/develop/Code/Codex/cra-android-qoder-r1` |
| 分支 | `codex/android-qoder-r1` |
| HEAD | `e31db92`（等于要求基线；**未新增提交、未 stage、未 commit、未 push、未切分支**） |
| 原项目目录 | 未修改（`D:/develop/Code/Codex/creation-reading-assistant` 全程只读，未被写入） |

工作区状态（`git status --porcelain`，全部落在 QODER.md 授予本人的路径内）：

```text
 M android/app/src/main/java/com/creationreadingassistant/data/repository/BookRepository.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/readinghistory/MyReadingRoute.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfRoute.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfSearchRoute.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ShelfBookActions.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ShelfViewModel.kt
?? android/app/src/main/java/com/creationreadingassistant/feature/library/deletion/
?? android/app/src/main/res/values/strings_deletion.xml
?? android/app/src/test/java/com/creationreadingassistant/feature/library/deletion/
```

未回滚、未覆盖、未 `clean`、未 `stash` 任何既有改动。

环境：`JAVA_HOME=D:\develop\Java\jdk-17.0.14`（已有）。工作树不含 `local.properties`，`ANDROID_HOME`/`ANDROID_SDK_ROOT` 原本为空；SDK 位于 `D:\develop\Android\Sdk`，**仅以命令行环境变量的方式**传入 `gradlew`，没有创建或修改 `local.properties`、`gradle.properties` 或任何构建脚本来补环境路径，没有复制任何密钥。

Gradle 串行：开工前确认共享 daemon（PID 42564）空闲——上一次构建 21:45 结束，此后日志只有 10 秒一次的注册表心跳，无任务在执行；本轮同一时间只有一个 Gradle 构建在跑，未启动第二个全量构建，未杀任何 Java 进程。

---

## 1. 先确认的缺陷（基线行为，已用行为测试复现）

QODER.md 指出的缺陷在基线 `e31db92` 上确认成立，且比描述的更不对称：

1. `BookRepository.deleteBook` 在一个事务里软删 `books` / `reading_progress` / `reading_sessions` / `notes` / `highlights` / `book_files`，把 `book_content.reader_preview` 与 `epub_json` 置空，并**硬删** `book_tag` / `book_category` / `shelf_book` / `chapter_reads`。这四张连接表没有 `deleted_at` 列，硬删之后库里不留任何痕迹，事后无从推断原来有什么关联。
2. `BookRepository.restoreBook` 只清 `books` / `reading_progress` / `reading_sessions` / `notes` / `highlights` / `book_files` 的 `deleted_at`，并且**按 bookId 捞起全部软删行**——用户在这次删书之前单独删掉的笔记、高亮、阅读会话会被一起复活。
3. 恢复侧对连接表、已读章节、正文缓存**完全不做任何事**。

净效果：撤销既复活了不该复活的（历史已删项），又丢掉了该恢复的（分类/标签/书单/已读章/正文缓存）。这不是文案问题，已按实现修复。

复现方式见 §4：`BookDeletionRestoreTest` 断言的是**书库的最终状态**。同样的断言放在基线实现上必然失败——例如撤销后 `tagIds/categoryIds/shelfLinks/chapterIndexes` 应全部回来（基线回不来），带旧戳的高亮应保持删除（基线会复活）。

---

## 2. 改动路径

### 2.1 修改（6 个文件，均在所有权内）

| 路径 | 变更要点 |
|------|----------|
| `data/repository/BookRepository.kt` | 新增作用范围快照捕获与恢复：`deleteBookScoped` / `deleteBooksScoped` / `captureDeletionSnapshot` / `applyDeletion` / `restoreDeletion` / `restoreDeletions`；`deleteBook` 改为委托 `deleteBookScoped` 并丢弃快照（既有调用方零改动即获得正确作用范围）；`restoreBook` 收敛为按级联戳恢复。**构造函数签名一字未改**（仍是同样 12 个 DAO），既有 `BookRepositoryTest` 原样编译通过。 |
| `ui/viewmodel/ShelfBookActions.kt` | 尾部新增可选依赖 `deletions: BookDeletionCoordinator? = null`；`deleteBook`/`deleteBooks` 在有协调器时整批走一次调用（一次事务、一张凭证），无协调器时保持原逐本委托语义；新增 `undoDeletion` / `dismissDeletionUndo`。其余方法未动。 |
| `ui/viewmodel/ShelfViewModel.kt` | 尾部新增可选注入 `deletions: BookDeletionCoordinator? = null` 并透传给 `ShelfBookActions`；暴露 `deletionUndoOffers` / `undoWindowSeconds` / `deleteBooks` / `undoDeletion` / `dismissDeletionUndo` / `pruneDeletionUndo`。既有方法签名未变，`ShelfViewModelTest` 的按名构造与断言原样通过。 |
| `ui/screen/shelf/ShelfRoute.kt` | 底部微岛改为 `Column`：撤销提示条在上、批量操作栏在下，互不遮挡；删除确认框改用按作用范围出文案的 `DeletionCopy`，整批走 `viewModel.deleteBooks(ids)`；`ShelfAction.ConfirmDelete` 走同一条路径。 |
| `ui/screen/shelf/ShelfSearchRoute.kt` | 搜索结果页接入同一个 `DeletionUndoBar`（撤销结果经 Snackbar 反馈）。 |
| `ui/screen/readinghistory/MyReadingRoute.kt` | 接入 `DeletionUndoBar` 与 `DeletionUndoViewModel`；删除确认框改用作用范围文案。原先那句不准确的「此操作不可撤销」已删除。 |

`diff --stat`：6 files changed, 696 insertions(+), 80 deletions(-)。

### 2.2 新增：`feature/library/deletion/`（9 个文件，943 行）

| 文件 | 行 | 职责 |
|------|----|------|
| `DeletionScope.kt` | 62 | `SHELVE` / `REMOVE_CONTENT` / `DELETE_BOOK` 三种作用范围 + `retention()` 投影（是否保留书籍资料/本地正文/阅读数据/关联、是否会话内可撤销）。文档明确「移除正文不是备份，也不构成可靠恢复」。 |
| `BookDeletionSnapshot.kt` | 142 | 一次操作的作用范围快照；`BookRestoreReport`（逐项恢复计数 + 让路计数 + 失效关系目标 + 正文载荷是否被丢弃）；`LiveRelationTargets`；`applyContentBudget`（正文缓存预算收敛）。 |
| `DeletionUndoPolicy.kt` | 70 | 有效期 12s、合并窗口 1.5s、最多 4 张凭证、单张最多 4,000,000 字符正文缓存；`DeletionClock` 时钟抽象；`BookDeletionCredential` / `DeletionUndoOffer`。 |
| `DeletionUndoStore.kt` | 197 | 会话内凭证登记处（**纯内存**）：`publish`（含连发合并与预算重算）、`findLive`（只看不取，失败可重试）、`consume`（取走即移除 → 重复撤销天然幂等）、`dismiss` / `prune` / `clear`；`offers: StateFlow` 最新在前。 |
| `BookDeletionCoordinator.kt` | 133 | UI 唯一对话对象：把一次入口操作收敛成一张凭证；撤销前解析当前仍活跃的关系目标；把恢复结果整理成可措辞的结构。`DeletionUndoFailure { UNAVAILABLE, RESTORE_FAILED }`。 |
| `DeletionModule.kt` | 26 | Hilt 提供 `DeletionUndoPolicy` 与 `DeletionClock`（`System.currentTimeMillis()`）。 |
| `DeletionUndoViewModel.kt` | 55 | 供未持有 `ShelfViewModel` 的入口（阅读历史页）使用。 |
| `DeletionUndoBar.kt` | 148 | 三个入口共用的撤销提示条：250ms 刷新倒计时，归零回调 `onExpired` 触发 `prune`；`offer == null` 时不渲染任何内容；触控目标 48dp；尊重减少动态效果设置。testTag：`deletion-undo-bar` / `deletion-undo-action` / `deletion-undo-dismiss`。 |
| `DeletionCopy.kt` | 114 | 按作用范围出确认框标题/正文/按钮与撤销结果文案。基于 `Context` 而非 `@Composable`，因此可以在 Snackbar 回调等非 composable 位置调用。 |

### 2.3 新增资源

`res/values/strings_deletion.xml`（23 条）。仓库现有 `res/values/` 只有 `colors.xml` / `strings.xml` / `themes.xml`，**没有任何 locale 变体目录**，因此未创建同名 locale 文件（凭空建 `values-en` 会与既有单语言资源约定不一致，且 `strings.xml` 归 Codex）。未改动既有 `strings.xml`。

### 2.4 新增测试

`android/app/src/test/java/com/creationreadingassistant/feature/library/deletion/`（5 个文件，1598 行），命名符合 `*Deletion*` / `*Restore*` 要求：

| 文件 | 行 | 用例数 |
|------|----|--------|
| `DeletionLibraryFixture.kt` | 388 | —（内存书库夹具） |
| `BookDeletionRestoreTest.kt` | 399 | 12 |
| `DeletionUndoCredentialTest.kt` | 347 | 16 |
| `BookDeletionCoordinatorRestoreTest.kt` | 327 | 12 |
| `ShelfBookDeletionDelegationTest.kt` | 137 | 7 |

---

## 3. 需求完成对照

### 3.1 需求 1：一次删除操作的明确作用范围 — 完成

- 作用范围的载体是 `BookDeletionSnapshot`，由 `captureDeletionSnapshot` / `buildDeletionSnapshot` 产出。
- **捕获与写入在 `bookDao.runInTransaction` 的同一个事务里完成**（`deleteBookScoped`：先 capture 再 applyDeletionLocked），所以快照严格等于「本次操作前处于活跃状态」的资料与关系，既不漏并发写入，也不会把此前已删的算进来。
- 快照只读活跃行：`bookDao.getById`、`progressDao.getByBook`、`sessionDao.observeByBook`、`highlightDao.observeByBook`、`noteDao.observeAllActive`、`bookFileDao.getByBook` 全部带 `deleted_at IS NULL` 过滤；连接表本身没有软删列，读到的就是当前全部关系。
- **级联戳**：同一次操作影响到的所有软删行写入同一个 `deletedAt` 字符串。这让没有快照的旧路径 `restoreBook` 也能按戳精确恢复，不再按 bookId 笼统复活历史已删项。

### 3.2 需求 2：事务一致性 + 恢复项覆盖 — 完成

删除侧（`applyDeletionLocked`）与恢复侧（`restoreDeletions`）各自一个事务；批量删除是**一次事务**处理整批，批量撤销同样是**一次事务**处理整批。

| 数据 | 删除 | 撤销恢复 |
|------|------|----------|
| `books` 资料行 | 软删（打戳） | 恢复；行已从库中消失时用快照重新插入 |
| `reading_progress` | 软删 | 恢复（含 `progress_percent` / `current_location_json` / `completion_state`） |
| `reading_sessions` 阅读记录 | 软删 | 逐条按 id 判定后恢复 |
| `notes` 笔记 | 软删 | 逐条按 id 判定后恢复 |
| `highlights` 高亮 | 软删 | 逐条按 id 判定后恢复 |
| `book_files` 文件关联 | 软删 | 恢复 |
| `book_content` 正文缓存 | 载荷置空（行保留） | 载荷写回；超预算被丢弃时如实报告，不假装恢复 |
| `book_tag` 标签关联 | 硬删 | 由快照恢复（唯一的恢复依据） |
| `book_category` 分类关联 | 硬删 | 由快照恢复 |
| `shelf_book` 书单关联 | 硬删 | 由快照恢复，**保留原 `position`** |
| `chapter_reads` 已读章节 | 硬删 | 由快照恢复，保留原 `read_at` |

`restoreDeletions` 把约 15 次批量读全部提到事务开头一次做完（`getByIds` / `getDeleted` / `getByBooks` / `getPreviewsByBookIds` / `getAllActive` / `getAll`），避免逐本 N+1，也避免在事务内反复订阅 Room Flow。

`inspirations` 不在删除路径内：`books` 是软删（UPDATE），`ON DELETE SET NULL` 外键不会触发，`inspirations.source_book_id` 原样保留，撤销后关联自动仍在。确认框文案已按这一真实行为改写（原先写「只会解除与这本书的关联」是不准确的）。

### 3.3 需求 3：不覆盖合法新修改 / 冲突处理 / 幂等 / 批量范围对应 — 完成

**不覆盖新修改**——`restoreOneSnapshot` 里三条子表（会话/笔记/高亮）共用同一个判定，避免规则各自漂移：

```kotlin
// 当前活跃，或被别的戳软删 → 本次删除之后有人动过，快照让路；
// 带本次戳，或库里已无此行 → 恢复，但必须有书籍主行兜住外键。
fun restorable(activeNow: Boolean, currentStamp: String?): Boolean = when {
    activeNow -> { skippedNewer++; false }
    currentStamp == snapshot.deletedAt -> bookRowPresent
    currentStamp != null -> { skippedNewer++; false }
    else -> bookRowPresent
}
```

书籍主行另有一层：若它带着**别的**戳（本次删除之后又被另一次删除覆盖），直接让路并返回，不去撤掉那一次。进度、文件、正文缓存同规则（正文缓存若已被重新缓存过，保留新缓存）。

**关系目标已删除时的冲突处理**——撤销前由 `TaxonomyRepository` 解析当前仍活跃的标签/分类/书单 id 集合（`LiveRelationTargets`），恢复时 `partition`：活跃的写回，已消失的进 `skippedRelationTargetIds`，**不写孤儿关系**，并在文案里如实说出「N 个分类/标签/书单已不存在，未恢复这些关联」。活跃集合必须在进入事务前解析——事务内读 Room Flow 拿不到一致快照。

**不产生重复关系**——连接行与已读章节都先 `filterNot { 当前已存在 }` 再按主键 REPLACE 写回，因此重复撤销不会翻倍，也不会把用户在撤销前重新添加的关联改回旧排序（`shelf_book.position` 以库中现值为准）。

**重复撤销幂等**——`consume` 取走即从登记处移除；第二次撤销拿到 `UNAVAILABLE` 且不写任何数据。撤销顺序是 `findLive`（只看不取）→ 写库 → 成功后才 `consume`，所以恢复事务失败时凭证仍在有效期内，用户可以重试，而不是白白烧掉一次机会。

**批量撤销范围与入口提示对应**——两层保证：
1. 本人所有的入口一律走 `deleteBooksScoped(ids)`：一次事务、一批快照、**一张凭证**，`offer.bookCount` 就是提示里的数量；
2. 对仍是 `ids.forEach { deleteBook(it) }` 形式的非本人所有入口，登记处有 1.5s 合并窗口兜底（从 `updatedAtMillis` 起算，合并时顺延 `expiresAtMillis`，所以批量循环进行中不会中途断开），把连发删除并成一张凭证。合并时按 bookId 保留第一份**非空**快照——重复删同一本书时第二次捕获必然是空的，用空快照覆盖会丢掉真正的恢复依据。

### 3.4 需求 4：会话内凭证 + 明确有效期 + 不无界积累 — 完成

- 有效期 12s（`DeletionUndoPolicy.undoWindowMillis`）。确认框的「可在 N 秒内撤销」与提示条倒计时**同源**于 `coordinator.undoWindowSeconds`（`ceil(millis/1000)`），改策略不会让文案与实际窗口对不上；宿主没有协调器时该值为 0，文案里的撤销行直接不出现，不会谎称「可在 0 秒内撤销」。
- 有界积累：过期凭证在每次读写时清理；同时存活的凭证不超过 4 张，超出淘汰最旧的一张；单张凭证的正文缓存字符数不超过 4,000,000，超预算丢载荷（资料与阅读数据不可重建，不计入预算），撤销仍恢复资料与阅读数据，并如实报告「正文缓存需重新打开书籍后重建」。合并后的结果会**重新**过一遍预算，避免单批合规、合并后超预算。
- **纯内存，不落库**，因此本轮**不需要 schema 请求**。不承诺重启可撤销；进程重启后 `offers` 必然为空，`DeletionUndoBar` 在 `offer == null` 时不渲染任何内容 → 不会出现失效的撤销按钮。倒计时归零回调 `onExpired` → `prune()`，提示条随即消失，用户不会点到一个已经无效的撤销。

### 3.5 需求 5：三个入口一致 + 三种作用范围分得开 — 完成（有一处受所有权限制，见 §6.4）

- 书架（`ShelfRoute`）、书架搜索（`ShelfSearchRoute`）、阅读历史（`MyReadingRoute`）共用同一个 `DeletionUndoBar` 组件与**同一个单例凭证登记处**：在书架删的书，切到阅读历史页仍然能撤销，反之亦然。
- 三种作用范围在措辞上分得开：`DeletionScope` + `retention()` 决定文案，搁置说清「只改阅读状态，资料/正文/进度/记录/笔记/高亮/关联/已读章节都保留」，移除正文说清「只释放本地正文缓存与文件关联，其余保留，正文需要重新选择文件或重新下载」，删除整本资料说清全部会被移除并给出撤销窗口。
- **不把清缓存当成备份或可靠恢复**：`deletion_scope_remove_content_warning` = 「清掉的正文不是备份，无法从本地还原原文件内容。」
- 未改 `ShelfBookActions.clearCacheForBooks` 的返回文案「已清理 N 本书的本地正文缓存」：既有 `ShelfBookActionsTest` 断言这个精确字符串，而该测试文件不在本人所有权内。诚实性修复放在确认框 warning 上。

### 3.6 需求 6：D2（移除正文 / 重新关联）完整设计 — 完成（仅设计）

见 §8。**本轮未改导入器**（`feature/library/ShelfImporter.kt` 不在本人所有权内，且 QODER.md 明确 D2 由 R2 实施），只读它取证。

---

## 4. 测试

### 4.1 必要回归逐项对照

| QODER.md 要求的回归 | 覆盖用例 |
|---------------------|----------|
| 含分类/标签/书单/已读章书籍删除后撤销 | `delete removes every relation and undo puts them all back` |
| 已有软删除高亮不复活 | `highlights and notes deleted before this operation are not resurrected`、`legacy stamp-scoped restoreBook also leaves pre-deleted rows alone` |
| 批量与单本 | `batch delete captures one snapshot per book and undo restores exactly that batch`、`batch delete exposes one offer whose count matches the prompt and undoes the whole batch`、`a single delete is just a batch of one`、`duplicate and blank ids collapse so the credential matches the real batch` |
| 连续删除 | `consecutive deletes - undoing the earlier one does not cancel the later one`、`a burst of single deletes coalesces into one credential covering the whole batch`、`coalescing stops once the burst window lapses`、`a burst of single-book deletes still collapses into one undoable batch` |
| 重复撤销 | `repeat undo is idempotent and does not duplicate relations`、`repeat undo is refused and writes nothing` |
| 失败事务 | `failed delete transaction leaves the library exactly as it was`、`a failed restore rolls back, keeps the credential live and succeeds on retry` |
| 撤销期间关系变化 | `undo keeps legitimate changes made after the delete`、`undo skips relation targets that were deleted during the window`、`relation targets are resolved live at undo time, not at delete time` |
| 本次删除前无正文/无记录 | `a book with no content and no records before delete still round-trips`、`deleting a book that does not exist yields a credential with nothing restorable`、`an empty snapshot batch still yields a live credential with nothing restorable`、`a zero budget keeps every book restorable except its content cache` |
| 窗口过期 | `offer disappears exactly when the window lapses`、`prune drops expired credentials so the bar cannot linger`、`undo after the window expires is refused and the book stays deleted`、`consume rejects an expired credential even though it is still registered` |

另含：凭证上限（`credentials never accumulate beyond the cap`）、不同作用范围不合并（`different scopes never merge`）、重复删除保留信息量更大的快照（`merging keeps the informative snapshot when a book is deleted twice`）、合并后重算预算（`content budget is re-applied after a merge`）、`dismiss` 只放弃用户关掉的那一张、`clear` 全清、书籍行已从库中消失时由快照重新插入、正文载荷被丢弃时如实报告、`ShelfBookActions` 有/无协调器两条委托路径、策略参数非法值被拒绝、窗口秒数向上取整不低报。

### 4.2 测试手法（满足「真实行为测试」「不用原始 SQL 绕开所有权」）

- **断言对象是书库的最终状态，不是「某方法被调了几次」**。`DeletionLibraryFixture` 用 mockk 做 DAO 门面，背后是真实的 `LinkedHashMap` 集合，并按 Room 的真实查询语义建模三件事：
  1. 软删除过滤——`getById` / `getByBook` / `observe*` 只返回 `deleted_at IS NULL` 的行，`getDeleted()` 只返回有戳的行；`ReadingSessionDao.getByIds` 例外，它刻意不过滤，恢复逻辑靠戳区分「本次删的」和「之后动过的」；
  2. 连接表硬删除——`clearByBook` / `clearForBook` 真的把行删掉，库里不留痕迹（这正是「没有快照就恢复不了关联」的根因）；
  3. 事务原子性——`runInTransaction` 在 block 抛错时回滚到进入前的状态（比 Room 接口默认实现更忠实：默认实现只是 `block()`，没有回滚）。夹具提供两个失败开关，分别让删除事务与恢复事务在**最后一步**抛错，用来验证不留半截状态。
- 被测对象是生产代码本体：`BookRepository` / `BookDeletionCoordinator` / `DeletionUndoStore` / `ShelfBookActions`。**没有**反射、**没有**原始 SQL、**没有**临时数据库副本、**没有**复制第二套业务实现，全部通过既有仓储与 DAO 接口完成。
- 时钟用 `DeletionClock` 抽象注入，过期与合并行为在 JVM 上可精确推进，不靠 `Thread.sleep`。

### 4.3 本轮由测试抓到并修掉的真实缺陷

1. **`DeletionUndoStore.consume` 留下失效提示条**：凭证已过期时 `pruneLocked` 先把它清掉，随后 `indexOfLast` 返回 -1 并**提前 return，没有刷新 `offers`**，于是 `StateFlow` 会继续广播一张已经撤销不了的凭证，撤销条留在界面上点了没反应。由 `consume rejects an expired credential even though it is still registered` 抓到，已在早退分支补 `publishOffersLocked(now)`。
2. **`pluralsResource` 不存在**：撤销条文案原写成 `<plurals>` + `androidx.compose.ui.res.pluralsResource`，该 API 实名是 `pluralStringResource`，编译期报 `Unresolved reference`。中文没有复数形态，`<plurals>` 本身也没有收益，已改为普通 `<string>` + `stringResource`，与仓库既有资源约定一致。
3. **`runCatching` 吞掉协程取消**：`ShelfViewModel.deleteBooks` 与 `DeletionUndoViewModel.deleteBooks` 原用 `runCatching`，它会把 `CancellationException` 也当成「删除失败」吞掉，让已经取消的作用域继续跑并回调 UI。改为显式 `try/catch` 并重新抛出取消。
4. **删除成功与否和凭证是否还在被混为一谈**：删除接口原返回 `DeletionUndoOffer?`，于是「数据确实删掉了，但凭证被新删除挤出上限」会被报告成删除失败——那是说谎。改为返回 `Boolean`，只表示删除本身。
5. **确认框谎报撤销窗口**：宿主没有协调器时 `undoWindowSeconds` 为 0，原逻辑会渲染出「可在 0 秒内撤销」。加了 `&& undoSeconds > 0` 守卫。
6. **搁置的确认按钮文案错拿成对话框标题**（`deletionConfirmAction(SHELVE)` 返回了 title），已补 `deletion_scope_shelve_confirm`。
7. **`sessionDao.getByIds(emptyList())` 未加保护**：SQLite 不接受空 `IN ()`。已加 `if (sessionIds.isEmpty()) emptyMap()` 分支，并用 `coVerify(exactly = 0) { sessionDao.getByIds(match { it.isEmpty() }) }` 锁死。
8. **灵感关联文案不准确**：见 §3.2 末段。

### 4.4 兼容性与 Hilt

- `BookRepository`、`ShelfBookActions`、`ShelfViewModel` 的既有构造参数与方法签名全部保持；新增依赖一律是**尾部 Kotlin 默认参数**，因此 `BookOperationsViewModel`（3 参构造 `ShelfBookActions`）、`ShelfViewModelTest`（按名构造 `ShelfViewModel`）、`BookRepositoryTest`、`ShelfBookActionsTest` 原样编译并通过。
- Hilt 侧：Dagger 调用全参构造并自行提供每个参数，Kotlin 的 `BookDeletionCoordinator? = null` 不会破坏注入。`:app:hiltJavaCompileDebug` 成功即证明 `DeletionModule` → `DeletionUndoStore` → `BookDeletionCoordinator` → `ShelfViewModel` / `DeletionUndoViewModel` 整张图可解析。

---

## 5. 定向命令、退出码与测试数

工作目录 `D:/develop/Code/Codex/cra-android-qoder-r1/android`，均以 `ANDROID_HOME=/d/develop/Android/Sdk ANDROID_SDK_ROOT=/d/develop/Android/Sdk` 前缀调用 `./gradlew`。

| # | 命令 | 退出码 | 结果 |
|---|------|--------|------|
| 1 | `./gradlew :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.library.deletion.*" --console=plain` | 1（Gradle 报 FAILED） | `:app:compileDebugKotlin` 失败：`DeletionUndoBar.kt:29/103 Unresolved reference 'pluralsResource'`。见 §4.3-2，已修。 |
| 2 | `./gradlew :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.library.deletion.*" --tests "...BookRepositoryTest" --tests "...ShelfBookActionsTest" --tests "...ShelfViewModelTest" --console=plain` | 1（Gradle 报 FAILED） | 编译通过；`83 tests completed, 1 failed` — `DeletionUndoCredentialTest > consume rejects an expired credential even though it is still registered`。见 §4.3-1，已修。 |
| 3 | 同命令 2 重跑 | **0（BUILD SUCCESSFUL in 1m 59s）** | **83 tests, 0 failures, 0 skipped** |
| 4 | `./gradlew :app:lintDebug --console=plain` | **0（BUILD SUCCESSFUL in 9m 39s）** | **0 errors, 4 warnings**；`app/build.gradle.kts` 配了 `lint { abortOnError = true }`，0 error 即门禁通过 |

命令 3 的逐类计数（取自 `app/build/test-results/testDebugUnitTest/TEST-*.xml`）：

| 测试类 | tests | failures | errors | skipped | 归属 |
|--------|-------|----------|--------|---------|------|
| `feature.library.deletion.BookDeletionRestoreTest` | 12 | 0 | 0 | 0 | 本轮新增 |
| `feature.library.deletion.DeletionUndoCredentialTest` | 16 | 0 | 0 | 0 | 本轮新增 |
| `feature.library.deletion.BookDeletionCoordinatorRestoreTest` | 12 | 0 | 0 | 0 | 本轮新增 |
| `feature.library.deletion.ShelfBookDeletionDelegationTest` | 7 | 0 | 0 | 0 | 本轮新增 |
| **新增小计** | **47** | **0** | **0** | **0** | |
| `data.repository.BookRepositoryTest` | 13 | 0 | 0 | 0 | 既有，兼容性验证 |
| `ui.viewmodel.ShelfBookActionsTest` | 12 | 0 | 0 | 0 | 既有，兼容性验证 |
| `ui.viewmodel.ShelfViewModelTest` | 11 | 0 | 0 | 0 | 既有，兼容性验证 |
| **既有小计** | **36** | **0** | **0** | **0** | |
| **合计** | **83** | **0** | **0** | **0** | |

`lintDebug` 补记：**0 errors, 4 warnings**，报告在 `app/build/reports/lint-results-debug.{html,txt,xml}`。4 条 warning 全部是既有问题且不在本人所有权内：1 条 `HardwareIds` 落在 `feature/sync/JsonBridge.kt`，3 条 `ObsoleteLintCustomCheck` 来自 Gradle transform 缓存里的依赖 `lint.jar`（`ui-release`、`lifecycle-livedata-core-2.9.4`、`runtime`），属工具链而非源码。检索 `feature/library/deletion` 与 `strings_deletion` 在 lint 报告中的命中数为 **0**，即本轮新增/修改的文件没有任何 lint 发现。

时序说明：lint 在最后一处纯文案修改（`deletion_scope_delete_book_inspiration_note` 措辞）**之前**启动、之后结束，因此分析阶段读到的可能不是最终文本。该修改只是改一条已被引用的 `<string>` 的文字内容，没有新增资源、没有改动格式参数个数，不影响 lint 结论。

另外该次构建中 Gradle 提示 daemon 将因 Metaspace 耗尽而在构建结束后退出（`org.gradle.jvmargs` 配置为 4 GiB 堆 / 512 MiB Metaspace）——这是仓库既有的内存设置，`gradle.properties` 不在本人所有权内，本轮未做任何调整。

说明：命令 3 之后又改了一处纯资源文案（`deletion_scope_delete_book_inspiration_note` 的措辞，§3.2 / §4.3-8），未重跑测试——没有任何用例断言这条字符串的内容，改动只影响确认框显示文本。

本轮已跑：定向单元测试回归 + 既有兼容套件 + `:app:lintDebug`。**未跑**：`assembleDebug` / `assembleRelease`、diff-check、视觉验收与真机验收——按 COMMON.md，这些属集成后由集成者串行执行的完整门禁；真机部分按用户指示交 WorkBuddy（路径见 §9），本报告结论为**未验收**。

---

## 6. 实际未完成项与限制

### 6.1 真机未验收
本轮**没有任何安装与视觉证据**：未构建 APK、未安装、未在真机上打开过阅读器或书架。按 COMMON.md，「菜单可点、预览命中、测试通过均不能替代正文真实生效」，因此本报告的验收结论是 **未验收**。设备侧交 WorkBuddy，路径见 §9。

### 6.2 JVM 单测没有真实 SQLite
项目 JVM 单测里没有 Robolectric，`room-testing` 虽在 `gradle/libs.versions.toml:93` 声明但只接到 androidTest，而 `app/build.gradle.kts` 归 Codex，本人不能加依赖。因此本轮的「真实行为测试」是在**按 Room 查询语义人工建模的内存书库**上跑的，不是真 SQLite。以下三点靠人工建模，可能与真实 Room 有偏差：外键约束的实际执行、Room 生成的 SQL 细节、`IN ()` 空集合行为（已在代码里显式保护并加断言锁定）。要彻底消除需要 androidTest + `room-testing` → 见 SR-3。

### 6.3 非本人所有的删除入口没有撤销条
这三处**已经自动获得作用范围正确的删除**（它们最终都汇到 `BookRepository.deleteBook` → `deleteBookScoped`，因此不再破坏关联的对称性、也不再复活历史已删项），但**不会登记凭证，也不会渲染撤销条**：

| 入口 | 路径 | 现状 |
|------|------|------|
| 首页继续阅读面板删除 | `ui/screen/home/HomeContinueSheet.kt:225` → `ui/viewmodel/BookViewModel.kt:52` | 直接 `repository.deleteBook(id)`，无凭证 |
| 归档页删除 | `ui/screen/homearchive/HomeArchiveScreens.kt:333` → `ui/viewmodel/BookOperationsViewModel.kt:57` | 走 `ShelfBookActions`，但该 ViewModel 用 3 参构造 → `deletions = null` → 退回逐本委托，无凭证 |
| 阅读器内删除 | `ui/viewmodel/ReaderViewModel.kt:389`（`ReaderAction.DeleteBook`） | 直接 `bookRepository.deleteBook(action.bookId)`，无凭证 |

→ SR-1。其中归档页只需在 `BookOperationsViewModel` 注入协调器并透传（一行级别改动）即可获得凭证。

### 6.4 搁置 / 移除正文的确认框尚未接入既有交互
`DeletionScope.SHELVE` 与 `REMOVE_CONTENT` 的文案与 `retention()` 投影已就绪，但这两个动作在既有 UI 中是**直接执行、没有确认框**的，而承载它们的文件不在本人所有权内：搁置唯一活跃入口是 `HomeContinueSheet.kt:174` → `HomeViewModel.shelve`（`ShelfViewModel.shelveBook` 目前**没有任何 UI 调用方**）；清缓存走 `BatchSheet` / `ShelfAction`（归 Codex）。本轮未改他人交互 → 并入 SR-1。

### 6.5 `restoreBook` 旧路径仍公开但无 UI 入口
已按级联戳收敛作用范围（不再复活历史已删项），但它**恢复不了硬删的连接表、已读章节和正文缓存**——库里没有时间戳可比对，这是数据本身的限制，不是实现偷懒。全仓检索确认当前**没有任何 UI 入口调用它**（只有既有 `ShelfViewModelTest` 覆盖），保留是为既有调用方兼容。若将来要做「回收站」，必须走快照路径，或先按 SR-4 把凭证落库。

### 6.6 策略取值未经真机压测
12s 窗口、1.5s 合并、4 张上限、4,000,000 字符正文预算都是本轮定的经验值，未在真机上用大书批量删除压测过。预算触发时的降级行为（丢正文载荷、如实报告）已有 JVM 用例覆盖，但真实内存压力下的表现未知。

### 6.7 捕获阶段的连接表读取是全表读
`ChapterReadDao` 只有 `getAll()`，`BookTagDao` / `BookCategoryDao` / `ShelfBookDao` 只有全表 `getAllActive()`，没有按 bookId 的 suspend getter，因此删除时在内存里过滤。单次用户操作可以接受（批量删除已把全表读提到事务外只做一次），但书架很大时仍是一次全表读 → SR-2。恢复侧同理（`getAllActive()` × 3 + `getAll()` × 1，每批一次）。

---

## 7. SEAM REQUEST

```text
SEAM REQUEST
目标文件：
  android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/BookOperationsViewModel.kt
  android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/BookViewModel.kt
  android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ReaderViewModel.kt
  android/app/src/main/java/com/creationreadingassistant/ui/screen/home/HomeContinueSheet.kt
  android/app/src/main/java/com/creationreadingassistant/ui/screen/homearchive/HomeArchiveScreens.kt
  android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BatchSheet.kt（搁置/清缓存确认框）
需要的接口/字段/行为：
  SR-1a BookOperationsViewModel：注入 BookDeletionCoordinator 并在构造 ShelfBookActions 时透传
        （现有构造函数第 4 个参数 deletions 已就位，只需传值），并在归档页宿主渲染 DeletionUndoBar。
  SR-1b BookViewModel.deleteBook / ReaderViewModel 的 ReaderAction.DeleteBook：改走
        BookDeletionCoordinator.deleteBook(bookId)，而不是直接 repository.deleteBook。
  SR-1c HomeContinueSheet / HomeArchiveScreens / 阅读器宿主：渲染
        feature.library.deletion.DeletionUndoBar(offer = offers.firstOrNull(), onUndo, onDismiss, onExpired)，
        撤销结果文案用 DeletionCopy.deletionUndoMessage(context, outcome)。
  SR-1d 搁置与清缓存入口：接 DeletionCopy.deletionConfirmTitle/Body/Action(context, DeletionScope.SHELVE /
        REMOVE_CONTENT, ...) 确认框；这两种作用范围 retention().undoableInSession 为 false，
        因此不要给它们挂撤销条。
调用位置：
  HomeContinueSheet.kt:225、HomeArchiveScreens.kt:333、ReaderViewModel.kt:389、
  BookOperationsViewModel.kt:57、BookViewModel.kt:52；确认框接在各自删除/搁置/清缓存按钮的 onClick 前。
为什么现有接口不足：
  DeletionUndoBar 与凭证登记处都已就位且是单例，但撤销条必须挂在「触发删除的那个宿主」上才能被用户看到；
  上述宿主与 ViewModel 全部不在 QODER.md 授予本人的路径内，本人不能改。
  这些入口的删除语义已经是正确的（都汇到 deleteBookScoped），缺的只是撤销入口与按作用范围出文案的确认框。
兼容方案与测试：
  全部是新增可选依赖与新增 UI 插槽，不改既有方法签名：ShelfBookActions/ShelfViewModel 的 deletions
  参数本身就有默认值 null，不传也能编译运行（退回逐本委托，语义仍正确）。
  DeletionUndoBar 在 offer == null 时不渲染任何内容，挂上去不会改变没有删除发生时的界面。
  建议测试：在归档页删一本带标签/书单的书 → 撤销条出现 → 撤销 → 关联回来；
  阅读器内删除 → 返回书架后撤销条仍在（同一个单例登记处）；搁置/清缓存 → 不出现撤销条。
是否阻塞本轮：否。本轮三个自有入口（书架、书架搜索、阅读历史）已完整可用并通过 47 条定向用例。
```

```text
SEAM REQUEST
目标文件：
  android/app/src/main/java/com/creationreadingassistant/data/local/dao/ChapterReadDao.kt
  android/app/src/main/java/com/creationreadingassistant/data/local/dao/TaxonomyDao.kt（BookTagDao:119 / BookCategoryDao:150 / ShelfBookDao:188）
  android/app/src/main/java/com/creationreadingassistant/data/local/dao/InspirationDao.kt（NoteDao:54 / HighlightDao:82）
需要的接口/字段/行为：
  SR-2a ChapterReadDao.getByBook(bookId: String): List<ChapterReadEntity>
        —— SELECT * FROM chapter_reads WHERE book_id = :bookId
  SR-2b BookTagDao.getByBook(bookId) / BookCategoryDao.getByBook(bookId) / ShelfBookDao.getByBook(bookId)
        —— 各自 SELECT * FROM <join> WHERE book_id = :bookId
  SR-2c NoteDao.getActiveByBook(bookId) / HighlightDao.getActiveByBook(bookId)
        —— WHERE book_id = :bookId AND deleted_at IS NULL
  均为新增方法，不改任何既有方法、实体或 schema，不需要数据库版本变更、不需要迁移。
调用位置：
  BookRepository.captureDeletionSnapshot（单本删除捕获）、BookRepository.deleteBooksScoped（批量捕获）、
  BookRepository.restoreDeletions（恢复前的现状读取）。
为什么现有接口不足：
  ChapterReadDao 只有全表 getAll()；三张连接表只有全表 getAllActive()；NoteDao/HighlightDao 只有
  全量 observeAllActive() 与按书的 Flow。删除/撤销因此必须先拉全表再在内存里按 bookId 过滤，
  书架很大时是一次不必要的全表读，且批量删除要按本重复过滤。
兼容方案与测试：
  纯新增，既有调用方不受影响。落地后把上述三处改为按书查询，行为等价、断言不变：
  现有 47 条用例应当全绿且不需要修改（夹具已按方法名建模，新增 getter 只需在夹具里补对应门面）。
是否阻塞本轮：否。当前实现正确，只是读放大；批量路径已把全表读提到事务外每批一次。
```

```text
SEAM REQUEST
目标文件：
  android/app/build.gradle.kts（dependencies 的 androidTestImplementation 块）
需要的接口/字段/行为：
  SR-3 把已在 gradle/libs.versions.toml:93 声明的 room-testing 接进 androidTest，
  并按需补 androidx.test:core / androidx.test.ext:junit，使删除/撤销可以在真实 SQLite 上跑仪器测试。
调用位置：
  新增 android/app/src/androidTest/java/com/creationreadingassistant/feature/library/deletion/ 下的
  Room 仪器测试（命名沿用 *Deletion* / *Restore*）。
为什么现有接口不足：
  JVM 单测里没有 Robolectric 也没有 room-testing，本轮只能用按 Room 语义人工建模的内存书库。
  外键约束的真实执行、Room 生成 SQL 的细节、空 IN() 行为都无法在 JVM 上证明（见 §6.2）。
  build.gradle.kts 归 Codex，本人不能加依赖。
兼容方案与测试：
  只加 androidTest 依赖，不影响 debug/release 产物与 JVM 单测。仪器测试用
  Room.inMemoryDatabaseBuilder，不触碰设备上的真实数据库、不修改用户书籍数据。
是否阻塞本轮：否。属于保真度提升，建议集成后由 Codex 决定。
```

```text
SEAM REQUEST
目标文件：
  android/app/src/main/java/com/creationreadingassistant/feature/library/ShelfImporter.kt
  android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ShelfViewModel.kt:481（booksProvider 赋值处）
需要的接口/字段/行为：
  SR-4（R2/D2 用，本轮不实施）
  a) booksProvider 的作用范围需要可配置：当前只有 ShelfViewModel 把它设成活跃书架列表
     （books.value，即 deleted_at IS NULL），BookOperationsViewModel 保持默认空列表（完全不去重）。
     D2 需要一个能看到软删除书籍的查询口径，才能把重新导入的文件认回原书。
  b) books.content_hash 的刷新策略：当前只在首次导入且原值为空时写入（ShelfImporter.kt:336-344），
     文件内容变更后哈希永不更新，因此哈希既可能缺失也可能过期。
  c) repairFile(bookId, uri) 需要内容哈希校验：当前只校验格式一致（ShelfImporter.kt:177-182）。
调用位置：
  ShelfImporter.importOne 的 duplicate 判定（:296-323）、content_hash 落库（:336-344）、
  repairFile（:165-204）、reselectPlainText / reselectEpub。
为什么现有接口不足：
  QODER.md 需求 6 明确要求 D2 本轮不改导入器，由 R2 实施；且 ShelfImporter.kt 不在本人所有权内。
  这里只是把设计所需的接缝先登记给 Codex，避免 R2 再返工。
兼容方案与测试：
  完整设计见 §8。核心是新增匹配与校验，不改既有导入成功路径。
是否阻塞本轮：否（本轮不实施 D2）。
```

---

## 8. D2 设计：移除正文 / 重新关联（仅设计，本轮未实施）

QODER.md 需求 6 要求把完整设计写进报告，且本轮不擅自改导入器。以下取证全部来自只读基线代码。

### 8.1 现状事实（带出处）

| 事实 | 出处 |
|------|------|
| 内容哈希是**整文件 MD5**，十六进制小写 | `ShelfImporter.kt:643-657` `contentHashOrNull(uri, size)` |
| 只在 `0 < size <= MAX_HASH_BYTES` 时计算；`MAX_HASH_BYTES = 32 * 1024 * 1024` | `ShelfImporter.kt:644`、`:709` |
| 超过 32 MiB（大 TXT / 大 EPUB）**返回 null**，退回弱指纹 | `ShelfImporter.kt:644` 注释「超限（如超大 TXT）返回 null 回退弱指纹」 |
| 去重判定 = 批内指纹 ∪ `booksProvider()` 里满足任一：`local_uri` 相等 / (格式+原文件名忽略大小写+size 相等) / (`content_hash` 双方非空且相等) | `ShelfImporter.kt:296-310` |
| `booksProvider` 只在 `ShelfViewModel:481` 被赋值为 `{ books.value }`，即**活跃**书架（`observeAllActive` → `deleted_at IS NULL`） | `ShelfViewModel.kt:481`；`BookOperationsViewModel.kt:37` 注释明确保持默认空列表 |
| 哈希只在**首次导入且原值为空**时写入，best-effort，失败不阻断导入；此后**永不刷新** | `ShelfImporter.kt:335-344` |
| `repairFile(bookId, uri)` 只校验**格式**一致（原书名后缀 vs 所选文件后缀），不校验内容哈希 | `ShelfImporter.kt:165-204`，尤其 `:177-182` |
| `repairFile` 要求书籍行**活跃**（`bookDao.getById` 带 `deleted_at IS NULL`），软删的书无法修复 | `BookDao.kt:36-37` |
| `clearBookCache(id)` 只做三件事：`books.content_status = "missing"` 且 `local_uri`/`local_content_path` 置空、`book_content` 两个载荷置空、`book_files` 软删 | `BookRepository.kt:241-246` |
| `clearBookCache` **不删除磁盘文件**（全仓 `.delete()` 检索里没有它）；导入副本留在 `filesDir/books/<bookId>` 成为不可达孤儿 | `ShelfImporter.kt:544` 给出目录布局；`BookRepository.kt:241-246` 无文件操作 |
| `isBookDisplayable` 要求 `content_status` 不在 failed/missing/downloading，且 `size > 0` 且 (有本地路径 ∨ 有 uri ∨ 有哈希) | `ui/util/BookReadiness.kt` |

### 8.2 核心问题

「删除整本资料」之后重新导入同一个文件，**会创建一本全新的书**：`booksProvider()` 只看活跃书籍，软删的原书不在去重集合里，于是走完整导入流程、生成新 id。原书 id 上的阅读进度、阅读会话、笔记、高亮、分类/标签/书单关联、已读章节全部留在那个软删 id 上，**永不重新关联**。用户看到的是「我删了又导回来，进度和笔记全没了」。

「移除正文」（`clearBookCache`）之后重新选择文件，走的是 `repairFile` → `reselectPlainText` / `reselectEpub`：书籍 id 不变，因此进度/笔记/关联天然保留——但它**只校验格式**，用户可以指向一个内容完全不同的同格式文件，此时 `reading_progress.current_location_json`（source 坐标定位符）与 `chapter_reads.chapter_index` 会被原样保留在**不同的正文**上。

### 8.3 内容哈希匹配方案（建议 R2 实施）

**匹配键分三档，按可信度降序使用，并且必须显式记录用了哪一档：**

1. **强匹配**：`books.content_hash` 非空且与待导入文件的 MD5 相等 → 认定为同一份内容。
2. **弱匹配**：哈希任一侧缺失（>32 MiB 的文件、或早期导入未落哈希的旧书）时，退回 `format` + `original_file_name`（忽略大小写）+ `size` 三元组。
3. **不匹配**：以上都不成立 → 按新书导入，但要在导入结果里说明「未识别为已有资料」。

**需要的改动：**

- `booksProvider` 增加一个「包含软删除书籍」的口径（例如 `BookRepository.observeBooksIncludingDeleted()` 或一个 suspend 的 `getAllIncludingDeleted()`），并让去重判定分两轮：先比活跃书，再比软删书。命中软删书时**不要**直接判定为 duplicate 而静默跳过——那是当前行为对用户最不友好的分支——而是给出明确选择：「识别到已删除的同内容资料，恢复它并保留阅读数据？」
- 哈希缺失的旧书：复用既有**封面补扫**的同款模式（`BookDao.getEpubBooksMissingCover` + 后台补扫），加一条 `getBooksMissingContentHash`，在 idle 时为有本地路径的书补算哈希。这是纯增量的后台任务，不改导入成功路径。
- 超过 32 MiB 的文件：不要为了匹配去提高上限（那会让导入前的哈希成本线性上涨）。改用**分段哈希**——头 1 MiB + 尾 1 MiB + 总长度，作为独立字段（需要 schema 变更 → 必须走 Codex），或者干脆接受弱匹配并在 UI 上说明置信度较低。

### 8.4 文件变更时的定位风险

这是 D2 最危险的部分：**进度与锚点是 source 坐标口径**（COMMON.md「source 坐标用于持久化，display 坐标只能派生」），一旦正文换了，source 坐标指向的就是另一段文字。

风险矩阵：

| 场景 | 当前行为 | 风险 | 建议 |
|------|----------|------|------|
| `repairFile` 指向**同内容**文件（换路径/换文件名） | 直接替换，进度保留 | 无 | 哈希相等时静默通过，这是正确行为 |
| `repairFile` 指向**不同内容**、同格式文件 | 直接替换，进度/已读章/锚点全部保留 | **静默错位**：用户翻到「上次的进度」落在无关段落；已读章节标记指向别的章；高亮/笔记的 `locator_json` 与 `excerpt` 对不上正文 | 哈希不等时**必须显式确认**，并说明后果；提供「同时清除定位数据」选项 |
| 重新导入命中软删书并恢复 | 当前不会发生（会新建书） | 恢复后正文可能与删除前不同（用户改过文件） | 恢复时比对哈希：相等 → 全量恢复；不等 → 恢复资料与关联，但把进度/已读章/锚点标为「正文已变更，定位可能失效」，让用户决定 |
| EPUB 章节结构变化（同书名不同版本） | `chapter_index` 按序号存 | 序号漂移 → 已读章标错 | 章节级匹配应优先用 `chapter_reads` 之外的稳定标识；若只有序号，必须在哈希不等时清零而不是保留 |

**降级原则**：宁可丢定位，不可静默错位。哈希不等时，默认动作应该是「保留资料与关联、清除定位（进度归零、已读章清空、锚点失效）」并明确告知，而不是保留一份指向错误位置的进度。定位数据是可重建的派生数据（重新读一遍就有），错位的定位不是。

### 8.5 资料保留矩阵（三种作用范围 × 各类数据）

| 数据 | 搁置 SHELVE | 移除正文 REMOVE_CONTENT | 删除整本资料 DELETE_BOOK |
|------|-------------|--------------------------|---------------------------|
| `books` 资料行 | 保留（仅 `completion_state` 改为 shelved） | 保留（`content_status` → missing） | 软删 |
| 本地正文缓存 `book_content` | 保留 | **清空载荷** | 清空载荷 |
| 文件关联 `book_files` | 保留 | **软删** | 软删 |
| `local_uri` / `local_content_path` | 保留 | **置空** | 保留在软删行上 |
| 磁盘上的导入副本 `filesDir/books/<id>` | 保留 | **保留但不可达**（孤儿，见 8.6） | 保留但不可达 |
| 阅读进度 / 阅读会话 | 保留 | 保留 | 软删 |
| 笔记 / 高亮 | 保留 | 保留 | 软删 |
| 分类 / 标签 / 书单关联 | 保留 | 保留 | **硬删**（仅快照可恢复） |
| 已读章节 `chapter_reads` | 保留 | 保留 | **硬删**（仅快照可恢复） |
| 灵感 `inspirations` | 保留 | 保留 | 行不动，`source_book_id` 保留 |
| 会话内可撤销 | 否（无需撤销，未丢数据） | 否（**不可恢复**，见下） | 是，12s 窗口 |
| 从「继续阅读」移除 | 是（`continueReadingStore.clear`） | 否 | 否（本轮未接入） |

**为什么 REMOVE_CONTENT 不可撤销、也不能被说成备份**：它清掉的是唯一的本地正文副本指针与解析缓存，没有任何 retained copy；恢复正文只能靠用户重新选择文件或重新下载。因此 `retention().undoableInSession = false`，确认框带 `deletion_scope_remove_content_warning`「清掉的正文不是备份，无法从本地还原原文件内容。」

### 8.6 R2 实施边界建议

1. **孤儿文件回收**：`clearBookCache` 与 `DELETE_BOOK` 都会让 `filesDir/books/<id>` 变成不可达孤儿。R2 应决定：要么在 REMOVE_CONTENT 时真的删掉文件（名副其实地「释放空间」），要么保留路径记录以便重新关联时复用（省一次导入）。二者互斥，需要产品决策。**本轮没有改这个行为**，因为改它会同时影响 `ShelfBookActionsTest` 断言的文案与磁盘状态。
2. **重新关联入口**：命中软删书时的「恢复并保留阅读数据」应当复用本轮的快照/凭证机制而不是另写一套——`BookDeletionCoordinator` 已经有「解析活跃关系目标 + 逐项判定 + 报告」的完整能力，R2 只需增加一个「跨会话的凭证来源」（落库）或一个「按 bookId 从库里重建快照」的入口。
3. **落库凭证需要 schema 变更**：会话内凭证不承诺重启可撤销是本轮明确接受的边界。若 R2 要做「删除后隔了很久还能恢复」，必须先按 COMMON.md 向 Codex 提 schema 请求，**本轮没有为此改数据库版本**。
4. **不要在 R2 之前给 REMOVE_CONTENT 挂撤销条**：它的 `undoableInSession = false` 是有意的，挂上去会兑现不了的承诺。

---

## 9. WorkBuddy 真机验收路径

前置：`adb devices` 确认真实 serial，之后每条命令都带 `adb -s <serial>`；不用 MuMu 模拟器。安装先调 `android/scripts/install_with_confirm.ps1 -Serial <serial> -Apk <absolute-apk>`；脚本超时或被 MIUI 阻止时，按 COMMON.md 允许回退 `adb -s <serial> install -r <absolute-apk>`，保留命令/退出码/日志，并明确记录「回退安装，不代表安装脚本 PASS」。不要求用户手动确认 MIUI，不改安全设置，不卸载应用规避签名，不清应用数据。若临时改 `stay_on_while_plugged_in`，结束恢复原值（原值为 null 则删除该键）。

**测试资料只用中性名称记录（「测试 EPUB」「测试 TXT」），不写真实书名、不输出正文、不输出数据库快照。不修改设备上的既有书籍数据。**

准备：一本带**分类 + 标签 + 书单关联 + 若干已读章节 + 阅读进度 + 至少一条笔记和一条高亮**的测试 TXT，以及一本测试 EPUB。建议先记录当前的分类/标签/书单归属与已读章节数，作为撤销后的比对基准。

| # | 步骤 | 期望 |
|---|------|------|
| 1 | 书架页对测试 TXT 执行「删除整本资料」 | 确认框标题为「删除整本资料？」，正文列出会被移除的内容，并写明「可在 12 秒内撤销；应用重启后不再提供撤销。」与「灵感不会被删除，与这本书的关联会保留，撤销后仍然指向这本书。」 |
| 2 | 确认删除 | 书从书架消失；底部出现撤销提示条（testTag `deletion-undo-bar`），文案「已删除 1 本资料，可在 N 秒内撤销」，N 逐秒递减 |
| 3 | 点「撤销」（`deletion-undo-action`） | Snackbar 出现「已撤销删除」；书回到书架 |
| 4 | 打开该书详情 | **分类、标签、书单关联全部回来**；已读章节数与删除前一致；阅读进度百分比与删除前一致；笔记与高亮条数一致；正文能正常打开（正文缓存已回来，不需要重新解析） |
| 5 | 删除前**先单独删掉一条高亮**，再删书，再撤销 | 那条被单独删掉的高亮**不复活**；其余高亮回来 |
| 6 | 书架多选 3 本执行批量删除 | 提示条文案为「已删除 3 本资料…」，与确认框里的数量一致；一次撤销把 3 本全部恢复（含各自关联） |
| 7 | 连续删除两本（间隔 < 1.5s） | 合并成一张凭证；撤销一次恢复两本 |
| 8 | 删除一本后**等提示条倒计时归零** | 提示条自行消失（不是留着点了没反应）；书保持删除状态 |
| 9 | 删除一本后立刻杀掉应用进程并重启 | 重启后**不出现任何撤销提示条**（凭证只在内存）；书保持删除状态 |
| 10 | 删除一本，在 12s 窗口内**去分类管理里删掉它所属的一个标签**，再撤销 | 书与其余关联恢复；Snackbar 如实说明「N 个分类/标签/书单已不存在，未恢复这些关联」；不产生指向已删标签的孤儿关联 |
| 11 | 删除一本，在窗口内**重新打开另一本书产生新进度**，再撤销 | 新进度不被覆盖；被删的书自身数据恢复 |
| 12 | 撤销成功后**再点一次撤销**（若提示条还在） | 不重复写回、关联不翻倍；提示为撤销窗口已结束或提示条已消失 |
| 13 | 书架**搜索页**搜到一本书并删除 | 搜索页底部出现同一个撤销提示条；撤销后书回到搜索结果与书架 |
| 14 | 在书架删除一本书，**切到阅读历史页** | 撤销提示条在阅读历史页同样出现（跨入口同一个单例登记处）；在那里撤销同样生效 |
| 15 | 在**阅读历史页**删除一本书 | 确认框用新的作用范围文案（不再是旧的「此操作不可撤销」）；出现撤销提示条；撤销后书与阅读记录回来 |
| 16 | 对一本书执行**搁置** | 不出现撤销提示条；书仍在书架（资料未丢），阅读进度、笔记、关联、已读章节都保留；从「继续阅读」移除 |
| 17 | 对一本书执行**清缓存 / 移除正文** | 不出现撤销提示条；确认文案含「清掉的正文不是备份，无法从本地还原原文件内容。」；执行后书架资料、进度、笔记、关联**都还在**，只有正文需要重新选择文件或重新下载 |
| 18 | 删除一本**没有正文缓存、没有阅读记录**的书（例如刚导入未打开）再撤销 | 撤销成功且不报错；书回到书架 |
| 19 | 阅读器内删除当前书（若该入口可达） | **已知限制**：删除语义正确，但**不会出现撤销提示条**（§6.3 / SR-1）。请如实记录为「未接入撤销」而不是缺陷回归 |
| 20 | 首页继续阅读面板 / 归档页删除 | 同上，**已知限制**：无撤销提示条 |
| 21 | 全程观察 | 纸墨主题、圆角、触控尺寸（撤销/关闭按钮 ≥ 48dp）、减少动态效果设置、手机/平板/横屏适配未退化；撤销条不与批量操作栏互相遮挡（撤销条在上） |

验收结论请写明每一步的实际观察结果。**开发侧本轮结论：未验收。**

---

## 10. 一句话结论

删除与撤销现在是同一套作用范围语义：一次操作一个级联戳、一份快照、一张有界有时效的会话凭证；撤销逐项判定让路，不覆盖合法新修改、不造孤儿或重复关系、重复撤销幂等、批量范围与提示数量一致。47 条新增用例 + 36 条既有兼容用例全绿（83/83），`:app:lintDebug` 0 errors（4 条 warning 均为既有问题，不在本轮文件内）。真机未验收；非本人所有的三个删除入口还缺撤销条，已按模板提交 SR-1；D2 只出设计未动导入器。
