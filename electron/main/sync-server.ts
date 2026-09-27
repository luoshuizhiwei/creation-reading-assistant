/**
 * 局域网同步服务（从 electron/main/index.ts 拆出，纯移动式重构）。
 *
 * 职责：桌面端 HTTP 同步服务的生命周期（绑定 / 停止 / 换网卡自愈）、设备配对、
 * 同步清单与信封构造、增量合并规则与请求路由。
 * 行为逐字保持；syncServer / pairingToken 等模块状态一并迁入，仅主进程入口经导出函数访问。
 */
import { createServer } from "node:http";
import type { IncomingMessage, Server, ServerResponse } from "node:http";
import { createReadStream } from "node:fs";
import { readFile, writeFile } from "node:fs/promises";
import { stat } from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import { networkInterfaces } from "node:os";
import type {
  BookFileManifest,
  DeviceInfo,
  PairingTokenResult,
  SyncEnvelope,
  SyncManifest,
  SyncPullResponse,
  SyncPushPayload,
  SyncPushResult,
  SyncRecordType,
  SyncStatus
} from "../../src/types/sync";
import type { InspirationItem } from "../../src/types/inspiration";
import type { LibraryBook, ReadingProgress, ReadingSession } from "../../src/types/library";
import {
  appLibraryFilesRoot,
  currentDeviceId,
  ensureDir,
  getOrCreateDeviceId,
  isRecord,
  makeId,
  now,
  optionalString,
  readSyncState,
  withSyncMetadata,
  writeLog,
  writeSyncState
} from "./storage";
import {
  normalizeBookFormat,
  normalizeLibraryBook,
  readLibraryIndex,
  writeLibraryIndex
} from "./library-store";
import { normalizeInspirationItem, readInspirations, writeInspirations } from "./inspiration-store";
import {
  normalizeProgressItem,
  normalizeSessionItem,
  readProgressList,
  readSessionList,
  syncAllProgressTotals,
  writeProgressList,
  writeSessionList
} from "./progress-store";

const SYNC_CHUNK_SIZE = 1024 * 1024;

/** 同步服务器与配对令牌的生命周期状态（仅经 withSyncServerLock 串行改写）。 */
let syncServer: Server | undefined;
let syncServerPort: number | undefined;
let syncServerHost: string | undefined;
let pairingToken: PairingTokenResult | undefined;

export function syncTimestamp(value?: string): number {
  const parsed = Date.parse(value ?? "");
  return Number.isFinite(parsed) ? parsed : 0;
}

export function listLanAddresses(): string[] {
  const addresses = new Set<string>(["127.0.0.1"]);
  const interfaces = networkInterfaces();
  for (const items of Object.values(interfaces)) {
    for (const item of items ?? []) {
      if (item.family === "IPv4" && !item.internal) addresses.add(item.address);
    }
  }
  return [...addresses];
}

export function scorePairingAddress(address: string): number {
  if (address === "127.0.0.1") return -1000;
  let score = 0;
  if (!address.startsWith("169.254.")) score += 50;
  if (!address.startsWith("192.168.137.")) score += 30;
  if (!address.endsWith(".1")) score += 10;
  if (address.startsWith("192.168.") || address.startsWith("10.") || address.startsWith("172.")) score += 5;
  return score;
}

export function pairingAddresses(addresses: string[]): string[] {
  const candidates = addresses.filter((address) => address !== "127.0.0.1");
  return (candidates.length > 0 ? candidates : ["127.0.0.1"]).sort((a, b) => scorePairingAddress(b) - scorePairingAddress(a));
}

export async function desktopDeviceInfo(): Promise<DeviceInfo> {
  const state = await readSyncState();
  return {
    deviceId: state.deviceId,
    name: "电脑端 · 创作阅读助手",
    platform: "desktop",
    pairedAt: state.updatedAt,
    lastSeenAt: now()
  };
}

export function normalizeDeviceInfo(value: unknown): DeviceInfo | undefined {
  if (!isRecord(value)) return undefined;
  const deviceId = optionalString(value.deviceId);
  if (!deviceId) return undefined;
  return {
    deviceId,
    name: optionalString(value.name) ?? "手机端",
    platform: value.platform === "android" || value.platform === "ios" || value.platform === "web" || value.platform === "desktop" ? value.platform : "android",
    pairedAt: optionalString(value.pairedAt) ?? now(),
    lastSeenAt: optionalString(value.lastSeenAt) ?? now()
  };
}

