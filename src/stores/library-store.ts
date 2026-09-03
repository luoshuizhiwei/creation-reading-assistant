import { create } from "zustand";
import type { LibraryBook, ReaderActivityState, ReaderSettings, ReadingProgress, ReadingSession, ReadingStatsSummary } from "@/types/library";

interface LibraryState {
  books: LibraryBook[];
  progress: Record<string, ReadingProgress | undefined>;
  activeBook?: LibraryBook;
  activeContent: string;
  activeEpubUrl?: string;
  activeEpubTargetHref?: string;
  /** TXT/MD 搜索命中跳转请求：仅当目标书打开时消费一次并清除 */
  activeTextJump?: { bookId: string; charOffset: number };
  readerSettings?: ReaderSettings;
  activeSession?: ReadingSession;
  stats?: ReadingStatsSummary;
  activity: ReaderActivityState;
  loading: boolean;
  setBooks: (books: LibraryBook[]) => void;
  setActiveBook: (book?: LibraryBook, content?: string, epubUrl?: string, epubTargetHref?: string) => void;
  setProgress: (progress: ReadingProgress) => void;
  setReaderSettings: (settings: ReaderSettings) => void;
  setActiveSession: (session?: ReadingSession) => void;
  setStats: (stats?: ReadingStatsSummary) => void;
  setActivity: (activity: Partial<ReaderActivityState>) => void;
  setLoading: (loading: boolean) => void;
}

export const useLibraryStore = create<LibraryState>((set) => ({
  books: [],
  progress: {},
  activeContent: "",
  activity: {
    isReaderPageActive: false,
    isWindowFocused: true,
    isUserActive: false,
    lastInteractionAt: Date.now(),
    isTracking: false
  },
  loading: false,
  setBooks: (books) => set({ books }),
  setActiveBook: (activeBook, activeContent = "", activeEpubUrl, activeEpubTargetHref) =>
    set({ activeBook, activeContent, activeEpubUrl, activeEpubTargetHref, activeSession: undefined }),
  setProgress: (progress) =>
    set((state) => ({
      progress: { ...state.progress, [progress.bookId]: progress }
    })),
  setReaderSettings: (readerSettings) => set({ readerSettings }),
  setActiveSession: (activeSession) =>
    set((state) => ({
      activeSession,
      activity: {
        ...state.activity,
        activeSessionId: activeSession?.id,
        isTracking: activeSession?.status === "active" || activeSession?.status === "paused"
      }
    })),
  setStats: (stats) => set({ stats }),
  setActivity: (activity) => set((state) => ({ activity: { ...state.activity, ...activity } })),
  setLoading: (loading) => set({ loading })
}));
