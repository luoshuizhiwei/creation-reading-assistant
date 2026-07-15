import { useEffect, useRef, useState, type Dispatch, type SetStateAction } from "react";
import { Capacitor } from "@capacitor/core";
import {
  buildReaderPreview,
  canCacheReaderText,
  withTimeout,
  writeReaderTextCache
} from "../utils/mobile-helpers";
import {
  BOOK_CONTENT_STORAGE_KEY_PREFIX,
  loadMobileReaderSettings,
  saveMobileReaderSettings,
  saveMobileSnapshot,
  type MobileSnapshot
} from "../services/mobile-storage";
import { readMobileBookFile, statMobileBookFile } from "../storage/mobile-files";
import { clearContinueRemoval } from "../features/shelf/book-progress";
import { getReadableBookLocalPath, isBookDownloaded } from "../features/shelf/book-status";
import {
  defaultReaderSettings,
  idleReaderState,
  type ReaderState
} from "../features/reader/reader-model";
import type { MobileBook, MobileReaderSettings } from "../types/mobile";

const READER_CONTENT_CACHE_MAX = 5;

interface UseMobileReaderBookOptions {
  snapshot: MobileSnapshot;
  setSnapshot: Dispatch<SetStateAction<MobileSnapshot>>;
  setMessage: (message: string) => void;
}

/**
 * 阅读器书籍加载与状态管理。
 * - 维护 readerBook / readerState / readerSettings
 * - openBook 优先用预览/缓存快速可见，再异步加载全文（12s 超时）
 * - closeMobileReader 与 pushMobileReaderHistory 协作处理 Android 返回键的历史栈
 * - 暴露 readerContentCacheRef 供同步/导入流程清缓存或写缓存
 */
