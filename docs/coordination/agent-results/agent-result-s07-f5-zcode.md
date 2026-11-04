# S07-F5 最小修复 — Zcode 执行报告

> 执行 Agent：Zcode　|　实际模型：`deepseek v4.1Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定比较基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收工 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 真机验收输入：`docs/qa/2026-09-21-s07-qoder-device-acceptance.md`（D-1 / D-3，均为 P2）
> 报告路径：`docs/coordination/agent-results/agent-result-s07-f5-zcode.md`
> 状态：**等待 Codex 主任务复核**（不自宣布 S07 完成，不领取下一片）

---

## 1. 约束遵守

| 约束 | 结果 |
|---|---|
| 不 reset / checkout / clean / stash | 未执行；`git stash list` = 0 |
| 不 stage / commit / push | 未执行；`git diff --cached --name-only` = 0 |
| 不全仓格式化 | 未执行 |
| 不覆盖 / 整理 / 删除他人改动 | 只写 2 个产品文件 + 3 个新测试；工作区其余改动原样保留 |
| 不修改 S08–S20 | 未触碰 |
| 不修复 D-2（设置作用域） | 未触碰 `ReaderOverride.kt` / `SettingsStore.kt` / 设置页 |
| 不优化 D-7（性能） | 未做任何性能改动（无对照数据） |
| 不操作真机 / MuMu | 未执行任何 `adb` 或设备命令 |
| 不修改 Codex / Claude 记忆库 | 未访问 |
| 未越界 → 无需 `SEAM REQUEST` | 两个修复都落在授权文件内 |

---

## 2. D-1 复现与证据还原

### 2.1 真机现象（截图复核）

| 截图 | 观察 |
|---|---|
| `51-md-image-pseudo-selection.png` | 轻点图片块（屏幕上只渲染出 alt 文字 `示意图`）→ 底部工具栏出现，摘录条显示 **`示意图`** |
| `52-md-image-highlight.png` | 从该工具栏高亮→红 → 提示 `已高亮`，红底落在 `示意图` 上 |
| `53-md-highlights-after-reopen.png` | 退出重开并滚到第二节：**红底 `示意图` 仍在**，即伪记录已落库并回显 |

### 2.2 用真实 parser 还原（本机可复现）

用真机夹具同形源码 `"第一节\n\n![示意图](t-img-s07.png)\n\n正文段落。\n"` 跑真实
`MarkdownParser.parse` + `MarkdownRenderModel.flatten`，实测输出：

```
canonicalText = [第一节\n示意图\n正文段落。]

kind=PARAGRAPH id=b0 canonicalRange=0..2  text=[第一节]    spans=[Plain(...)]
kind=PARAGRAPH id=b1 canonicalRange=4..6  text=[示意图]    spans=[Image(source=t-img-s07.png, alt=示意图, start=0, end=3)]
kind=PARAGRAPH id=b2 canonicalRange=8..12 text=[正文段落。] spans=[Plain(...)]
```

混合段落 `"前面文字 ![图](a.png) 后面文字。\n"`：

```
canonicalText = [前面文字 图 后面文字。]
spans = [Plain(start=0,end=5), Image(alt=图, start=5,end=6), Plain(start=6,end=12)]
```

**模型事实**：图片**没有独立 `MarkdownRenderKind`**，它是
`MarkdownRenderSpan.Image`，嵌在 `Paragraph`（或 `Heading`/`ListItem`/`TaskItem`）
的行内 spans 里；parser 把 **alt 文本写进了 `canonicalText`**
（`MarkdownDocument.kt` 的 `is MdInline.Image -> alt`）。

### 2.3 根因（单一假设，已用代码路径验证）

```
parser: MdInline.Image -> alt 写进 canonicalText
   └─ ReaderMarkdownRender.kt:43 unitText() = canonicalText.substring(unit.canonicalRange)
        └─ 纯图片段落的切片就是 "示意图"
   └─ MarkdownScrollHighlight.kt:245 markdownUnitIsBlockSelectable() 只看 unit.kind
        └─ PARAGRAPH 一律 true（无法区分「纯图片段落」与「普通文本段落」）
   └─ ReaderMarkdownRender.kt:276 selectable=true → Modifier.clickable 挂上
        └─ 轻点 → markdownBlockSelectionArgs(text="示意图", range=4..6)
             └─ onSelect("示意图", -1, 4, 7) → 保存高亮 → 落库 → 重开回显
