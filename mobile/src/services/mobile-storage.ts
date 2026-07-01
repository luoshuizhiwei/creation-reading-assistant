import type { InspirationSourceSnapshot } from "../../../src/types/inspiration";
import type { BookFormat, LibraryBook, ReadingLocation, ReadingProgress, ReadingSession } from "../../../src/types/library";
import { openMobileDatabase } from "../storage/mobile-database";
import { saveMobileBookBlob, saveMobileBookFile } from "../storage/mobile-files";
import type {
  ImportedMobileBook,
  MobileBook,
  MobileCategory,
  MobileInspiration,
  MobileNote,
  MobileReadingProgress,
  MobileReadingSession,
  MobileSnapshot,
  MobileShelf,
  MobileTag,
  SyncAccount
} from "../types/mobile";

const STORAGE_KEY = "creation-reading-assistant-mobile-snapshot";
const MIGRATION_KEY = "creation-reading-assistant-mobile-sqlite-migrated";
const DEVICE_KEY = "creation-reading-assistant-mobile-device-id";
export const BOOK_CONTENT_STORAGE_KEY_PREFIX = "creation-reading-assistant-mobile-book-content:";

export type { MobileSnapshot } from "../types/mobile";

const emptySnapshot = (): MobileSnapshot => ({
  inspirations: [],
  books: [],
  progress: [],
  sessions: [],
  notes: [],
  tags: [],
  categories: [],
  shelves: [],
  syncAccounts: [],
  updatedAt: new Date().toISOString()
});

function nowIso(): string {
  return new Date().toISOString();
}

export function getMobileDeviceId(): string {
  const saved = localStorage.getItem(DEVICE_KEY);
  if (saved) return saved;
  const deviceId = `android-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`;
  localStorage.setItem(DEVICE_KEY, deviceId);
  return deviceId;
}

export function normalizeMobileSnapshot(input?: Partial<MobileSnapshot>): MobileSnapshot {
  const fallback = emptySnapshot();
  return {
    inspirations: Array.isArray(input?.inspirations) ? input.inspirations : [],
    books: Array.isArray(input?.books) ? input.books : [],
    progress: Array.isArray(input?.progress) ? input.progress : [],
    sessions: Array.isArray(input?.sessions) ? input.sessions : [],
    notes: Array.isArray(input?.notes) ? input.notes : [],
    tags: Array.isArray(input?.tags) ? input.tags : [],
    categories: Array.isArray(input?.categories) ? input.categories : [],
    shelves: Array.isArray(input?.shelves) ? input.shelves : [],
    syncAccounts: Array.isArray(input?.syncAccounts) ? input.syncAccounts : [],
    updatedAt: typeof input?.updatedAt === "string" ? input.updatedAt : fallback.updatedAt
  };
}

async function readSnapshotFromLocalStorage(): Promise<MobileSnapshot> {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return emptySnapshot();
  try {
    return normalizeMobileSnapshot(JSON.parse(raw) as Partial<MobileSnapshot>);
  } catch {
    return emptySnapshot();
  }
}

async function readTablePayloads<T>(table: string): Promise<T[]> {
  const database = await openMobileDatabase().catch(() => undefined);
  if (!database) return [];
  const rows = await database.query<{ payload: string }>(`SELECT payload FROM ${table} WHERE deleted_at IS NULL ORDER BY updated_at DESC`);
  return rows
    .map((row) => {
      try {
        return JSON.parse(row.payload) as T;
      } catch {
        return undefined;
      }
    })
    .filter((row): row is T => Boolean(row));
}

async function upsertJson(table: string, idColumn: string, id: string, payload: object, extra: Record<string, string | undefined> = {}): Promise<void> {
  const database = await openMobileDatabase().catch(() => undefined);
  if (!database) return;
  const columns = [idColumn, "payload", "updated_at", "deleted_at", ...Object.keys(extra)];
  const values = [id, JSON.stringify(payload), "updatedAt" in payload ? String((payload as { updatedAt?: string }).updatedAt ?? nowIso()) : nowIso(), (payload as { deletedAt?: string }).deletedAt, ...Object.values(extra)];
  const placeholders = columns.map(() => "?").join(", ");
  const updates = columns
    .filter((column) => column !== idColumn)
    .map((column) => `${column}=excluded.${column}`)
    .join(", ");
  await database.run(`INSERT INTO ${table} (${columns.join(", ")}) VALUES (${placeholders}) ON CONFLICT(${idColumn}) DO UPDATE SET ${updates}`, values);
}

