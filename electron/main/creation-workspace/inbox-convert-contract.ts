import { strict as assert } from "node:assert";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationWorkspace
} from "./index";

async function withWorkspace<T>(directory: string, fn: (workspace: CreationWorkspace) => Promise<T>): Promise<T> {
  const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
  try {
    return await fn(workspace);
  } finally {
    await workspace.close();
  }
}

async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-inbox-convert-"));
  let tests = 0;
  try {
    const scenario = async (name: string, fn: () => Promise<void>): Promise<void> => {
      try {
        await fn();
        tests += 1;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    await scenario("原子转换：成功创建卡片并标记条目 used", async () => {
      await withWorkspace(path.join(parent, "ws-ok"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "项目A" });
        const item = await workspace.transact({ type: "inbox.create", title: "灵感", body: "正文内容" });
        const result = await workspace.transact({
          type: "inbox.convertToCard",
          itemId: item.itemId,
          baseRevision: item.revision,
          projectId: project.projectId
        });
        assert.equal(result.commandType, "inbox.convertToCard");
        const card = await workspace.read({ kind: "card.read", cardId: result.cardId });
        assert.equal(card?.kind, "reference");
        assert.equal(card?.title, "灵感");
        assert.equal(card?.projectId, null, "card.read 返回全局实体投影");
        assert.deepEqual(card?.linkedProjectIds, [project.projectId]);
        assert.equal(card?.usageCount, 1);
        const view = await workspace.read({ kind: "inbox.count" });
        assert.equal(view.total, 1);
        assert.equal(view.pending, 0);
      });
    });

    await scenario("revision 冲突拒绝转换", async () => {
      await withWorkspace(path.join(parent, "ws-rev"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "项目A" });
        const item = await workspace.transact({ type: "inbox.create", title: "灵感", body: "正文" });
        await workspace.transact({ type: "inbox.update", itemId: item.itemId, baseRevision: item.revision, status: "archived" });
        let caught: unknown;
        try {
          await workspace.transact({
            type: "inbox.convertToCard",
            itemId: item.itemId,
            baseRevision: item.revision,
            projectId: project.projectId
          });
        } catch (error) {
          caught = error;
        }
        assert.equal((caught as CreationWorkspaceError).code, "revision-mismatch");
        const cards = await workspace.read({ kind: "cards.list", projectId: project.projectId });
        assert.equal(cards.length, 0);
      });
    });

    await scenario("目标项目不存在时拒绝", async () => {
      await withWorkspace(path.join(parent, "ws-no-project"), async (workspace) => {
        const item = await workspace.transact({ type: "inbox.create", title: "灵感", body: "正文" });
        let caught: unknown;
        try {
          await workspace.transact({
            type: "inbox.convertToCard",
            itemId: item.itemId,
            baseRevision: item.revision,
            projectId: "project-nonexistent"
          });
        } catch (error) {
          caught = error;
        }
        assert.equal((caught as CreationWorkspaceError).code, "not-found");
      });
    });

    await scenario("重复调用（已 used）拒绝产生重复卡片", async () => {
      const directory = path.join(parent, "ws-dup");
      let cardId = "";
      await withWorkspace(directory, async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "项目A" });
        const item = await workspace.transact({ type: "inbox.create", title: "灵感", body: "正文" });
        const result = await workspace.transact({
          type: "inbox.convertToCard",
          itemId: item.itemId,
          baseRevision: item.revision,
          projectId: project.projectId
        });
        cardId = result.cardId;
        let caught: unknown;
        try {
          await workspace.transact({
            type: "inbox.convertToCard",
            itemId: item.itemId,
            baseRevision: result.revision,
            projectId: project.projectId
          });
        } catch (error) {
          caught = error;
        }
        assert.equal((caught as CreationWorkspaceError).code, "conflict");
        const cards = await workspace.read({ kind: "cards.list", projectId: project.projectId });
        assert.equal(cards.length, 1);
        assert.equal(cards[0]?.id, cardId);
      });

      await withWorkspace(directory, async (workspace) => {
        const project = (await workspace.read({ kind: "projects.list" }))[0];
        const cards = await workspace.read({ kind: "cards.list", projectId: project.id });
        assert.equal(cards.length, 1);
        assert.equal(cards[0]?.id, cardId);
      });
    });

    await scenario("重启后卡片与 used 状态保持", async () => {
      const directory = path.join(parent, "ws-restart");
      await withWorkspace(directory, async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "项目A" });
        const item = await workspace.transact({ type: "inbox.create", title: "灵感", body: "持久化正文" });
        await workspace.transact({
          type: "inbox.convertToCard",
          itemId: item.itemId,
          baseRevision: item.revision,
          projectId: project.projectId
        });
      });

      await withWorkspace(directory, async (workspace) => {
        const project = (await workspace.read({ kind: "projects.list" }))[0];
        const cards = await workspace.read({ kind: "cards.list", projectId: project.id });
        assert.equal(cards.length, 1);
        assert.equal(cards[0]?.kind, "reference");
        const view = await workspace.read({ kind: "inbox.count" });
        assert.equal(view.pending, 0);
      });
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    for (let attempt = 0; attempt < 5; attempt += 1) {
      try {
        await rm(parent, { recursive: true, force: true });
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