```

**根因一句话**：图片在模型里不是独立 kind，alt 又是 canonicalText 的一部分，
而选择资格只按 `unit.kind` 判断 —— 纯图片段落被当成了普通文本段落。

### 2.4 为什么原测试没抓住

`MarkdownUnitSourceRangeTest` 已有「非文本单元不得生成伪选区」用例，但它覆盖的是
**分隔线**与**整表**（两者都是独立 `MarkdownRenderKind`，因此 `when (unit.kind)`
能直接拒绝）。图片**没有独立 kind**，这条测试的判据结构上够不到它；而
`MarkdownPersistentHighlightProjectionTest` 只验证「已保存区间 → 单元」的正向投影，
从不验证「某个单元是否**应该**成为落点」。两个测试类的盲区正好叠加成 D-1。

---

## 3. D-1 最小修复

**修复位置：selection eligibility policy（不是保存层补丁、不是 UI 隐藏工具栏）。**

`MarkdownScrollHighlight.kt` 的 `markdownUnitIsBlockSelectable` 增加图片排除：

```kotlin
MarkdownRenderKind.CODE_BLOCK -> !markdownUnitContainsImage(unit) &&
    unit.canonicalRange.last >= unit.canonicalRange.first
```

新增 `markdownUnitContainsImage(unit)`：只读 parser 产出的 span **类型**，
覆盖 `Heading` / `Paragraph` / `ListItem` / `TaskItem` 的 spans 与表格全部单元格的 spans。

**为什么是「整个单元不可选」而不是「排除图片那段区间」**：整块选择提交的是一个
**连续** canonical 区间（`rangeStart..sourceEnd`），而 alt 位于该区间**内部**。要只选
真实正文就得提交两个不连续区间，或靠文本搜索/alt 形状定位正文边界 —— 两者都会把一个
可伪造的猜测写进持久化坐标。因此取安全契约：**含图片的单元整体不可选**。

**没有用文本搜索、没有看 alt 内容**（alt 是可任意伪造的用户文本；测试里专门有一条
反例：alt 恰好等于段落正文时仍不得放行）。

**修复自动覆盖的两处**（无需额外改动，已核对）：
1. **渲染层**：`ReaderMarkdownRender.kt:276` 的 `selectable` 与 `blockCanonicalRange`
   本来就以 `markdownUnitIsBlockSelectable` 为准 → 纯图片段落不再挂 `clickable`。
2. **持久高亮投影**：`MarkdownScrollHighlight.kt` 的 `else ->` 分支开头即
   `if (!markdownUnitIsBlockSelectable(unit)) continue` → **库里已存在的那条伪记录**
   也不会再被投影回图片块（真机 D-1 的第二半）。

**修复后纯图片块的四个后果**（全部有测试）：不产生 `selectedText`、不产生
`rangeStart`/`sourceEnd`、不显示工具栏（无 clickable）、不可能保存高亮/批注/灵感 locator
（`markdownBlockSelectionArgs` 返回 null）。

**未扩大范围**：图片相对路径渲染问题（Qoder 标 NOT TESTED）未触碰；
`ReaderMarkdownRender.kt` 最终**哈希与改动前完全一致**（见 §7）。

---

## 4. D-3 复现与证据还原

### 4.1 真机现象（截图复核）

| 截图 | 观察 |
|---|---|
| `18-scroll-ch2-with-old-selection.png` | 视图已在第2章正文，底部工具栏仍显示第1章摘录 `码头的绳索被海水泡得发白` |
| `19-scroll-ch6-disposed-unit-selection.png` | 视图已在第6章（无障碍树只剩第6章节点），工具栏与第1章摘录**仍常驻**，且带 `替换` 槽 |
| `20-scroll-ch1-highlight-placed.png` | 从该工具栏保存高亮 → 落点仍在第1章原句（坐标未错，但 UI 在操作一个用户看不见的选区） |

### 4.2 失效边界逐项排查

| 候选边界 | 判定 | 依据 |
|---|---|---|
| 单元只是离开 viewport | **← 这就是真实失效边界** | LazyColumn 预取/缓存窗口内继续组合，`onDispose` 不跑 |
| 单元仍被 LazyColumn 预组合/缓存 | **成立，且是上一条的机制** | `unitContentLoader` 另有 5 条 LRU 缓存（`ScrollUnitContentLoader`），刚滚出的项保持 Loaded |
| 单元真正 dispose | 不是本缺陷的触发路径 | 真机 102s 后第1章确实 dispose（§12 G 组），但 D-3 场景下它**没被触发** |
| ReadingUnit 身份替换 | 无关 | `itemsIndexed(key = unit.unitIndex)` 身份稳定 |
| 当前选区 owner 判据失配 | **不是根因** | `readingUnitOwnsSelection` 半开区间判据正确，`ReadingUnitSelectionOwnershipTest` 10 项覆盖 |
| 回调闭包 / VM→本地状态时序 | **不是根因** | 已用 `rememberUpdatedState` 读最新归属与回调（F2.3/F2.4 已修） |

### 4.3 根因（单一假设）

**当前唯一的选区失效机制是 `DisposableEffect.onDispose`，它回答的是「离开组合」，
不是「离开可见范围」。** 两者不等价：LazyColumn 会把刚滚出视口的项保留在预取/缓存
窗口内继续组合，因此「屏幕上没有、无障碍树没有」**并不蕴含**「已 dispose」——
`onDispose` 根本不跑，选区就一直挂在一个用户看不见的单元上。

代码侧佐证：全仓搜索 `plainListState` 的用法，**没有任何一处读
`layoutInfo.visibleItemsInfo` 来做选区失效判定**（只有 `ReaderProgressComputations`
用它算滚动位置）。即宿主层从未观察过「哪些单元可见」。

### 4.4 为什么原测试没抓住

`ReadingUnitSelectionOwnershipTest`（10 项）测的是 `readingUnitOwnsSelection` /
`readingUnitShouldClearOnDispose` 两个**纯谓词**；`ReadingUnitDisposeWiringGuardTest`
（8 项）测的是 `onDispose` **块的形状**。两者都正确，但它们的前提
——「owner 离开可见范围 ⇒ 该单元会 dispose」—— **从未被验证过，而且不成立**。
这正是任务书点名的「不得继续把 `DisposableEffect` 当作『离开可见区域』的等价事实」。

---

## 5. D-3 最小修复

**修复位置：持有全局 `LazyListState` 的宿主层（`ReaderContentHostPlainTextBranch`），
而不是在每个 item 里加第二个清除回调。**

### 5.1 新增纯策略（可测试，宿主层唯一判据）

`scrollSelectionInvalidation(selectedRangeStart, readingUnits, visibleUnitIndices, viewportInitialized)`
返回三值枚举 `ScrollSelectionInvalidation`：

| 判定顺序 | 条件 | 结论 |
|---:|---|---|
| 1 | `selectedRangeStart < 0`（无选区） | `KEEP` |
| 2 | viewport 未初始化（`totalItemsCount == 0`） | `UNDECIDABLE` |
| 3 | 起点不属于任何已加载单元 | `CLEAR`（安全清除：已无宿主） |
| 4 | owner 可见 | `KEEP`（相邻非 owner 不得误清） |
| 5 | owner 不可见 | `CLEAR` |

配套 `ScrollSelectionInvalidationGate` 去重门：只在
「上一次不是 CLEAR → 这一次是 CLEAR」的**跃变**上派发一次清除。

### 5.2 宿主层接线

在 `ReaderContentHostPlainTextBranch` 的 LazyColumn 之前新增
`LaunchedEffect(s.plainListState)`：

```kotlin
snapshotFlow {
    ScrollViewportSnapshot(
        visibleUnitIndices = listState.layoutInfo.visibleItemsInfo
            .mapNotNull { info -> latestReadingUnits.getOrNull(info.index)?.unitIndex }.toSet(),
        initialized = listState.layoutInfo.totalItemsCount > 0,
    )
}
    .distinctUntilChanged()
    .collect { viewport -> /* 判定 → 门 → latestClearSelection("", -1, -1, -1) */ }
