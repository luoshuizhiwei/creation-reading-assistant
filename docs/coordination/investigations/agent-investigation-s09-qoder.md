# S09 只读盘点：页码、硬件键与点击区域 — Qoder

> 执行 Agent：Qoder　|　声明模型：`qwen3.8Flash`
> 任务编号：S09-INVESTIGATION（**并行只读**任务）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`　固定基线：`origin/main = 5aed5939…`
> 开始/结束 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> **未运行任何 Gradle 命令**（窗口 A 正在改 D-2-B，避免抢构建锁）
> 本报告路径：`docs/coordination/investigations/agent-investigation-s09-qoder.md`
> 状态：只读盘点完成，等待 Codex 裁决。**未实施任何功能，未领取下一任务。**

标注约定：**[事实]** = 我读到的源码（带文件:行号）；**[推断]** = 静态推导；**[未验证]** = 需运行期/真机。

---

## 1. 结论摘要

S09 名义下其实混着**四项能力，成熟度差异极大**，其中两项存在"看起来有、实际没接线"的情况：

| 能力 | 真实状态 |
|---|---|
| **A 当前章页码 X/Y** | **已接线，但只在分页引擎里**。`{PageIndex}/{PageSize}` 由 `PagedReaderHeader/PagedReaderFooter` 渲染，而这两个 composable 的**唯一调用方是 `PagedTxtReaderHost`**。**滚动模式与 legacy 分页 EPUB 完全不渲染页眉页脚**，因此没有任何页码显示 |
| **B 全书页码 / 全书进度** | **全书页码不存在**。`WholeBookPageModel`（212 行 + 11 个测试）**在生产代码里零引用**（全仓仅其自身文件 1 处 + 其测试文件）。全书进度存在，但是**字符偏移百分比**（`absOffset*100/totalChars`），与"页"无任何换算关系 |
| **C 硬件键** | **接线完整、设计干净**（纯决策函数 + 单一 handler + 序列消费）。但存在三处真实缺口：TXT 滚动到章末无法用音量键跨章；媒体键桥只在分页引擎里激活；"朗读时音量键仍翻页"实际判的是 **TTS 面板是否可见**而非是否在朗读 |
| **D 点击区域** | **同一个设置有三套互不相同的实现**：分页引擎 33%/33%/33%、legacy 分页 EPUB 16%/68%/16%、滚动模式**根本没有翻页热区**；而设置页的预览卡用的是第三种比例 24%/52%/24%，**与两个真实消费端都不符** |

**最重要的一条**：`ReaderPageIndexManager`（声称是"全 App 唯一的当前页索引真源"）**只被写、几乎不被读** ——
唯一读取点是 `ReaderSheetHost.kt:374` 的"添加书签"，且**不校验 `bookId`、`clear()` 从未被调用、
只在分页引擎分支里 `update()`**。⇒ 滚动模式下书签拿到的是 `null`（无 locator）
或**上一本分页书的残留位置**。这是本轮发现的唯一 P1。

**能独立实现的**：C 的三处缺口修复、D 的"单一热区事实源"收敛、A 的"滚动模式补齐页眉页脚"。
**必须等全书页码产品契约的**：B 的一切（是否显示全书页码、口径、估算章的跳变是否可接受、
是否替换现有字符百分比）。

**测试层面的判断**：纯函数层（`resolveReaderTapAction`、`readerVolumeTurnPlan`、
`ReaderHardwareKeys`、`WholeBookPageModel`）覆盖得**相当好**，全部是真行为测试；
问题集中在**接线层零覆盖**（页脚 composable 从未被任何测试渲染、
`PageInfo` 的填充处 `PagedReaderControllerEffects.kt:146-163` 零测试）
与**一条占位测试**（`ReaderScreenTest.kt:206-221` 在测试里自建 `clickable` 再自点自测，
与阅读器点击代码毫无关系，却计入"点击区域"覆盖）。
⇒ 现状是"函数正确性有保证，端到端是否真的显示/真的翻页无人证明"。

---

## 2. 四项能力分拆

| 能力 | 定义边界 | 现状 | 与其他三项的耦合 |
|---|---|---|---|
| **A 当前章页码** | 页脚/页眉里"第 N 页 / 共 M 页 / 余 K 页"，M 为**本章**排版后的页数 | 有，仅分页引擎 | 依赖 `PageInfo`（分页控制器）；与 D 共用同一渲染槽（点区域翻页改变它） |
| **B 全书页码/进度** | 全书第 X 页 / 共 Y 页，或全书 % | 只有字符 % ；全书页码是**未接线的纯模型** | 需要 A 的每章页数 + 章节字符数；与 A 是聚合关系，不能各算各的 |
| **C 硬件键** | 音量键 / PAGE_UP·DOWN / SPACE / DPAD / 鼠标滚轮 / 媒体按钮（蓝牙翻页器、线控） | 有，链路完整 | 与 A/B 共用"翻页"动作；与 D 共用 canPrev/canNext 边界判定 |
| **D 点击区域** | `tapZoneMode`（three/five-zone）× `tapActionPreset`（standard/left_hand/both_next）× 中央唤菜单 × 边缘死区 | 有，但三套实现 | 与 C 同样落到"翻一页/上一章"；与 A 共用 PageInfo 的边界 |

**不能混为一谈的理由**：A 与 B 是"章内口径"与"全书口径"两套数学；C 与 D 是"两种输入设备到同一个动作"，
但今天它们各自判断边界（C 用 `canScrollForward/canNext`，D 用 `canPrevious/canNext`），
收敛前任何一方改动都可能让另一方行为漂移。

---

## 3. 发送方 / 状态 owner / 处理方 / 消费者链

### A 当前章页码

```
[发送方]  PagedReaderController 排版完成 → PageInfo(pageIndex, pageCount)
          （feature/reader/pager/PagedReaderPageSurface.kt，控制器持有页模型）
[owner]   PageInfo 的单一真源 = 分页控制器内部页模型；
          Compose 层只是把它作为参数往下传
[处理方]  PagedTxtReaderHost.kt:200 PagedReaderHeader(...)
                              :347 PagedReaderFooter(...)
          → PagedReaderHeaderFooter.kt:100 TipRow(absOffset = anchorValue, pageInfo = ...)
          → CustomTipPlaceholder.kt:127-144  {PageIndex}/{PageSize}/{PageRemaining} 替换
