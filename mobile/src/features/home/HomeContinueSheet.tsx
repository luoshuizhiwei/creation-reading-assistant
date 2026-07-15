import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, BookOpen, BookmarkX, Check, ChevronRight, MoreHorizontal, Trash2 } from "lucide-react";
import { createPortal } from "react-dom";
import type { MobileBook } from "../../types/mobile";
import type { MobileSnapshot } from "../../services/mobile-storage";
import { deleteMobileBook, saveMobileReadingProgress } from "../../services/mobile-storage";
import { clearContinueRemoval, getAllContinueBooks, lastReadAtFor, removeBookFromContinue } from "../shelf/book-progress";
import { formatBookProgress, getBookReadiness } from "../shelf/book-status";

type ContinueSortKey = "recent" | "progress" | "created" | "title";
type MenuView = "none" | "more" | "sort";

interface HomeContinueSheetProps {
  snapshot: MobileSnapshot;
  onClose: () => void;
  onOpenBook: (book: MobileBook) => void;
  onShowDetail: (bookId: string) => void;
  onGoShelf: () => void;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void }) => void;
}

interface ContinueBookItem {
  book: MobileBook;
  progress: number;
  lastReadAt: string;
}

const SORT_LABELS: Record<ContinueSortKey, string> = {
  recent: "最近阅读",
  progress: "阅读进度",
  created: "加入书库时间",
  title: "书名"
};

