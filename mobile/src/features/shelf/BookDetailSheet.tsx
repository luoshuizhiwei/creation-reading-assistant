import { useMemo, useRef, useState } from "react";
import { Trash2 } from "lucide-react";
import { deleteMobileBook, saveMobileSnapshot } from "../../services/mobile-storage";
import type { MobileBook, MobileSnapshot } from "../../types/mobile";
import { formatBytes } from "../../utils/mobile-helpers";
import { formatCompactDateTime, formatDuration } from "../../utils/format";
import { formatReaderPositionLabel, progressFor, readerPositionFor } from "./book-progress";
import { getBookReadiness, isBookDownloaded } from "./book-status";

function progressFromSessionScroll(session: MobileSnapshot["sessions"][number], fallback: number): string {
  const location = session.endLocation ?? session.startLocation;
  if (location) {
    return formatReaderPositionLabel({
      chapterTitle: location.text?.headingPath?.[0],
      pageIndex: location.page?.pageIndex,
      pageCount: location.page?.pageCount,
      progressPercent: location.progressPercent ?? fallback
    });
  }
  return formatReaderPositionLabel({ progressPercent: fallback });
}

/**
 * 根据书名生成纯色文字封面（SVG DataURL）。
 * - 取书名前 4 个字符，使用稳定的色相
 * - 输出为 SVG，避免 Canvas 在 Android WebView 中的兼容问题
 */
function generateTextCoverDataUrl(title: string): string {
  const safeTitle = (title || "未命名").trim();
  const display = safeTitle.slice(0, 4);
  const fullTitle = safeTitle.length > 4 ? safeTitle : safeTitle;
  // 基于书名产生稳定色相（0-360）
  let hash = 0;
  for (let i = 0; i < safeTitle.length; i += 1) {
    hash = (hash * 31 + safeTitle.charCodeAt(i)) & 0xffffffff;
  }
  const hue = Math.abs(hash) % 360;
  const bg1 = `hsl(${hue}, 42%, 52%)`;
  const bg2 = `hsl(${(hue + 28) % 360}, 50%, 38%)`;
  // 转义 SVG 文本中的特殊字符
  const escapeXml = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="240" height="336" viewBox="0 0 240 336">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="${bg1}"/>
      <stop offset="1" stop-color="${bg2}"/>
    </linearGradient>
  </defs>
  <rect width="240" height="336" fill="url(#g)"/>
  <text x="24" y="120" font-family="PingFang SC, Microsoft YaHei, sans-serif" font-size="56" font-weight="700" fill="rgba(255,255,255,0.96)">${escapeXml(display)}</text>
  <text x="24" y="310" font-family="PingFang SC, Microsoft YaHei, sans-serif" font-size="18" fill="rgba(255,255,255,0.78)">${escapeXml(fullTitle.length > 12 ? fullTitle.slice(0, 12) + "…" : fullTitle)}</text>
</svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

/**
 * 读取用户选择的图片文件为 DataURL，并限制最大边长以控制体积。
 * - 超过 1024px 的边会等比缩小
 * - 输出 JPEG（照片）或 PNG（带透明通道）格式
 */
function readImageAsCoverDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error ?? new Error("读取图片失败"));
    reader.onload = () => {
      const dataUrl = String(reader.result ?? "");
      const img = new Image();
      img.onerror = () => reject(new Error("无法解析图片，请选择 JPG / PNG / WebP 图片。"));
      img.onload = () => {
        const maxSize = 720;
        let { width, height } = img;
        if (width > maxSize || height > maxSize) {
          const ratio = Math.min(maxSize / width, maxSize / height);
          width = Math.round(width * ratio);
          height = Math.round(height * ratio);
        }
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d");
        if (!ctx) {
          resolve(dataUrl);
          return;
        }
        ctx.drawImage(img, 0, 0, width, height);
        // 统一输出 JPEG 以减小体积（封面不需要透明通道）
        try {
          resolve(canvas.toDataURL("image/jpeg", 0.85));
        } catch {
          resolve(dataUrl);
        }
      };
      img.src = dataUrl;
    };
    reader.readAsDataURL(file);
  });
}

