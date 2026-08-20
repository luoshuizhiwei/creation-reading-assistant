import { createHash, randomUUID } from "node:crypto";
import { copyFile, lstat, mkdir, readFile, readdir, realpath, rename, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { OperationCancelledError } from "../operation";
import type { OperationController } from "../operation";
import type {
  ProjectBundleData,
  ProjectBundleImportCommand,
  ProjectBundleImportResult,
  ProjectBundleResourceFile
} from "../../../src/types/creation";

/** 项目包文件层错误：code 用于测试断言（invalid-input / missing / not-found / integrity）。 */
export class ProjectBundleError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "ProjectBundleError";
    this.code = code;
  }
}

export interface ProjectBundleManifestFile {
  path: string;
  sha256: string;
  size: number;
}

export interface ProjectBundleManifest {
  /** v1 不含批注；v2 含批注（导入端兼容两者）。 */
  formatVersion: 1 | 2;
  projectTitle: string;
  exportedAt: string;
  counts: ProjectBundleData["counts"];
  files: ProjectBundleManifestFile[];
}

export interface ExportProjectBundleOptions {
  /** 工作区根目录（<dataRoot>/CreationWorkspace）。 */
  workspaceDirectory: string;
  /** workspace.read({ kind: "project.bundle.export" }) 的产物。 */
  data: ProjectBundleData;
  /** 用户选定的目标父目录。 */
  targetDirectory: string;
  /** 长任务协调器控制器：在可中断阶段检查取消并上报进度。 */
  operation?: OperationController;
}

export interface ImportProjectBundleOptions {
  workspaceDirectory: string;
  bundleDirectory: string;
  /** 真正的 DB 导入命令（协调器 withWorkspace 包裹）。 */
  transact: (command: ProjectBundleImportCommand) => Promise<ProjectBundleImportResult>;
  /** 长任务协调器控制器：在可中断阶段检查取消并上报进度。 */
  operation?: OperationController;
}

const MAX_RESOURCE_SIZE = 500 * 1024 * 1024;

function fail(code: string, message: string): never {
  throw new ProjectBundleError(code, message);
}

function sha256Buffer(buffer: Buffer): string {
  return createHash("sha256").update(buffer).digest("hex");
}

async function sha256File(filePath: string): Promise<string> {
  return sha256Buffer(await readFile(filePath));
}

/** 工作区相对路径（resources/**）安全检查：POSIX 风格、无绝对/穿越/盘符/反斜杠。 */
function isSafeRelativePath(value: unknown): value is string {
  if (typeof value !== "string" || !value.trim()) return false;
  if (value.length > 500) return false;
  if (path.isAbsolute(value)) return false;
  if (value.includes("..") || value.includes("\\") || value.includes(":") || value.includes("\0")) return false;
  return true;
}

function stripResourcesPrefix(relativePath: string): string {
  return relativePath.startsWith("resources/") ? relativePath.slice("resources/".length) : relativePath;
}

function assertPathInside(root: string, target: string): void {
  const relative = path.relative(root, target);
  if (relative === "" || relative.startsWith("..") || path.isAbsolute(relative)) {
    fail("invalid-input", "项目包路径越界。");
  }
}

async function assertRegularFileNoLink(filePath: string, root: string, operation?: OperationController): Promise<{ size: number; sha256: string }> {
  operation?.throwIfCancelled();
  let info;
  try {
    info = await lstat(filePath);
  } catch {
    fail("missing", "项目包文件缺失。");
  }
  if (info.isSymbolicLink() || !info.isFile()) {
    fail("invalid-input", "项目包拒绝符号链接或非普通文件。");
  }
  let realFile: string;
  let realRoot: string;
  try {
    realFile = await realpath(filePath);
    realRoot = await realpath(root);
  } catch {
    fail("missing", "项目包文件无法解析真实路径。");
  }
  assertPathInside(realRoot, realFile);
  return { size: info.size, sha256: await sha256File(filePath) };
}

