import { useCallback, useEffect, useRef, useState } from "react";
import {
  getTTSController,
  isLocalTTSAvailable,
  loadTTSSettings,
  saveTTSSettings,
  type TTSProgressInfo,
  type TTSStatus,
  type TTSSettings
} from "../services/mobile-tts";

export interface UseTTSReturn {
  status: TTSStatus;
  progress: TTSProgressInfo | null;
  settings: TTSSettings;
  isAvailable: boolean;
  error: string | null;
  play: (text: string, bookId: string, startCharOffset?: number) => void;
  resume: () => void;
  pause: () => void;
  stop: () => void;
  next: () => void;
  prev: () => void;
  jumpTo: (chunkIndex: number) => void;
  startFromCharOffset: (charOffset: number) => void;
  updateSettings: (settings: TTSSettings) => void;
  setAutoStop: (minutes: number) => void;
  setMediaMetadata: (metadata: { title: string; artist: string; artwork?: string }) => void;
}

export function useTTS(): UseTTSReturn {
  const controller = getTTSController();
  const [status, setStatus] = useState<TTSStatus>(controller.getStatus());
  const [progress, setProgress] = useState<TTSProgressInfo | null>(null);
  const [settings, setSettings] = useState<TTSSettings>(() => loadTTSSettings());
  const [error, setError] = useState<string | null>(null);
  const errorTimerRef = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    const unsubStatus = controller.onStatusChange(setStatus);
    const unsubProgress = controller.onProgress(setProgress);
    const unsubError = controller.onError((msg) => {
      setError(msg);
      if (errorTimerRef.current) clearTimeout(errorTimerRef.current);
      errorTimerRef.current = setTimeout(() => setError(null), 4000);
    });
    return () => {
      unsubStatus();
      unsubProgress();
      unsubError();
      if (errorTimerRef.current) clearTimeout(errorTimerRef.current);
    };
  }, [controller]);

  const play = useCallback((text: string, bookId: string, startCharOffset?: number) => {
    controller.loadText(text, bookId, startCharOffset ?? 0);
    void controller.play();
  }, [controller]);

  const resume = useCallback(() => {
    void controller.play();
  }, [controller]);

  const pause = useCallback(() => {
    controller.pause();
  }, [controller]);

  const stop = useCallback(() => {
    controller.stop();
    setProgress(null);
  }, [controller]);

  const next = useCallback(() => {
    controller.next();
  }, [controller]);

  const prev = useCallback(() => {
    controller.prev();
  }, [controller]);

  const jumpTo = useCallback((chunkIndex: number) => {
    controller.startFromChunk(chunkIndex);
  }, [controller]);

  const startFromCharOffset = useCallback((charOffset: number) => {
    controller.startFromCharOffset(charOffset);
  }, [controller]);

  const updateSettings = useCallback((next: TTSSettings) => {
    saveTTSSettings(next);
    setSettings(next);
    controller.updateSettings(next);
  }, [controller]);

  const setAutoStop = useCallback((minutes: number) => {
    controller.setAutoStop(minutes);
  }, [controller]);

  const setMediaMetadata = useCallback((metadata: { title: string; artist: string; artwork?: string }) => {
    controller.setMediaMetadata(metadata);
  }, [controller]);

  return {
    status,
    progress,
    settings,
    isAvailable: isLocalTTSAvailable(),
    error,
    play,
    resume,
    pause,
    stop,
    next,
    prev,
    jumpTo,
    startFromCharOffset,
    updateSettings,
    setAutoStop,
    setMediaMetadata
  };
}
