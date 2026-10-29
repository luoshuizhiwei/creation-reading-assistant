/**
 * BD-218：WCAG 1.4.4 文本缩放（Level AA）——整页缩放的按键解析。
 *
 * 应用原生的字号档（appFontScale，0.85–1.4）只改根字号，Tailwind 的 rem 文案跟着走，
 * 但全站 230 处固定 px 字号纹丝不动，而且上限 140%——用户没有任何手段把文字放到
 * 200%。浏览器/Electron 的页面缩放（webContents.setZoomFactor）是 1.4.4 的标准机制：
 * 它缩放布局视口，px/rem 一起放大，底部还能靠滚动到达。
 *
 * 这里只做纯函数：把一次 before-input-event 的按键翻成「下一个 zoomFactor」。
 * Ctrl/Cmd + =/+ 放大，- 缩小，0 还原；按住 Alt 的组合不抢（避免和输入法/系统键冲突）。
 * 上下限 0.5–2：下限防止界面缩成不可读，上限对齐 1.4.4 的 200% 要求。
 */
export const ZOOM_MIN = 0.5;
export const ZOOM_MAX = 2;
export const ZOOM_STEP = 0.1;

/** 保留两位小数，避免 0.1 的浮点累加出现 1.9000000000000001。 */
function round(value: number): number {
  return Math.round(value * 100) / 100;
}

/**
 * @param current 当前 zoomFactor（webContents.getZoomFactor()）
 * @param key     按键（Electron before-input-event 的 input.key）
 * @param modifier 平台对应的 Ctrl（Windows/Linux）或 Cmd（macOS）是否按下
 * @param alt      Alt 是否按下；按着 Alt 的组合一律不接管
 * @returns 下一个 zoomFactor；不是缩放快捷键时返回 null
 */
export function resolveZoomFactor(current: number, key: string, modifier: boolean, alt: boolean): number | null {
  if (!modifier || alt) return null;
  if (key === "=" || key === "+") return Math.min(ZOOM_MAX, round(current + ZOOM_STEP));
  if (key === "-") return Math.max(ZOOM_MIN, round(current - ZOOM_STEP));
  if (key === "0") return 1;
  return null;
}