[消费者]  屏幕上的页脚文本；设置页模板预览 CustomTipDialog.kt:116 renderSample(...)
[未接线]  滚动模式与 legacy 分页 EPUB 分支没有任何 TipRow/PagedReaderFooter 调用
          （grep 全仓：PagedReaderHeader/Footer 只出现在 PagedTxtReaderHost.kt）
```

### B 全书页码 / 全书进度

```
[发送方]  分页引擎 onPositionChanged(off, pct)  ← PagedReaderPageSurface
          滚动模式：LazyListState 首可见项偏移
[owner]   ReaderViewModel 的 stats.progressPercent（持久阅读进度）
[处理方]  ReaderChrome.kt:612  "$formatLabel · ${chapterTitle} · ${progressPercent.toInt()}%"
          CustomTipPlaceholder.kt:147-151  {ReadProgress} = absOffset*100f/totalChars
[消费者]  顶栏标题、页脚进度槽、书签 offset、进度 sheet、统计
[断链]    WholeBookPageModel 不在任何一条链上：
            grep -rn "WholeBookPageModel" android/app/src/main → 仅其自身文件 1 处（声明），0 个调用方
          它的输入（每章页数）其实**已经在** PageIndexStore 的 pageStarts 里，但没人聚合
```

### C 硬件键

```
[发送方]  KeyEvent（音量/PAGE/SPACE/DPAD）→ MainActivity.onKeyDown / onKeyUp / dispatchKeyEvent(ACTION_SCROLL)
          媒体按钮（蓝牙翻页器、线控）→ ReaderMediaButtonBridge 的 MediaSession.Callback
[owner]   ReaderHardwareKeys（**进程内单例 object**，可变 handler + consumedKeys 集合）
[处理方]  handler ← ReaderPlatformEffects.kt:126-137 DisposableEffect 安装/卸载
          → onVolumeUp/onVolumeDown（ReaderSessionEffects.kt:234/242）
          → readerVolumeKeyTurn → readerVolumeTurnPlan（纯函数，JVM 可测）
[消费者]  ReaderVolumeTurnPlan.Paged   → pagedHardwareTurnRequest.value = dir
                                         → PagedReaderControllerEffects.kt:185 LaunchedEffect 消费后**置回 null**
          …Scroll   → activeScrollState.animateScrollBy(视口高 × 0.88 × dir)
          …Chapter   → goToChapter(targetIndex)
          …UseSystemVolume → return false → MainActivity 交回 super（系统音量正常）
```

### D 点击区域

```
[发送方]  正文区 pointerInput / clickable
[owner]   无统一 owner —— 三套实现各自判定（见 §7）
[处理方1] PagedReaderPageSurface.kt:412 resolveReaderTapAction(x,y,w,h,tapZoneMode,canPrev,canNext,preset,edgeDeadzonePx)
          （feature/reader/pager/ReaderTapAction.kt:23-61）
[处理方2] PagedEpubContent.kt:236-312  用 clickable Box + weight(0.16/0.68/0.16) 自己分区
[处理方3] 滚动模式：ReaderTapObservation.kt readerCenterTapToToggle / readerTextFieldCenterTapToToggle
          —— **只有中央唤菜单，没有翻页分区**
