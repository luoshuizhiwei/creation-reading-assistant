# S12 只读静态调查：Legado 阅读进度导入完整链路 — Qoder

> 调查 Agent：Qoder（窗口 D）　|　声明模型：`qwen3.8Flash`
> 任务编号：S12-INVESTIGATION（严格只读静态调查，不实现修复）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`　固定基点：`origin/main = 5aed5939600e491843e3c44453921bd6273a233c`
> 开工 `git rev-parse --short HEAD` = `5aed593`
> 唯一产物：`docs/coordination/investigations/agent-investigation-s12-qoder.md`（本文件）
> 计划文档：`docs/plans/android/2026-09-14-android-legado-progress-import-slice.md`（**不当成现状**，逐条回到源码核对）
> 结论口径：以下所有事实均来自当前工作树源码与测试；推断、边界风险与未验证项均单独标注

---

## 1. Executive Summary

1. **链路是完整的、可跑的**：WebDAV 扫描 `backup*.zip` → 用户点「导入记录」→ 确认对话框 → 下载（64 MiB 上限）→ ZIP 内取 `bookshelf.json` → 解析 → 书名+作者归一化匹配 → 事务内读-判-写 → 首次打开按格式换算 → 后续正常保存改写为本应用 locator。每一步都有真实生产接线，没有 UI 有入口但无生产接线的半成品。
2. **格式边界与计划文档一致且已在代码里落地**：EPUB 写 locator v2，纯文本写 `legado_position_v1` 标记，Markdown 在匹配阶段被排除并计入 `unsupported`（`LegadoReadingProgressImporter.kt:99`、`:229-230`）。计划文档 §5 记录的 P1「Markdown 往返断裂」**当前已修**，且有 2 条回归用例咬住。
3. **最大结构性问题不是导入器本身，而是 EPUB `current_location_json` 没有单一写入契约**：至少 4 个应用内写入点按 **3 套不同的量纲约定**写同一字段（详见 §4）。导入器写的是其中量纲不自洽的一套，因此计划文档 §6.1 登记的「`globalOffset` 语义污染」**在当前代码里仍然成立**（P2-1）。
4. **计划文档 §6.1 的「正常阅读一次即自愈」结论只对了一半**：分页引擎下确实自愈；legacy 滚动 EPUB 的**章变更自动保存**（`ReaderChapterLoaderCoordinator.kt:57-66`）写的是 `(ci, co=0, offset=0)`，同样通不过 `SourceNavigationContract` 的一致性校验，因此该模式下不自洽状态会被反复重新引入。这是既有问题、非 S12 引入，但它是「缺契约」的直接证据。
5. **新发现（计划文档未登记）**：导入可以**把一本长篇的最后章节静默标成「已完成」**。`approximatePercent()` 用章号比算百分比，而 `mergeReaderProgress` 用 `percent >= 99.5f` 强制推导 `FINISHED`，且 `FINISHED` 在后续保存中被永久保持。等级 **P2**（可手动撤销，但用户无提示）。
6. **预览链缺失**：写入前用户看不到「会匹配几本、跳过哪些、覆盖哪些」；确认对话框只描述规则不描述结果；结果只有一条会自行消失的 Snackbar，无导入中状态、按钮不禁用。等级 **P2**（重复点击给出互相矛盾的反馈）。
7. **测试只覆盖纯函数层**：导入器 6 例、换算 5 例、标记 2 例、WebDAV 传输层若干例。**Manager 层的 `importLegadoBackup` / `listLegadoBackups` 零测试**，EPUB 首次打开消费路径零测试，重启持久化零测试，UI 预览/确认零测试。现有绿灯不构成「链路可用」的证据（详见 §6）。
8. **本轮不做**：不运行 Gradle、不运行 ADB、不改任何代码；真机与端到端行为全部列为未验证。

---

## 2. 当前生产调用链

全部为真实接线（非注释/非死代码），按执行顺序。

### 2.1 入口与列表

| 步 | 位置 | 事实 |
|---|---|---|
| 1 | `ui/screen/profile/WebDavSubPage.kt:585-699` | 「从开源阅读导入记录」卡片（`SettingsAnchor("legado-progress-import-card")`），标题右侧徽标 `${legadoBackups.size} 份备份` |
| 2 | `WebDavSubPage.kt:612-617` | 卡片说明文案：只读 `bookshelf.json`、只更新唯一匹配本地书、Markdown 暂不支持 |
| 3 | `WebDavSubPage.kt:618-630` | 「扫描开源阅读备份」按钮 → `ProfileAction.RefreshLegadoBackups`；`enabled = url.isNotBlank()` |
| 4 | `ui/screen/profile/ProfileRoute.kt:462` | → `viewModel.loadLegadoBackups()` |
| 5 | `ui/viewmodel/ProfileViewModel.kt:629-638` | `viewModelScope.launch(ioDispatcher)` → `webDavManager.listLegadoBackups()`；成功写 `_legadoBackups`，失败**静默置空列表**（无错误提示） |
| 6 | `ui/viewmodel/ProfileWebDavManager.kt:34-37` | 未配置 WebDAV 返回 `null`（调用方保持现状）；否则 `runCatching{ webDavBackup.listLegadoBackups(...) }` |
| 7 | `feature/sync/WebDavBackup.kt:249-290` | PROPFIND Depth:1 → `parsePropFind` → `filter { name.startsWith("backup") && endsWith(".zip") }`（`:277`），按解析后的 HTTP 日期倒序 |
| 8 | `WebDavSubPage.kt:639` | 列表**只渲染前 15 条**（`take(15)`），显示 name / `formatBytes(size)` / lastModified |

### 2.2 选择、确认、下载、解析

| 步 | 位置 | 事实 |
|---|---|---|
| 9 | `WebDavSubPage.kt:680-690` | 每条一个「导入记录」按钮 → `ProfileAction.ImportLegadoBackup(file.name)`。**无导入中状态、不禁用** |
| 10 | `ProfileRoute.kt:463-469` | `onConfirmDialogChange(ConfirmSpec(title="导入开源阅读记录", message=…, onConfirm={ viewModel.importLegadoBackup(context, action.filename) }))` |
| 11 | `ProfileRoute.kt:366-375` | `GlassAlertDialog`，按钮「确认」/「取消」 |
| 12 | `ProfileViewModel.kt:676-689` | `_webDavMsg=null` → `webDavManager.importLegadoBackup(...)`；成功 `_webDavMsg = report.summary()`，失败 `_webDavMsg = "开源阅读记录导入失败：${message}"` |
| 13 | `ProfileWebDavManager.kt:73-84` | 未配置直接抛「请先填写 WebDAV 配置」→ `DatabaseSafetyNet.snapshotNow(context, AppDatabase.DB_NAME)`（快照失败不阻塞）→ `webDavBackup.getBytes(maxBytes = 64 MiB)` → `legadoImporter.importArchive(zip)` |
| 14 | `WebDavBackup.kt:163-203` | `requireSafeTarget` + `requireSafeFilename`（允许 Unicode，`:342` 注释说明开源阅读会把设备名拼进文件名）→ `resolveTarget` → `withRetry` GET → 先查 `Content-Length`，再对流式读取逐块校验上限 |
| 15 | `LegadoReadingProgressImporter.kt:161-192` | `parseArchive`：`require(size <= 64 MiB)`；`ZipInputStream` 逐条目计数 `require(entries <= 256)`；命中根目录 `bookshelf.json`（`\` 归一为 `/`）→ `readAtMost(16 MiB)`；缺失 → `IllegalArgumentException("不是开源阅读备份：缺少 bookshelf.json")`；非数组或解析失败 → `IllegalArgumentException("$BOOKSHELF_ENTRY 不是有效的书架列表")` |
| 16 | `LegadoReadingProgressImporter.kt:196-211` | `parseProgress`：需要 `name` 非空、`durChapterIndex`、`durChapterPos` 均存在且 ≥ 0；`durChapterTime` 夹紧到 ≥0；`durChapterTitle`/`totalChapterNum` 可选。任一不满足 → 该条计入 `invalid`（`:186-190`） |

### 2.3 匹配与比较

| 步 | 位置 | 事实 |
|---|---|---|
| 17 | `LegadoReadingProgressImporter.kt:82-86` | `bookDao.getAllActive()`（`BookDao.kt:68-69`，`deleted_at IS NULL`，**不过滤 `content_status`**）按 `BookKey` 分组；远端条目按同一 key 分组并用 `REMOTE_ORDER`（`:298-300`，ci → co → 时间，**不看 durChapterTime 最新优先**）取最大者 |
| 18 | `LegadoReadingProgressImporter.kt:280-287` | `BookKey.normalize`：`trim` + 连续空白折叠为单空格 + `lowercase(Locale.ROOT)`；author null 视作 `""` |
| 19 | `LegadoReadingProgressImporter.kt:92-103` | 分支顺序：`isEmpty → unmatched++`；`candidates.none(::supportsImportedPosition) → unsupported++`；`size == 1 → 匹配`；`else → ambiguous++`。**歧义判定在 unsupported 之后，不因过滤而放宽** |
| 20 | `LegadoReadingProgressImporter.kt:229-230` | `supportsImportedPosition`：`format.lowercase() !in setOf("md","markdown")`。与 `ReaderDocumentLoader.kt:84` 的 Markdown 分支判定集完全一致 |
| 21 | `LegadoReadingProgressImporter.kt:108-115` | `bookDao.runInTransaction { … }`（`BookDao.kt:117-121` 的 `@Transaction`）内**先**批量读现有进度（`progressDao.getByBooks`，`ReadingDao.kt:20-21`；`matches` 为空时跳过查询，避免 `IN ()`） |
| 22 | `LegadoReadingProgressImporter.kt:251-278` | `shouldApply`：无现有进度或 `current_location_json` 空白 → 应用；`readingState == FINISHED` → 跳过；否则解析本地坐标 —— EPUB 走 `LocatorCodec.locatorFromProgress`，**回落正则 `"chapter"`/`"offset"`**；纯文本走 `LegadoProgressMarker.decode`。**本地解析不出坐标（原生 TXT 绝对偏移）时一律跳过**（`:275`），注释明确「绝不以外部章内偏移覆盖本地精确全书偏移」；比较为 `ci >` 或 `ci == 且 co >`（**等号不覆盖**） |

### 2.4 写库

| 步 | 位置 | 事实 |
|---|---|---|
| 23 | `LegadoReadingProgressImporter.kt:122-132` | 构造 `ReadingProgressEntity`：`progress_percent = approximatePercent()`（`:217-220`，`ci/total*100`，无 `totalChapterNum` 时 **0f**）；`last_read_at = durChapterTime`（≤0 时用 now）；`completion_state = existing?.completion_state ?: READING`；`current_location_json = locationFor(book, remote)` |
| 24 | `LegadoReadingProgressImporter.kt:235-249` | `locationFor`：EPUB → `LocatorBuilder.progressJson(legacyOffset=co, chapterIndex=ci, charOffset=co, globalOffset=co, legacyChapter=ci)`；其余（纯文本）→ `LegadoProgressMarker.encode(ci, co)` |
| 25 | `feature/reader/locator/LocatorBuilder.kt:36-55` | 产出 `{"chapter":ci,"offset":co,"locator_v2":{"v":2,"offset":co,"ci":ci,"co":co}}`。**注意 v2.offset 被写成章内偏移**（`globalOffset` 入参即 `co`） |
| 26 | `LegadoReadingProgressImporter.kt:133-141` | `progressDao.upsert(mergeReaderProgress(existing, incoming, …))` → `data/local/entity/ReadingEntities.kt:67-97`。`current_location_json` 采信 incoming（`:85`）；**`completion_state` 由 `percent >= 99.5f` 重新推导**（`:74-79`），导入传入的 state 被覆盖 |
| 27 | `LegadoReadingProgressImporter.kt:145-153` | 返回 `Report(scanned = valid+invalid, imported, unchanged, unmatched, ambiguous, unsupported, invalid)`；`skipped` 为后五项之和 |

### 2.5 首次打开消费

`ui/viewmodel/ReaderDocumentLoader.kt:54-237` 是唯一文档打开入口，按 `metadata.format.lowercase()` 三分支：

| 格式 | 消费点 | 换算 |
|---|---|---|
| `epub` | `:67` `epubRepository.loadProgress(bookId).coerceIn(0, chapters.lastIndex)`；`:76` `loadProgressOffset(bookId)` | `feature/reader/EpubRepository.kt:262-276`：优先 `LocatorCodec.locatorFromProgress(raw)` 的 `chapterIndex` / `charOffset`，无 v2 才回落顶层 `"chapter"`/`"offset"` 正则。**不识别 `legado_position_v1`**（EPUB 也不需要） |
| `md`/`markdown` | `:113-119` `parseStoredOffset` + `space != "canonical"` 时 `migrateLegacyOffset` | 导入永远不会走到这里（匹配阶段已排除） |
| 其余（TXT） | `:203-207` `resolveImportedTextOffset(locationJson, chapters = preDetected, textLength = fullText.length)` | `:313-321`：`LegadoProgressMarker.decode` 失败 → `parseStoredAbsoluteOffset`（v2.legacyOffset 优先，回落 `"offset"` 正则）；成功 → `chapters.getOrNull(ci)`，**越界或 chapters 为空返回 0**，否则 `(chapter.startOffset + co).coerceIn(0, textLength)` |

`preDetected` 只在**非流式**路径生成（`:171-195`：`fullText.isNotEmpty() && streamingDocument == null`）。流式大 TXT 的 `chapters` 恒为空列表 ⇒ 导入位置恒回落 0（书首）。

消费后的落点：
- EPUB 分页引擎：`ui/screen/reader/ReaderContentHostPagedBranch.kt:56-59` `initialOffset = chapterStartOffsets[chapterIndex] + savedEpubOffsetInChapter`
- EPUB 滚动模式：`ui/screen/reader/ReaderProgressEffects.kt:319-333` `targetGlobal = chapterBase + savedEpubOffsetInChapter` → `blockGlobalOffsets.indexOfLast{…}` → `scrollToItem`
- TXT：`ui/screen/reader/ReaderScreen.kt:220` `savedPlainOffset` → 滚动/分页两条恢复路径
- 章号初值：`ReaderDocumentLoadEffects.kt:79-81` `chapterIndex = epubContent?.initialChapterIndex ?: 0`，`pendingInitialPosition = loadedBook != null`（阻止首帧把恢复位置覆盖回 0，见 `ReaderProgressEffects.kt:642-645`）

### 2.6 后续保存回本应用原生坐标

| 触发 | 位置 | 写入内容 |
|---|---|---|
| TXT/Markdown 滚动 500ms 防抖 | `ReaderProgressEffects.kt:178-236`（`epubBook != null` 时 `:179` 直接 return） | `progressJson(legacyOffset=绝对偏移, chapterIndex=反推章号, charOffset=绝对偏移, space=canonical?)` ⇒ **标记被原生 locator 覆盖** |
| 分页引擎 500ms 防抖 | `ReaderProgressEffects.kt:241-290`；EPUB 分支 `:267` `SaveEpubProgress(bid, ci, pct, chapterOffset, absoluteOffset = off)` | EPUB：`EpubRepository.kt:228-244` `legacyOffset=章内`、`globalOffset=真全局` ⇒ **自洽** |
| 退出/离开保存 | `ReaderProgressActions.kt:180-260`（EPUB 分支 `:236` `globalOffset = chapterStart + chapterOffset`） | 自洽 |
| **章节变更自动保存** | `ReaderChapterLoaderCoordinator.kt:57-66` `epubRepository.saveProgress(bookId, chapterIndex, percent)`（默认 `offsetInChapter=0`、`absoluteOffset=-1`） | `(ci, co=0, offset=0)` ⇒ 除首章外**不自洽** |
| 书架「标记已读完 / 取消已读完」 | `ui/viewmodel/ShelfBookActions.kt:91-97` → `BookRepository.setReadingState` | 只改状态与 `completed_at` |

`LegadoProgressMarker` 没有任何显式清除逻辑；它被原生 locator **覆盖**即失效（TXT 首次落库、EPUB 不适用）。

---

## 3. TXT / Markdown / EPUB 矩阵

| 维度 | TXT（纯文本） | Markdown | EPUB |
|---|---|---|---|
| 是否允许导入 | ✅ | ❌ 匹配阶段排除（`Importer:99`、`:229-230`） | ✅ |
| 远端坐标 | `durChapterIndex` + `durChapterPos`（开源阅读自己的目录正则 + 源文本章内偏移） | 同左 | 同左 |
| 导入时实际写入 | `{"legado_position_v1":{"ci":N,"co":M}}`（`Importer:248`） | 不写，`unsupported++` | `{"chapter":ci,"offset":co,"locator_v2":{"v":2,"offset":co,"ci":ci,"co":co}}`（`Importer:238-244` + `LocatorBuilder:36-55`） |
| 首次打开由谁消费 | `ReaderDocumentLoader:203-207` → `resolveImportedTextOffset`（`:313-321`） | — | `EpubRepository.loadProgress` / `loadProgressOffset`（`:262-276`），经 `ReaderDocumentLoader:67/76` |
| 如何换算 | `chapter.startOffset + co`，用**本应用自己的**目录检测结果（`TxtChapterDetector` + 本书 `TxtTocProfile`）；`coerceIn(0, textLength)` | — | 不换算：`ci` 直接当章节索引（越界被 `coerceIn(0, lastIndex)` 夹紧），`co` 当章内偏移 |
| 重启后能否恢复 | ✅ 只要目录规则未变且非流式；标记本身持久在 `reading_progress` | 不适用 | ✅ `ci`/`co` 均从 locator 读回 |
| 首次正常保存后是否还留标记 | ❌ 被原生 locator 覆盖（`ReaderProgressEffects:228-233` / `:280-285`） | — | 不适用（本来就是 locator，但**量纲要等一次自洽写入才对**） |
| 章号越界 | `resolveImportedTextOffset:319` → 返回 **0**（书首），有测试 | — | `ReaderDocumentLoader:68` `coerceIn(0, chapters.lastIndex)` → 夹到最后一章；章内偏移越界 → `ReaderProgressEffects:327-329` `indexOfLast` 落到最后一个块（章末），`AnchorResolver` 路径另有 `coerceIn(0, chapterText.length)` |
| 超长偏移钳制 | ✅ `coerceIn(0, textLength)`，有测试（`…LegadoProgressTest:43-49`） | — | 无钳制（依赖 block 索引查找自然落到末尾，不崩溃） |
| 目录不一致 / 文档变化 | 落到**近似或错误位置**（启发式，计划文档 §6 已承认）；无等价性证明 | 不适用（正因如此才排除） | 章节数变化（重新解析/替换正文）→ `coerceIn` 夹紧，可能跳章 |
| 流式大文件 | ⚠️ `chapters` 为空 ⇒ **回书首，且 UI 无提示**（P2-2） | — | 不适用 |
| 本地已有原生进度 | `LegadoProgressMarker.decode` 返回 null ⇒ `shouldApply` 直接 **false**（`:275`），绝不覆盖 | — | 解析本地 locator 后按 `ci/co` 严格大于才覆盖 |
| 本地已 FINISHED | 跳过（`:257`） | — | 跳过 |
| 无本地正文的书（`content_status`） | ⚠️ `getAllActive` 不过滤 ⇒ 照样写入进度，打开报「本书暂无可阅读的正文」 | — | 同左 |
| 书架章号显示 | ✅ `ShelfModels.kt:283-292` 的 `"ci"` 正则能读标记里的 `ci` | — | ✅ 读 v2 `ci` |

---

## 4. EPUB 坐标 owner 与写入契约

### 4.1 谁解释导入坐标

**`EpubRepository.loadProgress` / `loadProgressOffset`（`EpubRepository.kt:262-276`）是 EPUB 恢复位置的唯一解释者**，它只认 locator v2 的 `ci`/`co`（回落顶层 `"chapter"`/`"offset"` 正则）。导入器写的正是这个结构，所以**恢复本身能工作**：章号与章内偏移都会被读到。

导入器**不**经过 `EpubRepository`，而是自己拼 `LocatorBuilder.progressJson`。

### 4.2 谁有写入权（当前事实：4 个写入点、3 套量纲约定）

| 写入点 | 顶层 `offset` | v2 `offset`(legacyOffset) | v2 `ci` | v2 `co` | 是否自洽（`chapterStart + co == offset`） |
|---|---|---|---|---|---|
| `LegadoReadingProgressImporter:238-244` | `co`（章内） | `co`（章内） | ci | co | ❌ 除首章外全部不自洽 |
| `EpubRepository.saveProgress` ← `ReaderProgressEffects:267`（分页防抖） | 章内 | **真全局** | ci | 章内 | ✅ |
| `EpubRepository.saveProgress` ← `ReaderProgressActions:253`（退出保存） | 章内 | **真全局** | ci | 章内 | ✅ |
| `EpubRepository.saveProgress` ← `ReaderChapterLoaderCoordinator:65`（章变更自动保存） | 0 | 0 | ci | 0 | ❌ 除首章外全部不自洽 |
| `JsonBridge:217`（本应用备份恢复） | 取决于备份 | 取决于备份 | — | — | 未知 |
| `SyncRepository:234-252` | 不改 `current_location_json`（只改 `payload`） | — | — | — | 不参与 |

**结论：当前没有形成单一写入契约。** `EpubRepository` 是读侧的默认 owner，但既不是唯一写者，也不校验写入是否满足自身不变式；导入器与章节自动保存都会写出不自洽 locator。

### 4.3 不自洽的实际后果（已核对当前代码，非推断）

`SourceNavigationContract.resolveChapteredPosition`（`:82-121`）在 `preferGlobalOffset=false`（EPUB 调用点，`ReaderProgressEffects.kt:426`）时：

```
absoluteOffset = locator.legacyOffset ?: chapterStart + chapterOffset      // :109
if (!preferGlobalOffset && explicitChapter != null && explicitOffset != null &&
    locator.legacyOffset != null && chapterStart + explicitOffset != locator.legacyOffset
) return null                                                              // :111-115
```

⇒ 不自洽 locator 会让**所有由 `pendingSourceLocatorJson` 驱动的回源跳转**（搜索结果、临时检视返回、按 locator 的定位）返回 null，落到 `ReaderProgressEffects.kt:373-376` 的 `showNotice("此阅读位置与当前正文不一致")` 并**不跳转**。

不受影响的路径（已核对）：
- 首次打开恢复：走 `loadProgress`/`loadProgressOffset`，不读 `legacyOffset` ⇒ ✅ 正确
- 按标注/笔记定位：`AnchorResolver.resolve`（`AnchorResolver.kt:22-29`）优先 `chapterIndex`，仅越界才回落 `legacyOffset` ⇒ ✅ 正确
- `ReaderTemporaryRoute.encodeSourceLocator`（`:43-55`）要求 `legacyOffset != null`，导入 locator 有值 ⇒ 能编码，但带出去的是章内偏移，回返时同样被 `:111-115` 拒绝

**自愈条件（对计划文档 §6.1 的更正）**：分页引擎下一次防抖保存即自洽；**legacy 滚动 EPUB 只有退出保存（`persistCurrentProgress`）会自洽**，而期间任何章跳转都会由 `ReaderChapterLoaderCoordinator:65` 重新写回不自洽值。因此「正常阅读一次即自愈」在 legacy EPUB 模式下**不成立**。这是既有行为，非 S12 引入，但同属"缺契约"的表现。

### 4.4 语义漂移点（百分比 / 章号 / 章内偏移 / locator）

| 量 | 导入侧 | 原生侧 | 漂移 |
|---|---|---|---|
| `progress_percent` | 章号比 `ci/total*100`（`Importer:217-220`），无 total 时 **0** | 字符比（`ReaderProgressEffects:281` / `ReaderProgressActions:239-251` 含 `EpubProgressCalibration` 实测校准） | 首次打开并保存后百分比会**跳变**；无 `totalChapterNum` 的书导入后显示 0% |
| `ci` | 开源阅读目录序号 | 本应用解析出的 EPUB spine 章节索引 | 只有当两边章节切分数一致时同义；EPUB 无目录规则，风险较低但不为零 |
| `co` | 章内字符偏移 | 章内字符偏移（同量纲） | 量纲一致，但**字符计数口径**（是否含标签/空白）未证明等价 |
| v2 `offset` | 章内偏移（错） | 全局偏移 | **就是 §4.2 的不自洽** |

### 4.5 需要裁决的最小问题（不实现）

1. EPUB 进度 locator 的**唯一写入者**是否收敛为 `EpubRepository.saveProgress`（导入器改为调用它，或引入 `EpubProgressWriter` 门面）？
2. 是否把「`chapterStart[ci] + co == offset`」做成**写入时断言**（不满足则拒绝或延迟换算），而不是读取时靠 `SourceNavigationContract` 事后拒绝？
3. `ReaderChapterLoaderCoordinator:65` 的「章变更即写章首」是否应当继续写 locator（它会把精确位置降为 0），还是只更新 `chapter_reads`？
4. 导入是否允许**延迟换算**：像 TXT 一样写「待换算标记」，在首次打开取得 `chapterStartOffsets` 后回写自洽 locator？（计划文档 §6.2 指出的正确方向，超出导入器边界，需 EPUB/locator owner 决定）

---

## 5. 预览、确认和结果反馈链

| 类别 | 是否存在 | 具体内容与位置 |
|---|---|---|
| **写入前预览** | ❌ **完全没有** | 没有任何"试算/扫描结果"接口。`importArchive` 解析与写入一体，无法只读预演。用户在点确认前不知道：会匹配几本、哪些跳过、哪些本地进度会被覆盖、无法解析几条 |
| **确认弹窗** | ✅ 有，但只描述**规则**不描述**结果** | `ProfileRoute.kt:463-469`：标题「导入开源阅读记录」；正文逐字为「将只读取 ${filename} 内的 bookshelf.json，并只导入能以书名和作者唯一匹配到本地 EPUB 或纯文本书籍的阅读位置。不会导入书源、设置、书架分组或正文；本地已有更可靠位置的书，以及 Markdown 书，都会跳过。确认继续？」⇒ 含**文件名**、含格式范围、含跳过规则；**不含**「不可撤销」「会覆盖本地进度」「预计条数」 |
| **写入后汇总** | ✅ 有，但载体弱 | `Importer:53-66` `summary()` →「已导入 N 条开源阅读记录；跳过 M 条（未匹配 X、歧义 Y、暂不支持导入 Z、本地位置更靠后 W、格式无效 V）」；经 `ProfileViewModel.kt:681` → `ProfileRoute.kt:187` `showMsg` → Snackbar（`ProfileRoute.kt:182`，`scope.launch{ snackbarHostState.showSnackbar }`） |
| **导入中状态** | ❌ 无 | `ProfileViewModel.kt:676-689` 无 busy 标志；`WebDavSubPage.kt:680-690` 按钮无 `enabled` 条件 ⇒ 64 MiB 下载期间界面无任何反馈，可连续点击 |
| **仅存在于 Report、用户看不到的信息** | ⚠️ 多处 | ① `scanned` 总数只进 `AppLog.event("WebDAV","开源阅读记录导入：imported/scanned")`（`ProfileViewModel.kt:682`），UI 不展示；② 具体**哪几本**书被导入/跳过/歧义/不支持，`Report` 只有计数，**没有明细**，任何地方都拿不到；③ `listLegadoBackups` 失败时 `ProfileViewModel.kt:635` 只把列表置空，**错误原因不显示**（与 `listBackups` 同风格，但用户会以为"目录里没有备份"）；④ 快照是否成功（`DatabaseSafetyNet.snapshotNow` 内部吞异常）用户无从知晓 |
| **可撤销性** | 部分、未告知 | 有 `DatabaseSafetyNet.snapshotNow`（`ProfileWebDavManager.kt:75`）整库文件快照，但确认对话框**没有**像 `DownloadRestore`（`ProfileRoute.kt:455-461`）那样写明「恢复前已自动备份数据库快照」。应用内**没有**导入撤销入口 |

---

## 6. 测试覆盖矩阵

判定口径：只有当测试**驱动了生产函数并断言其对外可见结果**时才算覆盖；仅断言 mock 被调用、或只测纯函数的一段，标注为「部分」。

| # | 行为 | 现有测试 | 真实覆盖判定 |
|---:|---|---|---|
| 1 | 唯一匹配 + EPUB 坐标保持 | `LegadoReadingProgressImporterTest:29-63` | ✅ 驱动 `importArchive` 全链（真解析 + 真 `mergeReaderProgress`），断言 locator `ci=8`/`co=321` 与 `percent=40f`。**但不断言 v2 `offset`** ⇒ §4.2 的量纲错误正好落在断言盲区 |
| 2 | 本地原生进度不被覆盖 | 同上 `:66-84` | ✅ 断言 `upsert` 调用 0 次 |
| 3 | 纯文本写标记而非裸偏移 | 同上 `:87-99` | ✅ 逐字比对 `LegadoProgressMarker.encode(3,77)` |
| 4 | Markdown 计入 unsupported 且不写 | 同上 `:102-120` | ✅ 含 `summary()` 文案断言 |
| 5 | Markdown+TXT 混合候选仍判歧义 | 同上 `:123-140` | ✅ |
| 6 | 缺 `bookshelf.json` | 同上 `:143-147` | ✅ |
| 7 | TXT 坐标往返 | `ReaderDocumentLoaderLegadoProgressTest:11-20`、`:23-31` | ✅ 驱动生产 `resolveImportedTextOffset` |
| 8 | 章号越界回书首 | 同上 `:34-40` | ✅ |
| 9 | 超长偏移钳制 | 同上 `:43-49` | ✅ |
| 10 | 原生 locator 不受影响 | 同上 `:52-56` | ✅ |
| 11 | 标记编解码 + 缺半 + 无关 JSON | `LegadoProgressMarkerTest:10-20` | ✅ 纯函数 |
| 12 | ZIP 下载 Unicode 文件名 + 大小上限 | `WebDavBackupTest:177-190` | ✅ 真 MockWebServer |
| 13 | `listLegadoBackups` 只留根目录 backup*.zip | `WebDavBackupTest:302-327` | ✅ |
| 14 | **解析异常（非法 JSON / 非数组 / 字段缺失）** | 仅 #6；非法 JSON、非数组、`durChapterIndex` 缺失/负数 | ❌ **未覆盖**（代码路径存在：`Importer:180-183`、`:199-201`） |
| 15 | **重复/歧义书名（远端同书多条目）** | #5 是本地多候选；远端同 key 多条目取 `REMOTE_ORDER` 最大 | ❌ **未覆盖** |
| 16 | **已完成书籍跳过** | `shouldApply:257` 的 FINISHED 分支 | ❌ **未覆盖** |
| 17 | **等值进度的 `unchanged` 语义**（重复导入同一备份） | #2 是「本地更靠后且非标记」 | ❌ **未覆盖**（等值不覆盖是契约，且决定重复点击是否安全） |
| 18 | **EPUB 坐标导入 → 首次打开消费** | 无 | ❌ **零覆盖**。`EpubRepository.loadProgress/loadProgressOffset` 没有任何测试驱动；`ReaderDocumentLoader` 的 EPUB 分支无测试 |
| 19 | **TXT 导入 → 打开 → 落库 → 重启恢复** | #7 只测换算函数 | ⚠️ 部分：跨 `importArchive` 与 loader 的端到端往返、Room 持久化、重启恢复均无测试（JVM 层无 Room 集成，`androidTest` 中无 Legado 相关文件） |
| 20 | **导入写出的 locator 是否自洽 / 回源跳转是否可用** | 无 | ❌ 零覆盖。`SourceNavigationContract` 有自己的测试，但没有任何用例把「导入产物」喂给它 |
| 21 | **Manager 层 `importLegadoBackup` / `listLegadoBackups`** | `ProfileWebDavManagerTest` 全文 10 例（`:32-146`）**无一条涉及 Legado** | ❌ 零覆盖：未配置报错、快照调用、`maxBytes=64MiB` 传参、失败传播均未测 |
| 22 | **ViewModel 层导入消息 / 日志** | `ProfileViewModelTest:132` 只把 `legadoImporter` 建成 relaxed mock，全文 4 例均与 Legado 无关 | ❌ 零覆盖 |
| 23 | **UI 预览 / 确认对话框 / Snackbar** | 无 Compose 测试 | ❌ 零覆盖（确认文案、按钮禁用、15 条截断均未测） |
| 24 | **部分失败 / 事务回滚**（第 2 本 `upsert` 抛异常） | 无 | ❌ 零覆盖。且 `runInTransaction` 在测试里被 stub 成直接执行（`ImporterTest:35`），**原子性从未被真正验证** |
| 25 | **`completion_state` 被 merge 改写** | 无 | ❌ 零覆盖（正是 §7 P2-3 的成因） |
| 26 | ZIP 条目过多 / 16 MiB 超限 | `parseArchive:169`、`readAtMost:308-310` | ❌ 未覆盖（有代码路径与异常消息，无测试） |

**总体判断**：测试覆盖的是「导入器的匹配与分类决策」和「TXT 换算函数」，这两块质量不错（断言真实生产函数、逐字比对写入内容）。但**跨边界链路（写库→打开→消费→回存）与所有 UI/Manager 接线基本无覆盖**，因此现有绿灯不能证明 S12 端到端可用，尤其**不能证明 EPUB 能被正确恢复**（EPUB 侧唯一的真实证据是 §2.5 的静态读码）。

---

## 7. 问题清单与严重度

### 7.1 现役可达缺陷

**P2-1 导入的 EPUB locator 量纲不自洽，回源跳转被拒**
- 触发前提：导入任一 EPUB（非首章），随后从搜索结果 / 临时检视返回 / 按 locator 定位。
- 源码证据：`LegadoReadingProgressImporter.kt:238-244`（`globalOffset = remote.charOffset`）→ `LocatorBuilder.kt:45` → `SourceNavigationContract.kt:109-115` 一致性校验返回 null → `ReaderProgressEffects.kt:373-376` 提示「此阅读位置与当前正文不一致」。
- 用户影响：点搜索命中/检视返回不跳转并弹一条令人困惑的提示；正常阅读并落库一次后消失（分页模式）。**不产生错误定位**。
- 等级理由：P2 而非 P1 —— 有明确自愈路径、不损坏数据、不影响主阅读；P2 而非 P3 —— 现役可达且提示语会误导用户以为位置丢了。
- 最小修复边界：`locationFor` 的 EPUB 分支不再自拼 locator，改走 `EpubRepository.saveProgress` 或延迟到打开时换算（§4.5 第 4 项）。**需要产品/架构裁决**（延迟换算涉及读侧识别标记）。
- 备注：计划文档 §6.1/§6.2 已登记并明确「未修」，本轮确认在当前代码仍成立，且更正了其「自愈」范围（见 §4.3）。

**P2-2 流式大 TXT 导入后打开回书首，且无任何提示**
- 触发前提：本地 TXT 走 `TextStreamLoader` 的流式分支（大文件），且已导入进度。
- 源码证据：`ReaderDocumentLoader.kt:171-195`（`preDetected` 仅非流式非空）→ `:203-207` 传入空 `chapters` → `:319` `chapters.getOrNull(ci) ?: return 0`。导入器与 `Report` 都不区分这一情形（`Importer:99-100` 只看 format）。
- 用户影响：看到「已导入 N 条」，打开却在书首；标记仍在库里，用户不知道是"没生效"还是"这本书本来在读开头"。大 TXT 恰是从开源阅读迁移的主要场景。
- 等级理由：P2 —— 可达、影响明确、但保守失败（不跳错位置）且无数据损坏。
- 最小修复边界：`Report` 增加「待换算 / 未取得目录」计数并在 `summary()` 如实说明；或在流式路径用 `TxtFileIndex` 的章起点做换算。**不需要产品裁决**（如实计数是既定原则，见 `Importer:96-98` 注释）。

**P2-3 导入可把长篇的最后一章静默标成「已完成」**
- 触发前提：远端 `durChapterIndex` 为末章且 `totalChapterNum ≥ 200`（网文常见：1000 章时末章 = 99.9%）。
- 源码证据：`Importer:217-220` `approximatePercent` → `Importer:129` 传 `completion_state = existing ?: READING` → `ReadingEntities.kt:74-79` `percent >= 99.5f → FINISHED` **覆盖入参**，且 `existing FINISHED` 永久保持 → `ShelfModels.kt:93` 归入「已完成」分组。
- 用户影响：书出现在「已完成」、`completed_at` 被写、后续导入被 `shouldApply:257` 永久跳过。可用书架「取消已读完」撤销（`ShelfBookActions.kt:91-97`），但 `BookActionSheet.kt:88` 的 `isFinished` 仍按 `percent >= 99.5f` 派生，撤销后入口文案不会立刻变回「标记为已读完」。
- 等级理由：P2 —— 用户可见、非预期、不自愈，但可手动恢复且不丢进度。
- 最小修复边界：导入路径不得由百分比推导完成态（给 `mergeReaderProgress` 增加"来源不推断完成态"的显式入参，或导入时把 percent 夹到 99.4f）。**需要产品裁决**：导入是否有权改变完成状态（建议：无权）。

**P2-4 无导入中状态、按钮不禁用，重复点击给出互相矛盾的反馈**
- 触发前提：网络慢或备份接近 64 MiB；用户连点两次「导入记录」并两次确认。
- 源码证据：`ProfileViewModel.kt:676-689` 无 busy 标志；`WebDavSubPage.kt:680-690` 按钮无 `enabled`；第二次导入时本地已是等值坐标 → `shouldApply:276-277` 严格大于不成立 → `unchanged++` → `summary()` 文案「本地位置更靠后」。
- 用户影响：下载期间界面像卡死；随后两条 Snackbar 内容矛盾（「已导入 1 条」/「跳过 1 条（本地位置更靠后 1）」）。**数据不损坏**（等值不覆盖 + 事务内读-判-写）。
- 等级理由：P2 —— 现役可达、用户可明确感知、`unchanged` 的措辞在等值场景下是**事实错误**（不是"更靠后"而是"相同"）。
- 最小修复边界：一个 in-flight 标志 + 按钮 `enabled`；`summary()` 把等值与更靠后分开措辞。不需要产品裁决。

### 7.2 特殊数据下的边界风险（非现役缺陷）

| ID | 风险 | 证据 | 判定 |
|---|---|---|---|
| B-1 | 远端同书多条目取「章号最大」而非「时间最新」 | `Importer:84-86`、`:298-300` | 语义可辩（合并备份时章号最大通常即最新）；无测试 |
| B-2 | `bookshelf.json` 在 ZIP 子目录内 → 报「缺少 bookshelf.json」 | `Importer:170` 精确根路径匹配 | UI 只提示 WebDAV 端子目录（`WebDavSubPage.kt:631-636`），未提示包内结构 |
| B-3 | EPUB 章内偏移越界（本地章节更短） | `ReaderDocumentLoader:68` 夹章号；`ReaderProgressEffects:327-329` 落到最后一个块 | 落章末而非书首，不崩溃；无钳制断言 |
| B-4 | 用户自定义 TXT 目录规则与开源阅读不等价 | `ReaderDocumentLoader:138` 读本本书 `effectiveTocProfile` | 计划文档 §6 已承认的启发式取舍；后果是**跳错位置**而非回书首 |
| B-5 | 无本地正文的书也被写进度 | `BookDao.kt:68-69` 不过滤 `content_status` | 打开报「本书暂无可阅读的正文」；进度已落库 |
| B-6 | `totalChapterNum` 缺失 ⇒ `percent = 0f` | `Importer:217-220` | 书架显示 0%，但位置已导入 |
| B-7 | 正则解析标记：`LegadoProgressMarker.kt:17-18` 的 `[^{}]*` | 字段间出现嵌套对象即失效 | 计划文档 §6 已登记「应改用 kotlinx.serialization」，未修 |

### 7.3 上线前 readiness blocker

| ID | 项 | 现状证据 |
|---|---|---|
| R-1 | 真机 / 端到端验收 | 计划文档 §4.3 与 §6 明确「真机验收：未做」；本轮亦未运行设备。EPUB 首次打开、流式 TXT、重启恢复均无行为证据 |
| R-2 | EPUB 单一写入契约 | §4.2：4 个写入点、3 套量纲；无写入时校验 |
| R-3 | 结果可见性 | 明细（哪几本）任何地方都拿不到；汇总只在 Snackbar 一闪而过（§5） |
| R-4 | 关键链路测试 | §6 表 #14–#26：Manager/VM/UI/EPUB 消费/重启/部分失败全部零覆盖 |

### 7.4 纯测试与可维护性缺口

- `ImporterTest` 用 stub 执行 `runInTransaction`（`:35`），**原子性从未被验证**；`progressDao.getByBooks` 用精确 list 匹配 stub，任何匹配顺序变化都会静默改变被测行为。
- #1 用例名承诺「preserves epub chapter coordinate」，但**恰恰没断言 coordinate 之外的 `offset` 字段**，形成"名字覆盖、断言不覆盖"的假安全感。
- `LegadoProgressMarkerTest` 只 2 例，未覆盖字段顺序变化、多余键、负数以外的畸形输入。
- 计划文档 §4.1 记录的用例名 `…preserves epub coordinate` 与当前源码 `…preserves epub chapter coordinate` 已不一致（文档漂移，非缺陷）。
- `Importer:261-266` 在 EPUB 分支内内联两条 `Regex(...)` 字面量，与 `LocatorCodec`/`EpubRepository` 的同款正则重复第三份（可维护性）。

---

## 8. 产品 / 架构待裁决项

| # | 问题 | 影响面 | 建议方向（不实施） |
|---:|---|---|---|
| D-1 | **EPUB 进度 locator 的唯一写入者是谁**（`EpubRepository` 门面 vs 各调用点自拼） | §4 全部；也决定 TXT 是否同样收口 | 收敛到 `EpubRepository.saveProgress`，并在写入处断言 `chapterStart[ci]+co == offset`，不满足则拒写或降级为「仅章号」 |
| D-2 | **导入是否允许延迟换算 EPUB 坐标**（打开时再算全局偏移） | 决定 P2-1 的修法形态 | 与 TXT 对齐：写「待换算」标记 + 打开时换算 + 回写；需 EPUB/locator owner 定 |
| D-3 | **导入是否有权改变完成状态** | P2-3 | 建议无权：`mergeReaderProgress` 增加来源标记，导入不推导 FINISHED |
| D-4 | **`progress_percent` 的口径**：导入用章号比、原生用字符比，是否允许同一字段两种来源 | 书架显示、P2-3、B-6 | 要么导入只写坐标不写百分比（打开后由原生保存补），要么在 UI 上区分"估算" |
| D-5 | **是否需要写入前预览**（dry-run 匹配结果） | §5；用户目前只能盲签 | 把 `importArchive` 拆为 `plan()` + `apply()`，确认框展示匹配/跳过明细 |
| D-6 | **Markdown 是否永远不支持**，还是要求用户提供目录规则等价证据后开放 | §3；计划文档已选"排除" | 保持排除，但需要把"为什么"写进用户可见文案（当前只说"暂不支持"） |
| D-7 | **重复导入是否要求幂等提示**（等值 vs 更靠后） | P2-4 | 分开计数与措辞 |

---

## 9. 推荐的最小后续切片

按 ROI 排序，每片都可独立验收：

1. **S12-F1（修措辞与状态，最小）**：`Report` 拆分 `unchanged` 为 `equal`/`behind`；导入不再由百分比推导完成态（D-3）；`mergeReaderProgress` 增加显式来源参数。预计 2 个生产文件 + 2 个测试文件。无架构变更。
2. **S12-F2（可见性）**：导入中 busy 标志 + 按钮禁用；`listLegadoBackups` 失败原因显示；`summary()` 增加「待换算（大文件）」计数并修 P2-2 的提示。仅 `ProfileViewModel` / `ProfileWebDavManager` / `WebDavSubPage`。
3. **S12-F3（EPUB 坐标契约，需 D-1/D-2 裁决后）**：统一 EPUB 进度写入到 `EpubRepository`，加写入时自洽断言；`ReaderChapterLoaderCoordinator:65` 改为不覆盖精确位置。这一片同时消掉 P2-1 与既有的 legacy EPUB 不自洽问题，**属于 locator owner 的架构片，不是 S12 的顺手修改**。
4. **S12-F4（测试补口）**：Manager/VM 层导入接线测试（未配置、maxBytes、失败传播、消息）；`SourceNavigationContract` 吃导入产物的契约测试；`getByBooks` 空集合、非法 JSON、远端重复条目、FINISHED 跳过、等值不覆盖。**不新增源码正则守卫**。
5. **S12-F5（真机与端到端）**：用测试 EPUB / 测试 TXT / 测试 Markdown 各一本做真实导入 → 打开 → 重启 → 回源跳转全链验收；含流式大 TXT 的降级确认。

---

## 10. 本轮明确未做事项

- **未运行 Gradle**（任何 task，包括定向 JVM 与编译）；§6 的覆盖判定全部来自读码，未实测用例是否真的通过。
- **未运行 ADB、未操作手机**；无任何设备行为证据。
- **未修改任何文件**（除本报告的这一个）。未改 Kotlin / 测试 / Gradle / 脚本 / 既有文档。
- **未 commit / push / stage / stash / reset / checkout / clean**，未全仓格式化，未回退或覆盖工作区任何他人改动。
- **未进入原始仓库** `D:\develop\Code\Codex\creation-reading-assistant`。
- 未处理窗口 B（安装脚本）与窗口 C（点击区域）的文件，未读取或评论其报告。
- 未实现任何修复，未对 §8 的待裁决项做产品决定。
- 未做 Room 迁移/事务的运行时验证（`@Transaction` 原子性、`IN ()` 空集合行为均为静态判断）。
- 未验证开源阅读实际备份文件的真实字段形态（例如 `durChapterPos` 是否含换行/标签字符）；§3 的「量纲一致」是**代码层面**的一致，不是与上游数据的实证对齐。

---

## 11. 数量统计

| 级别 | 数量 | 条目 |
|---|---:|---|
| **P0** | **0** | — |
| **P1** | **0** | — |
| **P2** | **4** | P2-1 EPUB locator 量纲不自洽（回源跳转被拒）；P2-2 流式大 TXT 导入回书首且无提示；P2-3 导入把末章长篇静默标成已完成；P2-4 无导入中状态 + `unchanged` 措辞事实错误 |
| **P3** | **5** | P3-1 `LegadoProgressMarker` 用正则解析（B-7，计划文档已登记未修）；P3-2 EPUB 本地坐标解析内联重复第三份正则；P3-3 `ImporterTest` 用例名承诺 coordinate 覆盖但未断言 `offset`（假安全感）；P3-4 计划文档用例名与当前测试名漂移；P3-5 B-2 的 ZIP 内子目录提示缺失 |
| **边界风险（特殊数据）** | **7** | B-1 … B-7（§7.2） |
| **readiness blocker** | **4** | R-1 真机/端到端未验；R-2 EPUB 单一写入契约缺失；R-3 结果明细不可见；R-4 关键链路零测试 |
| **产品/架构待裁决** | **7** | D-1 … D-7（§8） |
| 测试/可维护性缺口 | 12 项行为零覆盖 + 4 项断言质量问题 | §6 表 #14–#26、§7.4 |

---

## 12. 边界遵守声明

| 约束 | 结果 |
|---|---|
| 唯一可写文件 = 本报告 | ✅ 本轮只创建 `docs/coordination/investigations/agent-investigation-s12-qoder.md` |
| 不修改 Kotlin / 测试 / Gradle / 脚本 / 既有文档 | ✅ 未使用任何编辑工具触碰它们 |
| 不运行 Gradle | ✅ 未执行任何 `gradlew` |
| 不运行 ADB / 不操作手机 | ✅ 未执行任何 `adb` |
| 不 commit / push / stage / stash / reset / checkout / clean | ✅ 全部未执行；`git diff --cached` = 0，`git stash list` = 0，HEAD 仍 `5aed593` |
| 不覆盖或回滚任何现有修改 | ✅ 窗口 B（`android/scripts/**`）与窗口 C（点击区域）的既有改动未触碰 |
| 不进入原始仓库 | ✅ 所有路径均为 `…-recovery-20260919` |
| 不把计划文档当现状 | ✅ §2–§7 每条结论都回到当前源码行号；对计划文档 §6.1 的「自愈」结论提出更正（§4.3） |
| 书籍只用中性名称 | ✅ 全文只出现「测试 EPUB / 测试 TXT / 测试 Markdown」，不含任何真实书名 |
| 缺测试 / 未来可能 ≠ P1 | ✅ §7 严格区分现役可达缺陷、边界风险、readiness blocker 与测试缺口；无 P0/P1 |

**未领取下一任务。S12 的修复、真机验收与后续切片均等待 Codex 主任务裁决（尤其 §8 的 D-1 / D-2 / D-3 / D-5）。**
