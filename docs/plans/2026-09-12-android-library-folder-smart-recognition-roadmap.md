# Android 应用内书籍目录、智能识别与阅读体验融合路线

> 状态：第 1–3 组已接线，且 **2026-09-13 已由 WorkBuddy 在 Redmi K50 Ultra / HyperOS V816 真机独立验收 A–E PASS**。此前“SAF tree provider 返回空列表”的 BLOCKED 结论已由同机复现推翻：DocumentsUI、直接授权的子目录、上级 `reads` 根目录下的子目录、冷启动及刷新后均可枚举真实文件。保留 SAF 目录模式，不申请 `MANAGE_EXTERNAL_STORAGE`；只有未来在完整 URI/grant/query 证据下重现“选择器非空、App 空”时，才评估多文件选择回退。
> 验收覆盖：导入/已入架、>32 MiB 导入、内容更新、删书不删来源、来源失效后内部副本阅读、>1,000 项截断、>16 层深目录截断、主动取消扫描和清理恢复；临时来源和书架记录均已恢复。`SafBookSourceScanner` 的 2,000 上限按所有 SAF 目录项（文件与文件夹合计）计数，纯目录深树不能绕过截断，已有 JVM 回归。
> 安装说明（2026-09-13 更正）：~~验收中 `install_with_confirm.ps1` 因执行环境的 `PATHEXT=.CPL` 未完成~~——该旧结论作废；根因是脚本自身两个缺陷（PS5.1 STDERR 提升 + UI dump CP936 解码损坏），已修复并在同一真机复现 `RESULT: CONFIRM_LOOP` / `SCRIPT_EXITCODE=0`。direct ADB `install -r` 仅可作为功能回退（FALLBACK），不得标为脚本 PASS。书架删除后私有副本的回收策略属于独立产品生命周期议题，不改变本路线的 R3 结论。
> 日期：2026-09-12（第 3 组实施记录 2026-09-13 追加）
> 范围：独立原生 Android `android/`；不包含 Electron 桌面端
> 参考对象：番茄小说本地书导入、`HapeLee/legado-with-MD3`、KOReader、Android Storage Access Framework
> 本文是需求与实现路线，不表示相关功能已经完成。

## 1. 决策摘要

目标不是把 App 改造成全盘文件管理器，而是实现一个只管理用户明确授权目录的本地书库：

```text
系统目录选择器（仅首次授权、权限修复或更换根目录）
                         │
                         ▼
                 用户可见来源书库
          App 内浏览 / 搜索 / 智能识别 / 批选
                         │
                         ▼
                现有 ShelfImporter
                         │
                         ▼
              filesDir/books 稳定副本
                         │
                         ▼
                书架 / 阅读器 / 搜索 / TTS
```

最终采用以下融合方式：

- 从番茄吸收“智能识别后批量选择”的低门槛入口；
- 从 `legado-with-MD3` 吸收首次目录设置、逐级浏览、面包屑、搜索、排序、当前目录/递归扫描和批量导入；
- 从 KOReader 吸收“文件浏览器是书库入口”以及收藏、历史、快速跳转等长期方向；
- 保留当前项目“导入后复制到应用私有目录”的稳定阅读链路；
- 不申请 `MANAGE_EXTERNAL_STORAGE`，不扫描未获授权的整台设备；
- 首期不允许在 App 内删除、移动或重命名来源文件；
- “智能”首期指本地确定性识别，不宣传为 AI，也不上传书籍内容。

### 1.1 实施记录（2026-09-12）

**Module 层（Codex 交付，改动前基线 `compileDebugKotlin` PASS）**

- 已新增 `LibraryRootStore`：以独立 DataStore 保存单一 SAF 根目录、显示名、上次浏览位置和排序；更换根目录会清除旧位置，清除配置不会删除来源文件。
- 已新增 `SafLibrarySource`：只读列出当前目录的子目录与支持格式，目录优先排序，单目录上限 1,000 项，并将授权失效/不可读目录显式返回为 `UNREADABLE`。
- 已新增 `SmartBookRecognizer` / `SafSmartBookRecognizer` 与 `BookSampleClassifier`：事件流识别、进度、小样本（≤64 KiB）格式/编码/章节判定、高/中/低置信与可解释理由。
- 已新增 JVM 用例覆盖根目录状态、SAF 列表语义与识别分类；此时尚未改动导入 UI。

