import { strict as assert } from "node:assert";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type Annotation,
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
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-annotation-"));
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
    let sceneId = "";
    let cardId = "";
    let annotationId = "";

    await scenario("准备项目：正文含两段", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "批注测试" });
        projectId = created.projectId;
        sceneId = created.sceneId;
        await workspace.transact({
          type: "scene.updateBody",
          sceneId,
          baseRevision: 1,
          body: {
            type: "doc",
            content: [
              { type: "paragraph", content: [{ type: "text", text: "黄沙镇的风吹过街角。" }] },
              { type: "paragraph", content: [{ type: "text", text: "油灯在案头忽明忽暗。" }] }
            ]
          }
        });
        const card = await workspace.transact({ type: "card.create", projectId, kind: "character", title: "苏青" }) as { entityId: string };
        cardId = card.entityId;
      });
    });

    await scenario("创建批注：段落锚点 + 关联卡片 + 待处理状态", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        const result = await workspace.transact({
          type: "annotation.create",
          projectId,
          sceneId,
          cardId,
          anchor: { blockIndex: 0, textOffset: 0, textLength: 3 },
          note: "此处提到主角"
        });
        annotationId = result.annotationId;
        assert.equal(annotationId.startsWith("annotation-"), true);
        const list = (await workspace.read({ kind: "annotation.list", projectId, sceneId })) as Annotation[];
        assert.equal(list.length, 1);
        assert.equal(list[0]!.anchorInvalid, false);
        assert.equal(list[0]!.anchoredText, "黄沙镇");
        assert.equal(list[0]!.cardId, cardId);
        assert.equal(list[0]!.status, "open");
      });
    });

    await scenario("编辑正文使锚点失效：进入待重新定位而非静默删除", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        const body = await workspace.read({ kind: "scene.body", sceneId });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId,
          baseRevision: body!.revision,
          body: {
            type: "doc",
            content: [
              { type: "paragraph", content: [{ type: "text", text: "把第一段整个改掉了。" }] },
              { type: "paragraph", content: [{ type: "text", text: "油灯在案头忽明忽暗。" }] }
            ]
          }
        });
        const list = (await workspace.read({ kind: "annotation.list", projectId, sceneId })) as Annotation[];
        assert.equal(list.length, 1);
        assert.equal(list[0]!.anchorInvalid, true);
        assert.equal(list[0]!.anchoredText, "");
      });
    });

    await scenario("批注更新：解决状态与重新关联卡片", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        await workspace.transact({
          type: "annotation.update",
          annotationId,
          baseRevision: 1,
          status: "resolved",
          note: "已确认"
        });
        const list = (await workspace.read({ kind: "annotation.list", projectId })) as Annotation[];
        assert.equal(list[0]!.status, "resolved");
        assert.equal(list[0]!.note, "已确认");
        let mismatch: unknown;
        try {
          await workspace.transact({
            type: "annotation.update",
            annotationId,
            baseRevision: 1,
            status: "open"
          });
        } catch (error) {
          mismatch = error;
        }
        assert.equal((mismatch as CreationWorkspaceError).code, "revision-mismatch");
      });
    });

    await scenario("批注删除 + 非法参数校验", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        await workspace.transact({ type: "annotation.delete", annotationId });
        const list = (await workspace.read({ kind: "annotation.list", projectId })) as Annotation[];
        assert.equal(list.length, 0);
        let badAnchor: unknown;
        try {
          await workspace.transact({
            type: "annotation.create",
            projectId,
            sceneId,
            anchor: { blockIndex: -1, textOffset: 0, textLength: 0 },
            note: "坏锚点"
          });
        } catch (error) {
          badAnchor = error;
        }
        assert.equal((badAnchor as CreationWorkspaceError).code, "invalid-input");
        let foreignCard: unknown;
        try {
          await workspace.transact({
            type: "annotation.create",
            projectId,
            sceneId,
            cardId: "card-missing",
            anchor: { blockIndex: 0, textOffset: 0, textLength: 1 },
            note: "x"
          });
        } catch (error) {
          foreignCard = error;
        }
        assert.equal((foreignCard as CreationWorkspaceError).code, "not-found");
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
