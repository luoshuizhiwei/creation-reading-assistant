import { strict as assert } from "node:assert";
import { createHash } from "node:crypto";
import { removeWithRetry } from "./test-utils";
import { mkdtemp } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationWorkspace,
  type ResourceInfo
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
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-resource-"));
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

    let projectId = "";
    let cardId = "";
    let resourceId = "";
    let globalResourceId = "";
    let coverResourceId = "";
    const payload = "附件内容";
    const sha256 = createHash("sha256").update(payload).digest("hex");

    await scenario("准备项目与卡片", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "附件测试" });
        projectId = created.projectId;
        const card = await workspace.transact({ type: "card.create", projectId, kind: "character", title: "苏青" }) as { entityId: string };
        cardId = card.entityId;
      });
    });

    await scenario("登记附件：校验和/大小/相对路径 + 卡片关联", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        const result = await workspace.transact({
          type: "resource.attach",
          projectId,
          cardId,
          relativePath: `project-${projectId}/att-1.txt`,
          sha256,
          size: Buffer.byteLength(payload),
          originalName: "设定.txt"
        });
        resourceId = result.resourceId;
        assert.equal(resourceId.startsWith("resource-"), true);
        const list = (await workspace.read({ kind: "resource.list", projectId, cardId })) as ResourceInfo[];
        assert.equal(list.length, 1);
        assert.equal(list[0]!.originalName, "设定.txt");
        assert.equal(list[0]!.sha256, sha256);
        assert.equal(list[0]!.cardId, cardId);
        const all = (await workspace.read({ kind: "resource.list", projectId })) as ResourceInfo[];
        assert.equal(all.length, 1);
      });
    });

    await scenario("路径穿越/坏校验和/重复路径被拒绝", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        let traversal: unknown;
        try {
          await workspace.transact({
            type: "resource.attach",
            projectId,
            relativePath: "../evil.txt",
            sha256,
            size: 1
          });
        } catch (error) {
          traversal = error;
        }
        assert.equal((traversal as CreationWorkspaceError).code, "invalid-input");
        let badHash: unknown;
        try {
          await workspace.transact({
            type: "resource.attach",
            projectId,
            relativePath: `project-${projectId}/att-2.txt`,
            sha256: "not-a-hash",
            size: 1
          });
        } catch (error) {
          badHash = error;
        }
        assert.equal((badHash as CreationWorkspaceError).code, "invalid-input");
        let duplicate: unknown;
        try {
          await workspace.transact({
            type: "resource.attach",
            projectId,
            relativePath: `project-${projectId}/att-1.txt`,
            sha256,
            size: 1
          });
        } catch (error) {
          duplicate = error;
        }
        assert.equal((duplicate as CreationWorkspaceError).code, "conflict");
      });
    });

    await scenario("全局卡片附件不绑定项目，并投影到所有关联项目", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        const other = await workspace.transact({ type: "project.create", title: "共享目标" });
        await workspace.transact({ type: "card.link", projectId: other.projectId, cardId });
        const result = await workspace.transact({
          type: "resource.attach",
          cardId,
          relativePath: `resources/cards/${cardId}/shared.txt`,
          sha256,
          size: Buffer.byteLength(payload),
          originalName: "共享设定.txt"
        });
        globalResourceId = result.resourceId;
        const global = (await workspace.read({ kind: "resource.list", cardId })) as ResourceInfo[];
        assert.equal(global.length, 1);
        assert.equal(global[0]!.projectId, null);
        assert.equal(global[0]!.ownerScope, "card");
        const originalProjection = (await workspace.read({ kind: "resource.list", projectId, cardId })) as ResourceInfo[];
        const otherProjection = (await workspace.read({ kind: "resource.list", projectId: other.projectId, cardId })) as ResourceInfo[];
        assert.equal(originalProjection.some((item) => item.id === globalResourceId), true);
        assert.equal(otherProjection.some((item) => item.id === globalResourceId), true);
        assert.equal(otherProjection.some((item) => item.id === resourceId), false, "其他项目不得读取原项目的旧附件");
        const cover = await workspace.transact({
          type: "resource.attach", cardId, role: "cover",
          relativePath: `resources/cards/${cardId}/cover.png`, sha256, size: 1, originalName: "封面.png"
        });
        coverResourceId = cover.resourceId;
        let duplicateCover: unknown;
        try {
          await workspace.transact({
            type: "resource.attach", cardId, role: "cover",
            relativePath: `resources/cards/${cardId}/cover-2.png`, sha256, size: 1
          });
        } catch (error) { duplicateCover = error; }
        assert.equal((duplicateCover as CreationWorkspaceError).code, "conflict");
      });
    });

    await scenario("移除附件 + 跨项目卡片拒绝", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        const other = await workspace.transact({ type: "project.create", title: "另一项目" });
        const otherCard = await workspace.transact({ type: "card.create", projectId: other.projectId, kind: "character", title: "他人" }) as { entityId: string };
        let foreign: unknown;
        try {
          await workspace.transact({
            type: "resource.attach",
            projectId,
            cardId: otherCard.entityId,
            relativePath: `project-${projectId}/att-3.txt`,
            sha256,
            size: 1
          });
        } catch (error) {
          foreign = error;
        }
        assert.equal((foreign as CreationWorkspaceError).code, "invalid-input");
        await workspace.transact({ type: "resource.detach", resourceId });
        const list = (await workspace.read({ kind: "resource.list", projectId })) as ResourceInfo[];
        assert.equal(list.some((item) => item.id === resourceId), false);
        assert.equal(list.some((item) => item.id === globalResourceId && item.ownerScope === "card"), true);
        let missing: unknown;
        try {
          await workspace.transact({ type: "resource.detach", resourceId });
        } catch (error) {
          missing = error;
        }
        assert.equal((missing as CreationWorkspaceError).code, "not-found");
      });
    });

    await scenario("全局附件可独立移除", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        await workspace.transact({ type: "resource.detach", resourceId: globalResourceId });
        await workspace.transact({ type: "resource.detach", resourceId: coverResourceId });
        const list = (await workspace.read({ kind: "resource.list", cardId })) as ResourceInfo[];
        assert.equal(list.length, 0);
      });
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