[消费者]  翻页动作 / onToggleControls
```

---

## 4. 格式 × 阅读模式矩阵

| 格式 | 模式 | 渲染分支 | 当前章页码 | 全书进度 | 音量键翻页 | 媒体键/蓝牙翻页器 | 点击翻页热区 | 中央唤菜单 |
|---|---|---|---|---|---|---|---|---|
| TXT | 分页（引擎 on） | `ReaderContentHostPagedBranch` → `PagedTxtReaderHost` | ✅ `{PageIndex}/{PageSize}` | ✅ 字符% | ✅ `Paged(dir)` | ✅ | ✅ 1/3 三区（+五区上下 12%） | ✅ |
| TXT | 滚动 | `ReaderContentHostPlainTextBranch` | ❌ **无任何页码显示** | ✅ 字符%（顶栏） | ✅ `Scroll`；**章末不能跨章**（见 §6） | ❌ **桥未激活** | ❌ 无翻页热区 | ✅（两级观察器 + 仲裁门） |
| Markdown | 分页 | 同 `PagedTxtReaderHost`（`MarkdownPageSource`） | ✅ | ✅ | ✅ | ✅ | ✅ 同 TXT 分页 | ✅ |
| Markdown | 滚动 | `PlainText`/单元滚动分支 | ❌ | ✅ | ✅ Scroll / Chapter | ❌ |  | ✅ |
| EPUB | 分页（引擎 on） | `PagedTxtReaderHost`（`EpubPageSource`） | ✅ | ✅（**章基址为估算**，见 §5） | ✅ | ✅ | ✅ 1/3 | ✅ |
| EPUB | legacy 分页（引擎 off/auto 关闭） | `ReaderContentHostEpubBranch` → `PagedEpubContent` | ❌ **不渲染页脚** | ✅ 顶栏 % | ✅ Paged/Chapter | ❌ | ✅ 但 **16%/68%/16%** | ✅（0.68 中央 clickable） |
| EPUB | 滚动 | `ReaderContentHostEpubBranch` LazyColumn | ❌ | ✅ | ✅ Scroll/Chapter | ❌ | ❌ | ✅ |

**[事实]** 支撑：`ReaderContentHost.kt:160` 注释「pagerEngineOn → ReaderContentHostPagedBranch」；
`PagedReaderHeader/Footer` 仅出现在 `PagedTxtReaderHost.kt:200/347`；
`ReaderMediaButtonBridge` 仅在 `PagedTxtReaderHost.kt:283-288` 创建并 `activate()`；
`ReaderContentHostPlainTextBranch.kt:164/614` 只有 `readerCenterTapToToggle`。

⇒ **滚动模式下"页码"这一整个概念对用户不存在**（既无 X/Y，也无"全书第几页"），
只有顶栏一个整数百分比。这是 A/B 能力的最大空白。

---

## 5. 当前章与全书页码事实源

| 口径 | 事实源 | 计算 | 连续性 | 是否用户可见 |
|---|---|---|---|---|
| 当前章页码 | `PageInfo.pageIndex`（分页控制器） | 0 基，显示时 +1（`CustomTipPlaceholder.kt:128-131`） | **跨章归零**（每章 1..N） | ✅ 仅分页引擎页脚 |
| 本章总页 | `PageInfo.pageCount` | 本章排版结果 | 跨章跳变（每章不同） | ✅ 同上 |
| 本章余页 | `{PageRemaining}` = pageCount − (pageIndex+1)，夹到 ≥0 | 同上 | 同上 | ✅（用户需在模板里选） |
| 全书百分比 | `{ReadProgress}` 与 `stats.progressPercent` | `absOffset * 100f / source.totalChars`（:147-151） | **连续单调**（跨章不跳） | ✅ 页脚 + 顶栏 |
| 全书页码 | **不存在** | — | — | ❌ |
| 全书页码模型 | `WholeBookPageModel` | 前缀和：已排版章用精确页数，未排版章用 `ceil(章字数/平均页容量)` 估算 | 设计为 O(n) 重算 | ❌ **零接线** |

**三条必须记住的事实**：

1. **`{ReadProgress}` 与页码不是同一口径**：字符百分比 ≠ 页百分比。同一本书第 5/10 页
   不等于 50% 进度（章内页均字数不同、各章页数不同）。用户同时看到两者会认为互相矛盾。[事实]
2. **EPUB 的 `absOffset` 空间是估算章基址**：`EpubReplacedChapterSource.kt:19` 明确写
   `scopeSourceBase = delegate.chapterStartAbs(index)（估算章基址）`。
   分子分母同源所以比例自洽，但**与"真实字符进度"不等价**。[事实 + 推断]
3. **排版失效链是完整的**（我原本怀疑粗体漏进指纹，实测**没有**）：
   `fontWeightBold` 被编进 `typefaceKey`（`PagedTxtReaderHost.kt:169-172`
   `… + "|b=$fontWeightBold"`），而 `typefaceKey` 进 `LayoutConfig`（:264）并进 `fingerprint`
   （`LayoutConfig.kt:87`）。字号/行距/段距/视口（含旋转与边距）/字体/引擎版本/两端对齐
   全部在指纹里（:66-90）。纸张不参与 —— 正确，它不影响排版。[事实]
   - `ENGINE_VERSION = 4`，注释明确要求"改排版算法必须 +1"。[事实]
   - 缓存失效后 `PageIndexStore.prune` 每本书最多留 2 个指纹（`PageIndexStore.kt:60-62`）。[事实]
   - 有 `TypefaceKeyTest.kt` 覆盖 typefaceKey 的构成。[事实]

**跨章翻页时页码是否连续**：当前章页码**必然跳变**（10 → 1），这是"章内页码"的定义使然；
全书百分比连续。真正**未实现**的是"全书页码连续"（需要 B 的模型接线）。[事实]

---

## 6. 硬件键矩阵

| 维度 | 事实 | 位置 |
|---|---|---|
| 键码集合 | VOLUME_UP/PAGE_UP/DPAD_LEFT = −1；VOLUME_DOWN/PAGE_DOWN/SPACE/DPAD_RIGHT = +1；其余不消费 | `ReaderHardwareKeys.kt:29-35` |
| 开关 | `volumeKeyPaging=false` → `UseSystemVolume` → 不消费 → 系统音量正常 | `ReaderVolumeTurn.kt:57` |
| 反转 | `effectiveVolumeTurnDirection(dir, invert) = if (invert) -dir else dir` | `:36-37` |
| TTS 期间 | 条件是 **`ttsVisible = showTts`**（TTS 面板可见），**不是**"正在发声" | `:57`、`ReaderSessionEffects.kt:234` |
| 长按/连按 | 首个 DOWN 交给 handler；`repeatCount>0` 的 DOWN **只消费不再翻**；UP/CANCEL 消费并释放 | `ReaderHardwareKeys.kt:66-82` |
| 输入焦点 | 走 **Activity 级** `onKeyDown/onKeyUp/dispatchKeyEvent`，**不依赖 Compose 焦点**；正文 `BasicTextField` 拿到焦点也照样翻页 | `MainActivity.kt:213-230` |
| 系统音量副作用 | 未消费时交回 `super` ⇒ 音量条正常；消费时系统看不到该键 ⇒ 音量条不出现（设计意图） | `MainActivity.kt:214-222` |
| 鼠标滚轮 | `ACTION_SCROLL` 反射取 `AXIS_VSCROLL`，一次滚动一次翻页，不登记序列 | `ReaderHardwareKeys.kt:41-48, 59-63` |
| 媒体按钮 | `ReaderMediaButtonBridge` → `triggerDirection` 复用同一 handler；`PLAY_PAUSE` 返回但不动作（防焦点被抢） | `ReaderMediaButtonBridge.kt:44-56` |
| 与 TTS 的媒体焦点竞争 | 注释声明"TTS 的 MediaSession 优先级更高自动接管" | 同上 :13-16 |
| 序列泄漏防护 | `handler` 置 null 时 `consumedKeys.clear()` | `ReaderHardwareKeys.kt:19-23` |
| 请求去重 | `pagedHardwareTurnRequest` 消费后置回 `null` ⇒ 连续同方向按键不会被 StateFlow 去重吞掉 | `PagedReaderControllerEffects.kt:185-188` |

**三处真实缺口**：

1. **TXT 滚动模式音量键不能跨章**：
   `chapterCount = epubBook?.chapters?.size ?: markdownDocument?.chapters?.size ?: 0`
   （`ReaderVolumeTurn.kt:101`）⇒ 对纯 TXT 恒为 0 ⇒ `Chapter` 分支永不命中 ⇒ 章末直接回落系统音量。
   [事实] 是否算缺陷取决于 TXT 滚动是否单章视图：TXT 滚动用 `readingUnits` 跨章连续加载
   （`ScrollUnitContentLoader`），`canScrollForward` 在章内为真时走 `Scroll`；
   当整个已加载窗口滚到尽头而下一章尚未加载时，会短暂落到 `UseSystemVolume`。
   ⇒ 判 **P2（行为不一致：EPUB/Markdown 能跨章，TXT 不能）**，需真机确认触发频率。
2. **媒体键/蓝牙翻页器只在分页引擎激活**：`ReaderMediaButtonBridge` 仅在
   `PagedTxtReaderHost` 内 `activate()` ⇒ **滚动模式插着蓝牙耳机按"下一曲"完全无反应**。[事实] ⇒ **P2**。
3. **"朗读时音量键仍翻页"名不符实**：设置项名字讲的是"朗读时"，代码判的是 `showTts`（面板可见）。
   用户收起 TTS 面板后继续朗读时，音量键会**开始翻页**（而不是调节音量）。[事实] ⇒ **P3（文案/语义）**。

---

## 7. 点击区域矩阵

| 维度 | ① 分页引擎 | ② legacy 分页 EPUB | ③ 滚动模式（全格式） | ④ 设置页预览卡 |
|---|---|---|---|---|
| 实现 | `resolveReaderTapAction`（纯函数） | `clickable` Box 分区 | 两个 pointer 观察器 | 静态示意 |
| 三分区左右宽度 | **各 1/3（33.3%）** | **各 16%** | — | **各 24%** |
| 中央 | 其余 1/3 | 68% | 中央 1/3 判定（`isCenterTapZone`） | 52% |
| 五分区上下带 | `y < 0.12h` / `y > 0.88h`（12%） | `weight 0.12`（12%） | — | `weight 0.24`（24%） |
| 五分区水平带 | 仍按 1/3 | 0.16 / 0.68 / 0.16 | — | 0.30 / 0.40 / 0.30 |
| 边缘死区 | ✅ `edgeDeadzonePx` | ❌ 无 | ❌ 无 | 未表达 |
| 边界（不能翻时） | ✅ `canPrevious/canNext` → `NONE` | ✅ `clickable(enabled=…)` | — | 未表达 |
| `tapActionPreset` | ✅ 三分支 `when` | ✅ `isLeftAdvance/isRightAdvance` 另一套写法 | ❌ 不适用 | ✅ 标签联动 |
| 无障碍 | ❌ 纯 pointer 输入，**无 a11y 节点**，TalkBack 无法"翻下一页" | ✅ 有 `contentDescription="上一章/下一章"` | 中央 toggle 有 clickable | — |
| 选择态互斥 | 由 `PagedReaderPageSurface` 的 `selectionCleared`/消费顺序处理 | 正文 block `clickable` 优先 | **靠 `down.isConsumed` + `ReaderTapToggleGate` 仲裁**（:28-47） | — |
| 弹窗/底部 sheet 打开时 | 由 sheet 覆盖层拦截（父级 pointerInput 不收到正文点击） | 同 | 同 | — |
| 测试 | `ReaderTapActionTest` 9 + `ReaderTapActionPresetTest` 6 + `ReaderTapTurnPlanTest` 5 | **0 个热区测试**（`PagedEpubContentTest` 只有 1 个 scrollToItem 用例） | `ReaderTapObservationTest`（纯逻辑） | 0 |

**核心问题**：同一个 `tapZoneMode="three-zone"` 在分页引擎是"左右各 33%"，
在 legacy 分页 EPUB 是"左右各 16%"，而设置页预览卡画的是"24%"。
用户改这个设置时**无法预期实际热区大小**，且从一种渲染模式切到另一种（例如把 EPUB 引擎从
强制改成关闭）时，热区宽度会**凭空从 1/3 屏缩到 1/6 屏**。[事实] ⇒ **P2**。

**另一处设计陷阱**：`both_next` + `three-zone` 组合下，左=下一页、右=下一页、中=菜单 ⇒
**点击无法回到上一页**（引擎注释自己承认"五分区顶部或手势负责回退"，
`ReaderTapAction.kt:19`，但 three-zone 下没有顶部带）。legacy EPUB 同样
（`isLeftAdvance=true`、`isRightAdvance=true`）。⇒ **P3，需产品裁决**（是缺陷还是可接受的预设语义）。

---

## 8. 设置与消费端一致性

| 设置项 | 存储 | 是否可被本书覆盖 | 消费端数量 | 一致性结论 |
|---|---|---|---|---|
| `tapZoneMode` | 全局（不在 `ReaderOverrideKey`） | ❌ 全局 | **3 套**（引擎 / legacy EPUB / 预览卡） | ❌ **不一致**（P2） |
| `tapActionPreset` | 全局 | ❌ | 2 套实现 + 预览卡 | ⚠️ 动作映射一致，几何不一致 |
| `volumeKeyPaging` | 全局 | ❌ | 1 套（`readerVolumeTurnPlan`） | ✅ 单一事实源 |
| `volumeKeyInvert` | 全局 | ❌ | 1 套 | ✅ |
| `volumeKeyPagingDuringTts` | 全局 | ❌ | 1 套，但**判 `showTts` 而非播放状态** | ⚠️ 名实不符（P3） |
| `showReaderInfo` / `headerLeft` … | **可本书覆盖** | ✅ | 只有分页引擎渲染 | ❌ 设置说"本书可覆盖"，但**滚动模式下改了看不到**（P2） |
| `customTip*` 六槽 | 全局 | ❌ | 同上，仅分页引擎 | ❌ 同上 |
| `batteryTipStyle` | 全局 | ❌ | 同上 | ❌ 同上 |
| `pageTurnSpeed` / `autoPageSpeed` | 全局 | ❌ | 引擎 `PageTurner(speed)`；`AutoPaging` | ✅ |
| `readerMode`（分页/滚动） | **可本书覆盖** | ✅ | 决定走哪个分支 ⇒ **它是上述所有不一致的开关** | ⚠️ 根因 |

**根因判断**：`readerMode` + 分页引擎模式（D-2-A 已 per-book）决定走哪个渲染分支，
而**页眉页脚与点击热区在这两个分支里是两套独立实现**。
所以"设置项 ↔ 消费端"的一致性不是一个 bug，而是**缺一个共享的渲染/命中层**。[推断，基于上述事实]

---

## 9. 测试覆盖与缺口

> 本节由**我自己的定向清点** + 一个**并行只读子代理的全库测试盘点**合并而成
> （子代理只读 grep/读文件，未运行任何 Gradle）。
> 两处我采纳子代理的结论并已独立复核其行号；
> 子代理有一处表述我不采纳：它称 `ReaderSettingsRouterTest:265` "asserts tapZoneMode is a ReaderOverrideKey"，
> 我读到的实际是**反向**断言（`assertFalse(entries.any { it.id == "tapZoneMode" })`），以我的读法为准。

### 已有（关键类）

| 测试 | 类名（package 已核对） | @Test | 类型 | 覆盖 |
|---|---|---:|---|---|
| `feature.reader.pager.ReaderTapActionTest` | 同名 | 9 | **BEHAVIOR** | `resolveReaderTapAction` 三区/五区/边界→NONE；L62-68 带**反循环控制断言** |
| `feature.reader.pager.ReaderTapActionPresetTest` | 同名 | 6 | **BEHAVIOR** | 三个 preset 的方向映射（含 both_next 双侧 NEXT） |
| `feature.reader.pager.ReaderTapTurnPlanTest` | 同名 | 5 | **BEHAVIOR** | `ReaderTapTurnPlan`：谁提交翻页 / 手动揭示 / 边界 None |
| `feature.reader.pager.ReaderHardwareKeysTest` | 同名 | 9 | **BEHAVIOR** | DOWN/UP/repeat/CANCEL/交错不 stuck/`handler=null` 释放/滚轮/媒体 triggerDirection |
| `ui.screen.reader.ReaderVolumeKeyTurnPolicyTest` | 同名 | 9 | **BEHAVIOR** | `readerVolumeTurnPlan` 全分支 + 反转（paged/scroll/chapter 三种都测） |
| `feature.reader.pager.WholeBookPageModelTest` | 同名 | 11 | **BEHAVIOR（但测的是未接线代码）** | 前缀和、估算↔精确、setChapterCharCounts 丢弃、percent 单调与 0/100 端点、clamp、revision |
| `feature.reader.pager.CustomTipPlaceholderTest` | 同名 | 8 | **BEHAVIOR（部分）** | token 枚举/校验/`fromHeaderFooterItem(PAGE_NUMBER)→"{PageIndex}/{PageSize}"`；:53-70 用**手工造的** `PageInfo(2,10)` 断言渲染出 `3/10`；:57 `source = null` |
| `feature.reader.layout.PageAccessibleTextTest` | 同名 | 9 | **BEHAVIOR** | 真实 `ChapterPaginator.paginate` + 每页文本 == `substring(start,end)`；TXT+Markdown，**无 EPUB** |
| `feature.reader.pager.PagedReaderControllerCrossChapterNavTest` | 同名 | 5 | **BEHAVIOR** | 跨章前进落到下一章第 0 页（**仅 TxtChapterSource**） |
| `ui.screen.reader.ReaderProgressNavigationTest` | 同名 | 7 | **BEHAVIOR** | `seekToPercent`/`seekToChapterPercent`（**仅 scroll 路径、仅 EPUB**） |
| `feature.reader.locator.EpubProgressCalibrationTest` | 同名 | 8 | **BEHAVIOR** | EPUB 百分比↔字符偏移校准 |
| `ui.screen.reader.ReaderTapObservationTest` | 同名 | 9 | **BEHAVIOR** | `isCenterTapZone`/仲裁门/长按判定（滚动模式中央唤菜单） |
| `androidTest…content.PagedEpubContentTest` | 同名 | **1** | Compose/仪器 | 传了 `tapZoneMode="three-zone"` 但**只断言 scrollToItem，一次点击都没有** |
| `androidTest…ui.screen.ReaderScreenTest` | 同名 | 17 | ⚠️ 含**占位** | L206-221 见 SN-13 |
| `androidTest…ui.screen.ReaderAccessibilityLayoutTest` | 同名 | 4 | Compose/仪器 | 320/393/430dp + 大字号下控件不溢出（**不测点击/按键**） |
| `android/benchmark…ReaderBenchmark.kt` | 同名 | 6 | **BENCHMARK** | 唯一真按坐标点击处：`(w*4/5, h/2)` 翻 EPUB/TXT 页，**但不断言页码变了** |
| `feature.reader.layout.ChapterPaginatorBenchmark` | 同名 | 1 | ⚠️ | 唯一断言用 Kotlin `assert(median < 2000L)` —— **不开 `-ea` 时永不生效** |

### 必须纠正我自己先前的一句话

我一度写"没有占位测试"。**这是错的**，子代理找出三类，见 SN-13 / SN-14。

### 缺口（按能力，已合并子代理的 A1-A4 / B1-B4 / C1-C4 / D1-D5 / G1-G3）

| # | 缺口 | 严重度 |
|---|---|---|
| G1 | **没有任何测试渲染 `PagedReaderHeader`/`PagedReaderFooter`/`TipRow`**；`PagedReaderControllerEffects.kt:146-163`（唯一把 `controller.pageIndex/pageCount` 灌进 `PageInfo` 的地方）**零测试引用** ⇒ 页脚显示的页码可以是错的而全部测试仍绿 | **P2** |
| G2 | **生产进度公式与"页"零关系且零断言**：`TxtPagedController.kt:148-151` = `currentPageStartAbs * 100f / total`（字符偏移），`ReaderProgressActions.kt:281` 同类；没有任何测试断言 percent == f(pageIndex, pageCount, 总页数) | **P2** |
| G3 | `ReaderPageIndexManager` / `PageIndexStore` **无专属测试**，只在 3 个 VM 测试里当 mock 协作者出现 ⇒ SN-1 的书签链完全无保护 | **P1**（随 SN-1） |
| G4 | legacy EPUB 的 16%/68%/16% 热区**零点击测试**；`TapZonePreviewCard.kt:53-54` 与 `PagedEpubContent.kt:236-238` 两份重复的 preset→方向映射**零测试**，可与 `ReaderTapAction.kt:44-53` 静默分叉 | **P2** |
| G5 | `edgeDeadzonePx`（`PagedReaderPageSurface.kt:404` 硬编码 10.dp，:421 传入）**零覆盖** | **P2** |
| G6 | `ReaderMediaButtonBridge.kt` **整文件零测试**（含"只在分页引擎激活"这一事实） | **P2** |
| G7 | `volumeKeyPagingDuringTts = true` 的**正向分支未测**（策略测试只测了 deny 侧） | **P3** |
| G8 | `pagedHardwareTurnRequest` 的状态接力（`ReaderVolumeTurn.kt:105` → `ReaderPagerEngineState` → `ReaderContentHostPagedBranch:65` → `PagedReaderControllerEffects:185`）**无端到端测试** ⇒ 没有任何测试证明"按一次音量键，屏幕上真的翻了一页" | **P2** |
| G9 | **没有任何仪器化测试按过硬件键**（`androidTest` 内 grep `KEYCODE_VOLUME\|dispatchKeyEvent\|performKeyInput` 零命中）；坐标点击只存在于 benchmark | **P2** |
| G10 | 选择态 / sheet 打开 / 对话框打开时点击被拦截（`PagedReaderPageSurface.kt:391-401` 等）**零测试**；TalkBack 触摸探索零覆盖 | **P3** |
| G11 | 分页控制器真实测试**只有 TXT 源**（`PagedReaderController*Test` 全用 `TxtChapterSource`）⇒ Markdown / EPUB 的分页与页码无任何真控制器覆盖 | **P2** |
| G12 | `{PageRemaining}` / `{ReadProgress}` 的渲染只在 `source = null` 下被测过 ⇒ 章名、章序、真实进度渲染未测 | **P3** |

---

## 10. P0 / P1 / P2 / P3

| 级别 | 数量 | 项 |
|---|---:|---|
| P0 | **0** | — |
| P1 | **1** | SN-1 |
| P2 | **7** | SN-2 … SN-7、SN-13 |
| P3 | **6** | SN-8 … SN-12、SN-14 |

| ID | 级别 | 事实（文件:行） | 后果 | 最小修复 | 是否 S09 引入 |
|---|---|---|---|---|---|
| **SN-1** | **P1** | `ReaderSheetHost.kt:374` 读 `pageIndexManager.position.value` 生成书签锚点，**不校验 `pos.bookId == bid`**；`ReaderPageIndexManager.clear()` **全仓 0 个调用点**（grep `pageIndexManager.clear` = 0）；`update()` 只在 `ReaderContentHostPagedBranch.kt:75` 被调用 | ①滚动模式添加书签 ⇒ `position` 为 null ⇒ `absOffset=-1`、`locatorJson=null` ⇒ **书签无定位，跳不回位置**；②先读 A 书（分页）再读 B 书（滚动或首帧未回调）时点书签 ⇒ **写入 A 书的绝对偏移**，B 书书签指向错误位置。属**持久化数据错误** | 在 `onAddBookmark` 里加 `if (pos == null \|\| pos.bookId != bid) → 走 `progressPercent`/章内偏移回落路径并提示；并在阅读器 `onDispose`/切书时调用 `clear()` | 否（既有） |
| **SN-2** | P2 | 点击热区三套几何：`ReaderTapAction.kt:41-55`（1/3）、`PagedEpubContent.kt:288-312`（0.16/0.68/0.16）、`TapZonePreviewCard.kt:95-162`（0.24/0.52/0.24） | 同一设置在两种渲染模式下手感不同；预览卡两个都不对；切引擎会让热区宽度突变 | 让 legacy 分支复用 `resolveReaderTapAction`（或至少共享常量），预览卡改为引用同一常量 | 否 |
| **SN-3** | P2 | 页眉页脚（含 `{PageIndex}/{PageSize}`）只在 `PagedTxtReaderHost` 渲染；`ReaderContentHostPlainTextBranch` / `ReaderContentHostEpubBranch` 无 | 用户可在设置里打开"页眉页脚"、选"页码"，**滚动模式下毫无变化**；D-2-B 刚把它标成"本书可覆盖项"，覆盖后仍看不到 | 滚动模式补一条等价信息条（章 + 进度），或在设置页对滚动模式禁用/说明 | 否 |
| **SN-4** | P2 | `ReaderVolumeTurn.kt:101` `chapterCount` 对 TXT 恒 0 | TXT 滚动模式音量键到章末不再响应（EPUB/Markdown 可跨章） | 传入真实章节数来源（`bookIndex`/`txtChapters`），或明确 TXT 滚动不支持跨章并加测试钉住 | 否 |
| **SN-5** | P2 | `ReaderMediaButtonBridge` 仅在 `PagedTxtReaderHost.kt:283-288` 激活 | 滚动模式下蓝牙翻页器/线控完全失效 | 把 bridge 生命周期上移到与 `ReaderHardwareKeys.handler` 同一处（`ReaderPlatformEffects`） | 否 |
| **SN-6** | P2 | `WholeBookPageModel` 212 行 + 11 测试，生产 **0 引用** | 死能力：要么误导后续 Agent 以为全书页码已实现，要么白养一份测试；同时它是 B 能力的**唯一现成基础** | 产品裁决后二选一：接入（S09-B 切片）或删除（含测试与 backlog 条目） | 否 |
| **SN-7** | P2 | §9 缺口表 G1、G2、G4、G5、G6、G8、G9、G11 共 8 处关键链路零测试（页码显示一致性、进度↔页序、legacy 热区、边缘死区、媒体键桥、"按键→真的翻页"接力、Markdown/EPUB 真控制器） | 任何后续改动都在无保护状态下进行；SN-1 正是这种无保护的结果 | 按 §11 切片补，**优先补 G1/G8**（它们决定 A 与 C 是否可用） | 否 |
| **SN-13** | **P2** | `androidTest/…/ui/screen/ReaderScreenTest.kt:206-221` 是一条**占位测试**：它在测试内自己渲染 `Text("点击区域").testTag("clickable").clickable { clicked = true }`，然后 `assertHasClickAction()` + `performClick()` + `assertTrue(clicked)`。**完全不触及** `resolveReaderTapAction` / `PagedReaderPageSurface` / `PagedTxtReaderHost`，唯一可能结果就是成功 | 它在测试报告里制造"阅读器点击区域有仪器化覆盖"的假象。S09-B 若照这份清单判断"热区已受保护"，会漏掉全部真实风险 | **删除或改写**为对真实正文表面点击（`performTouchInput { click(position) }` 打在 `PagedReaderPageSurface` 上并断言页码变化）。同类弱断言：`ReaderProgressSheetPolicyTest:10` 的 `progressSheetTargetPercent(63.5f)==63.5f` 是恒等函数自证 | 否 |
| **SN-14** | P3 | 三处"永不可能失败"的断言：`ReaderProgressPreviewPolicyTest:33` `assertTrue(SEEK_PREVIEW_LINGER_MILLIS >= 500L)`（常量比常量）；`ChapterPaginatorBenchmark:90` 用 Kotlin `assert(...)`，**不开 `-ea` 时永不执行**；`CustomTipPlaceholderTest:57` 的 render 用例传 `source = null`，因此章名/章序/真实进度分支从未被走 | 测试计数虚高，给人"已覆盖"的错觉；benchmark 的"断言"实际不存在 | 常量断言删除；benchmark 改用 `org.junit.Assert`；补一条 `source != null` 的渲染用例 | 否 |
| **SN-8** | P3 | `volumeKeyPagingDuringTts` 实判 `showTts`（`ReaderVolumeTurn.kt:57`） | "朗读时音量键仍翻页"在收起面板后行为反转 | 改用 TTS 播放状态，或改设置项文案 | 否 |
| **SN-9** | P3 | `both_next` + `three-zone` 无点击回退（`ReaderTapAction.kt:19,44-53`） | 用户可能以为卡住 | 产品裁决：预览卡与设置项说明该 preset 只在五分区下可回退 | 否 |
| **SN-10** | P3 | 分页引擎热区无 a11y 节点；legacy EPUB 有 `contentDescription="上一章/下一章"` | TalkBack 用户在分页引擎下无法用点击翻页（只能靠音量键/无障碍操作） | 给引擎侧补两个语义按钮或 `semantics { onClick }` | 否 |
| **SN-11** | P3 | `PageIndexStore.kt:19` 注释称 contentKey「TXT / EPUB 均为书籍 id」，实际 `ReaderContentHostPagedBranch.kt:89-93` 是 `bid` / `"$bid\|md"` / `"$bid\|toc=$key"` 三种 | 注释与实现漂移 | 更新注释 | 否 |
| **SN-12** | P3 | `ReaderPageIndexManager` KDoc 称"书签创建、进度持久化、底部进度条等都从这里读"，实际只有书签一处读 | 文档夸大真源地位，易导致后来者误以为可依赖 | 修正 KDoc，或与 SN-1 一并把它变成真源 | 否 |