function validateManifestFileEntry(entry: unknown, seen: Set<string>): ProjectBundleManifestFile {
  if (typeof entry !== "object" || entry === null) fail("invalid-input", "项目包清单文件项无效。");
  const record = entry as Record<string, unknown>;
  const filePath = record.path;
  if (typeof filePath !== "string" || !filePath) fail("invalid-input", "项目包清单文件路径无效。");
  const normalized = filePath.replace(/\\/g, "/");
  const isProjectJson = normalized === "project.json";
  const isResource = normalized.startsWith("resources/");
  if (!isProjectJson && !isResource) fail("invalid-input", "项目包清单包含不允许的文件。");
  if (!isProjectJson) {
    const rest = normalized.slice("resources/".length);
    if (!isSafeRelativePath(rest) || rest === "") fail("invalid-input", "项目包清单包含不安全路径。");
  }
  if (seen.has(normalized)) fail("invalid-input", "项目包清单包含重复文件路径。");
  seen.add(normalized);
  const sha256 = record.sha256;
  if (typeof sha256 !== "string" || !/^[0-9a-f]{64}$/i.test(sha256)) fail("invalid-input", "项目包清单校验和不合法。");
  const size = record.size;
  if (!Number.isInteger(size) || Number(size) < 0 || Number(size) > MAX_RESOURCE_SIZE) {
    fail("invalid-input", "项目包清单大小无效。");
  }
  return { path: normalized, sha256: sha256.toLowerCase(), size: Number(size) };
}

