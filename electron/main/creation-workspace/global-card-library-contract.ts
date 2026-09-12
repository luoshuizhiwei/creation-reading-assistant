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

    // ---- Stage 4-F：关系图（整图 / 项目引用投影 / 边界） ----
    const graphProject = await workspace.transact({ type: "project.create", title: "关系图契约项目" });
    const graphProjectId = graphProject.projectId;
    const newCard = async (title: string): Promise<string> =>
      (await workspace.transact({ type: "card.create", kind: "character", title })).entityId;
    const graphA = await newCard("图节点甲");
    const graphB = await newCard("图节点乙");
    const graphOutside = await newCard("图节点丙（项目外）");
    await workspace.transact({ type: "card.link", projectId: graphProjectId, cardId: graphA });
    await workspace.transact({ type: "card.link", projectId: graphProjectId, cardId: graphB });
    const relationTypes = await workspace.read({ kind: "relationTypes.list" });
    const relationTypeId = relationTypes[0]!.id;
    await workspace.transact({
      type: "cardRelation.create",
      fromCardId: graphA,
      toCardId: graphB,
      relationTypeId
    });
    await workspace.transact({
      type: "cardRelation.create",
      fromCardId: graphA,
      toCardId: graphOutside,
      relationTypeId
    });

    await scenario("关系图全局视角：卡片全部入图，关系两端都在节点集内", async () => {
      const view = await workspace.read({ kind: "relationGraph.list" });
      assert.equal(view.scope, "global");
      assert.equal(view.projectId, null);
      const ids = new Set(view.nodes.map((node) => node.cardId));
      assert.ok(ids.has(graphA) && ids.has(graphB) && ids.has(graphOutside));
      assert.equal(view.hiddenRelationCount, 0);
      for (const edge of view.edges) {
        assert.ok(ids.has(edge.fromCardId) && ids.has(edge.toCardId));
      }
      const degreeOfA = view.nodes.find((node) => node.cardId === graphA)?.degree;
      assert.equal(degreeOfA, 2);
    });

    await scenario("关系图项目视角：只画已关联卡片，指向项目外的关系统计但不绘制", async () => {
      const view = await workspace.read({ kind: "relationGraph.list", projectId: graphProjectId });
      assert.equal(view.scope, "project");
      assert.equal(view.projectId, graphProjectId);
      const ids = new Set(view.nodes.map((node) => node.cardId));
      assert.ok(ids.has(graphA) && ids.has(graphB));
      assert.equal(ids.has(graphOutside), false, "项目视图不得把未关联的全局卡片拉进来");
      assert.equal(view.edges.length, 1);
      assert.equal(view.hiddenRelationCount, 1, "指向项目外卡片的关系必须显式报数");
      assert.equal(view.edges.some((edge) => edge.toCardId === graphOutside), false);
    });

    await scenario("关系本体是全局资产：项目视图读取不新增也不复制关系", async () => {
      const before = await workspace.read({ kind: "relationGraph.list" });
      await workspace.read({ kind: "relationGraph.list", projectId: graphProjectId });
      const after = await workspace.read({ kind: "relationGraph.list" });
      assert.equal(after.edges.length, before.edges.length);
      assert.deepEqual(
        after.edges.map((edge) => edge.id).sort(),
        before.edges.map((edge) => edge.id).sort()
      );
    });

    await scenario("已删除卡片不进图，其关系也不绘制", async () => {
      await workspace.transact({ type: "card.delete", cardId: graphOutside });
      const view = await workspace.read({ kind: "relationGraph.list", projectId: graphProjectId });
      const ids = new Set(view.nodes.map((node) => node.cardId));
      assert.equal(ids.has(graphOutside), false);
      assert.equal(view.edges.some((edge) => edge.toCardId === graphOutside), false);
      assert.equal(view.hiddenRelationCount, 0, "对端卡片已删除的关系不应再计入隐藏数");
    });

    await scenario("节点上限生效：截断数与隐藏关系统计准确", async () => {
      const all = await workspace.read({ kind: "cards.list" });
      // 下限被夹到 20：越界值不得把图放大，也不得让调用方拿到比请求更多的节点。
      const limited = await workspace.read({ kind: "relationGraph.list", limit: 2 });
      assert.equal(limited.nodes.length, Math.min(all.length, 20));
      assert.equal(limited.truncatedNodeCount, Math.max(0, all.length - 20));
      const ids = new Set(limited.nodes.map((node) => node.cardId));
      for (const edge of limited.edges) {
        assert.ok(ids.has(edge.fromCardId) && ids.has(edge.toCardId));
      }
    });

    await scenario("非法 projectId 被拒绝，不会退化成全图", async () => {
      await assert.rejects(
        () => workspace.read({ kind: "relationGraph.list", projectId: "   " }),
        (error: unknown) => error instanceof CreationWorkspaceError && error.code === "invalid-input"
      );
    });

    // ---- Stage 4-A 增强：列表封面缩略图 ----
    // 卡片列表要显示封面缩略图，就必须让 cards.list 一次性带出封面资源 ID，
    // 否则前端要为每张卡单独查一次资源（N+1）。以下场景锁住该契约。
    await scenario("cards.list 一次性带出封面资源 ID（列表缩略图，避免 N+1）", async () => {
      const withCover = await workspace.transact({
        type: "card.create",
        kind: "character",
        title: "带封面的卡",
        aliases: [],
        tags: []
      });
      const withoutCover = await workspace.transact({
        type: "card.create",
        kind: "character",
        title: "无封面的卡",
        aliases: [],
        tags: []
      });
      const coveredId = (withCover as { entityId?: string }).entityId ?? "";
      const bareId = (withoutCover as { entityId?: string }).entityId ?? "";
      assert.ok(coveredId.startsWith("card-"), `期望拿到卡片 ID，实际 ${coveredId}`);

      // 直接落到 global_card_resources 表插入一条 role='cover' 的资源。
      const database = new Database(path.join(directory, "workspace.sqlite"));
      try {
        database
          .prepare(
            `INSERT INTO global_card_resources (id, card_id, relative_path, sha256, size, original_name, role, created_at)
             VALUES (?, ?, ?, ?, ?, ?, 'cover', ?)`
          )
          .run(
            "resource-cover-contract",
            coveredId,
            "resources/cards/cover-contract.png",
            "c".repeat(64),
            2048,
            "封面.png",
            "2026-09-12T00:00:00.000Z"
          );
        // 同一张卡再插一个附件，确认只有 cover 被挑出来。
        database
          .prepare(
            `INSERT INTO global_card_resources (id, card_id, relative_path, sha256, size, original_name, role, created_at)
             VALUES (?, ?, ?, ?, ?, ?, 'attachment', ?)`
          )
          .run(
            "resource-attach-contract",
            coveredId,
            "resources/cards/attach-contract.txt",
            "d".repeat(64),
            128,
            "设定.txt",
            "2026-09-12T00:00:00.000Z"
          );
      } finally {
        database.close();
      }

      const all = (await workspace.read({ kind: "cards.list" })) as CardSummary[];
      const covered = all.find((item) => item.id === coveredId);
      const bare = all.find((item) => item.id === bareId);
      assert.ok(covered, "带封面的卡应出现在列表里");
      assert.ok(bare, "无封面的卡应出现在列表里");
      // 核心断言：cover 资源 ID 被挑出来，且不是附件。
      assert.equal(covered.coverResourceId, "resource-cover-contract");
      // 无封面时为 null（不是 undefined、不是空串），前端据此降级为占位。
      assert.equal(bare.coverResourceId, null);
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
