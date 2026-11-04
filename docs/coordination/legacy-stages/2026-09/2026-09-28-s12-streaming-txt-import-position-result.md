# S12 结果：流式大 TXT 导入 Legado 位置后首开回书首 — 2026-09-28

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜本轮仅 JVM 定向 Gradle，**未运行 MuMu/ADB/真机**，未进原仓库。
- 任务：处理调查报告 P2-2「流式大 TXT 导入 Legado 章节位置后，首次打开静默回书首」。
- **结论先行：可靠章节边界源存在且已核实——流式路径打开时即可获得 `TxtFileIndex`（对全文按同一 profile 单遍扫描的完整逻辑章节表，与阅读器自身目录/阅读单元同源，非 ReadingUnit/分页窗口）。最小修复 = 首开换算时以它为流式章节源（身份匹配 + 真实多章门控）；无可靠边界时保持 marker 并暴露用户提示接缝，不编造位置。定向 JVM 25/25 全绿。**

## 1. 当前调用链（LegadoProgressMarker(ci,co) 全路径，逐环核实）

```
导入（本轮只读）：LegadoReadingProgressImporter.locationFor → 非 EPUB 一律
  LegadoProgressMarker.encode(ci, co) = {"legado_position_v1":{"ci":N,"co":M}}  Importer:245-249
落库：progressDao.upsert（事务内 shouldApply 比较）——标记在首次原生保存前持续存活
首开：ReaderDocumentLoader.load（TXT 分支 :127-238）
  ├─ profile = rulesRepository.observe(bookId)（effectiveTocProfile，:138）
  ├─ TextStreamLoader.load(uri, profile)（:145-150）
  │    小文件 → fullText + TxtChapterDetector.detect（内存全文检测）
  │    大文件 → TxtFileScanner.scan(file, profile)（全文单遍流式扫描）
  │              → TxtFileIndex(chapters[charStart/charCount], detectedRuleId=profile.key, totalCharCount)
  │              → 经 TxtFileIndexCache（缓存身份 = profile.key，规则变化即失效）
  ├─ preDetected（仅小文件非流式生成，:171-195）
  ├─ 【本轮修复点】流式章节源 = streamingFileIndex 门控换算（见 §2）
  └─ initialAbsoluteOffset = resolveImportedTextOffset(locationJson, chapters, textLength)
       marker 解码成功 → chapters.getOrNull(ci) ?: return 0 → (startOffset+co) 钳 [0,textLength]
       非 marker（原生 locator）→ parseStoredAbsoluteOffset 直接读 v2 legacyOffset
消费：ReaderScreen:220 savedPlainOffset = initialAbsoluteOffset
  → 滚动恢复 ReaderProgressEffects:294-315（unitIndexForOffset 按 ReadingUnit 全局偏移定位）
  → 分页恢复 ReaderContentHostPagedBranch:56-62 initialOffset
后续：首次原生保存（防抖/退出）写原生 locator，marker 被覆盖（既有语义）
```

**修复前的断点**：流式路径 `preDetected` 恒为空 → `resolveImportedTextOffset` 拿到空章节表 → `getOrNull(ci) ?: return 0` → 静默回书首。

## 2. 可靠章节边界源的证据（为什么不违反「不得用窗口近似」红线）

流式路径打开时 `result.fileIndex` 非空，其 `chapters` 来自 `TxtFileScanner.scan(file, profile)`：

1. **完整而非窗口**：对全文做**单遍逐行顺序扫描**（内存仅驻留一行），每个标题命中生成 `ChapterEntry(charStart, byteStart, charCount)`，`charStart/charCount` 覆盖全书、连续无缺口（`TxtFileScanner.kt:186-247,313-386`）。ReadingUnit/checkpoint 只是渲染分片与字节对齐检查点，未参与本换算。
2. **与阅读器目录同源**：本书流式 TOC 与阅读单元分组消费的就是这份索引（`PlainTextDocument.fromFileIndex`）；「Legado ci N → 本阅读器目录第 N 章」是读者可见语义。
3. **检测语义同构**：标题匹配走同一 `profile.patterns`、同一 `MAX_TITLE_LENGTH=40`、同一密度保护（均长 <300 字回退「全文」）、同样的「开篇」首章处理，与 `TxtChapterDetector.detect`（小文件路径、既有已发布换算）逐条对应。
4. **身份可校验**：`detectedRuleId = profile.key`；`TxtFileIndexCache` 缓存身份即 `profile.key`，规则变化自动失效——与 `preDetectedRuleId` 的既有身份纪律一致。