export async function upsertPairedDevice(device: DeviceInfo, authTokenHash?: string): Promise<void> {
  const state = await readSyncState();
  const existing = state.devices.find((item) => item.deviceId === device.deviceId);
  const nextDevice: DeviceInfo & { authTokenHash?: string } = {
    ...device,
    pairedAt: existing?.pairedAt ?? device.pairedAt ?? now(),
    lastSeenAt: now(),
    authTokenHash: authTokenHash ?? existing?.authTokenHash
  };
  state.devices = [nextDevice, ...state.devices.filter((item) => item.deviceId !== device.deviceId)];
  await writeSyncState(state);
}

export async function listPairedDevices(): Promise<DeviceInfo[]> {
  return (await readSyncState()).devices.map(({ authTokenHash: _authTokenHash, ...device }) => device);
}

export async function removePairedDevice(deviceId: string): Promise<DeviceInfo[]> {
  const state = await readSyncState();
  state.devices = state.devices.filter((item) => item.deviceId !== deviceId);
  await writeSyncState(state);
  return state.devices;
}

export function toManifestEnvelope<TPayload>(
  type: SyncRecordType,
  id: string,
  item: { revision: number; deviceId: string; updatedAt: string; deletedAt?: string },
  payload: TPayload
): SyncEnvelope<TPayload> {
  return {
    id,
    type,
    revision: item.revision,
    deviceId: item.deviceId,
    updatedAt: item.updatedAt,
    deletedAt: item.deletedAt,
    payload
  };
}

export function toFullEnvelope<TPayload extends { revision: number; deviceId: string; updatedAt: string; deletedAt?: string }>(
  type: SyncRecordType,
  id: string,
  payload: TPayload
): SyncEnvelope<TPayload> {
  return toManifestEnvelope(type, id, payload, payload);
}

export async function buildBookFileManifest(books?: LibraryBook[]): Promise<BookFileManifest[]> {
  const source = books ?? (await readLibraryIndex({ includeDeleted: true }));
  const manifests: BookFileManifest[] = [];
  for (const book of source) {
    if (book.deletedAt) continue;
    try {
      const fileStats = await stat(book.filePath);
      manifests.push({
        bookId: book.id,
        fileName: book.originalFileName ?? path.basename(book.filePath),
        format: book.format,
        contentHash: book.contentHash,
        size: fileStats.size,
        chunkSize: SYNC_CHUNK_SIZE
      });
    } catch {
      await writeLog("warn", "Sync book file manifest skipped missing file.", { bookId: book.id, filePath: book.filePath });
    }
  }
  return manifests;
}

export async function buildSyncManifest(): Promise<SyncManifest> {
  const [inspirations, books, progress, sessions] = await Promise.all([
    readInspirations({ includeDeleted: true }),
    readLibraryIndex({ includeDeleted: true }),
    readProgressList({ includeDeleted: true }),
    readSessionList({ includeDeleted: true })
  ]);
  return {
    device: await desktopDeviceInfo(),
    generatedAt: now(),
    inspirations: inspirations.map((item) => toManifestEnvelope("inspiration", item.id, item, { id: item.id })),
    books: books.map((item) => toManifestEnvelope("book", item.id, item, { id: item.id })),
    progress: progress.map((item) => toManifestEnvelope("progress", item.bookId, item, { bookId: item.bookId })),
    sessions: sessions.map((item) => toManifestEnvelope("session", item.id, item, { id: item.id })),
    bookFiles: await buildBookFileManifest(books)
  };
}

export async function buildSyncPullResponse(): Promise<SyncPullResponse> {
  const [inspirations, books, progress, sessions] = await Promise.all([
    readInspirations({ includeDeleted: true }),
    readLibraryIndex({ includeDeleted: true }),
    readProgressList({ includeDeleted: true }),
    readSessionList({ includeDeleted: true })
  ]);
  return {
    manifest: await buildSyncManifest(),
    inspirations: inspirations.map((item) => toFullEnvelope("inspiration", item.id, item)),
    books: books.map((item) => toFullEnvelope("book", item.id, item)),
    progress: progress.map((item) => toFullEnvelope("progress", item.bookId, item)),
    sessions: sessions.map((item) => toFullEnvelope("session", item.id, item))
  };
}

