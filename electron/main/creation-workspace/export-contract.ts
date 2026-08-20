import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  openCreationWorkspace,
  type CreationStructureResult,
  type CreationWorkspace,
  type ProjectExportView
} from "./index";
import { buildDraftExport } from "../creation-export";

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-export-"));
  let workspace: CreationWorkspace | undefined;
  let tests = 0;
  let projectId = "";
  let sceneId = "";
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

    await scenario("成稿导出聚合卷章场景与正文文本", async () => {
      const created = await workspace!.transact({ type: "project.create", title: "导出测试" });
      projectId = created.projectId;
      sceneId = created.sceneId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "雨落在旧城墙上。" }] },
            { type: "sceneBreak" },
            { type: "paragraph", content: [{ type: "text", text: "风穿过长巷。" }] }
          ]
        }
      });
      const exportView = (await workspace!.read({ kind: "project.export", projectId })) as ProjectExportView;
      assert.equal(exportView.title, "导出测试");
      assert.equal(exportView.volumes.length, 1);
      assert.equal(exportView.volumes[0]?.title, "正文");
      const chapter = exportView.volumes[0]?.chapters[0];
      assert.equal(chapter?.displayNumber, "第1章");
      assert.equal(chapter?.scenes.length, 1);
      const text = chapter?.scenes[0]?.text ?? "";
      assert.equal(text.includes("雨落在旧城墙上。"), true);
      assert.equal(text.includes("风穿过长巷。"), true);
      assert.equal(text.includes("　　"), true);
      assert.equal(text.includes("\n\n"), true);
    });

    await scenario("多卷多章多场景结构正确聚合", async () => {
      const volume = await workspace!.transact({ type: "volume.create", projectId, title: "第二卷" }) as CreationStructureResult;
      const chapter = await workspace!.transact({ type: "chapter.create", projectId, volumeId: volume.entityId, title: "新章" }) as CreationStructureResult;
      const scene = await workspace!.transact({ type: "scene.create", chapterId: chapter.entityId, title: "新场景" }) as CreationStructureResult;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: scene.entityId,
        baseRevision: 1,
        body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "第二卷正文。" }] }] }
      });
      const exportView = (await workspace!.read({ kind: "project.export", projectId })) as ProjectExportView;
      assert.equal(exportView.volumes.length, 2);
      assert.equal(exportView.volumes[1]?.title, "第二卷");
      assert.equal(exportView.volumes[1]?.chapters[0]?.displayNumber, "第1章");
      assert.equal(exportView.volumes[1]?.chapters[0]?.scenes[0]?.text, "第二卷正文。");
    });

    await scenario("软删除实体不出现在导出", async () => {
      const exportBefore = (await workspace!.read({ kind: "project.export", projectId })) as ProjectExportView;
      const secondScene = exportBefore.volumes[1]?.chapters[0]?.scenes[0];
      assert.equal(secondScene !== undefined, true);
      await workspace!.transact({ type: "scene.delete", sceneId: secondScene!.id });
      const exportAfter = (await workspace!.read({ kind: "project.export", projectId })) as ProjectExportView;
      assert.equal(exportAfter.volumes[1]?.chapters[0]?.scenes.length, 0);
      await workspace!.transact({ type: "trash.restore", projectId, entity: "scene", entityId: secondScene!.id });
    });

    await scenario("成稿预设：平台净文本与审阅稿差异（authorNote/场景标题/无引用标记）", async () => {
      const created = (await workspace!.transact({ type: "project.create", title: "预设导出" })) as {
        projectId: string;
        sceneId: string;
      };
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: created.sceneId,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "雨落旧城。" }] },
            { type: "authorNote", content: [{ type: "text", text: "这里改一下语气。" }] },
            { type: "sceneBreak" },
            { type: "quoteLetter", content: [{ type: "text", text: "以剑为誓。" }] },
            { type: "centeredText", content: [{ type: "text", text: "终章" }] }
          ]
        }
      });
      const namedScene = (await workspace!.transact({
        type: "scene.create",
        chapterId: (await workspace!.read({ kind: "project.export", projectId: created.projectId }))!.volumes[0]!.chapters[0]!.id,
        title: "山道"
      })) as CreationStructureResult;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: namedScene.entityId,
        baseRevision: 1,
        body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "风急人未至。" }] }] }
      });
      const view = (await workspace!.read({
        kind: "project.export",
        projectId: created.projectId,
        includeBlocks: true
      })) as ProjectExportView;
      const firstScene = view.volumes[0]!.chapters[0]!.scenes[0]!;
      assert.equal(firstScene.blocks !== undefined, true);
      assert.equal(firstScene.blocks!.some((block) => block.kind === "authorNote"), true);

      const plain = buildDraftExport(view, "platform-plain");
      assert.equal(plain?.extension, "txt");
      assert.equal(plain!.text.includes("这里改一下语气"), false);
      assert.equal(plain!.text.includes("作者按"), false);
      assert.equal(plain!.text.includes("雨落旧城"), true);
      assert.equal(plain!.text.includes("以剑为誓"), true);
      assert.equal(plain!.text.includes("终章"), true);
      // 平台净文本不含内部场景标题（含非默认场景「山道」）
      assert.equal(plain!.text.includes("山道"), false);
      assert.equal(plain!.text.includes("card-"), false);
      assert.equal(plain!.text.includes("annotation-"), false);

      const review = buildDraftExport(view, "standard-review");
      assert.equal(review?.extension, "md");
      assert.equal(review!.text.includes("# 预设导出"), true);
      assert.equal(review!.text.includes("> 作者按：这里改一下语气。"), true);
      assert.equal(review!.text.includes("> 以剑为誓。"), true);
      assert.equal(review!.text.includes("**居中：** 终章"), true);
      assert.equal(review!.text.includes("<center>"), false);
      assert.equal(review!.text.includes("* * *"), true);
      // 非默认场景标题保留层级；引用与批注元数据不进入审阅稿
      assert.equal(review!.text.includes("#### 山道"), true);
      assert.equal(review!.text.includes("card-"), false);
      assert.equal(review!.text.includes("annotation-"), false);

      assert.equal(buildDraftExport(view, "unknown-preset"), null);
      assert.equal(buildDraftExport(view, undefined), null);
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
