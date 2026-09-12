import { strict as assert } from "node:assert";
import { mkdtemp, readdir } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import { readBackupManifest } from "../backup";
import { openCreationWorkspace, type CardSummary } from "./index";
import {
  V9_MIGRATION_BACKUP_MARKER,
  V10_GLOBAL_CARD_REPAIR_BACKUP_MARKER
} from "./global-card-migration";
import {
  assertV9AttachmentFiles,
  seedV9Baseline,
  V9_BASELINE,
  type V9Baseline
} from "./v9-baseline-fixture";
import { removeWithRetry } from "./test-utils";

async function listMigrationBackups(parent: string, workspaceName: string): Promise<string[]> {
  const prefix = `${workspaceName}${V9_MIGRATION_BACKUP_MARKER}`;
  return (await readdir(parent, { withFileTypes: true }))
    .filter((entry) => entry.isDirectory() && entry.name.startsWith(prefix))
    .map((entry) => path.join(parent, entry.name))
    .sort();
}

async function listRepairBackups(parent: string, workspaceName: string): Promise<string[]> {
  const prefix = `${workspaceName}${V10_GLOBAL_CARD_REPAIR_BACKUP_MARKER}`;
  return (await readdir(parent, { withFileTypes: true }))
    .filter((entry) => entry.isDirectory() && entry.name.startsWith(prefix))
    .map((entry) => path.join(parent, entry.name))
    .sort();
}

async function createV9Baseline(directory: string): Promise<V9Baseline> {
  const workspace = await openCreationWorkspace({ directory, testOnlyTargetSchemaVersion: 9 });
  try {
    return await seedV9Baseline(workspace, { workspaceDirectory: directory });
  } finally {
    await workspace.close();
  }
}

