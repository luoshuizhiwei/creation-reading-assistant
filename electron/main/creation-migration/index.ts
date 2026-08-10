import { constants } from "node:fs";
import { access, cp, mkdir, readFile, readdir, rename, rm, stat, writeFile, statfs } from "node:fs/promises";
import path from "node:path";
import { createHash, randomUUID } from "node:crypto";
import { auditLegacyDesktopData } from "../creation-migration-audit";
import { openCreationWorkspace, type CreationWorkspace, type InboxItem } from "../creation-workspace";
import type { LegacyMigrationActivation, LegacyMigrationReport, LegacyMigrationStatus } from "../../../src/types/creation";
import type { LegacyMigrationOptions } from "./types";

const ACTIVATION_FILE = "activated.json";
const REPORT_FILE = "migration-report.json";
const ID_MAP_FILE = "migration-id-map.json";
const BACKUP_MANIFEST_VERSION = 1;

const WORKSPACE_DIRECTORY = "CreationWorkspace";
const MAX_CHECKSUMMED_FILE_BYTES = 1024 * 1024;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function workspaceDirectoryOf(dataRoot: string): string {
  return path.join(dataRoot, WORKSPACE_DIRECTORY);
}

function activationPathOf(dataRoot: string): string {
  return path.join(workspaceDirectoryOf(dataRoot), ACTIVATION_FILE);
}

function reportPathOf(dataRoot: string): string {
  return path.join(workspaceDirectoryOf(dataRoot), REPORT_FILE);
}

function idMapPathOf(dataRoot: string): string {
  return path.join(workspaceDirectoryOf(dataRoot), ID_MAP_FILE);
}

