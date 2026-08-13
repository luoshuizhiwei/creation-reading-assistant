import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp, mkdir, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CardSummary,
  type CreationStructureResult,
  type CreationWorkspace
} from "./index";

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-cards-"));
  let workspace: CreationWorkspace | undefined;
  let tests = 0;
  let projectId = "";
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
    const initial = await workspace.check();
    assert.equal(initial.ok, true);
    assert.equal(initial.schemaVersion, 9);

    await scenario("内置 8 类卡片与 4 种关系类型已 seed", async () => {
      const created = await workspace!.transact({ type: "project.create", title: "测试项目" });
      projectId = created.projectId;
      const types = (await workspace!.read({ kind: "cardTypes.list", projectId }))!;
      assert.equal(types.length, 8);
      assert.equal(types.some((t) => t.kind === "character" && t.name === "角色"), true);
      assert.equal(types[0]?.fields.length, 1);
      const relations = (await workspace!.read({ kind: "relationTypes.list", projectId }))!;
      assert.equal(relations.length, 4);
      assert.equal(relations.some((r) => r.forwardName === "登场于" && r.reverseName === "登场角色"), true);
    });

    await scenario("自定义卡片类型（含必填/单选字段）可创建", async () => {
      const type = await workspace!.transact({
        type: "cardType.create",
        projectId,
        name: "技能",
        fields: [
          { key: "desc", label: "描述", kind: "multiline" },
          { key: "rank", label: "等级", kind: "select", options: ["S", "A", "B"] },
          { key: "mana", label: "耗蓝", kind: "number", required: true }
        ]
      }) as CreationStructureResult;
      const types = (await workspace!.read({ kind: "cardTypes.list", projectId }))!;
      const custom = types.find((t) => t.id === type.entityId);
      assert.equal(custom?.name, "技能");
      assert.equal(custom?.fields.length, 3);
      assert.equal(custom?.fields.find((f) => f.key === "rank")?.options?.join(","), "S,A,B");
      assert.equal(custom?.fields.find((f) => f.key === "mana")?.required, true);
    });

    await scenario("创建卡片：必填校验与未知字段拒绝", async () => {
      const types = (await workspace!.read({ kind: "cardTypes.list", projectId }))!;
      const skill = types.find((t) => t.name === "技能")!;
      const card = await workspace!.transact({
        type: "card.create",
        projectId,
        kind: skill.kind,
        title: "影袭",
        aliases: ["潜行一击", "暗影斩"],
        fields: { desc: "高速突袭", rank: "A", mana: 30 },
        tags: ["战斗"]
      }) as CreationStructureResult;
      assert.equal(card.entityId.startsWith("card-"), true);
      const summary = (await workspace!.read({ kind: "card.read", cardId: card.entityId })) as CardSummary;
      assert.equal(summary.title, "影袭");
      assert.deepEqual(summary.aliases, ["潜行一击", "暗影斩"]);
      assert.deepEqual(summary.tags, ["战斗"]);
      assert.equal(summary.fields.mana, 30);
      let missingRequired: unknown;
      try {
        await workspace!.transact({ type: "card.create", projectId, kind: skill.kind, title: "缺必填" });
      } catch (error) {
        missingRequired = error;
      }
      assert.equal((missingRequired as CreationWorkspaceError).code, "invalid-input");
      let unknownField: unknown;
      try {
        await workspace!.transact({
          type: "card.create",
          projectId,
          kind: skill.kind,
          title: "未知字段",
          fields: { nope: 1 }
        });
      } catch (error) {
        unknownField = error;
      }
      assert.equal((unknownField as CreationWorkspaceError).code, "invalid-input");
    });

    await scenario("卡片筛选与搜索", async () => {
      await workspace!.transact({ type: "card.create", projectId, kind: "character", title: "林晚" });
      await workspace!.transact({ type: "card.create", projectId, kind: "location", title: "旧城" });
      const all = (await workspace!.read({ kind: "cards.list", projectId })) as CardSummary[];
      assert.equal(all.length, 3);
      const characters = (await workspace!.read({ kind: "cards.list", projectId, cardKind: "character" })) as CardSummary[];
      assert.equal(characters.length, 1);
      assert.equal(characters[0]?.title, "林晚");
      const searched = (await workspace!.read({ kind: "cards.list", projectId, search: "影" })) as CardSummary[];
      assert.equal(searched.some((c) => c.title === "影袭"), true);
    });

    await scenario("创建资料卡：content 来源快照随卡保存", async () => {
      const card = await workspace!.transact({
        type: "card.create",
        projectId,
        kind: "reference",
        title: "摘录：河岸",
        fields: { note: "正文预览" },
        content: {
          excerpt: "完整摘录正文",
          source: { bookId: "book-1", bookTitle: "测试 TXT", locator: { bookId: "book-1", kind: "txt", offset: 123 }, createdAt: "2026-01-01T00:00:00.000Z" },
          inspirationId: "insp-1",
          excerptedAt: "2026-01-01T00:00:00.000Z"
        }
      }) as CreationStructureResult;
      const summary = (await workspace!.read({ kind: "card.read", cardId: card.entityId })) as CardSummary;
      assert.equal(summary.kind, "reference");
      assert.equal(summary.title, "摘录：河岸");
      let invalidContent: unknown;
      try {
        await workspace!.transact({
          type: "card.create",
          projectId,
          kind: "reference",
          title: "非法内容",
          content: "not-an-object" as never
        });
      } catch (error) {
        invalidContent = error;
      }
      assert.equal((invalidContent as CreationWorkspaceError).code, "invalid-input");
    });

    await scenario("更新卡片：改名与改字段（revision 校验）", async () => {
      const all = (await workspace!.read({ kind: "cards.list", projectId, search: "影" })) as CardSummary[];
      const shadow = all[0]!;
      await workspace!.transact({
        type: "card.update",
        cardId: shadow.id,
        title: "影袭·改",
        aliases: ["潜行"],
        baseRevision: shadow.revision
      });
      const updated = (await workspace!.read({ kind: "card.read", cardId: shadow.id })) as CardSummary;
      assert.equal(updated.title, "影袭·改");
      assert.deepEqual(updated.aliases, ["潜行"]);
      assert.equal(updated.revision, shadow.revision + 1);
      let stale: unknown;
      try {
        await workspace!.transact({ type: "card.update", cardId: shadow.id, title: "旧版本", baseRevision: 1 });
      } catch (error) {
        stale = error;
      }
      assert.equal((stale as CreationWorkspaceError).code, "revision-mismatch");
    });

    await scenario("换卡片类型：可映射字段保留，必填缺失拒绝", async () => {
      const characters = (await workspace!.read({ kind: "cards.list", projectId, cardKind: "character" })) as CardSummary[];
      const linh = characters.find((c) => c.title === "林晚")!;
      // 先给林晚加 note 字段，再换类型验证可映射字段保留
      await workspace!.transact({
        type: "card.update",
        cardId: linh.id,
        fields: { note: "主角" },
        baseRevision: linh.revision
      });
      const withNote = (await workspace!.read({ kind: "card.read", cardId: linh.id })) as CardSummary;
      const moved = await workspace!.transact({
        type: "card.update",
        cardId: linh.id,
        kind: "item",
        baseRevision: withNote.revision
      }) as CreationStructureResult;
      assert.equal(moved.entityId, linh.id);
      const itemCards = (await workspace!.read({ kind: "cards.list", projectId, cardKind: "item" })) as CardSummary[];
      const movedCard = itemCards.find((c) => c.id === linh.id)!;
      assert.equal(movedCard.kind, "item");
      assert.equal(movedCard.fields.note, "主角");
      const types = (await workspace!.read({ kind: "cardTypes.list", projectId }))!;
      const skill = types.find((t) => t.name === "技能")!;
      let reject: unknown;
      try {
        await workspace!.transact({
          type: "card.update",
          cardId: linh.id,
          kind: skill.kind,
          baseRevision: movedCard.revision
        });
      } catch (error) {
        reject = error;
      }
      assert.equal((reject as CreationWorkspaceError).code, "invalid-input");
      // 改回 character，保持后续场景不变
      const afterReject = (await workspace!.read({ kind: "card.read", cardId: linh.id })) as CardSummary;
      await workspace!.transact({
        type: "card.update",
        cardId: linh.id,
        kind: "character",
        baseRevision: afterReject.revision
      });
    });

    await scenario("卡片关系：正向/反向与说明", async () => {
      const characters = (await workspace!.read({ kind: "cards.list", projectId, cardKind: "character" })) as CardSummary[];
      const locations = (await workspace!.read({ kind: "cards.list", projectId, cardKind: "location" })) as CardSummary[];
      const linh = characters.find((c) => c.title === "林晚")!;
      const city = locations[0]!;
      const relationTypes = (await workspace!.read({ kind: "relationTypes.list", projectId }))!;
      const appearsAt = relationTypes.find((r) => r.forwardName === "登场于")!;
      await workspace!.transact({
        type: "cardRelation.create",
        projectId,
        fromCardId: linh.id,
        toCardId: city.id,
        relationTypeId: appearsAt.id,
        note: "第一章"
      });
      const relations = (await workspace!.read({ kind: "card.relations", cardId: linh.id }))!;
      assert.equal(relations.outgoing.length, 1);
      assert.equal(relations.outgoing[0]?.forwardName, "登场于");
      assert.equal(relations.outgoing[0]?.note, "第一章");
      assert.equal(relations.outgoing[0]?.toCardId, city.id);
      const reverse = (await workspace!.read({ kind: "card.relations", cardId: city.id }))!;
      assert.equal(reverse.incoming.length, 1);
      assert.equal(reverse.incoming[0]?.fromCardId, linh.id);
    });

    await scenario("删除卡片级联删除其关系", async () => {
      const characters = (await workspace!.read({ kind: "cards.list", projectId, cardKind: "character" })) as CardSummary[];
      const linh = characters.find((c) => c.title === "林晚")!;
      await workspace!.transact({ type: "card.delete", cardId: linh.id });
      const after = (await workspace!.read({ kind: "cards.list", projectId, cardKind: "character" })) as CardSummary[];
      assert.equal(after.some((c) => c.id === linh.id), false);
      const locations = (await workspace!.read({ kind: "cards.list", projectId, cardKind: "location" })) as CardSummary[];
      const reverse = (await workspace!.read({ kind: "card.relations", cardId: locations[0]!.id }))!;
      assert.equal(reverse.incoming.length, 0);
    });

    await scenario("v3→v4 迁移：卡片列升级与内置数据", async () => {
      const v3Directory = path.join(directory, "v3-legacy");
      await mkdir(v3Directory, { recursive: true });
      const raw = new Database(path.join(v3Directory, "workspace.sqlite"));
      raw.exec(`
        CREATE TABLE projects (
          id TEXT PRIMARY KEY, title TEXT NOT NULL, setup_json TEXT NOT NULL DEFAULT '{}',
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
        PRAGMA user_version = 3;
      `);
      raw
        .prepare("INSERT INTO projects(id, title, setup_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")
        .run("project-v3", "旧项目", "{}", "2026-08-01T00:00:00.000Z", "2026-08-01T00:00:00.000Z");
      raw
        .prepare("INSERT INTO cards(id, project_id, kind, title, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
        .run("card-v3", "project-v3", "character", "旧卡", "2026-08-01T00:00:00.000Z", "2026-08-01T00:00:00.000Z");
      raw.close();

      const migrated = await openCreationWorkspace({ directory: v3Directory });
      const report = await migrated.check();
      assert.equal(report.schemaVersion, 9);
      const types = (await migrated.read({ kind: "cardTypes.list", projectId: "project-v3" }))!;
      assert.equal(types.length, 8);
      const card = (await migrated.read({ kind: "card.read", cardId: "card-v3" })) as CardSummary;
      assert.equal(card.title, "旧卡");
      assert.deepEqual(card.aliases, []);
      assert.deepEqual(card.fields, {});
      await migrated.close();
    });

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
