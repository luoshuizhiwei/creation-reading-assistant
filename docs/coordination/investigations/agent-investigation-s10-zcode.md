# S10 只读调查：目录卷标题与自定义页眉页脚

- **执行**：Zcode（窗口 B）
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **固定比较基点**：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
- **任务性质**：只读调查，仅新增本报告
- **未运行**：Gradle、ADB、任何设备/安装操作
- **未修改**：除本报告外零文件

---

## 1. 执行摘要

**结论先行：S10 不是"已实现"或"未实现"单一状态，A、B 两条能力的状态截然不同。**

**S10-A（目录卷标题判定）：修复已落地且规则收紧到位，但只覆盖"目录列表"一个消费端，其余 8 个消费端零处理。**

- 旧的子串判据 `contains("卷") || contains("部")` 已换成整行正则 `VOLUME_HEADER`（[ReaderTocModels.kt:87](../../../android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderTocModels.kt:87)），并配 `VolumeHeaderTest` 的 10 条假阳性反例。**这一层是当前可达缺陷的真实修复，已生效。**
- 但 `isVolumeHeader` 全仓**只有两个调用点**：`readerTocEntries`（目录列表）和 `groupChaptersByVolume`（后者已无生产调用方）。卷节点在**点击跳转、当前章节标题、`{ChapterTitle}`、搜索结果、阅读进度、TTS 跨章、已读状态、书签回源**八个位置**没有任何特殊处理**。
- 关键后果：卷标题**仍作为一个真实章节存在于 `chapters` / `titles` 列表里**，因此它**可被点击、会参与章序号、会占用章计数**。目录列表里它被"吞掉"变成分组名，但同一个索引在别处仍是普通章节 —— 这是**同一份数据在两个消费端的语义分叉**，不是"已实现"。

**S10-B（自定义页眉页脚）：数据层与纯函数层基本完整，UI 层存在两个并存的实现，其中一个显示的文案与另一个相反。**

- 设置模型、六个槽位、10 个占位符、`CustomTipDialog`、`renderSample` 预览、`describeTemplate` 中文回显 —— **数据链路完整**。
- 但 `HeaderFooterPicker` 有**两份独立实现**：`ui/components/CustomTipDialog.kt:220`（折叠行显示 `describeTemplate` 中文）与 `ui/screen/reader/sheets/ReaderSettingsCommon.kt:783`（折叠行显示**原始 token**）。书内设置页（`ReaderDisplaySettings.kt:103-131`）用的是后者 → **同一个模板，在"我的→阅读设置"显示"章节名"，在书内设置显示"{ChapterTitle}"**。
- **六个槽位全部只写全局**：`customTipHeaderLeft/Center/Right`、`customTipFooterLeft/Center/Right` 不在 `ReaderOverrideKey` 里，书内设置即使选了"只改本书"也写全局。这与 D-2-B 刚修的文案一致（文案明确说这六项是全局），**不是缺陷，是已声明的契约**；但用户改"页眉中间"会静默影响所有书。
- **分页专属**：`PagedReaderHeader` / `PagedReaderFooter` 的唯一调用方是 `PagedTxtReaderHost`。滚动 TXT / 滚动 Markdown / legacy 分页 EPUB **完全不消费**。S09 已记录，本轮复核成立。

**计数：P0×0、P1×1、P2×6、P3×11、readiness blocker×3。**

---

## 2. 调查范围与明确未执行事项

### 已执行（只读）

- 逐文件读取 S10-A / S10-B 的全部生产实现、设置存储、UI、消费端与测试。
- 对 `origin/main`（`5aed5939`）做路径级 `git diff`，区分"基线已有"与"工作树新增"。
- 全仓 grep 定位调用方 / 消费端 / 死代码 / 测试覆盖。
- 阅读 S09 调查报告与协调看板，确认边界。

### 明确未执行

- 未运行 Gradle、未编译、未运行任何测试。所有测试结论只写"静态存在/未运行"，**不写 PASS**。
- 未运行 ADB、未连接设备、未截图。
- 未修改产品源码、测试源码、Gradle、数据库、设置、脚本、`.gitignore`、协调看板、handoff、Obsidian、TODO。
- 未触碰 `D:\develop\Code\Codex\creation-reading-assistant`。
- 未删除 S19 发现的临时 UI dump。
- 未修改 `ReaderSheetHost.kt`、`ReaderBookmarkPositionPolicyTest.kt`（窗口 C 所有权）。
- 未修改 `ReaderSettingsSheet.kt`、D-2-B 测试或真机证据（窗口 D 边界）。
- 未采用 S09 的实现建议（本轮只核实事实）。

### 基线判定

S10-B 的绝大多数文件相对 `5aed5939` 是**未跟踪新文件**或**大幅未提交修改**：

| 文件 | 相对基点状态 |
|---|---|
| `feature/reader/pager/CustomTipPlaceholder.kt` | **不存在于基点**（全新，未跟踪） |
| `ui/components/CustomTipDialog.kt` | **不存在于基点**（全新，未跟踪） |
| `feature/reader/pager/PagedReaderHeaderFooter.kt` | 已修改（+340 行级） |
| `data/settings/SettingsStore.kt` | 已修改（+223 行级） |
| `ui/screen/reader/sheets/ReaderDisplaySettings.kt` | 已修改 |
| `ui/screen/reader/sheets/ReaderTocModels.kt` | 已修改（+29 行） |
| `ui/screen/profile/ReaderSubPage.kt` | 已修改（+1083 行级） |

⇒ **本报告描述的 S10-B 行为属于工作树现状，不是已发布基线。** 所有"当前可达"判定均指工作树，非 `origin/main`。

---

## 3. S10-A / S10-B 拆分结论

| 维度 | S10-A 目录卷标题 | S10-B 自定义页眉页脚 |
|---|---|---|
| 判定函数是否存在 | ✅ `isVolumeHeader` | ✅ `CustomTipPlaceholder.render` |
| 是否已接线 | ✅ 仅目录列表 | ✅ 仅分页引擎 |
| 数据模型 | ❌ 无"卷"类型，纯标题正则推断 | ✅ 10 个枚举 token + 自由模板 |
| 消费端数量 | 1 / 9 | 1 / 5（格式×模式） |
| 设置可配置 | 无设置项 | ✅ 六槽位 + 电量样式 |
| 本书覆盖 | 不适用 | ❌ 六槽位不可覆盖（已声明） |
| 测试 | 5 个纯函数用例（假阳性充分、假阴性部分） | 13 个纯函数用例；**UI/渲染层零覆盖** |
| 净结论 | **修复已落地，但只修了 1/9 个面** | **数据层完成，UI 层存在实现分叉** |

---

## 4. 目录卷标题完整规则矩阵

### 4.1 "卷标题"的准确定义

**当前产品里，卷标题的唯一判定依据是"标题字符串整行匹配正则"，没有任何数据模型字段。**

- 不是目录分组节点（EPUB 的嵌套 navPoint 在解析时被**展平**，见 §5.3）。
- 不是"不可阅读章节"（所有章节都从同一份 `chapters` 列表来，没有可读性标记）。
- 不是"空正文条目"（`DocChapter` 没有 `isEmpty` 类字段）。
- **是**：`isVolumeHeader(title: String): Boolean`，纯标题正则。

### 4.2 数据模型字段

`DocChapter`（[ReaderDocument.kt:55](../../../android/app/src/main/java/com/creationreadingassistant/feature/reader/doc/ReaderDocument.kt:55) 声明）只有：`index / title / startOffset / charCount / charCountIsEstimated`。

| 概念 | 是否有字段 | 实际表达方式 |
|---|---|---|
| 普通章节 | 是 | 一个 `DocChapter` |
| 卷 | **否** | 仅标题正则；无类型、无层级、无 `isVolume` |
| 分组 | **否** | 由 `readerTocEntries` 在渲染期临时归组 |
| 不可点击条目 | **否** | 目录里卷标题不产出 `ReaderTocEntry`（`mapIndexedNotNull` 返回 null），但在 `chapters` 里仍是真章节 |
| 空正文条目 | **否** | 无此概念 |

⇒ **卷标题不是数据模型的一部分，只是渲染期的一个字符串判据。**

### 4.3 规则矩阵（`VOLUME_HEADER`，[ReaderTocModels.kt:84-108](../../../android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderTocModels.kt:84)）

判定前置：`trim()` 后非空且长度 ≤ 24。

