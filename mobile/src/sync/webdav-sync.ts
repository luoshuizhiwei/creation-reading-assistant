import type { MobileSnapshot, SyncAccount } from "../types/mobile";
import { sanitizeMobileBookForSync } from "../services/mobile-storage";

export const WEBDAV_REMOTE_ROOT = ".creation-reading-assistant/";
export const WEBDAV_MANIFEST_PATH = ".creation-reading-assistant/manifest.json";
export const WEBDAV_RECORDS_DIR = ".creation-reading-assistant/records/";
export const WEBDAV_BOOKS_DIR = ".creation-reading-assistant/books/";

export interface WebDavCredentials {
  endpoint: string;
  username?: string;
  password?: string;
}

export interface WebDavManifest {
  app: "creation-reading-assistant";
  version: 1;
  generatedAt: string;
  deviceId: string;
  records: Record<string, string>;
  books: Array<{ bookId: string; contentHash?: string; fileName: string; size: number }>;
}

function buildUrl(endpoint: string, path: string): string {
  return `${endpoint.replace(/\/+$/, "")}/${path.replace(/^\/+/, "")}`;
}

function authHeader(credentials: WebDavCredentials): HeadersInit {
  if (!credentials.username || !credentials.password) return {};
  return {
    authorization: `Basic ${btoa(unescape(encodeURIComponent(`${credentials.username}:${credentials.password}`)))}`
  };
}

async function ensureWebDavDir(credentials: WebDavCredentials, path: string): Promise<boolean> {
  const response = await fetch(buildUrl(credentials.endpoint, path), {
    method: "MKCOL",
    headers: authHeader(credentials)
  }).catch(() => undefined);
  return response?.ok ?? false;
}

export async function testWebDavConnection(credentials: WebDavCredentials): Promise<{ ok: boolean; message: string }> {
  if (!credentials.endpoint.trim()) return { ok: false, message: "请先填写 WebDAV 地址。" };
  const response = await fetch(buildUrl(credentials.endpoint, WEBDAV_REMOTE_ROOT), {
    method: "PROPFIND",
    headers: {
      ...authHeader(credentials),
      depth: "0"
    }
  }).catch(() => undefined);
  if (response?.ok || response?.status === 207) return { ok: true, message: "WebDAV 连接正常。" };
  const created = await ensureWebDavDir(credentials, WEBDAV_REMOTE_ROOT);
  if (created) return { ok: true, message: "已创建 WebDAV 同步目录。" };
  return { ok: false, message: "WebDAV 连接失败，请检查地址和凭据。" };
}

export function createWebDavManifest(snapshot: MobileSnapshot, account: SyncAccount): WebDavManifest {
  const safeBooks = snapshot.books.map(sanitizeMobileBookForSync);
  return {
    app: "creation-reading-assistant",
    version: 1,
    generatedAt: new Date().toISOString(),
    deviceId: account.deviceId,
    records: {
      inspirations: `${WEBDAV_RECORDS_DIR}inspirations.json`,
      books: `${WEBDAV_RECORDS_DIR}books.json`,
      progress: `${WEBDAV_RECORDS_DIR}reading-progress.json`,
      sessions: `${WEBDAV_RECORDS_DIR}reading-sessions.json`,
      notes: `${WEBDAV_RECORDS_DIR}notes.json`,
      tags: `${WEBDAV_RECORDS_DIR}tags.json`,
      categories: `${WEBDAV_RECORDS_DIR}categories.json`,
      shelves: `${WEBDAV_RECORDS_DIR}shelves.json`
    },
    books: safeBooks.map((book) => ({
      bookId: book.id,
      contentHash: book.contentHash,
      fileName: book.originalFileName ?? `${book.id}.${book.format}`,
      size: book.size
    }))
  };
}

async function putJson(credentials: WebDavCredentials, path: string, payload: unknown): Promise<void> {
  const response = await fetch(buildUrl(credentials.endpoint, path), {
    method: "PUT",
    headers: {
      ...authHeader(credentials),
      "content-type": "application/json"
    },
    body: JSON.stringify(payload, null, 2)
  });
  if (!response.ok && response.status !== 201 && response.status !== 204) {
    throw new Error(`WebDAV 上传失败：${response.status}`);
  }
}

export async function uploadWebDavSnapshot(credentials: WebDavCredentials, account: SyncAccount, snapshot: MobileSnapshot): Promise<WebDavManifest> {
  await ensureWebDavDir(credentials, WEBDAV_REMOTE_ROOT);
  await ensureWebDavDir(credentials, WEBDAV_RECORDS_DIR);
  await ensureWebDavDir(credentials, WEBDAV_BOOKS_DIR);

  const manifest = createWebDavManifest(snapshot, account);
  const safeBooks = snapshot.books.map(sanitizeMobileBookForSync);
  await putJson(credentials, ".creation-reading-assistant/records/inspirations.json", snapshot.inspirations);
  await putJson(credentials, ".creation-reading-assistant/records/books.json", safeBooks);
  await putJson(credentials, ".creation-reading-assistant/records/reading-progress.json", snapshot.progress);
  await putJson(credentials, ".creation-reading-assistant/records/reading-sessions.json", snapshot.sessions);
  await putJson(credentials, ".creation-reading-assistant/records/notes.json", snapshot.notes);
  await putJson(credentials, ".creation-reading-assistant/records/tags.json", snapshot.tags);
  await putJson(credentials, ".creation-reading-assistant/records/categories.json", snapshot.categories);
  await putJson(credentials, ".creation-reading-assistant/records/shelves.json", snapshot.shelves);
  await putJson(credentials, ".creation-reading-assistant/manifest.json", manifest);
  return manifest;
}

async function getJson<T>(credentials: WebDavCredentials, path: string, fallback: T): Promise<T> {
  const response = await fetch(buildUrl(credentials.endpoint, path), {
    headers: authHeader(credentials)
  });
  if (response.status === 404) return fallback;
  if (!response.ok) throw new Error(`WebDAV 下载失败：${response.status}`);
  return (await response.json()) as T;
}

export async function downloadWebDavSnapshot(credentials: WebDavCredentials, current: MobileSnapshot): Promise<MobileSnapshot> {
  await getJson<WebDavManifest | undefined>(credentials, ".creation-reading-assistant/manifest.json", undefined);
  const inspirations = await getJson(credentials, ".creation-reading-assistant/records/inspirations.json", current.inspirations);
  const books = await getJson(credentials, ".creation-reading-assistant/records/books.json", current.books);
  const progress = await getJson(credentials, ".creation-reading-assistant/records/reading-progress.json", current.progress);
  const sessions = await getJson(credentials, ".creation-reading-assistant/records/reading-sessions.json", current.sessions);
  const notes = await getJson(credentials, ".creation-reading-assistant/records/notes.json", current.notes);
  const tags = await getJson(credentials, ".creation-reading-assistant/records/tags.json", current.tags);
  const categories = await getJson(credentials, ".creation-reading-assistant/records/categories.json", current.categories);
  const shelves = await getJson(credentials, ".creation-reading-assistant/records/shelves.json", current.shelves);
  return {
    ...current,
    inspirations,
    books,
    progress,
    sessions,
    notes,
    tags,
    categories,
    shelves,
    updatedAt: new Date().toISOString()
  };
}
