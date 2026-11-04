# D 阶段 S05 三项残留 UI 场景：MuMu qahost 最短执行与证据清单（2026-09-28 窗口 / 2026-09-29 编制）

> **Codex 2026-09-29 复核更正：§0 的 A→B→C 共用单一状态不能直接成立。** A 滑块到书末后会保存书末 locator，C 取消完成后重开不能无额外步骤就预期回落到文中约 61%。执行 C 前必须独立建立并用 DB 证明“中途 `locator_v2` + 残余 100% + `reading`”的状态；可参考旧七站的“先中途位置保存→显式标记完成→取消完成”，但报告须标明它不同于 A 的自动书末路径。A 滑块到 100%只验证“跳至书末后自动保存”，不称逐页读到书末。详见 [总指挥复核](../../reviews/2026-09/2026-09-29-stage-d-v5-s05-review.md)。

> **性质声明：本文件是「执行计划」，不是验收报告。** 三项场景当前状态一律 **NOT RUN**。
> C 阶段 `docs/qa/2026-09/2026-09-28-s05-mumu-cross-page-finished-acceptance.md` 的七站截图与 DB 对账
> 证明的是**显式 `markFinished` 路径**（UI 长按 →「标记为已读完」/「取消已读完」）的展示契约，
> **不能外推**为本文件三项：真实读到书末自动 finished、首页完成卡片逐卡目视、取消读完后再阅读百分比自愈。
> 本计划只做只读代码核对与步骤设计，未运行 Gradle / ADB / MuMu，未改任何源码、测试或配置。

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`）
- 目标设备：MuMu `emulator-5556`（AOSP Android 12 / SDK 32 / x86_64），qahost 隔离沙箱 `com.creationreadingassistant.qahost`
- 三项编号沿用 [D 首轮门禁复核](../../reviews/2026-09/2026-09-28-stage-d-gate-review.md) §下一轮第 3 条与 [D 构建与设备报告](../../../qa/2026-09/2026-09-28-stage-d-build-and-mumu-gates.md) §7 的登记
- 上游：[S05-① 书架契约](2026-09-28-s05-cancel-finished-state-result.md)、[S05 跨页消费者](2026-09-28-s05-cross-page-finished-consumers-result.md)

---

## 0. 三项一览与当前状态

| # | 场景 | 当前状态 | 最短驱动入口 | 判定的**唯一权威**落点 |
|---|---|---|---|---|
| **A** | 真实读到书末自动置 finished | **NOT RUN** | 阅读器底部进度滑块拖到 100%（`seekToPercent`），等防抖落库 | DB 同书 `progress_percent` / `completion_state` / `completed_at` |
| **B** | 首页「已阅读完成」横排卡片逐卡显示 | **NOT RUN（卡级）** | 打开首页，分段滑动横排并逐段 dump | 每张卡的 UI XML 文本节点 + 与 DB `finished` 集合比对 |
| **C** | 取消读完后再次阅读时百分比自愈 | **NOT RUN（UI）** | 取消读完 → 重开阅读器 → 等防抖落库 → 退到书架 | DB `progress_percent` 由 100 回落到真实位置值 |

> 三项**共用同一个夹具生命周期**（播种 → A → B → C → 清理回写），不重复播种、不重复安装。
> 顺序不可换：C 依赖 A 产生的 finished 态与取消动作，B 的卡片内容依赖 A/C 后的 `completed_at` 排序。

---

## 1. 逐项代码入口与可观察结果（只读核对结果）

### 1.1 场景 A —— 真实读到书末自动置 finished

**写入链（自 UI 到 DB，逐跳已核对）：**

| 跳 | 位置 | 行为 |
|---|---|---|
| 1 | `ui/screen/reader/ReaderChrome.kt:376-397` | 底部 `CraSlider`（0f..100f）`onValueChangeFinished` → `onSeekProgress(sliderValue)`。**必须等到 `onValueChangeFinished`**，只拖不松手不触发跳转 |
| 2 | `ui/screen/reader/ReaderProgressActions.kt:318-368` | `seekToPercent`：TXT/Markdown → `jumpToPlainOffset(percent/100*totalChars)`；分页引擎开 → `pagedJumpRequest.value = targetOffset` |
| 3 | `ui/screen/reader/ReaderProgressActions.kt:130-147` | `jumpToPlainOffset`：分页模式写 `pagedJumpRequest`，滚动模式 `plainListState.scrollToItem` |
| 4a（滚动路径） | `ui/screen/reader/ReaderProgressEffects.kt:266-275` | 落点偏移 = `!canScrollForward ? totalChars : 当前可见偏移`；百分比 = `!canScrollForward && index>0 → 100f`，否则 `offset*100/totalChars` |
| 4b（分页路径） | `feature/reader/pager/TxtPagedController.kt:135-147` | `progressPercent`：末章末页硬返回 `100f`；否则 `currentPageStartAbs*100/totalChars` |
| 5 | `ui/screen/reader/ReaderProgressEffects.kt:295-308`（滚动）/ `:355-368`（分页） | 构造 `SaveProgress(ReadingProgressEntity(percent, completion_state = if (percent >= 99.9f) "finished" else "reading", current_location_json = locator_v2(legacyOffset…)))`，**500ms 防抖** |
| 6 | `ui/viewmodel/ReaderViewModel.kt:459-461` | `is ReaderAction.SaveProgress -> io { bookRepository.saveProgress(action.progress) }` |
| 7 | `data/repository/BookRepository.kt:108-118` | `progressDao.upsert(mergeReaderProgress(existing, incoming, nowIso, nowMillis))` |
| 8 | **`data/local/entity/ReadingEntities.kt:73-96`** | **权威判定**：`percent = incoming.progress_percent.coerceIn(0,100)`；`state = when { percent >= 99.5f -> FINISHED … }`；`completed_at = FINISHED ? (existing?.completed_at ?: nowMillis) : null` |

**可观察结果（DB，同一测试书 `reading_progress` 单行）：**

| 字段 | 期望 |
|---|---|
| `progress_percent` | `100.0`（或 ≥99.5；拖到 100 应为 100.0） |
| `completion_state` | `finished` |
| `completed_at` | 由 `NULL` → **非 NULL 的 epoch millis**（`merge` 首次写入 `nowMillis`） |
| `current_location_json` | `locator_v2`，`legacyOffset ≈ totalChars`（书末）；**不得**仍是 `legado_position_v1` |
| `updated_at` | 变化 |

**UI 侧（与 DB 同一时点）：** 书架该卡进入「已完成」分组、显示「已读完」文案与封面「读毕」印章；首页「已阅读完成」区出现该书（场景 B 的输入）。

**两个必须满足的前置门控（不满足则保存根本不触发，属操作问题不是缺陷）：**

1. `ReaderProgressEffects.kt:735-738` `canPersistNormalReadingProgress` = `!initialPositionPending && !temporaryInspection`
   —— 初始定位必须已完成（`pendingInitialPosition` 在 `ReaderProgressEffects.kt:406` / `:424` 置 false），且**不能**走 `navigationMode=temporary` 查阅入口。
2. `ReaderProgressEffects.kt:756-775` `LegadoMarkerSaveGuardSession`
   —— **首次 `observe` 只锚定并抑制，第二次起位置偏离锚点才放行**。拖滑块改变全书偏移即构成「偏离」，所以**必须真的改变位置**；只打开不移动，保存不会写。

### 1.2 场景 B —— 首页「已阅读完成」横排卡片逐卡显示

**数据源（已核对）：**

| 跳 | 位置 | 行为 |
|---|---|---|
| 1 | `ui/viewmodel/HomeViewModel.kt:129-138` | `completed = input.books.filter { isBookDisplayable(it) && isReadingFinished(progressById[it.id]) }.sortedWith { completionTime(second) compareTo completionTime(first) }.take(8)` |
| 2 | `ui/viewmodel/HomeViewModel.kt:170-176` | `completionTime` = `completed_at` 优先，回退 `Instant.parse(updated_at)`。**`completed_at` 决定横排顺序** |
| 3 | `ui/util/BookReadiness.kt:71-72` | `isReadingFinished(p) = p?.readingState == FINISHED`（显式状态权威，不看百分比） |
| 4 | `ui/util/BookReadiness.kt:21-27` | `isBookDisplayable(book)`：`deleted_at == null` 且 `content_status ∉ {failed, missing, downloading}` 且 `size>0` 且有本地路径/uri/hash |
| 5 | `ui/screen/home/components/HomeCompletedSection.kt:110-118` | `LazyRow` + `itemsIndexed(completedBooks, key = { _, b -> "completed-${b.id}" })` |
| 6 | `HomeCompletedSection.kt:154` | 每卡 `Modifier.testTag("completed-card-${book.id}")` |
| 7 | `HomeCompletedSection.kt:186-212` | 封面右上角翡翠绿「已读完」印章（`Color(0xFF059669)`） |
| 8 | `HomeCompletedSection.kt:243-253` | 封面下方绿点 + 「已读完」文本（`labelSmall`） |
| 9 | `res/values/strings.xml:15` | `home_completed` = **「已阅读完成」**（区标题用词，与卡片上的「已读完」不是同一字符串） |

**可观察结果（UI XML + 截图，逐卡）：** 对期望集合中的每一本，**每张卡**都要有：
① `completed-card-<bookId>` 节点存在；② 书名文本节点；③ 至少一处「已读完」文本节点；④ 卡片在 `completed-lazy-row` 内。

**关键已知坑（C 阶段 §7.1 已踩过，本轮必须规避）：** 横向 `LazyRow` **屏外卡位不产生文字节点**。
一次 dump 只能看到屏内的卡。C 阶段曾因两次 fling 跳过卡位而误判「首页继续阅读排除了某书」，结论当场修正。
本轮要求：`take(8)` 最多 8 张，**必须分段滑动 + 每段独立 dump + 全量归并去重**，不得以单次 dump 的缺失判定"卡片不存在"。

### 1.3 场景 C —— 取消读完后再次阅读时百分比自愈

**前置状态（由 A 产生，再执行取消）：** `progress_percent=100.0`（残余）、`completion_state=reading`（取消后）、`completed_at=NULL`、`current_location_json=locator_v2 offset 1080127`（未动）、`last_read_at` 未动。与 C 阶段报告 §五「取消读完」行一致。

**自愈链（已核对）：**

| 跳 | 位置 | 行为 |
|---|---|---|
| 1 | `ui/viewmodel/ReaderDocumentLoader.kt:345-350` | `parseStoredAbsoluteOffset(locationJson)` 读 `locator_v2.legacyOffset` → 恢复到该偏移。**只认 `locator_v2`；若仍是 `legado_position_v1` 则走 `resolveImportedTextOffset` 另一条路** |
| 2 | `ui/screen/reader/ReaderProgressEffects.kt:383-407` | 滚动模式：恢复滚动 + `LegadoMarkerSaveGuard.anchorAt` + `pendingInitialPositionState.value = false` |
| 3 | `ui/screen/reader/ReaderProgressEffects.kt:295-308` | 恢复完成后 500ms 防抖 `SaveProgress`，`percent = offset*100/totalChars`（约 61%，**不是 100**） |
| 4 | `data/local/entity/ReadingEntities.kt:80-96` | `base.copy(progress_percent = percent, …)`：**无条件用新算出的 percent 覆盖库里的残余 100**；`state` 因 `percent < 99.5` → `READING`；`completed_at` 保持 `null` |
| 5 | `ui/util/BookReadiness.kt:85-93` | 书卡文案 `formatBookProgressForCard`：`>=10f → "${toInt()}%"` → 由「100%」变为「62%」这类真实值 |

**可观察结果（DB + 书卡 UI，同一时点）：**

| 对象 | 期望 |
|---|---|
| DB `progress_percent` | 由 `100.0` → **≈61**（= 1080127 / totalChars ×100；具体值以实测 totalChars 为准，**只要显著 < 99.5 即算自愈**） |
| DB `completion_state` | 仍 `reading`（自愈不重新置 finished） |
| DB `completed_at` | 仍 `NULL` |
| DB `current_location_json` | 仍 `locator_v2`，`legacyOffset` 未漂移 |
| DB `updated_at` | **必须变化**（证明本次保存确实发生） |
| 书卡文案 | 由「100%」→「<n>%」真实值；仍归「在读」分组 |

**必须在验收前确认的前置（否则会误判）：** 该书的 `current_location_json` 必须是 `locator_v2` 而非 `legado_position_v1`。
若为 `legado_position_v1` 且章号越界/目录不可用，`unconvertedLegadoPosition != null` → guard 武装 → 未移动位置则**一直抑制保存**，百分比不会回落。
**这是 S12 既有的、有意为之的保护语义，不是本场景的缺陷**，遇到时应改判为「夹具不满足前置」，先把书翻动一次再验。

---

## 2. 共用前置（一次建立，三项复用）

### 2.1 设备与构建输入

| 项 | 要求 | 备注 |
|---|---|---|
| 设备 | `adb devices` 只应有 `emulator-5556`；所有命令显式 `-s emulator-5556` | 单设备所有者 |
| 宿主 APK | 沿用 D 阶段已安装的 `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`（qahost） | 若需重建，必须重记哈希并重试全部三项 |
| 测试 APK | `b8b3787b2b29ca5fe77e604c20af9b32b310f7956a7420837909c064e9e189e2`（播种用） | 与 C 阶段 S05 报告逐字节一致，已交叉印证 |
| 源码输入 | 冻结后取 `git rev-parse HEAD` + S05 相关文件 SHA-256 清单 | D 阶段要求冻结，冻结期不得并行改同一构建输入 |
| 安装方式 | `adb install -r`（保留数据） | **禁止** uninstall、`pm clear`、不带 `-r` 的安装 |
| wm density | 验收期 `wm density 420`（手机竖屏布局，与 C 阶段一致）；收尾 `wm density reset` | 物理密度 280 |
| 夜间模式 | **以当前基线为准，不要为对齐 C 阶段报告的 `no` 去切换** | D 阶段 G4 实测基线为 `yes`，已登记为跨会话变化（与 D1「S06 E1 夜间模式翻转」同类）。本轮只记录、不改 |

### 2.2 夹具播种（复用仓库既有 instrumentation，不伪造）

```
adb -s emulator-5556 shell am instrument -w -r \
  -e class com.creationreadingassistant.feature.sync.LegadoMarkerRealImportTemporaryTest#testProductionImportDirectlyIntoQahostDatabaseForUiAcceptance \
  com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

