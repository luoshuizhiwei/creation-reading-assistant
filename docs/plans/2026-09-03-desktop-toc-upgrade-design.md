# 桌面端阅读目录（TOC）升级方案

- 日期：2026-09-03
- 范围：桌面端 Electron（`src/` + `electron/`），不影响 `android/`
- 状态：**P0-A / P0-B 已实施**（2026-09-03，见 §9 实施记录）；P1（TXT 目录修正）待排期

## 1. 现状诊断

桌面端三种格式各有一套目录实现，能力参差，且互不复用：

| 格式 | 解析位置 | 存储 | UI 与跳转 | 主要缺口 |
|---|---|---|---|---|
| EPUB | 主进程导入时一次性解析（`electron/main/epub-metadata.ts` `parseToc`，EPUB3 nav 优先、回落 NCX），随 library index 落盘 | `book.epub.toc`，扁平 `EpubTocItem{id,label,href,level}`（`src/types/library.ts:8`） | `EpubSidePanel.tsx` 三 Tab（目录/高亮/书签），点击 `rendition.display(href)` 分页跳转 | 无当前章高亮（仅"当前位置：X"文本）、无搜索、无折叠、长目录无优化 |
| TXT | 渲染进程每次打开重算（`ReaderPage.tsx` `splitTxtChapters`：3 组正则 + 序章合成 + "正文"降级） | 不持久化（`useMemo`） | 右侧列表面板，`scrollIntoView(txt-chapter-N)` 平滑滚动 | 同上，且识别质量落后 Android（§1.1）、识别错了不可修正；**目录与设置面板上下堆叠在同一右列**（`ReaderPage.tsx:717-742`），信息架构混乱 |
| MD | 渲染进程（`markdownToc`，仅 ATX `#` 标题） | 不持久化 | 同 TXT，按 slug 锚点跳转 | Setext 标题（下划线式）漏识别；slug 撞名 → DOM id 重复 → `scrollIntoView` 跳错位置 |

两处目录 UI 都是纯 `map` 扁平按钮列表（`EpubSidePanel.tsx:98` / `ReaderPage.tsx:727`），
`level` 只用于缩进，无父子语义；全仓无虚拟列表依赖。

### 1.1 与 Android 线的识别能力差（2026-09-03）

Android `TxtChapterDetector` 刚完成平台语料增强（handoff 第 25 节）：60+ 标题语料 +
12 条正文反例，覆盖感言章（上架/完本/新书感言）、最终话/间章/幕间/末章（刺猬猫/轻小说系）、
廿/卅数字、"第两百章之后"类反例负向断言，以及编号样式自动嗅探
（num-dot → cn-num-dot → bracketed → num-bare，密度验证 ≥3 章且平均章长 ≥300 字，
全文 ≥3000 字才嗅探，探测入口断开递归）。

桌面 `splitTxtChapters` 未同步这些规则。两端不共享运行时代码，需按 TypeScript 重写移植；
**语料测试用例可直接搬**（`TxtChapterDetectorPlatformCorpusTest` / `TxtChapterDetectorAutoSniffTest`
→ 桌面 vitest）。桌面目录每次打开现算、无磁盘缓存，移植后不存在 Android 那样的 profile key
缓存失效问题。

### 1.2 相关既有资产

- legado backlog 第 9 条（TXT 章节识别规则可选与自定义，`docs/plans/backlog-09-txt-toc-rules-design.md`）
  是 Android 线对应物；桌面本方案取其"识别错了用户能修"的精神，交互形态按桌面另设计（§4 P1）。
- Android P3.3 目录已读标记 + 手动排序设计（`docs/plans/2026-08-16-toc-read-mark-manual-sort-design.md`）
  桌面未实现，列为候选（§6），不在本期。
- `scripts/verify-reader-formats.mjs` 固化了现有目录/面板结构断言
  （`txt-chapter-`、`tocCollapsed` 等），改动面板结构必须同步该脚本。

## 2. 目标与非目标

