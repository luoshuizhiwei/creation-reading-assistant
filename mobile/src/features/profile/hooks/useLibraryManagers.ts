import { useState } from "react";
import {
  getMobileDeviceId,
  saveMobileSnapshot,
  type MobileSnapshot
} from "../../../services/mobile-storage";
import type { MobileBook } from "../../../types/mobile";

type ConfirmDialog = { title: string; message: string; onConfirm: () => void } | null;

export function useLibraryManagers({
  snapshot,
  onSnapshotChange,
  onMessage,
  onConfirm
}: {
  snapshot: MobileSnapshot;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (value: string) => void;
  onConfirm: (dialog: ConfirmDialog) => void;
}) {
  const [newTagName, setNewTagName] = useState("");
  const [newTagType, setNewTagType] = useState<"book" | "inspiration" | "note">("book");
  const [newCategoryName, setNewCategoryName] = useState("");
  const [newShelfName, setNewShelfName] = useState("");
  const [editingManagerItem, setEditingManagerItem] = useState<{ kind: "tag" | "category" | "shelf"; id: string; name: string } | null>(null);
  const [editingManagerName, setEditingManagerName] = useState("");

  const saveSnapshotAndNotify = async (next: MobileSnapshot, message: string) => {
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(message);
  };

  const addTag = async () => {
    const name = newTagName.trim();
    if (!name) return;
    const timestamp = new Date().toISOString();
    const next = {
      ...snapshot,
      tags: [
        {
          id: `mobile-tag-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
          name,
          type: newTagType,
          createdAt: timestamp,
          updatedAt: timestamp,
          revision: 1,
          deviceId: getMobileDeviceId()
        },
        ...snapshot.tags.filter((item) => !(item.name === name && item.type === newTagType))
      ],
      updatedAt: timestamp
    };
    setNewTagName("");
    await saveSnapshotAndNotify(next, `已添加标签「${name}」。`);
  };

  const addCategory = async () => {
    const name = newCategoryName.trim();
    if (!name) return;
    const timestamp = new Date().toISOString();
    const next = {
      ...snapshot,
      categories: [
        {
          id: `mobile-category-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
          name,
          sortOrder: snapshot.categories.length + 1,
          createdAt: timestamp,
          updatedAt: timestamp,
          revision: 1,
          deviceId: getMobileDeviceId()
        },
        ...snapshot.categories.filter((item) => item.name !== name)
      ],
      updatedAt: timestamp
    };
    setNewCategoryName("");
    await saveSnapshotAndNotify(next, `已添加分类「${name}」。`);
  };

  const addShelf = async () => {
    const name = newShelfName.trim();
    if (!name) return;
    const timestamp = new Date().toISOString();
    const next = {
      ...snapshot,
      shelves: [
        {
          id: `mobile-shelf-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
          name,
          bookIds: [],
          createdAt: timestamp,
          updatedAt: timestamp,
          revision: 1,
          deviceId: getMobileDeviceId()
        },
        ...snapshot.shelves.filter((item) => item.name !== name)
      ],
      updatedAt: timestamp
    };
    setNewShelfName("");
    await saveSnapshotAndNotify(next, `已创建书单「${name}」。`);
  };

  const removeRecord = async (kind: "tag" | "category" | "shelf", id: string, name: string) => {
    onConfirm({
      title: `删除${kind === "tag" ? "标签" : kind === "category" ? "分类" : "书单"}`,
      message: `确定删除「${name}」吗？这不会删除书籍、灵感或笔记正文。`,
      onConfirm: async () => {
        const timestamp = new Date().toISOString();
        const targetTag = kind === "tag" ? snapshot.tags.find((item) => item.id === id) : undefined;
        const next = {
          ...snapshot,
          tags: kind === "tag" ? snapshot.tags.filter((item) => item.id !== id) : snapshot.tags,
          categories: kind === "category" ? snapshot.categories.filter((item) => item.id !== id) : snapshot.categories,
          shelves: kind === "shelf" ? snapshot.shelves.filter((item) => item.id !== id) : snapshot.shelves,
          books: kind === "tag" && targetTag?.type === "book"
            ? snapshot.books.map((book) => book.tagNames?.includes(name)
              ? {
                  ...book,
                  tagNames: book.tagNames.filter((tag) => tag !== name),
                  updatedAt: timestamp,
                  revision: (book.revision ?? 0) + 1
                }
              : book)
            : kind === "category"
              ? snapshot.books.map((book) => book.categoryIds?.includes(id)
                ? {
                    ...book,
                    categoryIds: book.categoryIds.filter((categoryId) => categoryId !== id),
                    updatedAt: timestamp,
                    revision: (book.revision ?? 0) + 1
                  }
                : book)
            : snapshot.books,
          inspirations: kind === "tag" && targetTag?.type === "inspiration"
            ? snapshot.inspirations.map((item) => item.tags.includes(name)
              ? {
                  ...item,
                  tags: item.tags.filter((tag) => tag !== name),
                  updatedAt: timestamp,
                  revision: item.revision + 1
                }
              : item)
            : snapshot.inspirations,
          updatedAt: timestamp
        };
        await saveSnapshotAndNotify(next, "已删除。");
        onConfirm(null);
      }
    });
  };

  const startRenameRecord = (kind: "tag" | "category" | "shelf", id: string, name: string) => {
    setEditingManagerItem({ kind, id, name });
    setEditingManagerName(name);
  };

  const cancelRenameRecord = () => {
    setEditingManagerItem(null);
    setEditingManagerName("");
  };

  const renameRecord = async () => {
    if (!editingManagerItem) return;
    const nextName = editingManagerName.trim();
    if (!nextName || nextName === editingManagerItem.name) {
      cancelRenameRecord();
      return;
    }
    const timestamp = new Date().toISOString();
    const { kind, id, name: oldName } = editingManagerItem;
    const targetTag = kind === "tag" ? snapshot.tags.find((item) => item.id === id) : undefined;
    const next: MobileSnapshot = {
      ...snapshot,
      tags: kind === "tag"
        ? snapshot.tags.map((item) => item.id === id ? { ...item, name: nextName, updatedAt: timestamp, revision: item.revision + 1 } : item)
        : snapshot.tags,
      categories: kind === "category"
        ? snapshot.categories.map((item) => item.id === id ? { ...item, name: nextName, updatedAt: timestamp, revision: item.revision + 1 } : item)
        : snapshot.categories,
      shelves: kind === "shelf"
        ? snapshot.shelves.map((item) => item.id === id ? { ...item, name: nextName, updatedAt: timestamp, revision: item.revision + 1 } : item)
        : snapshot.shelves,
      books: kind === "tag" && targetTag?.type === "book"
        ? snapshot.books.map((book) => book.tagNames?.includes(oldName)
          ? {
              ...book,
              tagNames: book.tagNames.map((tag) => tag === oldName ? nextName : tag),
              updatedAt: timestamp,
              revision: (book.revision ?? 0) + 1
            }
          : book)
        : snapshot.books,
      inspirations: kind === "tag" && targetTag?.type === "inspiration"
        ? snapshot.inspirations.map((item) => item.tags.includes(oldName)
          ? {
              ...item,
              tags: item.tags.map((tag) => tag === oldName ? nextName : tag),
              updatedAt: timestamp,
              revision: item.revision + 1
            }
          : item)
        : snapshot.inspirations,
      updatedAt: timestamp
    };
    cancelRenameRecord();
    await saveSnapshotAndNotify(next, `已重命名为「${nextName}」。`);
  };

  const removeBookFromShelf = async (shelfId: string, bookId: string) => {
    const timestamp = new Date().toISOString();
    const shelf = snapshot.shelves.find((item) => item.id === shelfId);
    if (!shelf) return;
    const book = snapshot.books.find((item) => item.id === bookId);
    const next: MobileSnapshot = {
      ...snapshot,
      shelves: snapshot.shelves.map((item) => item.id === shelfId
        ? {
            ...item,
            bookIds: item.bookIds.filter((id) => id !== bookId),
            updatedAt: timestamp,
            revision: item.revision + 1
          }
        : item),
      updatedAt: timestamp
    };
    await saveSnapshotAndNotify(next, `已从书单「${shelf.name}」移除${book ? `《${book.title}》` : "这本书"}。`);
  };

  return {
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
  };
}

export type LibraryManagers = ReturnType<typeof useLibraryManagers>;
export type { MobileBook };