export function mergeByUpdatedAt<T extends { updatedAt: string; revision: number; deviceId: string; deletedAt?: string }>(current: T | undefined, incoming: T): T {
  if (!current) return incoming;
  const currentTime = syncTimestamp(current.updatedAt);
  const incomingTime = syncTimestamp(incoming.updatedAt);
  if (incomingTime > currentTime) return incoming;
  if (incomingTime === currentTime && incoming.revision > current.revision) return incoming;
  return current;
}

export function mergeIncomingInspirations(
  currentItems: InspirationItem[],
  incomingEnvelopes: Array<SyncEnvelope<InspirationItem>>
): { items: InspirationItem[]; conflicts: Array<SyncEnvelope<InspirationItem>>; applied: number } {
  const byId = new Map(currentItems.map((item) => [item.id, item]));
  const conflicts: Array<SyncEnvelope<InspirationItem>> = [];
  let applied = 0;
  for (const envelope of incomingEnvelopes) {
    const incoming = normalizeInspirationItem(envelope.payload);
    if (!incoming) continue;
    const current = byId.get(incoming.id);
    if (current && !current.deletedAt && !incoming.deletedAt && current.deviceId !== incoming.deviceId && current.body !== incoming.body) {
      const winner = mergeByUpdatedAt(current, incoming);
      const loser = winner === current ? incoming : current;
      const timestamp = now();
      const conflict = withSyncMetadata<InspirationItem>({
        ...loser,
        id: makeId("insp-conflict"),
        title: `${loser.title}（冲突副本）`,
        createdAt: timestamp,
        updatedAt: timestamp,
        revision: 1,
        deviceId: currentDeviceId(),
        deletedAt: undefined
      });
      byId.set(incoming.id, winner);
      byId.set(conflict.id, conflict);
      conflicts.push(toFullEnvelope("inspiration", conflict.id, conflict));
      applied += 1;
      continue;
    }
    byId.set(incoming.id, mergeByUpdatedAt(current, incoming));
    applied += 1;
  }
  return { items: [...byId.values()], conflicts, applied };
}

export function mergeLibraryBooksById(currentItems: LibraryBook[], incomingEnvelopes: Array<SyncEnvelope<LibraryBook>>): { items: LibraryBook[]; applied: number } {
  const byId = new Map(currentItems.map((item) => [item.id, item]));
  let applied = 0;
  for (const envelope of incomingEnvelopes) {
    const incoming = normalizeLibraryBook(envelope.payload);
    if (!incoming) continue;
    byId.set(incoming.id, mergeByUpdatedAt(byId.get(incoming.id), incoming));
    applied += 1;
  }
  return { items: [...byId.values()], applied };
}

export function mergeReadingProgressByBookId(
  currentItems: ReadingProgress[],
  incomingEnvelopes: Array<SyncEnvelope<ReadingProgress>>
): { items: ReadingProgress[]; applied: number } {
  const byId = new Map(currentItems.map((item) => [item.bookId, item]));
  let applied = 0;
  for (const envelope of incomingEnvelopes) {
    const incoming = normalizeProgressItem(envelope.payload);
    if (!incoming) continue;
    byId.set(incoming.bookId, mergeByUpdatedAt(byId.get(incoming.bookId), incoming));
    applied += 1;
  }
  return { items: [...byId.values()], applied };
}

export function mergeReadingSessionsById(
  currentItems: ReadingSession[],
  incomingEnvelopes: Array<SyncEnvelope<ReadingSession>>
): { items: ReadingSession[]; applied: number } {
  const byId = new Map(currentItems.map((item) => [item.id, item]));
  let applied = 0;
  for (const envelope of incomingEnvelopes) {
    const incoming = normalizeSessionItem(envelope.payload);
    if (!incoming) continue;
    byId.set(incoming.id, mergeByUpdatedAt(byId.get(incoming.id), incoming));
    applied += 1;
  }
  return { items: [...byId.values()], applied };
}

