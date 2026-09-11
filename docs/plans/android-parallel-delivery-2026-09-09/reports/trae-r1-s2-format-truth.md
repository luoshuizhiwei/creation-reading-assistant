# Trae R1-S2 报告：导入 / 同步格式真源

- 工作树：`D:/develop/Code/Codex/creation-reading-assistant`
- 状态：**Dev complete（定向 JVM 测试通过，未真机/WorkBuddy 验收，交 WorkBuddy，真机验收留待两个并行修补冻结后统一安排）**
- 变更性质：新增纯 JVM 分类器 + 局部修改 ShelfImporter / SyncRepository + 对应测试；**未触碰** reader/pager、ReaderReplacementCapability、ReaderRulesSheet、ReaderPagerEngineState、Room schema / 历史书籍迁移删除、desktop、`docs/handoff/current.md`、他人的未提交文件与诊断工件；未 reset/checkout/format/commit/push/stage。

## 背景与目标

`ReaderDocumentLoader` 按 `books.format` 选择 TXT/EPUB 解析路径，而 ShelfImporter 只按扩展名归类、SyncRepository 下载后直接写 `content.${book.format}`。这会让「扩展名或同步 payload 声称 epub、实际为普通文本」的文件进入 EPUB 解析，或被错误持久化为 `content.epub`。本轮引入一份导入与同步共用的「格式真源」分类器，把「声明格式 vs 实际内容」的校验收敛到同一套规则。

## 核心规则（导入与同步共用，不分叉）

- 只依据三样输入判定，绝不为了识别格式读完整文件：声明格式、文件扩展名、正文前 4 字节。
- 仅 ZIP 本地文件头 `PK\x03\x04` 视为 EPUB；`markdown` 归一为 `md`（大小写不敏感）。
- 声称 epub 但正文不是 ZIP → 拒绝；缺失声明格式时不盲猜 epub，先按扩展名兜底，仍无结果则拒绝。
- 不自动迁移 / 删除 / 重写历史书籍，只保证本次新导入 / 下载不再制造格式错配。

## 改动路径

| 文件 | 说明 |
|---|---|
| `feature/library/FormatClassifier.kt`（新增） | 纯 JVM 分类器：`classify(claimedFormat?, fileName?, firstBytes) → Accepted(format)` 或 `Rejected(reason, claimedFormat?)`；`isEpubZip` 只认 `PK\x03\x04`；`markdown`→`md`。 |
| `feature/library/ShelfImporter.kt` | `importOne` 改为先读前 4 字节做分类：`Accepted` 才进入 epub/txt/md 导入；`Rejected(claimedFormat==null)` 沿用「不支持的格式」跳过；`Rejected(claimedFormat=="epub")` 归为失败，绝不进入 EPUB 解析。删除被分类器取代的 `SUPPORTED_FORMATS` 与内联 markdown 归一。 |
| `data/repository/SyncRepository.kt` | 抽取私有 `writeValidatedBookFile`，两条下载路径统一调用：按 `FormatClassifier` 判定真实格式，写 `content.{真实格式}`；声称 epub 但正文非 ZIP 或无法识别时，先于任何临时文件创建前抛错，不残留错误扩展名最终文件。 |

测试：

| 文件 | 说明 |
|---|---|
| `feature/library/FormatClassifierTest.kt`（新增，13 测试） | `.epub` 名但前四字节普通文本拒绝、声明 epub 且正文 ZIP 接受、ZIP 魔数与 epub 扩展名接受、`PK` 开头但非 `PK\x03\x04` 拒绝、缺失格式不盲猜 epub、缺失格式 + epub 扩展名 + 普通文本拒绝、txt/md/markdown 正常归类、markdown 声明归一、大小写归一、不支持扩展名拒绝、短于 4 字节不能当 epub。 |
| `data/repository/SyncRepositoryTest.kt`（+2 测试） | 新增「同步 payload 声称 epub 且正文 ZIP → 写 content.epub」「同步 payload 声称 epub 但正文普通文本 → 失败且无 content.epub / .tmp 残留」。 |
| `ui/viewmodel/ShelfImporterTest.kt`（+1 测试 + 两处既有 epub 用例补 openInputStream 桩） | 新增「`.epub` 文件名但正文普通文本 → 拒绝且不调用 openEpub」。两处既有 epub 用例补 `PK\x03\x04` 流桩，使旧用例在新的格式校验下继续成立。 |

> 说明：`ShelfImporterTest.kt` 物理路径在 `ui/viewmodel/`（不在字面 `feature/library/` 测试目录），但它是 `feature/library/ShelfImporter` 的唯一既有测试，为保持既有用例编译通过并补充导入侧格式覆盖而做最小修改，特此标注供集成者核对。

