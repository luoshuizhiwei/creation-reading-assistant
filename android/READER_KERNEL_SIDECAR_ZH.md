# 阅读器分页排版内核架构方案 SIDECAR-ZH

> 日期：2026-07-27
> 产出方式：4 路并行调研（legado 内核逐行核实 / 开源阅读器横向 / Compose 文本能力实测 / 中文排版规范）→ 3 份角度互异的独立架构提案 → 1 轮对抗性评审合成。
> 评审推翻了三案共有的 5 处错误，并订正了此前调研结论中的 3 条事实。

---

## 0. 评审结论速览

**推荐：以 [incremental] 为骨架，把 [quality] 的排版内核整体嫁接进来，测量/断点用 [pragmatic] 的双 seam，再补三处三案都错了的修正。**代号 SIDECAR-ZH。

三案的真实定位：

- **[quality]** 的排版内核是三案里唯一达到"能对标静读天下"水准的，但它的**交付形态注定烂尾**——6100 行、前置一次覆盖 3465 行 `ReaderScreen.kt` 的重构、且遗漏了会清库的 Room 迁移。
- **[incremental]** 的**工程形态是唯一正确的**（旁挂、可回退、不阻塞发布），而且贡献了三案里最强的两个局部结论（locator 无损代数恢复、搜索/渲染文本不一致的既有 bug）。但它的排版内核质量明显低于 [quality]，且均排配额和禁则兜底两处写错。
- **[pragmatic]** 在"该不该自研断行"上的论证方向对了但**论据选错了**（押在未验证的 LB19a 上），并且它的均排策略（"含拉丁整行不均排"）会让约五分之一的行右边缘参差——这比全部 ragged 更难看。

我在本文前先对题干给出的「5 条已核实事实」做三处订正，因为其中两条会误导架构决策。

---

## 1. 对「已核实事实」清单本身的三处订正

### 订正 1（事实 4）：inter-character 均排是 API **35**，不是 34

[compose-text] 用 `android-34/android.jar` 字节码实证：`JUSTIFICATION_MODE` 常量只暴露 `NONE`(0) 与 `INTER_WORD`(1)，`INTER_CHARACTER` 是 Android 15 的 `@FlaggedApi`。

这不改变"minSdk 24 下用不了"的结论，但**改变了时间表的性质**：从"targetSdk 到 34 就能白嫖"变成"minSdk 到 35 才能白嫖"，即 2029 年之后。所以自研均排不是过渡方案，是永久方案，值得一次做对。三案中只有 [pragmatic] §0.2 写对了这一条。

### 订正 2（事实 3）：Strictness 空转 ≠ 中文避头尾必须自研

`LineBreak.Strictness` 只是 CJK 小书写符号（ぁぃゃっ）与部分标点的松紧档，它**管不到** CL/OP/QU 类的基本禁则。基本禁则来自 UAX#14 的 LB13/LB19 族，minikin 自 Android 5 起就通过 ICU `BreakIterator::createLineInstance` 免费提供，API 24 起即有。[compose-text] 实测 3 万字纯中文行首禁则标点 0 次，且 `LOOSE` 档反而产生 51 个行首标点——反证默认档已经是对的。

**所以"自研断行"的正当理由不是 Strictness 空转。** 真正的理由是下面两条，三案都没把它讲清楚：

1. **均排要求逐簇 x 坐标**（由事实 4 决定），而算 x 必须先有 `advance[]`（由事实 2 决定必须走 `getTextWidths`）。既然 `advance[]` 的前缀和已经在手，贪心装行只是一次二分，**边际成本约 30 行**。反过来若用 `StaticLayout` 断行，你要么再跑一遍 `getTextWidths` 去对齐它的行边界（多花一遍钱），要么放弃均排。
2. **命中测试/选区/高亮同样要求逐簇 x 坐标**。Canvas 上没有 `SelectionContainer`（`SelectionRegistrar`/`Selectable` 是 internal，kotlinc 1.9.25 已证），`rectsForRange` 只能自己算。

一句话：**`clusterX[]` 是均排、选区、高亮、命中四件事的共同前置，一旦有了它，断行几乎是免费的。** 这才是应该写进设计文档的论证。

### 订正 3（事实 5）：TextMeasurer 的线程约束是**可解掉**的，不构成论据

androidx `TextMeasurer.kt` 第 79–80 行 `if (cacheSize > 0) TextLayoutCache(cacheSize)`——手动构造时传 `cacheSize = 0` 就不建缓存。此外 `ParagraphIntrinsics(...)` 与 `Paragraph(...)` 都是 public 且根本不碰 `TextLayoutCache`。

三案都没依赖 `TextMeasurer`，所以不受影响；但三案都把这条列为"必须绕开 Compose 文本栈"的论据之一，**这个论据是无效的**。绕开的真实理由仍是订正 2 的那两条。

### 关于 LB19a 引号问题：定性正确，但不能当地基

[quality] D1 与 [incremental] §3.2 都把它当成"必须自研禁则表的唯一硬理由"。技术推导本身站得住：Unicode 15.1 的 LB19a 四条规则

```
[^EastAsian] × QU ;  × QU ([^EastAsian] | eot)
[^EastAsian] QU × ;  (sot | [^EastAsian]) QU ×
```

全部带 `[^EastAsian]` 前提。中文正文里引号两侧都是东亚字符，四条都不适用，落回 LB31 `ALL ÷ / ÷ ALL` 默认允许断行。简体弯引号 `“”‘’` 在 UCD 里是 QU 类（而非台港 `「」` 的 CL/OP），所以确实会跑行首。

**但这与 [compose-text] 在 Android 15 真机上"行首禁则标点 0 次"的实测直接冲突**，可能解释是其统计集合未含 `”`，也可能是 minikin 的 `WordBreaker` 另有处理。**未解决。**

**处理原则：不把架构押在未验证的事实上。** 自研禁则表只是 ~240 行常量与纯函数，无论 LB19a 结论如何都无害（API 24–34 上它是冗余保险，API 35+ 上它是必需）。所以照做，但**论证强度不依赖它**。真机验证列入 §7 清单。

---

## 2. 三案共有的五个错误

这一节比逐案挑错更重要，因为这五条在任何一案落地后都会成为返工点。

