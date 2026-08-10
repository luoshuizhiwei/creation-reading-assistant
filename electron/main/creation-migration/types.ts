export interface LegacyMigrationOptions {
  /** 旧数据根目录（app-settings.json / inspirations.json 所在目录）。 */
  dataRoot: string;
  /** 书库目录；缺省为 dataRoot/AppLibrary。 */
  libraryRoot?: string;
  /** 备份所需最小剩余空间（字节），缺省 200MB。 */
  minFreeBytes?: number;
}

export interface LegacyMigrationActivation {
  formatVersion: 1;
  activatedAt: string;
  backupDirectory: string;
  reportPath: string;
  idMapPath: string;
  discovered: number;
  migrated: number;
  skipped: number;
  failed: number;
}

export interface LegacyMigrationReport {
  reportVersion: 1;
  generatedAt: string;
  activated: boolean;
  backup: {
    directory: string;
    manifestVersion: number;
    manifestPath: string;
    files: number;
    bytes: number;
    checksumVerified: boolean;
  };
  sources: {
    discovered: number;
    migrated: number;
    skipped: number;
    failed: number;
  };
  failures: Array<{ legacyId: string; reason: string; retryable: boolean }>;
  idMap: {
    path: string;
    entries: number;
  };
  targetStore: {
    directory: string;
    integrityOk: boolean;
    schemaVersion: number;
    wasFresh: boolean;
  };
  rollback: {
    how: string;
    backupDirectory: string;
  };
}

export interface LegacyMigrationStatus {
  activated: boolean;
  activation: LegacyMigrationActivation | null;
  report: LegacyMigrationReport | null;
  canProceed: boolean;
  /** 审计阻断原因（canProceed=false 时的可读说明）。 */
  blockingReasons: string[];
}