**UI 接线（WorkBuddy 本轮交付，报告见 `android-parallel-delivery-2026-09-09/reports/workbuddy-r4-library-folder.md`）**

- 新增 `LibraryBrowserViewModel` / `LibraryBrowserModels` / `LibraryBrowserPolicy` 与 `LibraryBrowserRoute` / `LibraryBrowserComponents`，并补上 Codex 缺失的 `LibrarySource` Hilt 绑定。
- 新增路由 `shelf/library`：根目录授权（写入前确认持久授权）、恢复上次目录、重新授权、面包屑、名称搜索、名称/时间/大小排序、刷新、长按多选与全选/反选、批量加入书架。
- 智能识别按钮进入结果页：进度「已检查 X / Y」、停止、四档页签（推荐 / 全部 / 疑似入架 / 未识别）、逐条识别依据与书名作者建议、默认只勾选推荐且未疑似入架、批量交给既有 `ShelfImporter`。
- 导入入口 Sheet 收敛为方案 6.1 三入口（我的书籍目录 / 从系统选择文件 / 从电脑导入），并移除两处 PDF 支持胶囊。
- 批量导入复用 `shelf-graph` 作用域的同一个 `ShelfImporter`，导入进度、去重与批次摘要与导入页一致。
- 本轮零 Room schema 变更。

**已如实降级（详见报告 §4）**

- 「下拉刷新」降级为显式刷新按钮；
- 「已入架文件单击打开对应书籍」与「内容有更新」档位待第 3 组来源索引；
- 重复提示为**弱判定**（仅文件名），UI 明确标注「疑似」，不据此跳过导入，符合第 12 节拒绝项。

**门禁（本轮快照实测）**：`compileDebugKotlin` PASS；`testDebugUnitTest` 250 套件 / 2200 tests / 0 fail / 0 err；`compileDebugAndroidTestKotlin` PASS；`lintDebug` 0 errors / 4 warnings（均为既有告警）。未执行真机验收，未 commit / push。

**第 3 组：来源索引与增量更新（zcode 交付，2026-09-13，报告见 `android-parallel-delivery-2026-09-09/reports/zcode-r4-library-source-index.md`）**

- 数据层：新增 `library_source_refs`（一书一条，外键随书架级联清除；`size`/`last_modified`/`content_hash` 固定为导入基线，观测只更新 `last_seen_at`/`availability`），Room v13→v14，`MIGRATION_13_14` 只建新表，schema 快照 `app/schemas/.../14.json` 已生成并逐字段核对；`candidate_fingerprint` 列为方案字段清单的最小补充（与全量 MD5 语义分离）。
- 写入路径：`ShelfImporter` 导入成功后 best-effort 落引用；rootId 只在 `sourceRootIdFor` 能证明来源位于授权树内时写入（authority 一致 + documentId 前缀含边界），系统选择器/不透明 provider 一律 null，不伪造。
- 判定升级：`LibraryBrowserPolicy` 升级为 §5.6 四级判定（①精确来源→「已在书架」、④同位置大小/时间变化→「内容有更新」、②指纹一致→「已在书架」、③格式+名+大小→「疑似」+ 原名称弱判兜底）；页签「疑似入架」改名「已入架」，行内文案按判据分层，确认档（有 bookId）浏览态单击打开既有 `reader/{bookId}` 路由；弱判定仍不跳过导入，最终去重仍在 `ShelfImporter`。
- 超大文件：>32 MiB 用 `fp1|size|headMd5|tailMd5`（256 KiB 首尾分块）候选指纹，仅用于去重提示，读不满整块即放弃；导入与识别两条路径接入，识别仍不做全量哈希、并发不变。
- 来源失效：仅完整未截断识别扫描后，对「同根、此前可见、本次未出现」的引用标记 `missing`；单目录浏览只做观测刷新（last_seen/available/rootId 回填）。刷新时机仅显式动作，零新增轮询。
- 门禁（本轮实测）：`testDebugUnitTest` **253 套件 / 2238 tests / 0 fail / 0 err / 0 skip**（基线 250/2200，净增 3 套件 / 38 用例）；`compileDebugAndroidTestKotlin` PASS（13→14 迁移用例已编译，**未在设备执行**）；`lintDebug` 0 errors / 4 warnings（与基线相同的既有告警）。未执行真机验收，未 commit / push。