- 期望：`OK (1 test)`、EXIT 0。该用例经生产 `LegadoReadingProgressImporter.importArchive` 真实解析受控 ZIP 并用 Room DAO 落库，**不是直接 SQL INSERT**。
- 测试书：`id = 00000000-0000-4000-8000-000000000012`，书名「测试 TXT」，正文 `…/files/books/<id>/test_s12_isolated.txt`。
- **C 阶段结束时该书已删除干净**（books 目录 22 项、DB 0 行残留），D 阶段 G4 也确认「全程未播种、不存在」→ **本轮必须重新播种**，不能假设它还在。
- 播种后须按 C 阶段 `38-fixture-normalize.txt` 的做法把本书 `content_status` 由 `READY` 规范化为 `available`
  （统计页口径为 `content_status == "available"`，其余站点用「非 failed/missing/downloading」；不改则统计面把本书排除在分布外）。
  **该直改仅作用于本测试书，随冷备回写一并还原。**

### 2.3 基线哈希与冷备（三项共用一套，验收前采一次）

| 类别 | 采集对象 | 用途 |
|---|---|---|
| 主 DB | `/data/user/0/com.creationreadingassistant.qahost/databases/creation_reading_assistant_native` 的 SHA-256 + 行数（books / reading_progress / reading_sessions） | 事后对账 |
| **冷备份** | 整个 qahost 数据目录打成 tar 拉回本地（C 阶段与 D 阶段 G4 均用此法） | **回写用，见 §7** |
| 设置 | `shared_prefs/*.xml` + `files/datastore/*.pb`（C 阶段为 13 个） | 逐项哈希 |
| 既有书籍文件 | `files/books/**`（C 阶段为 36 个，分布在 22 个目录项） | 逐项哈希 |
| 设备参数 | `wm size` / `wm density` / `user_rotation` / `accelerometer_rotation` / `Night mode` / `stay_on_while_plugged_in` / `screen_off_timeout` | 事后回读 |
| 测试书正文 | `test_s12_isolated.txt` 自身 SHA-256 | 证明夹具未变 |

