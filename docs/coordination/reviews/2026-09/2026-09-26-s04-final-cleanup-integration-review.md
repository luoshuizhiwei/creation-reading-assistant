# S04 残留安全清理链 · 最终独立审查

- **日期**：2026-09-26
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支 / HEAD**：`codex/recover-worktree-20260920` / `5aed593`
- **审查对象**：
  - `data/repository/BookRepository.kt`（`clearBookCache` / `deleteBookContentFiles` / `scanBookContentResidue` / `deleteTreeSparingResidue` / `OfflineBooksResidueClear`）
  - `ui/viewmodel/ShelfBookActions.kt`（`clearCacheForBooks` / `buildCacheClearMessage`）
  - 参照：`ui/screen/profile/StorageSubPage.kt`（调用点，只读）
- **性质**：**只读审查为主**。**未修改任何源码或测试**（理由见 §6）—— 未发现需要窄修的**已证实**缺陷。
- **结论先行**：核心保护链**成立且离线测试充分**（101 例全绿，本次复跑确认）。
  发现 **2 个已确认缺陷**（均**非**本链引入，属既有语义/覆盖缺口，其中 1 个是真实空间泄漏）
  与 **3 项需真机/模拟器验证的边界**。**未做任何修复，故当前 APK 哈希全部有效**（§7）。

---

## 0. 开工核对

| 检查 | 结果 |
|---|---|
| 目录 / 分支 / HEAD | 一致 ✓ |
| 目标报告是否已存在 | **否** → 首次撰写，非重复施工 ✓ |
| 是否覆盖 B / 集成者文件 | **否** —— 未写入任何文件，只新建本报告 ✓ |
| 是否改动源码 | **否** —— 两个源文件哈希与接手时一致（§7） ✓ |

> 关于「若你已完成审查，只需把现有报告保存到仓库」：本仓库内**不存在**任何
> S04 最终清理链审查报告（`ls docs/coordination/` 只有实现报告与消息修正报告）。
> 集成者「只找到 E 盘重定向报告和 101/101 结果」与本窗口所见一致。
> 故本次为**首次撰写**，非搬运既有报告。

---

## 1. 保护链的实际行为（逐项核对源码）

**残留标记集合**（`BookRepository.kt:883`）：`.backup` / `.repairing` / `.importing`。

**标记的真实落点**（已核对创建点，非推测）：

| 标记 | 创建点 | 位置 |
|---|---|---|
| `.backup` / `.repairing`（TXT/MD） | `ShelfImporter.kt:951-952` | `books/<id>/<safeName>.{repairing,backup}` |
| `.backup` / `.repairing`（EPUB 替换） | `EpubRepository.kt:91-92` | `books/epub/<id>.{repairing,backup}` |
| `.importing`（EPUB 导入/新鲜源） | `EpubParser.kt:69` | `books/epub/<id>.importing` |

⇒ 预检**两处都覆盖**：`scanBookContentResidue` 递归扫 `books/<id>/`（`:379-382`）
**并且**检查 `books/epub/<id>.<marker>`（`:384-389`）。与真实落点一致 ✓

### 1.1 单书清理（`clearBookCache`）

顺序为**预检 → 删文件 → 最后改 DB**（`:279-303`）：

| 结果 | 文件 | DB 指针 |
|---|---|---|
| `RefusedResidue` / `RefusedUnsafeScan` | 零改动 | **零改动**（`:280-281` 在 DB 写入之前 return） |
| `AbortedMidway` | 部分文件可能已删；标记与剩余文件保留 | **零改动**（`:289-291` 在 DB 写入之前 return） |
| `Cleared` / `ClearedWithRemains` | 正文删除（可能未删净） | 清空指针（`:295-297`） |

**「保留 backup 但清空 DB 指针」在拒绝路径上不存在** ✓ —— 已用测试钉住
（`BookResidueSafeCleanupTest` 的 `coVerify(exactly = 0) { ... upsert }`）。

### 1.2 清空离线（`OfflineBooksResidueClear.clear`）

纯文件层，**不读不写 DB**（`:1145-1176`）：`books/<id>/` 内含残留或不可列举 → 整目录保留；
`books/epub/` 按文件粒度，标记保留且**同书 `<id>.epub` 一并保留**（`:1196`）。

### 1.3 DB 写入安全性

三处指针清空全部走 `@Upsert`（`BookDao.kt:38` 起），**不是** `@Insert(onConflict = REPLACE)` ——
不会触发 `books` 外键级联删除阅读进度/笔记/高亮 ✓（这是该仓库 2026-09-17 真机事故的已知坑，
本链未回退它）。

---

## 2. 已确认缺陷（2 项）

