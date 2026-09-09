package com.creationreadingassistant.ui.screen.reader

/**
 * 底部进度滑块的「当前章节」预览文案（纯函数，供进度预览标签复用）。
 *
 * ## 死代码处置说明（T12-c）
 * 本文件此前还包含一个 `ZoomableProgressScrubber` @Composable（pinch 缩放 + 预览面板 +
 * 自绘 Canvas 刻度轨道）以及其附属的 `ChapterHeat` 数据类与 `DisabledAlpha` 常量。
 * 经全仓 grep 确证：该可组合项**没有任何生产路径引用**（既不在 [ReaderChrome] /
 * [ReaderInteractionLayer] 的组合树中，也不被任何 preview/其它组件调用），属确定性死代码。
 *
 * 更关键的是它与 [ReaderChrome] 内实时的 `ReaderBottomActions` 进度滑块**共用同一个
 * testTag `reader-progress-scrubber`**：一旦二者在某条路径下同屏组合，instrumented 的
 * `onNodeWithTag("reader-progress-scrubber")` 会因命中 2 个节点而抛歧义匹配异常
 * （见 ReaderBottomActionsTest）。因此已将该死可组合项连同 `ChapterHeat` / `DisabledAlpha`
 * 一并删除，从根源消除重复 testTag 歧义隐患。
 *
 * 仅保留下方被单元测试（ZoomableProgressScrubberTest）覆盖的纯标签函数——它不携带任何
 * testTag、不参与组合，与上述歧义隐患无关；保留它以维持既有单测覆盖不弱化。
 */
internal fun chapterProgressPreviewLabel(progress: Float): String =
    "本章 · ${progress.coerceIn(0f, 100f).toInt()}%"
