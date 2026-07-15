import { useEffect, useRef } from "react";

interface UseReaderWakeLockOptions {
  keepAwake: boolean;
  onMessage: (message: string) => void;
}

/**
 * 屏幕常亮（Wake Lock）管理。
 * 仅在 settings.keepAwake 为 true 时请求；卸载或关闭时释放当前锁。
 */
export function useReaderWakeLock({ keepAwake, onMessage }: UseReaderWakeLockOptions) {
  const wakeLockRef = useRef<{ release: () => Promise<void> } | undefined>();

  useEffect(() => {
    let cancelled = false;
    const releaseWakeLock = async () => {
      const lock = wakeLockRef.current;
      wakeLockRef.current = undefined;
      if (lock) {
        try {
          await lock.release();
        } catch {
          // Some Android WebViews reject release after the document is hidden; safe to ignore.
        }
      }
    };
    const requestWakeLock = async () => {
      await releaseWakeLock();
      if (!keepAwake) return;
      const wakeLockApi = (navigator as Navigator & { wakeLock?: { request: (type: "screen") => Promise<{ release: () => Promise<void> }> } }).wakeLock;
      if (!wakeLockApi) {
        onMessage("当前设备暂不支持屏幕常亮，阅读设置已保留。");
        return;
      }
      try {
        const lock = await wakeLockApi.request("screen");
        if (cancelled) await lock.release();
        else wakeLockRef.current = lock;
      } catch {
        onMessage("屏幕常亮开启失败，请检查系统电池或权限设置。");
      }
    };
    void requestWakeLock();
    return () => {
      cancelled = true;
      void releaseWakeLock();
    };
  }, [keepAwake, onMessage]);

  return { wakeLockRef };
}