### 共同错误 1：宽度调整与断行的**相位错误**（三案全中）

clreq 6.1.1 规定「先按排版风格做标点宽度调整，再做禁则」——因为挤压会改变换行位置。三案的实现：

- [quality]：`WidthAdjuster.adjust(run, cfg)` 对**整段**做一次，之后 `LineBreaker` 才断行。
- [incremental]：`applySqueeze(cl, p.text, cfg)` 同样对整段做一次，之后 `greedyFill`。
- [pragmatic]：把 P1 挤压推到 v1 之后躲开了，但 §3.3 明说"回退只往行首方向缩，不做挤进"——等于放弃 clreq 的核心。

**问题在于，两条最有价值的调整规则本身依赖行边界：**

| 规则 | 依赖 |
|---|---|
| 行末全角标点半宽（GB/T 15834 §5.1.10） | "行末"是断行的**输出** |
| 行首开始括号缩左半（clreq 6.3.2.2 / 6.2.1.1 对话段 1.5em） | "行首"是断行的**输出** |

[quality] 的判据写成 `squeezeLeft(i) = blankSide(i) < 0 && (isPunct(i-1) || i == lineStart)`，但在 `adjust` 阶段 `lineStart` 根本不存在，实现里只能退化成 `i == 0`（段首）。**结果是它自己列在 P1 清单里的"行首开始括号缩半"在其架构下做不出来。** 这是 [quality] 内部最实质的自相矛盾。

**正确的相位划分（本评审新增，SIDECAR-ZH 采用）：**

- **相位 A（位置无关，整段做一次）**：`·／` 固定半宽、连续标点挤压（只看相邻字符类）、中西间距 `gapAfter`。这些只依赖字符序列。
- **相位 B（位置相关，装行时做，且只会释放宽度、绝不占用宽度）**：行末标点半宽、行首开始括号缩左半。

算法：用相位 A 的宽度贪心装到 `end` → 对 `[lineStart, end)` 施加相位 B 得到 `usedB ≤ usedA` → 若释放出的宽度 ≥ `adv[end]`，**再吸一个簇进来**并重算相位 B → 循环。

收敛性：相位 B 释放总量 ≤ 1em（行首 0.5em + 行末 0.5em），而最窄的簇（拉丁 `i`）也有 ~0.25em，故循环上限 4 次。写死 `repeat(4)` 并加断言。**这就是 clreq 的"先挤进"，且不产生循环依赖。** 然后才做禁则回退（"后推出"）。

### 共同错误 2：均排的拉伸配额判据三案各错各的

| 提案 | 写法 | 问题 |
|---|---|---|
| [pragmatic] | `if (slack > 3f * emPx) return` + `if (containsLatinLetterOrDigit) return` | 只有 total 上限、无 per-gap 上限：5 簇行 slack=2.5em/4 gaps = **0.625em/gap**，远超 clreq 的 1/3em。更严重的是第二条：**中文小说含阿拉伯数字的行远不止 2%**（"第3章"、"2019年"、"100块"、时间、编号），真实量级 10–25%，等于五分之一的行随机地不齐 |
| [incremental] | `if (abs(d) <= cfg.firstLineIndentPx / 2)` | `firstLineIndentPx = 2em`，所以 per-gap 上限被写成 **1em（整整一个汉字宽）**。这是把 lightink 的"总 slack > 首行缩进宽则放弃"误抄成了"单间隙 slack" |
| [quality] | `maxStretchPerGapEm = 0.34f` + `slackOk: slack ≤ 2em` | **唯一写对的** |

**正确判据（两条并存）**：`d ≤ 1/3 em`（per-gap，clreq 6.3.3）**且** `slack ≤ 2em`（total，lightink 安全阀），拉伸间隙集合用 [quality] 的 `stretchable(i)` **逐间隙**判定（西文词内不拉、原子单元内不拉、连接号/间隔号前后不拉、末字后不拉），而不是 [pragmatic] 的整行开关。

### 共同错误 3：`letterSpacing` 还原法在 targetSdk 35 会失效，三案都当低风险

`drawDx = -d/2` 的推导本身正确（minikin 在每簇左右各加 `d/2`，故整体左移半格可让首簇墨水落回 `x`、末簇精确贴边）。

但 legado 源码里有**五处** `VANILLA_ICE_CREAM`(API 35) 兼容分支，说明 Android 15 改了这个语义，而该行为变更由 **targetSdkVersion 门控**。项目当前 `targetSdk = 34`（已核实 `app/build.gradle.kts:16`），但 Google Play 逐年强制推进 targetSdk，**升到 35 是排期内的确定事件，不是低概率风险**。届时全书右边缘整体偏移 `d/2`，且 `d` 逐行不同 → 参差。[pragmatic] 标"低"、[quality] 与 [incremental] 未提。

**解法（本评审新增，约 15 行）：运行期探针，把 `drawDx` 从常量改成探测值。**

```kotlin
/**
 * 探测「设了 letterSpacing 之后，首个字形墨水左边界相对绘制起点偏移了几个 d」。
 * API<35（或 targetSdk<35）返回 ~0.5f；Android 15 新语义返回 ~0f。
 * 顺手覆盖厂商 ROM 差异。引擎初始化时算一次，进 LayoutConfig 指纹。
 */
fun probeLetterSpacingBias(base: TextPaint): Float {
    val p = TextPaint(base).apply { textSize = 100f; letterSpacing = 0f }
    val r0 = android.graphics.Rect(); p.getTextBounds("中", 0, 1, r0)
    p.letterSpacing = 1.0f                       // d = 100px
    val r1 = android.graphics.Rect(); p.getTextBounds("中", 0, 1, r1)
    return ((r1.left - r0.left) / 100f).coerceIn(0f, 0.5f)
}
// 绘制：penX = clusterX[runStart] - bias * d
```

### 共同错误 4：都低估了 3465 行 `ReaderScreen.kt` 的接入摩擦，且只有一种应对可行