async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "global-card-migration-"));
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
    await scenario("v9 双项目基线升级：备份、稳定 ID、引用和项目关联全部保留", async () => {
      const directory = path.join(parent, "success-workspace");
      const baseline = await createV9Baseline(directory);
      await assertV9AttachmentFiles(directory, baseline);

      const before = new Database(path.join(directory, "workspace.sqlite"), { readonly: true });
      assert.equal(Number(before.pragma("user_version", { simple: true })), 9);
      const cardCount = (before.prepare("SELECT count(*) AS count FROM cards").get() as { count: number }).count;
      const relationCount = (before.prepare("SELECT count(*) AS count FROM card_relations").get() as { count: number }).count;
      before.close();

      const migrated = await openCreationWorkspace({ directory });
      try {
        const report = await migrated.check();
        assert.equal(report.ok, true);
        assert.equal(report.schemaVersion, 11);
        const cardsA = (await migrated.read({ kind: "cards.list", projectId: baseline.projectA })) as CardSummary[];
        const cardsB = (await migrated.read({ kind: "cards.list", projectId: baseline.projectB })) as CardSummary[];
        assert.equal(cardsA.some((card) => card.id === baseline.cardASkill && card.revision === V9_BASELINE.cardASkillRevision), true);
        assert.equal(cardsA.some((card) => card.id === baseline.cardBCharacter), false);
        assert.equal(cardsB.some((card) => card.id === baseline.cardBCharacter), true);
        const annotationA = await migrated.read({ kind: "annotation.list", projectId: baseline.projectA });
        assert.equal(annotationA.some((annotation) => annotation.id === baseline.annotationA && annotation.cardId === baseline.cardASkill), true);
        const resources = await migrated.read({ kind: "resource.list", projectId: baseline.projectA, cardId: baseline.cardASkill });
        assert.equal(resources.some((resource) => resource.id === baseline.resourceAId), true);
      } finally {
        await migrated.close();
      }

      const raw = new Database(path.join(directory, "workspace.sqlite"), { readonly: true });
      assert.equal(Number(raw.pragma("user_version", { simple: true })), 11);
      const links = raw.prepare("SELECT project_id, card_id FROM project_card_links ORDER BY project_id, card_id").all() as Array<{
        project_id: string;
        card_id: string;
      }>;
      assert.equal(links.length, cardCount);
      assert.equal(links.some((link) => link.project_id === baseline.projectA && link.card_id === baseline.cardASkill), true);
      assert.equal((raw.prepare("SELECT count(*) AS count FROM card_relations").get() as { count: number }).count, relationCount);
      const planning = JSON.parse(
        (raw.prepare("SELECT planning_json FROM scenes WHERE id = ?").get(baseline.sceneA) as { planning_json: string }).planning_json
      ) as { perspectiveCardId?: string; locationCardId?: string };
      assert.equal(planning.perspectiveCardId, baseline.cardACharacter);
      assert.equal(planning.locationCardId, baseline.cardALocation);
      raw.close();

      const backups = await listMigrationBackups(parent, path.basename(directory));
      assert.equal(backups.length, 1, "首次 v9→v10 只应创建一份迁移备份");
      const manifest = await readBackupManifest(backups[0]!);
      assert.equal(manifest.files.some((file) => file.path === "workspace.sqlite"), true);
      assert.equal(manifest.files.some((file) => file.path === baseline.resourceRelativePath), true);
      assert.equal(manifest.files.some((file) => file.path === baseline.resourceBRelativePath), true);
      const backupDb = new Database(path.join(backups[0]!, "app-data", "workspace.sqlite"), { readonly: true });
      assert.equal(Number(backupDb.pragma("user_version", { simple: true })), 9);
      assert.equal((backupDb.prepare("SELECT count(*) AS count FROM cards").get() as { count: number }).count, cardCount);
      backupDb.close();

      const reopened = await openCreationWorkspace({ directory });
      await reopened.close();
      assert.equal((await listMigrationBackups(parent, path.basename(directory))).length, 1, "迁移到 v11 后重开不得重复备份");
    });

    await scenario("空 v9 库升级后关联表为空且完整性通过", async () => {
      const directory = path.join(parent, "empty-workspace");
      const v9 = await openCreationWorkspace({ directory, testOnlyTargetSchemaVersion: 9 });
      await v9.close();
      const v10 = await openCreationWorkspace({ directory });
      try {
        const report = await v10.check();
        assert.equal(report.ok, true);
        assert.equal(report.schemaVersion, 11);
      } finally {
        await v10.close();
      }
      const raw = new Database(path.join(directory, "workspace.sqlite"), { readonly: true });
      assert.equal((raw.prepare("SELECT count(*) AS count FROM project_card_links").get() as { count: number }).count, 0);
      raw.close();
    });

    await scenario("场景悬空卡片引用拒绝升级且事务不留半表", async () => {
      const directory = path.join(parent, "broken-reference-workspace");
      const baseline = await createV9Baseline(directory);
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      raw.prepare("UPDATE scenes SET planning_json = ? WHERE id = ?").run(
        JSON.stringify({ perspectiveCardId: "card-missing" }),
        baseline.sceneA
      );
      raw.close();
      let error: unknown;
      try {
        await openCreationWorkspace({ directory });
      } catch (caught) {
        error = caught;
      }
      assert.equal((error as { code?: string }).code, "integrity");
      const after = new Database(path.join(directory, "workspace.sqlite"), { readonly: true });
      assert.equal(Number(after.pragma("user_version", { simple: true })), 9);
      assert.equal(after.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='project_card_links'").get(), undefined);
      after.close();
      assert.equal((await listMigrationBackups(parent, path.basename(directory))).length, 1, "拒绝升级前仍须留下可恢复备份");
    });

    await scenario("关系端点损坏时拒绝升级并保留 v9", async () => {
      const directory = path.join(parent, "broken-relation-workspace");
      await createV9Baseline(directory);
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      raw.pragma("foreign_keys = OFF");
      const relation = raw.prepare("SELECT id FROM card_relations LIMIT 1").get() as { id: string };
      raw.prepare("UPDATE card_relations SET to_card_id = ? WHERE id = ?").run("card-missing", relation.id);
      raw.close();

      let error: unknown;
      try {
        await openCreationWorkspace({ directory });
      } catch (caught) {
        error = caught;
      }
      assert.equal((error as { code?: string }).code, "integrity");
      const after = new Database(path.join(directory, "workspace.sqlite"), { readonly: true });
      assert.equal(Number(after.pragma("user_version", { simple: true })), 9);
      assert.equal(
        after.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='project_card_links'").get(),
        undefined
      );
      after.close();
      assert.equal((await listMigrationBackups(parent, path.basename(directory))).length, 1);
    });

    await scenario("事务中途故障回滚到 v9，重试可成功", async () => {
      const directory = path.join(parent, "rollback-workspace");
      await createV9Baseline(directory);
      let error: unknown;
      try {
        await openCreationWorkspace({ directory, testOnlyFailV9ToV10AfterLinkBackfill: true });
      } catch (caught) {
        error = caught;
      }
      assert.equal((error as { code?: string }).code, "integrity");
      const failed = new Database(path.join(directory, "workspace.sqlite"), { readonly: true });
      assert.equal(Number(failed.pragma("user_version", { simple: true })), 9);
      assert.equal(failed.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='project_card_links'").get(), undefined);
      failed.close();
      const retried = await openCreationWorkspace({ directory });
      try {
        assert.equal((await retried.check()).schemaVersion, 11);
      } finally {
        await retried.close();
      }
    });

    await scenario("备份失败时不进入迁移事务", async () => {
      const directory = path.join(parent, "backup-failure-workspace");
      await createV9Baseline(directory);
      let error: unknown;
      try {
        await openCreationWorkspace({ directory, testOnlyFailV9ToV10Backup: true });
      } catch (caught) {
        error = caught;
      }
      assert.equal((error as { code?: string }).code, "integrity");
      const after = new Database(path.join(directory, "workspace.sqlite"), { readonly: true });
      assert.equal(Number(after.pragma("user_version", { simple: true })), 9);
      assert.equal(after.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='project_card_links'").get(), undefined);
      after.close();
      assert.equal((await listMigrationBackups(parent, path.basename(directory))).length, 0);
    });

    await scenario("开发期不完整 v10 会先备份，再解除四张核心表的项目所有权", async () => {
      const directory = path.join(parent, "incomplete-v10-workspace");
      const baseline = await createV9Baseline(directory);
      const databasePath = path.join(directory, "workspace.sqlite");
      const incomplete = new Database(databasePath);
      incomplete.pragma("foreign_keys = OFF");
      incomplete.exec(`
        CREATE TABLE project_card_links (
          project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
          card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
          linked_at TEXT NOT NULL,
          PRIMARY KEY (project_id, card_id)
        );
        CREATE INDEX idx_project_card_links_card ON project_card_links(card_id, project_id);
        INSERT INTO project_card_links(project_id, card_id, linked_at)
          SELECT project_id, id, created_at FROM cards;
        PRAGMA user_version = 10;
      `);
      incomplete.close();

      const repaired = await openCreationWorkspace({ directory });
      try {
        assert.equal((await repaired.check()).ok, true);
        assert.equal((await repaired.read({ kind: "cards.list", projectId: baseline.projectA })).length > 0, true);
      } finally {
        await repaired.close();
      }

      const after = new Database(databasePath);
      const tables = ["card_types", "relation_types", "cards", "card_relations"];
      for (const table of tables) {
        const projectColumn = (after.prepare(`PRAGMA table_info(${table})`).all() as Array<{ name: string; notnull: number }>).find(
          (column) => column.name === "project_id"
        );
        assert.equal(projectColumn?.notnull, 0, `${table}.project_id 必须允许为空`);
        const projectOwnership = (after.prepare(`PRAGMA foreign_key_list(${table})`).all() as Array<{ table: string; from: string }>).some(
          (foreignKey) => foreignKey.table === "projects" && foreignKey.from === "project_id"
        );
        assert.equal(projectOwnership, false, `${table} 不得再由项目级联拥有`);
      }
      assert.equal(
        (after.prepare("PRAGMA table_info(card_types)").all() as Array<{ name: string }>).some((column) => column.name === "is_builtin"),
        true
      );
      assert.equal(
        (after.prepare("PRAGMA table_info(relation_types)").all() as Array<{ name: string }>).some((column) => column.name === "is_builtin"),
        true
      );
      after.close();

      const backups = await listRepairBackups(parent, path.basename(directory));
      assert.equal(backups.length, 1);
      const backup = new Database(path.join(backups[0]!, "app-data", "workspace.sqlite"), { readonly: true });
      assert.equal(Number(backup.pragma("user_version", { simple: true })), 10);
      assert.equal(
        (backup.prepare("PRAGMA table_info(cards)").all() as Array<{ name: string; notnull: number }>).find(
          (column) => column.name === "project_id"
        )?.notnull,
        1,
        "修复备份必须保留原始中间态"
      );
      backup.close();

      const reopened = await openCreationWorkspace({ directory });
      await reopened.close();
      assert.equal((await listRepairBackups(parent, path.basename(directory))).length, 1, "修复完成后不得重复备份");
    });

    await scenario("删除项目只解除关联，不级联删除全局卡片及自定义类型", async () => {
      const directory = path.join(parent, "project-delete-ownership-workspace");
      const workspace = await openCreationWorkspace({ directory });
      const project = await workspace.transact({ type: "project.create", title: "待删除项目" });
      const cardType = await workspace.transact({
        type: "cardType.create",
        projectId: project.projectId,
        name: "应保留卡片类型",
        fields: []
      });
      const relationType = await workspace.transact({
        type: "relationType.create",
        projectId: project.projectId,
        forwardName: "应保留关系",
        reverseName: "反向关系",
        fromKinds: [],
        toKinds: []
      });
      const customKind = (await workspace.read({ kind: "cardTypes.list" })).find((type) => type.id === cardType.entityId)?.kind;
      assert.ok(customKind);
      const card = await workspace.transact({
        type: "card.create",
        projectId: project.projectId,
        kind: customKind,
        title: "应保留全局卡片"
      });
      await workspace.close();

      const raw = new Database(path.join(directory, "workspace.sqlite"));
      raw.pragma("foreign_keys = ON");
      raw.prepare("DELETE FROM projects WHERE id = ?").run(project.projectId);
      assert.equal(raw.prepare("SELECT 1 FROM cards WHERE id = ?").get(card.entityId) !== undefined, true);
      assert.equal(raw.prepare("SELECT 1 FROM card_types WHERE id = ?").get(cardType.entityId) !== undefined, true);
      assert.equal(raw.prepare("SELECT 1 FROM relation_types WHERE id = ?").get(relationType.entityId) !== undefined, true);
      assert.equal(
        (raw.prepare("SELECT count(*) AS count FROM project_card_links WHERE card_id = ?").get(card.entityId) as { count: number }).count,
        0
      );
      raw.close();
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
