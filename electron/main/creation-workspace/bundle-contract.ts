import { strict as assert } from "node:assert";
import { createHash } from "node:crypto";
import { removeWithRetry } from "./test-utils";
import { mkdtemp } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  openCreationWorkspace,
  type CreationWorkspace,
  type ProjectBundleData,
  type ProjectBundleImportResult
} from "./index";
import { seedV9Baseline } from "./v9-baseline-fixture";

async function withWorkspace<T>(directory: string, fn: (workspace: CreationWorkspace) => Promise<T>): Promise<T> {
  const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
  try {
    return await fn(workspace);
  } finally {
    await workspace.close();
  }
}

function remapBundleCardId(data: ProjectBundleData, sourceCardId: string, targetCardId: string): ProjectBundleData {
  const cloned = structuredClone(data);
  for (const card of cloned.cards) {
    if (card.id === sourceCardId) card.id = targetCardId;
  }
  for (const relation of cloned.relations) {
    if (relation.fromCardId === sourceCardId) relation.fromCardId = targetCardId;
    if (relation.toCardId === sourceCardId) relation.toCardId = targetCardId;
  }
  for (const scene of cloned.scenes) {
    const planning = JSON.parse(scene.planningJson || "{}") as {
      perspectiveCardId?: string | null;
      locationCardId?: string | null;
      castCardIds?: string[] | null;
    };
    if (planning.perspectiveCardId === sourceCardId) planning.perspectiveCardId = targetCardId;
    if (planning.locationCardId === sourceCardId) planning.locationCardId = targetCardId;
    if (Array.isArray(planning.castCardIds)) {
      planning.castCardIds = planning.castCardIds.map((id) => id === sourceCardId ? targetCardId : id);
    }
    scene.planningJson = JSON.stringify(planning);
  }
  for (const resource of cloned.resources) {
    if (resource.cardId === sourceCardId) resource.cardId = targetCardId;
  }
  for (const annotation of cloned.annotations) {
    if (annotation.cardId === sourceCardId) annotation.cardId = targetCardId;
  }
  const remapPayload = (value: unknown): unknown => {
    if (value === sourceCardId) return targetCardId;
    if (Array.isArray(value)) return value.map(remapPayload);
    if (typeof value !== "object" || value === null) return value;
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, remapPayload(item)]));
  };
  for (const snapshot of cloned.snapshots) {
    if (snapshot.subjectId === sourceCardId) snapshot.subjectId = targetCardId;
    snapshot.payloadJson = JSON.stringify(remapPayload(JSON.parse(snapshot.payloadJson || "{}")));
  }
  return cloned;
}

async function seedCollisionCard(
  workspace: CreationWorkspace,
  title: string
): Promise<{ projectId: string; cardId: string }> {
  const project = await workspace.transact({
    type: "project.create",
    title: `本机-${title}`,
    setup: { template: "long-form", weeklyUpdateDays: [5], chapterWorkflow: ["规划", "待写"] }
  }) as { projectId: string };
  const card = await workspace.transact({
    type: "card.create",
    projectId: project.projectId,
    kind: "character",
    title,
    tags: ["主角"]
  }) as { entityId: string };
  return { projectId: project.projectId, cardId: card.entityId };
}

