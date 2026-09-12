import { strict as assert } from "node:assert";
import { createHash } from "node:crypto";
import { access, mkdir, mkdtemp, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CardLinkResult,
  type CardSummary,
  type CreationWorkspace,
  type TrashImpactView
} from "./index";
import { removeWithRetry } from "./test-utils";

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "global-card-library-"));
  const workspace = await openCreationWorkspace({ directory });
  const lifecycleDirectory = path.join(directory, "trash-lifecycle");
  let lifecycleWorkspace: CreationWorkspace | null = null;
  let lifecycleProjectA = "";
  let lifecycleProjectB = "";
  let lifecycleSceneId = "";
  let lifecycleCardId = "";
  let lifecyclePeerCardId = "";
  let lifecycleAnnotationId = "";
  let lifecycleResourcePath = "";
  let tests = 0;
  const scenario = async (name: string, task: () => Promise<void>): Promise<void> => {
    try {
      await task();
      tests += 1;
    } catch (error) {
      throw new Error(`场景「${name}」失败：${error instanceof Error ? error.stack ?? error.message : String(error)}`);
    }
  };

  try {
    const projectA = await workspace.transact({ type: "project.create", title: "关联项目甲" });
    const projectB = await workspace.transact({ type: "project.create", title: "关联项目乙" });
    let globalCardId = "";

    await scenario("无 projectId 创建全局卡片，默认不关联任何项目", async () => {
      const created = await workspace.transact({ type: "card.create", kind: "character", title: "全局角色" });
      globalCardId = created.entityId;
      assert.equal(created.projectId, null);
      const globalCards = await workspace.read({ kind: "cards.list", search: "全局角色" });
      const card = globalCards.find((item) => item.id === globalCardId);
      assert.ok(card);
      assert.equal(card.projectId, null);
      assert.deepEqual(card.linkedProjectIds, []);
      assert.equal(card.usageCount, 0);
      assert.equal((await workspace.read({ kind: "cards.list", projectId: projectA.projectId })).length, 0);
    });

    await scenario("link 幂等并返回使用项目数", async () => {
      const first = (await workspace.transact({
        type: "card.link",
        projectId: projectA.projectId,
        cardId: globalCardId
      })) as CardLinkResult;
      assert.equal(first.changed, true);
      assert.equal(first.linked, true);
      assert.equal(first.usageCount, 1);
      const repeated = (await workspace.transact({
        type: "card.link",
        projectId: projectA.projectId,
        cardId: globalCardId
      })) as CardLinkResult;
      assert.equal(repeated.changed, false);
      assert.equal(repeated.sequence, -1);
      assert.equal(repeated.usageCount, 1);
    });

    await scenario("同一卡片关联两个项目并在全局/项目投影返回完整使用信息", async () => {
      const linked = (await workspace.transact({
        type: "card.link",
        projectId: projectB.projectId,
        cardId: globalCardId
      })) as CardLinkResult;
      assert.equal(linked.usageCount, 2);
      const projectCard = (await workspace.read({ kind: "cards.list", projectId: projectA.projectId })).find(
        (item) => item.id === globalCardId
      );
      assert.ok(projectCard);
      assert.equal(projectCard.projectId, projectA.projectId);
      assert.deepEqual(projectCard.linkedProjectIds, [projectA.projectId, projectB.projectId].sort());
      assert.equal(projectCard.usageCount, 2);
      const global = (await workspace.read({ kind: "card.read", cardId: globalCardId })) as CardSummary;
      assert.equal(global.projectId, null);
      assert.equal(global.usageCount, 2);
    });

    await scenario("全局修改对两个项目立即可见", async () => {
      const eventsA: Array<{ projectId: string | null; commandType: string }> = [];
      const eventsB: Array<{ projectId: string | null; commandType: string }> = [];
      const stopA = workspace.watch({ projectId: projectA.projectId }, (event) => eventsA.push(event));
      const stopB = workspace.watch({ projectId: projectB.projectId }, (event) => eventsB.push(event));
      const current = (await workspace.read({ kind: "card.read", cardId: globalCardId }))!;
      try {
        await workspace.transact({
          type: "card.update",
          cardId: globalCardId,
          baseRevision: current.revision,
          title: "全局角色·更新"
        });
      } finally {
        stopA();
        stopB();
      }
      for (const projectId of [projectA.projectId, projectB.projectId]) {
        const cards = await workspace.read({ kind: "cards.list", projectId });
        assert.equal(cards.find((card) => card.id === globalCardId)?.title, "全局角色·更新");
      }
      assert.deepEqual(eventsA.map((event) => [event.projectId, event.commandType]), [[null, "card.update"]]);
      assert.deepEqual(eventsB.map((event) => [event.projectId, event.commandType]), [[null, "card.update"]]);
    });

    await scenario("unlink 幂等且只移除项目关联，不删除全局卡片", async () => {
      const first = (await workspace.transact({
        type: "card.unlink",
        projectId: projectA.projectId,
        cardId: globalCardId
      })) as CardLinkResult;
      assert.equal(first.changed, true);
      assert.equal(first.linked, false);
      assert.equal(first.usageCount, 1);
      const repeated = (await workspace.transact({
        type: "card.unlink",
        projectId: projectA.projectId,
        cardId: globalCardId
      })) as CardLinkResult;
      assert.equal(repeated.changed, false);
      assert.equal(repeated.usageCount, 1);
      assert.equal((await workspace.read({ kind: "cards.list", projectId: projectA.projectId })).some((c) => c.id === globalCardId), false);
      assert.equal((await workspace.read({ kind: "cards.list", projectId: projectB.projectId })).some((c) => c.id === globalCardId), true);
      assert.ok(await workspace.read({ kind: "card.read", cardId: globalCardId }));
    });

    await scenario("仍被场景任务卡引用时拒绝解除关联", async () => {
      const linked = await workspace.transact({
        type: "card.create",
        projectId: projectA.projectId,
        kind: "character",
        title: "场景视角"
      });
      await workspace.transact({
        type: "scene.updatePlanning",
        sceneId: projectA.sceneId,
        planning: { perspectiveCardId: linked.entityId }
      });
      await assert.rejects(
        workspace.transact({ type: "card.unlink", projectId: projectA.projectId, cardId: linked.entityId }),
        (error: unknown) => error instanceof CreationWorkspaceError && error.code === "conflict"
      );
      assert.equal(
        (await workspace.read({ kind: "cards.list", projectId: projectA.projectId })).some((card) => card.id === linked.entityId),
        true
      );
    });

    await scenario("卡片类型和关系类型按全局 Interface 读取", async () => {
      const type = await workspace.transact({
        type: "cardType.create",
        name: "全局技能",
        fields: [{ key: "level", label: "等级", kind: "number" }]
      });
      const relation = await workspace.transact({
        type: "relationType.create",
        forwardName: "师从",
        reverseName: "教授",
        fromKinds: ["character"],
        toKinds: ["character"]
      });
      const types = await workspace.read({ kind: "cardTypes.list" });
      const relationTypes = await workspace.read({ kind: "relationTypes.list" });
      assert.equal(types.find((item) => item.id === type.entityId)?.builtIn, false);
      assert.equal(types.filter((item) => item.builtIn).length, 8);
      assert.equal(relationTypes.find((item) => item.id === relation.entityId)?.builtIn, false);
      assert.equal(relationTypes.some((item) => item.builtIn), true);
    });

    await scenario("删除前影响完整，软删除隐藏卡片但保留恢复所需数据", async () => {
      lifecycleWorkspace = await openCreationWorkspace({ directory: lifecycleDirectory });
      const active = lifecycleWorkspace;
      const projectA = await active.transact({ type: "project.create", title: "回收项目甲" });
      const projectB = await active.transact({ type: "project.create", title: "回收项目乙" });
      lifecycleProjectA = projectA.projectId;
      lifecycleProjectB = projectB.projectId;
      lifecycleSceneId = projectA.sceneId;

      const card = await active.transact({
        type: "card.create",
        projectId: lifecycleProjectA,
        kind: "character",
        title: "待回收角色"
      });
      lifecycleCardId = card.entityId;
      await active.transact({ type: "card.link", projectId: lifecycleProjectB, cardId: lifecycleCardId });
      const peer = await active.transact({
        type: "card.create",
        projectId: lifecycleProjectA,
        kind: "character",
        title: "关系保留对象"
      });
      lifecyclePeerCardId = peer.entityId;
      const relationType = await active.transact({
        type: "relationType.create",
        forwardName: "守护",
        reverseName: "受守护",
        fromKinds: ["character"],
        toKinds: ["character"]
      });
      await active.transact({
        type: "cardRelation.create",
        fromCardId: lifecycleCardId,
        toCardId: lifecyclePeerCardId,
        relationTypeId: relationType.entityId
      });
      await active.transact({
        type: "scene.updateBody",
        sceneId: lifecycleSceneId,
        baseRevision: 1,
        body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "引用目标卡片" }] }] }
      });
      await active.transact({
        type: "scene.updatePlanning",
        sceneId: lifecycleSceneId,
        planning: { perspectiveCardId: lifecycleCardId, castCardIds: [lifecycleCardId] }
      });
      const annotation = await active.transact({
        type: "annotation.create",
        projectId: lifecycleProjectA,
        sceneId: lifecycleSceneId,
        cardId: lifecycleCardId,
        anchor: { blockIndex: 0, textOffset: 0, textLength: 2 },
        note: "保留批注关联"
      });
      lifecycleAnnotationId = annotation.annotationId;

      const payload = "全局卡片回收资源";
      const relativePath = `resources/cards/${lifecycleCardId}/evidence.txt`;
      lifecycleResourcePath = path.join(lifecycleDirectory, ...relativePath.split("/"));
      await mkdir(path.dirname(lifecycleResourcePath), { recursive: true });
      await writeFile(lifecycleResourcePath, payload, "utf8");
      await active.transact({
        type: "resource.attach",
        cardId: lifecycleCardId,
        relativePath,
        sha256: createHash("sha256").update(payload).digest("hex"),
        size: Buffer.byteLength(payload),
        originalName: "回收验证.txt"
      });

      const impact = await active.read({ kind: "trash.impact", entity: "card", entityId: lifecycleCardId }) as TrashImpactView;
      assert.equal(impact.linkedProjectCount, 2);
      assert.equal(impact.relatedCardCount, 1);
      assert.equal(impact.sceneReferenceCount, 1);
      assert.equal(impact.annotationCount, 1);
      assert.equal(impact.resourceCount, 1);

      await active.transact({ type: "card.delete", cardId: lifecycleCardId });
      assert.equal((await active.read({ kind: "cards.list" })).some((item) => item.id === lifecycleCardId), false);
      assert.equal((await active.read({ kind: "cards.list", projectId: lifecycleProjectA })).some((item) => item.id === lifecycleCardId), false);
      assert.equal((await active.read({ kind: "cards.list", projectId: lifecycleProjectB })).some((item) => item.id === lifecycleCardId), false);
      assert.equal(await active.read({ kind: "card.read", cardId: lifecycleCardId }), null);
      const globalTrash = await active.read({ kind: "trash.list" });
      assert.equal(globalTrash.some((item) => item.id === lifecycleCardId && item.projectId === null), true);
      const projectTrash = await active.read({ kind: "trash.list", projectId: lifecycleProjectA });
      assert.equal(projectTrash.some((item) => item.id === lifecycleCardId), true);

      const database = new Database(path.join(lifecycleDirectory, "workspace.sqlite"), { readonly: true });
      try {
        const relationCount = database.prepare("SELECT count(*) AS count FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").get(lifecycleCardId, lifecycleCardId) as { count: number };
        const resourceCount = database.prepare("SELECT count(*) AS count FROM global_card_resources WHERE card_id = ?").get(lifecycleCardId) as { count: number };
        const state = database.prepare("SELECT linked_project_ids_json FROM global_card_trash_state WHERE card_id = ?").get(lifecycleCardId) as { linked_project_ids_json: string };
        const annotationRow = database.prepare("SELECT card_id FROM annotations WHERE id = ?").get(lifecycleAnnotationId) as { card_id: string | null };
        assert.equal(relationCount.count, 1);
        assert.equal(resourceCount.count, 1);
        assert.deepEqual((JSON.parse(state.linked_project_ids_json) as string[]).sort(), [lifecycleProjectA, lifecycleProjectB].sort());
        assert.equal(annotationRow.card_id, lifecycleCardId);
      } finally {
        database.close();
      }
      await access(lifecycleResourcePath);
    });

    await scenario("恢复重建原项目关联，并继续保留关系、引用、批注和附件", async () => {
      assert.ok(lifecycleWorkspace);
      const active = lifecycleWorkspace;
      const restored = await active.transact({ type: "trash.restore", entity: "card", entityId: lifecycleCardId });
      assert.equal(restored.projectId, null);
      assert.equal((await active.read({ kind: "cards.list", projectId: lifecycleProjectA })).some((item) => item.id === lifecycleCardId), true);
      assert.equal((await active.read({ kind: "cards.list", projectId: lifecycleProjectB })).some((item) => item.id === lifecycleCardId), true);
      assert.equal((await active.read({ kind: "trash.list" })).length, 0);
      const relations = await active.read({ kind: "card.relations", cardId: lifecycleCardId });
      assert.equal(relations.outgoing.some((item) => item.toCardId === lifecyclePeerCardId), true);
      assert.equal((await active.read({ kind: "resource.list", cardId: lifecycleCardId })).length, 1);
      const annotations = await active.read({ kind: "annotation.list", projectId: lifecycleProjectA, sceneId: lifecycleSceneId });
      assert.equal(annotations.find((item) => item.id === lifecycleAnnotationId)?.cardId, lifecycleCardId);
      await access(lifecycleResourcePath);

      await active.transact({ type: "card.delete", cardId: lifecycleCardId });
      await active.close();
      lifecycleWorkspace = null;
    });

    await scenario("超过 30 天后永久清理引用与数据库记录，并排空文件 GC 队列", async () => {
      const expiredAt = "2026-07-01T00:00:00.000Z";
      const database = new Database(path.join(lifecycleDirectory, "workspace.sqlite"));
      try {
        database.prepare("UPDATE cards SET deleted_at = ? WHERE id = ?").run(expiredAt, lifecycleCardId);
        database.prepare("UPDATE global_card_trash_state SET deleted_at = ? WHERE card_id = ?").run(expiredAt, lifecycleCardId);
      } finally {
        database.close();
      }

      lifecycleWorkspace = await openCreationWorkspace({ directory: lifecycleDirectory });
      const active = lifecycleWorkspace;
      assert.equal(await active.read({ kind: "card.read", cardId: lifecycleCardId }), null);
      await assert.rejects(access(lifecycleResourcePath));

      const verified = new Database(path.join(lifecycleDirectory, "workspace.sqlite"), { readonly: true });
      try {
        const cardCount = verified.prepare("SELECT count(*) AS count FROM cards WHERE id = ?").get(lifecycleCardId) as { count: number };
        const relationCount = verified.prepare("SELECT count(*) AS count FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").get(lifecycleCardId, lifecycleCardId) as { count: number };
        const annotationRow = verified.prepare("SELECT card_id FROM annotations WHERE id = ?").get(lifecycleAnnotationId) as { card_id: string | null };
        const sceneRow = verified.prepare("SELECT planning_json FROM scenes WHERE id = ?").get(lifecycleSceneId) as { planning_json: string };
        const gcCount = verified.prepare("SELECT count(*) AS count FROM global_card_resource_gc").get() as { count: number };
        const planning = JSON.parse(sceneRow.planning_json) as { perspectiveCardId?: string; locationCardId?: string; castCardIds?: string[] };
        assert.equal(cardCount.count, 0);
        assert.equal(relationCount.count, 0);
        assert.equal(annotationRow.card_id, null);
        assert.notEqual(planning.perspectiveCardId, lifecycleCardId);
        assert.equal(planning.castCardIds?.includes(lifecycleCardId) ?? false, false);
        assert.equal(gcCount.count, 0);
      } finally {
        verified.close();
      }
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    const pendingLifecycleWorkspace = lifecycleWorkspace as CreationWorkspace | null;
    if (pendingLifecycleWorkspace) {
      try {
        await pendingLifecycleWorkspace.close();
      } catch {
        // 保留首个契约失败，清理阶段不覆盖错误。
      }
    }
    await workspace.close();
    await removeWithRetry(directory);
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