async function mirrorSnapshotToSQLite(snapshot: MobileSnapshot): Promise<void> {
  await Promise.all([
    ...snapshot.books.map((book) =>
      upsertJson("books", "id", book.id, book, {
        title: book.title,
        content_hash: book.contentHash
      })
    ),
    ...snapshot.books.map((book) =>
      upsertJson("book_files", "book_id", book.id, book, {
        file_name: book.originalFileName ?? `${book.id}.${book.format}`,
        format: book.format,
        content_hash: book.contentHash,
        size: String(book.size),
        local_uri: book.localUri
      })
    ),
    ...snapshot.inspirations.map((item) =>
      upsertJson("inspirations", "id", item.id, item, {
        title: item.title,
        source_book_id: item.source?.bookId ?? item.sourceBookId
      })
    ),
    ...snapshot.progress.map((item) => upsertJson("reading_progress", "book_id", item.bookId, item)),
    ...snapshot.sessions.map((item) =>
      upsertJson("reading_sessions", "id", item.id, item, {
        book_id: item.bookId
      })
    ),
    ...snapshot.notes.map((item) =>
      upsertJson("notes", "id", item.id, item, {
        book_id: item.bookId,
        inspiration_id: item.inspirationId
      })
    ),
    ...snapshot.tags.map((item) => upsertJson("tags", "id", item.id, item, { name: item.name })),
    ...snapshot.categories.map((item) => upsertJson("categories", "id", item.id, item, { name: item.name })),
    ...snapshot.shelves.map((item) => upsertJson("shelves", "id", item.id, item, { name: item.name })),
    ...snapshot.syncAccounts.map((item) =>
      upsertJson("sync_accounts", "id", item.id, item, {
        provider: item.provider
      })
    )
  ]).catch(() => undefined);
}

export async function migrateLegacySnapshot(): Promise<MobileSnapshot> {
  const snapshot = await readSnapshotFromLocalStorage();
  if (localStorage.getItem(MIGRATION_KEY) !== "done") {
    await mirrorSnapshotToSQLite(snapshot);
    localStorage.setItem(MIGRATION_KEY, "done");
  }
  return snapshot;
}

export async function initializeMobileStorage(): Promise<MobileSnapshot> {
  const snapshot = await migrateLegacySnapshot();
  const sqliteSnapshot = normalizeMobileSnapshot({
    inspirations: await readTablePayloads<MobileInspiration>("inspirations"),
    books: await readTablePayloads<MobileBook>("books"),
    progress: await readTablePayloads<MobileReadingProgress>("reading_progress"),
    sessions: await readTablePayloads<MobileReadingSession>("reading_sessions"),
    notes: await readTablePayloads<MobileNote>("notes"),
    tags: await readTablePayloads<MobileTag>("tags"),
    categories: await readTablePayloads<MobileCategory>("categories"),
    shelves: await readTablePayloads<MobileShelf>("shelves"),
    syncAccounts: await readTablePayloads<SyncAccount>("sync_accounts"),
    updatedAt: snapshot.updatedAt
  });
  if (
    sqliteSnapshot.inspirations.length ||
    sqliteSnapshot.books.length ||
    sqliteSnapshot.progress.length ||
    sqliteSnapshot.sessions.length ||
    sqliteSnapshot.notes.length
  ) {
    return {
      ...snapshot,
      ...sqliteSnapshot,
      syncAccounts: sqliteSnapshot.syncAccounts.length ? sqliteSnapshot.syncAccounts : snapshot.syncAccounts,
      tags: sqliteSnapshot.tags.length ? sqliteSnapshot.tags : snapshot.tags,
      categories: sqliteSnapshot.categories.length ? sqliteSnapshot.categories : snapshot.categories,
      shelves: sqliteSnapshot.shelves.length ? sqliteSnapshot.shelves : snapshot.shelves
    };
  }
  return snapshot;
}

export async function loadMobileSnapshot(): Promise<MobileSnapshot> {
  return initializeMobileStorage();
}

export async function saveMobileSnapshot(snapshot: MobileSnapshot): Promise<void> {
  const normalized = normalizeMobileSnapshot({ ...snapshot, updatedAt: nowIso() });
  localStorage.setItem(STORAGE_KEY, JSON.stringify(normalized));
  await mirrorSnapshotToSQLite(normalized);
}