**注意**：以上**全部不是 S09 新引入**（本任务是只读盘点，未改任何代码）。
它们是本仓库既有状态，S09 的实现切片会把其中一部分作为前置缺陷处理。

---

## 11. 三个以内的最小后续切片

### S09-A｜书签位置正确性（独立，可立即做）

- 范围：`ReaderSheetHost.onAddBookmark` 校验 `pos.bookId == bid`；`clear()` 接入切书/退出；
  无位置时走 `progressPercent`/章偏移回落。
- 文件：`ui/screen/reader/ReaderSheetHost.kt`、`feature/reader/pager/ReaderPageIndexManager.kt`（仅加调用，不改语义）、
  切书处的生命周期挂载点。
- 测试：JVM 新增「别书残留位置不得被用作本书书签锚点」「滚动模式无位置时书签仍可回读」。
- **不依赖**全书页码契约。

### S09-B｜点击热区单一事实源（独立，可立即做）

- 范围：把 legacy 分页 EPUB 的 `clickable` 分区改为调用 `resolveReaderTapAction`（或共享 zone 常量），
  预览卡改为从同一常量生成比例；顺带决定 `both_next`+three-zone 的回退语义（产品裁决 SN-9）。
- 文件：`ui/screen/reader/content/PagedEpubContent.kt`、`ui/screen/reader/sheets/TapZonePreviewCard.kt`、
  `feature/reader/pager/ReaderTapAction.kt`（可能只加常量）。
