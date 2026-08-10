import { strict as assert } from "node:assert";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationProjectOutline,
  type CreationStructureResult,
  type CreationWorkspace
} from "./index";

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-outline-"));
  let workspace: CreationWorkspace | undefined;
  let tests = 0;
  let created: { projectId: string; volumeId: string; chapterId: string; sceneId: string };
  let secondVolumeId = "";
  let secondChapterId = "";
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
    const initialReport = await workspace.check();
    assert.equal(initialReport.ok, true);

    await scenario("创建项目生成默认卷/章/场景，outline 正确", async () => {
      const result = await workspace!.transact({ type: "project.create", title: "测试项目" });
      created = { ...result, volumeId: result.volumeId, chapterId: result.chapterId, sceneId: result.sceneId };
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      assert.equal(outline.volumes.length, 1);
      assert.equal(outline.volumes[0]?.title, "正文");
      assert.equal(outline.volumes[0]?.chapters.length, 1);
      assert.equal(outline.volumes[0]?.chapters[0]?.title, "第一章");
      assert.equal(outline.volumes[0]?.chapters[0]?.displayNumber, "第1章");
      assert.equal(outline.volumes[0]?.chapters[0]?.scenes.length, 1);
      assert.equal(outline.volumes[0]?.chapters[0]?.scenes[0]?.wordCount, 0);
      assert.equal(outline.looseChapters.length, 0);
    });

    await scenario("第二卷/新章/新场景可创建", async () => {
      const volume = await workspace!.transact({
        type: "volume.create",
        projectId: created.projectId,
        title: "第二卷"
      }) as CreationStructureResult;
      secondVolumeId = volume.entityId;
      const chapter = await workspace!.transact({
        type: "chapter.create",
        projectId: created.projectId,
        volumeId: secondVolumeId,
        title: "新章节"
      }) as CreationStructureResult;
      secondChapterId = chapter.entityId;
      const scene = await workspace!.transact({
        type: "scene.create",
        chapterId: secondChapterId,
        title: "新场景"
      }) as CreationStructureResult;
      secondSceneId = scene.entityId;
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      assert.equal(outline.volumes.length, 2);
      assert.equal(outline.volumes[1]?.title, "第二卷");
      assert.equal(outline.volumes[1]?.chapters[0]?.title, "新章节");
      assert.equal(outline.volumes[1]?.chapters[0]?.scenes[0]?.title, "新场景");
      assert.equal(outline.volumes[1]?.chapters[0]?.displayNumber, "第1章");
    });

    await scenario("正文写入后 outline 字数更新", async () => {
      const body = {
        type: "doc" as const,
        content: [{ type: "paragraph", content: [{ type: "text", text: "雨落在旧城墙上。" }] }]
      };
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: created.sceneId,
        baseRevision: 1,
        body
      });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const scene = outline.volumes[0]?.chapters[0]?.scenes[0];
      assert.equal(scene?.wordCount, 7);
      assert.equal(scene?.revision, 2);
    });

    await scenario("卷/章/场景可改名（含 revision 校验）", async () => {
      await workspace!.transact({ type: "volume.rename", volumeId: secondVolumeId, title: "终卷", baseRevision: 1 });
      await workspace!.transact({ type: "chapter.rename", chapterId: secondChapterId, title: "终章", baseRevision: 1 });
      await workspace!.transact({ type: "scene.rename", sceneId: secondSceneId, title: "终景", baseRevision: 1 });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      assert.equal(outline.volumes[1]?.title, "终卷");
      assert.equal(outline.volumes[1]?.chapters[0]?.title, "终章");
      assert.equal(outline.volumes[1]?.chapters[0]?.scenes[0]?.title, "终景");
    });

    await scenario("卷重排：把第二卷移到第一之前", async () => {
      await workspace!.transact({
        type: "volume.reorder",
        volumeId: secondVolumeId,
        beforeVolumeId: created.volumeId
      });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      assert.equal(outline.volumes[0]?.id, secondVolumeId);
      assert.equal(outline.volumes[1]?.id, created.volumeId);
    });

    await scenario("章节跨卷移动", async () => {
      await workspace!.transact({
        type: "chapter.move",
        chapterId: secondChapterId,
        targetVolumeId: created.volumeId,
        beforeChapterId: created.chapterId
      });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const target = outline.volumes.find((item) => item.id === created.volumeId);
      assert.equal(target?.chapters.length, 2);
      assert.equal(target?.chapters[0]?.id, secondChapterId);
      assert.equal(target?.chapters[0]?.displayNumber, "第1章");
      assert.equal(target?.chapters[1]?.id, created.chapterId);
      assert.equal(target?.chapters[1]?.displayNumber, "第2章");
      const source = outline.volumes.find((item) => item.id === secondVolumeId);
      assert.equal(source?.chapters.length, 0);
    });

    await scenario("场景跨章移动", async () => {
      await workspace!.transact({
        type: "scene.move",
        sceneId: secondSceneId,
        targetChapterId: created.chapterId
      });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const chapter = outline.volumes.find((item) => item.id === created.volumeId)?.chapters.find(
        (item) => item.id === created.chapterId
      );
      assert.equal(chapter?.scenes.length, 2);
      assert.equal(chapter?.scenes[0]?.id, created.sceneId);
      assert.equal(chapter?.scenes[1]?.id, secondSceneId);
    });

    await scenario("章节状态更新：有效通过、无效拒绝", async () => {
      await workspace!.transact({
        type: "chapter.setStatus",
        chapterId: created.chapterId,
        status: "写作中",
        baseRevision: 1
      });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const chapter = outline.volumes.find((item) => item.id === created.volumeId)?.chapters.find(
        (item) => item.id === created.chapterId
      );
      assert.equal(chapter?.status, "写作中");
      let invalidError: unknown;
      try {
        await workspace!.transact({
          type: "chapter.setStatus",
          chapterId: created.chapterId,
          status: "不存在阶段",
          baseRevision: 2
        });
      } catch (error) {
        invalidError = error;
      }
      assert.equal(invalidError instanceof CreationWorkspaceError, true);
      assert.equal((invalidError as CreationWorkspaceError).code, "invalid-input");
    });

    await scenario("章节编号：序章与自定义覆盖", async () => {
      await workspace!.transact({
        type: "chapter.setNumbering",
        chapterId: created.chapterId,
        numbering: "prologue",
        baseRevision: 2
      });
      let outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      let chapter = outline.volumes.find((item) => item.id === created.volumeId)?.chapters.find(
        (item) => item.id === created.chapterId
      );
      assert.equal(chapter?.displayNumber, "序章");
      assert.equal(chapter?.numbering, "prologue");
      await workspace!.transact({
        type: "chapter.setNumbering",
        chapterId: created.chapterId,
        numbering: "custom",
        customNumber: "楔子",
        baseRevision: 3
      });
      outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      chapter = outline.volumes.find((item) => item.id === created.volumeId)?.chapters.find(
        (item) => item.id === created.chapterId
      );
      assert.equal(chapter?.displayNumber, "楔子");
    });

    await scenario("场景软删除后 outline 不含", async () => {
      await workspace!.transact({ type: "scene.delete", sceneId: secondSceneId });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const chapter = outline.volumes.find((item) => item.id === created.volumeId)?.chapters.find(
        (item) => item.id === created.chapterId
      );
      assert.equal(chapter?.scenes.length, 1);
      assert.equal(chapter?.scenes.some((scene) => scene.id === secondSceneId), false);
    });

    await scenario("章节软删除级联场景", async () => {
      await workspace!.transact({ type: "chapter.delete", chapterId: created.chapterId });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const target = outline.volumes.find((item) => item.id === created.volumeId);
      assert.equal(target?.chapters.some((chapter) => chapter.id === created.chapterId), false);
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      const rows = raw
        .prepare("SELECT count(*) AS count FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL")
        .get(created.chapterId) as { count: number };
      raw.close();
      assert.equal(rows.count, 0);
    });

    await scenario("卷软删除级联章节", async () => {
      await workspace!.transact({ type: "volume.delete", volumeId: secondVolumeId });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      assert.equal(outline.volumes.length, 1);
      assert.equal(outline.volumes.some((volume) => volume.id === secondVolumeId), false);
    });

    await scenario("revision-mismatch 拒绝旧版本改名", async () => {
      let mismatchError: unknown;
      try {
        await workspace!.transact({
          type: "volume.rename",
          volumeId: created.volumeId,
          title: "不应生效",
          baseRevision: 99
        });
      } catch (error) {
        mismatchError = error;
      }
      assert.equal(mismatchError instanceof CreationWorkspaceError, true);
      assert.equal((mismatchError as CreationWorkspaceError).code, "revision-mismatch");
    });

    await scenario("目标不属于同项目被拒绝", async () => {
      const other = await workspace!.transact({ type: "project.create", title: "其他作品" });
      let invalidError: unknown;
      try {
        await workspace!.transact({
          type: "chapter.move",
          chapterId: secondChapterId,
          targetVolumeId: other.volumeId
        });
      } catch (error) {
        invalidError = error;
      }
      assert.equal(invalidError instanceof CreationWorkspaceError, true);
      assert.equal((invalidError as CreationWorkspaceError).code, "invalid-input");
    });

    await scenario("v2→v3 迁移：默认卷与章节归属", async () => {
      const v2Directory = path.join(directory, "v2-legacy");
      const { mkdir } = await import("node:fs/promises");
      await mkdir(v2Directory, { recursive: true });
      const raw = new Database(path.join(v2Directory, "workspace.sqlite"));
      raw.exec(`
        CREATE TABLE projects (
          id TEXT PRIMARY KEY, title TEXT NOT NULL, setup_json TEXT NOT NULL DEFAULT '{}',
          created_at TEXT NOT NULL, updated_at TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE chapters (
          id TEXT PRIMARY KEY, project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
          title TEXT NOT NULL, sort_order INTEGER NOT NULL,
          created_at TEXT NOT NULL, updated_at TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE scenes (
          id TEXT PRIMARY KEY, chapter_id TEXT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
          title TEXT NOT NULL, sort_order INTEGER NOT NULL,
          body_json TEXT NOT NULL DEFAULT '{"type":"doc","content":[]}',
          created_at TEXT NOT NULL, updated_at TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE cards (
          id TEXT PRIMARY KEY, project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
          kind TEXT NOT NULL, title TEXT NOT NULL, content_json TEXT NOT NULL DEFAULT '{}',
          created_at TEXT NOT NULL, updated_at TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE card_relations (
          id TEXT PRIMARY KEY, project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
          from_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
          to_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
          relation_type TEXT NOT NULL, created_at TEXT NOT NULL,
          UNIQUE(from_card_id, to_card_id, relation_type)
        );
        CREATE TABLE resources (
          id TEXT PRIMARY KEY, project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
          relative_path TEXT NOT NULL, sha256 TEXT NOT NULL, created_at TEXT NOT NULL
        );
        CREATE TABLE snapshots (
          id TEXT PRIMARY KEY, project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
          subject_type TEXT NOT NULL, subject_id TEXT NOT NULL, payload_json TEXT NOT NULL, created_at TEXT NOT NULL
        );
        CREATE TABLE change_log (
          sequence INTEGER PRIMARY KEY AUTOINCREMENT, project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
          command_type TEXT NOT NULL, changes_json TEXT NOT NULL, committed_at TEXT NOT NULL
        );
        PRAGMA user_version = 2;
      `);
      raw
        .prepare("INSERT INTO projects(id, title, setup_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")
        .run("project-v2", "旧项目", "{}", "2026-08-01T00:00:00.000Z", "2026-08-01T00:00:00.000Z");
      raw
        .prepare("INSERT INTO chapters(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
        .run("chapter-v2", "project-v2", "旧章", 0, "2026-08-01T00:00:00.000Z", "2026-08-01T00:00:00.000Z");
      raw
        .prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
        .run("scene-v2", "chapter-v2", "旧景", 0, '{"type":"doc","content":[]}', "2026-08-01T00:00:00.000Z", "2026-08-01T00:00:00.000Z");
      raw.close();

      const migrated = await openCreationWorkspace({ directory: v2Directory });
      const outline = (await migrated.read({ kind: "project.outline", projectId: "project-v2" }))!;
      assert.equal(outline.volumes.length, 1);
      assert.equal(outline.volumes[0]?.title, "正文");
      assert.equal(outline.volumes[0]?.chapters[0]?.id, "chapter-v2");
      assert.equal(outline.volumes[0]?.chapters[0]?.scenes[0]?.id, "scene-v2");
      assert.equal(outline.looseChapters.length, 0);
      const report = await migrated.check();
      assert.equal(report.schemaVersion, 7);
      assert.equal(report.counts.volumes, 1);
      await migrated.close();
    });

    await scenario("安全重组：拆章、并章、批量状态", async () => {
      const chapter = await workspace!.transact({
        type: "chapter.create",
        projectId: created.projectId,
        volumeId: created.volumeId,
        title: "重组章"
      }) as CreationStructureResult;
      const sceneA = await workspace!.transact({
        type: "scene.create",
        chapterId: chapter.entityId,
        title: "场景甲"
      }) as CreationStructureResult;
      const sceneB = await workspace!.transact({
        type: "scene.create",
        chapterId: chapter.entityId,
        title: "场景乙"
      }) as CreationStructureResult;
      await workspace!.transact({
        type: "scene.create",
        chapterId: chapter.entityId,
        title: "场景丙"
      }) as CreationStructureResult;
      const split = await workspace!.transact({
        type: "chapter.split",
        chapterId: chapter.entityId,
        splitSceneId: sceneB.entityId,
        newChapterTitle: "拆分章"
      }) as CreationStructureResult;
      let outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const source = outline.volumes.find((volume) => volume.id === created.volumeId)?.chapters.find(
        (item) => item.id === chapter.entityId
      );
      const target = outline.volumes.find((volume) => volume.id === created.volumeId)?.chapters.find(
        (item) => item.id === split.entityId
      );
      assert.equal(source?.scenes.map((scene) => scene.title).join(","), "场景甲");
      assert.equal(target?.scenes.map((scene) => scene.title).join(","), "场景乙,场景丙");
      assert.equal(target?.title, "拆分章");
      assert.equal(typeof source?.displayNumber, "string");
      assert.equal(typeof target?.displayNumber, "string");
      // 并章：拆分章场景并入重组章末尾
      await workspace!.transact({
        type: "chapter.merge",
        sourceChapterId: split.entityId,
        targetChapterId: chapter.entityId
      });
      outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const merged = outline.volumes.find((volume) => volume.id === created.volumeId)?.chapters.find(
        (item) => item.id === chapter.entityId
      );
      assert.equal(merged?.scenes.map((scene) => scene.title).join(","), "场景甲,场景乙,场景丙");
      assert.equal(
        outline.volumes.find((volume) => volume.id === created.volumeId)?.chapters.some(
          (item) => item.id === split.entityId
        ),
        false
      );
      // 批量状态
      await workspace!.transact({
        type: "chapters.setStatus",
        chapterIds: [chapter.entityId, secondChapterId],
        status: "修订"
      });
      outline = (await workspace!.read({ kind: "project.outline", projectId: created.projectId }))!;
      const chapters = outline.volumes.find((volume) => volume.id === created.volumeId)?.chapters;
      assert.equal(chapters?.find((item) => item.id === chapter.entityId)?.status, "修订");
      assert.equal(chapters?.find((item) => item.id === secondChapterId)?.status, "修订");
      assert.equal(sceneA.entityId !== undefined, true);
    });

    const report = await workspace.check();
    assert.equal(report.ok, true);
    // counts 统计表行数（含软删除）：created 默认卷 + created 第二卷（已软删除）+ other 默认卷
    assert.equal(report.counts.volumes, 3);

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
