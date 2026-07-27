# pager 纯逻辑层对抗性复核（2026-07-27）

**范围**：`android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/` 下的三个纯 JVM 文件
—— `TxtPageSource.kt`（偏移可逆的段落切片）、`PageSelection.kt`（段内→章内偏移换算、中文扩句、命中矩形）、
`PageStartsCodec.kt`（分页缓存编解码），以及经 `layoutChapter` 可观察到的跨层契约。

**方法**：主审逐行推演 + 5 个独立复核维度并行挖掘（扩句 / 切片 / 命中换算 / 缓存契约 / 全链路），
每条候选缺陷再交给独立复核人以「驳倒它」为立场重新推演，只保留证实项。
上一轮教训（自测 24 全绿、独立复核仍确认 12 处缺陷）是这次流程的直接动因。

**产出**：
- 回归护甲测试：`android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/PagerMathAdversarialTest.kt`
  - 绿色测试锁定已核实为正确的边界行为；
  - 已确认缺陷以 `@Ignore` 测试锁定**期望行为**，修复实现后移除注解即转正。
- 本文档：确认缺陷 + 已核实无误的攻击面清单。
- **实现文件一律未动**（`PagedTxtReaderHost` 等正被并行修改，实现层修复统一之后做）。

> 2026-07-27 接手收口：S1、S2、C1 已修复，三条原 `@Ignore` 测试已转为正常回归测试；
> keep-with-next 漏扣段距也一并修正。当前 `:app:testDebugUnitTest` 全绿。

---

## 一、已确认并修复的缺陷

### S1（中）`sentenceAround`：多字符终止符被劈成两半

`PageSelection.kt` 的扩句只认单字符终止（`e++` 一次），`TRAILING` 表也不含 `…`。

推演（`"他愣住了……原来如此。"`，按在「愣」上，`at=1`）：

- 反向扫描 `s=0`；正向扫描在第一个 `…`（下标 4）停下，`e++` → 5；
- `text[5]` 是第二个 `…`，不在 `TRAILING` → 循环结束；
- 选区 = `[0,5)` = `"他愣住了…"` —— **半个省略号**。

且孤儿 `…` 自成一「句」：按在下标 5 上得到 `[5,6)` = `"…"`。
中文网文里 `……` 是最高频终止符之一，`！？` `？！` `！！！` 同理。
影响：长按选句摘录进笔记的文本带半个省略号，高亮边界落在省略号中间。

锁定测试：`SentenceAroundAdversarialTest.double ellipsis stays whole`（@Ignore）。

**修复方向**（供实现层参考，本轮未改）：`e++` 吃掉第一个终止符后，
继续 `while (e < len && chapterText[e] in SENTENCE_ENDS && chapterText[e] != '\n') e++`
再走 `TRAILING`；反向扫描同理跳过连续终止符。

### S2（中）`sentenceAround`：无标点段末把换行符（CRLF 下含 `\r`）选进摘录

`\n` 在 `SENTENCE_ENDS` 里承担「段边界」职责，但正向扫描停在 `\n` 后同样执行
「含句末标点」的 `e++`，把 `\n` 本身并进选区；`\r` 不在表里，还会被当普通字符先行掠过。

推演（`"第一段无标点\n　　第二段。"`，按在「段」上，`at=2`）：

- 正向扫描停在 `\n`（下标 6），`e++` → 7；选区 = `[0,7)` = `"第一段无标点\n"`。
- CRLF 文本 `"第一段\r\n第二段。"`：`\r` 不在 `SENTENCE_ENDS`，扫描越过它停在 `\n`，
  `e++` 后选区 = `"第一段\r\n"` —— **CR+LF 都进了摘录**。

调用方 `PagedTxtReaderHost` 直接 `chapterText.substring(...)` 送 `onSelect` 进笔记/灵感，
摘录尾部带不可见换行；高亮矩形恰好不受影响（`\n` 不属于任何行），
所以污染是**静默的**，用户看不出选区里多了字符。

锁定测试：`SentenceAroundAdversarialTest.unpunctuated paragraph end excludes the line break`（@Ignore）。

**修复方向**：终止符是 `\n` 时不执行 `e++`（段边界不属于句子）；
顺手把 `\r` 加进 `SENTENCE_ENDS` 或在扫描时与 `\n` 同等对待，CRLF 即自然归位。

### C1（中，跨层）空白章：`pages=[]` 而 `pageStarts=[0]`，下游 UI 永久转圈

`ChapterPaginator.paginate` 对空段落列表返回 `ChapterLayout(emptyList(), intArrayOf(0), 0)` ——
`pageStarts` 声称有 1 页、`pages` 实际 0 页，唯一一处打破「`pageStarts[i]` = 第 i 页首字符偏移」的契约。

触发条件：`paragraphsOf` 返回空，即章文本为空或纯空白（TXT 里的装饰性分隔章、
连续章标题之间只有空行、书尾空章都会出现）。

链条：`TxtPagedController.loadChapter` → `pageIndex = pickPage(l).coerceIn(0, -1→0)` →
`currentPage = pages.getOrNull(0) = null` → `PagedTxtReaderHost` 对 `page == null`
显示 `CircularProgressIndicator` 且 `isLayingOut` 已复位 —— **该章永远显示加载圈，
翻页手势也随之失效**（手势绑定在内容层上）。这份 `pageStarts=[0]` 还会作为缓存落库。

