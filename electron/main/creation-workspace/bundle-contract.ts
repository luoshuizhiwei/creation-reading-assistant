import { strict as assert } from "node:assert";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  openCreationWorkspace,
  type CreationWorkspace,
  type ProjectBundleData,
  type ProjectBundleImportResult
} from "./index";

async function withWorkspace<T>(directory: string, fn: (workspace: CreationWorkspace) => Promise<T>): Promise<T> {
  const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
  try {
    return await fn(workspace);
  } finally {
    await workspace.close();
  }
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
        await workspace.transact({
          type: "cardRelation.create",
          projectId: sourceProjectId,
          fromCardId: card.entityId,
          toCardId: card2.entityId,
          relationTypeId: "relation-type-character-character",
          note: "旧识"
        });
        await workspace.transact({ type: "snapshot.create", projectId: sourceProjectId, subjectType: "scene", subjectId: sourceSceneId, reason: "里程碑" });
        exported = (await workspace.read({ kind: "project.bundle.export", projectId: sourceProjectId }))!;
        assert.equal(exported.volumes.length, 2);
        assert.equal(exported.chapters.length, 2);
        assert.equal(exported.scenes.length, 1);
        assert.equal(exported.cards.length, 2);
        assert.equal(exported.relations.length, 1);
        assert.equal(exported.snapshots.length, 2);
        assert.equal(exported.project.setup.template, "long-form");
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

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await rm(parent, { recursive: true, force: true });
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
