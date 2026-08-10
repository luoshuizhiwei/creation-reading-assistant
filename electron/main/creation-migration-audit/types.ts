export type LegacySourceName =
  | "appSettings"
  | "inspirations"
  | "aiSecrets"
  | "library"
  | "highlights"
  | "bookmarks"
  | "readingProgress"
  | "libraryFiles"
  | "libraryCovers"
  | "searchIndexes";

export type LegacySourceStatus = "missing" | "empty" | "valid" | "recoverable-backup" | "invalid" | "metadata-only";

export interface LegacySourceAudit {
  status: LegacySourceStatus;
  version?: number;
  count: number;
  bytes: number;
  policy: "migrate" | "keep-in-place" | "legacy-reader-owned" | "inventory-only";
}

export interface MigrationAuditIssue {
  severity: "blocking" | "warning" | "info";
  code: string;
  source: LegacySourceName | "dataRoot" | "targetStore";
  legacyId?: string;
  message: string;
  retryable: boolean;
}

export interface InspirationMigrationPlanItem {
  legacyId: string;
  target: "global-inbox";
  proposedTargetId: string;
  bodySha256: string;
  variantsCount: number;
  hasSource: boolean;
}

export interface LegacyMigrationAuditReport {
  reportVersion: 1;
  auditedAt: string;
  canProceed: boolean;
  activated: false;
  writesPerformed: 0;
  root: {
    exists: boolean;
    readable: boolean;
    writable: boolean;
    freeBytes?: number;
  };
  sources: Record<LegacySourceName, LegacySourceAudit>;
  issues: MigrationAuditIssue[];
  inspirationPlan: {
    discovered: number;
    migratable: number;
    skipped: number;
    mappingStrategy: "preserve-if-available";
    items: InspirationMigrationPlanItem[];
  };
  readerCompatibility: {
    books: number;
    booksByFormat: { txt: number; md: number; epub: number; unknown: number };
    highlights: number;
    bookmarks: number;
    progress: number;
    files: number;
    covers: number;
    searchIndexes: number;
    assetBytes: number;
    policy: "legacy-reader-owned";
  };
  targetStore: {
    status: "absent" | "present";
    inspectedByOpening: false;
  };
}

export interface AuditLegacyDesktopDataOptions {
  dataRoot: string;
  libraryRoot?: string;
}