| # | 形态 | 输入示例 | 判定 | 依据 |
|---|---|---|---|---|
| 1 | 「第X卷」 | `第一卷` `第1卷` `第壹卷` | ✅ 卷 | `第$VOL_NUM{1,6}[卷部]` |
| 2 | 「第X部」 | `第二部` | ✅ 卷 | 同上 |
| 3 | 「卷一」 | `卷一` `卷之三` `卷十二` | ✅ 卷 | `卷之?$VOL_NUM{1,6}` |
| 4 | 「上卷/下卷」 | `上卷` `中卷` `下册` `中部` | ✅ 卷 | `[上中下][卷部]` |
| 5 | 「正文卷」 | — | ❌ **不判为卷** | 无此形态；`正文` 前缀只在 `TxtChapterDetector` 里 |
| 6 | 「第一部/篇/辑/册」 | `第一部` ✅；`第一篇` ❌；`第一辑` ❌；`第一册` ❌ | **部分** | 正则只含 `[卷部]`，不含 `篇/辑/册` |
| 7 | 标题含「卷」但是普通章节 | `019 系统的部分规则` `举报试卷怎么样呢` | ✅ 正确不判 | 整行匹配，非子串 |
| 8 | 有数字但无「卷」 | `019 系统的部分规则` | ✅ 正确不判 | 需前缀 `第…[卷部]` 等 |
| 9 | 空标题 | `""` `"   "` | ✅ 正确不判 | `t.isEmpty()` 前置 |
| 10 | 前后空白 | `　　第一卷` | ✅ 判为卷 | `trim()` |
| 11 | 全角/半角数字 | `第１卷`（全角） | ✅ 判为卷 | `VOL_NUM` 含 `０-９` |
| 12 | 中文数字 | `第一卷` | ✅ 判为卷 | `VOL_NUM` 含汉字 |
| 13 | 阿拉伯数字 | `第1卷` | ✅ 判为卷 | `VOL_NUM` 含 `0-9` |
| 14 | 罗马数字 | `Volume IV` ✅；`第IV卷` ❌ | **部分** | 仅 `vol|volume|part` 分支含 `[0-9ivxlcdm]` |
| 15 | 带副标题 | `第一卷 风起云涌` `第1卷：少年篇` | ✅ 判为卷 | `[ :：，,、—·-].{0,10}` |
| 16 | EPUB 子节点有链接 | — | ❌ 无处理（解析期已展平，见 §5.3） | — |
| 17 | 有链接但正文为空 | — | ❌ 无处理 | — |
| 18 | 无链接但有子节点 | — | ❌ 无处理 | — |
| 19 | 长度 > 24 | 长标题 | ✅ 正确不判 | `VOLUME_HEADER_MAX_LENGTH` |
| 20 | 副标题 > 10 字 | `第一卷 这是一个很长的副标题` | ❌ **不判为卷** | `.{0,10}` 上限 |

### 4.4 与 `TxtChapterDetector` 的关系（关键分叉）

两个模块**各有一套互不相同**的"卷"概念，且**不共享函数**：

| | `ReaderTocModels.isVolumeHeader` | `TxtChapterDetector` |
|---|---|---|
| 用途 | 目录列表归组 | TXT 章节识别（决定"是不是一章"） |
| 是否认识「卷一」 | ✅ | ✅（`[卷部篇册]$WS*之?$NUM`） |
| 是否认识「第一篇」 | ❌ | ✅（含 `篇`） |
| 是否认识「第一册」 | ❌ | ✅（含 `册`） |
| 是否认识「序章/楔子/番外」 | ❌ | ✅ |
| 是否认识 `Part` | ✅（`vol|volume|part`） | ❌（`en-extended` 是可选规则） |
| 长度上限 | 24 | 40 |
| 负向断言 | 无（整行匹配天然安全） | 有（`部(?!分赛游队)` 等） |

⇒ **同一个标题「第一篇 风起」，`TxtChapterDetector` 判为章节（进 `chapters`），`isVolumeHeader` 判为普通章节（不进分组）** —— 目录里它显示为一个可点击章节，而非分组名。**这是当前真实可达的行为不一致（P2-B1）。**

---

## 5. 四种格式的目录生成与消费链

### 5.1 TXT

- **生成**：`TxtChapterDetector.detect`（[TxtChapterDetector.kt:150-238](../../../android/app/src/main/java/com/creationreadingassistant/feature/reader/doc/TxtChapterDetector.kt:150)）。流式大文件走 `TxtFileScanner` → `TxtFileIndex`；小文件走 `detect` 全量。
- **规则来源**：`builtin`（标准四类 + 具名专名 + 平台章型）+ 逐书绑定的 `num-dot / num-bare / cn-num-dot / bracketed / en-extended / md-heading`（[BuiltinTocRules.kt:16-24](../../../android/app/src/main/java/com/creationreadingassistant/feature/reader/rules/BuiltinTocRules.kt:16)）。
- **卷的处理**：`TxtChapterDetector` 把「第X卷」「卷一」「上卷」识别为**普通章节**（它们进 `PATTERNS`，产出 `Chapter`）。**检测器没有"这是卷"的概念。** 卷与章节的分野只发生在目录渲染期。

### 5.2 Markdown

- **生成**：`MarkdownDocument.buildChaptersFromText`（[MarkdownDocument.kt:92](../../../android/app/src/main/java/com/creationreadingassistant/feature/reader/doc/MarkdownDocument.kt:92)），按 **H1 切章**，H2-H6 不拆章。
- **卷的处理**：**零特殊处理。** 一个 `# 第一卷` 的 H1 会成为一个普通章节，标题为「第一卷」；随后在目录渲染期被 `isVolumeHeader` 吞掉变成分组名。
- **副作用**：H1 本身也进入 `chapters`，因此它**占一个章序号**，且 `{ChapterTitle}` 在某页可能显示「第一卷」。

### 5.3 分页 EPUB

- **生成**：`EpubParser.resolveToc` → NCX（`parseNcx`，[EpubParser.kt:673](../../../android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubParser.kt:673)）或 EPUB3 Nav（`parseNavDocument`，:723）→ `buildChaptersFromToc`。
- **关键事实：嵌套 TOC 被展平。** `parseNcx` 只维护**单个** `inNavPoint` 标志；`<navPoint>` 内部的子 `<navPoint>` 会**重置** `currentLabel`/`currentSrc`，父节点的 label 在子节点开始时就丢失。⇒ **EPUB 原始 TOC 的层级结构在解析后完全不存在**，所有条目（含父级分组节点）成为平铺列表。
- `parseNavDocument` 用正则抓取 `<a href=...>`，**不解析 `<ol>/<li>` 嵌套**，同样是平铺。
- **卷的处理**：`buildChaptersFromToc` 为**每个 TOC 条目**（只要有可读 document 且 href 能匹配 spine）产出一个 `EpubChapter`。因此 EPUB 里的卷标题**成为真实章节**，其 `entryPath` 指向卷的首页（通常只有标题）。⇒ 在分页引擎里翻到该章，正文几乎为空。
- **"有链接但正文为空"**：`estimatedTextLength` 来自 ZIP 条目字节数，不判空。

### 5.4 legacy EPUB

- **生成**：与 §5.3 完全相同（同一 `EpubBook.chapters`）。
- **差异**：legacy 分页视图 `PagedEpubView`（[PagedEpubContent.kt:87](../../../android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt:87)）与滚动 `LazyColumn` 消费同一份章节列表。
- **卷的处理**：与 §5.3 相同，零特殊处理。

### 5.5 消费链汇总

```
[TXT]   TxtChapterDetector ─┐
[MD]    MarkdownDocument ───┼→ DocChapter / title 列表
[EPUB]  EpubParser (展平) ───┘        │
                                      ├→ readerTocState → readerTocEntries → isVolumeHeader → 目录分组
                                      ├→ PagedChapterSource.chapterTitle() → {ChapterTitle} / 顶栏
                                      ├→ chapterIndexFor() → {ChapterIndex}
                                      ├→ goToChapter() → 点击跳转
                                      ├→ ReaderChapterLoaderCoordinator → markRead / 进度
                                      ├→ computeEpubSearch → chTitle
                                      └→ TTS / 书签 / 高亮回源
```

**`isVolumeHeader` 只挂在第一条分支上。**

### 5.6 格式 × 数据来源 × 判定 owner × 可阅读 × 可跳转 × 测试

| 格式 | 数据来源 | 目录判定 owner | 卷识别 owner | 卷节点可阅读 | 卷节点可跳转 | 卷节点占章序号 | 测试 |
|---|---|---|---|---|---|---|---|
| TXT 小文件 | `TxtChapterDetector.detect` | `isVolumeHeader` | `isVolumeHeader` | ✅ 是（在 `chapters` 里） | ✅ 是（`onPickChapter`） | ✅ 是 | `VolumeHeaderTest`（纯函数） |
| TXT 流式 | `TxtFileScanner` + `detect` | 同上 | 同上 | ✅ | ✅ | ✅ | 同上 |
| Markdown | `buildChaptersFromText`（H1） | 同上 | 同上 | ✅ | ✅ | ✅ | 同上 |
| 分页 EPUB | NCX / Nav（**已展平**） | 同上 | 同上 | ✅ | ✅ | ✅ | 同上 |
| legacy EPUB | 同一份 | 同上 | 同上 | ✅ | ✅ | ✅ | 同上 |

