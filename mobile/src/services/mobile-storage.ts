import type { InspirationItem } from "../../../src/types/inspiration";
import type { LibraryBook, ReadingProgress, ReadingSession } from "../../../src/types/library";

const STORAGE_KEY = "creation-reading-assistant-mobile-snapshot";

export interface MobileSnapshot {
  inspirations: InspirationItem[];
  books: LibraryBook[];
  progress: ReadingProgress[];
  sessions: ReadingSession[];
  updatedAt: string;
}

export async function loadMobileSnapshot(): Promise<MobileSnapshot> {
  const fallback: MobileSnapshot = {
    inspirations: [],
    books: [],
    progress: [],
    sessions: [],
    updatedAt: new Date().toISOString()
  };
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return fallback;
  try {
    const parsed = JSON.parse(raw) as Partial<MobileSnapshot>;
    return {
      inspirations: Array.isArray(parsed.inspirations) ? parsed.inspirations : [],
      books: Array.isArray(parsed.books) ? parsed.books : [],
      progress: Array.isArray(parsed.progress) ? parsed.progress : [],
      sessions: Array.isArray(parsed.sessions) ? parsed.sessions : [],
      updatedAt: typeof parsed.updatedAt === "string" ? parsed.updatedAt : fallback.updatedAt
    };
  } catch {
    return fallback;
  }
}

export async function saveMobileSnapshot(snapshot: MobileSnapshot): Promise<void> {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
}