export async function applySyncPush(payload: SyncPushPayload): Promise<SyncPushResult> {
  if (payload.device) await upsertPairedDevice(payload.device);

  const incomingInspirations = Array.isArray(payload.inspirations) ? payload.inspirations : [];
  const incomingBooks = Array.isArray(payload.books) ? payload.books : [];
  const incomingProgress = Array.isArray(payload.progress) ? payload.progress : [];
  const incomingSessions = Array.isArray(payload.sessions) ? payload.sessions : [];

  const inspirationMerge = mergeIncomingInspirations(await readInspirations({ includeDeleted: true }), incomingInspirations);
  if (incomingInspirations.length > 0) await writeInspirations(inspirationMerge.items);

  const bookMerge = mergeLibraryBooksById(await readLibraryIndex({ includeDeleted: true }), incomingBooks);
  if (incomingBooks.length > 0) await writeLibraryIndex(bookMerge.items);

  const progressMerge = mergeReadingProgressByBookId(await readProgressList({ includeDeleted: true }), incomingProgress);
  if (incomingProgress.length > 0) await writeProgressList(progressMerge.items);

  const sessionMerge = mergeReadingSessionsById(await readSessionList({ includeDeleted: true }), incomingSessions);
  if (incomingSessions.length > 0) await writeSessionList(sessionMerge.items);

  await syncAllProgressTotals();

  // After sync, reconcile totalReadingTimeMs: take the of incoming and computed values
  // to avoid losing time tracked on other devices
  const finalSessions = await readSessionList();
  const finalProgress = await readProgressList();
  const incomingProgressMap = new Map(
    incomingProgress
      .map((env) => normalizeProgressItem(env.payload))
      .filter((p): p is ReadingProgress => p !== null)
      .map((p) => [p.bookId, p])
  );
  const reconciled = finalProgress.map((progress) => {
    const incoming = incomingProgressMap.get(progress.bookId);
    if (!incoming) return progress;
    const computed = finalSessions
      .filter((s) => s.bookId === progress.bookId)
      .reduce((sum, s) => sum + Math.max(0, s.activeDurationMs), 0);
    const merged = Math.max(progress.totalReadingTimeMs, incoming.totalReadingTimeMs, computed);
    return merged !== progress.totalReadingTimeMs ? { ...progress, totalReadingTimeMs: merged } : progress;
  });
  if (incomingProgress.length > 0) await writeProgressList(reconciled);

  return {
    ok: true,
    applied: {
      inspirations: inspirationMerge.applied,
      books: bookMerge.applied,
      progress: progressMerge.applied,
      sessions: sessionMerge.applied
    },
    conflicts: inspirationMerge.conflicts,
    manifest: await buildSyncManifest()
  };
}

export function isPairingTokenValid(token: string | undefined): boolean {
  return Boolean(pairingToken && token && pairingToken.token === token && syncTimestamp(pairingToken.expiresAt) > Date.now());
}

export async function getSyncStatus(): Promise<SyncStatus> {
  if (pairingToken && syncTimestamp(pairingToken.expiresAt) <= Date.now()) pairingToken = undefined;
  return {
    running: Boolean(syncServer && syncServerPort),
    port: syncServerPort,
    addresses: syncServer && syncServerHost ? [syncServerHost] : listLanAddresses(),
    device: await desktopDeviceInfo(),
    pairingToken
  };
}

// startSyncServer / stopSyncServer / createPairingToken 共享 syncServer 等模块状态，
// 且中间有多个 await 点；并发进入（如重绑等待连接排空时用户再点按钮）会产生
// 状态里看不到也关不掉的孤儿监听服务。所以三个入口一律经此队列串行执行。
export let syncServerOpQueue: Promise<unknown> = Promise.resolve();

export function withSyncServerLock<T>(operation: () => Promise<T>): Promise<T> {
  const run = syncServerOpQueue.then(operation, operation);
  syncServerOpQueue = run.then(
    () => undefined,
    () => undefined
  );
  return run;
}