### 2.1 【已确认】EPUB 图片缓存目录不被单书清理回收 —— 真实空间泄漏

**事实**：

- `EpubParser.kt:242` 在渲染含图片的章节时创建 `books/epub/<id>/img/` 并写入抽取的图片；
- `clearBookCache` 的 EPUB 分支**只删 `<id>.epub` 这一个文件**（`BookRepository.kt:353-357`）；
- 全仓 `grep` 确认：**没有任何生产代码删除 `books/epub/<id>/` 这个目录**
  （`grep -rn 'books/epub' ... | grep delete` → 无命中）。

**影响**：用户对一本含图 EPUB 执行「清缓存」后，`<id>.epub` 被删，但
`books/epub/<id>/img/` 下的图片**全部留存**，成为无主目录（DB 指针已清空 ⇒ 不可达）。
含图 EPUB 的图片缓存可达数 MB～数十 MB，属**真实空间未释放**。

**为什么不是本链引入的**：旧实现（`HEAD`）同样只删 `<id>.epub`（已用 `git show` 对照）。
本链**未加重**，但也**未修复**。

**为什么两条路径不一致**：`OfflineBooksResidueClear.clearEpubDir`（清空离线）
**会**删掉它 —— 因为 `<id>/` 是目录，落入 `file.isDirectory` 分支（`:1191`）被整棵删除。
即：**「清空所有离线书籍」能回收，「清缓存」不能**。

**严重度**：中。不销毁任何数据（残留保护不受影响），但「清缓存」的**空间释放承诺
与实际不符**，且用户没有任何入口回收这些无主图片（P1「残留无出路」的同类形态）。

**最小修复（若集成者批准）**：在 `deleteBookContentFiles` 的 EPUB 段落
（`:353` 附近）把 `File(context.filesDir, "books/epub/$bookId")` 这个**目录**也纳入
`deleteTreeSparingResidue` 处理（复用既有保护删除器，自动获得残留/不可列举保护），
并补 1 条离线测试。属 `BookRepository.kt` 所有权内、窄修。
**本窗口未实施**，理由见 §6。

### 2.2 【已确认】`AbortedMidway` 分支在仓储层无测试覆盖

**事实**：`BookCacheClearOutcome.AbortedMidway` 在 `BookResidueSafeCleanupTest` 中
**出现 0 次**；全仓仅 `ShelfBookActionsTest:190,204` 以**构造值**断言消息文案。

**含义**：`clearBookCache` 中产生 `AbortedMidway` 的那段逻辑
（`:289-291`：预检通过后、文件删除途中遇到新残留或目录变为不可列举）
**从未被任何测试执行过**。测试只验证了「给定这个 outcome 时消息怎么写」，
**没有**验证「什么情况下真的产生这个 outcome」。
`deleteTreeSparingResidue` 的 `ABORTED_*` 有单测（`:224-275`），
但**仓储层把它翻译成 `AbortedMidway` 并拦截 DB 写入**这一步是空的。

**严重度**：中（覆盖缺口，非行为错误）。这段代码恰好是**防「保留 backup 却清空指针」**
的关键闸门，正是最该被钉住的地方。

**说明**：该分支难以用真实文件系统确定性触发（需在预检与删除之间注入新标记），
合理做法是像 `sparing deleter keeps later subtree untouched`（`:253`）那样用
`mockk<File>` 固定 `listFiles()` 顺序，或在 `deleteBookContentFiles` 注入接缝。
**本窗口未实施**（属新增测试，超出「确有可证实缺陷时窄修」的范围）。

---

## 3. 离线测试覆盖（已复核，非采信报告）

本次**复跑**（`--offline`，`android/`）：

```
./gradlew :app:testDebugUnitTest \
  --tests "…data.repository.BookResidueSafeCleanupTest" \
  --tests "…data.repository.BookRepositoryTest" \
  --tests "…ui.viewmodel.ShelfBookActionsTest" \
  --tests "…ui.viewmodel.ShelfViewModelTest" \
  --tests "…ui.viewmodel.ShelfSourceRefreshFeedbackTest" --offline
```

**EXIT 0**；XML 汇总 **101 例，0 失败 0 错误 0 跳过**，与集成者所见一致：

| 类 | 例数 | 跳过 |
|---|---|---|
| `BookRepositoryTest` | 23 | 0 |
| `BookResidueSafeCleanupTest` | 25 | 0 |
| `ShelfBookActionsTest` | 18 | 0 |
| `ShelfSourceRefreshFeedbackTest` | 14 | 0 |
| `ShelfViewModelTest` | 21 | 0 |
| **合计** | **101** | **0** |

