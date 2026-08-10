import { strict as assert } from "node:assert";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationWorkspace,
  type ReplaceApplyResult,
  type ReplacePreviewView
} from "./index";

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-replace-"));
  let workspace: CreationWorkspace | undefined;
  let tests = 0;
  let projectId = "";
  let chapterId = "";
  let sceneA = "";
  let sceneB = "";
  let volumeId = "";
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

    await scenario("准备项目：两个场景的正文含重复词", async () => {
      const created = await workspace!.transact({ type: "project.create", title: "替换测试项目" });
      projectId = created.projectId;
      chapterId = created.chapterId;
      volumeId = created.volumeId;
      sceneA = created.sceneId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: sceneA,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [
            {
              type: "paragraph",
              content: [{ type: "text", text: "黄沙镇的风吹过黄沙镇的街。", marks: [{ type: "bold" }] }]
            },
            { type: "paragraph", content: [{ type: "text", text: "油灯还亮着。" }] },
            { type: "paragraph", content: [{ type: "text", text: "第一章的结尾。" }] }
          ]
        }
      });
      const extra = await workspace!.transact({
        type: "scene.create",
        chapterId,
        title: "第二场景"
      }) as { entityId: string };
      sceneB = extra.entityId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: sceneB,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [{ type: "paragraph", content: [{ type: "text", text: "黄沙镇不在了。" }] }]
        }
      });
    });

    await scenario("预览：全项目命中统计与上下文片段", async () => {
      const view = (await workspace!.read({
        kind: "replace.preview",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "project"
      })) as ReplacePreviewView;
      assert.equal(view.totalHits, 3);
      assert.equal(view.matchedScenes, 2);
      assert.equal(view.sceneHits.length, 2);
      const hitA = view.sceneHits.find((hit) => hit.sceneId === sceneA)!;
      assert.equal(hitA.count, 2);
      assert.equal(hitA.snippets.length, 2);
      assert.equal(hitA.snippets[0]!.includes("黄沙镇"), true);
      const hitB = view.sceneHits.find((hit) => hit.sceneId === sceneB)!;
      assert.equal(hitB.count, 1);
    });

    await scenario("预览：范围限定到单场景", async () => {
      const view = (await workspace!.read({
        kind: "replace.preview",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "scene",
        scopeId: sceneA
      })) as ReplacePreviewView;
      assert.equal(view.totalHits, 2);
      assert.equal(view.matchedScenes, 1);
      assert.equal(view.sceneHits[0]!.sceneId, sceneA);
    });

    await scenario("预览：范围限定到章节/卷且校验归属", async () => {
      const chapterView = (await workspace!.read({
        kind: "replace.preview",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "chapter",
        scopeId: chapterId
      })) as ReplacePreviewView;
      assert.equal(chapterView.totalHits, 3);
      const volumeView = (await workspace!.read({
        kind: "replace.preview",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "volume",
        scopeId: volumeId
      })) as ReplacePreviewView;
      assert.equal(volumeView.totalHits, 3);
      let foreignError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "黄沙镇",
          replaceWith: "雾都城",
          scope: "chapter",
          scopeId: "chapter-不存在"
        });
      } catch (error) {
        foreignError = error;
      }
      assert.equal((foreignError as CreationWorkspaceError).code, "not-found");
    });

    await scenario("执行替换：命中场景被修改并自动建保护快照", async () => {
      const result = (await workspace!.transact({
        type: "replace.apply",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "project"
      })) as ReplaceApplyResult;
      assert.equal(result.appliedScenes, 2);
      assert.equal(result.appliedHits, 3);
      assert.equal(result.skippedScenes, 0);
      assert.equal(result.snapshotIds.length, 2);
      const bodyA = await workspace!.read({ kind: "scene.body", sceneId: sceneA });
      assert.equal(JSON.stringify(bodyA?.body).includes("雾都城的风吹过雾都城的街"), true);
      const bodyB = await workspace!.read({ kind: "scene.body", sceneId: sceneB });
      assert.equal(JSON.stringify(bodyB?.body).includes("雾都城不在了"), true);
      const snapshots = (await workspace!.read({
        kind: "snapshot.list",
        projectId,
        subjectType: "scene",
        subjectId: sceneA
      })) as Array<{ reason: string }>;
      assert.equal(snapshots.some((item) => item.reason === "查找替换自动备份"), true);
    });

    await scenario("执行替换：排除的场景不被修改", async () => {
      const before = await workspace!.read({ kind: "scene.body", sceneId: sceneA });
      const result = (await workspace!.transact({
        type: "replace.apply",
        projectId,
        find: "雾都城",
        replaceWith: "旧月城",
        scope: "project",
        excludeSceneIds: [sceneA]
      })) as ReplaceApplyResult;
      assert.equal(result.appliedScenes, 1);
      assert.equal(result.appliedHits, 1);
      assert.equal(result.skippedScenes, 1);
      const after = await workspace!.read({ kind: "scene.body", sceneId: sceneA });
      assert.equal(JSON.stringify(after?.body), JSON.stringify(before?.body));
      const bodyB = await workspace!.read({ kind: "scene.body", sceneId: sceneB });
      assert.equal(JSON.stringify(bodyB?.body).includes("旧月城不在了"), true);
    });

    await scenario("执行替换：无命中时不写快照且返回零", async () => {
      const result = (await workspace!.transact({
        type: "replace.apply",
        projectId,
        find: "不存在的词",
        replaceWith: "x",
        scope: "project"
      })) as ReplaceApplyResult;
      assert.equal(result.appliedScenes, 0);
      assert.equal(result.appliedHits, 0);
      assert.equal(result.snapshotIds.length, 0);
    });

    await scenario("受限正则：基本替换与 $1 捕获组引用", async () => {
      const result = (await workspace!.transact({
        type: "replace.apply",
        projectId,
        find: "第(.)章",
        replaceWith: "第七$1章",
        scope: "scene",
        scopeId: sceneA,
        regex: true
      })) as ReplaceApplyResult;
      assert.equal(result.appliedScenes, 1);
      assert.equal(result.appliedHits, 1);
      const bodyA = await workspace!.read({ kind: "scene.body", sceneId: sceneA });
      assert.equal(JSON.stringify(bodyA?.body).includes("第七一章的结尾"), true);
    });

    await scenario("受限正则：拒绝环视与后向引用", async () => {
      let lookaheadError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "(?=黄沙镇)",
          replaceWith: "x",
          scope: "project",
          regex: true
        });
      } catch (error) {
        lookaheadError = error;
      }
      assert.equal((lookaheadError as CreationWorkspaceError).code, "invalid-input");
      let backrefError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "(黄)\\1",
          replaceWith: "x",
          scope: "project",
          regex: true
        });
      } catch (error) {
        backrefError = error;
      }
      assert.equal((backrefError as CreationWorkspaceError).code, "invalid-input");
      let invalidError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "(未闭合",
          replaceWith: "x",
          scope: "project",
          regex: true
        });
      } catch (error) {
        invalidError = error;
      }
      assert.equal((invalidError as CreationWorkspaceError).code, "invalid-input");
    });

    await scenario("空查找文本与非法范围报 invalid-input", async () => {
      let emptyError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "  ",
          replaceWith: "x",
          scope: "project"
        });
      } catch (error) {
        emptyError = error;
      }
      assert.equal((emptyError as CreationWorkspaceError).code, "invalid-input");
      let scopeError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "黄沙镇",
          replaceWith: "x",
          scope: "shelf" as never
        });
      } catch (error) {
        scopeError = error;
      }
      assert.equal((scopeError as CreationWorkspaceError).code, "invalid-input");
    });

    await scenario("正文结构保持：粗体标记与场景分隔不受替换影响", async () => {
      const bodyA = await workspace!.read({ kind: "scene.body", sceneId: sceneA });
      const text = JSON.stringify(bodyA?.body);
      assert.equal(text.includes('"type":"bold"'), true);
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await workspace?.close();
    await rm(directory, { recursive: true, force: true });
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
