import { strict as assert } from "node:assert";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
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

/** 收件箱计数：0/1/3 条未处理 + used 状态排除 + 软删除排除 + 重启保持。 */
async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-inbox-count-"));
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

    await scenario("空收件箱计数为 0", async () => {
      await withWorkspace(path.join(parent, "ws0"), async (workspace) => {
        const view = await workspace.read({ kind: "inbox.count" });
        assert.equal(view.total, 0);
        assert.equal(view.pending, 0);
      });
    });

    await scenario("1 条未处理 → pending 1", async () => {
      await withWorkspace(path.join(parent, "ws1"), async (workspace) => {
        await workspace.transact({ type: "inbox.create", title: "测试灵感A", body: "正文一" });
        const view = await workspace.read({ kind: "inbox.count" });
        assert.equal(view.total, 1);
        assert.equal(view.pending, 1);
      });
    });

    await scenario("3 条未处理 → pending 3", async () => {
      await withWorkspace(path.join(parent, "ws2"), async (workspace) => {
        await workspace.transact({ type: "inbox.create", title: "测试灵感A", body: "正文一" });
        await workspace.transact({ type: "inbox.create", title: "测试灵感B", body: "正文二" });
        await workspace.transact({ type: "inbox.create", title: "测试灵感C", body: "正文三" });
        const view = await workspace.read({ kind: "inbox.count" });
        assert.equal(view.total, 3);
        assert.equal(view.pending, 3);
      });
    });

    await scenario("used 状态排除：pending 只统计未处理条目", async () => {
      const directory = path.join(parent, "ws-used");
      await withWorkspace(directory, async (workspace) => {
        const a = await workspace.transact({ type: "inbox.create", title: "测试灵感A", body: "正文一" }) as { itemId: string };
        await workspace.transact({ type: "inbox.create", title: "测试灵感B", body: "正文二" });
        await workspace.transact({ type: "inbox.create", title: "测试灵感C", body: "正文三" });
        await workspace.transact({ type: "inbox.update", itemId: a.itemId, baseRevision: 1, status: "used" });
        let view = await workspace.read({ kind: "inbox.count" });
        assert.equal(view.total, 3);
        assert.equal(view.pending, 2);
        // 已转卡片的条目仍计入 total，但不再计入 pending。
        const all = await workspace.read({ kind: "inbox.list", limit: 50 });
        assert.equal(all.length, 3);
      });

      // 重启后计数保持一致。
      await withWorkspace(directory, async (workspace) => {
        const view = await workspace.read({ kind: "inbox.count" });
        assert.equal(view.total, 3);
        assert.equal(view.pending, 2);
      });
    });

    await scenario("软删除条目不计入计数", async () => {
      const directory = path.join(parent, "ws-delete");
      await withWorkspace(directory, async (workspace) => {
        const a = await workspace.transact({ type: "inbox.create", title: "测试灵感A", body: "正文一" }) as { itemId: string };
        await workspace.transact({ type: "inbox.create", title: "测试灵感B", body: "正文二" });
        await workspace.transact({ type: "inbox.delete", itemId: a.itemId });
        const view = await workspace.read({ kind: "inbox.count" });
        assert.equal(view.total, 1);
        assert.equal(view.pending, 1);
      });
    });

    await scenario("inbox.list 不用于计数：加载 limit 仍返回全部条目的真实数量", async () => {
      await withWorkspace(path.join(parent, "ws-list"), async (workspace) => {
        await workspace.transact({ type: "inbox.create", title: "测试灵感A", body: "正文一" });
        await workspace.transact({ type: "inbox.create", title: "测试灵感B", body: "正文二" });
        await workspace.transact({ type: "inbox.create", title: "测试灵感C", body: "正文三" });
        // 用 limit:1 列表长度不能当计数：列表只是截断视图，计数来自 inbox.count。
        const truncated = await workspace.read({ kind: "inbox.list", limit: 1 });
        assert.equal(truncated.length, 1);
        const view = await workspace.read({ kind: "inbox.count" });
        assert.equal(view.pending, 3);
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