function detectTitleFromFileName(fileName: string): string {
  return fileName.replace(/\.(txt|md|markdown|epub)$/i, "").replace(/[_-]+/g, " ").trim() || "未命名书籍";
}

function detectAuthor(content: string): string | undefined {
  const head = content.split(/\r?\n/).slice(0, 40).join("\n");
  const match = /(?:作者|Author)\s*[:：]\s*([^\n\r]{1,40})/i.exec(head) ?? /\bby\s+([^\n\r]{1,40})/i.exec(head);
  return match?.[1]?.trim();
}

export async function hashText(content: string): Promise<string> {
  const bytes = new TextEncoder().encode(content);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

export async function createImportedMobileBook(fileName: string, content: string, size: number): Promise<ImportedMobileBook> {
  const extension = fileName.toLowerCase().split(".").pop();
  const format: BookFormat = extension === "md" || extension === "markdown" ? "md" : extension === "epub" ? "epub" : "txt";
  return {
    title: detectTitleFromFileName(fileName),
    author: detectAuthor(content),
    format,
    originalFileName: fileName,
    content,
    size,
    contentHash: await hashText(content)
  };
}

export async function saveMobileBook(snapshot: MobileSnapshot, imported: ImportedMobileBook): Promise<MobileSnapshot> {
  const duplicateCount = snapshot.books.filter((book) => book.contentHash === imported.contentHash || book.originalFileName === imported.originalFileName).length;
  const id = `mobile-book-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`;
  const importedAt = nowIso();
  const storedFile = await saveMobileBookFile({
    bookId: id,
    originalFileName: imported.originalFileName,
    format: imported.format,
    content: imported.content
  });
  const book: MobileBook = {
    id,
    title: imported.title,
    author: imported.author,
    filePath: storedFile?.localFilePath ?? `mobile://${id}`,
    originalFileName: imported.originalFileName,
    originalFilePath: imported.originalFileName,
    originalPath: imported.originalFileName,
    format: imported.format,
    importedAt,
    updatedAt: importedAt,
    size: imported.size,
    contentHash: imported.contentHash,
    duplicateIndex: duplicateCount + 1,
    importLabel: duplicateCount ? `重复导入 #${duplicateCount + 1} · 导入于 ${new Date(importedAt).toLocaleDateString("zh-CN")}` : `导入于 ${new Date(importedAt).toLocaleDateString("zh-CN")}`,
    localUri: storedFile?.localUri,
    localFilePath: storedFile?.localFilePath,
    revision: 1,
    deviceId: getMobileDeviceId()
  };
  const next = {
    ...snapshot,
    books: [book, ...snapshot.books],
    updatedAt: nowIso()
  };
  await saveMobileSnapshot(next);
  localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${id}`);
  return next;
}

export async function saveSyncedMobileBookFile(snapshot: MobileSnapshot, book: MobileBook, content: string): Promise<MobileSnapshot> {
  const storedFile = await saveMobileBookFile({
    bookId: book.id,
    originalFileName: book.originalFileName ?? `${book.id}.${book.format}`,
    format: book.format,
    content
  });
  const nextBook: MobileBook = {
    ...book,
    filePath: storedFile?.localFilePath ?? book.filePath,
    localUri: storedFile?.localUri ?? book.localUri,
    localFilePath: storedFile?.localFilePath ?? book.localFilePath,
    updatedAt: nowIso()
  };
  localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${book.id}`);
  const next = {
    ...snapshot,
    books: [nextBook, ...snapshot.books.filter((item) => item.id !== book.id)],
    updatedAt: nowIso()
  };
  await saveMobileSnapshot(next);
  return next;
}