目标：

1. **目录可用性基线**：当前章节高亮 + 列表自动跟随、搜索过滤、层级折叠——三格式同享。
2. **目录识别质量**：TXT 对齐 Android 语料覆盖与自动嗅探；MD 补 Setext 识别与 slug 唯一化。
3. **TXT 目录可修正**：识别错时用户可合并/拆分/改名章节，结果持久化。

非目标（本期不做）：

- 不改 `EpubTocItem` 持久化 schema（树在渲染层派生，见 §3.1）。
- 不做 PDF（桌面端不支持该格式）。
- 目录已读标记、TXT/MD 书签高亮面板同构、目录跳转历史、虚拟滚动 → 候选（§6），按需另立项。

## 3. 关键设计决策

### 3.1 树派生而非改 schema

`EpubTocItem` 保持"扁平数组 + level"落盘不变；新增纯函数 `buildTocTree(items)` 在渲染层
按 level 游标把扁平列表派生成父子树。收益：零数据迁移、零主进程改动、备份/导出链路无感，
`level` 异常（跳级、乱序）时可兜底为"提升到不高于前一项 level+1"。

### 3.2 共享目录模块 `src/features/library/toc/`

纯函数模块 + 一个共用列表组件，两套面板消费：

- `tree.ts`：`buildTocTree`、折叠模型（collapsedIdSet、默认展开策略、展开到当前项的路径）
- `current.ts`：三格式"当前目录项"计算
  - EPUB：`relocated` href 已有 `currentTocItem`（`EpubReaderPage.tsx:732`）；升级为
    "先 href+fragment 精确匹配，再规范化 href 首个匹配"（同一文件多个目录锚点时现在会错配）
  - TXT/MD：锚点元素最近法——滚动时节流读取已缓存的锚点元素（`txt-chapter-N` /
    标题 slug），取视口顶之下最近者。不引入 IntersectionObserver，避免大文档观察器开销
- `filter.ts`：搜索过滤（大小写不敏感 includes）、命中时自动展开祖先路径、命中片段着色
- `TocList.tsx`：共用列表组件，props 为树/扁平项、`currentId`、`onJump`、折叠与搜索状态

### 3.3 UI 沿用现有 token 体系

基于 tokens.css / paper-copper 语义类做激活态与搜索框样式，不新增颜色 token；
目录文字为正文标题，一律默认无衬线。

## 4. 切片计划

### P0-A 目录交互基线（中，纯渲染层）

0. **TXT/MD 面板结构重排：目录与设置分离**（用户明确要求）。现状是目录列表与
   `ReaderSettingsPanel` 上下堆叠在同一 320px 右列（`ReaderPage.tsx:717-742`），改为
   对齐 EPUB 的成熟结构：**目录成为独立可收起的侧栏面板**（收起态同 EPUB 的 56px 竖条，
   含进度），**设置改为顶栏按钮唤起的独立抽屉**（复用 EPUB `EpubSettingsDrawer` 的交互
   形态，内容沿用现有 `ReaderSettingsPanel`）。这一刀先行，否则 P0-A 后续的折叠/搜索/
   跟随都要在"目录+设置"混合列里别扭落地，且为 §6 的侧栏同构（补书签/高亮 Tab）铺路。
1. **当前章节高亮 + 列表跟随**：当前项激活样式（`aria-current`）；当前项不在可视区时
   `scrollIntoView({ block: "nearest" })`（不加 smooth，避免动画打架）。
   用户正在浏览目录时暂停自动跟随（面板 hover/focus 挂起，离开后约 800ms 恢复）。
2. **层级折叠**：树派生 + 折叠按钮；目录项 > 200 时默认折叠到顶层（卷），自动展开当前项
   所在路径；提供"全部展开/全部收起"。
3. **搜索框**：目录 Tab 顶部输入框；过滤时切换为扁平命中列表（保留缩进），Esc 清空；
   树模式下命中则展开祖先。