- [pragmatic] 的 **Step 0**（"ReaderViewModel 真正接管 + TTS 迁出"）是一次覆盖 3465 行的重构，本身 2–3 周，且不在其 2430 行估算内。**在 Step 0 完成前分页内核一行都发布不了。**
- [quality] 同样把它列为 P1 前置。
- [incremental] 用 `remember` 持有的普通 controller 绕开，是**唯一能让第一个可用版本独立发布的策略**。代价（旋屏丢内存缓存）已被 Room 页索引对冲，且旋屏本来就改指纹必须重排。

已核实：`ReaderViewModel` 确为 `@Suppress("unused")` 注入的完全死代码（`ReaderScreen.kt:995`），全项目无第二处引用。[incremental] 的判断是对的。

### 共同错误 5（三案全漏）：EPUB 大章走的是 `chunked(2000)` 假段落

这条我从代码里挖出来，三案都没提，但它会**直接毁掉新引擎在大章上的观感**。

`EpubParser.loadChapterBlocks`（`EpubParser.kt:143-149`）：

```kotlin
if (entry.size > REGEX_BLOCK_PARSER_LIMIT_BYTES) {      // 2 MB
    return loadChapterText(cachedEpubPath, entryPath, chapterDir)
        .chunked(LARGE_CHAPTER_BLOCK_CHARS)             // 2000
        .filter { it.isNotBlank() }
        .map { EpubBlock.Text(it) }
}
```

后果：
1. **段落边界完全丢失** → 排版层会给每个 2000 字"段"加首行缩进，正文里每 2000 字冒出一个假缩进；
2. `isHeading` 全 false，标题角色丢失，`keep-with-next` 无从谈起；
3. `loadChapterText`（流式状态机抽取）与 `extractBlocks`（正则抽取）是**两套独立实现**，产出文本不同——这同时也是 [incremental] 发现的搜索/渲染不一致 bug 的根源。

**任何 `ParagraphSource` 都必须先修掉这条降级路径**（让大章也走流式抽取但保留 `<p>`/`<br>` 边界并产出 `Paragraph` 流），否则新引擎在大章上排出来的东西比现在的 LazyColumn 更难看。**列入 P0 必做。**

---

## 3. 逐案独有问题

### 3.1 [pragmatic] 简排内核 v1

除共同错误 1/2/3/4 外：

1. **§3.5 底部两端对齐只摊到 slice 之间**：`slice.yTop += surplus * idx / (n-1)`。一页只有 1 个 slice（单段跨整页）时**完全不生效**，而这在中文小说里是常见页型。legado A8 与 [quality] 都是摊到**行**。这个"省一半代码"的简化把主用例漏了。
2. **§3.5 `y += paraGap` 无条件执行**：段落恰好排满页时仍加，下一页首行 `top = paraGap`，白白空一截。
3. **§6.2 locator 迁移用 snippet 搜索，"成功率 90%+"是猜测**——而 [incremental] 证明可以**无损代数恢复**（见 §4.1-A），根本不需要搜索。这是 [pragmatic] 最大的局部劣势。
4. **§6.3 chapter_metrics 首次打开全书扫描"2–4 s"严重低估**：按现状 `EpubChapter.blocks` getter 每次 `new ZipFile()`，300 章 = 300 次开关 ZIP + 300 次 XHTML 解析，5 MB EPUB 真机上更接近 10–25 s，且与首屏抢 IO。[incremental] 的"学习比率渐进收敛"完全避开这笔开销。
5. **§0.4 的决策分叉是假的**："接受 ragged right 可砍 500 行"——但 `clusterX[]` 是选区/高亮/命中的前置（订正 2），砍不掉。
6. **代码量 2430 低估约 40%**：`SelectionOverlay` 200 行做长按取词 + 双把手 + 工具条挂载不现实（真实 400–600）；`PageCanvas` 220 行含装饰 + 图片叠加也偏薄。加上 Step 0 前置重构，真实成本 4000+。

### 3.2 [quality] 墨衡 MoHeng

除共同错误 1（其中它中得最深）外：

1. **§3.3.2 状态机 undo 路径不完整**：PROBE 第二分支压缩了 `[lineStart, cand]` 的**所有** gap，随后 `SQUEEZE → RETRACT` 只写了"撤销半宽"，没说撤销 gap 压缩。RETRACT 后行变短，被压缩的 gap 本应还原。规格漏洞，实现时必然踩。
2. **`AtomicUnits` 与 OVERFLOW 语义冲突**：超长原子单元（比行宽还长的 URL/英文串）会让 `breakOk` 在整个 run 内恒假，RETRACT 耗尽后 OVERFLOW 令 `breakAt = cand + 1`——**该位置在原子单元内部**，与"原子"定义矛盾。自愈但语义破坏，须显式定义"原子单元长于一行时降级为字素簇可断"。
3. **`LocatorMigrator` 打开书时批量迁移全部高亮/笔记**：200 条散在 100 章 → 100 次章解压，抢 IO 拖慢首屏。应改为读时懒解析 + 结果缓存。
4. **完全没提 `fallbackToDestructiveMigration()`**——已核实 `DatabaseModule.kt:41` 确有该调用，`AppDatabase.kt:69` 为 `version = 4`。新增 `chapter_pagination` 表若不写 Migration 4→5 会**静默清库**，用户全部高亮/笔记/灵感/同步状态归零。**这是三案里唯一的数据安全级遗漏**（[pragmatic] 至少写了"加 Migration"，[incremental] 明确点名了这一行）。
5. **`paragraphSpacingEm = 0f` 默认**与现有设置 `paragraphSpacing = 1.15f`（`SettingsStore.kt:106`）冲突，用户会感觉"段间距设置失灵"。
6. **`EpubBlock.Image` 只有 `filePath` 没有宽高**——[quality] 自己在风险里点出了（值得肯定），但没算进代码量：需改 `EpubParser.extractImage` 加 `inJustDecodeBounds` 量尺寸、改 `EpubBlock.Image` 数据类、牵动现有渲染。
7. **4300 + 1800 测试 = 6100 行，且 P1 前置 ReaderViewModel 重构**。以本项目现状（单人、Gradle 因中文路径跑不了测试、`SECURITY_AUDIT.md` §4 已记录），这是最可能烂尾的一案。

### 3.3 [incremental] SIDECAR-PAGE

**先说它的三个原创贡献（全部采纳）：**

