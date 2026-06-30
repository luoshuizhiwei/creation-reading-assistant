import { getDesktopApi } from "@/services/ipc-client";
import type {
  EndReadingSessionInput,
  GetReadingSessionsInput,
  ReaderBookPayload,
  ReaderEpubPayload,
  ReaderSettings,
  ReadingLocation,
  ReadingProgress,
  ReadingSession,
  ReadingStatsSummary,
  RecoverReadingSessionsResult,
  SaveProgressInput,
  StartReadingSessionInput,
  UpdateReadingSessionInput
} from "@/types/library";

export async function openBook(bookId: string): Promise<ReaderBookPayload> {
  return getDesktopApi().reader.openBook(bookId);
}

export async function openEpub(bookId: string): Promise<ReaderEpubPayload> {
  return getDesktopApi().reader.openEpub(bookId);
}

export async function saveProgress(input: SaveProgressInput): Promise<ReadingProgress> {
  return getDesktopApi().reader.saveProgress(input);
}

export async function getProgress(bookId: string): Promise<ReadingProgress | undefined> {
  return getDesktopApi().reader.getProgress(bookId);
}

export async function saveEpubLocation(input: SaveProgressInput): Promise<ReadingProgress> {
  return getDesktopApi().reader.saveEpubLocation(input);
}

export async function getEpubLocation(bookId: string): Promise<ReadingLocation | undefined> {
  return getDesktopApi().reader.getEpubLocation(bookId);
}

export async function startSession(input: StartReadingSessionInput): Promise<ReadingSession> {
  return getDesktopApi().reader.startSession(input);
}

export async function updateSession(input: UpdateReadingSessionInput): Promise<ReadingSession> {
  return getDesktopApi().reader.updateSession(input);
}

export async function endSession(input: EndReadingSessionInput): Promise<ReadingSession> {
  return getDesktopApi().reader.endSession(input);
}

export async function recoverActiveSession(): Promise<RecoverReadingSessionsResult> {
  return getDesktopApi().reader.recoverActiveSession();
}

export async function getSessions(input?: GetReadingSessionsInput): Promise<ReadingSession[]> {
  return getDesktopApi().reader.getSessions(input);
}

export async function getReadingStats(): Promise<ReadingStatsSummary> {
  return getDesktopApi().reader.getStats();
}

export async function getReaderSettings(): Promise<ReaderSettings> {
  return getDesktopApi().reader.getSettings();
}

export async function updateReaderSettings(settings: Partial<ReaderSettings>): Promise<ReaderSettings> {
  return getDesktopApi().reader.updateSettings(settings);
}
