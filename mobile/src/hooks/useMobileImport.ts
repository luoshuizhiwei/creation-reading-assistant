import { useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction, type MutableRefObject } from "react";
import {
  canCacheReaderText,
  formatBytes,
  MAX_MOBILE_EPUB_IMPORT_BYTES,
  MAX_MOBILE_IMPORT_BYTES,
  readImportFileContent,
  waitForBrowserPaint,
  withTimeout,
  writeReaderTextCache
} from "../utils/mobile-helpers";
import {
  createImportedMobileBook,
  isSupportedMobileBookFileName,
  loadMobileSnapshot,
  repairMobileBookFromImport,
  saveMobileBook,
  type MobileSnapshot
} from "../services/mobile-storage";
import type { BookFormat } from "../../../src/types/library";
import { addMobileLog } from "../services/mobile-logger";

/** 导入任务状态 */
export type ImportTaskStatus = "pending" | "processing" | "success" | "failed";
export type ImportTaskPhase = "queued" | "validating" | "reading" | "parsing" | "committing" | "done" | "error";

/** 实时导入队列项（含 File 引用，仅当前会话有效） */
export interface ImportTask {
  id: string;
  fileName: string;
  fileSize: number;
  status: ImportTaskStatus;
  phase: ImportTaskPhase;
  /** 仅保留兼容字段；没有真实字节进度时 UI 使用不定进度，不显示虚假百分比。 */
  progress: number;
  error?: string;
  encoding?: string;
  format?: BookFormat;
  isDuplicate?: boolean;
  bookId?: string;
  bookTitle?: string;
  createdAt: string;
  /** 用于重试失败导入，不持久化 */
  fileRef?: File;
}

/** 持久化导入历史项 */
export interface ImportHistoryEntry {
  id: string;
  fileName: string;
  fileSize: number;
  format?: BookFormat;
  status: "success" | "failed";
  error?: string;
  encoding?: string;
  isDuplicate?: boolean;
  bookId?: string;
  bookTitle?: string;
  timestamp: string;
}

/** localStorage 保留的导入历史条数上限 */
const IMPORT_HISTORY_MAX = 50;
const IMPORT_HISTORY_KEY = "creation-reading-assistant-mobile-import-history";

function maxImportBytesFor(fileName: string): number {
  return /\.epub$/i.test(fileName) ? MAX_MOBILE_EPUB_IMPORT_BYTES : MAX_MOBILE_IMPORT_BYTES;
}

