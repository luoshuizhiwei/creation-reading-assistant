import { strict as assert } from "node:assert";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationWorkspace,
  type ScenePlanning
} from "./index";

async function withWorkspace<T>(directory: string, fn: (workspace: CreationWorkspace) => Promise<T>): Promise<T> {
  const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
  try {
    return await fn(workspace);
  } finally {
    await workspace.close();
  }
}

/** 场景任务卡：视角/时间/地点/出场/目标/冲突/结果/情绪/目标字数。 */
async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-planning-"));
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

    await scenario("更新场景任务卡：九个字段完整写入并随大纲返回", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "任务卡测试" });
        const viewpoint = await workspace.transact({ type: "card.create", projectId: created.projectId, kind: "character", title: "苏青" }) as { entityId: string };
        const location = await workspace.transact({ type: "card.create", projectId: created.projectId, kind: "location", title: "黄沙镇" }) as { entityId: string };
        const cast1 = await workspace.transact({ type: "card.create", projectId: created.projectId, kind: "character", title: "顾淮" }) as { entityId: string };
        const cast2 = await workspace.transact({ type: "card.create", projectId: created.projectId, kind: "character", title: "沈砚" }) as { entityId: string };

        const result = await workspace.transact({
          type: "scene.updatePlanning",
          sceneId: created.sceneId,
          planning: {
            perspectiveCardId: viewpoint.entityId,
            time: "入夜后",
            locationCardId: location.entityId,
            castCardIds: [cast1.entityId, cast2.entityId],
            goal: "找到信",
            conflict: "油灯将熄",
            outcome: "信被风卷走",
            emotion: "焦灼",
            targetWords: 2500
          }
        });
        assert.equal(result.commandType, "scene.updatePlanning");
        const outline = await workspace.read({ kind: "project.outline", projectId: created.projectId });
        const planning = outline?.volumes[0]?.chapters[0]?.scenes[0]?.planning;
        assert.equal(planning?.perspectiveCardId, viewpoint.entityId);
        assert.equal(planning?.time, "入夜后");
        assert.equal(planning?.locationCardId, location.entityId);
        assert.deepEqual(planning?.castCardIds, [cast1.entityId, cast2.entityId]);
        assert.equal(planning?.goal, "找到信");
        assert.equal(planning?.conflict, "油灯将熄");
        assert.equal(planning?.outcome, "信被风卷走");
        assert.equal(planning?.emotion, "焦灼");
        assert.equal(planning?.targetWords, 2500);
      });
    });

    await scenario("部分字段更新与清空语义", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "部分更新" });
        await workspace.transact({
          type: "scene.updatePlanning",
          sceneId: created.sceneId,
          planning: { goal: "目标A", emotion: "平静", targetWords: 1000 }
        });
        await workspace.transact({
          type: "scene.updatePlanning",
          sceneId: created.sceneId,
          planning: { goal: "目标B" }
        });
        const outline = await workspace.read({ kind: "project.outline", projectId: created.projectId });
        const planning = outline?.volumes[0]?.chapters[0]?.scenes[0]?.planning as ScenePlanning | undefined;
        assert.equal(planning?.goal, "目标B");
        assert.equal(planning?.emotion, "平静");
        assert.equal(planning?.targetWords, 1000);
      });
    });

    await scenario("跨项目卡片引用与非法参数拒绝", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "校验测试" });
        const other = await workspace.transact({ type: "project.create", title: "另一项目" });
        const foreignCard = await workspace.transact({ type: "card.create", projectId: other.projectId, kind: "character", title: "外人" }) as { entityId: string };
        let foreign: unknown;
        try {
          await workspace.transact({
            type: "scene.updatePlanning",
            sceneId: created.sceneId,
            planning: { perspectiveCardId: foreignCard.entityId }
          });
        } catch (error) {
          foreign = error;
        }
        assert.equal((foreign as CreationWorkspaceError).code, "invalid-input");
        let missing: unknown;
        try {
          await workspace.transact({
            type: "scene.updatePlanning",
            sceneId: created.sceneId,
            planning: { locationCardId: "card-nope" }
          });
        } catch (error) {
          missing = error;
        }
        assert.equal((missing as CreationWorkspaceError).code, "not-found");
        let badWords: unknown;
        try {
          await workspace.transact({
            type: "scene.updatePlanning",
            sceneId: created.sceneId,
            planning: { targetWords: 0 }
          });
        } catch (error) {
          badWords = error;
        }
        assert.equal((badWords as CreationWorkspaceError).code, "invalid-input");
        let longTime: unknown;
        try {
          await workspace.transact({
            type: "scene.updatePlanning",
            sceneId: created.sceneId,
            planning: { time: "x".repeat(201) }
          });
        } catch (error) {
          longTime = error;
        }
        assert.equal((longTime as CreationWorkspaceError).code, "invalid-input");
        let noScene: unknown;
        try {
          await workspace.transact({
            type: "scene.updatePlanning",
            sceneId: "scene-nope",
            planning: { goal: "x" }
          });
        } catch (error) {
          noScene = error;
        }
        assert.equal((noScene as CreationWorkspaceError).code, "not-found");
      });
    });

    await scenario("更新规划不影响正文与场景 revision", async () => {
      await withWorkspace(path.join(parent, "ws"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "revision 测试" });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "正文内容" }] }] }
        });
        const before = await workspace.read({ kind: "scene.body", sceneId: created.sceneId });
        await workspace.transact({
          type: "scene.updatePlanning",
          sceneId: created.sceneId,
          planning: { goal: "x" }
        });
        const after = await workspace.read({ kind: "scene.body", sceneId: created.sceneId });
        assert.equal(after?.revision, before?.revision);
        assert.equal(JSON.stringify(after?.body).includes("正文内容"), true);
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