**第 3 组真机验收（WorkBuddy，2026-09-12，已被后续复验取代）**：当时静态契约 10/10 PASS、开发门禁 4/4 PASS、设备侧 Room `v13→v14` PASS，但来源索引 A–E 一度被标为 BLOCKED，原因是已选 tree URI 返回 0 子项。该记录保留用于解释调查起点，**不是当前结论**。

**2026-09-13 复验与最终结论**：同机、同应用的真实目录复现证明 DocumentsUI、直接子目录授权、上级 `reads` 根目录下钻、冷启动及刷新均能枚举文件；WorkBuddy 随后以隔离目录完成 A–E 独立验收 PASS。因此不实施先前拟议的“受限设备多文件选择回退 UI”，继续使用 `OpenDocumentTree`，也不引入 `MANAGE_EXTERNAL_STORAGE`。以后只有完整 URI/grant/query 证据重新表明“选择器非空而 App 为空”时，才重新评估该兼容性路径。

## 2. 调研事实与证据等级

### 2.1 番茄小说

能够确认的公开事实：

- Google Play 页面确认海外版开发者为 Beijing Zhending Technology Co., Ltd.，但商店说明没有公开“智能识别”的内部算法。
- 一份带步骤截图的第三方使用说明记录了“书架 → 导入图书 → 从本机导入 → 智能识别 → 批量选择加入书架”的用户路径。
- 多份第三方教程对“自动扫描候选本地书籍、批量勾选”描述一致。

证据限制：

- 没有找到番茄官方关于本地书“智能识别”判定规则、扫描范围、格式校验、标题/作者提取或章节识别算法的技术文档；
- “自动提取作者、完整章节结构”等说法只出现在第三方教程，不能当作已确认的番茄内部实现；
- 番茄不同地区、版本和 Android 系统上的入口、权限方式可能不同，本文不做像素级复刻。

因此，本文把可观察的产品行为抽象为三项需求，而不推测其源码：

1. **候选发现**：在用户允许的范围内找到可能是书籍的文件；
2. **书籍判定**：过滤日志、代码、缓存、空文件、伪 EPUB 等误报；
3. **内容识别**：导入时识别编码、标题候选、作者候选和章节结构。

### 2.2 legado-with-MD3

该项目的本地导入实现提供：

- 默认书籍根目录及持久授权恢复；
- 当前目录浏览与递归扫描；
- 文件名搜索、按名称/大小/时间排序；
- 面包屑、返回上级、全选、反选和批量加入书架；
- 首次引导里的书籍目录设置。

但以下实现不应复制：

- 每 1.5 秒轮询目录；
- `Channel.UNLIMITED` 与 16 路并发递归扫描；
- 仅凭文件名判定“已在书架”；
- 阅读或管理直接依赖外部来源文件；
- 来源文件删除能力默认暴露；
- 用 JS 执行文件名规则；
- 巨型 ViewModel、Controller 和菜单文件。

该参考仓库使用 GPL-3.0，而当前项目使用 MIT。若要保持当前项目的 MIT 发布方式，只能借鉴公开行为、交互和架构思想并净室重写，不直接复制其源码或资源。

### 2.3 Android 平台约束

Android 官方建议通过 `ACTION_OPEN_DOCUMENT_TREE` 让用户授权一个目录树，并允许 App 持久化 URI 权限。Android 11 以后，内部存储根目录、可靠 SD 卡根目录、`Download` 根目录、`Android/data` 和 `Android/obb` 等位置存在选择限制；大目录遍历也可能影响性能。

`filesDir` 适合作为稳定运行副本，但不适合作为用户书库：其他 App 无法直接访问，并且卸载 App 时会被删除。因此来源书库和运行副本必须分层。

## 3. 当前项目基线

当前工程不是从零开始，已有能力包括：

