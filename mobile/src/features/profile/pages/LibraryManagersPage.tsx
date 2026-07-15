import { BookOpen, FolderTree, Tags, Trash2 } from "lucide-react";
import { formatCompactDateTime } from "../../../utils/format";
import { TAG_TYPE_LABELS } from "../profile-constants";
import type { useLibraryManagers } from "../hooks/useLibraryManagers";
import type { MobileBook, MobileSnapshot } from "../../../types/mobile";
import type { ProfileSubPage } from "../ProfilePage";

export function LibraryManagersPage({
  activePage,
  snapshot,
  library,
  onOpenBook,
  onSetActivePage
}: {
  activePage: ProfileSubPage;
  snapshot: MobileSnapshot;
  library: ReturnType<typeof useLibraryManagers>;
  onOpenBook: (book: MobileBook) => void;
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
}) {
  const {
    newTagName,
    setNewTagName,
    newTagType,
    setNewTagType,
    newCategoryName,
    setNewCategoryName,
    newShelfName,
    setNewShelfName,
    editingManagerItem,
    editingManagerName,
    setEditingManagerName,
    addTag,
    addCategory,
    addShelf,
    removeRecord,
    startRenameRecord,
    cancelRenameRecord,
    renameRecord,
    removeBookFromShelf
  } = library;

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

  if (activePage === "tags") {
    const usageByType = new Map<string, { name: string; type: "book" | "inspiration" | "note"; count: number; managedId?: string }>();
    const ensureUsage = (name: string, type: "book" | "inspiration" | "note") => {
      const key = `${type}:${name}`;
      const current = usageByType.get(key) ?? { name, type, count: 0 };
      usageByType.set(key, current);
      return current;
    };
    snapshot.books.forEach((book) => book.tagNames?.forEach((tag) => {
      ensureUsage(tag, "book").count += 1;
    }));
    snapshot.inspirations.forEach((item) => item.tags.forEach((tag) => {
      ensureUsage(tag, "inspiration").count += 1;
    }));
    snapshot.tags.forEach((tag) => {
      ensureUsage(tag.name, tag.type).managedId = tag.id;
    });
    const tags = [...usageByType.values()].sort((left, right) => right.count - left.count || left.name.localeCompare(right.name, "zh-Hans-CN"));
    return (
      <div className="screen-stack profile-subpage">
        {renderHeader("数据管理", "标签管理")}
        <section className="subpage-card">
          <div className="inline-form tag-inline-form">
            <input value={newTagName} onChange={(event) => setNewTagName(event.target.value)} placeholder="新标签名称" />
            <select value={newTagType} onChange={(event) => setNewTagType(event.target.value as "book" | "inspiration" | "note")} aria-label="标签类型">
              <option value="book">书籍</option>
              <option value="inspiration">灵感</option>
              <option value="note">笔记</option>
            </select>
            <button onClick={() => void addTag()}>添加</button>
          </div>
          <div className="management-list">
            {tags.length ? tags.map((tag) => {
              const managed = tag.managedId ? snapshot.tags.find((item) => item.id === tag.managedId) : undefined;
              return (
                <article key={`${tag.type}:${tag.name}`} className="management-row">
                  <span className="profile-menu-icon"><Tags size={18} /></span>
                  <div>
                    <strong>{tag.name}</strong>
                    <small>{TAG_TYPE_LABELS[tag.type]}标签 · {tag.count ? `${tag.count} 处使用` : "暂未使用"}</small>
                    {editingManagerItem?.kind === "tag" && editingManagerItem.id === managed?.id && (
                      <div className="manager-rename-row">
                        <input value={editingManagerName} onChange={(event) => setEditingManagerName(event.target.value)} aria-label="新标签名称" />
                        <button onClick={() => void renameRecord()}>保存</button>
                        <button className="secondary" onClick={cancelRenameRecord}>取消</button>
                      </div>
                    )}
                  </div>
                  {managed && (
                    <>
                      <button className="secondary mini-row-action" onClick={() => startRenameRecord("tag", managed.id, managed.name)}>重命名</button>
                      <button className="icon-danger" onClick={() => void removeRecord("tag", managed.id, managed.name)} aria-label={`删除标签 ${tag.name}`}><Trash2 size={17} /></button>
                    </>
                  )}
                </article>
              );
            }) : <p className="empty-hint">还没有标签。记录灵感或手动添加后，会在这里统一管理。</p>}
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "categories") {
    return (
      <div className="screen-stack profile-subpage">
        {renderHeader("数据管理", "分类管理")}
        <section className="subpage-card">
          <div className="inline-form">
            <input value={newCategoryName} onChange={(event) => setNewCategoryName(event.target.value)} placeholder="新分类名称" />
            <button onClick={() => void addCategory()}>添加</button>
          </div>
          <div className="management-list">
            {snapshot.categories.length ? snapshot.categories.map((item) => {
              const categoryBooks = snapshot.books.filter((book) => book.categoryIds?.includes(item.id));
              return (
                <article key={item.id} className="management-row management-row-expanded">
                  <span className="profile-menu-icon"><FolderTree size={18} /></span>
                  <div>
                    <strong>{item.name}</strong>
                    <small>{categoryBooks.length} 本书 · {formatCompactDateTime(item.updatedAt)}</small>
                    {editingManagerItem?.kind === "category" && editingManagerItem.id === item.id && (
                      <div className="manager-rename-row">
                        <input value={editingManagerName} onChange={(event) => setEditingManagerName(event.target.value)} aria-label="新分类名称" />
                        <button onClick={() => void renameRecord()}>保存</button>
                        <button className="secondary" onClick={cancelRenameRecord}>取消</button>
                      </div>
                    )}
                    {categoryBooks.length > 0 && (
                      <div className="linked-book-list">
                        {categoryBooks.slice(0, 4).map((book) => (
                          <button key={book.id} onClick={() => onOpenBook(book)}>{book.title}</button>
                        ))}
                      </div>
                    )}
                  </div>
                  <button className="secondary mini-row-action" onClick={() => startRenameRecord("category", item.id, item.name)}>重命名</button>
                  <button className="icon-danger" onClick={() => void removeRecord("category", item.id, item.name)} aria-label={`删除分类 ${item.name}`}><Trash2 size={17} /></button>
                </article>
              );
            }) : <p className="empty-hint">还没有分类。创建后可在书籍详情里归类，并在书架顶部按分类筛选。</p>}
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "shelves") {
    return (
      <div className="screen-stack profile-subpage">
        {renderHeader("数据管理", "书单管理")}
        <section className="subpage-card">
          <div className="inline-form">
            <input value={newShelfName} onChange={(event) => setNewShelfName(event.target.value)} placeholder="新书单名称" />
            <button onClick={() => void addShelf()}>创建</button>
          </div>
          <div className="management-list">
            {snapshot.shelves.length ? snapshot.shelves.map((item) => {
              const shelfBooks = item.bookIds.map((bookId) => snapshot.books.find((book) => book.id === bookId)).filter((book): book is MobileBook => Boolean(book));
              return (
                <article key={item.id} className="management-row management-row-expanded">
                  <span className="profile-menu-icon"><BookOpen size={18} /></span>
                  <div>
                    <strong>{item.name}</strong>
                    <small>{shelfBooks.length} 本书 · {formatCompactDateTime(item.updatedAt)}</small>
                    {editingManagerItem?.kind === "shelf" && editingManagerItem.id === item.id && (
                      <div className="manager-rename-row">
                        <input value={editingManagerName} onChange={(event) => setEditingManagerName(event.target.value)} aria-label="新书单名称" />
                        <button onClick={() => void renameRecord()}>保存</button>
                        <button className="secondary" onClick={cancelRenameRecord}>取消</button>
                      </div>
                    )}
                    {shelfBooks.length > 0 && (
                      <div className="linked-book-list">
                        {shelfBooks.slice(0, 4).map((book) => (
                          <span key={book.id}>
                            <button onClick={() => onOpenBook(book)}>{book.title}</button>
                            <button className="text-danger" onClick={() => void removeBookFromShelf(item.id, book.id)}>移除</button>
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                  <button className="secondary mini-row-action" onClick={() => startRenameRecord("shelf", item.id, item.name)}>重命名</button>
                  <button className="icon-danger" onClick={() => void removeRecord("shelf", item.id, item.name)} aria-label={`删除书单 ${item.name}`}><Trash2 size={17} /></button>
                </article>
              );
            }) : <p className="empty-hint">还没有书单。创建后可在书籍详情里收书，并在书架顶部按书单筛选。</p>}
          </div>
        </section>
      </div>
    );
  }

  return null;
}