## 3. 改动文件（1 生产 + 1 新测试）

| 文件 | 改动 |
|---|---|
| `ui/viewmodel/ReaderDocumentLoader.kt`（独占） | ①流式章节源：`streamingFileIndex` 经 `detectedRuleId == profile.key && chapters.size >= 2` 门控映射为 `DocChapter` 表，`conversionTextLength` 流式取 `totalCharCount`（小文件路径行为逐字不变）；②`ReaderLoadedContent.Text` 新增可空默认字段 `unconvertedLegadoPosition: LegadoChapterPosition?`（用户提示接缝，见 §6）；③新增 `LegadoChapterPosition` 导入 |
| `app/src/test/.../ui/viewmodel/ReaderDocumentLoaderStreamingLegadoTest.kt`（新建） | 6 个端到端 JVM 用例（走真实 TextStreamLoader→TxtFileIndexCache→TxtFileScanner 链） |

- **`LegadoReadingProgressImporter.kt` 本轮零改动**（按任务书只读；TXT 分支无需改——marker 结构不变，改的只是首开消费侧）。
- **`LegadoProgressMarker.kt` 本轮零改动**（encode/decode 语义原样）。
- 上一轮 EPUB locator 契约（`LocatorCodec/LocatorBuilder/Importer EPUB 分支/EpubRepository`）未触碰；其定向测试在本轮套件中回归通过。
- `ReaderLoadedContent.Text` 唯一另一构造点 `ReaderSingleCorrectionCapabilityTest:525` 因新字段带默认值而零影响（全量测试源编译通过）。

## 4. 测试证据

命令（`android/` 下，同一过滤器共 4 轮）：

```bash
./gradlew.bat :app:testDebugUnitTest \
  --tests "...ui.viewmodel.ReaderDocumentLoaderStreamingLegadoTest" \
  --tests "...ui.viewmodel.ReaderDocumentLoaderLegadoProgressTest" \
  --tests "...ui.viewmodel.ReaderDocumentLoaderProfileTest" \
  --tests "...feature.sync.LegadoReadingProgressImporterTest" \
  --tests "...feature.sync.LegadoImportEpubLocatorContractTest"
```

| 轮次 | 退出码 | 说明 |
|---|---|---|
| run1 | 1 | **编译失败**：新测试类 `InMemoryRuleDao.deleteCustom` 笔误（`entity` → 应为 `id`），未执行任何测试 |
| run2 | 0 | 25/25（接缝字段加入前） |
| run3 | 1 | **编译失败**：生产文件漏 `LegadoChapterPosition` 导入，未执行任何测试 |
| run4 | **0** | **25 tests / 0 failures / 0 errors / 0 skipped**（`BUILD SUCCESSFUL in 1m 35s`） |

run4 逐类（XML 汇总见证据 `15-run4-xml-summary.txt`）：StreamingLegado 6、LoaderLegadoProgress 5、LoaderProfile 6、Importer 6、EPUB 契约 2。

任务书五场景 → 用例映射（全部经**真实加载链**，非纯函数自喂）：

| 场景 | 用例 | 断言要点 |
|---|---|---|
| 普通 TXT 位置保持 | `small txt keeps imported legado position via pre-detected chapters` | 两章小书 marker(1,5) → 第2章起点+5，非流式路径行为不变 |
| 流式有完整章节源 | `streaming txt converts legado chapter position from full file index` | 12 章大文件 marker(9,123) → `chapters[9].charStart+123`；接缝为 null |
| 越界不伪装成功 | `out of range legado chapter falls back to book start…` | marker(999,0) → 0（书首）；接缝非空携带原坐标 |
| 来源不完整不伪装 | `chapterless streaming file never converts against the degenerate full-text entry` | 无标题大文件 → 「全文」单章被门控排除；marker(0,5000) **不**落 5000（不把章内偏移当全书偏移）；接缝非空 |
| 原生进度不被导入覆盖 | 读侧：`native locator progress is honored directly over marker conversion`（v2 locator 777_777 直读）；写侧：既有 `LegadoReadingProgressImporterTest.does not overwrite an existing native plain text position`（导入器等值/落后不覆盖） | 两级互证 |
| 退出重进可恢复 | `converted position survives exit and re-enter via native save round trip` | 首开换算 X → 模拟原生保存落库 → 二次 `load()` 读回同一 X |

