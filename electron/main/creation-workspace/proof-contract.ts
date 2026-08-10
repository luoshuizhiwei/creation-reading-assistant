import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationWorkspace,
  type ProofView
} from "./index";

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-proof-"));
  let workspace: CreationWorkspace | undefined;
  let tests = 0;
  let projectId = "";
  let chapterId = "";
  let sceneA = "";
  let sceneB = "";
  let bodyA: unknown;
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

    await scenario("准备项目：场景含各类校对问题", async () => {
      const created = await workspace!.transact({ type: "project.create", title: "校对测试项目" });
      projectId = created.projectId;
      chapterId = created.chapterId;
      sceneA = created.sceneId;
      const longParagraph = "他沿着河岸走了又走，看见对岸的灯火明明灭灭，心想这世上总有说不清的事。".repeat(30);
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: sceneA,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "他他他站在门口，手里握着 信和 一封信。" }] },
            { type: "paragraph", content: [{ type: "text", text: "他说：「这件事（我们明天再说。" }] },
            { type: "paragraph", content: [{ type: "text", text: " 段首半角空格。" }] },
            { type: "paragraph", content: [{ type: "text", text: longParagraph }] },
            { type: "paragraph", content: [{ type: "text", text: "这里有个禁用词汇测试。" }] }
          ]
        }
      });
      bodyA = (await workspace!.read({ kind: "scene.body", sceneId: sceneA }))!.body;
      const extra = await workspace!.transact({
        type: "scene.create",
        chapterId,
        title: "干净场景"
      }) as { entityId: string };
      sceneB = extra.entityId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: sceneB,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [{ type: "paragraph", content: [{ type: "text", text: "这是一段完全正常的正文。" }] }]
        }
      });
    });

    await scenario("全部规则命中且只提示不修改", async () => {
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        bannedWords: ["禁用词"]
      })) as ProofView;
      assert.equal(view.scannedScenes, 2);
      assert.equal(view.affectedScenes, 1);
      const rules = new Set(view.issues.map((issue) => issue.rule));
      assert.equal(rules.has("repeatedChar"), true);
      assert.equal(rules.has("unbalancedPunctuation"), true);
      assert.equal(rules.has("abnormalSpacing"), true);
      assert.equal(rules.has("longParagraph"), true);
      assert.equal(rules.has("bannedWord"), true);
      const repeated = view.issues.find((issue) => issue.rule === "repeatedChar")!;
      assert.equal(repeated.message.includes("他他他"), true);
      const unbalanced = view.issues.find((issue) => issue.rule === "unbalancedPunctuation")!;
      assert.equal(unbalanced.message.includes("「」"), true);
      const banned = view.issues.find((issue) => issue.rule === "bannedWord")!;
      assert.equal(banned.message.includes("禁用词"), true);
      const after = await workspace!.read({ kind: "scene.body", sceneId: sceneA });
      assert.equal(JSON.stringify(after?.body), JSON.stringify(bodyA));
    });

    await scenario("范围限定单场景与规则裁剪", async () => {
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        sceneId: sceneB
      })) as ProofView;
      assert.equal(view.total, 0);
      const viewA = (await workspace!.read({
        kind: "proof.query",
        projectId,
        sceneId: sceneA,
        rules: ["longParagraph"]
      })) as ProofView;
      assert.equal(viewA.issues.length, 1);
      assert.equal(viewA.issues[0]!.rule, "longParagraph");
    });

    await scenario("禁用词为空时不触发 bannedWord 规则", async () => {
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        sceneId: sceneA,
        rules: ["bannedWord"],
        bannedWords: []
      })) as ProofView;
      assert.equal(view.total, 0);
    });

    await scenario("无效参数报 invalid-input", async () => {
      let thresholdError: unknown;
      try {
        await workspace!.read({
          kind: "proof.query",
          projectId,
          maxParagraphChars: 10
        });
      } catch (error) {
        thresholdError = error;
      }
      assert.equal((thresholdError as CreationWorkspaceError).code, "invalid-input");
      let limitError: unknown;
      try {
        await workspace!.read({
          kind: "proof.query",
          projectId,
          limit: 99999
        });
      } catch (error) {
        limitError = error;
      }
      assert.equal((limitError as CreationWorkspaceError).code, "invalid-input");
      let notFound: unknown;
      try {
        await workspace!.read({
          kind: "proof.query",
          projectId,
          sceneId: "scene-missing"
        });
      } catch (error) {
        notFound = error;
      }
      assert.equal((notFound as CreationWorkspaceError).code, "not-found");
    });

    await scenario("超长段落阈值可调", async () => {
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        sceneId: sceneA,
        rules: ["longParagraph"],
        maxParagraphChars: 4000
      })) as ProofView;
      assert.equal(view.total, 0);
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