| 能力 | 当前实现 | 结论 |
|---|---|---|
| 系统选择多文件 | `ShelfImportRoute` + `OpenMultipleDocuments` | 保留为备用入口 |
| 系统授权目录 | `ShelfImportRoute` + `OpenDocumentTree` | 改为首次授权/更换目录入口 |
| 有界递归扫描 | `SafBookSourceScanner` | 直接复用并扩展事件输出 |
| 格式真源判定 | `FormatClassifier` | 复用；增强 EPUB 结构预检 |
| TXT 编码识别 | `PlainTextDecoder` | 直接复用 |
| TXT/Markdown 章节识别 | `TxtChapterDetector`、`TxtFileScanner` | 已比普通“第 X 章”匹配更完整，不重复开发 |
| 内容哈希去重 | `ShelfImporter` | 复用；补齐超大文件策略和来源索引 |
| 私有稳定副本 | `ShelfImporter`、`EpubRepository` | 必须保留 |
| 导入批次与历史 | `ShelfImporter`、`ImportHistoryStore` | 复用到新界面 |

当前缺口：

- 用户每次仍以系统选择器为主要入口，没有固定的 App 内书籍目录；
- 扫描器只按扩展名/MIME 发现候选，还没有可解释的“智能识别结果”；
- 列表不能稳定显示“已导入、内容有更新、来源不可访问”；
- TXT 标题仍主要来自文件名，作者为空；
- 导入页展示 PDF，但格式真源并不支持 PDF，存在需求与实现不一致；
- 当前共享工作区有大量并行 WIP，首期不得抢改重叠文件或插入新的 Room migration。

## 4. 修正后的功能需求

### 4.1 P0：应用内书籍目录

首次进入“导入书籍”时：

1. 显示“设置我的书籍目录”；
2. 调用系统目录选择器完成一次授权；
3. 持久化 tree URI 和显示名称；
4. 回到 App 内浏览根目录；
5. 以后从书架进入时直接恢复上次目录；
6. 授权失效时显示“重新授权”，不清空书架和内部副本。

根目录设置必须可以跳过，用户仍可继续使用“从系统选择文件”和“从电脑导入”。

### 4.2 P0：应用内浏览

页面提供：

- 当前根目录卡片和“更换”入口；
- 顶部横向面包屑；
- 当前目录列表；
- 按名称、修改时间、大小排序；
- 文件名搜索；
- 下拉刷新；
- “当前目录”和“智能识别”两个模式；
- 长按多选、全选、反选、取消选择；
- 批量加入书架；
- 扫描进度和取消入口。

文件夹单击进入；普通文件单击切换选择；已入架文件单击可打开对应书籍。多选状态底部只显示批量导入动作，不提供来源删除。

### 4.3 P0：番茄式智能识别

“智能识别”按钮的准确说明应为：

> 在已授权书籍目录中查找可阅读文件。识别仅在本机完成，不上传正文。

扫描结果分为：

- **推荐导入**：高置信、未导入；默认勾选；
- **可能是书籍**：可读但结构不明确；默认不勾选；
- **已在书架**：精确或高可信匹配；默认不勾选；
- **内容有更新**：来源身份相同但大小/时间/哈希变化；
- **未识别**：格式不支持、文件损坏或内容不像书籍；可查看原因。

用户必须能够看到“为什么被推荐/为什么未识别”，避免智能识别成为黑盒。

### 4.4 P1：识别结果预览

点开候选文件显示：

- 来源目录和文件名；
- 格式、大小、修改时间；
- 检测到的编码；
- 推测书名和作者；
- 识别依据；
- 是否可能重复；
- 导入后章节识别结果。

TXT/Markdown 的书名和作者只能作为建议，导入前允许修改；不得静默覆盖用户输入。EPUB 的 OPF metadata 可作为权威首选，文件名作为回退。

### 4.5 P1：来源状态索引

在 Room schema 可安全演进后增加 `library_source_refs`，记录：

```text
bookId
rootId
providerAuthority
documentId
displayName
format
size
lastModified
contentHash
lastSeenAt
availability
```

用途：

- 精确标记已导入；
- 区分同名不同内容；
- 识别移动、改名和内容更新；
- 授权或介质失效时显示来源状态；
- 为后续增量扫描提供比较基线。

该表不是阅读正文的事实源。即使来源不可用，阅读器仍只依赖内部稳定副本。

## 5. 智能识别管线

智能识别应设计成一个深 Module。调用者只学习一个 Interface，复杂阶段留在 Implementation 内部。

```kotlin
interface SmartBookRecognizer {
    fun recognize(request: RecognitionRequest): Flow<RecognitionEvent>
}
```

Interface 约定：