**A. v1 locator 的无损代数恢复。** 已逐行核实：`EpubChapter.estimatedTextLength = entry?.size`（`EpubParser.kt:108-111`，ZIP 解压后字节数）；`buildBookIndex` 用它累加（`ReaderScreen.kt:976-987`）；`computeBlockGlobalOffsets` 在章基址上按 `block.text.length + 1` 累加（`ReaderScreen.kt:700-712`）。

因为中文 XHTML 的 UTF-8 字节数（含标签）恒 ≥ 3× 可见字符数，而章内偏移 `co ≤ charCount + blockCount`，故恒有 `co < zipBytes(ci)`，于是

```
ci = offsets.indexOfLast { it <= G }    // 恒正确
co = G - offsets[ci]                    // 已经就是真实章内字符偏移
```

**迁移是一次代数拆分，不是搜索。** 这把 [pragmatic]/[quality] 的"高风险数据改造 + 90% 成功率"降级成"读时解码 + 结构性正确"。三案里最强的一个局部结论。

（边界补充：`entry` 为 null 时 `estimatedTextLength = 0`，`buildBookIndex` 里 `coerceAtLeast(1)`。这类章节内容为空、不会有 locator 指向，不影响正确性；但代码要处理 `offsets[ci] == offsets[ci+1]` 的退化情形。）

**B. 超集 JSON 双写。** 已核实 `parseLocatorOffset` 用正则 `"offset"\s*:\s*(\d+)`（`ReaderScreen.kt:668-672`），所以在同一份 JSON 里保留 `offset` 字段并新增 `v/ci/co/href`，旧版本读它照常工作。**零迁移窗口、零回滚风险。**

**C. 搜索/渲染文本不一致的既有 bug。** 已核实 `computeEpubSearch` 走 `EpubParser.loadChapterText`（`EpubParser.kt:170+`，流式状态机），渲染走 `extractBlocks`（`EpubParser.kt:315+`，正则），两者**是两套独立实现**，产出文本长度不同。当前搜索只跳章所以没暴露，做章内精确跳转必然跳偏。三案里只有它发现了。

**再说它的问题：**

1. **均排 per-gap 配额写错**（共同错误 2）。
2. **禁则回退失败时 `{ k = end; break }` 直接接受违例**，把 `。` 放到行首——这是用户最容易注意到的失败模式。必须改用 [quality] 的 OVERFLOW（前进一簇 + 负字距压回）。
3. **`NO_START` 含 `—…`（STRICT 档）+ 回退上限 4 簇**：`——` 跨行边界时强制回退，配合问题 2 的失败路径更容易触发违例。应把 `—…` 单列为可关的 STRICT 档。
4. **`·` 同时出现在 `NO_START` 与 `NO_END`**：`A·B` 式人名在任何位置都不可断，长译名列表会整体溢出。需要逃生规则（原子单元长于一行时降级）。
5. **`PageTurner` 的 `onSettle(d)` 后紧接 `dx.snapTo(0f)`**：`onSettle` 触发的是异步重组，`snapTo` 会在重组前生效 → **一帧旧页闪回**。必须两段式提交（先把新页塞进 window 并 `withFrameNanos {}` 等一帧，再 snap）。
6. **kill switch 用单个 `reader_paged_v2_active` 标志**：force-stop、系统低内存杀进程、ANR 都会留下脏标志，让从未崩过的用户被永久降级。应改为**连续崩溃计数 ≥ 2** 才禁用，且成功阅读 60 s 后清零。
7. **`PagedReaderController` 用 `remember`** 需显式 `DisposableEffect` 取消协程，否则旋屏泄漏排版 job。
8. **排版内核质量低于 [quality]**：没有 `blankSide` 语义（削左/削右分不清）、没有原子单元 id、`stretchable` 判据不完整、DrawRun 合并写在渲染层且判据是"gapExtra 相等"，含拉丁行会退化成大量小 run。
9. **"只改 6 处"过于乐观**：6 个改动点本身核实无误，但选区状态、TTS 句范围、高亮列表、搜索命中、跳转请求、工具条可见性都要接到新宿主，真实触点 15–25 处。方向仍然对（远比另两案侵入小）。
10. **代码量 2700 低估约 30%**（P3 选区 350 → 真实 500+）。

---

## 4. 评分

10 分制。「代码量」维度按"估算诚实度 + 是否与收益匹配"打分，不是越小越好。

| 维度 | [pragmatic] | [quality] | [incremental] |
|---|---|---|---|
| **排版质量** | 6 —— P0 齐、P1 推迟；含拉丁行不均排是硬伤 | **9** —— 唯一完整覆盖 P0+P1，`blankSide`/原子单元/`stretchable` 三处只有它做对 | 6 —— 有 P1 骨架但禁则兜底会违例、均排配额错 |
| **实现风险** | 5 —— Step 0 前置吞掉一半预算；locator 迁移靠搜索 | **3** —— 前置重构 + 6100 行 + 遗漏会清库的 Migration | **8** —— 三道安全网、逐阶段可关、只新建表 |
| **代码量诚实度** | 6 —— 2430 实际约 4000（低估 40%） | 5 —— 4300 数字诚实但规模本身与项目产能不匹配 | **7** —— 2700 实际约 3500（低估 30%） |
| **可增量落地** | 4 —— Step 0 完成前零可发布产出 | 3 —— P2 之前用户看不到任何东西 | **10** —— P0 单独发布即正收益且零回归 |
| **长期可维护** | 7 —— 双 seam 设计好，但四层压三层后扩展 P1 要返工 | **8** —— 分层最干净、零 Android 依赖的 core 包 | 7 —— doc 层抽象是长期资产；`remember` 控制器是技术债 |
| **加权综合** | 5.6 | 5.6 | **7.6** |

三案里 **[incremental] 的形态 + [quality] 的内核**是明确的最优组合。[pragmatic] 没有任何维度领先，但它的**两个 seam 接口设计（`TextRuler` / `BreakOracle`）是三案里最好的可测试性方案**，应当嫁接。

---

## 5. 最终推荐方案：SIDECAR-ZH

### 5.1 决策点逐条归属

