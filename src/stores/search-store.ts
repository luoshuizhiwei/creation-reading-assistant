import { create } from "zustand";
import type { UnifiedSearchEntry, UnifiedSearchFilter } from "@/types/search";

interface UnifiedSearchState {
  open: boolean;
  keyword: string;
  filter: UnifiedSearchFilter;
  /** 当前项目上下文：项目内打开搜索时默认只搜该项目；undefined = 全部项目。 */
  projectContextId?: string;
  /** 是否忽略项目上下文搜索全部项目。 */
  allProjects: boolean;
  results: UnifiedSearchEntry[];
  loading: boolean;
  /** 部分来源失败时的可读说明（其他来源结果仍保留）。 */
  error: string | null;
  setOpen: (open: boolean) => void;
  openSearch: (options?: { projectId?: string; filter?: UnifiedSearchFilter }) => void;
  setProjectContext: (projectId?: string) => void;
  setFilter: (filter: UnifiedSearchFilter) => void;
  setAllProjects: (allProjects: boolean) => void;
  setKeyword: (keyword: string) => void;
  setResults: (results: UnifiedSearchEntry[]) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  reset: () => void;
}

export const useSearchStore = create<UnifiedSearchState>((set) => ({
  open: false,
  keyword: "",
  filter: "all",
  projectContextId: undefined,
  allProjects: false,
  results: [],
  loading: false,
  error: null,
  setOpen: (open) => set({ open }),
  openSearch: (options) =>
    set((state) => ({
      open: true,
      projectContextId: options?.projectId ?? state.projectContextId,
      filter: options?.filter ?? state.filter
    })),
  setProjectContext: (projectId) =>
    set((state) => ({
      projectContextId: projectId,
      // 项目上下文变化时保持"全部项目"为关闭，避免残留跨项目搜索。
      allProjects: projectId === undefined ? state.allProjects : false
    })),
  setFilter: (filter) => set({ filter }),
  setAllProjects: (allProjects) => set({ allProjects }),
  setKeyword: (keyword) => set({ keyword }),
  setResults: (results) => set({ results }),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error }),
  reset: () => set({ keyword: "", results: [], loading: false, error: null, filter: "all", allProjects: false })
}));