/** 递归遍历（拒绝符号链接/junction），返回目录内全部普通文件相对路径（POSIX）。 */
async function walkRegularFiles(root: string, relative: string, out: string[], operation?: OperationController): Promise<void> {
  let entries;
  try {
    entries = await readdir(path.join(root, relative), { withFileTypes: true });
  } catch {
    return;
  }
  for (const entry of entries) {
    operation?.throwIfCancelled();
    if (entry.isSymbolicLink()) fail("invalid-input", "项目包包含符号链接目录。");
    const childRelative = relative ? `${relative}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      await walkRegularFiles(root, childRelative, out, operation);
    } else if (entry.isFile()) {
      out.push(childRelative);
    } else {
      fail("invalid-input", "项目包包含非普通文件。");
    }
  }
}

function sanitizeTitle(title: string): string {
  const cleaned = title
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, "")
    .replace(/[. ]+$/g, "")
    .trim()
    .slice(0, 60);
  return cleaned || "项目";
}

function safeBasename(value: string): string {
  const base = value.split("/").pop() ?? "";
  const cleaned = base
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, "")
    .replace(/[. ]+$/g, "")
    .trim()
    .slice(0, 120);
  return cleaned || "attachment";
}

async function removeQuiet(target: string): Promise<void> {
  try {
    await rm(target, { recursive: true, force: true });
  } catch {
    // 尽力清理；不掩盖原始错误。
  }
}

async function pathExists(target: string): Promise<boolean> {
  try {
    await lstat(target);
    return true;
  } catch {
    return false;
  }
}

async function removeQuietNonRecursive(target: string): Promise<void> {
  try {
    await rm(target, { force: true });
  } catch {
    // 目录非空或已不存在时忽略。
  }
}

/**
 * 导出项目包目录（staging → 原子 rename 到唯一最终目录）。
 * 源附件缺失/哈希不符时拒绝，不产生“成功”包。
 */
export async function exportProjectBundleDirectory(options: ExportProjectBundleOptions): Promise<{ directory: string; manifest: ProjectBundleManifest }> {
  const { workspaceDirectory, data, targetDirectory, operation } = options;
  operation?.setPhase("validating");
  if (!data || typeof data !== "object" || (data.formatVersion !== 1 && data.formatVersion !== 2)) {
    fail("invalid-input", "项目包数据无效。");
  }
  const resources = Array.isArray(data.resources) ? data.resources : [];
  const resourceFiles: ProjectBundleManifestFile[] = [];
  operation?.setPhase("hashing");
  for (const resource of resources) {
    operation?.throwIfCancelled();
    if (!isSafeRelativePath(resource.relativePath) || !resource.relativePath.startsWith("resources/")) {
      fail("invalid-input", "项目包附件路径无效（禁止绝对路径/穿越）。");
    }
    if (typeof resource.sha256 !== "string" || !/^[0-9a-f]{64}$/i.test(resource.sha256)) {
      fail("invalid-input", "项目包附件校验和不合法。");
    }
    if (!Number.isInteger(resource.size) || Number(resource.size) < 0 || Number(resource.size) > MAX_RESOURCE_SIZE) {
      fail("invalid-input", "项目包附件大小超出允许范围。");
    }
    const sourcePath = path.join(workspaceDirectory, resource.relativePath);
    assertPathInside(workspaceDirectory, sourcePath);
    const source = await assertRegularFileNoLink(sourcePath, workspaceDirectory, operation);
    if (source.size !== Number(resource.size)) fail("integrity", "附件大小与登记不一致，拒绝导出。");
    if (source.sha256 !== resource.sha256.toLowerCase()) fail("integrity", "附件哈希与登记不一致，拒绝导出。");
    resourceFiles.push({
      path: `resources/${stripResourcesPrefix(resource.relativePath)}`,
      sha256: resource.sha256.toLowerCase(),
      size: Number(resource.size)
    });
  }

  const projectJson = `${JSON.stringify(data, null, 2)}\n`;
  const projectJsonBuffer = Buffer.from(projectJson, "utf8");
  const manifest: ProjectBundleManifest = {
    formatVersion: 2,
    projectTitle: data.project.title,
    exportedAt: data.exportedAt,
    counts: data.counts,
    files: [{ path: "project.json", sha256: sha256Buffer(projectJsonBuffer), size: projectJsonBuffer.length }, ...resourceFiles]
  };

  const staging = path.join(targetDirectory, `.creation-bundle-${randomUUID()}`);
  operation?.setPhase("copying");
  try {
    await mkdir(staging, { recursive: true });
    await writeFile(path.join(staging, "project.json"), projectJsonBuffer);
    await writeFile(path.join(staging, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
    for (const file of resourceFiles) {
      operation?.throwIfCancelled();
      const destination = path.join(staging, file.path);
      await mkdir(path.dirname(destination), { recursive: true });
      await copyFile(path.join(workspaceDirectory, "resources", stripResourcesPrefix(file.path)), destination);
      const copied = await assertRegularFileNoLink(destination, staging, operation);
      if (copied.size !== file.size || copied.sha256 !== file.sha256) {
        fail("integrity", "导出附件校验不一致。");
      }
    }
    const baseName = `项目包-${sanitizeTitle(data.project.title)}`;
    let finalName = baseName;
    let suffix = 2;
    for (let attempts = 0; attempts < 1000; attempts += 1) {
      if (!(await pathExists(path.join(targetDirectory, finalName)))) {
        break;
      }
      finalName = `${baseName}-${suffix}`;
      suffix += 1;
    }
    const finalDirectory = path.join(targetDirectory, finalName);
    operation?.setPhase("committing");
    await rename(staging, finalDirectory);
    return { directory: finalDirectory, manifest };
  } catch (error) {
    await removeQuiet(staging);
    if (error instanceof OperationCancelledError) throw error;
    if (error instanceof ProjectBundleError) throw error;
    if (error instanceof Error) {
      const code = (error as NodeJS.ErrnoException).code;
      if (code === "ENOENT" || code === "ENOTDIR") fail("missing", "导出源附件缺失。");
      fail("integrity", `无法完成项目包导出：${error.message}`);
    }
    throw error;
  }
}

/**
 * 校验并导入项目包目录。全部校验通过后：staging 复制 → 原子 rename 到唯一最终路径 → DB 事务；
 * DB 失败统一删除已落盘文件，保证零残留。
 */
export async function importProjectBundleDirectory(options: ImportProjectBundleOptions): Promise<ProjectBundleImportResult> {
  const { workspaceDirectory, bundleDirectory, transact, operation } = options;
  operation?.setPhase("validating");
  let bundleRootInfo;
  try {
    bundleRootInfo = await lstat(bundleDirectory);
  } catch {
    fail("missing", "项目包目录不存在。");
  }
  if (!bundleRootInfo.isDirectory() || bundleRootInfo.isSymbolicLink()) {
    fail("invalid-input", "项目包目录无效。");
  }

  const seenManifestPaths = new Set<string>();
  let manifest: ProjectBundleManifest;
  try {
    const parsed = JSON.parse(await readFile(path.join(bundleDirectory, "manifest.json"), "utf8")) as unknown;
    const parsedVersion = (parsed as Record<string, unknown>).formatVersion;
    if (typeof parsed !== "object" || parsed === null || (parsedVersion !== 1 && parsedVersion !== 2)) {
      fail("invalid-input", "项目包清单格式无效或版本不受支持。");
    }
    const files = (parsed as Record<string, unknown>).files;
    if (!Array.isArray(files)) fail("invalid-input", "项目包清单缺少文件列表。");
    manifest = {
      formatVersion: parsedVersion as 1 | 2,
      projectTitle: typeof (parsed as Record<string, unknown>).projectTitle === "string" ? (parsed as Record<string, unknown>).projectTitle as string : "",
      exportedAt: typeof (parsed as Record<string, unknown>).exportedAt === "string" ? (parsed as Record<string, unknown>).exportedAt as string : "",
      counts: (parsed as Record<string, unknown>).counts as ProjectBundleData["counts"],
      files: files.map((entry) => validateManifestFileEntry(entry, seenManifestPaths))
    };
  } catch (error) {
    if (error instanceof ProjectBundleError) throw error;
    fail("invalid-input", "项目包清单无法解析。");
  }

  // validating 阶段：仅做纯数据校验（清单条目、project.json、附件引用一致性），不触发文件哈希 IO。
  const manifestFileSet = new Map<string, ProjectBundleManifestFile>();
  for (const file of manifest.files) {
    manifestFileSet.set(file.path, file);
  }

  let data: ProjectBundleData;
  try {
    data = JSON.parse(await readFile(path.join(bundleDirectory, "project.json"), "utf8")) as ProjectBundleData;
  } catch {
    fail("invalid-input", "项目包 project.json 无法解析。");
  }
  if (!data || typeof data !== "object" || (data.formatVersion !== 1 && data.formatVersion !== 2)) {
    fail("invalid-input", "项目包格式无效或版本不受支持。");
  }
  if (manifest.formatVersion !== data.formatVersion) {
    fail("invalid-input", "项目包清单版本与项目数据版本不一致。");
  }
  const annotationEntries = (data as unknown as Record<string, unknown>).annotations;
  if (data.formatVersion === 2 && !Array.isArray(annotationEntries)) {
    fail("invalid-input", "v2 项目包缺少批注数据。");
  }
  const annotationCount = Array.isArray(annotationEntries) ? annotationEntries.length : 0;
  if (data.formatVersion === 2) {
    const dataCounts = (data as unknown as Record<string, unknown>).counts as Record<string, unknown> | undefined;
    const manifestCounts = manifest.counts as unknown as Record<string, unknown> | undefined;
    if (dataCounts?.annotations !== annotationCount || manifestCounts?.annotations !== annotationCount) {
      fail("invalid-input", "项目包批注数量与清单不一致。");
    }
  }

  const resources = Array.isArray(data.resources) ? data.resources : [];
  const resourceBundlePaths = new Set<string>();
  for (const resource of resources) {
    operation?.throwIfCancelled();
    if (!isSafeRelativePath(resource.relativePath) || !resource.relativePath.startsWith("resources/")) {
      fail("invalid-input", "项目包附件路径无效（禁止绝对路径/穿越）。");
    }
    if (typeof resource.sha256 !== "string" || !/^[0-9a-f]{64}$/i.test(resource.sha256)) {
      fail("invalid-input", "项目包附件校验和不合法。");
    }
    if (!Number.isInteger(resource.size) || Number(resource.size) < 0 || Number(resource.size) > MAX_RESOURCE_SIZE) {
      fail("invalid-input", "项目包附件大小超出允许范围。");
    }
    const bundlePath = `resources/${stripResourcesPrefix(resource.relativePath)}`;
    const listed = manifestFileSet.get(bundlePath);
    if (!listed) fail("missing", `项目包附件缺失：${bundlePath}`);
    if (listed.sha256 !== resource.sha256.toLowerCase() || listed.size !== Number(resource.size)) {
      fail("integrity", `项目包附件与元数据不一致：${bundlePath}`);
    }
    resourceBundlePaths.add(bundlePath);
  }

  // 清单中的附件必须全部被数据引用；resources/** 下不得有多余文件。
  for (const filePath of manifestFileSet.keys()) {
    if (filePath.startsWith("resources/") && !resourceBundlePaths.has(filePath)) {
      fail("invalid-input", `项目包包含多余附件：${filePath}`);
    }
  }

  // scanning 阶段：遍历真实文件树，拒绝未登记文件与多余顶层文件（只读扫描，零写入）。
  // 必须在 hashing 之前（协调器阶段顺序：validating → scanning → hashing → copying …）。
  operation?.setPhase("scanning");
  const walked: string[] = [];
  await walkRegularFiles(bundleDirectory, "resources", walked, operation);
  for (const filePath of walked) {
    operation?.throwIfCancelled();
    if (!manifestFileSet.has(filePath)) fail("invalid-input", `项目包包含未登记的文件：${filePath}`);
  }
  // 顶层只允许 project.json / manifest.json / resources。
  const topLevel = await readdir(bundleDirectory, { withFileTypes: true });
  for (const entry of topLevel) {
    operation?.throwIfCancelled();
    if (entry.name === "project.json" || entry.name === "manifest.json" || entry.name === "resources") continue;
    fail("invalid-input", `项目包包含多余文件：${entry.name}`);
  }

  // hashing 阶段：逐文件重算 SHA-256 / 大小，与清单比对（可中断）。
  operation?.setPhase("hashing");
  for (const file of manifest.files) {
    operation?.throwIfCancelled();
    const filePath = path.join(bundleDirectory, file.path);
    assertPathInside(bundleDirectory, filePath);
    const verified = await assertRegularFileNoLink(filePath, bundleDirectory, operation);
    if (verified.size !== file.size || verified.sha256 !== file.sha256) {
      fail("integrity", `项目包文件 ${file.path} 校验不一致。`);
    }
  }

  const targetProjectId = `project-${randomUUID()}`;
  const resourceFiles: ProjectBundleResourceFile[] = resources.map((resource) => ({
    relativePath: resource.relativePath,
    targetRelativePath: `resources/${targetProjectId}/${randomUUID()}-${safeBasename(resource.originalName ?? resource.relativePath)}`,
    sha256: resource.sha256.toLowerCase(),
    size: Number(resource.size)
  }));

  const staging = path.join(workspaceDirectory, `.bundle-import-${randomUUID()}`);
  const placedFinalFiles: string[] = [];
  const projectResourceDirectory = path.join(workspaceDirectory, `resources/${targetProjectId}`);

  const cleanupPlacedFiles = async (): Promise<void> => {
    for (const finalPath of placedFinalFiles) {
      await removeQuiet(finalPath);
    }
    await removeQuietNonRecursive(projectResourceDirectory);
    await removeQuiet(staging);
  };

  operation?.setPhase("copying");
  try {
    await mkdir(staging, { recursive: true });
    for (const file of resourceFiles) {
      operation?.throwIfCancelled();
      const sourcePath = path.join(bundleDirectory, "resources", stripResourcesPrefix(file.relativePath));
      const stagedPath = path.join(staging, file.targetRelativePath);
      await mkdir(path.dirname(stagedPath), { recursive: true });
      await copyFile(sourcePath, stagedPath);
      const staged = await assertRegularFileNoLink(stagedPath, staging, operation);
      if (staged.size !== file.size || staged.sha256 !== file.sha256) {
        fail("integrity", "导入附件校验不一致。");
      }
      const finalPath = path.join(workspaceDirectory, file.targetRelativePath);
      assertPathInside(workspaceDirectory, finalPath);
      await mkdir(path.dirname(finalPath), { recursive: true });
      await rename(stagedPath, finalPath);
      placedFinalFiles.push(finalPath);
    }
  } catch (error) {
    await cleanupPlacedFiles();
    if (error instanceof OperationCancelledError) throw error;
    if (error instanceof ProjectBundleError) throw error;
    if (error instanceof Error) {
      const code = (error as NodeJS.ErrnoException).code;
      if (code === "ENOENT" || code === "ENOTDIR") fail("missing", "项目包附件缺失。");
      fail("integrity", `无法导入项目包：${error.message}`);
    }
    throw error;
  }

  // database / committing 为不可中断阶段：进入后即便收到取消也先完成安全提交，结束再标记 deferredCancel。
  operation?.setPhase("database");
  let result: ProjectBundleImportResult;
  try {
    result = await transact({
      type: "project.bundle.import",
      data,
      targetProjectId,
      resourceFiles
    });
  } catch (error) {
    await cleanupPlacedFiles();
    if (error instanceof OperationCancelledError) throw error;
    throw error;
  }
  operation?.setPhase("committing");
  await removeQuiet(staging);
  return result;
}
