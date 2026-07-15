import type { InspirationSourceSnapshot, InspirationStatus, InspirationType } from "../../../src/types/inspiration";
import type { LibraryBook } from "../../../src/types/library";
import type { AddInspirationVariantInput } from "../../../src/types/inspiration";
import type { MobileInspiration, MobileNote, MobileSnapshot } from "../types/mobile";
import { getMobileDeviceId, nowIso, saveMobileSnapshot } from "./mobile-storage-core";

export async function addMobileInspiration(
  snapshot: MobileSnapshot,
  input: {
    title: string;
    body?: string;
    tags?: string[];
    categoryIds?: string[];
    type?: InspirationType;
    status?: InspirationStatus;
    source?: Partial<InspirationSourceSnapshot>;
  }
): Promise<MobileSnapshot> {
  const createdAt = nowIso();
  const item: MobileInspiration = {
    id: `mobile-insp-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
    title: input.title.trim() || "新的灵感",
    body: input.body?.trim() ?? "",
    type: input.type ?? "note",
    status: input.status ?? "inbox",
    tags: input.tags ?? ["手机端"],
    platformTags: [],
    categoryIds: input.categoryIds ?? [],
    source: input.source
      ? {
          createdAt,
          ...input.source
        }
      : undefined,
    variants: [],
    createdAt,
    updatedAt: createdAt,
    revision: 1,
    deviceId: getMobileDeviceId()
  };
  const next = {
    ...snapshot,
    inspirations: [item, ...snapshot.inspirations],
    updatedAt: nowIso()
  };
  await saveMobileSnapshot(next);
  return next;
}

export async function updateMobileInspiration(
  snapshot: MobileSnapshot,
  inspirationId: string,
  input: {
    title?: string;
    body?: string;
    tags?: string[];
    categoryIds?: string[];
    type?: MobileInspiration["type"];
    status?: MobileInspiration["status"];
    variants?: MobileInspiration["variants"];
    source?: MobileInspiration["source"] | null;
  }
): Promise<MobileSnapshot> {
  const current = snapshot.inspirations.find((item) => item.id === inspirationId);
  if (!current) return snapshot;
  const updatedAt = nowIso();
  const updated: MobileInspiration = {
    ...current,
    title: input.title !== undefined ? (input.title.trim() || "未命名灵感") : current.title,
    body: input.body !== undefined ? (input.body.trim() ?? current.body) : current.body,
    tags: input.tags ?? current.tags,
    categoryIds: input.categoryIds ?? current.categoryIds,
    type: input.type ?? current.type,
    status: input.status ?? current.status,
    variants: input.variants ?? current.variants,
    source: input.source === null ? undefined : input.source !== undefined ? input.source : current.source,
    updatedAt,
    revision: (current.revision ?? 0) + 1
  };
  const next = {
    ...snapshot,
    inspirations: [updated, ...snapshot.inspirations.filter((item) => item.id !== inspirationId)],
    updatedAt
  };
  await saveMobileSnapshot(next);
  return next;
}

export async function addMobileInspirationVariant(
  snapshot: MobileSnapshot,
  inspirationId: string,
  input: AddInspirationVariantInput
): Promise<MobileSnapshot> {
  const current = snapshot.inspirations.find((item) => item.id === inspirationId);
  if (!current) return snapshot;
  const timestamp = nowIso();
  const variant = {
    id: `mobile-variant-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
    kind: input.kind,
    content: input.content,
    prompt: input.prompt,
    model: input.model,
    createdAt: timestamp
  };
  const updated: MobileInspiration = {
    ...current,
    status: current.status === "inbox" || current.status === "usable" ? "polished" : current.status,
    variants: [variant, ...current.variants],
    updatedAt: timestamp,
    revision: (current.revision ?? 0) + 1
  };
  const next = {
    ...snapshot,
    inspirations: [updated, ...snapshot.inspirations.filter((item) => item.id !== inspirationId)],
    updatedAt: timestamp
  };
  await saveMobileSnapshot(next);
  return next;
}

