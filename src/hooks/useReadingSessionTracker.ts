import { useCallback, useEffect, useRef } from "react";
import type { RefObject } from "react";
import { endSession, startSession, updateSession } from "@/services/reader-service";
import { useLibraryStore } from "@/stores/library-store";
import { useAppStore } from "@/stores/app-store";
import type { ReadingLocation, ReadingSession } from "@/types/library";

type EndReason = "leave-reader" | "switch-book" | "window-close" | "idle-timeout";

function isWindowReadable(): boolean {
  return document.visibilityState === "visible" && document.hasFocus();
}

export function useReadingSessionTracker(_scrollerRef: RefObject<HTMLDivElement>, getCurrentLocation: () => ReadingLocation | undefined) {
  const activeBook = useLibraryStore((state) => state.activeBook);
  const settings = useLibraryStore((state) => state.readerSettings);
  const setActiveSession = useLibraryStore((state) => state.setActiveSession);
  const setActivity = useLibraryStore((state) => state.setActivity);
  const setError = useAppStore((state) => state.setError);
  const sessionRef = useRef<ReadingSession | undefined>(undefined);
  const lastTickAtRef = useRef(Date.now());
  const lastInteractionAtRef = useRef(Date.now());
  const lastPersistAtRef = useRef(Date.now());
  const idleSinceRef = useRef<number | undefined>(undefined);
  const pendingActiveMsRef = useRef(0);
  const pendingIdleMsRef = useRef(0);
  const persistInFlightRef = useRef(false);
  const endingRef = useRef(false);
  const endingPromiseRef = useRef<Promise<void> | undefined>();

  const tracking = settings?.tracking;

  const persistSession = useCallback(
    async (status: "active" | "paused", force = false, pauseReason?: "idle" | "window-blur" | "leave-reader") => {
      const session = sessionRef.current;
      if (!session || persistInFlightRef.current) return;
      const nowMs = Date.now();
      const shouldPersist = force || nowMs - lastPersistAtRef.current >= (tracking?.sessionPersistIntervalMs ?? 30_000);
      if (!shouldPersist && pendingActiveMsRef.current === 0 && pendingIdleMsRef.current === 0) return;
      if (!shouldPersist) return;
      persistInFlightRef.current = true;
      const activeDeltaMs = pendingActiveMsRef.current;
      const idleDeltaMs = pendingIdleMsRef.current;
      pendingActiveMsRef.current = 0;
      pendingIdleMsRef.current = 0;
      try {
        const updated = await updateSession({
          sessionId: session.id,
          status,
          pauseReason,
          activeDeltaMs,
          idleDeltaMs,
          location: getCurrentLocation()
        });
        sessionRef.current = updated;
        setActiveSession(updated);
        lastPersistAtRef.current = nowMs;
        setActivity({
          isReaderPageActive: true,
          isWindowFocused: isWindowReadable(),
          isUserActive: status === "active",
          idleSince: idleSinceRef.current,
          lastPersistAt: nowMs,
          lastLocation: getCurrentLocation()
        });
      } catch (error) {
        pendingActiveMsRef.current += activeDeltaMs;
        pendingIdleMsRef.current += idleDeltaMs;
        setError(error instanceof Error ? error.message : String(error));
      } finally {
        persistInFlightRef.current = false;
      }
    },
    [getCurrentLocation, setActiveSession, setActivity, setError, tracking?.sessionPersistIntervalMs]
  );

  const endTracking = useCallback(
    async (endReason: EndReason = "leave-reader") => {
      if (endingPromiseRef.current) {
        await endingPromiseRef.current;
        return;
      }
      const session = sessionRef.current;
      if (!session || endingRef.current) return;
      endingRef.current = true;
      const task = (async () => {
        try {
          const ended = await endSession({
            sessionId: session.id,
            endReason,
            activeDeltaMs: pendingActiveMsRef.current,
            idleDeltaMs: pendingIdleMsRef.current,
            location: getCurrentLocation()
          });
          pendingActiveMsRef.current = 0;
          pendingIdleMsRef.current = 0;
          sessionRef.current = undefined;
          setActiveSession(ended);
          setActivity({ isTracking: false, activeSessionId: undefined, isUserActive: false });
        } catch (error) {
          setError(error instanceof Error ? error.message : String(error));
        } finally {
          endingRef.current = false;
          endingPromiseRef.current = undefined;
        }
      })();
      endingPromiseRef.current = task;
      await task;
    },
    [getCurrentLocation, setActiveSession, setActivity, setError]
  );

  const beginTracking = useCallback(
    async (source: "manualOpen" | "restore" | "switchBook" = "manualOpen") => {
      const book = useLibraryStore.getState().activeBook;
      const currentSettings = useLibraryStore.getState().readerSettings;
      if (!book || !currentSettings?.tracking.trackReadingSessions) return;
      if (sessionRef.current || endingPromiseRef.current) {
        await endTracking("switch-book");
      }
      if (sessionRef.current) return;
      const location = getCurrentLocation();
      if (!location) return;
      try {
        const session = await startSession({ bookId: book.id, location, source });
        const nowMs = Date.now();
        sessionRef.current = session;
        lastTickAtRef.current = nowMs;
        lastInteractionAtRef.current = nowMs;
        lastPersistAtRef.current = nowMs;
        idleSinceRef.current = undefined;
        pendingActiveMsRef.current = 0;
        pendingIdleMsRef.current = 0;
        setActiveSession(session);
        setActivity({
          isReaderPageActive: true,
          isWindowFocused: isWindowReadable(),
          isUserActive: true,
          lastInteractionAt: nowMs,
          idleSince: undefined,
          lastTickAt: nowMs,
          lastPersistAt: nowMs,
          lastLocation: location
        });
      } catch (error) {
        setError(error instanceof Error ? error.message : String(error));
      }
    },
    [endTracking, getCurrentLocation, setActiveSession, setActivity, setError]
  );

  const recordInteraction = useCallback(() => {
    const nowMs = Date.now();
    lastInteractionAtRef.current = nowMs;
    if (idleSinceRef.current && sessionRef.current) {
      const maxPaused = tracking?.maxPausedBeforeNewSessionMs ?? 1_800_000;
      const idleAge = nowMs - idleSinceRef.current;
      idleSinceRef.current = undefined;
      if (idleAge > maxPaused) {
        void endTracking("idle-timeout").then(() => beginTracking("restore"));
      }
    } else if (!sessionRef.current) {
      void beginTracking("manualOpen");
    }
    setActivity({
      isReaderPageActive: true,
      isWindowFocused: isWindowReadable(),
      isUserActive: true,
      lastInteractionAt: nowMs,
      idleSince: undefined,
      lastLocation: getCurrentLocation()
    });
  }, [beginTracking, endTracking, getCurrentLocation, setActivity, tracking?.maxPausedBeforeNewSessionMs]);

  useEffect(() => {
    if (!activeBook || !settings?.tracking.trackReadingSessions) return;
    const timer = window.setTimeout(() => void beginTracking("manualOpen"), 120);
    return () => window.clearTimeout(timer);
  }, [activeBook?.id, beginTracking, settings?.tracking.trackReadingSessions]);

  useEffect(() => {
    if (!activeBook || !settings?.tracking.trackReadingSessions) return;
    const heartbeatMs = settings.tracking.sessionHeartbeatMs;
    const idleTimeoutMs = settings.tracking.idleTimeoutMs;
    const timer = window.setInterval(() => {
      const session = sessionRef.current;
      if (!session) return;
      const nowMs = Date.now();
      const tickDeltaMs = Math.max(0, Math.min(heartbeatMs * 2, nowMs - lastTickAtRef.current));
      const focused = isWindowReadable();
      const idleAt = lastInteractionAtRef.current + idleTimeoutMs;
      if (!focused) {
        pendingIdleMsRef.current += tickDeltaMs;
        idleSinceRef.current = idleSinceRef.current ?? nowMs;
        void persistSession("paused", true, "window-blur");
      } else if (nowMs > idleAt) {
        const activeUntilIdle = Math.max(0, Math.min(nowMs, idleAt) - lastTickAtRef.current);
        const idleDeltaMs = Math.max(0, tickDeltaMs - activeUntilIdle);
        pendingActiveMsRef.current += activeUntilIdle;
        pendingIdleMsRef.current += idleDeltaMs;
        idleSinceRef.current = idleSinceRef.current ?? idleAt;
        void persistSession("paused", true, "idle");
      } else {
        pendingActiveMsRef.current += tickDeltaMs;
        idleSinceRef.current = undefined;
        void persistSession("active");
      }
      lastTickAtRef.current = nowMs;
      setActivity({
        isReaderPageActive: true,
        isWindowFocused: focused,
        isUserActive: focused && !idleSinceRef.current,
        idleSince: idleSinceRef.current,
        lastTickAt: nowMs,
        lastLocation: getCurrentLocation()
      });
    }, heartbeatMs);
    return () => window.clearInterval(timer);
  }, [
    activeBook?.id,
    getCurrentLocation,
    persistSession,
    setActivity,
    settings?.tracking.idleTimeoutMs,
    settings?.tracking.sessionHeartbeatMs,
    settings?.tracking.trackReadingSessions
  ]);

  useEffect(() => {
    const pauseForBlur = () => {
      idleSinceRef.current = idleSinceRef.current ?? Date.now();
      void persistSession("paused", true, "window-blur");
    };
    const handleBeforeUnload = () => {
      // Snapshot critical session data synchronously so it survives window close.
      // The async endTracking may not complete before the renderer is destroyed;
      // recoverActiveSession() will reconcile any stale session on next launch.
      const session = sessionRef.current;
      if (session) {
        try {
          localStorage.setItem(
            "pending-session-end",
            JSON.stringify({
              sessionId: session.id,
              endReason: "window-close",
              activeDeltaMs: pendingActiveMsRef.current,
              idleDeltaMs: pendingIdleMsRef.current,
              location: getCurrentLocation(),
              savedAt: Date.now()
            })
          );
        } catch {
          // localStorage may be unavailable during unload; ignore.
        }
      }
      void endTracking("window-close");
    };
    window.addEventListener("blur", pauseForBlur);
    document.addEventListener("visibilitychange", pauseForBlur);
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => {
      window.removeEventListener("blur", pauseForBlur);
      document.removeEventListener("visibilitychange", pauseForBlur);
      window.removeEventListener("beforeunload", handleBeforeUnload);
    };
  }, [endTracking, persistSession]);

  const endTrackingRef = useRef(endTracking);
  endTrackingRef.current = endTracking;
  const endSessionOnSwitchRef = useRef(settings?.tracking.endSessionOnBookSwitch);
  endSessionOnSwitchRef.current = settings?.tracking.endSessionOnBookSwitch;

  useEffect(() => {
    return () => {
      // Fire-and-forget: never block navigation/unmount on session I/O.
      void endTrackingRef.current(
        endSessionOnSwitchRef.current ? "switch-book" : "leave-reader"
      );
    };
  }, [activeBook?.id]);

  useEffect(() => {
    if (settings && !settings.tracking.trackReadingSessions) void endTracking("leave-reader");
  }, [endTracking, settings?.tracking.trackReadingSessions]);

  return { recordInteraction, endTracking };
}