```

要点：

- **不轮询、不 sleep**：用 `snapshotFlow` 观察布局，`distinctUntilChanged` 去抖
  （任务书明确禁止固定 `delay` 猜测；守卫测试里有 `assertFalse(contains("delay("))`）。
- **两个输入经 `rememberUpdatedState` 读最新值**：effect 的 key 是列表身份，
  选区变化**不会**重启它；若在 `collect` 里捕获组合期快照，判据会永远用第一次组合时的
  选区起点（F2.3 登记过的同一类缺陷）。
- **`UNDECIDABLE` 不清**：一帧布局空值 / 首帧未测量不得误清真实选区；
  且该结论**不消耗**去重门，否则真正离开时反而漏清。
- **用户手势与 TTS/程序化滚动同判据**：策略只接收「可见集合」，不含滚动来源，
  两条路径结构上不可能分叉。
- **`DisposableEffect` 保留为最终资源回收防线**（任务书允许），但不再是唯一机制。
- **未采用**「消费者打开时永不清活动选区」——F2.4 的快照与活动选区继续正交，
  本策略派发的信号固定是空文本 `onSelect`，收敛到 `ClearSelection`，只动活动选区五字段。

---

## 6. RED → GREEN 证据

### 6.1 D-1（`MarkdownImageBlockSelectionTest`，新建）

**RED（修复前，`--rerun`）**：

```
> Task :app:testDebugUnitTest FAILED
MarkdownImageBlockSelectionTest > a pure image paragraph is not block selectable FAILED
MarkdownImageBlockSelectionTest > an empty alt image paragraph is not selectable either FAILED
MarkdownImageBlockSelectionTest > the persistent highlight projection skips image only paragraphs FAILED
MarkdownImageBlockSelectionTest > an image with a link style alt and title is still not selectable FAILED
MarkdownImageBlockSelectionTest > a paragraph mixing text and an image is not block selectable FAILED
MarkdownImageBlockSelectionTest > a pure image paragraph never produces block selection args FAILED
MarkdownImageBlockSelectionTest > a pure image paragraph never produces a saveable selection range FAILED
MarkdownImageBlockSelectionTest > the mixed paragraph contract is not implemented by guessing at alt text FAILED
17 tests completed, 8 failed
```

关键失败消息（证明复现的是真机同一缺陷）：

```
a pure image paragraph never produces block selection args
  → expected null, but was:<MarkdownBlockSelection(text=示意图, globalOffset=-1, rangeStart=4, sourceEnd=7)>
