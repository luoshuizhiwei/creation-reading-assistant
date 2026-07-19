import type { MutableRefObject } from "react";
import {
  addMobileHighlight,
  addMobileNote,
  deleteMobileHighlight,
  updateMobileHighlight,
  type MobileSnapshot
} from "../../../services/mobile-storage";
import type { HighlightColor } from "../../../../../src/types/library";
import type { MobileBook, MobileHighlight } from "../../../types/mobile";
import type { MobileReaderDocument } from "../../../reader/mobile-reader";
import type { ReaderDrawerTab } from "../reader-model";
import type { ReaderLocator } from "../engine-v2/types";

interface UseReaderAnnotationsOptions {
  book: MobileBook;
  document: MobileReaderDocument;
  snapshot: MobileSnapshot;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (message: string) => void;
  /** 共享状态 */
  selectionText: string;
  setSelectionText: React.Dispatch<React.SetStateAction<string>>;
  currentChapter: MobileReaderDocument["toc"][number] | undefined;
  currentProgress: number;
  getCurrentLocator: () => ReaderLocator | null;
  noteDraft: string;
  setNoteDraft: React.Dispatch<React.SetStateAction<string>>;
  setReaderNotice: React.Dispatch<React.SetStateAction<string>>;
  setReaderSearchQuery: React.Dispatch<React.SetStateAction<string>>;
  openReaderDrawer: (tab: ReaderDrawerTab) => void;
  /** 打开灵感记录面板，由调用方提供保存与分类能力 */
  openInspirationSheet: () => void;
  /** 来自 useReaderSession 的活跃引用（标注操作也需更新活跃时间） */
  lastReaderActivityRef: MutableRefObject<number>;
}

/**
 * 阅读器标注操作：灵感、笔记、书签、搜索选中文字、复制、清除选中。
 * 关键约束：
 * - 灵感来源区分选中文本（reader-selection）和阅读笔记（reader-note）
 * - 灵感保存后更新 lastSavedInspirationId 供 toast 跳转
 */