- 只扫描 `request.root` 授权范围；
- 支持取消；
- 持续返回进度、候选和最终摘要；
- 不修改文件、不导入书籍、不写书架；
- 读取量、并发、目录深度和结果数有明确上限；
- 单个文件失败不终止整个任务；
- 结果包含判定理由，不只返回 Boolean。

`SafSmartBookRecognizer` 是生产 Adapter，测试使用 `FakeSmartBookRecognizer`，使 Seam 同时服务 UI 和自动测试。

### 5.1 阶段 A：候选发现

复用 `SafBookSourceScanner`，但把一次性结果扩展为事件流：

```text
Started
DirectoryVisited
CandidateDiscovered
CandidateClassified
Progress
Warning
Completed
Cancelled
```

默认限制沿用现有值：

- 最多访问 2,000 个目录项（文件与文件夹合计）；
- 最多返回 500 本候选书；
- 最大目录深度 16；
- 并发查询最多 2；
- 允许用户取消；
- 达到上限后返回“结果已截断”，不伪装成完整扫描。

不使用定时轮询。刷新时机仅包括：用户下拉刷新、点击智能识别、重新进入页面后轻量校验，以及未来可选的受约束后台任务。

### 5.2 阶段 B：廉价预检

枚举阶段只读取文档 metadata。候选预检采用小量、渐进读取：

1. 扩展名和 MIME 初筛；
2. 前 4–64 KiB 内容探测；
3. EPUB ZIP 魔数和基础容器结构校验；
4. TXT/Markdown 编码检测；
5. 二进制/空文件/不可读文件排除；
6. 隐藏、临时、下载中、缓存和系统噪声文件排除。

不得为了列表识别读取所有候选文件全文。云盘或远程 DocumentsProvider 可能没有可靠大小，也可能在第一次读取时触发下载，UI 必须显示对应状态并允许跳过。

### 5.3 阶段 C：可解释评分

硬拒绝条件优先于评分：

- 空文件；
- 无读取权限；
- 明确二进制且不是有效 EPUB；
- 伪 EPUB；
- 格式不支持；
- `.part`、`.tmp` 等未完成文件。

通过硬门槛后再生成置信等级：

| 等级 | 示例依据 | 默认选择 |
|---|---|---:|
| 高 | 有效 EPUB；或可读长文本且出现多个可靠章节标题 | 是 |
| 中 | 可读 TXT/MD，但没有可靠章节结构；可能是短篇或散文 | 否 |
| 低 | 文本过短、代码/日志特征明显、替换字符比例过高 | 否 |

评分只用于排序和默认选择，不能替代格式真源校验。短篇、诗歌和无章节散文仍允许用户手动导入。

### 5.4 阶段 D：标题和作者建议

优先级：

1. EPUB OPF metadata；
2. TXT/MD 开头的明确字段，如“书名：”“作者：”；
3. 文件名中的保守模式，如 `书名 - 作者`、`《书名》作者名`；
4. 文件名去扩展名；
5. “未命名书籍”。

禁止：

- 用远程 AI 上传正文识别；
- 用 JS 执行任意文件名规则；
- 把模糊推测静默写成最终 metadata；
- 为了找作者扫描整本正文。

### 5.5 阶段 E：章节识别

当前项目的 `TxtChapterDetector` 已覆盖：

- 第 X 章/节/回/卷/部/篇/册；
- 序章、楔子、番外、尾声、终章等特殊章节；
- `Chapter N`；
- 数字+标点、中文数字+顿号、数字括号、Markdown 标题等候选规则；
- 最少章节数和平均章节长度保护；
- 误报过密时回退“全文”。

因此不再新写一套“番茄章节识别器”。正确做法是：

- 候选列表只做小样本结构判断；
- 用户确认导入后先复制到稳定副本；
- 再由 `TxtFileScanner` 对稳定副本顺序扫描一次，生成真实章节索引；
- 扫描结果显示“已识别 N 章”或“未检测到可靠目录，按全文阅读”；
- 保留用户选择目录规则和自定义规则的能力。

这样避免在“智能识别”和“正式导入”阶段重复读取大文件全文。

### 5.6 阶段 F：重复与更新判定

判定顺序：

1. 同一来源 `authority + documentId`：精确来源匹配；
2. 内容哈希相同：同内容文件，允许识别改名或移动；
3. 格式 + 文件名 + 大小相同：只显示“可能重复”，不能直接当作最终事实；
4. 来源相同但大小、修改时间或哈希变化：显示“内容有更新”。