a pure image paragraph never produces a saveable selection range
  → expected null, but was:<4..6>
```

**GREEN（修复后）**：`BUILD SUCCESSFUL`，17 tests / 0 failures / 0 errors / 0 skipped。

### 6.2 D-3（`ScrollSelectionVisibilityPolicyTest`，新建）

**RED（策略尚未接入判据前）**：D-3 的策略函数是新增的，因此先写策略与测试，
再用变异证明测试确实咬住旧行为（见 §6.3 变异 A：把可见性判定退回无条件 `KEEP`，
等价于修复前的「只靠 dispose」语义 → 6 项变红）。

**GREEN（修复后）**：20 tests / 0 failures / 0 errors / 0 skipped。

### 6.3 变异验证（证明新测试咬得住）

每次变异后从 `/tmp/s07f5/*.after.bak` 还原并复核哈希。

| 变异 | 内容 | 失败用例数 |
|---|---|---:|
| **A（D-1）** | 去掉 `!markdownUnitContainsImage(unit) &&`（回到「只看 kind」） | **8 FAILED** |
| **B（D-3）** | 可见性判定退回无条件 `KEEP`（等价修复前语义） | **6 FAILED** |
| **C（D-3）** | `UNDECIDABLE` 改为 `CLEAR`（首帧误清真实选区） | **1 FAILED** |
| **D（D-3）** | 去重门去掉 `&& !invalidated`（每帧重复派发） | **3 FAILED** |

---

## 7. 修改文件与 hunk

| 文件 | 类型 | hunk 目的 |
|---|---|---|
| `ui/screen/reader/MarkdownScrollHighlight.kt` | 修改 | ①`markdownUnitIsBlockSelectable` 的文本载体分支增加图片排除；②新增 `markdownUnitContainsImage` 与私有 `markdownUnitSpans`；③补 `MarkdownRenderSpan` import；④KDoc 写明「为什么是整个单元不可选」与 alt 来源 |
| `ui/screen/reader/ReaderContentHostPlainTextBranch.kt` | 修改 | ①新增 `ScrollSelectionInvalidation` 枚举 + `scrollSelectionInvalidation` 策略 + `ScrollSelectionInvalidationGate`；②新增私有 `ScrollViewportSnapshot`；③LazyColumn 前新增宿主层 `LaunchedEffect(s.plainListState)` 观察块；④补 `snapshotFlow` / `distinctUntilChanged` import；⑤`readingUnitShouldClearOnDispose` KDoc 注明它降级为最终防线 |
| `test/.../MarkdownImageBlockSelectionTest.kt` | **新增** | D-1 行为测试（17） |
| `test/.../ScrollSelectionVisibilityPolicyTest.kt` | **新增** | D-3 策略行为测试（20） |
| `test/.../ScrollSelectionVisibilityWiringGuardTest.kt` | **新增** | D-3 接线结构守卫（7） |

**明确未修改**：`ReaderMarkdownRender.kt`（D-1 的渲染层无需改动，它本来就以
`markdownUnitIsBlockSelectable` 为准）、`ReaderContentHostMarkdownBranch.kt`、
`ReaderViewModel.kt`、`ReaderScreenState.kt`、`ReaderOverride.kt`、`SettingsStore.kt`、
所有设置页与 EPUB 设置文件。

---

## 8. before / after 同文件集证据

同一文件清单、同一命令（`sha256sum`），仓库外临时目录 `/tmp/s07f5/`。

| 文件 | before(前16) | after(前16) | 判定 |
|---|---|---|---|
| `main/.../MarkdownScrollHighlight.kt` | `89bd78e2c77d637c` | `0e7d0ebc8349ac6c` | **CHANGED** |
| `main/.../ReaderContentHostPlainTextBranch.kt` | `ddab14689a84e51c` | `784199b715bcb806` | **CHANGED** |
| `main/.../ReaderMarkdownRender.kt` | `81a07cd47934ee3a` | `81a07cd47934ee3a` | IDENTICAL |
| `main/.../ReaderContentHostMarkdownBranch.kt` | `cc3c1ccbc9d986d5` | `cc3c1ccbc9d986d5` | IDENTICAL |
| `test/.../MarkdownUnitSourceRangeTest.kt` | `6ec2da42060f2b5d` | `6ec2da42060f2b5d` | IDENTICAL |
| `test/.../MarkdownBlockSelectionLocatorTest.kt` | `8506f653ce3146f4` | `8506f653ce3146f4` | IDENTICAL |
| `test/.../MarkdownPersistentHighlightProjectionTest.kt` | `e113bceeea2680ee` | `e113bceeea2680ee` | IDENTICAL |
| `test/.../ReaderSelectionRangeWiringGuardTest.kt` | `5e82ede4f4f10c1a` | `5e82ede4f4f10c1a` | IDENTICAL |
| `test/.../ReadingUnitSelectionOwnershipTest.kt` | `395403d512e07b7d` | `395403d512e07b7d` | IDENTICAL |
| `test/.../ReadingUnitDisposeWiringGuardTest.kt` | `167541ccb699ab5a` | `167541ccb699ab5a` | IDENTICAL |

**改动前后完整哈希（after）**：

```
0e7d0ebc8349ac6c6b272cb2782643d486a14f8e7493741a322784b5c3c487b9  main/.../MarkdownScrollHighlight.kt
784199b715bcb8061b7b88f791fe4ab7e8a789f5d450af5dca1dcdc90f97123e  main/.../ReaderContentHostPlainTextBranch.kt
4e8e4d72de515e53cfec545ef3c5cdaa8d6e5c825f06d8ada8d70937f36b0653  test/.../MarkdownImageBlockSelectionTest.kt
a69fd631b045b5e5012f34cc169a3e82a6ccc4c948ec7d0b5e7d2a7af3a8f7d1  test/.../ScrollSelectionVisibilityPolicyTest.kt
824a2c8a5dba2a1ca3882d5fafe9776b217d3b6ba017080484f57b54063ad606  test/.../ScrollSelectionVisibilityWiringGuardTest.kt
```

**未跟踪文件的补丁证据（不使用 `git add -N`）**：

```
MarkdownImageBlockSelectionTest.kt         -> git diff --no-index: 1 file changed, 333 insertions(+)
ScrollSelectionVisibilityPolicyTest.kt     -> git diff --no-index: 1 file changed, 318 insertions(+)
ScrollSelectionVisibilityWiringGuardTest.kt -> git diff --no-index: 1 file changed, 182 insertions(+)
```

**跟踪状态**：两个产品文件为 ` M`（已跟踪、工作区修改），三个新测试为 `??`（未跟踪）。

---

## 9. 未修改 D-2 / D-7 / S08–S20 的证据

### 9.1 禁改文件的 mtime + SHA256（本轮时段为 2026-09-21 12:00–12:48）

| 文件 | mtime | SHA256(前16) | 判定 |
|---|---|---|---|
| `data/settings/ReaderOverride.kt`（D-2 根因文件） | 2026-09-18 02:23:08 | `fa61c39f3c8b5bc0` | 未触碰 |
| `data/settings/SettingsStore.kt` | 2026-09-18 19:35:33 | `b9eb035ee41938a9` | 未触碰 |
| `ui/viewmodel/ReaderViewModel.kt` | 2026-09-21 01:10:31 | `19e6fb71fc5fb7ec` | 未触碰（F4.1 收工值，早于本轮） |
| `ui/screen/reader/ReaderScreenState.kt` | 2026-09-20 23:25:10 | `974ddeddc6a3c7c1` | 未触碰 |
| `feature/reader/pager/ReplacedChapterSource.kt`（D-7 相关） | 2026-09-19 23:58:15 | `3d75aaa58984302c` | 未触碰 |
| `ui/screen/reader/ReaderContentHostEpubBranch.kt`（D-4/D-5 相关） | 2026-09-20 21:20:46 | `bfcc6acfc6d7ee39` | 未触碰 |
| `ui/screen/reader/ReaderPagerEngineState.kt` | 2026-09-19 23:58:15 | `ea29e7886424cb4d` | 未触碰 |

全部 mtime 早于本轮窗口 → 无越界修改。**D-2 / D-7 未做任何改动**，S08–S20 未触碰。

### 9.2 未做的相关项（如实列出）

| 项 | 状态 |
|---|---|
| D-2 设置作用域（分页兼容模式写全局） | **未修**（任务禁止） |
| D-7 legacy EPUB gfxinfo 性能 | **未优化**（任务禁止；无对照组数据） |
| legacy EPUB 无障碍树（D-4） | 未触碰 |
| TTS 通知权限静默（D-6） | 未触碰 |
| 段落间隙长按（D-8） | 未触碰 |
| 搜索索引对纠错后文本（D-9） | 未触碰 |
| legacy EPUB 轻点收不起工具栏（D-5） | 未触碰 |
| S08 编辑入口 | 未实现 |
| 图片相对路径渲染（NOT TESTED） | 未触碰 |

---

## 10. 测试命令、退出码与 XML 精确计数

全部命令在 `android/` 下执行，均带 `--rerun` 避免 UP-TO-DATE 假绿。

### 10.1 编译

```bash
./gradlew --console=plain :app:compileDebugKotlin            # 退出码 0
./gradlew --console=plain :app:compileDebugUnitTestKotlin    # 退出码 0
```

### 10.2 任务书要求的验证集（12 类，`--rerun`，退出码 0）

```bash
./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "…MarkdownUnitSourceRangeTest" \
 --tests "…MarkdownBlockSelectionLocatorTest" \
 --tests "…MarkdownPersistentHighlightProjectionTest" \
 --tests "…ReaderSelectionRangeWiringGuardTest" \
 --tests "…ReadingUnitSelectionOwnershipTest" \
 --tests "…ReadingUnitDisposeWiringGuardTest" \
 --tests "…ReaderSelectionSnapshotSemanticsTest" \
 --tests "…ReaderSelectionSnapshotTest" \
 --tests "…TxtScrollChapterTargetTest" \
 --tests "…MarkdownImageBlockSelectionTest" \
 --tests "…ScrollSelectionVisibilityPolicyTest" \
 --tests "…ScrollSelectionVisibilityWiringGuardTest"
```

| 测试类 | 归属 | tests | failures | errors | skipped |
|---|---|---:|---:|---:|---:|
| `MarkdownUnitSourceRangeTest` | F3/F3.1 | 10 | 0 | 0 | 0 |
| `MarkdownBlockSelectionLocatorTest` | F3.1 | 10 | 0 | 0 | 0 |
| `MarkdownPersistentHighlightProjectionTest` | F3/F3.1 | 11 | 0 | 0 | 0 |
| `ReaderSelectionRangeWiringGuardTest` | F3.1 守卫 | 9 | 0 | 0 | 0 |
| `ReadingUnitSelectionOwnershipTest` | F2.2 | 10 | 0 | 0 | 0 |
| `ReadingUnitDisposeWiringGuardTest` | F2.4 守卫 | 8 | 0 | 0 | 0 |
| `ReaderSelectionSnapshotSemanticsTest` | F2.4 | 12 | 0 | 0 | 0 |
| `ReaderSelectionSnapshotTest` | F4.1 | 24 | 0 | 0 | 0 |
| `TxtScrollChapterTargetTest` | F2.1 | 7 | 0 | 0 | 0 |
| `MarkdownImageBlockSelectionTest` | **F5 新增** | 17 | 0 | 0 | 0 |
| `ScrollSelectionVisibilityPolicyTest` | **F5 新增** | 20 | 0 | 0 | 0 |
| `ScrollSelectionVisibilityWiringGuardTest` | **F5 新增** | 7 | 0 | 0 | 0 |
| **合计** | | **145** | **0** | **0** | **0** |

**请求 12 类 == XML 12 个文件；缺失类 0；零匹配 0。** XML mtime 与运行时刻一致
（12:43:39，运行结束 12:43:48），非陈旧文件。

### 10.3 S07 代表性回归（退出码 0）

```bash
./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.ui.screen.reader.*" \
 --tests "com.creationreadingassistant.ui.viewmodel.*" \
 --tests "com.creationreadingassistant.feature.reader.*"
```

| 项 | 数 |
|---|---:|
| 测试类 | 205 |
| tests | 1953 |
| failures | **0** |
| errors | 0 |
| skipped | 0 |

### 10.4 行为测试 vs 结构守卫（分开统计）

| 类别 | 类 | tests |
|---|---|---:|
| **结构 / 源码守卫**（读源文件文本） | 3（`ReaderSelectionRangeWiringGuardTest` 9、`ReadingUnitDisposeWiringGuardTest` 8、`ScrollSelectionVisibilityWiringGuardTest` 7） | **24** |
| **行为 / 策略测试**（真实 parser/render model、真实策略输入、真实状态断言） | 9 | **121** |
| 合计 | 12 | 145 |

F5 新增的 44 项中：**37 项是行为/策略测试**（真实 `MarkdownParser` + 真实
`MarkdownRenderModel`；真实 `ReadingUnit` 列表 + 真实可见集合），
**7 项是结构守卫**（`ScrollSelectionVisibilityWiringGuardTest`）。

---

## 11. Standards 与 Spec 双轴剩余问题

### Standards 轴（工程质量、可维护性、测试纪律）

**问题数：2**（均为 P3，非阻断）

| 级别 | 数量 | 项 |
|---|---:|---|
| P0/P1/P2 | 0 | — |
| P3 | 2 | ①`ScrollSelectionVisibilityWiringGuardTest` 是源码文本守卫，证明接线形状但不证明 Compose 运行时序（`visibleItemsInfo` 何时更新只有真机能确认）；②`markdownUnitSpans` 对 `CodeBlock`/`HorizontalRule` 返回空列表 —— 这两类本来就没有行内 span，属有意为之，但若将来给代码块加行内高亮需同步复核 |

**Standards 最严重项：P3-①**。本轮新增的 D-3 机制里，**策略层可被 JVM 完整断言，
接线层只能靠结构守卫 + 真机**。这一分界已在守卫测试的 KDoc 里写明，不冒充运行时证据。

### Spec 轴（用户可感知行为、产品契约）

**问题数：2**（本轮关闭 1，保留 1 为显式降级）

| 级别 | 数量 | 项 |
|---|---:|---|
| P0/P1 | 0 | — |
| P2 | 0 | D-1、D-3 已关闭 |
| P3 | 2 | ①**含图片的混合段落整体不可选**（安全契约，显式降级）：用户点了「前面文字 ![图] 后面文字」这类块将没有任何选区 —— 代价是少一个入口，收益是绝不产出伪坐标。这是本切片的有意取舍，需产品确认是否接受；②图片本身仍只显示 alt 文本（Qoder 标 NOT TESTED，本轮未触碰） |

**Spec 最严重项：P3-①**。它不是缺陷，而是 D-1 安全修复的直接代价，必须显式记录：
在「可整块选择」的粒度下，含图片的段落**没有**安全的可选区子区间。

---

## 12. 是否建议交 Qoder 定向真机复验

**建议：是，且应定向复验这两条。**

理由：

1. **D-1 的修复面在 JVM 层已完全自证**（真实 parser + 真实 render model + 8 条 RED → GREEN +
   变异 8 FAILED），但「轻点图片块不再弹工具栏」这一步**只能在真机确认** ——
   Compose 的 `clickable` 是否真的没挂上，JVM 结构上推不出来。
2. **D-3 的失效时机本质上依赖 Compose 运行时序**：`snapshotFlow` 观察到
   `visibleItemsInfo` 的时机、`distinctUntilChanged` 的去抖效果、
   `rememberUpdatedState` 是否真的读到最新选区 —— 全部只能真机验证。
   本轮的策略层证据**不能**替代它。
3. 两条都应**回归验证不误伤**：普通文本段落仍可选、小距离滚动不清选区、
   消费者快照（批注/灵感）在活动选区被清后仍保留摘录。

**定向复验清单（建议给 Qoder）**：

| # | 步骤 | 预期 |
|---:|---|---|
| 1 | 滚动 Markdown，轻点图片块（alt `示意图`） | 无工具栏、无摘录、无 `已高亮` 提示 |
| 2 | 同上，改用长按 | 同样无选区（不是只把轻点关掉） |
| 3 | 关闭重开测试 Markdown，滚到第二节 | 库中**已存在**的那条红底 `示意图` 伪高亮**不再回显**（投影层已挡） |
| 4 | 轻点普通文本段落 | 仍能整块选中、工具栏正常（不误伤） |
| 5 | 轻点「文字+图片」混合段落 | 无选区（安全契约，属预期） |
| 6 | 滚动 TXT 第1章建立选区 → 下滑到第6章 | 工具栏与摘录**自动消失**（D-3 修复目标） |
| 7 | 同上，用 TTS 朗读推进到第4章 | 同样自动消失（同判据） |
| 8 | 滚动 TXT 建立选区后**小幅度**滚动（owner 仍可见） | 工具栏**不**消失（不得误清） |
| 9 | 建立选区 → 打开批注 → 滚到其他章 | 工具栏消失，**批注摘录与草稿仍在**（F2.4 正交性不得回退） |
| 10 | 图片块修复后，检查真机无障碍树 | 图片块不应出现 `clickable=true` 的可选语义 |

**本轮不写「发布完成」，不领取 S08。** S08–S20 保持 HOLD。

---

## 13. 回传

- Agent 与实际模型：**Zcode / `deepseek v4.1Flash`**
- 报告绝对路径：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\agent-results\agent-result-s07-f5-zcode.md`
- 问题数：Standards 2（均为 P3，最严重 P3-①接线层证据边界）、Spec 2（均为 P3，最严重 P3-①混合段落安全契约）
- 测试：验证集 12 类 145 项 + S07 回归 205 类 1953 项，**全部 0 failure / 0 error / 0 skipped**，退出码 0
- index：`git diff --cached --name-only` = 0；`git stash list` = 0；HEAD 仍 `5aed593`
- 是否修改报告以外文件：**否**（仅 §7 列出的 2 个产品文件 + 3 个新测试，全在授权边界内）
- 未修改 D-2 / D-7 / S08–S20：**已用 mtime + SHA256 逐一证明**（§9）
- **等待 Codex 主任务复核，未领取下一片**
