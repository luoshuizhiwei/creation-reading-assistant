# 阅读器翻页流畅度调研：本项目 vs legado-with-MD3

> **状态**：纯调研，**未改任何源码**
> **日期**：2026-09-15
> **触发**：用户反馈「翻页无法做到 MD3 那样流畅」
> **方法**：本机源码核验 + 上游 legado 真源码逐文件阅读 + 既有实测基线复核
> **诚实边界**：本轮**未连设备复现测量**，帧数据引自既有 2026-09-06 / 09-07 采集；要立项需补当前 HEAD 复测

---

## 1. 结论摘要

**用户体感成立，且已能定位到具体路径。** 三条结论：

1. **无动画翻页已基本达标**，动画翻页（slide/cover）是真正的瓶颈。
   既有实测：`none` 效果 P50=13ms、Missed Vsync=0；而 `slide` P50=29ms、Missed Vsync=**353**。
2. **差距不在「有没有做优化」，而在架构层的「缓存机制」不同。**
   本项目 `PageTurner` 已有硬件位图三页缓存 + 空闲预渲染 + 落位覆盖层（相当先进）；
   上游 legado 用的是 **`Picture`/`RenderNode` 录制回放 + 对象池 + 脏标记**，
   两者原理相近，但**常驻性和池化程度不同**。
3. **不能照搬上游实现**：上游是 View/Canvas 体系，本项目是 Compose。
   但「预录制成可回放对象 → 池化常驻 → vsync 对齐」三条原理可移植。

---

## 2. 实测基线（既有数据，来源 `docs/perf/reader-fluency-baseline-2026-09-06.md` + `android/results/`）

**环境**：Redmi 22081212C / Android 15 / 120Hz 活动档（帧预算 8.33ms；60Hz 为 16.67ms）

| 采集文件 | 效果 | P50 | P90 | Missed Vsync | Slow draw | Slow UI | Janky |
|---|---|---:|---:|---:|---:|---:|---:|
| `reader-baseline-steady.txt` | **修前** | 19ms | 29ms | 72 | **176** | 123 | 38.4% |
| `reader-after-steady.txt` | `none` | **13ms** | 24ms | **0** | **36** | 21 | 63.5%* |
| `reader-fix-slide-steady.txt` | `slide` | **29ms** | 36ms | **353** | **532** | 398 | 68.7% |

\* `none` 的 Janky% 偏高是因为 jank 阈值按 120Hz(8.33ms) 判定，而 P50=13ms 在 60Hz 下不 jank。

**读法**：
- `none` 路径：`Slow draw` 176→36，`Missed Vsync` 72→0 ⇒ **优化已生效，60Hz 下可用**。
- `slide` 路径：`Slow draw` 高达 **532**、`Missed Vsync` **353** ⇒ **动画帧仍在重度绘制**。
- 修前基线已归因：瓶颈是 **主线程组合 + 绘制指令下发**（`Slow draw` > `Slow UI` ≫ `Slow bitmap`=0，GPU 不忙）。

---

## 3. 上游 legado 架构（已读源码）

**源码来源**：`CCSSNE/legado` 镜像（= 2026-04-27 原版末版）。关键文件：

```
ui/book/read/page/ReadView.kt
ui/book/read/page/PageView.kt
ui/book/read/page/ContentTextView.kt
ui/book/read/page/entities/TextPage.kt
ui/book/read/page/delegate/PageDelegate.kt
ui/book/read/page/delegate/HorizontalPageDelegate.kt
utils/canvasrecorder/CanvasRecorder.kt
utils/canvasrecorder/CanvasRecorderFactory.kt
utils/canvasrecorder/CanvasRecorderApi29Impl.kt
```

### 3.1 体系：View，不是 Compose

`ReadView : FrameLayout`，持有 `prevPage` / `curPage` / `nextPage` 三个 `PageView` 子 View：

```kotlin
init {
    addView(nextPage); addView(curPage); addView(prevPage)
    prevPage.invisible(); nextPage.invisible()
    curPage.markAsMainView()
    setWillNotDraw(false)
}

override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    pageDelegate?.onDraw(canvas)   // 翻页动画叠加
    autoPager.onDraw(canvas)
}
```

### 3.2 核心：整页录制成 `Picture` / `RenderNode`，动画期只回放

`TextPage` 持有 `canvasRecorder`，绘制时**先 render 再回放**：

```kotlin
fun draw(view: ContentTextView, canvas: Canvas, relativeOffset: Float) {
    if (AppConfig.optimizeRender) {
        render(view)
        canvas.withTranslation(0f, relativeOffset) {
            canvasRecorder.draw(this)     // ← 动画帧只走这一次回放
        }
    } else {
        canvas.withTranslation(0f, relativeOffset) { drawPage(view, this) }
    }
}

fun render(view: ContentTextView): Boolean {
    if (!isCompleted) return false
    return canvasRecorder.recordIfNeeded(view.width, renderHeight) {
        drawPage(view, this)              // ← 仅在脏/尺寸变化时录一次
    }
}
```