现有 `ShelfImporter` 对不超过 32 MiB 的文件计算全量 MD5，超大文件回退文件名+大小。后续可对超大文件使用“大小 + 首尾分块哈希”做快速候选指纹，真正覆盖原书前仍需完整导入校验。该指纹只用于去重，不用于安全签名。

## 6. 页面信息架构

### 6.1 导入入口 Sheet

```text
导入书籍

我的书籍目录
  在 App 内浏览、智能识别和批量导入

从系统选择文件
  临时选择一本或多本书

从电脑导入
  获取桌面端已同步书籍
```

首期移除 PDF 支持胶囊。只有格式真源、解析器、阅读器、搜索、TTS 和异常验收形成闭环后，才能重新宣称支持 PDF。

### 6.2 书籍目录页

```text
导入书籍

[我的书籍目录]  Documents / Books       [更换]

Books  ›  小说  ›  已完结
[搜索文件名……]                         [排序]

[当前目录] [智能识别]

📁 科幻小说                              32 项
📄 测试 TXT       4.2 MB · 昨天          已在书架
📄 测试 EPUB      8.1 MB · 09-10         推荐导入
📄 文本片段.md     6 KB · 09-08           可能是书籍

长按后：
[已选择 3 项] [全选] [反选]             [加入书架]
```

面包屑放在顶部内容区，不照搬参考项目的底部路径栏，避免与多选操作栏和系统手势安全区竞争空间。

### 6.3 智能识别结果页

```text
智能识别
已检查 486 / 732 个文件                         [停止]

[推荐 12] [全部 18] [已入架 5] [未识别 27]

✓ 测试 TXT
  推荐：可读中文文本 · 检测到可靠章节结构

□ 短篇集.md
  可能是书籍：文本可读 · 未检测到章节

— backup.log
  未识别：日志文件特征明显

[已选择 12 本]                            [加入书架]
```

默认只勾选“推荐导入且未入架”。扫描完成后必须给出：推荐数、可选数、重复数、未识别数、不可读目录数和是否截断。

## 7. Module 与 Seam 设计

### 7.1 `LibraryRootStore`

DataStore Module，保存：

- root URI；
- 显示名称；
- 上次浏览 document ID；
- 排序方式；
- 上次扫描摘要；
- 授权状态的最近检查结果。

第一阶段只有一个根目录，不新增 Room migration。

### 7.2 `LibrarySource`

```kotlin
interface LibrarySource {
    suspend fun list(location: LibraryLocation): LibraryListing
    fun recognize(request: RecognitionRequest): Flow<RecognitionEvent>
}
```

生产 Adapter 为 SAF，测试 Adapter 为内存文件树。目录遍历、provider 差异、未知 metadata、取消和限制均隐藏在 Implementation 中。

### 7.3 `SmartBookRecognizer`

负责发现、预检、评分和解释，不负责导入。其 Interface 是 UI 与测试共享的验收 Seam。

### 7.4 `ShelfImporter`

继续作为真正改变书架和文件状态的 Module：

- 逐本校验；
- 去重；
- 保存稳定副本；
- 解析 metadata；
- 建立章节索引；
- 写数据库；
- 记录批次结果。

不要增加一个只转发 `ShelfImporter` 方法的浅 Module。

### 7.5 `LibrarySourceIndex`

第二阶段再增加。它只保存来源关系和观测状态，不改变 `BookEntity.local_uri`、正文路径或阅读坐标的事实源。

## 8. 性能、隐私与失败语义

### 8.1 性能

- metadata 枚举与内容探测分阶段；
- 内容探测最多 2 路并发；
- 结果边产生边显示；
- 列表只保存轻量模型，不保留 `DocumentFile` 树；
- 不在主线程打开输入流；
- 不在扫描阶段生成封面、完整章节或全文哈希；
- 达到上限必须明确提示；
- 退出页面或点击停止必须取消底层读取。

### 8.2 隐私

- 只扫描用户授权根目录；
- 不申请全盘文件权限；
- 不上传文件名、目录、正文样本或识别结果；
- 诊断日志只记录中性编号、格式、大小、耗时和错误类型，不记录真实书名；
- WorkBuddy 报告继续使用“测试 TXT”“测试 EPUB”等中性名称。

### 8.3 失败语义

