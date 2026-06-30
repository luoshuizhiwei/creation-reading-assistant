import type { InspirationItem } from "./inspiration";
import type { LibraryBook, ReadingProgress, ReadingSession } from "./library";

export type SyncPlatform = "desktop" | "android" | "ios" | "web";
export type SyncRecordType = "inspiration" | "book" | "progress" | "session";

export interface DeviceInfo {
  deviceId: string;
  name: string;
  platform: SyncPlatform;
  pairedAt: string;
  lastSeenAt: string;
}

export interface SyncEnvelope<T> {
  id: string;
  type: SyncRecordType;
  revision: number;
  deviceId: string;
  updatedAt: string;
  deletedAt?: string;
  payload: T;
}

export interface BookFileManifest {
  bookId: string;
  fileName: string;
  format: "txt" | "md" | "epub";
  contentHash?: string;
  size: number;
  chunkSize: number;
}

export interface SyncManifest {
  device: DeviceInfo;
  generatedAt: string;
  inspirations: Array<SyncEnvelope<Pick<InspirationItem, "id">>>;
  books: Array<SyncEnvelope<Pick<LibraryBook, "id">>>;
  progress: Array<SyncEnvelope<Pick<ReadingProgress, "bookId">>>;
  sessions: Array<SyncEnvelope<Pick<ReadingSession, "id">>>;
  bookFiles: BookFileManifest[];
}

export interface SyncPullResponse {
  manifest: SyncManifest;
  inspirations: Array<SyncEnvelope<InspirationItem>>;
  books: Array<SyncEnvelope<LibraryBook>>;
  progress: Array<SyncEnvelope<ReadingProgress>>;
  sessions: Array<SyncEnvelope<ReadingSession>>;
}

export interface SyncPushPayload {
  device: DeviceInfo;
  inspirations?: Array<SyncEnvelope<InspirationItem>>;
  books?: Array<SyncEnvelope<LibraryBook>>;
  progress?: Array<SyncEnvelope<ReadingProgress>>;
  sessions?: Array<SyncEnvelope<ReadingSession>>;
}

export interface SyncPushResult {
  ok: boolean;
  applied: {
    inspirations: number;
    books: number;
    progress: number;
    sessions: number;
  };
  conflicts: Array<SyncEnvelope<InspirationItem>>;
  manifest: SyncManifest;
}

export interface PairingTokenResult {
  token: string;
  pairingUrl: string;
  qrPayload: string;
  pairingUrls: string[];
  qrPayloads: Array<{
    address: string;
    pairingUrl: string;
    qrPayload: string;
  }>;
  expiresAt: string;
}

export interface SyncStatus {
  running: boolean;
  port?: number;
  addresses: string[];
  device: DeviceInfo;
  pairingToken?: PairingTokenResult;
}