根因在 `ChapterPaginator`（layout 包），不在三个待审文件里，但通过
`TxtPageSource.layoutChapter` 直接可观察，且消费方全在 pager 包 —— 修复时两层要一起定契约：
要么空章合成一个空页，要么 `pageStarts` 返回空数组并让下游把空章跳过。

锁定测试：`LayoutChapterContractAdversarialTest.whitespace only chapter keeps pages and pageStarts the same size`（@Ignore）。

### C2（低，跨层，暂为隐患）`charCount` 语义分裂：Σ段文本长 ≠ 章文本长

`ChapterLayout.charCount = paragraphs.sumOf { it.text.length }` —— 不含被切掉的缩进、
换行、空行；而 `pageStarts` 里存的是**含**这些字符的章内偏移。后果：

- 偏移可以大于 `charCount`（如 `"　　正文若干字。"`：`charCount=6`，末字符章内偏移 7）；
- `PageIndexStore.load` 用 `char_count == expectedCharCount` 做内容一致性校验，
  注释说 expected 是「当前章的实际字符数」。将来接 load 时若按注释语义传
  `chapterText.length`，**缓存将永不命中且无任何报错**（load 失败被纪律性吞掉），
  分页缓存整个功能静默失效。

当前 `load` 尚无调用方，故列为隐患而非现行缺陷。已用绿色测试把「charCount = Σ段文本长」
的现口径锁死（`charCount is the sum of paragraph texts not the chapter length`），
接 load 的人会被这条测试与本节文字拦住。

---

## 二、复核过（含对抗性独立复核）确认**无误**的攻击面

以下每条都有绿色护甲测试锁定，后续修 S1/S2/C1 时不得改坏：

| 攻击面 | 结论 | 护甲测试 |
| --- | --- | --- |
| 偏移可逆不变式（CRLF/BOM/NBSP/U+2028/代理对/ZWJ emoji/空章/单字符章/章尾无换行） | 成立：收缩切片保持 `chapterText[charOffset+k]==text[k]` | `offset reversibility survives hostile inputs` |
| CRLF 行尾 | `\r` 被尾部收缩吞掉，偏移仍可逆，标题判定不受影响 | `crlf line endings…`、`title matching…` |
| `chapterTextOf` 越界章边界（负 start、负 count、start+count 溢出 Int） | 全部钳制为空串，不崩 | `chapterTextOf clamps hostile chapter bounds` |
| 标题判定（首非空段、trim 语义、误判防线） | 正确；空行前缀不影响 first 判定 | `title matching survives crlf and surrounding blanks` |
| 段内→章内换算（第二段命中、跨段选区矩形） | `lineParaOffsets + 段内` 无 off-by-one | `offsetAt converts second paragraph hits…`、`rectsForRange spans paragraphs…` |
| 命中钳制（x/y 越界、行间空隙归上一行、簇边界四舍五入） | 与注释宣称一致 | `offsetAt clamps…`、`gap between lines…`、`offsetInLine rounds…` |
| 被切掉的空白区间（`\n`+缩进）求矩形 | 空列表，无负宽/崩溃 | `range covering only stripped whitespace…` |
| HEADING 行高（1.25×）在 lineTops 与命中矩形中的一致性 | `lineBoxH` 与 `ChapterPaginator.lineHeight` 同式 | `heading line rect uses heading height` |
| 代理对/ZWJ：行边界、命中偏移、扩句边界都不劈开代理对 | `clusterStarts` 实存表保证 | 三个 `…never split surrogate pairs` 测试 |
| 章首缩进使 `pageStarts[0]=2`（≠0） | 非缺陷，但下游不得假设为 0；`pageIndexFor(0)` 仍解析到第 0 页 | `indented chapter first page start is 2…` |
| 逃生分支（200 个 `…` 无断点）后的行偏移自洽 | 首尾相接、单调推进 | `escape branch text keeps line offsets consistent` |
| Codec 负值/极值往返、空 blob（合法空数组）vs 损坏长度（null）、补码字节形状 | 正确 | `PageStartsCodecAdversarialTest` 全部 |
| `pageIndexFor` 空数组 / 越界锚点 | 返回 0 / 钳到末页，不崩 | `pageIndexFor on empty pageStarts…`、`single character chapter…` |

## 三、观察项（不算缺陷，修 S1/S2 时可顺手权衡）

1. **按在闭合引号上**（`"他说：“好。”然后走了。"` 按 `”`）：选区变成 `"”然后走了。"` ——
   孤儿引号黏进下一句。现有调用路径里命中点来自渲染文本，落点在引号上完全可能。
   若修 S1 时顺手把「`at` 落在 TRAILING 字符上时先左移出引号区」加上，此象自消。
2. **`TRAILING` 表完备性**：缺 `》〉】〕`、半角 `)"'`。书名号在句末（`"……见《红楼梦》。"`）
   不受影响（`。` 在书名号后），只有引文式结尾才暴露；补表即可。
3. **半角终止符** `. ! ? ;` 不断句：中文小说夹英文句（`"他说 OK. 然后走了。"`）会整段选中。
   属产品取舍而非缺陷，记录备查。
4. **NBSP（U+00A0）**：`Character.isWhitespace` 为 false，纯 NBSP 行会生成一个"空白段落"
   参与排版。TXT 来源里罕见，可暂不理。

<!-- 工作流复核结果待补：若有新确认缺陷，编号顺延 S3+/C3+ -->