- 测试：扩 `ReaderTapActionTest` 覆盖五区/死区/边界；新增一条"预览卡比例 == 引擎比例"的结构守卫（**行为优先，勿只测字符串**）；
  **同时处理 SN-13** —— 删掉或改写 `ReaderScreenTest.kt:206-221` 那条自点自测的占位用例，
  否则切片做完，测试报告里仍会出现"点击区域已有仪器化覆盖"的假信号。
- 与 A、C 无文件冲突。

### S09-C｜页码与进度口径（**必须等 B 能力产品契约**，先做 A 部分）

- C-1（可先做，不需契约）：滚动模式补齐与分页引擎一致的页眉页脚信息条（章 + 进度），消除 SN-3。
- C-2（需契约）：全书页码上线 —— 决定"估算章页数是否可显示为具体页码"、"校准瞬间跳几页是否可接受"、
  "是否用页口径替换现有字符 %"、"EPUB 估算章基址是否允许参与显示"。
  契约定了才谈 `WholeBookPageModel` 接线（SN-6 随之解决）。
- 建议把 C-1 与 C-2 拆成两次交付，C-1 不阻塞在裁决上。

**明确不建议合并成一个切片**：A/B/C 三者文件面互不重叠，但 C-2 依赖裁决；
把裁决型工作与修复型工作混在一片会重演 S07 的返工模式。