async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-bundle-"));
  let tests = 0;
  try {
    const scenario = async (name: string, fn: () => Promise<void>): Promise<void> => {
      try {
        await fn();
        tests += 1;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    let sourceProjectId = "";
    let sourceSceneId = "";
    let exported: ProjectBundleData | undefined;
    let sourceCardId = "";
    let sourceResourcePath = "";
    let sourceResourceSha = "";
    let exportedWithResource: ProjectBundleData | undefined;

    await scenario("准备源项目：卷章场景 + 卡片关系 + 快照", async () => {
      await withWorkspace(path.join(parent, "source"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "测试项目", setup: { template: "long-form", weeklyUpdateDays: [5], chapterWorkflow: ["规划", "待写"] } });
        sourceProjectId = created.projectId;
        sourceSceneId = created.sceneId;
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sourceSceneId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "项目包正文内容。" }] }] }
        });
        const volume = await workspace.transact({ type: "volume.create", projectId: sourceProjectId, title: "第二卷" }) as { entityId: string };
        await workspace.transact({ type: "chapter.create", projectId: sourceProjectId, volumeId: volume.entityId, title: "第四章" });
        const card = await workspace.transact({ type: "card.create", projectId: sourceProjectId, kind: "character", title: "苏青", tags: ["主角"] }) as { entityId: string };
        const card2 = await workspace.transact({ type: "card.create", projectId: sourceProjectId, kind: "character", title: "顾淮" }) as { entityId: string };
        sourceCardId = card.entityId;
        await workspace.transact({
          type: "scene.updatePlanning",
          sceneId: sourceSceneId,
          planning: { perspectiveCardId: card.entityId, castCardIds: [card.entityId, card2.entityId] }
        });
        await workspace.transact({
          type: "cardRelation.create",
          projectId: sourceProjectId,
          fromCardId: card.entityId,
          toCardId: card2.entityId,
          relationTypeId: "relation-type-character-character",
          note: "旧识"
        });
        await workspace.transact({
          type: "annotation.create",
          projectId: sourceProjectId,
          sceneId: sourceSceneId,
          cardId: card.entityId,
          anchor: { blockIndex: 0, textOffset: 0, textLength: 3 },
          note: "主角伏笔"
        });
        await workspace.transact({ type: "snapshot.create", projectId: sourceProjectId, subjectType: "scene", subjectId: sourceSceneId, reason: "里程碑" });
        exported = (await workspace.read({ kind: "project.bundle.export", projectId: sourceProjectId }))!;
        assert.equal(exported.volumes.length, 2);
        assert.equal(exported.chapters.length, 2);
        assert.equal(exported.scenes.length, 1);
        assert.equal(exported.cards.length, 2);
        assert.equal(exported.relations.length, 1);
        assert.equal(exported.snapshots.length, 2);
        assert.equal(exported.annotations.length, 1);
        assert.equal(exported.project.setup.template, "long-form");
      });
    });

    await scenario("导出包含附件元数据（resources + counts）", async () => {
      await withWorkspace(path.join(parent, "source"), async (workspace) => {
        const content = Buffer.from("材料文件内容-PDF-二进制-12345", "utf8");
        const sha256 = createHash("sha256").update(content).digest("hex");
        sourceResourcePath = `resources/cards/${sourceCardId}/ref-材料.pdf`;
        sourceResourceSha = sha256;
        await workspace.transact({
          type: "resource.attach",
          cardId: sourceCardId,
          relativePath: sourceResourcePath,
          sha256,
          size: content.length,
          originalName: "材料.pdf"
        });
        exportedWithResource = (await workspace.read({ kind: "project.bundle.export", projectId: sourceProjectId }))!;
        assert.equal(exportedWithResource.resources.length, 1);
        assert.equal(exportedWithResource.counts.resources, 1);
        assert.equal(exportedWithResource.resources[0]!.cardId, sourceCardId);
        assert.equal(exportedWithResource.resources[0]!.relativePath, sourceResourcePath);
        assert.equal(exportedWithResource.resources[0]!.sha256, sha256);
        assert.equal(exportedWithResource.resources[0]!.size, content.length);
      });
    });

    await scenario("全新目录导入项目包：正文/关系/快照校验一致", async () => {
      await withWorkspace(path.join(parent, "target"), async (workspace) => {
        const result = (await workspace.transact({
          type: "project.bundle.import",
          data: exported!
        })) as ProjectBundleImportResult;
        assert.equal(result.projectId, sourceProjectId);
        assert.equal(result.counts.volumes, 2);
        assert.equal(result.counts.chapters, 2);
        assert.equal(result.counts.scenes, 1);
        assert.equal(result.counts.cards, 2);
        assert.equal(result.counts.relations, 1);
        assert.equal(result.counts.snapshots, 2);
        const outline = await workspace.read({ kind: "project.outline", projectId: sourceProjectId });
        assert.equal(outline?.volumes.length, 2);
        const body = await workspace.read({ kind: "scene.body", sceneId: sourceSceneId });
        assert.equal(JSON.stringify(body?.body).includes("项目包正文内容"), true);
        const cards = (await workspace.read({ kind: "cards.list", projectId: sourceProjectId })) as Array<{ id: string; title: string }>;
        assert.equal(cards.some((card) => card.title === "苏青"), true);
        const relations = (await workspace.read({ kind: "card.relations", cardId: cards.find((card) => card.title === "苏青")!.id })) as {
          outgoing: Array<{ note: string | null }>;
          incoming: Array<{ note: string | null }>;
        };
        assert.equal(relations.outgoing.some((relation) => relation.note === "旧识"), true);
        const snapshots = (await workspace.read({ kind: "snapshot.list", projectId: sourceProjectId })) as Array<{ reason: string }>;
        assert.equal(snapshots.some((snapshot) => snapshot.reason === "里程碑"), true);
        const integrity = await workspace.check();
        assert.equal(integrity.ok, true);
      });
    });

    await scenario("导入后附件元数据重映射（resourceFiles）", async () => {
      await withWorkspace(path.join(parent, "target-resource"), async (workspace) => {
        const resource = exportedWithResource!.resources[0]!;
        const targetPath = `resources/project-imported-${resource.id}/材料-副本.pdf`;
        const result = (await workspace.transact({
          type: "project.bundle.import",
          data: exportedWithResource!,
          resourceFiles: [
            {
              relativePath: resource.relativePath,
              targetRelativePath: targetPath,
              sha256: resource.sha256,
              size: resource.size
            }
          ]
        })) as ProjectBundleImportResult;
        assert.equal(result.counts.resources, 1);
        const resources = (await workspace.read({ kind: "resource.list", projectId: result.projectId })) as Array<{
          id: string;
          cardId: string | null;
          relativePath: string;
          sha256: string;
          size: number;
          originalName: string | null;
        }>;
        assert.equal(resources.length, 1);
        assert.equal(resources[0]!.relativePath, targetPath);
        assert.equal(resources[0]!.cardId, sourceCardId);
        assert.equal(resources[0]!.sha256, sourceResourceSha);
        assert.equal(resources[0]!.size, resource.size);
        assert.equal(resources[0]!.originalName, "材料.pdf");
      });
    });

    await scenario("DB 层导入（无映射）沿用原相对路径", async () => {
      await withWorkspace(path.join(parent, "target-resource-plain"), async (workspace) => {
        const result = (await workspace.transact({
          type: "project.bundle.import",
          data: exportedWithResource!
        })) as ProjectBundleImportResult;
        assert.equal(result.counts.resources, 1);
        const resources = (await workspace.read({ kind: "resource.list", projectId: result.projectId })) as Array<{ relativePath: string }>;
        assert.equal(resources.length, 1);
        assert.equal(resources[0]!.relativePath, sourceResourcePath);
      });
    });

    await scenario("附件坏引用/重复 ID/映射不一致均被拒绝且零写入", async () => {
      await withWorkspace(path.join(parent, "target-resource-bad"), async (workspace) => {
        const resource = exportedWithResource!.resources[0]!;
        const capture = async (build: () => Promise<unknown>): Promise<string | undefined> => {
          try {
            await build();
            return undefined;
          } catch (error) {
            return (error as { code?: string }).code;
          }
        };

        assert.equal(
          await capture(() => workspace.transact({
            type: "project.bundle.import",
            data: { ...exportedWithResource!, resources: [{ ...resource, cardId: "card-missing" }] }
          })),
          "invalid-input"
        );

        assert.equal(
          await capture(() => workspace.transact({
            type: "project.bundle.import",
            data: {
              ...exportedWithResource!,
              project: { ...exportedWithResource!.project, id: "project-resource-dup-id" },
              resources: [resource, { ...resource, size: resource.size + 1 }]
            }
          })),
          "invalid-input"
        );

        assert.equal(
          await capture(() => workspace.transact({
            type: "project.bundle.import",
            data: {
              ...exportedWithResource!,
              project: { ...exportedWithResource!.project, id: "project-resource-dup-target" },
              resources: [resource, { ...resource, id: "resource-second" }]
            },
            resourceFiles: [
              { relativePath: resource.relativePath, targetRelativePath: "resources/project-x/a.pdf", sha256: resource.sha256, size: resource.size },
              { relativePath: resource.relativePath, targetRelativePath: "resources/project-x/a.pdf", sha256: resource.sha256, size: resource.size }
            ]
          })),
          "invalid-input"
        );

        assert.equal(
          await capture(() => workspace.transact({
            type: "project.bundle.import",
            data: { ...exportedWithResource!, project: { ...exportedWithResource!.project, id: "project-resource-no-map" } },
            resourceFiles: []
          })),
          "invalid-input"
        );

        assert.equal(
          await capture(() => workspace.transact({
            type: "project.bundle.import",
            data: { ...exportedWithResource!, project: { ...exportedWithResource!.project, id: "project-resource-extra-map" } },
            resourceFiles: [
              { relativePath: resource.relativePath, targetRelativePath: "resources/project-x/a.pdf", sha256: resource.sha256, size: resource.size },
              { relativePath: "resources/extra/other.pdf", targetRelativePath: "resources/project-x/b.pdf", sha256: resource.sha256, size: resource.size }
            ]
          })),
          "invalid-input"
        );

        const projects = (await workspace.read({ kind: "projects.list" })) as Array<{ id: string }>;
        assert.equal(projects.length, 0);
      });
    });

    await scenario("稳定 ID 内容相同：预检判定复用，导入只新增项目关联", async () => {
      await withWorkspace(path.join(parent, "target-card-reuse"), async (workspace) => {
        const local = await seedCollisionCard(workspace, "苏青");
        const incoming = remapBundleCardId(exported!, sourceCardId, local.cardId);
        const preview = await workspace.previewProjectBundleImport(incoming);
        assert.deepEqual(preview.identicalCardIds, [local.cardId]);
        assert.equal(preview.conflicts.length, 0);

        const result = await workspace.transact({
          type: "project.bundle.import",
          data: incoming,
          cardResolutions: [{ cardId: local.cardId, action: "reuse" }]
        }) as ProjectBundleImportResult;
        assert.deepEqual(
          result.cardMappings.find((mapping) => mapping.sourceCardId === local.cardId),
          { sourceCardId: local.cardId, targetCardId: local.cardId, action: "reused" }
        );
        const importedCards = await workspace.read({ kind: "cards.list", projectId: result.projectId });
        assert.equal(importedCards.some((card) => card.id === local.cardId), true);
        const localCard = await workspace.read({ kind: "card.read", cardId: local.cardId });
        assert.equal(localCard?.title, "苏青");
        assert.equal(localCard?.linkedProjectIds.includes(local.projectId), true);
        assert.equal(localCard?.linkedProjectIds.includes(result.projectId), true);
      });
    });

    await scenario("卡片正文相同但全局附件不同：仍视为冲突，禁止误判复用丢资产", async () => {
      await withWorkspace(path.join(parent, "target-card-resource-conflict"), async (workspace) => {
        const local = await seedCollisionCard(workspace, "苏青");
        const incoming = remapBundleCardId(exportedWithResource!, sourceCardId, local.cardId);
        const preview = await workspace.previewProjectBundleImport(incoming);
        assert.equal(preview.identicalCardIds.length, 0);
        assert.equal(preview.conflicts.length, 1);
        assert.equal(preview.conflicts[0]!.differingFields.includes("全局附件"), true);
      });
    });

    await scenario("稳定 ID 内容不同且无选择：conflict 且事务零写入", async () => {
      await withWorkspace(path.join(parent, "target-card-conflict-none"), async (workspace) => {
        const local = await seedCollisionCard(workspace, "本机苏青");
        const incoming = remapBundleCardId(exported!, sourceCardId, local.cardId);
        const preview = await workspace.previewProjectBundleImport(incoming);
        assert.equal(preview.identicalCardIds.length, 0);
        assert.equal(preview.conflicts.length, 1);
        assert.equal(preview.conflicts[0]!.differingFields.includes("名称"), true);
        const beforeProjects = await workspace.read({ kind: "projects.list" });
        let error: unknown;
        try {
          await workspace.transact({ type: "project.bundle.import", data: incoming });
        } catch (caught) {
          error = caught;
        }
        assert.equal((error as { code?: string }).code, "conflict");
        const afterProjects = await workspace.read({ kind: "projects.list" });
        assert.equal(afterProjects.length, beforeProjects.length);
        assert.equal((await workspace.read({ kind: "card.read", cardId: local.cardId }))?.title, "本机苏青");
      });
    });

    await scenario("稳定 ID 内容不同选择保留本机：项目、场景、关系、批注全部指向本机卡", async () => {
      await withWorkspace(path.join(parent, "target-card-keep-local"), async (workspace) => {
        const local = await seedCollisionCard(workspace, "本机苏青");
        const incoming = remapBundleCardId(exportedWithResource!, sourceCardId, local.cardId);
        const resource = incoming.resources.find((item) => item.cardId === local.cardId)!;
        const result = await workspace.transact({
          type: "project.bundle.import",
          data: incoming,
          cardResolutions: [{ cardId: local.cardId, action: "keep-local" }],
          resourceFiles: [{
            relativePath: resource.relativePath,
            targetRelativePath: `resources/cards/${local.cardId}/should-not-copy.pdf`,
            sha256: resource.sha256,
            size: resource.size,
            skip: true
          }]
        }) as ProjectBundleImportResult;
        assert.equal(result.cardMappings.find((item) => item.sourceCardId === local.cardId)?.action, "kept-local");
        assert.equal((await workspace.read({ kind: "card.read", cardId: local.cardId }))?.title, "本机苏青");
        const outline = await workspace.read({ kind: "project.outline", projectId: result.projectId });
        const importedScene = outline!.volumes.flatMap((volume) => volume.chapters).flatMap((chapter) => chapter.scenes)[0]!;
        assert.equal(importedScene.planning?.perspectiveCardId, local.cardId, `视角卡应从 ${sourceCardId} 重映射到 ${local.cardId}`);
        assert.equal(importedScene.planning?.castCardIds?.includes(local.cardId), true);
        const relations = await workspace.read({ kind: "card.relations", cardId: local.cardId });
        assert.equal(relations.outgoing.some((relation) => relation.note === "旧识"), true);
        const annotations = await workspace.read({ kind: "annotation.list", projectId: result.projectId });
        assert.equal(annotations.some((annotation) => annotation.cardId === local.cardId && annotation.note === "主角伏笔"), true);
        assert.equal(result.counts.resources, 0);
      });
    });

    await scenario("稳定 ID 内容不同选择导入副本：本机不变且所有引用与全局附件重映射", async () => {
      await withWorkspace(path.join(parent, "target-card-import-copy"), async (workspace) => {
        const local = await seedCollisionCard(workspace, "本机苏青");
        const incoming = remapBundleCardId(exportedWithResource!, sourceCardId, local.cardId);
        const resource = incoming.resources.find((item) => item.cardId === local.cardId)!;
        const copiedCardId = "card-import-copy-contract";
        const targetRelativePath = `resources/cards/${copiedCardId}/材料-副本.pdf`;
        const result = await workspace.transact({
          type: "project.bundle.import",
          data: incoming,
          cardResolutions: [{ cardId: local.cardId, action: "import-copy", targetCardId: copiedCardId }],
          resourceFiles: [{
            relativePath: resource.relativePath,
            targetRelativePath,
            sha256: resource.sha256,
            size: resource.size,
            skip: false
          }]
        }) as ProjectBundleImportResult;
        assert.deepEqual(
          result.cardMappings.find((item) => item.sourceCardId === local.cardId),
          { sourceCardId: local.cardId, targetCardId: copiedCardId, action: "copied" }
        );
        assert.equal((await workspace.read({ kind: "card.read", cardId: local.cardId }))?.title, "本机苏青");
        assert.equal((await workspace.read({ kind: "card.read", cardId: copiedCardId }))?.title, "苏青");
        const outline = await workspace.read({ kind: "project.outline", projectId: result.projectId });
        const importedScene = outline!.volumes.flatMap((volume) => volume.chapters).flatMap((chapter) => chapter.scenes)[0]!;
        assert.equal(importedScene.planning?.perspectiveCardId, copiedCardId);
        assert.equal(importedScene.planning?.castCardIds?.includes(copiedCardId), true);
        const relations = await workspace.read({ kind: "card.relations", cardId: copiedCardId });
        assert.equal(relations.outgoing.some((relation) => relation.note === "旧识"), true);
        const annotations = await workspace.read({ kind: "annotation.list", projectId: result.projectId });
        assert.equal(annotations.some((annotation) => annotation.cardId === copiedCardId && annotation.note === "主角伏笔"), true);
        const resources = await workspace.read({ kind: "resource.list", projectId: result.projectId });
        assert.equal(resources.some((item) => item.cardId === copiedCardId && item.relativePath === targetRelativePath), true);
      });
    });

    await scenario("稳定 ID 命中回收站卡片：禁止保留本机，只允许导入副本", async () => {
      await withWorkspace(path.join(parent, "target-card-deleted-conflict"), async (workspace) => {
        const local = await seedCollisionCard(workspace, "回收站苏青");
        await workspace.transact({ type: "card.delete", cardId: local.cardId });
        const incoming = remapBundleCardId(exported!, sourceCardId, local.cardId);
        const preview = await workspace.previewProjectBundleImport(incoming);
        assert.equal(preview.conflicts.length, 1);
        assert.equal(preview.conflicts[0]!.localDeleted, true);
        const beforeProjects = await workspace.read({ kind: "projects.list" });
        let keepError: unknown;
        try {
          await workspace.transact({
            type: "project.bundle.import",
            data: incoming,
            cardResolutions: [{ cardId: local.cardId, action: "keep-local" }]
          });
        } catch (caught) {
          keepError = caught;
        }
        assert.equal((keepError as { code?: string }).code, "conflict");
        assert.equal((await workspace.read({ kind: "projects.list" })).length, beforeProjects.length);

        const copiedCardId = "card-deleted-import-copy-contract";
        const copied = await workspace.transact({
          type: "project.bundle.import",
          data: incoming,
          cardResolutions: [{ cardId: local.cardId, action: "import-copy", targetCardId: copiedCardId }]
        }) as ProjectBundleImportResult;
        assert.equal(copied.cardMappings.find((item) => item.sourceCardId === local.cardId)?.targetCardId, copiedCardId);
        assert.equal((await workspace.read({ kind: "card.read", cardId: copiedCardId }))?.title, "苏青");
      });
    });

    await scenario("重复导入同一项目包被拒绝（ID 冲突）", async () => {
      await withWorkspace(path.join(parent, "target"), async (workspace) => {
        let error: unknown;
        try {
          await workspace.transact({ type: "project.bundle.import", data: exported! });
        } catch (caught) {
          error = caught;
        }
        assert.equal((error as { code?: string }).code, "conflict");
      });
    });

    await scenario("损坏的项目包被拒绝（formatVersion/引用缺失）", async () => {
      await withWorkspace(path.join(parent, "target2"), async (workspace) => {
        let versionError: unknown;
        try {
          await workspace.transact({
            type: "project.bundle.import",
            data: { ...exported!, formatVersion: 99 as never }
          });
        } catch (caught) {
          versionError = caught;
        }
        assert.equal((versionError as { code?: string }).code, "invalid-input");
        let relationError: unknown;
        try {
          await workspace.transact({
            type: "project.bundle.import",
            data: {
              ...exported!,
              project: { ...exported!.project, id: "project-broken-relations" },
              relations: [{ id: "relation-1", fromCardId: "card-missing", toCardId: "card-missing-2", relationType: "x", note: null, createdAt: "2026-01-01T00:00:00.000Z" }]
            }
          });
        } catch (caught) {
          relationError = caught;
        }
        assert.equal((relationError as { code?: string }).code, "invalid-input");
      });
    });

    await scenario("导出不存在的项目返回 null", async () => {
      await withWorkspace(path.join(parent, "source"), async (workspace) => {
        const result = await workspace.read({ kind: "project.bundle.export", projectId: "project-missing" });
        assert.equal(result, null);
      });
    });

    await scenario("v9 基线：项目 A 项目包导出范围仅含 A 的私有数据", async () => {
      // 共享夹具在 v9 下种入 A/B 两个项目，导出 A 的包，逐类核对范围隔离。
      await withWorkspace(path.join(parent, "v9-baseline"), async (workspace) => {
        const baseline = await seedV9Baseline(workspace);
        const bundle = (await workspace.read({
          kind: "project.bundle.export",
          projectId: baseline.projectA
        })) as ProjectBundleData;

        assert.equal(bundle.formatVersion, 2);
        assert.equal(bundle.project.id, baseline.projectA);
        assert.equal(bundle.project.id === baseline.projectB, false, "项目包只能属于项目 A");

        // 结构范围：只含 A 的卷 / 章 / 场景。
        assert.equal(bundle.volumes.length, 1);
        assert.equal(bundle.chapters.length, 1);
        assert.equal(bundle.scenes.length, 1);
        assert.equal(bundle.scenes[0]!.id, baseline.sceneA);

        // 卡片范围：含 A 的三张私有卡片，不含 B 的。
        const cardIds = bundle.cards.map((card) => card.id);
        assert.equal(cardIds.includes(baseline.cardASkill), true);
        assert.equal(cardIds.includes(baseline.cardACharacter), true);
        assert.equal(cardIds.includes(baseline.cardALocation), true);
        assert.equal(cardIds.includes(baseline.cardBCharacter), false, "项目包不得携带项目 B 的私有卡片");
        assert.equal(cardIds.includes(baseline.cardBLocation), false, "项目包不得携带项目 B 的私有地点卡");
        assert.equal(bundle.counts.cards, cardIds.length);

        // 自定义卡片类型范围：只含 A 的自定义类型（内置全局类型不带入包）。
        assert.equal(bundle.cardTypes.length, 1, "项目包只应携带项目 A 的自定义卡片类型");
        assert.equal(bundle.cardTypes[0]!.id, baseline.cardTypeA);
        assert.equal(bundle.cardTypes[0]!.kind, baseline.cardKindA);
        assert.equal(bundle.cardTypes.some((type) => type.id === baseline.cardTypeB), false, "项目包不得携带项目 B 的自定义卡片类型");

        // 卡片关系范围：A 的关系在包内，且两端卡片都在包内（无跨项目悬空引用）。
        assert.equal(bundle.relations.length, 1);
        for (const relation of bundle.relations) {
          assert.equal(cardIds.includes(relation.fromCardId), true, "关系起点必须在包内");
          assert.equal(cardIds.includes(relation.toCardId), true, "关系终点必须在包内");
        }

        // 批注范围：只含 A 的批注，且仍引用包内卡片。
        assert.equal(bundle.counts.annotations, 1);
        assert.equal(bundle.annotations.some((item) => item.id === baseline.annotationA), true);
        assert.equal(bundle.annotations.some((item) => item.id === baseline.annotationB), false, "项目包不得携带项目 B 的批注");
        assert.equal(bundle.annotations[0]!.cardId, baseline.cardASkill);
        assert.equal(bundle.annotations[0]!.sceneId, baseline.sceneA);

        // 场景任务卡的卡片引用随 planningJson 一并导出（迁移器须保留这些 ID）。
        const planning = JSON.parse(bundle.scenes[0]!.planningJson) as {
          perspectiveCardId?: string | null;
          locationCardId?: string | null;
          castCardIds?: string[] | null;
        };
        assert.equal(planning.perspectiveCardId, baseline.cardACharacter);
        assert.equal(planning.locationCardId, baseline.cardALocation);
        assert.deepEqual(planning.castCardIds, [baseline.cardACharacter, baseline.cardASkill]);

        // 附件元数据范围：只含 A 的（A / B 都有附件，这里证明 A 的包不带 B 的）。
        assert.equal(bundle.counts.resources, 1);
        assert.equal(bundle.resources.length, 1);
        assert.equal(bundle.resources[0]!.id, baseline.resourceAId);
        assert.equal(bundle.resources[0]!.relativePath, baseline.resourceRelativePath);
        assert.equal(bundle.resources[0]!.cardId, baseline.cardASkill);
        assert.equal(bundle.resources[0]!.sha256, baseline.resourceSha256);
        assert.equal(bundle.resources[0]!.size, baseline.resourceSize);
        assert.equal(bundle.resources.some((item) => item.id === baseline.resourceBId), false, "项目包不得携带项目 B 的附件记录 ID");
        assert.equal(
          bundle.resources.some((item) => item.relativePath === baseline.resourceBRelativePath),
          false,
          "项目包不得携带项目 B 的附件相对路径"
        );

        // 隔离核心断言：序列化结果里不得出现任何项目 B 的私有实体 id / 附件标识。
        const serialized = JSON.stringify(bundle);
        assert.equal(serialized.includes(baseline.projectB), false, "项目包不得包含项目 B 的 id");
        assert.equal(serialized.includes(baseline.cardBCharacter), false);
        assert.equal(serialized.includes(baseline.cardBLocation), false);
        assert.equal(serialized.includes(baseline.cardTypeB), false);
        assert.equal(serialized.includes(baseline.annotationB), false);
        assert.equal(serialized.includes(baseline.sceneB), false);
        assert.equal(serialized.includes(baseline.resourceBId), false, "项目包不得包含项目 B 的附件 ID");
        assert.equal(serialized.includes(baseline.resourceBRelativePath), false, "项目包不得包含项目 B 的附件路径");
        assert.equal(serialized.includes(baseline.resourceBSha256), false, "项目包不得包含项目 B 的附件内容标识（sha256）");
      });
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await removeWithRetry(parent);
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
