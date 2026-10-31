/**
 * BD-223（WCAG 1.4.4「放大到 200% 不丢内容与功能」）：懒加载路由 chunk 失败后的恢复通道。
 *
 * 为什么需要专门一条通道，而不是「就地重试」——下面是本仓实测（scripts/audit-lazy-recovery.mjs
 * 复刻的就是这条序列，注入方式是把产物里的路由 chunk 改名）：
 *   ① 路由 chunk 的一次读取失败会让 import() 抛
 *      "Failed to fetch dynamically imported module: file:///…/SettingsPage-xxx.js"，
 *      ErrorBoundary 接管整屏；
 *   ② 把文件放回原处、再进同一屏：0ms 直接抛，而且**不再发任何请求**（requestfailed 计数停在 1）
 *      ——Chromium 的模块图把这条 specifier 的失败永久记仇，同一个文档里没有任何重试路径；
 *   ③ 同一条 URL 换一个 specifier（加 ?r=2）：3ms 内成功。
 * 结论：这类失败只能靠**重新载入文档**（换一个模块图）恢复。旧实现把「重试当前页面」做成
 * setScreen("projects" / "library")，等于把用户甩去另一屏；而整应用重载又落回默认的项目书架，
 * 用户得自己把刚才那一屏重新点回来。放大到 200% 时这种失败更常见（视口收窄、路由切换更密），
 * 于是「缩放 200% 不丢内容与功能」在这条链上被打破。
 *
 * 本模块因此只做两件小事：
 *   - 把「这是不是一次 chunk 加载失败」判出来（只有这类失败才需要重载，普通渲染异常就地重试有效）；
 *   - 把「用户此刻在哪一屏」写进 URL 片段，让重载后的启动序列回到同一屏。
 * 片段只在下一次启动读一次并立刻擦掉：留在地址栏里会把之后每一次冷启动都劫持到那一屏。
 */
const RETURN_HASH_KEY = "return-to";

/** 各家内核的 chunk 加载失败文案：Chromium 两种 + 「预载 CSS 没读到」（Vite 的 preload 包装）。 */
const CHUNK_FAILURE_MARKERS = [
  "Failed to fetch dynamically imported module",
  "error loading dynamically imported module",
  "Unable to preload CSS for"
];

type MaybeError = { message?: string } | null | undefined;

/** 判「这次异常是不是懒加载 chunk 没读到」——是，就必须走重载通道；不是，就地重试才有意义。 */
export function isChunkLoadFailure(error?: MaybeError): boolean {
  const message = error?.message ?? "";
  if (!message) return false;
  return CHUNK_FAILURE_MARKERS.some((marker) => message.includes(marker));
}

/** 把「失败时用户所在的那一屏」记到 URL 片段里，供重载后的启动序列读回。 */
export function rememberReturnScreen(screen: string): void {
  const url = new URL(window.location.href);
  url.hash = `${RETURN_HASH_KEY}=${encodeURIComponent(screen)}`;
  window.history.replaceState(window.history.state, "", url.toString());
}

/**
 * 重新载入文档——换掉模块图，这是 chunk 失败唯一的出路（见顶部实测）。单独成一个函数只为两件事：
 * 一是有名字的注入点（jsdom 的 window.location.reload 不可改写，账本测试没法直接验这颗按钮）；
 * 二是让「重载」这件事在代码里只有一处写法，不至于哪天换成 Electron 的重载 API 时漏改。
 */
export function reloadDocument(): void {
  window.location.reload();
}

/**
 * 读出并立刻清除重载目标。`allowed` 由调用方把合法屏幕清单传进来（而不是本模块去 import 状态层），
 * 这样这个文件保持零依赖叶子，状态层反过来用它时不会形成 import 环。
 */
export function takeReturnScreen<T extends string>(allowed: readonly T[]): T | null {
  const raw = window.location.hash.replace(/^#/, "");
  if (!raw.startsWith(`${RETURN_HASH_KEY}=`)) return null;
  const value = decodeURIComponent(raw.slice(RETURN_HASH_KEY.length + 1));
  const url = new URL(window.location.href);
  url.hash = "";
  window.history.replaceState(window.history.state, "", url.toString());
  return (allowed as readonly string[]).includes(value) ? (value as T) : null;
}
