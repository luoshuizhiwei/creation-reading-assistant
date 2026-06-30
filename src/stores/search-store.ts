import { create } from "zustand";
import type { SearchResult } from "@/types/search";

interface SearchState {
  keyword: string;
  results: SearchResult[];
  open: boolean;
  loading: boolean;
  setKeyword: (keyword: string) => void;
  setResults: (results: SearchResult[]) => void;
  setOpen: (open: boolean) => void;
  setLoading: (loading: boolean) => void;
  reset: () => void;
}

export const useSearchStore = create<SearchState>((set) => ({
  keyword: "",
  results: [],
  open: false,
  loading: false,
  setKeyword: (keyword) => set({ keyword }),
  setResults: (results) => set({ results }),
  setOpen: (open) => set({ open }),
  setLoading: (loading) => set({ loading }),
  reset: () => set({ keyword: "", results: [], open: false, loading: false })
}));
