import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationSearchView,
  type CreationWorkspace
} from "./index";

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-search-"));
  let workspace: CreationWorkspace | undefined;
  let tests = 0;
  let projectA = "";
  let projectB = "";
  let extraChapterId = "";
  let sceneABodyId = "";
  let cardAId = "";
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
    const report = await workspace.check();
    assert.equal(report.ok, true);

    await scenario("准备两个项目与可搜索数据", async () => {
      const a = await workspace!.transact({ type: "project.create", title: "黄沙镇纪事" });
      projectA = a.projectId;
      const extraChapter = await workspace!.transact({
        type: "chapter.create",
        projectId: projectA,
        volumeId: a.volumeId,
        title: "第二章"
      }) as { entityId: string };
      extraChapterId = extraChapter.entityId;
      const extraScene = await workspace!.transact({
        type: "scene.create",
        chapterId: extraChapter.entityId,
        title: "深夜来信"
      }) as { entityId: string };
      sceneABodyId = extraScene.entityId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: sceneABodyId,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [
            {
              type: "paragraph",
              content: [{ type: "text", text: "黄沙镇的风又吹过街角，油灯在案头忽明忽暗。" }]
            },
            { type: "paragraph", content: [{ type: "text", text: "信纸末尾落款处写着一个熟悉的名字。" }] }
          ]
        }
      });
      const card = await workspace!.transact({
        type: "card.create",
        projectId: projectA,
        kind: "character",
        title: "沈砚",
        aliases: ["阿砚", "黄沙镇的小店主"],
        tags: ["主角", "黄沙镇"]
      }) as { entityId: string };
      cardAId = card.entityId;
      const b = await workspace!.transact({ type: "project.create", title: "雾都档案" });
      projectB = b.projectId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: b.sceneId,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [{ type: "paragraph", content: [{ type: "text", text: "雾都的雨夜，黄沙镇来的旅人敲响了门。" }] }]
        }
      });
    });

    await scenario("项目内正文搜索命中场景并带上下文片段", async () => {
      const view = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "油灯"
      })) as CreationSearchView;
      assert.equal(view.hits.length, 1);
      const hit = view.hits[0]!;
      assert.equal(hit.kind, "scene");
      assert.equal(hit.id, sceneABodyId);
      assert.equal(hit.chapterId, extraChapterId);
      assert.equal(hit.projectTitle, "黄沙镇纪事");
      assert.equal(hit.snippet?.includes("油灯"), true);
      assert.equal(view.total, 1);
    });

    await scenario("全局搜索跨项目命中场景正文", async () => {
      const view = (await workspace!.read({ kind: "search.query", text: "黄沙镇" })) as CreationSearchView;
      assert.equal(view.total, 4); // 项目 A 场景正文 + 项目 A 卡片(标题/别名/标签) + 项目 A 标题 + 项目 B 场景正文
      const scenes = view.hits.filter((h) => h.kind === "scene");
      assert.equal(scenes.length, 2);
      assert.equal(scenes.some((h) => h.projectTitle === "雾都档案"), true);
      assert.equal(scenes.some((h) => h.projectTitle === "黄沙镇纪事"), true);
      assert.equal(view.hits.some((h) => h.kind === "project" && h.title === "黄沙镇纪事"), true);
    });

    await scenario("范围过滤：只搜卡片", async () => {
      const view = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "黄沙镇",
        scopes: ["card"]
      })) as CreationSearchView;
      assert.equal(view.hits.length, 1);
      assert.equal(view.hits[0]!.kind, "card");
      assert.equal(view.hits[0]!.id, cardAId);
      assert.equal(view.hits[0]!.cardKind, "character");
    });

    await scenario("卡片类型筛选", async () => {
      const none = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "黄沙镇",
        scopes: ["card"],
        filters: { cardKinds: ["location"] }
      })) as CreationSearchView;
      assert.equal(none.hits.length, 0);
      const some = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "黄沙镇",
        scopes: ["card"],
        filters: { cardKinds: ["character"] }
      })) as CreationSearchView;
      assert.equal(some.hits.length, 1);
    });

    await scenario("卡片标签筛选（全命中）", async () => {
      const view = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "沈砚",
        scopes: ["card"],
        filters: { tags: ["主角"] }
      })) as CreationSearchView;
      assert.equal(view.hits.length, 1);
      const miss = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "沈砚",
        scopes: ["card"],
        filters: { tags: ["不存在"] }
      })) as CreationSearchView;
      assert.equal(miss.hits.length, 0);
    });

    await scenario("章节标题搜索与状态筛选", async () => {
      await workspace!.transact({ type: "chapter.setStatus", chapterId: extraChapterId, status: "待写", baseRevision: 1 });
      const all = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "第二章",
        scopes: ["chapter"]
      })) as CreationSearchView;
      assert.equal(all.hits.length, 1);
      assert.equal(all.hits[0]!.kind, "chapter");
      assert.equal(all.hits[0]!.chapterStatus, "待写");
      const none = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "第二章",
        scopes: ["chapter"],
        filters: { chapterStatuses: ["已发布"] }
      })) as CreationSearchView;
      assert.equal(none.hits.length, 0);
    });

    await scenario("项目标题搜索", async () => {
      const view = (await workspace!.read({
        kind: "search.query",
        text: "雾都",
        scopes: ["project"]
      })) as CreationSearchView;
      assert.equal(view.hits.length, 1);
      assert.equal(view.hits[0]!.kind, "project");
      assert.equal(view.hits[0]!.id, projectB);
    });

    await scenario("limit 生效且总数为实际返回数", async () => {
      const view = (await workspace!.read({ kind: "search.query", text: "黄沙镇", limit: 2 })) as CreationSearchView;
      assert.equal(view.hits.length, 2);
      assert.equal(view.total, 2);
    });

    await scenario("软删除的实体不再被搜索到", async () => {
      await workspace!.transact({ type: "scene.delete", sceneId: sceneABodyId });
      const view = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "油灯"
      })) as CreationSearchView;
      assert.equal(view.hits.length, 0);
      await workspace!.transact({ type: "card.delete", cardId: cardAId });
      const cards = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "黄沙镇",
        scopes: ["card"]
      })) as CreationSearchView;
      assert.equal(cards.hits.length, 0);
    });

    await scenario("空关键词与非法 limit 报 invalid-input", async () => {
      let emptyError: unknown;
      try {
        await workspace!.read({ kind: "search.query", text: "   " });
      } catch (error) {
        emptyError = error;
      }
      assert.equal((emptyError as CreationWorkspaceError).code, "invalid-input");
      let hugeLimitError: unknown;
      try {
        await workspace!.read({ kind: "search.query", text: "黄沙镇", limit: 9999 });
      } catch (error) {
        hugeLimitError = error;
      }
      assert.equal((hugeLimitError as CreationWorkspaceError).code, "invalid-input");
    });

    await scenario("LIKE 通配符按字面量处理", async () => {
      const card = await workspace!.transact({
        type: "card.create",
        projectId: projectA,
        kind: "item",
        title: "100%纯棉手帕"
      }) as { entityId: string };
      const view = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "100%",
        scopes: ["card"]
      })) as CreationSearchView;
      assert.equal(view.hits.some((h) => h.id === card.entityId), true);
      const everything = (await workspace!.read({
        kind: "search.query",
        projectId: projectA,
        text: "%",
        scopes: ["card"]
      })) as CreationSearchView;
      // % 按字面量处理：只命中真正含 % 的卡片，而不是通配全部
      assert.equal(everything.hits.length, 1);
      assert.equal(everything.hits[0]!.id, card.entityId);
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