export async function startSyncServerUnlocked(): Promise<SyncStatus> {
  await getOrCreateDeviceId();
  // 只绑定配对选定的那块局域网网卡，不监听 0.0.0.0
  const host = pairingAddresses(listLanAddresses())[0];
  if (syncServer && syncServerPort) {
    if (syncServerHost === host) return getSyncStatus();
    // 网卡地址变了：旧绑定已失效，停掉后重新绑定到新地址
    await stopSyncServerUnlocked();
  }
  syncServer = createServer((request, response) => {
    void handleSyncRequest(request, response).catch((error) => {
      void writeLog("error", "Sync server request failed.", error);
      sendJson(response, 500, { ok: false, message: error instanceof Error ? error.message : "同步服务内部错误。" });
    });
  });
  await new Promise<void>((resolve, reject) => {
    const onError = (error: Error) => reject(error);
    if (!syncServer) {
      reject(new Error("同步服务创建失败。"));
      return;
    }
    syncServer.once("error", onError);
    syncServer.listen(0, host, () => {
      syncServer?.off("error", onError);
      const address = syncServer?.address();
      syncServerPort = typeof address === "object" && address ? address.port : undefined;
      syncServerHost = host;
      resolve();
    });
  });
  return getSyncStatus();
}

export async function startSyncServer(): Promise<SyncStatus> {
  return withSyncServerLock(startSyncServerUnlocked);
}

export async function stopSyncServerUnlocked(): Promise<SyncStatus> {
  if (!syncServer) {
    syncServerPort = undefined;
    syncServerHost = undefined;
    pairingToken = undefined;
    return getSyncStatus();
  }
  const server = syncServer;
  await new Promise<void>((resolve) => server.close(() => resolve()));
  syncServer = undefined;
  syncServerPort = undefined;
  syncServerHost = undefined;
  pairingToken = undefined;
  return getSyncStatus();
}

export async function stopSyncServer(): Promise<SyncStatus> {
  return withSyncServerLock(stopSyncServerUnlocked);
}

// 设置页刷新状态时顺带自愈：绑定的网卡地址已经不存在（比如换了 Wi-Fi）就重绑到新地址
export async function getSyncStatusWithRebind(): Promise<SyncStatus> {
  if (syncServer && syncServerPort && syncServerHost && !listLanAddresses().includes(syncServerHost)) {
    return startSyncServer();
  }
  return getSyncStatus();
}

export async function createPairingTokenUnlocked(): Promise<PairingTokenResult> {
  const status = await startSyncServerUnlocked();
  if (!status.port) throw new Error("同步服务未能启动。");
  const token = crypto.randomBytes(18).toString("hex");
  const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();
  const options = pairingAddresses(status.addresses).map((address) => {
    const pairingUrl = `http://${address}:${status.port}/sync/pair?token=${encodeURIComponent(token)}`;
    const qrPayload = JSON.stringify({
      app: "创作阅读助手",
      version: 1,
      host: address,
      port: status.port,
      token,
      pairingUrl
    });
    return { address, pairingUrl, qrPayload };
  });
  const primary = options[0];
  pairingToken = {
    token,
    pairingUrl: primary.pairingUrl,
    qrPayload: primary.qrPayload,
    pairingUrls: options.map((item) => item.pairingUrl),
    qrPayloads: options,
    expiresAt
  };
  return pairingToken;
}

export async function createPairingToken(): Promise<PairingTokenResult> {
  return withSyncServerLock(createPairingTokenUnlocked);
}

export async function readRequestJson<T>(request: IncomingMessage): Promise<T> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buffer.byteLength;
    if (total > 25 * 1024 * 1024) throw new Error("同步请求过大。");
    chunks.push(buffer);
  }
  const content = Buffer.concat(chunks).toString("utf-8").trim();
  return (content ? JSON.parse(content) : {}) as T;
}

export async function readRequestBuffer(request: IncomingMessage, maxBytes = 256 * 1024 * 1024): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buffer.byteLength;
    if (total > maxBytes) throw new Error("上传的书籍文件过大。");
    chunks.push(buffer);
  }
  return Buffer.concat(chunks);
}

export function sendCorsHeaders(response: ServerResponse): void {
  response.setHeader("Access-Control-Allow-Origin", "*");
  response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS");
  response.setHeader("Access-Control-Allow-Headers", "content-type,x-device-id,x-sync-token,x-original-file-name,X-Original-File-Name");
}

export function sendJson(response: ServerResponse, statusCode: number, payload: unknown): void {
  sendCorsHeaders(response);
  response.statusCode = statusCode;
  response.setHeader("Content-Type", "application/json; charset=utf-8");
  response.end(JSON.stringify(payload));
}

export function sendText(response: ServerResponse, statusCode: number, payload: string): void {
  sendCorsHeaders(response);
  response.statusCode = statusCode;
  response.setHeader("Content-Type", "text/plain; charset=utf-8");
  response.end(payload);
}

