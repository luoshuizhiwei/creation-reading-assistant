import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationProjectOutline,
  type CreationStructureResult,
  type ProtectedStructureCommand,
  type StructureAffectedObject,
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

    const expectWorkspaceError = async (
      operation: () => Promise<unknown>,
      code: CreationWorkspaceError["code"]
    ): Promise<void> => {
      let received: unknown;
      try {
        await operation();
      } catch (error) {
        received = error;
      }
      assert.equal(received instanceof CreationWorkspaceError, true);
      assert.equal((received as CreationWorkspaceError).code, code);
    };

    const outlineShape = (outline: CreationProjectOutline): unknown => ({
      volumes: outline.volumes.map((volume) => ({
        id: volume.id,
        title: volume.title,
        sortOrder: volume.sortOrder,
        chapters: volume.chapters.map((chapter) => ({
          id: chapter.id,
          title: chapter.title,
          sortOrder: chapter.sortOrder,
          status: chapter.status,
          numbering: chapter.numbering,
          customNumber: chapter.customNumber,
          scenes: chapter.scenes.map((scene) => ({
            id: scene.id,
            title: scene.title,
            sortOrder: scene.sortOrder
          }))
        }))
      })),
      looseChapters: outline.looseChapters.map((chapter) => ({
        id: chapter.id,
        title: chapter.title,
        sortOrder: chapter.sortOrder,
        status: chapter.status,
        numbering: chapter.numbering,
        customNumber: chapter.customNumber,
        scenes: chapter.scenes.map((scene) => ({
          id: scene.id,
          title: scene.title,
          sortOrder: scene.sortOrder
        }))
      }))
    });

    const previewAndApply = async (
      projectId: string,
      command: ProtectedStructureCommand,
      reason: string
    ) => {
      const preview = await workspace!.previewStructure({
        type: "structure.preview",
        projectId,
        command
      });
      assert.equal(preview.ok, true);
      assert.equal(preview.command.type, command.type);
      assert.equal(preview.stale, false);
      assert.ok(preview.rows.length > 0);
      assert.equal(typeof preview.planId, "string");
      assert.ok(preview.planId.length > 0);
      const applied = await workspace!.applyStructure({
        type: "structure.applyWithProtection",
        projectId,
        planId: preview.planId,
        protectionReason: reason
      });
      assert.equal(applied.ok, true);
      assert.ok(applied.protectionSnapshotId.length > 0);
      assert.ok(applied.affected.length > 0);
      assert.equal(
        applied.affected.every(
          (item) =>
            (item.type === "volume" || item.type === "chapter" || item.type === "scene") &&
            item.id.length > 0 &&
            Number.isInteger(item.revision) &&
            item.revision > 0
        ),
        true
      );
      return { preview, applied };
    };

    const revertApplied = async (
      projectId: string,
      protectionSnapshotId: string,
      expectedAppliedRevisions: StructureAffectedObject[]
    ) =>
      workspace!.revertStructure({
        type: "structure.revert",
        projectId,
        protectionSnapshotId,
        expectedAppliedRevisions
      });

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
      assert.equal(scene?.wordCount, 8);
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
      assert.equal(report.schemaVersion, 9);
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

    await scenario("保护重组 chapter.split：权威预览、一次性应用、精确撤回", async () => {
      const project = await workspace!.transact({ type: "project.create", title: "拆章合约" });
      const sourceScene = (await workspace!.transact({
        type: "scene.create",
        chapterId: project.chapterId,
        title: "拆分起点"
      })) as CreationStructureResult;
      const trailingScene = (await workspace!.transact({
        type: "scene.create",
        chapterId: project.chapterId,
        title: "拆分后场景"
      })) as CreationStructureResult;
      const sourceBeforeEdit = (await workspace!.read({ kind: "scene.body", sceneId: sourceScene.entityId }))!;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: sourceScene.entityId,
        baseRevision: sourceBeforeEdit.revision,
        body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "提高版本后再拆章" }] }] }
      });
      const committedEvents: Array<{ commandType: string; changes: Array<{ entity: string; id: string; revision: number }> }> = [];
      const stopWatching = workspace!.watch({ projectId: project.projectId }, (event) => {
        committedEvents.push(event);
      });
      const before = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      const { preview, applied } = await previewAndApply(
        project.projectId,
        {
          type: "chapter.split",
          chapterId: project.chapterId,
          splitSceneId: sourceScene.entityId,
          newChapterTitle: "拆分结果"
        },
        "拆章保护"
      );
      assert.equal(preview.affectedSceneCount, 2);
      const changed = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      const chapters = changed.volumes[0]?.chapters ?? [];
      const splitChapter = chapters.find((chapter) => chapter.title === "拆分结果");
      assert.equal(chapters.find((chapter) => chapter.id === project.chapterId)?.scenes.length, 1);
      assert.deepEqual(splitChapter?.scenes.map((scene) => scene.id), [sourceScene.entityId, trailingScene.entityId]);
      const protectedEvent = committedEvents.find((event) => event.commandType === "structure.applyWithProtection");
      assert.ok(protectedEvent);
      assert.deepEqual(
        protectedEvent!.changes.map((change) => `${change.entity}:${change.id}`).sort(),
        applied.affected.map((change) => `${change.type}:${change.id}`).sort()
      );
      const movedSourceEvent = protectedEvent!.changes.find((change) => change.entity === "scene" && change.id === sourceScene.entityId);
      const movedSource = splitChapter?.scenes.find((scene) => scene.id === sourceScene.entityId);
      assert.equal(movedSourceEvent?.revision, movedSource?.revision);
      assert.ok((movedSourceEvent?.revision ?? 0) > 1);
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      const logged = raw.prepare(
        "SELECT changes_json FROM change_log WHERE project_id = ? AND command_type = 'structure.applyWithProtection' ORDER BY sequence DESC LIMIT 1"
      ).get(project.projectId) as { changes_json: string };
      raw.close();
      assert.deepEqual(JSON.parse(logged.changes_json), protectedEvent!.changes);
      stopWatching();
      const reverted = await revertApplied(project.projectId, applied.protectionSnapshotId, applied.affected);
      assert.equal(reverted.ok, true);
      const after = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.deepEqual(outlineShape(after), outlineShape(before));
    });

    await scenario("保护重组 chapter.merge：软删源章可完整撤回", async () => {
      const project = await workspace!.transact({ type: "project.create", title: "并章合约" });
      const source = (await workspace!.transact({
        type: "chapter.create",
        projectId: project.projectId,
        volumeId: project.volumeId,
        title: "待并章节"
      })) as CreationStructureResult;
      await workspace!.transact({ type: "scene.create", chapterId: source.entityId, title: "待并场景" });
      const before = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      const { preview, applied } = await previewAndApply(
        project.projectId,
        { type: "chapter.merge", sourceChapterId: source.entityId, targetChapterId: project.chapterId },
        "并章保护"
      );
      assert.ok(preview.softDeletedChapter?.length);
      const changed = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.equal(changed.volumes[0]?.chapters.some((chapter) => chapter.id === source.entityId), false);
      assert.equal(changed.volumes[0]?.chapters[0]?.scenes.length, 2);
      await revertApplied(project.projectId, applied.protectionSnapshotId, applied.affected);
      const after = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.deepEqual(outlineShape(after), outlineShape(before));
    });

    await scenario("保护重组 chapter.move：跨卷顺序可完整撤回", async () => {
      const project = await workspace!.transact({ type: "project.create", title: "移章合约" });
      const volume = (await workspace!.transact({
        type: "volume.create",
        projectId: project.projectId,
        title: "目标卷"
      })) as CreationStructureResult;
      const targetChapter = (await workspace!.transact({
        type: "chapter.create",
        projectId: project.projectId,
        volumeId: volume.entityId,
        title: "目标章"
      })) as CreationStructureResult;
      const before = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      const { applied } = await previewAndApply(
        project.projectId,
        {
          type: "chapter.move",
          chapterId: project.chapterId,
          targetVolumeId: volume.entityId,
          beforeChapterId: targetChapter.entityId
        },
        "移章保护"
      );
      const changed = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.equal(changed.volumes[0]?.chapters.length, 0);
      assert.deepEqual(changed.volumes[1]?.chapters.map((chapter) => chapter.id), [project.chapterId, targetChapter.entityId]);
      await revertApplied(project.projectId, applied.protectionSnapshotId, applied.affected);
      const after = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.deepEqual(outlineShape(after), outlineShape(before));
    });

    await scenario("保护重组 scene.move：跨章顺序可完整撤回", async () => {
      const project = await workspace!.transact({ type: "project.create", title: "移场景合约" });
      const targetChapter = (await workspace!.transact({
        type: "chapter.create",
        projectId: project.projectId,
        volumeId: project.volumeId,
        title: "目标章"
      })) as CreationStructureResult;
      const targetScene = (await workspace!.transact({
        type: "scene.create",
        chapterId: targetChapter.entityId,
        title: "目标场景"
      })) as CreationStructureResult;
      const before = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      const { applied } = await previewAndApply(
        project.projectId,
        {
          type: "scene.move",
          sceneId: project.sceneId,
          targetChapterId: targetChapter.entityId,
          beforeSceneId: targetScene.entityId
        },
        "移场景保护"
      );
      const changed = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.deepEqual(changed.volumes[0]?.chapters[1]?.scenes.map((scene) => scene.id), [project.sceneId, targetScene.entityId]);
      await revertApplied(project.projectId, applied.protectionSnapshotId, applied.affected);
      const after = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.deepEqual(outlineShape(after), outlineShape(before));
    });

    await scenario("保护重组 chapters.setStatus：批量状态可完整撤回", async () => {
      const project = await workspace!.transact({ type: "project.create", title: "状态合约" });
      const chapter = (await workspace!.transact({
        type: "chapter.create",
        projectId: project.projectId,
        volumeId: project.volumeId,
        title: "第二章"
      })) as CreationStructureResult;
      const before = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      const { preview, applied } = await previewAndApply(
        project.projectId,
        { type: "chapters.setStatus", chapterIds: [project.chapterId, chapter.entityId], status: "定稿" },
        "批量状态保护"
      );
      assert.ok(preview.rows.some((row) => row.value.includes("定稿")));
      const changed = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.deepEqual(changed.volumes[0]?.chapters.map((item) => item.status), ["定稿", "定稿"]);
      await revertApplied(project.projectId, applied.protectionSnapshotId, applied.affected);
      const after = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.deepEqual(outlineShape(after), outlineShape(before));
    });

    await scenario("保护重组 chapter.setNumbering：编号可完整撤回", async () => {
      const project = await workspace!.transact({ type: "project.create", title: "编号合约" });
      const before = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      const { preview, applied } = await previewAndApply(
        project.projectId,
        {
          type: "chapter.setNumbering",
          chapterId: project.chapterId,
          numbering: "custom",
          customNumber: "楔子",
          baseRevision: before.volumes[0]!.chapters[0]!.revision
        },
        "编号保护"
      );
      assert.ok(preview.numberingChange?.length);
      const changed = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.equal(changed.volumes[0]?.chapters[0]?.displayNumber, "楔子");
      await revertApplied(project.projectId, applied.protectionSnapshotId, applied.affected);
      const after = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.deepEqual(outlineShape(after), outlineShape(before));
    });

    await scenario("保护计划不可伪造、重复消费或过期使用", async () => {
      const project = await workspace!.transact({ type: "project.create", title: "计划令牌合约" });
      await expectWorkspaceError(
        () =>
          workspace!.applyStructure({
            type: "structure.applyWithProtection",
            projectId: project.projectId,
            planId: "forged-plan-id",
            protectionReason: "伪造计划"
          }),
        "conflict"
      );

      const once = await workspace!.previewStructure({
        type: "structure.preview",
        projectId: project.projectId,
        command: { type: "chapters.setStatus", chapterIds: [project.chapterId], status: "修订" }
      });
      await workspace!.applyStructure({
        type: "structure.applyWithProtection",
        projectId: project.projectId,
        planId: once.planId,
        protectionReason: "一次性计划"
      });
      await expectWorkspaceError(
        () =>
          workspace!.applyStructure({
            type: "structure.applyWithProtection",
            projectId: project.projectId,
            planId: once.planId,
            protectionReason: "重复消费"
          }),
        "conflict"
      );

      const expiring = await workspace!.previewStructure({
        type: "structure.preview",
        projectId: project.projectId,
        command: { type: "chapters.setStatus", chapterIds: [project.chapterId], status: "定稿" }
      });
      const actualNow = Date.now;
      const simulatedExpiry = actualNow() + 60 * 60 * 1000;
      Date.now = () => simulatedExpiry;
      try {
        await expectWorkspaceError(
          () =>
            workspace!.applyStructure({
              type: "structure.applyWithProtection",
              projectId: project.projectId,
              planId: expiring.planId,
              protectionReason: "过期计划"
            }),
          "conflict"
        );
      } finally {
        Date.now = actualNow;
      }
    });

    await scenario("预览后相关结构变化会使旧 plan 冲突", async () => {
      const project = await workspace!.transact({ type: "project.create", title: "过期预览合约" });
      const preview = await workspace!.previewStructure({
        type: "structure.preview",
        projectId: project.projectId,
        command: { type: "chapters.setStatus", chapterIds: [project.chapterId], status: "定稿" }
      });
      const outline = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      await workspace!.transact({
        type: "chapter.rename",
        chapterId: project.chapterId,
        title: "预览后已变化",
        baseRevision: outline.volumes[0]!.chapters[0]!.revision
      });
      await expectWorkspaceError(
        () =>
          workspace!.applyStructure({
            type: "structure.applyWithProtection",
            projectId: project.projectId,
            planId: preview.planId,
            protectionReason: "旧计划"
          }),
        "conflict"
      );
    });

    await scenario("apply 失败时保护快照、change_log 与结构一起回滚", async () => {
      const project = await workspace!.transact({ type: "project.create", title: "原子回滚合约" });
      const splitScene = (await workspace!.transact({
        type: "scene.create",
        chapterId: project.chapterId,
        title: "故障拆分点"
      })) as CreationStructureResult;
      const before = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      const preview = await workspace!.previewStructure({
        type: "structure.preview",
        projectId: project.projectId,
        command: {
          type: "chapter.split",
          chapterId: project.chapterId,
          splitSceneId: splitScene.entityId,
          newChapterTitle: "不应残留的半成品"
        }
      });
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      const snapshotCountBefore = (
        raw.prepare("SELECT count(*) AS count FROM snapshots WHERE project_id = ? AND subject_type = 'structure-operation'").get(project.projectId) as {
          count: number;
        }
      ).count;
      const changeCountBefore = (
        raw.prepare("SELECT count(*) AS count FROM change_log WHERE project_id = ?").get(project.projectId) as { count: number }
      ).count;
      raw.exec(`
        CREATE TRIGGER fail_protected_split_apply
        BEFORE UPDATE OF chapter_id ON scenes
        WHEN OLD.id = '${splitScene.entityId}' AND NEW.chapter_id <> OLD.chapter_id
        BEGIN
          SELECT RAISE(ABORT, 'injected protected apply failure');
        END;
      `);
      try {
        let applyError: unknown;
        try {
          await workspace!.applyStructure({
            type: "structure.applyWithProtection",
            projectId: project.projectId,
            planId: preview.planId,
            protectionReason: "事务故障注入"
          });
        } catch (error) {
          applyError = error;
        }
        assert.ok(applyError);
      } finally {
        raw.exec("DROP TRIGGER IF EXISTS fail_protected_split_apply");
      }
      const snapshotCountAfter = (
        raw.prepare("SELECT count(*) AS count FROM snapshots WHERE project_id = ? AND subject_type = 'structure-operation'").get(project.projectId) as {
          count: number;
        }
      ).count;
      const changeCountAfter = (
        raw.prepare("SELECT count(*) AS count FROM change_log WHERE project_id = ?").get(project.projectId) as { count: number }
      ).count;
      raw.close();
      assert.equal(snapshotCountAfter, snapshotCountBefore);
      assert.equal(changeCountAfter, changeCountBefore);
      const after = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.deepEqual(outlineShape(after), outlineShape(before));
    });

    await scenario("apply 后受影响对象再修改会阻止 revert", async () => {
      const project = await workspace!.transact({ type: "project.create", title: "撤回冲突合约" });
      const { applied } = await previewAndApply(
        project.projectId,
        { type: "chapters.setStatus", chapterIds: [project.chapterId], status: "定稿" },
        "撤回冲突保护"
      );
      const changed = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      await workspace!.transact({
        type: "chapter.rename",
        chapterId: project.chapterId,
        title: "应用后人工修改",
        baseRevision: changed.volumes[0]!.chapters[0]!.revision
      });
      await expectWorkspaceError(
        () => revertApplied(project.projectId, applied.protectionSnapshotId, applied.affected),
        "conflict"
      );
    });

    await scenario("revert 校验版本集合，不覆盖不相关修改且快照仅能撤回一次", async () => {
      const project = await workspace!.transact({ type: "project.create", title: "精确撤回合约" });
      const unrelated = (await workspace!.transact({
        type: "chapter.create",
        projectId: project.projectId,
        volumeId: project.volumeId,
        title: "不相关章节"
      })) as CreationStructureResult;
      const before = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      const target = before.volumes[0]!.chapters.find((chapter) => chapter.id === project.chapterId)!;
      const { applied } = await previewAndApply(
        project.projectId,
        {
          type: "chapter.setNumbering",
          chapterId: project.chapterId,
          numbering: "prologue",
          baseRevision: target.revision
        },
        "精确撤回保护"
      );
      const forgedRevisions = applied.affected.map((item, index) =>
        index === 0 ? { ...item, revision: item.revision + 1 } : item
      );
      await expectWorkspaceError(
        () => revertApplied(project.projectId, applied.protectionSnapshotId, forgedRevisions),
        "conflict"
      );

      const beforeUnrelatedRename = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      const unrelatedNow = beforeUnrelatedRename.volumes[0]!.chapters.find((chapter) => chapter.id === unrelated.entityId)!;
      await workspace!.transact({
        type: "chapter.rename",
        chapterId: unrelated.entityId,
        title: "必须保留的人工修改",
        baseRevision: unrelatedNow.revision
      });
      const reverted = await revertApplied(project.projectId, applied.protectionSnapshotId, applied.affected);
      assert.equal(reverted.ok, true);
      const after = (await workspace!.read({ kind: "project.outline", projectId: project.projectId }))!;
      assert.equal(after.volumes[0]?.chapters.find((chapter) => chapter.id === project.chapterId)?.numbering, target.numbering);
      assert.equal(
        after.volumes[0]?.chapters.find((chapter) => chapter.id === unrelated.entityId)?.title,
        "必须保留的人工修改"
      );
      await expectWorkspaceError(
        () => revertApplied(project.projectId, applied.protectionSnapshotId, applied.affected),
        "conflict"
      );
    });

    const report = await workspace.check();
    assert.equal(report.ok, true);
    // counts 统计表行数（含软删除）：基础流程 3 卷 + 保护重组合约各自的独立 fixture。
    assert.equal(report.counts.volumes, 15);

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await workspace?.close();
    await removeWithRetry(directory);
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
