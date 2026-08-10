import { strict as assert } from "node:assert";
import { createHash } from "node:crypto";
import { mkdtemp, rm } from "node:fs/promises";
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
        assert.equal(list.length, 0);
        let missing: unknown;
        try {
          await workspace.transact({ type: "resource.detach", resourceId });
        } catch (error) {
          missing = error;
        }
        assert.equal((missing as CreationWorkspaceError).code, "not-found");
      });
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await rm(parent, { recursive: true, force: true });
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
