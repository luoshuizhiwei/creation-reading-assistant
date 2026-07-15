import { MessageSquare, Trash2 } from "lucide-react";
import { formatCompactDateTime, formatDuration } from "../../../utils/format";
import {
  deleteMobileNote,
  saveMobileSnapshot,
  type MobileSnapshot
} from "../../../services/mobile-storage";
import type { MobileBook } from "../../../types/mobile";
import type { ProfileSubPage } from "../ProfilePage";

type ConfirmDialog = { title: string; message: string; onConfirm: () => void } | null;

export function ReadingAndNotesPage({
  activePage,
  snapshot,
  onSnapshotChange,
  onMessage,
  onConfirm,
  onOpenBook,
  focusedNoteId,
  onSetActivePage
}: {
  activePage: ProfileSubPage;
  snapshot: MobileSnapshot;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (value: string) => void;
  onConfirm: (dialog: ConfirmDialog) => void;
  onOpenBook: (book: MobileBook) => void;
  focusedNoteId?: string;
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
}) {
  const renderHeader = (eyebrow: string, title: string) => (
    <header className="mobile-header row-header subpage-header">
      <button className="ghost-button back-button" onClick={() => onSetActivePage(undefined)}>
        ← 返回
      </button>
      <div>
        <p className="mini-label">{eyebrow}</p>
        <h1>{title}</h1>
      </div>
    </header>
  );

  const saveSnapshotAndNotify = async (next: MobileSnapshot, message: string) => {
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(message);
  };

  const clearBookProgress = (book: MobileBook) => {
    onConfirm({
      title: "清除阅读进度",
      message: `确定清除《${book.title}》的阅读进度吗？阅读记录会保留。`,
      onConfirm: async () => {
        const timestamp = new Date().toISOString();
        const next: MobileSnapshot = {
          ...snapshot,
          progress: snapshot.progress.filter((item) => item.bookId !== book.id),
          updatedAt: timestamp
        };
        await saveSnapshotAndNotify(next, "已清除这本书的阅读进度。");
        onConfirm(null);
      }
    });
  };

  const removeNote = async (id: string) => {
    onConfirm({
      title: "删除笔记",
      message: "确定删除这条笔记/书签吗？删除后会从当前列表隐藏。",
      onConfirm: async () => {
        const next = await deleteMobileNote(snapshot, id);
        onSnapshotChange(next);
        onMessage("已删除笔记。");
        onConfirm(null);
      }
    });
  };

  if (activePage === "reading") {
    const progressItems = snapshot.progress
      .map((progress) => ({ progress, book: snapshot.books.find((book) => book.id === progress.bookId) }))
      .filter((item): item is { progress: typeof snapshot.progress[number]; book: MobileBook } => Boolean(item.book))
      .sort((left, right) => right.progress.lastReadAt.localeCompare(left.progress.lastReadAt));
    return (
      <div className="screen-stack profile-subpage">
        {renderHeader("阅读档案", "我的阅读")}
        <section className="profile-metric-strip">
          <article><strong>{snapshot.books.length}</strong><span>书架书籍</span></article>
          <article><strong>{progressItems.length}</strong><span>有进度</span></article>
          <article><strong>{formatDuration(snapshot.progress.reduce((sum, item) => sum + item.totalReadingTimeMs, 0))}</strong><span>累计时长</span></article>
        </section>
        <section className="subpage-card">
          <div className="management-list">
            {progressItems.length ? progressItems.map(({ progress, book }) => (
              <article key={book.id} className="management-row reading-row clickable-row" onClick={() => onOpenBook(book)}>
                <div className="book-cover mini-cover">{book.title.slice(0, 2)}</div>
                <div>
                  <strong>{book.title}</strong>
                  <small>{progress.progressPercent.toFixed(2)}% · {formatDuration(progress.totalReadingTimeMs)} · {formatCompactDateTime(progress.lastReadAt)}</small>
                  <div className="book-progress-line"><span style={{ width: `${Math.min(100, Math.max(0, progress.progressPercent))}%` }} /></div>
                </div>
                <button
                  className="secondary mini-row-action"
                  onClick={(event) => {
                    event.stopPropagation();
                    clearBookProgress(book);
                  }}
                >
                  清除进度
                </button>
              </article>
            )) : <p className="empty-hint">还没有阅读记录。打开一本书读一会儿，这里会自动生成档案。</p>}
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "notes") {
    const notes = snapshot.notes
      .filter((item) => !item.deletedAt)
      .sort((left, right) => {
        if (left.id === focusedNoteId) return -1;
        if (right.id === focusedNoteId) return 1;
        return right.updatedAt.localeCompare(left.updatedAt);
      });
    return (
      <div className="screen-stack profile-subpage">
        {renderHeader("阅读沉淀", "我的书评 / 笔记")}
        <section className="subpage-card">
          <div className="management-list">
            {notes.length ? notes.map((note) => {
              const book = note.bookId ? snapshot.books.find((item) => item.id === note.bookId) : undefined;
              return (
                <article key={note.id} className={`management-row note-row ${note.id === focusedNoteId ? "focused-row" : ""}`}>
                  <span className="profile-menu-icon"><MessageSquare size={18} /></span>
                  <div>
                    <strong>{note.title}</strong>
                    <small>{note.kind === "bookmark" ? "书签" : "笔记"} · {book?.title ?? "无来源书籍"} · {(note.progressPercent ?? 0).toFixed(1)}%</small>
                    <p>{note.excerpt || note.body || note.chapterTitle || "暂无正文"}</p>
                  </div>
                  {book && <button className="secondary mini-row-action" onClick={() => onOpenBook(book)}>打开书</button>}
                  <button className="icon-danger" onClick={() => void removeNote(note.id)} aria-label={`删除笔记 ${note.title}`}><Trash2 size={17} /></button>
                </article>
              );
            }) : <p className="empty-hint">还没有笔记。阅读页选中文字后，可以保存为笔记或书签。</p>}
          </div>
        </section>
      </div>
    );
  }

  return null;
}
