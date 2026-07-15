import { useEffect, useRef } from "react";
import type { MutableRefObject } from "react";
import type { MouseEvent } from "react";
import type { MobileReaderSettings } from "../../../types/mobile";

interface UseReaderGesturesOptions {
  settings: MobileReaderSettings;
  onSettingsChange: (settings: MobileReaderSettings) => void;
  /** 来自 useReaderSession 的活跃引用 */
  lastReaderActivityRef: MutableRefObject<number>;
  /** 来自 useReaderNavigation 的翻页操作 */
  runBackwardAction: () => void;
  runForwardAction: () => void;
  /** 来自主组件的 UI 控制切换 */
  onToggleControls: () => void;
  /** 来自 useReaderNavigation 的滑动方向 setter */
  setSwipeDirection: React.Dispatch<React.SetStateAction<"left" | "right" | null>>;
}

/**
 * 阅读器手势处理。
 * 负责：触摸滑动翻页（仅 paged 模式）、捏合缩放字号、点击区域翻页/显隐菜单。
 * 关键约束：
 * - 上下滚动模式下不处理左右滑动，避免误触切章；只保留捏合缩放字体
 * - 五分区点击模式在中部上下区域也可翻页
 */
export function useReaderGestures({
  settings,
  onSettingsChange,
  lastReaderActivityRef,
  runBackwardAction,
  runForwardAction,
  onToggleControls,
  setSwipeDirection
}: UseReaderGesturesOptions) {
  const swipeStartRef = useRef<{ x: number; y: number; time: number } | null>(null);
  const pinchStartRef = useRef<{ distance: number; fontSize: number } | null>(null);
  const swipeTimerRef = useRef<number>();

  // 组件卸载时清理定时器
  useEffect(() => {
    return () => {
      if (swipeTimerRef.current) window.clearTimeout(swipeTimerRef.current);
    };
  }, []);

  const handleReaderTouchStart = (event: React.TouchEvent<HTMLElement>) => {
    lastReaderActivityRef.current = Date.now();
    const touches = event.touches;
    if (touches.length === 2) {
      // Pinch start
      const distance = Math.hypot(
        touches[0].clientX - touches[1].clientX,
        touches[0].clientY - touches[1].clientY
      );
      pinchStartRef.current = { distance, fontSize: settings.fontSize };
      return;
    }
    if (touches.length === 1) {
      swipeStartRef.current = { x: touches[0].clientX, y: touches[0].clientY, time: Date.now() };
    }
  };

  const handleReaderTouchMove = (event: React.TouchEvent<HTMLElement>) => {
    const touches = event.touches;
    if (touches.length === 2 && pinchStartRef.current) {
      // Pinch to zoom font size
      const distance = Math.hypot(
        touches[0].clientX - touches[1].clientX,
        touches[0].clientY - touches[1].clientY
      );
      const ratio = distance / pinchStartRef.current.distance;
      const nextFontSize = Math.round(pinchStartRef.current.fontSize * ratio);
      const clamped = Math.max(12, Math.min(36, nextFontSize));
      if (clamped !== settings.fontSize) {
        onSettingsChange({ ...settings, fontSize: clamped });
      }
    }
  };

  const handleReaderTouchEnd = (event: React.TouchEvent<HTMLElement>) => {
    pinchStartRef.current = null;
    if (!swipeStartRef.current) return;
    const touches = event.changedTouches;
    if (!touches.length) { swipeStartRef.current = null; return; }
    const dx = touches[0].clientX - swipeStartRef.current.x;
    const dy = touches[0].clientY - swipeStartRef.current.y;
    const elapsed = Date.now() - swipeStartRef.current.time;
    const absDx = Math.abs(dx);
    const absDy = Math.abs(dy);
    swipeStartRef.current = null;

    // 上下滚动模式下不处理左右滑动，避免误触切章；只保留捏合缩放字体
    if (settings.readerMode !== "paged") return;

    // 降低阈值，放宽时间窗口
    if (absDx < 30 || elapsed > 500 || absDy > absDx * 1.2) return;

    if (dx > 0) {
      // Swipe right → backward
      setSwipeDirection("right");
      runBackwardAction();
    } else {
      // Swipe left → forward
      setSwipeDirection("left");
      runForwardAction();
    }
    if (swipeTimerRef.current) window.clearTimeout(swipeTimerRef.current);
    swipeTimerRef.current = window.setTimeout(() => {
      setSwipeDirection(null);
      swipeTimerRef.current = undefined;
    }, 300);
  };

  const handleReaderTap = (event: MouseEvent<HTMLElement>) => {
    lastReaderActivityRef.current = Date.now();
    if ((window.getSelection()?.toString().trim() ?? "").length > 0) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    const ratio = x / rect.width;
    const verticalRatio = y / rect.height;
    if (settings.tapZoneMode === "five-zone" && ratio >= 0.24 && ratio <= 0.76) {
      if (verticalRatio < 0.26) {
        runBackwardAction();
        return;
      }
      if (verticalRatio > 0.74) {
        runForwardAction();
        return;
      }
    }
    if (ratio < 0.24) {
      runBackwardAction();
      return;
    }
    if (ratio > 0.76) {
      runForwardAction();
      return;
    }
    onToggleControls();
  };

  return {
    handleReaderTouchStart,
    handleReaderTouchMove,
    handleReaderTouchEnd,
    handleReaderTap
  };
}