`CanvasRecorderApi29Impl`（API 29+）**同时持有 `Picture` 与 `RenderNode`**：

```kotlin
override fun beginRecording(width: Int, height: Int): Canvas {
    init(); renderNode!!.setPosition(0, 0, width, height)
    return picture!!.beginRecording(width, height)
}
override fun endRecording() { picture!!.endRecording(); flushRenderNode() }
override fun draw(canvas: Canvas) {
    if (canvas.isHardwareAccelerated) {
        if (!renderNode!!.hasDisplayList()) flushRenderNode()
        canvas.drawRenderNode(renderNode!!)   // 硬件路径：DisplayList 回放
    } else {
        canvas.drawPicture(picture!!)          // 软件路径
    }
}
```

**对象池**：`PicturePool` / `RenderNodePool`，`recycle()` 归还 —— 避免反复分配。

### 3.3 动画：`Scroller` + `computeScroll()` + vsync 对齐

```kotlin
protected val scroller by lazy { Scroller(readView.context, LinearInterpolator()) }

open fun computeScroll() {
    if (scroller.computeScrollOffset()) {
        readView.setTouchPoint(scroller.currX.toFloat(), scroller.currY.toFloat())
    } else if (isStarted) { onAnimStop(); stopScroll() }
}

protected fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, animationSpeed: Int) {
    val duration = if (dx != 0) (animationSpeed * abs(dx)) / viewWidth
                   else (animationSpeed * abs(dy)) / viewHeight
    scroller.startScroll(startX, startY, dx, dy, duration)
    isRunning = true; isStarted = true; readView.invalidate()
}
```

**高刷专项（2026-05-31）**：`HorizontalPageDelegate` 把重绘请求改为对齐 Choreographer：

```kotlin
// PageDelegate 新增钩子，HorizontalPageDelegate 覆盖为：
readView.postInvalidateOnAnimation()
// 且 computeScroll() 更新触摸点时传 invalidate=false，避免重复 invalidate；
// stopScroll() / abortAnim() 用 isStarted/isRunning 门控，防动画结束后空转
```

### 3.4 异步预渲染线程

`ContentTextView` 用**单线程池**（线程名 `TextPageRender`）预录邻页：

```kotlin
private val renderThread by lazy { Executors.newSingleThreadExecutor { Thread(it, "TextPageRender") } }
fun submitRenderTask() { renderThread.submit(renderRunnable) }

private fun preRenderPage() {
    var invalidate = false
    pageFactory.run {
        if (hasPrev() && prevPage.render(view)) invalidate = true
        if (curPage.render(view)) invalidate = true
        if (hasNext() && nextPage.render(view) && callBack.isScroll) invalidate = true
        // ...
    }
    if (invalidate) { postInvalidate(); pageDelegate?.postInvalidate() }
}
```

### 3.5 性能埋点

- `captureCover` / `prepareCover`：Cover 翻页预录制 —— **ACTION_DOWN 预录当前页，ACTION_UP 只补录下一页**，结果 **500ms 内有效**，超时/换页/旋转回退全量录制。
- `web.book.frames`：上报 `over32ms` / `over50ms` / `maxFrameGap` / `droppedFrames`。

---

## 4. 本项目架构（已读源码）

**关键文件**：`feature/reader/pager/PageTurner.kt`（647 行）、`PagedReaderPageSurface.kt`、`PagedTxtReaderHost.kt`

### 4.1 体系：Compose

`PageTurner` 是通用 Compose 翻页容器（`<Frame : Any>` 泛型），
`PagedReaderPageSurface` 装配，`renderFrame` 回调渲染各页：

```kotlin
PageTurner(
    currentFrame = frame, previousFrame = previous, nextFrame = next,
    effect = ..., speed = pageTurnSpeed, turnRequest = turnRequest.intValue,
    onPrevious = { selRange.value = null; controller.prevPage() },
    onNext = { selRange.value = null; controller.nextPage() },
) { rendered, isCurrent -> renderFrame(rendered, isCurrent) }
```

### 4.2 已有优化（2026-09-07 任务 #10「丝滑版」，相当先进）

1. **三页硬件位图缓存 + 角色轮换**：`rememberGraphicsLayer()` × 3，
   `toImageBitmap()` 得硬件位图（GPU 常驻、绘制零拷贝），
   settle 时按方向轮换引用 —— 每次翻页**只新捕获 next 一张**，旧引用交 GC。
2. **空闲期预渲染**：settle 后 `LaunchedEffect` 在空闲帧里 record→toImageBitmap，
   下次拖拽**起点零捕获帧**。
3. **落位提交覆盖层** `commitOverlay`：滑入页位图盖在 live 页上 1~2 帧，遮住提交帧重渲染。
4. **动画参数**：`Animatable dx` + `tween(FastOutSlowInEasing)`，
   时长 = `speed × 剩余位移比例`（屏越宽越恒定）；
   `VelocityTracker` 轻扫即翻（位移 OR 速度），settle 以 fling 速度作 `initialVelocity`。

### 4.3 页面绘制已高度优化