4. EPUB 当前项匹配升级（fragment 优先，见 §3.2）。
5. 两套面板（EPUB 三 Tab 面板、TXT/MD 右侧目录）都接入 `TocList`。

验收：vitest 覆盖 tree/current/filter；`verify-reader-formats.mjs` 同步新结构断言；
`npm run build` + 全量单测 + probe-round7/8（阅读器几何）重跑；多级目录测试 EPUB、
千章 TXT、含重复标题测试 MD 手工走查（高亮跟随、折叠、搜索、跳转均正常）。

### P0-B 目录识别质量（中，纯渲染层）

1. **TXT 规则移植**：按 Android 09-03 规则集补齐 `splitTxtChapters`
   （感言章、最终话/间章/幕间/末章、廿/卅、强单位副标题负向断言、编号样式自动嗅探
   及其密度验证与递归防护）；以移植语料测试驱动（先跑旧实现暴露缺口再修）。
2. **MD 补全**：`markdownToc` 支持 Setext 标题；slugify 加唯一后缀
   （同名标题 `-2` 递增，渲染端 `renderMarkdownHtml` 的 id 注入同步同一实现）。

验收：移植语料 vitest（60+ 正例 + 12 反例 + 嗅探 6 例）全绿；既有
`splitTxtChapters.test.ts` 不回归；正常"第X章"书籍嗅探不被触发（标准书不受影响用例）。

### P1 TXT 目录修正（大，含持久化与 IPC）

1. **编辑模式**：目录 Tab 进入编辑态，逐章支持"合并到上一章 / 从正文选区处拆分 / 重命名"；
   正文选区浮动工具条增加"设为章节起点"入口。不做人肉拖拽排序。
2. **持久化**：修正结果以整份章节表落盘，主进程 library index 每书新增
   `text.tocOverrides: { version: 1, chapters: Array<{ title, startIndex }> }`；
   存在 overrides 时完全取代启发式结果。IPC 新增
   `reader:saveTxtTocOverrides` / `reader:clearTxtTocOverrides`，`openBook` 回读下发。
3. **导出链检查项（实施时必做）**：新增持久化字段须同步桌面导出/备份与 library index
   版本口径（对照既有 index schema 演进方式），防止重导/恢复丢目录修正。
4. 章节锚点、进度、摘录的 `chapterTitle` 均从同一章节表派生，修正后即全文生效。

验收：overrides 读写 IPC 单测；重开书籍修正保持；清空 overrides 回落启发式识别；
导出→导入往返后 overrides 不丢。

## 5. 数据结构与接口（P1 落地形态）

```ts
// src/types/library.ts（新增，向后兼容：字段可选）
interface TxtTocOverrides {
  version: 1;
  chapters: Array<{ title: string; startIndex: number }>; // startIndex 为全文字符偏移
}
// LibraryBook.text?.tocOverrides
```

渲染层 `splitTxtChapters` 输出与 overrides 统一为同一 `TxtChapter` 形状，
锚点 `txt-chapter-N` 与 `chapterTitle` 派生逻辑不变。

## 6. 候选增强（按需另立项，不在本期）

- **虚拟滚动**（`@tanstack/react-virtual`）：折叠默认策略上线后，若千章目录首渲/过滤
  实测仍卡再引入；先测量后动手。
- **目录已读标记**：对齐 Android P3.3（行尾已读点 + 计数），桌面需先落"按章已读"
  的进度记录，成本中等。
- **TXT/MD 侧栏同构**：把 EPUB 三 Tab 面板泛化为 `ReaderSidePanel`，TXT/MD 补
  书签/高亮管理 Tab（类型本就跨格式，缺口在 TXT 定位语义：字符偏移锚点）。
  P0-A 第 0 刀（设置抽屉化、目录独立侧栏）完成后，此项仅剩补 Tab 的增量工作。
- **目录跳转历史**（前进/后退）。

## 7. 测试与验收总口径

- 单测（vitest）：`toc/tree`、`toc/current`、`toc/filter`、`splitTxtChapters` 移植语料、
  MD Setext/slug 唯一、P1 的 overrides 读写。
