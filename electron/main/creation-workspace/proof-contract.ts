import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp } from "node:fs/promises";
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
            { type: "paragraph", content: [{ type: "text", text: "这里有个禁用词汇测试。" }] },
            { type: "paragraph", content: [{ type: "text", text: "他笑了笑,说声再见." }] },
            { type: "paragraph", content: [{ type: "text", text: "突然他停住。顿时风起。瞬间云散。突然天黑。突然他笑了。" }] },
            { type: "paragraph", content: [{ type: "text", text: "他们向前走。" }] },
            { type: "paragraph", content: [{ type: "text", text: "他们看见灯火。" }] },
            { type: "paragraph", content: [{ type: "text", text: "他们决定过河。" }] }
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

    await scenario("新增强化规则：混用标点/口头禅/段落开头重复", async () => {
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        sceneId: sceneA,
        rules: ["mixedPunctuation", "crutchWord", "paragraphStartRepeat"]
      })) as ProofView;
      const byRule = new Map(view.issues.map((issue) => [issue.rule, issue]));
      const mixed = byRule.get("mixedPunctuation");
      assert.ok(mixed, "应命中中英混用标点");
      assert.equal(mixed!.message.includes("半角标点"), true);
      assert.ok(mixed!.snippet!.includes("笑了笑"));
      const crutch = byRule.get("crutchWord");
      assert.ok(crutch, "应命中口头禅");
      assert.equal(crutch!.message.includes("突然"), true);
      const startRepeat = byRule.get("paragraphStartRepeat");
      assert.ok(startRepeat, "应命中段落开头重复");
      assert.equal(startRepeat!.message.includes("连续段落"), true);
      // 干净场景在新规则下仍然干净
      const clean = (await workspace!.read({
        kind: "proof.query",
        projectId,
        sceneId: sceneB,
        rules: ["mixedPunctuation", "crutchWord", "paragraphStartRepeat"]
      })) as ProofView;
      assert.equal(clean.total, 0);
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
