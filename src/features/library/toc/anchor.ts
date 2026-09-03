/**
 * TXT/MD 滚动阅读的"章节 + 章内比例"进度锚定。
 *
 * 只存 scrollTop 的恢复在字号/行距/页边距变化后会漂移（像素布局变了，
 * 字符位置没变）。这里把滚动位置换算成字符坐标保存，恢复时再按新布局换算回来：
 * - 保存：当前章 = 滚动位置之上最近的锚点；章内比例 f =（scrollTop - 章顶）/ 章高度；
 *   charOffset = 章起 + f × 章字数。
 * - 恢复：按 charOffset（或保存的 f）反解比例，scrollTop = 新布局章顶 + f × 新布局章高度。
 * 章高度随排版近似等比缩放，f 保持稳定，恢复位置不再漂移。
 *
 * span.top 为锚点元素在滚动内容坐标系中的位置（元素文档顶 - 容器文档顶，含已滚距离），
 * 由阅读器页面测量（见 ReaderPage 的 ensureSpans）；本模块全部为纯函数。
 */

export interface AnchorSpan {
  /** 目录条目 id（如 txt-chapter-2 或 MD 标题 slug） */
  id: string;
  /** 锚点元素顶边在滚动内容坐标系中的位置（px） */
  top: number;
  /** 锚点标题（用于进度展示） */
  title: string;
  /** TXT 章节字符范围；MD 标题没有精确范围，可不传（charOffset 退化为全局比例） */
  charStart?: number;
  charEnd?: number;
}

export interface TextAnchor {
  /** 目录条目 id */
  chapterRef?: string;
  /** 章节标题路径（当前仅一层） */
  headingPath?: string[];
  /** 全文字符偏移（MD 为估算值） */
  charOffset?: number;
  /** 章内比例（0-1），恢复时的首选依据 */
  fractionInChapter: number;
}

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

/** 视口顶之上最近的锚点视为当前章；此阈值与目录高亮的判定保持一致。 */
const CURRENT_ANCHOR_PEEK = 40;

/**
 * 保存：把滚动位置换算为文本锚点。
 * @param scrollHeight/clientHeight 当前布局的滚动尺寸
 * @param contentLength 全文字符数
 */
export function computeTextAnchor(spans: AnchorSpan[], scrollTop: number, scrollHeight: number, clientHeight: number, contentLength: number): TextAnchor {
  const maxScroll = Math.max(1, scrollHeight - clientHeight);
  const globalFraction = clamp01(scrollTop / maxScroll);
  let index = -1;
  for (let i = 0; i < spans.length; i++) {
    if (spans[i].top <= scrollTop + CURRENT_ANCHOR_PEEK) index = i;
    else break;
  }
  if (index === -1) {
    return { fractionInChapter: globalFraction, charOffset: Math.round(globalFraction * contentLength) };
  }
  const span = spans[index];
  const spanBottom = index + 1 < spans.length ? spans[index + 1].top : Math.max(span.top + 1, scrollHeight);
  const fractionInChapter = spanBottom > span.top ? clamp01((scrollTop - span.top) / (spanBottom - span.top)) : 0;
  let charOffset: number | undefined;
  if (typeof span.charStart === "number" && typeof span.charEnd === "number" && span.charEnd > span.charStart) {
    charOffset = span.charStart + Math.round(fractionInChapter * (span.charEnd - span.charStart));
  } else {
    charOffset = Math.round(globalFraction * contentLength);
  }
  return { chapterRef: span.id, headingPath: [span.title], charOffset, fractionInChapter };
}

/**
 * 恢复：把保存的文本锚点换算回新布局下的滚动位置。返回 undefined 表示无可用锚点
 * （调用方回退 scrollTop 恢复）。
 */
export function computeAnchorScrollTop(
  spans: AnchorSpan[],
  anchor: { chapterRef?: string; charOffset?: number; fractionInChapter?: number },
  scrollHeight: number,
  clientHeight: number,
  contentLength: number
): number | undefined {
  const maxScroll = Math.max(0, scrollHeight - clientHeight);
  const index = anchor.chapterRef ? spans.findIndex((span) => span.id === anchor.chapterRef) : -1;
  if (index >= 0) {
    const span = spans[index];
    const spanBottom = index + 1 < spans.length ? spans[index + 1].top : Math.max(span.top + 1, scrollHeight);
    let fraction = anchor.fractionInChapter;
    if (
      (fraction === undefined || fraction <= 0) &&
      typeof span.charStart === "number" &&
      typeof span.charEnd === "number" &&
      typeof anchor.charOffset === "number" &&
      span.charEnd > span.charStart
    ) {
      fraction = clamp01((anchor.charOffset - span.charStart) / (span.charEnd - span.charStart));
    }
    const safeFraction = clamp01(fraction ?? 0);
    return Math.round(Math.min(maxScroll, span.top + safeFraction * Math.max(0, spanBottom - span.top)));
  }
  // 无章节锚点：全局字符比例恢复（纯文本或锚点丢失时）
  if (typeof anchor.charOffset === "number" && contentLength > 0) {
    return Math.round(clamp01(anchor.charOffset / contentLength) * maxScroll);
  }
  return undefined;
}

/** 当前章 id：滚动位置之上最近的锚点；文档最顶端（序章区）返回 undefined。 */
export function currentAnchorIdFromSpans(spans: AnchorSpan[], scrollTop: number): string | undefined {
  let current: string | undefined;
  for (const span of spans) {
    if (span.top <= scrollTop + CURRENT_ANCHOR_PEEK) current = span.id;
    else break;
  }
  return current;
}