---

## 12. 必须 Codex / 用户裁决的问题

| # | 问题 | 为什么不能由实现方决定 |
|---|---|---|
| Q1 | 全书是否要显示"第 X / 共 Y 页"？ | 未排版章只能估算；估算页码会在读到时**跳变**（例如 812 → 806）。是否接受 |
| Q2 | 若显示全书页码，与现有字符 % **并存还是替换**？ | 两者口径不同，并存会让用户以为其中一个错 |
| Q3 | 全书页码是否允许参与**跳转**（点进度条落到"第 800 页"）？ | 现有代码纪律是"页码只做显示，跳转以字符偏移为准"（`WholeBookPageModel.kt:18-19`、`PageIndexStore` 同类）。放宽即改数据语义 |
| Q4 | EPUB 的**估算章基址**是否允许出现在用户可见的页码/百分比里？ | 该空间是旧版混合量纲（S07 已确立"禁止改写为真实字符偏移"） |
| Q5 | 滚动模式要不要有"页码"概念？ | 滚动没有页；要么显示"全书页（估算）"，要么只显示 % |
| Q6 | `both_next` 预设是否必须保留回退能力？ | 涉及预设的产品语义定义，不是 bug 判定 |
| Q7 | 音量键/媒体键是否应视为同一能力（"外部翻页按键"），设置项要不要改名？ | 现在"音量键翻页"实际也门控媒体键与蓝牙翻页器 |
| Q8 | 页眉页脚在滚动模式无渲染 —— 是补渲染还是禁用设置项？ | 产品取舍（补渲染涉及正文高度与滚动位置稳定） |
| Q9 | `WholeBookPageModel` 若不接入，是否删除（含 11 个测试与 backlog 条目）？ | 涉及功能计划承诺 |
| Q10 | 分页引擎点击热区要不要补 TalkBack 语义（SN-10）？ | 影响正文无障碍结构，需与 S07 选区无障碍一起看 |