| 决策点 | 选择 | 来源 | 理由 |
|---|---|---|---|
| 总体形态 | 旁挂第三渲染分支，legacy 两条分支永久保留 | incremental | 唯一不依赖 ReaderViewModel 重构的路径。注意 `readerMode` 默认已是 `"paged"`（假分页，一页=一章），全量替换的爆炸半径覆盖全部用户 |
| 文档层 | `ReaderDocument` + `DocumentCache`，legacy 分支也改成消费它 | incremental | 两条渲染路径共用同一份正文与偏移语义，否则切引擎时 locator 会漂 |
| 测量主路 | `TextPaint.getTextWidths()`，包在 `TextRuler` 接口后 | pragmatic 的 seam + 三案共识 | 事实 2；seam 让 core 包能在 ASCII 路径用 `java -cp … JUnitCore` 跑（绕开 `SECURITY_AUDIT.md` §4 记录的中文路径 Gradle 故障） |
| 断点来源 | `android.icu.text.BreakIterator.getLineInstance()` ∩ 自研禁则覆写，包在 `BreakOracle` 接口后 | pragmatic | 西文分词/数字串/URL/`——`/`……` 白送（2.1 ms/3 万字），自研只做**覆写**不做重造 |
| 禁则模型 | `CharClass` 12 类 + `blankEm`/`blankSide` 静态表 | **quality** | 需要"削左/削右"语义才能做 P1；[incremental] 的裸字符串表做不了 |
| 原子单元 | `atomicRunId: IntArray`，含"长于一行时降级为字素簇可断"逃生条款 | quality + 本评审补 | 修 [quality] 的 OVERFLOW 语义冲突 |
| 断行兜底 | **OVERFLOW + 负字距压回** | **quality** | [incremental] 的"接受禁则违例"是最坏选择 |
| 宽度调整相位 | **相位 A 整段 + 相位 B 逐行（只释放宽度，迭代 ≤4 次）** | **本评审新增** | 三案都有相位错误；这是唯一能同时满足 clreq "先挤进后推出"且无循环依赖的划分 |
| 均排配额 | per-gap ≤ 1/3 em **且** total ≤ 2 em；`stretchable(i)` 逐间隙 | quality | pragmatic 的"含拉丁整行不均排"和 incremental 的 per-gap = 1em 都是错的 |
| 绘制 | `DrawRun` 合并 + `nativeCanvas.drawText` + **`letterSpacingBias` 探针** | quality + 本评审新增 | 0.77 ms/页；探针解掉 targetSdk 35 的定时炸弹 |
| 常驻数据 | **只常驻 `pageStarts: IntArray`，页按需重排（~1 ms）** | **incremental** | 比 [quality] 的整章行对象常驻（0.5 MB/章 × 6）省一个数量级；重排确定性由"排版是纯函数 + `pageStarts[i]` 恒落行边界 + 段首可由 `charOffset == para.startOffset` 判定"保证 |
| 底部均摊 | 摊到**行**（legado A8） | quality | 修 [pragmatic] 单-slice 页失效的问题 |
| 页索引持久化 | Room `reader_page_index` + **`MIGRATION_4_5` 只做 `CREATE TABLE IF NOT EXISTS`** | incremental | 已核实 `fallbackToDestructiveMigration()` 在位、`version = 4`；只新建表则最坏情况仅丢缓存 |
| 排版指纹 | 全字段 + `fontProbe`（无自带字体，全系统回退）+ `letterSpacingBias` + `engineVersion` | 三案共识 + 本评审补一项 | 已核实 `app/src/main/assets` 与 `res/font` **均不存在** |
| locator | **超集 JSON 双写 + 代数恢复 + excerpt 文本指纹校验** | **incremental** | 已核实无损；[pragmatic]/[quality] 的搜索式迁移是不必要的降级 |
| 迁移执行时机 | 读时懒解析 + `reader_anchor_cache` 本地表（**不回写 `locator_json`**） | incremental | 避免 bump `revision/updated_at` 触发全设备同步风暴 |
| 全书进度 | 学习比率渐进收敛，`char_count` 随阅读回填 | incremental | 避开 [pragmatic] 的全书预扫（真实 10–25 s） |
| 选区 | 自绘长按取词 + 双把手；`scroll` 分支永久保留 `BasicTextField` 系统选区作逃生口 | pragmatic 的降级思路 + incremental 的引擎开关 | 体验退步必须有出口且写进设置文案 |
| 翻页 | 双 `graphicsLayer` 只改 transform；`curl` → `cover` 并改设置文案；**两段式提交** | 三案共识 + 本评审修正 | 修 [incremental] 的 `snapTo` 闪帧 |
| 状态承载 | `remember` 持有的 `PagedReaderController`（P2–P5），P6 后再评估迁 ViewModel | incremental | 不阻塞发布；旋屏丢缓存已被 Room 页索引对冲 |
| kill switch | **连续崩溃计数 ≥ 2**，成功阅读 60 s 清零 | 本评审修正 | 单标志会误伤被系统杀掉的用户 |

### 5.2 分层与文件清单（含诚实代码量）

