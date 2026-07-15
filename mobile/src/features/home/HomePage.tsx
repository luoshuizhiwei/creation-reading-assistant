import { useEffect, useMemo, useRef, useState } from "react";
import { Book, ChevronRight, Clock, Search } from "lucide-react";
import type { MobileBook } from "../../types/mobile";
import type { MobileSnapshot } from "../../services/mobile-storage";
import { formatDuration } from "../../utils/format";
import { clearContinueRemoval, getContinueBooks, hasBookBeenRead, progressFor } from "../shelf/book-progress";
import { formatBookProgress, getBookReadiness, isBookReadableOnDevice } from "../shelf/book-status";
import { HomeContinueSheet } from "./HomeContinueSheet";

type HomeNavigationTarget = "shelf" | "stats" | "profile" | "inspiration";

interface HomePageProps {
  snapshot: MobileSnapshot;
  stats: { totalReadingMs: number; readingDays: number; completed: number; words: number; speed: number };
  todayReadingMs: number;
  onOpenBook: (book: MobileBook) => void;
  onShowBookDetail: (bookId: string) => void;
  onGo: (tab: HomeNavigationTarget) => void;
  onOpenGlobalSearch: () => void;
  onOpenInspiration: (inspirationId: string) => void;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void }) => void;
}

export function HomePage({
  snapshot,
  stats,
  todayReadingMs,
  onOpenBook,
  onShowBookDetail,
  onGo,
  onOpenGlobalSearch,
  onOpenInspiration,
  onSnapshotChange,
  onConfirm
}: HomePageProps) {
  const [continueSheetOpen, setContinueSheetOpen] = useState(false);
  const [continueRefreshToken, setContinueRefreshToken] = useState(0);
  const homeScrollYRef = useRef(0);
  const scrollLockedRef = useRef(false);

  // 持续追踪页面滚动位置，作为 Sheet 关闭后恢复位置的依据。
  // 比点击时读取更可靠：某些浏览器/环境在点击按钮触发 focus 后会先把页面滚回顶部。
  useEffect(() => {
    const handleScroll = () => {
      const y = window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
      if (y > 0) {
        homeScrollYRef.current = y;
      }
    };
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const captureHomeScrollY = () => {
    const y = window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
    homeScrollYRef.current = y;
  };

  const openContinueSheet = () => {
    // 兜底：若 pointerdown 未记录到有效值，再用当前值补一次
    captureHomeScrollY();
    setContinueSheetOpen(true);
  };

  // Sheet 打开时锁定背景滚动；关闭时恢复 openContinueSheet 预先保存的滚动位置。
  useEffect(() => {
    const body = document.body;
    const html = document.documentElement;
    if (!body || !html) return;
    let scrollTimer: number | undefined;
    const prevBodyOverflow = body.style.overflow;
    const prevHtmlOverflow = html.style.overflow;

    if (continueSheetOpen) {
      scrollLockedRef.current = true;
      body.style.overflow = "hidden";
      html.style.overflow = "hidden";
    } else {
      const saved = homeScrollYRef.current;
      scrollLockedRef.current = false;
      body.style.overflow = prevBodyOverflow;
      html.style.overflow = prevHtmlOverflow;
      if (saved > 0) {
        // 延迟恢复，等待浏览器重新建立滚动容器后再滚动
        scrollTimer = window.setTimeout(() => {
          window.scrollTo(0, saved);
        }, 50);
      }
    }

    return () => {
      if (scrollTimer) window.clearTimeout(scrollTimer);
      body.style.overflow = prevBodyOverflow;
      html.style.overflow = prevHtmlOverflow;
    };
  }, [continueSheetOpen]);

  const weekStart = useMemo(() => {
    const now = new Date();
    const day = now.getDay();
    const diff = now.getDate() - day + (day === 0 ? -6 : 1);
    return new Date(now.getFullYear(), now.getMonth(), diff);
  }, []);

  const readableBooks = useMemo(
    () => snapshot.books.filter(isBookReadableOnDevice),
    [snapshot.books]
  );

  const totalReadBooksCount = useMemo(() => {
    return readableBooks.filter((book) => hasBookBeenRead(snapshot, book.id)).length;
  }, [readableBooks, snapshot.progress, snapshot.sessions]);

  const booksImportedThisWeek = useMemo(() => {
    return readableBooks.filter((book) => new Date(book.importedAt) >= weekStart).length;
  }, [readableBooks, weekStart]);

  const readingBooksCount = useMemo(() => {
    return snapshot.progress.filter((p) => {
      if (p.completionState !== "reading") return false;
      const book = snapshot.books.find((b) => b.id === p.bookId);
      return book && isBookReadableOnDevice(book);
    }).length;
  }, [snapshot.progress, snapshot.books]);

  const completedBooksCount = useMemo(() => {
    return snapshot.progress.filter((p) => {
      const isCompleted = p.completionState === "completed" || p.progressPercent >= 99.5;
      if (!isCompleted) return false;
      const book = snapshot.books.find((b) => b.id === p.bookId);
      return book && isBookReadableOnDevice(book);
    }).length;
  }, [snapshot.progress, snapshot.books]);

  const continueBooks = useMemo(
    () => getContinueBooks(snapshot),
    [snapshot.books, snapshot.progress, snapshot.sessions, continueRefreshToken]
  );

  const completedBooks = useMemo(() => {
    return snapshot.books
      .filter((book) => {
        if (!isBookReadableOnDevice(book)) return false;
        const progress = snapshot.progress.find((p) => p.bookId === book.id);
        return progress && (progress.completionState === "completed" || progress.progressPercent >= 99.5);
      })
      .sort((left, right) => {
        const leftProgress = snapshot.progress.find((item) => item.bookId === left.id);
        const rightProgress = snapshot.progress.find((item) => item.bookId === right.id);
        const leftTime = leftProgress?.completedAt ?? leftProgress?.updatedAt ?? "";
        const rightTime = rightProgress?.completedAt ?? rightProgress?.updatedAt ?? "";
        return rightTime.localeCompare(leftTime);
      })
      .slice(0, 8);
  }, [snapshot.books, snapshot.progress]);

  const recentInspirations = useMemo(() => {
    return [...snapshot.inspirations]
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      .slice(0, 5);
  }, [snapshot.inspirations]);

  const handleOpenContinueBook = (book: MobileBook) => {
    clearContinueRemoval(book.id);
    const readiness = getBookReadiness(book);
    if (readiness.tone !== "ready") {
      onConfirm({
        title: "暂时无法阅读",
        message: `《${book.title}》${readiness.label}，暂时无法打开。请检查文件状态或重新导入/下载正文。`,
        onConfirm: () => {}
      });
      return;
    }
    onOpenBook(book);
  };

  return (
    <div className="screen-stack">
      <header className="mobile-header row-header home-header">
        <h1>首页</h1>
        <button className="round-action" onClick={onOpenGlobalSearch} aria-label="全局搜索">
          <Search size={22} />
        </button>
      </header>

      <section className="home-summary-row" aria-label="阅读概览">
        <button className="home-summary-card compact" onClick={() => onGo("shelf")}>
          <span><Book size={20} /></span>
          <div>
            <small>累计阅读</small>
            <strong>{totalReadBooksCount}<em className="home-summary-unit"> 本</em></strong>
          </div>
        </button>
        <button className="home-summary-card compact" onClick={() => onGo("stats")}>
          <span><Clock size={20} /></span>
          <div>
            <small>阅读时长</small>
            <strong>{formatDuration(stats.totalReadingMs)}</strong>
          </div>
        </button>
      </section>

      <section className="home-section home-continue-section">
        <div className="section-heading">
          <h2>继续阅读</h2>
          <button
            className="ghost-button home-section-more"
            onPointerDown={captureHomeScrollY}
            onClick={openContinueSheet}
            aria-label="管理继续阅读"
          >
            <ChevronRight size={20} />
          </button>
        </div>
        <div className="continue-strip-wrap home-continue-strip-wrap">
          <div className="continue-strip home-continue-strip">
            {continueBooks.length ? (
              continueBooks.map((book) => {
                const progress = progressFor(snapshot, book.id);
                return (
                  <button
                    key={book.id}
                    className="home-continue-card"
                    onClick={() => handleOpenContinueBook(book)}
                  >
                    <div className="home-continue-cover">
                      {book.coverDataUrl ? (
                        <img src={book.coverDataUrl} alt={book.title} loading="lazy" />
                      ) : (
                        <span>{book.title.slice(0, 2)}</span>
                      )}
                      <em>{book.format.toUpperCase()}</em>
                    </div>
                    <div className="home-continue-card-meta">
                      <span className="home-continue-card-title">{book.title}</span>
                      <small>{book.author || "作者未知"}</small>
                      <small className="home-continue-card-progress">{formatBookProgress(progress)}</small>
                    </div>
                    <span className="home-continue-card-arrow" aria-hidden="true">
                      <ChevronRight size={18} />
                    </span>
                  </button>
                );
              })
            ) : (
              <button className="home-continue-empty" onClick={() => onGo("shelf")}>
                书架还空着，先导入一本 TXT、Markdown 或 EPUB。
              </button>
            )}
          </div>
        </div>
      </section>

      <section className="home-section home-completed-section">
        <div className="section-heading">
          <h2>阅读统计</h2>
        </div>
        <div className="home-stats-grid">
          <article>
            <strong>{booksImportedThisWeek}</strong>
            <small>本周新增</small>
          </article>
          <article>
            <strong>{readingBooksCount}</strong>
            <small>在读</small>
          </article>
          <article>
            <strong>{completedBooksCount}</strong>
            <small>已读完</small>
          </article>
          <article>
            <strong>{formatCompactDuration(todayReadingMs)}</strong>
            <small>今日阅读</small>
          </article>
        </div>
      </section>

      <section className="section-block home-section">
        <div className="section-heading">
          <h2>最近灵感</h2>
          <button className="ghost-button" onClick={() => onGo("inspiration")}>
            全部 ›
          </button>
        </div>
        {recentInspirations.length ? (
          <div className="home-inspiration-list">
            {recentInspirations.map((item) => (
              <button
                key={item.id}
                className="home-inspiration-item"
                onClick={() => onOpenInspiration(item.id)}
              >
                <span className="home-inspiration-title">{item.title || "无标题灵感"}</span>
                <span className="home-inspiration-body">{item.body || item.source?.excerpt || ""}</span>
              </button>
            ))}
          </div>
        ) : (
          <p className="home-empty-hint">还没有灵感，阅读时选中文字即可保存为灵感。</p>
        )}
      </section>

      <section className="section-block home-section">
        <div className="section-heading">
          <h2>已阅读完成</h2>
          <button className="ghost-button" onClick={() => onGo("shelf")}>
            全部 ›
          </button>
        </div>
        {completedBooks.length ? (
          <div className="continue-strip-wrap home-completed-strip-wrap">
            <div className="continue-strip home-completed-strip">
              {completedBooks.map((book) => (
                <button
                  key={book.id}
                  className="home-completed-card"
                  onClick={() => handleOpenContinueBook(book)}
                >
                  <div className="home-continue-cover">
                    {book.coverDataUrl ? (
                      <img src={book.coverDataUrl} alt={book.title} loading="lazy" />
                    ) : (
                      <span>{book.title.slice(0, 2)}</span>
                    )}
                    <em>{book.format.toUpperCase()}</em>
                  </div>
                  <div className="home-continue-card-meta">
                    <span className="home-continue-card-title">{book.title}</span>
                    <small>{book.author || "作者未知"}</small>
                    <small className="home-continue-card-progress">已读完</small>
                  </div>
                </button>
              ))}
            </div>
          </div>
        ) : (
          <p className="home-empty-hint">还没有读完的书，继续阅读吧。</p>
        )}
      </section>

      {continueSheetOpen && (
        <HomeContinueSheet
          snapshot={snapshot}
          onClose={() => {
            setContinueSheetOpen(false);
            setContinueRefreshToken((prev) => prev + 1);
          }}
          onOpenBook={handleOpenContinueBook}
          onShowDetail={onShowBookDetail}
          onGoShelf={() => onGo("shelf")}
          onSnapshotChange={onSnapshotChange}
          onConfirm={onConfirm}
        />
      )}
    </div>
  );
}

function formatCompactDuration(ms: number): string {
  if (ms <= 0) return "0 分钟";
  if (ms < 60_000) return `${Math.round(ms / 1000)} 秒`;
  const totalMinutes = Math.round(ms / 60_000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes} 分钟`;
}
