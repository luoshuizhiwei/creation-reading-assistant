/**
 * 三格式的"当前目录项"计算。
 *
 * EPUB：优先用带 fragment 的完整 href 精确匹配（同一文件挂多个目录锚点时可区分），
 * 失败再按去 fragment 的 spine href 取首个匹配——与 epubjs relocated 上报的 href 口径一致。
 * TXT/MD 的当前章由滚动容器内的锚点元素计算（DOM 相关逻辑在阅读器页面内），此处提供
 * 纯函数版本：给定各锚点相对视口顶的偏移，取最后一个已越过阈值的锚点。
 */
import type { EpubTocItem } from "@/types/library";

export function normalizeEpubHref(value?: string): string | undefined {
  return value?.split("#")[0];
}

export function findCurrentTocItem(toc: EpubTocItem[], href?: string): EpubTocItem | undefined {
  if (!href) return undefined;
  const exact = toc.find((item) => item.href === href);
  if (exact) return exact;
  const normalized = normalizeEpubHref(href);
  if (!normalized) return undefined;
  return toc.find((item) => normalizeEpubHref(item.href) === normalized);
}

/**
 * 给定锚点相对滚动容器顶部的偏移（按文档顺序），返回最后一个 offset <= thresholdPx
 * 的锚点 id——即"视口顶之上最近的章节"。全部在视口下方时返回 undefined（文档最顶端）。
 */
export function lastAnchorBeforeThreshold(offsets: Array<{ id: string; offsetTop: number }>, thresholdPx: number): string | undefined {
  let current: string | undefined;
  for (const item of offsets) {
    if (item.offsetTop <= thresholdPx) current = item.id;
    else break;
  }
  return current;
}
