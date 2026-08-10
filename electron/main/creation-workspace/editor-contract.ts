import { strict as assert } from "node:assert";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import { CreationWorkspaceError, openCreationWorkspace } from "./index";
import type {
  CreationProjectNavigation,
  CreationProjectSummary,
  SceneBodyView,
  UpdateSceneBodyResult
} from "../../../src/types/creation";
import { createCreationCoordinator } from "../creation-coordinator/index";

const minimalSetup = { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["起草"] };
const emptyDoc = { type: "doc" as const, content: [] };

async function expectWorkspaceError(code: string, fn: () => Promise<unknown>): Promise<CreationWorkspaceError> {
  try {
    await fn();
  } catch (error) {
    assert.equal(error instanceof CreationWorkspaceError, true, "expected CreationWorkspaceError");
    assert.equal((error as CreationWorkspaceError).code, code);
    return error as CreationWorkspaceError;
  }
  assert.fail("expected workspace error to be thrown");
}

function snapshotRows(directory: string, sceneId: string) {
  const raw = new Database(path.join(directory, "workspace.sqlite"));
  try {
    return raw
      .prepare(
        `SELECT subject_type, subject_id, payload_json, project_id
         FROM snapshots
         WHERE subject_type = 'scene-autosave' AND subject_id = ?
         ORDER BY created_at, id`
      )
      .all(sceneId) as Array<{ subject_type: string; subject_id: string; payload_json: string; project_id: string }>;
  } finally {
    raw.close();
  }
}

function changeLogCount(directory: string): number {
  const raw = new Database(path.join(directory, "workspace.sqlite"));
  try {
    return (raw.prepare("SELECT count(*) AS count FROM change_log").get() as { count: number }).count;
  } finally {
    raw.close();
  }
}

let passed = 0;
const failures: string[] = [];

async function test(name: string, fn: () => Promise<void> | void): Promise<void> {
  try {
    await fn();
    passed += 1;
  } catch (error) {
    failures.push(`${name}: ${error instanceof Error ? error.stack ?? error.message : String(error)}`);
  }
}

