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
  addMobileInspiration,
  addMobileNote,
  addMobileReadingSession,
  loadMobileReaderSettings,
  saveMobileReadingProgress,
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
import {
  loadReaderEngineVersionForBook,
  saveReaderEngineVersionForBook
} from "../features/reader/engine-v2/engine-version";
import {
  acknowledgeNativeReaderCheckpoints,
  acknowledgeNativeReaderActions,
  canOpenWithNativeReader,
  getPendingNativeReaderCheckpoints,
  getPendingNativeReaderActions,
  mergeNativeSettingsIntoMobile,
  nativeSettingsFromMobile,
  openNativeReader,
  type NativeReaderAction,
  type NativeReaderCheckpoint
} from "../native/native-reader";
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
  const readerOpenAbortRef = useRef<AbortController>();
  const readerContentCacheRef = useRef(new Map<string, string>());
  const readerHistoryTokenRef = useRef<string>();
  const readerHistoryClosingRef = useRef(false);
  const nativeReaderOpeningRef = useRef(false);
  const nativeRecoveryRef = useRef(false);
  const snapshotRef = useRef(snapshot);
  snapshotRef.current = snapshot;

  // 包装设置更新：同步写入 localStorage，保证杀进程后设置不丢失
  const setReaderSettings = (next: MobileReaderSettings | ((prev: MobileReaderSettings) => MobileReaderSettings)) => {
    setReaderSettingsState((prev) => {
      const updated = typeof next === "function" ? (next as (prev: MobileReaderSettings) => MobileReaderSettings)(prev) : next;
      saveMobileReaderSettings(updated);
      return updated;
    });
  };

  const persistNativeReaderAction = async (action: NativeReaderAction, fallbackBook?: MobileBook) => {
    const book = snapshotRef.current.books.find((item) => item.id === action.bookId) ??
      (fallbackBook?.id === action.bookId ? fallbackBook : undefined);
    if (!book) throw new Error(`找不到原生阅读动作对应的书籍：${action.bookTitle || action.bookId}`);

    const actionProgress = Math.max(0, Math.min(100, action.progressPercent ?? 0));
    const locator = {
      version: 2 as const,
      bookId: book.id,
      format: book.format === "md" ? "markdown" as const : book.format,
      progression: actionProgress / 100,
      chapterId: action.chapterTitle,
      href: action.epubHref,
      textOffset: action.charOffset,
      epub: book.format === "epub"
        ? { position: action.chapterIndex, totalProgression: actionProgress / 100 }
        : undefined,
      updatedAt: action.createdAt ? new Date(action.createdAt).getTime() : Date.now()
    };
    const persistedId = action.actionId
      ? `native-${action.type}-${action.actionId}`
      : undefined;
    let next = snapshotRef.current;
    if (action.type === "bookmark" || action.type === "note") {
      next = await addMobileNote(next, {
        id: persistedId,
        book,
        title: `${action.type === "bookmark" ? "书签" : "笔记"}：${action.chapterTitle ?? book.title}`,
        body: action.excerpt ?? "",
        excerpt: action.excerpt,
        chapterTitle: action.chapterTitle,
        progressPercent: actionProgress,
        locator,
        kind: action.type
      });
    } else {
      next = await addMobileInspiration(next, {
        id: persistedId,
        title: "新的阅读灵感",
        body: "",
        tags: ["阅读灵感"],
        source: {
          bookId: book.id,
          bookTitle: book.title,
          bookAuthor: book.author,
          format: book.format,
          chapterTitle: action.chapterTitle,
          locationLabel: action.chapterTitle ?? `${actionProgress.toFixed(1)}%`,
          progressPercent: actionProgress,
          excerpt: action.excerpt,
          href: action.epubHref,
          createdFrom: action.excerpt ? "reader-selection" : "manual",
          locator
        }
      });
    }
    snapshotRef.current = next;
    setSnapshot(next);
    if (action.actionId) await acknowledgeNativeReaderActions([action.actionId]);
    return next;
  };

  const persistNativeReaderActions = async (actions: NativeReaderAction[], fallbackBook?: MobileBook) => {
    for (const action of actions) await persistNativeReaderAction(action, fallbackBook);
  };

  const persistNativeReaderCheckpoint = async (checkpoint: NativeReaderCheckpoint, fallbackBook?: MobileBook) => {
    const book = snapshotRef.current.books.find((item) => item.id === checkpoint.bookId) ??
      (fallbackBook?.id === checkpoint.bookId ? fallbackBook : undefined);
    if (!book) throw new Error(`找不到原生阅读会话对应的书籍：${checkpoint.bookTitle || checkpoint.bookId}`);
    const progressPercent = Math.max(0, Math.min(100, checkpoint.locator?.progressPercent ?? 0));
    const charOffset = Math.max(0, checkpoint.locator?.charOffset ?? 0);
    const pageIndex = Math.max(0, checkpoint.locator?.pageIndex ?? 0);
    const nativeLocation = book.format === "epub"
      ? {
          mode: "epub-cfi" as const,
          precision: "exact" as const,
          text: { charOffset, chapterRef: checkpoint.chapterTitle },
          epub: {
            href: checkpoint.locator?.epubHref,
            spineIndex: checkpoint.locator?.chapterIndex,
            chapterRef: checkpoint.chapterTitle
          },
          page: { pageIndex }
        }
      : {
          mode: "text-anchor" as const,
          precision: "exact" as const,
          text: { charOffset, chapterRef: checkpoint.chapterTitle },
          page: { pageIndex }
        };
    let next = await saveMobileReadingProgress(snapshotRef.current, book, progressPercent, 0, nativeLocation);
    const activeDurationMs = Math.max(0, checkpoint.activeDurationMs ?? 0);
    if (activeDurationMs > 0) {
      next = await addMobileReadingSession(
        next,
        book,
        activeDurationMs,
        progressPercent,
        nativeLocation,
        `native-session-${checkpoint.sessionId}`
      );
    }
    if (checkpoint.settings) {
      setReaderSettings((current) => mergeNativeSettingsIntoMobile(current, checkpoint.settings));
    }
    snapshotRef.current = next;
    setSnapshot(next);
    await acknowledgeNativeReaderCheckpoints([checkpoint.sessionId]);
    return next;
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
    readerOpenAbortRef.current?.abort();
    readerOpenAbortRef.current = undefined;
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
    readerOpenAbortRef.current?.abort();
    const openAbort = new AbortController();
    readerOpenAbortRef.current = openAbort;
    clearContinueRemoval(book.id);
    const requestId = openBookRequestRef.current + 1;
    const engineVersion = loadReaderEngineVersionForBook(book);
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
    if (engineVersion === "native-legado" && canOpenWithNativeReader(book, readerSettings)) {
      if (nativeReaderOpeningRef.current) return;
      nativeReaderOpeningRef.current = true;
      void (async () => {
        let nativeReaderReturned = false;
        try {
          const localPath = getReadableBookLocalPath(book);
          if (!localPath) throw new Error("正文文件路径缺失");
          const fileStat = await statMobileBookFile(localPath);
          const fileUri = fileStat?.uri ?? book.localUri;
          if (!fileUri) throw new Error("无法取得原生阅读器可访问的本地文件地址");
          const latestSnapshot = snapshotRef.current;
          const existingProgress = latestSnapshot.progress.find((item) => item.bookId === book.id);
          const result = await openNativeReader({
            bookId: book.id,
            title: book.title,
            author: book.author,
            fileUri,
            format: book.format === "epub" ? "epub" : book.format === "md" ? "md" : "txt",
            locator: {
              chapterIndex: existingProgress?.currentLocation?.epub?.spineIndex,
              charOffset: existingProgress?.currentLocation?.text?.charOffset,
              pageIndex: existingProgress?.currentLocation?.page?.pageIndex,
              progressPercent: existingProgress?.progressPercent,
              epubHref: existingProgress?.currentLocation?.epub?.href
            },
            settings: nativeSettingsFromMobile(readerSettings)
          });
          nativeReaderReturned = true;
          if (result.cancelled) return;
          if (result.settings) {
            setReaderSettings((current) => mergeNativeSettingsIntoMobile(current, result.settings));
          }
          const progressPercent = Math.max(0, Math.min(100, result.progressPercent ?? 0));
          const charOffset = Math.max(0, result.charOffset ?? 0);
          const pageIndex = Math.max(0, result.pageIndex ?? 0);
          const nativeLocation = book.format === "epub"
            ? {
                mode: "epub-cfi" as const,
                precision: "exact" as const,
                text: { charOffset, chapterRef: result.chapterTitle },
                epub: {
                  href: result.epubHref,
                  spineIndex: result.chapterIndex,
                  chapterRef: result.chapterTitle
                },
                page: { pageIndex }
              }
            : {
                mode: "text-anchor" as const,
                precision: "exact" as const,
                text: { charOffset, chapterRef: result.chapterTitle },
                page: { pageIndex }
              };
          let next = await saveMobileReadingProgress(
            snapshotRef.current,
            book,
            progressPercent,
            0,
            nativeLocation
          );
          const activeDurationMs = Math.max(0, result.activeDurationMs ?? 0);
          if (activeDurationMs > 0) {
            next = await addMobileReadingSession(
              next,
              book,
              activeDurationMs,
              progressPercent,
              nativeLocation,
              result.sessionId ? `native-session-${result.sessionId}` : undefined
            );
          }
          snapshotRef.current = next;
          setSnapshot(next);
          if (result.sessionId) await acknowledgeNativeReaderCheckpoints([result.sessionId]);
          try {
            await persistNativeReaderActions(result.actions ?? [], book);
          } catch (actionError) {
            const detail = actionError instanceof Error ? actionError.message : String(actionError);
            setMessage(`阅读位置已保存，但部分书签/笔记稍后重试：${detail}`);
          }
        } catch (error) {
          const detail = error instanceof Error ? error.message : String(error);
          if (nativeReaderReturned) {
            setMessage(`原生阅读结果保存失败：${detail}`);
            return;
          }
          // Keep the user able to read: a bridge or device-specific failure
          // switches only this book to the proven Legacy fallback.
          saveReaderEngineVersionForBook(book.id, "legacy");
          setMessage(`原生内核打开失败，已切回 Legacy：${detail}`);
          window.setTimeout(() => openBook(book), 0);
        } finally {
          nativeReaderOpeningRef.current = false;
        }
      })();
      return;
    }
    const webEngineVersion = engineVersion === "native-legado" ? "legacy" : engineVersion;
    const cachedContent = readerContentCacheRef.current.get(book.id)?.trim() ? readerContentCacheRef.current.get(book.id) : undefined;
    let persistedContent: string | undefined;
    try {
      const saved = localStorage.getItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${book.id}`);
      persistedContent = saved?.trim() ? saved : undefined;
    } catch {
      // 存储空间不足或 WebView 禁止访问 localStorage 时继续走文件读取。
    }
    // readerPreview 只用于书架摘要，不能先塞进阅读器。预览通常只包含开头，
    // 重新进入书籍时会先闪出错误页，再被完整正文和恢复位置替换。
    const immediateContent = cachedContent ?? persistedContent;
    pushMobileReaderHistory(book.id);
    setReaderBook(book);
    setReaderState({
      phase: immediateContent ? "ready" : "opening",
      engineVersion: webEngineVersion,
      bookId: book.id,
      title: book.title,
      visibleContent: immediateContent ?? "",
      fullContent: immediateContent
    });
    if (!isBookDownloaded(book)) {
      setReaderState({
        phase: "error",
        engineVersion: webEngineVersion,
        bookId: book.id,
        title: book.title,
        visibleContent: "",
        errorCode: "not_downloaded",
        errorMessage: "这本书的正文尚未下载到本机。请先下载正文，或重新导入本地文件。"
      });
      return;
    }

    window.requestAnimationFrame(() => {
      void withTimeout(readMobileBookContent(book), 12_000, "正文打开超时", openAbort.signal)
        .then((content) => {
          if (!isCurrentReaderLoad()) return;
          if (!content?.trim()) {
            setReaderStateIfCurrent({
              phase: "error",
              engineVersion: webEngineVersion,
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
            engineVersion: webEngineVersion,
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
          if (openAbort.signal.aborted || (error instanceof DOMException && error.name === "AbortError")) return;
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
            engineVersion: webEngineVersion,
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

  useEffect(() => {
    if (!Capacitor.isNativePlatform() || nativeRecoveryRef.current || snapshot.books.length === 0) return;
    nativeRecoveryRef.current = true;
    void (async () => {
      let recoveredSessions = 0;
      let recoveredActions = 0;
      try {
        const checkpoints = await getPendingNativeReaderCheckpoints();
        const recoverable = checkpoints.filter((checkpoint) =>
          snapshotRef.current.books.some((book) => book.id === checkpoint.bookId)
        );
        for (const checkpoint of recoverable) await persistNativeReaderCheckpoint(checkpoint);
        recoveredSessions = recoverable.length;

        const actions = await getPendingNativeReaderActions();
        const recoverableActions = actions.filter((action) =>
          snapshotRef.current.books.some((book) => book.id === action.bookId)
        );
        await persistNativeReaderActions(recoverableActions);
        recoveredActions = recoverableActions.length;
        if (recoveredSessions || recoveredActions) {
          setMessage(`已恢复 ${recoveredSessions} 个阅读会话、${recoveredActions} 条书签/笔记/灵感`);
        }
      } catch (error) {
        const detail = error instanceof Error ? error.message : String(error);
        setMessage(`恢复原生阅读数据失败，下次启动会重试：${detail}`);
      } finally {
        nativeRecoveryRef.current = false;
      }
    })();
  }, [snapshot.books.length]);

  useEffect(() => {
    return () => {
      readerOpenAbortRef.current?.abort();
      readerOpenAbortRef.current = undefined;
    };
  }, []);

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
