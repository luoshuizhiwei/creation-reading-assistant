// Barrel re-export 文件：保持原导入路径向后兼容。
// 实现已按内聚域拆分到 4 个子模块：
// - mobile-storage-core.ts: 常量、工具函数、snapshot 管理、SQLite 持久化
// - mobile-storage-books.ts: 书籍导入和文件管理
// - mobile-storage-inspirations.ts: 灵感和笔记管理
// - mobile-storage-reading.ts: 阅读进度、会话、快照导入导出、同步账户

export {
  BOOK_CONTENT_STORAGE_KEY_PREFIX,
  emptySnapshot,
  getBaseFileName,
  getMobileBookFileExtension,
  getMobileDeviceId,
  getStoredBookFileName,
  isSupportedMobileBookFileName,
  initializeMobileStorage,
  loadMobileReaderSettings,
  loadMobileSnapshot,
  migrateLegacySnapshot,
  normalizeMobileSnapshot,
  nowIso,
  sanitizeMobileBookForSync,
  sanitizeSyncAccount,
  saveMobileReaderSettings,
  saveMobileSnapshot,
  type MobileSnapshot
} from "./mobile-storage-core";

export {
  createImportedMobileBook,
  deleteMobileBook,
  hashText,
  repairMobileBookFromImport,
  recoverInterruptedMobileImports,
  saveMobileBook,
  saveSyncedMobileBookBlob,
  saveSyncedMobileBookFile
} from "./mobile-storage-books";

export {
  addMobileInspiration,
  addMobileInspirationVariant,
  addMobileNote,
  batchAddInspirationTags,
  batchDeleteInspirations,
  batchUpdateInspirationStatus,
  deleteMobileInspiration,
  deleteMobileNote,
  updateMobileInspiration
} from "./mobile-storage-inspirations";

export {
  addMobileHighlight,
  deleteMobileHighlight,
  exportBookExcerpts,
  updateMobileHighlight
} from "./mobile-storage-highlights";

export {
  addMobileReadingSession,
  compareReadingLocation,
  createReadingLocation,
  exportMobileSnapshot,
  importMobileSnapshot,
  mergeReadingProgress,
  saveMobileReadingProgress,
  saveSyncAccount
} from "./mobile-storage-reading";
