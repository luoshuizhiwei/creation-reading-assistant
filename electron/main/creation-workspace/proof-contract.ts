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

    // -----------------------------------------------------------------------
    // 阶段 4-E：全书扫描范围、按位置持久化忽略、别名一致性、疑似错拼
    // -----------------------------------------------------------------------

    let repeatScene = "";
    let projectB = "";
    let sceneB2 = "";
    await scenario("准备：同文本出现在两个段落 + 第二个隔离项目", async () => {
      const extra = await workspace!.transact({
        type: "scene.create",
        chapterId,
        title: "重复字场景"
      }) as { entityId: string };
      repeatScene = extra.entityId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: repeatScene,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "他他他站在门口。" }] },
            { type: "paragraph", content: [{ type: "text", text: "他他他也很惊讶。" }] }
          ]
        }
      });
      const createdB = await workspace!.transact({ type: "project.create", title: "校对隔离项目B" });
      projectB = createdB.projectId;
      sceneB2 = createdB.sceneId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: sceneB2,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [{ type: "paragraph", content: [{ type: "text", text: "他他他在项目B。" }] }]
        }
      });
    });

    await scenario("扫描范围与问题总数显式可见", async () => {
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        rules: ["repeatedChar"]
      })) as ProofView;
      assert.equal(view.scanScope.kind, "project");
      assert.equal(view.scanScope.label.includes("全书扫描"), true);
      assert.equal(view.scanScope.sceneCount, view.scannedScenes);
      assert.equal(view.scanScope.rules.includes("repeatedChar"), true);
      // 场景 A 一处 + 重复字场景两处 = 3 处未忽略命中。
      assert.equal(view.total, 3);
      assert.equal(view.ignoredCount, 0);
      assert.equal(view.rawTotal, 3);
      assert.equal(view.truncated, false);
      assert.equal(view.ignoreRecordCount, 0);
    });

    await scenario("按位置忽略：同场景另一段的相同文本不受影响", async () => {
      const before = (await workspace!.read({
        kind: "proof.query",
        projectId,
        rules: ["repeatedChar"]
      })) as ProofView;
      const group = before.issues.find((issue) => issue.sceneId === repeatScene)!;
      assert.equal(group.count, 2);
      assert.equal(group.locations.length, 2);
      const first = group.locations[0]!;
      const second = group.locations[1]!;
      // 两段文本相同，但段落序号不同 → 位置键必须不同，否则会一起被忽略掉。
      assert.equal(first.matchedText, second.matchedText);
      assert.notEqual(first.locationKey, second.locationKey);

      await workspace!.transact({
        type: "proof.ignore",
        projectId,
        sceneId: repeatScene,
        rule: "repeatedChar",
        locationKey: first.locationKey,
        matchedText: first.matchedText
      });

      const after = (await workspace!.read({
        kind: "proof.query",
        projectId,
        rules: ["repeatedChar"]
      })) as ProofView;
      assert.equal(after.total, 2, "忽略一处后未忽略总数应减一");
      assert.equal(after.ignoredCount, 1);
      assert.equal(after.rawTotal, 3, "忽略不应改变原始问题总数");
      assert.equal(after.ignoreRecordCount, 1);
      const afterGroup = after.issues.find((issue) => issue.sceneId === repeatScene)!;
      assert.equal(afterGroup.count, 1);
      assert.equal(afterGroup.ignoredCount, 1);
      // 分组内仍列出被忽略的位置，便于逐个取消忽略。
      assert.equal(afterGroup.locations.length, 2);
      assert.equal(afterGroup.locations.find((item) => item.ignored)?.locationKey, first.locationKey);
      assert.equal(afterGroup.locations.filter((item) => !item.ignored)[0]!.locationKey, second.locationKey);
    });

    await scenario("忽略记录不跨项目、不跨场景污染", async () => {
      const viewB = (await workspace!.read({
        kind: "proof.query",
        projectId: projectB,
        rules: ["repeatedChar"]
      })) as ProofView;
      assert.equal(viewB.total, 1, "项目 B 的同文本不应受项目 A 的忽略影响");
      assert.equal(viewB.ignoredCount, 0);
      assert.equal(viewB.ignoreRecordCount, 0);

      const viewA = (await workspace!.read({
        kind: "proof.query",
        projectId,
        sceneId: sceneA,
        rules: ["repeatedChar"]
      })) as ProofView;
      assert.equal(viewA.total, 1, "场景 A 的同文本不应受其它场景的忽略影响");
      assert.equal(viewA.scanScope.kind, "scene");
      assert.equal(viewA.scanScope.label.includes("单场景扫描"), true);
    });

    await scenario("忽略不触碰正文：body 与 revision 完全不变", async () => {
      const bodyBefore = await workspace!.read({ kind: "scene.body", sceneId: repeatScene });
      const bodyJsonBefore = JSON.stringify(bodyBefore?.body);
      await workspace!.transact({
        type: "proof.ignore",
        projectId,
        sceneId: repeatScene,
        rule: "repeatedChar",
        locationKey: "repeatedChar#0#nevermatches",
        matchedText: "占位"
      });
      const bodyAfter = await workspace!.read({ kind: "scene.body", sceneId: repeatScene });
      assert.equal(JSON.stringify(bodyAfter?.body), bodyJsonBefore, "忽略命令不得修改任何正文");
    });

    await scenario("重复忽略幂等，取消忽略后问题回归", async () => {
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        rules: ["repeatedChar"]
      })) as ProofView;
      const group = view.issues.find((issue) => issue.sceneId === repeatScene)!;
      const ignoredLocation = group.locations.find((item) => item.ignored)!;
      const before = (await workspace!.read({ kind: "proof.ignores", projectId })) as Array<{ id: string }>;

      const again = await workspace!.transact({
        type: "proof.ignore",
        projectId,
        sceneId: repeatScene,
        rule: group.rule,
        locationKey: ignoredLocation.locationKey
      });
      const after = (await workspace!.read({ kind: "proof.ignores", projectId })) as Array<{ id: string }>;
      assert.equal(after.length, before.length, "重复忽略同一位置不应新增记录");
      assert.equal(again.removed, 0);

      const listed = (await workspace!.read({ kind: "proof.ignores", projectId })) as Array<{
        id: string;
        sceneId: string;
        rule: string;
        locationKey: string;
      }>;
      const target = listed.find((entry) => entry.locationKey === ignoredLocation.locationKey)!;
      const removed = await workspace!.transact({
        type: "proof.unignore",
        projectId,
        ignoreId: target.id
      });
      assert.equal(removed.removed, 1);

      const restored = (await workspace!.read({
        kind: "proof.query",
        projectId,
        rules: ["repeatedChar"]
      })) as ProofView;
      assert.equal(restored.total, 3, "取消忽略后问题应回归");
      assert.equal(restored.ignoredCount, 0);
    });

    await scenario("全部忽略后进入已忽略列表，includeIgnored 可查回", async () => {
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        rules: ["repeatedChar"]
      })) as ProofView;
      const group = view.issues.find((issue) => issue.sceneId === repeatScene)!;
      for (const location of group.locations) {
        await workspace!.transact({
          type: "proof.ignore",
          projectId,
          sceneId: repeatScene,
          rule: "repeatedChar",
          locationKey: location.locationKey,
          matchedText: location.matchedText
        });
      }
      const hidden = (await workspace!.read({
        kind: "proof.query",
        projectId,
        rules: ["repeatedChar"]
      })) as ProofView;
      assert.equal(hidden.issues.some((issue) => issue.sceneId === repeatScene), false);

      const shown = (await workspace!.read({
        kind: "proof.query",
        projectId,
        rules: ["repeatedChar"],
        includeIgnored: true
      })) as ProofView;
      const ignoredGroup = shown.ignoredIssues.find((issue) => issue.sceneId === repeatScene);
      assert.ok(ignoredGroup, "全部忽略后应在 ignoredIssues 中可查回");
      assert.equal(ignoredGroup!.count, 0);
      assert.equal(ignoredGroup!.ignoredCount, 2);
      assert.equal(shown.ignoredCount, 2);
      assert.equal(shown.rawTotal, 3);

      // 清理：恢复默认状态，避免影响后续场景。
      for (const location of ignoredGroup!.locations) {
        await workspace!.transact({
          type: "proof.unignore",
          projectId,
          sceneId: repeatScene,
          rule: "repeatedChar",
          locationKey: location.locationKey
        });
      }
    });

    await scenario("无效忽略参数报 invalid-input / not-found", async () => {
      let badRule: unknown;
      try {
        await workspace!.transact({
          type: "proof.ignore",
          projectId,
          sceneId: repeatScene,
          rule: "notARule" as never,
          locationKey: "x"
        });
      } catch (error) {
        badRule = error;
      }
      assert.equal((badRule as CreationWorkspaceError).code, "invalid-input");

      let crossProject: unknown;
      try {
        await workspace!.transact({
          type: "proof.ignore",
          projectId: projectB,
          sceneId: repeatScene,
          rule: "repeatedChar",
          locationKey: "x"
        });
      } catch (error) {
        crossProject = error;
      }
      assert.equal((crossProject as CreationWorkspaceError).code, "invalid-input");

      let missing: unknown;
      try {
        await workspace!.transact({
          type: "proof.unignore",
          projectId,
          ignoreId: "proofIgnore-nope"
        });
      } catch (error) {
        missing = error;
      }
      assert.equal((missing as CreationWorkspaceError).code, "not-found");
    });

    let aliasScene = "";
    await scenario("别名一致性：全书少数派称呼逐个提示", async () => {
      await workspace!.transact({
        type: "card.create",
        projectId,
        kind: "character",
        title: "洛水之蔚",
        aliases: ["洛蔚"]
      });
      const extra = await workspace!.transact({
        type: "scene.create",
        chapterId,
        title: "别名场景"
      }) as { entityId: string };
      aliasScene = extra.entityId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: aliasScene,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "洛水之蔚站在河边。" }] },
            { type: "paragraph", content: [{ type: "text", text: "洛水之蔚望着对岸。" }] },
            { type: "paragraph", content: [{ type: "text", text: "洛蔚终于开口说话。" }] }
          ]
        }
      });
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        sceneId: aliasScene,
        rules: ["aliasInconsistency"]
      })) as ProofView;
      assert.equal(view.total, 1, "只提示少数派称呼所在位置");
      const issue = view.issues[0]!;
      assert.equal(issue.rule, "aliasInconsistency");
      assert.equal(issue.locations[0]!.matchedText, "洛蔚");
      assert.equal(issue.locations[0]!.paragraphIndex, 2);
      assert.equal(issue.message.includes("洛蔚"), true);
    });

    await scenario("别名一致：全书只有一种称呼时不报警", async () => {
      const single = await workspace!.transact({
        type: "scene.create",
        chapterId,
        title: "单一称呼场景"
      }) as { entityId: string };
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: single.entityId,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "慕辰走过来。" }] },
            { type: "paragraph", content: [{ type: "text", text: "慕辰点了点头。" }] }
          ]
        }
      });
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        sceneId: single.entityId,
        rules: ["aliasInconsistency"]
      })) as ProofView;
      assert.equal(view.total, 0);
    });

    await scenario("疑似错拼：与词表仅差一个字的词按位置提示", async () => {
      await workspace!.transact({
        type: "card.create",
        projectId,
        kind: "character",
        title: "慕辰",
        aliases: []
      });
      const extra = await workspace!.transact({
        type: "scene.create",
        chapterId,
        title: "错拼场景"
      }) as { entityId: string };
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: extra.entityId,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "慕辰走过来。" }] },
            { type: "paragraph", content: [{ type: "text", text: "慕辰点了点头。" }] },
            { type: "paragraph", content: [{ type: "text", text: "慕宸没有说话。" }] }
          ]
        }
      });
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        sceneId: extra.entityId,
        rules: ["suspectedTypo"]
      })) as ProofView;
      assert.equal(view.total, 1);
      const issue = view.issues[0]!;
      assert.equal(issue.rule, "suspectedTypo");
      assert.equal(issue.locations[0]!.matchedText, "慕宸");
      assert.equal(issue.locations[0]!.paragraphIndex, 2);
      assert.equal(issue.message.includes("慕辰"), true, "消息应指出疑似被写错的词条");
    });

    await scenario("疑似错拼：词表自身子串不算错拼", async () => {
      const extra = await workspace!.transact({
        type: "scene.create",
        chapterId,
        title: "词表子串场景"
      }) as { entityId: string };
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: extra.entityId,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [{ type: "paragraph", content: [{ type: "text", text: "洛水之蔚走过来。" }] }]
        }
      });
      const view = (await workspace!.read({
        kind: "proof.query",
        projectId,
        sceneId: extra.entityId,
        rules: ["suspectedTypo"]
      })) as ProofView;
      assert.equal(view.total, 0, "长名内部的二字片段不应被当成错拼");
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