export async function downloadBookFile(bookId: string, response: ServerResponse): Promise<void> {
  const book = (await readLibraryIndex()).find((item) => item.id === bookId);
  if (!book) {
    sendJson(response, 404, { ok: false, message: "没有找到这本书。" });
    return;
  }
  const fileBuffer = await readFile(book.filePath);
  sendCorsHeaders(response);
  response.statusCode = 200;
  response.setHeader("Content-Type", "application/octet-stream");
  response.setHeader("Content-Disposition", `attachment; filename*=UTF-8''${encodeURIComponent(book.originalFileName ?? path.basename(book.filePath))}`);
  response.setHeader("X-Content-Hash", book.contentHash ?? "");
  response.end(fileBuffer);
}

export function requestHeaderString(request: IncomingMessage, headerName: string): string | undefined {
  const value = request.headers[headerName.toLowerCase()];
  return Array.isArray(value) ? value[0] : value;
}

export function syncAuthTokenHash(token: string): string {
  return crypto.createHash("sha256").update(token).digest("hex");
}

export async function isPairedSyncRequest(request: IncomingMessage): Promise<boolean> {
  const deviceId = requestHeaderString(request, "x-device-id")?.trim();
  const authToken = requestHeaderString(request, "x-sync-token")?.trim();
  if (!deviceId || !authToken) return false;
  const state = await readSyncState();
  const device = state.devices.find((item) => item.deviceId === deviceId);
  if (!device?.authTokenHash) return false;
  const expected = Buffer.from(device.authTokenHash, "hex");
  const actual = Buffer.from(syncAuthTokenHash(authToken), "hex");
  return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
}

export async function requirePairedSyncDevice(request: IncomingMessage, response: ServerResponse): Promise<boolean> {
  if (await isPairedSyncRequest(request)) return true;
  sendJson(response, 403, { ok: false, message: "设备尚未配对，不能下载或上传书籍文件。" });
  return false;
}

export async function writeUploadedBookFile(book: LibraryBook, request: IncomingMessage): Promise<LibraryBook> {
  const rawOriginalFileName = requestHeaderString(request, "X-Original-File-Name");
  const originalFileName = rawOriginalFileName ? decodeURIComponent(rawOriginalFileName) : book.originalFileName ?? `${book.id}.${book.format}`;
  const format = normalizeBookFormat(book.format, originalFileName);
  const targetPath = path.join(appLibraryFilesRoot(), `${book.id}.${format}`);
  await ensureDir(appLibraryFilesRoot());
  const fileBuffer = await readRequestBuffer(request);
  await writeFile(targetPath, fileBuffer);
  const info = await stat(targetPath);
  const hash = crypto.createHash("sha256").update(fileBuffer).digest("hex");
  return {
    ...book,
    filePath: targetPath,
    originalFileName: book.originalFileName ?? originalFileName,
    originalFilePath: book.originalFilePath ?? originalFileName,
    originalPath: book.originalPath ?? originalFileName,
    format,
    size: info.size,
    contentHash: hash,
    updatedAt: now(),
    revision: book.revision + 1,
    deviceId: currentDeviceId()
  };
}

export async function uploadBookFile(bookId: string, request: IncomingMessage, response: ServerResponse): Promise<void> {
  const books = await readLibraryIndex({ includeDeleted: true });
  const book = books.find((item) => item.id === bookId);
  if (!book) {
    sendJson(response, 404, { ok: false, message: "请先同步书籍元数据，再上传书籍文件。" });
    return;
  }
  if (book.deletedAt) {
    sendJson(response, 410, { ok: false, message: "这本书已删除，不能继续上传文件。" });
    return;
  }
  const nextBook = await writeUploadedBookFile(book, request);
  await writeLibraryIndex([nextBook, ...books.filter((item) => item.id !== bookId)]);
  sendJson(response, 200, { ok: true, bookId, contentHash: nextBook.contentHash, size: nextBook.size });
}

