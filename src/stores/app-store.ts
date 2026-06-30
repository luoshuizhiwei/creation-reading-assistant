import { create } from "zustand";

export type AppScreen = "start" | "inspiration" | "library" | "reader" | "stats" | "settings";

export interface ReaderReturnState {
  bookId: string;
  label: string;
  fromScreen: AppScreen;
  progressLabel?: string;
}

interface AppState {
  screen: AppScreen;
  previousScreen?: AppScreen;
  readerReturn?: ReaderReturnState;
  loading: boolean;
  error?: string;
  setScreen: (screen: AppScreen, options?: { preserveReturn?: boolean }) => void;
  setReaderReturn: (readerReturn?: ReaderReturnState) => void;
  clearReaderReturn: () => void;
  setLoading: (loading: boolean) => void;
  setError: (error?: string) => void;
}

export const useAppStore = create<AppState>((set) => ({
  screen: "start",
  loading: false,
  setScreen: (screen, options) =>
    set((state) => ({
      previousScreen: state.screen,
      screen,
      readerReturn: options?.preserveReturn ? state.readerReturn : screen === "reader" ? undefined : state.readerReturn
    })),
  setReaderReturn: (readerReturn) => set({ readerReturn }),
  clearReaderReturn: () => set({ readerReturn: undefined }),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error })
}));

