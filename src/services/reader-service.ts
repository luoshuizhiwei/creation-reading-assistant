import { getDesktopApi } from "@/services/ipc-client";
import type {
  EndReadingSessionInput,
  GetReadingSessionsInput,
  LibraryBook,
  ReaderBookPayload,
  ReaderEpubPayload,
  ReaderPreset,
  ReaderSettings,
  ReadingLocation,
  ReadingProgress,
  ReadingSession,
  ReadingStatsSummary,
  RecoverReadingSessionsResult,
  SaveProgressInput,
  StartReadingSessionInput,
  TxtTocOverrides,
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

export async function getBatchProgress(bookIds: string[]): Promise<ReadingProgress[]> {
  const api = getDesktopApi();
  if (typeof api?.reader?.getBatchProgress === "function") {
    return api.reader.getBatchProgress(bookIds);
  }
  const results = await Promise.all(bookIds.map((id) => api.reader.getProgress(id)));
  return results.filter((p): p is ReadingProgress => Boolean(p));
}

export async function saveEpubLocation(input: SaveProgressInput): Promise<ReadingProgress> {
  return getDesktopApi().reader.saveEpubLocation(input);
}

export async function saveTxtTocOverrides(input: { bookId: string; overrides: TxtTocOverrides | null }): Promise<LibraryBook> {
  return getDesktopApi().reader.saveTxtTocOverrides(input);
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

/** 获取系统已安装字体列表（供字体选择器使用） */
export async function getInstalledFonts(): Promise<string[]> {
  return getDesktopApi().reader.getInstalledFonts();
}

/** 弹出系统文件对话框让用户选择字体文件 */
export async function chooseFont(): Promise<{ fileName: string; filePath: string } | null> {
  return getDesktopApi().reader.chooseFont();
}

/** 保存阅读器排版预设 */
export async function savePreset(preset: ReaderPreset): Promise<ReaderPreset> {
  return getDesktopApi().reader.savePreset(preset);
}

/** 删除阅读器排版预设 */
export async function deletePreset(presetId: string): Promise<void> {
  return getDesktopApi().reader.deletePreset(presetId);
}