```
feature/reader/doc/                       ← 格式中立，legacy 与新引擎共用
  ReaderDocument.kt                    80
  EpubDocument.kt                     160
  PlainTextDocument.kt                120
  TxtChapterDetector.kt               150
  DocumentCache.kt                    120   LRU 4 章 + 单飞
feature/reader/layout/                    ← ★ 零 android.* 依赖，纯 JVM 单测
  LayoutConfig.kt                     110   指纹含 fontProbe / letterSpacingBias / engineVersion
  TextRuler.kt                         70   接口 + FakeRuler(CJK=1em, ASCII=0.5em)
  BreakOracle.kt                       60   接口 + JdkBreakOracle(java.text.BreakIterator)
  CharClass.kt                        240   12 类 + blankEm/blankSide + 禁则三档
  AtomicUnits.kt                      110   ——/……/拉丁词/数字串 + 超长逃生
  WidthAdjuster.kt                    170   相位 A
  LineComposer.kt                     300   相位 B + 贪心装行 + 禁则回退 + OVERFLOW
  Justifier.kt                        190   slack 分配 + clusterX + DrawRun 合并
  ChapterPaginator.kt                 220   行→页 + keep-with-next + 底部均摊 + Flow
  PageModel.kt                        150
feature/reader/layout/android/
  PaintTextRuler.kt                   130   CJK 等宽短路 + per-job Paint 副本
  IcuBreakOracle.kt                    60   ThreadLocal<BreakIterator>
  LetterSpacingProbe.kt                40   ★ 本评审新增
feature/reader/render/
  PageCanvas.kt                       200
  PageHitTest.kt                      140   offsetAt / rectsForRange，纯函数可 JVM 测
  PageOverlay.kt                      120   高亮/选区/TTS/搜索四合一走 Path
  SelectionController.kt              480   ← 三案都低估了这个数
  PageTurner.kt                       220   两段式提交
  PagedReaderHost.kt                  240
feature/reader/PagedReaderController.kt 280
feature/reader/locator/
  LocatorCodec.kt                     110   超集 JSON
  LegacyOffsetCodec.kt                 90   冻结 buildBookIndex 公式 + 锁定单测
  AnchorResolver.kt                   180   代数恢复 + excerpt 校验 + 三级兜底
feature/reader/cache/
  PageIndexStore.kt                   140
  PageIndexEntity.kt / Dao             70
data/local/AppDatabase.kt             +40   MIGRATION_4_5（两条 CREATE TABLE）
ui/screen/ReaderScreen.kt        +180/-60   接线
feature/reader/EpubParser.kt         +120   ★ 大章 chunked 路径修复 + 图片 intrinsic 尺寸
                                    ─────
                                   ≈ 4700   （单测另计 ≈ 1200）
```

**结论：三案的 2430 / 4300 / 2700 都偏低。真实规模约 4700 + 1200 测试。** 但因为可以按 P0…P6 分七次发布，每次 400–900 行，实际风险远低于一次性 4700。

### 5.3 关键算法（已修正三案的错误处）

#### 段落装行主循环

```
fun layoutParagraph(p: Paragraph, cfg, ruler: TextRuler, oracle: BreakOracle): List<Line>

  // ① 一次测量，一次断点（唯一两次全段遍历）
  cl   = Clusterizer.of(p.text, ruler)             // advance[] / startInText[] / klass[]
  atom = AtomicUnits.compute(p.text, cl.klass)
  brk  = oracle.breaks(p.text)                     // ICU 合法断点位图

  // ② 相位 A：位置无关的宽度调整（整段一次）
  adjA = WidthAdjuster.phaseA(cl, cfg)             // ·／半宽 / 连续标点挤压 / 中西 gap
  pre  = prefixSum(adjA)                           // Float 前缀和

  lineStart = 0; first = true; lines = []
  while (lineStart < cl.count) {
      indent = if (first && p.role == BODY) cfg.firstLineIndentPx else 0f
      avail  = cfg.contentWidthPx - indent

      // ③ 贪心装行（二分）
      var end = upperBound(pre, pre[lineStart] + avail).coerceAtLeast(lineStart + 1)

      // ④ 相位 B：位置相关调整，只释放宽度，最多迭代 4 次「挤进」
      var adjB = phaseB(adjA, lineStart, end, cfg)         // 行末标点半宽 + 行首开始括号缩左半
      repeat(4) {
          val freed = avail - width(adjB, lineStart, end)
          if (end >= cl.count || freed < adjA[end]) return@repeat
          end++; adjB = phaseB(adjA, lineStart, end, cfg)
      }

      // ⑤ 禁则：后推出（回退），失败则 OVERFLOW（前进 + 负字距）
      if (end < cl.count) {
          var k = end
          val floor = lineStart + cfg.minLineClusters
          while (k > floor && !breakOk(k, cl, atom, brk, cfg)) k--
          end = if (k > floor && slackOk(lineStart, k, avail, cfg)) k
                else advanceToOverflow(end, cl, atom)      // ★ 不接受禁则违例
      }

      lines += Justifier.build(cl, adjB, lineStart, end, indent, avail, first,
                               isTail = (end == cl.count), cfg)
      lineStart = skipLeadingSpaces(end); first = false
  }
```

`breakOk(k)` = `k > lineStart` ∧ `!isNoStart(klass[k])` ∧ `!isNoEnd(klass[k-1])` ∧ `atom[k] != atom[k-1]`（含偶数边界例外）∧ `brk[startInText[k]]`。

`advanceToOverflow` = 跳到当前原子单元之后的第一个合法位置；若该原子单元本身长于一行，则降级为"任意字素簇边界可断"并强放。**这两条一起消灭所有死循环路径。**

#### 均排（`Justifier.build` 核心）

```
n     = end - lineStart
slack = avail - width(adjB, lineStart, end)
noStretch = isTail || role != BODY || !cfg.justify || n < cfg.minJustifyClusters   // 默认 10

J = (lineStart until end-1).filter { stretchable(it) }        // 逐间隙，不是整行开关
d = when {
    noStretch || J.isEmpty()        -> 0f
    slack > 0 -> (slack / J.size)
                   .coerceAtMost(cfg.maxStretchPerGapEm * emPx)      // ≤ 1/3 em
                   .let { if (slack > cfg.maxSlackEm * emPx) 0f else it }   // total ≤ 2 em
    else      -> (slack / J.size).coerceAtLeast(cfg.minCompressPerGapEm * emPx)  // ≥ -0.06 em
}

x = indent
for (i in lineStart until end) {
    clusterX[i - lineStart] = x + drawShift[i]                 // drawShift < 0 = 削左半补偿
    x += adjB[i] + gap[i] + (if (i in J) d else 0f)
}
clusterX[n] = x                                                // 末字精确落在 avail
```

`stretchable(i)`：`!(isLatinLike(klass[i]) && isLatinLike(klass[i+1]))` ∧ `atom[i] != atom[i+1]` ∧ 两侧均非 `CONNECTOR`/`PUNCT_MID` ∧ `i != end-1`。

#### DrawRun 合并 + 绘制

```
spacing[i] = clusterX[i+1] - clusterX[i] - adjB[i]
可合并：spacing 相等（±0.01px）∧ drawShift[i] == 0 ∧ klass[i] ∉ {LATIN, DIGIT}
penX   = clusterX[runStart] - cfg.letterSpacingBias * spacing        // ★ 探针值，不是常量 0.5
含拉丁/数字的簇：逐簇独立 run（避开连字/kerning 漂移）
```

