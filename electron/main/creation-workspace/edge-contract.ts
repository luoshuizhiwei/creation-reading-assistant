import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationWorkspace,
  type CreationSearchView,
  type ProjectStatsView
} from "./index";

async function withWorkspace<T>(directory: string, fn: (workspace: CreationWorkspace) => Promise<T>): Promise<T> {
  const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
  try {
    return await fn(workspace);
  } finally {
    await workspace.close();
  }
}

/** 边界与对抗输入：emoji/代理对、CRLF/BOM、特殊字符、空数据、并发修订、watch 生命周期。 */
async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-edge-"));
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

    await scenario("emoji 与代理对：正文保存/搜索/字数/校对锚点不破坏", async () => {
      await withWorkspace(path.join(parent, "emoji"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "表情测试" });
        const emojiText = "火把🔥 家族纹章𝕏𝕐 波浪𠮷野家";
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: emojiText }] }] }
        });
        const body = await workspace.read({ kind: "scene.body", sceneId: created.sceneId });
        const firstBlock = body?.body.content[0] as { content?: Array<{ text?: string }> } | undefined;
        assert.equal(firstBlock?.content?.[0]?.text, emojiText);
        const search = (await workspace.read({ kind: "search.query", projectId: created.projectId, text: "𝕏" })) as CreationSearchView;
        assert.equal(search.hits.length, 1);
        const stats = (await workspace.read({ kind: "stats.view", projectId: created.projectId })) as ProjectStatsView;
        assert.equal(stats.words.nonWhitespace > 0, true);
        const proof = await workspace.read({ kind: "proof.query", projectId: created.projectId });
        assert.equal(proof.scannedScenes, 1);
      });
    });

    await scenario("CRLF 与 BOM 文本导入被正确清洗", async () => {
      const filePath = path.join(parent, "crlf.txt");
      await import("node:fs/promises").then(({ writeFile }) =>
        writeFile(filePath, "\uFEFF第一章 起点\r\n\r\n第一段正文。\r\n第二段正文。\r\n\r\n第二章 终局\r\n\r\n结尾。", "utf8")
      );
      const { previewLegacyDraft } = await import("../../../electron/main/creation-import");
      const preview = await previewLegacyDraft({ filePath });
      assert.equal(preview.volumes[0]!.chapters.length, 2);
      assert.equal(preview.volumes[0]!.chapters[0]!.title, "第一章 起点");
      assert.equal(preview.volumes[0]!.chapters[0]!.body.includes("第一段正文"), true);
      assert.equal(preview.volumes[0]!.chapters[0]!.body.startsWith("\uFEFF"), false);
    });

    await scenario("特殊字符字面量：反斜杠/下划线/引号在搜索与替换中按字面量处理", async () => {
      await withWorkspace(path.join(parent, "special"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "特殊字符" });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "路径C:\\目录 与 下划线_和引号\"结尾" }] }] }
        });
        const backslash = (await workspace.read({ kind: "search.query", projectId: created.projectId, text: "\\" })) as CreationSearchView;
        assert.equal(backslash.hits.length, 1);
        const underscore = (await workspace.read({ kind: "search.query", projectId: created.projectId, text: "_" })) as CreationSearchView;
        assert.equal(underscore.hits.length, 1);
        const preview = await workspace.read({
          kind: "replace.preview",
          projectId: created.projectId,
          find: "引号\"结尾",
          replaceWith: "引号\"ok",
          scope: "project"
        });
        assert.equal(preview.totalHits, 1);
      });
    });

    await scenario("空场景与空项目：导出/统计/校对/批注不崩溃", async () => {
      await withWorkspace(path.join(parent, "empty"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "空项目" });
        const outline = await workspace.read({ kind: "project.outline", projectId: created.projectId });
        assert.equal(outline?.volumes[0]?.chapters[0]?.scenes[0]?.id, created.sceneId);
        const stats = (await workspace.read({ kind: "stats.view", projectId: created.projectId })) as ProjectStatsView;
        assert.equal(stats.words.han, 0);
        const proof = await workspace.read({ kind: "proof.query", projectId: created.projectId });
        assert.equal(proof.total, 0);
        const exportView = await workspace.read({ kind: "project.export", projectId: created.projectId });
        assert.equal(exportView?.volumes[0]?.chapters[0]?.scenes[0]?.text, "");
        const search = (await workspace.read({ kind: "search.query", projectId: created.projectId, text: "x" })) as CreationSearchView;
        assert.equal(search.hits.length, 0);
        // 空项目也可批注（锚点校验会拒绝无效锚点）
        let invalid: unknown;
        try {
          await workspace.transact({
            type: "annotation.create",
            projectId: created.projectId,
            sceneId: created.sceneId,
            anchor: { blockIndex: 0, textOffset: 0, textLength: 1 },
            note: "空场景锚点"
          });
        } catch (error) {
          invalid = error;
        }
        assert.equal((invalid as CreationWorkspaceError).code, "invalid-input");
      });
    });

    await scenario("并发修订：陈旧 baseRevision 被拒绝且不覆盖新内容", async () => {
      await withWorkspace(path.join(parent, "concurrent"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "并发测试" });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "第一版" }] }] }
        });
        const first = await workspace.read({ kind: "scene.body", sceneId: created.sceneId });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: first!.revision,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "第二版" }] }] }
        });
        let stale: unknown;
        try {
          await workspace.transact({
            type: "scene.updateBody",
            sceneId: created.sceneId,
            baseRevision: first!.revision,
            body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "旧客户端覆盖" }] }] }
          });
        } catch (error) {
          stale = error;
        }
        assert.equal((stale as CreationWorkspaceError).code, "revision-mismatch");
        const final = await workspace.read({ kind: "scene.body", sceneId: created.sceneId });
        assert.equal(JSON.stringify(final?.body).includes("第二版"), true);
        assert.equal(JSON.stringify(final?.body).includes("旧客户端覆盖"), false);
      });
    });

    await scenario("watch 生命周期：订阅收到已提交事件、退订后不再收到、项目过滤生效", async () => {
      await withWorkspace(path.join(parent, "watch"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "订阅测试" });
        const events: Array<{ projectId: string | null; commandType: string }> = [];
        const unsubscribeA = workspace.watch({ projectId: created.projectId }, (event) => {
          events.push({ projectId: event.projectId, commandType: event.commandType });
        });
        void unsubscribeA;
        let otherEvents = 0;
        const unsubscribeB = workspace.watch({}, () => {
          otherEvents += 1;
        });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: created.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [] }
        });
        const other = await workspace.transact({ type: "project.create", title: "另一个项目" });
        await workspace.transact({ type: "scene.updateBody", sceneId: other.sceneId, baseRevision: 1, body: { type: "doc", content: [] } });
        assert.equal(events.filter((event) => event.commandType === "scene.updateBody").length, 1);
        assert.equal(otherEvents, 3);
        unsubscribeA();
        // A 退订后的事件不再发给 A；B（全 scope）仍收到
        await workspace.transact({ type: "scene.updateBody", sceneId: created.sceneId, baseRevision: 2, body: { type: "doc", content: [] } });
        assert.equal(events.length, 1);
        assert.equal(otherEvents, 4);
        unsubscribeB();
        await workspace.transact({ type: "scene.updateBody", sceneId: other.sceneId, baseRevision: 2, body: { type: "doc", content: [] } });
        assert.equal(otherEvents, 4);
      });
    });

    await scenario("不存在的实体读取返回 null 或 not-found，不抛崩溃", async () => {
      await withWorkspace(path.join(parent, "missing"), async (workspace) => {
        assert.equal(await workspace.read({ kind: "project.tree", projectId: "project-nope" }), null);
        assert.equal(await workspace.read({ kind: "project.outline", projectId: "project-nope" }), null);
        assert.equal(await workspace.read({ kind: "scene.body", sceneId: "scene-nope" }), null);
        assert.equal(await workspace.read({ kind: "card.read", cardId: "card-nope" }), null);
        assert.equal(await workspace.read({ kind: "project.bundle.export", projectId: "project-nope" }), null);
        let trashError: unknown;
        try {
          await workspace.read({ kind: "trash.list", projectId: "project-nope" });
        } catch (error) {
          trashError = error;
        }
        assert.equal((trashError as CreationWorkspaceError).code, "not-found");
        let snapshotError: unknown;
        try {
          await workspace.read({ kind: "snapshot.list", projectId: "project-nope" });
        } catch (error) {
          snapshotError = error;
        }
        assert.equal((snapshotError as CreationWorkspaceError).code, "not-found");
      });
    });

    await scenario("超长输入与非法参数被拒绝且数据库不受污染", async () => {
      await withWorkspace(path.join(parent, "limits"), async (workspace) => {
        const created = await workspace.transact({ type: "project.create", title: "限制测试" });
        let longTitle: unknown;
        try {
          await workspace.transact({ type: "project.create", title: "x".repeat(201) });
        } catch (error) {
          longTitle = error;
        }
        assert.equal((longTitle as CreationWorkspaceError).code, "invalid-input");
        let longBody: unknown;
        try {
          await workspace.transact({
            type: "scene.updateBody",
            sceneId: created.sceneId,
            baseRevision: 1,
            body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "x".repeat(6_000_000) }] }] }
          });
        } catch (error) {
          longBody = error;
        }
        assert.equal((longBody as CreationWorkspaceError).code, "invalid-input");
        const list = (await workspace.read({ kind: "projects.list" })) as Array<{ title: string }>;
        assert.equal(list.filter((item) => item.title === "x".repeat(201)).length, 0);
        const body = await workspace.read({ kind: "scene.body", sceneId: created.sceneId });
        assert.equal(JSON.stringify(body?.body).includes("x".repeat(6_000_000)), false);
      });
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    for (let attempt = 0; attempt < 5; attempt += 1) {
      try {
        await removeWithRetry(parent);
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