## 5. 数据兼容边界（如实声明）

1. **marker 生命周期**：换算成功路径上 marker 仍保留在库中，直到首次原生保存（防抖 500ms / 退出保存）以原生 locator 覆盖——与既有语义一致，本轮未改。
2. **不可换合场景的存量风险（既有，未扩大）**：无可靠边界时打开落书首，随后任意一次原生保存会把 **offset=0 的原生 locator** 覆盖 marker，导入位置自此丢失。该「保存覆盖」在 `ReaderProgressEffects`/`ReaderProgressActions`（UI/进度 owner 文件），本轮不可改；接缝字段已让 UI 能在覆盖发生前提示用户（见 §6 SEAM-1）。
3. **「开篇」索引偏移**：正文前有前置内容时，本阅读器目录（两条路径同构）会在 index 0 插入「开篇」章，Legado 无此概念 → 此类文件的 ci 映射可能整体 +1。与小文件路径的已发布启发式同一等级（调查 B-4「目录不一致落到近似位置」），未修，属跨检测器对齐的产品决定。
4. **流式无自动嗅探**：小文件兜底会嗅探编号样式目录（`fallbackOrSniff`），`TxtFileScanner` 不嗅探 → 同一书两路径目录可能不同。换算一律用**流式书自己的目录**（即阅读器实际显示的），保证所见即所跳；差异本身登记为既有行为。
5. **上一轮 EPUB 契约**：本轮测试套件含其全部契约测试，回归通过；EPUB 路径零改动。

## 6. SEAM REQUEST（本轮零跨界修改）

| # | 请求 | 说明 |
|---|---|---|
| SEAM-1 | **UI 提示接线**：`ReaderLoadedContent.Text.unconvertedLegadoPosition` 非空时提示「导入的开源阅读位置暂未生效（未能取得可靠章节目录），已从书首打开；进度标记已保留」。消费方在 `ReaderScreen`/`ReaderProgressEffects`（进度/UI owner 文件）。字段已就绪（含 KDoc），UI 侧无需再解码 marker | 任务书要求的「明确的用户提示接缝」；接线时机与文案归总指挥/UI owner |
| SEAM-2 | **可选加固**：不可换算场景下，UI owner 可考虑在用户看到提示前抑制对 marker 书的首次原生落库（避免 offset=0 覆盖导入位置）。涉及保存门控（`ReaderProgressEffects`/`canPersistNormalReadingProgress` 一线），不在本轮授权 | 防导入位置被静默清掉的纵深防御；与 SEAM-1 同窗口裁决 |

## 7. 未解决项

1. SEAM-1/SEAM-2 的 UI 侧接线与裁决（本轮只交付了数据接缝）；
2. 「开篇」索引对齐、流式嗅探差异（§5.3/5.4）——产品决定，登记不动；
3. 真机/端到端验收（6MB 级真实书 + 真实导入）仍待设备通道；本轮为 JVM 定向证据，不写成设备验收；
4. 调查报告其余 P2 项（P2-3 完成态静默提升、P2-4 导入中状态）维持原登记。

## 8. 证据路径

`E:\creation-reading-assistant\codex-s12-streamtxt-20260928\`：

| 工件 | 内容 |
|---|---|
| `00-changed-files-git-status.txt` | 改动文件 git 状态（Loader `M`；Importer/marker 为前轮遗留 `??` 且本轮零改动；新测试 `??`） |
| `10/11/13/14-targeted-jvm-run{1..4}.log` | 四轮原始输出（含两次编译失败与最终 EXIT=0，如实留存） |
| `12-run2-xml-summary.txt`、`15-run4-xml-summary.txt` | XML 逐类汇总（最终 25/0/0/0） |
| `TEST-*.xml`（5 份） | Gradle 原始 XML |

Gradle 缓存沿用既有 `D:\develop\env\gradle`；日志/XML 工件落 E 盘（构建目录重定向需改 Gradle 配置，不属本轮授权）。

---

本轮未 commit/push/stage/reset/checkout/clean；未清理他人未提交文件；书籍仅用「测试 TXT」等中性名称；未发现与窗口 2/3 的所有权交叉。交总指挥复核，本棒停止。
