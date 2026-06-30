import { create } from "zustand";
import type { InspirationItem } from "@/types/inspiration";

interface InspirationState {
  items: InspirationItem[];
  selectedId?: string;
  loading: boolean;
  setItems: (items: InspirationItem[]) => void;
  setSelectedId: (selectedId?: string) => void;
  upsertItem: (item: InspirationItem) => void;
  setLoading: (loading: boolean) => void;
}

export const useInspirationStore = create<InspirationState>((set) => ({
  items: [],
  loading: false,
  setItems: (items) =>
    set((state) => ({
      items,
      selectedId: state.selectedId && items.some((item) => item.id === state.selectedId) ? state.selectedId : items[0]?.id
    })),
  setSelectedId: (selectedId) => set({ selectedId }),
  upsertItem: (item) =>
    set((state) => ({
      items: [item, ...state.items.filter((current) => current.id !== item.id)].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
      selectedId: item.id
    })),
  setLoading: (loading) => set({ loading })
}));