⇒ **`isVolumeHeader` 是全格式共享的唯一判定函数**（第 4 问的答案：共享，非各自实现）。**但"卷"与"章节"的分野只在目录列表里成立。**

---

## 6. 卷标题假阳性 / 假阴性风险

### 6.1 假阳性（普通章节被误判为卷）

**已修复。** `VolumeHeaderTest` 的 10 条反例（[VolumeHeaderTest.kt:20-31](../../../android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/sheets/VolumeHeaderTest.kt:20)）覆盖了旧子串判据的典型误判。整行匹配 + 24 字上限 + 显式前缀集合后，假阳性面很小。

**残留假阳性（推断，低风险）**：`第一部 阿Q正传` 这类"部"确实是卷；但**「上部」「下部」作为普通章节标题**（如正文里的小节名）会被判为卷。属于可接受的取舍。

### 6.2 假阴性（真卷标题被当普通章节）

**存在，且不止一处（P2-B1）：**

| 漏判形态 | 原因 | 后果 |
|---|---|---|
| 「第一篇」「第一辑」「第一册」 | 正则只含 `[卷部]` | 目录里显示为可点击章节，而非分组名 |
| 「第IV卷」「第Ⅳ卷」 | 罗马数字只在 `vol|volume|part` 分支 | 同上 |
| 副标题 > 10 字 | `.{0,10}` 上限 | 同上 |
| 「序章/楔子/番外/终章」 | 完全不在 `VOLUME_HEADER` 里 | 同上（这些是 `TxtChapterDetector` 认识的章节形态） |
| 卷标题带书名号 | `《第一卷》` | 无 `OPEN` 前缀支持（`TxtChapterDetector` 有） |

**假阴性的实际后果比假阳性轻**（章节没被吞掉，仍可点击阅读），但**目录里卷的视觉分组缺失**，且**同一本小说里「第一卷」被分组、「第一篇」不被分组**，观感不一致。

### 6.3 跨模块不一致（P2-B1 的核心）

`TxtChapterDetector` 与 `isVolumeHeader` 对**同一标题**给出**不同判定**，且两者都是当前生效的生产逻辑。这不是"假阳性/假阴性"，而是**两套真源**。

---

## 7. 页眉页脚完整字段矩阵

### 7.1 设置字段

| 字段 | 默认值 | 存储键 | 作用域 | 设置 UI（我的） | 设置 UI（书内） | 消费端 | 测试 |
|---|---|---|---|---|---|---|---|
| `showReaderInfo` | `true` | `reader_show_info` | **全局 + 本书覆盖**（`SHOW_READER_INFO`） | ✅ `ReaderSubPage.kt:529` | ✅ `ReaderDisplaySettings.kt:88` | `PagedReaderHeader/Footer` | `ReaderSettingsSheetTest`（可见性）；无覆盖测试 |
| `headerLeft` | `BOOK_NAME` | `reader_header_left` | 全局 + 本书覆盖（`HEADER_LEFT`） | ❌ **UI 已不再使用** | ❌ 已不再使用 | `fromHeaderFooterItem` 兜底 | `SettingsStoreTest:87` |
| `headerRight` | `NONE` | `reader_header_right` | 同上 | ❌ | ❌ | 同上 | `SettingsStoreTest` |
| `footerLeft` | `CHAPTER_TITLE` | `reader_footer_left` | 同上 | ❌ | ❌ | 同上 | `SettingsStoreTest` |
| `footerRight` | `PROGRESS` | `reader_footer_right` | 同上 | ❌ | ❌ | 同上 | `SettingsStoreTest:88` |
| `customTipHeaderLeft` | `""` | `reader_custom_tip_header_left` | **仅全局** | ✅ `ReaderSubPage.kt:550` | ✅ `ReaderDisplaySettings.kt:103` | `PagedReaderHeader` | ❌ 无 |
| `customTipHeaderCenter` | `""` | `reader_custom_tip_header_center` | **仅全局** | ✅ :558 | ✅ :108 | 同上 | ❌ 无 |
| `customTipHeaderRight` | `""` | `reader_custom_tip_header_right` | **仅全局** | ✅ :566 | ✅ :113 | 同上 | ❌ 无 |
| `customTipFooterLeft` | `""` | `reader_custom_tip_footer_left` | **仅全局** | ✅ :574 | ✅ :118 | `PagedReaderFooter` | ❌ 无 |
| `customTipFooterCenter` | `""` | `reader_custom_tip_footer_center` | **仅全局** | ✅ :582 | ✅ :123 | 同上 | ❌ 无 |
| `customTipFooterRight` | `""` | `reader_custom_tip_footer_right` | **仅全局** | ✅ :590 | ✅ :128 | 同上 | ❌ 无 |
| `batteryTipStyle` | `ICON_ONLY` | `reader_battery_tip_style` | **仅全局** | ✅ :540 | ✅ :93 | `BatteryTip` | ❌ **零测试** |

### 7.2 关键发现：四个 legacy 枚举字段已成僵尸

`headerLeft/Right`、`footerLeft/Right`（`HeaderFooterItem`）**在 UI 层已无任何写入入口**（`ReaderSubPage` / `ReaderDisplaySettings` 都改成了写 `customTip*`）。它们仍：

1. 参与 `ReaderOverrideKey`（`HEADER_LEFT` 等四个键），可被本书覆盖；
2. 参与 `ReaderSettings` 相等性判断（影响 `ReaderSettingsRouter.changedOverridableKeys`）；
3. 通过 `effectiveHeaderLeft() = customTipHeaderLeft.ifBlank { headerItemToTemplate(headerLeft) }` 作为**兜底默认值**生效。

⇒ 用户**看不到、改不了**这四个字段，但它们的默认值（`BOOK_NAME` / `NONE` / `CHAPTER_TITLE` / `PROGRESS`）**决定了六个槽位在"未自定义"时的初始显示**。这是一个**隐藏的、不可见的默认值来源**（P3，非缺陷但违反"设置项应可见"）。

### 7.3 `HeaderFooterPicker` 实现分叉（P2-B2）

| | `ui/components/CustomTipDialog.kt:220` | `ui/screen/reader/sheets/ReaderSettingsCommon.kt:783` |
|---|---|---|
| 调用方 | `ReaderSubPage.kt`（我的→阅读设置） | `ReaderDisplaySettings.kt`（书内设置） |
| 折叠行文案 | `describeTemplate(template).ifBlank { "无" }` → **中文**（「章节名 · 当前时间」） | `template.ifBlank { "无" }` → **原始 token**（`{ChapterTitle} · {Time}`） |
| 注释 | 明确写了"折叠行给人看的是中文…token 只在点开后的输入框里出现"（:252-253） | 无 |

⇒ **同一份用户设置，在两个入口显示不同内容。** `ReaderSettingsCommon.kt:814` 的行为与 `CustomTipDialog.kt:252-254` 注释声明的设计意图直接矛盾。**这是实现分叉，不是设计选择**（后者注释把意图写得很清楚，前者只是没跟上）。

### 7.4 两个 legacy 重载是死代码（P3）

`HeaderFooterPicker(label, selected: HeaderFooterItem, onSelect)` 在 `CustomTipDialog.kt:287` 与 `ReaderSettingsCommon.kt:844` **各有一份**，**全仓零调用**（grep 确认所有调用点都传 `template =`）。它们连同 `fromHeaderFooterItem` 一起构成迁移残留。

---

## 8. 六槽位保存、回显与渲染链

| 环节 | 状态 | 证据 |
|---|---|---|
| 设置可写 | ✅ 六个 `SettingsStore` 键，读写对称（:511-516 / :660-665） | `SettingsStore.kt` |
| 六槽位 UI 可达 | ✅ 两个入口各 6 个 `HeaderFooterPicker` | `ReaderDisplaySettings.kt:103-131`、`ReaderSubPage.kt:550-592` |
| 保存 | ✅ `onChange(settings.copy(customTipXxx = it))` → `applyReaderEdit` | `ReaderSettingsEditApplier.kt:19` |
| 回显（编辑框内） | ✅ `initialTemplate = template`（`CustomTipDialog.kt:61`） | 光标置尾 `TextRange(len)` |
| 回显（折叠行） | ⚠️ **分叉**：书内显示原始 token，我的显示中文 | §7.3 |
| 渲染 | ✅ 左/中/右三槽 `TipSlot`，各占 `weight(1f)` | `PagedReaderHeaderFooter.kt:164-168` |
| 中间槽兜底 | ✅ 仅 `customTipHeaderCenter`（无 legacy 对应物） | `SettingsStore.kt:286` |
| 空模板 | ✅ `TipSlot` 提前 `return@Box` | `PagedReaderHeaderFooter.kt:191` |