### 2.4 三项共用的「保存是否发生」判据

判定顺序固定，任何一项不满足先归操作问题：

1. `updated_at` 变化 ⇒ 本次写库**确实发生了**；未变 ⇒ 保存路径被门控挡住（看下面 2、3）。
2. `pendingInitialPosition` 是否已清（初始定位是否完成）⇒ 未完成则 `canPersistNormalReadingProgress=false`。
3. `LegadoMarkerSaveGuard` 是否仍武装 ⇒ 武装且未移动位置则抑制（既有保护语义）。
4. 是否等到 500ms 防抖之后（建议每次操作后**静置 ≥1.5s 再采集**）。
5. 是否走了 `navigationMode=temporary` 查阅入口 ⇒ 该模式下所有普通保存被抑制。

---

## 3. 场景 A —— 执行步骤与证据清单

**A-0 前置**：播种完成、`content_status` 已规范化、基线已采；DB 中该书为 `progress_percent=0.0 / reading / completed_at=NULL / legado_position_v1{ci:99,co:500}`。

| 步 | 操作 | 等待 | 采集 |
|---|---|---|---|
| A-1 | 打开测试书进入阅读器（**普通入口**，不得从搜索结果进 → 那是 temporary 模式） | 首屏排版完成 ≥2s | `A1-reader-open.png` + `A1.xml`；读 DB 得 `A1-db.txt` |
| A-2 | 点出底部控制条，把全书进度滑块**拖到最右端并松手** | ≥1.5s（等 500ms 防抖） | `A2-slider-100.png` + `A2.xml`（应见进度 100% 或书末页） |
| A-3 | （保险）再轻点一次屏幕/小幅滑动，制造一次位置偏离，确保 guard 已解除 | ≥1.5s | `A3-nudge.png` |
| A-4 | 正常返回退出阅读器（触发退出落库），回到书架 | ≥1.5s | `A4-shelf.png` + `A4.xml`；**`A4-db.txt`（判定权威）** |
| A-5 | 打开首页 | ≥1s | `A5-home.png` + `A5.xml`（供 B 复用） |

