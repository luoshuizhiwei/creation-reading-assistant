import { useEffect, useRef } from "react";
import type { RefObject } from "react";
import { saveProgress } from "@/services/reader-service";
import { useLibraryStore } from "@/stores/library-store";
import { useAppStore } from "@/stores/app-store";
import type { ReadingLocation } from "@/types/library";

export function buildScrollLocation(scroller: HTMLDivElement | null): ReadingLocation | undefined {
  const book = useLibraryStore.getState().activeBook;
  if (!book || !scroller) return undefined;
  const maxScroll = Math.max(1, scroller.scrollHeight - scroller.clientHeight);
  const progressPercent = Math.min(1, Math.max(0, scroller.scrollTop / maxScroll));
  return {
    format: book.format,
    mode: "scroll",
    progressPercent,
    precision: "estimated",
    scroll: {
      scrollTop: scroller.scrollTop,
      scrollHeight: scroller.scrollHeight,
      containerHeight: scroller.clientHeight
    },
    sourceVersion: {
      fileSize: book.size
    },
    updatedAt: new Date().toISOString()
  };
}

export function useReaderProgress(scrollerRef: RefObject<HTMLDivElement>) {
  const activeBook = useLibraryStore((state) => state.activeBook);
  const setProgress = useLibraryStore((state) => state.setProgress);
  const setError = useAppStore((state) => state.setError);
  const lastSaveRef = useRef<number | null>(null);

  const getCurrentLocation = () => buildScrollLocation(scrollerRef.current);

  const flushProgress = async () => {
    const book = useLibraryStore.getState().activeBook;
    const location = getCurrentLocation();
    if (!book || !location) return undefined;
    try {
      const progress = await saveProgress({ bookId: book.id, location });
      setProgress(progress);
      return progress;
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
      return undefined;
    }
  };

  const scheduleSave = () => {
    const interval = useLibraryStore.getState().readerSettings?.tracking.progressSaveIntervalMs ?? 2_000;
    if (lastSaveRef.current) window.clearTimeout(lastSaveRef.current);
    lastSaveRef.current = window.setTimeout(() => void flushProgress(), interval);
  };

  useEffect(() => {
    return () => {
      if (lastSaveRef.current) window.clearTimeout(lastSaveRef.current);
      void flushProgress();
    };
  }, [activeBook?.id]);

  useEffect(() => {
    const handler = () => void flushProgress();
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, []);

  return { scheduleSave, flushProgress, getCurrentLocation };
}