**保存/回显/渲染三条链在数据层是通的。** 唯一断点是折叠行文案分叉。

**六槽位作用域**：全部**仅全局**。书内设置选"只改本书"时，改页眉槽位**仍写全局** —— 这与 D-2-B 的页级说明一致（[ReaderSettingsScopeNoticeTest.kt:114-116](../../../android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderSettingsScopeNoticeTest.kt:114) 明确钉住"页眉页脚"是分组名、其下六个槽位写全局）。**已声明契约，不计缺陷。**

---

## 9. 所有占位符逐项矩阵

`CustomTipPlaceholder`（[CustomTipPlaceholder.kt:12-26](../../../android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/CustomTipPlaceholder.kt:12)），10 个 token。

| token | 中文名 | 数据来源 | 单位/基数 | 起点 | null/未知输出 | 分页 | 滚动 | legacy EPUB | 预览一致 | 测试 |
|---|---|---|---|---|---|---|---|---|---|---|
| `{BookName}` | 书名 | `bookName` 参数 ← `s.bookTitle` | 文本 | — | 空串 | ✅ | ❌ | ❌ | ✅ `测试书籍` | ✅ |
| `{ChapterTitle}` | 章节名 | `source.chapterTitle(source.chapterIndexFor(absOffset))` | 文本 | — | `""`（`chapterCount==0`） | ✅ | ❌ | ❌ | ✅ `第3章 序幕` | ✅ |
| `{Time}` | 当前时间 | `PagedReaderClockEffects` → `HH:mm` | 分钟 | — | 恒有值 | ✅ | ❌ | ❌ | ✅ `12:30` | ❌ |
| `{BatteryPercent}` | 电量 | `getBatteryLevel` → 0..100 | % | — | `""`（`level < 0`） | ✅ | ❌ | ❌ | ✅ `85%` | ❌ |
| `{ChapterIndex}` | 本章序 | `chapterIndexFor(absOffset) + 1` | 章 | **1** | `"1"`（无 source） | ✅ | ❌ | ❌ | ✅ `3` | ❌ |
| `{ChapterSize}` | 总章数 | `source.chapterCount` | 章 | 1 | `"0"` | ✅ | ❌ | ❌ | ✅ `120` | ❌ |
| `{PageIndex}` | 当前页 | `pageInfo.pageIndex + 1` | 页 | **1** | `""`（`pageCount<=0`） | ✅ | ❌ | ❌ | ✅ `1` | ⚠️ 仅 `3/10` 合成值 |
| `{PageSize}` | 本章总页 | `pageInfo.pageCount` | 页 | 1 | `""` | ✅ | ❌ | ❌ | ✅ `18` | ⚠️ 同上 |
| `{PageRemaining}` | 本章余页 | `pageCount - (pageIndex+1)` | 页 | **0** | `"0"` | ✅ | ❌ | ❌ | ✅ `17` | ❌ |
| `{ReadProgress}` | 总进度% | `absOffset * 100 / source.totalChars` | % | 0 | `"0.0%"` | ✅ | ❌ | ❌ | ✅ `24.5%` | ⚠️ 间接 |

### 9.1 关键事实

1. **`{PageIndex}/{PageSize}` 的"本章"语义**：`PageInfo.pageCount` 来自 `controller.pageCount`，即**当前章排版后的页数**。跨章归零。S09 已记录，本轮复核成立。
2. **`{PageIndex}` 在无排版时输出空串**，但 `{PageRemaining}` 输出 `"0"`，`{ChapterSize}` 输出 `"0"` —— **三者对"未知"的表达不一致**（空 vs 0）。属 P3 一致性。
3. **`{ChapterSize}` 是"总章数"还是"本章数"？** 中文名写"总章数"，实现是 `source.chapterCount`（全书章节总数）。**名称与实现一致，但用户可能读成"本章有多少页"的配对项。** 需产品裁决（§18）。
4. **`renderSample` 与真实渲染的一致性**：
   - `{ChapterTitle}` 样例 `第3章 序幕` —— 真实值可能为空串（无 source / 空标题），**预览比真实更"好看"**。
   - `{BatteryPercent}` 样例 `85%` —— 真实值在电量不可用时是**空串**（`TipSlot` 还会走 `splitOnBattery` 的降级分支）。**预览不反映降级**。
   - `{PageIndex}/{PageSize}` 样例 `1/18` —— 真实值在未排版时是 `""` 或 `"/"`（`{PageIndex}/{PageSize}` 两段各自为空时渲染成 `/`）。**预览不反映**。
   - ⇒ **预览是"理想值"，不覆盖 null/未知分支**（P3，测试缺口）。
5. **`{PageRemaining}` 用 `coerceAtLeast(0)`**：末页输出 `0`，符合直觉。
6. **无 `{ChapterIndex}/{ChapterSize}` 之外的章节级进度 token**（如"本章百分比"）。

### 9.2 未在枚举中但代码存在的其他 token

**无。** `extractPlaceholders` 的正则 `\{([^{}]+)\}` 会抓出任何 `{Xxx}`，但 `render` 只替换上述 10 个。**未知 token 原样保留在输出里**（见 §12）。

---

## 10. 格式 × 阅读模式消费矩阵

| # | 格式 | 模式 | 渲染组件 | 页眉 | 页脚 | 电量 | 时间 |
|---|---|---|---|---|---|---|---|
| 1 | TXT | 分页引擎（`pagerEngineMode=on` + `readerMode=paged`） | `PagedReaderHost` | ✅ | ✅ | ✅ | ✅ |
| 2 | Markdown | 分页引擎 | `PagedReaderHost` | ✅ | ✅ | ✅ | ✅ |
| 3 | EPUB | 分页引擎（`epubPagerEngineMode=on/auto`） | `PagedReaderHost` | ✅ | ✅ | ✅ | ✅ |
| 4 | TXT | 滚动 | `ReaderContentHostPlainTextBranch` | ❌ | ❌ | ❌ | ❌ |
| 5 | Markdown | 滚动 | `ReaderContentHostMarkdownBranch` | ❌ | ❌ | ❌ | ❌ |
| 6 | EPUB | 滚动（`LazyColumn`） | `ReaderContentHostEpubBranch` | ❌ | ❌ | ❌ | ❌ |
| 7 | EPUB | legacy 分页（引擎 off） | `PagedEpubView` | ❌ | ❌ | ❌ | ❌ |

**第 4-7 行是 S09 已记录的 SN-3。** 本轮复核：`PagedReaderHeader` / `PagedReaderFooter` 的调用点全仓**只有** `PagedTxtReaderHost.kt:200` 与 `:347`（grep 确认）。`PagedReaderClockEffects` 同样只在此处。

⇒ **7 种组合中只有 3 种消费页眉页脚。** 用户在设置里配置六槽位、开启"安静阅读信息"，在**滚动模式下完全看不到任何变化**。

**这不是 bug（产品契约缺失）**：S09 已把它列为需产品裁决项（Q5/Q8）。本轮不重复计为 P1/P2。

### 10.1 "设置里可配置但某模式完全不消费"清单

| 设置项 | 配置入口 | 消费端 | 未消费模式 | 分类 |
|---|---|---|---|---|
| `showReaderInfo` | 两处 | 分页引擎 | 滚动 TXT/MD/EPUB、legacy 分页 EPUB | **产品契约缺失**（S09 Q8 待裁） |
| 六槽位模板 | 两处 | 分页引擎 | 同上 | **产品契约缺失** |
| `batteryTipStyle` | 两处 | 分页引擎 | 同上 | **产品契约缺失** |

**已确认事实**：设置项可写、可存、可恢复；渲染端只在分页引擎。
**合理推断**：滚动模式没有"页"的概念，`{PageIndex}` 本就无意义；但 `{ChapterTitle}` / `{Time}` / `{BatteryPercent}` 在滚动模式下**完全有意义**。
**需要用户裁决**：滚动模式是否补信息条（S09 Q8）。

---

## 11. 设置 UI、存储、覆盖路由与渲染端一致性

| 层 | 我的→阅读设置（`ReaderSubPage`） | 书内设置（`ReaderDisplaySettings`） | 一致？ |
|---|---|---|---|
| 开关标题 | 「安静阅读信息」 | 「显示安静阅读信息」 | ⚠️ 文案不同 |
| 电量样式 | `SettingSegmentedRow` | `MicroOptionPillRow` | 组件不同，语义同 |
| 六槽位 | `ui.components.HeaderFooterPicker` | `sheets.HeaderFooterPicker` | ❌ **折叠行文案不同** |
| 存储键 | 同一批 `KEY_CUSTOM_TIP_*` | 同 | ✅ |
| 落盘路径 | `ProfileAction.UpdateReader` → `settingsVm.updateReader` → **直接写全局** | `applyReaderEdit` → `ReaderSettingsRouter` → **可覆盖项进本书** | ⚠️ **路径不同** |
| 六槽位作用域 | 全局 | 全局（不在 `ReaderOverrideKey`） | ✅ 结果一致 |