export function useMobileReaderBook({ snapshot, setSnapshot, setMessage }: UseMobileReaderBookOptions) {
  const [readerBook, setReaderBook] = useState<MobileBook>();
  const [readerState, setReaderState] = useState<ReaderState>(idleReaderState);
  const [readerSettings, setReaderSettingsState] = useState<MobileReaderSettings>(() => loadMobileReaderSettings());

  const openBookRequestRef = useRef(0);
  const readerLoadSeqRef = useRef(0);
  const readerContentCacheRef = useRef(new Map<string, string>());
  const readerHistoryTokenRef = useRef<string>();
  const readerHistoryClosingRef = useRef(false);

  // 包装设置更新：同步写入 localStorage，保证杀进程后设置不丢失
  const setReaderSettings = (next: MobileReaderSettings | ((prev: MobileReaderSettings) => MobileReaderSettings)) => {
    setReaderSettingsState((prev) => {
      const updated = typeof next === "function" ? (next as (prev: MobileReaderSettings) => MobileReaderSettings)(prev) : next;
      saveMobileReaderSettings(updated);
      return updated;
    });
  };

  const evictReaderContentCache = (bookId: string, content: string) => {
    const cache = readerContentCacheRef.current;
    cache.delete(bookId);
    cache.set(bookId, content);
    if (cache.size > READER_CONTENT_CACHE_MAX) {
      const oldest = cache.keys().next().value;
      if (oldest !== undefined) cache.delete(oldest);
    }
  };

  const pushMobileReaderHistory = (bookId: string) => {
    if (!Capacitor.isNativePlatform()) return;
    const token = `reader-${bookId}-${Date.now()}`;
    readerHistoryTokenRef.current = token;
    try {
      window.history.pushState({ ...(window.history.state ?? {}), mobileReaderToken: token }, "", window.location.href);
    } catch {
      readerHistoryTokenRef.current = undefined;
    }
  };

  const closeMobileReader = () => {
    if (Capacitor.isNativePlatform() && readerHistoryTokenRef.current && !readerHistoryClosingRef.current) {
      readerHistoryClosingRef.current = true;
      try {
        window.history.back();
      } catch {
        // If the WebView refuses history navigation, still close the reader state below.
      } finally {
        window.setTimeout(() => {
          // popstate 正常会先消费该标记；这里只是 WebView 未派发 popstate 时的兜底。
          readerHistoryClosingRef.current = false;
          readerHistoryTokenRef.current = undefined;
        }, 600);
      }
    }
    openBookRequestRef.current += 1;
    readerLoadSeqRef.current += 1;
    setReaderBook(undefined);
    setReaderState(idleReaderState);
  };

  const readMobileBookContent = async (book: MobileBook): Promise<string | undefined> => {
    if (book.contentStatus === "missing") throw new Error("missing_content: 这本书的正文尚未下载到本机。");
    if (book.contentStatus === "failed") throw new Error("read_failed: 这本书上次下载或保存正文失败，请重新导入或重新同步。");
    const localPath = getReadableBookLocalPath(book);
    if (!localPath) throw new Error("missing_content: 正文文件路径缺失。");
    const fileStat = await statMobileBookFile(localPath);
    if (!fileStat?.size) throw new Error("missing_content: 正文文件不存在或已被清理。");
    const savedContent = localStorage.getItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${book.id}`);
    if (savedContent?.trim()) return savedContent;
    const content = await readMobileBookFile(localPath, book.format);
    if (!content?.trim()) return undefined;
    if (canCacheReaderText(book.format, content, book.size)) {
      writeReaderTextCache(book.id, content);
    }
    return content;
  };

  const openBook = (book: MobileBook) => {
    clearContinueRemoval(book.id);
    const requestId = openBookRequestRef.current + 1;
    openBookRequestRef.current = requestId;
    const loadSeq = readerLoadSeqRef.current + 1;
    readerLoadSeqRef.current = loadSeq;
    const isCurrentReaderLoad = () => openBookRequestRef.current === requestId && readerLoadSeqRef.current === loadSeq;
    const setReaderStateIfCurrent = (nextState: ReaderState) => {
      setReaderState((previous) => {
        if (!isCurrentReaderLoad() || previous.bookId !== book.id) return previous;
        return nextState;
      });
    };
    const cachedContent = readerContentCacheRef.current.get(book.id)?.trim() ? readerContentCacheRef.current.get(book.id) : undefined;
    const previewContent = cachedContent ?? book.readerPreview?.trim() ?? "";
    const immediateContent = cachedContent ?? previewContent;
    pushMobileReaderHistory(book.id);
    setReaderBook(book);
    setReaderState({
      phase: previewContent ? "preview" : "opening",
      bookId: book.id,
      title: book.title,
      visibleContent: immediateContent
    });
    if (!isBookDownloaded(book)) {
      setReaderState({
        phase: "error",
        bookId: book.id,
        title: book.title,
        visibleContent: "",
        errorCode: "not_downloaded",
        errorMessage: "这本书的正文尚未下载到本机。请先下载正文，或重新导入本地文件。"
      });
      return;
    }

    window.requestAnimationFrame(() => {
      if (previewContent && isCurrentReaderLoad()) {
        setReaderStateIfCurrent({
          phase: "loadingFullContent",
          bookId: book.id,
          title: book.title,
          visibleContent: previewContent
        });
      }
      void withTimeout(readMobileBookContent(book), 12_000, "正文打开超时")
        .then((content) => {
          if (!isCurrentReaderLoad()) return;
          if (!content?.trim()) {
            setReaderStateIfCurrent({
              phase: "error",
              bookId: book.id,
              title: book.title,
              visibleContent: "",
              errorCode: "empty_content",
              errorMessage: "这本书的正文为空或没有读到内容。请返回书架重新导入本地文件；如果它来自电脑同步，请先重新下载正文。"
            });
            return;
          }
          const nextContent = content;
          evictReaderContentCache(book.id, nextContent);
          setReaderStateIfCurrent({
            phase: "ready",
            bookId: book.id,
            title: book.title,
            visibleContent: nextContent,
            fullContent: nextContent
          });
          if (content && !book.readerPreview && book.format !== "epub") {
            const readerPreview = buildReaderPreview(content);
            if (readerPreview) {
              setSnapshot((prev) => {
                const next = {
                  ...prev,
                  books: prev.books.map((item) => item.id === book.id ? { ...item, readerPreview } : item)
                };
                void saveMobileSnapshot(next);
                return next;
              });
            }
          }
        })
        .catch((error) => {
          if (!isCurrentReaderLoad()) return;
          const detail = error instanceof Error ? error.message : String(error);
          const isMissingContent = /missing_content/i.test(detail);
          if (isMissingContent) {
            setSnapshot((prev) => {
              const next = {
                ...prev,
                books: prev.books.map((item) => item.id === book.id ? { ...item, contentStatus: "missing" as const } : item)
              };
              void saveMobileSnapshot(next);
              return next;
            });
          }
          setReaderStateIfCurrent({
            phase: "error",
            bookId: book.id,
            title: book.title,
            visibleContent: "",
            errorCode: /超时|timeout/i.test(detail) ? "read_timeout" : isMissingContent ? "missing_content" : "read_failed",
            errorMessage: /超时|timeout/i.test(detail)
              ? "正文打开超过 12 秒，已停止等待。可以返回书架重新导入，或重新下载正文。"
              : isMissingContent
                ? detail.replace(/^missing_content:\s*/i, "")
              : `读取本地正文失败：${detail}`
          });
          setMessage(`读取《${book.title}》失败：${detail}`);
        });
    });
  };

  // popstate 监听：用户按物理返回键触发 history.back() 时关闭阅读器
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return undefined;
    const handleReaderHistoryBack = () => {
      if (!readerBook) return;
      // UI 返回按钮/硬件返回键已经先清理阅读器状态时，history.back() 只负责
      // 消费进入阅读器时压入的历史项，不能再次执行 closeMobileReader。
      if (readerHistoryClosingRef.current) {
        readerHistoryTokenRef.current = undefined;
        readerHistoryClosingRef.current = false;
        return;
      }
      readerHistoryClosingRef.current = true;
      readerHistoryTokenRef.current = undefined;
      closeMobileReader();
      window.setTimeout(() => {
        readerHistoryClosingRef.current = false;
      }, 0);
    };
    window.addEventListener("popstate", handleReaderHistoryBack);
    return () => window.removeEventListener("popstate", handleReaderHistoryBack);
  }, [readerBook]);

  return {
    readerBook,
    setReaderBook,
    readerState,
    setReaderState,
    readerSettings,
    setReaderSettings,
    openBook,
    closeMobileReader,
    readMobileBookContent,
    readerContentCacheRef
  };
}
