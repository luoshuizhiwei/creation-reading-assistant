/**
 * 灵感 / 划线 / 书签数据层（从 electron/main/index.ts 拆出，纯移动式重构）。
 *
 * 职责：inspirations.json / highlights.json / bookmarks.json 的读写与结构规范化。
 * 行为逐字保持，仅把顶层声明导出供主进程入口与同步层使用。
 */
import type { BookmarkItem, HighlightItem } from "../../src/types/library";
import type {
  AddInspirationVariantInput,
  CreateInspirationInput,
  InspirationItem,
  InspirationSourceSnapshot,
  InspirationSourceLocation,
  InspirationStatus,
  InspirationType,
  InspirationVariant,
  UpdateInspirationInput
} from "../../src/types/inspiration";
import {
  bookmarksPath,
  currentDeviceId,
  highlightsPath,
  inspirationsPath,
  isRecord,
  makeId,
  nextSyncMetadata,
  normalizeTags,
  now,
  optionalString,
  readJson,
  requireString,
  withFileLock,
  withSyncMetadata,
  writeJson,
  writeLog
} from "./storage";

export function normalizeInspirationType(value: unknown): InspirationType {
  return value === "plot" || value === "character" || value === "world" || value === "scene" || value === "line" || value === "trope" || value === "note"
    ? value
    : "note";
}

export function normalizeInspirationStatus(value: unknown): InspirationStatus {
  return value === "inbox" || value === "usable" || value === "polished" || value === "used" || value === "archived" ? value : "inbox";
}

export function normalizeSourceLocation(value: unknown): InspirationSourceLocation | undefined {
  if (!isRecord(value)) return undefined;
  return {
    format: value.format === "txt" || value.format === "md" || value.format === "epub" ? value.format : undefined,
    progressPercent: typeof value.progressPercent === "number" ? Math.min(100, Math.max(0, value.progressPercent)) : undefined,
    excerpt: optionalString(value.excerpt),
    href: optionalString(value.href),
    cfi: optionalString(value.cfi),
    scrollTop: typeof value.scrollTop === "number" ? Math.max(0, value.scrollTop) : undefined,
    createdFrom:
      value.createdFrom === "reader-selection" || value.createdFrom === "reader-note" || value.createdFrom === "manual" ? value.createdFrom : undefined
  };
}

export function normalizeInspirationSource(value: unknown): InspirationSourceSnapshot | undefined {
  if (!isRecord(value)) return undefined;
  const createdAt = optionalString(value.createdAt) ?? now();
  return {
    bookId: optionalString(value.bookId),
    bookTitle: optionalString(value.bookTitle),
    bookAuthor: optionalString(value.bookAuthor),
    format: value.format === "txt" || value.format === "md" || value.format === "epub" ? value.format : undefined,
    chapterTitle: optionalString(value.chapterTitle),
    locationLabel: optionalString(value.locationLabel),
    progressPercent: typeof value.progressPercent === "number" ? Math.min(100, Math.max(0, value.progressPercent)) : undefined,
    excerpt: optionalString(value.excerpt),
    href: optionalString(value.href),
    cfi: optionalString(value.cfi),
    scrollTop: typeof value.scrollTop === "number" ? Math.max(0, value.scrollTop) : undefined,
    createdFrom:
      value.createdFrom === "reader-selection" || value.createdFrom === "reader-note" || value.createdFrom === "manual" ? value.createdFrom : undefined,
    createdAt
  };
}

export function normalizeInspirationVariant(value: unknown): InspirationVariant | undefined {
  if (!isRecord(value)) return undefined;
  const content = optionalString(value.content);
  if (!content) return undefined;
  const kind =
    value.kind === "polish" || value.kind === "expand" || value.kind === "platform-style" || value.kind === "conflict" || value.kind === "humanize"
      ? value.kind
      : "polish";
  return {
    id: optionalString(value.id) ?? makeId("variant"),
    kind,
    content,
    prompt: optionalString(value.prompt) ?? "",
    model: optionalString(value.model) ?? "",
    createdAt: optionalString(value.createdAt) ?? now()
  };
}

