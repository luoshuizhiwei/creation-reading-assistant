import { constants } from "node:fs";
import { access, readFile, readdir, stat, statfs } from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import type {
  AuditLegacyDesktopDataOptions,
  InspirationMigrationPlanItem,
  LegacyMigrationAuditReport,
  LegacySourceAudit,
  LegacySourceName,
  MigrationAuditIssue
} from "./types";

interface JsonSourceDefinition {
  name: LegacySourceName;
  relativePath: string;
  policy: LegacySourceAudit["policy"];
  getItems?: (value: unknown) => unknown[] | null;
}

interface ParsedSource {
  audit: LegacySourceAudit;
  value?: unknown;
}

const JSON_SOURCES: JsonSourceDefinition[] = [
  { name: "appSettings", relativePath: "app-settings.json", policy: "migrate" },
  {
    name: "inspirations",
    relativePath: "inspirations.json",
    policy: "migrate",
    getItems: (value) => (Array.isArray(value) ? value : isRecord(value) && Array.isArray(value.items) ? value.items : null)
  },
  {
    name: "library",
    relativePath: path.join("AppLibrary", "library.json"),
    policy: "legacy-reader-owned",
    getItems: (value) => (isRecord(value) && Array.isArray(value.books) ? value.books : null)
  },
  {
    name: "highlights",
    relativePath: path.join("AppLibrary", "highlights.json"),
    policy: "legacy-reader-owned",
    getItems: listItems
  },
  {
    name: "bookmarks",
    relativePath: path.join("AppLibrary", "bookmarks.json"),
    policy: "legacy-reader-owned",
    getItems: listItems
  },
  {
    name: "readingProgress",
    relativePath: path.join("AppLibrary", "reading-progress.json"),
    policy: "legacy-reader-owned",
    getItems: listItems
  }
];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function listItems(value: unknown): unknown[] | null {
  return Array.isArray(value) ? value : isRecord(value) && Array.isArray(value.items) ? value.items : null;
}

function versionOf(value: unknown): number | undefined {
  return isRecord(value) && typeof value.version === "number" ? value.version : undefined;
}

async function exists(filePath: string): Promise<boolean> {
  try {
    await stat(filePath);
    return true;
  } catch {
    return false;
  }
}

async function parseJsonFile(filePath: string): Promise<{ value: unknown; bytes: number } | null> {
  try {
    const info = await stat(filePath);
    if (!info.isFile()) return null;
    const content = await readFile(filePath, "utf8");
    if (!content.trim()) return null;
    return { value: JSON.parse(content) as unknown, bytes: info.size };
  } catch {
    return null;
  }
}

async function isEmptyJsonFile(filePath: string): Promise<boolean> {
  try {
    const content = await readFile(filePath, "utf8");
    return !content.trim();
  } catch {
    return false;
  }
}

function resolveSourcePath(dataRoot: string, libraryRoot: string, relativePath: string): string {
  const libraryPrefix = `AppLibrary${path.sep}`;
  return relativePath.startsWith(libraryPrefix)
    ? path.join(libraryRoot, relativePath.slice(libraryPrefix.length))
    : path.join(dataRoot, relativePath);
}

async function auditJsonSource(
  dataRoot: string,
  libraryRoot: string,
  definition: JsonSourceDefinition,
  issues: MigrationAuditIssue[]
): Promise<ParsedSource> {
  const primaryPath = resolveSourcePath(dataRoot, libraryRoot, definition.relativePath);
  const backupPath = `${primaryPath}.bak`;
  const primaryPresent = await exists(primaryPath);
  const primary = primaryPresent ? await parseJsonFile(primaryPath) : null;
  if (primary) {
    const items = definition.getItems?.(primary.value);
    if (definition.getItems && items === null) {
      const backup = await parseJsonFile(backupPath);
      const backupItems = backup ? definition.getItems(backup.value) : null;
      if (backup && backupItems !== null) {
        issues.push({
          severity: "warning",
          code: "using-backup",
          source: definition.name,
          message: "主文件结构无法识别，审计计划使用可解析的备份文件。",
          retryable: true
        });
        return {
          audit: {
            status: "recoverable-backup",
            version: versionOf(backup.value),
            count: backupItems.length,
            bytes: backup.bytes,
            policy: definition.policy
          },
          value: backup.value
        };
      }
      issues.push({
        severity: "blocking",
        code: "invalid-json-shape",
        source: definition.name,
        message: "旧数据文件结构无法识别。",
        retryable: true
      });
      return {
        audit: { status: "invalid", version: versionOf(primary.value), count: 0, bytes: primary.bytes, policy: definition.policy }
      };
    }
    return {
      audit: {
        status: "valid",
        version: versionOf(primary.value),
        count: items?.length ?? 1,
        bytes: primary.bytes,
        policy: definition.policy
      },
      value: primary.value
    };
  }

  const backup = await parseJsonFile(backupPath);
  if (backup) {
    const items = definition.getItems?.(backup.value);
    if (!definition.getItems || items !== null) {
      issues.push({
        severity: "warning",
        code: "using-backup",
        source: definition.name,
        message: "主文件不可用，审计计划使用可解析的备份文件。",
        retryable: true
      });
      return {
        audit: {
          status: "recoverable-backup",
          version: versionOf(backup.value),
          count: items?.length ?? 1,
          bytes: backup.bytes,
          policy: definition.policy
        },
        value: backup.value
      };
    }
  }

  if (primaryPresent || (await exists(backupPath))) {
    if (primaryPresent && (await isEmptyJsonFile(primaryPath)) && !(await exists(backupPath))) {
      issues.push({
        severity: "warning",
        code: "source-empty",
        source: definition.name,
        message: "旧数据文件为空，将按无条目处理。",
        retryable: true
      });
      return { audit: { status: "empty", count: 0, bytes: 0, policy: definition.policy } };
    }
    issues.push({
      severity: "blocking",
      code: "json-unreadable",
      source: definition.name,
      message: "主文件和备份文件均无法解析。",
      retryable: true
    });
    return { audit: { status: "invalid", count: 0, bytes: 0, policy: definition.policy } };
  }

  issues.push({
    severity: "info",
    code: "source-missing",
    source: definition.name,
    message: "未发现该旧数据文件。",
    retryable: false
  });
  return { audit: { status: "missing", count: 0, bytes: 0, policy: definition.policy } };
}