**A 的证据清单（缺一不可）：**

| 证据 | 文件 | 判定点 |
|---|---|---|
| 阅读器滑块 100% 截图 | `A2-slider-100.png` | 滑块拇指在最右、气泡/百分比显示 100% |
| 书架归入已完成 | `A4-shelf.png` + `A4.xml` | 分组计数变化 + 书卡「已读完」+ 封面「读毕」印章 |
| DB 前后 | `A1-db.txt` → `A4-db.txt` | `100.0 / finished / completed_at 非 NULL / locator_v2 legacyOffset≈totalChars / updated_at 变化` |
| 命令与退出码 | `A-commands.log` | 每条 adb 命令 + EXIT |

**A 的通过判据**（DB 为准，UI 为辅）：`progress_percent ≥ 99.5` **且** `completion_state = "finished"` **且** `completed_at` 由 NULL 变为非 NULL。
**任一不满足 → 不写 PASS，按 §6 分流。**

### A 的口径提示（需总指挥裁决，见 §9-①）

`seekToPercent(100)` 是**跳转到书末**，不是「逐页读到书末」。两者在 `mergeReaderProgress` 层等价（都是 `percent=100 → FINISHED`），
但严格说本场景驱动的是「落到书末后由自动保存写入 finished」，而非「连续翻页到最后一页」。
**这是当前代码里唯一可在设备上一轮内完成的路径**：自动翻页最快档为「93 秒翻完一屏」（`AutoPaging.kt` 速度 1 → 102-9=93s；速度 10 → 12s/屏），
169 章的书按自动翻页跑完不现实。建议接受本路径并在报告中把口径写全，不写「逐页读到书末已验」。

