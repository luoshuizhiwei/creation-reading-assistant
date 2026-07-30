import { useCallback, useEffect, useRef } from "react";
import type { RefObject } from "react";
import { saveProgress } from "@/services/reader-service";
import { useLibraryStore } from "@/stores/library-store";
import { useAppStore } from "@/stores/app-store";
import type { ReadingLocation } from "@/types/library";
import { computeScrollLocation } from "@/utils/reading-progress";

export function buildScrollLocation(scroller: HTMLDivElement | null, book = useLibraryStore.getState().activeBook): ReadingLocation | undefined {
  if (!book || !scroller) return undefined;
  return computeScrollLocation(scroller, book);
}

export function useReaderProgress(scrollerRef: RefObject<HTMLDivElement>) {
  const activeBook = useLibraryStore((state) => state.activeBook);
  const setProgress = useLibraryStore((state) => state.setProgress);
  const setError = useAppStore((state) => state.setError);
  const lastSaveRef = useRef<number | null>(null);

  const getCurrentLocation = useCallback(
    () => buildScrollLocation(scrollerRef.current),
    [scrollerRef]
  );

  const flushProgressForBook = useCallback(async (book = useLibraryStore.getState().activeBook) => {
    const location = buildScrollLocation(scrollerRef.current, book);
    if (!book || !location) return undefined;
    try {
      const progress = await saveProgress({ bookId: book.id, location });
      setProgress(progress);
      return progress;
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
      return undefined;
    }
  }, [scrollerRef, setProgress, setError]);

  const flushProgress = useCallback(async () => flushProgressForBook(useLibraryStore.getState().activeBook), [flushProgressForBook]);

  const flushProgressRef = useRef(flushProgress);
  flushProgressRef.current = flushProgress;
  const flushProgressForBookRef = useRef(flushProgressForBook);
  flushProgressForBookRef.current = flushProgressForBook;

  const scheduleSave = () => {
    const interval = useLibraryStore.getState().readerSettings?.tracking.progressSaveIntervalMs ?? 2_000;
    if (lastSaveRef.current) window.clearTimeout(lastSaveRef.current);
    lastSaveRef.current = window.setTimeout(() => void flushProgressRef.current(), interval);
  };

  useEffect(() => {
    return () => {
      if (lastSaveRef.current) window.clearTimeout(lastSaveRef.current);
      // Use the store's latest active book instead of a stale closure.
      void flushProgressRef.current();
    };
  }, [activeBook?.id]);

  useEffect(() => {
    const handler = () => void flushProgressRef.current();
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, []);

  return { scheduleSave, flushProgress, getCurrentLocation };
}
