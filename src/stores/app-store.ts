import { create } from "zustand";

export const APP_SCREENS = ["projects", "card-library", "inbox", "inspiration", "library", "reader", "stats", "settings"] as const;
export type AppScreen = (typeof APP_SCREENS)[number];

export interface AppError {
  id: string;
  message: string;
  timestamp: number;
}

interface AppState {
  screen: AppScreen;
  previousScreen?: AppScreen;
  loading: boolean;
  /** 写作台请求的应用壳级专注模式。 */
  creationFocusMode: boolean;
  errors: AppError[];
  setScreen: (screen: AppScreen) => void;
  setLoading: (loading: boolean) => void;
  setCreationFocusMode: (enabled: boolean) => void;
  setError: (error?: string) => void;
  dismissError: (id: string) => void;
  clearErrors: () => void;
}

let errorIdCounter = 0;

export const useAppStore = create<AppState>((set) => ({
  screen: "projects",
  loading: false,
  creationFocusMode: false,
  errors: [],
  setScreen: (screen) => set((state) => ({
    previousScreen: state.screen,
    screen,
    creationFocusMode: screen === "projects" ? state.creationFocusMode : false
  })),
  setLoading: (loading) => set({ loading }),
  setCreationFocusMode: (creationFocusMode) => set({ creationFocusMode }),
  setError: (error) => {
    if (!error) return;
    set((state) => ({
      errors: [...state.errors, { id: `err-${++errorIdCounter}-${Date.now()}`, message: error, timestamp: Date.now() }]
    }));
  },
  dismissError: (id) => set((state) => ({ errors: state.errors.filter((e) => e.id !== id) })),
  clearErrors: () => set({ errors: [] })
}));