export function HomeContinueSheet({
  snapshot,
  onClose,
  onOpenBook,
  onShowDetail,
  onGoShelf,
  onSnapshotChange,
  onConfirm
}: HomeContinueSheetProps) {
  const [sortKey, setSortKey] = useState<ContinueSortKey>("recent");
  const [sortAsc, setSortAsc] = useState(false);
  const [actionBookId, setActionBookId] = useState<string>("");
  const [removalVersion, setRemovalVersion] = useState(0);
  const [menuView, setMenuView] = useState<MenuView>("none");
  const [manageMode, setManageMode] = useState(false);

  const menuRef = useRef<HTMLDivElement>(null);
  const moreButtonRef = useRef<HTMLButtonElement>(null);

  const items = useMemo<ContinueBookItem[]>(() => {
    const list = getAllContinueBooks(snapshot).map((book, originalIndex) => {
      const progress = snapshot.progress.find((p) => p.bookId === book.id);
      return {
        book,
        progress: progress?.progressPercent ?? 0,
        lastReadAt: lastReadAtFor(snapshot, book.id) ?? "",
        originalIndex
      };
    });

    list.sort((a, b) => {
      const missingValue = (value: string | number) => value === "" || !Number.isFinite(typeof value === "number" ? value : 0);
      let aValue: string | number;
      let bValue: string | number;
      let compare = 0;
      switch (sortKey) {
        case "recent":
          aValue = a.lastReadAt;
          bValue = b.lastReadAt;
          break;
        case "progress":
          aValue = a.progress;
          bValue = b.progress;
          break;
        case "created":
          aValue = a.book.importedAt;
          bValue = b.book.importedAt;
          break;
        case "title":
          aValue = a.book.title;
          bValue = b.book.title;
          break;
      }
      const aMissing = missingValue(aValue);
      const bMissing = missingValue(bValue);
      if (aMissing !== bMissing) return aMissing ? 1 : -1;
      if (typeof aValue === "number" && typeof bValue === "number") compare = aValue - bValue;
      else compare = String(aValue).localeCompare(String(bValue), "zh-CN");
      if (compare !== 0) return sortAsc ? compare : -compare;
      return a.originalIndex - b.originalIndex;
    });

    return list;
  }, [snapshot.books, snapshot.progress, snapshot.sessions, sortKey, sortAsc, removalVersion]);

  const hasReliableCreatedAt = useMemo(
    () => items.length === 0 || items.every((item) => Boolean(item.book.importedAt)),
    [items]
  );

  const actionBook = useMemo(
    () => items.find((item) => item.book.id === actionBookId)?.book,
    [items, actionBookId]
  );

  // Android 硬件返回键逐层处理
  useEffect(() => {
    const handleBack = (event: Event) => {
      if (menuView === "sort") {
        event.preventDefault();
        event.stopImmediatePropagation();
        setMenuView("more");
        return;
      }
      if (menuView === "more") {
        event.preventDefault();
        event.stopImmediatePropagation();
        setMenuView("none");
        return;
      }
      if (actionBookId) {
        event.preventDefault();
        event.stopImmediatePropagation();
        setActionBookId("");
        return;
      }
      event.preventDefault();
      event.stopImmediatePropagation();
      onClose();
    };

    window.addEventListener("mobile-tab-back", handleBack);
    return () => window.removeEventListener("mobile-tab-back", handleBack);
  }, [menuView, actionBookId, onClose]);

  // 点击菜单外部关闭菜单
  useEffect(() => {
    if (menuView === "none") return;

    const handlePointerDown = (event: PointerEvent | MouseEvent | TouchEvent) => {
      const target = event.target as Node;
      if (
        menuRef.current?.contains(target) ||
        moreButtonRef.current?.contains(target)
      ) {
        return;
      }
      setMenuView("none");
    };

    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [menuView]);

  const handleOpenBook = (book: MobileBook) => {
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
    onClose();
    onOpenBook(book);
  };

  const handleMarkRead = async (book: MobileBook) => {
    const next = await saveMobileReadingProgress(snapshot, book, 100);
    onSnapshotChange(next);
    setActionBookId("");
  };

  const handleRemoveFromContinue = (book: MobileBook) => {
    // 使用独立的本地隐藏状态，不修改阅读进度，不参与同步
    removeBookFromContinue(book.id);
    setRemovalVersion((v) => v + 1);
    setActionBookId("");
  };

  const handleMarkUnread = async (book: MobileBook) => {
    const next = await saveMobileReadingProgress(snapshot, book, 0);
    onSnapshotChange(next);
    setActionBookId("");
  };

  const handleDeleteBook = (book: MobileBook) => {
    setActionBookId("");
    onConfirm({
      title: "删除书籍",
      message: `确定从书架删除《${book.title}》吗？本地正文文件、阅读进度、书签和笔记会一并移除，此操作不可撤销。`,
      onConfirm: async () => {
        const next = await deleteMobileBook(snapshot, book.id);
        onSnapshotChange(next);
      }
    });
  };

  const handleMaskClick = () => {
    if (menuView !== "none") {
      setMenuView("none");
      return;
    }
    onClose();
  };

  const handleToggleManage = () => {
    setManageMode((prev) => !prev);
    setMenuView("none");
  };

  const sheetContent = (
    <div className="home-sheet" role="dialog" aria-modal="true">
      <div className="home-sheet-mask" onClick={handleMaskClick} />
      <aside
        className={[
          "home-bottom-sheet",
          items.length === 0 && !actionBook ? "home-bottom-sheet-empty" : ""
        ].join(" ")}
      >
        <div className="home-sheet-handle" aria-hidden="true">
          <span />
        </div>

        {actionBook ? (
          <div className="home-sheet-page">
            <header className="home-sheet-header home-sheet-header-action">
              <button
                className="home-sheet-back"
                onClick={() => setActionBookId("")}
                aria-label="返回列表"
              >
                <ArrowLeft size={20} />
              </button>
              <h3>{actionBook.title}</h3>
              <span className="home-sheet-header-spacer" />
            </header>
            <div className="home-sheet-actions">
              <button
                onClick={() => {
                  onClose();
                  onShowDetail(actionBook.id);
                }}
              >
                <BookOpen size={18} /> 查看详情
              </button>
              <button onClick={() => void handleMarkRead(actionBook)}>
                <Check size={18} /> 标记为已读完
              </button>
              <button onClick={() => void handleRemoveFromContinue(actionBook)}>
                <BookmarkX size={18} /> 从继续阅读移除
              </button>
              <button onClick={() => void handleMarkUnread(actionBook)}>
                <span className="home-sheet-action-icon">↺</span> 标记为未读
              </button>
              <button className="danger" onClick={() => handleDeleteBook(actionBook)}>
                <Trash2 size={18} /> 删除本地书籍
              </button>
            </div>
          </div>
        ) : (
          <div className="home-sheet-page">
            <header className="home-sheet-header">
              <h3>继续阅读</h3>
              <button
                ref={moreButtonRef}
                className="home-sheet-more"
                onClick={() => setMenuView((v) => (v === "more" ? "none" : "more"))}
                aria-label="更多选项"
                aria-expanded={menuView === "more"}
              >
                <MoreHorizontal size={24} />
              </button>
            </header>

            {menuView !== "none" && (
              <div ref={menuRef} className="home-sheet-menu" role="menu">
                {menuView === "more" && (
                  <>
                    <button
                      className="home-sheet-menu-row home-sheet-menu-has-child"
                      onClick={() => setMenuView("sort")}
                      role="menuitem"
                    >
                      <span>排序方式</span>
                      <ChevronRight size={16} />
                    </button>
                    <button
                      className="home-sheet-menu-row"
                      onClick={handleToggleManage}
                      role="menuitem"
                    >
                      <span>{manageMode ? "完成管理" : "管理继续阅读"}</span>
                    </button>
                  </>
                )}

                {menuView === "sort" && (
                  <>
                    <div className="home-sheet-menu-title">
                      <button
                        className="home-sheet-menu-back"
                        onClick={() => setMenuView("more")}
                        aria-label="返回"
                      >
                        <ArrowLeft size={18} />
                      </button>
                      <span>排序方式</span>
                    </div>
                    {(Object.keys(SORT_LABELS) as ContinueSortKey[])
                      .filter((key) => key !== "created" || hasReliableCreatedAt)
                      .map((key) => (
                        <button
                          key={key}
                          className={[
                            "home-sheet-menu-row",
                            sortKey === key ? "active" : ""
                          ].join(" ")}
                          onClick={() => {
                            setSortKey(key);
                            setMenuView("none");
                          }}
                          role="menuitem"
                        >
                          <span>{SORT_LABELS[key]}</span>
                          {sortKey === key && <Check size={16} />}
                        </button>
                      ))}
                    <div className="home-sheet-menu-divider" />
                    <button
                      className={["home-sheet-menu-row", sortAsc ? "active" : ""].join(" ")}
                      onClick={() => {
                        setSortAsc(true);
                        setMenuView("none");
                      }}
                      role="menuitem"
                    >
                      <span>升序</span>
                      {sortAsc && <Check size={16} />}
                    </button>
                    <button
                      className={["home-sheet-menu-row", !sortAsc ? "active" : ""].join(" ")}
                      onClick={() => {
                        setSortAsc(false);
                        setMenuView("none");
                      }}
                      role="menuitem"
                    >
                      <span>降序</span>
                      {!sortAsc && <Check size={16} />}
                    </button>
                  </>
                )}
              </div>
            )}

            <div className="home-sheet-body">
              {items.length === 0 ? (
                <div className="home-sheet-empty">
                  <span className="home-sheet-empty-icon">
                    <BookOpen size={40} />
                  </span>
                  <p className="home-sheet-empty-title">暂无可以继续阅读的书籍</p>
                  <p className="home-sheet-empty-subtitle">开始阅读后，书籍会出现在这里</p>
                  <button
                    className="home-sheet-empty-action"
                    onClick={() => {
                      onClose();
                      onGoShelf();
                    }}
                  >
                    前往书架
                  </button>
                </div>
              ) : (
                <ul className="home-continue-list" role="list">
                  {items.map((item) => {
                    const readiness = getBookReadiness(item.book);
                    const readable = readiness.tone === "ready";
                    return (
                      <li key={item.book.id} className="home-continue-list-item">
                        <button
                          className="home-continue-list-main"
                          onClick={() => {
                            if (manageMode) return;
                            handleOpenBook(item.book);
                          }}
                          disabled={!readable}
                        >
                          <div className="home-continue-list-cover">
                            {item.book.coverDataUrl ? (
                              <img src={item.book.coverDataUrl} alt={item.book.title} loading="lazy" />
                            ) : (
                              <span>{item.book.title.slice(0, 2)}</span>
                            )}
                            <em>{item.book.format.toUpperCase()}</em>
                          </div>
                          <div className="home-continue-list-meta">
                            <span className="home-continue-list-title">{item.book.title}</span>
                            <span className="home-continue-list-author">
                              {item.book.author || "作者未知"}
                            </span>
                            <span className="home-continue-list-progress">
                              {formatBookProgress(item.progress)}
                              {!readable && " · " + readiness.label}
                            </span>
                          </div>
                        </button>
                        {manageMode ? (
                          <button
                            className="home-continue-list-remove"
                            onClick={(event) => {
                              event.stopPropagation();
                              handleRemoveFromContinue(item.book);
                            }}
                            aria-label={`从继续阅读移除《${item.book.title}》`}
                          >
                            <BookmarkX size={18} />
                          </button>
                        ) : (
                          <button
                            className="home-continue-list-more"
                            onClick={(event) => {
                              event.stopPropagation();
                              setActionBookId(item.book.id);
                            }}
                            aria-label={`${item.book.title} 操作`}
                          >
                            <MoreHorizontal size={18} />
                          </button>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          </div>
        )}
      </aside>
    </div>
  );

  return createPortal(sheetContent, document.body);
}