---

## 4. 场景 B —— 执行步骤与证据清单

**B-0 前置**：A 已完成，该书带 `completed_at` 进入首页完成区（若 `completed_at` 较旧可能排序靠后，`take(8)` 有被挤出风险 —— 采集前先用 DB 算出期望集合与顺序，见下）。

**B-1 先用 DB 算出期望集合（这一步必须在 UI 采集之前）：**

```sql
-- 期望集合 = isBookDisplayable 且 completion_state='finished'，按 completed_at 倒序取前 8
SELECT b.id, b.title, p.completed_at, p.updated_at
FROM books b LEFT JOIN reading_progress p ON p.book_id = b.id
WHERE b.deleted_at IS NULL
  AND b.content_status NOT IN ('failed','missing','downloading')
  AND b.size > 0
  AND (b.local_content_path IS NOT NULL OR b.local_uri IS NOT NULL OR b.content_hash IS NOT NULL)
  AND p.completion_state = 'finished'
ORDER BY COALESCE(p.completed_at, 0) DESC;
```

把前 8 行记为**期望卡片清单**（id + 书名 + 顺序）。UI 侧的逐卡比对以它为准。

| 步 | 操作 | 等待 | 采集 |
|---|---|---|---|
| B-1 | 首页滚到「已阅读完成」区，全区块截图 | ≥1s（等进场动画 240ms 完成） | `B1-completed-block.png` + `B1.xml` |
| B-2 | 横排**小幅左滑一屏**，停稳后 dump | ≥1s | `B2.png` + `B2.xml` |
| B-3 | 重复 B-2 直到横排滑到底（最多 8 张卡，约 2–3 段） | ≥1s/段 | `B3.png` / `B3.xml` … `Bn.xml` |
| B-4 | （可选）反向滑回，交叉验证同一批卡位 | ≥1s | `B-rev.xml` |