function loadImportHistory(): ImportHistoryEntry[] {
  try {
    const raw = localStorage.getItem(IMPORT_HISTORY_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((item): item is ImportHistoryEntry => Boolean(item && typeof item.id === "string"));
  } catch {
    return [];
  }
}

function saveImportHistory(entries: ImportHistoryEntry[]): void {
  try {
    localStorage.setItem(IMPORT_HISTORY_KEY, JSON.stringify(entries.slice(0, IMPORT_HISTORY_MAX)));
  } catch {
    // 忽略存储失败
  }
}

interface UseMobileImportOptions {
  snapshot: MobileSnapshot;
  setSnapshot: Dispatch<SetStateAction<MobileSnapshot>>;
  setMessage: (message: string) => void;
  /** 导入成功后写入阅读器正文缓存 */
  readerContentCacheRef: MutableRefObject<Map<string, string>>;
}

/**
 * 文件导入流程：校验文件名/大小 → 复制到内存 → 解析/验证 → 最终提交。
 * - 跳过非书籍文件和系统隐藏文件
 * - 超过 MAX_MOBILE_IMPORT_BYTES 的文件单独计数提示
 * - 内容哈希完全相同的书不会重复创建；同名但内容不同仍作为可区分副本导入
 * - 维护实时导入队列（importTasks）和持久化导入历史（importHistory）
 */
export function useMobileImport({ snapshot, setSnapshot, setMessage, readerContentCacheRef }: UseMobileImportOptions) {
  // 用 ref 持有最新 snapshot，避免循环导入时使用陈旧快照
  const snapshotRef = useRef(snapshot);
  const importingRef = useRef(false);
  const runningTaskIdsRef = useRef(new Set<string>());
  const mountedRef = useRef(true);
  snapshotRef.current = snapshot;

  const [importTasks, setImportTasks] = useState<ImportTask[]>([]);
  const [importHistory, setImportHistory] = useState<ImportHistoryEntry[]>(() => loadImportHistory());

  useEffect(() => () => {
    mountedRef.current = false;
  }, []);

  useEffect(() => {
    saveImportHistory(importHistory);
  }, [importHistory]);

  const updateTask = useCallback((taskId: string, patch: Partial<ImportTask>) => {
    if (!mountedRef.current) return;
    setImportTasks((prev) => prev.map((task) => (task.id === taskId ? { ...task, ...patch } : task)));
  }, []);

  const appendHistory = useCallback((entry: ImportHistoryEntry) => {
    setImportHistory((prev) => [entry, ...prev].slice(0, IMPORT_HISTORY_MAX));
  }, []);

  /** 处理单个文件的导入核心逻辑，返回导入结果 */
  const processFile = useCallback(async (taskId: string, file: File): Promise<"imported" | "duplicate"> => {
    if (runningTaskIdsRef.current.has(taskId)) throw new Error("该导入任务正在处理中，请勿重复提交。");
    runningTaskIdsRef.current.add(taskId);
    updateTask(taskId, { status: "processing", phase: "validating", progress: 0 });
    try {
      if (!isSupportedMobileBookFileName(file.name)) throw new Error("不支持的文件类型，仅支持 TXT、Markdown 和 EPUB。");
      if (file.size <= 0) throw new Error("EMPTY_FILE：文件内容为空，未导入。");
      if (file.size > maxImportBytesFor(file.name)) throw new Error(`文件过大，超过 ${formatBytes(maxImportBytesFor(file.name))} 上限。`);

      updateTask(taskId, { phase: "reading" });
      const { content, encoding } = await readImportFileContent(file);
      updateTask(taskId, { phase: file.name.toLowerCase().endsWith(".epub") ? "parsing" : "validating", encoding });
      const imported = await withTimeout(
        createImportedMobileBook(file.name, content, file.size),
        file.name.toLowerCase().endsWith(".epub") ? 20_000 : 10_000,
        file.name.toLowerCase().endsWith(".epub") ? "EPUB 导入校验超时，未创建书籍记录。" : "文件校验超时，未创建书籍记录。"
      );
      snapshotRef.current = await loadMobileSnapshot();
      const previousBookIds = new Set(snapshotRef.current.books.map((book) => book.id));
      const isDuplicateImport = snapshotRef.current.books.some((book) => book.contentHash === imported.contentHash);

      // 重复导入是书库的正式能力：每次都创建独立记录和独立文件，
      // saveMobileBook 会为后导入的副本补上“重复导入 #N”标签。
      updateTask(taskId, { phase: "committing", format: imported.format, isDuplicate: isDuplicateImport });
      const next = await saveMobileBook(snapshotRef.current, imported);
      const savedBook = next.books.find((book) => !previousBookIds.has(book.id));
      if (savedBook) {
        if (canCacheReaderText(imported.format, content, file.size)) {
          readerContentCacheRef.current.set(savedBook.id, content);
          writeReaderTextCache(savedBook.id, content);
        } else {
          readerContentCacheRef.current.delete(savedBook.id);
        }
      }
      snapshotRef.current = next;
      setSnapshot(next);
      updateTask(taskId, {
        status: "success",
        phase: "done",
        progress: 100,
        bookId: savedBook?.id,
        bookTitle: savedBook?.title
      });
      appendHistory({
        id: taskId,
        fileName: file.name,
        fileSize: file.size,
        format: imported.format,
        status: "success",
        encoding,
        isDuplicate: isDuplicateImport,
        bookId: savedBook?.id,
        bookTitle: savedBook?.title,
        timestamp: new Date().toISOString()
      });
      return isDuplicateImport ? "duplicate" : "imported";
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      addMobileLog("error", "书籍导入", `${file.name}：${detail}`, { code: "IMPORT_FAILED" });
      updateTask(taskId, { status: "failed", phase: "error", error: detail });
      appendHistory({
        id: taskId,
        fileName: file.name,
        fileSize: file.size,
        status: "failed",
        error: detail,
        timestamp: new Date().toISOString()
      });
      throw error;
    } finally {
      runningTaskIdsRef.current.delete(taskId);
    }
  }, [updateTask, appendHistory, setSnapshot, readerContentCacheRef]);

  const importFiles = useCallback(async (files: FileList | null) => {
    if (!files?.length) return;
    if (importingRef.current) {
      setMessage("已有书籍正在导入，请等待当前任务完成后再选择文件。");
      return;
    }
    importingRef.current = true;
    try {
    const selectedFiles = Array.from(files);
    const supportedFiles = selectedFiles.filter((file) => isSupportedMobileBookFileName(file.name));
    const emptyFiles = supportedFiles.filter((file) => file.size <= 0);
    const oversizedFiles = supportedFiles.filter((file) => file.size > maxImportBytesFor(file.name));
    const bookFiles = supportedFiles.filter((file) => file.size > 0 && file.size <= maxImportBytesFor(file.name));
    const skippedCount = selectedFiles.length - supportedFiles.length;

    if (!bookFiles.length && !oversizedFiles.length && !emptyFiles.length) {
      setMessage("没有找到可导入的 TXT / Markdown / EPUB 文件；已跳过非书籍或系统隐藏文件。");
      return;
    }

    // 为所有候选文件创建队列任务（含超大文件，标记为 failed）
    const now = Date.now();
    const newTasks: ImportTask[] = [];
    bookFiles.forEach((file, index) => {
      newTasks.push({
        id: `import-${now}-${index}`,
        fileName: file.name,
        fileSize: file.size,
        status: "pending",
        phase: "queued",
        progress: 0,
        createdAt: new Date().toISOString(),
        fileRef: file
      });
    });
    oversizedFiles.forEach((file, index) => {
      newTasks.push({
        id: `import-${now}-oversized-${index}`,
        fileName: file.name,
        fileSize: file.size,
        status: "failed",
        phase: "error",
        progress: 0,
        error: `文件过大，超过 ${formatBytes(maxImportBytesFor(file.name))} 上限`,
        createdAt: new Date().toISOString()
      });
    });
    emptyFiles.forEach((file, index) => {
      newTasks.push({
        id: `import-${now}-empty-${index}`,
        fileName: file.name,
        fileSize: file.size,
        status: "failed",
        phase: "error",
        progress: 0,
        error: "EMPTY_FILE：文件内容为空，未导入",
        createdAt: new Date().toISOString()
      });
    });

    setImportTasks((prev) => [...newTasks, ...prev]);
    setMessage(`准备导入 ${bookFiles.length} 本书${oversizedFiles.length ? `，${oversizedFiles.length} 个超大文件已跳过` : ""}……`);
    await waitForBrowserPaint();

    let importedCount = 0;
    let duplicateCount = 0;
    let failedCount = 0;
    for (const task of newTasks) {
      if (task.status === "failed") {
        // 超大文件直接记录历史
        failedCount += 1;
        appendHistory({
          id: task.id,
          fileName: task.fileName,
          fileSize: task.fileSize,
          status: "failed",
          error: task.error,
          timestamp: new Date().toISOString()
        });
        continue;
      }
      const file = task.fileRef;
      if (!file) continue;
      try {
        const result = await processFile(task.id, file);
        if (result === "duplicate") duplicateCount += 1;
        else importedCount += 1;
      } catch {
        failedCount += 1;
        await waitForBrowserPaint();
      }
    }

    setMessage(
      [
        importedCount ? `已导入 ${importedCount} 本书` : "没有成功导入书籍",
        skippedCount ? `跳过 ${skippedCount} 个非书籍或系统文件` : "",
        oversizedFiles.length ? `${oversizedFiles.length} 个文件超过对应格式的安全上限` : "",
        emptyFiles.length ? `${emptyFiles.length} 个空文件未导入` : "",
        duplicateCount ? `${duplicateCount} 本重复书籍已作为独立副本导入并添加区分标签` : "",
        failedCount ? `${failedCount} 本导入失败` : "",
        importedCount || duplicateCount ? "同名或同内容书籍都可以分别打开，不会互相覆盖" : ""
      ].filter(Boolean).join("；") + "。"
    );
    } finally {
      importingRef.current = false;
    }
  }, [processFile, appendHistory, setMessage]);

  /** 重试失败的导入任务（仅当前会话内有效，因为 File 引用不持久化） */
  const retryImport = useCallback(async (taskId: string) => {
    const task = importTasks.find((item) => item.id === taskId);
    if (!task || task.status !== "failed" || !task.fileRef) {
      setMessage("该失败项无法重试，请重新选择文件导入。");
      return;
    }
    const file = task.fileRef;
    if (importingRef.current || runningTaskIdsRef.current.has(taskId)) {
      setMessage("已有书籍正在导入，请等待当前任务完成后再重试。");
      return;
    }
    importingRef.current = true;
    updateTask(taskId, { status: "pending", phase: "queued", progress: 0, error: undefined });
    try {
      await processFile(taskId, file);
      setMessage(`《${file.name}》重新导入成功。`);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      addMobileLog("warn", "书籍导入", `${file.name} 重试失败：${detail}`, { code: "IMPORT_RETRY_FAILED" });
      setMessage(`《${file.name}》重试失败：${detail}`);
    } finally {
      importingRef.current = false;
    }
  }, [importTasks, processFile, updateTask, setMessage]);

  /** 安全修复缺失正文：只在内容哈希（旧数据无哈希时为文件名）匹配时复用原 bookId。 */
  const repairBookFile = useCallback(async (bookId: string, file: File | undefined) => {
    if (!file) return;
    if (importingRef.current) {
      setMessage("已有书籍正在导入，请等待当前任务完成后再修复正文。");
      return;
    }
    const taskId = `repair-${bookId}-${Date.now().toString(36)}`;
    const task: ImportTask = {
      id: taskId,
      fileName: file.name,
      fileSize: file.size,
      status: "pending",
      phase: "queued",
      progress: 0,
      createdAt: new Date().toISOString(),
      fileRef: file
    };
    setImportTasks((prev) => [task, ...prev]);
    importingRef.current = true;
    runningTaskIdsRef.current.add(taskId);
    try {
      updateTask(taskId, { status: "processing", phase: "validating" });
      if (!isSupportedMobileBookFileName(file.name) || file.size <= 0 || file.size > maxImportBytesFor(file.name)) {
        throw new Error(file.size <= 0 ? "EMPTY_FILE：文件内容为空。" : "所选文件类型或大小不符合导入要求。");
      }
      updateTask(taskId, { phase: "reading" });
      const { content, encoding } = await readImportFileContent(file);
      updateTask(taskId, { phase: file.name.toLowerCase().endsWith(".epub") ? "parsing" : "validating", encoding });
      const imported = await withTimeout(
        createImportedMobileBook(file.name, content, file.size),
        file.name.toLowerCase().endsWith(".epub") ? 20_000 : 10_000,
        "重新导入校验超时，原书籍记录未修改。"
      );
      updateTask(taskId, { phase: "committing", format: imported.format });
      snapshotRef.current = await loadMobileSnapshot();
      const next = await repairMobileBookFromImport(snapshotRef.current, bookId, imported);
      const repaired = next.books.find((book) => book.id === bookId);
      snapshotRef.current = next;
      setSnapshot(next);
      if (repaired && canCacheReaderText(repaired.format, content, repaired.size)) {
        readerContentCacheRef.current.set(repaired.id, content);
        writeReaderTextCache(repaired.id, content);
      }
      updateTask(taskId, { status: "success", phase: "done", progress: 100, bookId, bookTitle: repaired?.title });
      appendHistory({
        id: taskId,
        fileName: file.name,
        fileSize: file.size,
        format: imported.format,
        status: "success",
        encoding,
        bookId,
        bookTitle: repaired?.title,
        timestamp: new Date().toISOString()
      });
      setMessage(`《${repaired?.title ?? file.name}》正文已修复，原阅读进度、书签、笔记和灵感来源均已保留。`);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      addMobileLog("error", "正文修复", `${file.name}：${detail}`, { code: "BOOK_REPAIR_FAILED" });
      updateTask(taskId, { status: "failed", phase: "error", error: detail });
      appendHistory({ id: taskId, fileName: file.name, fileSize: file.size, status: "failed", error: detail, timestamp: new Date().toISOString() });
      setMessage(`修复正文失败：${detail}`);
    } finally {
      runningTaskIdsRef.current.delete(taskId);
      importingRef.current = false;
    }
  }, [appendHistory, readerContentCacheRef, setMessage, setSnapshot, updateTask]);

  /** 清空导入历史 */
  const clearImportHistory = useCallback(() => {
    setImportHistory([]);
    setMessage("已清空导入历史。");
  }, [setMessage]);

  /** 从队列中移除已完成/失败的任务（清理队列） */
  const dismissImportTask = useCallback((taskId: string) => {
    setImportTasks((prev) => prev.filter((task) => task.id !== taskId));
  }, []);

  return {
    importFiles,
    importTasks,
    importHistory,
    retryImport,
    repairBookFile,
    clearImportHistory,
    dismissImportTask
  };
}