export async function deleteMobileInspiration(snapshot: MobileSnapshot, inspirationId: string): Promise<MobileSnapshot> {
  const next = {
    ...snapshot,
    inspirations: snapshot.inspirations.filter((item) => item.id !== inspirationId),
    updatedAt: nowIso()
  };
  await saveMobileSnapshot(next);
  return next;
}

/** 批量更新灵感状态 */
export async function batchUpdateInspirationStatus(
  snapshot: MobileSnapshot,
  inspirationIds: string[],
  status: MobileInspiration["status"]
): Promise<MobileSnapshot> {
  if (!inspirationIds.length) return snapshot;
  const idSet = new Set(inspirationIds);
  const updatedAt = nowIso();
  const inspirations = snapshot.inspirations.map((item) =>
    idSet.has(item.id)
      ? { ...item, status, updatedAt, revision: (item.revision ?? 0) + 1 }
      : item
  );
  const next = { ...snapshot, inspirations, updatedAt };
  await saveMobileSnapshot(next);
  return next;
}

/** 批量为灵感添加标签（去重） */
export async function batchAddInspirationTags(
  snapshot: MobileSnapshot,
  inspirationIds: string[],
  tagsToAdd: string[]
): Promise<MobileSnapshot> {
  if (!inspirationIds.length || !tagsToAdd.length) return snapshot;
  const idSet = new Set(inspirationIds);
  const updatedAt = nowIso();
  const inspirations = snapshot.inspirations.map((item) => {
    if (!idSet.has(item.id)) return item;
    const existing = new Set(item.tags);
    const merged = [...tagsToAdd.filter((tag) => !existing.has(tag)), ...item.tags];
    return { ...item, tags: merged, updatedAt, revision: (item.revision ?? 0) + 1 };
  });
  const next = { ...snapshot, inspirations, updatedAt };
  await saveMobileSnapshot(next);
  return next;
}

/** 批量删除灵感 */
export async function batchDeleteInspirations(
  snapshot: MobileSnapshot,
  inspirationIds: string[]
): Promise<MobileSnapshot> {
  if (!inspirationIds.length) return snapshot;
  const idSet = new Set(inspirationIds);
  const next = {
    ...snapshot,
    inspirations: snapshot.inspirations.filter((item) => !idSet.has(item.id)),
    updatedAt: nowIso()
  };
  await saveMobileSnapshot(next);
  return next;
}

export async function deleteMobileNote(snapshot: MobileSnapshot, noteId: string): Promise<MobileSnapshot> {
  const target = snapshot.notes.find((item) => item.id === noteId);
  if (!target) return snapshot;
  const updatedAt = nowIso();
  const deleted = { ...target, deletedAt: updatedAt };
  const next = {
    ...snapshot,
    notes: [deleted, ...snapshot.notes.filter((item) => item.id !== noteId)],
    updatedAt
  };
  await saveMobileSnapshot(next);
  return next;
}

export async function addMobileNote(
  snapshot: MobileSnapshot,
  input: {
    book: LibraryBook;
    title: string;
    body?: string;
    excerpt?: string;
    chapterTitle?: string;
    progressPercent?: number;
    kind?: MobileNote["kind"];
  }
): Promise<MobileSnapshot> {
  const timestamp = nowIso();
  const note: MobileNote = {
    id: `mobile-note-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
    bookId: input.book.id,
    title: input.title.trim() || (input.kind === "bookmark" ? "阅读书签" : "阅读笔记"),
    body: input.body?.trim() ?? "",
    excerpt: input.excerpt?.trim(),
    chapterTitle: input.chapterTitle,
    progressPercent: input.progressPercent,
    kind: input.kind ?? "note",
    createdAt: timestamp,
    updatedAt: timestamp,
    revision: 1,
    deviceId: getMobileDeviceId()
  };
  const next = {
    ...snapshot,
    notes: [note, ...snapshot.notes],
    updatedAt: timestamp
  };
  await saveMobileSnapshot(next);
  return next;
}
