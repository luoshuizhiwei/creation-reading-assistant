import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import { CreationWorkspaceError, openCreationWorkspace } from "./index";

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-workspace-"));
  let workspace: Awaited<ReturnType<typeof openCreationWorkspace>> | undefined;
  try {
    workspace = await openCreationWorkspace({ directory });
    const report = await workspace.check();
    assert.equal(report.ok, true);
    assert.equal(report.schema.ok, true);
    assert.equal(report.relations.ok, true);
    assert.equal(report.resources.ok, true);
    assert.equal(report.indexes.ok, true);
    assert.equal(report.snapshots.ok, true);

    const created = await workspace.transact({ type: "project.create", title: "测试作品" });
    const tree = await workspace.read({ kind: "project.tree", projectId: created.projectId });
    assert.equal(tree?.project.title, "测试作品");
    assert.equal(tree?.chapters.length, 1);
    assert.equal(tree?.chapters[0]?.scenes.length, 1);
    assert.equal(tree?.chapters[0]?.scenes[0]?.body?.type, "doc");
    assert.equal(tree?.chapters[0]?.scenes[0]?.body.content.length, 0);

    await workspace.close();
    workspace = await openCreationWorkspace({ directory });
    const persisted = await workspace.read({ kind: "project.tree", projectId: created.projectId });
    assert.deepEqual(persisted, tree);

    const workspaceEvents: Array<{ sequence: number; commandType: string }> = [];
    const projectEvents: Array<{ sequence: number; commandType: string }> = [];
    const unwatchWorkspace = workspace.watch({}, (event) =>
      workspaceEvents.push({ sequence: event.sequence, commandType: event.commandType })
    );
    const unwatchProject = workspace.watch(
      { projectId: created.projectId },
      (event) => projectEvents.push({ sequence: event.sequence, commandType: event.commandType })
    );

    const body = {
      type: "doc" as const,
      content: [{ type: "paragraph", content: [{ type: "text", text: "雨落在旧城墙上。" }] }]
    };
    const updated = await workspace.transact({
      type: "scene.updateBody",
      sceneId: created.sceneId,
      baseRevision: 1,
      body
    });
    assert.equal(updated.revision, 2);
    assert.deepEqual(workspaceEvents, [{ sequence: updated.sequence, commandType: "scene.updateBody" }]);
    assert.deepEqual(projectEvents, [{ sequence: updated.sequence, commandType: "scene.updateBody" }]);
    const afterUpdate = await workspace.read({ kind: "project.tree", projectId: created.projectId });
    assert.deepEqual(afterUpdate?.chapters[0]?.scenes[0]?.body, body);
    const bodyView = await workspace.read({ kind: "scene.body", sceneId: created.sceneId });
    assert.equal(bodyView?.revision, 2);
    assert.deepEqual(bodyView?.body, body);
    const navigation = await workspace.read({ kind: "project.navigation", projectId: created.projectId });
    assert.equal("body" in (navigation?.chapters[0]?.scenes[0] ?? {}), false);
    const snapshotFixture = new Database(path.join(directory, "workspace.sqlite"));
    let snapshotRows: Array<{ payload_json: string }>;
    try {
      snapshotRows = snapshotFixture
        .prepare("SELECT payload_json FROM snapshots WHERE subject_type = 'scene-autosave' AND subject_id = ?")
        .all(created.sceneId) as Array<{ payload_json: string }>;
    } finally {
      snapshotFixture.close();
    }
    assert.equal(snapshotRows.length, 1);
    assert.deepEqual(JSON.parse(snapshotRows[0].payload_json), { body: { type: "doc", content: [] }, revision: 1 });

    let staleError: unknown;
    try {
      await workspace.transact({
        type: "scene.updateBody",
        sceneId: created.sceneId,
        baseRevision: 1,
        body: { type: "doc", content: [] }
      });
    } catch (error) {
      staleError = error;
    }
    assert.equal(staleError instanceof CreationWorkspaceError, true);
    assert.equal((staleError as CreationWorkspaceError).code, "revision-mismatch");
    const afterRejectedUpdate = await workspace.read({ kind: "project.tree", projectId: created.projectId });
    assert.deepEqual(afterRejectedUpdate, afterUpdate);
    assert.equal(workspaceEvents.length, 1);
    assert.equal(projectEvents.length, 1);

    unwatchWorkspace();
    unwatchProject();
    await workspace.transact({ type: "project.create", title: "第二个测试作品" });
    assert.equal(workspaceEvents.length, 1);
    assert.equal(projectEvents.length, 1);

    let unknownCommandError: unknown;
    try {
      await workspace.transact({ type: "unknown", title: "不应创建" } as never);
    } catch (error) {
      unknownCommandError = error;
    }
    assert.equal(unknownCommandError instanceof CreationWorkspaceError, true);
    assert.equal((unknownCommandError as CreationWorkspaceError).code, "invalid-input");

    for (const malformedCommand of [
      { type: "project.create" },
      { type: "scene.updateBody", sceneId: 42, baseRevision: 1, body: null }
    ]) {
      let malformedError: unknown;
      try {
        await workspace.transact(malformedCommand as never);
      } catch (error) {
        malformedError = error;
      }
      assert.equal(malformedError instanceof CreationWorkspaceError, true);
      assert.equal((malformedError as CreationWorkspaceError).code, "invalid-input");
    }

    for (const [scope, listener] of [
      [null, () => undefined],
      [{}, null]
    ] as const) {
      let malformedWatchError: unknown;
      try {
        workspace.watch(scope as never, listener as never);
      } catch (error) {
        malformedWatchError = error;
      }
      assert.equal(malformedWatchError instanceof CreationWorkspaceError, true);
      assert.equal((malformedWatchError as CreationWorkspaceError).code, "invalid-input");
    }

    let checkEvents = 0;
    const unwatchCheck = workspace.watch({}, () => {
      checkEvents += 1;
    });
    const finalReport = await workspace.check();
    assert.equal(finalReport.latestSequence, 3);
    assert.deepEqual(finalReport.counts, {
      projects: 2,
      volumes: 2,
      chapters: 2,
      scenes: 2,
      cards: 0,
      relations: 0,
      resources: 0,
      snapshots: 1,
      sessions: 0,
      inbox: 0,
      annotations: 0
    });
    assert.equal(checkEvents, 0);
    unwatchCheck();

    let malformedReadError: unknown;
    try {
      await workspace.read(null as never);
    } catch (error) {
      malformedReadError = error;
    }
    assert.equal(malformedReadError instanceof CreationWorkspaceError, true);
    assert.equal((malformedReadError as CreationWorkspaceError).code, "invalid-input");

    await workspace.close();
    await workspace.close();
    let closedError: unknown;
    try {
      await workspace.read({ kind: "project.tree", projectId: created.projectId });
    } catch (error) {
      closedError = error;
    }
    assert.equal(closedError instanceof CreationWorkspaceError, true);
    assert.equal((closedError as CreationWorkspaceError).code, "closed");
    workspace = undefined;

    const rawBodyFixture = new Database(path.join(directory, "workspace.sqlite"));
    rawBodyFixture.prepare("UPDATE scenes SET body_json = ? WHERE id = ?").run("{broken", created.sceneId);
    rawBodyFixture.close();
    workspace = await openCreationWorkspace({ directory });
    let corruptBodyError: unknown;
    try {
      await workspace.read({ kind: "project.tree", projectId: created.projectId });
    } catch (error) {
      corruptBodyError = error;
    }
    assert.equal(corruptBodyError instanceof CreationWorkspaceError, true);
    assert.equal((corruptBodyError as CreationWorkspaceError).code, "integrity");
    await workspace.close();
    workspace = undefined;

    const corruptedDirectory = path.join(directory, "corrupted");
    workspace = await openCreationWorkspace({ directory: corruptedDirectory });
    await workspace.close();
    workspace = undefined;
    const rawFixture = new Database(path.join(corruptedDirectory, "workspace.sqlite"));
    rawFixture.exec("DROP TABLE resources");
    rawFixture.close();
    workspace = await openCreationWorkspace({ directory: corruptedDirectory });
    const corruptedReport = await workspace.check();
    assert.equal(corruptedReport.ok, false);
    assert.equal(
      corruptedReport.schema.issues.some((issue) => issue.code === "schema-table-missing"),
      true
    );

    await workspace.close();
    workspace = undefined;

    const invalidDirectory = path.join(directory, "not-a-directory");
    await writeFile(invalidDirectory, "fixture", "utf8");
    let openError: unknown;
    try {
      await openCreationWorkspace({ directory: invalidDirectory });
    } catch (error) {
      openError = error;
    }
    assert.equal(openError instanceof CreationWorkspaceError, true);
    assert.equal((openError as CreationWorkspaceError).code, "integrity");
    assert.equal((openError as CreationWorkspaceError).message.includes(invalidDirectory), false);

    process.stdout.write(`${JSON.stringify({ allPass: true, tests: 11 })}\n`);
  } finally {
    await workspace?.close();
    await removeWithRetry(directory);
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