## 必须完成项对照

1. **新增可纯 JVM 测试的分类器，只吃声明格式/扩展名/前 4 字节**：完成。`FormatClassifier` 无 Android 依赖，`classify` 只接收声明格式、文件名与 `ByteArray`（前 4 字节）。
2. **仅 `PK\x03\x04` 视为 EPUB；markdown 归一 md**：完成。`isEpubZip` 精确比较 4 字节；`markdown`/`Markdown`→`md`。
3. **非 ZIP 的 `.epub` 或 payload `format=epub` 不产生 / 保留 `content.epub`、不进 EPUB 解析**：完成。导入侧在 openEpub 前拒绝；同步侧在写临时文件前抛错，测试断言无 `content.epub` 与 `.tmp` 残留。
4. **导入与同步下载复用同一分类规则；downloadBookContent 与批量路径不分叉**：完成。两处下载均调 `writeValidatedBookFile` → 同一 `FormatClassifier`。
5. **ZIP 正文以实际 EPUB 落库；TXT/MD 按声明/扩展名处理；缺失 format 不盲猜 epub**：完成。`Accepted` 返回规范化后的 `epub`/`txt`/`md`，落盘名使用该值。
6. **不自动迁移/删除/重写历史书籍**：完成。仅改动新导入与下载路径，不触碰历史数据。
7. **覆盖 5 类场景**：见测试文件（`FormatClassifierTest` + `SyncRepositoryTest` + `ShelfImporterTest` 三处覆盖）。
8. **只跑定向 JVM 测试、`:app:compileDebugKotlin`、`git diff --check`**：完成，结果见下。
9. **报告不记录真实书名/正文内容**：完成（测试用中性书名与占位文本）。

## 验证记录（真实命令与结果）

工作目录 `android/`：

| 命令 | 退出码 | 结果 |
|---|---|---|
| `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --console=plain` | 0 | BUILD SUCCESSFUL（`compileDebugKotlin`、`compileDebugUnitTestKotlin` 均通过） |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.library.FormatClassifierTest" --tests "com.creationreadingassistant.data.repository.SyncRepositoryTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" --console=plain` | 0 | BUILD SUCCESSFUL，**47 tests, 0 failures, 0 errors, 0 skipped** |
| `git diff --check`（仓库根） | 0 | 无空白错误（仅有既有文件的 CRLF→LF 提示性 warning，非错误） |

测试数明细（来自 `app/build/test-results/testDebugUnitTest/*.xml`）：

- `FormatClassifierTest`：`tests="13" failures="0" errors="0" skipped="0"`
- `SyncRepositoryTest`：`tests="18" failures="0" errors="0" skipped="0"`（16 既有 + 2 新增）
- `ShelfImporterTest`：`tests="16" failures="0" errors="0" skipped="0"`（15 既有 + 1 新增）

## 未覆盖 / 风险

- **未真机验收**：按任务要求留待两个并行修补冻结后统一安排。同步下载格式错配后的 UI 提示、`content_status=failed` 落库后的界面表现需真机确认。
- **批量下载错配分支未单独断言**：`downloadPendingBooks` 的格式错配走 `writeValidatedBookFile` 抛错 → `runCatching` 记 `SyncFailedEntry` + 标记 failed，与 `downloadBookContent` 复用同一私有函数，逻辑不分叉；该分支由 `downloadBookContent` 的错配测试 + 编译覆盖，未额外加一条批量下载专用测试。
- **`applyBook` 对缺失 format 仍默认 `"txt"`**：属于既有行为，本轮未改动（不在本次文件所有权与目标范围内）。分类器对「缺失 format」不盲猜 epub 的能力已由 `FormatClassifierTest` 覆盖；是否需要把 applyBook 的缺失 format 由默认 `"txt"` 改为空值，取决于 Codex 侧规则页降级说明的结论，届时再统一处理。
- **`readMagic` 无法打开流时按非 ZIP 处理**：真实 SAF 场景 `openInputStream` 极少失败；即便失败，对 epub 声明会拒绝，且其后 `importEpub` 也无法解析，安全同位。
- **导入侧不创建 `content.*`**：EPUB 导入走 `EpubRepository` 自身缓存，TXT/MD 导入写入自有副本，导入侧本就不会产生 `content.epub`；「不得创建/保留 content.epub」主要约束同步下载路径，已在同步侧测试验证。

---

# R1-S2.1 增补：格式真源闭环更正

- 状态：**Dev complete（仅定向 JVM 测试通过；未真机验收、未最终验收）**。本增补只做「真实 ZIP 字节优先」与「同步下载后的元数据闭环」两处更正，不宣称最终验收。
- 变更边界：仅改动并拥有下述 4 个文件，未触碰 Room schema / 历史数据迁移、`docs/handoff/current.md`、desktop、`archives/**`、他人未提交改动；未 stage / commit / push / 真机测试。