**B 的证据清单：**

| 证据 | 判定点 |
|---|---|
| `B1..Bn.xml` 全量归并 | 每一张期望卡片都能找到 `completed-card-<bookId>` 节点 |
| 每卡文本 | 书名节点存在 + 至少一处「已读完」文本节点 |
| 顺序 | UI 出现顺序与 DB `completed_at` 倒序一致（同一卡位在两段 dump 中重复出现时按首次出现位置计） |
| `B1-completed-block.png` | 区标题「已阅读完成」、右侧「全部」可见 |
| 期望清单 | `B-expect.txt`（B-1 的 SQL 输出） |

**B 的通过判据**：期望清单中每张卡都能在归并后的 XML 中找到，且都带「已读完」文本。
**严禁**以单次 dump 的缺失判定缺卡 —— 屏外卡位无文字节点是已知渲染行为，不是缺陷。

---

## 5. 场景 C —— 执行步骤与证据清单

**C-0 前置**：A 已完成（`100.0 / finished / completed_at 非 NULL`）。

| 步 | 操作 | 等待 | 采集 |
|---|---|---|---|
| C-1 | 书局长按测试书 →「取消已读完」（`setReadingState(READING)`） | ≥1s | `C1-cancel.png` + `C1.xml`；**`C1-db.txt`** |
| C-2 | **核对 `current_location_json` 必须是 `locator_v2`** | — | 若为 `legado_position_v1` → 夹具前置不满足，先按 §1.3 末段处理 |
| C-3 | 重新打开测试书（普通入口），等初始定位完成并静置 | ≥2s（排版）+ ≥1.5s（防抖） | `C3-reopen.png` + `C3.xml`；`C3-db.txt` |
| C-4 | （保险）轻滑一次制造位置偏离，确保 guard 解除 | ≥1.5s | `C4.xml` |
| C-5 | 正常返回退出，回到书架 | ≥1.5s | `C5-shelf.png` + `C5.xml`；**`C5-db.txt`（判定权威）** |

**C 的证据清单：**

| 证据 | 判定点 |
|---|---|
| `C1-db.txt` | `100.0 / reading / completed_at=NULL / current_location_json 未动`（与 C 阶段报告 §五「取消读完」行一致） |
| `C3-db.txt` 或 `C5-db.txt` | `progress_percent` 由 `100.0` → **显著 < 99.5**；`completion_state` 仍 `reading`；`completed_at` 仍 `NULL`；`legacyOffset` 未漂移；**`updated_at` 已变化** |
| `C5-shelf.png` + `C5.xml` | 书卡文案由「100%」变为真实百分比（如「62%」），仍归「在读」分组 |
| `C-commands.log` | 每条命令 + EXIT |

**C 的通过判据**：`updated_at` 变化 **且** `progress_percent < 99.5` **且** `completed_at` 仍为 `NULL`。
三者同时成立才算自愈已验。

---

## 6. 失败时如何区分产品缺陷与测试操作问题