**关键事实**：书内设置走 `ReaderSettingsRouter`，但六槽位**不在** `ReaderOverrideKey.entries` 里，因此 `changedOverridableKeys` 不会看到它们 → `plan.global` 会包含新值 → 写全局。**与"我的"入口殊途同归。**

⇒ **两个入口的落盘结果一致，但折叠行显示不一致。** 唯一真实分叉是 §7.3 的文案。

---

## 12. 电量、时间、空值、未知 token、超长内容行为

### 12.1 电量

| 场景 | 行为 | 位置 |
|---|---|---|
| 无权限/不可用（`level < 0`） | `splitOnBattery` 不切段 → 整个模板交给 `render` → `{BatteryPercent}` 替换为 `""` | [PagedReaderHeaderFooter.kt:229](../../../android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/PagedReaderHeaderFooter.kt:229) |
| 图标 + 百分比重复 | `ICON_ONLY`：图标 + 并排 `10sp` 数字（**数字不在图标内**，注释称"内显"但实现是并排） | :304-342 |
| 低电量样式 | `level <= 15` → `BatteryAlert` 图标（**仅 `ICON_WITH_TEXT` 分支用**） | :346-350 |
| 与 `{BatteryPercent}` 重复 | ⚠️ **可能**：若用户模板是 `"{BatteryPercent}"` 且样式为 `ICON_WITH_TEXT`，则渲染 `[图标] 85%`；若模板是 `"电量 {BatteryPercent}"` 则 `电量 [图标] 85%` | — |
| 刷新 | 30 秒轮询 + `ACTION_BATTERY_CHANGED` 广播 | [PagedReaderClock.kt:44-77](../../../android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/PagedReaderClock.kt:44) |

**`ICON_ONLY` 的命名与实现不符**：枚举名/标签是"电量内显"、Kotlin 文档写"数字画进电池轮廓里"，但 `BatteryInlineLabel` 实际是**图标与数字并排**（注释也承认"改为图标与数字各占其位"）。⇒ **文档/标签与实际不符（P3）。**

### 12.2 时间

- 刷新：对齐系统整分钟边界（`delayToNextMinute`）+ `ACTION_TIME_TICK` 广播。
- 影响重组：`currentTime` 是 `MutableState<String>`，每分钟变一次 → 触发 `PagedReaderHeader/Footer` 重组。**不会重分页**（`currentTime` 不在 `LayoutConfig` 的 `remember` key 里）。
- 电量每 30 秒变一次，同样不参与分页 key。⇒ **无重分页风险。**
- 但 `PagedReaderClockEffects` **无条件启动**（`PagedReaderHost` 内 `LaunchedEffect(Unit)`），**即使 `showReaderInfo = false`**。⇒ 见 §12.5。

### 12.3 空模板

| 层 | 行为 |
|---|---|
| 六槽位全空 + `showReaderInfo = true` | `PagedReaderHeader` 的 `if` 条件为 false → **整个页眉不渲染，不留空隙** |
| `showReaderInfo = false`（页脚） | 渲染 `Spacer(height = footerHeight)` → **保留空隙** |
| `showReaderInfo = false`（页眉） | **不留空隙** |

⚠️ **页眉与页脚在关闭时的空间行为不对称**（P3）：页脚留 `28dp` 空隙保持正文稳定，页眉**完全不占位** → 关闭"安静阅读信息"时正文会向上移动约 `24dp`。`PagedReaderHeader` 的 `if` 没有 `else` 分支。

### 12.4 未知 token / 特殊字符 / 换行 / 超长 / 双向文本

| 场景 | 行为 | 评价 |
|---|---|---|
| 未知 token `{Nope}` | **原样保留**在渲染输出里；`CustomTipDialog` 会标红并禁用"确定" | ⚠️ 但**已保存的旧模板**（如早期版本写入的）会原样显示 `{Nope}` 给用户 |
| 特殊字符 | 原样透传，无转义 | ✅ 安全（Compose `Text`） |
| 换行 | `OutlinedTextField` **未设 `singleLine`** → 可输入换行；渲染端 `Text(maxLines = 1, Ellipsis)` | ⚠️ 换行会被截断为省略号 |
| 超长文本 | **无长度限制**（输入框与存储均无上限）；渲染端 `maxLines = 1` + 每槽 `weight(1f)` | ⚠️ 超长模板被截断，用户不知道被截了多少 |
| 双向文本 | 无 RTL 处理 | 推断：`Row` 布局在 RTL 下会镜像，但模板内容不感知 |
| 重复 token | ✅ 支持（`replace` 全量替换；`splitOnBattery` 用循环处理多个 `{BatteryPercent}`） | ✅ |
| 空模板保存 | ✅ `render("")` → `""`；折叠行显示"无" | ✅ |

### 12.5 开关关闭后的行为（`showReaderInfo = false`）

| 问题 | 答案 | 证据 |
|---|---|---|
| 是否不占空间（页眉） | ❌ **仍不占空间**，但这是"没有 Spacer"而非"有意留空" | `PagedReaderHeaderFooter.kt:73` |
| 是否不占空间（页脚） | ✅ 占固定空隙 | :122 |
| 是否仍计算模板 | ⚠️ **仍计算**：`templateLeft`/`templateRight` 在 `if` **之前**求值 | :71-72, :117-118 |
| 是否仍读取电量/时间 | ✅ **仍读取**：`PagedReaderClockEffects` 无条件运行 | `PagedTxtReaderHost.kt:181` |
| 是否影响分页结果 | ❌ 不影响（`showReaderInfo` 不在 `LayoutConfig` key 里） | `PagedTxtReaderHost.kt:245-266` |

⇒ **关闭页眉页脚后，时钟 effect、电量广播接收器、模板求值全部继续运行**（P3 性能/工程改进，非缺陷）。

---

## 13. 与 S09-C1、D-2-B 的边界和共享 seam

### 13.1 D-2-B 已处理的是"作用域说明透明度"

- D-2-B 只改**文案**（页级作用域说明），不改任何 S10 功能。
- 其测试 `ReaderSettingsScopeNoticeTest.kt:114-116` 明确**钉住**"页眉页脚"是分组名、六槽位写全局。⇒ **S10 不得修改 D-2-B 文案**，本轮未修改。
- **S10 与 D-2-B 无功能重叠。**

### 13.2 S09 的三项边界

| S09 项 | 与 S10 关系 | 本轮处置 |
|---|---|---|
| SN-3（滚动模式无页眉页脚） | **同一事实**，S09 已记录 | 复核成立，**不重复计为 P1/P2**；列为待产品裁决 |
| S09 全书页码（`WholeBookPageModel`） | **不属于 S10** | 未接入；`WholeBookPageModel` 全仓**零生产调用**（grep 确认） |
| S09 点击区域 / 硬件键 | **不属于 S10** | 未涉及 |

### 13.3 共享 seam

| 文件 | S10 需求 | 冲突方 | 处置 |
|---|---|---|---|
| `feature/reader/pager/PagedReaderHeaderFooter.kt` | S10-B 渲染端 | **S09-C1**（若选"抽共享信息条"，S09 报告已预告需要此文件所有权） | **共享 seam，不得并行实施** |
| `ui/screen/reader/sheets/ReaderTocModels.kt` | S10-A 卷判定 | 无 | S10 独占 |
| `ui/components/CustomTipDialog.kt` | S10-B 对话框 + 折叠行 | 无 | S10 独占 |
| `ui/screen/reader/sheets/ReaderSettingsCommon.kt` | S10-B 第二份 picker | **D-2-B 刚改过此文件的 `displayValue`/scope notice** | **共享 seam**（不同函数，但同文件） |
| `ReaderSheetHost.kt` | S10-A 目录接线 | **窗口 C（S09-A-F1）** | 本窗口**不得触碰** |

**⇒ SEAM REQUEST：见 §19。**

### 13.4 不得给出的并行建议

S09 报告末尾建议"C-1（滚动模式补齐页眉页脚信息条）可先做"。**本报告不采用该建议**：它与 S10-B 的渲染端是同一文件（`PagedReaderHeaderFooter.kt`），且属于产品裁决范围。**若 S10-B 与 S09-C1 同时实施，会争用同一文件的渲染结构。**

---

## 14. 测试资产清单及真实性分级

### 14.1 清单

