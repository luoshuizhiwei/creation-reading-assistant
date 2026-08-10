import { strict as assert } from "node:assert";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CardSummary,
  type CreationStructureResult,
  type CreationWorkspace,
  type TrashItem
} from "./index";

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-history-"));
  let workspace: CreationWorkspace | undefined;
  let tests = 0;
  let created: { projectId: string; volumeId: string; chapterId: string; sceneId: string };
  let secondSceneId = "";
  try {
    const scenario = async <T>(name: string, fn: () => Promise<T>): Promise<T> => {
      try {
        const result = await fn();
        tests += 1;
        return result;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    workspace = await openCreationWorkspace({ directory });
    const report = await workspace.check();
    assert.equal(report.ok, true);

    await scenario("准备项目并写入正文", async () => {
      const result = await workspace!.transact({ type: "project.create", title: "测试项目" });
      created = { ...result, volumeId: result.volumeId, chapterId: result.chapterId, sceneId: result.sceneId };
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: created.sceneId,
        baseRevision: 1,
        body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "原稿内容" }] }] }
      });
      const extra = await workspace!.transact({
        type: "scene.create",
        chapterId: created.chapterId,
        title: "第二场景"
      }) as CreationStructureResult;
      secondSceneId = extra.entityId;
    });

    await scenario("场景删除进回收站，可恢复且正文保留", async () => {
      await workspace!.transact({ type: "scene.delete", sceneId: created.sceneId });
      const trash = (await workspace!.read({ kind: "trash.list", projectId: created.projectId })) as TrashItem[];
      assert.equal(trash.some((item) => item.entity === "scene" && item.id === created.sceneId), true);
      await workspace!.transact({
        type: "trash.restore",
        projectId: created.projectId,
        entity: "scene",
        entityId: created.sceneId
      });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const scene = outline.volumes[0]?.chapters[0]?.scenes.find((s) => s.id === created.sceneId);
      assert.equal(scene !== undefined, true);
      const bodyView = await workspace!.read({ kind: "scene.body", sceneId: created.sceneId });
      assert.equal(JSON.stringify(bodyView?.body).includes("原稿内容"), true);
    });

    await scenario("章节删除级联场景，恢复章节连带场景", async () => {
      await workspace!.transact({ type: "chapter.delete", chapterId: created.chapterId });
      const trash = (await workspace!.read({ kind: "trash.list", projectId: created.projectId })) as TrashItem[];
      assert.equal(trash.some((item) => item.entity === "chapter" && item.id === created.chapterId), true);
      assert.equal(trash.some((item) => item.entity === "scene" && item.id === created.sceneId), true);
      await workspace!.transact({
        type: "trash.restore",
        projectId: created.projectId,
        entity: "chapter",
        entityId: created.chapterId
      });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const chapter = outline.volumes[0]?.chapters.find((c) => c.id === created.chapterId);
      assert.equal(chapter?.scenes.length, 2);
    });

    await scenario("卷删除进回收站，可整体恢复", async () => {
      const volume = await workspace!.transact({ type: "volume.create", projectId: created.projectId, title: "待删卷" }) as CreationStructureResult;
      await workspace!.transact({ type: "volume.delete", volumeId: volume.entityId });
      await workspace!.transact({ type: "trash.restore", projectId: created.projectId, entity: "volume", entityId: volume.entityId });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      assert.equal(outline.volumes.some((v) => v.id === volume.entityId), true);
    });

    await scenario("卡片删除进回收站，可恢复", async () => {
      const card = await workspace!.transact({ type: "card.create", projectId: created.projectId, kind: "character", title: "苏青" }) as CreationStructureResult;
      await workspace!.transact({ type: "card.delete", cardId: card.entityId });
      const trash = (await workspace!.read({ kind: "trash.list", projectId: created.projectId })) as TrashItem[];
      assert.equal(trash.some((item) => item.entity === "card" && item.id === card.entityId), true);
      await workspace!.transact({ type: "trash.restore", projectId: created.projectId, entity: "card", entityId: card.entityId });
      const cards = (await workspace!.read({ kind: "cards.list", projectId: created.projectId })) as CardSummary[];
      assert.equal(cards.some((c) => c.id === card.entityId && c.title === "苏青"), true);
    });

    await scenario("永久删除从回收站移除并真正删除", async () => {
      await workspace!.transact({ type: "scene.delete", sceneId: secondSceneId });
      await workspace!.transact({ type: "trash.purge", projectId: created.projectId, entity: "scene", entityId: secondSceneId });
      const trash = (await workspace!.read({ kind: "trash.list", projectId: created.projectId })) as TrashItem[];
      assert.equal(trash.some((item) => item.id === secondSceneId), false);
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const chapter = outline.volumes[0]?.chapters.find((c) => c.id === created.chapterId);
      assert.equal(chapter?.scenes.some((s) => s.id === secondSceneId), false);
      let notFound: unknown;
      try {
        await workspace!.transact({ type: "trash.purge", projectId: created.projectId, entity: "scene", entityId: secondSceneId });
      } catch (error) {
        notFound = error;
      }
      assert.equal((notFound as CreationWorkspaceError).code, "not-found");
    });

    await scenario("场景命名快照：创建、列表、恢复正文", async () => {
      const before = await workspace!.read({ kind: "scene.body", sceneId: created.sceneId });
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: created.sceneId,
        baseRevision: before!.revision,
        body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "修改后的稿子" }] }] }
      });
      await workspace!.transact({
        type: "snapshot.create",
        projectId: created.projectId,
        subjectType: "scene",
        subjectId: created.sceneId,
        reason: "初稿完成"
      });
      const snapshots = (await workspace!.read({
        kind: "snapshot.list",
        projectId: created.projectId,
        subjectType: "scene",
        subjectId: created.sceneId
      })) as Array<{ id: string; reason: string; subjectType: string }>;
      assert.equal(snapshots.length, 1);
      assert.equal(snapshots[0]?.reason, "初稿完成");
      // 改稿后再恢复
      const after = await workspace!.read({ kind: "scene.body", sceneId: created.sceneId });
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: created.sceneId,
        baseRevision: after!.revision,
        body: { type: "doc", content: [] }
      });
      await workspace!.transact({ type: "snapshot.restore", projectId: created.projectId, snapshotId: snapshots[0]!.id });
      const bodyView = await workspace!.read({ kind: "scene.body", sceneId: created.sceneId });
      assert.equal(JSON.stringify(bodyView?.body).includes("修改后的稿子"), true);
    });

    await scenario("卡片命名快照：恢复标题与字段", async () => {
      const card = await workspace!.transact({ type: "card.create", projectId: created.projectId, kind: "character", title: "顾淮", fields: { note: "原名" } }) as CreationStructureResult;
      await workspace!.transact({ type: "snapshot.create", projectId: created.projectId, subjectType: "card", subjectId: card.entityId, reason: "定稿" });
      const snapshots = (await workspace!.read({ kind: "snapshot.list", projectId: created.projectId, subjectType: "card", subjectId: card.entityId })) as Array<{ id: string }>;
      await workspace!.transact({ type: "card.update", cardId: card.entityId, title: "顾淮·改", baseRevision: 1 });
      await workspace!.transact({ type: "snapshot.restore", projectId: created.projectId, snapshotId: snapshots[0]!.id });
      const restored = (await workspace!.read({ kind: "card.read", cardId: card.entityId })) as CardSummary;
      assert.equal(restored.title, "顾淮");
      assert.equal(restored.fields.note, "原名");
    });

    await scenario("恢复不存在的回收站实体报错", async () => {
      let notFound: unknown;
      try {
        await workspace!.transact({ type: "trash.restore", projectId: created.projectId, entity: "scene", entityId: "scene-missing" });
      } catch (error) {
        notFound = error;
      }
      assert.equal((notFound as CreationWorkspaceError).code, "not-found");
    });

    await scenario("回收站到期自动清理（超过 30 天）", async () => {
      const expired = await workspace!.transact({ type: "scene.create", chapterId: created.chapterId, title: "过期场景" }) as CreationStructureResult;
      await workspace!.transact({ type: "scene.delete", sceneId: expired.entityId });
      // 手工把 deleted_at 改到 40 天前，模拟到期
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      const oldTime = new Date(Date.now() - 40 * 24 * 60 * 60 * 1000).toISOString();
      raw.prepare("UPDATE scenes SET deleted_at = ? WHERE id = ?").run(oldTime, expired.entityId);
      raw.close();
      // 关闭后重新打开，触发到期清理
      await workspace!.close();
      workspace = await openCreationWorkspace({ directory });
      const trash = (await workspace!.read({ kind: "trash.list", projectId: created.projectId })) as TrashItem[];
      assert.equal(trash.some((item) => item.id === expired.entityId), false);
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await workspace?.close();
    await rm(directory, { recursive: true, force: true });
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
