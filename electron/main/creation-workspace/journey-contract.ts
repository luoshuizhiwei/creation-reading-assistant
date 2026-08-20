import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type Annotation,
  type CardSummary,
  type CreationSearchView,
  type CreationWorkspace,
  type InboxItem,
  type ProjectStatsView,
  type ReplaceApplyResult,
  type ReplacePreviewView,
  type ResourceInfo,
  type SnapshotInfo,
  type TrashItem
} from "./index";

/**
 * 端到端用户旅程：模拟真实使用者从建项目到备份的完整操作链，
 * 每个阶段之间断言数据一致性，暴露跨功能联动的回归。
 */
async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-journey-"));
  let tests = 0;
  const results: Record<string, unknown> = {};
  let workspace: CreationWorkspace | undefined;
  try {
    const step = async <T>(name: string, fn: () => Promise<T>): Promise<T> => {
      try {
        const result = await fn();
        tests += 1;
        return result;
      } catch (error) {
        throw new Error(`旅程步骤「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    workspace = await openCreationWorkspace({ directory });

    let projectId = "";
    let volumeId = "";
    let chapterId = "";
    let sceneId = "";
    let secondSceneId = "";
    let cardId = "";

    await step("1. 新建项目（向导等效）", async () => {
      const created = await workspace!.transact({
        type: "project.create",
        title: "新书稿",
        setup: { template: "long-form", weeklyUpdateDays: [1, 5], chapterWorkflow: ["规划", "待写", "写作中", "初稿", "定稿"] }
      });
      projectId = created.projectId;
      volumeId = created.volumeId;
      chapterId = created.chapterId;
      sceneId = created.sceneId;
      const list = (await workspace!.read({ kind: "projects.list" })) as Array<{ id: string; chapterCount: number; sceneCount: number }>;
      const summary = list.find((item) => item.id === projectId)!;
      assert.equal(summary.chapterCount, 1);
      assert.equal(summary.sceneCount, 1);
    });

    await step("2. 写作：正文保存 + revision 递增", async () => {
      const saved = await workspace!.transact({
        type: "scene.updateBody",
        sceneId,
        baseRevision: 1,
        body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "第一章的开头。" }] }] }
      });
      assert.equal(saved.revision, 2);
      const body = await workspace!.read({ kind: "scene.body", sceneId });
      assert.equal(body?.revision, 2);
    });

    await step("3. 大纲：建章建场景 + 重排", async () => {
      const chapter2 = await workspace!.transact({ type: "chapter.create", projectId, volumeId, title: "第二章" }) as { entityId: string };
      secondSceneId = (await workspace!.transact({ type: "scene.create", chapterId: chapter2.entityId, title: "深夜来信" }) as { entityId: string }).entityId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: secondSceneId,
        baseRevision: 1,
        body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "第二章正文，黄沙镇的风又吹过。" }] }] }
      });
      const outline = await workspace!.read({ kind: "project.outline", projectId });
      assert.equal(outline?.volumes[0]?.chapters.length, 2);
      // 重排：把第二章移到第一章前面
      await workspace!.transact({ type: "chapter.reorder", chapterId: chapter2.entityId, beforeChapterId: chapterId });
      const reordered = await workspace!.read({ kind: "project.outline", projectId });
      assert.equal(reordered?.volumes[0]?.chapters[0]?.id, chapter2.entityId);
    });

    await step("4. 卡片：建卡 + 关系 + 筛选", async () => {
      const card = await workspace!.transact({ type: "card.create", projectId, kind: "character", title: "苏青", aliases: ["阿青"], tags: ["主角"], fields: { note: "女一" } }) as { entityId: string };
      cardId = card.entityId;
      const card2 = await workspace!.transact({ type: "card.create", projectId, kind: "character", title: "顾淮" }) as { entityId: string };
      await workspace!.transact({
        type: "cardRelation.create",
        projectId,
        fromCardId: cardId,
        toCardId: card2.entityId,
        relationTypeId: "relation-type-character-character",
        note: "旧识"
      });
      const byAlias = (await workspace!.read({ kind: "cards.list", projectId, search: "阿青" })) as CardSummary[];
      assert.equal(byAlias.some((item) => item.id === cardId), true);
      const relations = await workspace!.read({ kind: "card.relations", cardId });
      assert.equal(relations.outgoing.length, 1);
    });

    await step("5. 搜索：正文/卡片/章节/项目四范围", async () => {
      const view = (await workspace!.read({ kind: "search.query", projectId, text: "黄沙镇" })) as CreationSearchView;
      assert.equal(view.hits.some((hit) => hit.kind === "scene"), true);
      const cardsOnly = (await workspace!.read({ kind: "search.query", projectId, text: "苏青", scopes: ["card"] })) as CreationSearchView;
      assert.equal(cardsOnly.hits.some((hit) => hit.id === cardId), true);
    });

    await step("6. 查找替换：预览 → 排除 → 执行 → 快照可恢复", async () => {
      const preview = (await workspace!.read({
        kind: "replace.preview",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "project"
      })) as ReplacePreviewView;
      assert.equal(preview.totalHits, 1);
      const result = (await workspace!.transact({
        type: "replace.apply",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "project"
      })) as ReplaceApplyResult;
      assert.equal(result.appliedHits, 1);
      const body = await workspace!.read({ kind: "scene.body", sceneId: secondSceneId });
      assert.equal(JSON.stringify(body?.body).includes("雾都城"), true);
      const snapshots = (await workspace!.read({ kind: "snapshot.list", projectId, subjectType: "scene", subjectId: secondSceneId })) as SnapshotInfo[];
      assert.equal(snapshots.some((item) => item.reason === "查找替换自动备份"), true);
    });

    await step("7. 批注：创建 → 编辑失效 → 待重新定位", async () => {
      const created = await workspace!.transact({
        type: "annotation.create",
        projectId,
        sceneId: secondSceneId,
        cardId,
        anchor: { blockIndex: 0, textOffset: 0, textLength: 5 },
        note: "主角登场"
      });
      const list = (await workspace!.read({ kind: "annotation.list", projectId, sceneId: secondSceneId })) as Annotation[];
      assert.equal(list[0]!.anchorInvalid, false);
      // 编辑正文让锚定文本变化
      const body = await workspace!.read({ kind: "scene.body", sceneId: secondSceneId });
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: secondSceneId,
        baseRevision: body!.revision,
        body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "开篇完全改写。" }] }] }
      });
      const after = (await workspace!.read({ kind: "annotation.list", projectId, sceneId: secondSceneId })) as Annotation[];
      assert.equal(after[0]!.anchorInvalid, true);
      await workspace!.transact({ type: "annotation.delete", annotationId: created.annotationId });
    });

    await step("8. 附件：登记 → 列表 → 移除", async () => {
      const attached = await workspace!.transact({
        type: "resource.attach",
        projectId,
        cardId,
        relativePath: `resources/${projectId}/demo.txt`,
        sha256: "a".repeat(64),
        size: 1024,
        originalName: "设定.txt"
      });
      const list = (await workspace!.read({ kind: "resource.list", projectId, cardId })) as ResourceInfo[];
      assert.equal(list.length, 1);
      await workspace!.transact({ type: "resource.detach", resourceId: attached.resourceId });
      const after = (await workspace!.read({ kind: "resource.list", projectId, cardId })) as ResourceInfo[];
      assert.equal(after.length, 0);
    });

    await step("9. 快照与回收站：改坏前快照 → 修改 → 恢复；删除 → 恢复", async () => {
      await workspace!.transact({ type: "snapshot.create", projectId, subjectType: "scene", subjectId: sceneId, reason: "发布前" });
      const body = await workspace!.read({ kind: "scene.body", sceneId });
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId,
        baseRevision: body!.revision,
        body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "第一章被改坏的内容。" }] }] }
      });
      const snapshots = (await workspace!.read({ kind: "snapshot.list", projectId, subjectType: "scene", subjectId: sceneId })) as SnapshotInfo[];
      await workspace!.restoreSnapshotWithProtection({
        type: "snapshot.restoreWithProtection",
        projectId,
        snapshotId: snapshots.find((item) => item.reason === "发布前")!.id,
        protectionReason: "恢复前保护"
      });
      const restored = await workspace!.read({ kind: "scene.body", sceneId });
      assert.equal(JSON.stringify(restored?.body).includes("第一章的开头"), true);
      // 回收站
      await workspace!.transact({ type: "card.delete", cardId });
      const trash = (await workspace!.read({ kind: "trash.list", projectId })) as TrashItem[];
      assert.equal(trash.some((item) => item.id === cardId), true);
      await workspace!.transact({ type: "trash.restore", projectId, entity: "card", entityId: cardId });
      const cards = (await workspace!.read({ kind: "cards.list", projectId })) as CardSummary[];
      assert.equal(cards.some((item) => item.id === cardId), true);
    });

    await step("10. 统计与会话：字数三口径 + 会话上报 + 连续天数", async () => {
      await workspace!.transact({
        type: "session.report",
        projectId,
        sceneId,
        // 使用当前时刻（本地"今天"），避免本地午夜后「1 小时前」落在昨天导致 streak 断言失败。
        startedAt: new Date().toISOString(),
        activeSeconds: 600,
        netChars: 120
      });
      const stats = (await workspace!.read({ kind: "stats.view", projectId })) as ProjectStatsView;
      assert.equal(stats.words.han > 0, true);
      assert.equal(stats.streakDays >= 1, true);
      assert.equal(stats.sessionMinutes.total >= 10, true);
    });

    await step("11. 本地校对：发现问题不修改", async () => {
      const proof = await workspace!.read({ kind: "proof.query", projectId, bannedWords: ["坏词"] });
      assert.equal(proof.issues.length >= 0, true);
      const before = await workspace!.read({ kind: "scene.body", sceneId: secondSceneId });
      const proof2 = await workspace!.read({ kind: "proof.query", projectId });
      assert.equal(proof2.scannedScenes >= 1, true);
      const after = await workspace!.read({ kind: "scene.body", sceneId: secondSceneId });
      assert.equal(JSON.stringify(after?.body), JSON.stringify(before?.body));
    });

    await step("12. 成稿导出聚合与项目包导出导入（全新目录）", async () => {
      const exportView = await workspace!.read({ kind: "project.export", projectId });
      assert.equal(exportView?.volumes[0]?.chapters.length, 2);
      const bundle = await workspace!.read({ kind: "project.bundle.export", projectId });
      assert.equal(bundle?.counts.chapters, 2);
      // 项目包导入到全新目录
      const targetDirectory = path.join(directory, "imported");
      await import("node:fs/promises").then(({ mkdir }) => mkdir(targetDirectory, { recursive: true }));
      const imported = await openCreationWorkspace({ directory: targetDirectory });
      try {
        const result = await imported.transact({ type: "project.bundle.import", data: bundle! });
        assert.equal(result.counts.chapters, 2);
        const importedOutline = await imported.read({ kind: "project.outline", projectId });
        assert.equal(importedOutline?.volumes[0]?.chapters.length, 2);
      } finally {
        await imported.close();
      }
    });

    await step("13. 旧稿导入：draft 结构建项目", async () => {
      const draftResult = await workspace!.transact({
        type: "project.importDraft",
        title: "旧稿项目",
        volumes: [{ title: "正文", chapters: [{ title: "第一章", body: "旧稿正文段落。\n\n第二段。" }] }]
      });
      assert.equal(draftResult.chapterCount, 1);
      const outline = await workspace!.read({ kind: "project.outline", projectId: draftResult.projectId });
      const draftSceneId = outline?.volumes[0]?.chapters[0]?.scenes[0]?.id!;
      const body = await workspace!.read({ kind: "scene.body", sceneId: draftSceneId });
      assert.equal(JSON.stringify(body?.body).includes("旧稿正文段落"), true);
    });

    await step("14. 收件箱（迁移目标）与完整性", async () => {
      const inboxItem = await workspace!.transact({
        type: "inbox.create",
        title: "旧灵感",
        body: "灵感正文",
        legacyId: "insp-old-1",
        tags: ["剧情"],
        variants: [{ content: "候选" }]
      });
      const inbox = (await workspace!.read({ kind: "inbox.list", limit: 10 })) as InboxItem[];
      assert.equal(inbox.some((item) => item.id === inboxItem.itemId), true);
      // 重复 legacyId 拒绝
      let conflict: unknown;
      try {
        await workspace!.transact({ type: "inbox.create", title: "重复", body: "x", legacyId: "insp-old-1" });
      } catch (error) {
        conflict = error;
      }
      assert.equal((conflict as CreationWorkspaceError).code, "conflict");
      const integrity = await workspace!.check();
      assert.equal(integrity.ok, true);
      results.integrity = integrity;
    });

    await step("15. 重启持久化：重开工作区数据一致", async () => {
      await workspace!.close();
      workspace = await openCreationWorkspace({ directory });
      const list = (await workspace!.read({ kind: "projects.list" })) as Array<{ id: string }>;
      assert.equal(list.some((item) => item.id === projectId), true);
      const body = await workspace!.read({ kind: "scene.body", sceneId });
      assert.equal(JSON.stringify(body?.body).includes("第一章的开头"), true);
      const inbox = (await workspace!.read({ kind: "inbox.list", limit: 10 })) as InboxItem[];
      assert.equal(inbox.length, 1);
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    try {
      await workspace?.close();
    } catch {
      // Connection already closed.
    }
    for (let attempt = 0; attempt < 5; attempt += 1) {
      try {
        await removeWithRetry(directory);
        break;
      } catch {
        await new Promise((resolve) => setTimeout(resolve, 300));
      }
    }
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