export async function downloadBookChunk(bookId: string, index: number, response: ServerResponse): Promise<void> {
  const book = (await readLibraryIndex()).find((item) => item.id === bookId);
  if (!book) {
    sendJson(response, 404, { ok: false, message: "没有找到这本书。" });
    return;
  }
  const fileStats = await stat(book.filePath);
  const start = index * SYNC_CHUNK_SIZE;
  if (start >= fileStats.size || index < 0) {
    sendJson(response, 416, { ok: false, message: "分块序号超出范围。" });
    return;
  }
  // Read only the required byte range via stream instead of loading entire file
  const end = Math.min(start + SYNC_CHUNK_SIZE - 1, fileStats.size - 1);
  const chunks: Buffer[] = [];
  const stream = createReadStream(book.filePath, { start, end });
  await new Promise<void>((resolve, reject) => {
    stream.on("data", (chunk: Buffer | string) => chunks.push(typeof chunk === "string" ? Buffer.from(chunk) : chunk));
    stream.on("end", resolve);
    stream.on("error", reject);
  });
  const chunkBuffer = Buffer.concat(chunks);
  sendCorsHeaders(response);
  response.statusCode = 200;
  response.setHeader("Content-Type", "application/octet-stream");
  response.setHeader("Content-Range", `bytes ${start}-${end}/${fileStats.size}`);
  response.setHeader("X-Chunk-Index", String(index));
  response.end(chunkBuffer);
}

export async function handlePairingRequest(request: IncomingMessage, response: ServerResponse, url: URL): Promise<void> {
  const body = request.method === "POST" ? await readRequestJson<{ token?: string; device?: DeviceInfo }>(request) : {};
  const token = body.token ?? url.searchParams.get("token") ?? undefined;
  if (!isPairingTokenValid(token)) {
    sendJson(response, 401, { ok: false, message: "配对码无效或已过期，请在电脑端重新生成。" });
    return;
  }
  // 配对码一次有效：验证通过立即作废，防止有效期内被重放
  pairingToken = undefined;
  const device =
    normalizeDeviceInfo(body.device) ??
    ({
      deviceId: `android-${crypto.createHash("sha256").update(token ?? "").digest("hex").slice(0, 12)}`,
      name: "Android 手机端",
      platform: "android",
      pairedAt: now(),
      lastSeenAt: now()
    } satisfies DeviceInfo);
  const deviceAuthToken = crypto.randomBytes(32).toString("hex");
  await upsertPairedDevice(device, syncAuthTokenHash(deviceAuthToken));
  sendJson(response, 200, {
    ok: true,
    device: await desktopDeviceInfo(),
    manifest: await buildSyncManifest(),
    authToken: deviceAuthToken
  });
}

export async function handleSyncRequest(request: IncomingMessage, response: ServerResponse): Promise<void> {
  sendCorsHeaders(response);
  if (request.method === "OPTIONS") {
    response.statusCode = 204;
    response.end();
    return;
  }
  const url = new URL(request.url ?? "/", "http://127.0.0.1");
  if (url.pathname === "/sync/pair") {
    await handlePairingRequest(request, response, url);
    return;
  }
  if (request.method === "GET" && url.pathname === "/sync/manifest") {
    if (!(await requirePairedSyncDevice(request, response))) return;
    sendJson(response, 200, await buildSyncManifest());
    return;
  }
  if (request.method === "POST" && url.pathname === "/sync/pull") {
    if (!(await requirePairedSyncDevice(request, response))) return;
    sendJson(response, 200, await buildSyncPullResponse());
    return;
  }
  if (request.method === "POST" && url.pathname === "/sync/push") {
    if (!(await requirePairedSyncDevice(request, response))) return;
    const payload = await readRequestJson<SyncPushPayload>(request);
    sendJson(response, 200, await applySyncPush(payload));
    return;
  }
  if (url.pathname.startsWith("/sync/books/")) {
    const parts = url.pathname.split("/").map(decodeURIComponent);
    const bookId = parts[3];
    if (request.method === "GET" && parts[4] === "file" && bookId) {
      if (!(await requirePairedSyncDevice(request, response))) return;
      await downloadBookFile(bookId, response);
      return;
    }
    if (request.method === "PUT" && parts[4] === "file" && bookId) {
      if (!(await requirePairedSyncDevice(request, response))) return;
      await uploadBookFile(bookId, request, response);
      return;
    }
    if (request.method === "GET" && parts[4] === "chunks" && bookId) {
      if (!(await requirePairedSyncDevice(request, response))) return;
      await downloadBookChunk(bookId, Number(parts[5] ?? "-1"), response);
      return;
    }
  }
  sendText(response, 404, "Not found");
}