### 6.1 通用分流（先过这一层）

| 观察 | 归**测试操作问题**的条件 | 归**产品缺陷**的条件 |
|---|---|---|
| `updated_at` 未变 | 未等 500ms 防抖；未退出阅读器；走了 temporary 入口 | 已静置 ≥1.5s、已正常退出、普通入口，仍不写库 |
| `updated_at` 变了但字段不对 | —— | 字段值不符合 §1 的期望（**这是缺陷信号**） |
| UI 与 DB 不一致 | 截图早于重组稳定；横排屏外节点缺失；夜间模式配色差异 | 已静置、已分段 dump、文本节点确实与 DB 相反 |
| 数值边界 | `percent` 落在 99.5 附近的取舍（`merge` 用 99.5，`SaveProgress` 构造用 99.9 —— 不一致是有意的分层：构造值是 incoming 意图，`merge` 是权威） | 明确远离边界仍判错 |

### 6.2 场景 A 专项

| 现象 | 先排查（操作） | 判为缺陷的信号 |
|---|---|---|
| `percent` 未到 99.5 | 拖动未松手（无 `onValueChangeFinished`）；落点被章末/页末吸附回退；`pagedJumpRequest` 未生效时书未真正到末尾 | 落点 UI 已显示书末且 `legacyOffset≈totalChars`，但 `percent` 仍 <99.5 → `TxtPagedController:135-147` / `ReaderProgressEffects:266-275` 的百分比计算问题 |
| `percent=100` 但 `state=reading` | `updated_at` 是否已变（未变 = 保存没发生，操作问题） | `updated_at` 已变且 `percent≥99.5` 而 `state≠finished` → `mergeReaderProgress`（`ReadingEntities.kt:73-79`）被绕过或走了别的写入路径 |
| `completed_at` 仍 NULL | 同上 | `state=finished` 而 `completed_at` 仍 NULL → `ReadingEntities.kt:90-94` 分支异常 |
| `current_location_json` 仍是 `legado_position_v1` | guard 未解除（未移动位置） | 已明显移动位置且 `updated_at` 已变，location 仍未更新 |

### 6.3 场景 B 专项

| 现象 | 先排查（操作） | 判为缺陷的信号 |
|---|---|---|
| 期望卡在 XML 中找不到 | 只 dump 了一次（屏外节点缺失）；`take(8)` 排序挤出（`completed_at` 旧） | 已分段滑到底并归并，期望卡仍缺失 → `HomeViewModel.kt:129-138` 过滤或 `HomeCompletedSection.kt:110-118` 渲染问题 |
| 卡在但缺「已读完」文本 | dump 时进场动画未完成；卡片宽度为 116dp、文字被 `maxLines=1 + Ellipsis` 截断（**注意：省略号截断仍是同一文本节点，不算缺失**） | 节点稳定且文本确实为其他文案 → `HomeCompletedSection.kt:186-212` / `:243-253` 文案或 `BookReadiness.kt:85-93` 口径问题 |
| 多出非 finished 的书 | 期望清单算错（`content_status` 未规范化为 `available`；统计页与其余站点口径不同） | 期望清单正确而 UI 多出非 finished 书 → `isReadingFinished` 未生效 |

### 6.4 场景 C 专项

| 现象 | 先排查（操作） | 判为缺陷的信号 |
|---|---|---|
| `percent` 仍 100 且 `updated_at` 未变 | 未等防抖；未退出；`current_location_json` 是 `legado_position_v1` 且 guard 武装未解除（**既有保护语义，改判夹具前置不足**） | `updated_at` 已变而 `percent` 仍 100 → 恢复偏移未生效或百分比计算错误（`ReaderDocumentLoader.kt:345-350` / `ReaderProgressEffects.kt:266-275`） |
| `percent` 回落但 `completion_state` 变 finished | 落点被算到 ≥99.5（例如恢复到书末附近） | 回落值明显 <99.5 却仍写 finished → `ReadingEntities.kt:73-79` 判定问题 |
| `completed_at` 被重新写入 | —— | 取消后再次阅读不应重新置完成时间；若写入 → `ReadingEntities.kt:90-94` 问题 |
| `legacyOffset` 漂移 | 恢复后又被翻页/滑动改变 | 未做任何移动而 `legacyOffset` 变化 → 恢复路径改写位置 |