---

## 13. 文件所有权与 SEAM REQUEST

### 本任务

**未修改任何文件**。唯一新增：本报告。
未运行 Gradle、未接触 D-2-B 正在改的 `ReaderSettingsSheet.kt` /
`ReaderSettingsScopeNoticeTest.kt` / `ReaderSettingsSheetTest.kt`（我只读过，未写）。

### 后续切片的所有权建议（避免与窗口 A 冲突）

| 切片 | 需要的文件 | 与 D-2-B 是否冲突 |
|---|---|---|
| S09-A | `ui/screen/reader/ReaderSheetHost.kt`、`feature/reader/pager/ReaderPageIndexManager.kt`、书签相关 VM 测试 | **不冲突**（D-2-B 只碰 `ReaderSettingsSheet.kt` + 两个测试文件） |
| S09-B | `ui/screen/reader/content/PagedEpubContent.kt`、`ui/screen/reader/sheets/TapZonePreviewCard.kt`、`feature/reader/pager/ReaderTapAction.kt` + 其测试 | **不冲突** |
| S09-C1 | 滚动正文分支（`ReaderContentHostPlainTextBranch.kt` / `ReaderContentHostEpubBranch.kt`）+ `PagedReaderHeaderFooter.kt` | ⚠️ 若要把页眉页脚抽成共享 composable，会触碰 `PagedReaderHeaderFooter.kt`，与任何人都不冲突 |
| S09-C2 | `WholeBookPageModel.kt` 接线 + ViewModel + `ReaderChrome.kt` + `CustomTipPlaceholder.kt` | ⚠️ **会触碰设置页文案/模板层**，建议排在 D-2-B 收口之后 |

