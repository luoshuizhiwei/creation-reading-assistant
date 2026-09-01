import { useCallback, useRef } from "react";
import { listBooks } from "@/services/library-service";
import { openBook, openEpub } from "@/services/reader-service";
import { sourcesForFilter, sortEntries } from "@/features/search/aggregate";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useLibraryStore } from "@/stores/library-store";
import { useSearchStore } from "@/stores/search-store";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import { messageFromError } from "@/utils/format";
import type { SearchNavigateIntent, UnifiedSearchEntry } from "@/types/search";

const SEARCH_DEBOUNCE_MS = 250;
let searchRunSequence = 0;

/**
 * openIntent 的类型化结果。
 *
 * - navigated：成功导航，调用方应关闭 SearchPanel。
 * - blocked：被 leave guard 拒绝（用户在脏正文项目中拒绝离开），
 *   调用方必须保持 SearchPanel 打开，保留关键词/筛选/结果，不产生部分导航状态。
 * - invalid：意图无效（缺关键字 ID 或目标不存在），已通过 toast 提示用户；
 *   调用方可保持 SearchPanel 打开或关闭（这里保持打开，让用户选其它结果）。
 */
export type OpenIntentResult = "navigated" | "blocked" | "invalid";

export function useSearchActions() {
  const setKeyword = useSearchStore((state) => state.setKeyword);
  const setResults = useSearchStore((state) => state.setResults);
  const setOpen = useSearchStore((state) => state.setOpen);
  const setLoading = useSearchStore((state) => state.setLoading);
  const setError = useSearchStore((state) => state.setError);
  const setAppError = useAppStore((state) => state.setError);
  const showToast = useUIStore((state) => state.showToast);
  const timerRef = useRef<number>();
  const abortRef = useRef<AbortController | undefined>();

  /** 取消进行中的搜索（abort 源扫描 + 清理防抖 timer）。取消是正常结束，不算来源失败。 */
  const cancelSearch = useCallback(() => {
    if (timerRef.current) {
      window.clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
    abortRef.current?.abort();
    abortRef.current = undefined;
  }, []);

  const runSearch = useCallback(
    (keyword: string) => {
      const sequence = ++searchRunSequence;
      setKeyword(keyword);
      cancelSearch();
      if (!keyword.trim()) {
        setLoading(false);
        setResults([]);
        setError(null);
        return;
      }
      timerRef.current = window.setTimeout(() => {
        setLoading(true);
        setError(null);
        const controller = new AbortController();
        abortRef.current = controller;
        const state = useSearchStore.getState();
        const projectId = state.allProjects ? undefined : state.projectContextId;
        const sources = sourcesForFilter(state.filter);
        void Promise.allSettled(
          sources.map((source) =>
            source.search({ keyword, projectId, limit: 50, filter: state.filter, signal: controller.signal })
          )
        )
          .then((settled) => {
            if (sequence !== searchRunSequence) return;
            const entries: UnifiedSearchEntry[] = [];
            const failures: string[] = [];
            settled.forEach((outcome, index) => {
              if (outcome.status === "fulfilled") {
                entries.push(...outcome.value);
                return;
              }
              // 主动取消（AbortError）不是来源失败，不提示用户。
              if (outcome.reason instanceof Error && outcome.reason.name === "AbortError") return;
              failures.push(sources[index]?.label ?? "未知来源");
            });
            sortEntries(entries);
            setResults(entries);
            if (failures.length > 0) {
              setError(`部分来源不可用：${failures.join("、")}。已显示其余来源的结果。`);
            }
          })
          .catch((error) => {
            if (sequence !== searchRunSequence) return;
            setError(messageFromError(error));
          })
          .finally(() => {
            if (sequence === searchRunSequence) setLoading(false);
          });
      }, SEARCH_DEBOUNCE_MS);
    },
    [cancelSearch, setError, setKeyword, setLoading, setResults]
  );

  const openIntent = useCallback(
    async (intent: SearchNavigateIntent): Promise<OpenIntentResult> => {
      const appState = useAppStore.getState();
      const leaveGuard = useCreationStore.getState().leaveGuard;
      // blocked 时保持原页面、保持 SearchPanel 打开、保留结果，不产生部分导航状态
      if (appState.screen === "projects" && leaveGuard && !(await leaveGuard())) {
        return "blocked";
      }
      const creationStore = useCreationStore.getState();
      switch (intent.kind) {
        case "project": {
          if (!intent.projectId) return "invalid";
          creationStore.setSelectedId(intent.projectId);
          useAppStore.getState().setScreen("projects");
          // 通过 seam 通知项目页切到 overview（而非默认 writing）。
          useCreationStore.getState().requestProjectNavigation({
            target: { projectId: intent.projectId, view: "overview" },
            createdAt: Date.now()
          });
          return "navigated";
        }
        case "chapter": {
          if (!intent.projectId || !intent.chapterId) return "invalid";
          useCreationStore.getState().setSelectedId(intent.projectId);
          useAppStore.getState().setScreen("projects");
          useCreationStore.getState().requestProjectNavigation({
            target: { projectId: intent.projectId, view: "writing", chapterId: intent.chapterId },
            createdAt: Date.now()
          });
          return "navigated";
        }
        case "scene": {
          if (!intent.projectId || !intent.sceneId) return "invalid";
          useCreationStore.getState().setSelectedId(intent.projectId);
          useAppStore.getState().setScreen("projects");
          useCreationStore.getState().requestProjectNavigation({
            target: { projectId: intent.projectId, view: "writing", sceneId: intent.sceneId },
            createdAt: Date.now()
          });
          return "navigated";
        }
        case "card": {
          if (!intent.projectId || !intent.cardId) return "invalid";
          useCreationStore.getState().setSelectedId(intent.projectId);
          useAppStore.getState().setScreen("projects");
          useCreationStore.getState().requestProjectNavigation({
            target: { projectId: intent.projectId, view: "cards", cardId: intent.cardId },
            createdAt: Date.now()
          });
          return "navigated";
        }
        case "inbox": {
          useAppStore.getState().setScreen("inbox");
          if (intent.inboxItemId) {
            // 提交收件箱选中请求，InboxPage 消费后选中目标条目。
            useCreationStore.getState().requestInboxSelection(intent.inboxItemId);
          }
          return "navigated";
        }
        case "book": {
          if (!intent.bookId) return "invalid";
          let books = useLibraryStore.getState().books;
          let book = books.find((item) => item.id === intent.bookId);
          if (!book) {
            books = await listBooks();
            useLibraryStore.getState().setBooks(books);
            book = books.find((item) => item.id === intent.bookId);
          }
          if (!book) throw new Error("未找到要打开的书籍");
          const payload = book.format === "epub" ? await openEpub(intent.bookId) : await openBook(intent.bookId);
          const content = "content" in payload ? payload.content : "";
          const epubUrl = "epubUrl" in payload ? payload.epubUrl : undefined;
          useLibraryStore.getState().setActiveBook(payload.book, content, epubUrl, intent.epubHref);
          if (payload.progress) useLibraryStore.getState().setProgress(payload.progress);
          useLibraryStore.getState().setReaderSettings(payload.settings);
          useAppStore.getState().setScreen("reader");
          if (book.format === "epub" && !intent.epubHref) {
            showToast({
              tone: "info",
              title: "EPUB 全文索引未建立",
              body: "已打开书籍首页；如需跳到具体章节，请先重新导入或等待索引生成。"
            });
          }
          return "navigated";
        }
        case "inspiration":
          if (intent.inspirationId) useInspirationStore.getState().setSelectedId(intent.inspirationId);
          useAppStore.getState().setScreen("inspiration");
          return "navigated";
      }
    },
    [showToast]
  );

  const openResult = useCallback(
    async (entry: UnifiedSearchEntry): Promise<OpenIntentResult> => {
      try {
        const result = await openIntent(entry.intent);
        // 只有 navigated 才关闭 SearchPanel；
        // blocked 保持打开 + 保留结果；invalid 也保持打开让用户选其它结果
        if (result === "navigated") {
          setOpen(false);
        }
        return result;
      } catch (error) {
        setAppError(messageFromError(error));
        return "invalid";
      }
    },
    [openIntent, setAppError, setOpen]
  );

  return { runSearch, openResult, openIntent, cancelSearch };
}
