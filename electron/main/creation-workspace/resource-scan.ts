/**
 * 资源孤儿只读扫描。
 *
 * 默认只读：识别 resources 记录与 resources/ 文件树之间的一致性问题，绝不修改 DB 或文件，
 * 也绝不暴露绝对路径。本轮仅产出问题清单（preview），不实现自动删除；
 * 未来若要做修复，必须走 preview → confirm → apply 流程。
 *
 * 覆盖的问题类型：
 * - file-missing        记录存在但文件缺失
 * - file-unreferenced   文件存在但无记录引用
 * - size-mismatch       文件大小与记录不一致
 * - hash-mismatch       SHA-256 与记录不一致
 * - unsafe-relative-path relativePath 为绝对路径 / 路径穿越 / 越界 / 指向目录
 * - symlink-escape      符号链接 / junction / reparse point 真实路径越界
 * - duplicate-path      同一相对路径存在多条资源记录
 * - record-conflict     资源记录 ID 重复（数据库内冲突）
 *
 * 设计约束（来自 P1 收尾规格）：
 * - 不读取/修改工作区数据；不改 DB；不移动/删除文件；
 * - 运行中的 operation 临时目录（点前缀，如 .bundle-import-* / .creation-bundle-*）不误报为孤儿；
 * - 扫描只在相对路径层面工作，消息不含任何绝对路径；
 * - 循环内周期性调用 operation.throwIfCancelled()（scanning 阶段可中断）。
 */

import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { lstat, readdir, realpath } from "node:fs/promises";
import { existsSync } from "node:fs";
import { pipeline } from "node:stream/promises";
import path from "node:path";
import Database from "better-sqlite3";
import type { OperationController } from "../operation";

export type ResourceIssueType =
  | "file-missing"
  | "file-unreferenced"
  | "size-mismatch"
  | "hash-mismatch"
  | "unsafe-relative-path"
  | "symlink-escape"
  | "duplicate-path"
  | "record-conflict";

export interface ResourceRecord {
  id: string;
  projectId: string | null;
  cardId: string | null;
  relativePath: string;
  sha256: string;
  size: number;
  originalName: string | null;
  createdAt: string;
}

export interface ResourceIssue {
  type: ResourceIssueType;
  /** 关联的资源记录 ID；纯文件侧问题（如未引用文件）可能缺失。 */
  resourceId?: string;
  /** 工作区相对路径（始终以 resources/ 开头，绝不含绝对前缀）。 */
  relativePath: string;
  message: string;
}

export interface ResourceScanResult {
  issues: ResourceIssue[];
  scannedRecordCount: number;
  scannedFileCount: number;
}

/**
 * 从只读连接读取全部资源记录。调用方负责连接的生命周期。
 * 仅做普通 SELECT，不开启事务、不改 schema、不写数据。
 */
export function readResourceRecords(db: Database): ResourceRecord[] {
  const hasGlobalResources = db.prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'global_card_resources'").get() !== undefined;
  const sql = hasGlobalResources
    ? `SELECT id, project_id, card_id, relative_path, sha256, size, original_name, created_at FROM resources
       UNION ALL
       SELECT id, NULL AS project_id, card_id, relative_path, sha256, size, original_name, created_at FROM global_card_resources`
    : "SELECT id, project_id, card_id, relative_path, sha256, size, original_name, created_at FROM resources";
  const rows = db.prepare(sql)
    .all() as Array<{
    id: string;
    project_id: string | null;
    card_id: string | null;
    relative_path: string;
    sha256: string;
    size: number;
    original_name: string | null;
    created_at: string;
  }>;
  return rows.map((row) => ({
    id: row.id,
    projectId: row.project_id,
    cardId: row.card_id,
    relativePath: row.relative_path,
    sha256: row.sha256,
    size: Number(row.size),
    originalName: row.original_name,
    createdAt: row.created_at
  }));
}

/** 资源相对路径安全检查：POSIX 风格、落在 resources/ 下、无绝对/穿越/盘符/反斜杠。 */
function isSafeRelativePath(value: unknown): value is string {
  if (typeof value !== "string" || !value.trim()) return false;
  if (value.length > 500) return false;
  if (!value.startsWith("resources/")) return false;
  if (path.isAbsolute(value)) return false;
  if (value.includes("..") || value.includes("\\") || value.includes(":") || value.includes("\0")) return false;
  return true;
}

async function sha256FileStreaming(filePath: string): Promise<string> {
  const hash = createHash("sha256");
  await pipeline(createReadStream(filePath), hash);
  return hash.digest("hex");
}

/**
 * 递归遍历 resources/ 下的普通文件（相对路径以 resources/ 开头）。
 * 忽略点前缀条目（临时目录 .bundle-import-* / .creation-bundle-* 等），
 * 不穿越符号链接目录（避免越界 / 死循环）。symlink/junction 普通文件由调用方单独判定。
 */
async function walkResourceFiles(
  root: string,
  relative: string,
  out: string[],
  operation?: OperationController
): Promise<void> {
  let entries;
  try {
    entries = await readdir(path.join(root, relative), { withFileTypes: true });
  } catch {
    return;
  }
  for (const entry of entries) {
    operation?.throwIfCancelled();
    if (entry.name.startsWith(".")) continue;
    const child = relative ? `${relative}/${entry.name}` : entry.name;
    if (entry.isSymbolicLink()) continue;
    if (entry.isDirectory()) {
      await walkResourceFiles(root, child, out, operation);
    } else if (entry.isFile()) {
      out.push(`resources/${child}`);
    }
  }
}