### SEAM REQUEST

**本任务无 SEAM REQUEST**（只读，未越界改任何文件）。

预告一条：S09-C1 若选择"抽共享信息条"，需要把
`feature/reader/pager/PagedReaderHeaderFooter.kt` 的所有权划给该切片；
它当前不属于 D-2-B，但属于同一 reader 域，需 Codex 串行安排。

---

## 14. 未确认项

| # | 项 | 需要什么 |
|---|---|---|
| U1 | SN-1 的跨书残留是否真的在真机可复现（需要"先读 A 分页书 → 再读 B 滚动书 → 加书签 → 查看书签跳转"） | 真机；本轮禁止操作手机 |
| U2 | SN-4（TXT 滚动音量键不能跨章）的实际触发频率 | 真机；取决于下一章预加载时机 |
| U3 | 三套热区几何在真机上的可感知差异（16% 是否小到常被误触成中央） | 真机 |
| U4 | 滚动模式是否真的**完全没有**页码显示（我只做了 grep 级确认，未逐分支读渲染代码） | 需要通读两个滚动分支的 composable 树，或真机截图 |
| U5 | `{ReadProgress}` 对 EPUB 的实际误差幅度（估算章基址 vs 真实字符） | 需要真机 + 多章 EPUB 对照 |
| U6 | 我未运行任何测试 ⇒ §9 的 @Test 数量是**静态计数**，不代表通过状态 | 由持有构建锁的一方执行 |
| U7 | `PageTurner` 内部是否还有第三套页码口径（我只读了 pager 包外围，未通读 814 行） | 后续切片实施时确认 |
| U8 | `ReaderTapTurnPlan`（`PagedReaderPageSurface.kt:74-91`）与 `resolveReaderTapAction` 的分层关系（后者是纯判定、前者含手动揭示/自动翻页）是否会让热区收敛时漏改一处 | 实施时确认 |

---

## 15. 是否建议进入实现

**建议：分两步放行。**

1. **立即放行 S09-A 与 S09-B**（互不重叠、不依赖产品裁决、且 A 修的是 P1 数据错误）。
2. **S09-C 暂缓**：C-1（滚动模式补信息条）可在裁决 Q5/Q8 后放行；
   C-2（全书页码）必须先拿到 Q1–Q4 的书面产品契约，否则会把"估算页码跳变"这类
   争议直接实现进用户可见界面，重演"先做了再返工"。

**不建议**把 S09 当成一个切片整体放行：它内部四项能力的成熟度、依赖与风险等级完全不同，
其中 B 的一半（全书页码）根本还没接线。

---

## 16. 回传摘要

| 项 | 内容 |
|---|---|
| 任务性质 | 只读盘点，**未运行 Gradle**，未修改任何文件 |
| 四项能力 | A 当前章页码=已接线但**仅分页引擎**；B 全书页码=**未接线模型**（`WholeBookPageModel` 生产 0 引用）+ 字符 % 进度；C 硬件键=链路完整但有 3 处缺口；D 点击区域=**三套互不相同的实现** |
| 发现的 P 级 | **P0 0 / P1 1 / P2 7 / P3 6**，全部为既有问题，非本任务引入 |
| 最严重项 | **SN-1（P1）**：书签读取 `ReaderPageIndexManager.position` 时不校验 `bookId`、`clear()` 从未被调用、只在分页引擎写入 ⇒ 滚动模式书签无定位，跨书可能写入**上一本书的绝对偏移** |
| 格式 × 模式矩阵 | 7 行矩阵（§4）：滚动模式无页码、无翻页热区、无媒体键；legacy 分页 EPUB 无页码且热区 16% |
| 排版失效 | **已核实完整**：字号/行距/段距/视口/旋转/字体/**粗体（经 typefaceKey）**/引擎版本全部进指纹；纸张不参与（正确）。我原本怀疑粗体漏项，实测**不成立** |
| 测试 | 分页/热区/按键的**纯函数层覆盖其实不差**（`ReaderTapActionTest` 9 + Preset 6 + TurnPlan 5 + HardwareKeys 9 + VolumeKeyTurnPolicy 9 + WholeBookPageModel 11 全为真行为测试）。真正的问题有三层：①**接线层零覆盖** —— 渲染页脚的 `PagedReaderHeader/Footer/TipRow` 与把 `controller.pageIndex` 灌进 `PageInfo` 的 `PagedReaderControllerEffects.kt:146-163` 无任何测试；②**占位测试** —— `ReaderScreenTest:206-221` 自点自测，制造"热区已覆盖"的假象（我一度也被误导，已纠正）；③**格式偏斜** —— 真控制器测试只用 `TxtChapterSource`，Markdown/EPUB 分页零覆盖 |
| 方法 | 我的定向读码 + grep + 一个**并行只读子代理**做全库测试盘点（子代理同样未运行 Gradle）；子代理结论我逐条复核，其中一处（`ReaderSettingsRouterTest:265` 的断言方向）我按自己读到的反向断言纠正 |
| 后续切片 | 3 个：S09-A 书签正确性、S09-B 热区单一事实源、S09-C 页码与进度（C-1 可先行，C-2 需契约） |
| 需产品裁决 | 10 问（Q1–Q10），核心是"全书页码是否显示、是否与字符 % 并存、是否允许参与跳转" |
| SEAM REQUEST | 无（本任务）；预告 S09-C1 需要 `PagedReaderHeaderFooter.kt` 所有权 |
| 与窗口 A 冲突 | **无**。未触碰 `ReaderSettingsSheet.kt` 及 D-2-B 的两个测试文件（只读） |
| 仓库状态 | HEAD `5aed593`、`git diff --cached` = 0、`git stash list` = 0 |
| 报告路径 | `docs/coordination/investigations/agent-investigation-s09-qoder.md` |

**未领取下一任务。不实施代码，不做真机。**