export function normalizeInspirationItem(value: unknown): InspirationItem | undefined {
  if (!isRecord(value)) return undefined;
  let title = optionalString(value.title);
  if (!title) {
    title = "未命名灵感";
    void writeLog("warn", "Inspiration item had empty title and was normalized with a fallback title.", {
      id: optionalString(value.id) ?? "unknown"
    });
  }
  const createdAt = optionalString(value.createdAt) ?? now();
  return withSyncMetadata(
    {
      id: optionalString(value.id) ?? makeId("insp"),
      title,
      body: typeof value.body === "string" ? value.body : "",
      type: normalizeInspirationType(value.type),
      status: normalizeInspirationStatus(value.status),
      tags: normalizeTags(value.tags),
      platformTags: normalizeTags(value.platformTags),
      source: normalizeInspirationSource(value.source),
      sourceBookId: optionalString(value.sourceBookId),
      sourceLocation: normalizeSourceLocation(value.sourceLocation),
      variants: Array.isArray(value.variants) ? value.variants.map(normalizeInspirationVariant).filter((item): item is InspirationVariant => Boolean(item)) : [],
      createdAt,
      updatedAt: optionalString(value.updatedAt) ?? createdAt,
      revision: typeof value.revision === "number" ? value.revision : undefined,
      deviceId: optionalString(value.deviceId),
      deletedAt: optionalString(value.deletedAt)
    },
    createdAt
  );
}

export async function readInspirations(options: { includeDeleted?: boolean } = {}): Promise<InspirationItem[]> {
  const raw = await readJson<unknown>(inspirationsPath(), { version: 1, updatedAt: now(), items: [] });
  const items = isRecord(raw) && Array.isArray(raw.items) ? raw.items : Array.isArray(raw) ? raw : [];
  return items
    .map(normalizeInspirationItem)
    .filter((item): item is InspirationItem => Boolean(item))
    .filter((item) => options.includeDeleted || !item.deletedAt)
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}

export async function writeInspirations(items: InspirationItem[]): Promise<InspirationItem[]> {
  const sorted = [...items].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  await writeJson(inspirationsPath(), { version: 1, updatedAt: now(), items: sorted });
  return sorted;
}

export async function createInspiration(input: CreateInspirationInput): Promise<InspirationItem> {
  const title = requireString(input.title, "灵感标题");
  const item: InspirationItem = {
    id: makeId("insp"),
    title,
    body: typeof input.body === "string" ? input.body : "",
    type: normalizeInspirationType(input.type),
    status: normalizeInspirationStatus(input.status),
    tags: normalizeTags(input.tags),
    platformTags: normalizeTags(input.platformTags),
    source: normalizeInspirationSource(input.source),
    sourceBookId: optionalString(input.sourceBookId),
    sourceLocation: normalizeSourceLocation(input.sourceLocation),
    variants: [],
    revision: 1,
    deviceId: currentDeviceId(),
    createdAt: now(),
    updatedAt: now()
  };
  // Acquire file-level lock to prevent read-write race with concurrent IPC handlers
  await withFileLock(inspirationsPath(), async () => {
    await writeInspirations([item, ...(await readInspirations())]);
  });
  return item;
}

export async function readInspiration(id: string): Promise<InspirationItem | undefined> {
  return (await readInspirations()).find((item) => item.id === id);
}