| 状态 | 用户提示 | 可恢复动作 |
|---|---|---|
| 未设置目录 | 尚未设置我的书籍目录 | 选择目录 / 从系统选文件 |
| 授权失效 | 无法访问原书籍目录，书架内已导入书籍不受影响 | 重新授权 / 更换目录 |
| 云盘离线 | 文件暂时不可用 | 重试 / 跳过 |
| 扫描截断 | 目录较大，仅显示前 N 个候选 | 缩小目录 / 继续分目录扫描 |
| 文件损坏 | 文件格式与内容不一致 | 查看原因 / 跳过 |
| 用户取消 | 已停止，未导入任何新文件 | 重新扫描 |
| 部分导入失败 | X 本成功，Y 本失败 | 查看失败项 / 重试失败项 |

来源失效不得让已导入书籍变成不可读，因为内部稳定副本仍是阅读事实源。

## 9. 阅读器后续优化承接

文件夹与智能识别完成后，参考 `legado-with-MD3` 和 KOReader 的高价值能力，按以下顺序推进：

1. **阅读器工具动作配置**：显示/隐藏/拖动排序，不引入大量颜色、毛玻璃和自定义图标；
2. **阅读记录时间轴与章节下钻**：复用现有 `reading_sessions`，新增章节和真实阅读字数必须先定义数据事实源；
3. **快速浏览**：进度拖动时显示章节和正文预览，在轨道上标出章节、书签、笔记和高亮；
4. **预测性返回**：只在导航和 Sheet 上使用，必须尊重 reduced-motion；
5. **封面共享转场**：纯视觉优化，排在功能与性能之后；
6. **手柄翻页**：当前已有音量键翻页，手柄只作为可选输入 Adapter；
7. **多格式扩展**：PDF、漫画、有声书各自立项，不与本地目录首期混做。

书架动态保存视图已经存在，不重复实现参考项目的智能伴生分组；可在现有视图之上提供“未读、在读、已完成、最近导入”等预置条件。

## 10. 执行路线

### 第 0 组：共享 WIP 收口

- 梳理当前未提交 Android 改动的归属；
- 完成现有修复的定向验证和 WorkBuddy 验收；
- 形成可追溯基线；
- 不把 desktop 或无关诊断工件混入 Android 提交。

### 第 1 组：目录授权与 App 内浏览

- `LibraryRootStore`；
- 根目录授权、恢复、重新授权；
- `LibrarySource` SAF Adapter；
- 当前目录列表、面包屑、搜索、排序、刷新；
- 保留系统文件和电脑导入入口；
- 移除错误 PDF 宣称。

本组不改 Room schema。

### 第 2 组：智能识别最小闭环

- `SmartBookRecognizer`；
- 事件流、进度和取消；
- 小样本格式/编码/文本判定；
- 高/中/低置信和原因；
- 推荐结果默认选择；
- 批量交给现有 `ShelfImporter`；
- 导入结束展示真实章节结果。

### 第 3 组：来源索引与增量更新

- `library_source_refs` 需求和 migration；
- 精确已入架、移动/改名、来源失效、内容更新；
- 超大文件快速候选指纹；
- 轻量刷新，不做持续轮询。

必须等当前 Room WIP 收口后再开始。

### 第 4 组：阅读器高价值优化

- 工具动作配置；
- 阅读时间轴和章节统计；
- 快速浏览及章节/书签/高亮标记；
- reduced-motion 兼容的预测性返回；
- 最后评估共享元素转场。

## 11. 验收矩阵

### 11.1 自动测试

- DataStore 根目录读写、旧值和损坏值恢复；
- 面包屑与父子 document ID 导航；
- 名称/时间/大小排序；
- 文件数、候选数、深度和取消限制；
- 空文件、二进制、伪 EPUB、不可读文件；
- UTF-8、UTF-16、GB18030 TXT；
- 标准章节、编号章节、无章节短篇、误报风暴；
- 同名不同内容、改名同内容、超大文件；
- 扫描失败不写书架；
- 导入后内部副本存在且来源失效仍可打开。

### 11.2 WorkBuddy 真机验收