典型纯中文行 → 1 个 run；带行末半宽标点 → 2 个；带行首开始括号 → 3 个。

### 5.4 缓存与失效

| 层 | 内容 | 容量 | 生命周期 |
|---|---|---|---|
| L0 章正文 | `ChapterContent` | 4 章 | `DocumentCache` 内存，单飞 |
| L1 页索引 | `pageStarts: IntArray` + `charCount` | 一章 60 页 ≈ 300 B | 内存 LRU 8 章 **+ Room 持久化** |
| L2 渲染页 | `RenderedPage`（含 `Line`/`clusterX`） | 5 页窗口 | 纯内存，用完即弃，按需重排 ~1 ms |

**绝不常驻整章行对象。** L1 落库的价值：冷启动不重排就能算出"第 37 / 62 页"与百分比。

失效唯一入口：

```kotlin
BoxWithConstraints {
    val cfg = rememberLayoutConfig(constraints, readerSettings, density, fontProbe, lsBias)
    LaunchedEffect(cfg.fingerprint) { controller.reconfigure(cfg) }
}
```

`reconfigure` 行为：**`charOffset` 不动**，取消旧协程，从当前章重排，完成后 `pageIndexFor(charOffset)` 回到同一句话。每本书最多留 2 个指纹的页索引。