---

## 7. 恢复与收尾（三项全部做完后一次性执行）

1. `adb shell am force-stop com.creationreadingassistant.qahost`。
2. **用 §2.3 的冷备份 tar 回写** qahost 数据目录，**不要靠 SQL 删除测试书来"恢复"**。
   理由：S12 已实测「删除行后主 DB SHA 由 `d938c7b8…` 变为 `7bd076c6…`，大小相同但字节不同」（SQLite 页标记可复用），
   逻辑删除无法还原字节；本轮承诺的「主库逐字节一致」只能靠冷备回写达成。
3. 回读并与 §2.3 基线逐项比较：主 DB SHA-256、13 个设置文件、36 个既有书籍文件、`books` 目录项数（应为 22）、DB 行数、测试书 books/progress 行（应 0 残留）。
4. 设备参数回读：`wm size` / `wm density reset` / `user_rotation` / `accelerometer_rotation` / 夜间模式 / `stay_on_while_plugged_in` / `screen_off_timeout`，全部与基线一致。
5. 清理 `/sdcard` 与 `/data/local/tmp` 中转文件（`grep -c 'cra-'` 应为 0）。
6. 属主与权限恢复为 `u0_a70:u0_a70`、DB `660`。
7. 安装状态说明：qahost 宿主/测试 APK 保留安装（沿用历次会话惯例），**不回退旧版 APK**；如需回退由总指挥裁决。

**诚实边界（照抄 D 阶段 G4 的写法）**：只写「主库逐字节一致 + `-shm`/`-wal`、`profileInstalled`、WorkManager 临时文件可能变化」，**不写「整包逐字节恢复」**。

---

## 8. 边界与不得外推

| 项 | 状态 |
|---|---|
| 本文件三项场景 | **NOT RUN**（本文件只是计划） |
| C 阶段七站截图 | 证明的是**显式 markFinished / 取消**路径，**不得**外推为三项已通过 |
| 设备口径 | 仅 MuMu `emulator-5556` 模拟器 / qahost 隔离沙箱；**不代表实体手机或 MIUI** |
| 真实进程 kill（S14 双 rename 间杀进程） | NOT RUN，本计划不涉及 |
| 整包 DB+文件原子性 | 未验，本计划不涉及 |
| 正式书库 / 发布签名 / 原仓库 | 不触碰 |
| 夜间模式下的上述页面 | 未验（以基线值为准执行，不切换） |
| 统计页非相关区块（人物拓扑/足迹/趋势） | 范围外 |

---

## 9. 需要总指挥裁决的口径点

1. **场景 A 的驱动方式**：接受「滑块跳到 100% 后由自动保存写 finished」作为「真实读到书末自动完成」的设备证据，
   还是必须坚持「连续翻页到最后一页」（按 `AutoPaging.kt` 的速度模型，169 章的书一轮内不可行）。
   —— 建议接受前者并在报告中写全口径，不写「逐页读到书末已验」。
2. **场景 C 的夹具前置**：若播种后该书的 `current_location_json` 仍是 `legado_position_v1`（未换算），
   自愈会被 S12 的 `LegadoMarkerSaveGuard` 抑制。是先翻动一次再验（同一场景内解决），
   还是换用不带 Legado marker 的夹具重播种（需重新走一遍播种与基线）—— 建议前者。
3. **设备基线夜间模式**：D 阶段实测为 `yes`，C 阶段 S05 报告为 `no`，变化发生在两次会话之间（与 D1「S06 E1 夜间模式翻转」同类）。
   本计划按「以当前基线为准、只记录不切换」处理；若总指挥要求先隔离该变化，需另开窗口，不并入本轮三项。

---

## 10. 本文件的协作纪律

- 未运行 Gradle / ADB / MuMu；未占用设备。
- 未修改任何产品源码、测试源码、构建配置或他人报告。唯一写入文件即本文件。
- 未 commit / push / reset / checkout；未触碰原仓库、实体手机、正式书库。
- 与另一窗口不写同一文件（本文件为 S05 设备计划，不覆盖 `2026-09-28-stage-d-independent-review.md` 或 `2026-09-28-stage-d-gate-review.md`）。
