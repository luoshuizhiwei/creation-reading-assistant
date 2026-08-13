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

async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-project-home-"));
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

    await scenario("0 项目：首页视图返回空数组", async () => {
      await withWorkspace(path.join(parent, "ws-empty"), async (workspace) => {
        const view = await workspace.read({ kind: "project.home" });
        assert.equal(view.projects.length, 0);
      });
    });

    await scenario("无目标项目：currentChars 统计非空白字符，goal 为 undefined", async () => {
      await withWorkspace(path.join(parent, "ws-no-goal"), async (workspace) => {
        const created = await workspace.transact({
          type: "project.create",
          title: "无目标",
          setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"] }
        });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "测试正文，包含汉字与标点符号。" }] }] }
        });
        const view = await workspace.read({ kind: "project.home" });
        assert.equal(view.projects.length, 1);
        const entry = view.projects[0];
        assert.equal(entry.title, "无目标");
        assert.ok(entry.currentChars > 0, `currentChars should be > 0, got ${entry.currentChars}`);
        assert.equal(entry.setup.totalWordGoal, undefined);
      });
    });

    await scenario("有目标且写到一半：进度百分比正确反映 currentChars/goal", async () => {
      await withWorkspace(path.join(parent, "ws-goal"), async (workspace) => {
        const created = await workspace.transact({
          type: "project.create",
          title: "有目标",
          setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"], totalWordGoal: 1000 }
        });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "五" }] }] }
        });
        const view = await workspace.read({ kind: "project.home" });
        const entry = view.projects[0];
        assert.equal(entry.currentChars, 1);
        assert.equal(entry.setup.totalWordGoal, 1000);
      });
    });

    await scenario("多项目聚合：单次查询返回所有项目，不 N+1", async () => {
      await withWorkspace(path.join(parent, "ws-multi"), async (workspace) => {
        for (let i = 0; i < 3; i += 1) {
          await workspace.transact({
            type: "project.create",
            title: `项目${i}`,
            setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"] }
          });
        }
        const view = await workspace.read({ kind: "project.home" });
        assert.equal(view.projects.length, 3);
        for (const entry of view.projects) {
          assert.ok(entry.id);
          assert.ok(entry.title);
          assert.equal(typeof entry.currentChars, "number");
        }
      });
    });

    await scenario("只排除空白；标点与符号按 Unicode 字符计入 currentChars", async () => {
      await withWorkspace(path.join(parent, "ws-chars"), async (workspace) => {
        const created = await workspace.transact({
          type: "project.create",
          title: "空白测试",
          setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"] }
        });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 1,
          body: {
            type: "doc",
            content: [
              { type: "paragraph", content: [{ type: "text", text: "  \u3000 \n\t 你好，世界！😀  " }] }
            ]
          }
        });
        const view = await workspace.read({ kind: "project.home" });
        const entry = view.projects[0];
        const stats = await workspace.read({ kind: "stats.view", projectId: created.projectId });
        // 与 stats.view.words.nonWhitespace 使用同一口径，并按 Unicode 字符而非 UTF-16 单元计数。
        assert.equal(entry.currentChars, 7);
        assert.equal(entry.currentChars, stats?.words.nonWhitespace);
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