export function BookDetailSheet({
  book,
  snapshot,
  downloadingBookId,
  onClose,
  onOpenBook,
  onDownloadBook,
  onCancelDownload,
  onSnapshotChange,
  onMessage,
  onConfirm
}: {
  book: MobileBook;
  snapshot: MobileSnapshot;
  downloadingBookId?: string;
  onClose: () => void;
  onOpenBook: (book: MobileBook) => void;
  onDownloadBook: (book: MobileBook) => void;
  onCancelDownload: () => void;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (message: string) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void }) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [editTitle, setEditTitle] = useState(book.title);
  const [editAuthor, setEditAuthor] = useState(book.author ?? "");
  const [editDescription, setEditDescription] = useState(book.description ?? "");
  const coverInputRef = useRef<HTMLInputElement>(null);

  const progress = progressFor(snapshot, book.id);
  const position = readerPositionFor(snapshot, book.id);
  const downloaded = isBookDownloaded(book);
  const readiness = getBookReadiness(book);
  const inspirationCount = snapshot.inspirations.filter((item) => item.source?.bookId === book.id).length;
  const totalReadingMs = snapshot.progress.find((item) => item.bookId === book.id)?.totalReadingTimeMs ?? 0;
  const bookSessions = snapshot.sessions
    .filter((item) => item.bookId === book.id)
    .sort((left, right) => right.startAt.localeCompare(left.startAt));
  const recentSessions = bookSessions.slice(0, 3);
  const bookNotes = snapshot.notes
    .filter((item) => item.bookId === book.id && !item.deletedAt)
    .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
  const bookmarkCount = bookNotes.filter((item) => item.kind === "bookmark").length;
  const noteCount = bookNotes.filter((item) => item.kind !== "bookmark").length;
  const recentNotes = bookNotes.slice(0, 3);
  const bookTagNames = useMemo(() => {
    const names = new Set<string>();
    snapshot.tags.filter((tag) => tag.type === "book").forEach((tag) => names.add(tag.name));
    book.tagNames?.forEach((tag) => names.add(tag));
    return [...names].sort((left, right) => left.localeCompare(right, "zh-Hans-CN"));
  }, [book.tagNames, snapshot.tags]);

  const toggleBookTag = async (tagName: string) => {
    const timestamp = new Date().toISOString();
    const currentTagNames = book.tagNames ?? [];
    const inTag = currentTagNames.includes(tagName);
    const nextBook: MobileBook = {
      ...book,
      tagNames: inTag ? currentTagNames.filter((item) => item !== tagName) : [tagName, ...currentTagNames],
      updatedAt: timestamp,
      revision: (book.revision ?? 0) + 1
    };
    const next = {
      ...snapshot,
      books: [nextBook, ...snapshot.books.filter((item) => item.id !== book.id)],
      updatedAt: timestamp
    };
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(inTag ? `已移除书籍标签「${tagName}」。` : `已给《${book.title}》添加标签「${tagName}」。`);
  };

  const toggleCategory = async (categoryId: string) => {
    const timestamp = new Date().toISOString();
    const targetCategory = snapshot.categories.find((item) => item.id === categoryId);
    if (!targetCategory) return;
    const currentCategoryIds = book.categoryIds ?? [];
    const inCategory = currentCategoryIds.includes(categoryId);
    const nextBook: MobileBook = {
      ...book,
      categoryIds: inCategory ? currentCategoryIds.filter((id) => id !== categoryId) : [categoryId, ...currentCategoryIds],
      updatedAt: timestamp,
      revision: (book.revision ?? 0) + 1
    };
    const next = {
      ...snapshot,
      books: [nextBook, ...snapshot.books.filter((item) => item.id !== book.id)],
      updatedAt: timestamp
    };
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(inCategory ? `已从分类「${targetCategory.name}」移除《${book.title}》。` : `已把《${book.title}》加入分类「${targetCategory.name}》。`);
  };

  const toggleShelf = async (shelfId: string) => {
    const timestamp = new Date().toISOString();
    const targetShelf = snapshot.shelves.find((item) => item.id === shelfId);
    if (!targetShelf) return;
    const inShelf = targetShelf.bookIds.includes(book.id);
    const nextShelf = {
      ...targetShelf,
      bookIds: inShelf ? targetShelf.bookIds.filter((id) => id !== book.id) : [book.id, ...targetShelf.bookIds],
      updatedAt: timestamp,
      revision: (targetShelf.revision ?? 0) + 1
    };
    const next = {
      ...snapshot,
      shelves: [nextShelf, ...snapshot.shelves.filter((item) => item.id !== shelfId)],
      updatedAt: timestamp
    };
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(inShelf ? `已从书单「${targetShelf.name}」移除《${book.title}》。` : `已把《${book.title}》加入书单「${targetShelf.name}》。`);
  };

  const updateBookCover = async (coverDataUrl: string | undefined) => {
    const timestamp = new Date().toISOString();
    const nextBook: MobileBook = {
      ...book,
      coverDataUrl,
      updatedAt: timestamp,
      revision: (book.revision ?? 0) + 1
    };
    const next = {
      ...snapshot,
      books: [nextBook, ...snapshot.books.filter((item) => item.id !== book.id)],
      updatedAt: timestamp
    };
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(coverDataUrl ? "已更新书籍封面。" : "已重置书籍封面。");
  };

  const handleCoverFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = ""; // 允许重复选择同一文件
    if (!file) return;
    if (!/image\/(jpeg|jpg|png|webp|gif)/i.test(file.type)) {
      onMessage("仅支持 JPG / PNG / WebP / GIF 图片。");
      return;
    }
    if (file.size > 8 * 1024 * 1024) {
      onMessage("图片过大（超过 8MB），请选择更小的图片。");
      return;
    }
    try {
      const dataUrl = await readImageAsCoverDataUrl(file);
      await updateBookCover(dataUrl);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      onMessage(`封面设置失败：${detail}`);
    }
  };

  const handleGenerateTextCover = async () => {
    await updateBookCover(generateTextCoverDataUrl(editTitle.trim() || book.title));
  };

  const handleResetCover = async () => {
    await updateBookCover(undefined);
  };

  const handleSaveEdit = async () => {
    const trimmedTitle = editTitle.trim();
    if (!trimmedTitle) {
      onMessage("书名不能为空。");
      return;
    }
    const trimmedAuthor = editAuthor.trim();
    const trimmedDescription = editDescription.trim();
    const changed =
      trimmedTitle !== book.title ||
      trimmedAuthor !== (book.author ?? "") ||
      trimmedDescription !== (book.description ?? "");
    if (!changed) {
      setEditing(false);
      return;
    }
    const timestamp = new Date().toISOString();
    const nextBook: MobileBook = {
      ...book,
      title: trimmedTitle,
      author: trimmedAuthor || undefined,
      description: trimmedDescription || undefined,
      updatedAt: timestamp,
      revision: (book.revision ?? 0) + 1
    };
    const next = {
      ...snapshot,
      books: [nextBook, ...snapshot.books.filter((item) => item.id !== book.id)],
      updatedAt: timestamp
    };
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    setEditing(false);
    onMessage("已保存书籍信息。");
  };

  const handleCancelEdit = () => {
    setEditTitle(book.title);
    setEditAuthor(book.author ?? "");
    setEditDescription(book.description ?? "");
    setEditing(false);
  };

  const importedAtLabel = book.importedAt ? formatCompactDateTime(book.importedAt) : "时间未知";
  const fileSizeLabel = formatBytes(book.size);
  const formatLabel = book.format.toUpperCase();

  return (
    <div className="screen-stack book-detail-page book-detail-sheet reading-book-profile" role="region" aria-label={`${book.title} 详情`}>
      <header className="mobile-header row-header subpage-header">
        <button className="ghost-button back-button" onClick={onClose}>← 返回</button>
        <div>
          <p className="mini-label">本地书库</p>
          <h1>书籍详情</h1>
        </div>
        {!editing && (
          <button className="ghost-button edit-toggle-btn" onClick={() => setEditing(true)} aria-label="编辑书籍信息">
            编辑
          </button>
        )}
      </header>

      {editing ? (
        <section className="book-detail-edit-form book-detail-insights">
          <div className="book-detail-section-title">
            <strong>编辑书籍信息</strong>
          </div>
          <label className="book-edit-field">
            <span>书名</span>
            <input
              type="text"
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              placeholder="请输入书名"
              maxLength={120}
              autoFocus
            />
          </label>
          <label className="book-edit-field">
            <span>作者</span>
            <input
              type="text"
              value={editAuthor}
              onChange={(e) => setEditAuthor(e.target.value)}
              placeholder="作者未知"
              maxLength={60}
            />
          </label>
          <label className="book-edit-field">
            <span>简介</span>
            <textarea
              value={editDescription}
              onChange={(e) => setEditDescription(e.target.value)}
              placeholder="为这本书写一段简介（可选）"
              maxLength={2000}
              rows={4}
            />
          </label>
          <div className="book-edit-actions">
            <button className="secondary-button" onClick={handleCancelEdit}>取消</button>
            <button onClick={handleSaveEdit}>保存</button>
          </div>
        </section>
      ) : null}

      <section className="book-detail-hero">
        {book.coverDataUrl ? (
          <div className="book-cover detail-cover book-cover-image" role="img" aria-label={`《${book.title}》封面`}>
            <img src={book.coverDataUrl} alt={book.title} />
          </div>
        ) : (
          <div className="book-cover detail-cover">
            <span>{book.title.slice(0, 4)}</span>
            <em>{formatLabel}</em>
          </div>
        )}
        <div className="book-detail-title-block">
          <p className={`book-readiness-pill ${readiness.tone}`}>{readiness.label}</p>
          <h3>{book.title}</h3>
          <p>{book.author || "作者未知"}</p>
          {book.description ? <p className="book-detail-desc">{book.description}</p> : null}
          {book.importLabel && <span className="detail-badge">{book.importLabel}</span>}
        </div>
      </section>

      {!editing && (
        <section className="book-detail-insights book-cover-section">
          <div className="book-detail-section-title">
            <strong>封面设置</strong>
          </div>
          <div className="book-cover-actions">
            <button className="secondary-button" onClick={() => coverInputRef.current?.click()}>
              本地图片
            </button>
            <button className="secondary-button" onClick={() => void handleGenerateTextCover()}>
              文字封面
            </button>
            {book.coverDataUrl ? (
              <button className="secondary-button text-danger" onClick={() => void handleResetCover()}>
                重置封面
              </button>
            ) : null}
          </div>
          <input
            ref={coverInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            onChange={(e) => void handleCoverFileChange(e)}
            style={{ display: "none" }}
          />
        </section>
      )}

      <div className="book-detail-progress">
        <div>
          <strong>{formatReaderPositionLabel(position)}</strong>
          <span>阅读进度</span>
        </div>
        <div>
          <strong>{formatDuration(totalReadingMs)}</strong>
          <span>累计阅读</span>
        </div>
        <div>
          <strong>{inspirationCount}</strong>
          <span>灵感</span>
        </div>
      </div>
      <div className="book-progress-line detail-line" aria-label={`阅读进度 ${progress.toFixed(1)}%`}>
        <span style={{ width: `${Math.min(100, Math.max(0, progress))}%` }} />
      </div>
      <div className="book-detail-actions">
        <button onClick={() => (downloaded ? onOpenBook(book) : onDownloadBook(book))}>
          {downloaded ? (progress > 0 ? "继续阅读" : "开始阅读") : "下载后阅读"}
        </button>
        <button
          className="secondary-button"
          disabled={downloaded && downloadingBookId !== book.id}
          onClick={() => {
            if (downloadingBookId === book.id) onCancelDownload();
            else onDownloadBook(book);
          }}
        >
          {downloadingBookId === book.id ? "取消下载" : downloaded ? "正文已下载" : "下载正文"}
        </button>
      </div>

      <section className="book-detail-insights book-meta-section">
        <div className="book-detail-section-title">
          <strong>文件信息</strong>
        </div>
        <dl className="book-meta-list">
          <div><dt>原始文件名</dt><dd>{book.originalFileName || book.originalFilePath || "未知"}</dd></div>
          <div><dt>导入时间</dt><dd>{importedAtLabel}</dd></div>
          <div><dt>文件大小</dt><dd>{fileSizeLabel}</dd></div>
          <div><dt>格式</dt><dd>{formatLabel}</dd></div>
        </dl>
      </section>

      <section className="book-detail-insights">
        <div className="book-detail-section-title">
          <strong>阅读记录</strong>
          <span>{formatDuration(totalReadingMs)}累计</span>
        </div>
        {recentSessions.length ? recentSessions.map((session) => (
          <article key={session.id} className="book-detail-timeline-item">
            <div>
              <strong>{formatCompactDateTime(session.startAt)}</strong>
              <span>{formatDuration(session.activeDurationMs || session.durationMs)} · {session.status === "recovered" ? "异常恢复" : "已记录"}</span>
            </div>
            <em>{progressFromSessionScroll(session, progress)}</em>
          </article>
        )) : <p className="empty-hint">还没有阅读记录。开始阅读后，这里会显示最近几次阅读。</p>}
      </section>
      <section className="book-detail-insights">
        <div className="book-detail-section-title">
          <strong>书签与笔记</strong>
          <span>{bookmarkCount} 个书签 · {noteCount} 条笔记</span>
        </div>
        {recentNotes.length ? recentNotes.map((note) => (
          <article key={note.id} className="book-detail-note-preview">
            <strong>{note.kind === "bookmark" ? "书签" : "笔记"} · {(note.progressPercent ?? progress).toFixed(1)}%</strong>
            <p>{note.excerpt || note.body || note.chapterTitle || "当前位置"}</p>
          </article>
        )) : <p className="empty-hint">阅读时点“书签”或“笔记”，这本书的沉淀会集中在这里。</p>}
      </section>
      <section className="book-detail-insights">
        <div className="book-detail-section-title">
          <strong>所在书单</strong>
          <span>{snapshot.shelves.filter((item) => item.bookIds.includes(book.id)).length} 个</span>
        </div>
        {snapshot.shelves.length ? (
          <div className="shelf-chip-list">
            {snapshot.shelves.map((shelf) => {
              const active = shelf.bookIds.includes(book.id);
              return (
                <button key={shelf.id} className={active ? "shelf-chip active" : "shelf-chip"} onClick={() => void toggleShelf(shelf.id)}>
                  {active ? "✓ " : "+ "}{shelf.name}
                </button>
              );
            })}
          </div>
        ) : <p className="empty-hint">还没有书单。可以在“我的 / 书单管理”里先创建。</p>}
      </section>
      <section className="book-detail-insights">
        <div className="book-detail-section-title">
          <strong>所属分类</strong>
          <span>{book.categoryIds?.length ?? 0} 个</span>
        </div>
        {snapshot.categories.length ? (
          <div className="shelf-chip-list">
            {snapshot.categories.map((category) => {
              const active = book.categoryIds?.includes(category.id) ?? false;
              return (
                <button key={category.id} className={active ? "shelf-chip active" : "shelf-chip"} onClick={() => void toggleCategory(category.id)}>
                  {active ? "✓ " : "+ "}{category.name}
                </button>
              );
            })}
          </div>
        ) : <p className="empty-hint">还没有分类。可以在“我的 / 分类管理”里先创建。</p>}
      </section>
      <section className="book-detail-insights">
        <div className="book-detail-section-title">
          <strong>书籍标签</strong>
          <span>{book.tagNames?.length ?? 0} 个</span>
        </div>
        {bookTagNames.length ? (
          <div className="shelf-chip-list">
            {bookTagNames.map((tagName) => {
              const active = book.tagNames?.includes(tagName) ?? false;
              return (
                <button key={tagName} className={active ? "shelf-chip active" : "shelf-chip"} onClick={() => void toggleBookTag(tagName)}>
                  {active ? "✓ " : "+ "}{tagName}
                </button>
              );
            })}
          </div>
        ) : <p className="empty-hint">还没有书籍标签。可以在“我的 / 标签管理”里创建类型为“书籍”的标签。</p>}
      </section>
      <section className="book-detail-insights">
        <button
          className="text-danger"
          style={{ width: "100%", padding: "12px", textAlign: "center", border: "1px solid var(--app-hairline)", borderRadius: "12px", background: "transparent", fontSize: "14px", cursor: "pointer" }}
          onClick={() => {
            onConfirm({
              title: "删除书籍",
              message: `确定从书架删除《${book.title}》吗？本地正文文件和阅读进度将一并移除，此操作不可撤销。`,
              onConfirm: async () => {
                const next = await deleteMobileBook(snapshot, book.id);
                onSnapshotChange(next);
                onMessage(`已删除《${book.title}》。`);
                onClose();
              }
            });
          }}
        >
          <Trash2 size={15} style={{ verticalAlign: -2, marginRight: 4 }} />删除本书
        </button>
      </section>
    </div>
  );
}