| 文件 | 位置 | 用例数 | 分级 | 说明 |
|---|---|---|---|---|
| `VolumeHeaderTest.kt` | `test/.../sheets/` | 5 | **BEHAVIOR（真）** | 10 条假阳性 + 14 条正例 + 全角缩进 + 分组保留索引 |
| `CustomTipPlaceholderTest.kt` | `test/.../pager/` | 8 | **BEHAVIOR（真）** | 提取/校验/转换/渲染；**用合成 `PageInfo(2,10)`，非真实控制器** |
| `CustomTipTemplateDescribeTest.kt` | `test/.../pager/` | 5 | **BEHAVIOR（真）** | `describeTemplate` 中文回显；含"不吞未知占位符" |
| `ReaderSettingsOverlayTest.kt` | `test/.../settings/` | 22 | **BEHAVIOR（真）** | 覆盖叠加；含 `HEADER_LEFT` 非法值回退（:80） |
| `SettingsStoreTest.kt` | `test/.../settings/` | — | **BEHAVIOR（真）** | `headerLeft`/`footerRight` 落盘读回（:87-88） |
| `ReadingPresetsTest.kt` | `test/.../settings/` | — | **BEHAVIOR（真）** | 预设回到结构默认含 `headerLeft`（:69） |
| `ReaderSettingsSheetTest.kt` | `androidTest/.../sheets/` | 6 | **Compose UI（未运行）** | 仅断言"页眉左侧"标签存在/消失；**不测槽位内容、不测对话框** |
| `ReaderTocSheetTest.kt` | `androidTest/.../sheets/` | 8 | **Compose UI（未运行）** | 卷分组索引保留（:48-67）；**不测点击跳转** |
| `ReaderRulesSheetTest.kt` | `androidTest/.../sheets/` | — | **Compose UI（未运行）** | 用 `readerTocEntries(listOf("第一卷",...))` 作 fixture |
| `ReaderSettingsScopeNoticeTest.kt` | `test/.../sheets/` | 21 | **BEHAVIOR（真）** | D-2-B 文案；**含源码文本扫描**（`the notice function has no catch all else branch`，:最后） |

### 14.2 逐项回答重点问题

| # | 问题 | 答案 |
|---|---|---|
| 1 | 是否有测试真正渲染 `PagedReaderHeader`/`PagedReaderFooter`/`TipRow`？ | ❌ **零**。grep 全仓 test/androidTest 无任何引用。`TipRow` 是 `private`，无法从测试触达。 |
| 2 | 是否有测试把真实 `PageInfo` 输入模板？ | ❌ **零**。`CustomTipPlaceholderTest:59` 手工构造 `PageInfo(chapterIndex=0, pageIndex=2, pageCount=10, progressPercent=30f)`；`PagedReaderControllerEffects.kt:158`（唯一真实来源）**零测试引用**。 |
| 3 | 是否测试六个槽位分别保存与回显？ | ❌ **零**。只有"页眉左侧"一个标签的显隐断言（`ReaderSettingsSheetTest:95-100`）。 |
| 4 | 是否测试每个 token 的真实值？ | ⚠️ **部分**。`renderReplacesAllKnownPlaceholders`（:53-70）一次性断言 8 个 token 的**合成值**；`{ChapterIndex}`/`{ChapterSize}`/`{PageRemaining}` 的真实分支（`source = null`）**未单独覆盖**。 |
| 5 | 是否测试未知 token、空模板、非法模板？ | ✅ `isValidReturnsFalseForUnknownPlaceholders`（:35-39）；`renderHandlesEmptyTemplateGracefully`（:73-84）；`describeTemplate` 保留未知（:29-31）。**但 `CustomTipDialog` 的"确定禁用"行为零测试。** |
| 6 | 是否测试书籍切换、本书覆盖和全局默认的隔离？ | ⚠️ **部分**。`ReaderSettingsOverlayTest` 测覆盖叠加（纯函数）；**六槽位不在可覆盖项里**，因此"书内改页眉→影响全局"这一真实路径**零测试**。 |
| 7 | 是否测试横屏、大字号、小屏和长文本溢出？ | ⚠️ **间接**。`ReaderSettingsSheetTest:scopeNoticeRemainsVisibleAt320DpWithLargeFont` 用 320dp/1.3x 测**作用域说明**，非槽位。**页眉页脚自身的 320dp 溢出零测试。** |
| 8 | 卷标题判定是否覆盖假阳性和假阴性？ | ⚠️ **假阳性充分（10 条），假阴性零覆盖**（无「第一篇」「第IV卷」「序章」「《第一卷》」用例）。 |
| 9 | 是否存在会制造虚假覆盖感的占位测试？ | ⚠️ **有一处**：`ReaderSettingsScopeNoticeTest` 的 `the notice function has no catch all else branch` 做**源码文本扫描**（`java.io.File(...).readText()` + 花括号配平），不是行为测试。它测的是"源码长什么样"，不是"文案是什么"。**属源码扫描类，非恒等断言，可接受但应标注。** |
| 10 | 历史报告声称的 PASS 是否对应当前工作树代码？ | **无法核实**（本轮未运行 Gradle）。但 S10-B 的核心文件（`CustomTipPlaceholder.kt`、`CustomTipDialog.kt`）**不在 `origin/main` 里**，任何声称"S10-B 已 PASS"的历史记录都只能指工作树快照，**不能指已发布基线**。 |

### 14.3 测试缺口汇总

| 缺口 | 严重度 |
|---|---|
| `PagedReaderHeader`/`PagedReaderFooter`/`TipRow` 零渲染测试 | **P2**（接线层无覆盖，页脚可以显示错误页码而全绿） |
| `PagedReaderControllerEffects` 的 `PageInfo` 写入零测试 | **P2** |
| `batteryTipStyle` 三档样式零测试 | **P3** |
| 六槽位保存/回显零测试 | **P3** |
| `CustomTipDialog` 校验/禁用/插入零测试 | **P3** |
| 卷标题假阴性零测试 | **P2** |
| 六槽位"书内改→写全局"零测试 | **P3** |

---

## 15. 双轴问题表

### 15.1 Standards / 工程与测试

| 编号 | 严重度 | 可达 | 位置 | 证据 | 影响 | 建议 | 需产品裁决 |
|---|---|---|---|---|---|---|---|
| **ST-1** | **P2** | ✅ | `ui/screen/reader/sheets/ReaderSettingsCommon.kt:783-841` vs `ui/components/CustomTipDialog.kt:220-281` | 两份 `HeaderFooterPicker`；:814 `template.ifBlank`（原始 token）vs :254 `describeTemplate(...)`（中文） | 同一设置在两个入口显示不同内容；书内设置显示 `{ChapterTitle}`，与 `CustomTipDialog.kt:252-253` 注释声明的设计意图直接矛盾 | 删除 `ReaderSettingsCommon` 那份，统一到 `ui/components` | 否 |
| **ST-2** | **P2** | ✅ | `feature/reader/pager/PagedReaderHeaderFooter.kt:100` 等 | `PagedReaderHeader/Footer/TipRow` 全仓零测试引用；`PagedReaderControllerEffects.kt:158` 唯一 `PageInfo` 来源零测试 | 页脚页码可以完全错误而全部测试仍绿 | 补 Compose 渲染测试 + `PageInfo` 写入测试 | 否 |
| **ST-3** | **P3** | ✅ | `ui/components/CustomTipDialog.kt:287`、`ui/screen/reader/sheets/ReaderSettingsCommon.kt:844` | 两个 `HeaderFooterPicker(selected: HeaderFooterItem)` 重载，全仓零调用 | 迁移残留，增加维护面 | 删除 | 否 |
| **ST-4** | **P3** | ✅ | `ui/screen/reader/sheets/ReaderTocModels.kt:71` | `groupChaptersByVolume` 唯一调用方是测试 | 死代码 | 删除或标注 | 否 |
| **ST-5** | **P3** | ✅ | `feature/reader/pager/PagedReaderHeaderFooter.kt:73` | 页眉关闭时无 `Spacer`（页脚 :122 有） | 关闭"安静阅读信息"时正文上移约 24dp | 补对称 Spacer | 否 |
| **ST-6** | **P3** | ✅ | `feature/reader/pager/PagedTxtReaderHost.kt:181` | `PagedReaderClockEffects` 无条件启动 | `showReaderInfo = false` 时仍每 30 秒读电量、注册广播 | 用 `showReaderInfo` 作 effect key | 否 |
| **ST-7** | **P3** | ✅ | `data/settings/SettingsStore.kt:49-63` + `PagedReaderHeaderFooter.kt:286-342` | 枚举标签"电量内显"、KDoc"数字画进电池轮廓里" vs 实现"图标与数字并排" | 文档/标签与实现不符 | 改文案 | 否 |
| **ST-8** | **P3** | ✅ | `feature/reader/pager/CustomTipPlaceholder.kt:161-175` | `renderSample` 用理想值，不覆盖 null 分支 | 预览不反映电量不可用/未排版 | 补降级样例说明 | 否 |
| **ST-9** | **P3** | ✅ | `ui/screen/reader/sheets/ReaderSettingsScopeNoticeTest.kt`（末用例） | `java.io.File(...).readText()` 源码扫描 | 测源码形态而非行为 | 标注为结构守卫 | 否 |
| **ST-10** | **P3** | ✅ | `ui/components/CustomTipDialog.kt:83-111` | `OutlinedTextField` 无 `singleLine`、无长度上限 | 可输入换行/超长，渲染端静默截断 | 加 `singleLine` + 长度提示 | 否 |
| **ST-11** | **P3** | ✅ | `feature/reader/pager/CustomTipPlaceholder.kt:130/136/142/125` | `{PageIndex}`/`{PageSize}` 未知时 `""`，`{PageRemaining}`/`{ChapterSize}` 未知时 `"0"` | 同一语义两种表达 | 统一为 `""` | 否 |