## 目标与实现要点

**目标 1 —— 真实 ZIP 字节优先（`FormatClassifier`）**

`classify` 现在先判 `isEpubZip(firstBytes)`：只要前 4 字节是 `PK\x03\x04`，无论声明 format 或文件名扩展名声称 txt/md，一律返回 `Accepted("epub")`；否则走原「声明 → 扩展名兜底 → 缺失拒绝」逻辑，其中声称 epub 但正文非 ZIP 依旧拒绝。

**目标 2 —— 同步下载后的元数据闭环（`SyncRepository`）**

新增 `persistDownloadedContent(book, written, now)`，单本 `downloadBookContent` 与 pending 批量 `downloadPendingBooks` 在 `writeValidatedBookFile` 原子落盘成功后统一调用，闭环回写：

- `BookEntity`：`format` / `size` / `local_uri`（`Uri.fromFile(file).toString()`）/ `local_content_path`（绝对路径）/ `content_status="available"` / `updated_at`；且 `payload` 经 `withPayloadFormat` 只改 `format` 字段、保留其余未知字段。
- `BookFileEntity`：`format` / `file_name` / `local_uri` / `size` / `updated_at` 与实际写入文件一致。

因此 `ReaderDocumentLoader` 读 `BookEntity.format` 时，真实 EPUB 必然按 EPUB 打开，不会停留在「仅 BookFileEntity 是 epub、BookEntity 仍是 txt」的错配。

> 测试 seam：新增 `internal var fileUri: (File) -> String = { file -> Uri.fromFile(file).toString() }`。生产默认即 `Uri.fromFile(...).toString()`；JVM 单测里 `Uri.fromFile` 恒返回 null（`unitTests.isReturnDefaultValues=true`），测试注入 `"file://" + absolutePath` 做等价替换，与既有 `ReaderDocumentLoader.uriParser` seam 同一手法。

## 改动文件

| 文件 | 说明 |
|---|---|
| `feature/library/FormatClassifier.kt` | `classify` 增加 ZIP 字节优先分支（`isEpubZip` 先判）。 |
| `data/repository/SyncRepository.kt` | 新增 `persistDownloadedContent`（单本/批量共用）、`withPayloadFormat`、`fileUri` 测试 seam；两条下载路径统一在落盘成功后调用。 |
| `feature/library/FormatClassifierTest.kt` | 新增 ZIP 字节优先用例：声明 txt / md 扩展名 + ZIP 字节 → epub；缺失格式与文件名 + ZIP 字节 → epub 等（由 13 增至 16 测试）。 |
| `data/repository/SyncRepositoryTest.kt` | 新增闭环用例：声明 txt + ZIP 正文 → BookFile 与 BookEntity 均 epub、路径可用、payload format=epub 且保留其他字段；pending 批量 ZIP→epub 走同一落盘逻辑；注入 `fileUri` seam；修正 `local_uri` 断言为 `file://...`（由 18 增至 20 测试）。 |

## 验证记录（真实命令与结果）

工作目录 `android/`：

| 命令 | 退出码 | 结果 |
|---|---|---|
| `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.library.FormatClassifierTest" --tests "com.creationreadingassistant.data.repository.SyncRepositoryTest"` | 0 | BUILD SUCCESSFUL，**36 tests, 0 failures, 0 errors, 0 skipped** |
| `git diff --check`（仓库根） | 0 | 无空白错误（仅有既有文件的 CRLF→LF 提示性 warning） |

测试数明细（来自 `app/build/test-results/testDebugUnitTest/*.xml`）：

- `FormatClassifierTest`：`tests="16" failures="0" errors="0" skipped="0"`
- `SyncRepositoryTest`：`tests="20" failures="0" errors="0" skipped="0"`

## 未覆盖 / 风险

- **未真机验收、未最终验收**：按任务要求，真机验收留待两个并行修补冻结后统一安排。
- **真实 `Uri.fromFile` 的路径编码未在 JVM 验证**：`fileUri` seam 在测试中用 `"file://" + absolutePath` 等价替换，真实 Uri 编码行为仅在设备运行时生效（与既有 `ReaderDocumentLoader.uriParser` 一致）。
- **批量下载「普通文本声明 epub → 拒绝」未单独加批量专用测试**：该分支与单本复用同一 `writeValidatedBookFile`（抛错于临时文件创建前），由单本拒绝测试 + 编译覆盖；批量 ZIP→epub 已有专用测试。
- **`applyBook` 对缺失 format 仍默认 `"txt"`**：既有行为，本轮未改动，不在本次文件所有权与目标范围内。