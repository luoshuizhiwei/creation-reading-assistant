import type { ExcerptResult, ExcerptSourceSnapshot } from "@/types/library";

/**
 * 摘录命令映射共享深模块。
 *
 * 由 renderer 的生产 destination（excerpt-destination-impl.ts）和
 * Electron contract（reader-excerpt-contract.ts）共用，避免重复实现
 * sourceToSnapshot / 命令构造 / entityId 读取 / 失败归一化逻辑。
 *
 * 接收方实现 ExcerptCommandExecutor：
 * - renderer 注入 getDesktopApi().creation（真实 IPC）；
 * - contract 注入 workspace.transact/read（真实 better-sqlite3 store）。
 *
 * 不直接依赖 electron / better-sqlite3，可被任意 JS 环境复用。
 */

/** 摘录命令：与 IPC 命令结构一一对应，但与 electron/preload 解耦。 */
export interface InboxCreateCommand {
  type: "inbox.create";
  title: string;
  body: string;
  kind: string;
  status: string;
  tags: string[];
  source: Record<string, unknown>;
}

export interface CardCreateCommand {
  type: "card.create";
  projectId: string;
  kind: string;
  title: string;
  tags: string[];
  content: Record<string, unknown>;
}

export interface InboxCreateResult {
  itemId?: string;
}

export interface CardCreateResult {
  entityId?: string;
  commandType?: string;
  cardId?: string;
}

/**
 * 命令执行器：由调用方注入真实实现。
 *
 * - renderer 生产实现：通过 getDesktopApi().creation 调用 preload IPC；
 * - contract：通过 workspace.transact 调用真实 better-sqlite3 store；
 * - 测试：通过 vi.fn() 注入 mock。
 */
export interface ExcerptCommandExecutor {
  inboxCreate(command: InboxCreateCommand): Promise<InboxCreateResult>;
  cardCreate(command: CardCreateCommand): Promise<CardCreateResult>;
}

/** 合法资料卡类型：reference（不是 excerpt）。 */
export const EXCERPT_CARD_KIND = "reference";

/** 收件箱条目类型：note（札记）。 */
export const EXCERPT_INBOX_KIND = "note";

/** 收件箱条目状态：inbox（待处理）。 */
export const EXCERPT_INBOX_STATUS = "inbox";

/**
 * 把来源快照转换为可序列化的 plain object。
 * 保留所有字段，contract 和生产实现共用此函数。
 */
export function sourceToSnapshot(source: ExcerptSourceSnapshot): Record<string, unknown> {
  return {
    bookId: source.bookId,
    bookTitle: source.bookTitle,
    bookAuthor: source.bookAuthor,
    format: source.format,
    chapterTitle: source.chapterTitle,
    progressPercent: source.progressPercent,
    locationLabel: source.locationLabel,
    excerpt: source.excerpt,
    href: source.href,
    cfi: source.cfi,
    charOffset: source.charOffset,
    charLength: source.charLength,
    scrollTop: source.scrollTop,
    createdAt: source.createdAt
  };
}

/**
 * 构造 inbox.create 命令。
 * 标题、tags、source 都按统一规则生成，renderer 和 contract 共用。
 */
export function buildInboxCreateCommand(source: ExcerptSourceSnapshot): InboxCreateCommand {
  return {
    type: "inbox.create",
    title: `摘录：${source.bookTitle}`,
    body: source.excerpt,
    kind: EXCERPT_INBOX_KIND,
    status: EXCERPT_INBOX_STATUS,
    tags: ["摘录", source.format.toUpperCase()],
    source: sourceToSnapshot(source)
  };
}

/**
 * 构造 card.create 命令。
 * 资料卡类型固定为 reference（合法内置类型，不是 excerpt）。
 */
export function buildCardCreateCommand(projectId: string, source: ExcerptSourceSnapshot): CardCreateCommand {
  return {
    type: "card.create",
    projectId,
    kind: EXCERPT_CARD_KIND,
    title: `摘录：${source.bookTitle}`,
    tags: ["摘录", source.format.toUpperCase()],
    content: sourceToSnapshot(source)
  };
}

/**
 * 执行 inbox.create 并归一化结果。
 *
 * 失败时返回 success=false + error，绝不假成功：
 * - IPC 抛错 → 透传错误信息；
 * - 返回空 itemId → "收件箱创建返回了空 ID。"
 */
export async function executeSaveToInbox(
  executor: ExcerptCommandExecutor,
  source: ExcerptSourceSnapshot
): Promise<ExcerptResult> {
  try {
    const command = buildInboxCreateCommand(source);
    const result = await executor.inboxCreate(command);
    if (!result.itemId) {
      return { success: false, error: "收件箱创建返回了空 ID。" };
    }
    return { success: true, itemId: result.itemId };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : String(error) };
  }
}

/**
 * 执行 card.create 并归一化结果。
 *
 * 关键修复：读取 entityId（不是 cardId）。
 * card.create 命令通过 StructureCommandResult.entityId 返回新建卡片 ID；
 * 旧字段 cardId 已废弃，即使 IPC 错误返回 cardId 也不认。
 *
 * 失败时返回 success=false + error，绝不假成功：
 * - IPC 抛错 → 透传错误信息；
 * - 返回空 entityId → "资料卡创建返回了空 ID。"
 */
export async function executeSaveToProjectCard(
  executor: ExcerptCommandExecutor,
  projectId: string,
  source: ExcerptSourceSnapshot
): Promise<ExcerptResult> {
  try {
    const command = buildCardCreateCommand(projectId, source);
    const result = await executor.cardCreate(command);
    // 只读 entityId；旧字段 cardId 即使存在也忽略
    const entityId = result.entityId;
    if (!entityId) {
      return { success: false, error: "资料卡创建返回了空 ID。" };
    }
    return { success: true, itemId: entityId };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : String(error) };
  }
}