export async function saveSyncedMobileBookBlob(snapshot: MobileSnapshot, book: MobileBook, blob: Blob): Promise<MobileSnapshot> {
  const storedFile = await saveMobileBookBlob({
    bookId: book.id,
    originalFileName: book.originalFileName ?? `${book.id}.${book.format}`,
    format: book.format,
    blob
  });
  const nextBook: MobileBook = {
    ...book,
    filePath: storedFile?.localFilePath ?? book.filePath,
    localUri: storedFile?.localUri ?? book.localUri,
    localFilePath: storedFile?.localFilePath ?? book.localFilePath,
    size: blob.size || book.size,
    updatedAt: nowIso()
  };
  localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${book.id}`);
  const next = {
    ...snapshot,
    books: [nextBook, ...snapshot.books.filter((item) => item.id !== book.id)],
    updatedAt: nowIso()
  };
  await saveMobileSnapshot(next);
  return next;
}

export async function addMobileInspiration(
  snapshot: MobileSnapshot,
  input: {
    title: string;
    body?: string;
    tags?: string[];
    source?: Partial<InspirationSourceSnapshot>;
  }
): Promise<MobileSnapshot> {
  const createdAt = nowIso();
  const item: MobileInspiration = {
    id: `mobile-insp-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
    title: input.title.trim() || "新的灵感",
    body: input.body?.trim() ?? "",
    type: "note",
    status: "inbox",
    tags: input.tags ?? ["手机端"],
    platformTags: [],
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

export function createReadingLocation(format: BookFormat, progressPercent: number, scrollTop = 0): ReadingLocation {
  return {
    format,
    mode: "scroll",
    progressPercent,
    precision: "estimated",
    scroll: {
      scrollTop,
      scrollHeight: 100,
      containerHeight: 100
    },
    updatedAt: nowIso()
  };
}

export async function saveMobileReadingProgress(snapshot: MobileSnapshot, book: LibraryBook, progressPercent: number, activeDeltaMs = 0): Promise<MobileSnapshot> {
  const current = snapshot.progress.find((item) => item.bookId === book.id);
  const updatedAt = nowIso();
  const progress: ReadingProgress = {
    bookId: book.id,
    filePath: book.filePath,
    format: book.format,
    currentLocation: createReadingLocation(book.format, progressPercent),
    progressPercent,
    lastReadAt: updatedAt,
    totalReadingTimeMs: (current?.totalReadingTimeMs ?? 0) + activeDeltaMs,
    completionState: progressPercent >= 99 ? "completed" : progressPercent > 0 ? "reading" : "unread",
    completedAt: progressPercent >= 99 ? updatedAt : current?.completedAt,
    revision: (current?.revision ?? 0) + 1,
    deviceId: getMobileDeviceId(),
    updatedAt
  };
  const next = {
    ...snapshot,
    progress: [progress, ...snapshot.progress.filter((item) => item.bookId !== book.id)],
    updatedAt
  };
  await saveMobileSnapshot(next);
  return next;
}

export async function addMobileReadingSession(snapshot: MobileSnapshot, book: LibraryBook, durationMs: number, progressPercent: number): Promise<MobileSnapshot> {
  const createdAt = nowIso();
  const location = createReadingLocation(book.format, progressPercent);
  const session: ReadingSession = {
    id: `mobile-session-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
    bookId: book.id,
    filePath: book.filePath,
    format: book.format,
    startAt: new Date(Date.now() - durationMs).toISOString(),
    endAt: createdAt,
    durationMs,
    activeDurationMs: durationMs,
    idleDurationMs: 0,
    wallDurationMs: durationMs,
    startLocation: location,
    endLocation: location,
    dateKey: createdAt.slice(0, 10),
    status: "ended",
    source: "manualOpen",
    endReason: "leave-reader",
    revision: 1,
    deviceId: getMobileDeviceId(),
    createdAt,
    updatedAt: createdAt,
    lastPersistAt: createdAt
  };
  const next = { ...snapshot, sessions: [session, ...snapshot.sessions], updatedAt: nowIso() };
  await saveMobileSnapshot(next);
  return next;
}

export async function saveSyncAccount(snapshot: MobileSnapshot, account: Omit<SyncAccount, "id" | "createdAt" | "updatedAt" | "revision" | "deviceId">): Promise<MobileSnapshot> {
  const timestamp = nowIso();
  const nextAccount: SyncAccount = {
    ...account,
    id: `sync-${account.provider}-${Date.now().toString(36)}`,
    createdAt: timestamp,
    updatedAt: timestamp,
    revision: 1,
    deviceId: getMobileDeviceId()
  };
  const next = {
    ...snapshot,
    syncAccounts: [nextAccount, ...snapshot.syncAccounts.filter((item) => item.provider !== account.provider)],
    updatedAt: timestamp
  };
  await saveMobileSnapshot(next);
  return next;
}
