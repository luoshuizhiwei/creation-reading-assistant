# 阅读器翻页流畅度基线（before）— 2026-09-06

> 用途：作为后续「翻页动画 / 手势 / 绘制管线」改进（Phase 2）的对照锚点。
> 本次为**纯测量任务**，未改动任何阅读器源码逻辑，仅新建本文档与 `android/results/` 下的采集脚本与原始数据。

## 1. 被测环境与构建

| 项 | 值 |
|----|----|
| 设备 | Redmi 22081212C，序列号 `c49ac6cf`，Android 15（API 35） |
| 屏幕 | 1220 × 2712，density 480 |
| 刷新率 | 系统 `renderFrameRate = 120.00001`（mode 2，支持 60/90/120Hz，当前活动档 = 120Hz） |
| 被测包 | `com.creationreadingassistant`（debug），versionName `0.5.0`，versionCode 2 |
| 构建时间 | `lastUpdateTime = 2026-09-06 19:23`（当天近期构建） |
| 源码一致性 | 工作树中阅读器源码（`ui/screen/reader/**`、`feature/reader/**`、`PageTurner.kt`、`PagedTxtReaderHost` 等）**未改动**（`git status` 仅显示 shelf/* 与桌面 `src/*` 的既有改动，非本次产生）→ 已装构建的阅读器行为与当前源码一致，直接测量，未重建。 |
| `stay_on_while_plugged_in` | 测试前 = 3，测试后 = 3（全程未改；设备插电常亮，无需临时启用） |
| 测试书籍 | 中性名「测试 EPUB（超大章样本）」含 1 个 1201 页超大章 + 若干小章；「测试 EPUB（净化样本）」3 个小章。未记录真实书名，未改动设备书籍数据。 |

### 采集方法
- 每场景：`dumpsys gfxinfo <pkg> reset` 清零 → `input tap` 触发交互 → `dumpsys gfxinfo <pkg> framestats` 采集。
- 翻页交互（经 `uiautomator dump` 定位实测确认）：上一页 = `tap(90,1356)`，下一页 = `tap(1130,1356)`，中央 `tap(610,1356)` = 唤出/隐藏菜单。EPUB 分页阅读用**点击分区**翻页（非 PageTurner 拖拽）。
- 采集脚本：`android/results/driver_reader_baseline.py`（驱动）、`android/results/parse_reader_framestats.py`（解析，沿用 `sort_retest_framestats.py` 的 `FrameCompleted-IntendedVsync` 口径）。
- 原始 framestats 输出：`android/results/reader-baseline-{steady,crosschapter,firstopen,heavyreload}.txt`。
- **macrobenchmark 未跑**：`benchmarktarget` 包未安装，复跑需重建 benchmark + target 两个 APK，成本高；本基线仅 gfxinfo 手工采集。

## 2. 三场景（+1 补充）流畅度关键数值

下表 P50/P90/P95/P99 为 `dumpsys gfxinfo` **汇总段百分位**（覆盖窗口内全部帧，权威口径）；`Slow*`/`Missed Vsync` 为汇总计数；`max(逐帧)` 来自 framestats 环形缓冲的 `FrameCompleted-IntendedVsync`。

| 场景 | 章/路径 | 总帧 | Janky% | P50 | P90 | P95 | P99 | Slow UI thread | Slow draw cmds | Slow bitmap | Missed Vsync | High input latency | max(逐帧) |
|------|---------|-----:|-------:|----:|----:|----:|----:|---:|---:|---:|---:|---:|---:|
| **A 稳态章内连续翻页**（前进 25 页） | 超大章样本·1201 页大章内 | 521 | **38.4%** | 19ms | 29ms | 31ms | 36ms | **123** | **176** | 0 | 72 | 586 | 46ms |
| **B 跨章翻页**（超大章 → 小章） | 第1章末页 → 第2章 | 29 | 69.0% | 24ms | 31ms | 31ms | 31ms | 14 | 18 | 0 | 4 | 17 | 42ms |
| **C 首次打开书到首屏正文** | 冷开该书（恢复到小章） | 52 | 28.9% | 13ms | 28ms | 36ms | **93ms** | 11 | 10 | 0 | 2 | 45 | **93ms** |
| **F 补充·重负载跨章**（小章 → 1201 页大章重载） | 第2章 → 第1章大章 | 101 | 53.5% | 23ms | 31ms | 32ms | 34ms | 33 | 45 | 0 | 19 | 82 | 45ms |

framestats 环形缓冲逐帧（`FrameCompleted-IntendedVsync`，反映窗口尾段密集翻页）：
- A（120 帧）：P50=32.6 / P90=39.6 / P95=41.1 / P99=42.1 / max=46.0 ms
- B（29 帧）：P50=27.5 / P90=40.0 / max=42.3 ms
- C（52 帧）：P50=12.8 / P90=32.2 / P99=68.4 / max=92.9 ms
- F（101 帧）：P50=31.6 / P90=40.8 / P99=44.3 / max=45.2 ms

> 说明：汇总段百分位基于全部帧的直方图（如 A 覆盖 521 帧），framestats 原始段仅保留最近 N 帧（A 为 120 帧），故 A 的 framestats P50（32ms）高于汇总 P50（19ms）——尾段连续快翻比整窗平均更重。两者均列出，以**汇总段为准**报告。

## 3. 主瓶颈归因

1. **稳态章内翻页本身就不顺畅（核心问题）**：场景 A 在超大章内连续前进翻页，有效帧时 P50≈19ms（≈52fps 等效），**38% 帧超截止**；密集尾段（framestats）P50 升到 32ms、jank 近 100%。即便按 60Hz 预算（16.67ms）衡量，P50=19ms 也已**踩线超标**，更无法满足系统当前的 120Hz（8.33ms 预算）。
2. **瓶颈在主线程组合 + 绘制指令下发，不在 GPU/位图**：A 场景 `Slow issue draw commands = 176` > `Slow UI thread = 123` ≫ `Slow bitmap uploads = 0`；`High input latency = 586` 极高，说明 UI 线程繁忙导致输入事件排队。这与既有诊断「GPU 仅 3–8ms、瓶颈在主线程组合/绘制」一致。
3. **500ms 级尖峰在当前构建不可复现**：全场景最慢单帧仅 **93ms**（场景 C 首开），跨章（B）与超大章重载（F）最慢帧均 ~42–45ms。旧样本 `pageturn-gfxinfo-170700.txt` 的 P50=89ms/P90=500ms（仅 23 帧）**不是稳态特征**，更像一次冷启动/首次分词的过期采样。当前构建的最大尖峰集中在**首次打开书到首屏**（文档加载 + 首次分页 + Compose 首帧，93ms），而非稳态翻页或跨章加载。
4. **跨章/重载无明显惩罚**：进入 1201 页超大章（F）max 仅 45ms，说明分页是**按需/懒排版**（落到章末只排邻近页，未一次性排 1201 页），跨章加载不构成尖峰来源。

**结论**：可复现的流畅度痛点是**稳态章内翻页持续掉帧**（主线程 draw commands + UI thread），而**非**跨章/首开的偶发大尖峰；500ms 级尖峰在当前构建不存在，首开 93ms 是最大单帧。

## 4. G26 排查结论（阅读器玻璃弹层）

只读 grep `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/`：

- **命中**：`ReaderSheetHost.kt`（L12/L149/L166）与 `tts/ReaderTtsBar.kt`（L62/L339）。
- `ReaderSheetHost` 将阅读器**全部** sheet（目录 TOC / 书签 / 笔记 / 净化规则 / 设置 / 主题等）统一包在 `GlassModalBottomSheet` 内（`ReaderScreen` 的 `sheet?.let { GlassModalBottomSheet(...) { when(type){...} } }`）。
- `GlassModalBottomSheet`（`ui/components/GlassDialogs.kt`）= Material3 `ModalBottomSheet`（**Dialog 宿主窗口**）+ `Modifier.glassWindowBlur(LocalGlassPalette.current != null)`。
- `glassWindowBlur`（`ui/theme/GlassWindow.kt`）在 `enabled && GlassCapabilities.isRealBlurSupported`（**API 31+**）时，通过反射对 Dialog 窗口调用 `setBlurBehindRadius` 做**真背景模糊**。本机 API 35 满足条件。

**判定**：阅读器菜单/设置/书签等 sheet **仍使用玻璃模糊弹层**，与书架排序曾因「玻璃模糊 + Dialog 窗口」产生 95ms/141ms 阻塞帧是**同一组合**。风险条件：仅当阅读器主题为玻璃/APPLE 主题（`LocalGlassPalette.current != null`）时真模糊才生效；非玻璃主题下 `glassWindowBlur` 为 no-op，仅剩 Dialog 窗口创建开销。

> 注意：本次三场景均在 sheet 关闭状态下测翻页，**未采集** sheet 打开瞬间的阻塞帧。G26 的弹层打开成本需单独一轮（打开目录/设置 sheet + reset/collect）量化，建议列入 Phase 2 前的一项补充测量。

## 5. 对 Phase 2（绘制管线优化）是否必要的初步判断

**判断：有必要，且数据直接支撑。** 依据：

- 稳态翻页掉帧的主导项是 `Slow issue draw commands`(176) 与 `Slow UI thread`(123)，而 `Slow bitmap uploads = 0`、GPU 侧不忙 → 优化应针对**每帧绘制指令下发与主线程组合**，正是 Phase 2「Picture 录制 / 邻页位图预渲染 / draw 路径去分配」的目标面。
- 翻页动画每帧重新组合/重绘页面内容；把邻页预渲染为位图、用 `Picture` 录制回放、消除 draw 路径上的临时分配，可直接压低 `Slow draw commands` 与 UI 线程帧时，把 P50 从 ~19–32ms 拉向 60Hz（16.67ms）乃至 120Hz（8.33ms）预算。
- 相反，跨章/首开尖峰（≤93ms、偶发单帧）**不是** Phase 2 的主战场；首开 93ms 更偏文档加载/首次分页，属另一条优化线。

优先级建议：Phase 2 聚焦**稳态翻页 draw 路径**；补一轮 **G26 玻璃弹层打开阻塞帧**量化后再决定是否纳入范围。

## 6. 产物清单

- 本文档：`docs/perf/reader-fluency-baseline-2026-09-06.md`
- 原始 framestats：
  - `android/results/reader-baseline-steady.txt`（场景 A）
  - `android/results/reader-baseline-crosschapter.txt`（场景 B）
  - `android/results/reader-baseline-firstopen.txt`（场景 C）
  - `android/results/reader-baseline-heavyreload.txt`（场景 F 补充）
- 采集/解析脚本：`android/results/driver_reader_baseline.py`、`android/results/parse_reader_framestats.py`、`android/results/list_ui_nodes.py`
- 未改动任何阅读器源码；未 commit / push / reset / checkout；`stay_on_while_plugged_in` 保持 3。
