import { strict as assert } from "node:assert";
import { createHash, randomUUID } from "node:crypto";
import { mkdtemp, mkdir, readFile, readdir, rm, symlink, writeFile, lstat } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { openCreationWorkspace, scanResourceConsistencyCore, type CreationWorkspace } from "../creation-workspace";
import {
  exportProjectBundleDirectory,
  importProjectBundleDirectory,
  ProjectBundleError
} from "./index";
import {
  assertV9AttachmentFiles,
  seedV9Baseline,
  V9_ATTACHMENT_A_CONTENT,
  V9_BASELINE,
  type V9Baseline
} from "../creation-workspace/v9-baseline-fixture";
import type { ProjectBundleData, ProjectBundleImportCommand, ProjectBundleImportResult } from "../../../src/types/creation";

async function withWorkspace<T>(directory: string, fn: (workspace: CreationWorkspace) => Promise<T>): Promise<T> {
  const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
  try {
    return await fn(workspace);
  } finally {
    await workspace.close();
  }
}

function sha256Buffer(buffer: Buffer): string {
  return createHash("sha256").update(buffer).digest("hex");
}

async function sha256File(filePath: string): Promise<string> {
  return sha256Buffer(await readFile(filePath));
}

async function listRelativeFiles(root: string, relative = ""): Promise<string[]> {
  const out: string[] = [];
  let entries;
  try {
    entries = await readdir(path.join(root, relative), { withFileTypes: true });
  } catch {
    return out;
  }
  for (const entry of entries) {
    const child = relative ? `${relative}/${entry.name}` : entry.name;
    if (entry.isDirectory()) out.push(...await listRelativeFiles(root, child));
    else if (entry.isFile()) out.push(child);
  }
  return out;
}