`PageCanvas` 用 `drawIntoCanvas { nativeCanvas }` **逐行自绘**（非 Compose Text），并有：

- `PageDrawPlan` **逐行不变量预计算**：baseline / boxH / top / basePaint / markerWidth 只在
  页/字体/主题/布局变化时算一次，**绘制期按索引取指令，杜绝逐帧 `measureText`**。
- `TextPaint` / `Paint` / `fontMetrics` 全部 `remember` 复用 ⇒ **draw 路径零分配**。

⇒ 结论：**单帧绘制本身已优化到位**，问题在「何时触发绘制」与「缓存是否常驻」。

---

## 5. 核心差距归因（按重要性）

| # | 差距 | 上游做法 | 本项目现状 | 影响 |
|---|---|---|---|---|
| **1** | **动画帧的绘制成本未被彻底消除** | page 录完后动画期每帧仅 `drawRenderNode` 一次回放 | 已用位图，但 `engaged`(拖拽中) 与首翻兜底走 `ensureBitmaps()` 现录；且 **pre-render 窗口只在 settle 后几帧挂载**（源码注释：稳态空闲帧不挂载，避免布局拉高 P50） | slide P50=29ms、Slow draw=532 |
| **2** | **无对象池 / 无 RenderNode 级复用** | `PicturePool` / `RenderNodePool` 池化，`recycle()` 归还 | 每次 `toImageBitmap()` 产新硬件位图、旧引用交 GC；稳态 3 张全屏位图常驻（≈10MB+/张） | GC 压力 + 分配抖动 |
| **3** | **帧调度未显式对齐 vsync** | 横向动画专用 `postInvalidateOnAnimation()` 对齐 Choreographer 吃满 90/120Hz | 走 Compose `Animatable`（Recomposer 帧循环） | 120Hz 档下 P50=29ms ≈ 每 3~4 帧出一帧 |

**补充观察**：本项目 `PageTurner` 用 `GraphicsLayer.toImageBitmap()`，该 API 在 API 26+
返回 **HARDWARE 位图**（源码注释已正确指出不能绘入软件 Canvas）。但一次**全屏**
`toImageBitmap()` 是 GPU→位图拷贝，成本不低；上游的 `Picture`/`RenderNode` 回放
是**零拷贝**的显示列表回放。这是机制层的本质差异。

---

## 6. 可移植的优化方向（Compose 适配，按性价比）

| 优先级 | 方案 | 依据 | 风险 |
|---|---|---|---|
| **A** | **扩大预渲染窗口**：不限于 settle 后几帧，改为「静止态异步常驻」，用后台线程做邻页 compose/layout/record | 上游 `TextPageRender` 单线程池常驻预渲染 | 需压住空闲帧成本；本仓注释已指出「稳态空闲帧挂载会拉高 P50」，须先解决布局成本 |
| **A** | **补齐 vsync 对齐**：确认 `Animatable` 帧循环在 120Hz 下的行为，必要时改用 `withFrameNanos` 自驱 + `Choreographer` 对齐 | 上游 2026-05-31 专项 | 需实测，Compose 内部已有帧循环，收益待验证 |
| **B** | **引入可回收的录制对象**：评估 `GraphicsLayer` 池化 / 复用（避免每次 `toImageBitmap()` 新分配） | 上游 `PicturePool`/`RenderNodePool` | Compose `GraphicsLayer` 是否支持复用需查 API |
| **B** | **覆盖 `engaged`（拖拽中）路径**：拖拽开始即确保三页位图就绪，杜绝动画中途现录 | 上游 ACTION_DOWN 预录当前页 | 已有 `ensureBitmaps()` 兜底，需量化未命中率 |
| **C** | **补性能埋点**：当前无 `over32ms`/`over50ms` 类帧上报 | 上游 `web.book.frames` | 低风险，但需接入 |

---

## 7. 建议的下一步（未执行，待确认）

**先测量，再优化。** 现有数据是 2026-09-06/07 采集，源码其后有改动，不能直接作为立项依据。

建议顺序：
1. **复测当前 HEAD 构建的 `slide`/`cover` 稳态翻页**（gfxinfo framestats，同口径），
   确认 P50=29ms / Missed Vsync=353 是否仍成立。
2. 若成立，用 **Perfetto/systrace** 抓一次 slide 动画窗口，看主线程热点是
   Compose 组合、`toImageBitmap` 拷贝，还是 draw 指令 —— **定位到具体函数再动手**。
3. 按 §6 的 A 级方案小步验证，每步复测一次。

---

## 8. 参考

- 本仓实测基线：`docs/perf/reader-fluency-baseline-2026-09-06.md`
- 原始采集：`android/results/reader-baseline-*.txt`、`reader-after-*.txt`、`reader-fix-slide-*.txt`
- 上游源码：`github.com/CCSSNE/legado`（镜像）/ `github.com/gedoor/legado`（原仓）
- 高刷专项记录：`legado` 仓库「新功能踩坑记录-阅读页与翻页」

---

*本报告为只读调研产出，未修改任何源码，未 commit / push。*