export interface ScanResourceOptions {
  /** 工作区根目录（含 workspace.sqlite 与 resources/ 子目录）。 */
  workspaceDirectory: string;
  /** 可选 operation 控制器：scanning 阶段可中断。 */
  operation?: OperationController;
}

/**
 * 执行只读一致性扫描。自行打开一个只读 SQLite 连接（WAL 下与写连接可共存），
 * 读取记录、遍历文件树、比对，返回稳定问题清单。不修改任何数据。
 */
export async function scanResourceConsistencyCore(options: ScanResourceOptions): Promise<ResourceScanResult> {
  const { workspaceDirectory, operation } = options;
  operation?.setPhase("scanning");
  const issues: ResourceIssue[] = [];
  const resourcesDir = path.join(workspaceDirectory, "resources");

  // 1) 只读连接读取记录（DB 缺失时按空工作区处理，文件仍会被标记为未引用）。
  let records: ResourceRecord[] = [];
  const dbPath = path.join(workspaceDirectory, "workspace.sqlite");
  let db: Database | undefined;
  if (existsSync(dbPath)) {
    try {
      db = new Database(dbPath, { readonly: true });
      records = readResourceRecords(db);
    } finally {
      if (db) {
        try {
          db.close();
        } catch {
          // 只读连接关闭失败不影响扫描结论。
        }
      }
    }
  }

  // 2) 记录级重复 / 冲突检测（不触及文件）。
  const pathToIds = new Map<string, string[]>();
  const seenIds = new Set<string>();
  for (const rec of records) {
    operation?.throwIfCancelled();
    if (seenIds.has(rec.id)) {
      issues.push({ type: "record-conflict", resourceId: rec.id, relativePath: rec.relativePath, message: "资源记录 ID 重复，存在数据库冲突。" });
    } else {
      seenIds.add(rec.id);
    }
    const ids = pathToIds.get(rec.relativePath) ?? [];
    ids.push(rec.id);
    pathToIds.set(rec.relativePath, ids);
  }
  for (const [relativePath, ids] of pathToIds) {
    if (ids.length > 1) {
      for (const id of ids) {
        issues.push({ type: "duplicate-path", resourceId: id, relativePath, message: "同一相对路径存在多条资源记录。" });
      }
    }
  }

  // 3) 逐记录核对文件存在性 / 大小 / 哈希 / 越界。
  const referencedFiles = new Set<string>();
  for (const rec of records) {
    operation?.throwIfCancelled();
    if (!isSafeRelativePath(rec.relativePath)) {
      issues.push({
        type: "unsafe-relative-path",
        resourceId: rec.id,
        relativePath: rec.relativePath,
        message: "资源相对路径不安全（绝对路径 / 路径穿越 / 越界 / 未落在 resources/ 下）。"
      });
      continue;
    }
    const filePath = path.join(workspaceDirectory, rec.relativePath);
    let info;
    try {
      info = await lstat(filePath);
    } catch {
      issues.push({ type: "file-missing", resourceId: rec.id, relativePath: rec.relativePath, message: "资源记录存在但对应文件缺失。" });
      continue;
    }
    if (info.isSymbolicLink() || !info.isFile()) {
      const reason = info.isDirectory() ? "资源记录指向目录而非文件" : "资源文件为符号链接或非普通文件";
      issues.push({ type: "symlink-escape", resourceId: rec.id, relativePath: rec.relativePath, message: `${reason}，真实路径可能越界工作区。` });
      continue;
    }
    // junction / reparse point 真实路径越界检测。
    try {
      const real = await realpath(filePath);
      const rel = path.relative(workspaceDirectory, real);
      if (rel === "" || rel.startsWith("..") || path.isAbsolute(rel)) {
        issues.push({ type: "symlink-escape", resourceId: rec.id, relativePath: rec.relativePath, message: "资源文件真实路径越界工作区。" });
        continue;
      }
    } catch {
      issues.push({ type: "file-missing", resourceId: rec.id, relativePath: rec.relativePath, message: "资源文件真实路径不可解析。" });
      continue;
    }
    const sha = await sha256FileStreaming(filePath);
    if (info.size !== rec.size) {
      issues.push({
        type: "size-mismatch",
        resourceId: rec.id,
        relativePath: rec.relativePath,
        message: `文件大小(${info.size})与记录(${rec.size})不一致。`
      });
    } else if (sha !== String(rec.sha256).toLowerCase()) {
      issues.push({ type: "hash-mismatch", resourceId: rec.id, relativePath: rec.relativePath, message: "文件 SHA-256 与记录不一致。" });
    }
    referencedFiles.add(rec.relativePath);
  }

  // 4) 遍历文件树，标记未被任何记录引用的文件（忽略点前缀临时目录）。
  const walkedFiles: string[] = [];
  await walkResourceFiles(resourcesDir, "", walkedFiles, operation);
  for (const relativePath of walkedFiles) {
    operation?.throwIfCancelled();
    referencedFiles.add(relativePath);
    if (!pathToIds.has(relativePath)) {
      issues.push({ type: "file-unreferenced", relativePath, message: "资源文件存在但无数据库记录引用。" });
    }
  }

  return {
    issues,
    scannedRecordCount: records.length,
    scannedFileCount: walkedFiles.length
  };
}