### 5.5 Room 迁移（数据安全红线）

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS reader_page_index (
            content_key TEXT NOT NULL, chapter_index INTEGER NOT NULL,
            fingerprint INTEGER NOT NULL, page_starts BLOB NOT NULL,
            char_count INTEGER NOT NULL, created_at INTEGER NOT NULL,
            PRIMARY KEY(content_key, chapter_index, fingerprint))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS reader_anchor_cache (
            kind TEXT NOT NULL, entity_id TEXT NOT NULL, content_key TEXT NOT NULL,
            chapter_index INTEGER NOT NULL, char_offset INTEGER NOT NULL,
            confidence INTEGER NOT NULL, resolved_at INTEGER NOT NULL,
            PRIMARY KEY(kind, entity_id, content_key))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS reader_book_prefs (
            book_id TEXT NOT NULL PRIMARY KEY, engine TEXT,
            font_size_override REAL, updated_at INTEGER NOT NULL)""")
    }
}
```

**只做 `CREATE TABLE IF NOT EXISTS`，绝不 ALTER 任何现有表。** 合入前必须用一份真实 v4 库文件做升级验证——`DatabaseModule.kt:41` 的 `fallbackToDestructiveMigration()` 会把任何迁移失误变成静默清库。

---

## 6. 分阶段实施路径

每阶段独立可发布，均有可验收产出。

### P0 · 修既有欠账（约 700 行，零回归风险，单独发布即正收益）

1. `doc/` 全套（`ReaderDocument` / `EpubDocument` / `PlainTextDocument` / `TxtChapterDetector` / `DocumentCache`）
2. **修 `EpubParser` 大章降级路径**：`entry.size > 2 MB` 时也走流式抽取并保留 `<p>`/`<br>` 段落边界，产出真正的 `Paragraph` 流（共同错误 5）
3. **`EpubBlock.Image` 补 intrinsic 宽高**（`inJustDecodeBounds`），排版层从此零 IO
4. **搜索改走 `ReaderDocument`**，与渲染共用同一份文本（[incremental] 发现的既有 bug）
5. legacy 两条分支改成消费 `ReaderDocument`
6. `LegacyOffsetCodec` 抽出并加锁定单测（冻结 `buildBookIndex` 公式）

**验收**：TXT 有目录；搜索不再每次全书解压且命中偏移与渲染同源；2 MB+ 章节不再出现每 2000 字一个假缩进；`LegacyOffsetCodec` 与现行 `buildBookIndex` 逐值等价的单测通过；无任何 UI 变化。

### P1 · 排版内核（约 1200 行 + 900 行单测，不接 UI）

`layout/` 全套（`CharClass` / `AtomicUnits` / `WidthAdjuster` 相位 A / `LineComposer` 相位 B+装行+禁则+OVERFLOW / `Justifier` / `ChapterPaginator`），配 `FakeRuler` + `JdkBreakOracle`。

**验收（全部纯 JVM，ASCII 路径 `java -cp … JUnitCore`）**：

1. 禁则全表逐字符（含 `”’·`）不出现在行首/行末
2. `"。".repeat(400)` 不死循环、行数有限、无禁则违例
3. 超长原子单元（120 字符无空格 URL）走降级分支、不死循环
4. 代理对 `𠮷` / 组合字 / ZWJ emoji 家族不被拆簇
5. 相位 B 迭代收敛：断言 `repeat(4)` 内必然稳定
6. 挤压三断言：`。”` = 1.5em、`“‘` = 1.5em、`）（` = 1.0em
7. 段首 `「你好」` 实测缩进 = 1.5em
8. 均排：非末行 `clusterX[n] == avail`（±0.01px）；`d ≤ 1/3 em` 且 `slack ≤ 2em`
9. 首行缩进：`lines[0].startX == 2em`、`lines[1].startX == 0`（跨页续段不重复缩进）
10. `pageIndexFor(pageStarts[k]) == k`；改字号后 `charOffset` 不变
11. 按 `pageStarts[i]` 重排单页，结果与整章排版的第 i 页逐字段相等（**这是"只存 IntArray"策略的正确性依据，必须单测**）

### P2 · TXT 只读分页（约 800 行，隐藏开关）

`PaintTextRuler` / `IcuBreakOracle` / `LetterSpacingProbe` / `PageCanvas` / `PageHitTest` / `PagedReaderHost` / `PagedReaderController` / `MIGRATION_4_5` / `PageIndexStore`。新增设置 `pagerEngineMode`，**默认 `off`**。

**验收**：真机能看到正确排版的 TXT 页；点击翻页可用；改字号后停在同一句；插桩测试断言 `clusterX` 与 Skia 实绘的像素偏差 < 0.5 px；用 v4 库文件真机升级验证不清库；开关关掉后行为与今天逐字节一致。

### P3 · 选区与高亮（约 600 行）

`SelectionController` + `PageOverlay`。复用现有 `SelectionToolbar` 与高亮/笔记/AI/灵感/复制五个动作（它们只要 `selectedText` 与偏移）。

**验收**：长按取词、双把手拖拽、跨行选区、四类高亮（选区/笔记/搜索/TTS）走同一条 `rectsForRange`；设置文案明写"精细选中请切回滚动模式"。

### P4 · EPUB + 翻页动效（约 700 行）

图片整块不跨页 + 绝对定位 `AsyncImage` 叠加、标题 `keep-with-next`、`PageTurner` 四档（两段式提交）、`curl` → `cover` 并改设置文案、`autoEligible` 准入。

**验收**：EPUB 分页可用；`pageTurnEffect` 四档真正生效且翻页 60 fps 无掉帧、无一帧旧页闪回；额外内存增量 ≈ 0（对比 `AnimatedContent` 与截图方案）。

### P5 · locator 与进度（约 500 行）

`LocatorCodec` 超集双写、`AnchorResolver` 代数恢复 + excerpt 校验、`reader_anchor_cache`、学习比率进度收敛。

**验收**：旧版 App 读新写的 `locator_json` 仍跳到同一位置（正则兼容）；EPUB 历史高亮/笔记定位准确率埋点 > 95%；降级条目在笔记列表带"位置近似"标签且可手动重锚；进度百分比不再是 ZIP 字节估算。

### P6 · P1 排版规则收尾 + 灰度默认（约 300 行）

`pagerEngineMode` 默认切 `auto`；崩溃计数 kill switch；行长上限 42 em 钳制；`scroll` 模式统一到同一引擎（可选）。

**验收**：连续两次崩溃才禁用且成功阅读 60 s 清零；平板横屏行长不超过 42 字；`paragraphSpacing` / `chineseTypography` / `pageMargin` 三个此前装饰性的设置项全部有真实语义。

---

## 7. 落地前必须真机验证的五项

1. **LB19a 引号（API 35 设备）**：排一段 `他说“你好”，我点头。` 于多个行宽，检查 `”` 与 `。` 是否出现在行首。结论不影响架构（禁则表照写），但决定这条论据在文档里的措辞强度。
2. **`letterSpacingBias` 探针**：在 targetSdk 34 与 35 两种编译产物上各跑一次，确认返回 0.5f / 0f，并确认绘制后回读的行墨水右边界与 `clusterX[n]` 一致。
3. **黄金标准对拍**：纯 CJK 无引号段落，我们的 `lineStart[]` 与 `StaticLayout.getLineStart(i)` 逐值相等（[compose-text] 已实测行数 1555 == 1555，这里要对到断点）。
4. **大章路径**：找一本含 > 2 MB 单章的 EPUB，验证 P0 修复后段落边界与标题角色正确。
5. **`MIGRATION_4_5`**：拿一份真实 v4 `.db` 文件做升级，确认高亮/笔记/灵感/同步状态一条不丢。

基准工具链沿用 `app_process` + `Looper.prepareMainLooper()` + `ActivityThread.systemMain()` + `Typeface.loadPreinstalledSystemFontMap()` 三步引导，免安装跑（绕开 `SECURITY_AUDIT.md` §4 记录的 Gradle 故障）。

---

## 8. 明确不做清单

| 项 | 理由 |
|---|---|
| `TextAlign.Justify` | 事实 4：inter-word，中文零效果 |
| `TextIndent` / `ParagraphStyle(textIndent)` | 被翻译成 `LeadingMarginSpan.Standard` 且 `setSpan(0, length)` 作用整段，跨页续段会重复缩进 |
| 往正文插 `U+3000` 做缩进 | 会同时破坏 locator、搜索、TTS 分句三条链路；我们有 `startX` 元数据槽位，legado 没有 |
| `TextMeasurer` / `rememberTextMeasurer` 做逐字测量 | 事实 5；即便可用 `cacheSize=0` 解掉，走 `getTextWidths` 仍更快更可控 |
| `TextPaint.breakText` | 3 万字 17 秒，O(n²) |
| `SelectionContainer` 用于 Canvas | `SelectionRegistrar` 是 internal，kotlinc 1.9.25 已证外部不可用 |
| `AnimatedContent` / 截图缓存做翻页 | 一页 1000×1900 ARGB_8888 = 7.25 MB，比整章布局贵 12 倍 |
| 真·卷曲 curl | 需 mesh shader；`RenderEffect` 仅 API 31+。映射到 `cover` 并改设置文案 |
| 预分页全书 | 只排当前章 ±1；FBReaderJ 与 Book's Story 的教训 |
| 占位汉字 `袮` / `꧁` | 用 `U+FFFC`（Unicode 官方占位符） |
| 排版内核里做 IO / DB / DI 查找 | `layout/` 包禁止 `import android.*`（`TextRuler`/`BreakOracle` 的 android 实现在 `layout/android/` 子包），加 CI 检查 |
| `exceed()` 的整数除法、`justifyHtmlLine` 的 `currentX = col.start`、`TextMeasure` 的 `ceil()` | legado 的三个确认 bug |
| 直排 / 注音 ruby / 着重号 / 行尾悬挂 | clreq 6.1.3 原文指出绝大多数中文出版物不做悬挂；其余与简体横排小说无关 |

---

## 9. 合规交代

SIDECAR-ZH 的唯一外部依赖是 Android 平台 API（`android.graphics.*`、`android.icu.text.*`、`androidx.compose.*`），不新增任何三方库，不含任何 GPL 代码。

`CharClass` 的字符表来自 **clreq（W3C，CC-BY）** 与 **GB/T 15834—2011**（国家标准，规范文本可自由实现），与 legado `ZhLayout` 的表不同且更全（legado 字面量有重复、缺 `』〔〈〉｛｝`、缺省略号/破折号规则）。`drawDx` 是 minikin `letterSpacing` 语义的数学推论，且本方案改用运行期探针而非硬编码常量。

配套必做：改写 `ProfileScreen.kt:1747` 的「Android 端按 GPL-3.0 发布」文案——原生端不含 GPL 代码，该声明应整体撤除或改写，并补上 LICENSE 文件。