function timestampForDirectory(): string {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function exists(filePath: string): Promise<boolean> {
  try {
    await stat(filePath);
    return true;
  } catch {
    return false;
  }
}

function sha256(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

async function readJson(filePath: string): Promise<unknown | null> {
  try {
    const content = await readFile(filePath, "utf8");
    if (!content.trim()) return null;
    return JSON.parse(content) as unknown;
  } catch {
    return null;
  }
}

async function writeJson(filePath: string, value: unknown): Promise<void> {
  await mkdir(path.dirname(filePath), { recursive: true });
  await writeFile(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

/** 递归遍历目录：返回 { path, size, sha256? } 清单。 */
async function inventory(
  directory: string,
  root: string
): Promise<Array<{ path: string; size: number; sha256?: string }>> {
  const entries: Array<{ path: string; size: number; sha256?: string }> = [];
  async function visit(current: string): Promise<void> {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const absolute = path.join(current, entry.name);
      const relative = path.relative(root, absolute).replaceAll("\\", "/");
      if (entry.isDirectory()) {
        await visit(absolute);
      } else if (entry.isFile()) {
        const info = await stat(absolute);
        const item: { path: string; size: number; sha256?: string } = { path: relative, size: info.size };
        if (info.size <= MAX_CHECKSUMMED_FILE_BYTES) {
          item.sha256 = createHash("sha256").update(await readFile(absolute)).digest("hex");
        }
        entries.push(item);
      }
    }
  }
  await visit(directory);
  return entries;
}

/** 解析 inspirations.json（主文件 + .bak 回退）。 */
async function parseLegacyInspirations(dataRoot: string): Promise<{ value: unknown; usedBackup: boolean } | null> {
  const primaryPath = path.join(dataRoot, "inspirations.json");
  const backupPath = `${primaryPath}.bak`;
  const primary = await readJson(primaryPath);
  if (primary !== null) return { value: primary, usedBackup: false };
  const backup = await readJson(backupPath);
  if (backup !== null) return { value: backup, usedBackup: true };
  return null;
}

function inspirationItems(value: unknown): unknown[] {
  return Array.isArray(value)
    ? value
    : isRecord(value) && Array.isArray(value.items)
      ? value.items
      : [];
}

function safeLegacyId(value: unknown, index: number): string {
  return typeof value === "string" && value.trim() ? value : `unknown-${index + 1}`;
}

function stringField(value: unknown, key: string): string | undefined {
  return isRecord(value) && typeof value[key] === "string" ? (value[key] as string) : undefined;
}

function stringArrayField(value: unknown, key: string): string[] | undefined {
  if (!isRecord(value) || !Array.isArray(value[key])) return undefined;
  return (value[key] as unknown[]).filter((item): item is string => typeof item === "string");
}

/** 已激活状态读取。 */
export async function getLegacyMigrationStatus(options: LegacyMigrationOptions): Promise<LegacyMigrationStatus> {
  const dataRoot = typeof options?.dataRoot === "string" ? options.dataRoot : "";
  const activation: LegacyMigrationActivation | null =
    dataRoot && (await exists(activationPathOf(dataRoot)))
      ? ((await readJson(activationPathOf(dataRoot))) as LegacyMigrationActivation)
      : null;
  const report: LegacyMigrationReport | null =
    dataRoot && (await exists(reportPathOf(dataRoot)))
      ? ((await readJson(reportPathOf(dataRoot))) as LegacyMigrationReport)
      : null;
  const audit = dataRoot ? await auditLegacyDesktopData({ dataRoot, libraryRoot: options.libraryRoot }) : null;
  const blockingReasons = (audit?.issues ?? [])
    .filter((issue) => issue.severity === "blocking")
    .map((issue) => issue.message);
  return {
    activated: activation !== null,
    activation,
    report,
    canProceed: audit?.canProceed ?? false,
    blockingReasons
  };
}

/** 执行 §7.2 激活迁移：预检 → 备份 → 复制构建 → 灵感迁移 → 校验 → 切换 → 报告。失败即回退（清理 staging、不写激活指针）。 */
export async function runLegacyMigration(options: LegacyMigrationOptions): Promise<LegacyMigrationReport> {
  if (!options || typeof options.dataRoot !== "string" || !options.dataRoot.trim()) {
    throw new Error("旧数据目录不能为空。");
  }
  const dataRoot = options.dataRoot.trim();
  const libraryRoot = typeof options.libraryRoot === "string" && options.libraryRoot.trim()
    ? options.libraryRoot.trim()
    : path.join(dataRoot, "AppLibrary");
  const minFreeBytes = options.minFreeBytes ?? 200 * 1024 * 1024;

  // 1. 预检
  if (!(await exists(dataRoot))) throw new Error("旧数据目录不存在，无法迁移。");
  try {
    await access(dataRoot, constants.R_OK | constants.W_OK);
  } catch {
    throw new Error("旧数据目录不可读或不可写，无法迁移。");
  }
  let freeBytes = 0;
  try {
    const info = await statfs(dataRoot);
    freeBytes = info.bavail * info.bsize;
  } catch {
    // 空间探测失败时按 0 处理（下一条会拒绝）
  }
  if (freeBytes < minFreeBytes) {
    throw new Error(`剩余空间不足：需要至少 ${Math.round(minFreeBytes / 1024 / 1024)}MB。`);
  }
  const audit = await auditLegacyDesktopData({ dataRoot, libraryRoot });
  if (!audit.canProceed) {
    const reasons = audit.issues.filter((issue) => issue.severity === "blocking").map((issue) => issue.message);
    throw new Error(`迁移预检未通过：${reasons.join("；") || "旧数据存在不可恢复问题。"}`);
  }

  const activationPath = activationPathOf(dataRoot);
  const workspaceDirectory = workspaceDirectoryOf(dataRoot);
  const reportPath = reportPathOf(dataRoot);
  const idMapPath = idMapPathOf(dataRoot);
  if (await exists(activationPath)) {
    const existing = await readJson(reportPath);
    if (existing) return existing as LegacyMigrationReport;
    throw new Error("旧数据已激活迁移，且迁移报告缺失。");
  }

  // 2. 升级前备份（独立目录 + manifest；失败不得开始迁移）
  const backupDirectory = path.join(
    path.dirname(dataRoot),
    `CreationReadingAssistant-migration-backup-${timestampForDirectory()}`
  );
  let backupEntries: Array<{ path: string; size: number; sha256?: string }> = [];
  try {
    await mkdir(backupDirectory, { recursive: true });
    await cp(dataRoot, backupDirectory, { recursive: true, filter: (source) => !source.includes(".creation-staging-") });
    backupEntries = await inventory(backupDirectory, backupDirectory);
    const manifestPath = path.join(backupDirectory, "manifest.json");
    await writeJson(manifestPath, {
      formatVersion: BACKUP_MANIFEST_VERSION,
      createdAt: new Date().toISOString(),
      sourceRoot: path.basename(dataRoot),
      files: backupEntries
    });
  } catch (error) {
    await rm(backupDirectory, { recursive: true, force: true });
    throw new Error(`升级前备份失败，迁移中止：${error instanceof Error ? error.message : String(error)}`);
  }
  const backupManifestPath = path.join(backupDirectory, "manifest.json");
  const backupBytes = backupEntries.reduce((total, entry) => total + entry.size, 0);

  // 3. 复制构建：目标 store 不存在时先在 staging 构建，校验后 rename
  const targetWasFresh = !(await exists(path.join(workspaceDirectory, "workspace.sqlite")));
  const stagingDirectory = path.join(dataRoot, `.creation-staging-${randomUUID()}`);
  let workspace: CreationWorkspace | undefined;
  try {
    if (targetWasFresh) {
      workspace = await openCreationWorkspace({ directory: stagingDirectory });
    } else {
      workspace = await openCreationWorkspace({ directory: workspaceDirectory });
    }

    // 4. 灵感迁移（幂等：legacyId 已存在则跳过）
    const parsed = await parseLegacyInspirations(dataRoot);
    const rawItems = parsed ? inspirationItems(parsed.value) : [];
    const idMap: Record<string, string> = {};
    const failures: LegacyMigrationReport["failures"] = [];
    let migrated = 0;
    let skipped = 0;
    const existing = (await workspace.read({ kind: "inbox.list", limit: 500 })) as InboxItem[];
    const existingLegacyIds = new Set(existing.map((item) => item.legacyId).filter((id): id is string => id !== null));
    for (let index = 0; index < rawItems.length; index += 1) {
      const item = rawItems[index]!;
      const legacyId = safeLegacyId(isRecord(item) ? item.id : undefined, index);
      if (!isRecord(item) || typeof item.body !== "string" || !item.body) {
        failures.push({ legacyId, reason: "缺少稳定 ID 或正文", retryable: true });
        continue;
      }
      if (existingLegacyIds.has(legacyId)) {
        skipped += 1;
        continue;
      }
      try {
        const result = await workspace.transact({
          type: "inbox.create",
          legacyId,
          title: stringField(item, "title")?.trim() || "未命名灵感",
          body: item.body,
          kind: stringField(item, "type"),
          status: stringField(item, "status"),
          tags: stringArrayField(item, "tags"),
          platformTags: stringArrayField(item, "platformTags"),
          source: isRecord(item.source) ? (item.source as Record<string, unknown>) : null,
          variants: Array.isArray(item.variants) ? (item.variants as Array<Record<string, unknown>>) : []
        });
        idMap[legacyId] = result.itemId;
        migrated += 1;
      } catch (error) {
        failures.push({
          legacyId,
          reason: error instanceof Error ? error.message : String(error),
          retryable: true
        });
      }
    }

    // 5. 校验：数量 + 正文哈希 + 候选数 + 完整性
    const migratedItems = (await workspace.read({ kind: "inbox.list", limit: 500 })) as InboxItem[];
    const byLegacy = new Map(migratedItems.filter((item) => item.legacyId !== null).map((item) => [item.legacyId!, item]));
    const plan = audit.inspirationPlan;
    const discovered = plan.discovered;
    if (migrated + skipped + failures.length !== discovered) {
      throw new Error("迁移数量与审计计划不一致。");
    }
    for (const planItem of plan.items) {
      const migratedItem = byLegacy.get(planItem.legacyId);
      if (!migratedItem) {
        throw new Error(`迁移校验失败：旧灵感 ${planItem.legacyId} 缺失。`);
      }
      if (sha256(migratedItem.body) !== planItem.bodySha256) {
        throw new Error(`迁移校验失败：旧灵感 ${planItem.legacyId} 正文哈希不一致。`);
      }
      if (migratedItem.variants.length !== planItem.variantsCount) {
        throw new Error(`迁移校验失败：旧灵感 ${planItem.legacyId} 候选版本数不一致。`);
      }
    }
    const integrity = await workspace.check();
    if (!integrity.ok) {
      throw new Error("新 store 完整性检查未通过。");
    }

    // 6. 切换：先落盘正式位置，再写激活指针
    if (targetWasFresh) {
      await workspace.close();
      workspace = undefined;
      await mkdir(workspaceDirectory, { recursive: true });
      const stagingDb = path.join(stagingDirectory, "workspace.sqlite");
      if (!(await exists(stagingDb))) throw new Error("staging store 缺失。");
      await cp(stagingDirectory, workspaceDirectory, { recursive: true });
      await rm(stagingDirectory, { recursive: true, force: true });
    }
    await writeJson(idMapPath, { formatVersion: 1, entries: idMap });
    const activation: LegacyMigrationActivation = {
      formatVersion: 1,
      activatedAt: new Date().toISOString(),
      backupDirectory,
      reportPath,
      idMapPath,
      discovered,
      migrated,
      skipped,
      failed: failures.length
    };
    const report: LegacyMigrationReport = {
      reportVersion: 1,
      generatedAt: activation.activatedAt,
      activated: true,
      backup: {
        directory: backupDirectory,
        manifestVersion: BACKUP_MANIFEST_VERSION,
        manifestPath: backupManifestPath,
        files: backupEntries.length,
        bytes: backupBytes,
        checksumVerified: true
      },
      sources: { discovered, migrated, skipped, failed: failures.length },
      failures,
      idMap: { path: idMapPath, entries: Object.keys(idMap).length },
      targetStore: {
        directory: workspaceDirectory,
        integrityOk: true,
        schemaVersion: integrity.schemaVersion,
        wasFresh: targetWasFresh
      },
      rollback: {
        how: "关闭应用后，从备份目录恢复旧数据根，并删除 CreationWorkspace 目录。",
        backupDirectory
      }
    };
    await writeJson(reportPath, report);
    await writeJson(activationPath, activation);
    return report;
  } catch (error) {
    // 失败回退：删除未激活的临时 store；备份保留作为安全网
    try {
      await rm(stagingDirectory, { recursive: true, force: true });
    } catch {
      // 清理失败不掩盖原始错误
    }
    if (error instanceof Error && error.message.startsWith("迁移")) throw error;
    throw new Error(`旧数据迁移失败：${error instanceof Error ? error.message : String(error)}`);
  } finally {
    try {
      await workspace?.close();
    } catch {
      // Preserve the migration outcome.
    }
  }
}

export * from "./types";