export function useReaderAnnotations({
  book,
  document,
  snapshot,
  onSnapshotChange,
  onMessage,
  selectionText,
  setSelectionText,
  currentChapter,
  currentProgress,
  getCurrentLocator,
  noteDraft,
  setNoteDraft,
  setReaderNotice,
  setReaderSearchQuery,
  openReaderDrawer,
  openInspirationSheet,
  lastReaderActivityRef
}: UseReaderAnnotationsOptions) {
  const addReaderInspiration = async () => {
    lastReaderActivityRef.current = Date.now();
    openInspirationSheet();
  };

  const addReaderBookmark = async () => {
    const duplicate = snapshot.notes.some((item) =>
      item.bookId === book.id &&
      item.kind === "bookmark" &&
      !item.deletedAt &&
      Math.abs((item.progressPercent ?? 0) - currentProgress) < 0.15 &&
      (item.chapterTitle ?? "") === (currentChapter?.title ?? "")
    );
    if (duplicate) {
      setReaderNotice("当前位置已经有书签。");
      onMessage("当前位置已经有书签，无需重复添加。");
      return;
    }
    const next = await addMobileNote(snapshot, {
      book,
      title: `书签：${currentChapter?.title ?? book.title}`,
      body: "",
      excerpt: selectionText || undefined,
      chapterTitle: currentChapter?.title,
      progressPercent: currentProgress,
      locator: getCurrentLocator() ?? undefined,
      kind: "bookmark"
    });
    onSnapshotChange(next);
    setReaderNotice(`已添加书签：${currentProgress.toFixed(1)}%`);
    onMessage("已在当前阅读位置添加书签。");
  };

  const addReaderHighlight = async (color: HighlightColor = "yellow") => {
    const text = selectionText.trim();
    if (!text) {
      setReaderNotice("先选中文字再添加高亮。");
      return;
    }
    const { snapshot: next, highlight } = await addMobileHighlight(snapshot, {
      book,
      text,
      color,
      chapterTitle: currentChapter?.title,
      progressPercent: currentProgress,
      locator: getCurrentLocator() ?? undefined
    });
    onSnapshotChange(next);
    setReaderNotice(`已添加${colorLabel(color)}高亮。`);
    clearSelectedText();
    return highlight;
  };

  const deleteReaderHighlight = async (highlightId: string) => {
    const next = await deleteMobileHighlight(snapshot, highlightId);
    onSnapshotChange(next);
    onMessage("已删除高亮。");
  };

  const updateReaderHighlightColor = async (highlightId: string, color: HighlightColor) => {
    const next = await updateMobileHighlight(snapshot, highlightId, { color });
    onSnapshotChange(next);
  };

  const highlightToNote = async (highlight: MobileHighlight) => {
    const next = await addMobileNote(snapshot, {
      book,
      title: `笔记：${highlight.chapterTitle ?? book.title}`,
      body: highlight.note ? `${highlight.text}\n\n${highlight.note}` : highlight.text,
      excerpt: highlight.text,
      chapterTitle: highlight.chapterTitle,
      progressPercent: highlight.progressPercent,
      locator: highlight.locator,
      kind: "note"
    });
    onSnapshotChange(next);
    setReaderNotice("已将高亮转为笔记。");
    onMessage("已将高亮转为笔记。");
  };

  const highlightToInspiration = (highlight: MobileHighlight) => {
    setSelectionText(highlight.text);
    openInspirationSheet();
  };

  const addReaderNote = async () => {
    const content = noteDraft.trim() || selectionText.trim();
    if (!content) {
      setReaderNotice("先选中文字，或在笔记抽屉里写一点内容。");
      return;
    }
    const next = await addMobileNote(snapshot, {
      book,
      title: `笔记：${currentChapter?.title ?? book.title}`,
      body: content,
      excerpt: selectionText || undefined,
      chapterTitle: currentChapter?.title,
      progressPercent: currentProgress,
      locator: getCurrentLocator() ?? undefined,
      kind: "note"
    });
    onSnapshotChange(next);
    setNoteDraft("");
    setReaderNotice("已保存阅读笔记。");
    onMessage("已保存当前书籍的阅读笔记。");
  };

  const searchSelectedText = () => {
    const keyword = selectionText.trim();
    if (!keyword) return;
    setReaderSearchQuery(keyword.slice(0, 80));
    openReaderDrawer("search");
  };

  const copySelectedText = async () => {
    const text = selectionText.trim();
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      setReaderNotice("已复制选中文字。");
      onMessage("已复制选中文字。");
    } catch {
      setReaderNotice("复制失败，请使用系统选择菜单复制。");
    }
  };

  const clearSelectedText = () => {
    window.getSelection()?.removeAllRanges();
    setSelectionText("");
  };

  const captureSelection = (_event?: React.SyntheticEvent) => {
    lastReaderActivityRef.current = Date.now();
    const text = window.getSelection()?.toString().trim() ?? "";
    setSelectionText(text.slice(0, 800));
  };

  return {
    addReaderInspiration,
    addReaderBookmark,
    addReaderNote,
    addReaderHighlight,
    deleteReaderHighlight,
    updateReaderHighlightColor,
    highlightToNote,
    highlightToInspiration,
    searchSelectedText,
    copySelectedText,
    clearSelectedText,
    captureSelection
  };
}

const HIGHLIGHT_COLOR_LABELS: Record<HighlightColor, string> = {
  yellow: "黄色",
  red: "红色",
  green: "绿色",
  blue: "蓝色",
  purple: "紫色"
};

export function colorLabel(color: HighlightColor): string {
  return HIGHLIGHT_COLOR_LABELS[color] ?? color;
}

export const HIGHLIGHT_COLOR_OPTIONS: Array<{ color: HighlightColor; label: string }> = [
  { color: "yellow", label: "黄" },
  { color: "green", label: "绿" },
  { color: "blue", label: "蓝" },
  { color: "red", label: "红" },
  { color: "purple", label: "紫" }
];
