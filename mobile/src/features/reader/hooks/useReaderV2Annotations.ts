import type { Dispatch, MutableRefObject, SetStateAction } from "react";
import {
  addMobileHighlight,
  addMobileNote,
  deleteMobileHighlight,
  deleteMobileNote,
  type MobileSnapshot
} from "../../../services/mobile-storage";
import type { MobileBook } from "../../../types/mobile";
import type { ReaderLocator } from "../engine-v2/types";
import { isSameReaderPosition } from "../reader-v2-annotations";

interface UseReaderV2AnnotationsOptions {
  book: MobileBook;
  snapshotRef: MutableRefObject<MobileSnapshot>;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (message: string) => void;
  locator?: ReaderLocator;
  chapterTitle?: string;
  selectionText: string;
  selectionLocator?: ReaderLocator;
  setSelectionText: Dispatch<SetStateAction<string>>;
  lastReaderActivityRef: MutableRefObject<number>;
}

export function useReaderV2Annotations({
  book,
  snapshotRef,
  onSnapshotChange,
  onMessage,
  locator,
  chapterTitle,
  selectionText,
  selectionLocator,
  setSelectionText,
  lastReaderActivityRef
}: UseReaderV2AnnotationsOptions) {
  const activeLocator = () => selectionLocator ?? locator;
  const progressPercent = () => Math.max(0, Math.min(100, (activeLocator()?.progression ?? 0) * 100));
  const commit = (next: MobileSnapshot) => {
    snapshotRef.current = next;
    onSnapshotChange(next);
  };
  const touch = () => {
    lastReaderActivityRef.current = Date.now();
  };
  const clearSelection = () => {
    window.getSelection()?.removeAllRanges();
    setSelectionText("");
  };

  const addBookmark = async () => {
    touch();
    const current = activeLocator();
    const duplicate = snapshotRef.current.notes.some((item) =>
      item.bookId === book.id && item.kind === "bookmark" && !item.deletedAt && (
        isSameReaderPosition(item.locator, current) || (
          !item.locator && Math.abs((item.progressPercent ?? 0) - progressPercent()) < 0.15
        )
      )
    );
    if (duplicate) {
      onMessage("当前位置已经有书签。");
      return;
    }
    const next = await addMobileNote(snapshotRef.current, {
      book,
      title: `书签：${chapterTitle ?? book.title}`,
      excerpt: selectionText || undefined,
      chapterTitle,
      progressPercent: progressPercent(),
      locator: current,
      kind: "bookmark"
    });
    commit(next);
    onMessage("已在当前阅读位置添加书签。");
  };

  const addNote = async (body: string) => {
    touch();
    const text = body.trim() || selectionText.trim();
    if (!text) {
      onMessage("请先写下笔记，或选中一段正文。");
      return false;
    }
    const next = await addMobileNote(snapshotRef.current, {
      book,
      title: `笔记：${chapterTitle ?? book.title}`,
      body: text,
      excerpt: selectionText || undefined,
      chapterTitle,
      progressPercent: progressPercent(),
      locator: activeLocator(),
      kind: "note"
    });
    commit(next);
    clearSelection();
    onMessage("已保存当前阅读笔记。");
    return true;
  };

  const addHighlight = async () => {
    touch();
    const text = selectionText.trim();
    if (!text) {
      onMessage("请先选中文字再添加高亮。");
      return;
    }
    const { snapshot: next } = await addMobileHighlight(snapshotRef.current, {
      book,
      text,
      chapterTitle,
      progressPercent: progressPercent(),
      locator: activeLocator()
    });
    commit(next);
    clearSelection();
    onMessage("已保存选中文字的高亮。");
  };

  const removeNote = async (noteId: string) => {
    const next = await deleteMobileNote(snapshotRef.current, noteId);
    commit(next);
    onMessage("已删除这条书签或笔记。");
  };

  const removeHighlight = async (highlightId: string) => {
    const next = await deleteMobileHighlight(snapshotRef.current, highlightId);
    commit(next);
    onMessage("已删除这条高亮。");
  };

  return { addBookmark, addNote, addHighlight, removeNote, removeHighlight, clearSelection };
}

