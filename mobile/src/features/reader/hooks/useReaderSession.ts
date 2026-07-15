import { useEffect, useRef, useState } from "react";
import { READER_IDLE_THRESHOLD_MS, READER_SESSION_TICK_MS } from "../reader-constants";

interface UseReaderSessionOptions {
  bookId: string;
}

/**
 * 阅读会话计时。
 * - readerSessionStartRef：会话开始时间戳
 * - lastReaderActivityRef：最后一次活跃时间戳（由手势/点击/滚动更新）
 * - activeReadingMs：本次阅读的活跃时长（空闲超过 45s 不计入）
 *
 * 关键约束：阅读活跃时长只在用户活跃时累加（idle < 45s），
 * 避免空闲时间被错误计入阅读时长。
 */
export function useReaderSession({ bookId }: UseReaderSessionOptions) {
  const readerSessionStartRef = useRef(Date.now());
  const lastReaderActivityRef = useRef(Date.now());
  const [activeReadingMs, setActiveReadingMs] = useState(0);

  // 活跃时长心跳：每秒检查 idle 时长，仅活跃时累加
  useEffect(() => {
    // 切换书籍时重置会话计时基准，避免旧书的最后活跃时间污染新书
    readerSessionStartRef.current = Date.now();
    lastReaderActivityRef.current = Date.now();
    setActiveReadingMs(0);

    let lastTickMs = Date.now();
    const timer = window.setInterval(() => {
      const now = Date.now();
      const tickDelta = now - lastTickMs;
      lastTickMs = now;
      const idleMs = now - lastReaderActivityRef.current;
      // 只在活跃期间累加，避免空闲时间计入阅读时长
      if (idleMs < READER_IDLE_THRESHOLD_MS) {
        setActiveReadingMs(prev => prev + tickDelta);
      }
    }, READER_SESSION_TICK_MS);
    return () => window.clearInterval(timer);
  }, [bookId]);

  return {
    readerSessionStartRef,
    lastReaderActivityRef,
    activeReadingMs,
    setActiveReadingMs
  };
}
