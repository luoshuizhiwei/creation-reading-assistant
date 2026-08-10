import { create } from "zustand";

export type AppScreen = "start" | "projects" | "inspiration" | "library" | "reader" | "stats" | "settings";

export interface ReaderReturnState {
  bookId: string;
  label: string;
  fromScreen: AppScreen;
  progressLabel?: string;
}

export interface AppError {
  id: string;
  message: string;
  timestamp: number;
}

interface AppState {
  screen: AppScreen;
  previousScreen?: AppScreen;
  readerReturn?: ReaderReturnState;
  loading: boolean;
  errors: AppError[];
  setScreen: (screen: AppScreen, options?: { preserveReturn?: boolean }) => void;
  setReaderReturn: (readerReturn?: ReaderReturnState) => void;
  clearReaderReturn: () => void;
  setLoading: (loading: boolean) => void;
  setError: (error?: string) => void;
  dismissError: (id: string) => void;
  clearErrors: () => void;
}

let errorIdCounter = 0;

export const useAppStore = create<AppState>((set) => ({
  screen: "start",
  loading: false,
  errors: [],
  setScreen: (screen, options) =>
    set((state) => ({
      previousScreen: state.screen,
      screen,
      readerReturn: options?.preserveReturn ? state.readerReturn : screen === "reader" ? undefined : state.readerReturn
    })),
  setReaderReturn: (readerReturn) => set({ readerReturn }),
  clearReaderReturn: () => set({ readerReturn: undefined }),
  setLoading: (loading) => set({ loading }),
  setError: (error) => {
    if (!error) return;
    set((state) => ({
      errors: [...state.errors, { id: `err-${++errorIdCounter}-${Date.now()}`, message: error, timestamp: Date.now() }]
    }));
  },
  dismissError: (id) => set((state) => ({ errors: state.errors.filter((e) => e.id !== id) })),
  clearErrors: () => set({ errors: [] })
}));