### 15.2 Spec / 产品要求与格式覆盖

| 编号 | 严重度 | 可达 | 位置 | 证据 | 影响 | 建议 | 需产品裁决 |
|---|---|---|---|---|---|---|---|
| **SP-1** | **P1** | ✅ | `ui/screen/reader/sheets/ReaderTocModels.kt:105` vs `feature/reader/doc/TxtChapterDetector.kt:83` | 两套"卷"判定；`isVolumeHeader` 不含 `篇/辑/册/序章/番外`，`TxtChapterDetector` 含 | **「第一篇 风起」被判为可点击章节，而「第一卷 风起」被判为分组名** —— 同一本书里两种观感 | 统一到单一真源 | 否（但统一口径需裁决） |
| **SP-2** | **P2** | ✅ | 全仓 grep：`isVolumeHeader` 仅 2 个调用点 | 点击跳转/`{ChapterTitle}`/搜索结果/进度/TTS/已读/书签 **零处理** | 目录里卷被"吞掉"变成分组名，但**同一个索引在别处仍是普通章节**；点分组名无反应（设计），但**章序号、`{ChapterIndex}`、`{ChapterSize}`、已读统计仍把卷算作一章** | 明确卷的语义（见 §18） | **是** |
| **SP-3** | **P2** | ✅ | `feature/reader/EpubParser.kt:673-720`（`parseNcx`） | 单个 `inNavPoint` 标志 → 嵌套 navPoint 展平 | EPUB 原始 TOC 层级完全丢失；卷节点成为"只有标题的空章"，用户翻到该章看到空白页 | 保留层级或标记卷节点 | **是** |
| **SP-4** | **P2** | ✅ | `feature/reader/doc/MarkdownDocument.kt:92` + `ReaderTocModels.kt:105` | H1「第一卷」进 `chapters`，随后被目录吞掉 | 与 SP-1 同源：Markdown 的 H1 卷标题占章序号 | 同 SP-1 | **是** |
| **SP-5** | **P2** | ✅ | `PagedReaderHeaderFooter.kt` 仅 `PagedTxtReaderHost.kt:200/347` 调用 | 7 种格式×模式组合中只有 3 种消费 | 滚动模式配置六槽位**完全无效果** | **等产品裁决**（S09 Q5/Q8） | **是** |
| **SP-6** | **P3** | ✅ | `data/settings/SettingsStore.kt:266-269` | `headerLeft/Right`、`footerLeft/Right` UI 已无写入入口 | 四个字段是**不可见的默认值来源**；用户无法改，但它们决定六槽位的初始显示 | 或恢复入口，或明确废弃 | **是** |
| **SP-7** | **P3** | ✅ | `data/settings/SettingsStore.kt:286/294` | 中间槽无 legacy 对应物，`effectiveHeaderCenter()` 直接返回 `customTipHeaderCenter` | 中间槽"未自定义"= 空，而左右槽"未自定义"= 有默认值 | 产品决定中间槽是否应有默认 | **是** |

### 15.3 分级计数

| 级别 | 数量 | 编号 |
|---|---|---|
| **P0** | **0** | — |
| **P1** | **1** | SP-1 |
| **P2** | **6** | ST-1、ST-2（Standards）+ SP-2、SP-3、SP-4、SP-5（Spec） |
| **P3** | **11** | ST-3、ST-4、ST-5、ST-6、ST-7、ST-8、ST-9、ST-10、ST-11、SP-6、SP-7 |
| **readiness blocker** | **3** | 见 §15.4 |

> 注：P2 共 6 项。其中 **SP-5 与 S09 SN-3 是同一事实**（滚动模式不消费页眉页脚），本报告为完整性保留并在 §13.2 明确说明重复；若按"S10 净新增"计则为 5 项。

### 15.4 Readiness blocker（尚未接线/未发布能力，上线前必须解决）

| 编号 | 内容 | 为何是 blocker |
|---|---|---|
| **RB-1** | `PagedReaderHeader/Footer/TipRow` 与 `PagedReaderControllerEffects` 的 `PageInfo` 写入**零测试覆盖** | 页脚页码是**用户直接可见的读数**；无测试意味着任何分页改动都可能静默显示错页码。**上线前必须有渲染级测试。** |
| **RB-2** | `batteryTipStyle` 三档样式（尤其 `ICON_ONLY` 的自绘 Canvas）**零测试** | 自绘 `Canvas` 的尺寸/填充比例无任何断言；低电量图标分支（`level <= 15`）仅在 `ICON_WITH_TEXT` 下使用，`ICON_ONLY` 不用 —— **分支逻辑本身可能有意料外的组合**。 |
| **RB-3** | 卷标题语义未定义（SP-2/SP-3） | 卷是"可点击章节"还是"纯分组"没有产品定义；EPUB 层级被展平后，卷节点成为空章。**在定义清楚之前，任何"卷"相关改动都会引入新的不一致。** |

---

## 16. 双轴问题表已在 §15 给出（编号 / 严重度 / 可达 / 文件行号 / 证据 / 影响 / 建议 / 是否需裁决）

---

## 17. 最多三个最小后续切片

### 切片 S10-B1：页眉页脚折叠行文案统一 + 死代码清理

- **范围**：删除 `ReaderSettingsCommon.kt:783-860` 的 `HeaderFooterPicker`（两份重载）与 `CustomTipDialog.kt:287-305` 的 legacy 重载，让书内设置统一走 `ui.components.HeaderFooterPicker`。
- **依赖**：无。
- **文件所有权**：`ui/screen/reader/sheets/ReaderSettingsCommon.kt`（与 D-2-B 同文件但不同函数）、`ui/components/CustomTipDialog.kt`。
- **与 S09/D-2-B 冲突**：⚠️ `ReaderSettingsCommon.kt` 是 D-2-B 的共享文件（不同函数）。**需 Codex 确认 D-2-B 已收尾。**
- **关闭**：ST-1、ST-3。

### 切片 S10-A1：卷判定单一真源

- **范围**：让 `isVolumeHeader` 复用 `TxtChapterDetector` 的卷形态集合（或反之），消除 SP-1 的双真源。
- **依赖**：**必须先有产品裁决**（卷的语义定义，见 §18）。
- **文件所有权**：`ui/screen/reader/sheets/ReaderTocModels.kt`（S10 独占）、`feature/reader/doc/TxtChapterDetector.kt`（若改）。
- **与 S09/D-2-B 冲突**：无。
- **关闭**：SP-1（部分）。

### 切片 S10-B2：页眉页脚渲染级测试补齐

- **范围**：新增 `PagedReaderHeader`/`PagedReaderFooter` 的 Compose 渲染测试；把真实 `PageInfo` 输入 `CustomTipPlaceholder.render`；覆盖 `batteryTipStyle` 三档。
- **依赖**：无（纯新增测试）。
- **文件所有权**：新增测试文件（`androidTest` 或 `test`）。
- **与 S09/D-2-B 冲突**：⚠️ 若 S09-C1 决定"抽共享信息条"，`PagedReaderHeaderFooter.kt` 会改结构，测试需要跟着改。**建议在 S09-C1 裁决后再定测试目标。**
- **关闭**：ST-2、RB-1、RB-2（部分）。

**三个切片互不混合**：B1 是 UI 去重、A1 是判定统一、B2 是测试补齐。**依赖关系**：A1 依赖产品裁决；B2 依赖 S09-C1 裁决；B1 无依赖。

---

## 18. 必须由 Codex/用户裁决的问题