- 仅使用连接的真实 Android 手机，不使用 MuMu；
- 先 `adb devices` 核对 serial，所有命令显式 `adb -s <serial>`；
- 首次授权、取消、重新授权、重启恢复；
- 当前目录浏览和智能识别；
- 搜索、排序、面包屑、返回、多选和取消扫描；
- 大目录截断与长任务不假死；
- 测试 TXT/测试 EPUB 推荐、重复、失败和部分成功；
- SD 卡或云盘暂不可用时的恢复提示；
- 删除书架项不会删除共享目录原文件；
- 来源撤销后内部副本仍可阅读、搜索、TTS；
- 真机报告不得记录具体测试书名。

安装要求：优先运行 `android/scripts/install_with_confirm.ps1`；若脚本已实际超时或被 MIUI 阻断，先保存失败证据，再允许使用已确认 serial 的 `adb -s <serial> install -r <apk>` 继续功能验收。直接 ADB 仅是功能测试回退，不得写成安装脚本 PASS。

逐条执行手册（前置条件、命令、现场保护、判定表、报告模板）见
[`2026-09-12-android-library-folder-device-acceptance.md`](2026-09-12-android-library-folder-device-acceptance.md)。

### 11.3 性能门槛

- 目录列表首屏不等待完整递归扫描；
- 扫描过程中页面可滚动、可返回、可取消；
- 大目录不 OOM、不 ANR；
- 内容探测并发不超过 2；
- 停止后不再继续增加候选或打开新输入流；
- 扫描和导入分别统计耗时，不能把扫描完成误报为导入完成。

## 12. 明确拒绝项

- `MANAGE_EXTERNAL_STORAGE`；
- 未经授权扫描整台设备；
- 自动上传书籍或样本文本；
- 每 1.5 秒轮询目录；
- 无界队列或高并发递归；
- 仅按文件名判断重复；
- 在浏览列表静默删除、移动或重命名来源文件；
- 用 JS 执行文件名解析规则；
- 在格式链未完成前宣称支持 PDF；
- 直接复制 GPL 源码或资源；
- 为“智能识别”引入远程大模型依赖；
- 与文件夹首期同时改造主题引擎、导航框架或依赖注入体系。

## 13. 参考资料

- [Android：Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files)
- [Android：Access app-specific files](https://developer.android.com/training/data-storage/app-specific)
- [Google Play：Use of All files access permission](https://support.google.com/googleplay/android-developer/answer/10467955?hl=en)
- [HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3)
- [legado-with-MD3：ImportBookViewModel](https://github.com/HapeLee/legado-with-MD3/blob/main/app/src/main/java/io/legado/app/ui/book/import/local/ImportBookViewModel.kt)
- [legado-with-MD3：ImportBookScreen](https://github.com/HapeLee/legado-with-MD3/blob/main/app/src/main/java/io/legado/app/ui/book/import/local/ImportBookScreen.kt)
- [legado-with-MD3：OnboardingScreen](https://github.com/HapeLee/legado-with-MD3/blob/main/app/src/main/java/io/legado/app/feature/onboarding/OnboardingScreen.kt)
- [KOReader User Guide](https://koreader.rocks/user_guide/)
- [番茄小说 Google Play 页面](https://play.google.com/store/apps/details?id=com.dragon.read.oversea.gp)
- [第三方截图说明：番茄免费小说从本机导入图书](https://www.itmop.com/article/51976.html)

## 14. 下一步

**已完成**：第 1 组（目录授权、App 内浏览、入口收敛、去 PDF 宣称）、第 2 组（智能识别最小闭环）与
第 3 组（来源索引与增量更新：`library_source_refs` v13→v14、导入落引用、§5.6 四级判定、超大文件候选指纹、
轻量观测/失效对账）均已接线并通过门禁，实施记录见 §1.1，报告见
`docs/plans/android-parallel-delivery-2026-09-09/reports/{workbuddy-r4-library-folder,zcode-r4-library-source-index}.md`。
**真机验收已完成**：WorkBuddy 于 2026-09-13 以隔离目录完成方案 §11.2 的 A–E 动态矩阵，13→14 迁移测试亦已纳入四道 Gradle 门禁；R3 不再是待验收项。

**后续候选**：可选的手势级下拉刷新与识别结果预览页（P1）；以及另行定义的私有副本回收/恢复生命周期契约。两者均不得倒灌为 R3 的返工理由。
第 4 组（阅读器高价值优化）不在本轮。

任何实现中若需要修改其他 Agent 所有文件，必须提交 `SEAM REQUEST` 交给集成者，不得直接越界。
