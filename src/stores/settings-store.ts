import { create } from "zustand";
import type { AppSettings } from "@/types/settings";

interface SettingsState {
  settings?: AppSettings;
  loading: boolean;
  setSettings: (settings?: AppSettings) => void;
  setLoading: (loading: boolean) => void;
}

export const useSettingsStore = create<SettingsState>((set) => ({
  loading: false,
  setSettings: (settings) => set({ settings }),
  setLoading: (loading) => set({ loading })
}));
