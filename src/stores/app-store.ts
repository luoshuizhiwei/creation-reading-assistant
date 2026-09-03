import { create } from "zustand";

export const APP_SCREENS = ["projects", "inbox", "inspiration", "library", "reader", "stats", "settings"] as const;
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
  errors: AppError[];
  setScreen: (screen: AppScreen) => void;
  setLoading: (loading: boolean) => void;
  setError: (error?: string) => void;
  dismissError: (id: string) => void;
  clearErrors: () => void;
}

let errorIdCounter = 0;

export const useAppStore = create<AppState>((set) => ({
  screen: "projects",
  loading: false,
  errors: [],
  setScreen: (screen) => set((state) => ({ previousScreen: state.screen, screen })),
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
