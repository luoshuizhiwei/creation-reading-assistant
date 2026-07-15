import { useMemo, useState } from "react";
import type { InspirationSourceSnapshot } from "../../../../../src/types/inspiration";
import { getMobileDeviceId, nowIso } from "../../../services/mobile-storage-core";
import { addMobileInspiration, type MobileSnapshot } from "../../../services/mobile-storage";
import type { MobileBook } from "../../../types/mobile";
import type { MobileReaderDocument } from "../../../reader/mobile-reader";

interface ReaderInspirationSheetProps {
  book: MobileBook;
  snapshot: MobileSnapshot;
  selectionText: string;
  currentProgress: number;
  currentChapter?: MobileReaderDocument["toc"][number];
  onSave: (nextSnapshot: MobileSnapshot, savedId: string) => void;
  onClose: () => void;
}

function buildNewInspirationTags(snapshot: MobileSnapshot, tagNames: string[]): MobileSnapshot["tags"] {
  const existing = new Set(snapshot.tags.filter((tag) => tag.type === "inspiration").map((tag) => tag.name));
  const timestamp = nowIso();
  return tagNames
    .filter((name) => !existing.has(name))
    .map((name) => ({
      id: `mobile-tag-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}-${name.slice(0, 4)}`,
      name,
      type: "inspiration" as const,
      createdAt: timestamp,
      updatedAt: timestamp,
      revision: 1,
      deviceId: getMobileDeviceId()
    }));
}

function parseTagInput(value: string): string[] {
  return value
    .split(/[，,\s]+/)
    .map((tag) => tag.trim())
    .filter(Boolean);
}