- 守卫：`verify-reader-formats.mjs` 结构断言同步；涉及面板布局时重跑 probe-round7/8。
- 全量：`npm run build`、`npm test`（当前基线 720 项）。
- 手工走查用中性命名测试书（多级目录测试 EPUB / 千章 TXT / 重复标题测试 MD），
  不入库存档截图。

## 8. 风险与坑位

1. **自动跟随与用户滚动打架**：经典 TOC 坑。挂起-恢复策略 + `block:"nearest"` 无动画；
   自动跟随只滚目录列表、永不反向滚正文，天然无回环。
2. **epubjs `relocated` 50ms 防抖**触发的高频重渲：当前项计算结果不变时组件 memo 短路。
3. **大 TXT 当前章计算**：锚点元素全量在 DOM（整本渲染），只需缓存元素引用 +
   offsetTop 二分；字号/行距变更导致重排时重建缓存。超大文本若未来引入分块渲染，
   此实现需换百分比近似——本期整本渲染，不适用。
4. **verify 契约锁结构**：P0-A 改两处面板结构，`verify-reader-formats.mjs` 必须同切片更新，
   不能拖到集成期。
5. **P1 落库字段**：桌面导出/备份链路是否已有 per-book 扩展字段的先例，实施前先核对，
   避免只写 index 不写导出的半吊子。
6. 两端不共享代码：Android 语料增强移植是"重写规则 + 搬测试"，不是引依赖。

## 9. 实施记录（2026-09-03，P0-A + P0-B）

**已落地：**

- 新增共享模块 `src/features/library/toc/`：
  - `txt-chapters.ts`——Android TxtChapterDetector 的 TS 移植（8 组内置形态 + 强弱单位 +
    具名/平台章型 + 4 个嗅探候选 + 密度守卫），导出 `splitTxtChapters`（沿用原 TxtChapter 契约）；
  - `markdown-toc.ts`——放弃"行扫描 + 按数量配对 id"，改为 markdown-it token 流提取标题并
    直接注入 id（Setext 支持、代码块伪标题天然排除、slug 唯一序号）；
  - `tree.ts` / `current.ts` / `filter.ts` / `TocList.tsx`——树派生（层级跳级钳制）、
  EPUB 当前项 fragment 优先匹配、搜索过滤、共用目录列表（折叠/搜索/当前项高亮 + 挂起式跟随）。
- `ReaderPage.tsx` 重构：目录独立可收起侧栏（56px 收起态含进度），设置移入
  `ReaderSettingsDrawer`（新组件，与 EPUB 同构）；滚动当前章锚点跟踪（rAF 节流，
  打开即计算一次）；`EpubSidePanel.tsx` 目录 Tab 接入 `TocList`。
- `verify-reader-formats.mjs` 同步：内部实现断言改指新模块，新增 5 条结构守卫。

**有意偏差：**

- Android 具名表中的「结局/大結局」未引入——桌面既有回归把独立成行的「结局」判为正文；
  「第X章 大结局」形态已由强单位模式覆盖。
- 密度守卫与嗅探仅在正文 ≥3000 字时启用（Android 同参数），短文行为与旧实现完全一致。

**验证（2026-09-03 实跑）：** vitest 全量 766 passed / 4 skipped（新增 46 项：平台语料
60+ 正例 12 反例、嗅探 7 例、MD 目录 7 例、tree/current/filter 20+ 例）；
`npm run build` ✅；`verify-reader-formats` ✅；`probe-round7` / `probe-round8` /
`electron-smoke`（12 checks）✅。`verify:beta --scope=desktop` 在 main 上因
`verify:reposition` 等脚本已被 547801c 清理而报缺——**既有问题**，与本轮无关，属集成者修复。

**未做（后续）：** P1 TXT 目录修正（编辑模式 + tocOverrides 持久化 + 导出链核对）；
§6 候选项全部未动。