/** 手工构建一个项目包目录，返回 { directory, data, resourceContent }。 */
async function buildBundle(
  parent: string,
  overrides: {
    data?: Partial<ProjectBundleData>;
    resourceRelativePath?: string;
    resourceContent?: Buffer;
    manifestExtraFiles?: Array<{ path: string; sha256: string; size: number }>;
  } = {}
): Promise<{ directory: string; data: ProjectBundleData; resourcePath: string }> {
  const directory = path.join(parent, `bundle-${randomUUID()}`);
  await mkdir(directory, { recursive: true });
  const resourceRelativePath = overrides.resourceRelativePath ?? `resources/project-src/uuid-file.pdf`;
  const resourceContent = overrides.resourceContent ?? Buffer.from("附件内容-2026", "utf8");
  const baseData: ProjectBundleData = {
    formatVersion: 1,
    project: {
      id: "project-src",
      title: "测试项目",
      setup: { template: "long-form", weeklyUpdateDays: [5], chapterWorkflow: ["规划", "待写"] },
      createdAt: "2026-01-01T00:00:00.000Z",
      updatedAt: "2026-01-01T00:00:00.000Z",
      revision: 1
    },
    volumes: [{ id: "volume-1", title: "第一卷", sortOrder: 0, createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:00.000Z", revision: 1 }],
    chapters: [
      {
        id: "chapter-1",
        volumeId: "volume-1",
        title: "第一章",
        sortOrder: 0,
        status: "待写",
        numberingKind: "auto",
        customNumber: null,
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
        revision: 1
      }
    ],
    scenes: [
      {
        id: "scene-1",
        chapterId: "chapter-1",
        title: "开篇",
        sortOrder: 0,
        bodyJson: '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"导入正文。"}]}]}',
        planningJson: "{}",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
        revision: 1
      }
    ],
    cardTypes: [],
    relationTypes: [],
    cards: [],
    relations: [],
    snapshots: [],
    resources: [
      {
        id: "resource-1",
        cardId: null,
        relativePath: resourceRelativePath,
        sha256: sha256Buffer(resourceContent),
        size: resourceContent.length,
        originalName: "材料.pdf",
        createdAt: "2026-01-01T00:00:00.000Z"
      }
    ],
    annotations: [],
    counts: { volumes: 1, chapters: 1, scenes: 1, cards: 0, relations: 0, snapshots: 0, resources: 1, annotations: 0 },
    exportedAt: "2026-01-01T00:00:00.000Z"
  };
  const data = { ...baseData, ...overrides.data, project: { ...baseData.project, ...overrides.data?.project } } as ProjectBundleData;
  if (overrides.data?.volumes !== undefined) data.volumes = overrides.data.volumes as ProjectBundleData["volumes"];
  if (overrides.data?.resources !== undefined) data.resources = overrides.data.resources as ProjectBundleData["resources"];
  if (overrides.data?.annotations !== undefined) data.annotations = overrides.data.annotations as ProjectBundleData["annotations"];
  const projectJson = `${JSON.stringify(data, null, 2)}\n`;
  const projectJsonBuffer = Buffer.from(projectJson, "utf8");
  const bundleResourcePath = `resources/${resourceRelativePath.replace(/^resources\//, "")}`;
  await mkdir(path.dirname(path.join(directory, bundleResourcePath)), { recursive: true });
  await writeFile(path.join(directory, bundleResourcePath), resourceContent);
  const manifest = {
    formatVersion: data.formatVersion,
    projectTitle: data.project.title,
    exportedAt: data.exportedAt,
    counts: data.counts,
    files: [
      { path: "project.json", sha256: sha256Buffer(projectJsonBuffer), size: projectJsonBuffer.length },
      { path: bundleResourcePath, sha256: sha256Buffer(resourceContent), size: resourceContent.length },
      ...(overrides.manifestExtraFiles ?? [])
    ]
  };
  await writeFile(path.join(directory, "project.json"), projectJsonBuffer);
  await writeFile(path.join(directory, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  return { directory, data, resourcePath: bundleResourcePath };
}

async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-bundle-files-"));
  let tests = 0;
  try {
    const scenario = async (name: string, fn: () => Promise<void>): Promise<void> => {
      try {
        await fn();
        tests += 1;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.stack ?? error.message : String(error)}`);
      }
    };

    let exportedDirectory = "";
    let exportedData: ProjectBundleData | undefined;
    let sourceWorkspaceDir = "";
    let sourceCardId = "";
    let sourceResourceRel = "";
    let sourceResourceSha = "";
    let globalResourceRel = "";
    let globalResourceSha = "";
    let unrelatedGlobalSha = "";
    let traversalTransact: (command: ProjectBundleImportCommand) => Promise<ProjectBundleImportResult>;
    /** v9 双项目基线（M0.2）：导出目录与基线 id，供后续导入场景复用。 */
    let v9BundleDirectory = "";
    let v9Baseline: V9Baseline | undefined;

    await scenario("带附件导出：staging→唯一目录、manifest 哈希一致", async () => {
      sourceWorkspaceDir = path.join(parent, "source-ws");
      await withWorkspace(sourceWorkspaceDir, async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "测试项目", setup: { template: "long-form", weeklyUpdateDays: [5], chapterWorkflow: ["规划", "待写"] } }) as { projectId: string };
        const card = await workspace.transact({ type: "card.create", projectId: created.projectId, kind: "character", title: "苏青" }) as { entityId: string };
        sourceCardId = card.entityId;
        const content = Buffer.from("PDF-二进制-材料-内容-20260814", "utf8");
        sourceResourceSha = sha256Buffer(content);
        sourceResourceRel = `resources/${created.projectId}/${randomUUID()}-材料.pdf`;
        await mkdir(path.dirname(path.join(sourceWorkspaceDir, sourceResourceRel)), { recursive: true });
        await writeFile(path.join(sourceWorkspaceDir, sourceResourceRel), content);
        await workspace.transact({
          type: "resource.attach",
          projectId: created.projectId,
          cardId: card.entityId,
          relativePath: sourceResourceRel,
          sha256: sourceResourceSha,
          size: content.length,
          originalName: "材料.pdf"
        });
        const globalContent = Buffer.from("全局卡片封面-20260911", "utf8");
        globalResourceSha = sha256Buffer(globalContent);
        globalResourceRel = `resources/cards/${card.entityId}/${randomUUID()}-封面.png`;
        await mkdir(path.dirname(path.join(sourceWorkspaceDir, globalResourceRel)), { recursive: true });
        await writeFile(path.join(sourceWorkspaceDir, globalResourceRel), globalContent);
        await workspace.transact({
          type: "resource.attach",
          cardId: card.entityId,
          role: "cover",
          relativePath: globalResourceRel,
          sha256: globalResourceSha,
          size: globalContent.length,
          originalName: "封面.png"
        });
        const unrelatedProject = await workspace.transact({ type: "project.create", title: "无关项目" });
        const unrelatedCard = await workspace.transact({ type: "card.create", projectId: unrelatedProject.projectId, kind: "character", title: "无关角色" }) as { entityId: string };
        const unrelatedContent = Buffer.from("不得进入目标项目包", "utf8");
        unrelatedGlobalSha = sha256Buffer(unrelatedContent);
        const unrelatedPath = `resources/cards/${unrelatedCard.entityId}/${randomUUID()}-private.bin`;
        await mkdir(path.dirname(path.join(sourceWorkspaceDir, unrelatedPath)), { recursive: true });
        await writeFile(path.join(sourceWorkspaceDir, unrelatedPath), unrelatedContent);
        await workspace.transact({ type: "resource.attach", cardId: unrelatedCard.entityId, relativePath: unrelatedPath, sha256: unrelatedGlobalSha, size: unrelatedContent.length });
        exportedData = (await workspace.read({ kind: "project.bundle.export", projectId: created.projectId }))!;
        assert.equal(exportedData.resources.length, 2);
        assert.equal(exportedData.resources.some((resource) => resource.ownerScope === "card"), true);
        assert.equal(JSON.stringify(exportedData).includes(unrelatedGlobalSha), false, "项目包不得携带未关联卡片的全局资产");
      });
      const exportParent = path.join(parent, "exports");
      await mkdir(exportParent, { recursive: true });
      const result = await exportProjectBundleDirectory({
        workspaceDirectory: sourceWorkspaceDir,
        data: exportedData!,
        targetDirectory: exportParent
      });
      exportedDirectory = result.directory;
      const bundleFiles = (await listRelativeFiles(exportedDirectory)).sort();
      assert.equal(bundleFiles.includes("manifest.json"), true);
      assert.equal(bundleFiles.includes("project.json"), true);
      const bundleResourcePath = `resources/${sourceResourceRel.replace(/^resources\//, "")}`;
      const globalBundleResourcePath = `resources/${globalResourceRel.replace(/^resources\//, "")}`;
      assert.equal(bundleFiles.includes(bundleResourcePath), true);
      assert.equal(bundleFiles.includes(globalBundleResourcePath), true);
      assert.equal(result.manifest.files.length, 3);
      assert.equal(result.manifest.formatVersion, 2);
      assert.equal(exportedData!.formatVersion, 2);
      assert.equal(exportedData!.annotations.length, 0);
      assert.equal(exportedData!.counts.annotations, 0);
      const projectJsonOnDisk = await readFile(path.join(exportedDirectory, "project.json"));
      assert.equal(result.manifest.files.find((file) => file.path === "project.json")!.sha256, sha256Buffer(projectJsonOnDisk));
      assert.equal(result.manifest.files.find((file) => file.path === bundleResourcePath)!.sha256, await sha256File(path.join(exportedDirectory, bundleResourcePath)));
    });

    await scenario("全新工作区导入：表/文件/哈希一致、新项目 ID 重映射", async () => {
      const targetDir = path.join(parent, "target-ws");
      await withWorkspace(targetDir, async (workspace) => {
        const result = await importProjectBundleDirectory({
          workspaceDirectory: targetDir,
          bundleDirectory: exportedDirectory,
          transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
        });
        assert.equal(result.counts.resources, 2);
        assert.equal(result.projectId.startsWith("project-"), true);
        const resources = (await workspace.read({ kind: "resource.list", projectId: result.projectId })) as Array<{
          relativePath: string;
          sha256: string;
          size: number;
          cardId: string | null;
          originalName: string | null;
          ownerScope?: "project" | "card";
          role?: "attachment" | "cover";
        }>;
        assert.equal(resources.length, 2);
        const projectResource = resources.find((resource) => resource.ownerScope === "project")!;
        const globalResource = resources.find((resource) => resource.ownerScope === "card")!;
        assert.equal(projectResource.relativePath.startsWith(`resources/${result.projectId}/`), true);
        assert.equal(projectResource.sha256, sourceResourceSha);
        assert.equal(projectResource.cardId, sourceCardId);
        assert.equal(projectResource.originalName, "材料.pdf");
        assert.equal(globalResource.relativePath.startsWith(`resources/cards/${sourceCardId}/`), true);
        assert.equal(globalResource.sha256, globalResourceSha);
        assert.equal(globalResource.originalName, "封面.png");
        assert.equal(globalResource.role, "cover");
        const onDiskPath = path.join(targetDir, projectResource.relativePath);
        const info = await lstat(onDiskPath);
        assert.equal(info.isFile(), true);
        assert.equal(info.size, projectResource.size);
        assert.equal(await sha256File(onDiskPath), sourceResourceSha);
        assert.equal(await sha256File(path.join(targetDir, globalResource.relativePath)), globalResourceSha);
        const integrity = await workspace.check();
        assert.equal(integrity.ok, true);
      });
    });

    await scenario("稳定 ID 复用：本机相同全局附件不重复复制，项目附件仍正常导入", async () => {
      const targetDir = path.join(parent, "target-card-reuse-files");
      await withWorkspace(targetDir, async (workspace) => {
        const seedData: ProjectBundleData = {
          ...structuredClone(exportedData!),
          project: { ...exportedData!.project, id: "project-local-reuse-seed" },
          resources: [],
          counts: { ...exportedData!.counts, resources: 0 }
        };
        await workspace.transact({ type: "project.bundle.import", data: seedData });
        const localGlobalPath = `resources/cards/${sourceCardId}/local-cover.png`;
        const globalBytes = await readFile(path.join(sourceWorkspaceDir, globalResourceRel));
        await mkdir(path.dirname(path.join(targetDir, localGlobalPath)), { recursive: true });
        await writeFile(path.join(targetDir, localGlobalPath), globalBytes);
        await workspace.transact({
          type: "resource.attach",
          cardId: sourceCardId,
          role: "cover",
          relativePath: localGlobalPath,
          sha256: globalResourceSha,
          size: globalBytes.length,
          originalName: "封面.png"
        });

        const result = await importProjectBundleDirectory({
          workspaceDirectory: targetDir,
          bundleDirectory: exportedDirectory,
          cardResolutions: [{ cardId: sourceCardId, action: "reuse" }],
          transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
        });
        assert.equal(result.cardMappings.find((item) => item.sourceCardId === sourceCardId)?.action, "reused");
        assert.equal(result.counts.resources, 1, "复用卡片的全局附件不应重复入库，只新增项目附件");
        const resources = await workspace.read({ kind: "resource.list", projectId: result.projectId });
        const globalResources = resources.filter((resource) => resource.ownerScope === "card");
        assert.equal(globalResources.length, 1);
        assert.equal(globalResources[0]!.relativePath, localGlobalPath);
        assert.equal(await sha256File(path.join(targetDir, localGlobalPath)), globalResourceSha);
        const allFiles = await listRelativeFiles(targetDir);
        assert.equal(allFiles.filter((file) => file.startsWith(`resources/cards/${sourceCardId}/`)).length, 1);
      });
    });

    await scenario("稳定 ID 导入副本：全局附件复制到新卡目录，原卡与原文件不变", async () => {
      const targetDir = path.join(parent, "target-card-copy-files");
      await withWorkspace(targetDir, async (workspace) => {
        const seedData: ProjectBundleData = {
          ...structuredClone(exportedData!),
          project: { ...exportedData!.project, id: "project-local-copy-seed" },
          resources: [],
          counts: { ...exportedData!.counts, resources: 0 }
        };
        await workspace.transact({ type: "project.bundle.import", data: seedData });
        await workspace.transact({ type: "card.update", cardId: sourceCardId, title: "本机苏青", baseRevision: 1 });
        const copiedCardId = "card-file-layer-import-copy";
        const result = await importProjectBundleDirectory({
          workspaceDirectory: targetDir,
          bundleDirectory: exportedDirectory,
          cardResolutions: [{ cardId: sourceCardId, action: "import-copy", targetCardId: copiedCardId }],
          transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
        });
        assert.equal(result.cardMappings.find((item) => item.sourceCardId === sourceCardId)?.targetCardId, copiedCardId);
        assert.equal((await workspace.read({ kind: "card.read", cardId: sourceCardId }))?.title, "本机苏青");
        assert.equal((await workspace.read({ kind: "card.read", cardId: copiedCardId }))?.title, "苏青");
        const resources = await workspace.read({ kind: "resource.list", projectId: result.projectId });
        const copiedGlobal = resources.find((resource) => resource.ownerScope === "card" && resource.cardId === copiedCardId)!;
        assert.equal(copiedGlobal.relativePath.startsWith(`resources/cards/${copiedCardId}/`), true);
        assert.equal(await sha256File(path.join(targetDir, copiedGlobal.relativePath)), globalResourceSha);
        assert.equal((await listRelativeFiles(targetDir)).some((file) => file.startsWith(`resources/cards/${sourceCardId}/`)), false);
      });
    });

    await scenario("同名目标导出采用唯一目录名，不覆盖", async () => {
      const exportParent = path.join(parent, "exports-dup");
      await mkdir(exportParent, { recursive: true });
      const first = await exportProjectBundleDirectory({
        workspaceDirectory: sourceWorkspaceDir,
        data: exportedData!,
        targetDirectory: exportParent
      });
      const second = await exportProjectBundleDirectory({
        workspaceDirectory: sourceWorkspaceDir,
        data: exportedData!,
        targetDirectory: exportParent
      });
      assert.notEqual(first.directory, second.directory);
      assert.equal((await listRelativeFiles(first.directory)).includes("project.json"), true);
      assert.equal((await listRelativeFiles(second.directory)).includes("project.json"), true);
    });

    await scenario("导出源附件缺失拒绝，不产生包", async () => {
      const exportParent = path.join(parent, "exports-missing");
      await mkdir(exportParent, { recursive: true });
      await rm(path.join(sourceWorkspaceDir, sourceResourceRel), { force: true });
      let error: unknown;
      try {
        await exportProjectBundleDirectory({
          workspaceDirectory: sourceWorkspaceDir,
          data: exportedData!,
          targetDirectory: exportParent
        });
      } catch (caught) {
        error = caught;
      }
      assert.equal(error instanceof ProjectBundleError, true);
      assert.equal((error as ProjectBundleError).code, "missing");
      assert.equal((await listRelativeFiles(exportParent)).length, 0);
      // 恢复源文件供后续场景使用
      await writeFile(path.join(sourceWorkspaceDir, sourceResourceRel), Buffer.from("PDF-二进制-材料-内容-20260814", "utf8"));
    });

    await scenario("导出源附件哈希不符拒绝", async () => {
      const exportParent = path.join(parent, "exports-hash");
      await mkdir(exportParent, { recursive: true });
      await writeFile(path.join(sourceWorkspaceDir, sourceResourceRel), Buffer.from("被篡改的内容", "utf8"));
      let error: unknown;
      try {
        await exportProjectBundleDirectory({
          workspaceDirectory: sourceWorkspaceDir,
          data: exportedData!,
          targetDirectory: exportParent
        });
      } catch (caught) {
        error = caught;
      }
      assert.equal((error as ProjectBundleError).code, "integrity");
      assert.equal((await listRelativeFiles(exportParent)).length, 0);
      await writeFile(path.join(sourceWorkspaceDir, sourceResourceRel), Buffer.from("PDF-二进制-材料-内容-20260814", "utf8"));
    });

    await scenario("包内坏哈希拒绝且零残留", async () => {
      const built = await buildBundle(parent);
      await writeFile(path.join(built.directory, built.resourcePath), Buffer.from("篡改后的附件", "utf8"));
      const targetDir = path.join(parent, "target-bad-hash");
      await withWorkspace(targetDir, async (workspace) => {
        let error: unknown;
        try {
          await importProjectBundleDirectory({
            workspaceDirectory: targetDir,
            bundleDirectory: built.directory,
            transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
          });
        } catch (caught) {
          error = caught;
        }
        assert.equal(error instanceof ProjectBundleError, true);
        assert.equal((error as ProjectBundleError).code, "integrity");
        const projects = (await workspace.read({ kind: "projects.list" })) as Array<{ id: string }>;
        assert.equal(projects.length, 0);
        const files = await listRelativeFiles(targetDir);
        assert.equal(files.some((file) => file.startsWith("resources/")), false);
        assert.equal(files.some((file) => file.startsWith(".bundle-import-")), false);
      });
    });

    await scenario("包内附件缺失拒绝且零残留", async () => {
      const built = await buildBundle(parent);
      await rm(path.join(built.directory, built.resourcePath), { force: true });
      const targetDir = path.join(parent, "target-missing");
      await withWorkspace(targetDir, async (workspace) => {
        let error: unknown;
        try {
          await importProjectBundleDirectory({
            workspaceDirectory: targetDir,
            bundleDirectory: built.directory,
            transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
          });
        } catch (caught) {
          error = caught;
        }
        assert.equal((error as ProjectBundleError).code, "missing");
        const projects = (await workspace.read({ kind: "projects.list" })) as Array<{ id: string }>;
        assert.equal(projects.length, 0);
      });
    });

    await scenario("包内额外附件拒绝", async () => {
      const built = await buildBundle(parent);
      await writeFile(path.join(built.directory, "resources", "extra-未登记.pdf"), Buffer.from("多余附件", "utf8"));
      const targetDir = path.join(parent, "target-extra");
      await withWorkspace(targetDir, async (workspace) => {
        let error: unknown;
        try {
          await importProjectBundleDirectory({
            workspaceDirectory: targetDir,
            bundleDirectory: built.directory,
            transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
          });
        } catch (caught) {
          error = caught;
        }
        assert.equal((error as ProjectBundleError).code, "invalid-input");
        assert.equal(((await workspace.read({ kind: "projects.list" })) as Array<{ id: string }>).length, 0);
      });
    });

    await scenario("清单路径穿越（.. 与绝对路径）拒绝", async () => {
      const traversalParent = path.join(parent, "traversal");
      await mkdir(traversalParent, { recursive: true });
      const data: ProjectBundleData = {
        formatVersion: 1,
        project: { id: "project-src", title: "测试项目", setup: { template: "long-form", weeklyUpdateDays: [5], chapterWorkflow: ["规划", "待写"] }, createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:00.000Z", revision: 1 },
        volumes: [{ id: "volume-1", title: "第一卷", sortOrder: 0, createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:00.000Z", revision: 1 }],
        chapters: [],
        scenes: [],
        cardTypes: [],
        relationTypes: [],
        cards: [],
        relations: [],
        snapshots: [],
        resources: [],
        annotations: [],
        counts: { volumes: 1, chapters: 0, scenes: 0, cards: 0, relations: 0, snapshots: 0, resources: 0, annotations: 0 },
        exportedAt: "2026-01-01T00:00:00.000Z"
      };
      const projectJson = `${JSON.stringify(data, null, 2)}\n`;
      const evil = Buffer.from("越界文件", "utf8");
      const directory = path.join(traversalParent, `bundle-${randomUUID()}`);
      await mkdir(directory, { recursive: true });
      await writeFile(path.join(directory, "project.json"), projectJson);
      await writeFile(path.join(directory, "evil.txt"), evil);
      const manifest = {
        formatVersion: 1,
        projectTitle: data.project.title,
        exportedAt: data.exportedAt,
        counts: data.counts,
        files: [
          { path: "project.json", sha256: sha256Buffer(Buffer.from(projectJson, "utf8")), size: Buffer.byteLength(projectJson) },
          { path: "resources/../evil.txt", sha256: sha256Buffer(evil), size: evil.length }
        ]
      };
      await writeFile(path.join(directory, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
      const targetDir = path.join(parent, "target-traversal");
      await withWorkspace(targetDir, async (workspace) => {
        traversalTransact = (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>;
        let error: unknown;
        try {
          await importProjectBundleDirectory({
            workspaceDirectory: targetDir,
            bundleDirectory: directory,
            transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
          });
        } catch (caught) {
          error = caught;
        }
        assert.equal((error as ProjectBundleError).code, "invalid-input");
        assert.equal(((await workspace.read({ kind: "projects.list" })) as Array<{ id: string }>).length, 0);
      });

      const absoluteDirectory = path.join(traversalParent, `bundle-${randomUUID()}`);
      await mkdir(absoluteDirectory, { recursive: true });
      await writeFile(path.join(absoluteDirectory, "project.json"), projectJson);
      await writeFile(path.join(absoluteDirectory, "evil.txt"), evil);
      const absoluteManifest = {
        formatVersion: 1,
        projectTitle: data.project.title,
        exportedAt: data.exportedAt,
        counts: data.counts,
        files: [
          { path: "project.json", sha256: sha256Buffer(Buffer.from(projectJson, "utf8")), size: Buffer.byteLength(projectJson) },
          { path: "C:/evil.txt", sha256: sha256Buffer(evil), size: evil.length }
        ]
      };
      await writeFile(path.join(absoluteDirectory, "manifest.json"), `${JSON.stringify(absoluteManifest, null, 2)}\n`, "utf8");
      let absoluteError: unknown;
      try {
        await importProjectBundleDirectory({
          workspaceDirectory: targetDir,
          bundleDirectory: absoluteDirectory,
          transact: traversalTransact
        });
      } catch (caught) {
        absoluteError = caught;
      }
      assert.equal((absoluteError as ProjectBundleError).code, "invalid-input");
    });

    await scenario("junction 符号链接拒绝且零残留", async () => {
      const built = await buildBundle(parent);
      const outside = path.join(parent, `outside-${randomUUID()}`);
      await mkdir(outside, { recursive: true });
      await writeFile(path.join(outside, "secret.txt"), Buffer.from("秘密", "utf8"));
      await symlink(outside, path.join(built.directory, "resources", "junction-link"), "junction");
      const targetDir = path.join(parent, "target-junction");
      await withWorkspace(targetDir, async (workspace) => {
        let error: unknown;
        try {
          await importProjectBundleDirectory({
            workspaceDirectory: targetDir,
            bundleDirectory: built.directory,
            transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
          });
        } catch (caught) {
          error = caught;
        }
        assert.equal((error as ProjectBundleError).code, "invalid-input");
        const projects = (await workspace.read({ kind: "projects.list" })) as Array<{ id: string }>;
        assert.equal(projects.length, 0);
        const files = await listRelativeFiles(targetDir);
        assert.equal(files.some((file) => file.startsWith("resources/")), false);
        assert.equal(files.some((file) => file.startsWith(".bundle-import-")), false);
      });
    });

    await scenario("v2 项目包：批注与关联卡完整往返", async () => {
      const sourceDir = path.join(parent, "source-ws-anno");
      let exportedV2: ProjectBundleData | undefined;
      let sourceAnnotationId = "";
      let sourceInvalidAnnotationId = "";
      await withWorkspace(sourceDir, async (workspace) => {
        const created = (await workspace.transact({
          type: "project.create",
          title: "批注项目",
          setup: { template: "long-form", weeklyUpdateDays: [5], chapterWorkflow: ["规划", "待写"] }
        })) as { projectId: string; sceneId: string };
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 1,
          body: {
            type: "doc",
            content: [{ type: "paragraph", content: [{ type: "text", text: "雾都的雨夜。" }] }]
          }
        });
        const card = (await workspace.transact({
          type: "card.create",
          projectId: created.projectId,
          kind: "character",
          title: "林晚"
        })) as { entityId: string };
        const annotation = (await workspace.transact({
          type: "annotation.create",
          projectId: created.projectId,
          sceneId: created.sceneId,
          cardId: card.entityId,
          anchor: { blockIndex: 0, textOffset: 0, textLength: 2, text: "雾都" },
          note: "开头氛围待打磨",
          status: "open"
        })) as { annotationId: string };
        sourceAnnotationId = annotation.annotationId;
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 2,
          body: {
            type: "doc",
            content: [{ type: "paragraph", content: [{ type: "text", text: "雨夜的雾都。" }] }]
          }
        });
        const validAnnotation = (await workspace.transact({
          type: "annotation.create",
          projectId: created.projectId,
          sceneId: created.sceneId,
          anchor: { blockIndex: 0, textOffset: 3, textLength: 2, text: "雾都" },
          note: "有效锚点",
          status: "open"
        })) as { annotationId: string };
        sourceInvalidAnnotationId = annotation.annotationId;
        assert.notEqual(validAnnotation.annotationId, sourceInvalidAnnotationId);
        exportedV2 = (await workspace.read({ kind: "project.bundle.export", projectId: created.projectId }))!;
        assert.equal(exportedV2.formatVersion, 2);
        assert.equal(exportedV2.annotations.length, 2);
        assert.equal(exportedV2.annotations.find((item) => item.id === sourceAnnotationId)!.cardId, card.entityId);
        assert.equal(exportedV2.annotations.every((item) => item.anchor.textLength === 2), true);
        assert.equal(exportedV2.counts.annotations, 2);
      });
      const exportParent = path.join(parent, "exports-anno");
      await mkdir(exportParent, { recursive: true });
      const bundleResult = await exportProjectBundleDirectory({
        workspaceDirectory: sourceDir,
        data: exportedV2!,
        targetDirectory: exportParent
      });
      assert.equal(bundleResult.manifest.formatVersion, 2);
      assert.equal(bundleResult.manifest.counts.annotations, 2);
      const targetDir = path.join(parent, "target-anno");
      await withWorkspace(targetDir, async (workspace) => {
        const result = await importProjectBundleDirectory({
          workspaceDirectory: targetDir,
          bundleDirectory: bundleResult.directory,
          transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
        });
        assert.equal(result.counts.annotations, 2);
        const annotations = (await workspace.read({ kind: "annotation.list", projectId: result.projectId })) as Array<{
          id: string;
          sceneId: string;
          cardId: string | null;
          note: string;
          status: string;
          anchorInvalid: boolean;
          anchor: { blockIndex: number; textOffset: number; textLength: number; text?: string };
        }>;
        assert.equal(annotations.length, 2);
        const importedInvalid = annotations.find((item) => item.id === sourceInvalidAnnotationId)!;
        assert.equal(importedInvalid.note, "开头氛围待打磨");
        assert.equal(importedInvalid.status, "open");
        assert.equal(importedInvalid.anchor.blockIndex, 0);
        assert.equal(importedInvalid.anchor.textLength, 2);
        assert.equal(importedInvalid.anchor.text, "雾都");
        assert.equal(importedInvalid.anchorInvalid, true);
        assert.notEqual(importedInvalid.cardId, null);
        assert.notEqual(importedInvalid.sceneId, "");
        const importedValid = annotations.find((item) => item.note === "有效锚点")!;
        assert.equal(importedValid.anchorInvalid, false);
        const integrity = await workspace.check();
        assert.equal(integrity.ok, true);
      });
    });

    await scenario("v2 项目包：批注坏引用拒绝且零写入", async () => {
      const built = await buildBundle(parent, {
        data: {
          formatVersion: 2,
          annotations: [
            {
              id: "annotation-bad",
              sceneId: "scene-missing",
              cardId: null,
              anchor: { blockIndex: 0, textOffset: 0, textLength: 2, text: "导入" },
              note: "坏引用",
              status: "open",
              revision: 1,
              createdAt: "2026-01-01T00:00:00.000Z",
              updatedAt: "2026-01-01T00:00:00.000Z"
            }
          ],
          counts: { volumes: 1, chapters: 1, scenes: 1, cards: 0, relations: 0, snapshots: 0, resources: 1, annotations: 1 }
        }
      });
      const targetDir = path.join(parent, "target-bad-anno");
      await withWorkspace(targetDir, async (workspace) => {
        let error: unknown;
        try {
          await importProjectBundleDirectory({
            workspaceDirectory: targetDir,
            bundleDirectory: built.directory,
            transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
          });
        } catch (caught) {
          error = caught;
        }
        assert.equal((error as { code?: string }).code, "invalid-input");
        assert.equal(((await workspace.read({ kind: "projects.list" })) as Array<{ id: string }>).length, 0);
        const files = await listRelativeFiles(targetDir);
        assert.equal(files.some((file) => file.startsWith("resources/")), false);
        assert.equal(files.some((file) => file.startsWith(".bundle-import-")), false);
      });
    });

    await scenario("v2 项目包：批注跨项目卡片引用拒绝", async () => {
      const built = await buildBundle(parent, {
        data: {
          formatVersion: 2,
          annotations: [
            {
              id: "annotation-ghost",
              sceneId: "scene-1",
              cardId: "card-ghost",
              anchor: { blockIndex: 0, textOffset: 0, textLength: 2, text: "导入" },
              note: "跨项目引用",
              status: "open",
              revision: 1,
              createdAt: "2026-01-01T00:00:00.000Z",
              updatedAt: "2026-01-01T00:00:00.000Z"
            }
          ],
          counts: { volumes: 1, chapters: 1, scenes: 1, cards: 0, relations: 0, snapshots: 0, resources: 1, annotations: 1 }
        }
      });
      const targetDir = path.join(parent, "target-ghost-anno");
      await withWorkspace(targetDir, async (workspace) => {
        let error: unknown;
        try {
          await importProjectBundleDirectory({
            workspaceDirectory: targetDir,
            bundleDirectory: built.directory,
            transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
          });
        } catch (caught) {
          error = caught;
        }
        assert.equal((error as { code?: string }).code, "invalid-input");
        assert.equal(((await workspace.read({ kind: "projects.list" })) as Array<{ id: string }>).length, 0);
      });
    });

    await scenario("v2 项目包：重复批注 ID 与损坏锚点拒绝", async () => {
      const duplicate = await buildBundle(parent, {
        data: {
          formatVersion: 2,
          annotations: [
            {
              id: "annotation-x",
              sceneId: "scene-1",
              cardId: null,
              anchor: { blockIndex: 0, textOffset: 0, textLength: 2, text: "导入" },
              note: "一",
              status: "open",
              revision: 1,
              createdAt: "2026-01-01T00:00:00.000Z",
              updatedAt: "2026-01-01T00:00:00.000Z"
            },
            {
              id: "annotation-x",
              sceneId: "scene-1",
              cardId: null,
              anchor: { blockIndex: 0, textOffset: 0, textLength: 2, text: "导入" },
              note: "二",
              status: "open",
              revision: 1,
              createdAt: "2026-01-01T00:00:00.000Z",
              updatedAt: "2026-01-01T00:00:00.000Z"
            }
          ],
          counts: { volumes: 1, chapters: 1, scenes: 1, cards: 0, relations: 0, snapshots: 0, resources: 1, annotations: 2 }
        }
      });
      const targetDir = path.join(parent, "target-dup-anno");
      await withWorkspace(targetDir, async (workspace) => {
        let error: unknown;
        try {
          await importProjectBundleDirectory({
            workspaceDirectory: targetDir,
            bundleDirectory: duplicate.directory,
            transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
          });
        } catch (caught) {
          error = caught;
        }
        assert.equal((error as { code?: string }).code, "invalid-input");

        const brokenAnchor = await buildBundle(parent, {
          data: {
            formatVersion: 2,
            annotations: [
              {
                id: "annotation-y",
                sceneId: "scene-1",
                cardId: null,
                anchor: { blockIndex: -1, textOffset: 0, textLength: 2, text: "导入" },
                note: "锚点损坏",
                status: "open",
                revision: 1,
                createdAt: "2026-01-01T00:00:00.000Z",
                updatedAt: "2026-01-01T00:00:00.000Z"
              }
            ],
            counts: { volumes: 1, chapters: 1, scenes: 1, cards: 0, relations: 0, snapshots: 0, resources: 1, annotations: 1 }
          }
        });
        let anchorError: unknown;
        try {
          await importProjectBundleDirectory({
            workspaceDirectory: targetDir,
            bundleDirectory: brokenAnchor.directory,
            transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
          });
        } catch (caught) {
          anchorError = caught;
        }
        assert.equal((anchorError as { code?: string }).code, "invalid-input");
        assert.equal(((await workspace.read({ kind: "projects.list" })) as Array<{ id: string }>).length, 0);
      });
    });

    await scenario("v1 项目包（无批注）仍可导入", async () => {
      const built = await buildBundle(parent);
      assert.equal(built.data.formatVersion, 1);
      const targetDir = path.join(parent, "target-v1-compat");
      await withWorkspace(targetDir, async (workspace) => {
        const result = await importProjectBundleDirectory({
          workspaceDirectory: targetDir,
          bundleDirectory: built.directory,
          transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
        });
        assert.equal(result.counts.annotations, 0);
        const annotations = (await workspace.read({ kind: "annotation.list", projectId: result.projectId })) as unknown[];
        assert.equal(annotations.length, 0);
        const integrity = await workspace.check();
        assert.equal(integrity.ok, true);
      });
    });

    await scenario("v2 批注数量与 project/manifest counts 不一致时拒绝", async () => {
      const built = await buildBundle(parent, {
        data: {
          formatVersion: 2,
          annotations: [],
          counts: { volumes: 1, chapters: 1, scenes: 1, cards: 0, relations: 0, snapshots: 0, resources: 1, annotations: 1 }
        }
      });
      let error: unknown;
      try {
        await importProjectBundleDirectory({
          workspaceDirectory: path.join(parent, "target-v2-count-mismatch"),
          bundleDirectory: built.directory,
          transact: async () => {
            throw new Error("数量校验应先于数据库事务。");
          }
        });
      } catch (caught) {
        error = caught;
      }
      assert.equal(error instanceof ProjectBundleError, true);
      assert.equal((error as ProjectBundleError).code, "invalid-input");
    });

    await scenario("DB 事务失败：已落盘文件全部清理，零半导入", async () => {
      const built = await buildBundle(parent, {
        data: { volumes: [] as never, counts: { volumes: 0, chapters: 0, scenes: 0, cards: 0, relations: 0, snapshots: 0, resources: 1 } as never }
      });
      const targetDir = path.join(parent, "target-rollback");
      await withWorkspace(targetDir, async (workspace) => {
        let error: unknown;
        try {
          await importProjectBundleDirectory({
            workspaceDirectory: targetDir,
            bundleDirectory: built.directory,
            transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
          });
        } catch (caught) {
          error = caught;
        }
        assert.equal((error as { code?: string }).code, "invalid-input");
        const projects = (await workspace.read({ kind: "projects.list" })) as Array<{ id: string }>;
        assert.equal(projects.length, 0);
        const files = await listRelativeFiles(targetDir);
        assert.equal(files.some((file) => file.startsWith("resources/")), false);
        assert.equal(files.some((file) => file.startsWith(".bundle-import-")), false);
      });
    });

    await scenario("v9 基线（真实附件）：导出 A 仅含 A 的附件文件，B 的文件绝不进入导出目录", async () => {
      const sourceWorkspace = path.join(parent, "v9-source-ws");
      let exportedA: ProjectBundleData | undefined;
      await withWorkspace(sourceWorkspace, async (workspace) => {
        // A / B 各自写入确定性真实附件字节（resources/<projectId>/ 下）。
        v9Baseline = await seedV9Baseline(workspace, { workspaceDirectory: sourceWorkspace });
        exportedA = (await workspace.read({ kind: "project.bundle.export", projectId: v9Baseline.projectA }))!;
      });
      const baseline = v9Baseline!;

      // 1) workspace 包数据只含 A 的资源元数据，不含 B 的任何标识。
      assert.equal(exportedA!.counts.resources, 1);
      assert.equal(exportedA!.resources.length, 1);
      assert.equal(exportedA!.resources[0]!.relativePath, baseline.resourceRelativePath);
      assert.equal(exportedA!.resources[0]!.sha256, baseline.resourceSha256);
      assert.equal(exportedA!.resources[0]!.size, baseline.resourceSize);
      const exportedJson = JSON.stringify(exportedA);
      assert.equal(exportedJson.includes(baseline.resourceBRelativePath), false, "包数据不得含 B 的附件路径");
      assert.equal(exportedJson.includes(baseline.resourceBSha256), false, "包数据不得含 B 的内容标识");
      assert.equal(exportedJson.includes(baseline.resourceBId), false, "包数据不得含 B 的附件 ID");

      // 2) 文件层导出：目录内只出现 A 的那一个附件文件。
      const exportParent = path.join(parent, "exports-v9");
      await mkdir(exportParent, { recursive: true });
      const result = await exportProjectBundleDirectory({
        workspaceDirectory: sourceWorkspace,
        data: exportedA!,
        targetDirectory: exportParent
      });
      v9BundleDirectory = result.directory;

      const files = (await listRelativeFiles(v9BundleDirectory)).sort();
      const expectedResourcePath = `resources/${baseline.resourceRelativePath.replace(/^resources\//, "")}`;
      assert.deepEqual(
        files.filter((file) => !file.startsWith("resources/")),
        ["manifest.json", "project.json"]
      );
      assert.deepEqual(files.filter((file) => file.startsWith("resources/")), [expectedResourcePath]);
      assert.equal(files.some((file) => file.includes("baseline-ref-b")), false, "B 的附件文件不得进入 A 的导出目录");
      assert.equal(files.some((file) => file.includes(baseline.projectB)), false, "导出目录不得出现 B 的项目 ID");

      // 3) manifest 中 A 附件的 size / sha256 与源文件实际字节一致。
      const manifestEntry = result.manifest.files.find((file) => file.path === expectedResourcePath);
      assert.equal(manifestEntry !== undefined, true, "manifest 必须列出 A 的附件");
      assert.equal(manifestEntry!.sha256, baseline.resourceSha256);
      assert.equal(manifestEntry!.size, baseline.resourceSize);
      const exportedResourceFile = path.join(v9BundleDirectory, expectedResourcePath);
      assert.equal((await lstat(exportedResourceFile)).size, baseline.resourceSize);
      assert.equal(await sha256File(exportedResourceFile), baseline.resourceSha256);
      // 导出出来的字节必须就是 A 的原始内容
      assert.deepEqual(await readFile(exportedResourceFile), Buffer.from(V9_ATTACHMENT_A_CONTENT, "utf8"));

      // 4) 导出目录内的 project.json 不得含 B 的项目 ID / 附件路径 / 内容标识。
      const projectJsonText = await readFile(path.join(v9BundleDirectory, "project.json"), "utf8");
      for (const marker of [
        baseline.projectB,
        baseline.resourceBRelativePath,
        baseline.resourceBSha256,
        baseline.resourceBId
      ]) {
        assert.equal(projectJsonText.includes(marker), false, `导出包 project.json 不得含 B 的标识：${marker}`);
      }

      // 5) 导出流程不得破坏源工作区里 A / B 两份附件。
      await assertV9AttachmentFiles(sourceWorkspace, baseline);
    });

    await scenario("v9 基线（真实附件）：导入全新工作区后 A 的字节/哈希与资源记录正确、check 与扫描均通过", async () => {
      const baseline = v9Baseline!;
      const targetWorkspace = path.join(parent, "v9-target-ws");
      await withWorkspace(targetWorkspace, async (workspace) => {
        const result = await importProjectBundleDirectory({
          workspaceDirectory: targetWorkspace,
          bundleDirectory: v9BundleDirectory,
          transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
        });
        assert.equal(result.counts.resources, 1, "导入应只带 A 的那一份附件");
        assert.notEqual(result.projectId, baseline.projectA, "导入应生成新的项目 ID");

        const resources = (await workspace.read({ kind: "resource.list", projectId: result.projectId })) as Array<{
          relativePath: string;
          sha256: string;
          size: number;
          cardId: string | null;
          originalName: string | null;
        }>;
        assert.equal(resources.length, 1);
        assert.equal(resources[0]!.relativePath.startsWith(`resources/${result.projectId}/`), true);
        assert.equal(resources[0]!.sha256, baseline.resourceSha256, "导入后资源记录哈希必须与 A 一致");
        assert.equal(resources[0]!.size, baseline.resourceSize);
        assert.equal(resources[0]!.originalName, V9_BASELINE.resourceOriginalName);

        // 文件实体：存在、大小与字节哈希与源 A 附件完全一致。
        const onDisk = path.join(targetWorkspace, resources[0]!.relativePath);
        const info = await lstat(onDisk);
        assert.equal(info.isFile(), true);
        assert.equal(info.size, baseline.resourceSize);
        assert.equal(await sha256File(onDisk), baseline.resourceSha256);
        assert.deepEqual(await readFile(onDisk), Buffer.from(V9_ATTACHMENT_A_CONTENT, "utf8"));

        // B 的附件（文件与记录）绝不进入新工作区。
        const resourceFiles = (await listRelativeFiles(targetWorkspace)).filter((file) => file.startsWith("resources/"));
        assert.equal(resourceFiles.length, 1, "新工作区 resources/ 下只应有 A 的那一个附件文件");
        assert.equal(resourceFiles.some((file) => file.includes("baseline-ref-b")), false);
        assert.equal(resourceFiles.some((file) => file.includes(baseline.projectB)), false);

        const integrity = await workspace.check();
        assert.equal(integrity.ok, true, "导入后 workspace.check() 必须通过");
      });

      // 只读资源扫描：导入后的记录 ↔ 文件必须一致（零问题）。
      const scan = await scanResourceConsistencyCore({ workspaceDirectory: targetWorkspace });
      assert.equal(scan.issues.length, 0, `导入后应零问题，实际：${JSON.stringify(scan.issues)}`);
      assert.equal(scan.scannedRecordCount >= 1, true);
      assert.equal(scan.scannedFileCount >= 1, true);
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await rm(parent, { recursive: true, force: true });
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