export function ReaderInspirationSheet({
  book,
  snapshot,
  selectionText,
  currentProgress,
  currentChapter,
  onSave,
  onClose
}: ReaderInspirationSheetProps) {
  const defaultTitle = `阅读灵感：${book.title}`;
  const [title, setTitle] = useState(defaultTitle);
  const [body, setBody] = useState("");
  const [selectedCategoryIds, setSelectedCategoryIds] = useState<string[]>(book.categoryIds ?? []);
  const [selectedTagNames, setSelectedTagNames] = useState<string[]>([book.title, "阅读灵感"]);
  const [newTagInput, setNewTagInput] = useState("");
  const [newCategoryInput, setNewCategoryInput] = useState("");
  const [pendingCategories, setPendingCategories] = useState<Array<{ id: string; name: string }>>([]);

  const managedInspirationTags = useMemo(
    () => snapshot.tags.filter((tag) => tag.type === "inspiration").map((tag) => tag.name),
    [snapshot.tags]
  );

  const toggleCategory = (id: string) => {
    setSelectedCategoryIds((prev) => (prev.includes(id) ? prev.filter((item) => item !== id) : [id, ...prev]));
  };

  const toggleTag = (name: string) => {
    setSelectedTagNames((prev) => (prev.includes(name) ? prev.filter((item) => item !== name) : [name, ...prev]));
  };

  const addNewTags = () => {
    const names = parseTagInput(newTagInput);
    if (!names.length) return;
    setSelectedTagNames((prev) => Array.from(new Set([...names, ...prev])));
    setNewTagInput("");
  };

  const addNewCategory = () => {
    const name = newCategoryInput.trim();
    if (!name) return;
    const id = `mobile-category-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`;
    setPendingCategories((prev) => [{ id, name }, ...prev]);
    setSelectedCategoryIds((prev) => [id, ...prev]);
    setNewCategoryInput("");
  };

  const handleSave = async () => {
    const hasContent = title.trim() || body.trim() || selectionText.trim();
    if (!hasContent) return;

    const activePendingCategories = pendingCategories.filter((item) => selectedCategoryIds.includes(item.id));
    const finalCategoryIds = Array.from(new Set([...selectedCategoryIds]));
    const finalTagNames = Array.from(new Set(selectedTagNames));

    const timestamp = nowIso();
    const newTagRecords = buildNewInspirationTags(snapshot, finalTagNames);
    const newCategoryRecords: MobileSnapshot["categories"] = activePendingCategories.map((item, index) => ({
      id: item.id,
      name: item.name,
      sortOrder: snapshot.categories.length + index + 1,
      createdAt: timestamp,
      updatedAt: timestamp,
      revision: 1,
      deviceId: getMobileDeviceId()
    }));

    let working = snapshot;
    if (newTagRecords.length) {
      working = { ...working, tags: [...newTagRecords, ...working.tags] };
    }
    if (newCategoryRecords.length) {
      working = { ...working, categories: [...newCategoryRecords, ...working.categories] };
    }

    const source: Partial<InspirationSourceSnapshot> = {
      bookId: book.id,
      bookTitle: book.title,
      bookAuthor: book.author,
      format: book.format,
      chapterTitle: currentChapter?.title,
      locationLabel: currentProgress ? `${currentProgress.toFixed(1)}%` : "当前位置附近",
      progressPercent: currentProgress,
      excerpt: selectionText || undefined,
      createdFrom: selectionText ? "reader-selection" : "reader-note"
    };

    const next = await addMobileInspiration(working, {
      title: title.trim() || defaultTitle,
      body: body.trim() || undefined,
      tags: finalTagNames,
      categoryIds: finalCategoryIds,
      source
    });
    const saved = next.inspirations[0];
    onSave(next, saved.id);
  };

  const canSave = Boolean(title.trim() || body.trim() || selectionText.trim());

  return (
    <>
      <div className="reader-sheet-mask" onClick={onClose} />
      <aside className="reader-bottom-sheet-panel reader-inspiration-sheet" role="dialog" aria-modal="true">
        <header className="reader-sheet-handle">
          <span className="reader-sheet-grabber" />
        </header>
        <h4 className="reader-sheet-title">记录灵感</h4>

        <div className="reader-inspiration-form">
          <label className="reader-inspiration-field">
            <span>标题</span>
            <input
              type="text"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="给这个点子起个标题"
            />
          </label>

          <label className="reader-inspiration-field">
            <span>我的想法</span>
            <textarea
              value={body}
              onChange={(event) => setBody(event.target.value)}
              placeholder="这个设定/冲突/人物可以用在哪里？"
              rows={3}
            />
          </label>

          {selectionText && (
            <div className="reader-inspiration-excerpt">
              <span>来源摘录</span>
              <blockquote>{selectionText}</blockquote>
            </div>
          )}

          <div className="reader-inspiration-section">
            <div className="reader-inspiration-section-header">
              <span>分类</span>
              <small>与书籍分类共用</small>
            </div>
            {snapshot.categories.length || pendingCategories.length ? (
              <div className="shelf-chip-list">
                {snapshot.categories.map((category) => {
                  const active = selectedCategoryIds.includes(category.id);
                  return (
                    <button
                      key={category.id}
                      className={active ? "shelf-chip active" : "shelf-chip"}
                      onClick={() => toggleCategory(category.id)}
                    >
                      {active ? "✓ " : "+ "}{category.name}
                    </button>
                  );
                })}
                {pendingCategories.map((category) => {
                  const active = selectedCategoryIds.includes(category.id);
                  return (
                    <button
                      key={category.id}
                      className={active ? "shelf-chip active" : "shelf-chip"}
                      onClick={() => toggleCategory(category.id)}
                    >
                      {active ? "✓ " : "+ "}{category.name}
                    </button>
                  );
                })}
              </div>
            ) : (
              <p className="empty-hint">还没有分类，可以新建一个。</p>
            )}
            <div className="reader-inspiration-inline-add">
              <input
                value={newCategoryInput}
                onChange={(event) => setNewCategoryInput(event.target.value)}
                placeholder="新建分类，如 玄幻"
                onKeyDown={(event) => {
                  if (event.key === "Enter") addNewCategory();
                }}
              />
              <button onClick={addNewCategory}>添加</button>
            </div>
          </div>

          <div className="reader-inspiration-section">
            <div className="reader-inspiration-section-header">
              <span>标签</span>
              <small>默认带上来源书名，可再补充</small>
            </div>
            <div className="shelf-chip-list">
              {managedInspirationTags.map((tagName) => {
                const active = selectedTagNames.includes(tagName);
                return (
                  <button
                    key={tagName}
                    className={active ? "shelf-chip active" : "shelf-chip"}
                    onClick={() => toggleTag(tagName)}
                  >
                    {active ? "✓ " : "+ "}{tagName}
                  </button>
                );
              })}
              {selectedTagNames
                .filter((name) => !managedInspirationTags.includes(name))
                .map((name) => (
                  <button key={name} className="shelf-chip active" onClick={() => toggleTag(name)}>
                    ✓ {name}
                  </button>
                ))}
            </div>
            <div className="reader-inspiration-inline-add">
              <input
                value={newTagInput}
                onChange={(event) => setNewTagInput(event.target.value)}
                placeholder="新标签，多个用逗号分隔"
                onKeyDown={(event) => {
                  if (event.key === "Enter") addNewTags();
                }}
              />
              <button onClick={addNewTags}>添加</button>
            </div>
          </div>

          <div className="reader-inspiration-actions">
            <button disabled={!canSave} onClick={() => void handleSave()}>
              保存灵感
            </button>
            <button className="secondary-button" onClick={onClose}>
              取消
            </button>
          </div>
        </div>
      </aside>
    </>
  );
}
