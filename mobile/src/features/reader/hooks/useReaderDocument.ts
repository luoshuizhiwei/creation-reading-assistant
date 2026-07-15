import { useEffect, useRef, useState } from "react";
import {
  buildPlainTextFallbackDocument,
  emptyReaderDocument
} from "../reader-model";
import { renderMobileDocument, type MobileReaderDocument } from "../../../reader/mobile-reader";
import type { MobileBook, MobileReaderSettings } from "../../../types/mobile";
import type { ReadingLocation } from "../../../../../src/types/library";
import {
  READER_LOAD_TIMEOUT_MS,
  READER_LOADING_HINT_DELAY_MS,
  READER_LOADING_HINT_LONG_MS
} from "../reader-constants";
import { readerTocIndexFromCharOffset } from "../reader-navigation";

interface UseReaderDocumentOptions {
  book: MobileBook;
  content: string;
  loading?: boolean;
  readerChapterIndex: number;
  readerMode: MobileReaderSettings["readerMode"];
  /** 会话开始时的初始进度（0-100），用于首次打开时恢复到对应章节 */
  sessionStartProgress: number;
  /** 上次保存的精确阅读位置，优先于 sessionStartProgress 使用 */
  initialLocation?: ReadingLocation;
  /** 由主组件持有的章节索引 setter（与导航 hook 共享） */
  setReaderChapterIndex: React.Dispatch<React.SetStateAction<number>>;
  /** 由主组件持有的当前章节 setter（与导航 hook 共享） */
  setCurrentChapter: React.Dispatch<React.SetStateAction<MobileReaderDocument["toc"][number] | undefined>>;
}

/**
 * 阅读器正文渲染与加载状态。
 * 负责：文档解析、超时检测、loading 提示显隐。
 * 不负责：章节导航、进度保存、视口恢复。
 */
export function useReaderDocument({
  book,
  content,
  loading,
  readerChapterIndex,
  readerMode,
  sessionStartProgress,
  initialLocation,
  setReaderChapterIndex,
  setCurrentChapter
}: UseReaderDocumentOptions) {
  const [document, setDocument] = useState<MobileReaderDocument>(() => emptyReaderDocument(book.title, book.format));
  const [documentRendering, setDocumentRendering] = useState(false);
  const [documentError, setDocumentError] = useState("");
  const [readerTimeoutError, setReaderTimeoutError] = useState("");
  const [showLoadingHint, setShowLoadingHint] = useState(false);
  const [loadingHintLong, setLoadingHintLong] = useState(false);
  const initialReaderChapterAppliedRef = useRef("");

  // 文档渲染：当 content / chapter / mode 变化时重新解析
  useEffect(() => {
    let cancelled = false;
    if (!content) {
      setDocument(emptyReaderDocument(book.title, book.format));
      setDocumentError("");
      setDocumentRendering(false);
      return () => {
        cancelled = true;
      };
    }
    setDocumentRendering(true);
    setDocumentError("");
    if (book.format === "epub") {
      // EPUB 正文由 EpubReaderView/epubjs 负责真实渲染。这里不再为了拿目录预先打开一次 EPUB，
      // 避免进入阅读页时出现“结构解析一次 + iframe 渲染再打开一次”的重复成本。
      setDocument(emptyReaderDocument(book.title, book.format));
      setDocumentRendering(false);
      return () => {
        cancelled = true;
      };
    }
    void renderMobileDocument(book.format, content, book.title, { chapterIndex: readerChapterIndex, renderAllChapters: readerMode === "scroll" })
      .then((nextDocument) => {
        if (cancelled) return;
        // EPUB 由 EpubReaderView 单独渲染 iframe，parseEpubDocumentStructure 返回的 html 为空是预期行为
        if (!nextDocument.html.trim() && book.format !== "epub") {
          setDocument(buildPlainTextFallbackDocument(book, content));
          setDocumentError("正文解析结果为空，已切换为纯文本兜底阅读。");
          return;
        }
        if (initialReaderChapterAppliedRef.current !== book.id) {
          initialReaderChapterAppliedRef.current = book.id;
          // 优先按全局字符偏移定位章节，避免虚拟章节切分策略变化后位置错乱
          const charOffset = initialLocation?.text?.charOffset;
          if (typeof charOffset === "number" && charOffset > 0 && nextDocument.fullText) {
            const anchoredIndex = readerTocIndexFromCharOffset(nextDocument, charOffset);
            if (anchoredIndex !== readerChapterIndex) {
              setReaderChapterIndex(anchoredIndex);
              return;
            }
          }
          if ((nextDocument.totalChapters ?? 0) > 1 && sessionStartProgress > 0.1) {
            const total = nextDocument.totalChapters ?? nextDocument.toc.length;
            const guessedChapterIndex = Math.min(total - 1, Math.max(0, Math.floor((sessionStartProgress / 100) * total)));
            if (guessedChapterIndex !== readerChapterIndex) {
              setReaderChapterIndex(guessedChapterIndex);
              return;
            }
          }
        }
        initialReaderChapterAppliedRef.current = book.id;
        setDocument(nextDocument);
        setCurrentChapter(nextDocument.toc[nextDocument.currentTocIndex ?? readerChapterIndex] ?? nextDocument.toc[0]);
      })
      .catch((error) => {
        if (cancelled) return;
        const detail = error instanceof Error ? error.message : String(error);
        if (book.format === "epub") {
          // 跳转失败时保留原 document，不清空，避免用户从可读章节跳到失败章节后连原内容也消失
          setDocumentError(`EPUB 解析失败：${detail || "未知错误"}。可点击目录其他章节或返回书架重试。`);
          return;
        }
        setDocument(buildPlainTextFallbackDocument(book, content));
        setDocumentError(`正文排版失败，已切换为纯文本兜底：${detail || "未知错误"}`);
      })
      .finally(() => {
        if (!cancelled) setDocumentRendering(false);
      });
    return () => {
      cancelled = true;
    };
  }, [book.format, book.id, book.title, content, readerChapterIndex, readerMode, sessionStartProgress]);

  // 加载超时检测
  useEffect(() => {
    setReaderTimeoutError("");
    if (!loading && !documentRendering) return undefined;
    const timer = window.setTimeout(() => {
      setReaderTimeoutError("正文打开超时。可以返回书架重新导入，或在“我的 / 同步”里重新下载正文。");
    }, READER_LOAD_TIMEOUT_MS);
    return () => window.clearTimeout(timer);
  }, [loading, documentRendering, book.id]);

  // loading 提示延迟显示（避免本地快速加载时闪烁）
  useEffect(() => {
    if (!loading && !documentRendering) {
      setShowLoadingHint(false);
      return undefined;
    }
    setShowLoadingHint(false);
    const timer = window.setTimeout(() => {
      setShowLoadingHint(true);
    }, READER_LOADING_HINT_DELAY_MS);
    return () => {
      window.clearTimeout(timer);
      setShowLoadingHint(false);
    };
  }, [book.id, documentRendering, loading]);

  // loading 提示长文本延迟显示
  useEffect(() => {
    setLoadingHintLong(false);
    if (!loading && !documentRendering) return undefined;
    const timer = window.setTimeout(() => {
      setLoadingHintLong(true);
    }, READER_LOADING_HINT_LONG_MS);
    return () => window.clearTimeout(timer);
  }, [book.id, documentRendering, loading]);

  return {
    document,
    setDocument,
    documentRendering,
    documentError,
    readerTimeoutError,
    showLoadingHint,
    loadingHintLong
  };
}
