import { BookmarkPlus, Highlighter, NotebookPen, Sparkles, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import type { MobileSnapshot } from "../../../services/mobile-storage";
import type { MobileBook } from "../../../types/mobile";

interface ReaderV2AnnotationsPanelProps {
  book: MobileBook;
  snapshot: MobileSnapshot;
  selectionText: string;
  onAddBookmark: () => void;
  onAddHighlight: () => void;
  onAddNote: (body: string) => Promise<boolean>;
  onAddInspiration: () => void;
  onDeleteNote: (id: string) => void;
  onDeleteHighlight: (id: string) => void;
}

export function ReaderV2AnnotationsPanel({
  book,
  snapshot,
  selectionText,
  onAddBookmark,
  onAddHighlight,
  onAddNote,
  onAddInspiration,
  onDeleteNote,
  onDeleteHighlight
}: ReaderV2AnnotationsPanelProps) {
  const [noteDraft, setNoteDraft] = useState("");
  const notes = useMemo(
    () => snapshot.notes.filter((item) => item.bookId === book.id && !item.deletedAt),
    [book.id, snapshot.notes]
  );
  const highlights = useMemo(
    () => snapshot.highlights.filter((item) => item.bookId === book.id),
    [book.id, snapshot.highlights]
  );
  const inspirations = useMemo(
    () => snapshot.inspirations.filter((item) => !item.deletedAt && item.source?.bookId === book.id),
    [book.id, snapshot.inspirations]
  );

  return (
    <div className="reader-v2-annotations">
      {selectionText ? (
        <blockquote className="reader-v2-selection-preview">{selectionText}</blockquote>
      ) : (
        <p className="reader-v2-annotation-hint">选中文字后，可保存高亮、笔记或阅读灵感；未选中时仍可记录当前位置。</p>
      )}
      <div className="reader-v2-annotation-actions">
        <button onClick={onAddBookmark}><BookmarkPlus size={18} />书签</button>
        <button onClick={onAddHighlight} disabled={!selectionText}><Highlighter size={18} />高亮</button>
        <button onClick={onAddInspiration}><Sparkles size={18} />灵感</button>
      </div>
      <label className="reader-v2-note-editor">
        <span><NotebookPen size={17} />阅读笔记</span>
        <textarea
          value={noteDraft}
          onChange={(event) => setNoteDraft(event.target.value)}
          placeholder={selectionText ? "补充你对这段文字的想法（可选）" : "写下当前阅读位置的想法"}
          rows={4}
        />
        <button onClick={() => void onAddNote(noteDraft).then((saved) => saved && setNoteDraft(""))}>保存笔记</button>
      </label>

      <section className="reader-v2-annotation-list">
        <header><strong>书签与笔记</strong><span>{notes.length}</span></header>
        {notes.length ? notes.map((item) => (
          <article key={item.id}>
            <div><strong>{item.title}</strong><small>{item.chapterTitle ?? `${(item.progressPercent ?? 0).toFixed(1)}%`}</small></div>
            {item.body ? <p>{item.body}</p> : null}
            <button onClick={() => onDeleteNote(item.id)} aria-label="删除"><Trash2 size={16} /></button>
          </article>
        )) : <p className="empty-hint">还没有书签或笔记。</p>}
      </section>

      <section className="reader-v2-annotation-list">
        <header><strong>高亮</strong><span>{highlights.length}</span></header>
        {highlights.length ? highlights.map((item) => (
          <article key={item.id}>
            <div><p>{item.text}</p><small>{item.chapterTitle ?? `${(item.progressPercent ?? 0).toFixed(1)}%`}</small></div>
            <button onClick={() => onDeleteHighlight(item.id)} aria-label="删除"><Trash2 size={16} /></button>
          </article>
        )) : <p className="empty-hint">还没有高亮。</p>}
      </section>

      <p className="reader-v2-inspiration-count">本书已记录 {inspirations.length} 条灵感</p>
    </div>
  );
}

