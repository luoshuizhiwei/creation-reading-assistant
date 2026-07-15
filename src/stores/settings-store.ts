import { create } from "zustand";
import type { AppSettings } from "@/types/settings";

interface SettingsState {
  settings?: AppSettings;
  loading: boolean;
  error?: string;
  setSettings: (settings?: AppSettings) => void;
  setLoading: (loading: boolean) => void;
  setError: (error?: string) => void;
}

export const useSettingsStore = create<SettingsState>((set) => ({
  loading: false,
  setSettings: (settings) => set({ settings }),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error })
}));