export async function updateInspiration(id: string, input: UpdateInspirationInput): Promise<InspirationItem> {
  return withFileLock(inspirationsPath(), async () => {
    const items = await readInspirations();
    const current = items.find((item) => item.id === id);
    if (!current) throw new Error("未找到灵感。");
    const next: InspirationItem = {
      ...current,
      title: typeof input.title === "string" ? input.title.trim() : current.title,
      body: typeof input.body === "string" ? input.body : current.body,
      type: input.type ? normalizeInspirationType(input.type) : current.type,
      status: input.status ? normalizeInspirationStatus(input.status) : current.status,
      tags: Array.isArray(input.tags) ? normalizeTags(input.tags) : current.tags,
      platformTags: Array.isArray(input.platformTags) ? normalizeTags(input.platformTags) : current.platformTags,
      source: input.source === undefined ? current.source : normalizeInspirationSource(input.source),
      sourceBookId: input.sourceBookId === undefined ? current.sourceBookId : optionalString(input.sourceBookId),
      sourceLocation: input.sourceLocation === undefined ? current.sourceLocation : normalizeSourceLocation(input.sourceLocation),
      variants: Array.isArray(input.variants)
        ? input.variants.map(normalizeInspirationVariant).filter((item): item is InspirationVariant => Boolean(item))
        : current.variants,
      ...nextSyncMetadata(current),
      updatedAt: now()
    };
    await writeInspirations(items.map((item) => (item.id === id ? next : item)));
    return next;
  });
}

export async function deleteInspiration(id: string): Promise<InspirationItem[]> {
  return withFileLock(inspirationsPath(), async () => {
    const timestamp = now();
    const items = await readInspirations({ includeDeleted: true });
    const nextItems = items.map((item) => (item.id === id ? { ...item, ...nextSyncMetadata(item), deletedAt: timestamp, updatedAt: timestamp } : item));
    await writeInspirations(nextItems);
    return nextItems.filter((item) => !item.deletedAt).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  });
}

export async function addInspirationVariant(id: string, input: AddInspirationVariantInput): Promise<InspirationItem> {
  return withFileLock(inspirationsPath(), async () => {
    const items = await readInspirations();
    const current = items.find((item) => item.id === id);
    if (!current) throw new Error("未找到灵感。");
    const content = requireString(input.content, "AI 候选内容");
    const variant: InspirationVariant = {
      id: makeId("variant"),
      kind:
        input.kind === "polish" || input.kind === "expand" || input.kind === "platform-style" || input.kind === "conflict" || input.kind === "humanize"
          ? input.kind
          : "polish",
      content,
      prompt: typeof input.prompt === "string" ? input.prompt : "",
      model: typeof input.model === "string" ? input.model : "",
      createdAt: now()
    };
    const nextStatus: InspirationStatus = current.status === "inbox" || current.status === "usable" ? "polished" : current.status;
    const next = { ...current, status: nextStatus, variants: [variant, ...current.variants], ...nextSyncMetadata(current), updatedAt: now() };
    await writeInspirations(items.map((item) => (item.id === id ? next : item)));
    return next;
  });
}

export async function readHighlights(): Promise<HighlightItem[]> {
  const raw = await readJson<unknown>(highlightsPath(), { version: 1, updatedAt: now(), items: [] });
  const items = isRecord(raw) && Array.isArray(raw.items) ? raw.items : Array.isArray(raw) ? raw : [];
  return items
    .filter((item): item is HighlightItem => isRecord(item) && typeof item.id === "string" && typeof item.bookId === "string")
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}

export async function writeHighlights(items: HighlightItem[]): Promise<void> {
  await writeJson(highlightsPath(), { version: 1, updatedAt: now(), items });
}

export async function readBookmarks(): Promise<BookmarkItem[]> {
  const raw = await readJson<unknown>(bookmarksPath(), { version: 1, updatedAt: now(), items: [] });
  const items = isRecord(raw) && Array.isArray(raw.items) ? raw.items : Array.isArray(raw) ? raw : [];
  return items
    .filter((item): item is BookmarkItem => isRecord(item) && typeof item.id === "string" && typeof item.bookId === "string")
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export async function writeBookmarks(items: BookmarkItem[]): Promise<void> {
  await writeJson(bookmarksPath(), { version: 1, updatedAt: now(), items });
}