**覆盖到**：三种标记逐一的拒绝与保留、同书 `.epub` 保护、无残留照常清理（含三处 DB 指针断言）、
目录不可列举 → 保守拒绝、删除器兜底（中止/干净/部分/未删净四态）、
清空离线的保留/同书保护/杂项清理/无残留等效/根目录孤儿、批量消息全拒无成功字样。

**覆盖缺口**：§2.2（`AbortedMidway` 的产生路径）；`books/epub/<id>/img` 相关（无测试提及）。

**平台限制（诚实登记）**：两条「删除失败」用例（`:169`、`:190`）以
`Assume.assumeTrue(os.name contains Windows)` 门控 —— 依赖 Windows 独占删除语义。
在非 Windows 主机上这两例会**静默跳过**（本次在 Windows 上运行，`skipped=0`）。

---

## 4. 数据库指针与用户提示一致性

逐条核对**文案与代码实际状态**，未发现虚报：

| 代码状态 | 文案（`BookRepository.kt:1065-1106` / `ShelfBookActions.kt:199-221`） | 一致？ |
|---|---|---|
| `rootUnreadable` | 「清空操作未执行、未删除任何文件」 | ✓ |
| 全拒（`protectedEntries>0`，无删除） | 「清空未执行」+「已原样保留，未删除也未自动恢复」 | ✓ **无成功字样** |
| 有删除 + 有保留 | 「清空已部分执行」+ 逐项计数 | ✓ |
| 仅 `remainsEntries>0` | 「清空未完成」（`nothingDeletedSoFar && remains==0` 的 else 分支） | ✓ |
| `AbortedMidway` | 「清理中止…部分本地文件可能已被清理」 | ✓ 未声称成功 |
| `ClearedWithRemains` | 「正文未删净…空间未完全释放」 | ✓ 与 `Cleared` 区分 |

**一处文档（非代码）瑕疵**：实现报告
（`2026-09-25-s04-residue-safe-cleanup-implementation.md:34`）写
「全部被保护时为『清空已中止』」，但**代码里没有这个字符串**（已 grep 确认），
实际输出是「清空未执行」。**代码正确、报告笔误**，不影响行为。已在此登记，
**未改该报告**（不属本窗口所有权）。

**结论**：提示语与状态**一致**，未发现虚报或「保留备份却报成功」的形态 ✓

---

## 5. 仍需模拟器 / 真实手机验证的边界

**本窗口未运行 adb / instrumentation / 真机**（遵守授权）。以下**只能**由设备侧确认：

1. **`StorageSubPage` 的 Compose 接线与 Snackbar 呈现**：对话框 onClick 是薄接线
   （调对象 + `ShowMessage`），保护逻辑本体已离线覆盖；但
   「清空已部分执行…」这类长文案的**实际显示时长/是否被后续 Snackbar 覆盖**未上机验证。
   代码层面两条消息经同一 `showMsg` → `snackbarHostState.showSnackbar`（`ProfileRoute.kt:182`），
   `showSnackbar` 是**挂起排队**语义，故「后一条覆盖前一条」在框架层面不成立；
   但**排队导致的时长叠加**属体验问题，需真机观察。
2. **真实占用文件导致的删除失败**：离线用 Windows 独占句柄模拟；
   真机上由系统/杀毒/媒体扫描占用引发的形态与频率未验证。
3. **SAF 权限失效下的 EPUB 删除**：`clearBookCache` 删的是应用私有副本
   （不依赖 SAF 权限），故预期不受影响；但**未在真机确认**。
4. **`books/epub/<id>/img` 的实际体积占比**：§2.1 的严重度依赖真实含图 EPUB 的图片体积，
   未在真机测量。

**已有设备证据的边界**：`docs/qa/2026-09/2026-09-25-s04-epub-residue-qahost-device-acceptance.md`
验证的是 **EPUB `.backup` 残留守卫**（2 条方法，`OK (1 test)` ×2），
**不是**本报告的清理链（`clearBookCache` / `OfflineBooksResidueClear`）。
**不得**用该设备报告为 §2 的清理链背书 —— 二者覆盖对象不同。

---

## 6. 为什么未做任何修复

任务要求「确有可证实缺陷时，**仅在** `BookRepository.kt`、`ShelfBookActions.kt`
及各自直接测试内作窄修」。本窗口**发现**两个已确认缺陷（§2），但**均未实施修复**，理由：

1. **§2.1（图片缓存泄漏）**：修复会改动 `BookRepository.kt` 的删除语义 ——
   新增对 `books/epub/<id>/` 的递归删除。虽然复用既有保护删除器可保持安全，
   但这**改变了「清缓存」实际删除的文件集合**，属行为变更而非纯缺陷修补；
   且需配套新测试与设备复验。**这会让当前 APK 哈希失效**（§7），
   影响 B 正在进行的验收锚点 —— 应由集成者统一决策后再动。