async function run(): Promise<void> {
  const base = await mkdtemp(path.join(os.tmpdir(), "creation-editor-"));
  try {
    await test("project.navigation exposes metadata without body; scene.body returns the full view", async () => {
      const directory = path.join(base, "navigation-body");
      const workspace = await openCreationWorkspace({ directory });
      const created = await workspace.transact({ type: "project.create", title: "测试项目", setup: minimalSetup });

      const navigation = (await workspace.read({
        kind: "project.navigation",
        projectId: created.projectId
      })) as CreationProjectNavigation;
      assert.equal(navigation.project.id, created.projectId);
      assert.equal(navigation.chapters.length, 1);
      assert.equal(navigation.chapters[0].scenes.length, 1);
      assert.equal(navigation.chapters[0].scenes[0].id, created.sceneId);
      assert.equal("body" in navigation.chapters[0].scenes[0], false, "navigation scenes must not carry body");

      const view = (await workspace.read({ kind: "scene.body", sceneId: created.sceneId })) as SceneBodyView;
      assert.equal(view.sceneId, created.sceneId);
      assert.equal(view.projectId, created.projectId);
      assert.equal(view.chapterId, created.chapterId);
      assert.equal(view.title, "默认场景");
      assert.deepEqual(view.body, emptyDoc);
      assert.equal(view.revision, 1);
      assert.equal(typeof view.updatedAt, "string");

      assert.equal(await workspace.read({ kind: "scene.body", sceneId: "scene-missing" }), null);
      assert.equal(await workspace.read({ kind: "project.navigation", projectId: "project-missing" }), null);
      await workspace.close();
    });

    await test("scene.updateBody keeps exactly one scene-autosave snapshot of the previous state", async () => {
      const directory = path.join(base, "snapshot-one-deep");
      const workspace = await openCreationWorkspace({ directory });
      const created = await workspace.transact({ type: "project.create", title: "测试项目", setup: minimalSetup });
      const firstBody = { type: "doc" as const, content: [{ type: "paragraph" as const, content: [{ type: "text" as const, text: "第一稿" }] }] };
      const secondBody = { type: "doc" as const, content: [{ type: "paragraph" as const, content: [{ type: "text" as const, text: "第二稿" }] }] };

      const first = (await workspace.transact({
        type: "scene.updateBody",
        sceneId: created.sceneId,
        baseRevision: 1,
        body: firstBody
      })) as UpdateSceneBodyResult;
      assert.equal(first.revision, 2);
      assert.equal(first.projectId, created.projectId);
      assert.equal(first.sceneId, created.sceneId);
      assert.equal(typeof first.sequence, "number");

      let snapshots = snapshotRows(directory, created.sceneId);
      assert.equal(snapshots.length, 1, "exactly one scene-autosave snapshot after first save");
      assert.deepEqual(JSON.parse(snapshots[0].payload_json), { body: emptyDoc, revision: 1 });
      assert.equal(snapshots[0].subject_type, "scene-autosave");
      assert.equal(snapshots[0].subject_id, created.sceneId);
      assert.equal(snapshots[0].project_id, created.projectId);

      const second = (await workspace.transact({
        type: "scene.updateBody",
        sceneId: created.sceneId,
        baseRevision: 2,
        body: secondBody
      })) as UpdateSceneBodyResult;
      assert.equal(second.revision, 3);

      const projects = (await workspace.read({ kind: "projects.list" })) as CreationProjectSummary[];
      assert.equal(projects[0].updatedAt, second.updatedAt, "scene saves must promote project recency");

      snapshots = snapshotRows(directory, created.sceneId);
      assert.equal(snapshots.length, 1, "snapshot replaced in place, still one row");
      assert.deepEqual(JSON.parse(snapshots[0].payload_json), { body: firstBody, revision: 2 });

      const view = (await workspace.read({ kind: "scene.body", sceneId: created.sceneId })) as SceneBodyView;
      assert.equal(view.revision, 3);
      assert.deepEqual(view.body, secondBody);
      await workspace.close();
    });

    await test("revision mismatch and invalid input leave body, snapshot, and change_log untouched", async () => {
      const directory = path.join(base, "conflict-zero-side-effects");
      const workspace = await openCreationWorkspace({ directory });
      const created = await workspace.transact({ type: "project.create", title: "测试项目", setup: minimalSetup });
      const firstBody = { type: "doc" as const, content: [{ type: "paragraph" as const, content: [{ type: "text" as const, text: "已提交正文" }] }] };
      const updated = (await workspace.transact({
        type: "scene.updateBody",
        sceneId: created.sceneId,
        baseRevision: 1,
        body: firstBody
      })) as UpdateSceneBodyResult;
      assert.equal(updated.revision, 2);
      const logAfterSave = changeLogCount(directory);
      const snapshotAfterSave = snapshotRows(directory, created.sceneId);
      assert.equal(snapshotAfterSave.length, 1);

      await expectWorkspaceError("revision-mismatch", () =>
        workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [] }
        })
      );
      await expectWorkspaceError("invalid-input", () =>
        workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 2,
          body: { type: "paragraph" }
        } as never)
      );
      await expectWorkspaceError("invalid-input", () =>
        workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 2,
          body: { type: "doc", content: [{ type: "table" }] }
        } as never)
      );
      await expectWorkspaceError("not-found", () =>
        workspace.transact({
          type: "scene.updateBody",
          sceneId: "scene-missing",
          baseRevision: 2,
          body: { type: "doc", content: [] }
        })
      );

      const view = (await workspace.read({ kind: "scene.body", sceneId: created.sceneId })) as SceneBodyView;
      assert.equal(view.revision, 2);
      assert.deepEqual(view.body, firstBody);
      assert.equal(changeLogCount(directory), logAfterSave, "failed writes must not touch change_log");
      assert.deepEqual(snapshotRows(directory, created.sceneId), snapshotAfterSave, "failed writes must not touch snapshots");
      await workspace.close();
    });

    await test("coordinator withWorkspace: in-flight save finishes before maintenance, new ops are closed", async () => {
      const directory = path.join(base, "coordinator-inflight");
      const coordinator = createCreationCoordinator({ resolveDirectory: () => directory });
      let saveFinished = false;
      let maintenanceSawSaveFinished = false;
      let releaseMaintenance: () => void = () => undefined;
      const gate = new Promise<void>((resolve) => {
        releaseMaintenance = resolve;
      });

      const save = coordinator.withWorkspace(async (workspace) => {
        await workspace.transact({ type: "project.create", title: "测试项目", setup: minimalSetup });
        saveFinished = true;
      });
      const maintenance = coordinator.withWorkspaceClosed(async () => {
        maintenanceSawSaveFinished = saveFinished;
        await gate;
      });

      await new Promise((resolve) => setTimeout(resolve, 30));
      await expectWorkspaceError("closed", () => coordinator.withWorkspace(async () => undefined));
      await expectWorkspaceError("closed", () => coordinator.getWorkspace());

      releaseMaintenance();
      await save;
      await maintenance;
      assert.equal(saveFinished, true);
      assert.equal(maintenanceSawSaveFinished, true, "maintenance must start only after in-flight save completed");

      const reopened = await coordinator.getWorkspace();
      const list = (await reopened.read({ kind: "projects.list" })) as CreationProjectSummary[];
      assert.equal(list.length, 1);
      assert.equal(list[0].title, "测试项目");
      await coordinator.close();
    });

    await test("coordinator watch rebinds across maintenance close/reopen", async () => {
      const directory = path.join(base, "coordinator-rebind");
      const coordinator = createCreationCoordinator({ resolveDirectory: () => directory });
      const events: Array<{ sequence: number; commandType: string }> = [];
      const unsubscribe = await coordinator.watch({}, (event) =>
        events.push({ sequence: event.sequence, commandType: event.commandType })
      );

      const first = await coordinator.getWorkspace();
      const createdA = await first.transact({ type: "project.create", title: "测试项目甲", setup: minimalSetup });
      assert.equal(events.length, 1);
      assert.equal(events[0].commandType, "project.create");

      await coordinator.withWorkspaceClosed(async () => undefined);
      assert.equal(events.length, 1, "no events may be delivered while the workspace is closed");

      const second = await coordinator.getWorkspace();
      assert.notEqual(second, first);
      await second.transact({ type: "project.create", title: "测试项目乙", setup: minimalSetup });
      assert.equal(events.length, 2, "watch must be rebound on the reopened workspace");
      assert.equal(events[1].sequence, createdA.sequence + 1);

      unsubscribe();
      await second.transact({ type: "project.create", title: "测试项目丙", setup: minimalSetup });
      assert.equal(events.length, 2, "unsubscribed watch must not receive further events");
      await coordinator.close();
    });
  } finally {
    await rm(base, { recursive: true, force: true });
  }

  if (failures.length > 0) {
    process.stderr.write(`${failures.join("\n\n")}\n`);
    process.exitCode = 1;
    return;
  }
  process.stdout.write(`${JSON.stringify({ allPass: true, tests: passed })}\n`);
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