async function inventoryDirectory(directory: string): Promise<{ count: number; bytes: number; failed: boolean }> {
  try {
    const entries = await readdir(directory, { withFileTypes: true });
    let count = 0;
    let bytes = 0;
    for (const entry of entries) {
      const entryPath = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        const nested = await inventoryDirectory(entryPath);
        count += nested.count;
        bytes += nested.bytes;
        if (nested.failed) return { count, bytes, failed: true };
      } else if (entry.isFile()) {
        count += 1;
        bytes += (await stat(entryPath)).size;
      }
    }
    return { count, bytes, failed: false };
  } catch {
    return { count: 0, bytes: 0, failed: true };
  }
}

function emptySource(policy: LegacySourceAudit["policy"]): LegacySourceAudit {
  return { status: "missing", count: 0, bytes: 0, policy };
}

function sha256(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

function safeLegacyId(value: unknown, index: number): string {
  return typeof value === "string" && value.trim() ? value : `unknown-${index + 1}`;
}

export async function auditLegacyDesktopData(
  options: AuditLegacyDesktopDataOptions
): Promise<LegacyMigrationAuditReport> {
  const issues: MigrationAuditIssue[] = [];
  const sources = {
    appSettings: emptySource("migrate"),
    inspirations: emptySource("migrate"),
    aiSecrets: emptySource("keep-in-place"),
    library: emptySource("legacy-reader-owned"),
    highlights: emptySource("legacy-reader-owned"),
    bookmarks: emptySource("legacy-reader-owned"),
    readingProgress: emptySource("legacy-reader-owned"),
    libraryFiles: emptySource("inventory-only"),
    libraryCovers: emptySource("inventory-only"),
    searchIndexes: emptySource("inventory-only")
  } satisfies Record<LegacySourceName, LegacySourceAudit>;

  const dataRoot = typeof options?.dataRoot === "string" ? options.dataRoot : "";
  const libraryRoot =
    typeof options?.libraryRoot === "string" && options.libraryRoot.trim()
      ? options.libraryRoot
      : path.join(dataRoot, "AppLibrary");
  let rootExists = false;
  let rootIsDirectory = false;
  if (dataRoot) {
    try {
      const rootInfo = await stat(dataRoot);
      rootExists = true;
      rootIsDirectory = rootInfo.isDirectory();
    } catch {
      // Reported as a missing root below.
    }
  }
  let readable = false;
  let writable = false;
  let freeBytes: number | undefined;
  if (rootExists && rootIsDirectory) {
    try {
      await access(dataRoot, constants.R_OK);
      readable = true;
    } catch {
      // Reported below without exposing the path.
    }
    try {
      await access(dataRoot, constants.W_OK);
      writable = true;
    } catch {
      // A write probe is intentionally forbidden in this slice.
    }
    try {
      const info = await statfs(dataRoot);
      freeBytes = info.bavail * info.bsize;
    } catch {
      // Free space is advisory in the audit-only slice.
    }
  }

  if (!rootExists || !rootIsDirectory || !readable) {
    issues.push({
      severity: "blocking",
      code: !rootExists ? "root-missing" : !rootIsDirectory ? "root-not-directory" : "root-unreadable",
      source: "dataRoot",
      message: !rootExists ? "旧数据目录不存在。" : !rootIsDirectory ? "旧数据根不是目录。" : "旧数据目录不可读。",
      retryable: true
    });
  }

  let inspirationValue: unknown;
  let libraryValue: unknown;
  if (rootExists && rootIsDirectory && readable) {
    for (const definition of JSON_SOURCES) {
      const parsed = await auditJsonSource(dataRoot, libraryRoot, definition, issues);
      sources[definition.name] = parsed.audit;
      if (definition.name === "inspirations") inspirationValue = parsed.value;
      if (definition.name === "library") libraryValue = parsed.value;
    }

    const secretPath = path.join(dataRoot, "ai-secrets.json");
    try {
      const secretInfo = await stat(secretPath);
      sources.aiSecrets = {
        status: "metadata-only",
        count: secretInfo.isFile() ? 1 : 0,
        bytes: secretInfo.isFile() ? secretInfo.size : 0,
        policy: "keep-in-place"
      };
    } catch {
      sources.aiSecrets = emptySource("keep-in-place");
    }

    const directories: Array<[LegacySourceName, string]> = [
      ["libraryFiles", path.join(libraryRoot, "files")],
      ["libraryCovers", path.join(libraryRoot, "covers")],
      ["searchIndexes", path.join(libraryRoot, "search-index")]
    ];
    for (const [name, directory] of directories) {
      const inventory = await inventoryDirectory(directory);
      if (inventory.failed && (await exists(directory))) {
        issues.push({
          severity: "warning",
          code: "inventory-error",
          source: name,
          message: "资产目录存在，但无法完整盘点。",
          retryable: true
        });
      }
      sources[name] = {
        status: (await exists(directory)) ? "metadata-only" : "missing",
        count: inventory.count,
        bytes: inventory.bytes,
        policy: "inventory-only"
      };
    }
  }

  const rawInspirations = Array.isArray(inspirationValue)
    ? inspirationValue
    : isRecord(inspirationValue) && Array.isArray(inspirationValue.items)
      ? inspirationValue.items
      : [];
  const inspirationItems: InspirationMigrationPlanItem[] = [];
  let skippedInspirations = 0;
  rawInspirations.forEach((item, index) => {
    if (!isRecord(item) || typeof item.id !== "string" || typeof item.body !== "string") {
      skippedInspirations += 1;
      issues.push({
        severity: "warning",
        code: "inspiration-item-invalid",
        source: "inspirations",
        legacyId: isRecord(item) ? safeLegacyId(item.id, index) : `unknown-${index + 1}`,
        message: "灵感条目缺少稳定 ID 或正文，迁移计划将跳过该条目。",
        retryable: true
      });
      return;
    }
    inspirationItems.push({
      legacyId: item.id,
      target: "global-inbox",
      proposedTargetId: item.id,
      bodySha256: sha256(item.body),
      variantsCount: Array.isArray(item.variants) ? item.variants.length : 0,
      hasSource: isRecord(item.source) || typeof item.sourceBookId === "string" || isRecord(item.sourceLocation)
    });
  });

  const books = isRecord(libraryValue) && Array.isArray(libraryValue.books) ? libraryValue.books : [];
  const booksByFormat = { txt: 0, md: 0, epub: 0, unknown: 0 };
  for (const book of books) {
    const format = isRecord(book) ? book.format : undefined;
    if (format === "txt" || format === "md" || format === "epub") booksByFormat[format] += 1;
    else booksByFormat.unknown += 1;
  }

  const targetPresent = rootExists && rootIsDirectory && (await exists(path.join(dataRoot, "CreationWorkspace", "workspace.sqlite")));
  if (targetPresent) {
    issues.push({
      severity: "warning",
      code: "target-store-present",
      source: "targetStore",
      message: "检测到目标 store；本审计未打开、修改或激活它。",
      retryable: false
    });
  }

  return {
    reportVersion: 1,
    auditedAt: new Date().toISOString(),
    canProceed: !issues.some((issue) => issue.severity === "blocking"),
    activated: false,
    writesPerformed: 0,
    root: { exists: rootExists, readable, writable, freeBytes },
    sources,
    issues,
    inspirationPlan: {
      discovered: rawInspirations.length,
      migratable: inspirationItems.length,
      skipped: skippedInspirations,
      mappingStrategy: "preserve-if-available",
      items: inspirationItems
    },
    readerCompatibility: {
      books: books.length,
      booksByFormat,
      highlights: sources.highlights.count,
      bookmarks: sources.bookmarks.count,
      progress: sources.readingProgress.count,
      files: sources.libraryFiles.count,
      covers: sources.libraryCovers.count,
      searchIndexes: sources.searchIndexes.count,
      assetBytes: sources.libraryFiles.bytes + sources.libraryCovers.bytes + sources.searchIndexes.bytes,
      policy: "legacy-reader-owned"
    },
    targetStore: { status: targetPresent ? "present" : "absent", inspectedByOpening: false }
  };
}

export * from "./types";
