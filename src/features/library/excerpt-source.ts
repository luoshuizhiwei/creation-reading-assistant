import type { ID } from "@/types/common";
import type { BookFormat, ExcerptSourceSnapshot, ExcerptTarget } from "@/types/library";

/** 选文最大长度上限，避免超长摘录拖慢收件箱/资料卡存储。 */
export const EXCERPT_MAX_LENGTH = 2000;

/** 同一选文重复提交的防抖窗口（毫秒）。窗口内相同签名视为重复。 */
export const EXCERPT_DEDUP_WINDOW_MS = 3_000;

/**
 * 构建摘录来源时的上下文。由阅读器组件从当前书籍、选中文字、位置等状态填充。
 * 与 {@link ExcerptSourceSnapshot} 分离，便于在纯函数中校验和归一化。
 */
export interface ExcerptBuildContext {
  bookId: ID;
  bookTitle: string;
  bookAuthor?: string;
  format: BookFormat;
  chapterTitle?: string;
  /** 0-1 进度比例 */
  progressPercent?: number;
  /** 原始选中文本（尚未 trim/截断） */
  excerpt: string;
  href?: string;
  cfi?: string;
  charOffset?: number;
  charLength?: number;
  scrollTop?: number;
  /** 覆盖默认 createdAt，测试注入用 */
  now?: () => string;
}

/** trim 后为空（或仅空白）的选文视为无效。 */
export function isEmptyExcerpt(text: string): boolean {
  return text.trim().length === 0;
}

/** 截断到上限，保留完整字符，不破坏多字节边界。 */
export function capExcerpt(text: string, max = EXCERPT_MAX_LENGTH): string {
  const trimmed = text.trim();
  if (trimmed.length <= max) return trimmed;
  return trimmed.slice(0, max);
}

/** 根据进度和章节生成人类可读的位置标签。 */
export function buildLocationLabel(chapterTitle: string | undefined, progressPercent: number | undefined): string {
  const progressLabel = progressPercent !== undefined && Number.isFinite(progressPercent)
    ? `${Math.round(progressPercent * 100)}% 附近`
    : "未知进度";
  return chapterTitle ? `${chapterTitle} · ${progressLabel}` : progressLabel;
}

/**
 * 从上下文构建归一化的 {@link ExcerptSourceSnapshot}。
 * 调用方负责保证 bookId / bookTitle / format 已填充；
 * 此函数负责 trim、截断、生成 locationLabel 和 createdAt。
 */
export function buildExcerptSource(ctx: ExcerptBuildContext): ExcerptSourceSnapshot {
  const createdAt = ctx.now ? ctx.now() : new Date().toISOString();
  return {
    bookId: ctx.bookId,
    bookTitle: ctx.bookTitle,
    bookAuthor: ctx.bookAuthor,
    format: ctx.format,
    chapterTitle: ctx.chapterTitle,
    progressPercent: ctx.progressPercent,
    locationLabel: buildLocationLabel(ctx.chapterTitle, ctx.progressPercent),
    excerpt: capExcerpt(ctx.excerpt),
    href: ctx.href,
    cfi: ctx.cfi,
    charOffset: ctx.charOffset,
    charLength: ctx.charLength,
    scrollTop: ctx.scrollTop,
    createdAt
  };
}

/**
 * 生成摘录签名，用于重复检测。
 * 同一本书、同一段选文（含精确位置）视为同一条摘录。
 */
export function makeExcerptSignature(source: ExcerptSourceSnapshot): string {
  const locator = source.cfi ?? source.href ?? source.charOffset ?? "";
  return `${source.bookId}|${source.excerpt}|${locator}`;
}

/**
 * 判断当前摘录是否与上一次重复。
 * 仅当签名完全相同且在 {@link EXCERPT_DEDUP_WINDOW_MS} 窗口内时返回 true。
 */
export function isDuplicateExcerpt(
  current: ExcerptSourceSnapshot,
  last: { signature: string; at: number } | undefined,
  windowMs: number = EXCERPT_DEDUP_WINDOW_MS
): boolean {
  if (!last) return false;
  const sig = makeExcerptSignature(current);
  if (sig !== last.signature) return false;
  const now = Date.now();
  return now - last.at < windowMs;
}

/** 校验摘录目标是否完整可用。 */
export function isValidExcerptTarget(target: ExcerptTarget): boolean {
  if (target.kind === "inbox") return true;
  return Boolean(target.projectId);
}