2. **§2.2（覆盖缺口）**：补测试**不改变行为**，但属**新增测试**，
   不在「确有缺陷时窄修」的授权范围内；且合理实现需要 `mockk` 固定 `listFiles()`
   顺序或引入接缝，设计取舍应由集成者定。
3. 两者**都不是**本链引入的回归，**不影响**本链核心结论（保护链成立）。

> **因此：本次审查未改动任何源码或测试，当前所有 APK 哈希仍然有效。**
> 若集成者决定修复 §2.1，**必须重新构建并通知 B 更新验收锚点**（见 §7）。

---

## 7. 产物哈希现状（供集成者对照）

**本窗口未改源码**，故以下哈希与接手时一致：

| 文件 | sha256 |
|---|---|
| `BookRepository.kt` | `56340e48d7b6f653396d660ccea73c3819463d17b92180c73de549e9dbe98086` |
| `ShelfBookActions.kt` | `44a7c89662794557e752c54e5370e763e078f1b8458e4f3ab3f4dd0b4be39735` |

**当前产物**：

| 产物 | sha256 | mtime |
|---|---|---|
| `app-debug.apk`（正式包） | `53d0c01b0d36dc19690dc1f62834f19fc61634a2dd394de2b23efc9da85359ac` | — |
| `app-qahost.apk`（host） | `08c287642a2cb26cfff8fa8862e845015c0318e63bb429cc496578c2e0d75de7` | 2026-09-25 20:48 |
| `app-qahost-androidTest.apk`（test） | `6d19c24449d75f893c20fef947f165fd005f08733187ad1f3e5c5662c1456ec7` | 2026-09-25 17:28 |

> ⚠️ **两个需要注意的锚点漂移（非本窗口造成，登记供集成者处置）**：
>
> 1. **B 的测试 APK 锚点已失效**：`2026-09-25-s04-epub-residue-qahost-device-acceptance.md`
>    记录其验收锚点为 `CEFB77C0…061EF`，而**磁盘上现为 `6D19C244…`**
>    （test APK mtime 17:28 晚于该报告 17:10）。B 的验收结论**对应的是旧测试 APK**，
>    不能用于当前产物。
> 2. **qahost host APK 在 B 验收后被重建**：B 记录 host 包 `lastUpdateTime=15:16:09`
>    且「未重装」；磁盘上 `app-qahost.apk` mtime 为 **20:48**，晚于 B 的报告。
>
> 这两条**不是**本窗口所为（本窗口未构建、未安装）。若后续要复验，须重新对齐锚点。

---

## 8. 审查动作自证

| 动作 | 是否执行 |
|---|---|
| 只读审查源码 / 测试 / 报告 | ✅ |
| 复跑离线 JVM 定向测试（`--offline`） | ✅ 101 例 EXIT 0 |
| **修改源码 / 测试** | ❌ **未修改任何文件** |
| 新建本报告 | ✅ |
| adb / 安装 / instrumentation / 真机 / 模拟器 | ❌ |
| 触碰真实书库 / E 盘备份 / 原始仓库 | ❌ |
| 覆盖 B 或集成者文件 | ❌ |
| commit / push / stage / reset / checkout / clean | ❌ |

---

## 9. 给集成者的结论汇总

| # | 项 | 判定 |
|---|---|---|
| 1 | 全量清理 / 单书清理 / 三标记保护 / 部分删除中止 / 普通文件删除失败 / EPUB 删除失败 / `ClearedWithRemains` / DB 指针 / 用户提示 | **一致且成立**（§1、§4）✓ |
| 2 | 离线测试覆盖 | **101/101 通过**（本次复跑确认）；**缺口**：`AbortedMidway` 产生路径（§2.2） |
| 3 | 已确认缺陷 | **2 项**：EPUB 图片缓存目录不被单书清理回收（**真实空间泄漏**，§2.1）；`AbortedMidway` 仓储层无覆盖（§2.2） |
| 4 | 是否本链引入 | **否**，两项均属既有语义/覆盖缺口 |
| 5 | 本窗口是否修复 | **否** —— 未改任何文件，**APK 哈希全部有效**（§6、§7） |
| 6 | 需真机/模拟器验证 | 4 项（§5）；**且**：B 的设备报告验证的是残留守卫，**不是**本清理链 |
| 7 | 锚点风险 | B 的 test APK 锚点 `CEFB77C0…` 与磁盘 `6D19C244…` **不符**；host APK 于 20:48 被重建（§7） |