| # | 问题 | 为什么必须裁决 | 影响切片 |
|---|---|---|---|
| **Q1** | **"卷"在产品里到底是什么？** 可点击的空章节，还是纯分组容器？ | 决定 SP-2（8 个消费端是否需要处理卷）、SP-3（EPUB 空章是否保留） | S10-A1 |
| **Q2** | **卷标题应否参与章序号 / `{ChapterIndex}` / `{ChapterSize}` / 已读统计？** | 当前**参与**（卷在 `chapters` 里）。目录里被吞、别处算一章 = 数据语义分叉 | S10-A1 |
| **Q3** | **EPUB 展平的 TOC 层级要不要恢复？** | 决定 SP-3 是"修"还是"接受现状"；恢复层级是解析层大改 | S10-A1 / SP-3 |
| **Q4** | **`{ChapterSize}` 是"全书总章数"还是"本章页数的配对项"？** | 中文名"总章数"实现为 `chapterCount`（全书）；与 `{PageSize}`（本章）不成对 | 文案 |
| **Q5** | **滚动模式要不要页眉页脚？**（S09 Q5/Q8 的延续） | 决定 SP-5 是"补渲染"还是"禁用设置项+说明" | S10-B2 |
| **Q6** | **四个 legacy 枚举字段（`headerLeft/Right`、`footerLeft/Right`）保留还是废弃？** | 它们是**不可见的默认值来源**；废弃会影响存量用户设置 | S10-B1 |
| **Q7** | **中间槽位"未自定义"是否应有默认值？** | 当前左右槽有默认（`headerLeft=BOOK_NAME` 等），中间槽恒空 | 文案 / 默认值 |

---

## 19. SEAM REQUEST

| # | 请求 | 理由 |
|---|---|---|
| **SEAM-1** | 请求 `feature/reader/pager/PagedReaderHeaderFooter.kt` 的**排他所有权**归 S10-B2 | S09 报告（§"文件所有权"）已预告 S09-C1 若"抽共享信息条"需要此文件。**两者不得并行。** 请求 Codex 明确：在 S09-C1 裁决前，此文件不分配给任何人。 |
| **SEAM-2** | 请求确认 `ui/screen/reader/sheets/ReaderSettingsCommon.kt` 可被 S10-B1 修改 | 该文件是 D-2-B 的共享文件（`displayValue` / scope notice 在同文件）。S10-B1 只删 `HeaderFooterPicker` 两个重载（:783-860），**不触碰** D-2-B 的 `displayValue`（:743-780）与 notice。 |
| **SEAM-3** | 请求确认 `feature/reader/doc/TxtChapterDetector.kt` 是否可被 S10-A1 修改 | 若 Q1/Q2 裁决为"统一到检测器口径"，需改此文件；该文件当前无其他窗口占用。 |

---

## 20. 未覆盖项

| # | 未覆盖内容 | 原因 |
|---|---|---|
| U1 | 未运行任何 Gradle / 测试 | 任务明确禁止 |
| U2 | 未做真机验证（页眉页脚实际渲染、电量图标外观） | 任务明确禁止 |
| U3 | 未验证 `PagedReaderControllerEffects.kt:146-163` 的 `PageInfo` 在**跨章**时是否真的及时更新（静态读代码只能确认写入时机是 `snapshotFlow`） | 需运行 |
| U4 | 未验证 `{ReadProgress}` 在**替换净化投影**活跃时是否仍用 source 口径的 `absOffset` | 需运行；静态看 `absOffset = anchorValue` 是 source 口径，但净化后 source 与 display 可能有偏移 |
| U5 | 未逐字节核对 S10-B 各文件的**未提交差异归属**（哪些是 S10 引入、哪些是其他切片混入） | 工作树是多人共享脏区，无法在只读模式下判定作者 |
| U6 | 未检查 Desktop/Electron 端是否有对应的卷标题/页眉页脚实现 | 任务范围限定 Android |
| U7 | 未验证 `ReaderSubPage.kt` 的 1083 行级修改中是否有 S10 以外的功能 | 超出 S10 范围 |
| U8 | 未验证 `CustomTipDialog` 在 **RTL 语言**下的布局 | 无 RTL 测试环境 |
| U9 | 未验证 `BatteryInlineLabel` 自绘 Canvas 在**深色纸**下的对比度 | 需真机截图 |
| U10 | 未核实历史报告（如 D-2-B）中提及 S10 的结论是否对应当前工作树 | 需 Codex 交叉核对 |

---

## 21. 最终建议

### 21.1 已实现且只缺验证

| 能力 | 状态 |
|---|---|
| 目录卷标题**假阳性修复** | ✅ 已实现，`VolumeHeaderTest` 10 条反例充分。**只缺真机确认**（在真实含「试卷」「拳击部」的 TXT 上，目录不再丢章）。 |
| 六槽位数据链路（存/取/渲染） | ✅ 已实现，读写对称。**缺渲染级测试**。 |
| 10 个占位符替换 | ✅ 已实现，纯函数测试覆盖 8 个。**缺真实 `PageInfo` 测试**。 |
| 中文回显 `describeTemplate` | ✅ 已实现且有测试。**但在书内设置页未生效**（ST-1）。 |
| 电量三种样式 | ✅ 已实现。**零测试**。 |

### 21.2 存在当前缺陷

| 缺陷 | 级别 |
|---|---|
| 卷判定双真源（SP-1）：同一本书里「第一卷」分组、「第一篇」不分组 | **P1** |
| `HeaderFooterPicker` 双实现（ST-1）：书内设置显示 `{ChapterTitle}`，我的设置显示「章节名」 | **P2** |
| 页眉页脚渲染层零测试（ST-2 / RB-1） | **P2** |
| 卷在目录里被吞、在别处算一章（SP-2） | **P2** |
| EPUB TOC 层级展平导致空卷章（SP-3） | **P2** |
| Markdown H1 卷标题占章序号（SP-4） | **P2** |

### 21.3 必须等待产品契约

| 项 | 等待 |
|---|---|
| 滚动模式是否补页眉页脚（SP-5） | Q5（S09 Q5/Q8） |
| 卷的语义定义（SP-2 / SP-3） | Q1 / Q2 / Q3 |
| legacy 枚举字段去留（SP-6） | Q6 |
| 中间槽默认值（SP-7） | Q7 |

### 21.4 是否建议进入实现

**建议：先裁决 Q1-Q7，再进入实现。** 理由：

1. **S10-A 与 S10-B 是两个独立能力**，可以分别放行 —— **S10-B1（UI 去重）无依赖，可立即放行**。
2. **S10-A1（判定统一）必须先有 Q1/Q2/Q3 的裁决**，否则统一到哪一侧都是猜。
3. **S10-B2（渲染测试）建议在 S09-C1 裁决后再定目标**，避免测试写完后 `PagedReaderHeaderFooter.kt` 被 S09-C1 重构。
4. **当前工作树的 S10-B 是未提交状态**（`CustomTipPlaceholder.kt` / `CustomTipDialog.kt` 不在 `origin/main`），**任何"已实现"的宣称都只对工作树成立**。

---

## 22. 回传摘要

| 项 | 值 |
|---|---|
| **报告绝对路径** | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\investigations\agent-investigation-s10-zcode.md` |
| **P0 数量** | **0** |
| **P1 数量** | **1**（SP-1 卷判定双真源） |
| **P2 数量** | **6**（ST-1、ST-2、SP-2、SP-3、SP-4、SP-5；其中 SP-5 与 S09 SN-3 同一事实） |
| **P3 数量** | **11**（ST-3 ~ ST-11、SP-6、SP-7） |
| **readiness blocker 数量** | **3**（RB-1 页眉页脚零渲染测试、RB-2 电量样式零测试、RB-3 卷语义未定义） |
| **最严重问题** | **SP-1（P1）**：`isVolumeHeader` 与 `TxtChapterDetector` 是两套互不相同的"卷"判定。同一本小说里「第一卷」被判为分组名、「第一篇」被判为可点击章节。两者都是当前生效的生产逻辑。 |
| **是否运行 Gradle** | **否** |
| **是否运行 ADB / 设备操作** | **否** |
| **除报告外是否修改文件** | **否**（`git status --short` 仅显示本报告为新增） |
| **是否申请下一阶段** | **否。等待 Codex 核验。** |

### 关键边界声明

- 未修改 `ReaderSheetHost.kt`（窗口 C 所有权）、`ReaderSettingsSheet.kt`（窗口 D 边界）、D-2-B 测试、S08/S09/S19 文件或报告。
- 未修改协调看板、handoff、Obsidian、TODO、`.gitignore`。
- 未删除 S19 发现的临时 UI dump。
- 未接入 `WholeBookPageModel`（S09 范畴）。
- 报告中未记录任何真实测试书名；源码/日志/UI dump 中如发现具体书名，一律以「发现具体测试书名」表述，未复制原文。

### 命令记录

```
$ git rev-parse --abbrev-ref HEAD
codex/recover-worktree-20260920
$ git rev-parse HEAD
5aed5939600e491843e3c44453921bd6273a233c
$ git status --short -- docs/coordination/investigations/agent-investigation-s10-zcode.md
（执行于报告写入后，见下）
```

---

**本报告为只读调查结论。等待 Codex 主任务核验，未领取下一片，未实施任何修复。**
